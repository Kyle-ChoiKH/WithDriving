package io.vroomi.character.render.plasma

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.character.render.theme.drawTextCentered
import kotlin.math.PI
import kotlin.math.sin

/** The lab's colours: a blue-black room, black bakelite, cool steel, and the three lights (violet, neon orange, amber). */
internal object Lab {
    val roomTop = Color(0xFF0E1016)
    val roomBottom = Color(0xFF1A1C26)
    val desk = Color(0xFF1D202B)
    val deskDeep = Color(0xFF11131A)
    val bakelite = Color(0xFF0B0C10)
    val bakeliteLight = Color(0xFF2A2D38)
    val steel = Color(0xFF8E96A6)
    val steelLight = Color(0xFFD6DBE6)
    val steelDark = Color(0xFF454B59)
    val glass = Color(0xFFCDBEF2)
    val violet = Color(0xFFB45CFF)
    val magenta = Color(0xFFE48CFF)
    val core = Color(0xFFFBF3FF)
    val neon = Color(0xFFFF8A3C)
    val neonDim = Color(0xFF5A2A14)
    val amber = Color(0xFFFFB45A)
    val plate = Color(0xFF6C717C)
    val getter = Color(0xFFC9CED8)
    val phosphor = Color(0xFF7CFFB4)
    val screen = Color(0xFF06120E)
}

private val HAIR = Stroke(1f)
private val BEZEL = Stroke(5f)

/** A black bakelite body lit from the upper left: a soft highlight along its top edge. */
internal fun DrawScope.body(l: Float, t: Float, r: Float, b: Float, rad: Float = 8f) {
    drawRoundRect(Brush.verticalGradient(0f to Lab.bakeliteLight, 0.18f to Color(0xFF15171E), 1f to Lab.bakelite, startY = t, endY = b), Offset(l, t), Size(r - l, b - t), CornerRadius(rad))
    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(l + rad, t + 1f), Size(r - l - rad * 2f, 1.2f))
    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(l, t), Size(r - l, b - t), CornerRadius(rad), style = HAIR)
}

// ---------------------------------------------------------------- Lv.2: the neon counter (five tubes)

internal const val COUNTER_TUBES = 5
internal const val TUBE_PITCH = 48f

/** The counter's housing and the five glass tubes, unlit (static). [x] is its centre, [desk] the desk line. */
internal fun DrawScope.counterBody(x: Float, desk: Float) {
    val half = COUNTER_TUBES * TUBE_PITCH / 2f + 12f
    body(x - half, desk - 34f, x + half, desk)
    for (i in 0 until COUNTER_TUBES) {
        val cx = x + (i - (COUNTER_TUBES - 1) / 2f) * TUBE_PITCH
        val l = cx - 19f; val t = desk - 122f
        // The glass: a dark envelope with a domed top, one thin reflection, a fine cathode mesh behind the digit.
        drawRoundRect(Color(0xFF12131A), Offset(l, t), Size(38f, 92f), CornerRadius(17f))
        drawRoundRect(Lab.neonDim.copy(alpha = 0.35f), Offset(l + 5f, t + 14f), Size(28f, 66f), CornerRadius(6f))
        for (k in 0 until 6) drawLine(Color.Black.copy(alpha = 0.35f), Offset(l + 6f, t + 20f + k * 10f), Offset(l + 32f, t + 20f + k * 10f), 0.7f)
        drawRoundRect(Lab.glass.copy(alpha = 0.30f), Offset(l, t), Size(38f, 92f), CornerRadius(17f), style = HAIR)
        drawLine(Color.White.copy(alpha = 0.22f), Offset(l + 7f, t + 16f), Offset(l + 7f, t + 70f), 1.6f, cap = StrokeCap.Round)
    }
}

/** The lit digits: orange neon with a soft halo. [meters] shows as its last five digits. Brightness [b] 0..1. */
internal fun DrawScope.counterDigits(x: Float, desk: Float, meters: Int, tx: ThemeTexts, b: Float) {
    var v = meters.coerceAtLeast(0) % 100_000
    for (i in COUNTER_TUBES - 1 downTo 0) {
        val d = v % 10; v /= 10
        val cx = x + (i - (COUNTER_TUBES - 1) / 2f) * TUBE_PITCH
        val cy = desk - 122f + 47f
        drawOval(Brush.radialGradient(listOf(Lab.neon.copy(alpha = 0.34f * b), Lab.neon.copy(alpha = 0f)), Offset(cx, cy), 34f), Offset(cx - 24f, cy - 38f), Size(48f, 76f))
        drawTextCentered(tx.get(DIGITS[d], 46f, Lab.neon), cx, cy, alpha = b)
    }
}

