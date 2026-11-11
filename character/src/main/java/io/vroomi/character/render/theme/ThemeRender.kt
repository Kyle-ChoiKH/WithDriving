package io.vroomi.character.render.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.vroomi.character.CharacterTarget
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.Season
import io.vroomi.character.render.Spring
import io.vroomi.character.render.calibre.CalibreText
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.character.theme.ThemeModels
import io.vroomi.character.theme.ThemeSim
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The shared rendering kit of the procedural themes. A painter draws in design units: the canvas is
 * [ThemeFrame.dh] = 600 units tall (or less when a narrow canvas needs [ThemePainter.minDesignWidth]) and as
 * wide as the canvas allows ([ThemeFrame.dw]). The runtime handles CORNER size, the safety dim and timing.
 */
class ThemeFrame {
    /** Canvas pixels of the scene rectangle. */
    var w = 0f; var h = 0f
    /** px per design unit. */
    var k = 1f
    /** Design units visible across and down. */
    var dw = 0f; var dh = 0f
    var density: Density = Density(1f)
    /** The scene options of this frame (skin, season, calm, upgrade step...). */
    var options: SceneOptions = SceneOptions()
    /** The upgrade step of this frame, 0..4 = Lv.1..5 ([SceneOptions.model], clamped). */
    val model: Int get() = options.model.coerceIn(0, ThemeModels.COUNT - 1)
    val cx: Float get() = dw / 2f
    val cy: Float get() = dh / 2f
}

/** A theme's painter (reads the simulation, never changes it). */
internal abstract class ThemePainter<S : ThemeSim> {
    /** The narrowest design width the composition needs (a narrow canvas scales the scene down). */
    open val minDesignWidth: Float = 1000f
    /**
     * The width round the centre that has to stay in view. On a canvas narrower than [minDesignWidth] the scene
     * keeps its size and loses its sides down to this width (the subject stays large in a split or upright window);
     * only a canvas narrower still scales the scene down. The default keeps the whole composition.
     */
    open val focusWidth: Float get() = minDesignWidth

    /**
     * The colour of the scene's top edge (its wall, ceiling or sky), or null. On an upright canvas, where the
     * scene is shorter than the canvas, a painter that names it is moved down toward the middle and the space above
     * is filled with this colour; without it the scene stays at the top and its floor runs on below.
     */
    open fun headroom(sim: S): Color? = null
    /** The colour behind a CORNER-sized scene. */
    open val backdrop: Color = Color(0xFF15171C)
    /** Optional: draws in canvas px before [draw] (cached static layers stay crisp). [left], [top]: scene origin. */
    open fun drawPx(s: DrawScope, sim: S, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {}
    /** Draws in design units (the canvas is already scaled by [ThemeFrame.k]). */
    abstract fun draw(s: DrawScope, sim: S, f: ThemeFrame, tx: ThemeTexts)
    /** Parked only: a tap in design units. */
    open fun tap(sim: S, x: Float, y: Float) {}
}

/**
 * One cached static layer (an ImageBitmap) of one theme. All slots share one memory budget ([LayerCache]);
 * the least recently used bitmaps are dropped first and re-rendered when needed.
 */
internal class LayerSlot(val owner: SceneTheme) {
    var key = Long.MIN_VALUE; private set
    var bmp: ImageBitmap? = null; private set
    var lastUse = 0L; private set
    val bytes: Long get() = bmp?.let { it.width.toLong() * it.height * 4 } ?: 0L

    /** The bitmap for [key], rendered by [draw] (w × h px) only when the key changed or it was evicted. */
    inline fun get(key: Long, w: Int, h: Int, density: Density, crossinline draw: DrawScope.() -> Unit): ImageBitmap {
        lastUse = LayerCache.tick()
        val b = bmp
        if (b != null && this.key == key) return b
        return render(key, w, h, density) { draw() }
    }

    fun render(key: Long, w: Int, h: Int, density: Density, draw: DrawScope.() -> Unit): ImageBitmap {
        val img = ImageBitmap(w.coerceAtLeast(2), h.coerceAtLeast(2))
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat()), draw)
        bmp = img; this.key = key
        LayerCache.added(this)
        return img
    }

    fun drop() { bmp = null; key = Long.MIN_VALUE }

    /** The bitmap if it is current for [key] (null: render it with [render]). */
    fun cached(key: Long): ImageBitmap? { lastUse = LayerCache.tick(); return if (this.key == key) bmp else null }
}

