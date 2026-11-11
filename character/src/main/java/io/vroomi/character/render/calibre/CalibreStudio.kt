package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import io.vroomi.character.render.hash01
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** A small cached picture drawn at [at] (a prop on the bench). */
internal class Sprite(val bmp: ImageBitmap, val at: Offset)

/**
 * The watchmaker's bench around the watch: a bright matte surface (ivory to warm grey) under one soft light from
 * above, a low-contrast grain, a gentle fall-off at the edges and two out-of-focus props (a loupe and tweezers).
 * Everything is built once per canvas size into bitmaps; the draw loop only reads it. Also the night lamp, the
 * light brushes and the catalogue caption.
 */
internal class CalibreStudio {
    /** The bench (gradient, light, grain, edge fall-off), the size of the canvas. */
    var bench: ImageBitmap? = null; private set
    /** The props, laid out around the watch's full-size place; they fade when the watch moves to the corner. */
    var props: List<Sprite> = emptyList(); private set
    private var benchKey = Long.MIN_VALUE

    private var lightR = -1f
    /** A warm and a cold version of the key light's colour on the scene (blended by the cabin temperature). */
    var keyWarm: Brush = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent)); private set
    var keyCold: Brush = keyWarm; private set
    /** Night: the warm desk lamp, multiplied over the day scene (its pool on the watch, darker toward the edges). */
    var lamp: Brush = keyWarm; private set
    /** A unit bloom (radius 1 at the origin) for a jewel catching the light. */
    val bloom: Brush = Brush.radialGradient(
        0f to Color(0xFFFFE9EC), 0.25f to Color(0xFFFF8FA3).copy(alpha = 0.55f), 1f to Color(0xFFFF4D6D).copy(alpha = 0f),
        center = Offset.Zero, radius = 1f,
    )
    /** A unit lume halo (radius 1 at the origin). */
    val lumeHalo: Brush = Brush.radialGradient(listOf(CalPal.lumeHalo.copy(alpha = 0.45f), CalPal.lumeHalo.copy(alpha = 0f)), Offset.Zero, 1f)

    /** The bevel sweep (combo, entrance) and the lume edge strokes. */
    var sweep = Stroke(1f); private set
    var sweepTrail = Stroke(1f); private set
    val lumeEdge = Stroke(width = 3f)

    fun release() { bench = null; props = emptyList(); benchKey = Long.MIN_VALUE; lightR = -1f; parkedCaption.reset(); drivingCaption.reset() }

    /**
     * Builds what the canvas [w] x [h] needs. [r] is the watch's movement radius now; ([x0], [y0]) and [r0] are its
     * full-size place and radius, which lay out the bench, the props and the lamp.
     */
    fun ensure(w: Float, h: Float, r: Float, x0: Float, y0: Float, r0: Float, density: Density) {
        val key = (w.roundToInt().toLong() shl 40) xor (h.roundToInt().toLong() shl 20) xor (r0 / 2f).roundToInt().toLong()
        if (key != benchKey || bench == null) {
            benchKey = key
            bench = buildBench(w.roundToInt().coerceAtLeast(2), h.roundToInt().coerceAtLeast(2), x0, y0, r0, density)
            props = buildProps(w, h, x0, y0, r0, density)
            lamp = Brush.radialGradient(
                0f to CalPal.lampCentre, 0.30f to CalPal.lampCentre, 0.62f to CalPal.lampMid, 1f to CalPal.lampEdge,
                center = Offset(x0 - r0 * 0.25f, y0 - r0 * 0.35f), radius = r0 * 4.2f,
            )
        }
        val rq = (r / 2f).roundToInt() * 2f
        if (rq != lightR) {
            lightR = rq
            sweep = Stroke(width = rq * 0.02f, cap = StrokeCap.Round)
            sweepTrail = Stroke(width = rq * 0.012f, cap = StrokeCap.Round)
            keyWarm = Brush.radialGradient(0f to CalPal.warmLight.copy(alpha = 0.08f), 0.5f to CalPal.warmLight.copy(alpha = 0.03f), 1f to Color.Transparent, center = Offset.Zero, radius = rq * 2.6f)
            keyCold = Brush.radialGradient(0f to CalPal.coldLight.copy(alpha = 0.08f), 0.5f to CalPal.coldLight.copy(alpha = 0.03f), 1f to Color.Transparent, center = Offset.Zero, radius = rq * 2.6f)
        }
    }

    private fun render(w: Int, h: Int, density: Density, block: DrawScope.() -> Unit): ImageBitmap {
        val img = ImageBitmap(w.coerceAtLeast(2), h.coerceAtLeast(2))
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat()), block)
        return img
    }

    private fun buildBench(w: Int, h: Int, x0: Float, y0: Float, r0: Float, density: Density): ImageBitmap = render(w, h, density) {
        // The matte bench: ivory at the back, warm grey toward the front edge.
        drawRect(Brush.verticalGradient(0f to CalPal.benchTop, 0.55f to CalPal.benchMid, 1f to CalPal.benchBottom))
        // One large soft light from above, a little toward the key light (upper left).
        drawRect(Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.42f), 0.45f to Color.White.copy(alpha = 0.14f), 1f to Color.Transparent,
            center = Offset(x0 - r0 * 0.9f, y0 - r0 * 2.0f), radius = r0 * 5.2f,
        ))
        // Low-contrast grain of the bench mat (a small repeating tile).
        drawRect(ShaderBrush(ImageShader(grainTile(density), TileMode.Repeated, TileMode.Repeated)))
        // A gentle fall-off at the edges.
        drawRect(Brush.radialGradient(
            0.50f to Color.Transparent, 0.82f to CalPal.benchEdge.copy(alpha = 0.12f), 1f to CalPal.benchEdge.copy(alpha = 0.26f),
            center = Offset(w / 2f, y0 + r0 * 0.1f), radius = maxOf(w.toFloat(), h.toFloat()) * 0.62f,
        ))
    }

    private fun grainTile(density: Density): ImageBitmap {
        val n = TILE
        return render(n, n, density) {
            for (i in 0 until 900) {
                val x = hash01(i, 101) * n; val y = hash01(i, 102) * n
                val r = 0.5f + 0.7f * hash01(i, 103)
                val c = if (hash01(i, 104) > 0.5f) Color.White.copy(alpha = 0.05f) else CalPal.benchShadow.copy(alpha = 0.035f)
                // Wrap at the tile edge so the repeat is seamless.
                for (dx in intArrayOf(0, -n, n)) for (dy in intArrayOf(0, -n, n)) {
                    if (dx != 0 && x > 2f && x < n - 2f) continue
                    if (dy != 0 && y > 2f && y < n - 2f) continue
                    drawCircle(c, r, Offset(x + dx, y + dy))
                }
            }
        }
    }

    // ---------------------------------------------------------------- props (soft, out of focus, low contrast)

    private fun buildProps(w: Float, h: Float, x0: Float, y0: Float, r0: Float, density: Density): List<Sprite> {
        val out = ArrayList<Sprite>(2)
        // A loupe lying on its side, upper right of the watch.
        loupe(Offset(x0 + r0 * 2.95f, y0 - r0 * 0.78f), r0, density)?.let { if (it.inside(w, h)) out += it }
        // Tweezers, lower left, their tips toward the watch.
        tweezers(Offset(x0 - r0 * 4.15f, y0 + r0 * 1.22f), Offset(x0 - r0 * 2.25f, y0 + r0 * 0.40f), r0, density)?.let { if (it.inside(w, h)) out += it }
        return out
    }

    private fun Sprite.inside(w: Float, h: Float) = at.x < w && at.y < h && at.x + bmp.width > 0 && at.y + bmp.height > 0

    /**
     * The loupe: a short satin aluminium tube lying on its side (the lens toward the watch, the wider eyecup away),
     * seen from above and far out of focus. A tube lit from above: a bright streak along its axis; the lens end shows
     * its glass as a pale, cool face with one reflection.
     */
    private fun loupe(c: Offset, r0: Float, density: Density): Sprite? {
        val len = r0 * 0.92f; val big = r0 * 0.33f; val small = r0 * 0.25f
        val blur = r0 * 0.05f
        val pad = r0 * 0.42f
        val half = len / 2f + pad
        val n = ceil(half * 2f).toInt()
        if (n < 8) return null
        val ang = 160f * PI.toFloat() / 180f
        val o = Offset(half, half)
        val ux = cos(ang); val uy = sin(ang); val vx = -uy; val vy = ux
        fun p(a: Float, b: Float) = Offset(o.x + ux * a + vx * b, o.y + uy * a + vy * b)
        fun ellipse(cx: Float, rw: Float, rh: Float) = Path().apply {
            for (i in 0..48) {
                val t = i / 48f * 2f * PI.toFloat()
                val q = p(cx + cos(t) * rw, sin(t) * rh)
                if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
            }
            close()
        }
        // Lens end at +len/2 (toward the watch), eyecup end at -len/2.
        val body = union(Path().apply {
            val a = p(len / 2f, -small); moveTo(a.x, a.y)
            val b = p(-len / 2f, -big); lineTo(b.x, b.y)
            val d = p(-len / 2f, big); lineTo(d.x, d.y)
            val e = p(len / 2f, small); lineTo(e.x, e.y)
            close()
        }, ellipse(-len / 2f, big * 0.30f, big), ellipse(len / 2f, small * 0.34f, small))
        val glass = ellipse(len / 2f, small * 0.30f, small * 0.90f)
        val rim = ellipse(-len / 2f, big * 0.26f, big * 0.92f)
        val bmp = render(n, n, density) {
            // Its soft shadow on the bench (down and right, away from the light).
            blurred(body, CalPal.benchShadow.copy(alpha = 0.20f), r0 * 0.10f, r0 * 0.06f, r0 * 0.09f)
            // The tube, lit from above: one bright streak along its axis.
            blurredBrush(body, Brush.linearGradient(
                0f to CalPal.propDark, 0.32f to Color(0xFFDAD6CF), 0.55f to CalPal.propMetal, 1f to CalPal.propDark,
                start = p(0f, -big), end = p(0f, big),
            ), blur)
            // The eyecup's rolled rim (a slightly darker band), and the lens: pale cool glass with one reflection.
            blurred(rim, CalPal.propDark.copy(alpha = 0.45f), blur)
            blurred(glass, Color(0xFFC8D1D6), blur * 0.8f)
            blurred(ellipse(len / 2f + small * 0.05f, small * 0.10f, small * 0.42f).also { it.translate(Offset(vx, vy) * (-small * 0.30f)) }, Color.White.copy(alpha = 0.6f), blur)
        }
        return Sprite(bmp, Offset(c.x - half, c.y - half))
    }

    /** Steel tweezers: two slender blades joined at the back, the tips slightly apart; defocused. */
    private fun tweezers(rear: Offset, tip: Offset, r0: Float, density: Density): Sprite? {
        val pad = r0 * 0.30f
        val minX = minOf(rear.x, tip.x) - pad; val minY = minOf(rear.y, tip.y) - pad
        val wpx = ceil(maxOf(rear.x, tip.x) - minX + pad).toInt(); val hpx = ceil(maxOf(rear.y, tip.y) - minY + pad).toInt()
        if (wpx < 8 || hpx < 8) return null
        val a0 = Offset(rear.x - minX, rear.y - minY); val t0 = Offset(tip.x - minX, tip.y - minY)
        val len = hypot(t0.x - a0.x, t0.y - a0.y)
        val ang = atan2(t0.y - a0.y, t0.x - a0.x)
        val ux = cos(ang); val uy = sin(ang); val vx = -uy; val vy = ux
        fun p(a: Float, b: Float) = Offset(a0.x + ux * a + vx * b, a0.y + uy * a + vy * b)
        fun blade(side: Float): Path = Path().apply {
            val w0 = r0 * 0.050f; val w1 = r0 * 0.012f
            val open = r0 * 0.045f
            val a = p(0f, side * 0.004f * r0); moveTo(a.x, a.y)
            val b = p(len * 0.35f, side * (w0 * 0.2f + open * 0.15f)); lineTo(b.x, b.y)
            val c = p(len, side * open); lineTo(c.x, c.y)
            val d = p(len, side * (open + w1)); lineTo(d.x, d.y)
            val e = p(len * 0.35f, side * (w0 * 1.6f + open * 0.15f)); lineTo(e.x, e.y)
            val f = p(0f, side * w0); lineTo(f.x, f.y)
            close()
        }
        val l = blade(-1f); val r = blade(1f)
        val bmp = render(wpx, hpx, density) {
            blurred(union(l, r), CalPal.benchShadow.copy(alpha = 0.20f), r0 * 0.05f, r0 * 0.035f, r0 * 0.05f)
            for (b in listOf(l, r)) blurredBrush(b, Brush.linearGradient(
                0f to CalPal.propDark, 0.5f to Color(0xFFD4D0C9), 1f to CalPal.propMetal, start = p(0f, -r0 * 0.1f), end = p(0f, r0 * 0.1f),
            ), r0 * 0.022f)
        }
        return Sprite(bmp, Offset(minX, minY))
    }

    // ---------------------------------------------------------------- the catalogue caption

    /** Parked: the full catalogue line in fine engraved type, with the maker's mark in small capitals. */
    val parkedCaption = CaptionSet("p", withMark = true)
    /** Driving: the two values only, every piece at least the driving text minimum (24 sp). */
    val drivingCaption = CaptionSet("d", withMark = false)

    /** Builds both caption sets for canvas height [h]; [minPx] is 24 sp in px for this screen. */
    fun caption(h: Float, minPx: Float, t: CalibreText) {
        val fine = (h * 0.026f).roundToInt().toFloat()
        parkedCaption.ensure(fine, fine * 0.92f, t)
        val big = maxOf(fine, kotlin.math.ceil(minPx * 1.02f))
        drivingCaption.ensure(big, big, t)
    }

    companion object {
        const val TILE = 128
        /** Caption text: a dark warm grey on the bright bench (well over 4.5 : 1), labels a step lighter. */
        val CAPTION = Color(0xFF2F2C29)
        val CAPTION_DIM = Color(0xFF524D47)
    }
}

