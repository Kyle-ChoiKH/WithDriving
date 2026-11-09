package io.vroomi.character.terrarium

import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The glass-jar garden (pure Kotlin). It grows with the distance driven (1 unit per km, capped per plant) and keeps
 * its growth across trips ([persist]: growth counters, bloom count and season only; no location, no times). Growth
 * never goes down and nothing wilts. A flower opens after 5 minutes of smooth driving (no harsh event in that
 * window): speed is never the trigger.
 */
class TerrariumSim : ThemeSim() {

    /** Growth units per plant: FERN, SAPLING, IVY, SUCCULENT, MOSS, FLOWER. */
    val growth = FloatArray(PLANTS)
    /** Opened flowers (lifetime, capped), buds waiting (stars), sapling leaf rings (km milestones). */
    var blooms = 0; private set
    var buds = 0; private set
    var rings = 0; private set
    var sprouts = 0; private set
    var bloomAt = -100.0; private set
    var sproutAt = -100.0; private set
    var ringAt = -100.0; private set

    private var lastTripKm = -1f
    /** Seconds of smooth driving since the last harsh event or bloom. */
    var smoothFor = 0.0; private set

    // Pond and leaves
    /** Surface tilt (deg): leans against the acceleration, at most 4°. */
    var tilt = 0f; private set
    private var tiltVel = 0f
    var sloshAt = -100.0; private set
    var sway = 0f; private set
    var swayPhase = 0.0; private set
    /** Condensation on the glass (A/C, rain); the kit's fog/clear handle the defrost. */
    val condensation: Float get() = fog

    // Lamp, lantern, small lives
    var lamp = 1f; private set
    var lantern = 0f; private set
    var evening = 0f; private set
    var moth = 0f; private set
    /** Ladybug position along its leaf 0 (left) .. 1 (right). */
    var ladybug = 0.5f; private set
    private var ladybugTarget = 0.5f
    var ladybugWalking = false; private set
    var cloudRain = 0f; private set

    private var arrivalChipUntil = -1.0
    private var bloomChipUntil = -1.0
    private var sproutChipUntil = -1.0
    private var sloshChipUntil = -1.0

    override val allChips: List<String> = listOf("도착", "꽃이 피었어요", "새싹이 돋았어요", "물결이 출렁", "병 속에 비", "충전 중", "반딧불이", "정원이 자라요", "쉬는 중")

    override fun onStart(nowMs: Long) { lastTripKm = input.journey.tripKm }
    override fun onTripStart() { lastTripKm = input.journey.tripKm; evening = 0f; moth = 0f }

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        // Growth: 1 unit per km driven, spread over the plants (each capped).
        val km = input.journey.tripKm
        if (lastTripKm < 0f || km < lastTripKm) lastTripKm = km
        val d = km - lastTripKm
        if (d > 0f) { grow(d); lastTripKm = km }

        // Smooth driving opens a flower every 5 minutes (harsh events reset the window).
        if (moving && kmh > 3f) {
            smoothFor += dt
            if (smoothFor >= SMOOTH_BLOOM_S) { smoothFor = 0.0; bloom() }
        }

        // Pond: tilt toward −atan(a/g), scaled and capped, with a damped spring; a harsh brake adds one slosh.
        val target = (-atan(accel / 9.81f) * 180f / PI.toFloat() * TILT_SCALE).coerceIn(-MAX_TILT, MAX_TILT)
        val k = 40f; val c = 9f
        val a = k * (target - tilt) - c * tiltVel
        tiltVel += a * ddt.toFloat(); tilt += tiltVel * ddt.toFloat()
        tilt = tilt.coerceIn(-MAX_TILT, MAX_TILT)

        // Leaves sway with the climate fan (calm halves it, reduced motion stills it).
        val amp = if (reducedMotion) 0f else (0.15f + 0.85f * fan) * (if (calm) 0.5f else 1f)
        sway = approach(sway, amp, (dt / 1.0).toFloat())
        swayPhase += ddt * (0.35 + 0.5 * fan) * 2 * PI

        // Lamp: charging brightens it slowly toward the battery level; arrival dims it to an evening glow.
        val lampT = when {
            evening > 0.5f -> 0.55f
            charging > 0.5f && battery >= 0f -> 0.75f + 0.25f * battery
            else -> 0.9f
        }
        lamp = approach(lamp, lampT, (dt / 2.5).toFloat())
        lantern = approach(lantern, if (night > 0.5f || evening > 0.5f) 1f else 0f, (dt / 1.5).toFloat())
        if (moving && kmh > 3f) { evening = approach(evening, 0f, (dt / 2.0).toFloat()); moth = approach(moth, 0f, (dt / 1.0).toFloat()) }
        else if (evening > 0f) moth = approach(moth, 1f, (dt / 3.0).toFloat())

        // Ladybug walks slowly toward the blinker's side.
        val walk = (if (reducedMotion) 0.0 else 0.12) * ddt
        ladybugWalking = kotlin.math.abs(ladybugTarget - ladybug) > 0.01f && walk > 0
        ladybug = approach(ladybug, ladybugTarget, walk.toFloat())

