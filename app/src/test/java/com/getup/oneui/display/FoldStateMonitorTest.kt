package com.getup.oneui.display

import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoldStateMonitorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var foldStateMonitor: FoldStateMonitor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        foldStateMonitor = FoldStateMonitor()
    }

    @After
    fun tearDown() {
        foldStateMonitor.destroy()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        assertFalse(foldStateMonitor.isDeviceUnfolded.value)
        assertNull(foldStateMonitor.foldingState.value)
    }

    @Test
    fun testManualUnfoldedSetting() {
        foldStateMonitor.setDeviceUnfoldedForTest(true)
        assertTrue(foldStateMonitor.isDeviceUnfolded.value)

        foldStateMonitor.setDeviceUnfoldedForTest(false)
        assertFalse(foldStateMonitor.isDeviceUnfolded.value)
    }

    @Test
    fun testProcessLayoutInfoWithoutFoldingFeatures() {
        val emptyLayoutInfo = WindowLayoutInfo(emptyList())
        foldStateMonitor.processLayoutInfo(emptyLayoutInfo)

        assertFalse(foldStateMonitor.isDeviceUnfolded.value)
        assertNull(foldStateMonitor.foldingState.value)
    }
}