private val DIGITS = Array(10) { it.toString() }

// ---------------------------------------------------------------- Lv.3: the valve amplifier (two valves)

/** The chassis and the two valves' glass, plates and getters (static). */
internal fun DrawScope.valvesBody(x: Float, desk: Float) {
    body(x - 96f, desk - 40f, x + 96f, desk)
    for (s in listOf(-1f, 1f)) {
        val cx = x + s * 44f
        val t = desk - 176f
        // Socket, glass envelope, grey plate with its ribs, the silver getter flash in the dome.
        drawRoundRect(Lab.bakelite, Offset(cx - 28f, desk - 54f), Size(56f, 18f), CornerRadius(4f))
        drawRoundRect(Lab.bakeliteLight.copy(alpha = 0.7f), Offset(cx - 28f, desk - 54f), Size(56f, 3f), CornerRadius(2f))
        val env = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(cx - 26f, t, cx + 26f, desk - 50f, CornerRadius(24f))) }
        drawPath(env, Color(0xFF14161E).copy(alpha = 0.85f))
        clipPath(env) {
            drawOval(Brush.radialGradient(listOf(Lab.getter.copy(alpha = 0.8f), Lab.getter.copy(alpha = 0f)), Offset(cx - 4f, t + 12f), 26f), Offset(cx - 26f, t - 6f), Size(52f, 40f))
        }
        drawRoundRect(Brush.horizontalGradient(listOf(Lab.steelDark, Lab.plate, Lab.steelDark), startX = cx - 14f, endX = cx + 14f), Offset(cx - 14f, t + 42f), Size(28f, 66f), CornerRadius(3f))
        for (k in 1..3) drawLine(Color.Black.copy(alpha = 0.3f), Offset(cx - 14f, t + 42f + k * 16.5f), Offset(cx + 14f, t + 42f + k * 16.5f), 0.8f)
        drawPath(env, Lab.glass.copy(alpha = 0.32f), style = HAIR)
        drawLine(Color.White.copy(alpha = 0.22f), Offset(cx - 18f, t + 26f), Offset(cx - 18f, desk - 70f), 1.6f, cap = StrokeCap.Round)
    }
}

