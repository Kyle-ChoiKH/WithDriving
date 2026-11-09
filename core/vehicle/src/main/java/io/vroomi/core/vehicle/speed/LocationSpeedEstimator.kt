package io.vroomi.core.vehicle.speed

import io.vroomi.core.vehicle.signal.Thresholds

/**
 * Pure speed-from-fixes estimator (unit tested). Pleos FusedLocation 1.1.3 LocationData has
 * latitude, longitude, altitude, heading, confidence, timestampMillis and NO speed field
 * (verified in FusedLocation-1.1.3-sources.jar), so speed = distance / time between fixes.
 */
class LocationSpeedEstimator {
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastTs = -1L

    fun reset() { lastTs = -1L }

    /** @return km/h, or null when the fix cannot produce a trustworthy value. */
    fun onFix(lat: Double, lon: Double, tsMs: Long): Float? {
        if (lastTs < 0) {
            remember(lat, lon, tsMs)
            return null
        }
        val dt = tsMs - lastTs
        if (dt < Thresholds.LOCATION_MIN_GAP_MS) return null // too close, wait for the next fix
        if (dt > Thresholds.LOCATION_MAX_GAP_MS) {            // gap too long, restart
            remember(lat, lon, tsMs)
            return null
        }
        val metres = Geo.haversineM(lastLat, lastLon, lat, lon)
        remember(lat, lon, tsMs)
        val kmh = (metres / (dt / 1000.0) * 3.6).toFloat()
        return if (kmh > Thresholds.LOCATION_MAX_PLAUSIBLE_KMH) null else kmh
    }

    private fun remember(lat: Double, lon: Double, ts: Long) {
        lastLat = lat; lastLon = lon; lastTs = ts
    }
}
