package com.getup.oneui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataModelsTest {

    @Test
    fun testClockStyleEnum() {
        val styles = ClockStyle.entries
        assertEquals(5, styles.size)

        val boldStyle = ClockStyle.DIGITAL_BOLD
        assertEquals("digital_bold", boldStyle.id)
        assertEquals("Digital Bold", boldStyle.displayName)
        assertTrue(boldStyle.isDigital)

        val analogStyle = ClockStyle.ANALOG_CLASSIC
        assertFalse(analogStyle.isDigital)
    }

    @Test
    fun testWallpaperOptionPresets() {
        val defaultGradients = WallpaperOption.DEFAULT_GRADIENTS
        assertTrue(defaultGradients.isNotEmpty())

        val midnightGradient = defaultGradients[0]
        assertEquals("grad_midnight", midnightGradient.id)
        assertEquals("Midnight Blue", midnightGradient.name)

        val customImage = WallpaperOption.CustomImage(imageUri = "content://media/external/images/1")
        assertEquals("custom_image", customImage.id)
        assertEquals("content://media/external/images/1", customImage.imageUri)
    }

    @Test
    fun testSloganConfigDefaultsAndCopy() {
        val defaultConfig = SloganConfig()
        assertEquals("Welcome to Dual Screen Console", defaultConfig.text)
        assertTrue(defaultConfig.isVisible)
        assertFalse(defaultConfig.isMarquee)
        assertEquals(SloganConfig.TextAlignment.CENTER, defaultConfig.alignment)

        val modifiedConfig = defaultConfig.copy(text = "Hello Dual Screen", isMarquee = true)
        assertEquals("Hello Dual Screen", modifiedConfig.text)
        assertTrue(modifiedConfig.isMarquee)
    }

    @Test
    fun testBatteryDisplayOptionDefaults() {
        val defaultBattery = BatteryDisplayOption()
        assertTrue(defaultBattery.isVisible)
        assertTrue(defaultBattery.showPercentage)
        assertEquals(BatteryDisplayOption.BatteryStyle.HORIZONTAL, defaultBattery.style)
        assertEquals(20, defaultBattery.lowBatteryThreshold)
    }

    @Test
    fun testSecondaryDisplayConfigDefaults() {
        val config = SecondaryDisplayConfig()
        assertEquals(ClockStyle.DIGITAL_BOLD, config.clockStyle)
        assertNotNull(config.wallpaper)
        assertEquals("Welcome to Dual Screen Console", config.sloganConfig.text)
        assertTrue(config.batteryOption.isVisible)
        assertTrue(config.isPresentationEnabled)
        assertTrue(config.showSeconds)
        assertTrue(config.is24HourFormat)
        assertEquals(1.0f, config.brightnessLevel, 0.001f)
    }
}
