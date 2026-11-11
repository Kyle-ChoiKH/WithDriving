package io.vroomi.character.render.musicbox

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import io.vroomi.character.render.hash01

/**
 * The room and its materials: a deep warm-brown room under one lamp (never black), walnut and lacquered mahogany,
 * maple and ebony for inlay, burgundy velvet, brass for the movement's bed and cylinder, polished steel for the
 * comb and the pins.
 */
internal object Room {
    val wallTop = Color(0xFF2A1E17)
    val wallMid = Color(0xFF3D2C20)
    val table = Color(0xFF55392A)
    val tableDeep = Color(0xFF33231A)
    val lamp = Color(0xFFFFD2A0)

    val walnut = Color(0xFF7C5638)
    val walnutLight = Color(0xFFA2784E)
    val walnutShade = Color(0xFF4F3522)
    val mahogany = Color(0xFF70341F)
    val mahoganyLight = Color(0xFF9A4B2B)
    val mahoganyShade = Color(0xFF431E13)
    val grain = Color(0xFF2B190F)
    val maple = Color(0xFFE3C99C)
    val ebony = Color(0xFF2A1C15)
    val ivory = Color(0xFFF0E5CF)
    val velvet = Color(0xFF6F1F2F)
    val velvetLight = Color(0xFF943346)
    val velvetShade = Color(0xFF46121D)

    val brass = Color(0xFFC49B50)
    val brassLight = Color(0xFFF1D892)
    val brassMid = Color(0xFFA67F3A)
    val brassDark = Color(0xFF6D5021)
    val gold = Color(0xFFE8C76E)
    val silver = Color(0xFFCDD2D8)
    val silverLight = Color(0xFFF7F8FA)
    val silverMid = Color(0xFFB4BAC2)
    val silverDark = Color(0xFF7E858E)
    val blued = Color(0xFF5A6C8C)
    val graphite = Color(0xFF4B5058)
    val lead = Color(0xFF8D9299)
    val pearl = Color(0xFFFFF4E0)
    val ink = Color(0xFF2C1D12)
    val shadow = Color(0xFF120B07)
}

/** The wood of one upgrade step: matte walnut for the first box, lacquered mahogany from the second. */
internal class Wood(val base: Color, val light: Color, val shade: Color, val lacquer: Boolean) {
    companion object {
        val WALNUT = Wood(Room.walnut, Room.walnutLight, Room.walnutShade, false)
        val MAHOGANY = Wood(Room.mahogany, Room.mahoganyLight, Room.mahoganyShade, true)
    }
}

/**
 * A wooden panel: the tone lit from above, a low-contrast straight grain made in code (long, slightly wandering
 * lines of two weights), and, when lacquered, one soft reflection band of the lamp.
 */
internal fun DrawScope.woodPanel(p: Path, wood: Wood, seed: Int, vertical: Boolean = false) {
    val b = p.getBounds()
    drawPath(p, Brush.verticalGradient(0f to wood.light, 0.3f to wood.base, 1f to wood.shade, startY = b.top, endY = b.bottom))
    clipPath(p) {
        val n = ((if (vertical) b.width else b.height) / 3.2f).toInt().coerceIn(6, 90)
        for (i in 0 until n) {
            val u = (i + hash01(i, seed)) / n
            val a = 0.05f + 0.10f * hash01(i, seed + 1)
            val w = if (hash01(i, seed + 2) > 0.8f) 1.4f else 0.6f
            val drift = (hash01(i, seed + 3) - 0.5f) * 7f
            if (vertical) {
                val x = b.left + u * b.width
                drawLine(Room.grain.copy(alpha = a), Offset(x, b.top), Offset(x + drift, b.bottom), w)
            } else {
                val y = b.top + u * b.height
                drawLine(Room.grain.copy(alpha = a), Offset(b.left, y), Offset(b.right, y + drift), w)
            }
        }
        if (wood.lacquer) {
            // The lamp in the lacquer: one broad soft band and a thin bright line under the upper edge.
            drawRect(Brush.linearGradient(0f to Color.White.copy(alpha = 0f), 0.4f to Color.White.copy(alpha = 0.20f), 0.62f to Color.White.copy(alpha = 0.05f), 1f to Color.White.copy(alpha = 0f),
                start = Offset(b.left + b.width * 0.08f, b.top), end = Offset(b.left + b.width * 0.5f, b.bottom)), Offset(b.left, b.top), Size(b.width, b.height))
            drawRect(Color.White.copy(alpha = 0.22f), Offset(b.left + b.width * 0.06f, b.top + minOf(5f, b.height * 0.1f)), Size(b.width * 0.88f, 1.4f))
        }
    }
}

