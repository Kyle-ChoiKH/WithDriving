package io.vroomi.character.theme

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeKitTest {

    @Test fun flashGuardNeverAllowsFourPulsesInOneSecond() {
        val g = FlashGuard()
        val times = ArrayList<Double>()
        g.recorder = { r, t -> if (r == 3) times += t }
        var t = 0.0
        while (t < 60.0) { g.allow(3, t); t += 1.0 / 120.0 }
        assertTrue(times.size in 170..180)
        for (i in 3 until times.size) assertTrue(times[i] - times[i - 3] >= 1.0)
        // Regions are independent.
        assertTrue(g.allow(4, 59.999))
    }

    @Test fun chipHolderKeepsEachChipFourSecondsAndClearsOnSafety() {
        val h = ChipHolder()
        assertEquals("a", h.update("a", 0.0, false))
        assertEquals("a", h.update("b", 3.9, false))
        assertEquals("b", h.update("b", 4.1, false))
        assertNull(h.update("b", 4.2, true))
        assertEquals("c", h.update("c", 4.3, false))
    }

    @Test fun fixedStepperRunsWholeSubsteps() {
        val s = FixedStepper(1.0 / 120.0)
        var n = 0
        repeat(60) { s.advance(1f / 60f) { n++ } }
        assertTrue(n in 119..121)
        n = 0
        s.advance(5f) { n++ } // a long hitch is clamped to 0.1 s
        assertTrue(n <= 13)
    }

    private class Probe : ThemeSim() {
        var starCount = 0; var harsh = 0; var blinks = 0; var km = 0
        override val allChips = listOf("주행", "정지")
        override fun advance(dt: Double, ddt: Double, nowMs: Long) {}
        override fun chooseChip(): String = if (moving) "주행" else "정지"
        override fun onStar() { starCount++ }
        override fun onHarsh() { harsh++ }
        override fun onBlink(side: TurnSignal) { blinks++ }
        override fun onMilestone(km: Int) { this.km = km }
    }

    @Test fun simReadsEventsOnceAndFreezesOnSafety() {
        val p = Probe()
        var j = Journey(active = true, tripSeq = 1)
        var c = CarSignals(speedKmh = 40f, gear = GearState.DRIVE, moving = true)
        var now = 0L
        fun run(s: Float) { repeat((s * 60).toInt()) { p.step(1f / 60f, CharacterTarget(car = c, journey = j), now, 14f); now += 16 } }
        run(1f)
        j = j.copy(starSeq = 1, stars = 1, milestoneKm = 1); c = c.copy(harshBrakeSeq = 1)
        run(1f)
        assertEquals(1, p.starCount); assertEquals(1, p.harsh); assertEquals(1, p.km)
        c = c.copy(turnSignal = TurnSignal.LEFT)
        run(4f)
        assertTrue("blinker at 1.5 Hz: ${p.blinks}", p.blinks in 5..7)
        val deco = p.decoTime
        c = c.copy(safetyWarningActive = true)
        run(2f)
        assertNull(p.chip)
        assertTrue(p.freeze > 0.99f)
        assertTrue("decoration stands still", p.decoTime - deco < 0.4)
        // Unknown speed is not zero: it is "unknown".
        c = CarSignals()
        run(1f)
        assertTrue(!p.speedKnown)
    }
}
