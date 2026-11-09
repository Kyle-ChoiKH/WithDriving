package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The macro studio around the watch: a black velvet pile texture (one small repeating tile), the soft key-light
 * lift, the contact shadow, a lens vignette and the catalogue caption. Everything is built once per size; the
 * draw loop only reads it.
 */
internal class CalibreStudio {
    private var tile: ImageBitmap? = null
    /** The velvet pile: fine fibres leaning one way, repeated over the whole canvas. */
    var pile: Brush? = null; private set

    private var lightR = -1f
    /** Key-light lift on the velvet, centred at the origin (draw translated to the watch). */
    var lift: Brush = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent)); private set
    /** The soft shadow under the floating watch, centred at the origin. */
    var shadow: Brush = lift; private set
    /** A warm and a cold version of the key light's colour on the scene (blended by the cabin temperature). */
    var keyWarm: Brush = lift; private set
    var keyCold: Brush = lift; private set
    /** A unit bloom (radius 1 at the origin) for a jewel catching the light. */
    val bloom: Brush = Brush.radialGradient(
        0f to Color(0xFFFFE9EC), 0.25f to Color(0xFFFF8FA3).copy(alpha = 0.55f), 1f to Color(0xFFFF4D6D).copy(alpha = 0f),
        center = Offset.Zero, radius = 1f,
    )
    /** A unit lume halo (radius 1 at the origin). */
    val lumeHalo: Brush = Brush.radialGradient(listOf(CalPal.lumeHalo.copy(alpha = 0.55f), CalPal.lumeHalo.copy(alpha = 0f)), Offset.Zero, 1f)

    /** The bevel sweep (combo, entrance) and the lume edge strokes. */
    var sweep = Stroke(1f); private set
    var sweepTrail = Stroke(1f); private set
    val lumeEdge = Stroke(width = 4f)

    private var vignetteKey = Long.MIN_VALUE
    var vignette: Brush = lift; private set

    fun release() { tile = null; pile = null; lightR = -1f; vignetteKey = Long.MIN_VALUE; parkedCaption.reset(); drivingCaption.reset() }

    fun ensure(w: Float, h: Float, r: Float, density: Density) {
        if (pile == null) {
            val t = velvetTile(density)
            tile = t
            pile = ShaderBrush(ImageShader(t, TileMode.Repeated, TileMode.Repeated))
        }
        val rq = (r / 2f).roundToInt() * 2f
        if (rq != lightR) {
            lightR = rq
            lift = Brush.radialGradient(0f to CalPal.velvetLift, 0.45f to CalPal.velvetLift.copy(alpha = 0.45f), 1f to Color.Transparent, center = Offset.Zero, radius = rq * 3.4f)
            shadow = Brush.radialGradient(0f to Color.Black.copy(alpha = 0.85f), 0.62f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent, center = Offset.Zero, radius = rq * 1.75f)
            sweep = Stroke(width = rq * 0.02f, cap = StrokeCap.Round)
            sweepTrail = Stroke(width = rq * 0.012f, cap = StrokeCap.Round)
            keyWarm = Brush.radialGradient(0f to CalPal.warmLight.copy(alpha = 0.10f), 0.5f to CalPal.warmLight.copy(alpha = 0.04f), 1f to Color.Transparent, center = Offset.Zero, radius = rq * 2.6f)
            keyCold = Brush.radialGradient(0f to CalPal.coldLight.copy(alpha = 0.10f), 0.5f to CalPal.coldLight.copy(alpha = 0.04f), 1f to Color.Transparent, center = Offset.Zero, radius = rq * 2.6f)
        }
        val vk = (w.roundToInt().toLong() shl 20) xor h.roundToInt().toLong()
        if (vk != vignetteKey) {
            vignetteKey = vk
            vignette = Brush.radialGradient(0.45f to Color.Transparent, 0.8f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.85f),
                center = Offset(w / 2f, h * 0.46f), radius = maxOf(w, h) * 0.62f)
        }
    }

    private fun velvetTile(density: Density): ImageBitmap {
        val n = TILE
        val img = ImageBitmap(n, n)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(n.toFloat(), n.toFloat())) {
            // Pile fibres: short strokes leaning about 60°, a few catching the light.
            for (i in 0 until 1400) {
                val x = hash01(i, 101) * n; val y = hash01(i, 102) * n
                val a = -1.05f + (hash01(i, 103) - 0.5f) * 0.6f
                val l = 1.2f + 3.2f * hash01(i, 104)
                val bright = hash01(i, 105)
                val col = if (bright > 0.93f) CalPal.fibre.copy(alpha = 0.30f) else CalPal.fibre.copy(alpha = 0.05f + 0.10f * bright)
                drawLine(col, Offset(x, y), Offset(x + cos(a) * l, y + sin(a) * l), strokeWidth = 0.7f)
            }
            // Dark gaps between the tufts.
            for (i in 0 until 500) drawCircle(Color.Black.copy(alpha = 0.45f), 0.6f + hash01(i, 107) * 0.8f, Offset(hash01(i, 108) * n, hash01(i, 109) * n))
        }
        return img
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
        const val TILE = 192
        val CAPTION = Color(0xFFC9C3B8)
        val CAPTION_DIM = Color(0xFF8D877E)
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
