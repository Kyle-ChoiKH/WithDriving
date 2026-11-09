package io.vroomi.app

import io.vroomi.app.diary.DiaryStore
import io.vroomi.app.diary.TripRecord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The treasure map keeps no coordinates: not in the trip record, not in the logs, not in the docs' scenario logs. */
class MapPrivacyTest {
    private val coordinate = Regex("""(-?\d{1,3}\.\d{5,})\s*[,/ ]\s*(-?\d{1,3}\.\d{5,})""")

    @Test fun tripRecordHasNoLocationField() {
        TripRecord::class.java.declaredFields.forEach { f ->
            assertTrue(f.name, listOf("lat", "lon", "gps", "coord").none { f.name.lowercase().contains(it) })
        }
        val t = TripRecord(1, 0, 60_000, 2f, 0, 0, 0, 0, 0, 0, 90, sketch = List(128) { (it % 10) / 10f })
        val json = DiaryStore.encode(listOf(t))
        assertTrue(!coordinate.containsMatchIn(json))
        val back = DiaryStore.decode(json).single()
        assertTrue(back.sketch!!.size == 128 && back.sketch!!.all { it in 0f..1f })
    }

    @Test fun scenarioLogsHoldNoCoordinates() {
        val dirs = listOf(File("../docs/scenario-logs"), File("build/scenario-logs"))
        dirs.filter { it.isDirectory }.flatMap { it.listFiles()!!.toList() }.forEach { f ->
            assertTrue("${f.name} holds a coordinate-like pair", !coordinate.containsMatchIn(f.readText()))
        }
    }
}
