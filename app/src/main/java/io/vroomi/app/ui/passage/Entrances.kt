package io.vroomi.app.ui.passage

import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.SpringMath
import io.vroomi.character.calibre.SceneTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The signature entrance of every theme as a pure model: its duration, its easing and the few numbers that
 * decide what the screen shows at progress p (how much of the incoming world is uncovered, its opacity, a
 * dark shade over it and a light mist over everything). The drawing code ([EntranceDraw]) and the flash test
 * read the same functions, so the luminance the test checks is the luminance the screen shows.
 */
object Entrances {
    /** At intensity 1. Intensity scales both the duration and the distance. */
    fun durationMs(t: SceneTheme): Float = when (t) {
        SceneTheme.KART -> 700f
        SceneTheme.PAPER -> 800f
        SceneTheme.CALIBRE -> 850f
        SceneTheme.ENGINE -> 750f
        SceneTheme.MUSIC_BOX -> 750f
        SceneTheme.PLASMA -> 800f
        SceneTheme.HANGAR -> 800f
        SceneTheme.TERRARIUM -> 900f
        SceneTheme.AQUARIUM -> 850f
        SceneTheme.ORBIT -> 800f
        SceneTheme.TREASURE_MAP -> 800f
        SceneTheme.PIXEL -> 400f
        else -> 700f
    }

    /** PIXEL switches each virtual pixel once, in 16 ordered steps (linear time, no easing). */
    const val PIXEL_STEPS = 16

    /** A dissolve between two themes far apart in brightness takes this long instead (the flash rule). */
    const val PIXEL_CALM_MS = 600f

    /** An entrance into a much brighter theme (a big luminance step upwards) lasts at least this long (the flash rule). */
    const val BRIGHT_ENTRANCE_MS = 1100f

    /**
     * Eased progress at the time fraction [f] (0..1) of an entrance lasting [durationMs]: the Passage spring's
     * curve stretched to the duration and normalised to reach exactly 1. PIXEL steps linearly.
     */
    fun eased(t: SceneTheme, f: Float, durationMs: Float): Float {
        val x = f.coerceIn(0f, 1f)
        if (t == SceneTheme.PIXEL) return floor(x * PIXEL_STEPS) / PIXEL_STEPS
        if (x >= 1f) return 1f
        val omega = Motion.Passage.omega * (Motion.Passage.settleMs / durationMs)
        val zeta = Motion.Passage.dampingRatio.toDouble()
        val end = 1.0 - SpringMath.remaining(durationMs / 1000.0, omega, zeta)
        val now = 1.0 - SpringMath.remaining(x * durationMs / 1000.0, omega, zeta)
        return (now / end).toFloat().coerceIn(0f, 1f)
    }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ---------------------------------------------------------------- per-theme parameters

    /** PAPER: the angle of each half (degrees). */
    fun foldDeg(p: Float, intensity: Float): Float = 70f * intensity * (1f - p)

    /** MUSIC_BOX: the lid's vertical scale, pivoted at the top. */
    fun hingeScale(p: Float, intensity: Float): Float = 1f - 0.08f * intensity * (1f - p)

    /** KART: the world settles from +2 % (offset as a share of the height) and 1.02 x scale. */
    fun riseOffset(p: Float, intensity: Float): Float = 0.02f * intensity * (1f - p)

    /** ORBIT: the iris centre (share of width, height) for a canvas of w x h px: the sun of the orbit painter. */
    fun irisCenter(w: Float, h: Float): Pair<Float, Float> {
        // OrbitPainter: design height 600, sun at (dw / 2 - 640, 160) in design units, k = min(h / 600, w / 1000).
        val k = min(h / 600f, w / 1000f)
        val dw = w / k
        val x = ((dw / 2f - 640f) * k).coerceIn(0f, w)
        val y = (160f * k).coerceIn(0f, h)
        return (x / w) to (y / h)
    }

    /** ORBIT: the iris radius at p (px), reaching the farthest corner. */
    fun irisRadius(p: Float, w: Float, h: Float): Float {
        val (cx, cy) = irisCenter(w, h)
        val x = cx * w; val y = cy * h
        val far = max(max(hypot(x, y), hypot(w - x, y)), max(hypot(x, h - y), hypot(w - x, h - y)))
        return p * far * 1.02f
    }

    /** PLASMA: the iris opens from the centre of the canvas (where the globe stands) to the farthest corner (px). */
    fun plasmaRadius(p: Float, w: Float, h: Float): Float = p * hypot(w / 2f, h / 2f) * 1.02f

    /** HANGAR: the shutter's edge (px from the top): the incoming hangar is uncovered from the top down. */
    fun shutter(p: Float, h: Float): Float = p * h

    /** TERRARIUM: the mist (alpha 0..0.35, rising over the time fraction [f]) and its clearing front (share of height, on the spring). */
    fun mistUp(f: Float, intensity: Float): Float = 0.35f * intensity * smoothstep(0f, 0.35f, f)
    fun mistFront(p: Float): Float = smoothstep(0.45f, 1f, p)
    const val MIST_EDGE = 0.22f
    /** The mist colour's relative luminance (a cool light grey-green). */
    const val MIST_LUMA = 0.62f
    const val MIST_ARGB = 0xFFDCE6E0.toInt()

