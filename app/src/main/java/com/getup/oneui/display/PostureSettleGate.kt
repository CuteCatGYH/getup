package com.getup.oneui.display

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Converts a stream of raw, possibly-transient fold postures into a single **settled**
 * posture verdict, applying a per-posture stability window.
 *
 * ## Why this exists
 * DeviceStateManager fires a burst of states during a physical hinge movement, e.g.
 * `OPENED(3) -> HALF_OPENED(2) -> CLOSED(0)` when folding. Acting on the first reading of
 * any of these causes the classic bugs:
 *  - the transient `OPENED` blip mid-fold lights the cover display back up ("再次解锁 / 内屏也亮"), and
 *  - the transient `HALF_OPENED` pass-through is mistaken for a resting half-open posture.
 *
 * The gate only trusts a posture once it has *persisted* for its window and is re-verified
 * at the end of that window.
 *
 * ## Windows
 *  - `OPENED`      -> [openedSettleMs]  (short; genuine unfolds settle quickly)
 *  - `TRANSITIONAL`-> [transitionalSettleMs] (longer, so a fold/unfold *pass-through* through
 *                    the hinge never qualifies as a resting half-open posture)
 *  - `CLOSED`      -> immediate (folded is never usable, no need to wait)
 *
 * ## Source of truth
 * The [postureProvider] must return the **authoritative** posture derived from
 * DeviceStateManager (or `null` when unresolved). Geometric display-size heuristics must
 * NOT be used, because they are exactly what blips during the transition.
 *
 * ## Reuse
 * This class is deliberately posture-agnostic and Android-service-free, so the future
 * StandBy feature can reuse the same gate (with different windows) to decide when a
 * resting half-open posture has truly settled. It is unit-testable with a virtual-time
 * dispatcher.
 */
class PostureSettleGate(
    private val scope: CoroutineScope,
    private val openedSettleMs: Long,
    private val transitionalSettleMs: Long,
    private val postureProvider: () -> FoldPosture?,
    private val logTag: String = "PostureSettleGate"
) {

    private var settleJob: Job? = null

    /** The last posture confirmed to be stable. `null` until any posture settles. */
    @Volatile
    var settledPosture: FoldPosture? = null
        private set

    /**
     * Whether the cover display may currently be presented on, based on the *settled*
     * posture. A stable [FoldPosture.TRANSITIONAL] resting posture qualifies; a mere
     * pass-through does not.
     */
    val isUsable: Boolean
        get() = settledPosture?.isUsableForCoverDisplay == true

    /**
     * Feed the latest authoritative [posture].
     *
     * @param onSettled invoked (on the gate's scope, main thread) the first time a posture
     *   becomes stable, or whenever the settled posture *changes* to another stable one.
     *   Prefer this for posture *transitions*; use [recheck] for an explicit external trigger
     *   that must act even when the settled posture has not changed.
     */
    fun onPosture(posture: FoldPosture?, onSettled: () -> Unit = {}) {
        onPostureInternal(posture, force = false, onSettled = onSettled)
    }

    /**
     * Explicitly re-run the settle debounce for the current posture and **always** invoke
     * [onSettled] once the posture is confirmed stable-and-usable, even if the settled
     * verdict has not changed.
     *
     * This exists for trigger-based re-presentation paths (screen-on / unlock) where the
     * device may have been continuously unfolded the whole time: the settled posture is
     * still OPENED, yet the cover display was dismissed while the screen was off and now
     * needs to be shown again. A plain [onPosture] cannot serve that case because it
     * (correctly) suppresses callbacks for an unchanged verdict.
     */
    fun recheck(posture: FoldPosture?, onSettled: () -> Unit = {}) {
        onPostureInternal(posture, force = true, onSettled = onSettled)
    }

    private fun onPostureInternal(posture: FoldPosture?, force: Boolean, onSettled: () -> Unit) {
        when (posture) {
            null -> reset() // Unresolved: drop any verdict rather than guess.
            FoldPosture.CLOSED -> {
                // Folded is definitive and immediately unusable. No settle window needed.
                settleJob?.cancel()
                settleJob = null
                if (settledPosture != FoldPosture.CLOSED) {
                    settledPosture = FoldPosture.CLOSED
                    onSettled()
                } else if (force) {
                    // Even if we were already CLOSED, an explicit recheck wants the final
                    // verdict delivered (callers can use it to enforce teardown).
                    onSettled()
                }
            }
            FoldPosture.OPENED,
            FoldPosture.TRANSITIONAL -> scheduleSettle(posture, force, onSettled)
        }
    }

    private fun scheduleSettle(candidate: FoldPosture, force: Boolean, onSettled: () -> Unit) {
        // Already settled on this posture and nothing pending -> nothing to do, UNLESS this is
        // a forced recheck (screen-on / unlock re-presentation path).
        if (!force && settledPosture == candidate && settleJob?.isActive != true) return

        val windowMs = when (candidate) {
            FoldPosture.TRANSITIONAL -> transitionalSettleMs
            else -> openedSettleMs
        }

        // Restart the window on every signal so we measure *continuous* time in this posture.
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(windowMs)
            // Re-verify: the posture may have changed during the window (e.g. the device
            // kept moving and actually folded).
            if (postureProvider() == candidate) {
                // Normally only fire when the STABLE verdict actually changes; a repeated
                // signal for an already-settled posture must not re-trigger side effects.
                // A forced recheck deliberately bypasses this so an explicit trigger
                // (screen-on/unlock) can always re-present.
                val changed = settledPosture != candidate
                settledPosture = candidate
                if (changed || force) {
                    Log.i(logTag, "Posture settled: $candidate (window=${windowMs}ms, force=$force)")
                    onSettled()
                }
            } else {
                Log.i(logTag, "Settle window elapsed but posture is no longer $candidate; aborting")
            }
        }
    }

    /** Invalidate any pending/active verdict (e.g. on fold, shutdown, or user reset). */
    fun reset() {
        settleJob?.cancel()
        settleJob = null
        settledPosture = null
    }
}