/**
 * One size of the catalogue caption: the cached pieces (mark, separator, labels, values by index), so the draw
 * loop never builds a string. [labelPx] may be smaller than [px] only in the parked set.
 */
internal class CaptionSet(private val id: String, val withMark: Boolean) {
    var px = -1f; private set
    private var labelPx = -1f
    private var text: CalibreText? = null
    var mark: TextLayoutResult? = null; private set
    var sep: TextLayoutResult? = null; private set
    var reserveLabel: TextLayoutResult? = null; private set
    var chronoLabel: TextLayoutResult? = null; private set
    var chargeLabel: TextLayoutResult? = null; private set
    private val hours = arrayOfNulls<TextLayoutResult>(73)
    private val chrono = arrayOfNulls<TextLayoutResult>(1800)
    private val pct = arrayOfNulls<TextLayoutResult>(101)

    fun reset() { px = -1f; text = null }

    fun ensure(p: Float, lp: Float, t: CalibreText) {
        if (p == px && lp == labelPx && text === t) return
        px = p; labelPx = lp; text = t
        hours.fill(null); chrono.fill(null); pct.fill(null)
        val k = "cal-cap-$id|$p|$lp"
        mark = if (withMark) t.styled("$k|mark", smallCaps("CALIBRE VR-01"), p, CalibreStudio.CAPTION, FontFamily.Serif, FontWeight.Normal, 0.26f) else null
        sep = t.styled("$k|sep", "·", p, CalibreStudio.CAPTION_DIM, FontFamily.Serif, FontWeight.Normal, 0f)
        reserveLabel = t.styled("$k|res", "파워 리저브", lp, CalibreStudio.CAPTION_DIM, FontFamily.Serif, FontWeight.Normal, 0.12f)
        chronoLabel = t.styled("$k|chr", "크로노그래프", lp, CalibreStudio.CAPTION_DIM, FontFamily.Serif, FontWeight.Normal, 0.12f)
        chargeLabel = t.styled("$k|chg", "충전", lp, CalibreStudio.CAPTION_DIM, FontFamily.Serif, FontWeight.Normal, 0.12f)
    }

