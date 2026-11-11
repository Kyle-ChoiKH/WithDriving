package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * The watch's strap, one per upgrade step, so a step changes the whole piece and not only its movement: 1 tan
 * leather, 2 black leather with cream stitching, 3 navy sailcloth, 4 a steel bracelet, 5 green embossed leather
 * with gold stitching. It lies under the case (drawn first), running off the bench picture at both ends.
 *
 * Drawn in the watch's own coordinates: the centre is ([half], [half]) and the case's radius is [u].
 */
internal fun DrawScope.drawStrap(half: Float, u: Float, model: Int) {
    val c = Offset(half, half)
    for (dir in floatArrayOf(-1f, 1f)) {
        val y0 = c.y + dir * 0.68f * u; val y1 = c.y + dir * 4.2f * u
        val w0 = 0.60f * u; val w1 = 0.52f * u
        val band = Path().apply { moveTo(c.x - w0, y0); lineTo(c.x + w0, y0); lineTo(c.x + w1, y1); lineTo(c.x - w1, y1); close() }
        // Its soft shadow on the bench.
        val shadow = Path().apply { addPath(band, Offset(0.05f * u, 0.07f * u)) }
        drawPath(shadow, Color.Black.copy(alpha = 0.16f))
        val top = minOf(y0, y1); val hgt = kotlin.math.abs(y1 - y0)
        when (model.coerceIn(0, 4)) {
            3 -> clipPath(band) {
                // A steel bracelet: rows of three links, the centre one polished.
                drawRect(Brush.horizontalGradient(listOf(Color(0xFF7E858D), Color(0xFFD5D9DE), Color(0xFFF2F4F6), Color(0xFFB4BAC1), Color(0xFF6F767E)), startX = c.x - w0, endX = c.x + w0), Offset(c.x - w0, top), Size(w0 * 2, hgt))
                val row = 0.26f * u
                var y = top
                while (y < top + hgt) {
                    drawLine(Color(0xFF3F454C), Offset(c.x - w0, y), Offset(c.x + w0, y), 0.022f * u)
                    drawLine(Color.White.copy(alpha = 0.55f), Offset(c.x - w0, y + 0.02f * u), Offset(c.x + w0, y + 0.02f * u), 0.008f * u)
                    y += row
                }
                for (s in floatArrayOf(-1f, 1f)) drawLine(Color(0xFF4A5057), Offset(c.x + s * 0.24f * u, top), Offset(c.x + s * 0.24f * u, top + hgt), 0.018f * u)
                drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startX = c.x - 0.24f * u, endX = c.x + 0.24f * u), Offset(c.x - 0.24f * u, top), Size(0.48f * u, hgt))
            }
            else -> {
                val base = when (model) { 0 -> Color(0xFF8A5A36); 1 -> Color(0xFF202024); 2 -> Color(0xFF263F66); else -> Color(0xFF1F4A38) }
                val stitch = when (model) { 0 -> Color(0xFFEAD9B6); 1 -> Color(0xFFEDE6D6); 2 -> Color(0xFFDCE6F2); else -> Color(0xFFD8B25A) }
                clipPath(band) {
                    drawRect(base, Offset(c.x - w0, top), Size(w0 * 2, hgt))
                    when (model) {
                        2 -> { // sailcloth: a fine weave
                            var y = top
                            while (y < top + hgt) { drawLine(Color.White.copy(alpha = 0.07f), Offset(c.x - w0, y), Offset(c.x + w0, y), 0.012f * u); y += 0.05f * u }
                            var x = c.x - w0
                            while (x < c.x + w0) { drawLine(Color.Black.copy(alpha = 0.10f), Offset(x, top), Offset(x, top + hgt), 0.010f * u); x += 0.05f * u }
                        }
                        4 -> { // embossed scales: rows of rounded tiles, offset like brickwork
                            val th = 0.20f * u; val tw = 0.30f * u
                            var row = 0; var y = top
                            while (y < top + hgt) {
                                var x = c.x - w0 - (if (row % 2 == 0) 0f else tw / 2)
                                while (x < c.x + w0) {
                                    drawRoundRect(Color.Black.copy(alpha = 0.26f), Offset(x, y), Size(tw, th), CornerRadius(0.05f * u), style = Stroke(0.018f * u))
                                    drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(x + 0.03f * u, y + 0.03f * u), Size(tw - 0.06f * u, th * 0.4f), CornerRadius(0.04f * u))
                                    x += tw
                                }
                                y += th; row++
                            }
                        }
                        else -> { // smooth leather: a few faint creases across
                            var y = top + 0.3f * u
                            while (y < top + hgt) { drawLine(Color.Black.copy(alpha = 0.10f), Offset(c.x - w0, y), Offset(c.x + w0, y + 0.03f * u), 0.014f * u); y += 0.42f * u }
                        }
                    }
                    // The one light from the upper left: a lit left flank, a shaded right edge.
                    drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.16f), 0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.34f), startX = c.x - w0, endX = c.x + w0), Offset(c.x - w0, top), Size(w0 * 2, hgt))
                }
                // Stitching along both edges.
                val dash = Stroke(0.022f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.07f * u, 0.06f * u), 0f))
                for (s in floatArrayOf(-1f, 1f)) drawLine(stitch.copy(alpha = 0.9f), Offset(c.x + s * (w0 - 0.07f * u), y0), Offset(c.x + s * (w1 - 0.07f * u), y1), dash.width, cap = StrokeCap.Round, pathEffect = dash.pathEffect)
            }
        }
        drawPath(band, Color.Black.copy(alpha = 0.45f), style = Stroke(0.012f * u))
    }
}
