package io.vroomi.character.render.calibre

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.calibre.CalibreTone

/** A metal: base, the lit highlight and the shadow side (the finish colour of the chosen finish). */
internal class Metal(val base: Color, val light: Color, val dark: Color) {
    fun mix(o: Metal, t: Float) = Metal(lerp(base, o.base, t), lerp(light, o.light, t), lerp(dark, o.dark, t))
}

internal class CalibrePalette(
    val plate: Metal,
    val bridge: Metal,
    val rotor: Metal,
    val wheel: Metal,
    val case: Metal,
    val engrave: Color,
    val dial: Metal,
    val dialPrint: Color,
    val hand: Metal,
    /** Plain dial finish of the first two steps: 0 sunray brushing, 1 opaline (a fine matte). */
    val guilloche: Int,
)

internal object CalPal {
    val yellowGold = Metal(Color(0xFFC6A15E), Color(0xFFF2E1B0), Color(0xFF6A5127))
    val roseGold = Metal(Color(0xFFC48D76), Color(0xFFF1D1C1), Color(0xFF6A3F30))
    val rhodium = Metal(Color(0xFFB4B9C0), Color(0xFFF1F3F6), Color(0xFF5A616B))
    /** The case of the gold finishes: polished steel, so the finish colour stays on the rotor, wheels and hands. */
    val steelCase = Metal(Color(0xFFA9AEB5), Color(0xFFF2F4F7), Color(0xFF41464E))
    val plateSilver = Metal(Color(0xFF9EA3AA), Color(0xFFE2E5E9), Color(0xFF5F646C))
    val dlc = Metal(Color(0xFF2B2D32), Color(0xFF70747D), Color(0xFF0D0E11))
    val dlcPlate = Metal(Color(0xFF3A3D43), Color(0xFF7D828B), Color(0xFF17181B))
    val blued = Metal(Color(0xFF2E54B0), Color(0xFF9DB9F2), Color(0xFF11224F))
    /** Plain polished steel screws (the first step, and the case back's screws at every step). */
    val steelScrew = Metal(Color(0xFFAEB3BA), Color(0xFFF4F6F8), Color(0xFF50565E))
    val blackSteel = Metal(Color(0xFF1C1E23), Color(0xFFD9DDE3), Color(0xFF07080A))
    val ruby = Metal(Color(0xFFB0233C), Color(0xFFFF9AAB), Color(0xFF4A0816))
    val lume = Color(0xFFA9EBD2)
    /** Lume by day: a pale cream-green, it only glows at night. */
    val lumeDay = Color(0xFFE3EADC)
    val lumeGlow = Color(0xFF7FE7C2)
    val hairspring = Color(0xFF8C98AD)
    val coldLight = Color(0xFFDCE8FF)
    val warmLight = Color(0xFFFFE2BC)
    val gunmetal = Metal(Color(0xFF4A4E55), Color(0xFF9EA3AB), Color(0xFF1C1E22))
    /** Night: the lume glows green-blue under the warm desk lamp. */
    val lumeNight = Color(0xFF8DF2DC)
    val lumeHalo = Color(0xFF3FD6C0)

    // ---- the watchmaker's bench (a bright matte studio)
    /** Ivory at the top of the bench, warm grey toward the front edge. */
    val benchTop = Color(0xFFF3EFE8)
    val benchMid = Color(0xFFEAE4DA)
    val benchBottom = Color(0xFFDDD6CB)
    /** The colour of every shadow on the bench (a warm, slightly brown dark, never pure black). */
    val benchShadow = Color(0xFF3A3027)
    /** The soft fall-off at the bench's edges. */
    val benchEdge = Color(0xFF8A7E6E)
    /** Soft out-of-focus props: a satin aluminium loupe and steel tweezers. */
    val propMetal = Color(0xFFB9B4AB)
    val propDark = Color(0xFF8E887E)
    /** The openworked steps' plate: dark ruthenium grey, so the bright bevelled bridges and the wheels stand out. */
    val ruthenium = Metal(Color(0xFF4E535B), Color(0xFF8F959E), Color(0xFF2C3036))
    /** Through an opening in the movement: the dial's underside, far below (a dark slate, softly lit). */
    val depth = Metal(Color(0xFF2E333B), Color(0xFF59616C), Color(0xFF1A1D22))

    /** Night: the bench under a warm desk lamp (multiplied over the day scene): the lamp's pool, then its fall-off. */
    val lampCentre = Color(0xFFFFF1DE)
    val lampMid = Color(0xFFDCC6AA)
    val lampEdge = Color(0xFFA58F78)

    fun of(t: CalibreTone): CalibrePalette = when (t) {
        CalibreTone.YELLOW_GOLD -> CalibrePalette(
            plate = plateSilver, bridge = rhodium, rotor = yellowGold, wheel = yellowGold, case = steelCase,
            engrave = Color(0xFFD9B970),
            dial = Metal(Color(0xFF15233F), Color(0xFF34507F), Color(0xFF0A1122)), dialPrint = Color(0xFFEDE4CF),
            hand = yellowGold, guilloche = 0,
        )
        CalibreTone.ROSE_GOLD -> CalibrePalette(
            plate = plateSilver, bridge = rhodium, rotor = roseGold, wheel = roseGold, case = steelCase,
            engrave = Color(0xFFE2B49E),
            dial = Metal(Color(0xFFD7D3CB), Color(0xFFF6F3EE), Color(0xFF9F9A90)), dialPrint = Color(0xFF2C2A30),
            hand = roseGold, guilloche = 1,
        )
        CalibreTone.RHODIUM_BLUE -> CalibrePalette(
            plate = Metal(Color(0xFF6F757E), Color(0xFFB9BEC6), Color(0xFF3B4048)), bridge = rhodium, rotor = rhodium, wheel = blued,
            case = Metal(Color(0xFF9EA4AC), Color(0xFFEDEFF2), Color(0xFF4F555E)),
            engrave = Color(0xFF3F67C2),
            dial = Metal(Color(0xFF1D3E86), Color(0xFF4C74C9), Color(0xFF0D1E47)), dialPrint = Color(0xFFF1F3F7),
            hand = rhodium, guilloche = 0,
        )
        CalibreTone.BLACK_DLC -> CalibrePalette(
            plate = dlcPlate, bridge = dlc, rotor = dlc, wheel = yellowGold, case = dlc,
            engrave = Color(0xFFD4B26A),
            dial = Metal(Color(0xFFE2A68C), Color(0xFFF6CDB9), Color(0xFFA9715B)), dialPrint = Color(0xFF2A1F1C),
            hand = yellowGold, guilloche = 1,
        )
    }
}

internal fun Color.a(alpha: Float): Color = copy(alpha = (this.alpha * alpha).coerceIn(0f, 1f))
