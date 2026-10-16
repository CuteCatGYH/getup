package com.getup.oneui.display

import android.app.Activity
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Monitors fold posture changes (e.g. flat / half-opened / folded) using Jetpack WindowInfoTracker.
 * Detects when a foldable device is opened/unfolded to the main display.
 */
class FoldStateMonitor(
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val TAG = "FoldStateMonitor"

    private val _isDeviceUnfolded = MutableStateFlow(false)
    val isDeviceUnfolded: StateFlow<Boolean> = _isDeviceUnfolded.asStateFlow()

    private val _foldingState = MutableStateFlow<FoldingFeature.State?>(null)
    val foldingState: StateFlow<FoldingFeature.State?> = _foldingState.asStateFlow()

    private var monitorJob: Job? = null

    /**
     * Start observing WindowLayoutInfo changes from WindowInfoTracker for the given Activity.
     */
    fun startMonitoring(activity: Activity) {
        monitorJob?.cancel()
        monitorJob = coroutineScope.launch {
            try {
                val tracker = WindowInfoTracker.getOrCreate(activity)
                tracker.windowLayoutInfo(activity).collect { layoutInfo ->
                    processLayoutInfo(layoutInfo)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error collecting windowLayoutInfo", t)
            }
        }
    }

    /**
     * Parse layout features to determine if the device is unfolded.
     */
    fun processLayoutInfo(layoutInfo: WindowLayoutInfo) {
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        if (foldingFeature != null) {
            _foldingState.value = foldingFeature.state
            val isUnfolded = (foldingFeature.state == FoldingFeature.State.FLAT ||
                    foldingFeature.state == FoldingFeature.State.HALF_OPENED)
            Log.d(TAG, "FoldingFeature detected: state=${foldingFeature.state}, isUnfolded=$isUnfolded")
            _isDeviceUnfolded.value = isUnfolded
        } else {
            _foldingState.value = null
            Log.d(TAG, "No FoldingFeature detected. Assuming not unfolded.")
            _isDeviceUnfolded.value = false
        }
    }

    @VisibleForTesting
    fun setDeviceUnfoldedForTest(unfolded: Boolean) {
        _isDeviceUnfolded.value = unfolded
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun destroy() {
        stopMonitoring()
        coroutineScope.cancel()
    }
}
