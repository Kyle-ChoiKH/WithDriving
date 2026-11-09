package io.vroomi.app.ui.passage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import io.vroomi.character.calibre.SceneTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the entrance primitives over the live scene and its snapshot (masks, shading and transforms in the
 * app's UI layer; no painter changes). [content] draws the incoming scene in container coordinates and may be
 * called more than once. Paths and the Bayer masks are preallocated or cached.
 */
internal class EntranceRenderer {
    private val path = Path()
    private val bayerCache = HashMap<Long, ImageBitmap>()
    private val layerPaint = Paint()

    /** The incoming world through the mask (drawn inside a layer whose alpha is the entrance's [Entrances.inAlpha]). */
    fun drawMasked(
        s: DrawScope, theme: SceneTheme, p: Float, intensity: Float,
        pixelCell: Float, pixelOriginX: Float, pixelOriginY: Float,
        content: DrawScope.() -> Unit,
    ) = with(s) {
        val w = size.width; val h = size.height
        when (theme) {
            SceneTheme.KART -> {
                val off = Entrances.riseOffset(p, intensity) * h
                val sc = 1f + off / h
                translate(0f, off) { scale(sc, sc, Offset(w / 2f, h)) { content() } }
            }
            SceneTheme.PAPER -> {
                val deg = Entrances.foldDeg(p, intensity)
                val c = cos(deg * PI.toFloat() / 180f).coerceAtLeast(0.02f)
                val shade = 0.35f * sin(deg * PI.toFloat() / 180f)
                val spine = w / 2f
                clipRect(0f, 0f, spine, h) {
                    scale(c, 1f, Offset(spine, h / 2f)) { content() }
                    if (shade > 0.005f) drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = shade * 1.4f), Color.Black.copy(alpha = shade * 0.4f)), spine - c * spine, spine), Offset(spine - c * spine, 0f), Size(c * spine, h))
                }
                clipRect(spine, 0f, w, h) {
                    scale(c, 1f, Offset(spine, h / 2f)) { content() }
                    if (shade > 0.005f) drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = shade * 0.4f), Color.Black.copy(alpha = shade * 1.4f)), spine, spine + c * spine), Offset(spine, 0f), Size(c * spine, h))
                }
            }
            SceneTheme.CALIBRE -> content()
            SceneTheme.ENGINE, SceneTheme.TREASURE_MAP -> clipRect(0f, 0f, p * w, h) { content() }
            SceneTheme.MUSIC_BOX -> {
                val sy = Entrances.hingeScale(p, intensity)
                scale(1f, sy, Offset(w / 2f, 0f)) { content() }
            }
            SceneTheme.MARBLE -> {
                val sw = Entrances.sweep(p, w, h)
                path.reset()
                path.moveTo(0f, 0f)
                path.lineTo(sw.coerceAtMost(w), 0f)
                if (sw > w) path.lineTo(w, (sw - w).coerceAtMost(h))
                if (sw > h) path.lineTo((sw - h).coerceAtMost(w), h) else path.lineTo(0f, sw.coerceAtMost(h))
                if (sw > h) path.lineTo(0f, h)
                path.close()
                clipPath(path) { content() }
            }
            SceneTheme.AQUARIUM -> {
                val y = h * (1f - p)
                val amp = h * 0.012f * (1f - p) * intensity
                path.reset()
                path.moveTo(0f, h)
                var x = 0f
                val step = w / 24f
                while (x <= w + 0.5f) { path.lineTo(x, y + amp * sin(x / w * 2f * PI.toFloat() * 2f + p * 6f)); x += step }
                path.lineTo(w, h)
                path.close()
                clipPath(path) { content() }
            }
            SceneTheme.ORBIT -> {
                val (cx, cy) = Entrances.irisCenter(w, h)
                val r = Entrances.irisRadius(p, w, h)
                path.reset()
                path.addOval(Rect(Offset(cx * w, cy * h), r))
                clipPath(path) { content() }
            }
            SceneTheme.PIXEL -> {
                val k = (p * Entrances.PIXEL_STEPS).roundToInt().coerceIn(0, Entrances.PIXEL_STEPS)
                if (k >= Entrances.PIXEL_STEPS) { content(); return@with }
                if (k <= 0) return@with
                val cell = max(1, pixelCell.roundToInt())
                val mask = bayer(cell, k)
                drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), layerPaint) }
                content()
                translate(pixelOriginX, pixelOriginY) {
                    drawRect(ShaderBrush(ImageShader(mask, TileMode.Repeated, TileMode.Repeated)), Offset(-pixelOriginX, -pixelOriginY), size, blendMode = BlendMode.DstIn)
                }
                drawIntoCanvas { it.restore() }
            }
            else -> content()
        }
    }

    /** Effects that sit over both worlds (the section line, the rolled edge, the water line, the mist). */
    fun drawOver(s: DrawScope, theme: SceneTheme, p: Float, f: Float, intensity: Float, tint: Color, hairPx: Float, rollPx: Float, grain: Brush?) = with(s) {
        val w = size.width; val h = size.height
        val gd = Entrances.globalDim(theme, p, f, intensity)
        when (theme) {
            SceneTheme.CALIBRE -> {
                if (gd > 0.003f) drawRect(Color.Black, alpha = gd)
                // One soft specular band, tilted 20 degrees, crossing the bridges once.
                val b = Entrances.bandPos(p)
                if (b in 0.001f..0.999f) {
                    val a = Entrances.BAND_MAX_ALPHA * sin(PI.toFloat() * b)
                    val bw = w * 0.14f
                    val x = -bw + b * (w + 2 * bw)
                    rotate(20f, Offset(x, h / 2f)) {
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = a), Color.Transparent), x - bw / 2f, x + bw / 2f),
                            Offset(x - bw / 2f, -h), Size(bw, h * 3f),
                        )
                    }
                }
            }
            SceneTheme.PAPER -> if (gd > 0.003f) drawRect(Color.Black, alpha = gd)
            SceneTheme.MUSIC_BOX -> {
                // The lid's shade lifts off from the top edge down (soft edge of 25 % of the height).
                val base = Entrances.LID_SHADE * intensity * Entrances.smoothstep(0f, 0.22f, f)
                if (base > 0.003f) {
                    val front = Entrances.lidFront(p) * 1.25f
                    val top = (front - 0.25f) * h
                    val bottom = front * h
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = base), startY = top, endY = bottom), Offset(0f, top.coerceAtLeast(0f)), Size(w, (bottom - top.coerceAtLeast(0f)).coerceAtLeast(0f)))
                    if (bottom < h) drawRect(Color.Black.copy(alpha = base), Offset(0f, bottom), Size(w, h - bottom))
                }
            }
            else -> Unit
        }
        when (theme) {
            SceneTheme.ENGINE -> if (p in 0.001f..0.999f) drawRect(tint, Offset(p * w - hairPx / 2f, 0f), Size(hairPx, h))
            SceneTheme.TREASURE_MAP -> if (p in 0.001f..0.999f) {
                val x = p * w
                // The rolled edge: a small cylinder of parchment with its own light and a shadow on the old world.
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color(0x33000000), Color(0x22FFF4DC), Color(0x14FFF4DC), Color(0x44000000), Color.Transparent),
                        x, x + rollPx,
                    ),
                    Offset(x, 0f), Size(rollPx, h),
                )
            }
            SceneTheme.AQUARIUM -> if (p in 0.001f..0.999f) {
                val y = h * (1f - p)
                val amp = h * 0.012f * (1f - p) * intensity
                path.reset()
                var x = 0f
                val step = w / 24f
                path.moveTo(0f, y + amp * sin(p * 6f))
                while (x <= w + 0.5f) { path.lineTo(x, y + amp * sin(x / w * 2f * PI.toFloat() * 2f + p * 6f)); x += step }
                drawPath(path, Color.White.copy(alpha = 0.28f), style = androidx.compose.ui.graphics.drawscope.Stroke(hairPx * 2f))
            }
            SceneTheme.MARBLE -> if (p in 0.001f..0.999f) {
                val sw = Entrances.sweep(p, w, h)
                drawLine(tint.copy(alpha = 0.5f), Offset(sw, 0f), Offset(sw - h, h), hairPx)
            }
            SceneTheme.TERRARIUM -> {
                val up = Entrances.mistUp(f, intensity)
                if (up > 0.003f) {
                    val front = Entrances.mistFront(p) * (1f + Entrances.MIST_EDGE)
                    val mist = Color(Entrances.MIST_ARGB)
                    val top = ((front - Entrances.MIST_EDGE) * h).coerceIn(-h, 2 * h)
                    val bottom = (front * h).coerceAtLeast(top + 1f)
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to mist.copy(alpha = up), startY = top, endY = bottom), Offset(0f, top.coerceAtLeast(0f)), Size(w, (bottom - top.coerceAtLeast(0f)).coerceAtLeast(0f)))
                    if (bottom < h) {
                        drawRect(mist.copy(alpha = up), Offset(0f, bottom), Size(w, h - bottom))
                        if (grain != null) drawRect(grain, Offset(0f, bottom), Size(w, h - bottom), alpha = up * 0.25f)
                    }
                }
            }
            else -> Unit
        }
    }

    /** A 4 x 4 ordered-dither mask: cell (i, j) is open once step k passes its Bayer threshold. */
    private fun bayer(cell: Int, k: Int): ImageBitmap {
        val key = cell.toLong() * 64 + k
        return bayerCache.getOrPut(key) {
            if (bayerCache.size > 40) bayerCache.clear()
            val img = ImageBitmap(cell * 4, cell * 4)
            val c = Canvas(img)
            val paint = Paint().apply { color = Color.White }
            for (j in 0 until 4) for (i in 0 until 4) if (BAYER[j * 4 + i] < k) {
                c.drawRect(i * cell.toFloat(), j * cell.toFloat(), (i + 1) * cell.toFloat(), (j + 1) * cell.toFloat(), paint)
            }
            img
        }
    }

    companion object {
        /** The 4 x 4 Bayer matrix (thresholds 0..15). */
        val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    }
}
