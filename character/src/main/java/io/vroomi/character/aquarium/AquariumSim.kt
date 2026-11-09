package io.vroomi.character.aquarium

import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A planted aquarium that rides in the car (pure Kotlin, deterministic). Six invented species; the schools follow
 * boids (separation, alignment, cohesion, bounds) around a lead point whose pace follows the car's speed (capped).
 * The water leans against the acceleration (atan(a/g) scaled, at most 4°), a harsh brake makes one fast-start
 * turn, the blinker steers the lead school, reverse drifts the schools backwards.
 * Tank units: x 0..[W] (left to right), y 0..[H] (surface to sand).
 */
class AquariumSim : ThemeSim() {

    enum class Species(val label: String, val length: Float, val schooling: Boolean) {
        /** 은빛 줄무늬떼: slender, body-and-tail undulation, schools. */
        STRIPE("은빛 줄무늬떼", 30f, true),
        /** 동글노: round, rows with its pectoral fins. */
        ROWER("동글노", 34f, false),
        /** 리본장어: long, full-body waves. */
        RIBBON("리본장어", 110f, false),
        /** 모래납작이: flat bottom-dweller. */
        FLAT("모래납작이", 52f, false),
        /** 돛지느러미: tall-finned glider. */
        SAIL("돛지느러미", 44f, true),
        /** 꼬마청소새우: tiny cleaner. */
        SHRIMP("꼬마청소새우", 16f, false),
    }

    val n = FISH
    val species = Array(FISH) { Species.STRIPE }
    val fx = FloatArray(FISH); val fy = FloatArray(FISH)
    val vx = FloatArray(FISH); val vy = FloatArray(FISH)
    /** Swim phase (rad) of the travelling body wave; size multiplier. */
    val phase = FloatArray(FISH); val size = FloatArray(FISH)
    private val home = FloatArray(FISH * 2)

    // Lead points of the two schools.
    private var leadAngle = 0.0
    var leadX = W / 2f; private set
    var leadY = H * 0.45f; private set
    private var steerX = 0f

    /** Surface tilt (deg), leaning against the acceleration. */
    var tilt = 0f; private set
    private var tiltVel = 0f
    var startleAt = -100.0; private set
    var ringAt = -100.0; private set
    var clamAt = -100.0; private set
    var gatherAt = -100.0; private set
    val decorX = FloatArray(DECOR); val decorY = FloatArray(DECOR); val decorKind = IntArray(DECOR); val decorAt = FloatArray(DECOR)
    var decor = 0; private set
    var bubblePhase = 0.0; private set
    var causticPhase = 0.0; private set
    var plantPhase = 0.0; private set
    var filterLamp = 0f; private set
    var plankton = 0f; private set

    private var rng = 0x9E3779B9.toInt()
    private var startleChipUntil = -1.0
    private var pearlChipUntil = -1.0
    private var ringChipUntil = -1.0
    private var arrivalChipUntil = -1.0

    override val allChips: List<String> = listOf("도착", "진주 발견", "물고기 원무", "깜짝 회전", "충전 중", "수면에 비", "밤의 수조", "함께 헤엄", "쉬는 중")

    override fun onStart(nowMs: Long) {
        var k = 0
        fun add(s: Species, count: Int) { repeat(count) { if (k < FISH) { species[k] = s; k++ } } }
        add(Species.STRIPE, 16); add(Species.SAIL, 4); add(Species.ROWER, 3); add(Species.RIBBON, 2); add(Species.FLAT, 2); add(Species.SHRIMP, 3)
        for (i in 0 until FISH) {
            fx[i] = 120f + rand() * (W - 240f); fy[i] = 60f + rand() * (H - 140f)
            vx[i] = (rand() - 0.5f) * 20f; vy[i] = (rand() - 0.5f) * 6f
            phase[i] = rand() * 6.28f; size[i] = 0.85f + rand() * 0.3f
            home[i * 2] = fx[i]; home[i * 2 + 1] = fy[i]
            when (species[i]) {
                Species.FLAT -> { fy[i] = H - 22f; home[i * 2 + 1] = H - 22f }
                Species.SHRIMP -> { fy[i] = H - 40f - rand() * 20f; home[i * 2] = 700f + rand() * 120f; home[i * 2 + 1] = fy[i] }
                Species.RIBBON -> { fy[i] = H - 70f - rand() * 40f; home[i * 2 + 1] = fy[i] }
                else -> Unit
            }
        }
    }

    private fun rand(): Float {
        rng = rng xor (rng shl 13); rng = rng xor (rng ushr 17); rng = rng xor (rng shl 5)
        return (rng and 0x7fffffff) / 2147483648f
    }

