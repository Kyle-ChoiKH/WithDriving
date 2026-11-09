package io.vroomi.character.terrarium

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Deterministic plant shapes for the terrarium (pure Kotlin): L-systems expanded with a seeded angle jitter and
 * drawn by a turtle into segments. More growth reveals more of the same drawing (never less), so a plant only
 * ever grows. Coordinates are relative to the plant's base, y up, in design units.
 */
object PlantGen {

    /** One stroke of a plant: from (x0, y0) to (x1, y1), [w] wide; [leaf] marks a leaf or frond tip. */
    class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val w: Float, val leaf: Boolean, val order: Int)

    enum class Kind(val axiom: String, val rules: Map<Char, String>, val angle: Float, val step: Float, val maxIter: Int) {
        /** A fern: arching fronds with pinnae. */
        FERN("X", mapOf('X' to "F+[[X]-X]-F[-FX]+X", 'F' to "FF"), 22f, 4.2f, 4),
        /** A sapling: a forked little tree. */
        SAPLING("X", mapOf('X' to "F[+X]F[-X]+X", 'F' to "FF"), 24f, 5.2f, 4),
        /** A trailing ivy hanging from driftwood (grows downwards). */
        IVY("X", mapOf('X' to "F[-L]F[+L]X", 'F' to "F"), 28f, 9f, 6),
    }

    /** Expands [kind] for [iterations]. Strings stay small (a few thousand symbols at most). */
    fun expand(kind: Kind, iterations: Int): String {
        var s = kind.axiom
        repeat(iterations) {
            val b = StringBuilder(s.length * 3)
            for (c in s) b.append(kind.rules[c] ?: c.toString())
            s = b.toString()
        }
        return s
    }

    /**
     * The segments of a plant at [growth] 0..1. The full drawing is the deepest iteration; growth reveals its
     * segments in drawing order, so the result is monotonic in growth.
     */
    fun segments(kind: Kind, seed: Int, growth: Float, scale: Float = 1f): List<Seg> {
        val g = growth.coerceIn(0f, 1f)
        val full = draw(kind, seed, kind.maxIter, scale)
        val n = (full.size * (0.08f + 0.92f * g)).toInt().coerceIn(1, full.size)
        return full.subList(0, n)
    }

    private fun draw(kind: Kind, seed: Int, iterations: Int, scale: Float): List<Seg> {
        val s = expand(kind, iterations)
        val out = ArrayList<Seg>(s.length / 2)
        // Turtle state: x, y, heading (deg, 90 = up), width, depth.
        val stack = ArrayList<FloatArray>()
        var x = 0f; var y = 0f
        var h = if (kind == Kind.IVY) -90f else 90f
        var depth = 0
        var k = 0
        // F doubles each iteration for the fern and the sapling; the ivy grows linearly.
        val step = if (kind == Kind.IVY) kind.step * scale else kind.step * scale / (1 shl (iterations - 1)).toFloat().coerceAtLeast(1f) * 2.6f
        for (c in s) {
            k++
            when (c) {
                'F' -> {
                    val jitter = (hash(seed, k) - 0.5f) * 10f
                    val a = (h + jitter) * PI.toFloat() / 180f
                    val nx = x + cos(a) * step; val ny = y + sin(a) * step
                    val w = (3.2f - depth * 0.55f).coerceAtLeast(0.6f) * scale
                    out += Seg(x, y, nx, ny, w, false, out.size)
                    x = nx; y = ny
                }
                'L' -> {
                    val a = (h + (hash(seed, k + 7) - 0.5f) * 30f) * PI.toFloat() / 180f
                    out += Seg(x, y, x + cos(a) * step * 1.4f, y + sin(a) * step * 1.4f, 5f * scale, true, out.size)
                }
                '+' -> h += kind.angle + (hash(seed, k + 3) - 0.5f) * 8f
                '-' -> h -= kind.angle + (hash(seed, k + 5) - 0.5f) * 8f
                '[' -> { stack += floatArrayOf(x, y, h, depth.toFloat()); depth++ }
                ']' -> {
                    // The tip of a branch carries a leaf (ferns: a pinna).
                    if (kind != Kind.IVY) out += Seg(x, y, x + cos(h * PI.toFloat() / 180f) * step * 0.9f, y + sin(h * PI.toFloat() / 180f) * step * 0.9f, 4f * scale, true, out.size)
                    val st = stack.removeAt(stack.size - 1)
                    x = st[0]; y = st[1]; h = st[2]; depth = st[3].toInt()
                }
            }
        }
        if (kind == Kind.IVY) out += Seg(x, y, x + 2f, y - step, 5f * scale, true, out.size)
        return out
    }

    /** A rosette (succulent): [n] fleshy leaves around the centre, inner ones smaller. */
    fun rosette(seed: Int, growth: Float): List<Seg> {
        val n = (5 + growth.coerceIn(0f, 1f) * 13).toInt()
        val out = ArrayList<Seg>(n)
        for (i in 0 until n) {
            val ring = i / 6
            val a = (i * 137.5f + hash(seed, i) * 12f) * PI.toFloat() / 180f
            val len = (34f - ring * 9f) * (0.75f + 0.25f * hash(seed, i + 50))
            out += Seg(0f, 0f, cos(a) * len, sin(a) * len * 0.45f + 6f, 14f - ring * 3f, true, i)
        }
        return out
    }

    fun hash(seed: Int, k: Int): Float {
        var x = seed * 374761393 + k * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0x7fffffff) / 2147483648f
    }

    fun clamp01(v: Float): Float = min(1f, kotlin.math.max(0f, v))
}
