package io.vroomi.app.ui.design

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale: six roles carried by weight, size and spacing (the device font, no bundled files), and the
 * driving roles. Hangul crowds when tracked tightly, so nothing goes tighter than -1 %; small text opens up by
 * +1 %. Line heights sit on the 4 dp grid and are trimmed at the top and bottom, so a label is optically centred
 * in its box. Numbers use tabular figures.
 *
 * Parked roles may go down to 18 sp. Anything visible while moving uses a [drive] role (24 sp and up).
 */
object VType {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
    private val platform = PlatformTextStyle(includeFontPadding = false)
    /** Korean wraps between words (phrases), never inside one, where the platform supports it. */
    private val breaks = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Normal, LineBreak.WordBreak.Phrase)
    private fun role(size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
        fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.em,
        lineHeightStyle = trim, platformStyle = platform, lineBreak = breaks,
    )

    /** Page titles (테마, 설정) and the stage's theme name. */
    val largeTitle = role(40, 48, FontWeight.SemiBold, -0.01)
    /** Sheet and card titles. */
    val title = role(30, 40, FontWeight.SemiBold, -0.005)
    /** Row titles, card names, button labels. */
    val headline = role(24, 32, FontWeight.SemiBold, 0.0)
    /** Running text. */
    val body = role(22, 32, FontWeight.Normal, 0.0)
    /** Subtitles, hints and footnotes. */
    val callout = role(20, 28, FontWeight.Normal, 0.005)
    /** Section headers, rail labels and small tags (parked only). */
    val caption = role(18, 24, FontWeight.Medium, 0.01)

    /** Driving: big toggles, the demo caption (24 sp, the floor while moving). */
    val drive = role(24, 32, FontWeight.SemiBold, 0.0)
    /** Driving: running text of the demo caption. */
    val driveBody = role(24, 32, FontWeight.Normal, 0.0)
    /** Driving: the status chip. */
    val driveChip = role(26, 32, FontWeight.SemiBold, 0.0)

    /** Big numbers (the trip score). */
    val number = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", lineHeightStyle = trim, platformStyle = platform)
    val tabular = TextStyle(fontFeatureSettings = "tnum")

    /** The smallest text size while moving (sp). */
    const val MOVING_FLOOR_SP = 24f
    /** The smallest text size while parked (sp). */
    const val PARKED_FLOOR_SP = 18f

    /** Every role that can be shown while moving. */
    val movingRoles: List<TextStyle> get() = listOf(drive, driveBody, driveChip)
    val parkedRoles: List<TextStyle> get() = listOf(largeTitle, title, headline, body, callout, caption)
}
