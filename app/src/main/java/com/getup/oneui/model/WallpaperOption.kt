package com.getup.oneui.model

/**
 * Represents background wallpaper options for the presentation screen.
 */
sealed class WallpaperOption {
    abstract val id: String
    abstract val name: String

    /**
     * Solid color background.
     */
    data class PresetColor(
        override val id: String,
        override val name: String,
        val colorHex: Long
    ) : WallpaperOption()

    /**
     * Linear gradient color background.
     */
    data class PresetGradient(
        override val id: String,
        override val name: String,
        val startColorHex: Long,
        val endColorHex: Long,
        val angleDegrees: Float = 45f
    ) : WallpaperOption()

    /**
     * Custom image wallpaper provided via Uri string.
     */
    data class CustomImage(
        override val id: String = "custom_image",
        override val name: String = "Custom Image",
        val imageUri: String
    ) : WallpaperOption()

    companion object {
        val DEFAULT_GRADIENTS: List<PresetGradient> = listOf(
            PresetGradient(
                id = "grad_midnight",
                name = "Midnight Blue",
                startColorHex = 0xFF0F2027,
                endColorHex = 0xFF2C5364
            ),
            PresetGradient(
                id = "grad_sunset",
                name = "Sunset Glow",
                startColorHex = 0xFF8A2387,
                endColorHex = 0xFFE94057
            ),
            PresetGradient(
                id = "grad_cyberpunk",
                name = "Cyberpunk Purple",
                startColorHex = 0xFF12002B,
                endColorHex = 0xFF8A00D4
            ),
            PresetGradient(
                id = "grad_emerald",
                name = "Emerald Forest",
                startColorHex = 0xFF052520,
                endColorHex = 0xFF095B4F
            ),
            PresetGradient(
                id = "grad_aurora",
                name = "Aurora Borealis",
                startColorHex = 0xFF00C9FF,
                endColorHex = 0xFF92FE9D
            )
        )

        val DEFAULT_COLORS: List<PresetColor> = listOf(
            PresetColor(
                id = "color_dark",
                name = "Deep Charcoal",
                colorHex = 0xFF121212
            ),
            PresetColor(
                id = "color_navy",
                name = "Navy Dark",
                colorHex = 0xFF0B192C
            ),
            PresetColor(
                id = "color_oled",
                name = "Pure Black (OLED)",
                colorHex = 0xFF000000
            ),
            PresetColor(
                id = "color_slate",
                name = "Slate Grey",
                colorHex = 0xFF1E293B
            )
        )
    }
}
