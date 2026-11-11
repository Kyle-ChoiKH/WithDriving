package io.vroomi.app

import io.vroomi.app.diary.DiaryStore
import io.vroomi.app.diary.SmoothnessScore
import io.vroomi.app.diary.Sticker
import io.vroomi.app.diary.TripRecord
import io.vroomi.app.diary.TripRecorder
import io.vroomi.app.progress.SafetyGrade
import io.vroomi.app.runtime.MovingDetector
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.signal.DrivingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothnessScoreTest {
    @Test fun formula() {
        assertEquals(100, SmoothnessScore.compute(0, 0, 0))
        assertEquals(100 - 4 - 12, SmoothnessScore.compute(1, 2, 0))
        assertEquals(100 - 6, SmoothnessScore.compute(0, 0, 3 * 60_000))
        assertEquals(0, SmoothnessScore.compute(10, 10, 0))
    }

    @Test fun stickers() {
        assertEquals(Sticker.STAR, Sticker.forScore(95))
        assertEquals(Sticker.BREEZE, Sticker.forScore(70))
        assertEquals(Sticker.SPROUT, Sticker.forScore(69))
    }
}

class TripRecorderTest {
    @Test fun recordsOneTripEndingInPark() {
        val r = TripRecorder()
        var t = 0L
        val moving = VehicleSnapshot(speedKmh = 60f, gear = GearState.DRIVE, fanLevel = 1)
        assertNull(r.onSnapshot(moving.copy(speedKmh = 0f), t))
        // 60 km/h for 60 s = 1 km
        repeat(600) { t += 100; assertNull(r.onSnapshot(if (it == 300) moving.copy(fanLevel = 3) else moving.copy(fanLevel = if (it > 300) 3 else 1), t)) }
        r.onEvent(DrivingEvent.HARSH_BRAKE)
        r.onEvent(DrivingEvent.BURST)
        t += 100; assertNull(r.onSnapshot(moving.copy(speedKmh = 0f, fanLevel = 3), t))
        t += 100
        val trip = r.onSnapshot(moving.copy(speedKmh = 0f, gear = GearState.PARK, fanLevel = 3), t)
        assertNotNull(trip)
        trip!!
        assertEquals(1f, trip.distanceKm, 0.05f)
        assertEquals(1, trip.harshBrakes)
        assertEquals(1, trip.bursts)
        assertEquals(1, trip.climateMoments)
        assertEquals(94, trip.score)
        assertFalse(r.isActive)
    }

    @Test fun prefersOdometerDelta() {
        val r = TripRecorder()
        val s = VehicleSnapshot(speedKmh = 30f, gear = GearState.DRIVE, odometerKm = 100f)
        r.onSnapshot(s, 0)
        r.onSnapshot(s.copy(odometerKm = 102.5f), 1_000)
        val trip = r.onSnapshot(s.copy(speedKmh = 0f, gear = GearState.PARK, odometerKm = 102.5f), 2_000)!!
        assertEquals(2.5f, trip.distanceKm, 0.001f)
    }

    @Test fun longRedLightInDriveDoesNotSplitTheTrip() {
        val r = TripRecorder()
        val d = VehicleSnapshot(speedKmh = 50f, gear = GearState.DRIVE)
        var t = 0L
        repeat(100) { t += 100; r.onSnapshot(d, t) }
        repeat(1_000) { t += 100; assertNull(r.onSnapshot(d.copy(speedKmh = 0f), t)) } // 100 s at a red light
        assertTrue(r.isActive)
        repeat(100) { t += 100; r.onSnapshot(d, t) }
        assertNotNull(r.onSnapshot(d.copy(speedKmh = 0f, gear = GearState.PARK), t + 100))
    }

    @Test fun ignoresTinyTrips() {
        val r = TripRecorder()
        val s = VehicleSnapshot(speedKmh = 6f, gear = GearState.DRIVE)
        r.onSnapshot(s, 0)
        assertNull(r.onSnapshot(s.copy(speedKmh = 0f, gear = GearState.PARK), 1_000))
    }
}

class MovingDetectorTest {
    @Test fun unknownGearUsesHysteresis() {
        val m = MovingDetector()
        assertFalse(m.update(2f, GearState.UNKNOWN, false, 0))
        assertTrue(m.update(5f, GearState.UNKNOWN, false, 100))
        assertTrue(m.update(0f, GearState.UNKNOWN, false, 200))
        assertTrue(m.update(0f, GearState.UNKNOWN, false, 3_000))
        assertFalse(m.update(0f, GearState.UNKNOWN, false, 3_300))
    }

    @Test fun redLightInDriveStaysMovingUntilPark() {
        val m = MovingDetector()
        assertTrue(m.update(40f, GearState.DRIVE, false, 0))
        assertTrue(m.update(0f, GearState.DRIVE, false, 120_000)) // two minutes at a red light
        assertFalse(m.update(0f, GearState.PARK, false, 121_000))
    }

    @Test fun uxRestrictionMeansMoving() {
        val m = MovingDetector()
        assertTrue(m.update(null, GearState.PARK, true, 0))
    }
}

class DiaryStoreCodecTest {
    @Test fun roundTrip() {
        val t = TripRecord(1, 2, 3_600_000, 12.3f, 2, 1, 0, 5_000, 4, 1, 92)
        val back = DiaryStore.decode(DiaryStore.encode(listOf(t)))
        assertEquals(listOf(t), back)
        val withJourney = t.copy(stars = 41, bestCombo = 22, biomes = 5, demo = true)
        assertEquals(listOf(withJourney), DiaryStore.decode(DiaryStore.encode(listOf(withJourney))))
    }

    @Test fun oldDiaryFilesStillLoad() {
        val old = """[{"id":1,"startMs":2,"endMs":3,"distanceKm":1.5,"bursts":0,"harshAccel":0,"harshBrakes":0,"overspeedMs":0,"climate":0,"rain":0,"score":100}]"""
        val t = DiaryStore.decode(old).single()
        assertEquals(0, t.stars); assertEquals(1, t.biomes); assertEquals(false, t.demo)
    }

    @Test fun demoTripsNeverCountForTheGrade() {
        val real = TripRecord(1, 0, 1, 20f, 0, 0, 0, 0, 0, 0, 95, stars = 80)
        val demo = real.copy(id = 2, demo = true)
        assertEquals(20f, SafetyGrade.safeKm(listOf(real, demo)), 0.001f)
        assertEquals(80, SafetyGrade.totalStars(listOf(real, demo)))
    }
}
