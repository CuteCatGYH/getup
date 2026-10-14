package com.getup.oneui.ui.console

import com.getup.oneui.model.BatteryDisplayOption
import com.getup.oneui.model.ClockStyle
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.model.SloganConfig
import com.getup.oneui.model.WallpaperOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainConsoleUiTest {

    @Test
    fun testPreviewAspectRatios() {
        val square = PreviewAspectRatio.SQUARE
        assertEquals(1.0f, square.ratio, 0.001f)
        assertEquals("1:1 Cover", square.label)

        val landscape = PreviewAspectRatio.LANDSCAPE_16_9
        assertEquals(16f / 9f, landscape.ratio, 0.001f)
        assertEquals("16:9 Monitor", landscape.label)

        val portrait = PreviewAspectRatio.PORTRAIT_4_3
        assertEquals(4f / 3f, portrait.ratio, 0.001f)
        assertEquals("4:3 Display", portrait.label)
    }

    @Test
    fun testConsoleStateUpdateCycle() {
        var state = SecondaryDisplayConfig()

        // 1. Clock style updates
        state = state.copy(clockStyle = ClockStyle.DIGITAL_NEON, is24HourFormat = false, showSeconds = false)
        assertEquals(ClockStyle.DIGITAL_NEON, state.clockStyle)
        assertFalse(state.is24HourFormat)
        assertFalse(state.showSeconds)

        // 2. Slogan updates
        val newSlogan = SloganConfig(
            text = "Notice: Meeting in Progress",
            fontSizeSp = 28,
            textColorHex = 0xFFFFD700,
            alignment = SloganConfig.TextAlignment.LEFT,
            isMarquee = true,
            isVisible = true
        )
        state = state.copy(sloganConfig = newSlogan)
        assertEquals("Notice: Meeting in Progress", state.sloganConfig.text)
        assertEquals(28, state.sloganConfig.fontSizeSp)
        assertEquals(SloganConfig.TextAlignment.LEFT, state.sloganConfig.alignment)
        assertTrue(state.sloganConfig.isMarquee)

        // 3. Wallpaper updates
        val sunsetGradient = WallpaperOption.DEFAULT_GRADIENTS[1]
        state = state.copy(wallpaper = sunsetGradient, brightnessLevel = 0.75f)
        assertEquals("grad_sunset", state.wallpaper.id)
        assertEquals(0.75f, state.brightnessLevel, 0.001f)

        // 4. Battery settings updates
        val circularBattery = BatteryDisplayOption(
            isVisible = true,
            showPercentage = true,
            style = BatteryDisplayOption.BatteryStyle.CIRCULAR,
            lowBatteryThreshold = 15
        )
        state = state.copy(batteryOption = circularBattery)
        assertEquals(BatteryDisplayOption.BatteryStyle.CIRCULAR, state.batteryOption.style)
        assertEquals(15, state.batteryOption.lowBatteryThreshold)

        // 5. Presentation toggling
        state = state.copy(isPresentationEnabled = false, targetDisplayId = 4)
        assertFalse(state.isPresentationEnabled)
        assertEquals(4, state.targetDisplayId)
    }
}
