package io.vroomi.core.vehicle.speed

import io.vroomi.core.vehicle.model.SpeedSourceKind
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A provider of raw vehicle speed. Vehicle SDK v2.3.2 has NO speed and NO accelerator-pedal property
 * (verified in Vehicle-2.3.2-sources.jar: no Speed domain, Vehicle exposes only the 20 domains
 * listed in docs/permissions.md), so speed comes from one of these sources, tried in order:
 *   1) AaosSpeedSource      – android.car PERF_VEHICLE_SPEED, only if ALLOW_AAOS_SPEED is true
 *   2) FusedLocationSpeedSource – derived from consecutive Pleos FusedLocation fixes
 *   3) the simulator (SimulatedVehicleProvider) for the emulator, demo video and PT
 */
interface SpeedSource {
    val kind: SpeedSourceKind

    /** @return true if the source started and will deliver samples. */
    fun start(onSpeed: (tsMs: Long, kmh: Float) -> Unit): Boolean
    fun stop()
}

/** Tries each source in order and keeps the first that starts. */
class SpeedSourceChain(private val sources: List<SpeedSource>) {
    var active: SpeedSource? = null
        private set

    fun start(onSpeed: (tsMs: Long, kmh: Float) -> Unit): SpeedSourceKind {
        for (s in sources) {
            val ok = try { s.start(onSpeed) } catch (t: Throwable) { false }
            if (ok) {
                active = s
                return s.kind
            }
        }
        return SpeedSourceKind.NONE
    }

    fun stop() {
        active?.stop()
        active = null
    }
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in metres. */
    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
