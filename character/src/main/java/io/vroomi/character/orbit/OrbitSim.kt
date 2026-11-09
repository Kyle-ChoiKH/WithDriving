package io.vroomi.character.orbit

import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A tiny fictional star system (pure Kotlin). The car is the small craft "VR-1" orbiting a blue-green planet:
 * one lap is one kilometre driven, so the orbital speed follows the car's speed (capped at 120 km/h) and laps are
 * distance, never speed. Reverse flies retrograde; P docks at the ring station. Stars become moonlets that obey
 * Kepler's third law against each other (T = T0 × (r ÷ r0)^1.5 at radii spaced by 1.45).
 */
class OrbitSim : ThemeSim() {

    /** Craft angle on its orbit (rad, prograde = increasing). */
    var angle = 0.0; private set
    var retrograde = false; private set
    var docked = 0f; private set
    var plume = 0f; private set
    var retro = 0f; private set
    var wobbleAt = -100.0; private set
    var rcsAt = -100.0; private set
    var rcsSide = TurnSignal.NONE; private set
    var panels = 0f; private set
    var lamps = 0f; private set
    var laps = 0; private set
    var lapAt = -100.0; private set
    var windPhase = 0.0; private set
    var meteorAt = -100.0; private set
    var meteorSeed = 0; private set

    // Moonlets.
    var moonlets = 0; private set
    val moonAngle = DoubleArray(MAX_MOONLETS)
    val moonBornAt = DoubleArray(MAX_MOONLETS)
    var conjunctionAt = -100.0; private set
    var ringGlow = 0f; private set
    var ringAt = -100.0; private set

    private var lastMeters = -1f
    private var nextMeteor = 0.0
    private var arrivalChipUntil = -1.0
    private var moonChipUntil = -1.0
    private var conjChipUntil = -1.0
    private var lapChipUntil = -1.0

    override val allChips: List<String> = listOf("도킹", "위성 탄생", "행성 정렬", "한 바퀴 완주", "태양광 충전", "유성우", "역행 궤도", "궤도 비행")

    override fun onStart(nowMs: Long) { lastMeters = input.journey.tripKm * 1000f; angle = STATION }
    override fun onTripStart() { lastMeters = input.journey.tripKm * 1000f; moonlets = 0; ringGlow = 0f }

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        val d = ddt
        val meters = input.journey.tripKm * 1000f
        if (lastMeters < 0f || meters < lastMeters) lastMeters = meters
        val dm = (meters - lastMeters).toDouble(); lastMeters = meters
        retrograde = gear == GearState.REVERSE
        val parked = gear == GearState.PARK
        if (!parked) {
            // 1 km = one lap (2π); capped at 120 km/h.
            val capped = min(dm, (120.0 / 3.6) * dt)
            angle += capped / LAP_M * 2 * PI * (if (retrograde) -1.0 else 1.0) * (1.0 - docked)
            docked = approach(docked, 0f, (dt / 2.0).toFloat())
        } else {
            // Glide to the station on the shorter arc, then dock.
            var delta = (STATION - angle) % (2 * PI)
            if (delta > PI) delta -= 2 * PI
            if (delta < -PI) delta += 2 * PI
            val step = (0.6 * d).coerceAtMost(abs(delta)) * Math.signum(delta)
            angle += step
            docked = approach(docked, if (abs(delta) < 0.05) 1f else 0f, (dt / 2.5).toFloat())
        }
        plume = approach(plume, if (!parked && accel > 0.3f) ((accel - 0.3f) / 2.2f).coerceIn(0.15f, 1f) else 0f, (dt / 0.3).toFloat())
        val braking = car.brakePressed == true || accel < -0.6f
        retro = approach(retro, if (!parked && braking && kmh > 3f) 1f else 0f, (dt / 0.25).toFloat())
        panels = approach(panels, if (charging > 0.5f) 1f else 0f, (dt / 2.0).toFloat())
        lamps = approach(lamps, if (night > 0.5f && docked < 0.5f) 1f else 0.15f, (dt / 1.0).toFloat())
        windPhase += ddt * (0.05 + 0.4 * fan) * (if (calm) 0.5 else 1.0)

