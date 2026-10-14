package com.getup.oneui.model

/**
 * Options for battery level display on the secondary screen.
 */
data class BatteryDisplayOption(
    val isVisible: Boolean = true,
    val showPercentage: Boolean = true,
    val style: BatteryStyle = BatteryStyle.HORIZONTAL,
    val lowBatteryThreshold: Int = 20
) {
    enum class BatteryStyle {
        HORIZONTAL,
        VERTICAL,
        CIRCULAR,
        MINIMAL
    }
}