    fun hours(v: Int): TextLayoutResult {
        val i = v.coerceIn(0, 72)
        return hours[i] ?: text!!.styled("cal-cap-$id|$px|h$i", "${i}시간", px, CalibreStudio.CAPTION, FontFamily.Serif, FontWeight.Normal, 0.12f).also { hours[i] = it }
    }

    fun chrono(seconds: Long): TextLayoutResult {
        val i = (seconds % 1800L).toInt().coerceAtLeast(0)
        return chrono[i] ?: text!!.styled("cal-cap-$id|$px|c$i", "%d:%02d".format(i / 60, i % 60), px, CalibreStudio.CAPTION, FontFamily.Serif, FontWeight.Normal, 0.12f).also { chrono[i] = it }
    }

    fun percent(v: Int): TextLayoutResult {
        val i = v.coerceIn(0, 100)
        return pct[i] ?: text!!.styled("cal-cap-$id|$px|p$i", "$i%", px, CalibreStudio.CAPTION, FontFamily.Serif, FontWeight.Normal, 0.12f).also { pct[i] = it }
    }

    private fun smallCaps(s: String): AnnotatedString = buildAnnotatedString {
        var start = true
        for (ch in s) {
            if (ch == ' ' || ch == '-') { append(ch); start = true; continue }
            if (start || !ch.isLetter()) append(ch) else withStyle(SpanStyle(fontSize = 0.78f.em)) { append(ch) }
            start = false
        }
    }
}
