package io.vroomi.character

import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.signal.DrivingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTrackerTest {

    /** Drives at [kmh] for [seconds] in 100 ms steps. */
    private fun JourneyTracker.drive(kmh: Float, seconds: Int, start: Long, gear: GearState = GearState.DRIVE): Pair<Journey, Long> {
        var t = start
        var j = update(kmh, kmh > 3f || gear != GearState.PARK, gear, null, t)
        repeat(seconds * 10) { t += 100; j = update(kmh, true, gear, null, t) }
        return j to t
    }

    @Test fun oneStarPer250mOfSmoothDriving() {
        val tr = JourneyTracker()
        // 36 km/h = 10 m/s -> 60 s = 600 m -> 2 stars
        val (j, _) = tr.drive(36f, 60, 0)
        assertEquals(2, j.stars)
        assertEquals(2, j.starSeq)
        assertTrue(tr.drainEvents().count { it == JourneyTracker.Event.STAR } == 2)
    }

    @Test fun harshEventRestartsTheStarInProgressAndTheCombo() {
        val tr = JourneyTracker()
        var (j, t) = tr.drive(36f, 40, 0)      // 400 m: 1 star, 150 m toward the next
        assertEquals(1, j.stars); assertEquals(1, j.combo)
        tr.onHarsh()
        val r = tr.drive(36f, 20, t)           // 200 m more: not enough for a star (restarted)
        j = r.first
        assertEquals(1, j.stars)
        assertEquals(0, j.combo)
    }

    @Test fun speedingEarnsNothing() {
        val tr = JourneyTracker()
        val (j, _) = tr.drive(130f, 30, 0)     // ~1 km above 110 km/h
        assertEquals(0, j.stars)
        assertEquals(1, j.milestoneKm)          // distance still counts for km signs
    }

    @Test fun fiveInARowIsACombo() {
        val tr = JourneyTracker()
        val (j, _) = tr.drive(90f, 52, 0)       // 25 m/s * 52 s = 1300 m -> 5 stars
        assertEquals(5, j.stars)
        assertEquals(1, j.comboSeq)
        assertTrue(JourneyTracker.Event.COMBO in tr.drainEvents())
    }

    @Test fun sceneryChangesWithDistanceAndTripResetsInPark() {
        val tr = JourneyTracker()
        var (j, t) = tr.drive(72f, 90, 0)       // 20 m/s * 90 s = 1800 m -> 2 scenery changes
        assertEquals(Biome.FIELD, j.biome)
        assertEquals(3, j.biomesSeen)
        assertEquals(1, j.milestoneKm)
        // Park: the journey ends but keeps its numbers for the diary...
        t += 100; j = tr.update(0f, false, GearState.PARK, null, t)
        assertEquals(7, j.stars)
        // ...and the next drive starts fresh in the city.
        t += 100; j = tr.update(20f, true, GearState.DRIVE, null, t)
        assertEquals(0, j.stars)
        assertEquals(Biome.CITY, j.biome)
        assertEquals(7, j.starSeq)               // sequence keeps counting: no replayed pickups
    }

    @Test fun redLightInDriveDoesNotEndTheJourney() {
        val tr = JourneyTracker()
        var (j, t) = tr.drive(36f, 30, 0)       // 300 m: 1 star
        repeat(1200) { t += 100; j = tr.update(0f, true, GearState.DRIVE, null, t) } // 2 minutes at a light
        val r = tr.drive(36f, 20, t)            // 200 m more: segment continues (50 + 200 = 250)
        assertEquals(2, r.first.stars)
    }

    @Test fun directorTurnsJourneyIntoChipsVoiceAndKart() {
        val d = CharacterDirector()
        val s = VehicleSnapshot(speedKmh = 90f, gear = GearState.DRIVE, fanLevel = 1, fanLevelRange = 0..8, setTempC = 23f,
            brakePressed = false, turnSignal = TurnSignal.LEFT, headlightsOn = true, wiperActive = true)
        var t = 0L
        var target = d.update(s, true, false, emptySet(), t)
        val voice = ArrayList<VroomiEvent>()
        repeat(600) { t += 100; target = d.update(s, true, false, emptySet(), t); voice += d.drainVoiceEvents() }
        // 60 s at 25 m/s = 1500 m: 6 stars, 1 combo, 1 km sign, scenery changed
        assertEquals(6, target.journey.stars)
        assertTrue(voice.containsAll(listOf(VroomiEvent.STAR_COMBO, VroomiEvent.MILESTONE, VroomiEvent.BIOME_PARK)))
        assertTrue(target.kart.driving && target.kart.headlights && target.kart.wipers)
        assertEquals(Blinker.LEFT, target.kart.blinker)
        d.onDrivingEvent(DrivingEvent.HARSH_BRAKE, t)
        target = d.update(s, true, false, emptySet(), t + 100)
        assertEquals(0, target.journey.combo)
        assertTrue((target.statusChip ?: "").length <= 12)
    }
}
