package com.getup.oneui.viewmodel

import com.getup.oneui.model.BatteryDisplayOption
import com.getup.oneui.model.ClockStyle
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.model.SloganConfig
import com.getup.oneui.model.WallpaperOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DualScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: DualScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        com.getup.oneui.model.DualScreenConfigRepository.setConfig(SecondaryDisplayConfig())
        viewModel = DualScreenViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() {
        val initialState = viewModel.uiState.value
        assertEquals(ClockStyle.DIGITAL_BOLD, initialState.clockStyle)
        assertTrue(initialState.isPresentationEnabled)
        assertEquals("Welcome to Dual Screen Console", initialState.sloganConfig.text)
    }

    @Test
    fun testUpdateClockStyle() {
        viewModel.updateClockStyle(ClockStyle.ANALOG_CLASSIC)
        assertEquals(ClockStyle.ANALOG_CLASSIC, viewModel.uiState.value.clockStyle)
    }

    @Test
    fun testUpdateWallpaper() {
        val newWallpaper = WallpaperOption.DEFAULT_COLORS[0]
        viewModel.updateWallpaper(newWallpaper)
        assertEquals(newWallpaper, viewModel.uiState.value.wallpaper)
    }

    @Test
    fun testUpdateSloganText() {
        val newSlogan = "Special Announcement!"
        viewModel.updateSloganText(newSlogan)
        assertEquals(newSlogan, viewModel.uiState.value.sloganConfig.text)
    }

    @Test
    fun testUpdateSloganConfig() {
        val newConfig = SloganConfig(
            text = "New Slogan",
            textColorHex = 0xFFFF0000,
            fontSizeSp = 36,
            isMarquee = true
        )
        viewModel.updateSloganConfig(newConfig)
        assertEquals(newConfig, viewModel.uiState.value.sloganConfig)
    }

    @Test
    fun testUpdateBatteryOption() {
        val newBatteryOption = BatteryDisplayOption(
            isVisible = true,
            showPercentage = false,
            style = BatteryDisplayOption.BatteryStyle.CIRCULAR
        )
        viewModel.updateBatteryOption(newBatteryOption)
        assertEquals(newBatteryOption, viewModel.uiState.value.batteryOption)
    }

    @Test
    fun testTogglePresentation() {
        viewModel.togglePresentation(false)
        assertFalse(viewModel.uiState.value.isPresentationEnabled)

        viewModel.togglePresentation(true)
        assertTrue(viewModel.uiState.value.isPresentationEnabled)
    }

    @Test
    fun testSelectTargetDisplay() {
        viewModel.selectTargetDisplay(2)
        assertEquals(2, viewModel.uiState.value.targetDisplayId)
    }

    @Test
    fun testUpdateBrightness() {
        viewModel.updateBrightness(0.5f)
        assertEquals(0.5f, viewModel.uiState.value.brightnessLevel, 0.001f)

        // Test coercing limits
        viewModel.updateBrightness(1.5f)
        assertEquals(1.0f, viewModel.uiState.value.brightnessLevel, 0.001f)

        viewModel.updateBrightness(0.01f)
        assertEquals(0.1f, viewModel.uiState.value.brightnessLevel, 0.001f)
    }

    @Test
    fun testResetToDefaults() {
        viewModel.updateClockStyle(ClockStyle.ANALOG_MODERN)
        viewModel.updateSloganText("Custom Text")
        viewModel.togglePresentation(false)

        viewModel.resetToDefaults()

        val resetState = viewModel.uiState.value
        assertEquals(ClockStyle.DIGITAL_BOLD, resetState.clockStyle)
        assertEquals("Welcome to Dual Screen Console", resetState.sloganConfig.text)
        assertTrue(resetState.isPresentationEnabled)
    }

    @Test
    fun testAutoStartOnUnfoldPreference() {
        // Initial state should be true (or default configured)
        assertTrue(viewModel.autoStartOnUnfold.value)

        // User toggles off "展开大屏时自动开启"
        viewModel.setAutoStartOnUnfoldForTest(false)
        assertFalse(viewModel.autoStartOnUnfold.value)

        // User toggles back on
        viewModel.setAutoStartOnUnfoldForTest(true)
        assertTrue(viewModel.autoStartOnUnfold.value)
    }

    @Test
    fun testFoldStateMonitorIntegration() {
        assertNotNull(viewModel.foldStateMonitor)
        assertFalse(viewModel.isDeviceUnfolded.value)

        viewModel.foldStateMonitor.setDeviceUnfoldedForTest(true)
        assertTrue(viewModel.isDeviceUnfolded.value)

        viewModel.foldStateMonitor.setDeviceUnfoldedForTest(false)
        assertFalse(viewModel.isDeviceUnfolded.value)
    }

    @Test
    fun testWindowAreaHelperIntegration() {
        assertNotNull(viewModel.windowAreaHelper)
        assertFalse(viewModel.isRearDisplayActive.value)
        assertFalse(viewModel.isRearScreenActive.value)
    }

    @Test
    fun testKeepScreenOnPreference() {
        // Initial state should be true (default)
        assertTrue(viewModel.keepScreenOn.value)

        // Toggle keep screen on off
        viewModel.setKeepScreenOnForTest(false)
        assertFalse(viewModel.keepScreenOn.value)

        // Toggle back on
        viewModel.setKeepScreenOnForTest(true)
        assertTrue(viewModel.keepScreenOn.value)
    }

    @Test
    fun testShizukuInitialState() {
        // Without binder or permission, authorized should be false
        assertFalse(viewModel.isShizukuAuthorized.value)
        assertFalse(viewModel.isCoverServiceRunning.value)
    }

    @Test
    fun testSharedConfigSync() {
        val testSlogan = "Keep Moving Forward"
        viewModel.updateSloganText(testSlogan)
        assertEquals(testSlogan, viewModel.uiState.value.sloganConfig.text)
        assertEquals(testSlogan, com.getup.oneui.model.DualScreenConfigRepository.config.value.sloganConfig.text)
    }

    @Test
    fun testAntiMisoperationWhenFolded() {
        // Device is folded by default
        assertFalse(viewModel.isDeviceUnfolded.value)

        // Attempting to toggle Shizuku service while folded should be prohibited
        val dummyContext = object : android.content.ContextWrapper(null) {}
        viewModel.toggleShizukuService(dummyContext, true)

        // Configuration presentation should NOT be enabled because device is folded
        assertFalse(viewModel.isRearScreenActive.value)
    }
}
