package com.getup.oneui.display

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostureSettleGateTest {

    private val openedMs = 600L
    private val transitionalMs = 900L

    private fun gate(scope: TestScope, provider: () -> FoldPosture?) = PostureSettleGate(
        scope = scope,
        openedSettleMs = openedMs,
        transitionalSettleMs = transitionalMs,
        postureProvider = provider
    )

    @Test
    fun `opened posture settles after the opened window and is usable`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var provider: FoldPosture? = FoldPosture.OPENED
        var fired = 0
        val g = gate(scope) { provider }

        g.onPosture(FoldPosture.OPENED) { fired++ }
        assertFalse(g.isUsable)

        advanceTimeBy(openedMs - 1)
        assertFalse(g.isUsable)

        advanceTimeBy(1)
        advanceUntilIdle()
        assertTrue(g.isUsable)
        assertEquals(FoldPosture.OPENED, g.settledPosture)
        assertEquals(1, fired)
    }

    @Test
    fun `resting half-open posture settles after the longer transitional window and is usable`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var provider: FoldPosture? = FoldPosture.TRANSITIONAL
        var fired = 0
        val g = gate(scope) { provider }

        g.onPosture(FoldPosture.TRANSITIONAL) { fired++ }

        // Not trusted at the (shorter) opened window.
        advanceTimeBy(openedMs)
        assertFalse(g.isUsable)

        // Trusted once the longer transitional window elapses.
        advanceTimeBy(transitionalMs - openedMs)
        advanceUntilIdle()
        assertTrue(g.isUsable)
        assertEquals(FoldPosture.TRANSITIONAL, g.settledPosture)
        assertEquals(1, fired)
    }

    @Test
    fun `transient half-open pass-through during a fold never becomes usable`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        // The hinge passes through HALF_OPENED for ~400ms, then the device folds.
        var provider: FoldPosture? = FoldPosture.TRANSITIONAL
        var fired = 0
        val g = gate(scope) { provider }

        g.onPosture(FoldPosture.TRANSITIONAL) { fired++ }
        advanceTimeBy(400)
        provider = FoldPosture.CLOSED
        g.onPosture(FoldPosture.CLOSED) { fired++ }

        advanceUntilIdle()
        assertFalse(g.isUsable)
        assertEquals(FoldPosture.CLOSED, g.settledPosture)
    }

    @Test
    fun `transient opened blip mid-fold never becomes usable`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var provider: FoldPosture? = FoldPosture.OPENED
        var fired = 0
        val g = gate(scope) { provider }

        g.onPosture(FoldPosture.OPENED) { fired++ }
        advanceTimeBy(30) // ~30ms blip (README 缺陷 A), then fold.
        provider = FoldPosture.CLOSED
        g.onPosture(FoldPosture.CLOSED) { fired++ }

        advanceUntilIdle()
        assertFalse(g.isUsable)
        assertEquals(FoldPosture.CLOSED, g.settledPosture)
    }

    @Test
    fun `closed is immediately settled and never usable`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var provider: FoldPosture? = FoldPosture.CLOSED
        var fired = 0
        val g = gate(scope) { provider }

        g.onPosture(FoldPosture.CLOSED) { fired++ }
        assertFalse(g.isUsable)
        assertEquals(FoldPosture.CLOSED, g.settledPosture)
        assertEquals(1, fired)
    }

    @Test
    fun `unresolved posture resets the verdict`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var provider: FoldPosture? = FoldPosture.TRANSITIONAL
        var fired = 0
        val g = gate(scope) { provider }

        g.onPosture(FoldPosture.TRANSITIONAL) { fired++ }
        advanceTimeBy(100)
        provider = null
        g.onPosture(null) { fired++ }

        advanceUntilIdle()
        assertFalse(g.isUsable)
        assertEquals(null, g.settledPosture)
    }

    @Test
    fun `re-verification failure aborts the settled verdict`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        // Signal claims transitional, but the authoritative re-check reads closed again.
        var provider: FoldPosture? = FoldPosture.CLOSED
        var fired = 0
        val g = gate(scope) { provider }

        g.onPosture(FoldPosture.TRANSITIONAL) { fired++ }
        advanceTimeBy(transitionalMs)
        advanceUntilIdle()

        assertFalse(g.isUsable)
        assertEquals(0, fired)
    }

    @Test
    fun `repeated signal for an already settled posture does not re-fire`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var provider: FoldPosture? = FoldPosture.OPENED
        var fired = 0
        val g = gate(scope) { provider }

        g.onPosture(FoldPosture.OPENED) { fired++ }
        advanceUntilIdle()
        assertEquals(1, fired)

        // A second identical signal must not re-trigger the callback.
        g.onPosture(FoldPosture.OPENED) { fired++ }
        advanceUntilIdle()
        assertEquals(1, fired)
    }

    @Test
    fun `recheck fires even when the settled posture is unchanged (screen-on reopening)`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        // Device has been continuously OPENED the whole time (e.g. screen-off then unlock).
        val provider: FoldPosture? = FoldPosture.OPENED
        var fired = 0
        val g = gate(scope) { provider }

        // First settle: fires once.
        g.onPosture(FoldPosture.OPENED) { fired++ }
        advanceUntilIdle()
        assertEquals(1, fired)

        // Regression: a plain re-signal is (correctly) suppressed because the verdict is
        // unchanged...
        g.onPosture(FoldPosture.OPENED) { fired++ }
        advanceUntilIdle()
        assertEquals(1, fired)

        // ...but an explicit recheck MUST fire, so the cover display can be re-presented
        // after it was dismissed on screen-off.
        g.recheck(FoldPosture.OPENED) { fired++ }
        advanceUntilIdle()
        assertEquals(2, fired)
        assertTrue(g.isUsable)
        assertEquals(FoldPosture.OPENED, g.settledPosture)
    }

    @Test
    fun `recheck still requires the settle window and re-verification`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var provider: FoldPosture? = FoldPosture.OPENED
        var fired = 0
        val g = gate(scope) { provider }

        g.recheck(FoldPosture.OPENED) { fired++ }
        // Not fired immediately: still debounced.
        assertFalse(g.isUsable)
        assertEquals(0, fired)

        // Folds mid-window: the forced recheck must abort.
        advanceTimeBy(openedMs / 2)
        provider = FoldPosture.CLOSED

        advanceUntilIdle()
        assertEquals(0, fired)
        assertFalse(g.isUsable)
    }

    @Test
    fun `recheck on a closed posture enforces the closed verdict`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var provider: FoldPosture? = FoldPosture.CLOSED
        var fired = 0
        val g = gate(scope) { provider }

        g.recheck(FoldPosture.CLOSED) { fired++ }
        advanceUntilIdle()
        assertEquals(1, fired)
        assertFalse(g.isUsable)
        assertEquals(FoldPosture.CLOSED, g.settledPosture)
    }
}
