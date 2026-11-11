package io.vroomi.app.ui.design

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.vroomi.character.calibre.SceneTheme

/**
 * Each theme's chrome colours, taken from its own palette: [tint] is the theme's signature colour, [backdrop] the
 * letterbox, launch and stage-edge colour. [luminance] is the theme's mean relative luminance as measured from its
 * still (app/src/test/resources/theme_luminance.json; a test keeps the two in step).
 *
 * The interface never uses the raw tint. Its one accent is the tint tamed for UI use: chroma capped (a phosphor
 * cyan becomes a quiet sea green), lightness moved until it reads 4.5:1 on the light and on the dark surface, and
 * a near-neutral tint (brushed steel) becomes graphite in light mode and silver in dark mode, so a primary button
 * never looks disabled.
 */
class ThemeAmbience(val theme: SceneTheme, val tint: Int, val backdrop: Int, val luminance: Float) {
    /** The accent on the light surface and on the dark surface (4.8:1, so it still reads 4.5:1 on a static material). */
    val tintLight: Int = accent(tint, dark = false)
    val tintDark: Int = accent(tint, dark = true)
    fun tintFor(dark: Boolean): Int = if (dark) tintDark else tintLight
    /** Text on an accent fill. */
    fun onTint(dark: Boolean): Int = if (dark) Neutral.INK_DARK_ON_TINT else Neutral.INK_LIGHT_ON_TINT

    companion object {
        /** The accent's chroma cap and the chroma under which a tint counts as neutral. */
        const val ACCENT_CHROMA = 0.105f
        const val NEUTRAL_CHROMA = 0.035f

        fun accent(tint: Int, dark: Boolean): Int {
            val neutral = ColorMath.chroma(tint) < NEUTRAL_CHROMA
            // Yellows turn to olive and khaki when darkened: lean them to the nearer clean hue first.
            val base = if (neutral || dark) tint else ColorMath.leanFromYellow(tint)
            val tamed = when {
                neutral && !dark -> ColorMath.tame(base, 0.02f, maxL = 0.36f)
                neutral -> ColorMath.tame(base, 0.02f, minL = 0.82f)
                dark -> ColorMath.tame(base, 0.09f, minL = 0.70f, maxL = 0.78f)
                else -> ColorMath.tame(base, ACCENT_CHROMA, maxL = 0.56f)
            }
            val surface = if (dark) Neutral.SURFACE_DARK else Neutral.SURFACE_LIGHT
            val text = if (dark) Neutral.INK_DARK_ON_TINT else Neutral.INK_LIGHT_ON_TINT
            // Readable as text on the surface, and its fill carries its own label at 4.5:1.
            var c = ColorMath.ensureContrast(tamed, surface, if (dark) 4.8f else 5.0f)
            if (ColorMath.contrast(text, c) < 4.6f) c = ColorMath.ensureContrast(c, text, 4.6f)
            return c
        }
    }
}

object Ambience {
    private fun a(t: SceneTheme, tint: Long, backdrop: Long, lum: Float) = ThemeAmbience(t, tint.toInt(), backdrop.toInt(), lum)

    val all: List<ThemeAmbience> = listOf(
        a(SceneTheme.KART, 0xFF3CC8B4, 0xFF15262A, 0.52f),        // brand teal over a dusk-teal letterbox
        a(SceneTheme.PAPER, 0xFFE8478C, 0xFF231C3B, 0.439f),      // fluorescent pink ink, the night mat's violet
        a(SceneTheme.CALIBRE, 0xFFB7C0CA, 0xFF2B2520, 0.677f),    // brushed steel, the bench's warm shadow
        a(SceneTheme.ENGINE, 0xFFE0A02A, 0xFF14171A, 0.062f),     // oil amber, the graphite studio
        a(SceneTheme.MUSIC_BOX, 0xFF8E2F45, 0xFF241A14, 0.097f),   // burgundy velvet, the lamp-lit brown room
        a(SceneTheme.PLASMA, 0xFFB45CFF, 0xFF0E1016, 0.013f),     // plasma violet, the dark lab's blue-black
        a(SceneTheme.HANGAR, 0xFFF07A1A, 0xFF1B1D20, 0.054f),     // signal orange, the hangar's graphite
        a(SceneTheme.TERRARIUM, 0xFF9BC53D, 0xFF14201A, 0.284f),  // fresh leaf, moonlit moss
        a(SceneTheme.AQUARIUM, 0xFF2F8FD8, 0xFF081622, 0.320f),   // open-water blue, the deep
        a(SceneTheme.ORBIT, 0xFF7FF0FF, 0xFF020405, 0.003f),      // phosphor cyan, the display's black
        a(SceneTheme.TREASURE_MAP, 0xFFA8322D, 0xFF1E1812, 0.707f), // madder red, iron-gall ink
        a(SceneTheme.PIXEL, 0xFF9B6BE8, 0xFF120F1A, 0.305f),      // dusk violet, the night sky's ink
    )

