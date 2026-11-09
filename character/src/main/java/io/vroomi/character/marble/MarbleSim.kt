package io.vroomi.character.marble

import io.vroomi.character.marble.MarbleLayout.A
import io.vroomi.character.marble.MarbleLayout.BYPASS
import io.vroomi.character.marble.MarbleLayout.CHIME
import io.vroomi.character.marble.MarbleLayout.GOLD
import io.vroomi.character.marble.MarbleLayout.L
import io.vroomi.character.marble.MarbleLayout.LOOP
import io.vroomi.character.marble.MarbleLayout.P
import io.vroomi.character.marble.MarbleLayout.R
import io.vroomi.character.marble.MarbleLayout.RET
import io.vroomi.character.theme.FixedStepper
import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A wall-mounted marble machine driven by the car (pure Kotlin, deterministic, fixed step 1/240 s).
 * A marble on a rail moves in 1D with the rolling-ball result a = (5 ÷ 7)·g·sinθ − c·v (I = 2/5 m r²); marbles change
 * rail only at defined junctions and stations, so they never fall through. 24 steel marbles circulate (the count
 * is conserved); gold marbles join for stars and retire at the trip's end. Energy never rises, except on the lift.
 */
class MarbleSim(private val sound: io.vroomi.character.theme.SoundEvents = io.vroomi.character.theme.SoundEvents()) : ThemeSim(), io.vroomi.character.theme.SoundSource by sound {

    val tracks = MarbleLayout.tracks
    val state = IntArray(MAX) { if (it < STEEL) HOPPER else GONE }
    val rail = IntArray(MAX)
    val pos = FloatArray(MAX)
    val vel = FloatArray(MAX)
    val gold = BooleanArray(MAX) { it >= STEEL }
    val slotOf = IntArray(MAX) { -1 }
    private val hopperQueue = IntArray(MAX)
    private var hopperHead = 0
    private var hopperCount = 0

    /** Lift rotation in slots (one slot rises past the top per unit). */
    var liftPhase = 0.0; private set
    val slotMarble = IntArray(SLOTS) { -1 }
    private var lastWrap = 0L

    var brakeLever = 0f; private set
    var stoppersAt = -100.0; private set
    var flipLeft = true; private set
    var pendAngle = 0f; private set
    var cupMarble = -1; private set
    private var pendTime = 0.0
    var windmill = 0.0; private set
    var gears = 0.0; private set
    var solar = 0.0; private set
    var beads = 0; private set
    var beadAt = -100.0; private set
    var tray = 0; private set
    var chimeUntil = -100.0; private set
    val chimeAt = DoubleArray(CHIME_BARS) { -100.0 }
    var coolGlow = 0f; private set
    var lamps = 0f; private set
    private var retiring = false
    private val physics = FixedStepper(1.0 / 240.0)
    private val pt = FloatArray(2)

    private var goldChipUntil = -1.0
    private var chimeChipUntil = -1.0
    private var arrivalChipUntil = -1.0
    private var queueChip = false

    override val allChips: List<String> = listOf("도착", "줄무늬 구슬 출발", "차임 사다리", "구슬 대기 중", "멈춤 핀", "진자 운반", "되돌이 고리", "구슬이 돌아요")

    override fun onStart(nowMs: Long) {
        for (i in 0 until STEEL) { state[i] = HOPPER; pushHopper(i) }
    }

    override fun onTripStart() { tray = 0; retiring = false; beads = 0 }

    private fun pushHopper(i: Int) {
        state[i] = HOPPER; vel[i] = 0f
        hopperQueue[(hopperHead + hopperCount) % MAX] = i; hopperCount++
    }

    private fun popHopper(): Int {
        if (hopperCount == 0) return -1
        val i = hopperQueue[hopperHead]; hopperHead = (hopperHead + 1) % MAX; hopperCount--
        return i
    }

