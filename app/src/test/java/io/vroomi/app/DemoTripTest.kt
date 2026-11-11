package io.vroomi.app

import io.vroomi.app.diary.TripRecorder
import io.vroomi.app.progress.SafetyGrade
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A trip that had demo (simulated) driving in it is a demo trip, even when the demo was stopped before the trip ended. */
class DemoTripTest {
    private val moving = VehicleSnapshot(speedKmh = 60f, gear = GearState.DRIVE)
    private val parked = VehicleSnapshot(speedKmh = 0f, gear = GearState.PARK)

    /** 60 km/h for 60 s = 1 km. */
    private fun drive(r: TripRecorder, from: Long, demo: Boolean): Long {
        var t = from
        repeat(601) { t += 100; assertNull(r.onSnapshot(moving, t, demo)) }
        return t
    }

    @Test fun demoStoppedEarlyStaysADemoTrip() {
        val r = TripRecorder()
        var t = drive(r, 0L, demo = true)
        // "End demo" was pressed: the real car is parked, and the demo flag is already off when the trip closes.
        t += 100
        val trip = r.onSnapshot(parked, t, false)
        assertNotNull(trip)
        assertTrue("a trip with demo driving in it is a demo trip", trip!!.demo)
        assertEquals("demo km never count toward the grade", 0f, SafetyGrade.safeKm(listOf(trip)), 0f)
    }

    @Test fun nextRealTripIsReal() {
        val r = TripRecorder()
        var t = drive(r, 0L, demo = true)
        t += 100; assertTrue(r.onSnapshot(parked, t, false)!!.demo)
        t = drive(r, t, demo = false)
        t += 100
        val trip = r.onSnapshot(parked, t, false)!!
        assertFalse(trip.demo)
        assertEquals(1f, SafetyGrade.safeKm(listOf(trip)), 0.05f)
    }

    @Test fun realTripIsRealByDefault() {
        val r = TripRecorder()
        var t = 0L
        repeat(601) { t += 100; assertNull(r.onSnapshot(moving, t)) }
        assertFalse(r.onSnapshot(parked, t + 100)!!.demo)
    }
}