        // Meteor shower while the wipers run: at most one soft streak every 2 s (flash-guarded).
        if (rain > 0.5f && !reducedMotion && decoTime >= nextMeteor && flash.allow(REGION_SCENE, time)) {
            meteorAt = decoTime; meteorSeed++; nextMeteor = decoTime + 2.0 + (meteorSeed % 3) * 0.4
        }

        // Moonlets: Kepler periods; a combo lines them up for a few seconds.
        val conj = since(conjunctionAt)
        for (k in 0 until moonlets) {
            val level = k % LEVELS
            val period = period(level)
            if (conj in 0.0..CONJ_S) {
                // Drift toward a common angle, then let go.
                val target = CONJ_ANGLE
                var delta = (target - moonAngle[k]) % (2 * PI)
                if (delta > PI) delta -= 2 * PI
                if (delta < -PI) delta += 2 * PI
                moonAngle[k] += delta * min(1.0, d * 1.2)
            } else moonAngle[k] += 2 * PI / period * d * (if (reducedMotion) 0.0 else 1.0)
        }
        ringGlow = approach(ringGlow, if (ringAt > 0) 1f else 0f, (dt / 3.0).toFloat())
    }

    override fun onStar() {
        if (moonlets >= MAX_MOONLETS) return
        moonAngle[moonlets] = angle + PI * 0.3 * moonlets
        moonBornAt[moonlets] = decoTime
        moonlets++
        moonChipUntil = time + 4.0
    }

    override fun onCombo() { conjunctionAt = decoTime; ringAt = decoTime; conjChipUntil = time + 5.0 }
    override fun onHarsh() { wobbleAt = decoTime }
    override fun onBlink(side: TurnSignal) { rcsAt = decoTime; rcsSide = side }
    override fun onMilestone(km: Int) { laps = km; lapAt = decoTime; lapChipUntil = time + 4.0 }
    override fun onArrival() { arrivalChipUntil = time + 6.0 }

    /** One small damped wobble after a harsh brake (rad). */
    fun wobble(): Float {
        val t = since(wobbleAt)
        if (t < 0 || t > 2.0) return 0f
        return (exp(-t / 0.4) * sin(2 * PI * 1.6 * t) * 0.12).toFloat()
    }

    /** RCS puff 0..1 at the blinker's phase (one per blink, ≤ 1.5 Hz). */
    fun rcs(): Float {
        val t = since(rcsAt)
        return if (t < 0 || t > 0.4) 0f else (1.0 - t / 0.4).toFloat()
    }

    fun meteor(): Float {
        val t = since(meteorAt)
        return if (t < 0 || t > 1.6) -1f else (t / 1.6).toFloat()
    }

    override fun chooseChip(): String? = when {
        time < arrivalChipUntil || (docked > 0.9f && gear == GearState.PARK && arrivalChipUntil > 0) -> "도킹"
        time < conjChipUntil -> "행성 정렬"
        time < moonChipUntil -> "위성 탄생"
        time < lapChipUntil -> "한 바퀴 완주"
        charging > 0.5f -> "태양광 충전"
        moving && rain > 0.5f -> "유성우"
        moving && retrograde -> "역행 궤도"
        moving && kmh > 3f -> "궤도 비행"
        else -> null
    }

    companion object {
        const val LAP_M = 1000.0
        const val MAX_MOONLETS = 12
        const val LEVELS = 4
        const val RATIO = 1.45
        /** Visual period of the innermost moonlet (s). */
        const val T0 = 24.0
        const val CONJ_S = 6.0
        const val CONJ_ANGLE = -PI / 2
        /** Where the ring station waits on the orbit. */
        const val STATION = 0.0

        fun radiusRatio(level: Int): Double = RATIO.pow(level)
        fun period(level: Int): Double = T0 * radiusRatio(level).pow(1.5)
    }
}
