package com.getup.oneui.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldPostureTest {

    @Test
    fun `raw states map to the expected posture`() {
        assertEquals(FoldPosture.CLOSED, FoldPosture.fromRawState(DeviceStateMonitor.STATE_CLOSED))
        assertEquals(FoldPosture.TRANSITIONAL, FoldPosture.fromRawState(DeviceStateMonitor.STATE_TENT))
        assertEquals(FoldPosture.TRANSITIONAL, FoldPosture.fromRawState(DeviceStateMonitor.STATE_HALF_OPENED))
        assertEquals(FoldPosture.OPENED, FoldPosture.fromRawState(DeviceStateMonitor.STATE_OPENED))
        assertEquals(FoldPosture.OPENED, FoldPosture.fromRawState(DeviceStateMonitor.STATE_CONCURRENT))
    }

    @Test
    fun `unresolved or unknown states map to null`() {
        assertNull(FoldPosture.fromRawState(null))
        assertNull(FoldPosture.fromRawState(99))
    }

    @Test
    fun `closed is not usable, opened and transitional are`() {
        assertFalse(FoldPosture.CLOSED.isUsableForCoverDisplay)
        assertTrue(FoldPosture.OPENED.isUsableForCoverDisplay)
        assertTrue(FoldPosture.TRANSITIONAL.isUsableForCoverDisplay)
    }
}