/** The 48 MB budget for every theme's cached layers. Themes that are not shown release theirs. */
internal object LayerCache {
    const val BUDGET_BYTES = 48L * 1024 * 1024
    private val slots = ArrayList<LayerSlot>()
    private var clock = 0L

    fun tick(): Long = ++clock

    /** Slots added by this thread inside [scoped] (offscreen stills), dropped when it ends. */
    private val scope = ThreadLocal<ArrayList<LayerSlot>?>()

    /** Runs [block] (an offscreen still) and drops every layer it built, so stills never crowd out the live scene. */
    fun <T> scoped(block: () -> T): T {
        val mine = ArrayList<LayerSlot>()
        scope.set(mine)
        try { return block() } finally {
            scope.set(null)
            synchronized(this) { mine.forEach { it.drop() }; slots.removeAll { s -> mine.any { it === s } } }
        }
    }

    @Synchronized fun added(slot: LayerSlot) {
        scope.get()?.let { if (it.none { s -> s === slot }) it += slot }
        if (slot !in slots) slots += slot
        var total = bytesInUse()
        while (total > BUDGET_BYTES) {
            val victim = slots.filter { it !== slot && it.bmp != null }.minByOrNull { it.lastUse } ?: break
            total -= victim.bytes; victim.drop()
        }
    }

    @Synchronized fun bytesInUse(): Long = slots.sumOf { it.bytes }

    /** Drops the layers of every theme except [keep]. */
    @Synchronized fun releaseExcept(keep: SceneTheme?) {
        slots.forEach { if (it.owner != keep) it.drop() }
        slots.removeAll { it.bmp == null }
    }
}

/** Measured texts (fixed strings only), cached so the draw loop never measures or builds a string. */
internal class ThemeTexts(val text: CalibreText) {
    private val map = HashMap<Long, TextLayoutResult>()
    private val byString = HashMap<String, HashMap<Long, TextLayoutResult>>()
    /** [px] in the current drawing units. */
    fun get(s: String, px: Float, color: Color, bold: Boolean = false): TextLayoutResult {
        val inner = byString.getOrPut(s) { HashMap() }
        val key = (px * 4).roundToInt().toLong() * 1_000_003L + color.value.toLong().hashCode() * 2 + if (bold) 1 else 0
        return inner.getOrPut(key) { text.get(s, px, color, bold) }
    }
}

internal fun DrawScope.drawTextCentered(t: TextLayoutResult, x: Float, y: Float, alpha: Float = 1f) {
    drawText(t, topLeft = Offset(x - t.size.width / 2f, y - t.size.height / 2f), alpha = alpha.coerceIn(0f, 1f))
}

/** Simulation + painter + layout of one procedural theme. */
internal class ThemeRuntime<S : ThemeSim>(val theme: SceneTheme, val sim: S, val painter: ThemePainter<S>) {
    val corner = Spring(0f, 30f, 11f)
    var options = SceneOptions(); private set
    val frame = ThemeFrame()
    /**
     * Shows the whole composition scaled to the canvas, whatever its shape (no cropped sides, no headroom): for
     * pictures that zoom into a part of the scene by its place in the composition.
     */
    var wholeComposition = false
    private var headPx = 0f
    private var headColor: Color? = null
    private var texts: ThemeTexts? = null
    private var lastTm: TextMeasurer? = null
    private val clip = Path()

    fun step(dt: Float, t: CharacterTarget, o: SceneOptions, nowMs: Long) {
        options = o
        sim.step(dt, t, nowMs, o.hourOfDay, o.calm, o.reducedMotion, seasonIndex(o.season))
        corner.step(if (t.sizeMode == SizeMode.CORNER) 1f else 0f, dt)
    }

    fun texts(tm: TextMeasurer, density: Density): ThemeTexts {
        val t = texts
        if (t != null && lastTm === tm) return t
        return ThemeTexts(CalibreText(tm, density)).also { texts = it; lastTm = tm }
    }

