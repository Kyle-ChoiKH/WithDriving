package io.vroomi.character.render.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import io.vroomi.character.CharacterTarget
import io.vroomi.character.SizeMode
import io.vroomi.character.engine.EngineMechanics
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.engine.EngineView
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.Spring
import io.vroomi.character.render.calibre.CalibreText
import io.vroomi.character.theme.ThemeModels
import kotlin.math.roundToInt

/**
 * All animation state of the Cutaway scene: the mechanics (car → powertrain), layout springs, the
 * SIDE/FRONT cross-fade, the cached static layers (one per upgrade step) and the studio backdrop. [step] advances it; the painter only reads.
 */
internal class EngineRuntime {
    val mech = EngineMechanics()

    var view = EngineView.SIDE; private set
    private var pendingView: EngineView? = null
    /** Cross-fade progress 0..1 (1 = settled). The view swaps at 0.5. */
    var fade = 1f; private set

    val cx = Spring(0.5f, 30f, 11f)
    val cy = Spring(0.5f, 30f, 11f)
    val scale = Spring(1f, 40f, 12f)
    val callouts = Spring(1f, 20f, 9f)
    /** 1 SIDE .. 0 FRONT (camera zoom on cylinder 1 / the motor). */
    val zoom = Spring(1f, 26f, 10f)
    var moving = false; private set
    /**
     * 0 parked .. 1 moving, linear over [TEXT_FADE_S]. Small text fades out with it, because text shown while
     * the car moves must be at least [MIN_DRIVING_SP]; only the telemetry readout (sized to it) remains.
     */
    var drive = 0f; private set
    var lastOptions = SceneOptions(); private set
    /** The upgrade step the painter draws, 0..4 = Lv.1..5 ([SceneOptions.model] of the last step, clamped). */
    val model: Int get() = lastOptions.model.coerceIn(0, ThemeModels.COUNT - 1)
    var choice = EnginePowertrain.AUTO; private set
    var forced: Powertrain? = null; private set
    /** Snapshot tests only: the readout shows the widest value of each cell, to pin its layout. */
    internal var probeWidestValues = false
    /** Kept for the picker miniatures' API (the studio look has no callouts). */
    var calloutsEnabled = true

    private val layerCache = LinkedHashMap<Long, EngineLayers>()
    private var lastKey = Long.MIN_VALUE
    private var lastLayer: EngineLayers? = null
    var texts: EngineTexts? = null; private set
    private var textsTm: TextMeasurer? = null

    /** The colours of this frame (day blended toward night); set by the painter, read by every part. */
    val ink: EngineInk = EngineInk.DAY.copy()
    /** Screen layout: the machine's field and the telemetry display. */
    val layout = PanelLayout()
    /** Reused clip path for the CORNER card. */
    val clip = Path()

    // The studio backdrops (room, lights, the display's card), one per light, cached per canvas size and layout.
    private var sheetDay: ImageBitmap? = null
    private var sheetNight: ImageBitmap? = null
    private var sheetDayKey = Long.MIN_VALUE
    private var sheetNightKey = Long.MIN_VALUE
    private var lamp: Brush? = null
    private var lampKey = Long.MIN_VALUE

    /** Reusable scratch for path sampling (no allocation per frame). */
    val pt = FloatArray(2)
    val pt2 = FloatArray(2)

    fun requestToggle() {
        if (moving || fade < 1f) return
        pendingView = if (view == EngineView.SIDE) EngineView.FRONT else EngineView.SIDE
        fade = 0f
    }

    fun releaseLayers() {
        layerCache.clear(); lastKey = Long.MIN_VALUE; lastLayer = null
        sheetDay = null; sheetNight = null; sheetDayKey = Long.MIN_VALUE; sheetNightKey = Long.MIN_VALUE
    }

    fun setView(v: EngineView) { view = v; pendingView = null; fade = 1f; zoom.snap(if (v == EngineView.SIDE) 1f else 0f) }

    /** Another app plays music: 0..1, swelling over a second (half in calm mode). */
    var listening = 0f; private set
    /** Seconds of the notes' clock (it stands still during a safety warning). */
    var noteT = 0.0; private set

