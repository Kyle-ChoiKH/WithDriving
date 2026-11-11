package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import io.vroomi.character.CharacterTarget
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.CalibreGeometry
import io.vroomi.character.calibre.CalibreMechanics
import io.vroomi.character.calibre.CalibreTone
import io.vroomi.character.calibre.CalibreView
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.Spring
import io.vroomi.character.theme.ThemeModels
import kotlin.math.min
import kotlin.math.roundToInt

/** Measured text cached per (text, size, colour): no per-frame text layout. */
internal class CalibreText(private val tm: TextMeasurer, private val density: Density) {
    private val map = HashMap<String, TextLayoutResult>()
    fun get(text: String, px: Float, color: Color, bold: Boolean = false): TextLayoutResult {
        val key = "$text|${px.roundToInt()}|${color.value}|$bold"
        return map.getOrPut(key) {
            if (map.size > 96) map.clear()
            tm.measure(AnnotatedString(text), TextStyle(
                fontSize = with(density) { px.toSp() }, color = color,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            ))
        }
    }

    private val styledMap = HashMap<String, TextLayoutResult>()

    /**
     * Text in a theme's own type voice (family, weight, tracking, italic), cached by [key]. The key holds the
     * size; callers keep the returned layout in a field, so no string is built per frame.
     */
    fun styled(
        key: String, text: CharSequence, px: Float, color: Color, family: FontFamily = FontFamily.SansSerif,
        weight: FontWeight = FontWeight.Normal, spacingEm: Float = 0f, italic: Boolean = false,
    ): TextLayoutResult = styledMap.getOrPut(key) {
        if (styledMap.size > 400) styledMap.clear()
        val style = TextStyle(
            fontSize = with(density) { px.toSp() }, color = color, fontFamily = family, fontWeight = weight,
            letterSpacing = spacingEm.em, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        )
        if (text is AnnotatedString) tm.measure(text, style) else tm.measure(AnnotatedString(text.toString()), style)
    }
}

/**
 * All animation state of the Calibre scene: the mechanics (car -> movement), the layout springs,
 * the caseback/dial flip and the cached finish layers. [step] advances it; the painter only reads.
 */
internal class CalibreRuntime(initialReserveH: Float = 41f) {
    val mech = CalibreMechanics(initialReserveH)

    var view = CalibreView.CASEBACK; private set
    private var pendingView: CalibreView? = null
    /** Flip progress 0..1 (1 = no flip running). The view swaps at 0.5. */
    var flip = 1f; private set

    val cx = Spring(0.5f, 30f, 11f)
    val cy = Spring(0.5f, 30f, 11f)
    val scale = Spring(1f, 40f, 12f)
    val info = Spring(0f, 20f, 9f)
    /** Braking (pedal or a clear deceleration while moving), eased: the watch tips a little toward the light. */
    val brake = Spring(0f, 30f, 11f)
    var moving = false; private set
    var finish = CalibreFinish.AUTO; private set
    var lastOptions = SceneOptions(); private set
    /** The upgrade step the painter draws, 0..4 = Lv.1..5 ([SceneOptions.model] of the last step, clamped). */
    val model: Int get() = lastOptions.model.coerceIn(0, ThemeModels.COUNT - 1)

    // Reused paths (no per-frame allocation)
    val hairspring = Path()
    val mainspring = Path()

    private val layerCache = LinkedHashMap<Long, CalibreLayers>()
    private var lastKey = Long.MIN_VALUE
    private var lastLayers: CalibreLayers? = null

    /** The studio: the bench and its props, the lamp and light brushes and the caption texts (built once per size). */
    val studio = CalibreStudio()

    fun requestFlip() {
        if (moving || flip < 1f) return
        pendingView = if (view == CalibreView.CASEBACK) CalibreView.DIAL else CalibreView.CASEBACK
        flip = 0f
    }

    fun setView(v: CalibreView) { view = v; pendingView = null; flip = 1f }

    fun releaseLayers() { layerCache.clear(); lastKey = Long.MIN_VALUE; lastLayers = null; studio.release() }

    fun step(dt: Float, t: CharacterTarget, o: SceneOptions, finish: CalibreFinish, nowMs: Long) {
        this.finish = finish
        lastOptions = o
        moving = t.kart.driving || t.car.moving
        mech.step(dt, t, nowMs, o.hourOfDay, finish, o.calm, o.reducedMotion)

        // While driving the caseback faces the driver (the movement is the show).
        if (moving && view == CalibreView.DIAL && flip >= 1f) { pendingView = CalibreView.CASEBACK; flip = 0f }
        if (flip < 1f) {
            val before = flip
            flip = (flip + dt / FLIP_S).coerceAtMost(1f)
            if (before < 0.5f && flip >= 0.5f) pendingView?.let { view = it; pendingView = null }
        }

        val corner = t.sizeMode == SizeMode.CORNER
        cx.step(if (corner) 0.86f else 0.5f, dt)
        cy.step(if (corner) 0.30f else 0.455f, dt)
        scale.step(if (corner) 0.30f else 1f, dt)
        info.step(if (moving && !corner) 1f else 0f, dt)
        val c = t.car
        val braking = moving && (c.brakePressed == true || (c.accelMps2 ?: 0f) < -1.5f) && c.safetyWarningActive != true
        brake.step(if (braking && !o.calm) 1f else 0f, dt)
    }

    /** Movement radius in px at full size for a canvas, and its cache size (quantized). */
    fun fullRadius(w: Float, h: Float): Float = min(h * 0.292f, w * 0.29f) * min(1f, lastOptions.sizeScale.coerceAtLeast(0.85f))

    /** The finish layers at movement radius [u] (quantized) for upgrade step [model]; the studio key light never moves. */
    fun layers(u: Float, tone: CalibreTone, model: Int, density: Density, tm: TextMeasurer): CalibreLayers {
        val uq = ((u / 4f).roundToInt() * 4).coerceAtLeast(24).toFloat()
        val key = (uq.toBits().toLong() * 8 + tone.ordinal) * 8 + model.coerceIn(0, ThemeModels.COUNT - 1)
        if (key == lastKey) lastLayers?.let { return it }
        // A value copy of the density: the dial layer and engravings are measured lazily, after this draw.
        val l = layerCache[key] ?: CalibreLayers(uq, tone, model, Density(density.density, density.fontScale), tm).also {
            layerCache[key] = it
            while (layerCache.size > 3) layerCache.remove(layerCache.keys.first())
        }
        lastKey = key; lastLayers = l
        return l
    }

    companion object {
        const val FLIP_S = 0.6f
        /** Pivot of a wheel in cache px. */
        fun at(l: CalibreLayers, p: CalibreGeometry.Pivot) = Offset(l.c.x + p.x * l.u, l.c.y + p.y * l.u)
    }
}
