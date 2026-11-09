package io.vroomi.character.orbit

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

class OrbitTest {

    private class Fly(val sim: OrbitSim = OrbitSim()) {
        var now = 1_000_000L; var km = 0f; var stars = 0; var combo = 0
        fun run(seconds: Float, kmh: Float, gear: GearState = GearState.DRIVE, turn: TurnSignal = TurnSignal.NONE, each: () -> Unit = {}) {
            repeat((seconds * 60).toInt()) {
                km += kmh / 3600f / 60f
                val c = CarSignals(speedKmh = kmh, gear = gear, moving = kmh > 0.5f, turnSignal = turn)
                sim.step(1f / 60f, CharacterTarget(kart = KartState(driving = kmh > 0.5f), car = c,
                    journey = Journey(active = true, tripSeq = 1, tripKm = km, stars = stars, starSeq = stars, comboSeq = combo, milestoneKm = km.toInt())), now, 14f)
                now += 1000L / 60
                each()
            }
        }
    }

    @Test fun keplerPeriodRatios() {
        for (l in 1 until OrbitSim.LEVELS) {
            val expected = OrbitSim.radiusRatio(l).pow(1.5)
            assertEquals(expected, OrbitSim.period(l) / OrbitSim.period(0), expected * 0.005)
        }
        // And the moonlets actually move at those periods.
        val f = Fly(); f.run(1f, 30f)
        f.stars = 1; f.run(0.5f, 30f); f.stars = 2; f.run(0.5f, 30f)
        assertEquals(2, f.sim.moonlets)
        val a0 = f.sim.moonAngle.copyOf()
        f.run(10f, 30f)
        val w0 = (f.sim.moonAngle[0] - a0[0]) / 10.0; val w1 = (f.sim.moonAngle[1] - a0[1]) / 10.0
        assertEquals(OrbitSim.radiusRatio(1).pow(1.5), w0 / w1, 0.01)
    }

    @Test fun oneLapPerKmAndRetrogradeInReverse() {
        val f = Fly()
        val start = f.sim.angle
        f.run(60f, 60f) // 1 km
        assertEquals(2 * PI, f.sim.angle - start, 0.05)
        f.run(1f, 60f)
        assertEquals(1, f.sim.laps)
        val before = f.sim.angle
        f.run(20f, 6f, GearState.REVERSE)
        assertTrue("retrograde", f.sim.angle < before)
        // Speed is capped: 240 km/h for a minute is still at most 2 laps.
        val g = Fly(); g.run(60f, 240f)
        assertTrue(g.sim.angle - OrbitSim.STATION <= 2 * 2 * PI + 0.05)
    }

    @Test fun moonletCapAndParkDocks() {
        val f = Fly(); f.stars = 30; f.run(1f, 40f)
        // One moonlet per new star event; the cap holds.
        repeat(20) { f.stars++; f.run(0.2f, 40f) }
        assertTrue(f.sim.moonlets <= OrbitSim.MAX_MOONLETS)
        f.run(15f, 0f, GearState.PARK)
        assertTrue(f.sim.docked > 0.9f)
    }

    @Test fun rcsAtMostThreeHzAndMeteorsRare() {
        val f = Fly()
        var puffs = 0; var last = -1.0
        f.run(10f, 50f, turn = TurnSignal.LEFT) { if (f.sim.rcsAt != last) { puffs++; last = f.sim.rcsAt } }
        assertTrue("rcs puffs $puffs in 10 s", puffs <= 16)
        var meteors = 0; var lastM = -100.0
        f.run(60f, 50f) {} // dry
        val g = Fly()
        g.run(60f, 50f, each = {})
        repeat(1) {
            val h = Fly()
            repeat(60 * 60) { _ ->
                h.sim.step(1f / 60f, CharacterTarget(car = CarSignals(speedKmh = 50f, gear = GearState.DRIVE, moving = true, wiperActive = true),
                    journey = Journey(active = true, tripSeq = 1)), h.now, 14f)
                h.now += 16
                if (h.sim.meteorAt != lastM) { meteors++; lastM = h.sim.meteorAt }
            }
        }
        assertTrue("meteors $meteors per minute", meteors in 10..31)
    }
}
