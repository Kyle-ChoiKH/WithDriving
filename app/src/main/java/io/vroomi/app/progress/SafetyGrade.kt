package io.vroomi.app.progress

import io.vroomi.app.diary.TripRecord
import io.vroomi.character.Accessory
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The five safe-driving grades (spec 4.1). A grade is earned when the safe distance ([needKm]) and the recent safe
 * score ([needScore]) are both reached; bronze is where everyone starts.
 */
enum class Grade(val level: Int, val label: String, val needKm: Float, val needScore: Int) {
    BRONZE(1, "브론즈", 0f, 0),
    SILVER(2, "실버", 10f, 75),
    GOLD(3, "골드", 30f, 80),
    PLATINUM(4, "플래티넘", 60f, 85),
    DIAMOND(5, "다이아몬드", 100f, 90);

    /** The grade above this one, or null for diamond. */
    fun next(): Grade? = entries.getOrNull(ordinal + 1)

    companion object {
        /** The grade of [level], clamped to 1..5. */
        fun of(level: Int): Grade = entries[level.coerceIn(1, entries.size) - 1]
    }
}

/**
 * The safe-driving grade from the diary (pure, no Android). Demo trips never count. The grade a user has reached is
 * stored ([io.vroomi.app.settings.Settings.gradeReached]) and never drops, except by a diary reset.
 */
object SafetyGrade {
    /** The recent score is the distance-weighted mean of this many newest real trips. */
    const val RECENT_TRIPS = 10

    /** Safe distance: km of real trips scoring at least 70 ([TripRecord.countsAsCare]). */
    fun safeKm(trips: List<TripRecord>): Float =
        trips.filter { it.countsAsCare && !it.demo }.sumOf { it.distanceKm.toDouble() }.toFloat()

    /**
     * The recent safe score: the distance-weighted mean score of the newest [RECENT_TRIPS] real trips (the diary keeps
     * them newest first), rounded. Null without a real trip; a plain mean when those trips add up to no distance.
     */
    fun recentScore(trips: List<TripRecord>): Int? {
        val recent = trips.asSequence().filter { !it.demo }.take(RECENT_TRIPS).toList()
        if (recent.isEmpty()) return null
        val km = recent.sumOf { it.distanceKm.coerceAtLeast(0f).toDouble() }
        val mean = if (km <= 0.0) recent.sumOf { it.score.toDouble() } / recent.size
        else recent.sumOf { it.score * it.distanceKm.coerceAtLeast(0f).toDouble() } / km
        return mean.roundToInt()
    }

    /** The highest grade whose distance and score are both reached by [trips]. */
    fun earned(trips: List<TripRecord>): Grade {
        val km = safeKm(trips)
        val score = recentScore(trips)
        return Grade.entries.last { g -> km >= g.needKm && (g.needScore <= 0 || (score != null && score >= g.needScore)) }
    }

    /** The grade shown: what the trips earn now, never below the stored [reached] level. */
    fun current(trips: List<TripRecord>, reached: Int): Grade = Grade.of(max(earned(trips).level, Grade.of(reached).level))

    /** Stars of the real trips. */
    fun totalStars(trips: List<TripRecord>): Int = trips.filter { !it.demo }.sumOf { it.stars }

    /** Safe km still missing for the next grade (0 when only the score is missing), null at diamond. */
    fun kmToNext(trips: List<TripRecord>, reached: Int): Float? {
        val next = current(trips, reached).next() ?: return null
        return (next.needKm - safeKm(trips)).coerceAtLeast(0f)
    }

    /** The kart's outfits a [grade] opens (one per grade, the old level mapping). */
    fun outfits(grade: Grade): Set<Accessory> = Accessory.entries.filter { it.wardrobe && it.unlockLevel <= grade.level }.toSet()
}