/** The filaments' amber glow, 0.3 idling .. 1 under acceleration. */
internal fun DrawScope.valvesGlow(x: Float, desk: Float, glow: Float) {
    for (s in listOf(-1f, 1f)) {
        val cx = x + s * 44f
        val t = desk - 176f
        val c = Offset(cx, t + 78f)
        drawOval(Brush.radialGradient(listOf(Lab.amber.copy(alpha = 0.42f * glow), Lab.amber.copy(alpha = 0f)), c, 44f), Offset(cx - 30f, t + 28f), Size(60f, 100f))
        drawLine(Lab.amber.copy(alpha = 0.35f + 0.65f * glow), Offset(cx, t + 36f), Offset(cx, t + 116f), 2.6f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.55f * glow), Offset(cx, t + 44f), Offset(cx, t + 108f), 1f, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Lv.4: the oscilloscope

internal const val SCOPE_R = 52f

/** The scope's case and bezel, the dark round screen and its graticule (static). */
internal fun DrawScope.scopeBody(x: Float, desk: Float) {
    body(x - 76f, desk - 148f, x + 76f, desk, 10f)
    val c = Offset(x, desk - 82f)
    drawCircle(Lab.screen, SCOPE_R, c)
    drawCircle(Lab.phosphor.copy(alpha = 0.10f), SCOPE_R * 0.86f, c, style = HAIR)
    for (k in -2..2) {
        drawLine(Lab.phosphor.copy(alpha = if (k == 0) 0.20f else 0.09f), Offset(x - SCOPE_R, c.y + k * 18f), Offset(x + SCOPE_R, c.y + k * 18f), 0.7f)
        drawLine(Lab.phosphor.copy(alpha = if (k == 0) 0.20f else 0.09f), Offset(x + k * 18f, c.y - SCOPE_R), Offset(x + k * 18f, c.y + SCOPE_R), 0.7f)
    }
    drawCircle(Brush.sweepGradient(listOf(Lab.steelLight, Lab.steelDark, Lab.steel, Lab.steelDark, Lab.steelLight), c), SCOPE_R + 2.5f, c, style = BEZEL)
    // Two knobs under the screen.
    for (s in listOf(-1f, 1f)) {
        val k = Offset(x + s * 44f, desk - 14f)
        drawCircle(Brush.radialGradient(listOf(Lab.steelLight, Lab.steelDark), Offset(k.x - 2f, k.y - 3f), 11f), 7f, k)
    }
}

/** The trace: a sine wave, [hz] cycles across the screen, amplitude [amp] 0..1, sweeping with [t]. */
internal fun DrawScope.scopeTrace(x: Float, desk: Float, hz: Float, amp: Float, t: Double, b: Float, clip: Path) {
    val c = Offset(x, desk - 82f)
    clip.reset(); clip.addOval(Rect(c, SCOPE_R - 2f))
    clipPath(clip) {
        val n = 64
        val w = SCOPE_R * 2f
        var px = c.x - SCOPE_R; var py = c.y - (amp * SCOPE_R * 0.62f * sin(t * 5.0)).toFloat()
        for (i in 1..n) {
            val u = i / n.toFloat()
            val xx = c.x - SCOPE_R + w * u
            val yy = c.y - (amp * SCOPE_R * 0.62f * sin(2.0 * PI * hz * u + t * 5.0)).toFloat()
            drawLine(Lab.phosphor.copy(alpha = 0.22f * b), Offset(px, py), Offset(xx, yy), 5f, cap = StrokeCap.Round)
            drawLine(Lab.phosphor.copy(alpha = 0.95f * b), Offset(px, py), Offset(xx, yy), 1.5f, cap = StrokeCap.Round)
            px = xx; py = yy
        }
    }
}

// ---------------------------------------------------------------- Lv.5: Jacob's ladder

internal const val LADDER_TOP = 176f

/** The ladder's base and its two diverging rods (static). */
internal fun DrawScope.ladderBody(x: Float, desk: Float) {
    body(x - 50f, desk - 34f, x + 50f, desk)
    for (s in listOf(-1f, 1f)) {
        val a = Offset(x + s * 9f, desk - 34f); val b = Offset(x + s * 44f, LADDER_TOP)
        drawLine(Lab.steelDark, a, b, 4.6f, cap = StrokeCap.Round)
        drawLine(Lab.steel, a, b, 3f, cap = StrokeCap.Round)
        drawLine(Lab.steelLight.copy(alpha = 0.7f), Offset(a.x - 0.8f, a.y), Offset(b.x - 0.8f, b.y), 0.9f, cap = StrokeCap.Round)
        drawCircle(Lab.steelLight, 3.2f, b)
    }
}

/**
 * The arc between the rods at height [f] (0 at the foot .. 1 at the top). It rises slowly with the distance, fades
 * in at the foot and softly out near the top; its brightness only breathes ([b]), it never flashes.
 */
internal fun DrawScope.ladderArc(x: Float, desk: Float, f: Float, wobble: Double, b: Float, path: Path, glow: Stroke, mid: Stroke, core: Stroke) {
    val env = (f / 0.06f).coerceIn(0f, 1f) * ((1f - f) / 0.18f).coerceIn(0f, 1f)
    if (env <= 0.01f) return
    val bottom = desk - 40f
    val y = bottom + (LADDER_TOP + 8f - bottom) * f
    val half = 9f + (44f - 9f) * f
    val lift = 7f + 10f * f + (4.0 * sin(wobble * 2.3)).toFloat()
    path.reset()
    path.moveTo(x - half, y)
    path.quadraticTo(x + (5.0 * sin(wobble * 3.1)).toFloat(), y - lift, x + half, y)
    val a = env * b
    drawPath(path, Lab.violet.copy(alpha = 0.26f * a), style = glow)
    drawPath(path, Lab.magenta.copy(alpha = 0.6f * a), style = mid)
    drawPath(path, Lab.core.copy(alpha = 0.92f * a), style = core)
}

/** Used by the painter and its tests: which instruments an upgrade step shows. */
internal object PlasmaStep {
    fun counter(model: Int) = model >= 1
    fun valves(model: Int) = model >= 2
    fun scope(model: Int) = model >= 3
    fun ladder(model: Int) = model >= 4
}
