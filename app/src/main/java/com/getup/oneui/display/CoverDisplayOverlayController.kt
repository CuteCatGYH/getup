package com.getup.oneui.display

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.ui.presentation.PresentationScreen
import com.getup.oneui.ui.theme.双屏并发Theme
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages an overlay window directly on a specified Display (e.g. Display 1 / Cover Display)
 * using [Context.createWindowContext] with TYPE_APPLICATION_OVERLAY.
 * This runs independently of Activities and Presentations, enabling persistent background rendering.
 */
class CoverDisplayOverlayController(
    private val serviceContext: Context,
    val display: Display,
    private val configFlow: StateFlow<SecondaryDisplayConfig>,
    private var keepScreenOn: Boolean = false
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val TAG = "CoverOverlayController"

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    private var windowContext: Context? = null
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null

    var isShowing: Boolean = false
        private set

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    fun show(): Boolean {
        if (isShowing) return true

        // Defensive validation: never mount an overlay on an invalid display. Mounting on the
        // default display while the device is folded produces a window-context conflict crash
        // (BadTokenException / IllegalStateException), so bail out cleanly instead.
        if (!display.isValid) {
            Log.w(TAG, "Refusing to show overlay: target display ${display.displayId} is not valid")
            return false
        }

        return try {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

            val wc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                serviceContext.createWindowContext(
                    display,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    null
                )
            } else {
                serviceContext.createDisplayContext(display)
            }
            windowContext = wc

            val wm = wc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: throw IllegalStateException("WindowManager not found in window context")
            windowManager = wm

            // Only add FLAG_KEEP_SCREEN_ON if explicitly enabled by user.
            // When keepScreenOn is false, FLAG_KEEP_SCREEN_ON must NEVER be present,
            // allowing system display timeout and auto screen off to function normally.
            val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    (if (keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            )

            val view = ComposeView(wc).apply {
                setViewTreeLifecycleOwner(this@CoverDisplayOverlayController)
                setViewTreeSavedStateRegistryOwner(this@CoverDisplayOverlayController)
                setViewTreeViewModelStoreOwner(this@CoverDisplayOverlayController)

                setContent {
                    双屏并发Theme {
                        val config by configFlow.collectAsState()
                        PresentationScreen(config = config)
                    }
                }
            }
            composeView = view

            wm.addView(view, params)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            isShowing = true
            Log.i(TAG, "Cover display overlay window added on display ${display.displayId} (${display.name}), keepScreenOn=$keepScreenOn")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show cover display overlay window", e)
            dismiss()
            false
        }
    }

    /**
     * Dynamically update the keepScreenOn flag on the active overlay window.
     */
    fun updateKeepScreenOn(enabled: Boolean) {
        keepScreenOn = enabled
        val view = composeView ?: return
        val wm = windowManager ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val oldFlags = params.flags
        params.flags = if (enabled) {
            params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        if (params.flags != oldFlags) {
            try {
                wm.updateViewLayout(view, params)
                Log.d(TAG, "Dynamically updated overlay keepScreenOn=$enabled (flags=${params.flags})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update overlay layout flags", e)
            }
        }
    }

    fun dismiss() {
        try {
            composeView?.let { view ->
                windowManager?.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing cover display overlay view", e)
        } finally {
            composeView = null
            windowManager = null
            windowContext = null
            isShowing = false
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
            Log.d(TAG, "Cover display overlay dismissed.")
        }
    }
}