    fun of(theme: SceneTheme): ThemeAmbience = all.firstOrNull { it.theme == theme } ?: all[0]
}

/**
 * The neutral ramps (a cool grey with the faintest blue, so every theme's accent sits on it without a cast), the
 * surfaces and the text tiers, for day and night.
 */
object Neutral {
    val RAMP: IntArray = intArrayOf(
        0xFFF6F7F8.toInt(), 0xFFEBEDEF.toInt(), 0xFFD7DADE.toInt(), 0xFFB9BDC3.toInt(), 0xFF8F949B.toInt(),
        0xFF6A6F76.toInt(), 0xFF4B5056.toInt(), 0xFF33373C.toInt(), 0xFF212428.toInt(), 0xFF131518.toInt(),
    )
    /** The base of every translucent material. */
    const val SURFACE_LIGHT = 0xFFF8F9FA.toInt()
    const val SURFACE_DARK = 0xFF1A1D21.toInt()
    /** Page backgrounds (gallery wall, settings) and the grouped sections on them. */
    const val BACKGROUND_LIGHT = 0xFFF1F2F4.toInt()
    const val BACKGROUND_DARK = 0xFF0E1013.toInt()
    const val GROUPED_LIGHT = 0xFFFFFFFF.toInt()
    const val GROUPED_DARK = 0xFF1B1E22.toInt()
    const val INK_LIGHT = 0xFF14171A.toInt()
    const val INK_DARK = 0xFFECEEF1.toInt()
    const val INK_LIGHT_ON_TINT = 0xFFFFFFFF.toInt()
    const val INK_DARK_ON_TINT = 0xFF0C0E10.toInt()
    /** Text tiers: secondary is the text colour at 64 %; tertiary (44 %) is for marks, never text; separators 10 %. */
    const val SECONDARY = 0.64f
    const val TERTIARY = 0.44f
    const val SEPARATOR = 0.10f
    /** Fills of quiet controls (a track, a pressed row, the rail's selection): the text colour at 6 % / 10 %. */
    const val FILL_LIGHT = 0.06f
    const val FILL_DARK = 0.10f
    /** Debug overlay text (developer builds only). */
    const val DEBUG_INK = 0xFFFFE08A.toInt()

    fun c(argb: Int) = Color(argb)
    fun ink(dark: Boolean): Int = if (dark) INK_DARK else INK_LIGHT
    fun surface(dark: Boolean): Int = if (dark) SURFACE_DARK else SURFACE_LIGHT
    fun background(dark: Boolean): Int = if (dark) BACKGROUND_DARK else BACKGROUND_LIGHT
    fun grouped(dark: Boolean): Int = if (dark) GROUPED_DARK else GROUPED_LIGHT
}

/** Semantic colours (scores, the reset action), each readable as text on its mode's surface. */
object Semantic {
    fun positive(dark: Boolean): Int = if (dark) 0xFF5CCB8F.toInt() else 0xFF177A4A.toInt()
    fun caution(dark: Boolean): Int = if (dark) 0xFFE9B04F.toInt() else 0xFF8A5A0E.toInt()
    fun critical(dark: Boolean): Int = if (dark) 0xFFF2867B.toInt() else 0xFFC0362C.toInt()
}

/**
 * The chrome colours of the moment, animated between themes in Oklab by the passage. Read [tint] and
 * [backdrop] in draw lambdas where possible: the values change every frame during a passage.
 */
@Stable
class ChromeColors(initial: ThemeAmbience, dark: Boolean) {
    var dark: Boolean = dark
    var tintArgb by mutableIntStateOf(initial.tintFor(dark))
    var backdropArgb by mutableIntStateOf(initial.backdrop)
    var onTintArgb by mutableIntStateOf(initial.onTint(dark))
    val tint: Color get() = Color(tintArgb)
    val backdrop: Color get() = Color(backdropArgb)
    val onTint: Color get() = Color(onTintArgb)

    /** Sets the mix between two themes (0 = [from], 1 = [to]). */
    fun mix(from: ThemeAmbience, to: ThemeAmbience, t: Float) {
        tintArgb = ColorMath.lerpOklab(from.tintFor(dark), to.tintFor(dark), t)
        backdropArgb = ColorMath.lerpOklab(from.backdrop, to.backdrop, t)
        onTintArgb = if (t < 0.5f) from.onTint(dark) else to.onTint(dark)
    }
}

val LocalChrome = staticCompositionLocalOf<ChromeColors> { ChromeColors(Ambience.of(SceneTheme.KART), false) }
