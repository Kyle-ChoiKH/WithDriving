package io.vroomi.core.vehicle.sim

import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import org.json.JSONObject

/**
 * A scripted drive. Keyframes are sparse: each one sets only the fields it names.
 * - speed and naviRemainingM are linearly interpolated between keyframes that define them
 * - every other field is held (step) from the last keyframe that set it
 * - "arrive": true is a one-shot event that stamps naviArrivedAtMs
 * - "caption" (optional, Korean) explains the moment on screen during the demo drive
 */
data class Scenario(
    val id: String,
    val title: String,
    val durationS: Float,
    val keyframes: List<Keyframe>,
    val description: String = "",
    /** Sort order in the demo picker (lower first). */
    val order: Int = 100,
    /** Optional scene theme shown for this demo only ("KART", "CALIBRE", "ENGINE"); null = the user's own. */
    val theme: String? = null,
    /** Optional powertrain for the ENGINE theme during this demo ("ICE" or "EV"). */
    val powertrain: String? = null,
    /** Optional scripted route for the TREASURE_MAP demo: metres (east, north) pairs from the start. */
    val route: FloatArray? = null,
    /** Optional theme upgrade step shown during this demo (0..4 = Lv.1..5, whatever the grade); null = the app's default. */
    val model: Int? = null,
)

data class Keyframe(val t: Float, val values: Map<String, Any>)

object ScenarioParser {
    fun parse(json: String): Scenario {
        val o = JSONObject(json)
        val arr = o.getJSONArray("keyframes")
        val frames = ArrayList<Keyframe>(arr.length())
        for (i in 0 until arr.length()) {
            val k = arr.getJSONObject(i)
            val map = HashMap<String, Any>()
            val it = k.keys()
            while (it.hasNext()) {
                val key = it.next()
                if (key != "t" && key != "note") map[key] = k.get(key)
            }
            frames += Keyframe(k.getDouble("t").toFloat(), map)
        }
        frames.sortBy { it.t }
        val duration = o.optDouble("durationS", (frames.lastOrNull()?.t ?: 0f).toDouble()).toFloat()
        return Scenario(
            id = o.getString("id"),
            title = o.optString("title", o.getString("id")),
            durationS = duration,
            keyframes = frames,
            description = o.optString("description", ""),
            order = o.optInt("order", 100),
            theme = o.optString("theme", "").uppercase().takeIf { it.isNotBlank() },
            powertrain = o.optString("powertrain", "").uppercase().takeIf { it.isNotBlank() },
            route = o.optJSONArray("route")?.let { r ->
                FloatArray(r.length() * 2) { i -> r.getJSONArray(i / 2).getDouble(i % 2).toFloat() }
            },
            model = if (o.has("model") && !o.isNull("model")) o.getInt("model") else null,
        )
    }
}

/** Pure sampler: what the car looks like t seconds into the scenario. */
class ScenarioSampler(private val scenario: Scenario) {

    fun sample(tS: Float, base: VehicleSnapshot, nowMs: Long, startedAtMs: Long): VehicleSnapshot {
        var s = base
        val frames = scenario.keyframes
        // Step fields: apply every keyframe up to t, in order.
        for (f in frames) {
            if (f.t > tS) break
            s = applyStep(s, f.values, startedAtMs + (f.t * 1000).toLong())
        }
        // Interpolated fields.
        interpolate("speed", tS)?.let { s = s.copy(speedKmh = it.toFloat()) }
        interpolate("naviRemainingM", tS)?.let { s = s.copy(naviRemainingM = it.toInt()) }
        return s.copy(timestampMs = nowMs)
    }

    /** Caption of the latest keyframe that has one, shown for [holdS] seconds. */
    fun captionAt(tS: Float, holdS: Float = 7f): String? {
        var c: String? = null
        var at = 0f
        for (f in scenario.keyframes) {
            if (f.t > tS) break
            (f.values["caption"] as? String)?.let { c = it; at = f.t }
        }
        return c?.takeIf { tS - at <= holdS }
    }

