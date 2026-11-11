package io.vroomi.app.progress

import io.vroomi.app.diary.TripRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the card after a trip says about the grade. */
class GradeNudgeTest {
    private var nextId = 1L
    private fun trip(km: Float, score: Int, demo: Boolean = false) =
        TripRecord(nextId++, 0, 60_000, km, 0, 0, 0, 0, 0, 0, score, demo = demo)

    @Test fun demoNeverCounts() {
        val t = trip(5f, 100, demo = true)
        assertEquals(GradeNudge.Demo, GradeNudge.of(t, listOf(t), 1))
    }

    @Test fun showsHowFarTheNextGradeIs() {
        val t = trip(4f, 90)
        val n = GradeNudge.of(t, listOf(t), 1) as GradeNudge.Next
        assertEquals(Grade.SILVER, n.grade)
        assertEquals(6f, n.kmLeft, 0.01f)
        assertNull(n.scoreNeeded)
        assertEquals(1, n.model)      // silver opens step 2 of every theme
    }

    @Test fun saysWhenOnlyTheScoreIsShort() {
        // 12 km driven, but the recent score (70) is under silver's 75.
        val trips = listOf(trip(6f, 70), trip(6f, 70))
        val n = GradeNudge.of(trips[0], trips, 1) as GradeNudge.Next
        assertEquals(0f, n.kmLeft, 0f)
        assertEquals(Grade.SILVER.needScore, n.scoreNeeded)
    }

    @Test fun celebratesTheTripThatRaisedTheGrade() {
        val old = trip(8f, 90)
        val now = trip(3f, 90)
        // The diary keeps the newest first; the runtime has already stored the new grade.
        val n = GradeNudge.of(now, listOf(now, old), 2)
        assertTrue("$n", n is GradeNudge.Promoted)
        assertEquals(Grade.SILVER, (n as GradeNudge.Promoted).grade)
        assertEquals(1, n.model)
        // The trip after it shows the way to gold again.
        val later = trip(1f, 90)
        assertTrue(GradeNudge.of(later, listOf(later, now, old), 2) is GradeNudge.Next)
    }

    @Test fun nothingLeftToSayAtTheTop() {
        val t = trip(2f, 95)
        assertNull(GradeNudge.of(t, listOf(t), 5))
    }
}
