package com.getup.oneui.display

import android.os.Binder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.getup.oneui.ui.theme.双屏并发Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Helper class managing Jetpack WindowManager WindowAreaController APIs for Samsung foldables
 * and other foldable devices supporting Rear Display Presentation (dual-screen mode).
 */
@OptIn(ExperimentalWindowApi::class)
class WindowAreaHelper(
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val TAG = "WindowAreaHelper"

    private val windowAreaController: WindowAreaController by lazy {
        WindowAreaController.getOrCreate()
    }

    private val _isRearDisplayAvailable = MutableStateFlow(false)
    val isRearDisplayAvailable: StateFlow<Boolean> = _isRearDisplayAvailable.asStateFlow()

    private val _isRearDisplayActive = MutableStateFlow(false)
    val isRearDisplayActive: StateFlow<Boolean> = _isRearDisplayActive.asStateFlow()

    private var rearAreaInfo: WindowAreaInfo? = null
    private var currentSession: WindowAreaSessionPresenter? = null

    private val composableContent = mutableStateOf<(@Composable () -> Unit)?>(null)

    init {
        startObservingWindowAreas()
    }

    private fun startObservingWindowAreas() {
        coroutineScope.launch {
            try {
                windowAreaController.windowAreaInfos.collect { windowAreaInfos ->
                    Log.d(TAG, "Received windowAreaInfos count: ${windowAreaInfos.size}")
                    val rearInfo = windowAreaInfos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                    rearAreaInfo = rearInfo

                    if (rearInfo != null) {
                        val capability = rearInfo.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
                        val status = capability.status
                        Log.d(TAG, "Rear display capability status: $status")

                        _isRearDisplayAvailable.value = (status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE ||
                                status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE)
                        _isRearDisplayActive.value = (status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE ||
                                currentSession != null)
                    } else {
                        _isRearDisplayAvailable.value = false
                        _isRearDisplayActive.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting windowAreaInfos", e)
                _isRearDisplayAvailable.value = false
                _isRearDisplayActive.value = false
            }
        }
    }

    /**
     * Start presenting content on the rear/cover display.
     * Uses [ComponentActivity] to bind view tree lifecycle, saved state, and view model stores
     * so that the Jetpack Compose hierarchy runs smoothly inside the presentation session.
     */
    fun startRearDisplayPresentation(
        activity: ComponentActivity,
        content: @Composable () -> Unit
    ) {
        val info = rearAreaInfo
        if (info == null) {
            Log.w(TAG, "Cannot start rear display presentation: rearAreaInfo is null")
            return
        }

        val capability = info.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
        if (capability.status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED ||
            capability.status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE
        ) {
            Log.w(TAG, "Rear display presentation capability unavailable: status=${capability.status}")
            return
        }

        composableContent.value = content

        if (currentSession != null && _isRearDisplayActive.value) {
            Log.d(TAG, "Rear display presentation already active, content updated.")
            return
        }

        val token: Binder = info.token
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : WindowAreaPresentationSessionCallback {
            override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                Log.i(TAG, "Rear display session started successfully!")
                currentSession = session
                _isRearDisplayActive.value = true

                val composeView = ComposeView(session.context).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)

                    setContent {
                        双屏并发Theme {
                            composableContent.value?.invoke()
                        }
                    }
                }

                session.setContentView(composeView)
            }

            override fun onSessionEnded(t: Throwable?) {
                if (t != null) {
                    Log.e(TAG, "Rear display session ended with error", t)
                } else {
                    Log.i(TAG, "Rear display session ended normally")
                }
                currentSession = null
                _isRearDisplayActive.value = false
            }

            override fun onContainerVisibilityChanged(isVisible: Boolean) {
                Log.d(TAG, "Rear display container visibility changed: isVisible=$isVisible")
            }
        }

        try {
            windowAreaController.presentContentOnWindowArea(
                token = token,
                activity = activity,
                executor = executor,
                windowAreaPresentationSessionCallback = callback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call presentContentOnWindowArea", e)
            _isRearDisplayActive.value = false
            currentSession = null
        }
    }

    /**
     * Stop and close the rear display presentation session.
     */
    fun stopRearDisplayPresentation() {
        Log.d(TAG, "Stopping rear display presentation")
        try {
            currentSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing rear display session", e)
        } finally {
            currentSession = null
            _isRearDisplayActive.value = false
        }
    }

    fun destroy() {
        stopRearDisplayPresentation()
        coroutineScope.cancel()
    }
}