    /** CALIBRE: one soft specular band, at most 15 % white, crossing once between p 0.35 and 1. */
    fun bandPos(p: Float): Float = smoothstep(0.35f, 1f, p)
    const val BAND_MAX_ALPHA = 0.15f

    // ---------------------------------------------------------------- the luminance model

    /** Share of the canvas the incoming world covers at p. */
    fun coverage(t: SceneTheme, p: Float, intensity: Float = 1f, w: Float = 1920f, h: Float = 720f): Float = when (t) {
        SceneTheme.PAPER -> cos(foldDeg(p, intensity) * PI.toFloat() / 180f)
        SceneTheme.ENGINE, SceneTheme.AQUARIUM, SceneTheme.TREASURE_MAP, SceneTheme.HANGAR -> p
        SceneTheme.MUSIC_BOX -> hingeScale(p, intensity)
        SceneTheme.PLASMA -> discArea(0.5f, 0.5f, plasmaRadius(p, w, h), w, h)
        SceneTheme.ORBIT -> irisCenter(w, h).let { (cx, cy) -> discArea(cx, cy, irisRadius(p, w, h), w, h) }
        SceneTheme.PIXEL -> p
        else -> 1f
    }

    /**
     * Opacity of the incoming world at the linear time fraction [f] (opacity is a tween; masks and transforms
     * follow the spring). The ramps are long enough that a dark-to-bright passage never changes in a hurry.
     */
    fun inAlpha(t: SceneTheme, f: Float): Float = when (t) {
        SceneTheme.KART -> smoothstep(0f, 0.9f, f)
        SceneTheme.PAPER -> (f / 0.85f).coerceIn(0f, 1f)
        SceneTheme.CALIBRE -> smoothstep(0f, 0.45f, f)
        SceneTheme.MUSIC_BOX -> smoothstep(0f, 0.75f, f)   // a high-key studio: slow enough that dark-to-bright never hurries
        SceneTheme.TERRARIUM -> smoothstep(0.25f, 0.55f, f)
        else -> 1f
    }

    /** A black shade over the incoming world only (PAPER's tilted pages; mean over the canvas). */
    fun inDim(t: SceneTheme, p: Float, intensity: Float = 1f): Float = when (t) {
        SceneTheme.PAPER -> 0.35f * sin(foldDeg(p, intensity) * PI.toFloat() / 180f)
        else -> 0f
    }

    /**
     * Darkness over both worlds (mean over the canvas): the old world dims first (over the time fraction [f]),
     * so it is never seen through the arriving one; then the new world rises out of it (on the spring, [p]).
     * CALIBRE: the movement rises out of darkness. MUSIC_BOX: the lid's shade lifts off from the top down.
     * PAPER: the page casts a soft shade while it folds open.
     */
    fun globalDim(t: SceneTheme, p: Float, f: Float, intensity: Float = 1f): Float = when (t) {
        SceneTheme.CALIBRE -> 0.85f * intensity * smoothstep(0f, 0.22f, f) * (1f - smoothstep(0.15f, 1f, p))
        SceneTheme.MUSIC_BOX -> LID_SHADE * intensity * smoothstep(0f, 0.22f, f) * (1f - lidFront(p))
        SceneTheme.PAPER -> 0.35f * intensity * smoothstep(0f, 0.2f, f) * (1f - smoothstep(0.1f, 1f, p))
        else -> 0f
    }

    const val LID_SHADE = 0.7f
    /** MUSIC_BOX: how far the lid's shade has lifted (share of the height, top first). */
    fun lidFront(p: Float): Float = smoothstep(0.1f, 0.95f, p)

    /** Mean alpha of the light mist over everything (TERRARIUM only): [p] eased progress, [f] time fraction. */
    fun mist(t: SceneTheme, p: Float, f: Float, intensity: Float = 1f): Float {
        if (t != SceneTheme.TERRARIUM) return 0f
        val up = mistUp(f, intensity)
        val front = mistFront(p)
        // Below the front the mist is still there; above it, it has cleared (soft edge of MIST_EDGE).
        val remaining = (1f - front * (1f + MIST_EDGE)).coerceIn(0f, 1f) + MIST_EDGE * 0.5f * (if (front in 0.001f..0.999f) 1f else 0f)
        return up * remaining.coerceIn(0f, 1f)
    }

    /** Share of a w x h canvas inside the disc of radius [r] (px) centred at ([cx], [cy]) (shares of width, height). */
    private fun discArea(cx: Float, cy: Float, r: Float, w: Float, h: Float): Float {
        val x0 = cx * w; val y0 = cy * h
        var inside = 0
        val nx = 160; val ny = 60
        for (j in 0 until ny) for (i in 0 until nx) {
            val x = (i + 0.5f) / nx * w; val y = (j + 0.5f) / ny * h
            if (hypot(x - x0, y - y0) <= r) inside++
        }
        return inside.toFloat() / (nx * ny)
    }
}
