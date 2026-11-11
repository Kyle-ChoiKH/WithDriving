package io.vroomi.character.hangar

import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.min

/**
 * HANGAR: a lit hangar where the user's own giant robot is assembled by the distance driven (spec 5.12). Pure Kotlin.
 *
 * - Assembly: 30 km of real driving build one unit, 0 → 100 %. Only distance counts: driving faster never
 *   builds faster. The order is frame → inner machinery → armour → paint → markings ([stage]). A finished unit
 *   stands ready for half a kilometre, then the next unit starts on the same gantry.
 * - The progress is kept across restarts ([persist]). A demo shows the build from the armour stage on its own
 *   shown values; the saved assembly never moves during a demo ([demo]).
 * - The car: speed lights the reactor and turns the cooling fan (capped at 110 km/h), acceleration vents steam,
 *   a blinker lights that shoulder's beacon and turns the head a little, the brake dips the knees, harsh braking
 *   closes the gantry clamps under an amber rotating lamp, a star sends the crane with a small part, a
 *   kilometre lifts an armour plate, arriving makes the robot wave.
 */
class HangarSim : ThemeSim() {

    /** This unit's assembly 0..1 as shown (the demo's own value during a demo). */
    var progress = 0f; private set
    /** Which unit is on the gantry, from 1. */
    var unit = 1; private set
    /** 0 frame, 1 inner machinery, 2 armour, 3 paint, 4 markings, 5 complete. */
    val stage: Int get() = stageOf(progress)
    /** How far the present stage has come, 0..1 (the painter brings parts in with it). */
    val stageProgress: Float get() {
        val s = stage
        if (s >= 5) return 1f
        val lo = if (s == 0) 0f else STAGE_ENDS[s - 1]
        return ((progress - lo) / (STAGE_ENDS[s] - lo)).coerceIn(0f, 1f)
    }

