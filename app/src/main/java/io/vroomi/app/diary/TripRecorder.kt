package io.vroomi.app.diary

import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.signal.DrivingEvent
import io.vroomi.core.vehicle.signal.Thresholds

/**
 * Builds one TripRecord per drive. A trip starts once speed reaches 5 km/h and ends when the car is
 * put in Park (or, when the gear is unknown, stays stopped for 60 s). Distance prefers the Odometer delta (Vehicle SDK) and
 * falls back to integrated speed. Nothing location-related is stored.
 */
class TripRecorder {
    private var active = false
    private var startMs = 0L
    private var lastMs = 0L
    private var stoppedSince: Long? = null
    private var integratedKm = 0.0
    private var odoStart: Float? = null
    private var odoLast: Float? = null
    private var bursts = 0
    private var harshAccel = 0
    private var harshBrakes = 0
    private var overspeedMs = 0L
    private var climate = 0
    private var rain = 0
    private var prev: VehicleSnapshot? = null
    private var sawDemo = false

    val isActive: Boolean get() = active

    fun onEvent(e: DrivingEvent) {
        if (!active) return
        when (e) {
            DrivingEvent.BURST -> bursts++
            DrivingEvent.HARSH_ACCEL -> harshAccel++
            DrivingEvent.HARSH_BRAKE -> harshBrakes++
        }
    }

    /**
     * @param demo true while the snapshot comes from a demo drive. A trip that had any demo driving in it is stored as a
     * demo trip, even when the demo was stopped before the trip ended (so it never counts toward the safety grade).
     * @return the finished trip when one just ended, else null.
     */
    fun onSnapshot(s: VehicleSnapshot, nowMs: Long, demo: Boolean = false): TripRecord? {
        val speed = s.speedKmh ?: 0f
        val p = prev
        prev = s
        if (!active) {
            if (speed >= Thresholds.TRIP_START_KMH) { start(s, nowMs); sawDemo = demo }
            return null
        }
        if (demo) sawDemo = true
        val dt = (nowMs - lastMs).coerceIn(0L, 2_000L)
        lastMs = nowMs
        integratedKm += speed * dt / 3_600_000.0
        if (speed > Thresholds.OVERSPEED_KMH) overspeedMs += dt
        s.odometerKm?.let { odoLast = it; if (odoStart == null) odoStart = it }
        if (p != null) {
            val f = s.fanLevel; val pf = p.fanLevel
            if (f != null && pf != null && f > pf) climate++
            if ((s.setTempC ?: 0f) != (p.setTempC ?: 0f) && p.setTempC != null) climate++
            if (s.wiperActive == true && p.wiperActive != true) rain++
        }

        val stopped = speed < 1f
        if (stopped) { if (stoppedSince == null) stoppedSince = nowMs } else stoppedSince = null
        val parked = s.gear == GearState.PARK && stopped
        // Without gear information a 60 s stop ends the trip; in D/R/N (e.g. a long red light) it does not.
        val longStop = s.gear == GearState.UNKNOWN &&
            (stoppedSince?.let { nowMs - it >= Thresholds.TRIP_END_STOP_MS } ?: false)
        if (parked || longStop) return finish(nowMs)
        return null
    }

    private fun start(s: VehicleSnapshot, nowMs: Long) {
        active = true
        startMs = nowMs; lastMs = nowMs
        stoppedSince = null
        integratedKm = 0.0
        odoStart = s.odometerKm; odoLast = s.odometerKm
        bursts = 0; harshAccel = 0; harshBrakes = 0; overspeedMs = 0; climate = 0; rain = 0
    }

    private fun finish(nowMs: Long): TripRecord? {
        active = false
        val odoKm = run {
            val a = odoStart; val b = odoLast
            if (a != null && b != null && b - a > 0.01f && b - a < 2_000f) b - a else null
        }
        val km = odoKm ?: integratedKm.toFloat()
        if (km < Thresholds.TRIP_MIN_DISTANCE_KM) return null
        return TripRecord(
            id = startMs,
            startMs = startMs,
            endMs = nowMs,
            distanceKm = km,
            bursts = bursts,
            harshAccel = harshAccel,
            harshBrakes = harshBrakes,
            overspeedMs = overspeedMs,
            climateMoments = climate,
            rainMoments = rain,
            score = SmoothnessScore.compute(harshAccel, harshBrakes, overspeedMs),
            demo = sawDemo,
        )
    }
}
