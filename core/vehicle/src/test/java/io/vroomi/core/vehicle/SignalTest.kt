package io.vroomi.core.vehicle

import io.vroomi.core.vehicle.signal.DrivingEvent
import io.vroomi.core.vehicle.signal.DrivingEventDetector
import io.vroomi.core.vehicle.signal.SignalProcessor
import io.vroomi.core.vehicle.speed.Geo
import io.vroomi.core.vehicle.speed.LocationSpeedEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SignalProcessorTest {
    @Test fun constantSpeedHasNoAcceleration() {
        val p = SignalProcessor()
        var last = p.onSpeedSample(0, 50f)
        for (i in 1..50) last = p.onSpeedSample(i * 100L, 50f)
        assertEquals(50f, last.kmh, 0.01f)
        assertTrue(abs(last.accelMps2) < 0.05f)
    }

    @Test fun rampProducesPositiveAcceleration() {
        val p = SignalProcessor()
        p.onSpeedSample(0, 0f)
        var last = p.onSpeedSample(0, 0f)
        // 0 -> 36 km/h in 2 s = 5 m/s^2
        for (i in 1..20) last = p.onSpeedSample(i * 100L, i * 1.8f)
        assertTrue("accel=${last.accelMps2}", last.accelMps2 > 3f)
    }

    @Test fun brakingProducesNegativeAcceleration() {
        val p = SignalProcessor()
        for (i in 0..20) p.onSpeedSample(i * 100L, 60f)
        var last = p.onSpeedSample(2100, 60f)
        for (i in 1..10) last = p.onSpeedSample(2100 + i * 100L, 60f - i * 4f) // -11 m/s^2
        assertTrue("accel=${last.accelMps2}", last.accelMps2 < -3.5f)
    }

    @Test fun tinySpeedsReadAsZeroAndHugeAreClamped() {
        val p = SignalProcessor()
        assertEquals(0f, p.onSpeedSample(0, 1.0f).kmh, 0f)
        val q = SignalProcessor()
        assertEquals(200f, q.onSpeedSample(0, 400f).kmh, 0.01f)
    }
}

class DrivingEventDetectorTest {
    private fun run(d: DrivingEventDetector, accel: Float, fromMs: Long, toMs: Long, brake: Boolean? = false): List<DrivingEvent> {
        val out = ArrayList<DrivingEvent>()
        var t = fromMs
        while (t <= toMs) { out += d.update(t, accel, brake); t += 100 }
        return out
    }

    @Test fun sustainedStrongAccelIsOneBurst() {
        val d = DrivingEventDetector()
        val e = run(d, 2.4f, 0, 1500)
        assertEquals(listOf(DrivingEvent.BURST), e)
    }

    @Test fun shortSpikeIsNotABurst() {
        val d = DrivingEventDetector()
        assertTrue(run(d, 2.4f, 0, 200).isEmpty())
    }

    @Test fun harshAccelFiresOnceUntilReset() {
        val d = DrivingEventDetector()
        assertEquals(listOf(DrivingEvent.HARSH_ACCEL), run(d, 3.4f, 0, 2000))
        assertTrue(run(d, 0f, 2100, 2500).isEmpty())
        assertEquals(listOf(DrivingEvent.HARSH_ACCEL), run(d, 3.4f, 2600, 3000))
    }

    @Test fun harshBrakeByDecelerationOrPedal() {
        val d = DrivingEventDetector()
        assertEquals(listOf(DrivingEvent.HARSH_BRAKE), run(d, -4f, 0, 1000))
        run(d, 0f, 1100, 1300)
        assertTrue(run(d, -3.2f, 1400, 1600, brake = false).isEmpty())
        assertEquals(listOf(DrivingEvent.HARSH_BRAKE), run(d, -3.2f, 1700, 1900, brake = true))
    }
}

class LocationSpeedTest {
    @Test fun haversineOneDegreeLatitude() {
        val m = Geo.haversineM(37.0, 127.0, 38.0, 127.0)
        assertEquals(111_195.0, m, 200.0)
    }

    @Test fun speedFromTwoFixes() {
        val e = LocationSpeedEstimator()
        assertNull(e.onFix(37.5, 127.0, 0))
        // ~100 m north in 4 s = 90 km/h
        val dLat = 100.0 / 111_195.0
        val v = e.onFix(37.5 + dLat, 127.0, 4_000)!!
        assertEquals(90f, v, 1f)
    }

    @Test fun longGapRestarts() {
        val e = LocationSpeedEstimator()
        e.onFix(37.5, 127.0, 0)
        assertNull(e.onFix(37.6, 127.0, 10_000))
    }

    @Test fun implausibleJumpRejected() {
        val e = LocationSpeedEstimator()
        e.onFix(37.5, 127.0, 0)
        assertNull(e.onFix(37.51, 127.0, 1_000)) // 1.1 km in 1 s
    }
}
