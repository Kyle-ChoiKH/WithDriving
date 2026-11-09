package io.vroomi.character.calibre

import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Timekeeping truth: every hand comes from the real clock and time zone. The seconds advance in
 * 8 steps per second (28,800 vph); the balance runs at exactly 4 Hz, locked to the same clock.
 */
object CalibreTime {
    class Hands(
        /** Degrees clockwise from 12 o'clock. */
        val hourDeg: Float,
        val minuteDeg: Float,
        /** Small seconds, stepped to 1/8 s. */
        val secondDeg: Float,
        val dayOfMonth: Int,
        /** Days since the last new moon (0 .. 29.53). */
        val moonAgeDays: Double,
    )

    const val SYNODIC_MONTH_DAYS = 29.530588853
    /** A reference new moon: 2000-01-06 18:14 UTC. */
    private const val NEW_MOON_REF_MS = 947_182_440_000L

    /** Seconds within the minute, rounded down to the last beat (1/8 s). */
    fun steppedSeconds(epochMs: Long, zone: ZoneId): Double {
        val z = Instant.ofEpochMilli(epochMs).atZone(zone)
        val s = z.second + z.nano / 1e9
        return floor(s * CalibreGeometry.BEATS_PER_SECOND) / CalibreGeometry.BEATS_PER_SECOND
    }

    fun hands(epochMs: Long, zone: ZoneId): Hands {
        val z = Instant.ofEpochMilli(epochMs).atZone(zone)
        val sec = steppedSeconds(epochMs, zone)
        val min = z.minute + sec / 60.0
        val hour = (z.hour % 12) + min / 60.0
        return Hands(
            hourDeg = (hour * 30.0).toFloat(),
            minuteDeg = (min * 6.0).toFloat(),
            secondDeg = (sec * 6.0).toFloat(),
            dayOfMonth = z.dayOfMonth,
            moonAgeDays = moonAge(epochMs),
        )
    }

    fun moonAge(epochMs: Long): Double {
        val days = (epochMs - NEW_MOON_REF_MS) / 86_400_000.0
        val a = days % SYNODIC_MONTH_DAYS
        return if (a < 0) a + SYNODIC_MONTH_DAYS else a
    }

    /** Beats since the epoch (8 per second). */
    fun beats(epochMs: Long): Long = Math.floorDiv(epochMs * CalibreGeometry.BEATS_PER_SECOND, 1000L)

    /** Balance amplitude in degrees from the reserve: ≥48 h 300°, 24 h 270°, 6 h 220°, interpolated. */
    fun amplitudeDeg(reserveH: Float): Float = when {
        reserveH >= 48f -> 300f
        reserveH >= 24f -> 270f + (reserveH - 24f) / 24f * 30f
        reserveH >= 6f -> 220f + (reserveH - 6f) / 18f * 50f
        else -> 220f
    }

    /** Balance angle (degrees) at [epochMs]: always 4 Hz, zero crossings exactly on every beat. */
    fun balanceDeg(epochMs: Long, amplitudeDeg: Float): Float {
        val phase = Math.floorMod(epochMs, 1000L) / 1000.0
        return (amplitudeDeg * sin(2.0 * PI * CalibreGeometry.BALANCE_HZ * phase)).toFloat()
    }
}
