package io.vroomi.character.render

import kotlin.random.Random

internal enum class PType {
    WIND, BREATH, SNOW, STEAM, RAIN, SPARKLE, DUST, CONFETTI, ENERGY, ZZZ,
    /** Music note while Vroomi hums. */
    NOTE,
    /** Soft electric puffs behind the kart when it speeds up (it is an EV: no smoke). */
    ZAP,
    /** Water thrown up by the wheels in the rain. */
    SPLASH,
    /** Little stars bursting from a collected star. */
    STARPOP,
    /** Seasonal: autumn leaves / spring petals / winter flakes drifting across. */
    FALL,
}

/**
 * Pooled particles in normalized scene coordinates (x: 0..1 of width, y: 0..1 of height).
 * Fixed capacity and primitive arrays: zero allocations per frame.
 */
internal class ParticleSystem(val capacity: Int = 220, seed: Int = 7) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val life = FloatArray(capacity)
    val maxLife = FloatArray(capacity)
    val size = FloatArray(capacity)
    val hue = FloatArray(capacity)
    val type = arrayOfNulls<PType>(capacity)
    var count = 0
        private set
    val rnd = Random(seed)

    fun spawn(t: PType, px: Float, py: Float, pvx: Float, pvy: Float, lifeS: Float, sz: Float, h: Float = 0f) {
        if (count >= capacity) return
        val i = count++
        type[i] = t; x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = lifeS; maxLife[i] = lifeS; size[i] = sz; hue[i] = h
    }

    /** [scrollDx]: how far the near world moved this frame (width units), applied to ground-bound particles. */
    fun step(dt: Float, scrollDx: Float = 0f) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f || x[i] < -0.25f || x[i] > 1.25f || y[i] < -0.25f || y[i] > 1.25f) {
                removeAt(i)
                continue
            }
            when (type[i]) {
                PType.CONFETTI, PType.DUST, PType.SPLASH -> vy[i] += 0.7f * dt          // gravity
                PType.STARPOP -> { vx[i] *= (1f - 2.5f * dt); vy[i] *= (1f - 2.5f * dt) }
                PType.STEAM, PType.ZZZ, PType.BREATH, PType.NOTE -> vx[i] *= (1f - 0.8f * dt)
                PType.FALL -> vx[i] += kotlin.math.sin(life[i] * 3f + hue[i] * 6f) * 0.02f * dt
                else -> Unit
            }
            if (type[i] == PType.DUST || type[i] == PType.SPLASH || type[i] == PType.ZAP) x[i] -= scrollDx
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
    }

    fun clear() { count = 0 }

    private fun removeAt(i: Int) {
        val last = count - 1
        if (i != last) {
            type[i] = type[last]; x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
            life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]; hue[i] = hue[last]
        }
        type[last] = null
        count--
    }
}

/** Damped spring used for every animated property. */
internal class Spring(var value: Float, private val stiffness: Float = 90f, private val damping: Float = 18f) {
    var velocity = 0f
    fun step(target: Float, dt: Float): Float {
        // Two half steps keep stiff springs stable at low frame rates.
        val h = dt / 2f
        repeat(2) {
            val a = stiffness * (target - value) - damping * velocity
            velocity += a * h
            value += velocity * h
        }
        return value
    }
    fun snap(v: Float) { value = v; velocity = 0f }
    fun kick(v: Float) { velocity += v }
}

/** One-off things that live on the road and scroll past: star pickups, km signs, the finish arch. */
internal enum class EType { STAR, KM_SIGN, FINISH }

internal class Entities(val capacity: Int = 24) {
    val type = arrayOfNulls<EType>(capacity)
    val x = FloatArray(capacity)      // width units, screen position
    val value = IntArray(capacity)
    val age = FloatArray(capacity)
    var count = 0
        private set

    fun spawn(t: EType, px: Float, v: Int = 0) {
        if (count >= capacity) return
        val i = count++
        type[i] = t; x[i] = px; value[i] = v; age[i] = 0f
    }

    fun removeAt(i: Int) {
        val last = count - 1
        if (i != last) { type[i] = type[last]; x[i] = x[last]; value[i] = value[last]; age[i] = age[last] }
        type[last] = null
        count--
    }

    fun countOf(t: EType): Int { var n = 0; for (i in 0 until count) if (type[i] == t) n++; return n }

    fun clear() { count = 0 }
}
