package io.vroomi.character.terrarium

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.core.vehicle.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TerrariumTest {

    private class Drive(val sim: TerrariumSim = TerrariumSim()) {
        var now = 1_000_000L; var km = 0f
        fun run(seconds: Float, kmh: Float, a: Float = 0f, block: (CarSignals) -> CarSignals = { it }, journey: (Journey) -> Journey = { it }, hour: Float = 14f, each: () -> Unit = {}) {
            repeat((seconds * 60).toInt()) {
                km += kmh / 3600f / 60f
                val car = block(CarSignals(speedKmh = kmh, accelMps2 = a, gear = if (kmh > 0) GearState.DRIVE else GearState.PARK, moving = kmh > 0.5f))
                sim.step(1f / 60f, CharacterTarget(kart = KartState(driving = kmh > 0.5f), car = car, journey = journey(Journey(active = true, tripSeq = 1, tripKm = km))), now, hour)
                now += 1000L / 60
                each()
            }
        }
    }

    @Test fun lSystemsAreDeterministicAndGrowthOnlyAdds() {
        for (kind in PlantGen.Kind.entries) {
            val a = PlantGen.segments(kind, 7, 0.6f); val b = PlantGen.segments(kind, 7, 0.6f)
            assertEquals(a.size, b.size)
            for (i in a.indices) { assertEquals(a[i].x1, b[i].x1, 0f); assertEquals(a[i].y1, b[i].y1, 0f) }
            var last = 0
            for (g in 0..20) {
                val s = PlantGen.segments(kind, 7, g / 20f)
                assertTrue("$kind grows monotonically", s.size >= last)
                // The smaller plant is the beginning of the larger one (the same drawing).
                if (last > 0) { val prev = PlantGen.segments(kind, 7, (g - 1) / 20f); assertEquals(prev.last().x1, s[prev.size - 1].x1, 0f) }
                last = s.size
            }
        }
    }

    @Test fun growsOneUnitPerKmCappedAndNeverShrinks() {
        val d = Drive()
        d.run(36f, 100f) // 1 km
        assertEquals(TerrariumSim.SHARE.sum(), d.sim.growth.sum(), 0.05f)
        val before = d.sim.growth.copyOf()
        d.run(20f, 0f)
        for (i in before.indices) assertTrue(d.sim.growth[i] >= before[i])
        d.run(7200f, 120f) // 240 km: every plant reaches its cap
        for (i in 0 until TerrariumSim.PLANTS) assertEquals(TerrariumSim.CAP[i], d.sim.growth[i], 1e-3f)
    }

    @Test fun bloomsOnSmoothDrivingNotOnSpeed() {
        val slow = Drive(); slow.run(305f, 30f)
        assertEquals("5 smooth minutes open a flower", 1, slow.sim.blooms)
        val fast = Drive(); fast.run(200f, 130f)
        assertEquals("speed alone never blooms", 0, fast.sim.blooms)
        val harsh = Drive()
        harsh.run(200f, 40f); harsh.run(1f, 40f, block = { it.copy(harshBrakeSeq = 1) }); harsh.run(200f, 40f, block = { it.copy(harshBrakeSeq = 1) })
        assertEquals("a harsh brake restarts the window", 0, harsh.sim.blooms)
    }

    @Test fun pondTiltFollowsAtanAndIsCapped() {
        val d = Drive(); d.run(6f, 50f, a = 1.5f)
        val expected = -Math.toDegrees(kotlin.math.atan(1.5 / 9.81)).toFloat() * TerrariumSim.TILT_SCALE
        assertEquals(expected, d.sim.tilt, 0.15f)
        d.run(6f, 50f, a = 9f)
        assertEquals(-TerrariumSim.MAX_TILT, d.sim.tilt, 0.1f)
        var maxAbs = 0f
        d.run(3f, 50f, block = { it.copy(harshBrakeSeq = 1) }) { maxAbs = maxOf(maxAbs, abs(d.sim.tilt)) }
        assertTrue(maxAbs <= TerrariumSim.MAX_TILT + 1e-3f)
    }

    @Test fun firefliesGlowAtHalfAHertzOrSlower() {
        val d = Drive()
        var rises = 0; var prev = 0f
        d.run(20f, 40f, block = { it.copy(headlightsOn = true) }) {
            val v = d.sim.firefly(0) - 0.7f
            if (prev < 0f && v >= 0f) rises++
            prev = v
        }
        assertTrue("rises $rises times in 20 s", rises <= 10)
    }

    @Test fun stateRoundTripsWithoutLocation() {
        val a = TerrariumSim()
        a.restore("g=10,20,5,5,4,6;b=3;u=1;r=2;s=1")
        val text = a.persist()
        val b = TerrariumSim(); b.restore(text)
        assertTrue(a.growth.contentEquals(b.growth))
        assertEquals(3, b.blooms); assertEquals(2, b.rings)
        // Restoring an older (smaller) state never shrinks the garden.
        b.restore("g=1,1,1,1,1,1;b=0")
        assertEquals(20f, b.growth[1], 0f); assertEquals(3, b.blooms)
        // Only counters and the season are stored: no location, no time of day.
        assertTrue(listOf("lat", "lon", "gps", "time", "hour").none { text.contains(it, ignoreCase = true) })
        TerrariumSim::class.java.declaredFields.forEach { f -> assertTrue(f.name, listOf("latitude", "longitude").none { f.name.contains(it, ignoreCase = true) }) }
    }
}
