package io.vroomi.character.render.paper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

/**
 * The pop-up book's own palette: three risograph spot inks (fluorescent pink, medium blue, yellow) printed on
 * off-white card and kraft, a blue cutting mat, and the warm desk-lamp night. Inks are always laid down with a
 * multiply blend, so where two inks overlap they make a third colour (pink + blue = purple, blue + yellow = green,
 * pink + yellow = red-orange), and each ink plate sits slightly off register.
 */
internal object PaperInk {
    /** The second ink of the current upgrade step ([use]): warm red, or the special edition's fluorescent pink. */
    var PINK = Color(0xFFFF48B0)
    val FLUO = Color(0xFFFF48B0)
    val RED = Color(0xFFF2543D)
    /** Pink ink under the desk lamp at night. */
    val VIOLET = Color(0xFF6A2C9A)
    val BLUE = Color(0xFF0078BF)
    /** The third ink ([use]): yellow from step 2; at step 1 (two inks) a pale tint of the red stands in its place. */
    var YELLOW = Color(0xFFFFE800)
    val YELLOW_INK = Color(0xFFFFE800)
    val RED_TINT = Color(0xFFFAC3B4)
    /** Gold foil (step 5). */
    val GOLD = Color(0xFFCFA43A)
    val GOLD_LIGHT = Color(0xFFF7E6A6)

    /** Sets the inks of an upgrade step: 1 blue + red, 2..4 blue + red + yellow, 5 blue + fluorescent pink + yellow. */
    fun use(model: Int) {
        PINK = if (model >= 4) FLUO else RED
        YELLOW = if (model >= 1) YELLOW_INK else RED_TINT
    }

    val CARD = Color(0xFFF8F3E7)
    val CORE = Color(0xFFFFFFFF)
    val PAGE = Color(0xFFF3EDDD)
    val PAGE_EDGE = Color(0xFFDCD3BF)
    val KRAFT = Color(0xFFC9A074)
    val KRAFT_DARK = Color(0xFFAE845C)

    /** The fluorescent-pink cutting mat, its grid, and the same mat in the dark at night. */
    val MAT = Color(0xFFE65AA0)
    val MAT_LINE = Color(0xFFF07DB8)
    val MAT_MAJOR = Color(0xFFF7A2CC)
    val MAT_DARK_LINE = Color(0xFF55307E)
    val MAT_DARK_MAJOR = Color(0xFF66408E)
    /** The blue cloth of the cover boards. */
    val CLOTH = Color(0xFF1E5DA6)
    val CLOTH_EDGE = Color(0xFF184C8A)

    /** Flat cast shadows, multiplied onto whatever is below. */
    val SHADOW_DAY = Color(0xFFB4B0C8)
    val SHADOW_NIGHT = Color(0xFF9C7E66)
    val CREASE = Color(0xFF9A9384)
    val BRAD = Color(0xFF8E8C98)
    val THREAD = Color(0xFF6E665A)

    /** The desk lamp at night: a warm pool falling off to deep violet (multiplied over the whole scene). */
    val LAMP = Color(0xFFF6DDB0)
    val LAMP_MID = Color(0xFFB08A86)
    val LAMP_EDGE = Color(0xFF241840)
    /** The cutting mat in the dark around the lamp's pool. */
    val MAT_DARK = Color(0xFF3A1C5C)

    /** Each ink plate's fixed misregistration (design units). */
    val REG_PINK = Offset(2.2f, -1.5f)
    val REG_BLUE = Offset(-1.8f, 1.9f)
    val REG_YELLOW = Offset(1.1f, 2.4f)

    fun pink(night: Float): Color = androidx.compose.ui.graphics.lerp(PINK, VIOLET, night.coerceIn(0f, 1f))
}

/**
 * Heavy cut-out capitals, drawn as polygons (no font files): chunky letters as if cut from card with scissors.
 * Glyphs live on a 10 × 14 grid (y down); holes use the even-odd rule.
 */
internal object CutType {
    const val H = 14f
    private val cache = HashMap<Char, Path>()

    fun glyph(ch: Char): Path = cache.getOrPut(ch) { build(ch) }

    fun advance(ch: Char): Float = when (ch) { '-' -> 8f; ' ' -> 6f; else -> 12f }

    private fun build(ch: Char): Path {
        val p = Path().apply { fillType = PathFillType.EvenOdd }
        fun poly(vararg xy: Float) {
            p.moveTo(xy[0], xy[1])
            var i = 2
            while (i < xy.size) { p.lineTo(xy[i], xy[i + 1]); i += 2 }
            p.close()
        }
        when (ch) {
            'O' -> { poly(0f, 2f, 2f, 0f, 8f, 0f, 10f, 2.5f, 10f, 12f, 8f, 14f, 2f, 14f, 0f, 11.5f); poly(4f, 4f, 6f, 4f, 6f, 10f, 4f, 10f) }
            'P' -> { poly(0f, 0f, 8f, 0f, 10f, 2f, 10f, 7f, 8f, 9f, 4.2f, 9f, 4.2f, 14f, 0f, 14f); poly(4.2f, 3f, 6f, 3f, 6f, 6f, 4.2f, 6f) }
            'U' -> poly(0f, 0f, 4.2f, 0f, 4.2f, 10f, 5.8f, 10f, 5.8f, 0f, 10f, 0f, 10f, 12f, 8f, 14f, 2f, 14f, 0f, 12f)
            'R' -> { poly(0f, 0f, 8f, 0f, 10f, 2f, 10f, 6.5f, 8.2f, 8f, 10.4f, 14f, 6f, 14f, 4.6f, 9f, 4.2f, 9f, 4.2f, 14f, 0f, 14f); poly(4.2f, 3f, 6f, 3f, 6f, 6f, 4.2f, 6f) }
            'A' -> { poly(0f, 14f, 3f, 0f, 7f, 0f, 10.2f, 14f, 6.2f, 14f, 5.7f, 11f, 4.3f, 11f, 3.8f, 14f); poly(4.6f, 8f, 5.4f, 8f, 5f, 4.6f) }
            'D' -> { poly(0f, 0f, 7f, 0f, 10f, 3f, 10f, 11f, 7f, 14f, 0f, 14f); poly(4.2f, 4f, 5.4f, 4f, 6f, 5f, 6f, 9f, 5.4f, 10f, 4.2f, 10f) }
            '-' -> poly(0f, 6f, 6f, 5.6f, 6f, 8.4f, 0f, 8f)
            else -> Unit
        }
        return p
    }
}
