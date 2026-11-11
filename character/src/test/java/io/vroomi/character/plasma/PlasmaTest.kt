package io.vroomi.character.plasma

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlasmaTest {

    private fun car(kmh: Float, turn: TurnSignal = TurnSignal.NONE, brake: Boolean = false, safety: Boolean = false, accel: Float = 0f) =
        CarSignals(speedKmh = kmh, accelMps2 = accel, gear = GearState.DRIVE, moving = kmh > 0.5f, turnSignal = turn, brakePressed = brake,
            safetyWarningActive = safety, fanLevel = 2, fanMax = 8)

    private fun target(c: CarSignals, km: Float, stars: Int = 0, combo: Int = 0) =
        CharacterTarget(kart = KartState(driving = c.moving), car = c, journey = Journey(active = true, tripSeq = 1, tripKm = km, stars = stars, starSeq = stars, comboSeq = combo, milestoneKm = km.toInt()))

    /** A steady drive for [seconds]; [each] sees the simulation after every frame. */
    private fun run(kmh: Float, turn: TurnSignal = TurnSignal.NONE, brake: Boolean = false, seconds: Float = 8f, sim: PlasmaSim = PlasmaSim(), each: (PlasmaSim) -> Unit = {}): PlasmaSim {
        val frames = (seconds * 60).toInt()
        for (i in 0 until frames) {
            val t = i / 60f
            sim.step(1f / 60f, target(car(kmh, turn, brake), kmh * t / 3600f, stars = (t / 4f).toInt()), 1_000_000L + i * 16L, 14f)
            each(sim)
        }
        return sim
    }

    @Test fun filamentsCapAtSprint() {
        assertEquals(run(kmh = 110f).filaments, run(kmh = 180f).filaments)
        assertTrue(run(kmh = 110f).filaments <= PlasmaSim.MAX_FILAMENTS)
        assertEquals(PlasmaSim.MAX_FILAMENTS, run(kmh = 110f).filaments)
        assertTrue(run(kmh = 55f).filaments in 8..10)
    }

    @Test fun stoppedHasFour() { assertEquals(4, run(kmh = 0f).filaments) }

    @Test fun leftBlinkerGathersLeft() {
        assertTrue(run(kmh = 40f, turn = TurnSignal.LEFT).gather < -0.5f)
        assertTrue(run(kmh = 40f, turn = TurnSignal.RIGHT).gather > 0.5f)
        assertEquals(0f, run(kmh = 40f).gather, 0.001f)
    }

    @Test fun brakeShrinks() {
        val s = run(kmh = 40f, brake = true)
        assertTrue(s.reach < 0.7f)
        assertTrue(s.braking > 0.9f)
        assertEquals(1f, run(kmh = 40f).reach, 0.001f)
    }

    @Test fun flickerStaysWithin15Percent() {
        var lo = 1f; var hi = 0f
        run(kmh = 70f, seconds = 30f) { lo = minOf(lo, it.flicker); hi = maxOf(hi, it.flicker) }
        assertTrue("min $lo", lo >= 0.85f)
        assertTrue("max $hi", hi <= 1.0f)
        assertTrue("it breathes: $lo..$hi", hi - lo > 0.05f)
    }

    @Test fun noMoreThanThreePulsesPerSecond() {
        val sim = PlasmaSim()
        val pulses = HashMap<Int, ArrayList<Double>>()
        sim.flash.recorder = { region, time -> pulses.getOrPut(region) { ArrayList() }.add(time) }
        var touches = 0; var last = -100.0
        run(kmh = 60f, turn = TurnSignal.LEFT, seconds = 30f, sim = sim) { if (it.touchAt != last) { last = it.touchAt; touches++ } }
        assertTrue("the blinker leaves touch points: $touches", touches >= 20)
        assertTrue(pulses.isNotEmpty())
        for ((region, times) in pulses) for (i in 3 until times.size) assertTrue("region $region", times[i] - times[i - 3] > 1.0)
    }

    @Test fun ladderResetsEachKm() {
        val sim = PlasmaSim()
        fun at(km: Float): Float { sim.step(1f / 60f, target(car(60f), km), 1_000_000L, 14f); return sim.ladder }
        assertTrue(at(0.5f) in 0.45f..0.55f)
        assertTrue(at(0.98f) > 0.9f)
        assertTrue(at(1.02f) < 0.1f)
        assertEquals(1020, sim.odometerM)
    }

    @Test fun safetyHoldsTheFilamentsAndDimsThem() {
        val sim = run(kmh = 60f)
        val before = sim.phase
        for (i in 0 until 180) sim.step(1f / 60f, target(car(60f, safety = true), 0.2f), 2_000_000L + i * 16L, 14f)
        val held = sim.phase
        for (i in 0 until 60) sim.step(1f / 60f, target(car(60f, safety = true), 0.2f), 2_100_000L + i * 16L, 14f)
        assertTrue(held > before)            // it eased to a stop
        assertEquals(held, sim.phase, 1e-6)  // and stands still while the warning lasts
        assertTrue(sim.reach < 0.7f)
    }

    @Test fun chipsAreHonest() {
        val sim = PlasmaSim()
        assertEquals(listOf("전류가 일렁여요", "왼쪽으로 모여요", "오른쪽으로 모여요", "전류가 움츠러들어요", "빛점이 남았어요"), sim.allChips)
        for (c in sim.allChips) for (w in ThemeSim.FORBIDDEN_WORDS) assertTrue("$c / $w", !c.contains(w))
        assertEquals(PlasmaSim.CHIP_LEFT, run(kmh = 40f, turn = TurnSignal.LEFT, seconds = 6f).chip)
    }
}
