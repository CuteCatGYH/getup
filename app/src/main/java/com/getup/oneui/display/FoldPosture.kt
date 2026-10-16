package com.getup.oneui.display

/**
 * Semantic fold posture, derived from Samsung's raw `DeviceStateManager` state ids.
 *
 * This is the single, shared vocabulary used by the UI gate, the foreground service and
 * (in the future) the StandBy feature, so that "what counts as usable" is decided in ONE
 * place instead of being re-derived ad hoc at every call site.
 *
 * Raw state ids reported by Samsung:
 * ```
 * 0 = CLOSED (folded)
 * 1 = TENT
 * 2 = HALF_OPENED
 * 3 = OPENED
 * 4 = CONCURRENT_INNER_DEFAULT
 * 5 = CONCURRENT_OUTER_DEFAULT (not observed on current devices)
 * ```
 *
 * IMPORTANT: [TRANSITIONAL] covers `TENT` and `HALF_OPENED`. These are **bidirectional**
 * intermediates: they occur during BOTH unfolding and folding. Consumers must therefore
 * NOT treat them as settled until they've observed the posture persist (see [PostureSettleGate]).
 */
enum class FoldPosture {
    /** Physically folded (state 0). The cover display is the only active display. */
    CLOSED,

    /**
     * Hinge is part-way (TENT / HALF_OPENED). May be a genuine resting posture OR a
     * momentary pass-through during a fold/unfold movement — only the settle gate can tell
     * those apart.
     */
    TRANSITIONAL,

    /** Fully open, steady (OPENED / CONCURRENT). Safe to present on the cover display. */
    OPENED;

    /** Postures in which the cover display may legitimately be presented on. */
    val isUsableForCoverDisplay: Boolean
        get() = this == OPENED || this == TRANSITIONAL

    companion object {
        /**
         * Classify a raw `DeviceStateManager` id into a [FoldPosture].
         * Returns `null` for an unresolved/`null` state or an unknown id, so callers can
         * distinguish "not resolved yet" from "definitely folded".
         */
        fun fromRawState(state: Int?): FoldPosture? = when (state) {
            DeviceStateMonitor.STATE_CLOSED -> CLOSED
            DeviceStateMonitor.STATE_TENT,
            DeviceStateMonitor.STATE_HALF_OPENED -> TRANSITIONAL
            DeviceStateMonitor.STATE_OPENED,
            DeviceStateMonitor.STATE_CONCURRENT -> OPENED
            else -> null
        }
    }
}
