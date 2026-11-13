package io.vroomi.character.render.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin

/**
 * Small eighth notes that rise from ([x], [y]), drift and fade while another app plays music ([listening] 0..1).
 * Drawn from shapes (no font), [size] design units tall; [time] is the theme's decoration clock, so the notes stand still
 * during a safety warning. Nothing is drawn while [listening] is 0.
 */
internal fun DrawScope.drawMusicNotes(
    x: Float, y: Float, color: Color, listening: Float, time: Double,
    size: Float = 30f, rise: Float = 190f, count: Int = 4, lean: Float = 1f, glow: Color? = null,
) {
    if (listening <= 0.01f) return
    for (i in 0 until count) {
        val u = ((time / NOTE_S + i.toDouble() / count) % 1.0).toFloat()
        val fade = (if (u < 0.12f) u / 0.12f else if (u < 0.6f) 1f else (1f - u) / 0.4f).coerceIn(0f, 1f)
        val a = fade * listening
        if (a <= 0.01f) continue
        val nx = x + lean * (u * rise * 0.45f + 9f * sin((u * 2f * PI + i * 2.1).toFloat())) + ((i * 3) % count - (count - 1) / 2f) * size * 0.8f
        val ny = y - u * rise
        val sz = size * (0.85f + 0.3f * u)
        glow?.let { drawCircle(it.copy(alpha = it.alpha * a * 0.22f), sz * 0.7f, Offset(nx + sz * 0.2f, ny - sz * 0.4f)) }
        drawEighth(nx, ny, sz, color.copy(alpha = color.alpha * a), double = i % 2 == 1)
    }
}

/** One note: tilted head, stem and flag (or two heads beamed when [double]); ([x], [y]) is the head's centre. */
internal fun DrawScope.drawEighth(x: Float, y: Float, size: Float, color: Color, double: Boolean = false) {
    val hw = size * 0.36f; val hh = size * 0.26f
    val stroke = size * 0.11f
    fun head(cx: Float) = rotate(-22f, Offset(cx, y)) { drawOval(color, Offset(cx - hw / 2f, y - hh / 2f), Size(hw, hh)) }
    val top = y - size * 0.95f
    if (double) {
        val x2 = x + size * 0.55f
        head(x); head(x2)
        val s1 = x + hw * 0.42f; val s2 = x2 + hw * 0.42f
        drawLine(color, Offset(s1, y - hh * 0.2f), Offset(s1, top), stroke)
        drawLine(color, Offset(s2, y - hh * 0.2f), Offset(s2, top - size * 0.08f), stroke)
        drawLine(color, Offset(s1, top), Offset(s2, top - size * 0.08f), stroke * 1.8f)
    } else {
        head(x)
        val sx = x + hw * 0.42f
        drawLine(color, Offset(sx, y - hh * 0.2f), Offset(sx, top), stroke)
        drawLine(color, Offset(sx, top), Offset(sx + size * 0.3f, top + size * 0.32f), stroke * 1.3f)
    }
}

/** Seconds for one note to rise and fade. */
private const val NOTE_S = 3.2