    private fun interpolate(key: String, t: Float): Double? {
        var prev: Keyframe? = null
        for (f in scenario.keyframes) {
            if (!f.values.containsKey(key)) continue
            if (f.t <= t) {
                prev = f
            } else {
                val p = prev ?: return num(f.values[key])
                val a = num(p.values[key]) ?: return null
                val b = num(f.values[key]) ?: return a
                val u = ((t - p.t) / (f.t - p.t)).coerceIn(0f, 1f)
                return a + (b - a) * u
            }
        }
        return prev?.let { num(it.values[key]) }
    }

    private fun applyStep(s0: VehicleSnapshot, v: Map<String, Any>, frameMs: Long): VehicleSnapshot {
        var s = s0
        for ((k, raw) in v) {
            s = when (k) {
                "brake" -> s.copy(brakePressed = bool(raw))
                "gear" -> s.copy(gear = gear(raw))
                "fan" -> s.copy(fanLevel = num(raw)?.toInt())
                "temp" -> s.copy(setTempC = num(raw)?.toFloat())
                "cabinTemp" -> s.copy(cabinTempC = num(raw)?.toFloat())
                "ac" -> s.copy(acOn = bool(raw))
                "maxAc" -> s.copy(maxAcOn = bool(raw))
                "defrost" -> s.copy(defrostOn = bool(raw))
                "maxDefrost" -> s.copy(maxDefrostOn = bool(raw))
                "airCleaner" -> s.copy(airCleanerOn = bool(raw))
                "recirc" -> s.copy(recircOn = bool(raw))
                "hvacAuto" -> s.copy(hvacAutoOn = bool(raw))
                "seatHeat" -> s.copy(seatHeatLevel = num(raw)?.toInt())
                "seatVent" -> s.copy(seatVentLevel = num(raw)?.toInt())
                "turn" -> s.copy(turnSignal = turn(raw))
                "wiper" -> s.copy(wiperActive = bool(raw))
                "lights" -> s.copy(headlightsOn = bool(raw))
                "door" -> s.copy(doorOpen = bool(raw))
                "evCharging" -> s.copy(evCharging = bool(raw))
                "battery" -> s.copy(evBatteryPct = num(raw)?.toInt())
                "mode" -> s.copy(drivingMode = mode(raw))
                "cruise" -> s.copy(cruiseOn = bool(raw))
                "navi" -> s.copy(naviGuidanceActive = bool(raw))
                "arrive" -> if (bool(raw) == true) s.copy(naviArrivedAtMs = frameMs, naviGuidanceActive = false) else s
                "safety" -> s.copy(safetyWarningActive = bool(raw))
                else -> s // speed / naviRemainingM handled by interpolation; unknown keys ignored
            }
        }
        return s
    }

    private fun num(x: Any?): Double? = (x as? Number)?.toDouble()
    private fun bool(x: Any?): Boolean? = x as? Boolean
    private fun gear(x: Any?): GearState = when ((x as? String)?.uppercase()) {
        "P" -> GearState.PARK; "R" -> GearState.REVERSE; "N" -> GearState.NEUTRAL; "D" -> GearState.DRIVE
        else -> GearState.UNKNOWN
    }
    private fun turn(x: Any?): TurnSignal = when ((x as? String)?.uppercase()) {
        "LEFT" -> TurnSignal.LEFT; "RIGHT" -> TurnSignal.RIGHT; "HAZARD" -> TurnSignal.HAZARD
        else -> TurnSignal.NONE
    }
    private fun mode(x: Any?): DriveModeState = when ((x as? String)?.uppercase()) {
        "ECO" -> DriveModeState.ECO; "SPORT" -> DriveModeState.SPORT; "NORMAL" -> DriveModeState.NORMAL
        else -> DriveModeState.UNKNOWN
    }
}
