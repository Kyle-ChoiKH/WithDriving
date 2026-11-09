package io.vroomi.app.diary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local-only diary (JSON file in app storage). Keeps the newest 200 trips. */
class DiaryStore(private val file: File) {
    private val _trips = MutableStateFlow<List<TripRecord>>(emptyList())
    val trips: StateFlow<List<TripRecord>> = _trips.asStateFlow()

    fun load() {
        _trips.value = runCatching { decode(file.readText(Charsets.UTF_8)) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(t: TripRecord) {
        val next = (listOf(t) + _trips.value).take(MAX)
        _trips.value = next
        save(next)
    }

    @Synchronized
    fun clear() {
        _trips.value = emptyList()
        save(emptyList())
    }

    private fun save(list: List<TripRecord>) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(encode(list), Charsets.UTF_8)
            if (!tmp.renameTo(file)) { file.writeText(encode(list), Charsets.UTF_8); tmp.delete() }
        }
    }

    companion object {
        private const val MAX = 200

        fun encode(list: List<TripRecord>): String {
            val arr = JSONArray()
            list.forEach { t ->
                arr.put(JSONObject().apply {
                    put("id", t.id); put("startMs", t.startMs); put("endMs", t.endMs)
                    put("distanceKm", t.distanceKm.toDouble()); put("bursts", t.bursts)
                    put("harshAccel", t.harshAccel); put("harshBrakes", t.harshBrakes)
                    put("overspeedMs", t.overspeedMs); put("climate", t.climateMoments)
                    put("rain", t.rainMoments); put("score", t.score)
                    put("stars", t.stars); put("bestCombo", t.bestCombo); put("biomes", t.biomes); put("demo", t.demo)
                    t.sketch?.let { sk -> put("sketch", org.json.JSONArray().apply { sk.forEach { v -> put((v * 1000).toInt() / 1000.0) } }) }
                })
            }
            return arr.toString()
        }

        fun decode(text: String): List<TripRecord> {
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TripRecord(
                    id = o.getLong("id"), startMs = o.getLong("startMs"), endMs = o.getLong("endMs"),
                    distanceKm = o.getDouble("distanceKm").toFloat(), bursts = o.optInt("bursts"),
                    harshAccel = o.optInt("harshAccel"), harshBrakes = o.optInt("harshBrakes"),
                    overspeedMs = o.optLong("overspeedMs"), climateMoments = o.optInt("climate"),
                    rainMoments = o.optInt("rain"), score = o.optInt("score"),
                    stars = o.optInt("stars"), bestCombo = o.optInt("bestCombo"), biomes = o.optInt("biomes", 1),
                    sketch = o.optJSONArray("sketch")?.let { arr -> List(arr.length()) { k -> arr.getDouble(k).toFloat() } },
                    demo = o.optBoolean("demo"),
                )
            }
        }
    }
}
