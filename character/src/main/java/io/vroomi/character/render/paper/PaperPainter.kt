package io.vroomi.character.render.paper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.vroomi.character.Biome
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.paper.PaperSim
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Deterministic hash → [0, 1) (this theme's own). */
private fun h01(a: Int, b: Int): Float {
    var x = a * 1103515245 + b * 12345 + 0x5bd1e995
    x = (x xor (x ushr 15)) * 2246822519.toInt()
    x = x xor (x ushr 13)
    return (x and 0x7fffffff) / 2147483648f
}

/** Paper flop: a hinge that stands up, overshoots a little and settles (0 → 1). Never a squash or a stretch. */
private fun flop(u: Float): Float { val x = u.coerceIn(0f, 1f); val v = 1f - x; return 1f - v * v * (1f - 2.4f * x) }

/** Layout of the spread in design units (a 600-unit tall stage, centred vertically by [oy]). */
private class PGeo(val dw: Float, val dh: Float) {
    val cx = dw / 2f
    val oy = max(0f, (dh - 600f) / 2f)
    val l = cx - 540f; val r = cx + 540f; val t = 66f; val b = 546f
    val foldBack = 334f
    val foldMid = 388f
    val ribbonTop = 396f; val ribbonBot = 452f
    val foldFront = 512f
    val carX = cx - 410f; val carY = 442f
    /** The car and its crane are drawn at this scale. */
    val carScale = 1.35f

    val pageL = Path().apply { moveTo(l, t + 8f); quadraticTo(l + 270f, t - 6f, cx, t + 12f); lineTo(cx, b); quadraticTo(l + 270f, b - 8f, l, b + 4f); close() }
    val pageR = Path().apply { moveTo(cx, t + 12f); quadraticTo(cx + 270f, t - 6f, r, t + 8f); lineTo(r, b + 4f); quadraticTo(cx + 270f, b - 8f, cx, b); close() }

    /** The lamp pool for the night (multiplied over the frame). */
    val lamp = Brush.radialGradient(0f to PaperInk.LAMP, 0.32f to PaperInk.LAMP, 0.7f to PaperInk.LAMP_MID, 1f to PaperInk.LAMP_EDGE, center = Offset(cx + 240f, oy + 170f), radius = 820f)

    // ---- the origami car (origin: under the rear bumper on the road line; facing right)
    val carRear = poly(4f, -14f, 8f, -40f, 46f, -44f, 54f, -14f)
    val carDoor = poly(54f, -14f, 46f, -44f, 140f, -46f, 150f, -13f)
    val carHood = poly(140f, -46f, 174f, -38f, 184f, -22f, 178f, -12f, 150f, -13f)
    val carSill = poly(6f, -24f, 182f, -22f, 178f, -12f, 4f, -14f)
    val carBackGlass = poly(46f, -44f, 66f, -74f, 78f, -74f, 68f, -44f)
    val carSideGlass = poly(68f, -44f, 78f, -74f, 106f, -74f, 124f, -45f)
    val carWindscreen = poly(124f, -45f, 106f, -74f, 112f, -74f, 140f, -46f)
    val carRoof = poly(64f, -74f, 114f, -74f, 112f, -79f, 68f, -79f)
    val carAll = poly(4f, -14f, 8f, -40f, 46f, -44f, 66f, -74f, 68f, -79f, 112f, -79f, 114f, -74f, 140f, -46f, 174f, -38f, 184f, -22f, 178f, -12f)
    val wheelR = 19f
    val wheelRear = Offset(40f, -18f)
    val wheelFront = Offset(144f, -18f)
    val wheel = Path().apply { addOval(Rect(-wheelR, -wheelR, wheelR, wheelR)) }
    val wheelInk = Path().apply { fillType = PathFillType.EvenOdd; addOval(Rect(-15f, -15f, 15f, 15f)); addOval(Rect(-9f, -9f, 9f, 9f)) }
    val cone = poly(184f, -30f, 440f, -76f, 440f, 6f)

    // ---- the folded crane (origin: its feet on the roof; facing right)
    val craneBody = poly(-22f, -12f, 0f, -21f, 24f, -12f, 0f, -4f)
    val craneTail = poly(-14f, -13f, -48f, -50f, -6f, -9f)
    val craneNeck = poly(12f, -13f, 42f, -54f, 17f, -9f)
    val craneHead = poly(40f, -56f, 54f, -45f, 42f, -48f)
    val craneWingFar = poly(-6f, -18f, 6f, -66f, 22f, -18f)
    val craneWingNear = poly(-14f, -18f, -28f, -72f, 16f, -18f)
    val craneHinge = Offset(2f, -18f)
    val craneNeckHinge = Offset(14f, -11f)

    // ---- small cut pieces
    val arrow = poly(0f, -7f, 16f, -7f, 16f, -14f, 30f, 0f, 16f, 14f, 16f, 7f, 0f, 7f)
    val drop = Path().apply { moveTo(0f, -12f); quadraticTo(9f, 3f, 0f, 10f); quadraticTo(-9f, 3f, 0f, -12f); close() }
    val flag = poly(0f, 0f, 30f, 0f, 15f, 34f)
    val dot = Path().apply { addOval(Rect(-13f, -13f, 13f, 13f)) }
    val ring = Path().apply { fillType = PathFillType.EvenOdd; addOval(Rect(-13f, -13f, 13f, 13f)); addOval(Rect(-8f, -8f, 8f, 8f)) }
    val sunDisc = Path().apply { addOval(Rect(-40f, -40f, 40f, 40f)) }
    /** The lower part of the sun disc (shaded with pink dots), cut once. */
    val sunLower = Path().apply { op(sunDisc, Path().apply { addRect(Rect(-40f, 8f, 40f, 40f)) }, androidx.compose.ui.graphics.PathOperation.Intersect) }
    val sunTeeth = Path().apply {
        for (i in 0 until 28) {
            val a = i * PI.toFloat() / 14f
            val rr = if (i % 2 == 0) 58f else 44f
            if (i == 0) moveTo(cos(a) * rr, sin(a) * rr) else lineTo(cos(a) * rr, sin(a) * rr)
        }
        close()
    }
    val moon = lune(30f, Rect(-14f, -40f, 42f, 16f))
    val crescent = lune(9f, Rect(-3f, -12f, 13f, 4f))
    val tab = poly(0f, 0f, 34f, 0f, 34f, 26f, 26f, 32f, 8f, 32f, 0f, 26f)
    val tabArrow = poly(8f, 9f, 20f, 9f, 20f, 5f, 28f, 13f, 20f, 21f, 20f, 17f, 8f, 17f)
    val bookmark = poly(0f, 0f, 30f, 0f, 30f, 92f, 15f, 78f, 0f, 92f)

    /** A crescent: a disc of radius [r] with [bite] cut away (built once). */
    private fun lune(r: Float, bite: Rect): Path {
        val disc = Path().apply { addOval(Rect(-r, -r, r, r)) }
        val cut = Path().apply { addOval(bite) }
        return Path().apply { op(disc, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
    }

    private fun poly(vararg xy: Float) = Path().apply {
        moveTo(xy[0], xy[1]); var i = 2
        while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }
        close()
    }
}

/**
 * The risograph pop-up book: an open book lies on a pink cutting mat; layered pop-up planes stand on parallel folds
 * (paper mountains, a biome strip, the kraft route ribbon and a grass fringe), and a folded origami car with a crane
 * on its roof drives along the ribbon. Every printed area is a flat spot ink laid down by multiply, off register.
 */
internal class PaperPainter : ThemePainter<PaperSim>() {
    override val minDesignWidth = 1120f
    override val backdrop = Color(0xFF231C3B)

    private var geo: PGeo? = null
    private val baseDay = LayerSlot(SceneTheme.PAPER)
    private val baseNight = LayerSlot(SceneTheme.PAPER)
    private val tmp = Path()
    private var textAlpha = 1f
    private var textClock = -1.0
    private val hairline = androidx.compose.ui.graphics.drawscope.Stroke(1f)
    private val tmp2 = Path()

    // Per-frame lighting: the window light (day) comes from the upper left, the desk lamp (night) from the upper right.
    private var sdx = 6f
    private var sdy = 7f
    private var shadow = PaperInk.SHADOW_DAY
    private var pink = PaperInk.PINK
    private var pinkTone: ColorFilter = TONE_PINK

    private val dots: ImageBitmap = ImageBitmap(9, 9).also { b ->
        Canvas(b).drawCircle(Offset(4.5f, 4.5f), 2.4f, Paint().apply { color = Color.Black; isAntiAlias = true })
    }
    private val fineDots: ImageBitmap = ImageBitmap(6, 6).also { b ->
        Canvas(b).drawCircle(Offset(3f, 3f), 1.2f, Paint().apply { color = Color.Black; isAntiAlias = true })
    }
    private val grain: ImageBitmap = ImageBitmap(97, 97).also { b ->
        val c = Canvas(b); val p = Paint()
        for (i in 0 until 900) {
            val x = (h01(i, 1) * 97f).toInt(); val y = (h01(i, 2) * 97f).toInt()
            p.color = if (i % 3 == 0) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.05f + 0.10f * h01(i, 3))
            c.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, p)
        }
    }
    private val tone = ShaderBrush(ImageShader(dots, TileMode.Repeated, TileMode.Repeated))
    private val fineTone = ShaderBrush(ImageShader(fineDots, TileMode.Repeated, TileMode.Repeated))
    private val grainBrush = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))

    private fun geo(dw: Float, dh: Float): PGeo {
        val g = geo
        if (g != null && abs(g.dw - dw) < 0.5f && abs(g.dh - dh) < 0.5f) return g
        return PGeo(dw, dh).also { geo = it }
    }

    override fun tap(sim: PaperSim, x: Float, y: Float) { sim.requestFlip() }

    override fun drawPx(s: DrawScope, sim: PaperSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val g = geo(f.dw, f.dh)
        PaperInk.use(f.model)
        val w = f.w.roundToInt(); val h = f.h.roundToInt(); val k = f.k
        val key = (w.toLong() shl 20) xor h.toLong()
        val n = sim.night
        val at = IntOffset(left.roundToInt(), top.roundToInt())
        if (n < 0.999f) {
            val day = baseDay.get(key, w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { book(g, false) } }
            s.drawImage(day, dstOffset = at, dstSize = IntSize(day.width, day.height))
        }
        if (n > 0.001f) {
            val night = baseNight.get(key, w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { book(g, true) } }
            s.drawImage(night, dstOffset = at, dstSize = IntSize(night.width, night.height), alpha = n)
        }
    }

    override fun draw(s: DrawScope, sim: PaperSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        val g = geo(f.dw, f.dh)
        val model = f.model
        PaperInk.use(model)
        val n = sim.night
        light(n)
        val intro = f.options.intro
        withTransform({ translate(0f, g.oy) }) {
            clipRect(g.l, g.t - 40f, g.r, g.b) {
                sky(g, sim)
                // Foil is stamped on the page itself, so the planes stand in front of it.
                if (PaperStep.special(model)) foil(g)
                if (PaperStep.pullTab(model)) { cloudTab(g, sim); balloon(g, sim) }
                if (PaperStep.farPlane(model)) standUp(intro, 0, g.foldBack - 26f) { farHills(g, sim) }
                standUp(intro, 0, g.foldBack) { mountains(g, sim) }
                if (PaperStep.pullTab(model)) kite(g, sim)
                standUp(intro, 1, g.foldMid) { scenery(g, sim) }
                ribbon(g, sim)
                standUp(intro, 2, g.carY) { carScene(g, sim) }
                if (PaperStep.farPlane(model)) standUp(intro, 2, g.foldFront) { flowers(g, sim) }
                standUp(intro, 3, g.foldFront) { fringe(g, sim) }
                threads(g, sim)
                garland(g, sim)
            }
            pullTab(g, sim)
            title(g.cx + 70f, 100f, sdx, if (PaperStep.special(model)) PaperInk.GOLD else PaperInk.BLUE, PaperInk.REG_BLUE, TITLE_SCALE, TITLE, legibility(sim, f, CutType.H * TITLE_SCALE))
            confetti(g, sim)
            bookmark(g, sim)
            pageFlip(g, sim)
            cover(g, sim)
        }
        // Print grain over everything, then the desk lamp at night.
        drawRect(grainBrush, Offset.Zero, Size(f.dw, f.dh), alpha = 0.9f)
        if (n > 0.01f) drawRect(g.lamp, Offset.Zero, Size(f.dw, f.dh), alpha = n, blendMode = BlendMode.Multiply)
    }

    /**
     * Text legibility while driving: in-scene text must render at least [MIN_TEXT_SP] tall (from the frame scale and
     * the display density), otherwise it fades out (0.3 s) while the car moves and comes back when parked.
     * Returns the text alpha for a glyph [heightUnits] design units tall.
     */
    private fun legibility(sim: PaperSim, f: ThemeFrame, heightUnits: Float): Float {
        val minPx = with(f.density) { MIN_TEXT_SP.sp.toPx() }
        val ok = heightUnits * f.k >= minPx
        val target = if (!sim.moving || ok) 1f else 0f
        // The first frame (a still, or a fresh scene) starts settled.
        val dt = if (textClock < 0.0) FADE_S else (sim.time - textClock).toFloat().coerceIn(0f, 0.1f)
        textClock = sim.time
        textAlpha = if (textAlpha < target) min(target, textAlpha + dt / FADE_S) else max(target, textAlpha - dt / FADE_S)
        return textAlpha
    }

    private fun light(n: Float) {
        sdx = 6f - 12f * n; sdy = 7f
        shadow = lerp(PaperInk.SHADOW_DAY, PaperInk.SHADOW_NIGHT, n)
        pink = PaperInk.pink(n)
        pinkTone = if (n > 0.5f) TONE_VIOLET else if (PaperInk.PINK == PaperInk.FLUO) TONE_PINK else TONE_RED
    }

    // ------------------------------------------------------------------ printing helpers

    /**
     * A cut card piece: a flat cast shadow, the bare white card core showing on the side the ink plate slipped
     * from, and the card printed with [ink] (ink × card, as a multiply would give). No clipping: three fills.
     */
    private fun DrawScope.piece(path: Path, ink: Color, reg: Offset, card: Color = PaperInk.CARD, lift: Float = 1f) {
        if (lift > 0f) withTransform({ translate(sdx * lift, sdy * lift) }) { drawPath(path, shadow, blendMode = BlendMode.Multiply) }
        if (reg != Offset.Zero) withTransform({ translate(-reg.x, -reg.y) }) { drawPath(path, PaperInk.CORE) }
        drawPath(path, Color(ink.red * card.red, ink.green * card.green, ink.blue * card.blue, 1f))
    }

    /** A second ink overprinted on a piece (off register, multiplied: two inks make a third colour). */
    private fun DrawScope.over(path: Path, ink: Color, reg: Offset, alpha: Float = 1f) {
        withTransform({ translate(reg.x, reg.y) }) { drawPath(path, ink, alpha = alpha, blendMode = BlendMode.Multiply) }
    }

    /** Halftone shading in one ink. */
    private fun DrawScope.halftone(path: Path, filter: ColorFilter, fine: Boolean = false) {
        drawPath(path, if (fine) fineTone else tone, colorFilter = filter, blendMode = BlendMode.Multiply)
    }

    private fun DrawScope.crease(a: Offset, b: Offset, w: Float = 1.2f) {
        drawLine(PaperInk.CREASE, a, b, w, blendMode = BlendMode.Multiply)
    }

    /**
     * A pop-up plane standing up on its fold. Entrance accent (intro < 1): the planes stand up one after another,
     * hinged at their fold line with a little paper flop. At intro = 1 the plane is drawn as is.
     */
    private inline fun DrawScope.standUp(intro: Float, i: Int, foldY: Float, block: DrawScope.() -> Unit) {
        if (intro >= 1f) { block(); return }
        val up = flop((intro - 0.25f - i * 0.12f) / 0.4f)
        if (up <= 0.01f) return
        withTransform({ scale(1f, up, Offset(0f, foldY)) }) { block() }
    }

    // ------------------------------------------------------------------ the static book (cached, px)

    private fun DrawScope.book(g: PGeo, night: Boolean) {
        val mat = if (night) PaperInk.MAT_DARK else PaperInk.MAT
        drawRect(mat, size = Size(g.dw, g.dh))
        // The cutting mat: a fine grid, every fifth line stronger, and 45° guides.
        val line = if (night) PaperInk.MAT_DARK_LINE else PaperInk.MAT_LINE
        val major = if (night) PaperInk.MAT_DARK_MAJOR else PaperInk.MAT_MAJOR
        var x = 0f
        var i = 0
        while (x < g.dw) { drawLine(if (i % 5 == 0) major else line, Offset(x, 0f), Offset(x, g.dh), if (i % 5 == 0) 1.6f else 0.8f); x += 40f; i++ }
        var y = 0f; i = 0
        while (y < g.dh) { drawLine(if (i % 5 == 0) major else line, Offset(0f, y), Offset(g.dw, y), if (i % 5 == 0) 1.6f else 0.8f); y += 40f; i++ }
        for (d in 0 until 3) drawLine(major, Offset(d * 400f - 200f, g.dh), Offset(d * 400f - 200f + g.dh, 0f), 1.2f)
        val sx = if (night) -6f else 6f
        withTransform({ translate(0f, g.oy) }) {
            // Offcuts left on the mat.
            offcut(g.l - 150f, 140f, 0, sx); offcut(g.l - 110f, 470f, 1, sx); offcut(g.r + 120f, 210f, 2, sx); offcut(g.r + 160f, 450f, 3, sx)
            // The cover boards (blue cloth), the page block and the two pages.
            drawRect(Color(0xFF6E6680), Offset(g.l - 16f + sx * 1.6f, g.t - 12f + 10f), Size(g.r - g.l + 32f, g.b - g.t + 28f), blendMode = BlendMode.Multiply)
            drawRect(PaperInk.CLOTH, Offset(g.l - 16f, g.t - 12f), Size(g.r - g.l + 32f, g.b - g.t + 28f))
            drawRect(PaperInk.CLOTH_EDGE, Offset(g.l - 16f, g.b + 10f), Size(g.r - g.l + 32f, 6f))
            for (k in 0 until 5) {
                drawLine(PaperInk.PAGE_EDGE, Offset(g.l + 2f + k, g.b + 2f + k * 1.6f), Offset(g.r - 2f - k, g.b + 2f + k * 1.6f), 1.4f)
            }
            drawPath(g.pageL, PaperInk.PAGE); drawPath(g.pageR, PaperInk.PAGE)
            // Paper fibres in the sheet: short hairs, barely lighter or darker than the page.
            for (i in 0 until 460) {
                val fx = g.l + 6f + h01(i, 71) * (g.r - g.l - 12f); val fy = g.t + 16f + h01(i, 72) * (g.b - g.t - 22f)
                val a = h01(i, 73) * 3.14f; val len = 5f + h01(i, 74) * 12f
                drawLine(if (i % 3 == 0) Color.White.copy(alpha = 0.55f) else Color(0xFF8A7F68).copy(alpha = 0.10f + 0.09f * h01(i, 75)),
                    Offset(fx, fy), Offset(fx + kotlin.math.cos(a) * len, fy + kotlin.math.sin(a) * len), 0.7f)
            }
            // The gutter: a halftone shade band and the centre crease.
            tmp.reset(); tmp.addRect(Rect(g.cx - 36f, g.t + 10f, g.cx + 36f, g.b))
            drawPath(tmp, fineTone, colorFilter = TONE_CREASE, blendMode = BlendMode.Multiply)
            tmp.reset(); tmp.addRect(Rect(g.cx - 14f, g.t + 10f, g.cx + 14f, g.b))
            drawPath(tmp, tone, colorFilter = TONE_CREASE, blendMode = BlendMode.Multiply)
            drawLine(PaperInk.CREASE, Offset(g.cx, g.t + 12f), Offset(g.cx, g.b), 1.6f)
            // The parallel folds and slots the planes stand on.
            for (fy in floatArrayOf(g.foldBack, g.foldMid, g.foldFront)) {
                // The fold's own shadow: the page dips toward the crease.
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF7A7364).copy(alpha = 0.20f)), startY = fy - 18f, endY = fy), Offset(g.l + 8f, fy - 18f), Size(g.r - g.l - 16f, 18f))
                drawLine(Color(0xFF8C8577), Offset(g.l + 8f, fy + 0.5f), Offset(g.r - 8f, fy + 0.5f), 1.2f)
                drawLine(Color.White, Offset(g.l + 8f, fy + 2f), Offset(g.r - 8f, fy + 2f), 1f)
            }
            // Pull-tab slot at the foot of the left page.
            drawRoundRectSlot(g.l + 64f, g.b - 26f, 220f)
            // Registration marks printed in the page corners, each ink a hair apart.
            regMark(g.l + 30f, g.t + 30f); regMark(g.r - 30f, g.t + 30f); regMark(g.l + 30f, g.b - 26f); regMark(g.r - 30f, g.b - 26f)
        }
    }

    private fun DrawScope.drawRoundRectSlot(x: Float, y: Float, w: Float) {
        drawLine(Color(0xFF6F6858), Offset(x, y), Offset(x + w, y), 3f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(x, y + 2.5f), Offset(x + w, y + 2.5f), 1f, cap = StrokeCap.Round)
    }

    private fun DrawScope.regMark(x: Float, y: Float) {
        for ((c, o) in REG_INKS) {
            drawCircle(c, 7f, Offset(x + o.x, y + o.y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.1f), blendMode = BlendMode.Multiply)
            drawLine(c, Offset(x - 11f + o.x, y + o.y), Offset(x + 11f + o.x, y + o.y), 1.1f, blendMode = BlendMode.Multiply)
            drawLine(c, Offset(x + o.x, y - 11f + o.y), Offset(x + o.x, y + 11f + o.y), 1.1f, blendMode = BlendMode.Multiply)
        }
    }

    private fun DrawScope.offcut(x: Float, y: Float, i: Int, sx: Float) {
        tmp.reset()
        when (i % 2) {
            0 -> tmp.addOval(Rect(x - 16f, y - 16f, x + 16f, y + 16f))
            else -> { tmp.moveTo(x - 30f, y + 10f); tmp.lineTo(x + 26f, y - 18f); tmp.lineTo(x + 16f, y + 20f); tmp.close() }
        }
        val ink = when (i) { 0 -> PaperInk.YELLOW; 1 -> PaperInk.BLUE; 2 -> PaperInk.YELLOW; else -> PaperInk.PINK }
        withTransform({ translate(sx * 0.4f, 3f) }) { drawPath(tmp, Color(0xFF9A8FA8), blendMode = BlendMode.Multiply) }
        drawPath(tmp, PaperInk.CARD)
        clipPath(tmp) { withTransform({ translate(1.5f, -1f) }) { drawPath(tmp, ink, blendMode = BlendMode.Multiply) } }
    }

    /** Cut-out capitals: each letter a card piece, slightly rotated, printed in one ink off register. */
    private fun DrawScope.title(x0: Float, y0: Float, shadowX: Float, ink: Color, reg: Offset, s: Float, text: String, alpha: Float = 1f) {
        if (alpha <= 0.01f) return
        val printed = Color(ink.red * PaperInk.CARD.red, ink.green * PaperInk.CARD.green, ink.blue * PaperInk.CARD.blue, 1f)
        var x = x0
        text.forEachIndexed { i, ch ->
            if (ch != ' ') {
                val path = CutType.glyph(ch)
                val rot = TILT[i % TILT.size]
                withTransform({ translate(x, y0); rotate(rot, Offset(5f * s, 7f * s)); scale(s, s, Offset.Zero) }) {
                    withTransform({ translate(shadowX / s * 0.6f, 4f / s) }) { drawPath(path, shadow, alpha = alpha, blendMode = BlendMode.Multiply) }
                    // The white card core shows where the ink plate slipped (no clipping).
                    withTransform({ translate(-reg.x / s, -reg.y / s) }) { drawPath(path, PaperInk.CORE, alpha = alpha) }
                    drawPath(path, printed, alpha = alpha)
                }
            }
            x += CutType.advance(ch) * s
        }
    }

    // ------------------------------------------------------------------ the back: sun wheel, moon and stars

    private fun DrawScope.sky(g: PGeo, sim: PaperSim) {
        val day = 1f - sim.moon
        if (day > 0.02f) {
            // The sun is a volvelle: a toothed pink wheel on a brad, turned by the pull-tab, under a yellow disc.
            val sx = g.l + 150f; val sy = 168f
            withTransform({ translate(sx, sy); rotate((sim.sunAngle * 57.3).toFloat(), Offset.Zero) }) {
                withTransform({ translate(sdx * 0.6f, sdy * 0.6f) }) { drawPath(g.sunTeeth, shadow, alpha = day, blendMode = BlendMode.Multiply) }
                drawPath(g.sunTeeth, PaperInk.CARD, alpha = day)
                over(g.sunTeeth, pink, PaperInk.REG_PINK, day)
            }
            withTransform({ translate(sx, sy) }) {
                drawPath(g.sunDisc, PaperInk.CARD, alpha = day)
                over(g.sunDisc, PaperInk.YELLOW, PaperInk.REG_YELLOW, day)
                drawPath(g.sunLower, tone, alpha = day, colorFilter = pinkTone, blendMode = BlendMode.Multiply)
            }
            drawCircle(PaperInk.BRAD, 4f, Offset(sx, sy), alpha = day)
        }
        val m = sim.moon
        if (m > 0.02f) {
            // A crescent moon and punched paper stars (circles and crescents, never pointed) on threads.
            val mx = g.r - 170f; val my = 176f
            drawLine(PaperInk.THREAD, Offset(mx, g.t - 30f), Offset(mx - 6f, my - 30f), 1f, alpha = m)
            withTransform({ translate(mx, my); rotate(-14f, Offset.Zero) }) {
                withTransform({ translate(sdx * 0.8f, sdy * 0.8f) }) { drawPath(g.moon, shadow, alpha = m, blendMode = BlendMode.Multiply) }
                drawPath(g.moon, PaperInk.CARD, alpha = m)
                over(g.moon, PaperInk.YELLOW, PaperInk.REG_YELLOW, m)
            }
            for (i in 0 until 7) {
                val x = g.l + 90f + i * 150f + h01(i, 7) * 40f
                val len = 50f + h01(i, 8) * 90f
                val sway = sin(sim.leafPhase * 0.5 + i).toFloat() * 3f
                drawLine(PaperInk.THREAD, Offset(x, g.t - 30f), Offset(x + sway, g.t + len), 0.8f, alpha = m)
                withTransform({ translate(x + sway, g.t + len + 10f) }) {
                    val p = if (i % 3 == 1) g.crescent else g.dot
                    val sc = if (i % 3 == 1) 1f else 0.5f
                    withTransform({ scale(sc, sc, Offset.Zero) }) {
                        drawPath(p, PaperInk.CARD, alpha = m)
                        over(p, PaperInk.YELLOW, PaperInk.REG_YELLOW, m)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the upgrade steps' additions (PaperStep)

    /** Step 3: one more plane behind the mountains (far hills in a fine blue halftone), standing on its own fold. */
    private fun DrawScope.farHills(g: PGeo, sim: PaperSim) {
        val period = 310f
        val s = (sim.strip * 0.10).toFloat()
        val first = floor(s / period).toInt()
        val off = s - first * period
        val base = g.foldBack - 26f
        val count = ((g.r - g.l) / period).toInt() + 3
        for (k in -1 until count) {
            val idx = first + k
            val x = g.l + k * period - off
            val h = 104f + h01(idx, 61) * 44f
            tmp.reset(); tmp.moveTo(x - 30f, base)
            tmp.quadraticTo(x + period * 0.28f, base - h * 1.5f, x + period * 0.62f, base - h * 0.5f)
            tmp.quadraticTo(x + period * 0.85f, base - h * 0.9f, x + period + 30f, base); tmp.close()
            piece(tmp, Color.White, Offset.Zero, lift = 0.7f)
            halftone(tmp, TONE_BLUE, fine = true)
        }
        crease(Offset(g.l + 8f, base), Offset(g.r - 8f, base))
    }

    /** Step 4: two clouds ride a paper strip in a slot across the right page's sky; the tab is pulled only while parked. */
    private fun DrawScope.cloudTab(g: PGeo, sim: PaperSim) {
        val y = 196f; val l = g.cx + 60f; val r = g.r - 60f
        drawRoundRectSlot(l, y, r - l)
        val x = l + 20f + (r - l - 350f) * sim.cloudSlide
        withTransform({ translate(x + 296f, y - 13f) }) { piece(g.tab, Color.White, Offset.Zero, PaperInk.KRAFT, lift = 0.5f) }
        for (c in 0 until 2) {
            val cx = x + c * 160f; val sc = if (c == 0) 1f else 0.8f
            tmp.reset()
            tmp.addOval(Rect(cx, y - 30f * sc, cx + 60f * sc, y + 2f)); tmp.addOval(Rect(cx + 32f * sc, y - 54f * sc, cx + 98f * sc, y + 2f)); tmp.addOval(Rect(cx + 76f * sc, y - 34f * sc, cx + 134f * sc, y + 2f))
            piece(tmp, Color.White, Offset.Zero, lift = 0.6f)
            tmp2.reset(); tmp2.addOval(Rect(cx + 8f * sc, y - 12f * sc, cx + 126f * sc, y + 2f))
            halftone(tmp2, TONE_BLUE, fine = true)
        }
    }

    /**
     * Step 4, on the left page: a paper balloon on its own tab, striped in the edition's inks. It rides the same
     * pull as the clouds (so it moves only while parked) and hangs still otherwise.
     */
    private fun DrawScope.balloon(g: PGeo, sim: PaperSim) {
        val x = g.cx - 150f; val y = 178f - 26f * sim.cloudSlide
        // The tab's slot and the strip the balloon stands on.
        drawRoundRectSlot(x - 5f, 96f, 10f)
        drawLine(PaperInk.KRAFT_DARK, Offset(x, 104f), Offset(x, y - 58f), 3f)
        tmp.reset(); tmp.addOval(Rect(x - 56f, y - 66f, x + 56f, y + 50f))
        piece(tmp, Color.White, Offset.Zero, lift = 0.9f)
        clipPath(tmp) {
            val inks = arrayOf(PaperInk.PINK, PaperInk.YELLOW, PaperInk.BLUE)
            for (i in 0 until 7) if (i % 2 == 0) drawRect(inks[(i / 2) % 3], Offset(x - 56f + i * 16f, y - 66f), Size(16f, 116f))
            drawRect(Color.Black.copy(alpha = 0.10f), Offset(x + 14f, y - 66f), Size(42f, 116f))
        }
        tmp2.reset(); tmp2.addOval(Rect(x - 40f, y + 20f, x + 40f, y + 50f))
        halftone(tmp2, TONE_BLUE, fine = true)
        // The ropes and the basket.
        for (dx in floatArrayOf(-22f, 22f)) drawLine(PaperInk.KRAFT_DARK, Offset(x + dx, y + 44f), Offset(x + dx * 0.5f, y + 72f), 1.6f)
        tmp.reset(); tmp.moveTo(x - 15f, y + 70f); tmp.lineTo(x + 15f, y + 70f); tmp.lineTo(x + 12f, y + 92f); tmp.lineTo(x - 12f, y + 92f); tmp.close()
        piece(tmp, PaperInk.KRAFT, Offset.Zero, PaperInk.KRAFT, lift = 0.7f)
        drawLine(PaperInk.KRAFT_DARK, Offset(x - 14f, y + 78f), Offset(x + 14f, y + 78f), 1.4f)
    }

    /** Step 3's front layer: a row of paper flowers on their own fold, in front of the road (the book now has three layers). */
    private fun DrawScope.flowers(g: PGeo, sim: PaperSim) {
        val period = 58f
        val s = (sim.strip * 1.25).toFloat()
        val first = floor(s / period).toInt()
        val off = s - first * period
        val base = g.foldFront + 8f
        val count = ((g.r - g.l) / period).toInt() + 3
        for (k in -1 until count) {
            val idx = first + k
            val x = g.l + k * period - off + h01(idx, 71) * 16f
            val hgt = 36f + h01(idx, 72) * 26f
            drawLine(PaperInk.BLUE, Offset(x, base), Offset(x, base - hgt), 3.2f)
            tmp.reset(); tmp.moveTo(x, base - hgt * 0.35f); tmp.quadraticTo(x + 15f, base - hgt * 0.62f, x + 22f, base - hgt * 0.3f); tmp.quadraticTo(x + 10f, base - hgt * 0.24f, x, base - hgt * 0.35f); tmp.close()
            drawPath(tmp, PaperInk.BLUE)
            val head = if (Math.floorMod(idx, 3) == 0) PaperInk.YELLOW else PaperInk.PINK
            val top = base - hgt
            tmp.reset()
            if (Math.floorMod(idx, 2) == 0) {
                tmp.moveTo(x - 12f, top - 18f); tmp.lineTo(x - 6f, top - 9f); tmp.lineTo(x, top - 21f); tmp.lineTo(x + 6f, top - 9f); tmp.lineTo(x + 12f, top - 18f); tmp.lineTo(x + 10f, top + 3f); tmp.lineTo(x - 10f, top + 3f); tmp.close()
            } else tmp.addOval(Rect(x - 13f, top - 19f, x + 13f, top + 7f))
            piece(tmp, head, Offset.Zero, lift = 0.5f)
            if (Math.floorMod(idx, 2) != 0) drawCircle(PaperInk.CARD, 4.6f, Offset(x, top - 6f))
        }
        crease(Offset(g.l + 8f, base), Offset(g.r - 8f, base))
    }

    /** Step 4, on the right page: a paper kite on its string, bows on its tail. It rides the pull tab with the clouds. */
    private fun DrawScope.kite(g: PGeo, sim: PaperSim) {
        val x = g.cx + 318f + 46f * sim.cloudSlide; val y = 268f
        drawLine(PaperInk.KRAFT_DARK, Offset(x, y + 40f), Offset(x - 84f, g.foldBack - 6f), 1.5f)
        tmp.reset(); tmp.moveTo(x, y - 48f); tmp.lineTo(x + 36f, y); tmp.lineTo(x, y + 42f); tmp.lineTo(x - 36f, y); tmp.close()
        piece(tmp, PaperInk.PINK, Offset.Zero, lift = 0.8f)
        clipPath(tmp) {
            drawRect(PaperInk.YELLOW, Offset(x, y - 48f), Size(36f, 48f))
            drawRect(PaperInk.BLUE, Offset(x - 36f, y), Size(36f, 42f))
        }
        drawLine(PaperInk.CARD, Offset(x, y - 48f), Offset(x, y + 42f), 1.6f); drawLine(PaperInk.CARD, Offset(x - 36f, y), Offset(x + 36f, y), 1.6f)
        for (i in 0 until 3) {
            val bx = x - 16f - i * 18f; val by = y + 54f + i * 13f
            tmp2.reset(); tmp2.moveTo(bx - 9f, by - 6f); tmp2.lineTo(bx + 9f, by + 6f); tmp2.lineTo(bx + 9f, by - 6f); tmp2.lineTo(bx - 9f, by + 6f); tmp2.close()
            drawPath(tmp2, if (i % 2 == 0) PaperInk.YELLOW else PaperInk.PINK)
        }
    }

    /** Step 5: gold foil stamped on the special edition: a rule inside each page's edge and punched discs in the sky. */
    private fun DrawScope.foil(g: PGeo) {
        val rule = androidx.compose.ui.graphics.drawscope.Stroke(2.6f)
        for ((x0, x1) in listOf(g.l + 14f to g.cx - 22f, g.cx + 22f to g.r - 14f)) {
            drawRect(PaperInk.GOLD, Offset(x0, g.t + 24f), Size(x1 - x0, g.b - g.t - 42f), style = rule)
            drawLine(PaperInk.GOLD_LIGHT, Offset(x0 + 6f, g.t + 23.4f), Offset(x1 - (x1 - x0) * 0.4f, g.t + 23.4f), 1f)
        }
        for (i in 0 until 10) {
            val x = g.l + 70f + h01(i, 91) * (g.r - g.l - 140f); val y = g.t + 40f + h01(i, 92) * 120f; val r = 4f + h01(i, 93) * 5f
            drawCircle(PaperInk.GOLD, r, Offset(x, y))
            drawArc(PaperInk.GOLD_LIGHT, 200f, 110f, false, Offset(x - r * 0.65f, y - r * 0.65f), Size(r * 1.3f, r * 1.3f), style = hairline)
        }
    }

    // ------------------------------------------------------------------ the planes

    /** Paper mountains: halftone lit faces, solid shade faces, bare white card for the snow, a crease down each ridge. */
    private fun DrawScope.mountains(g: PGeo, sim: PaperSim) {
        val period = 236f
        val s = (sim.strip * 0.22).toFloat()
        val first = floor(s / period).toInt()
        val off = s - first * period
        val base = g.foldBack
        val shadeRight = sim.night < 0.5f
        val count = ((g.r - g.l) / period).toInt() + 3
        for (k in -1 until count) {
            val idx = first + k
            val x = g.l + k * period - off - 60f
            val h = 120f + h01(idx, 1) * 90f
            val w = 250f + h01(idx, 2) * 90f
            val px = x + w * (0.4f + h01(idx, 3) * 0.2f)
            val py = base - h
            tmp.reset(); tmp.moveTo(x, base); tmp.lineTo(px, py); tmp.lineTo(x + w, base); tmp.close()
            withTransform({ translate(sdx, sdy) }) { drawPath(tmp, shadow, blendMode = BlendMode.Multiply) }
            drawPath(tmp, PaperInk.CARD)
            halftone(tmp, TONE_BLUE)
            // The shade face, printed solid.
            tmp2.reset()
            if (shadeRight) { tmp2.moveTo(px, py); tmp2.lineTo(x + w, base); tmp2.lineTo(px + (x + w - px) * 0.18f, base); tmp2.close() }
            else { tmp2.moveTo(px, py); tmp2.lineTo(x, base); tmp2.lineTo(px - (px - x) * 0.18f, base); tmp2.close() }
            over(tmp2, PaperInk.BLUE, PaperInk.REG_BLUE)
            // Snow: the bare card, cut with a zig-zag.
            val cap = 0.24f
            tmp2.reset(); tmp2.moveTo(px, py)
            val lx = px + (x - px) * cap; val rx = px + (x + w - px) * cap; val cy = py + h * cap
            tmp2.lineTo(rx, cy); tmp2.lineTo(rx - (rx - px) * 0.35f, cy - 10f); tmp2.lineTo(px, cy + 4f); tmp2.lineTo(lx + (px - lx) * 0.35f, cy - 10f); tmp2.lineTo(lx, cy); tmp2.close()
            drawPath(tmp2, PaperInk.CORE)
            crease(Offset(px, py), Offset(px + (if (shadeRight) 1f else -1f) * (w * 0.18f * 0.5f), base))
        }
    }

    private fun DrawScope.scenery(g: PGeo, sim: PaperSim) {
        // The newest scene stands up from its fold with a paper flop; the previous one lies down behind it.
        val e = flop(sim.rise())
        if (e < 1f && sim.rise() < 1f) {
            val down = 1f - sim.rise()
            withTransform({ scale(1f, down * down, Offset(0f, g.foldMid)) }) { strip(g, sim, sim.biomeBefore) }
        }
        withTransform({ scale(1f, max(0.02f, e), Offset(0f, g.foldMid)) }) { strip(g, sim, sim.biomeNow) }
    }

    private fun DrawScope.strip(g: PGeo, sim: PaperSim, b: Biome) {
        val period = 210f
        val s = (sim.strip * 0.55).toFloat()
        val first = floor(s / period).toInt()
        val off = s - first * period
        val base = g.foldMid
        val count = ((g.r - g.l) / period).toInt() + 3
        for (k in -1 until count) {
            val idx = first + k
            val x = g.l + k * period - off
            val v = h01(idx, 11)
            when (b) {
                Biome.CITY -> house(x + 40f, base, v, Math.floorMod(idx, 3))
                Biome.PARK -> { tree(x + 60f, base, 110f + v * 50f); if (v > 0.5f) tree(x + 150f, base, 80f + v * 20f) }
                Biome.FIELD -> { accordion(x + 10f, base, v); pinwheel(x + 175f, base, sim, idx) }
                Biome.SEASIDE -> { river(x, base, period); if (idx % 2 == 0) bridge(x + 30f, base) else boat(x + 70f, base, v) }
                Biome.MOUNTAIN -> { pine(x + 50f, base, 120f + v * 50f); pine(x + 110f, base, 80f + v * 30f); if (v > 0.6f) boulder(x + 170f, base) }
            }
        }
    }

    /** A folded paper house: a printed front, a shaded side wall, a purple roof (blue over pink) and a punched window. */
    private fun DrawScope.house(x: Float, base: Float, v: Float, inkIndex: Int) {
        val w = 78f; val wall = if (v > 0.5f) 82f else 54f; val roof = 34f
        val ink = when (inkIndex) { 0 -> pink; 1 -> PaperInk.YELLOW; else -> PaperInk.BLUE }
        val reg = CONFETTI_REG[inkIndex]
        val tone = when (inkIndex) { 0 -> pinkTone; 1 -> pinkTone; else -> TONE_BLUE }
        val roofInk = if (inkIndex == 2) PaperInk.YELLOW else PaperInk.BLUE
        tmp.reset(); tmp.moveTo(x + w, base); tmp.lineTo(x + w, base - wall); tmp.lineTo(x + w + 24f, base - wall - 10f); tmp.lineTo(x + w + 24f, base - 8f); tmp.close()
        piece(tmp, ink, reg); halftone(tmp, tone)
        tmp.reset(); tmp.moveTo(x, base); tmp.lineTo(x, base - wall); tmp.lineTo(x + w / 2f, base - wall - roof); tmp.lineTo(x + w, base - wall); tmp.lineTo(x + w, base); tmp.close()
        piece(tmp, ink, reg)
        tmp2.reset(); tmp2.moveTo(x - 4f, base - wall + 2f); tmp2.lineTo(x + w / 2f, base - wall - roof - 2f); tmp2.lineTo(x + w + 4f, base - wall + 2f); tmp2.lineTo(x + w - 8f, base - wall + 10f); tmp2.lineTo(x + w / 2f, base - wall - roof + 12f); tmp2.lineTo(x + 8f, base - wall + 10f); tmp2.close()
        over(tmp2, roofInk, if (inkIndex == 2) PaperInk.REG_YELLOW else PaperInk.REG_BLUE)
        // A cut-out arched door (the shaded page shows through).
        tmp2.reset(); tmp2.moveTo(x + w / 2f - 11f, base); tmp2.lineTo(x + w / 2f - 11f, base - 22f)
        tmp2.quadraticTo(x + w / 2f, base - 38f, x + w / 2f + 11f, base - 22f); tmp2.lineTo(x + w / 2f + 11f, base); tmp2.close()
        drawPath(tmp2, PaperInk.PAGE); drawPath(tmp2, shadow, blendMode = BlendMode.Multiply)
        crease(Offset(x + w, base - wall), Offset(x + w, base))
    }

    /** An origami tree: stacked triangles printed yellow with blue over it (green), shaded on one face. */
    private fun DrawScope.tree(x: Float, base: Float, h: Float) {
        tmp.reset(); tmp.addRect(Rect(x - 6f, base - 30f, x + 6f, base)); piece(tmp, Color.White, Offset.Zero, PaperInk.KRAFT)
        for (i in 0 until 3) {
            val w = 92f - i * 20f
            val bot = base - 22f - i * h * 0.24f
            val top = bot - h * 0.42f
            tmp.reset(); tmp.moveTo(x - w / 2f, bot); tmp.lineTo(x, top); tmp.lineTo(x + w / 2f, bot); tmp.close()
            piece(tmp, PaperInk.YELLOW, PaperInk.REG_YELLOW)
            over(tmp, PaperInk.BLUE, PaperInk.REG_BLUE, 0.85f)
            tmp2.reset()
            if (sdx > 0f) { tmp2.moveTo(x, top); tmp2.lineTo(x + w / 2f, bot); tmp2.lineTo(x, bot); tmp2.close() }
            else { tmp2.moveTo(x, top); tmp2.lineTo(x - w / 2f, bot); tmp2.lineTo(x, bot); tmp2.close() }
            halftone(tmp2, TONE_BLUE)
            crease(Offset(x, top), Offset(x, bot))
        }
    }

    /** An accordion-folded field: a fan-fold strip in yellow, every other facet shaded with pink dots. */
    private fun DrawScope.accordion(x: Float, base: Float, v: Float) {
        val n = 9; val fw = 16f; val hi = 46f + v * 18f
        tmp.reset(); tmp.moveTo(x, base)
        for (i in 0..n) tmp.lineTo(x + i * fw, base - hi - (if (i % 2 == 0) 0f else 12f))
        tmp.lineTo(x + n * fw, base); tmp.close()
        piece(tmp, PaperInk.YELLOW, PaperInk.REG_YELLOW)
        for (i in 0 until n) if (i % 2 == 1) {
            tmp2.reset(); tmp2.moveTo(x + i * fw, base); tmp2.lineTo(x + i * fw, base - hi - 12f); tmp2.lineTo(x + (i + 1) * fw, base - hi); tmp2.lineTo(x + (i + 1) * fw, base); tmp2.close()
            halftone(tmp2, pinkTone)
        }
    }

    /** A paper pinwheel on a stick: the fan turns it. */
    private fun DrawScope.pinwheel(x: Float, base: Float, sim: PaperSim, idx: Int) {
        val top = base - 104f
        drawLine(PaperInk.KRAFT_DARK, Offset(x, base), Offset(x, top), 4f)
        val a = (sim.leafPhase * 57.3 * 2.0).toFloat() + idx * 23f
        withTransform({ translate(x, top); rotate(a, Offset.Zero) }) {
            for (i in 0 until 4) {
                withTransform({ rotate(i * 90f, Offset.Zero) }) {
                    tmp.reset(); tmp.moveTo(0f, 0f); tmp.lineTo(0f, -30f); tmp.lineTo(22f, -8f); tmp.close()
                    piece(tmp, if (i % 2 == 0) pink else PaperInk.BLUE, if (i % 2 == 0) PaperInk.REG_PINK else PaperInk.REG_BLUE, lift = 0.5f)
                }
            }
        }
        drawCircle(PaperInk.BRAD, 3.5f, Offset(x, top))
    }

    /** An accordion-folded river: a blue zig-zag band, alternate facets darker. */
    private fun DrawScope.river(x: Float, base: Float, w: Float) {
        val n = (w / 20f).toInt() + 1
        tmp.reset(); tmp.moveTo(x, base)
        for (i in 0..n) tmp.lineTo(x + i * 20f, base - 26f - (if (i % 2 == 0) 0f else 8f))
        tmp.lineTo(x + n * 20f, base); tmp.close()
        piece(tmp, PaperInk.BLUE, PaperInk.REG_BLUE, lift = 0.5f)
        halftone(tmp, TONE_BLUE, fine = true)
    }

    /** A pop-up arch bridge in pink (the arch is cut out of the card). */
    private fun DrawScope.bridge(x: Float, base: Float) {
        tmp.reset(); tmp.fillType = PathFillType.EvenOdd
        tmp.moveTo(x, base); tmp.lineTo(x, base - 70f); tmp.lineTo(x + 160f, base - 70f); tmp.lineTo(x + 160f, base); tmp.close()
        tmp.addOval(Rect(x + 22f, base - 52f, x + 138f, base + 52f))
        piece(tmp, pink, PaperInk.REG_PINK)
        tmp2.reset(); tmp2.addRect(Rect(x, base - 70f, x + 160f, base - 60f))
        over(tmp2, PaperInk.YELLOW, PaperInk.REG_YELLOW)
        for (i in 0 until 7) crease(Offset(x + 12f + i * 22f, base - 70f), Offset(x + 12f + i * 22f, base - 60f), 1f)
        tmp.fillType = PathFillType.NonZero
    }

    /** An origami boat: a blue hull and two white sails with yellow printed on one. */
    private fun DrawScope.boat(x: Float, base: Float, v: Float) {
        val y = base - 26f
        tmp.reset(); tmp.moveTo(x - 40f, y - 18f); tmp.lineTo(x + 44f, y - 18f); tmp.lineTo(x + 26f, y + 2f); tmp.lineTo(x - 24f, y + 2f); tmp.close()
        piece(tmp, PaperInk.BLUE, PaperInk.REG_BLUE)
        tmp.reset(); tmp.moveTo(x, y - 20f); tmp.lineTo(x, y - 86f - v * 14f); tmp.lineTo(x + 38f, y - 20f); tmp.close()
        piece(tmp, PaperInk.YELLOW, PaperInk.REG_YELLOW)
        tmp.reset(); tmp.moveTo(x - 4f, y - 20f); tmp.lineTo(x - 4f, y - 70f); tmp.lineTo(x - 30f, y - 20f); tmp.close()
        piece(tmp, PaperInk.CARD, Offset.Zero)
        halftone(tmp, TONE_BLUE, fine = true)
    }

    /** A folded pine: one face yellow under blue (green), the other solid blue. */
    private fun DrawScope.pine(x: Float, base: Float, h: Float) {
        val w = h * 0.42f
        tmp.reset(); tmp.moveTo(x - w / 2f, base); tmp.lineTo(x, base - h); tmp.lineTo(x + w / 2f, base); tmp.close()
        piece(tmp, PaperInk.BLUE, PaperInk.REG_BLUE)
        tmp2.reset()
        if (sdx > 0f) { tmp2.moveTo(x - w / 2f, base); tmp2.lineTo(x, base - h); tmp2.lineTo(x, base); tmp2.close() }
        else { tmp2.moveTo(x + w / 2f, base); tmp2.lineTo(x, base - h); tmp2.lineTo(x, base); tmp2.close() }
        over(tmp2, PaperInk.YELLOW, PaperInk.REG_YELLOW)
        tmp2.reset(); tmp2.moveTo(x, base - h); tmp2.lineTo(x + w * 0.12f, base - h * 0.78f); tmp2.lineTo(x - w * 0.12f, base - h * 0.78f); tmp2.close()
        drawPath(tmp2, PaperInk.CORE)
        crease(Offset(x, base - h), Offset(x, base))
    }

    private fun DrawScope.boulder(x: Float, base: Float) {
        tmp.reset(); tmp.moveTo(x - 30f, base); tmp.lineTo(x - 24f, base - 30f); tmp.lineTo(x + 4f, base - 42f); tmp.lineTo(x + 30f, base - 26f); tmp.lineTo(x + 34f, base); tmp.close()
        piece(tmp, Color.White, Offset.Zero, PaperInk.KRAFT)
        crease(Offset(x + 4f, base - 42f), Offset(x, base)); crease(Offset(x - 24f, base - 30f), Offset(x, base))
    }

    // ------------------------------------------------------------------ the route ribbon and the front fringe

    /** The kraft route ribbon, accordion-folded: alternate facets in shade, hairline creases, scrolling with the drive. */
    private fun DrawScope.ribbon(g: PGeo, sim: PaperSim) {
        val top = g.ribbonTop; val bot = g.ribbonBot
        drawRect(shadow, Offset(g.l, top + 6f), Size(g.r - g.l, bot - top), blendMode = BlendMode.Multiply)
        drawRect(PaperInk.KRAFT, Offset(g.l, top), Size(g.r - g.l, bot - top))
        val fw = 72f; val slant = 18f
        val s = (sim.strip % (fw * 2)).toFloat()
        var x = g.l - fw * 2 - s
        var i = 0
        while (x < g.r + fw) {
            if (i % 2 == 0) {
                tmp.reset(); tmp.moveTo(x, top); tmp.lineTo(x + fw, top); tmp.lineTo(x + fw - slant, bot); tmp.lineTo(x - slant, bot); tmp.close()
                drawPath(tmp, PaperInk.KRAFT_DARK, alpha = 0.55f, blendMode = BlendMode.Multiply)
            }
            drawLine(Color(0xFF8D6A48), Offset(x, top), Offset(x - slant, bot), 1.2f)
            x += fw; i++
        }
        drawLine(Color(0xFFE3C7A2), Offset(g.l, top), Offset(g.r, top), 1.4f)
    }

    /** The cut-paper grass fringe on the front fold (yellow under blue: green), in one long piece. */
    private fun DrawScope.fringe(g: PGeo, sim: PaperSim) {
        val step = 18f
        val s = ((sim.strip * 1.3) % (step * 64)).toFloat()
        val base = g.foldFront
        tmp.reset(); tmp.moveTo(g.l - 40f, base)
        var x = g.l - 40f - (s % step)
        var i = floor((s) / step).toInt()
        while (x < g.r + 40f) {
            val h = 14f + h01(i, 21) * 26f
            tmp.lineTo(x + step * 0.5f, base - h); tmp.lineTo(x + step, base - 4f)
            x += step; i++
        }
        tmp.lineTo(g.r + 40f, base); tmp.lineTo(g.r + 40f, base + 6f); tmp.lineTo(g.l - 40f, base + 6f); tmp.close()
        piece(tmp, PaperInk.YELLOW, PaperInk.REG_YELLOW)
        over(tmp, PaperInk.BLUE, PaperInk.REG_BLUE, 0.55f)
    }

    // ------------------------------------------------------------------ the origami car and the crane

    private fun DrawScope.carScene(g: PGeo, sim: PaperSim) {
        val kx = g.carX; val ky = g.carY + sim.bounce
        // Braking: a puff of punched paper dots behind.
        val dt = sim.since(sim.dustAt).toFloat()
        if (dt in 0f..0.9f) {
            val a = if (dt < 0.3f) dt / 0.3f else 1f - (dt - 0.3f) / 0.6f
            for (i in 0 until 3) {
                withTransform({ translate(kx - 14f - i * 22f, ky - 14f - i * 7f); scale(0.5f + i * 0.15f, 0.5f + i * 0.15f, Offset.Zero) }) {
                    drawPath(g.dot, PaperInk.CARD, alpha = a); over(g.dot, PaperInk.KRAFT_DARK, Offset.Zero, a * 0.6f)
                }
            }
        }
        // Headlamps: a cone of yellow cellophane.
        if (sim.cone > 0.02f) withTransform({ translate(kx, ky); scale(g.carScale, g.carScale, Offset.Zero) }) { drawPath(g.cone, PaperInk.YELLOW, alpha = 0.55f * sim.cone, blendMode = BlendMode.Multiply) }
        val brake = sim.car.brakePressed == true || sim.since(sim.dustAt) in 0.0..0.9
        val flap = max(max(sim.blinkL, sim.blinkR), if (sim.happy) (0.5f + 0.5f * sin(sim.decoTime * 2 * PI * 0.9).toFloat()) else 0f)
        car(g, kx, ky, sim.wheelAngle.toFloat(), sim.hingeAngle, flap, if (brake) 1f else 0f, sim.blinkL, sim.blinkR)
    }

    /**
     * The origami car at ([kx], [ky]) with its crane. [tip] is the hinge angle (the car tips back on its rear wheel
     * after a harsh brake, the crane rocks on its feet); [flap] lifts the crane's wings; [tuck] folds its head down.
     */
    private fun DrawScope.car(g: PGeo, kx: Float, ky: Float, wheel: Float, tip: Float, flap: Float, tuck: Float, blinkL: Float, blinkR: Float) {
        withTransform({ translate(kx, ky); scale(g.carScale, g.carScale, Offset.Zero); rotate(-abs(tip) * 0.25f, g.wheelRear) }) {
            // The car's own flat shadow on the ribbon.
            withTransform({ translate(sdx, 2f) }) { drawPath(g.carAll, shadow, blendMode = BlendMode.Multiply) }
            drawPath(g.carAll, PaperInk.CARD)
            // Faceted planes: lit pink, a shaded door, a darker sill, glass in blue (purple where it meets pink).
            over(g.carRear, pink, PaperInk.REG_PINK)
            over(g.carDoor, pink, PaperInk.REG_PINK); halftone(g.carDoor, pinkTone, fine = true)
            over(g.carHood, pink, PaperInk.REG_PINK)
            over(g.carSill, pink, PaperInk.REG_PINK, 0.6f); halftone(g.carSill, pinkTone)
            over(g.carRoof, pink, PaperInk.REG_PINK)
            over(g.carBackGlass, PaperInk.BLUE, PaperInk.REG_BLUE); over(g.carBackGlass, pink, PaperInk.REG_PINK, 0.7f)
            over(g.carSideGlass, PaperInk.BLUE, PaperInk.REG_BLUE)
            over(g.carWindscreen, PaperInk.BLUE, PaperInk.REG_BLUE); halftone(g.carWindscreen, TONE_BLUE)
            // Fold lines between the planes.
            crease(Offset(46f, -44f), Offset(54f, -14f)); crease(Offset(140f, -46f), Offset(150f, -13f))
            crease(Offset(46f, -44f), Offset(140f, -46f)); crease(Offset(6f, -24f), Offset(182f, -22f))
            crease(Offset(68f, -44f), Offset(78f, -74f), 1f); crease(Offset(124f, -45f), Offset(106f, -74f), 1f)
            // Paper wheels on brads: a printed blue ring and two crease lines that turn.
            paperWheel(g, g.wheelRear, wheel); paperWheel(g, g.wheelFront, wheel)
            // Blinkers: a cut-out arrow flips out on its hinge at the car's rhythm.
            if (blinkL > 0.02f) withTransform({ translate(4f, -26f); scale(-blinkL, 1f, Offset.Zero) }) { piece(g.arrow, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0.4f); over(g.arrow, pink, PaperInk.REG_PINK, 0.35f) }
            if (blinkR > 0.02f) withTransform({ translate(182f, -26f); scale(blinkR, 1f, Offset.Zero) }) { piece(g.arrow, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0.4f); over(g.arrow, pink, PaperInk.REG_PINK, 0.35f) }
            // The crane rides on the roof and rocks on its feet.
            withTransform({ translate(90f, -79f); rotate(tip * 0.6f, Offset.Zero) }) { crane(g, flap, tuck) }
        }
    }

    private fun DrawScope.paperWheel(g: PGeo, c: Offset, wheel: Float) {
        withTransform({ translate(c.x, c.y); rotate(wheel * 57.3f, Offset.Zero) }) {
            withTransform({ translate(sdx * 0.4f, 3f) }) { drawPath(g.wheel, shadow, blendMode = BlendMode.Multiply) }
            drawPath(g.wheel, PaperInk.CARD)
            over(g.wheelInk, PaperInk.BLUE, PaperInk.REG_BLUE)
            crease(Offset(-g.wheelR, 0f), Offset(g.wheelR, 0f)); crease(Offset(0f, -g.wheelR), Offset(0f, g.wheelR))
        }
        drawCircle(PaperInk.BRAD, 3.2f, c)
    }

    /**
     * The folded crane: printed yellow, the far wing overprinted pink (orange-red where the inks meet), blue fold
     * lines. [flap] opens the wings on their hinge; [tuck] folds the neck down (braking).
     */
    private fun DrawScope.crane(g: PGeo, flap: Float, tuck: Float) {
        withTransform({ scale(0.8f, 0.8f, Offset.Zero) }) {
            withTransform({ rotate(flap * 22f, g.craneHinge) }) { piece(g.craneWingFar, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0.5f); over(g.craneWingFar, pink, PaperInk.REG_PINK, 0.75f) }
            piece(g.craneTail, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0.5f)
            withTransform({ rotate(tuck * 40f, g.craneNeckHinge) }) {
                piece(g.craneNeck, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0.5f)
                piece(g.craneHead, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0f); over(g.craneHead, pink, PaperInk.REG_PINK)
            }
            piece(g.craneBody, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0.5f)
            halftone(g.craneBody, pinkTone, fine = true)
            withTransform({ rotate(-flap * 30f, g.craneHinge) }) {
                piece(g.craneWingNear, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0.5f)
                drawLine(PaperInk.BLUE, Offset(-14f, -18f), Offset(16f, -18f), 1.6f, blendMode = BlendMode.Multiply)
                drawLine(PaperInk.BLUE, Offset(-21f, -45f), Offset(16f, -18f), 1f, blendMode = BlendMode.Multiply)
            }
            drawLine(PaperInk.BLUE, Offset(-22f, -12f), Offset(24f, -12f), 1.2f, blendMode = BlendMode.Multiply)
        }
    }

    // ------------------------------------------------------------------ hanging things, rewards and the book

    /** Paper drops (rain) and card snowflakes (A/C) hang on threads and swing. */
    private fun DrawScope.threads(g: PGeo, sim: PaperSim) {
        val swing = sin(sim.rainSwing).toFloat() * 12f
        if (sim.rain > 0.02f) for (i in 0 until 9) {
            val x = g.l + 110f + i * 104f; val len = 80f + (i % 3) * 40f
            withTransform({ rotate(swing * (if (i % 2 == 0) 1f else -0.8f), Offset(x, g.t - 30f)) }) {
                drawLine(PaperInk.THREAD, Offset(x, g.t - 30f), Offset(x, g.t + len), 0.8f, alpha = sim.rain)
                withTransform({ translate(x, g.t + len + 14f); scale(1.6f, 1.6f, Offset.Zero) }) {
                    drawPath(g.drop, PaperInk.CARD, alpha = sim.rain); over(g.drop, PaperInk.BLUE, PaperInk.REG_BLUE, sim.rain)
                }
            }
        }
        if (sim.ac > 0.02f) for (i in 0 until 6) {
            val x = g.l + 170f + i * 150f; val len = 56f + (i % 2) * 50f
            withTransform({ rotate(sin(sim.leafPhase + i).toFloat() * 6f, Offset(x, g.t - 30f)) }) {
                drawLine(PaperInk.THREAD, Offset(x, g.t - 30f), Offset(x, g.t + len), 0.8f, alpha = sim.ac)
                val cy = g.t + len + 14f
                drawCircle(PaperInk.CARD, 15f, Offset(x, cy), alpha = sim.ac)
                for (a in 0 until 3) {
                    val ang = a * PI.toFloat() / 3f
                    drawLine(PaperInk.BLUE, Offset(x - cos(ang) * 12f, cy - sin(ang) * 12f), Offset(x + cos(ang) * 12f, cy + sin(ang) * 12f), 3f, cap = StrokeCap.Round, alpha = sim.ac, blendMode = BlendMode.Multiply)
                }
            }
        }
    }

    /**
     * The reward: a punched confetti dot flies in from the right margin, flipping over (front printed, back bare
     * card), and lands in a row on the right page; after a while it lies down flat on its hinge.
     */
    private fun DrawScope.confetti(g: PGeo, sim: PaperSim) {
        for (k in 0 until PaperSim.STARS) {
            val t = sim.since(sim.starPopAt[k]).toFloat()
            if (t < 0f || t > 9f) continue
            val slot = k
            val tx = g.cx + 100f + slot * 66f; val ty = 214f
            val u = (t / 0.9f).coerceIn(0f, 1f)
            val e = 1f - (1f - u) * (1f - u) * (1f - u)
            val sx = g.r + 70f; val sy = 120f + slot * 18f
            val x = sx + (tx - sx) * e
            val y = sy + (ty - sy) * e - sin(u * PI.toFloat()) * 70f
            val turn = cos(u * PI.toFloat() * 3f)
            val face = turn >= 0f || u >= 1f
            val sxScale = if (u >= 1f) 1f else max(0.08f, abs(turn))
            val down = if (t > 8f) 1f - (t - 8f) else 1f
            val ink = CONFETTI[slot % CONFETTI.size]
            val reg = CONFETTI_REG[slot % CONFETTI.size]
            withTransform({ translate(x, y); scale(sxScale * 1.7f, (if (u < 1f) 1.7f else down * 1.7f), Offset(0f, 13f)) }) {
                withTransform({ translate(sdx * 0.8f, sdy * 0.8f) }) { drawPath(g.dot, shadow, blendMode = BlendMode.Multiply) }
                drawPath(g.dot, PaperInk.CARD)
                if (face) {
                    over(g.dot, if (ink == PaperInk.PINK) pink else ink, reg)
                    over(g.ring, if (ink == PaperInk.BLUE) PaperInk.YELLOW else PaperInk.BLUE, PaperInk.REG_BLUE, 0.85f)
                }
            }
        }
    }

    /** The combo: a pull-tab strip of paper bunting; the flags flop down one after another along the string. */
    private fun DrawScope.garland(g: PGeo, sim: PaperSim) {
        val t = sim.since(sim.garlandAt).toFloat()
        if (t < 0f || t > 14f) return
        val y = 92f
        val x0 = g.l + 90f; val x1 = g.r - 130f
        val pull = if (t < 1.4f) t / 1.4f else if (t > 12.6f) 1f - (t - 12.6f) / 1.4f else 1f
        drawLine(PaperInk.THREAD, Offset(x0, y), Offset(x0 + (x1 - x0) * pull, y), 1.4f)
        val n = ((x1 - x0) / 48f).toInt()
        for (i in 0 until n) {
            val start = 0.2f + i * 0.05f
            val open = if (t < 12.6f) flop((t - start) / 0.45f) else flop(1f - (t - 12.6f - (n - i) * 0.03f) / 0.4f)
            if (open <= 0.02f) continue
            val x = x0 + 8f + i * 48f
            if (x > x0 + (x1 - x0) * pull) break
            val ink = CONFETTI[i % CONFETTI.size]
            withTransform({ translate(x, y); scale(1f, open, Offset.Zero) }) {
                piece(g.flag, if (ink == PaperInk.PINK) pink else ink, CONFETTI_REG[i % CONFETTI.size], lift = 0.6f)
            }
        }
        // The pull-tab at the end of the strip.
        withTransform({ translate(x0 + (x1 - x0) * pull + 6f, y - 13f) }) {
            piece(g.tab, Color.White, Offset.Zero, PaperInk.KRAFT, lift = 0.5f)
            over(g.tabArrow, pink, PaperInk.REG_PINK)
        }
    }

    /** The pull-tab at the page's foot slides with the speed (it turns the sun wheel). */
    private fun DrawScope.pullTab(g: PGeo, sim: PaperSim) {
        withTransform({ translate(g.l + 64f + 186f * sim.tab, g.b - 40f) }) {
            piece(g.tab, Color.White, Offset.Zero, PaperInk.KRAFT, lift = 0.5f)
            over(g.tabArrow, pink, PaperInk.REG_PINK)
        }
    }

    private fun DrawScope.bookmark(g: PGeo, sim: PaperSim) {
        val slide = (sim.since(sim.bookmarkAt) / 1.0).toFloat().coerceIn(0f, 1f)
        val target = (sim.bookmark % 20) * 46f
        val prev = ((sim.bookmark - 1).coerceAtLeast(0) % 20) * 46f
        val x = g.l + 40f + prev + (target - prev) * flop(slide)
        withTransform({ translate(x, g.t - 22f) }) { piece(g.bookmark, PaperInk.BLUE, PaperInk.REG_BLUE, lift = 0.5f) }
    }

    /** Parked: the right page turns over (1.2 s), shaded with halftone near its fold. */
    private fun DrawScope.pageFlip(g: PGeo, sim: PaperSim) {
        val p = sim.flip()
        if (p < 0f) return
        val w = (g.r - g.cx) * cos(p * PI.toFloat())
        val lift = sin(p * PI.toFloat())
        tmp.reset(); tmp.moveTo(g.cx, g.t + 12f); tmp.lineTo(g.cx + w, g.t + 4f - 24f * lift); tmp.lineTo(g.cx + w, g.b + 4f + 6f * lift); tmp.lineTo(g.cx, g.b); tmp.close()
        withTransform({ translate(sdx * lift, sdy * lift) }) { drawPath(tmp, shadow, blendMode = BlendMode.Multiply) }
        drawPath(tmp, if (w > 0f) PaperInk.PAGE else PaperInk.PAGE_EDGE)
        if (lift > 0.05f) drawPath(tmp, tone, alpha = lift, colorFilter = TONE_CREASE, blendMode = BlendMode.Multiply)
        drawPath(tmp, PaperInk.CREASE, style = hairline)
    }

    /**
     * Arrival: the book closes. The right half folds over onto the left (the mat shows where it lay), then its
     * blue cloth cover faces up with a pasted paper label and the cut-out title.
     */
    private fun DrawScope.cover(g: PGeo, sim: PaperSim) {
        val c = sim.closed
        if (c < 0.01f) return
        val n = sim.night
        // The right half lifts away: the cutting mat shows through where it lay.
        val gone = ((c - 0.15f) / 0.5f).coerceIn(0f, 1f)
        if (gone > 0f) {
            val top = g.t - 30f; val bot = g.b + 40f
            drawRect(lerp(PaperInk.MAT, PaperInk.MAT_DARK, n), Offset(g.cx, top), Size(g.r + 40f - g.cx, bot - top), alpha = gone)
            val line = lerp(PaperInk.MAT_LINE, PaperInk.MAT_DARK_LINE, n)
            var x = (floor(g.cx / 40f) + 1f) * 40f
            while (x < g.r + 40f) { drawLine(line, Offset(x, top), Offset(x, bot), 0.8f, alpha = gone); x += 40f }
            var y = (floor((top + g.oy) / 40f) + 1f) * 40f - g.oy
            while (y < bot) { drawLine(line, Offset(g.cx, y), Offset(g.r + 40f, y), 0.8f, alpha = gone); y += 40f }
        }
        val w = (g.r - g.cx) * cos(c * PI.toFloat())
        val edge = g.cx + w + (if (w < 0f) -16f else 16f)
        tmp.reset(); tmp.moveTo(g.cx, g.t - 12f); tmp.lineTo(edge, g.t - 12f); tmp.lineTo(edge, g.b + 16f); tmp.lineTo(g.cx, g.b + 16f); tmp.close()
        withTransform({ translate(sdx, sdy) }) { drawPath(tmp, shadow, blendMode = BlendMode.Multiply) }
        drawPath(tmp, if (c < 0.5f) PaperInk.PAGE else PaperInk.CLOTH)
        if (c > 0.5f) {
            val a = ((c - 0.5f) / 0.5f).coerceIn(0f, 1f)
            if (a > 0.3f) {
                // A pasted paper label on the cloth, with the title in cut-out capitals.
                val lx = g.l + 120f; val ly = 210f
                tmp2.reset(); tmp2.addRect(Rect(lx, ly, lx + 300f, ly + 130f))
                piece(tmp2, PaperInk.YELLOW, PaperInk.REG_YELLOW, lift = 0.6f)
                tmp2.reset(); tmp2.addRect(Rect(lx + 14f, ly + 100f, lx + 286f, ly + 112f))
                over(tmp2, pink, PaperInk.REG_PINK)
                title(lx + 36f, ly + 28f, sdx, PaperInk.BLUE, PaperInk.REG_BLUE, 3.2f, COVER_TITLE)
            }
        }
    }

    // ------------------------------------------------------------------ detail sheet

    /** A still of the pop-up mechanics (for snapshots): the car and crane, a V-fold standing up, the sun wheel and the rewards. */
    fun drawMechanicsSheet(s: DrawScope, f: ThemeFrame) = with(s) {
        val k = f.k
        val g = PGeo(s.size.width / k, s.size.height / k)
        light(0f)
        withTransform({ scale(k, k, Offset.Zero) }) {
            drawRect(PaperInk.MAT, size = Size(g.dw, g.dh))
            drawRect(PaperInk.PAGE, Offset(30f, 30f), Size(g.dw - 60f, g.dh - 60f))
            title(60f, 60f, 5f, PaperInk.BLUE, PaperInk.REG_BLUE, 2.4f, TITLE)
            // The car at three crane poses: rest, wings up (turning), head tucked (braking).
            withTransform({ translate(60f, 300f); scale(1.1f, 1.1f, Offset.Zero) }) { car(g, 0f, 0f, 0.4f, 0f, 0f, 0f, 0f, 0f) }
            withTransform({ translate(60f, 520f); scale(1.1f, 1.1f, Offset.Zero) }) { car(g, 0f, 0f, 1.1f, 6f, 1f, 0f, 0f, 1f) }
            withTransform({ translate(60f, 740f); scale(1.1f, 1.1f, Offset.Zero) }) { car(g, 0f, 0f, 0.2f, -14f, 0f, 1f, 0f, 0f) }
            // A tree plane standing up on its V-fold: lying, half up, overshooting, settled.
            for (i in 0 until 4) {
                val up = floatArrayOf(0.15f, 0.55f, 1.06f, 1f)[i]
                val bx = 480f + i * 92f
                drawLine(PaperInk.CREASE, Offset(bx - 44f, 500f), Offset(bx + 44f, 500f), 1.2f)
                withTransform({ scale(1f, up, Offset(0f, 500f)) }) { tree(bx, 500f, 100f) }
            }
            // The sun wheel, confetti and bunting flags.
            withTransform({ translate(700f, 200f); rotate(12f, Offset.Zero) }) {
                drawPath(g.sunTeeth, PaperInk.CARD); over(g.sunTeeth, pink, PaperInk.REG_PINK)
                drawPath(g.sunDisc, PaperInk.CARD); over(g.sunDisc, PaperInk.YELLOW, PaperInk.REG_YELLOW)
            }
            drawCircle(PaperInk.BRAD, 4f, Offset(700f, 200f))
            for (i in 0 until 3) withTransform({ translate(500f + i * 64f, 600f); scale(1.7f, 1.7f, Offset.Zero) }) {
                drawPath(g.dot, PaperInk.CARD); over(g.dot, CONFETTI[i], CONFETTI_REG[i]); over(g.ring, if (i == 1) PaperInk.YELLOW else PaperInk.BLUE, PaperInk.REG_BLUE, 0.85f)
            }
            for (i in 0 until 5) withTransform({ translate(470f + i * 48f, 680f); scale(1f, floatArrayOf(0.2f, 0.6f, 1.06f, 1f, 1f)[i], Offset.Zero) }) {
                piece(g.flag, CONFETTI[i % 3], CONFETTI_REG[i % 3], lift = 0.6f)
            }
            withTransform({ translate(730f, 676f) }) { piece(g.tab, Color.White, Offset.Zero, PaperInk.KRAFT, lift = 0.5f); over(g.tabArrow, pink, PaperInk.REG_PINK) }
            drawRect(grainBrush, Offset.Zero, Size(g.dw, g.dh), alpha = 0.9f)
        }
    }

    private companion object {
        const val TITLE = "POP-UP ROAD"
        const val TITLE_SCALE = 2.2f
        /** The smallest in-scene text allowed while moving (sp), and its fade time. */
        const val MIN_TEXT_SP = 24f
        const val FADE_S = 0.3f
        const val COVER_TITLE = "POP-UP"
        val TILT = floatArrayOf(-5f, 3f, -2f, 4f, -4f, 2f, -3f, 5f, -1f, 3f, -4f)
        val CONFETTI = arrayOf(PaperInk.PINK, PaperInk.YELLOW, PaperInk.BLUE)
        val CONFETTI_REG = arrayOf(PaperInk.REG_PINK, PaperInk.REG_YELLOW, PaperInk.REG_BLUE)
        val REG_INKS = arrayOf(PaperInk.PINK to PaperInk.REG_PINK, PaperInk.BLUE to PaperInk.REG_BLUE, PaperInk.YELLOW to PaperInk.REG_YELLOW)
        val TONE_PINK = ColorFilter.tint(PaperInk.FLUO, BlendMode.SrcIn)
        val TONE_RED = ColorFilter.tint(PaperInk.RED, BlendMode.SrcIn)
        val TONE_VIOLET = ColorFilter.tint(PaperInk.VIOLET, BlendMode.SrcIn)
        val TONE_BLUE = ColorFilter.tint(PaperInk.BLUE, BlendMode.SrcIn)
        val TONE_CREASE = ColorFilter.tint(Color(0xFFB9B1A0), BlendMode.SrcIn)
    }
}
