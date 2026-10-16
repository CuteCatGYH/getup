package com.getup.oneui.display

import android.os.Binder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
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
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.ui.presentation.PresentationScreen
import com.getup.oneui.ui.theme.双屏并发Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private val windowAreaController: WindowAreaController? by lazy {
        try {
            WindowAreaController.getOrCreate()
        } catch (t: Throwable) {
            Log.w(TAG, "WindowAreaController not available", t)
            null
        }
    }

    private val _isRearDisplayAvailable = MutableStateFlow(false)
    val isRearDisplayAvailable: StateFlow<Boolean> = _isRearDisplayAvailable.asStateFlow()

    private val _isRearDisplayActive = MutableStateFlow(false)
    val isRearDisplayActive: StateFlow<Boolean> = _isRearDisplayActive.asStateFlow()

    private var rearAreaInfo: WindowAreaInfo? = null
    private var currentSession: WindowAreaSessionPresenter? = null

    init {
        startObservingWindowAreas()
    }

    private fun startObservingWindowAreas() {
        coroutineScope.launch {
            try {
                val controller = windowAreaController ?: return@launch
                controller.windowAreaInfos.collect { windowAreaInfos ->
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
    fun startRearDisplay(
        activity: ComponentActivity,
        configFlow: StateFlow<SecondaryDisplayConfig>
    ) {
        val controller = windowAreaController
        if (controller == null) {
            Log.w(TAG, "Cannot start rear display: WindowAreaController is null")
            return
        }

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

        if (currentSession != null && _isRearDisplayActive.value) {
            Log.d(TAG, "Rear display presentation already active.")
            return
        }

        val token: Binder = info.token
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : WindowAreaPresentationSessionCallback {
            override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                Log.i(TAG, "Rear display session started successfully!")
                currentSession = session
                _isRearDisplayActive.value = true

                fun trySetContentView(attempt: Int = 1) {
                    try {
                        val composeView = ComposeView(session.context).apply {
                            setViewTreeLifecycleOwner(activity)
                            setViewTreeSavedStateRegistryOwner(activity)
                            setViewTreeViewModelStoreOwner(activity)

                            setContent {
                                双屏并发Theme {
                                    val currentConfig = configFlow.collectAsState().value
                                    PresentationScreen(config = currentConfig)
                                }
                            }
                        }

                        session.setContentView(composeView)
                        Log.i(TAG, "session.setContentView succeeded on attempt $attempt")
                    } catch (e: Exception) {
                        Log.w(TAG, "setContentView failed on attempt $attempt: ${e.message}")
                        if (attempt < 5 && currentSession != null) {
                            coroutineScope.launch {
                                delay(150L * attempt)
                                trySetContentView(attempt + 1)
                            }
                        } else {
                            Log.e(TAG, "setContentView permanently failed after $attempt attempts", e)
                        }
                    }
                }

                trySetContentView(1)
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
            controller.presentContentOnWindowArea(
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

    fun startRearDisplayPresentation(
        activity: ComponentActivity,
        configFlow: StateFlow<SecondaryDisplayConfig>
    ) {
        startRearDisplay(activity, configFlow)
    }

    /**
     * Stop and close the rear display presentation session.
     */
    fun stopRearDisplay() {
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

    fun stopRearDisplayPresentation() {
        stopRearDisplay()
    }

    fun destroy() {
        stopRearDisplay()
        coroutineScope.cancel()
    }
}
