package io.vroomi.character.render.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
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
 * SIDE/FRONT cross-fade and the cached static layers. [step] advances it; the painter only reads.
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
     * 0 parked .. 1 moving, linear over [TEXT_FADE_S]. Small sheet text fades out with it, because text shown
     * while the car moves must be at least [MIN_DRIVING_SP]; only the title-block values (sized to it) remain.
     */
    var drive = 0f; private set
    var lastOptions = SceneOptions(); private set
    /** The upgrade step the painter draws, 0..4 = Lv.1..5 ([SceneOptions.model] of the last step, clamped). */
    val model: Int get() = lastOptions.model.coerceIn(0, ThemeModels.COUNT - 1)
    var choice = EnginePowertrain.AUTO; private set
    var forced: Powertrain? = null; private set
    /** Snapshot tests only: the title block shows the widest value of each cell, to pin its layout. */
    internal var probeWidestValues = false
    /** Picker miniatures hide the callouts (too small to read). */
    var calloutsEnabled = true

    private val layerCache = LinkedHashMap<Long, EngineLayers>()
    private var lastKey = Long.MIN_VALUE
    private var lastLayer: EngineLayers? = null
    var texts: EngineTexts? = null; private set
    private var textsTm: TextMeasurer? = null

    /** The ink of this frame (day paper blended toward night slate); set by the painter, read by every part. */
    val ink: ManualInk = ManualInk.DAY.copy()
    /** Screen layout of the sheet (field, parts list, title block). */
    val sheet = SheetLayout()
    /** Reused clip path for the CORNER card. */
    val clip = Path()

    // The printed sheets (paper, grid, frames, labels), one per ink, cached per canvas size and powertrain.
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

    fun step(dt: Float, t: CharacterTarget, o: SceneOptions, choice: EnginePowertrain, forced: Powertrain?, nowMs: Long, neutralThrottle: Double = 0.0) {
        lastOptions = o
        this.choice = choice; this.forced = forced
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
        val key = (kq.toBits().toLong() * 4 + p.ordinal) * 2 + if (night) 1 else 0
        if (key == lastKey) lastLayer?.let { return it } // no boxing on the per-frame path
        layerCache[key]?.let { lastKey = key; lastLayer = it; return it }
        val l = EngineLayers(kq, p, night, density, text)
        layerCache[key] = l
        while (layerCache.size > 4) layerCache.remove(layerCache.keys.first())
        lastKey = key; lastLayer = l
        return l
    }

    /** The printed sheet for this canvas, ink and powertrain (rendered once, then reused). */
    fun sheetLayer(w: Float, h: Float, night: Boolean, p: Powertrain, density: Density, texts: EngineTexts): ImageBitmap {
        val key = (((w.roundToInt().toLong() shl 24) xor (h.roundToInt().toLong() shl 2)) + p.ordinal * 2 + if (night) 1 else 0) * 4096 + sheet.version
        if (night) { sheetNight?.let { if (sheetNightKey == key) return it } } else { sheetDay?.let { if (sheetDayKey == key) return it } }
        val img = ImageBitmap(w.roundToInt().coerceAtLeast(2), h.roundToInt().coerceAtLeast(2))
        val ink = if (night) ManualInk.NIGHT else ManualInk.DAY
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) { printSheet(sheet, texts, ink, p) }
        if (night) { sheetNight = img; sheetNightKey = key } else { sheetDay = img; sheetDayKey = key }
        return img
    }

    /** The work lamp's warm pool (one brush per canvas size). */
    fun lampPool(w: Float, h: Float): Brush {
        val key = (w.roundToInt().toLong() shl 20) xor h.roundToInt().toLong()
        lamp?.let { if (lampKey == key) return it }
        return Brush.radialGradient(listOf(ManualInk.DAY.lamp.copy(alpha = 0.42f), ManualInk.DAY.lamp.copy(alpha = 0.12f), Color.Transparent), Offset(w * 0.18f, h * 0.1f), w * 0.6f)
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

/** The manual's type voice: condensed bold sans, Latin set in capitals. */
internal object ManualType {
    val condensed: FontFamily = FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)))
    val bold = FontWeight.Bold
}

/**
 * Every text the sheet prints, measured once per screen height in black (drawn with the ink of the frame): labels,
 * balloon numbers, zone marks and the values of the title block (indexed arrays, so the draw loop never builds a string).
 */