    fun step(dt: Float, t: CharacterTarget, o: SceneOptions, choice: EnginePowertrain, forced: Powertrain?, nowMs: Long, neutralThrottle: Double = 0.0) {
        lastOptions = o
        val musicT = if (t.car.musicPlaying == true) (if (o.calm || o.reducedMotion) io.vroomi.character.theme.ThemeSim.CALM_MUSIC else 1f) else 0f
        listening = io.vroomi.character.theme.ThemeSim.approach(listening, musicT, dt)
        if (t.car.safetyWarningActive != true) noteT += dt
        this.choice = choice; this.forced = forced
        mech.turbo = IceStep.turbo(model)
        moving = t.kart.driving || t.car.moving
        drive = if (moving) (drive + dt / TEXT_FADE_S).coerceAtMost(1f) else (drive - dt / TEXT_FADE_S).coerceAtLeast(0f)
        mech.step(dt, t, nowMs, o.hourOfDay, choice, forced, o.calm, o.reducedMotion, neutralThrottle)

        if (moving && view == EngineView.FRONT && fade >= 1f) { pendingView = EngineView.SIDE; fade = 0f }
        if (fade < 1f) {
            val before = fade
            fade = (fade + dt / FADE_S).coerceAtMost(1f)
            if (before < 0.5f && fade >= 0.5f) pendingView?.let { view = it; pendingView = null }
        }
        val corner = t.sizeMode == SizeMode.CORNER
        cx.step(if (corner) 0.84f else 0.5f, dt)
        cy.step(if (corner) 0.22f else 0.5f, dt)
        scale.step(if (corner) 0.30f else 1f, dt)
        callouts.step(if (calloutsEnabled && view == EngineView.SIDE) 1f else 0f, dt)
        zoom.step(if (view == EngineView.SIDE) 1f else 0f, dt)
    }

    fun layers(k: Float, p: Powertrain, night: Boolean, density: Density, text: CalibreText): EngineLayers {
        val kq = ((k * 20f).roundToInt().coerceAtLeast(4)) / 20f
        val key = ((kq.toBits().toLong() * 4 + p.ordinal) * 2 + if (night) 1 else 0) * 8 + model
        if (key == lastKey) lastLayer?.let { return it } // no boxing on the per-frame path
        layerCache[key]?.let { lastKey = key; lastLayer = it; return it }
        val l = EngineLayers(kq, p, night, model, density, text)
        layerCache[key] = l
        while (layerCache.size > 4) layerCache.remove(layerCache.keys.first())
        lastKey = key; lastLayer = l
        return l
    }

    /** The studio backdrop for this canvas and light (rendered once, then reused). */
    fun sheetLayer(w: Float, h: Float, night: Boolean, density: Density): ImageBitmap {
        val key = (((w.roundToInt().toLong() shl 24) xor (h.roundToInt().toLong() shl 2)) + if (night) 1 else 0) * 4096 + layout.version
        if (night) { sheetNight?.let { if (sheetNightKey == key) return it } } else { sheetDay?.let { if (sheetDayKey == key) return it } }
        val img = ImageBitmap(w.roundToInt().coerceAtLeast(2), h.roundToInt().coerceAtLeast(2))
        val ink = if (night) EngineInk.NIGHT else EngineInk.DAY
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) { printStudio(layout, ink) }
        if (night) { sheetNight = img; sheetNightKey = key } else { sheetDay = img; sheetDayKey = key }
        return img
    }

    /** The work lamp's warm pool (one brush per canvas size). */
    fun lampPool(w: Float, h: Float): Brush {
        val key = (w.roundToInt().toLong() shl 20) xor h.roundToInt().toLong()
        lamp?.let { if (lampKey == key) return it }
        return Brush.radialGradient(listOf(EngineInk.DAY.lamp.copy(alpha = 0.42f), EngineInk.DAY.lamp.copy(alpha = 0.12f), Color.Transparent), Offset(w * 0.18f, h * 0.1f), w * 0.6f)
            .also { lamp = it; lampKey = key }
    }

    fun texts(h: Float, tm: TextMeasurer, density: Density): EngineTexts {
        val hq = (h / 8f).roundToInt() * 8f
        val minPx = with(density) { MIN_DRIVING_SP.sp.toPx() }
        texts?.let { if (it.h == hq && it.minPx == minPx && textsTm === tm) return it }
        // A value copy of the density: a DrawScope's own density is only valid while it draws, and values
        // are measured lazily (a reused DrawScope from another frame would report density 1).
        textsTm = tm
        return EngineTexts(hq, minPx, CalibreText(tm, Density(density.density, density.fontScale))).also { texts = it }
    }

    companion object {
        const val FADE_S = 0.5f
        const val TEXT_FADE_S = 0.3f
        /** The smallest text size allowed on screen while the car moves. */
        const val MIN_DRIVING_SP = 24
    }
}

/**
 * Every text of the scene, measured once per screen height in black (drawn with the colour of the frame). The
 * readout's values and heading are never below the driving minimum; captions are small and fade while driving.
 * Values are indexed arrays, so the draw loop never builds a string.
 */
