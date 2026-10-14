package com.getup.oneui.model

/**
 * Unified data model holding all presentation screen settings.
 */
data class SecondaryDisplayConfig(
    val clockStyle: ClockStyle = ClockStyle.DIGITAL_BOLD,
    val wallpaper: WallpaperOption = WallpaperOption.DEFAULT_GRADIENTS[0],
    val sloganConfig: SloganConfig = SloganConfig(),
    val batteryOption: BatteryDisplayOption = BatteryDisplayOption(),
    val isPresentationEnabled: Boolean = true,
    val showSeconds: Boolean = true,
    val is24HourFormat: Boolean = true,
    val targetDisplayId: Int? = null,
    val brightnessLevel: Float = 1.0f
)