        cloudRain = approach(cloudRain, rain, (ddt / 2.0).toFloat())
    }

    private fun grow(km: Float) {
        for (i in 0 until PLANTS) growth[i] = min(CAP[i], growth[i] + km * SHARE[i])
    }

    private fun bloom() {
        if (buds > 0) buds--
        blooms = min(MAX_BLOOMS, blooms + 1)
        bloomAt = decoTime; bloomChipUntil = time + 5.0
        if (flash.allow(REGION_STAR, time)) Unit
    }

    override fun onStar() {
        // A star: a seed sprouts (a little growth) or a bud forms on the flower plant.
        if (buds < MAX_BUDS && blooms < MAX_BLOOMS) buds++ else sprouts = min(MAX_SPROUTS, sprouts + 1)
        sproutAt = decoTime; sproutChipUntil = time + 4.0
        growth[MOSS] = min(CAP[MOSS], growth[MOSS] + 0.2f)
    }

    override fun onCombo() { bloom() }

    override fun onMilestone(km: Int) { rings = min(MAX_RINGS, rings + 1); ringAt = decoTime }

    override fun onHarsh() {
        smoothFor = 0.0
        sloshAt = decoTime
        tiltVel += 26f
        sloshChipUntil = time + 4.0
    }

    override fun onArrival() { evening = 1f; arrivalChipUntil = time + 6.0 }

    override fun onBlink(side: TurnSignal) {
        ladybugTarget = when (side) { TurnSignal.LEFT -> 0.05f; TurnSignal.RIGHT -> 0.95f; else -> ladybugTarget }
    }

    /** Firefly glow 0..1 (≤ 0.5 Hz, never a flash). */
    fun firefly(i: Int): Float {
        if (lantern < 0.05f || reducedMotion) return 0f
        val ph = decoTime * FIREFLY_HZ * (0.7 + 0.08 * i) * 2 * PI + i * 1.7
        return (lantern * (0.35 + 0.65 * (0.5 + 0.5 * sin(ph)))).toFloat()
    }

    /** One slosh after a harsh brake: damped over 1 s. */
    fun slosh(): Float {
        val t = since(sloshAt)
        if (t < 0 || t > 1.5) return 0f
        return (exp(-t / 0.35) * sin(2 * PI * 1.8 * t)).toFloat()
    }

    override fun chooseChip(): String? = when {
        time < arrivalChipUntil -> "도착"
        time < bloomChipUntil -> "꽃이 피었어요"
        time < sproutChipUntil -> "새싹이 돋았어요"
        time < sloshChipUntil -> "물결이 출렁"
        charging > 0.5f -> "충전 중"
        moving && rain > 0.5f -> "병 속에 비"
        moving && night > 0.5f -> "반딧불이"
        moving && kmh > 3f -> "정원이 자라요"
        else -> null
    }

    override fun persist(): String = buildString {
        append("g=").append(growth.joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it) })
        append(";b=").append(blooms).append(";u=").append(buds).append(";r=").append(rings).append(";s=").append(sprouts)
        append(";season=").append(season)
    }

    override fun restore(state: String) {
        state.split(';').forEach { kv ->
            val parts = kv.split('=')
            if (parts.size != 2) return@forEach
            val v = parts[1]
            when (parts[0]) {
                "g" -> v.split(',').forEachIndexed { i, x -> if (i < PLANTS) x.toFloatOrNull()?.let { growth[i] = maxOf(growth[i], it.coerceIn(0f, CAP[i])) } }
                "b" -> v.toIntOrNull()?.let { blooms = maxOf(blooms, it.coerceIn(0, MAX_BLOOMS)) }
                "u" -> v.toIntOrNull()?.let { buds = maxOf(buds, it.coerceIn(0, MAX_BUDS)) }
                "r" -> v.toIntOrNull()?.let { rings = maxOf(rings, it.coerceIn(0, MAX_RINGS)) }
                "s" -> v.toIntOrNull()?.let { sprouts = maxOf(sprouts, it.coerceIn(0, MAX_SPROUTS)) }
            }
        }
    }

    /** Growth of [plant] as 0..1 of its cap. */
    fun fraction(plant: Int): Float = growth[plant] / CAP[plant]

    companion object {
        const val PLANTS = 6
        const val FERN = 0
        const val SAPLING = 1
        const val IVY = 2
        const val SUCCULENT = 3
        const val MOSS = 4
        const val FLOWER = 5
        /** Units (km) each plant can take, and the share of each km it gets. */
        val CAP = floatArrayOf(40f, 60f, 30f, 25f, 20f, 30f)
        val SHARE = floatArrayOf(0.30f, 0.30f, 0.20f, 0.15f, 0.25f, 0.20f)
        const val MAX_BLOOMS = 7
        const val MAX_BUDS = 5
        const val MAX_RINGS = 6
        const val MAX_SPROUTS = 6
        const val SMOOTH_BLOOM_S = 300.0
        const val TILT_SCALE = 0.35f
        const val MAX_TILT = 4f
        const val FIREFLY_HZ = 0.4
        const val FIREFLIES = 6
    }
}
