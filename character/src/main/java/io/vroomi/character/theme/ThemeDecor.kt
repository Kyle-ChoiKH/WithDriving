package io.vroomi.character.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import io.vroomi.character.calibre.SceneTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The themes' props: three small things per theme that the user can put into the scene, like the kart's outfits.
 * They open with the safe-driving grade (silver, gold, platinum) and each one is switched on or off on its own.
 * The kart has its wardrobe instead.
 *
 * A prop is drawn over the finished scene in canvas fractions, so it does not depend on a theme's own layout: it
 * hangs from the top edge, floats in the open part of the picture or stands at the bottom edge as a foreground
 * object. Props are still (nothing blinks or moves), carry no information, and are drawn under the colourway.
 */
object ThemeDecor {
    const val COUNT = 3

    private val NAMES: Map<SceneTheme, List<String>> = mapOf(
        SceneTheme.CALIBRE to listOf("루페", "드라이버 세트", "부품 트레이"),
        SceneTheme.ENGINE to listOf("작업등", "체커 깃발", "안전 원뿔"),
        SceneTheme.MUSIC_BOX to listOf("촛불", "악보", "별 모빌"),
        SceneTheme.PLASMA to listOf("필라멘트 전구", "빛나는 플라스크", "보안경"),
        SceneTheme.HANGAR to listOf("호이스트 고리", "정비 드론", "원뿔과 드럼통"),
        SceneTheme.TERRARIUM to listOf("꼬마 전구", "나비", "버섯집"),
        SceneTheme.AQUARIUM to listOf("해파리", "보물 상자", "잠수 헬멧"),
        SceneTheme.ORBIT to listOf("비행접시", "유성우", "깃발 소행성"),
        SceneTheme.TREASURE_MAP to listOf("돋보기", "디바이더", "깃펜과 잉크"),
        SceneTheme.PAPER to listOf("가위", "풀", "색연필"),
        SceneTheme.PIXEL to listOf("깃발 줄", "열기구", "새 떼"),
    )

    /** True when the theme has props (every theme but the kart). */
    fun has(theme: SceneTheme): Boolean = NAMES.containsKey(theme)

    /** The theme's props in the order they open (empty for the kart). */
    fun names(theme: SceneTheme): List<String> = NAMES[theme].orEmpty()

    /** The grade level (2 silver .. 4 platinum) that opens prop [index]. */
    fun unlockLevel(index: Int): Int = index + 2

