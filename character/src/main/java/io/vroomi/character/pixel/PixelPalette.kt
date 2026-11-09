package io.vroomi.character.pixel

/**
 * Pixel World's own fixed 16-colour master palette (ARGB): dusk violets, magenta, teal-green, sand and charcoal.
 * Every look of the world (day, dusk, eco, night, rain) is a remap table over these same 16 colours, the classic
 * palette swap: no mood ever adds a colour, and night is a true swap of the same sprites and tiles, not an overlay.
 */
object PixelColors {
    const val INK = 0
    const val NIGHT_VIOLET = 1
    const val DUSK = 2
    const val LAVENDER = 3
    const val LILAC = 4
    const val CREAM = 5
    const val SAND = 6
    const val OCHRE = 7
    const val RUST = 8
    const val MAGENTA = 9
    const val PINK = 10
    const val DEEP_JADE = 11
    const val JADE = 12
    const val LIME = 13
    const val SLATE = 14
    const val MIST = 15

    val MASTER: IntArray = intArrayOf(
        0xFF120F1A.toInt(), // ink: charcoal black
        0xFF2B2142.toInt(), // night violet
        0xFF4E3B7A.toInt(), // dusk violet
        0xFF8C6BBF.toInt(), // lavender
        0xFFCDB9EC.toInt(), // pale lilac
        0xFFFFF4E0.toInt(), // cream white
        0xFFF2D49B.toInt(), // sand
        0xFFC8935A.toInt(), // ochre sand
        0xFF8E4B3A.toInt(), // canyon rust
        0xFFB8306E.toInt(), // magenta
        0xFFF27BAA.toInt(), // pink
        0xFF1C5550.toInt(), // deep teal-green
        0xFF3A9C72.toInt(), // teal-green
        0xFF9ED67A.toInt(), // lime
        0xFF4A4A5E.toInt(), // charcoal slate
        0xFF8D8BA6.toInt(), // grey lilac mist
    )
}

/** One mood of the world: each master index is shown as the master colour its remap entry names. */
enum class PixelPalette(private val remap: IntArray) {
    DAY(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)),
    /** SPORT: the sky turns magenta and pink. */
    SUNSET(intArrayOf(0, 1, 2, 9, 10, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)),
    /** ECO: a pale fresh sky, and ochre and rust ground turn teal-green. */
    MEADOW(intArrayOf(0, 1, 2, 4, 5, 5, 6, 12, 11, 9, 10, 11, 12, 13, 14, 15)),
    /** Night: every colour steps down its own ramp; magenta stays (the rover and the neon). */
    NIGHT(intArrayOf(0, 0, 1, 1, 2, 15, 3, 2, 1, 9, 9, 0, 11, 12, 1, 14)),
    /** Rain: grey-violet drizzle. */
    RAIN(intArrayOf(0, 1, 2, 14, 15, 4, 15, 14, 14, 3, 4, 11, 11, 12, 14, 15));

    /** The 16 colours of this mood (all taken from [PixelColors.MASTER]). */
    val hex: IntArray = IntArray(16) { PixelColors.MASTER[remap[it]] }

    fun color(index: Int): Int = hex[index]

    /** An emissive colour (lamps, neon, sparkles, stars): the master colour, the same in every mood. */
    fun lit(index: Int): Int = PixelColors.MASTER[index]

    companion object {
        /** The flat sky blue of a famous platformer: never used (tested). */
        const val FORBIDDEN_SKY = 0xFF5C94FC.toInt()

        /** A palette character ('0'..'9', 'a'..'f') → index, or −1 for '.' (transparent). */
        fun index(ch: Char): Int = when (ch) { in '0'..'9' -> ch - '0'; in 'a'..'f' -> ch - 'a' + 10; else -> -1 }
    }
}
