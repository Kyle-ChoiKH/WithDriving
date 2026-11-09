package io.vroomi.app.ui.design

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colour arithmetic on packed ARGB ints (pure Kotlin, unit-tested): sRGB <-> Oklab, interpolation in Oklab,
 * WCAG relative luminance and contrast, and a contrast fixer that only moves the Oklab lightness.
 */
object ColorMath {

    fun srgbToLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    fun linearToSrgb(c: Float): Float {
        val x = c.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) x * 12.92f else (1.055 * x.toDouble().pow(1 / 2.4) - 0.055).toFloat()
    }

    fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF
    private fun ch(argb: Int, shift: Int): Float = ((argb shr shift) and 0xFF) / 255f

    /** [out] = Oklab (L, a, b) of [argb] (alpha ignored). */
    fun toOklab(argb: Int, out: FloatArray) {
        val r = srgbToLinear(ch(argb, 16)); val g = srgbToLinear(ch(argb, 8)); val b = srgbToLinear(ch(argb, 0))
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        out[0] = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        out[1] = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        out[2] = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    }

    fun fromOklab(L: Float, a: Float, b: Float, alpha: Int = 0xFF): Int {
        val l = L + 0.3963377774f * a + 0.2158037573f * b
        val m = L - 0.1055613458f * a - 0.0638541728f * b
        val s = L - 0.0894841775f * a - 1.2914855480f * b
        val l3 = l * l * l; val m3 = m * m * m; val s3 = s * s * s
        val r = +4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
        val g = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
        val bb = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
        return pack(alpha, linearToSrgb(r), linearToSrgb(g), linearToSrgb(bb))
    }

    private fun pack(a: Int, r: Float, g: Float, b: Float): Int =
        (a.coerceIn(0, 255) shl 24) or ((r * 255f).roundToInt().coerceIn(0, 255) shl 16) or
            ((g * 255f).roundToInt().coerceIn(0, 255) shl 8) or (b * 255f).roundToInt().coerceIn(0, 255)

    private val la = FloatArray(3)
    private val lb = FloatArray(3)

    /** Interpolates two colours in Oklab (perceptually even, no grey dip between complementary hues). */
    @Synchronized
    fun lerpOklab(from: Int, to: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        if (u <= 0f) return from
        if (u >= 1f) return to
        toOklab(from, la); toOklab(to, lb)
        val alpha = (alpha(from) + (alpha(to) - alpha(from)) * u).roundToInt()
        return fromOklab(la[0] + (lb[0] - la[0]) * u, la[1] + (lb[1] - la[1]) * u, la[2] + (lb[2] - la[2]) * u, alpha)
    }

    /** WCAG relative luminance 0..1. */
    fun luminance(argb: Int): Float =
        0.2126f * srgbToLinear(ch(argb, 16)) + 0.7152f * srgbToLinear(ch(argb, 8)) + 0.0722f * srgbToLinear(ch(argb, 0))

    /** WCAG contrast ratio 1..21. */
    fun contrast(a: Int, b: Int): Float {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** Source-over composite of [top] (with its alpha) on an opaque [bottom]. */
    fun over(top: Int, bottom: Int): Int {
        val a = alpha(top) / 255f
        fun mix(shift: Int) = ch(top, shift) * a + ch(bottom, shift) * (1f - a)
        return pack(0xFF, mix(16), mix(8), mix(0))
    }

    /** Oklab chroma of [color]. */
    fun chroma(color: Int): Float {
        val lab = FloatArray(3); toOklab(color, lab)
        return kotlin.math.sqrt(lab[1] * lab[1] + lab[2] * lab[2])
    }

    /**
     * Tames a scene colour for interface use: its Oklab chroma is capped at [maxChroma] (the hue is kept) and its
     * lightness is clamped to [minL]..[maxL].
     */
    fun tame(color: Int, maxChroma: Float, minL: Float = 0f, maxL: Float = 1f): Int {
        val lab = FloatArray(3); toOklab(color, lab)
        val c = kotlin.math.sqrt(lab[1] * lab[1] + lab[2] * lab[2])
        val k = if (c > maxChroma) maxChroma / c else 1f
        return fromOklab(lab[0].coerceIn(minL, maxL), lab[1] * k, lab[2] * k)
    }

    /**
     * Oklab hues between 70° and 132° (amber, yellow, lime) turn olive or khaki when darkened for contrast. This
     * leans such a colour to the nearer clean hue (58°, a burnt orange; or 145°, a leaf green), keeping L and C.
     */
    fun leanFromYellow(color: Int): Int {
        val lab = FloatArray(3); toOklab(color, lab)
        val c = kotlin.math.sqrt(lab[1] * lab[1] + lab[2] * lab[2])
        val h = Math.toDegrees(kotlin.math.atan2(lab[2].toDouble(), lab[1].toDouble()))
        if (h < 70.0 || h > 132.0) return color
        val to = Math.toRadians(if (h < 101.0) 58.0 else 145.0)
        return fromOklab(lab[0], (c * kotlin.math.cos(to)).toFloat(), (c * kotlin.math.sin(to)).toFloat())
    }

    /**
     * Moves only the Oklab lightness of [color] (keeping its hue and chroma direction) until it reaches [ratio]
     * against [against]: darker on a light background, lighter on a dark one.
     */
    fun ensureContrast(color: Int, against: Int, ratio: Float): Int {
        if (contrast(color, against) >= ratio) return color
        val lab = FloatArray(3); toOklab(color, lab)
        val darken = luminance(against) > 0.18f
        var lo = if (darken) 0f else lab[0]
        var hi = if (darken) lab[0] else 1f
        var best = if (darken) fromOklab(0f, lab[1], lab[2]) else fromOklab(1f, lab[1], lab[2])
        repeat(28) {
            val mid = (lo + hi) / 2f
            // Chroma shrinks towards the ends so the colour stays inside sRGB.
            val k = 1f - abs(mid - lab[0]) * 0.6f
            val c = fromOklab(mid, lab[1] * k, lab[2] * k)
            val ok = contrast(c, against) >= ratio
            if (darken) { if (ok) { best = c; lo = mid } else hi = mid } else { if (ok) { best = c; hi = mid } else lo = mid }
        }
        return best
    }
}