    /** The props of [mask] (bit i = prop i) of [theme], over the scene that fills [ds]. */
    fun draw(ds: DrawScope, theme: SceneTheme, mask: Int, alpha: Float = 1f) {
        if (mask == 0 || alpha <= 0.01f || !has(theme)) return
        with(ds) {
            val layer = alpha < 0.99f
            if (layer) drawIntoCanvas { it.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { this.alpha = alpha }) }
            for (i in 0 until COUNT) if (mask and (1 shl i) != 0) prop(theme, i)
            if (layer) drawIntoCanvas { it.restore() }
        }
    }

    // ------------------------------------------------------------------ helpers

    private val ROUND = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val STEEL = Color(0xFFB9C0C8)
    private val STEEL_DARK = Color(0xFF5E666F)
    private val BRASS = Color(0xFFC9A14A)
    private val BRASS_DARK = Color(0xFF8A6A2A)
    private val INK = Color(0xFF1B1D21)
    private val WARM = Color(0xFFFFE2A0)

    /** Draws [block] in design units (600 down the canvas) with the origin at the canvas fraction ([fx], [fy]). */
    private inline fun DrawScope.at(fx: Float, fy: Float, rot: Float = 0f, scale: Float = 1f, block: DrawScope.() -> Unit) =
        atPx(fx * size.width, fy * size.height, rot, scale, block)

    /** One design unit in px: 600 units down the canvas, and never more than 760 across (so props stay small in an upright window). */
    private fun DrawScope.unit(): Float = min(size.height / 600f, size.width / 760f)

    private inline fun DrawScope.atPx(x: Float, y: Float, rot: Float = 0f, scale: Float = 1f, block: DrawScope.() -> Unit) {
        val u = unit() * scale
        withTransform({ translate(x, y); rotate(rot, Offset.Zero); scale(u, u, Offset.Zero) }, block)
    }

    private fun DrawScope.glow(c: Offset, r: Float, color: Color, a: Float) =
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = 0f)), c, r), r, c)

    private fun DrawScope.ground(cx: Float, w: Float, a: Float = 0.30f) =
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = a), Color.Transparent), Offset(cx, -4f), w), Offset(cx - w, -14f), Size(w * 2, 22f))

    private fun poly(vararg v: Float) = Path().apply { moveTo(v[0], v[1]); var i = 2; while (i < v.size) { lineTo(v[i], v[i + 1]); i += 2 }; close() }

    private fun star(c: Offset, r: Float) = Path().apply {
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f; val rr = if (i % 2 == 0) r else r * 0.45f
            val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** A traffic cone standing on y = 0 at [x]. */
    private fun DrawScope.cone(x: Float, h: Float) {
        ground(x, h * 0.5f)
        drawRect(Color(0xFF2A2C30), Offset(x - h * 0.36f, -h * 0.10f), Size(h * 0.72f, h * 0.10f))
        drawPath(poly(x - h * 0.26f, -h * 0.10f, x + h * 0.26f, -h * 0.10f, x + h * 0.06f, -h, x - h * 0.06f, -h), Color(0xFFF07A1A))
        drawPath(poly(x - h * 0.19f, -h * 0.40f, x + h * 0.19f, -h * 0.40f, x + h * 0.14f, -h * 0.58f, x - h * 0.14f, -h * 0.58f), Color(0xFFF4F1E8))
        drawPath(poly(x + h * 0.04f, -h * 0.10f, x + h * 0.26f, -h * 0.10f, x + h * 0.06f, -h), Color.Black.copy(alpha = 0.18f))
    }

    // ------------------------------------------------------------------ the props

    private fun DrawScope.prop(theme: SceneTheme, i: Int) {
        when (theme) {
            SceneTheme.CALIBRE -> {
              // The bench beside the watch (the watch takes about half the canvas height each side of the centre).
              val spare = (size.width / 2f - 0.50f * size.height) / unit()
              when (i) {
                0 -> if (spare >= 112f) atPx(spare * 0.62f * unit(), 0.36f * size.height) { // a loupe lying on the bench
                    drawCircle(Color.Black.copy(alpha = 0.16f), 53f, Offset(8f, 10f))
                    drawCircle(Brush.sweepGradient(listOf(STEEL, STEEL_DARK, STEEL, Color.White, STEEL_DARK, STEEL), Offset.Zero), 50f, Offset.Zero)
                    drawCircle(Color(0xFF2A2D31), 41f, Offset.Zero)
                    drawCircle(Brush.radialGradient(listOf(Color(0x80E6F1FB), Color(0xA0AFC8E2)), Offset(-10f, -12f), 60f), 38f, Offset.Zero)
                    drawArc(Color.White.copy(alpha = 0.7f), 200f, 60f, false, Offset(-30f, -30f), Size(60f, 60f), style = Stroke(3f, cap = StrokeCap.Round))
                }
                1 -> if (spare >= 150f) atPx(size.width - spare * 0.60f * unit(), 0.48f * size.height, rot = -24f) { // three small screwdrivers
                    val grips = arrayOf(Color(0xFFC8402F), Color(0xFF2F6FB3), Color(0xFFE0B23A))
                    for (k in 0 until 3) {
                        val y = k * 24f - 24f
                        drawLine(Color.Black.copy(alpha = 0.14f), Offset(-74f, y + 6f), Offset(74f, y + 6f), 9f, cap = StrokeCap.Round)
                        drawLine(STEEL, Offset(-6f, y), Offset(70f, y), 4f)
                        drawLine(Color.White.copy(alpha = 0.6f), Offset(-6f, y - 1f), Offset(66f, y - 1f), 1f)
                        drawLine(STEEL_DARK, Offset(70f, y), Offset(78f, y), 2.4f)
                        drawRoundRect(grips[k], Offset(-78f, y - 7f), Size(72f, 14f), CornerRadius(6f))
                        drawRect(Color.Black.copy(alpha = 0.22f), Offset(-78f, y + 2f), Size(72f, 5f))
                        drawRect(STEEL, Offset(-14f, y - 7f), Size(6f, 14f))
                    }
                }
                else -> if (spare >= 150f) atPx(size.width - spare * 0.66f * unit(), 0.74f * size.height) { // a parts tray with two wheels and screws
                    drawRoundRect(Color.Black.copy(alpha = 0.16f), Offset(-64f, -36f), Size(140f, 84f), CornerRadius(12f))
                    drawRoundRect(Color(0xFF3B3A36), Offset(-70f, -42f), Size(140f, 84f), CornerRadius(12f))
                    drawRoundRect(Color(0xFF2E5A45), Offset(-62f, -34f), Size(124f, 68f), CornerRadius(8f))
                    for ((c, r) in listOf(Offset(-30f, -4f) to 15f, Offset(4f, 12f) to 9f)) {
                        for (k in 0 until 12) { val a = k * PI.toFloat() / 6f; drawCircle(BRASS, r * 0.2f, Offset(c.x + cos(a) * r, c.y + sin(a) * r)) }
                        drawCircle(BRASS, r, c); drawCircle(BRASS_DARK, r, c, style = Stroke(1.2f)); drawCircle(Color(0xFF2E5A45), r * 0.3f, c)
                    }
                    for (c in listOf(Offset(36f, -14f), Offset(44f, 14f), Offset(24f, 0f))) { drawCircle(STEEL, 5f, c); drawLine(STEEL_DARK, Offset(c.x - 3f, c.y), Offset(c.x + 3f, c.y), 1.4f) }
                }
              }
            }
            SceneTheme.ENGINE -> when (i) {
                0 -> at(0.50f, 0f) { // a caged work lamp on its cable
                    drawLine(INK, Offset(0f, 0f), Offset(0f, 40f), 3f)
                    glow(Offset(0f, 74f), 86f, WARM, 0.34f)
                    drawRoundRect(Color(0xFF2A2C30), Offset(-11f, 38f), Size(22f, 14f), CornerRadius(3f))
                    drawRoundRect(Color(0xFFFFF0C4), Offset(-9f, 52f), Size(18f, 38f), CornerRadius(9f))
                    for (x in floatArrayOf(-12f, 0f, 12f)) drawLine(INK, Offset(x * 0.8f, 52f), Offset(x, 92f), 1.6f)
                    drawArc(INK, 0f, 180f, false, Offset(-12f, 80f), Size(24f, 16f), style = Stroke(1.6f))
                    drawLine(INK, Offset(-12f, 70f), Offset(12f, 70f), 1.6f)
                }
                1 -> at(0.22f, 0f) { // a string of chequered pennants along the top
                    val n = 9
                    var px = 0f; var py = 8f
                    for (k in 1..n) {
                        val f = k / n.toFloat(); val nx = 320f * f; val ny = 8f + 20f * (1f - (2f * f - 1f) * (2f * f - 1f))
                        drawLine(Color(0xFF8E96A0), Offset(px, py), Offset(nx, ny), 1.4f)
                        if (k < n) {
                            drawRect(Color(0xFFF4F1E8), Offset(nx - 12f, ny), Size(24f, 20f))
                            for (r in 0 until 2) for (c in 0 until 3) if ((r + c) % 2 == 0) drawRect(INK, Offset(nx - 12f + c * 8f, ny + r * 10f), Size(8f, 10f))
                            drawRect(Color.Black.copy(alpha = 0.4f), Offset(nx - 12f, ny), Size(24f, 20f), style = Stroke(1f))
                        }
                        px = nx; py = ny
                    }
                }
                else -> at(0.92f, 1f) { cone(-26f, 64f); cone(30f, 50f) }
            }
            SceneTheme.MUSIC_BOX -> when (i) {
                0 -> at(0.10f, 1f) { // a candle in a brass holder
                    glow(Offset(0f, -150f), 150f, WARM, 0.32f)
                    ground(0f, 70f)
                    drawOval(BRASS_DARK, Offset(-48f, -22f), Size(96f, 22f)); drawOval(BRASS, Offset(-46f, -26f), Size(92f, 20f))
                    drawRect(BRASS, Offset(-8f, -40f), Size(16f, 18f))
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFFFFFBF0), Color(0xFFE9DFC8), Color(0xFFC9BDA2)), startX = -14f, endX = 14f), Offset(-14f, -132f), Size(28f, 94f))
                    drawOval(Color(0xFFFFF6DC), Offset(-14f, -137f), Size(28f, 10f))
                    drawLine(INK, Offset(0f, -134f), Offset(0f, -144f), 1.6f)
                    drawPath(Path().apply { moveTo(0f, -176f); quadraticTo(13f, -156f, 0f, -142f); quadraticTo(-13f, -156f, 0f, -176f); close() }, Color(0xFFFFB03A))
                    drawPath(Path().apply { moveTo(0f, -166f); quadraticTo(6f, -154f, 0f, -145f); quadraticTo(-6f, -154f, 0f, -166f); close() }, Color(0xFFFFF4C2))
                }
                1 -> at(0.70f, 0.975f, rot = -7f) { // a page of sheet music on the table
                    drawRect(Color.Black.copy(alpha = 0.22f), Offset(-72f, -100f), Size(156f, 104f))
                    drawRect(Color(0xFFF6EFDD), Offset(-78f, -106f), Size(156f, 104f))
                    for (s in 0 until 2) {
                        val y0 = -90f + s * 44f
                        for (l in 0 until 5) drawLine(Color(0xFF4A4038), Offset(-66f, y0 + l * 5f), Offset(66f, y0 + l * 5f), 0.8f)
                        for (k in 0 until 7) {
                            val x = -54f + k * 18f; val y = y0 + 20f - ((k * 3 + s * 2) % 5) * 5f
                            drawOval(Color(0xFF2A241E), Offset(x - 4f, y - 3f), Size(8f, 6f)); drawLine(Color(0xFF2A241E), Offset(x + 4f, y), Offset(x + 4f, y - 16f), 1f)
                        }
                    }
                }
                else -> at(0.82f, 0f) { // a mobile of brass stars
                    drawLine(Color(0xFF8A7A5A), Offset(0f, 0f), Offset(0f, 30f), 1.2f)
                    drawLine(BRASS, Offset(-74f, 26f), Offset(74f, 36f), 3f, cap = StrokeCap.Round)
                    val hang = listOf(Offset(-70f, 26f) to 62f, Offset(-28f, 29f) to 96f, Offset(22f, 33f) to 54f, Offset(68f, 36f) to 84f)
                    for ((k, hp) in hang.withIndex()) {
                        val (p, len) = hp
                        drawLine(Color(0xFF8A7A5A), p, Offset(p.x, p.y + len), 1f)
                        val c = Offset(p.x, p.y + len + 12f)
                        if (k == 1) { drawCircle(BRASS, 14f, c); drawCircle(Color(0xFF3A2A1E), 12f, Offset(c.x + 6f, c.y - 3f)) }
                        else { drawPath(star(c, 14f), BRASS); drawPath(star(c, 14f), BRASS_DARK, style = Stroke(1f)) }
                    }
                }
            }
            SceneTheme.PLASMA -> when (i) {
                0 -> at(0.22f, 0f) { // a bare filament bulb on its cord
                    drawLine(INK, Offset(0f, 0f), Offset(0f, 96f), 2.4f)
                    glow(Offset(0f, 140f), 110f, WARM, 0.24f)
                    drawRect(BRASS_DARK, Offset(-9f, 96f), Size(18f, 16f))
                    drawCircle(Color(0x33FFE9B0), 27f, Offset(0f, 140f)); drawCircle(Color(0xAAFFF3D0), 27f, Offset(0f, 140f), style = Stroke(1.4f))
                    val fil = Path().apply { moveTo(-9f, 150f); for (k in 0..6) lineTo(-9f + k * 3f, if (k % 2 == 0) 132f else 148f) }
                    drawPath(fil, Color(0xFFFFC46B), style = ROUND)
                    drawArc(Color.White.copy(alpha = 0.5f), 210f, 50f, false, Offset(-20f, 120f), Size(40f, 40f), style = Stroke(2f, cap = StrokeCap.Round))
                }
                1 -> at(0.08f, 1f) { // a flask of glowing liquid
                    glow(Offset(0f, -50f), 120f, Color(0xFF6CFFB0), 0.22f)
                    ground(0f, 70f)
                    val body = Path().apply { moveTo(-11f, -150f); lineTo(11f, -150f); lineTo(11f, -104f); lineTo(54f, -14f); quadraticTo(56f, -6f, 46f, -6f); lineTo(-46f, -6f); quadraticTo(-56f, -6f, -54f, -14f); lineTo(-11f, -104f); close() }
                    val liquid = Path().apply { moveTo(-33f, -58f); lineTo(33f, -58f); lineTo(54f, -14f); quadraticTo(56f, -6f, 46f, -6f); lineTo(-46f, -6f); quadraticTo(-56f, -6f, -54f, -14f); close() }
                    drawPath(liquid, Brush.verticalGradient(listOf(Color(0xFF9CFFC8), Color(0xFF1FA868)), startY = -58f, endY = -6f))
                    drawPath(body, Color.White.copy(alpha = 0.10f)); drawPath(body, Color.White.copy(alpha = 0.7f), style = Stroke(1.6f, join = StrokeJoin.Round))
                    drawRect(Color.White.copy(alpha = 0.7f), Offset(-14f, -154f), Size(28f, 5f))
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(-22f, -70f), Offset(-40f, -30f), 2.4f, cap = StrokeCap.Round)
                    for (c in listOf(Offset(-8f, -30f), Offset(14f, -42f), Offset(4f, -18f))) drawCircle(Color.White.copy(alpha = 0.55f), 3f, c, style = Stroke(1f))
                }
                else -> at(0.72f, 1f) { // safety goggles at the front of the bench
                    ground(0f, 80f, 0.4f)
                    drawLine(Color(0xFF3A3F47), Offset(-96f, -24f), Offset(96f, -24f), 8f, cap = StrokeCap.Round)
                    for (s in floatArrayOf(-1f, 1f)) {
                        drawRoundRect(Color(0xFF23262B), Offset(s * 34f - 30f, -46f), Size(60f, 40f), CornerRadius(14f))
                        drawRoundRect(Brush.linearGradient(listOf(Color(0x99CFE6FF), Color(0x558FB0D6)), Offset(s * 34f - 24f, -40f), Offset(s * 34f + 24f, -12f)), Offset(s * 34f - 24f, -40f), Size(48f, 28f), CornerRadius(10f))
                        drawLine(Color.White.copy(alpha = 0.6f), Offset(s * 34f - 16f, -34f), Offset(s * 34f - 2f, -34f), 2f, cap = StrokeCap.Round)
                    }
                    drawRect(Color(0xFF23262B), Offset(-6f, -34f), Size(12f, 10f))
                }
            }
            SceneTheme.HANGAR -> when (i) {
                0 -> at(0.86f, 0f) { // a hoist chain and its hook
                    for (k in 0 until 12) drawOval(STEEL_DARK, Offset(-4f, k * 13f), Size(8f, 16f), style = Stroke(2.4f))
                    drawRect(Color(0xFFE0A02A), Offset(-16f, 156f), Size(32f, 26f)); drawRect(INK, Offset(-16f, 156f), Size(32f, 26f), style = Stroke(1.2f))
                    for (k in 0 until 3) drawLine(INK, Offset(-16f + k * 12f, 182f), Offset(-4f + k * 12f, 156f), 3f)
                    drawPath(Path().apply { moveTo(0f, 182f); lineTo(0f, 204f); quadraticTo(0f, 228f, -18f, 222f); quadraticTo(-28f, 216f, -22f, 204f) }, STEEL, style = Stroke(6f, cap = StrokeCap.Round))
                }
                1 -> at(0.90f, 0.36f) { // a service drone with its lamp
                    drawPath(poly(-8f, 10f, 8f, 10f, 46f, 150f, -46f, 150f), Brush.verticalGradient(listOf(WARM.copy(alpha = 0.22f), WARM.copy(alpha = 0f)), startY = 10f, endY = 150f))
                    for (s in floatArrayOf(-1f, 1f)) {
                        drawLine(STEEL_DARK, Offset(0f, -2f), Offset(s * 40f, -12f), 4f, cap = StrokeCap.Round)
                        drawOval(Color.White.copy(alpha = 0.22f), Offset(s * 40f - 22f, -20f), Size(44f, 8f))
                        drawRect(INK, Offset(s * 40f - 2f, -18f), Size(4f, 8f))
                    }
                    drawRoundRect(Color(0xFF2E3338), Offset(-22f, -8f), Size(44f, 16f), CornerRadius(6f))
                    drawRect(Color(0xFFF07A1A), Offset(-22f, -2f), Size(44f, 3f))
                    drawCircle(WARM, 4f, Offset(0f, 9f)); drawCircle(Color(0xFF7FE0FF), 2f, Offset(16f, -3f))
                }
                else -> at(0.10f, 1f) { // an oil drum and two cones
                    ground(-30f, 60f)
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF3F74A8), Color(0xFF2F5D8A), Color(0xFF1C3A58)), startX = -62f, endX = 2f), Offset(-62f, -92f), Size(64f, 90f))
                    for (y in floatArrayOf(-64f, -34f)) drawRect(Color.Black.copy(alpha = 0.25f), Offset(-62f, y), Size(64f, 4f))
                    drawOval(Color(0xFF4F86BD), Offset(-62f, -100f), Size(64f, 16f)); drawOval(INK, Offset(-44f, -96f), Size(10f, 5f))
                    cone(36f, 58f); cone(82f, 44f)
                }
            }
            SceneTheme.TERRARIUM -> when (i) {
                0 -> at(0.03f, 0f) { // a string of fairy lights across the top
                    val span = size.width / unit() * 0.60f
                    val n = 4
                    for (s in 0 until n) {
                        val x0 = span * s / n; val x1 = span * (s + 1) / n
                        val wire = Path().apply { moveTo(x0, 6f); quadraticTo((x0 + x1) / 2f, 70f, x1, 6f) }
                        drawPath(wire, Color(0xFF2E3B26).copy(alpha = 0.8f), style = Stroke(1.6f))
                        for (k in 1..4) {
                            val f = k / 5f; val x = x0 + (x1 - x0) * f; val y = 6f + 64f * 2f * f * (1f - f) + 6f
                            glow(Offset(x, y + 4f), 26f, WARM, 0.5f)
                            drawCircle(Color(0xFFFFF3CF), 5f, Offset(x, y + 4f))
                        }
                    }
                }
                1 -> { // two butterflies
                    for ((k, p) in listOf(Triple(0.20f, 0.34f, -18f), Triple(0.28f, 0.22f, 22f)).withIndex()) at(p.first, p.second, rot = p.third, scale = if (k == 0) 1f else 0.72f) {
                        val wing = if (k == 0) Color(0xFFF2A23A) else Color(0xFF9FD3F2)
                        for (s in floatArrayOf(-1f, 1f)) {
                            drawOval(wing, Offset(if (s < 0) -34f else 2f, -26f), Size(32f, 30f)); drawOval(INK.copy(alpha = 0.7f), Offset(if (s < 0) -34f else 2f, -26f), Size(32f, 30f), style = Stroke(1.6f))
                            drawOval(wing.copy(alpha = 0.9f), Offset(if (s < 0) -26f else 4f, 0f), Size(22f, 20f)); drawOval(INK.copy(alpha = 0.7f), Offset(if (s < 0) -26f else 4f, 0f), Size(22f, 20f), style = Stroke(1.4f))
                            drawCircle(Color.White.copy(alpha = 0.7f), 3f, Offset(s * 20f, -14f))
                        }
                        drawLine(INK, Offset(0f, -18f), Offset(0f, 16f), 3.4f, cap = StrokeCap.Round)
                        drawLine(INK, Offset(0f, -18f), Offset(-7f, -30f), 1f); drawLine(INK, Offset(0f, -18f), Offset(7f, -30f), 1f)
                    }
                }
                else -> at(0.10f, 1f) { // a tiny mushroom house
                    ground(0f, 80f, 0.4f)
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFFBF3E2), Color(0xFFE2D6BD), Color(0xFFB9AC92)), startX = -32f, endX = 32f), Offset(-32f, -86f), Size(64f, 86f), CornerRadius(14f))
                    drawPath(Path().apply { moveTo(-13f, 0f); lineTo(-13f, -30f); quadraticTo(0f, -50f, 13f, -30f); lineTo(13f, 0f); close() }, Color(0xFF7A4A2A))
                    drawCircle(BRASS, 2.4f, Offset(8f, -18f))
                    drawCircle(Color(0xFFFFE08A), 8f, Offset(16f, -62f)); drawCircle(Color(0xFF7A4A2A), 8f, Offset(16f, -62f), style = Stroke(2f))
                    val cap = Path().apply { moveTo(-76f, -78f); quadraticTo(-70f, -168f, 0f, -170f); quadraticTo(70f, -168f, 76f, -78f); quadraticTo(0f, -62f, -76f, -78f); close() }
                    drawPath(cap, Brush.horizontalGradient(listOf(Color(0xFFE8604E), Color(0xFFD8483B), Color(0xFF9C2C24)), startX = -76f, endX = 76f))
                    for (c in listOf(Offset(-36f, -118f) to 11f, Offset(8f, -140f) to 9f, Offset(40f, -104f) to 12f, Offset(-8f, -98f) to 7f)) drawOval(Color(0xFFFFF7E8), Offset(c.first.x - c.second, c.first.y - c.second * 0.8f), Size(c.second * 2, c.second * 1.6f))
                    for (k in 0 until 7) drawLine(Color(0xFF4F8F4A), Offset(-58f + k * 19f, 0f), Offset(-62f + k * 19f + (k % 3) * 4f, -16f - (k % 2) * 8f), 2.4f, cap = StrokeCap.Round)
                }
            }
            SceneTheme.AQUARIUM -> when (i) {
                0 -> { // two jellyfish
                    for ((k, p) in listOf(Offset(0.14f, 0.30f), Offset(0.23f, 0.47f)).withIndex()) at(p.x, p.y, scale = if (k == 0) 1f else 0.68f) {
                        val bell = Path().apply { moveTo(-34f, 0f); quadraticTo(-36f, -46f, 0f, -48f); quadraticTo(36f, -46f, 34f, 0f); quadraticTo(17f, -8f, 0f, 0f); quadraticTo(-17f, -8f, -34f, 0f); close() }
                        glow(Offset(0f, -20f), 70f, Color(0xFFFFC6E8), 0.20f)
                        drawPath(bell, Brush.verticalGradient(listOf(Color(0xCCFFE3F3), Color(0x88F29BD0)), startY = -48f, endY = 0f))
                        drawPath(bell, Color.White.copy(alpha = 0.7f), style = Stroke(1.4f))
                        for (t in -2..2) {
                            val x = t * 12f
                            drawPath(Path().apply { moveTo(x, -2f); quadraticTo(x + 8f, 22f, x, 44f); quadraticTo(x - 8f, 64f, x + 2f, 86f - kotlin.math.abs(t) * 10f) }, Color(0xAAFFD6EC), style = Stroke(1.6f, cap = StrokeCap.Round))
                        }
                    }
                }
                1 -> at(0.12f, 1f) { // an open chest of gold
                    ground(0f, 86f, 0.35f)
                    glow(Offset(0f, -66f), 90f, Color(0xFFFFE08A), 0.34f)
                    drawPath(poly(-58f, -62f, 58f, -62f, 48f, -112f, -48f, -112f), Color(0xFF5A3A22))
                    drawPath(poly(-58f, -62f, 58f, -62f, 48f, -112f, -48f, -112f), BRASS_DARK, style = Stroke(3f))
                    for (c in listOf(Offset(-30f, -64f), Offset(-10f, -70f), Offset(12f, -66f), Offset(32f, -62f), Offset(0f, -60f), Offset(-20f, -58f), Offset(22f, -58f))) { drawOval(Color(0xFFFFD84D), Offset(c.x - 10f, c.y - 5f), Size(20f, 10f)); drawOval(Color(0xFFB8860B), Offset(c.x - 10f, c.y - 5f), Size(20f, 10f), style = Stroke(1f)) }
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF8A5A34), Color(0xFF6B4324), Color(0xFF442A16)), startX = -58f, endX = 58f), Offset(-58f, -56f), Size(116f, 56f))
                    for (x in floatArrayOf(-58f, -8f, 42f)) drawRect(BRASS, Offset(x, -56f), Size(16f, 56f))
                    drawRect(BRASS_DARK, Offset(-58f, -56f), Size(116f, 56f), style = Stroke(2f))
                    drawRoundRect(BRASS, Offset(-9f, -42f), Size(18f, 20f), CornerRadius(4f)); drawCircle(INK, 3f, Offset(0f, -32f))
                }
                else -> at(0.66f, 1f) { // an old diving helmet
                    ground(0f, 76f, 0.35f)
                    drawRoundRect(BRASS_DARK, Offset(-50f, -30f), Size(100f, 30f), CornerRadius(8f))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFE9C873), BRASS, BRASS_DARK), Offset(-16f, -76f), 80f), 48f, Offset(0f, -62f))
                    drawCircle(BRASS_DARK, 24f, Offset(0f, -60f)); drawCircle(Color(0xFF12303A), 19f, Offset(0f, -60f))
                    drawArc(Color.White.copy(alpha = 0.5f), 200f, 60f, false, Offset(-14f, -74f), Size(28f, 28f), style = Stroke(2f, cap = StrokeCap.Round))
                    for (s in floatArrayOf(-1f, 1f)) { drawCircle(BRASS_DARK, 11f, Offset(s * 42f, -62f)); drawCircle(Color(0xFF12303A), 7f, Offset(s * 42f, -62f)) }
                    for (k in 0 until 6) drawCircle(Color(0xFFE9C873), 2.4f, Offset(-40f + k * 16f, -18f))
                    drawRect(BRASS_DARK, Offset(-6f, -118f), Size(12f, 10f))
                }
            }
            SceneTheme.ORBIT -> {
                val ink = Color(0xFF7CF2B0)
                val soft = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val line = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                when (i) {
                    0 -> at(0.20f, 0.26f) { // a saucer, in the display's line
                        val p = Path().apply { addOval(Rect(-48f, -10f, 48f, 12f)); moveTo(-22f, -8f); quadraticTo(0f, -40f, 22f, -8f) }
                        drawPath(p, ink.copy(alpha = 0.22f), style = soft); drawPath(p, ink, style = line)
                        for (x in floatArrayOf(-28f, 0f, 28f)) drawCircle(ink, 2.6f, Offset(x, 2f))
                        for (x in floatArrayOf(-22f, 0f, 22f)) drawLine(ink.copy(alpha = 0.45f), Offset(x, 16f), Offset(x * 1.8f, 58f), 1.2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f))
                    }
                    1 -> at(0.40f, 0.09f) { // a meteor shower
                        for (k in 0 until 6) {
                            val x = k * 34f; val y = (k * 37 % 5) * 9f; val len = 46f + (k % 3) * 16f
                            drawLine(ink.copy(alpha = 0.25f), Offset(x + len, y - len * 0.5f), Offset(x, y), 5f, cap = StrokeCap.Round)
                            drawLine(ink, Offset(x + len, y - len * 0.5f), Offset(x, y), 1.4f, cap = StrokeCap.Round)
                            drawCircle(Color.White, 2.4f, Offset(x, y))
                        }
                    }
                    else -> at(0.24f, 0.82f) { // an asteroid with a flag planted on it
                        val rock = poly(-40f, 4f, -30f, -18f, -6f, -26f, 22f, -20f, 40f, -2f, 30f, 20f, 2f, 28f, -26f, 22f)
                        drawPath(rock, ink.copy(alpha = 0.22f), style = soft); drawPath(rock, ink, style = line)
                        drawCircle(ink.copy(alpha = 0.7f), 7f, Offset(-12f, 4f), style = line); drawCircle(ink.copy(alpha = 0.7f), 4f, Offset(16f, 8f), style = line)
                        drawLine(ink, Offset(6f, -22f), Offset(6f, -68f), 1.6f)
                        val flag = poly(6f, -68f, 34f, -60f, 6f, -50f)
                        drawPath(flag, ink.copy(alpha = 0.35f)); drawPath(flag, ink, style = line)
                    }
                }
            }
            SceneTheme.TREASURE_MAP -> when (i) {
                0 -> at(0.25f, 0.32f, rot = -32f) { // a magnifying glass lying on the chart
                    drawCircle(Color.Black.copy(alpha = 0.14f), 48f, Offset(8f, 10f))
                    drawRoundRect(Color.Black.copy(alpha = 0.14f), Offset(48f, 0f), Size(96f, 18f), CornerRadius(9f))
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8A5A34), Color(0xFF5A3A22)), startY = -9f, endY = 9f), Offset(52f, -9f), Size(92f, 18f), CornerRadius(9f))
                    drawRect(BRASS, Offset(42f, -8f), Size(16f, 16f))
                    drawCircle(Color(0x33CFE0EA), 40f, Offset.Zero)
                    drawCircle(Brush.sweepGradient(listOf(BRASS, BRASS_DARK, Color(0xFFE9C873), BRASS_DARK, BRASS), Offset.Zero), 44f, Offset.Zero, style = Stroke(7f))
                    drawArc(Color.White.copy(alpha = 0.6f), 195f, 55f, false, Offset(-30f, -30f), Size(60f, 60f), style = Stroke(3f, cap = StrokeCap.Round))
                }
                1 -> at(0.60f, 0.80f, rot = 16f) { // brass dividers
                    for (s in floatArrayOf(-1f, 1f)) {
                        drawLine(Color.Black.copy(alpha = 0.14f), Offset(6f, -84f), Offset(s * 36f + 6f, 6f), 7f, cap = StrokeCap.Round)
                        drawLine(BRASS, Offset(0f, -90f), Offset(s * 30f, -18f), 7f, cap = StrokeCap.Round)
                        drawLine(Color(0xFFE9C873), Offset(-1f, -90f), Offset(s * 30f - 1f, -18f), 1.6f)
                        drawLine(STEEL, Offset(s * 30f, -18f), Offset(s * 36f, 0f), 3f, cap = StrokeCap.Round)
                    }
                    drawCircle(BRASS_DARK, 11f, Offset(0f, -90f)); drawCircle(BRASS, 8f, Offset(0f, -90f))
                    drawRect(BRASS_DARK, Offset(-4f, -112f), Size(8f, 14f))
                }
                else -> at(0.84f, 0.60f) { // a quill and an inkwell
                    drawCircle(Color.Black.copy(alpha = 0.16f), 30f, Offset(6f, 8f))
                    drawCircle(BRASS_DARK, 28f, Offset.Zero); drawCircle(Color(0xFF1A1F2A), 21f, Offset.Zero); drawCircle(Color(0xFF05070B), 13f, Offset.Zero)
                    drawArc(Color.White.copy(alpha = 0.4f), 200f, 60f, false, Offset(-18f, -18f), Size(36f, 36f), style = Stroke(2f, cap = StrokeCap.Round))
                    val vane = Path().apply { moveTo(-26f, 22f); quadraticTo(-96f, 6f, -150f, 86f); quadraticTo(-92f, 70f, -26f, 22f); close() }
                    drawPath(Path().apply { addPath(vane, Offset(5f, 7f)) }, Color.Black.copy(alpha = 0.12f))
                    drawPath(vane, Color(0xFFF8F1DF)); drawPath(vane, Color(0xFF8C7B66), style = Stroke(1f))
                    drawLine(Color(0xFF8C7B66), Offset(-4f, 6f), Offset(-150f, 86f), 1.6f)
                    for (k in 1..6) drawLine(Color(0xFFBBAE96), Offset(-20f - k * 18f, 16f + k * 9.5f), Offset(-34f - k * 17f, 14f + k * 6f), 0.8f)
                }
            }
            SceneTheme.PAPER -> when (i) {
                0 -> at(0.035f, 0.56f, rot = 6f) { // scissors on the cutting mat
                    for (s in floatArrayOf(-1f, 1f)) {
                        drawPath(poly(s * 3f, 14f, s * 12f, -84f, s * 4f, -88f, -s * 5f, 10f), Color.Black.copy(alpha = 0.14f).let { it })
                        drawPath(poly(s * 1f, 10f, s * 9f, -90f, s * 2f, -94f, -s * 6f, 6f), STEEL)
                        drawLine(Color.White.copy(alpha = 0.6f), Offset(s * 2f, 0f), Offset(s * 6f, -84f), 1f)
                        drawOval(Color(0xFF1FA39A), Offset(-s * 30f - 16f, 14f), Size(32f, 42f), style = Stroke(7f))
                    }
                    drawCircle(STEEL_DARK, 4f, Offset(0f, 2f))
                }
                1 -> at(0.965f, 0.42f) { // a glue stick
                    drawRoundRect(Color.Black.copy(alpha = 0.14f), Offset(-10f, -52f), Size(30f, 116f), CornerRadius(8f))
                    drawRoundRect(Color(0xFFF7F4EC), Offset(-15f, -58f), Size(30f, 116f), CornerRadius(8f))
                    drawRect(Color(0xFFFFE800), Offset(-15f, -26f), Size(30f, 52f))
                    drawRoundRect(Color(0xFFF2543D), Offset(-15f, -58f), Size(30f, 30f), CornerRadius(8f))
                    drawRect(Color.Black.copy(alpha = 0.14f), Offset(5f, -58f), Size(10f, 116f))
                }
                else -> at(0.36f, 0.972f) { // three colour pencils along the bottom of the mat
                    val cols = arrayOf(Color(0xFFFFE800), Color(0xFF0078BF), Color(0xFFF2543D))
                    for (k in 0 until 3) {
                        val y = (k - 1) * 11f; val x = k * 22f
                        drawRect(Color.Black.copy(alpha = 0.14f), Offset(x - 96f, y + 1f), Size(170f, 9f))
                        drawRect(cols[k], Offset(x - 100f, y - 4f), Size(160f, 8f)); drawRect(Color.Black.copy(alpha = 0.16f), Offset(x - 100f, y + 1f), Size(160f, 3f))
                        drawPath(poly(x + 60f, y - 4f, x + 60f, y + 4f, x + 80f, y), Color(0xFFE9CFA6)); drawPath(poly(x + 73f, y - 1.4f, x + 73f, y + 1.4f, x + 80f, y), cols[k])
                        drawRect(STEEL, Offset(x - 108f, y - 4f), Size(8f, 8f))
                    }
                }
            }
            SceneTheme.PIXEL -> {
                // Blocks on the pixel world's own grid.
                val c = max(1f, floor(min(size.width / 480f, size.height / 180f)))
                fun px(ox: Float, oy: Float, x: Int, y: Int, w: Int, h: Int, color: Color) = drawRect(color, Offset(ox + x * c, oy + y * c), Size(w * c, h * c))
                val pink = Color(0xFFE8508C); val cream = Color(0xFFFFF4D6); val lime = Color(0xFF8FD95B); val jade = Color(0xFF2FA37A); val ink = Color(0xFF2B1F3A)
                when (i) {
                    0 -> { // a line of pennants along the top
                        val cols = arrayOf(pink, cream, lime, jade)
                        val cells = (size.width / c).toInt()
                        px(0f, 0f, 0, 2, cells, 1, ink)
                        var x = 4; var k = 0
                        while (x < cells - 8) {
                            for (r in 0 until 4) px(0f, 0f, x + r, 3 + r, 7 - r * 2, 1, cols[k % 4])
                            x += 12; k++
                        }
                    }
                    1 -> { // a hot-air balloon
                        val ox = floor(size.width * 0.06f / c) * c; val oy = floor(size.height * 0.30f / c) * c
                        val rows = intArrayOf(5, 9, 11, 13, 13, 13, 13, 11, 9, 7, 5)
                        for ((r, wd) in rows.withIndex()) for (x in 0 until wd) px(ox, oy, 7 - wd / 2 + x, r, 1, 1, if (((7 - wd / 2 + x) / 2) % 2 == 0) pink else cream)
                        px(ox, oy, 5, 11, 1, 3, ink); px(ox, oy, 9, 11, 1, 3, ink)
                        px(ox, oy, 5, 14, 5, 3, Color(0xFF8A5A34)); px(ox, oy, 5, 14, 5, 1, Color(0xFFB98352))
                    }
                    else -> { // a flock of birds
                        val ox = floor(size.width * 0.48f / c) * c; val oy = floor(size.height * 0.46f / c) * c
                        for ((bx, by) in listOf(0 to 4, 9 to 1, 18 to 5, 26 to 0, 34 to 3)) {
                            px(ox, oy, bx, by + 1, 1, 1, ink); px(ox, oy, bx + 1, by, 1, 1, ink); px(ox, oy, bx + 2, by + 1, 1, 1, ink); px(ox, oy, bx + 3, by, 1, 1, ink); px(ox, oy, bx + 4, by + 1, 1, 1, ink)
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

/** Draws the props of [mask] over the content ([alpha] fades them, for example while the scene shrinks into its corner). */
fun Modifier.themeDecor(theme: SceneTheme, mask: Int, alpha: () -> Float = { 1f }): Modifier {
    if (mask == 0 || !ThemeDecor.has(theme)) return this
    return drawWithContent {
        drawContent()
        ThemeDecor.draw(this, theme, mask, alpha())
    }
}
