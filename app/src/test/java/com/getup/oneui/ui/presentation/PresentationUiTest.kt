package com.getup.oneui.ui.presentation

import com.getup.oneui.model.BatteryDisplayOption
import com.getup.oneui.model.ClockStyle
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.model.SloganConfig
import com.getup.oneui.model.WallpaperOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PresentationUiTest {

    @Test
    fun testClockTimeFormatting24HourWithSeconds() {
        // Epoch milli for 15:30:45 UTC or local
        val timeMs = 1700000000000L
        val localTime = Instant.ofEpochMilli(timeMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

        val pattern24WithSeconds = "HH:mm:ss"
        val formatter = DateTimeFormatter.ofPattern(pattern24WithSeconds)
        val formatted = localTime.format(formatter)

        assertNotNull(formatted)
        assertTrue(formatted.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun testClockTimeFormatting12Hour() {
        val time = LocalTime.of(14, 5, 9)
        val pattern12 = "hh:mm:ss"
        val formatter = DateTimeFormatter.ofPattern(pattern12)
        val formatted = time.format(formatter)

        assertEquals("02:05:09", formatted)
    }

    @Test
    fun testSecondaryDisplayConfigStateReactivity() {
        var config = SecondaryDisplayConfig()

        // Verify initial clock style
        assertEquals(ClockStyle.DIGITAL_BOLD, config.clockStyle)

        // Update clock style to Analog Modern
        config = config.copy(clockStyle = ClockStyle.ANALOG_MODERN)
        assertEquals(ClockStyle.ANALOG_MODERN, config.clockStyle)

        // Update Wallpaper
        val newWallpaper = WallpaperOption.DEFAULT_GRADIENTS[2] // Cyberpunk
        config = config.copy(wallpaper = newWallpaper)
        assertEquals("grad_cyberpunk", config.wallpaper.id)

        // Update Slogan
        config = config.copy(
            sloganConfig = SloganConfig(
                text = "Welcome to Cover Display",
                textColorHex = 0xFF00E5FF,
                isMarquee = true
            )
        )
        assertEquals("Welcome to Cover Display", config.sloganConfig.text)
        assertTrue(config.sloganConfig.isMarquee)

        // Update Battery option
        config = config.copy(
            batteryOption = BatteryDisplayOption(
                isVisible = true,
                style = BatteryDisplayOption.BatteryStyle.CIRCULAR,
                showPercentage = true
            )
        )
        assertEquals(BatteryDisplayOption.BatteryStyle.CIRCULAR, config.batteryOption.style)
    }

    @Test
    fun testWallpaperOptionTypes() {
        val gradient = WallpaperOption.PresetGradient(
            id = "test_grad",
            name = "Test Gradient",
            startColorHex = 0xFF000000,
            endColorHex = 0xFFFFFFFF,
            angleDegrees = 90f
        )
        assertEquals(90f, gradient.angleDegrees, 0.001f)

        val color = WallpaperOption.PresetColor(
            id = "test_color",
            name = "Test Color",
            colorHex = 0xFFFF0000
        )
        assertEquals(0xFFFF0000, color.colorHex)

        val custom = WallpaperOption.CustomImage(
            imageUri = "file:///sdcard/wallpaper.jpg"
        )
        assertEquals("file:///sdcard/wallpaper.jpg", custom.imageUri)
    }

    @Test
    fun testLowBatteryThresholdEvaluation() {
        val batteryOption = BatteryDisplayOption(lowBatteryThreshold = 20)

        val isLevel15Low = 15 <= batteryOption.lowBatteryThreshold
        assertTrue(isLevel15Low)

        val isLevel50Low = 50 <= batteryOption.lowBatteryThreshold
        assertFalse(isLevel50Low)
    }
}
