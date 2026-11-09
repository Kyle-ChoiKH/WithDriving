package io.vroomi.character.render.plasma

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.vroomi.character.plasma.PlasmaSim
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.character.render.theme.drawTextCentered
import kotlin.math.max

/**
 * PLASMA, the skeleton scene: the dark blue-black lab (#0E1016 → #1A1C26), the bench edge, a still glass globe on its
 * black base and the theme's name. No motion and no pulses yet; the theme's own task draws the real globe, its
 * filaments and the instruments of each upgrade step ([ThemeFrame.model]).
 */
internal class PlasmaPainter : ThemePainter<PlasmaSim>() {
    override val minDesignWidth = 1000f
    override val backdrop = Color(0xFF0E1016)

    private val room = Brush.verticalGradient(listOf(Color(0xFF0E1016), Color(0xFF1A1C26)), 0f, 600f)
    private val rim = Stroke(2f)

    override fun draw(s: DrawScope, sim: PlasmaSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        val w = f.dw; val h = f.dh
        val lum = 1f - 0.2f * sim.night
        drawRect(room, size = Size(w, h))
        // The bench: a slightly lighter top and its faint front edge.
        val bench = h * 0.80f
        drawRect(Color(0xFF15171F), Offset(0f, bench), Size(w, h - bench))
        drawRect(Color.White.copy(alpha = 0.06f), Offset(0f, bench), Size(w, 1.5f))
        // The globe on its base (still).
        val c = Offset(f.cx, h * 0.47f)
        val r = 150f
        drawRect(Color(0xFF07080B), Offset(c.x - 70f, c.y + r - 12f), Size(140f, bench - (c.y + r - 12f)))
        drawCircle(Brush.radialGradient(listOf(GLOW.copy(alpha = 0.38f * lum), GLOW.copy(alpha = 0f)), c, r), r, c)
        drawCircle(Color(0xFFCDBEF2).copy(alpha = 0.35f * lum), r, c, style = rim)
        drawCircle(Color(0xFFF4ECFF).copy(alpha = 0.9f * lum), 10f, c)
        // The name, top centre (clear of the app's corners), at 24 sp or more.
        val px = max(40f, 24f * f.density.density * f.density.fontScale / f.k)
        drawTextCentered(tx.get(NAME, px, Color(0xFFE6DDF5)), f.cx, 70f, alpha = lum)
    }

    private companion object {
        const val NAME = "플라즈마"
        val GLOW = Color(0xFFB45CFF)
    }
}