internal class EngineTexts(val h: Float, val minPx: Float, val text: CalibreText) {
    /** Title-block values stay on screen while driving, so they are never below the driving minimum. */
    private val valuePx = maxOf(h * 0.052f, minPx * 1.05f)
    private val labelPx = h * 0.030f
    /** The parts list's label size (the sheet layout sizes its rows from it). */
    val labelSize: Float get() = labelPx
    private val cellPx = h * 0.024f
    private val rpm = arrayOfNulls<TextLayoutResult>(1601)
    private val pct = arrayOfNulls<TextLayoutResult>(101)
    private val kw = arrayOfNulls<TextLayoutResult>(401)
    private val slow = arrayOfNulls<TextLayoutResult>(400)
    private val balloons = arrayOfNulls<TextLayoutResult>(12)
    private val numbers = arrayOfNulls<TextLayoutResult>(12)
    private val zones = arrayOfNulls<TextLayoutResult>(9)
    private val letters = arrayOfNulls<TextLayoutResult>(4)
    private val gears = HashMap<String, TextLayoutResult>()
    private val labels = HashMap<String, TextLayoutResult>()
    private val cells = HashMap<String, TextLayoutResult>()
    private val marks = HashMap<String, TextLayoutResult>()

    private fun cond(key: String, s: String, px: Float, spacing: Float = 0.04f) =
        text.styled("eng-t|$key|$px|$s", s, px, Color.Black, ManualType.condensed, ManualType.bold, spacing)

    fun rpm(v: Double): TextLayoutResult {
        val i = (v / 10).roundToInt().coerceIn(0, 1600) // up to 16,000 (the VE-M1's top speed)
        return rpm[i] ?: cond("v", "%,d".format(i * 10), valuePx).also { rpm[i] = it }
    }
    fun percent(v: Double): TextLayoutResult {
        val i = (v * 100).roundToInt().coerceIn(0, 100)
        return pct[i] ?: cond("v", "$i%", valuePx).also { pct[i] = it }
    }
    /** −60 .. 340 kW. */
    fun kw(v: Double): TextLayoutResult {
        val i = (v.roundToInt() + 60).coerceIn(0, 400)
        return kw[i] ?: cond("v", if (i < 60) "−${60 - i}" else "${i - 60}", valuePx).also { kw[i] = it }
    }
    fun gear(label: String): TextLayoutResult = gears.getOrPut(label) { cond("v", label, valuePx) }
    fun slowMotion(n: Int): TextLayoutResult? {
        if (n <= 0) return null
        val i = n.coerceIn(1, 399)
        return slow[i] ?: cond("n", "NOTE  슬로모션 1/$i", cellPx, 0.08f).also { slow[i] = it }
    }
    /** Balloon numbers are drawn in design units (scaled with the drawing). */
    fun balloon(i: Int): TextLayoutResult = balloons[i] ?: cond("b", "$i", 15f, 0f).also { balloons[i] = it }
    fun listNumber(i: Int): TextLayoutResult = numbers[i] ?: cond("ln", "$i", cellPx, 0f).also { numbers[i] = it }
    fun zone(i: Int): TextLayoutResult = zones[i] ?: cond("z", "$i", h * 0.018f, 0f).also { zones[i] = it }
    fun zoneLetter(i: Int): TextLayoutResult = letters[i] ?: cond("zl", "ABCD"[i].toString(), h * 0.018f, 0f).also { letters[i] = it }
    /** Fixed strings only: measured once, then a map lookup without allocation. */
    fun label(s: String): TextLayoutResult = labels.getOrPut(s) { cond("l", s, labelPx) }
    fun cell(s: String): TextLayoutResult = cells.getOrPut(s) { cond("c", s, cellPx, 0.08f) }
    private val designs = HashMap<String, TextLayoutResult>()
    /** Small drawing text in design units (dimension figures, gear tiles); one size per string. */
    fun design(s: String, size: Float): TextLayoutResult = designs.getOrPut(s) { cond("d", s, size, 0.06f) }
    /** A section name drawn in design units, beside the cutting-plane frame. */
    fun section(s: String): TextLayoutResult = marks.getOrPut("§$s") { cond("s", s, 17f, 0.06f) }
    private var estimateText: TextLayoutResult? = null
    /** The driving-time estimate heading of the title block, never below the driving minimum. */
    fun estimate(): TextLayoutResult = estimateText ?: cond("e", "추정값", minPx * 1.05f, 0.04f).also { estimateText = it }
    fun mark(s: String): TextLayoutResult = marks.getOrPut(s) { cond("m", s, maxOf(h * 0.062f, minPx * 1.2f), 0.06f) }
}
