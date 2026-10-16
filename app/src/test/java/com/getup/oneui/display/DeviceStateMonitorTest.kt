package com.getup.oneui.display

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceStateMonitorTest {

    private lateinit var monitor: DeviceStateMonitor

    @Before
    fun setUp() {
        val dummyContext = object : ContextWrapper(null) {}
        monitor = DeviceStateMonitor(dummyContext)
    }

    @Test
    fun testInitialState() {
        assertNull(monitor.currentState.value)
        assertFalse(monitor.isUnfolded.value)
    }

    @Test
    fun testTransitionToClosed() {
        monitor.setStateForTest(DeviceStateMonitor.STATE_CLOSED)
        assertEquals(DeviceStateMonitor.STATE_CLOSED, monitor.currentState.value)
        assertFalse(monitor.isUnfolded.value)
    }

    @Test
    fun testTransitionToHalfOpened() {
        // HALF_OPENED is a transient hinge intermediate (occurs during both folding and
        // unfolding), so it must NOT be treated as a stable unfolded state.
        monitor.setStateForTest(DeviceStateMonitor.STATE_HALF_OPENED)
        assertEquals(DeviceStateMonitor.STATE_HALF_OPENED, monitor.currentState.value)
        assertFalse(monitor.isUnfolded.value)
    }

    @Test
    fun testTransitionToTentIsNotUnfolded() {
        monitor.setStateForTest(DeviceStateMonitor.STATE_TENT)
        assertEquals(DeviceStateMonitor.STATE_TENT, monitor.currentState.value)
        assertFalse(monitor.isUnfolded.value)
    }

    @Test
    fun testOnlyOpenedAndConcurrentAreUnfolded() {
        // Genuine steady unfolded states.
        monitor.setStateForTest(DeviceStateMonitor.STATE_OPENED)
        assertTrue(monitor.isUnfolded.value)

        monitor.setStateForTest(DeviceStateMonitor.STATE_CONCURRENT)
        assertTrue(monitor.isUnfolded.value)

        // Transient / folded states must all report not-unfolded.
        monitor.setStateForTest(DeviceStateMonitor.STATE_HALF_OPENED)
        assertFalse(monitor.isUnfolded.value)
        monitor.setStateForTest(DeviceStateMonitor.STATE_TENT)
        assertFalse(monitor.isUnfolded.value)
        monitor.setStateForTest(DeviceStateMonitor.STATE_CLOSED)
        assertFalse(monitor.isUnfolded.value)
    }

    @Test
    fun testTransitionToOpened() {
        monitor.setStateForTest(DeviceStateMonitor.STATE_OPENED)
        assertEquals(DeviceStateMonitor.STATE_OPENED, monitor.currentState.value)
        assertTrue(monitor.isUnfolded.value)
    }

    @Test
    fun testTransitionToConcurrent() {
        monitor.setStateForTest(DeviceStateMonitor.STATE_CONCURRENT)
        assertEquals(DeviceStateMonitor.STATE_CONCURRENT, monitor.currentState.value)
        assertTrue(monitor.isUnfolded.value)
    }

    @Test
    fun testOnStateChangedListenerInvocation() {
        var capturedOldState: Int? = null
        var capturedNewState: Int? = null

        monitor.onStateChangedListener = { old, new ->
            capturedOldState = old
            capturedNewState = new
        }

        // Transition from initial null to OPENED (3)
        monitor.setStateForTest(DeviceStateMonitor.STATE_OPENED)
        assertNull(capturedOldState)
        assertEquals(DeviceStateMonitor.STATE_OPENED, capturedNewState)

        // Transition from OPENED (3) to CONCURRENT (4)
        monitor.setStateForTest(DeviceStateMonitor.STATE_CONCURRENT)
        assertEquals(DeviceStateMonitor.STATE_OPENED, capturedOldState)
        assertEquals(DeviceStateMonitor.STATE_CONCURRENT, capturedNewState)

        // Samsung system notification [关闭] clicked: Transition from CONCURRENT (4) to OPENED (3)
        monitor.setStateForTest(DeviceStateMonitor.STATE_OPENED)
        assertEquals(DeviceStateMonitor.STATE_CONCURRENT, capturedOldState)
        assertEquals(DeviceStateMonitor.STATE_OPENED, capturedNewState)

        // Device physically folded: Transition from OPENED (3) to CLOSED (0)
        monitor.setStateForTest(DeviceStateMonitor.STATE_CLOSED)
        assertEquals(DeviceStateMonitor.STATE_OPENED, capturedOldState)
        assertEquals(DeviceStateMonitor.STATE_CLOSED, capturedNewState)
        assertFalse(monitor.isUnfolded.value)
    }

    @Test
    fun testIsDeviceUnfoldedHelperSafeFallback() {
        assertFalse(DeviceStateMonitor.isDeviceUnfolded(null))
        val dummyContext = object : ContextWrapper(null) {}
        assertFalse(DeviceStateMonitor.isDeviceUnfolded(dummyContext))
    }

    @Test
    fun testSecondaryDisplayCountSafeFallback() {
        // A context without a DisplayManager service must never throw.
        assertEquals(0, DeviceStateMonitor.getSecondaryDisplayCount(null))
        val dummyContext = object : ContextWrapper(null) {}
        assertEquals(0, DeviceStateMonitor.getSecondaryDisplayCount(dummyContext))
        assertFalse(DeviceStateMonitor.hasSecondaryDisplay(dummyContext))
    }

    @Test
    fun testIsSafeToPresentOnSecondaryDisplayIsConservative() {
        // Null and service-less contexts must resolve to "not safe" so we never render
        // onto the wrong display and crash.
        assertFalse(DeviceStateMonitor.isSafeToPresentOnSecondaryDisplay(null))
        val dummyContext = object : ContextWrapper(null) {}
        assertFalse(DeviceStateMonitor.isSafeToPresentOnSecondaryDisplay(dummyContext))
    }

    @Test
    fun testCurrentStateStartsUnresolved() {
        // The DeviceStateManager callback has not fired yet: the authoritative state must be
        // null so callers (e.g. the CoverDisplayService debounce) treat it as "not unfolded"
        // rather than falling back to an unreliable geometric heuristic.
        assertNull(monitor.currentState.value)
        assertFalse(monitor.isUnfolded.value)
    }

    @Test
    fun testFoldTransitionDirectlyFromOpenedIsImmediatelyNotUnfolded() {
        // Reproduces the fold transition the CoverDisplayService debounce must survive:
        // the device goes OPENED -> CLOSED, and the authoritative state must report
        // "not unfolded" on the CLOSED callback (even if a geometric heuristic still reads
        // the display as unfolded for ~30ms — README 缺陷 A).
        monitor.setStateForTest(DeviceStateMonitor.STATE_OPENED)
        assertTrue(monitor.isUnfolded.value)

        monitor.setStateForTest(DeviceStateMonitor.STATE_CLOSED)
        assertEquals(DeviceStateMonitor.STATE_CLOSED, monitor.currentState.value)
        assertFalse(monitor.isUnfolded.value)
    }

    @Test
    fun testUnfoldTransitionPassesThroughTransientHalfOpened() {
        // A genuine unfold is reported as CLOSED -> ... HALF_OPENED/TENT ... -> OPENED.
        // The transient intermediates must never be mistaken for a genuine unfold, so a
        // consumer that only acts on the steady OPENED state will not fire prematurely.
        monitor.setStateForTest(DeviceStateMonitor.STATE_CLOSED)
        assertFalse(monitor.isUnfolded.value)

        monitor.setStateForTest(DeviceStateMonitor.STATE_HALF_OPENED)
        assertFalse(monitor.isUnfolded.value)

        monitor.setStateForTest(DeviceStateMonitor.STATE_TENT)
        assertFalse(monitor.isUnfolded.value)

        monitor.setStateForTest(DeviceStateMonitor.STATE_OPENED)
        assertTrue(monitor.isUnfolded.value)
    }
}
