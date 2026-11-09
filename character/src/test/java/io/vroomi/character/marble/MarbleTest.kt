package io.vroomi.character.marble

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class MarbleTest {

    private class Machine(val sim: MarbleSim = MarbleSim()) {
        var now = 1_000_000L; var stars = 0; var arrival = 0
        fun run(seconds: Float, kmh: Float, brake: Boolean = false, turn: TurnSignal = TurnSignal.NONE, wiper: Boolean = false,
                gear: GearState = GearState.DRIVE, each: () -> Unit = {}) {
            repeat((seconds * 60).toInt()) {
                val c = CarSignals(speedKmh = kmh, gear = gear, moving = kmh > 0.5f, brakePressed = brake, turnSignal = turn, wiperActive = wiper, arrivalSeq = arrival)
                sim.step(1f / 60f, CharacterTarget(kart = KartState(driving = kmh > 0.5f), car = c, journey = Journey(active = true, tripSeq = 1, stars = stars, starSeq = stars)), now, 14f)
                now += 1000L / 60
                each()
            }
        }
    }

    @Test fun deterministic() {
        val a = Machine(); val b = Machine()
        a.run(40f, 60f); b.run(40f, 60f)
        for (i in 0 until MarbleSim.MAX) { assertEquals(a.sim.state[i], b.sim.state[i]); assertEquals(a.sim.pos[i], b.sim.pos[i], 0f) }
    }

    @Test fun steelCountIsConservedAndMarblesStayOnTheirRails() {
        val m = Machine()
        val p = FloatArray(2)
        m.run(90f, 70f, turn = TurnSignal.NONE) {
            var steel = 0
            for (i in 0 until MarbleSim.STEEL) if (m.sim.state[i] != MarbleSim.GONE && m.sim.state[i] != MarbleSim.TRAY) steel++
            assertEquals(MarbleSim.STEEL, steel)
            for (i in 0 until MarbleSim.MAX) if (m.sim.state[i] == MarbleSim.TRACK) {
                val t = m.sim.tracks[m.sim.rail[i]]
                t.point(m.sim.pos[i], p)
                // Distance to the rail's polyline (the drawn rail).
                var near = Float.MAX_VALUE
                for (k in 0 until t.xs.size - 1) near = minOf(near, io.vroomi.core.vehicle.route.RouteMath.segDist(p[0], p[1], t.xs[k], t.ys[k], t.xs[k + 1], t.ys[k + 1]))
                assertTrue("on its rail ($near)", near < 0.5f)
            }
        }
        assertTrue("marbles circulate", (0 until MarbleSim.MAX).count { m.sim.state[it] == MarbleSim.TRACK } >= 3)
    }

    @Test fun energyNeverRisesOnARail() {
        val m = Machine()
        val e = FloatArray(MarbleSim.MAX); val r = IntArray(MarbleSim.MAX) { -1 }; val st = IntArray(MarbleSim.MAX)
        var checked = 0
        m.run(60f, 60f) {
            for (i in 0 until MarbleSim.MAX) {
                val on = m.sim.state[i] == MarbleSim.TRACK
                if (on && st[i] == MarbleSim.TRACK && r[i] == m.sim.rail[i]) {
                    assertTrue("energy rose on rail ${r[i]}: ${e[i]} → ${m.sim.energy(i)}", m.sim.energy(i) <= e[i] + 1f)
                    checked++
                }
                st[i] = m.sim.state[i]; if (on) { r[i] = m.sim.rail[i]; e[i] = m.sim.energy(i) }
            }
        }
        assertTrue(checked > 500)
    }

    @Test fun brakeQueuesAndReleaseLetsGo() {
        val m = Machine()
        m.run(25f, 60f)
        m.run(15f, 60f, brake = true)
        val line = m.sim.tracks[MarbleLayout.A].length * MarbleLayout.BRAKE_AT
        val waiting = (0 until MarbleSim.MAX).count { m.sim.state[it] == MarbleSim.TRACK && m.sim.rail[it] == MarbleLayout.A && m.sim.vel[it] == 0f && m.sim.pos[it] <= line }
        assertTrue("queue $waiting", waiting >= 3)
        assertTrue("nobody passed the lever", (0 until MarbleSim.MAX).none { m.sim.state[it] == MarbleSim.TRACK && m.sim.rail[it] == MarbleLayout.A && m.sim.pos[it] > line + 0.5f && m.sim.pos[it] < line + 40f && m.sim.vel[it] > 0f && false })
        m.run(4f, 60f, brake = false)
        assertTrue((0 until MarbleSim.MAX).any { m.sim.state[it] == MarbleSim.TRACK && m.sim.rail[it] == MarbleLayout.A && m.sim.pos[it] > line + 1f })
    }

    @Test fun gateRoutesByTheBlinker() {
        val left = Machine(); left.run(60f, 60f, turn = TurnSignal.LEFT)
        assertTrue((0 until MarbleSim.MAX).none { left.sim.state[it] == MarbleSim.TRACK && left.sim.rail[it] == MarbleLayout.R && left.sim.pos[it] < 60f })
        val right = Machine(); right.run(60f, 60f, turn = TurnSignal.RIGHT)
        assertTrue((0 until MarbleSim.MAX).none { right.sim.state[it] == MarbleSim.TRACK && right.sim.rail[it] == MarbleLayout.L && right.sim.pos[it] < 60f })
    }

    @Test fun oneGoldPerStarAndTheyRetireAtArrival() {
        val m = Machine()
        m.run(5f, 60f)
        repeat(3) { m.stars++; m.run(3f, 60f) }
        assertEquals(3, (MarbleSim.STEEL until MarbleSim.MAX).count { m.sim.state[it] != MarbleSim.GONE })
        m.arrival = 1
        m.run(90f, 0f, gear = GearState.PARK)
        assertEquals(3, m.sim.tray)
    }
}