    // The saved assembly (what persist() writes): untouched while a demo plays.
    private var savedProgress = 0f
    private var savedUnit = 1
    private var savedHoldKm = 0f
    private var holdKm = 0f
    /** True while a demo plays: the shown assembly runs on its own, the saved one stands still. */
    var demo = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) { progress = savedProgress; unit = savedUnit; holdKm = savedHoldKm }
        }

    /** A demo starts from the armour stage (or [from]) and finishes within a short drive. */
    fun startDemo(from: Float = 0.6f) {
        demo = true
        progress = from.coerceIn(0f, 1f); unit = savedUnit; holdKm = 0f
    }

    private var lastKm = -1f

    /** The reactor's light 0.2..1 and the cooling fan's angle (rad): both follow the speed up to 110 km/h. */
    var reactor = 0.2f; private set
    var fanAngle = 0.0; private set
    /** Steam from the back vents 0..1 (under acceleration). */
    var steam = 0f; private set
    /** The shoulder beacons follow the car's blinkers. */
    val beaconL: Float get() = blinkL
    val beaconR: Float get() = blinkR
    /** −1 turned left .. 1 turned right (a small turn toward the blinker). */
    var headTurn = 0f; private set
    /** 0 standing .. 1 knees dipped (the brake). */
    var kneeDip = 0f; private set
    /** 0 open .. 1 the gantry clamps closed (for a few seconds after harsh braking). */
    var clamp = 0f; private set
    /** The amber lamp's angle while the clamps are closed (it turns slowly, it never flashes). */
    var lampAngle = 0.0; private set
    /** 0..1 waving (after arriving). */
    val wave: Float get() { val t = since(arrivalAt).toFloat(); return if (t in 0f..WAVE_S) 1f else 0f }
    /** The visor's light 0.35..1 (brighter with the headlights on). */
    var visor = 0.35f; private set
    /** The hangar's ventilation fan angle (it follows the climate fan). */
    var ventAngle = 0.0; private set

    override val allChips: List<String> = listOf(CHIP_BUILD, CHIP_ARMOUR, CHIP_PAINT, CHIP_DONE, CHIP_REACTOR, CHIP_IDLE)

    override fun onStart(nowMs: Long) { lastKm = input.journey.tripKm }
    override fun onTripStart() { lastKm = input.journey.tripKm }

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        // ---- assembly: the distance since the last substep, nothing else
        val km = input.journey.tripKm
        val d = if (lastKm < 0f) 0f else (km - lastKm).coerceIn(0f, MAX_STEP_KM)
        lastKm = km
        if (d > 0f) {
            if (demo) build(d, DEMO_KM) else {
                build(d, UNIT_KM)
                savedProgress = progress; savedUnit = unit; savedHoldKm = holdKm
            }
        }

        // ---- the robot's reactions
        val v = min(kmh, SPRINT_CAP_KMH) / SPRINT_CAP_KMH
        reactor = approach(reactor, 0.2f + 0.8f * v, (dt / 0.8).toFloat())
        if (!reducedMotion) fanAngle += ddt * (if (calm) 0.5 else 1.0) * (0.6 + 7.0 * v)
        ventAngle += ddt * (0.4 + 5.0 * fan)
        steam = approach(steam, ((accel - 0.6f) / 1.6f).coerceIn(0f, 1f), (dt / 0.6).toFloat())
        val side = when (turn) { TurnSignal.LEFT -> -1f; TurnSignal.RIGHT -> 1f; else -> 0f }
        headTurn = approach(headTurn, side * 0.7f, (dt / 0.5).toFloat())
        kneeDip = approach(kneeDip, if (car.brakePressed == true) 1f else 0f, (dt / 0.4).toFloat())
        val held = since(harshAt) in 0.0..CLAMP_S
        clamp = approach(clamp, if (held) 1f else 0f, (dt / 0.5).toFloat())
        if (clamp > 0.01f) lampAngle += ddt * 2.4
        visor = approach(visor, if (car.headlightsOn == true) 1f else 0.35f, (dt / 0.5).toFloat())
    }

    /** Adds [km] of driving to the shown unit, [fullKm] being a whole unit's distance. */
    private fun build(km: Float, fullKm: Float) {
        if (progress >= COMPLETE) {
            // A finished unit stands ready; after half a kilometre the next one starts (not in a demo).
            if (demo) return
            holdKm += km
            if (holdKm >= HOLD_KM) { unit++; progress = (holdKm - HOLD_KM) / fullKm; holdKm = 0f }
            return
        }
        progress += km / fullKm
        if (progress >= COMPLETE) { progress = 1f; holdKm = 0f }
    }

    override fun chooseChip(): String? = when {
        parked -> CHIP_IDLE
        stage >= 5 -> CHIP_DONE
        kmh > 80f || accel > 1.2f -> CHIP_REACTOR
        stage == 2 -> CHIP_ARMOUR
        stage >= 3 -> CHIP_PAINT
        moving -> CHIP_BUILD
        else -> null
    }

    /** "p=<progress>;u=<unit>" of the saved assembly (never the demo's). */
    override fun persist(): String = "p=%.4f;u=%d".format(java.util.Locale.US, savedProgress, savedUnit)

    override fun restore(state: String) {
        for (kv in state.split(';')) {
            val i = kv.indexOf('='); if (i < 0) continue
            when (kv.substring(0, i).trim()) {
                "p" -> kv.substring(i + 1).toFloatOrNull()?.let { savedProgress = it.coerceIn(0f, 1f) }
                "u" -> kv.substring(i + 1).toIntOrNull()?.let { savedUnit = it.coerceAtLeast(1) }
            }
        }
        savedHoldKm = 0f
        if (!demo) { progress = savedProgress; unit = savedUnit; holdKm = 0f }
    }

    companion object {
        const val CHIP_BUILD = "조립 중"
        const val CHIP_ARMOUR = "장갑을 달아요"
        const val CHIP_PAINT = "도색 중"
        const val CHIP_DONE = "완성!"
        const val CHIP_REACTOR = "동력로 가동"
        const val CHIP_IDLE = "정비 대기"

        /** Real kilometres for one unit. */
        const val UNIT_KM = 30f
        /** A demo's whole build in kilometres (so a 90-second showcase reaches the finished robot). */
        const val DEMO_KM = 2.2f
        /** A finished unit stands this long before the next starts. */
        const val HOLD_KM = 0.5f
        const val COMPLETE = 0.9995f
        /** Where each stage ends: frame, inner machinery, armour, paint, markings. */
        val STAGE_ENDS = floatArrayOf(0.2f, 0.45f, 0.7f, 0.9f, 1.0f)
        const val SPRINT_CAP_KMH = 110f
        const val CLAMP_S = 5.0
        const val WAVE_S = 5f
        /** A jump in the trip distance larger than this in one substep is not driving (a new trip, a restore). */
        const val MAX_STEP_KM = 0.05f

        fun stageOf(p: Float): Int = when {
            p >= COMPLETE -> 5
            p >= STAGE_ENDS[3] -> 4
            p >= STAGE_ENDS[2] -> 3
            p >= STAGE_ENDS[1] -> 2
            p >= STAGE_ENDS[0] -> 1
            else -> 0
        }
    }
}
