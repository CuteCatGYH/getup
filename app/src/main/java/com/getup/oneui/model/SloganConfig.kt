package com.getup.oneui.model

/**
 * Configuration for secondary screen text slogan/announcement display.
 */
data class SloganConfig(
    val text: String = "Welcome to Dual Screen Console",
    val textColorHex: Long = 0xFFFFFFFF,
    val fontSizeSp: Int = 28,
    val isVisible: Boolean = true,
    val isMarquee: Boolean = false,
    val alignment: TextAlignment = TextAlignment.CENTER
) {
    enum class TextAlignment {
        LEFT, CENTER, RIGHT
    }
}