internal class EngineTexts(val h: Float, val minPx: Float, val text: CalibreText) {
    private val valuePx = maxOf(h * EngineType.LARGE, minPx * 1.25f)
    private val headPx = maxOf(h * EngineType.MEDIUM, minPx * 1.05f)
    private val estPx = maxOf(h * 0.034f, minPx)
    private val smallPx = h * EngineType.SMALL
    private val rpm = arrayOfNulls<TextLayoutResult>(1601)
    private val pct = arrayOfNulls<TextLayoutResult>(101)
    private val kw = arrayOfNulls<TextLayoutResult>(401)
    private val slow = arrayOfNulls<TextLayoutResult>(400)
    private val degs = arrayOfNulls<TextLayoutResult>(360)
    private val advs = arrayOfNulls<TextLayoutResult>(61)
    private val gears = HashMap<String, TextLayoutResult>()
    private val heads = HashMap<String, TextLayoutResult>()
    private val cells = HashMap<String, TextLayoutResult>()
    private val marks = HashMap<String, TextLayoutResult>()
    private val designs = HashMap<String, TextLayoutResult>()

    private fun mono(key: String, s: String, px: Float, weight: FontWeight = EngineType.bold) =
        text.styled("eng-m|$key|$px|$s", s, px, Color.Black, EngineType.mono, weight, 0f)
    private fun cond(key: String, s: String, px: Float, weight: FontWeight = EngineType.medium, spacing: Float = 0.03f) =
        text.styled("eng-c|$key|$px|$s", s, px, Color.Black, EngineType.condensed, weight, spacing)

    fun rpm(v: Double): TextLayoutResult {
        val i = (v / 10).roundToInt().coerceIn(0, 1600) // up to 16,000 (the motor's top speed)
        return rpm[i] ?: mono("v", (i * 10).toString(), valuePx).also { rpm[i] = it }
    }
    fun percent(v: Double): TextLayoutResult {
        val i = (v * 100).roundToInt().coerceIn(0, 100)
        return pct[i] ?: mono("v", "$i%", valuePx).also { pct[i] = it }
    }
    /** −60 .. 340 kW. */
    fun kw(v: Double): TextLayoutResult {
        val i = (v.roundToInt() + 60).coerceIn(0, 400)
        return kw[i] ?: mono("v", if (i < 60) "−${60 - i}" else "${i - 60}", valuePx).also { kw[i] = it }
    }
    fun gear(label: String): TextLayoutResult = gears.getOrPut(label) { mono("v", label, valuePx) }
    /** The display's heading (the step's name), at or above the driving minimum. */
    fun head(s: String): TextLayoutResult = heads.getOrPut(s) { cond("h", s, headPx, EngineType.bold, 0.02f) }
    private var estimateText: TextLayoutResult? = null
    /** "추정값": every value of the display is an estimate. Never below the driving minimum. */
    fun estimate(): TextLayoutResult = estimateText ?: cond("e", "추정값", estPx, EngineType.medium, 0.04f).also { estimateText = it }
    /** Captions and cell labels (fixed strings only: measured once, then a map lookup without allocation). */
    fun cell(s: String): TextLayoutResult = cells.getOrPut(s) { cond("c", s, smallPx, EngineType.medium, 0.06f) }
    fun deg(i: Int): TextLayoutResult { val k = i.coerceIn(0, 359); return degs[k] ?: mono("d", "%03d°".format(k), smallPx, EngineType.medium).also { degs[k] = it } }
    /** Spark advance, 0..60 degrees before the top. */
    fun advance(i: Int): TextLayoutResult { val k = i.coerceIn(0, 60); return advs[k] ?: cond("a", "점화 $k°", smallPx, EngineType.medium, 0.04f).also { advs[k] = it } }
    fun slowMotion(n: Int): TextLayoutResult? {
        if (n <= 0) return null
        val i = n.coerceIn(1, 399)
        return slow[i] ?: cond("n", "슬로모션 1/$i", smallPx, EngineType.medium, 0.06f).also { slow[i] = it }
    }
    /** Small text in design units (gear tiles); one size per string. */
    fun design(s: String, size: Float): TextLayoutResult = designs.getOrPut(s) { cond("d", s, size, EngineType.bold, 0.06f) }
    /** A section name drawn in design units, beside the cutting-plane frame. */
    fun section(s: String): TextLayoutResult = marks.getOrPut("§$s") { cond("s", s, 17f, EngineType.bold, 0.06f) }

    val valueH: Float get() = rpm(0.0).size.height.toFloat()
    val headH: Float get() = head("VE").size.height.toFloat()
    val cellH: Float get() = cell("RPM").size.height.toFloat()
}
