package io.vroomi.app.progress

import io.vroomi.app.diary.TripRecord

/**
 * What the card after a trip says about the safe-driving grade, so that every drive ends with the next reward in
 * sight: the grade just reached and the version it opened, or how far the next grade is and what it will open.
 * A demo drive never counts, and the card says so.
 */
sealed interface GradeNudge {
    /** The trip was a demo: it does not move the grade. */
    data object Demo : GradeNudge

    /** This trip raised the grade to [grade]: step [model] of every theme is open now. */
    data class Promoted(val grade: Grade, val model: Int) : GradeNudge

    /**
     * The next grade is [grade]: [kmLeft] of safe distance to go (0 when the distance is there and only the score is
     * short, [scoreNeeded] then); it opens step [model].
     */
    data class Next(val grade: Grade, val kmLeft: Float, val scoreNeeded: Int?, val model: Int) : GradeNudge

    companion object {
        /** [trips] is the diary with [trip] in it; [reached] the stored grade (already raised by this trip). */
        fun of(trip: TripRecord, trips: List<TripRecord>, reached: Int): GradeNudge? {
            if (trip.demo) return Demo
            val now = SafetyGrade.current(trips, reached)
            val before = SafetyGrade.earned(trips.filter { it.id != trip.id })
            if (SafetyGrade.earned(trips).level > before.level && SafetyGrade.earned(trips).level == now.level) {
                return Promoted(now, ThemeAccess.maxModel(now))
            }
            val next = now.next() ?: return null
            val left = (SafetyGrade.kmToNext(trips, reached) ?: 0f).coerceAtLeast(0f)
            val score = SafetyGrade.recentScore(trips)
            val scoreShort = score == null || score < next.needScore
            return Next(next, left, if (left <= 0f && scoreShort) next.needScore else null, ThemeAccess.maxModel(next))
        }
    }
}
