package io.vroomi.character.aquarium

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.core.vehicle.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class AquariumTest {

    private class Tank(val sim: AquariumSim = AquariumSim()) {
        var now = 1_000_000L
        fun run(seconds: Float, kmh: Float, a: Float = 0f, gear: GearState = GearState.DRIVE, block: (CarSignals) -> CarSignals = { it }, each: () -> Unit = {}) {
            repeat((seconds * 60).toInt()) {
                val c = block(CarSignals(speedKmh = kmh, accelMps2 = a, gear = gear, moving = kmh > 0.5f))
                sim.step(1f / 60f, CharacterTarget(kart = KartState(driving = kmh > 0.5f), car = c, journey = Journey(active = true, tripSeq = 1)), now, 14f)
                now += 1000L / 60
                each()
            }
        }
    }

    @Test fun boidsAreDeterministic() {
        val a = Tank(); val b = Tank()
        a.run(20f, 50f); b.run(20f, 50f)
        for (i in 0 until AquariumSim.FISH) { assertEquals(a.sim.fx[i], b.sim.fx[i], 0f); assertEquals(a.sim.fy[i], b.sim.fy[i], 0f) }
    }

    @Test fun fishStayInTheTankForTenMinutes() {
        val t = Tank()
        var speed = 0f
        t.run(600f, 0f, block = { it.copy(speedKmh = speed) }) {
            speed = ((t.now / 7000L) % 130L).toFloat()
            for (i in 0 until AquariumSim.FISH) {
                assertTrue(t.sim.fx[i] in 0f..AquariumSim.W && t.sim.fy[i] in 0f..AquariumSim.H)
            }
        }
    }

    @Test fun tiltFollowsAtanAndIsCapped() {
        val t = Tank(); t.run(6f, 50f, a = 1.5f)
        val expected = -Math.toDegrees(kotlin.math.atan(1.5 / 9.81)).toFloat() * AquariumSim.TILT_SCALE
        assertEquals(expected, t.sim.tilt, 0.15f)
        t.run(6f, 50f, a = 9f)
        assertEquals(-AquariumSim.MAX_TILT, t.sim.tilt, 0.1f)
    }

    @Test fun oneStartlePerEventThenCalm() {
        val t = Tank(); t.run(5f, 60f)
        t.run(0.2f, 40f, block = { it.copy(harshBrakeSeq = 1) })
        val at = t.sim.startleAt
        t.run(3f, 40f, block = { it.copy(harshBrakeSeq = 1) })
        assertEquals("the same event startles once", at, t.sim.startleAt, 0.0)
        // Calm within 2 s: school speeds back under the cruise limit.
        val cruise = 40f + 140f * 0.4f
        for (i in 0 until AquariumSim.FISH) assertTrue(hypot(t.sim.vx[i], t.sim.vy[i]) <= cruise * 1.05f)
    }

    @Test fun fishCountAndCausticsRate() {
        assertTrue(AquariumSim.FISH <= 40)
        val t = Tank()
        var last = t.sim.caustics(); var maxStep = 0f
        t.run(30f, 40f) { val c = t.sim.caustics(); maxStep = maxOf(maxStep, abs(c - last)); last = c }
        // 0.08 Hz: a frame changes the intensity by far less than 1 %.
        assertTrue("caustic step $maxStep", maxStep < 0.01f)
    }
}
