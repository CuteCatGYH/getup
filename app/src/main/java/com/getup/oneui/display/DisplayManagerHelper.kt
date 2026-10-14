package com.getup.oneui.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.compose.runtime.Composable
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.ui.presentation.PresentationScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper class managing Android DisplayManager listener, secondary display detection,
 * and presentation window lifecycles for dual-screen operation.
 */
class DisplayManagerHelper(private val context: Context) {

    private val TAG = "DisplayManagerHelper"

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    private val _connectedDisplays = MutableStateFlow<List<DisplayInfo>>(emptyList())
    val connectedDisplays: StateFlow<List<DisplayInfo>> = _connectedDisplays.asStateFlow()

    private val _activePresentationDisplayId = MutableStateFlow<Int?>(null)
    val activePresentationDisplayId: StateFlow<Int?> = _activePresentationDisplayId.asStateFlow()

    private val _isPresentationShowing = MutableStateFlow(false)
    val isPresentationShowing: StateFlow<Boolean> = _isPresentationShowing.asStateFlow()

    private var activePresentation: SecondaryDisplayPresentation? = null
    private var isListening = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "Secondary display added: $displayId")
            refreshConnectedDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Secondary display removed: $displayId")
            if (_activePresentationDisplayId.value == displayId) {
                dismissPresentation()
            }
            refreshConnectedDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.d(TAG, "Secondary display changed: $displayId")
            refreshConnectedDisplays()
        }
    }

    /**
     * Start observing display connection/disconnection events.
     */
    fun startListening() {
        if (isListening || displayManager == null) return
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        isListening = true
        refreshConnectedDisplays()
    }

    /**
     * Stop observing display events and dismiss any active presentation.
     */
    fun stopListening() {
        if (!isListening || displayManager == null) return
        displayManager.unregisterDisplayListener(displayListener)
        isListening = false
        dismissPresentation()
    }

    /**
     * Scan available secondary presentation displays and update StateFlow.
     */
    fun refreshConnectedDisplays() {
        if (displayManager == null) {
            _connectedDisplays.value = emptyList()
            return
        }

        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val allDisplays = displayManager.displays

        // Filter out default primary screen (ID 0)
        val secondaryList = allDisplays.filter { it.displayId != Display.DEFAULT_DISPLAY }
            .map { display ->
                val isPresentationCategory = presentationDisplays.any { it.displayId == display.displayId }
                val displayMetrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getMetrics(displayMetrics)
                DisplayInfo(
                    id = display.displayId,
                    name = display.name ?: "External Display ${display.displayId}",
                    width = displayMetrics.widthPixels,
                    height = displayMetrics.heightPixels,
                    refreshRate = display.refreshRate,
                    isPresentationDisplay = isPresentationCategory
                )
            }

        _connectedDisplays.value = secondaryList
        Log.d(TAG, "Updated connected secondary displays count: ${secondaryList.size}")
    }

    /**
     * Shows a presentation window on the specified display (or first available secondary display).
     */
    fun showPresentation(
        targetDisplayId: Int? = null,
        configFlow: StateFlow<SecondaryDisplayConfig>,
        content: @Composable (SecondaryDisplayConfig) -> Unit = { config -> PresentationScreen(config = config) }
    ): Boolean {
        if (displayManager == null) return false

        val displays = displayManager.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
        if (displays.isEmpty()) {
            Log.w(TAG, "No secondary displays available to launch Presentation.")
            dismissPresentation()
            return false
        }

        val targetDisplay = if (targetDisplayId != null) {
            displays.find { it.displayId == targetDisplayId } ?: displays.first()
        } else {
            displays.first()
        }

        // If presentation is already showing on this target display, do nothing
        if (activePresentation != null &&
            _activePresentationDisplayId.value == targetDisplay.displayId &&
            activePresentation?.isShowing == true
        ) {
            return true
        }

        // Dismiss existing presentation if target changed
        dismissPresentation()

        return try {
            val presentation = SecondaryDisplayPresentation(
                outerContext = context,
                display = targetDisplay,
                configFlow = configFlow,
                content = content
            )
            presentation.show()
            activePresentation = presentation
            _activePresentationDisplayId.value = targetDisplay.displayId
            _isPresentationShowing.value = true
            Log.i(TAG, "Presentation successfully shown on display: ${targetDisplay.displayId} (${targetDisplay.name})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show presentation on display ${targetDisplay.displayId}", e)
            dismissPresentation()
            false
        }
    }

    /**
     * Dismisses the active secondary presentation dialog.
     */
    fun dismissPresentation() {
        try {
            activePresentation?.let {
                if (it.isShowing) {
                    it.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing presentation", e)
        } finally {
            activePresentation = null
            _activePresentationDisplayId.value = null
            _isPresentationShowing.value = false
            Log.d(TAG, "Presentation dismissed.")
        }
    }
}
