package com.getup.oneui.display

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import java.lang.ref.WeakReference
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
 * Monitors fold posture changes (e.g. flat / half-opened / folded) using Jetpack WindowInfoTracker
 * combined with DeviceStateMonitor fallback for Samsung devices where displayFeatures can be empty when flat.
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
    private var activityRef: WeakReference<Activity>? = null

    /**
     * Authoritative fold signal supplied by a live [DeviceStateMonitor]. When the system's
     * DeviceStateManager explicitly reports a folded state, it must win over the geometric
     * (display-dimension / display-count) heuristics, which can be fooled by a software
     * device-state override where the hinge is still physically open.
     */
    @Volatile
    private var authoritativeUnfolded: Boolean? = null

    /**
     * Bind an authoritative fold-state source. Returns null-safe: pass the monitor's
     * `isUnfolded` value here whenever it emits a change.
     */
    fun setAuthoritativeUnfoldedForTest(unfolded: Boolean?) {
        authoritativeUnfolded = unfolded
    }

    /**
     * Start observing WindowLayoutInfo changes from WindowInfoTracker for the given Activity.
     */
    fun startMonitoring(activity: Activity) {
        activityRef = WeakReference(activity)
        monitorJob?.cancel()
        monitorJob = coroutineScope.launch {
            try {
                val tracker = WindowInfoTracker.getOrCreate(activity)
                tracker.windowLayoutInfo(activity).collect { layoutInfo ->
                    processLayoutInfo(layoutInfo, activity)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error collecting windowLayoutInfo", t)
            }
        }
    }

    /**
     * Parse layout features to determine if the device is unfolded.
     *
     * A confirmed folded state from the live [DeviceStateMonitor] (backed by the system
     * DeviceStateManager) always wins, because geometric heuristics can be fooled by a
     * software device-state override where the hinge remains physically open.
     */
    fun processLayoutInfo(layoutInfo: WindowLayoutInfo, context: Context? = null) {
        // Authoritative device-state signal takes precedence.
        val authoritative = authoritativeUnfolded
        if (authoritative == false) {
            _foldingState.value = null
            Log.d(TAG, "Authoritative DeviceStateManager reports folded; overriding geometric check")
            _isDeviceUnfolded.value = false
            return
        }

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
            // When device is opened flat, Samsung sometimes does not emit a FoldingFeature.
            // Check display dimensions and configuration instead of assuming false.
            val targetContext = context ?: activityRef?.get()
            val unfolded = if (targetContext != null) DeviceStateMonitor.isDeviceUnfolded(targetContext) else false
            Log.d(TAG, "No FoldingFeature detected. Display check isUnfolded=$unfolded")
            _isDeviceUnfolded.value = unfolded
        }
    }

    @VisibleForTesting
    fun setDeviceUnfoldedForTest(unfolded: Boolean) {
        _isDeviceUnfolded.value = unfolded
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        activityRef = null
    }

    fun destroy() {
        stopMonitoring()
        coroutineScope.cancel()
    }
}
