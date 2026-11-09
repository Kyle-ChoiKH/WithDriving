package io.vroomi.character.treasuremap

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.route.LiveRoute
import io.vroomi.core.vehicle.route.TripSketch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class TreasureMapTest {

    @After fun clear() { LiveRoute.demoRoute = null; LiveRoute.clear(); TripSketch.take() }

    private class Drive(val sim: TreasureMapSim = TreasureMapSim()) {
        var now = 1_000_000L; var km = 0f; var stars = 0; var arrival = 0
        fun run(seconds: Float, kmh: Float, turn: TurnSignal = TurnSignal.NONE, active: Boolean = true, gear: GearState = GearState.DRIVE) {
            repeat((seconds * 60).toInt()) {
                km += kmh / 3600f / 60f
                val car = CarSignals(speedKmh = kmh, gear = gear, moving = kmh > 0.5f, turnSignal = turn, arrivalSeq = arrival)
                sim.step(1f / 60f, CharacterTarget(kart = KartState(driving = kmh > 0.5f), car = car,
                    journey = Journey(active = active, tripSeq = 1, tripKm = km, stars = stars, starSeq = stars)), now, 14f)
                now += 1000L / 60
            }
        }
    }

    @Test fun deadReckoningTurnsAbout70DegreesAfterABlinkerSequence() {
        LiveRoute.clear()
        val d = Drive()
        d.run(10f, 50f)
        assertEquals(TreasureMapSim.Source.RECKONED, d.sim.source)
        assertEquals(0.0, d.sim.heading, 1e-6)
        d.run(3f, 50f, TurnSignal.LEFT)
        assertEquals("no turn while blinking", 0.0, d.sim.heading, 1e-6)
        d.run(5f, 50f)
        assertEquals(70.0, d.sim.heading * 180 / PI, 1.0)
        d.run(3f, 50f, TurnSignal.RIGHT); d.run(5f, 50f)
        assertEquals(0.0, d.sim.heading * 180 / PI, 1.0)
        // The trail is a doodle that only grows behind the token.
        assertTrue(d.sim.count > 20)
        assertEquals(d.sim.headX, d.sim.xs[d.sim.count - 1], 5.5f)
    }

    @Test fun demoRouteIsFollowedByDistance() {
        LiveRoute.demoRoute = floatArrayOf(0f, 0f, 1000f, 0f, 1000f, 1000f)
        val d = Drive()
        d.run(90f, 60f) // 1.5 km
        assertEquals(TreasureMapSim.Source.DEMO, d.sim.source)
        assertEquals(1000f, d.sim.headX, 20f); assertEquals(500f, d.sim.headY, 20f)
    }

    @Test fun marksStampsAndTheSketch() {
        LiveRoute.demoRoute = floatArrayOf(0f, 0f, 3000f, 0f)
        val d = Drive()
        d.run(30f, 60f); d.stars = 1; d.run(1f, 60f)
        assertEquals(1, d.sim.marks)
        assertEquals(d.sim.headX, d.sim.markX[0], 20f)
        // Two stops within two minutes give one rest stamp.
        d.run(35f, 0f); d.run(10f, 40f); d.run(35f, 0f)
        assertEquals(1, d.sim.stamps)
        d.run(130f, 40f); d.run(35f, 0f)
        assertEquals(2, d.sim.stamps)
        // Arrival: the map rolls up and the diary gets a normalised sketch (no coordinates).
        d.arrival = 1
        d.run(4f, 0f, gear = GearState.PARK)
        assertTrue(d.sim.roll > 0.9f)
        val sk = TripSketch.take()!!
        assertEquals(128, sk.size); sk.forEach { assertTrue(it in 0f..1f) }
    }

    @Test fun cameraFitsTheTrailSmoothly() {
        LiveRoute.demoRoute = floatArrayOf(0f, 0f, 5000f, 0f)
        val d = Drive()
        d.run(240f, 60f) // 4 km
        val r1 = d.sim.camR
        assertTrue("fits about 2 km half-extent: $r1", r1 in 1800f..2800f)
        d.run(0.5f, 60f)
        assertTrue("smoothed, no jump", abs(d.sim.camR - r1) < 200f)
        assertTrue(d.sim.camR >= TreasureMapSim.MIN_HALF_EXTENT)
    }

    @Test fun nothingPersistedHoldsALocation() {
        TreasureMapSim::class.java.declaredFields.forEach { f -> assertTrue(f.name, listOf("lat", "lon").none { p -> f.name.lowercase().startsWith(p) }) }
        assertEquals(null, TreasureMapSim().persist())
    }
}
