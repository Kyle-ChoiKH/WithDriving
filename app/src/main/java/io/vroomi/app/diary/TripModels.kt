package io.vroomi.app.diary

import io.vroomi.core.vehicle.signal.Thresholds
import kotlin.math.max
import kotlin.math.roundToInt

data class TripRecord(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Float,
    val bursts: Int,
    val harshAccel: Int,
    val harshBrakes: Int,
    val overspeedMs: Long,
    val climateMoments: Int,
    val rainMoments: Int,
    val score: Int,
    /** Stars Vroomi collected (one per 250 m without harsh events). */
    val stars: Int = 0,
    val bestCombo: Int = 0,
    /** Different sceneries Vroomi's road passed through. */
    val biomes: Int = 1,
    /** Recorded during a demo drive (simulator): shown in the diary, never counted for the safe-driving grade. */
    val demo: Boolean = false,
    /** The map theme's sketch of the trail: 64 points (x, y) in the unit square, origin removed. Never coordinates. */
    val sketch: List<Float>? = null,
) {
    val durationMin: Int get() = ((endMs - startMs) / 60_000L).toInt()
    val sticker: Sticker get() = Sticker.forScore(score)
    val countsAsCare: Boolean get() = score >= Thresholds.CARE_SCORE_MIN
}

enum class Sticker(val label: String) {
    STAR("반짝 별"), BREEZE("부드러운 바람"), SPROUT("새싹 응원");

    companion object {
        fun forScore(score: Int) = when {
            score >= 90 -> STAR
            score >= Thresholds.CARE_SCORE_MIN -> BREEZE
            else -> SPROUT
        }
    }
}

/** §8: 100 − harsh accel×4 − harsh brake×6 − (minutes over 110 km/h)×2, floored at 0. */
object SmoothnessScore {
    fun compute(harshAccel: Int, harshBrakes: Int, overspeedMs: Long): Int {
        val overMin = overspeedMs / 60_000f
        val raw = 100f -
            harshAccel * Thresholds.SCORE_HARSH_ACCEL_PENALTY -
            harshBrakes * Thresholds.SCORE_HARSH_BRAKE_PENALTY -
            overMin * Thresholds.SCORE_OVERSPEED_PER_MIN_PENALTY
        return max(0, raw.roundToInt())
    }
}