    fun draw(s: DrawScope, tm: TextMeasurer, frameMs: Float? = null) = with(s) {
        val c = corner.value.coerceIn(0f, 1f)
        val fullW = size.width; val fullH = size.height
        // CORNER: the scene shrinks into the top-right 30 %.
        val w = fullW + (fullW * 0.30f - fullW) * c
        val h = fullH + (fullH * 0.30f - fullH) * c
        val left = (fullW - w - fullW * 0.02f * c)
        val top = fullH * 0.03f * c
        if (c > 0.01f) drawRect(painter.backdrop)
        val f = frame
        f.w = w; f.h = h; f.density = this; f.options = options
        val kByH = h / DESIGN_H
        val kByW = w / (if (wholeComposition) painter.minDesignWidth else min(painter.focusWidth, painter.minDesignWidth))
        f.k = min(kByH, kByW)
        f.dw = w / f.k; f.dh = h / f.k
        // An upright canvas: the scene moves down toward the middle under a band of its own top colour.
        headColor = if (wholeComposition) null else painter.headroom(sim)
        headPx = if (headColor != null && f.dh - DESIGN_H > 1f) (f.dh - DESIGN_H) * HEAD_SHARE * f.k else 0f
        if (headPx > 0f) { f.h = h - headPx; f.dh = f.h / f.k }
        val tx = texts(tm, this)
        val clipped = c > 0.01f
        if (clipped) {
            clip.reset()
            clip.addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, top + h, CornerRadius(h * 0.08f, h * 0.08f)))
        }
        if (clipped) clipPath(clip) { drawBody(this, left, top, w, h, tx) } else drawBody(this, left, top, w, h, tx)
        if (frameMs != null) {
            val t = tx.get("draw " + (frameMs * 10).roundToInt() / 10f + " ms", 13f * density, Color(0xFFFFE08A))
            drawText(t, topLeft = Offset(12f, fullH - t.size.height - 10f))
        }
    }

    private fun drawBody(s: DrawScope, left: Float, top: Float, w: Float, h: Float, tx: ThemeTexts) = with(s) {
        val f = frame
        headColor?.let { if (headPx > 0f) drawRect(it, Offset(left, top), Size(w, headPx + 1f)) }
        painter.drawPx(this, sim, f, tx, left, top + headPx)
        withTransform({ translate(left, top + headPx); scale(f.k, f.k, Offset.Zero) }) {
            painter.draw(this, sim, f, tx)
        }
        val dim = 0.3f * sim.freeze
        if (dim > 0.005f) drawRect(Color.Black, Offset(left, top), Size(w, h), alpha = dim)
    }

    /** A tap in canvas px (parked only), mapped to design units. */
    fun tap(x: Float, y: Float) {
        if (sim.moving || corner.value > 0.05f) return
        val f = frame
        if (f.k <= 0f) return
        painter.tap(sim, x / f.k, (y - headPx) / f.k)
    }

    companion object {
        const val DESIGN_H = 600f
        /** The share of an upright canvas's spare height that goes above the scene (the rest is more floor below). */
        const val HEAD_SHARE = 0.42f
        fun seasonIndex(s: Season): Int = when (s) { Season.SPRING -> 0; Season.SUMMER -> 1; Season.AUTUMN -> 2; Season.WINTER -> 3 }
    }
}

/** Animation state of a procedural theme, hoisted per screen. The app reads the chip (and the sim for sound). */
@Stable
class ThemeSceneState internal constructor(internal val rt: ThemeRuntime<*>) {
    val theme: SceneTheme get() = rt.theme
    var chip by mutableStateOf<String?>(null)
        internal set
    /** The simulation (read-only use: sound, persistence). */
    val sim: ThemeSim get() = rt.sim
}

/** Every theme drawn by this kit (null for KART, CALIBRE and ENGINE, which have their own scenes). */
internal fun newThemeRuntime(theme: SceneTheme): ThemeRuntime<*>? = ThemeCatalog.create(theme)

fun isKitTheme(theme: SceneTheme): Boolean = ThemeCatalog.supports(theme)

/** [restore] gives the persisted state of a theme (read once, before the first frame). */
@Composable
fun rememberThemeSceneState(theme: SceneTheme, restore: (SceneTheme) -> String? = { null }): ThemeSceneState =
    remember(theme) {
        ThemeSceneState(requireNotNull(newThemeRuntime(theme)) { "$theme is not a kit theme" }).also { st -> restore(theme)?.let { st.sim.restore(it) } }
    }

/** Drops the cached layers of every kit theme except [keep] (the 48 MB budget is shared). */
fun releaseThemeLayers(keep: SceneTheme?) = LayerCache.releaseExcept(keep)

