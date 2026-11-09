package io.vroomi.character.render.hangar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.vroomi.character.hangar.HangarSim
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.character.render.theme.drawTextCentered
import kotlin.math.max

/**
 * HANGAR, the skeleton scene: the dark hangar (#1B1D20), a concrete floor with one painted line, two gantry columns
 * and their beam, and the theme's name. No robot yet; the theme's own task draws the bay, the HX frame, its assembly
 * and the armour of each upgrade step ([ThemeFrame.model]).
 */
internal class HangarPainter : ThemePainter<HangarSim>() {
    override val minDesignWidth = 1000f
    override val backdrop = Color(0xFF1B1D20)

    private val hall = Brush.verticalGradient(listOf(Color(0xFF16181B), Color(0xFF1B1D20)), 0f, 420f)
    private val floor = Brush.verticalGradient(listOf(Color(0xFF26282B), Color(0xFF2F3134)), 420f, 600f)

    override fun draw(s: DrawScope, sim: HangarSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        val w = f.dw; val h = f.dh
        val lum = 1f - 0.2f * sim.night
        val floorY = h * 0.70f
        drawRect(hall, size = Size(w, floorY))
        drawRect(floor, Offset(0f, floorY), Size(w, h - floorY))
        // One painted floor line in signal orange.
        drawRect(LINE.copy(alpha = 0.55f * lum), Offset(0f, floorY + (h - floorY) * 0.55f), Size(w, 4f))
        // Two gantry columns and the beam between them (graphite steel).
        val gap = 260f
        for (x in floatArrayOf(f.cx - gap, f.cx + gap - 26f)) drawRect(STEEL, Offset(x, 120f), Size(26f, floorY - 120f))
        drawRect(STEEL, Offset(f.cx - gap, 120f), Size(gap * 2f, 18f))
        drawRect(Color.White.copy(alpha = 0.05f), Offset(f.cx - gap, 120f), Size(gap * 2f, 1.5f))
        // The name, top centre (clear of the app's corners), at 24 sp or more.
        val px = max(40f, 24f * f.density.density * f.density.fontScale / f.k)
        drawTextCentered(tx.get(NAME, px, Color(0xFFE4E1DC)), f.cx, 70f, alpha = lum)
    }

    private companion object {
        const val NAME = "로봇 격납고"
        val LINE = Color(0xFFF07A1A)
        val STEEL = Color(0xFF3A3D42)
    }
}
