package io.vroomi.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Vroomi's own icons, drawn in code on a 24-unit grid with 1.9-unit round strokes (no icon pack, nothing traced):
 * simple line drawings with a 2-unit margin, optically centred. A few small marks (play, stop, star) are filled.
 * Tinted by the caller.
 */
object VIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        }.build()

    private fun filled(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        }.build()

    /** A rounded rectangle as path commands (corners as cubic quarter circles). */
    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        val k = rad * 0.5523f
        moveTo(l + rad, t)
        lineTo(r - rad, t); curveTo(r - rad + k, t, r, t + rad - k, r, t + rad)
        lineTo(r, b - rad); curveTo(r, b - rad + k, r - rad + k, b, r - rad, b)
        lineTo(l + rad, b); curveTo(l + rad - k, b, l, b - rad + k, l, b - rad)
        lineTo(l, t + rad); curveTo(l, t + rad - k, l + rad - k, t, l + rad, t)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        val k = r * 0.5523f
        moveTo(cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        close()
    }

    /** Two strokes crossing at the optical centre (slightly shorter than the box so it does not look heavy). */
    val Close: ImageVector = icon("close") {
        moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
    }

    /** Chevrons sit 0.75 unit towards their point: their optical centre is not their box centre. */
    val ChevronLeft: ImageVector = icon("chevron_left") {
        moveTo(14.75f, 5.5f); lineTo(8.25f, 12f); lineTo(14.75f, 18.5f)
    }

    val ChevronRight: ImageVector = icon("chevron_right") {
        moveTo(9.25f, 5.5f); lineTo(15.75f, 12f); lineTo(9.25f, 18.5f)
    }

    val ChevronDown: ImageVector = icon("chevron_down") {
        moveTo(5.5f, 9.25f); lineTo(12f, 15.75f); lineTo(18.5f, 9.25f)
    }

    // ---------------------------------------------------------------- the rail

    /** A house: a pitched roof over a body with a door. */
    val Home: ImageVector = icon("home") {
        moveTo(3.5f, 11.2f); lineTo(12f, 4.2f); lineTo(20.5f, 11.2f)
        moveTo(6f, 9.4f); lineTo(6f, 19.5f); lineTo(18f, 19.5f); lineTo(18f, 9.4f)
        moveTo(10.2f, 19.5f); lineTo(10.2f, 14.6f); lineTo(13.8f, 14.6f); lineTo(13.8f, 19.5f)
    }

    /** A framed landscape: the worlds (themes). */
    val Worlds: ImageVector = icon("worlds") {
        roundRect(3.5f, 5f, 20.5f, 19f, 2.6f)
        moveTo(6f, 16.4f); lineTo(10f, 12.2f); lineTo(13f, 15.2f); lineTo(15f, 13.2f); lineTo(18f, 16.4f)
        circle(15.6f, 9.2f, 1.4f)
    }

    /** A notebook with a spine and two ruled lines. */
    val Diary: ImageVector = icon("diary") {
        roundRect(5.5f, 3.5f, 18.5f, 20.5f, 2.2f)
        moveTo(9f, 3.5f); lineTo(9f, 20.5f)
        moveTo(11.8f, 9f); lineTo(15.4f, 9f)
        moveTo(11.8f, 12.5f); lineTo(15.4f, 12.5f)
    }

    /** A coat hanger. */
    val Wardrobe: ImageVector = icon("wardrobe") {
        moveTo(10.1f, 5.9f)
        curveTo(10.1f, 4.8f, 11f, 4f, 12f, 4f)
        curveTo(13.1f, 4f, 13.9f, 4.8f, 13.9f, 5.8f)
        curveTo(13.9f, 7.1f, 12f, 7.4f, 12f, 8.8f)
        lineTo(12f, 9.4f)
        lineTo(4.2f, 15.6f)
        curveTo(3.3f, 16.3f, 3.8f, 17.8f, 5f, 17.8f)
        lineTo(19f, 17.8f)
        curveTo(20.2f, 17.8f, 20.7f, 16.3f, 19.8f, 15.6f)
        lineTo(12f, 9.4f)
    }

    /** Three sliders: the settings. */
    val Settings: ImageVector = icon("settings") {
        moveTo(4f, 7f); lineTo(7f, 7f); moveTo(11.4f, 7f); lineTo(20f, 7f)
        circle(9.2f, 7f, 2.2f)
        moveTo(4f, 12f); lineTo(12.6f, 12f); moveTo(17f, 12f); lineTo(20f, 12f)
        circle(14.8f, 12f, 2.2f)
        moveTo(4f, 17f); lineTo(5.8f, 17f); moveTo(10.2f, 17f); lineTo(20f, 17f)
        circle(8f, 17f, 2.2f)
    }

    // ---------------------------------------------------------------- actions

    /** A play mark, filled (its optical centre sits 0.8 unit right of the box centre). */
    val Play: ImageVector = filled("play") {
        moveTo(8.4f, 5.6f); lineTo(18.6f, 12f); lineTo(8.4f, 18.4f); close()
    }

    /** A stop mark, filled. */
    val Stop: ImageVector = filled("stop") { roundRect(7f, 7f, 17f, 17f, 2.2f) }

    /** A microphone. */
    val Mic: ImageVector = icon("mic") {
        roundRect(9f, 3.5f, 15f, 13.5f, 3f)
        moveTo(6f, 11.2f); curveTo(6f, 14.6f, 8.7f, 17.2f, 12f, 17.2f); curveTo(15.3f, 17.2f, 18f, 14.6f, 18f, 11.2f)
        moveTo(12f, 17.2f); lineTo(12f, 20.5f)
    }

    /** A speaker with two waves. */
    val Speaker: ImageVector = icon("speaker") {
        moveTo(4f, 9.6f); lineTo(7.4f, 9.6f); lineTo(11.6f, 5.8f); lineTo(11.6f, 18.2f); lineTo(7.4f, 14.4f); lineTo(4f, 14.4f); close()
        moveTo(15.2f, 9.2f); curveTo(16.6f, 10.7f, 16.6f, 13.3f, 15.2f, 14.8f)
        moveTo(17.8f, 6.8f); curveTo(20.6f, 9.6f, 20.6f, 14.4f, 17.8f, 17.2f)
    }

    /** A speaker, crossed out. */
    val SpeakerOff: ImageVector = icon("speaker_off") {
        moveTo(4f, 9.6f); lineTo(7.4f, 9.6f); lineTo(11.6f, 5.8f); lineTo(11.6f, 18.2f); lineTo(7.4f, 14.4f); lineTo(4f, 14.4f); close()
        moveTo(15.4f, 9.6f); lineTo(20.2f, 14.4f)
        moveTo(20.2f, 9.6f); lineTo(15.4f, 14.4f)
    }

    /** Two gentle waves: calm mode. */
    val Calm: ImageVector = icon("calm") {
        moveTo(4f, 9.4f); curveTo(6.7f, 7.4f, 9.3f, 7.4f, 12f, 9.4f); curveTo(14.7f, 11.4f, 17.3f, 11.4f, 20f, 9.4f)
        moveTo(4f, 14.6f); curveTo(6.7f, 12.6f, 9.3f, 12.6f, 12f, 14.6f); curveTo(14.7f, 16.6f, 17.3f, 16.6f, 20f, 14.6f)
    }

    /** A check mark. */
    val Check: ImageVector = icon("check") {
        moveTo(5.5f, 12.6f); lineTo(10f, 17f); lineTo(18.5f, 7.6f)
    }

    /** A five-point star, filled (the bond badge's star count). */
    val Star: ImageVector = filled("star") {
        val cx = 12f; val cy = 12.6f; val ro = 8.6f; val ri = 3.7f
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0)
            val r = if (i % 2 == 0) ro else ri
            val x = cx + (r * Math.cos(a)).toFloat(); val y = cy + (r * Math.sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** All icons (the icon test checks each stays inside its 2-unit margin). */
    val all: List<ImageVector> get() = listOf(Close, ChevronLeft, ChevronRight, ChevronDown, Home, Worlds, Diary, Wardrobe, Settings, Play, Stop, Mic, Speaker, SpeakerOff, Calm, Check, Star)
}
