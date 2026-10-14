package com.getup.oneui.model

/**
 * Defines available clock styles for secondary screen rendering.
 */
enum class ClockStyle(
    val id: String,
    val displayName: String,
    val isDigital: Boolean,
    val description: String
) {
    DIGITAL_MINIMAL(
        id = "digital_minimal",
        displayName = "Digital Minimal",
        isDigital = true,
        description = "Clean modern digital typography with minimal clutter"
    ),
    DIGITAL_BOLD(
        id = "digital_bold",
        displayName = "Digital Bold",
        isDigital = true,
        description = "Large high-contrast digital display"
    ),
    DIGITAL_NEON(
        id = "digital_neon",
        displayName = "Digital Neon",
        isDigital = true,
        description = "Vibrant glowing digital clock face"
    ),
    ANALOG_CLASSIC(
        id = "analog_classic",
        displayName = "Analog Classic",
        isDigital = false,
        description = "Traditional 12-hour analog clock with hour and minute hands"
    ),
    ANALOG_MODERN(
        id = "analog_modern",
        displayName = "Analog Modern",
        isDigital = false,
        description = "Sleek frameless analog style with smooth continuous second hand"
    )
}