/** A procedural theme driven by the car at 60 fps. [interactive] (parked only): taps reach the painter. */
@Composable
fun ThemeScene(
    target: CharacterTarget,
    options: SceneOptions,
    state: ThemeSceneState,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    showFrameTime: Boolean = false,
    onFirstFrame: (() -> Unit)? = null,
) {
    val rt = state.rt
    val tm = rememberTextMeasurer()
    val currentTarget by rememberUpdatedState(target)
    val currentOptions by rememberUpdatedState(options)
    val currentFirst by rememberUpdatedState(onFirstFrame)
    var frame by remember { mutableLongStateOf(0L) }
    val drawMs = remember { FloatArray(1) }
    // [0] drawn once (every static layer is built during that draw), [1] reported
    val drawn = remember(rt) { BooleanArray(2) }

    LaunchedEffect(rt) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                rt.step(dt, currentTarget, currentOptions, System.currentTimeMillis())
                val c = rt.sim.chip
                if (c != state.chip) state.chip = c
                frame = now
                if (drawn[0] && !drawn[1]) { drawn[1] = true; currentFirst?.invoke() }
            }
        }
    }
    val tap = if (interactive) Modifier.pointerInput(rt) { detectTapGestures { p -> rt.tap(p.x, p.y) } } else Modifier
    Canvas(modifier.then(tap)) {
        @Suppress("UNUSED_VARIABLE") val f = frame
        val t0 = if (showFrameTime) System.nanoTime() else 0L
        rt.draw(this, tm, if (showFrameTime) drawMs[0] else null)
        if (showFrameTime) drawMs[0] = drawMs[0] * 0.9f + (System.nanoTime() - t0) / 1e6f * 0.1f
        drawn[0] = true
    }
}

/** Simulates a script ([at] seconds → target) at 60 Hz without a frame clock. */
internal fun ThemeRuntime<*>.simulate(seconds: Float, options: SceneOptions, nowMs: Long, at: (Float) -> CharacterTarget) {
    val frames = (seconds * 60).toInt()
    val start = nowMs - (seconds * 1000).toLong()
    for (i in 0 until frames) step(1f / 60f, at(i / 60f), options, start + i * 1000L / 60)
    corner.snap(corner.value.roundToInt().toFloat())
}

/** A still frame after simulating [seconds] of [target], for snapshots and previews. */
@Composable
fun ThemeSceneStill(
    theme: SceneTheme,
    target: CharacterTarget,
    options: SceneOptions,
    modifier: Modifier = Modifier,
    seconds: Float = 3f,
    nowMs: Long = DEFAULT_NOW_MS,
    restore: String? = null,
) {
    val rt = remember(theme, target, options, seconds, nowMs, restore) {
        requireNotNull(newThemeRuntime(theme)).also { r ->
            restore?.let { r.sim.restore(it) }
            r.simulate(seconds, options, nowMs) { target }
        }
    }
    val tm = rememberTextMeasurer()
    Canvas(modifier) { rt.draw(this, tm) }
}

/** The picker card: a still frame (default), or live when [live]. */
@Composable
fun ThemeMiniature(theme: SceneTheme, modifier: Modifier = Modifier, nowMs: Long? = null, live: Boolean = false) {
    val target = remember { CharacterTarget(sizeMode = SizeMode.FULL) }
    val options = remember { SceneOptions(hourOfDay = 14f) }
    if (live) {
        val state = remember(theme) { ThemeSceneState(requireNotNull(newThemeRuntime(theme))) }
        ThemeScene(target, options, state, modifier)
    } else {
        ThemeSceneStill(theme, target, options, modifier, seconds = 1.5f, nowMs = nowMs ?: DEFAULT_NOW_MS)
    }
}

/** Design-unit helpers shared by the painters. */
internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t
internal fun clamp01(x: Float): Float = max(0f, min(1f, x))

/** Draws a cached px bitmap at a design-unit position at its native pixel size (crisp inside the scaled frame). */
internal fun DrawScope.drawNative(img: ImageBitmap, f: ThemeFrame, x: Float, y: Float, alpha: Float = 1f) {
    val k = f.k
    withTransform({ scale(1f / k, 1f / k, Offset.Zero) }) { drawImage(img, Offset(x * k, y * k), alpha = alpha.coerceIn(0f, 1f)) }
}