    val inHopper: Int get() = hopperCount

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        val braking = car.brakePressed == true
        brakeLever = approach(brakeLever, if (braking) 1f else 0f, (dt / 0.2).toFloat())
        coolGlow = approach(coolGlow, ac, (dt / 0.8).toFloat())
        lamps = approach(lamps, if (night > 0.5f) 1f else 0f, (dt / 1.0).toFloat())
        windmill += ddt * (0.3 + 5.0 * fan) * (if (calm) 0.5 else 1.0)
        gears += ddt * (0.2 + 1.2 * (kmh / 100f).coerceIn(0f, 1f)) * (if (mode < -0.3f) 0.5 else 1.0)
        if (charging > 0.5f) solar += ddt * 0.6

        // The physics runs at 1/240 s inside the theme's 1/120 s step (deterministic).
        physics.accumulate(ddt.toFloat())
        while (physics.take()) stepPhysics(physics.stepS)
        var waiting = 0
        for (i in 0 until MAX) if (state[i] == TRACK && rail[i] == A && vel[i] == 0f) waiting++
        queueChip = braking && waiting >= 3
        chimes()
    }

    private fun liftRate(): Double = when {
        retiring -> 0.0
        gear == GearState.PARK && charging > 0.5f -> 0.25 // the small solar wheel turns it slowly
        else -> 2.0 * (kmh / 120f).coerceIn(0f, 1f).toDouble()
    } * (if (calm) 0.5 else 1.0) * (if (reducedMotion) 0.0 else 1.0)

    private fun stepPhysics(h: Double) {
        val hf = h.toFloat()
        // Lift: slots rise; a slot passing the bottom loads from the hopper, passing the top unloads onto rail A.
        val before = liftPhase
        liftPhase += liftRate() * h
        val k0 = floor(before).toLong(); val k1 = floor(liftPhase).toLong()
        for (k in k0 + 1..k1) {
            // Slot j is at the top when phase + j ≡ 0 (mod SLOTS) and at the bottom half a turn later.
            val top = Math.floorMod(-k, SLOTS.toLong()).toInt()
            val m = slotMarble[top]
            if (m >= 0) { slotMarble[top] = -1; if (spaceAt(A, 0f)) enter(m, A, 0f, 40f) else pushHopper(m) }
            // The slot just past the top comes back to the bottom (half a turn later) — it loads now.
            val bottom = Math.floorMod(SLOTS / 2 - k, SLOTS.toLong()).toInt()
            if (slotMarble[bottom] < 0 && !retiring) {
                val i = popHopper()
                if (i >= 0) { slotMarble[bottom] = i; state[i] = LIFT; slotOf[i] = bottom }
            }
        }

        // Pendulum (wipers): 1.6 s period; it ferries one marble from side A (right) to side B (left).
        if (rain > 0.5f && !reducedMotion) {
            pendTime += h
            pendAngle = (MarbleLayout.PEND_AMP * sin(2 * PI * pendTime / PENDULUM_S)).toFloat()
        } else {
            pendAngle *= (1f - min(1f, hf * 2f))
            if (cupMarble >= 0) { enter(cupMarble, P, 0f, 20f); cupMarble = -1 }
        }
        if (cupMarble >= 0 && pendAngle < -MarbleLayout.PEND_AMP * 0.85f && spaceAt(P, 0f)) { enter(cupMarble, P, 0f, 20f); cupMarble = -1 }

        val hold = since(stoppersAt) in 0.0..1.0
        val g = G; val c = FRICTION
        for (i in 0 until MAX) {
            if (state[i] != TRACK) continue
            val t = tracks[rail[i]]
            if (hold) { vel[i] = 0f; continue }
            val h0 = t.heightAt(pos[i]); val v0 = vel[i]
            val a = (5f / 7f) * g * t.sinSlope(pos[i]) - c * v0
            var v = (v0 + a * hf).coerceAtLeast(0f)
            var s = pos[i] + v * hf
            // Brake station on rail A: the lever stops the marbles at its line.
            if (rail[i] == A && brakeLever > 0.5f) {
                val line = t.length * MarbleLayout.BRAKE_AT
                if (pos[i] <= line && s > line) { s = line; v = 0f }
            }
            // Energy never rises on a rail: clamp the speed to what the drop allows.
            val h1 = t.heightAt(s)
            val vMax2 = v0 * v0 + (10f / 7f) * g * (h1 - h0)
            if (v * v > vMax2) v = sqrt(max(0f, vMax2))
            pos[i] = s; vel[i] = v
            if (s >= t.length) arrive(i)
        }
        keepSpacing()
    }

    /** Marbles on the same rail never overlap: a follower stops behind the one in front. */
    private fun keepSpacing() {
        for (r in tracks.indices) {
            for (i in 0 until MAX) {
                if (state[i] != TRACK || rail[i] != r) continue
                for (j in 0 until MAX) {
                    if (j == i || state[j] != TRACK || rail[j] != r) continue
                    val gap = pos[j] - pos[i]
                    if (gap in 0f..GAP && !(gap == 0f && j < i)) { pos[i] = max(0f, pos[j] - GAP); vel[i] = min(vel[i], vel[j]) }
                }
            }
        }
    }

    private fun spaceAt(r: Int, s: Float): Boolean {
        for (j in 0 until MAX) if (state[j] == TRACK && rail[j] == r && abs(pos[j] - s) < GAP) return false
        return true
    }

    private fun enter(i: Int, r: Int, s: Float, v: Float) { state[i] = TRACK; rail[i] = r; pos[i] = s; vel[i] = v; slotOf[i] = -1 }

    /** End of a rail: the junction decides where the marble goes next (or it waits at the end). */
    private fun arrive(i: Int) {
        val t = tracks[rail[i]]
        val v = vel[i]
        fun go(r: Int, s: Float = 0f): Boolean = if (spaceAt(r, s)) { enter(i, r, s, max(20f, v * 0.8f)); true } else { pos[i] = t.length; vel[i] = 0f; false }
        when (rail[i]) {
            A -> {
                val chime = gold[i] && decoTime < chimeUntil
                val next = when {
                    chime -> CHIME
                    turn == TurnSignal.LEFT -> L
                    turn == TurnSignal.RIGHT -> R
                    else -> { flipLeft = !flipLeft; if (flipLeft) L else R }
                }
                go(next)
            }
            L -> if (rain > 0.5f && !reducedMotion) {
                // Wait for the cup on side A.
                if (cupMarble < 0 && pendAngle > MarbleLayout.PEND_AMP * 0.85f) { cupMarble = i; state[i] = CUP }
                else { pos[i] = t.length; vel[i] = 0f }
            } else go(BYPASS)
            BYPASS -> go(P, tracks[P].nearest(t.xs.last(), t.ys.last()))
            P, RET -> toHopper(i)
            R -> if (gear == GearState.REVERSE) go(LOOP) else go(RET)
            LOOP -> go(RET, tracks[RET].nearest(t.xs.last(), t.ys.last()))
            CHIME -> go(RET)
            GOLD -> go(RET, tracks[RET].nearest(t.xs.last(), t.ys.last()))
        }
    }

    private fun toHopper(i: Int) {
        sound.push(0, 0.25f, 1)
        if (gold[i] && retiring) { state[i] = TRAY; tray++; return }
        pushHopper(i)
    }

    /** Chime bars light as a gold marble passes each corner of the ladder (flash-guarded). */
    private fun chimes() {
        val t = tracks[CHIME]
        for (i in 0 until MAX) {
            if (state[i] != TRACK || rail[i] != CHIME) continue
            val bar = ((pos[i] / t.length) * CHIME_BARS).toInt().coerceIn(0, CHIME_BARS - 1)
            if (decoTime - chimeAt[bar] > 0.6 && flash.allow(REGION_SCENE + bar % 3, time)) {
                chimeAt[bar] = decoTime
                sound.push(CHIME_NOTES[bar], 0.55f, 0)
            }
        }
    }

    override fun chooseChip(): String? {
        return when {
            time < arrivalChipUntil -> "도착"
            time < chimeChipUntil -> "차임 사다리"
            time < goldChipUntil -> "줄무늬 구슬 출발"
            since(stoppersAt) in 0.0..1.5 -> "멈춤 핀"
            queueChip -> "구슬 대기 중"
            moving && gear == GearState.REVERSE -> "되돌이 고리"
            moving && rain > 0.5f -> "진자 운반"
            moving && kmh > 3f -> "구슬이 돌아요"
            else -> null
        }
    }

    override fun onStar() {
        val i = (STEEL until MAX).firstOrNull { state[it] == GONE } ?: return
        if (!spaceAt(GOLD, 0f)) return
        enter(i, GOLD, 0f, 30f)
        goldChipUntil = time + 4.0
    }

    override fun onCombo() { chimeUntil = decoTime + 14.0; chimeChipUntil = time + 5.0 }
    override fun onHarsh() { stoppersAt = decoTime }
    override fun onMilestone(km: Int) { beads = min(20, beads + 1); beadAt = decoTime }
    override fun onArrival() { retiring = true; arrivalChipUntil = time + 6.0 }

    /** Marble position in machine units. */
    fun where(i: Int, out: FloatArray) {
        when (state[i]) {
            TRACK -> tracks[rail[i]].point(pos[i], out)
            LIFT -> { val u = slotHeight(slotOf[i]); out[0] = MarbleLayout.LIFT_X; out[1] = MarbleLayout.LIFT_BOTTOM - u * (MarbleLayout.LIFT_BOTTOM - MarbleLayout.LIFT_TOP) }
            CUP -> cupPoint(out)
            else -> { out[0] = -100f; out[1] = -100f }
        }
    }

    /** 0 bottom .. 1 top of the lift for slot [k] (half the slots rise, half return down the back). */
    fun slotHeight(k: Int): Float {
        val u = ((liftPhase + k) % SLOTS / SLOTS * 2.0).toFloat()
        return if (u <= 1f) 1f - u else u - 1f
    }

    fun cupPoint(out: FloatArray) {
        val a = Math.toRadians(pendAngle.toDouble())
        out[0] = (MarbleLayout.PEND_PIVOT_X + sin(a) * MarbleLayout.PEND_LEN).toFloat()
        out[1] = (MarbleLayout.PEND_PIVOT_Y + kotlin.math.cos(a) * MarbleLayout.PEND_LEN).toFloat()
    }

    /** Total energy per unit mass of a marble on a rail (rolling): ½·(7/5)·v² − g·h. */
    fun energy(i: Int): Float = 0.7f * vel[i] * vel[i] - G * tracks[rail[i]].heightAt(pos[i])

    fun count(stateWanted: Int, goldWanted: Boolean? = null): Int =
        (0 until MAX).count { state[it] == stateWanted && (goldWanted == null || gold[it] == goldWanted) }

    companion object {
        const val STEEL = 24
        const val GOLD_MAX = 12
        const val MAX = STEEL + GOLD_MAX
        const val SLOTS = 10
        const val G = 700f
        const val FRICTION = 1.6f
        const val GAP = 14f
        const val PENDULUM_S = 1.6
        const val CHIME_BARS = 7
        /** A descending pentatonic run for the chime bars (an original figure, not a tune). */
        val CHIME_NOTES = intArrayOf(88, 86, 83, 81, 79, 76, 74)

        const val HOPPER = 0
        const val LIFT = 1
        const val TRACK = 2
        const val CUP = 3
        const val TRAY = 4
        const val GONE = 5
    }
}
