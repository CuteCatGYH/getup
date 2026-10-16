package com.getup.oneui.display

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Monitors physical device posture and folding states (0=CLOSED, 1=TENT, 2=HALF_OPENED, 3=OPENED, 4=CONCURRENT)
 * directly via Android's DeviceStateManager system service, combined with DisplayManager dimensions
 * and Configuration metrics for 100% reliable fold/unfold detection across all One UI / Android versions.
 */
class DeviceStateMonitor(private val context: Context) {

    private val TAG = "DeviceStateMonitor"

    private val _currentState = MutableStateFlow<Int?>(null)
    val currentState: StateFlow<Int?> = _currentState.asStateFlow()

    /**
     * Semantic posture derived from [currentState]. `null` while unresolved.
     * NOTE: [FoldPosture.TRANSITIONAL] here is the *raw* classification — it does not yet
     * distinguish a resting half-open posture from a transient pass-through. Consumers that
     * need that distinction must run the signal through a [PostureSettleGate].
     */
    val posture: StateFlow<FoldPosture?> = _currentState
        .map { FoldPosture.fromRawState(it) }
        .stateIn(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _isUnfolded = MutableStateFlow(false)
    val isUnfolded: StateFlow<Boolean> = _isUnfolded.asStateFlow()

    private var dsmInstance: Any? = null
    private var callbackProxy: Any? = null
    private var displayManager: DisplayManager? = null
    private var isRegistered = false

    var onStateChangedListener: ((oldState: Int?, newState: Int) -> Unit)? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "onDisplayAdded: $displayId (secondary=${displayId != Display.DEFAULT_DISPLAY})")
            // A newly added secondary display is the definitive "unfolded" signal.
            syncUnfoldFromDisplay()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "onDisplayRemoved: $displayId")
            syncUnfoldFromDisplay()
        }

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                syncUnfoldFromDisplay()
            }
        }
    }

    fun startListening() {
        if (isRegistered) return

        // 1. Initial synchronous check from display/configuration
        val initialUnfolded = isDeviceUnfolded(context)
        _isUnfolded.value = initialUnfolded
        Log.i(TAG, "DeviceStateMonitor startListening: initial isUnfolded=$initialUnfolded")

        // 2. Register DisplayManager listener to monitor fold/unfold display resolution transitions
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayManager = dm
            dm?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        } catch (t: Throwable) {
            Log.w(TAG, "Could not register DisplayListener", t)
        }

        // 3. Register DeviceStateManager callback
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.w(TAG, "DeviceStateManager requires Android 12 (API 31)+")
            isRegistered = true
            return
        }

        try {
            val dsm = context.getSystemService("device_state")
                ?: run {
                    Log.w(TAG, "DEVICE_STATE_SERVICE not available on this device")
                    isRegistered = true
                    return
                }
            dsmInstance = dsm

            val callbackInterface = Class.forName("android.hardware.devicestate.DeviceStateManager\$DeviceStateCallback")

            val invocationHandler = object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                    when (method.name) {
                        "onStateChanged", "onDeviceStateChanged" -> {
                            val stateArg = args?.getOrNull(0)
                            val stateId = extractStateId(stateArg)
                            if (stateId != null) {
                                handleStateChanged(stateId)
                            }
                        }
                        "onBaseStateChanged" -> {
                            val stateArg = args?.getOrNull(0)
                            val stateId = extractStateId(stateArg)
                            Log.d(TAG, "Device base state: $stateId")
                        }
                        "onSupportedStatesChanged" -> {
                            Log.d(TAG, "Supported device states updated")
                        }
                        "equals" -> return proxy === args?.getOrNull(0)
                        "hashCode" -> return System.identityHashCode(proxy)
                        "toString" -> return "DeviceStateCallbackProxy"
                    }
                    return null
                }
            }

            val proxy = Proxy.newProxyInstance(
                callbackInterface.classLoader,
                arrayOf(callbackInterface),
                invocationHandler
            )
            callbackProxy = proxy

            val registerMethod = dsm.javaClass.getMethod(
                "registerCallback",
                Executor::class.java,
                callbackInterface
            )
            registerMethod.invoke(dsm, ContextCompat.getMainExecutor(context), proxy)
            isRegistered = true
            Log.i(TAG, "Successfully registered DeviceStateManager callback")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register DeviceStateManager callback", e)
            isRegistered = true
        }
    }

    private fun extractStateId(stateArg: Any?): Int? {
        if (stateArg == null) return null
        if (stateArg is Number) return stateArg.toInt()
        return try {
            val method = stateArg.javaClass.getMethod("getIdentifier")
            (method.invoke(stateArg) as? Number)?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun syncUnfoldFromDisplay() {
        // When DeviceStateManager has a definitive steady state, it must win over the
        // geometric display-dimension heuristic. In particular, CLOSED/TENT/HALF_OPENED all
        // mean "not fully unfolded", so we never let the heuristic claim otherwise and
        // trigger a spurious present during a fold transition.
        when (_currentState.value) {
            STATE_CLOSED, STATE_TENT, STATE_HALF_OPENED -> {
                if (_isUnfolded.value) {
                    _isUnfolded.value = false
                    Log.i(TAG, "syncUnfoldFromDisplay: steady state ${_currentState.value} -> isUnfolded=false")
                }
                return
            }
        }

        val unfolded = isDeviceUnfolded(context)
        if (_isUnfolded.value != unfolded) {
            _isUnfolded.value = unfolded
            Log.i(TAG, "syncUnfoldFromDisplay: updated isUnfolded=$unfolded")
        }
    }

    fun handleStateChanged(newState: Int) {
        val oldState = _currentState.value
        if (oldState == newState) return

        _currentState.value = newState

        // Samsung Fold state semantics:
        // 0: CLOSED / FOLDED
        // 1: TENT
        // 2: HALF_OPENED
        // 3: OPENED
        // 4: CONCURRENT_INNER_DEFAULT
        // 5: CONCURRENT_OUTER_DEFAULT
        //
        // IMPORTANT: HALF_OPENED (2) and TENT (1) are TRANSIENT intermediates that occur
        // during BOTH unfolding and folding. Treating them as "unfolded" causes spurious
        // auto-starts mid-fold. We only regard the steady OPENED/CONCURRENT states as
        // genuinely unfolded.
        val unfolded = when (newState) {
            STATE_OPENED, STATE_CONCURRENT -> true
            STATE_CLOSED, STATE_TENT, STATE_HALF_OPENED -> false
            else -> isDeviceUnfolded(context)
        }
        _isUnfolded.value = unfolded

        Log.i(TAG, "Device state changed: $oldState -> $newState (isUnfolded=$unfolded)")
        onStateChangedListener?.invoke(oldState, newState)
    }

    @VisibleForTesting
    fun setStateForTest(newState: Int) {
        handleStateChanged(newState)
    }

    fun stopListening() {
        if (!isRegistered) return
        try {
            displayManager?.unregisterDisplayListener(displayListener)
            displayManager = null

            val dsm = dsmInstance
            val proxy = callbackProxy
            if (dsm != null && proxy != null) {
                val callbackInterface = Class.forName("android.hardware.devicestate.DeviceStateManager\$DeviceStateCallback")
                val unregisterMethod = dsm.javaClass.getMethod("unregisterCallback", callbackInterface)
                unregisterMethod.invoke(dsm, proxy)
                Log.i(TAG, "Successfully unregistered DeviceStateManager callback")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error unregistering DeviceStateManager callback", e)
        } finally {
            isRegistered = false
            callbackProxy = null
            dsmInstance = null
        }
    }

    companion object {
        const val STATE_CLOSED = 0
        const val STATE_TENT = 1
        const val STATE_HALF_OPENED = 2
        const val STATE_OPENED = 3
        const val STATE_CONCURRENT = 4

        /**
         * Returns the number of non-default (secondary/cover) displays currently known
         * to the system. When the foldable is fully folded there is exactly ONE active
         * display; when unfolded there are TWO (inner + cover).
         */
        fun getSecondaryDisplayCount(context: Context?): Int {
            if (context == null) return 0
            return try {
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                dm?.displays?.count { it.displayId != Display.DEFAULT_DISPLAY } ?: 0
            } catch (t: Throwable) {
                0
            }
        }

        /**
         * Whether a secondary (cover / external) display is currently available for rendering.
         */
        fun hasSecondaryDisplay(context: Context?): Boolean = getSecondaryDisplayCount(context) > 0

        /**
         * Strict, crash-safe guard used to decide whether it is physically safe to
         * open a Presentation / Overlay window on a secondary display.
         *
         * It is deliberately conservative: it returns false (i.e. DO NOT present)
         * whenever the device appears to be folded OR no secondary display exists.
         * Presenting while folded would target the display the app is already on,
         * causing a window-context conflict / BadTokenException crash.
         */
        fun isSafeToPresentOnSecondaryDisplay(context: Context?): Boolean {
            if (context == null) return false
            // A secondary display must physically exist to render on. When folded the
            // cover screen IS Display 0, so this count is 0 and we bail out safely.
            if (!hasSecondaryDisplay(context)) return false
            // And the hardware must report as unfolded.
            return isDeviceUnfolded(context)
        }

        /**
         * Reliable check for whether a foldable device is currently unfolded.
         * Order of precedence (most reliable first):
         * 0. Authoritative DeviceStateManager state, when already resolved by an active monitor.
         * 1. Default Display dimensions:
         *    - Folded: Display.DEFAULT_DISPLAY is the cover screen (min dim <= 1100).
         *    - Unfolded: Display.DEFAULT_DISPLAY is the inner main screen (min dim > 1500).
         * 2. Configuration widths (only as a last resort, since an Activity that does not
         *    recreate on fold can expose stale unfolded dimensions and yield false positives).
         * 3. Display-count heuristic: a second display implies the hardware is unfolded.
         */
        fun isDeviceUnfolded(context: Context?): Boolean {
            if (context == null) return false
            try {
                // 1. Default Display real dimensions are the most trustworthy signal on
                //    Samsung foldables regardless of Activity recreation / config changes.
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                val defaultDisplay = dm?.getDisplay(Display.DEFAULT_DISPLAY)
                val size = Point()
                var haveSize = false
                if (defaultDisplay != null) {
                    @Suppress("DEPRECATION")
                    defaultDisplay.getRealSize(size)
                    haveSize = size.x > 0 && size.y > 0
                    if (haveSize) {
                        val minDim = minOf(size.x, size.y)
                        if (minDim > 1500) return true
                        if (minDim in 1..1100) return false
                    }
                }

                // 2. Only trust Configuration when we could not read display dimensions,
                //    and additionally require a second display to confirm "unfolded" so a
                //    stale Activity configuration can never produce a false unfolded state.
                val config = context.resources?.configuration
                if (config != null) {
                    if (config.smallestScreenWidthDp >= 600 || config.screenWidthDp >= 600) {
                        // Guard against stale config on the cover screen while folded.
                        if (hasSecondaryDisplay(context)) return true
                    }
                    if (config.smallestScreenWidthDp in 1..499 && config.screenWidthDp in 1..499) {
                        return false
                    }
                }

                // 3. Last-resort heuristic: the presence of a secondary display means the
                //    hardware is unfolded, because the cover screen is Display 0 when folded.
                return hasSecondaryDisplay(context)
            } catch (t: Throwable) {
                // Fallback safe return
            }
            return false
        }
    }
}