    /** Detail sheets only: one fish of each species lined up, facing right. */
    var lineup = false

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        val d = ddt.toFloat()
        if (d <= 0f) return
        if (lineup) {
            for (i in 0 until FISH) { fx[i] = -200f; fy[i] = -200f; vx[i] = 0f; vy[i] = 0f }
            Species.entries.forEachIndexed { k, sp ->
                val i = (0 until FISH).first { species[it] == sp }
                fx[i] = 170f + (k % 3) * 300f + sp.length; fy[i] = if (k < 3) 150f else 300f; vx[i] = 10f; phase[i] += d * 3f
            }
            return
        }
        val calmK = if (calm) 0.5f else 1f
        // Lead point: an elongated loop; its pace follows speed (capped), reverse runs it backwards.
        val pace = (0.05 + 0.25 * (kmh / 100f).coerceIn(0f, 1f)) * calmK * (if (reducedMotion) 0.0 else 1.0)
        val dir = if (gear == GearState.REVERSE) -1.0 else 1.0
        leadAngle += pace * dir * ddt * 2 * PI * 0.35
        val steerT = when { blinkL > 0.5f || (turn == TurnSignal.LEFT) -> -220f; turn == TurnSignal.RIGHT -> 220f; else -> 0f }
        steerX += (steerT - steerX) * min(1f, d / 1.5f)
        val gather = since(gatherAt) in 0.0..8.0
        leadX = if (gather) W / 2f else (W / 2f + cos(leadAngle).toFloat() * 300f + steerX).coerceIn(140f, W - 140f)
        leadY = if (gather) H * 0.48f else H * 0.42f + sin(leadAngle * 2).toFloat() * 60f
        val ring = since(ringAt) in 0.0..RING_S
        val startle = since(startleAt)
        val cruise = (40f + 140f * (kmh / 100f).coerceIn(0f, 1f)) * calmK

        for (i in 0 until FISH) {
            val sp = species[i]
            var ax = 0f; var ay = 0f
            if (sp.schooling) {
                // Boids within the same species.
                var cx = 0f; var cy = 0f; var avx = 0f; var avy = 0f; var cnt = 0
                for (j in 0 until FISH) {
                    if (j == i || species[j] != sp) continue
                    val dx = fx[j] - fx[i]; val dy = fy[j] - fy[i]
                    val dd = hypot(dx, dy)
                    if (dd < 90f) { cx += fx[j]; cy += fy[j]; avx += vx[j]; avy += vy[j]; cnt++ }
                    if (dd < 22f && dd > 0.01f) { ax -= dx / dd * (22f - dd) * 6f; ay -= dy / dd * (22f - dd) * 6f }
                }
                if (cnt > 0) {
                    ax += (cx / cnt - fx[i]) * 0.6f + (avx / cnt - vx[i]) * 0.8f
                    ay += (cy / cnt - fy[i]) * 0.6f + (avy / cnt - vy[i]) * 0.8f
                }
                // Follow the lead (the sail fish trail behind and higher), or hold a ring after a combo.
                val off = if (sp == Species.SAIL) -90f else 0f
                var tx = leadX + off; var ty = leadY + if (sp == Species.SAIL) -50f else 0f
                if (ring && sp == Species.STRIPE) {
                    val a = i * 2 * PI / 16 + since(ringAt) * 0.6
                    tx = W / 2f + cos(a).toFloat() * 110f; ty = H * 0.42f + sin(a).toFloat() * 70f
                    ax += (tx - fx[i]) * 2.4f; ay += (ty - fy[i]) * 2.4f
                } else { ax += (tx - fx[i]) * 0.35f; ay += (ty - fy[i]) * 0.35f }
            } else {
                // Solo species drift around a home point.
                val hx = if (gather && sp != Species.FLAT && sp != Species.SHRIMP) W / 2f + (i % 5 - 2) * 40f else home[i * 2]
                val hy = if (gather && sp != Species.FLAT && sp != Species.SHRIMP) H * 0.5f else home[i * 2 + 1]
                val wob = time * (0.2 + 0.05 * i)
                ax += (hx + sin(wob).toFloat() * (if (sp == Species.RIBBON) 160f else 70f) - fx[i]) * 0.25f
                ay += (hy + cos(wob * 1.3).toFloat() * (if (sp == Species.FLAT || sp == Species.SHRIMP) 2f else 25f) - fy[i]) * 0.25f
            }
            // One fast-start turn after a harsh brake: a kick away from the centre, then calm within 2 s.
            if (startle in 0.0..0.25 && sp != Species.FLAT) {
                val k = (1.0 - startle / 0.25).toFloat() * 900f
                val dx = fx[i] - W / 2f; val dy = fy[i] - H / 2f; val dd = max(1f, hypot(dx, dy))
                ax += dx / dd * k; ay += dy / dd * k * 0.5f
            }
            // Bounds: glass, surface and sand.
            if (fx[i] < 50f) ax += (50f - fx[i]) * 8f
            if (fx[i] > W - 50f) ax -= (fx[i] - (W - 50f)) * 8f
            if (fy[i] < 40f) ay += (40f - fy[i]) * 8f
            if (fy[i] > H - 18f) ay -= (fy[i] - (H - 18f)) * 8f
            vx[i] += ax * d; vy[i] += ay * d
            // Speed limits per species (the schools cruise faster with the car, capped).
            val maxV = when (sp) { Species.STRIPE -> cruise; Species.SAIL -> cruise * 0.7f; Species.RIBBON -> 50f; Species.FLAT -> 12f; Species.SHRIMP -> 10f; Species.ROWER -> 35f } *
                (if (startle in 0.0..2.0) 2.2f - startle.toFloat() * 0.6f else 1f)
            val v = hypot(vx[i], vy[i])
            if (v > maxV) { vx[i] *= maxV / v; vy[i] *= maxV / v }
            val damp = exp(-d * 0.4f); vx[i] *= damp; vy[i] *= damp
            fx[i] = (fx[i] + vx[i] * d).coerceIn(10f, W - 10f)
            fy[i] = (fy[i] + vy[i] * d).coerceIn(14f, H - 8f)
            phase[i] += d * (3f + min(v, 160f) * 0.06f) * (if (sp == Species.RIBBON) 0.6f else 1f)
        }

