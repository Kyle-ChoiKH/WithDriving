package io.vroomi.app.progress

import io.vroomi.app.diary.TripRecord
import io.vroomi.character.Accessory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.roundToInt

/** The safe-driving grade (spec 4.1): the rules, the edge cases and the review focus 2 and 3. */
class SafetyGradeTest {
    private var nextId = 1L
    /** One trip of [km] scoring [score] (newest first, as the diary keeps them). */
    private fun trip(km: Float, score: Int, demo: Boolean = false) =
        TripRecord(nextId++, 0, 60_000, km, 0, 0, 0, 0, 0, 0, score, demo = demo)

    @Test fun bronzeWithNoTrips() { assertEquals(Grade.BRONZE, SafetyGrade.earned(emptyList())); assertNull(SafetyGrade.recentScore(emptyList())) }

    @Test fun silverNeedsBothDistanceAndScore() {
        assertEquals(Grade.BRONZE, SafetyGrade.earned(listOf(trip(12f, 72))))   // 거리 OK, 점수 72 < 75
        assertEquals(Grade.SILVER, SafetyGrade.earned(listOf(trip(12f, 90))))
    }

    @Test fun recentScoreIsDistanceWeightedOverLastTen() {
        val trips = listOf(trip(30f, 90), trip(10f, 50)) + List(10) { trip(1f, 10) }  // 최신 순: 앞 10개만
        assertEquals(((30 * 90 + 10 * 50 + 8 * 10) / 48f).roundToInt(), SafetyGrade.recentScore(trips))
    }

    @Test fun zeroDistanceTripsDoNotDivideByZero() { assertEquals(80, SafetyGrade.recentScore(listOf(trip(0f, 70), trip(0f, 90)))) }

    @Test fun demoTripsNeverCount() { assertEquals(Grade.BRONZE, SafetyGrade.earned(listOf(trip(200f, 100, demo = true)))) }

    @Test fun gradeNeverDropsBelowReached() { assertEquals(Grade.GOLD, SafetyGrade.current(emptyList(), reached = 3)) }

    @Test fun gradeFromExistingTrips() { assertEquals(Grade.DIAMOND, SafetyGrade.earned(List(10) { trip(11f, 95) })) }

    @Test fun outfitsFollowGrade() { assertEquals(setOf(Accessory.SCARF, Accessory.GOGGLES, Accessory.BEANIE), SafetyGrade.outfits(Grade.GOLD)) }

    @Test fun boundariesAreInclusive() {
        // Exactly 30 km at exactly 80: gold.
        assertEquals(Grade.GOLD, SafetyGrade.earned(listOf(trip(30f, 80))))
        // A rough trip (score < 70) adds no safe distance, but it still pulls the recent score down.
        val trips = listOf(trip(40f, 60), trip(30f, 95))
        assertEquals(30f, SafetyGrade.safeKm(trips), 0.001f)
        assertEquals(((40 * 60 + 30 * 95) / 70f).roundToInt(), SafetyGrade.recentScore(trips))
    }

    @Test fun reachedHigherThanEarnedKeepsTheReachedGrade() {
        val trips = listOf(trip(12f, 90))
        assertEquals(Grade.SILVER, SafetyGrade.current(trips, reached = 1))
        assertEquals(Grade.PLATINUM, SafetyGrade.current(trips, reached = 4))
        assertEquals(Grade.DIAMOND, SafetyGrade.current(trips, reached = 9))   // clamped
    }

    @Test fun kmToNextAndStars() {
        val trips = listOf(trip(12f, 90).copy(stars = 30), trip(5f, 95, demo = true).copy(stars = 99))
        assertEquals(18f, SafetyGrade.kmToNext(trips, reached = 1)!!, 0.001f)    // silver -> gold needs 30 km
        assertEquals(48f, SafetyGrade.kmToNext(trips, reached = 3)!!, 0.001f)    // gold -> platinum needs 60 km
        assertNull(SafetyGrade.kmToNext(trips, reached = 5))
        assertEquals(30, SafetyGrade.totalStars(trips))
    }

    @Test fun gradeOrder() {
        assertEquals(Grade.SILVER, Grade.BRONZE.next())
        assertNull(Grade.DIAMOND.next())
        assertEquals(Grade.BRONZE, Grade.of(0))
        assertEquals(Grade.DIAMOND, Grade.of(7))
        assertEquals(listOf("브론즈", "실버", "골드", "플래티넘", "다이아몬드"), Grade.entries.map { it.label })
    }
}