/** A string of inlay along a straight line: alternating maple and ebony lozenges between two hairlines of maple. */
internal fun DrawScope.inlayString(x0: Float, x1: Float, y: Float, h: Float, pitch: Float = 16f) {
    drawRect(Room.ebony, Offset(x0, y - h / 2f), Size(x1 - x0, h))
    drawLine(Room.maple, Offset(x0, y - h / 2f), Offset(x1, y - h / 2f), 0.9f)
    drawLine(Room.maple, Offset(x0, y + h / 2f), Offset(x1, y + h / 2f), 0.9f)
    val n = ((x1 - x0) / pitch).toInt().coerceAtLeast(1)
    val step = (x1 - x0) / n
    val lozenge = Path()
    for (i in 0 until n) {
        val cx = x0 + (i + 0.5f) * step
        lozenge.reset()
        lozenge.moveTo(cx - step * 0.42f, y); lozenge.lineTo(cx, y - h * 0.38f); lozenge.lineTo(cx + step * 0.42f, y); lozenge.lineTo(cx, y + h * 0.38f); lozenge.close()
        drawPath(lozenge, if (i % 2 == 0) Room.maple else Room.walnutLight)
    }
}

/** A brass corner mount: an L of sheet brass with two pins. [sx], [sy] point from the corner into the panel. */
internal fun DrawScope.cornerMount(x: Float, y: Float, sx: Float, sy: Float, arm: Float = 46f, w: Float = 13f) {
    val p = Path().apply {
        moveTo(x, y); lineTo(x + sx * arm, y); lineTo(x + sx * arm, y + sy * w); lineTo(x + sx * w, y + sy * w)
        lineTo(x + sx * w, y + sy * arm); lineTo(x, y + sy * arm); close()
    }
    val b = p.getBounds()
    drawPath(p, Brush.linearGradient(0f to Room.brassLight, 0.5f to Room.brass, 1f to Room.brassMid, start = Offset(b.left, b.top), end = Offset(b.right, b.bottom)))
    drawPath(p, Room.brassDark.copy(alpha = 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(0.9f))
    drawCircle(Room.brassDark, 1.8f, Offset(x + sx * (arm - 8f), y + sy * w / 2f))
    drawCircle(Room.brassDark, 1.8f, Offset(x + sx * w / 2f, y + sy * (arm - 8f)))
}

/** A small polished dome (a screw head or a pivot): soft top light. */
internal fun DrawScope.dome(x: Float, y: Float, r: Float, light: Color = Color.White, mid: Color = Room.silver, dark: Color = Room.silverMid) {
    drawCircle(Room.shadow.copy(alpha = 0.3f), r, Offset(x + 0.6f, y + 1.2f))
    drawCircle(Brush.radialGradient(listOf(light, mid, dark), Offset(x - r * 0.3f, y - r * 0.4f), r * 1.4f), r, Offset(x, y))
}

/** A rounded plate with a soft diagonal light (brass or ivory). */
internal fun DrawScope.plate(l: Float, t: Float, w: Float, h: Float, rad: Float, light: Color, mid: Color, dark: Color) {
    drawRoundRect(Room.shadow.copy(alpha = 0.35f), Offset(l + 0.8f, t + 1.6f), Size(w, h), CornerRadius(rad))
    drawRoundRect(Brush.linearGradient(0f to light, 0.45f to mid, 1f to dark, start = Offset(l, t), end = Offset(l + w, t + h)), Offset(l, t), Size(w, h), CornerRadius(rad))
}