        // Water surface lean.
        val target = (-atan(accel / 9.81f) * 180f / PI.toFloat() * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
        val a = 40f * (target - tilt) - 9f * tiltVel
        tiltVel += a * d; tilt = (tilt + tiltVel * d).coerceIn(-MAX_TILT, MAX_TILT)

        bubblePhase += ddt * (0.4 + 1.6 * fan + charging)
        causticPhase += ddt * 0.08 * calmK
        plantPhase += ddt * (0.5 + 0.6 * fan) * calmK
        filterLamp = approach(filterLamp, if (charging > 0.5f) 1f else 0f, (dt / 1.2).toFloat())
        plankton = approach(plankton, if (night > 0.5f) 1f else 0f, (dt / 2.0).toFloat())
        for (k in 0 until decor) if (decorY[k] < SAND_Y - 6f) decorY[k] = min(SAND_Y - 6f, decorY[k] + d * 40f)
    }

    override fun onHarsh() { startleAt = decoTime; startleChipUntil = time + 4.0 }
    override fun onStar() { if (flash.allow(REGION_STAR, time)) clamAt = decoTime; pearlChipUntil = time + 4.0 }
    override fun onCombo() { ringAt = decoTime; ringChipUntil = time + 5.0 }
    override fun onArrival() { gatherAt = decoTime; arrivalChipUntil = time + 6.0 }
    override fun onMilestone(km: Int) {
        if (decor < DECOR) {
            decorX[decor] = 200f + ((km * 137) % 600).toFloat(); decorY[decor] = 30f; decorKind[decor] = km % 3; decorAt[decor] = decoTime.toFloat()
            decor++
        }
    }
    override fun onTripStart() { decor = 0 }

    /** Body-wave sample: lateral offset at fraction u (0 head .. 1 tail) of fish [i]. */
    fun wave(i: Int, u: Float): Float {
        val sp = species[i]
        val amp = when (sp) { Species.RIBBON -> 9f; Species.STRIPE -> 3.2f; Species.SAIL -> 2.6f; Species.FLAT -> 1.2f; else -> 1.6f } * (if (reducedMotion) 0f else 1f)
        val env = if (sp == Species.RIBBON) 0.3f + 0.7f * u else u * u
        return sin(phase[i] - u * (if (sp == Species.RIBBON) 9f else 5f)) * amp * env
    }

    /** Heading (rad) of fish [i] from its velocity (faces right when still). */
    fun heading(i: Int): Float {
        val v = hypot(vx[i], vy[i])
        return if (v < 2f) (if (vx[i] < 0f) PI.toFloat() else 0f) else atan2(vy[i], vx[i])
    }

    /** The clam's opening 0..1 (opens for about 6 s after a star). */
    fun clamOpen(): Float {
        val t = since(clamAt)
        if (t < 0 || t > 7.0) return 0f
        return when { t < 0.8 -> (t / 0.8).toFloat(); t < 6.0 -> 1f; else -> (1.0 - (t - 6.0)).toFloat() }
    }

    /** Caustic intensity 0..1 (changes at 0.5 Hz or slower). */
    fun caustics(): Float = (0.75f + 0.25f * sin(causticPhase * 2 * PI).toFloat()) * (1f - 0.6f * night)

    override fun chooseChip(): String? = when {
        time < arrivalChipUntil -> "도착"
        time < pearlChipUntil -> "진주 발견"
        time < ringChipUntil -> "물고기 원무"
        time < startleChipUntil -> "깜짝 회전"
        charging > 0.5f -> "충전 중"
        moving && rain > 0.5f -> "수면에 비"
        moving && night > 0.5f -> "밤의 수조"
        moving && kmh > 3f -> "함께 헤엄"
        else -> null
    }

    companion object {
        const val W = 1000f
        const val H = 420f
        const val SAND_Y = 400f
        const val FISH = 30
        const val DECOR = 8
        const val TILT_SCALE = 0.35f
        const val MAX_TILT = 4f
        const val RING_S = 6.0
        val ALL_SPECIES = Species.entries
    }
}
