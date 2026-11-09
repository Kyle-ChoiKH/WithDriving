package io.vroomi.character.render.aquarium

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.aquarium.AquariumSim
import io.vroomi.character.aquarium.AquariumSim.Species
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.Intro
import io.vroomi.character.render.hash01
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.character.render.theme.drawNative
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * AQUARIUM, art direction: immersive underwater. The viewer is inside the water: no tank, no glass, no room.
 * The surface is a rippling silver line near the top that leans with the car's acceleration; below it the
 * water falls from bright turquoise to deep teal through three or four layers of depth fog, god rays slant
 * down from the surface, marine snow drifts, and the seafloor of sunlit sand recedes into the blue. Fish have
 * smooth gradient bodies with countershading (dark back, pale belly). Nothing is snappy; everything drags.
 */

/** The theme's private palette: turquoise to deep teal, sunlit sand; ink blue and plankton at night. */
private object AP {
    val shallow = Color(0xFF63D2CF)
    val upper = Color(0xFF2FA6B4)
    val middle = Color(0xFF167988)
    val deep = Color(0xFF0B4F60)
    val abyss = Color(0xFF073845)
    val fog = Color(0xFF1C8191)
    val mirror = Color(0xFFBDF1EC)
    val mirrorHigh = Color(0xFF86DCD9)
    val sand = Color(0xFFE6D3A4)
    val sandShade = Color(0xFFAE9A6E)
    val rock = Color(0xFF4E6F72)
    val rockLit = Color(0xFF8FB0A8)
    val rockDark = Color(0xFF1F3B42)
    val grass = Color(0xFF3E8D63)
    val grassLit = Color(0xFF7CC08A)
    val sunlight = Color(0xFFF4FFE8)
    val caustic = Color(0xFFFFFBDD)
    val night = Color(0xFF061430)
    val nightDeep = Color(0xFF020818)
    val plankton = Color(0xFF7DF7E1)
    val pearl = Color(0xFFF7F1E6)
    val nacre = Color(0xFFF2D9DE)
    val shell = Color(0xFFD9A98F)
    val shellDark = Color(0xFF9A6A58)
}

/** Countershaded body colours per species: back, flank, belly, fin. */
private class Skin(val back: Color, val flank: Color, val belly: Color, val fin: Color, val line: Color)

private val SKINS = mapOf(
    Species.STRIPE to Skin(Color(0xFF2B5D6E), Color(0xFFB4D5DB), Color(0xFFF4FAF6), Color(0xFFCDE6EA), Color(0xFF8AF0F2)),
    Species.ROWER to Skin(Color(0xFF8F6417), Color(0xFFF3C04A), Color(0xFFFFF0C6), Color(0xFF5E86D8), Color(0xFFFFE38A)),
    Species.RIBBON to Skin(Color(0xFF1F3F3D), Color(0xFF507F75), Color(0xFFD9D3AE), Color(0xFF6E9C8E), Color(0xFFE6E0BC)),
    Species.FLAT to Skin(Color(0xFF7C6A47), Color(0xFFB8A57A), Color(0xFFE7DCBE), Color(0xFFC9B68C), Color(0xFF5E4E32)),
    Species.SAIL to Skin(Color(0xFF332C63), Color(0xFF8678BE), Color(0xFFE6E0F5), Color(0xFFB7AEE3), Color(0xFFD6CCFF)),
    Species.SHRIMP to Skin(Color(0xFFD37C5A), Color(0xFFF0B293), Color(0xFFFBE2D2), Color(0xFFF5C3A8), Color(0xFFFFFFFF)),
)

private class AGeo(val dw: Float) {
    val cx = dw / 2f
    /** Water surface (design units) and the sim's tank mapped full-bleed. */
    val surface = 56f
    val sy = 1.12f
    val sx = ((dw - 140f) / AquariumSim.W).coerceIn(1.0f, 1.5f)
    val top = 70f
    fun tx(u: Float) = cx + (u - AquariumSim.W / 2f) * sx
    fun ty(v: Float) = top + v * sy
    val sandY = ty(AquariumSim.SAND_Y)
    /** Where the seafloor disappears into the blue. */
    val horizon = 430f

    val ray = Array(5) { k ->
        val x0 = cx - 700f + k * 330f + hash01(k, 3) * 80f
        val w0 = 22f + hash01(k, 4) * 40f
        val w1 = 70f + hash01(k, 5) * 80f
        Path().apply { moveTo(x0, surface); lineTo(x0 + w0, surface); lineTo(x0 + w0 + 150f + w1, 540f); lineTo(x0 + 150f, 540f); close() }
    }
    val rayBrush = Brush.verticalGradient(listOf(AP.sunlight.copy(alpha = 0.28f), AP.sunlight.copy(alpha = 0.08f), AP.sunlight.copy(alpha = 0f)), startY = surface, endY = 540f)
    val mirrorBrush = Brush.verticalGradient(listOf(AP.mirrorHigh, AP.mirror), startY = -90f, endY = surface)
    val snell = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), AP.mirror.copy(alpha = 0f)), Offset(cx - 220f, surface), 360f)
    val ecoTint = Brush.verticalGradient(listOf(Color(0xFF9CE3A6).copy(alpha = 0.18f), Color.Transparent), startY = 0f, endY = 600f)
    val sportShaft = Brush.linearGradient(listOf(Color(0xFFFFC27A).copy(alpha = 0.24f), Color.Transparent), Offset(dw, 0f), Offset(cx - 200f, 600f))
    val nightTint = Brush.verticalGradient(listOf(AP.night.copy(alpha = 0.70f), AP.nightDeep.copy(alpha = 0.86f)), startY = 0f, endY = 600f)
    val warmTint = Color(0xFFFFC27A)
    val coolTint = Color(0xFF8FD8FF)
    val glow = Brush.radialGradient(listOf(AP.plankton.copy(alpha = 0.6f), AP.plankton.copy(alpha = 0f)), Offset.Zero, 1f)
    val pearlBrush = Brush.radialGradient(listOf(Color.White, AP.pearl, Color(0xFFC9BFB2)), Offset(-0.3f, -0.35f), 1.3f)

    // Seagrass beds: base x (design units), height, depth layer (0 far .. 1 near).
    val grassX = FloatArray(18) { cx - dw * 0.52f + hash01(it, 11) * dw * 1.04f }
    val grassH = FloatArray(18) { 90f + hash01(it, 12) * 190f }
    val grassZ = FloatArray(18) { hash01(it, 13) }
    // Kelp stalks in the middle distance (base x, how far below the surface they stop).
    val kelpX = floatArrayOf(cx - dw * 0.38f, cx - dw * 0.31f, cx + dw * 0.12f, cx + dw * 0.36f, cx + dw * 0.42f)
    val kelpH = floatArrayOf(40f, 110f, 160f, 70f, 130f)
    // Marine snow.
    val snowX = FloatArray(80) { hash01(it, 21) * (dw + 100f) - 50f }
    val snowY = FloatArray(80) { hash01(it, 22) * 600f }
    val snowR = FloatArray(80) { 0.7f + hash01(it, 23).pow(3f) * 1.8f }
    val plankX = FloatArray(60) { hash01(it, 31) * dw }
    val plankY = FloatArray(60) { 80f + hash01(it, 32) * 470f }
}

/** The aquarium painter: inside the water, with no tank and no room. */
internal class AquariumPainter : ThemePainter<AquariumSim>() {
    override val minDesignWidth = 1100f
    override val backdrop = Color(0xFF062A33)

    private var geo: AGeo? = null
    private val base = LayerSlot(SceneTheme.AQUARIUM)
    private val body = Path()
    private val back = Path()
    private val fin = Path()
    private val wave = Path()
    private val ring = Stroke(1.4f)
    private val rippleStroke = Stroke(2.2f, cap = StrokeCap.Round)
    private val pt = FloatArray(9 * 2)
    /** Per-fish depth (0 near .. 1 far): smaller, foggier, drawn first. Fixed per index. */
    private val depth = FloatArray(AquariumSim.FISH) { hash01(it, 77) * 0.8f }
    private val order = (0 until AquariumSim.FISH).sortedByDescending { depth[it] }.toIntArray()
    /** Body gradients per species and depth-fog step (the far and the deep are mixed toward the water's colour). */
    private val brushes = arrayOfNulls<Brush>(Species.entries.size * FOG_STEPS)

    private fun geo(dw: Float): AGeo {
        val g = geo
        if (g != null && abs(g.dw - dw) < 0.5f) return g
        return AGeo(dw).also { geo = it }
    }

    /** The body gradient in the fish's own unit frame (y −half back .. +half belly), fogged by [step]. */
    private fun bodyBrush(sp: Species, step: Int): Brush {
        val i = sp.ordinal * FOG_STEPS + step
        brushes[i]?.let { return it }
        val s = SKINS.getValue(sp)
        val hw = maxHalf(sp)
        val fog = step / (FOG_STEPS - 1f) * 0.62f
        fun c(x: Color) = lerp(x, AP.fog, fog)
        return Brush.verticalGradient(0f to c(s.back), 0.38f to c(lerp(s.back, s.flank, 0.7f)), 0.55f to c(s.flank), 1f to c(s.belly), startY = -hw, endY = hw).also { brushes[i] = it }
    }

    override fun drawPx(s: DrawScope, sim: AquariumSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val g = geo(f.dw)
        val w = f.w.roundToInt(); val h = f.h.roundToInt(); val k = f.k
        val bmp = base.get((w.toLong() shl 20) xor h.toLong(), w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { staticLayer(g, f) } }
        s.drawImage(bmp, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(bmp.width, bmp.height))
    }

    override fun draw(s: DrawScope, sim: AquariumSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        val g = geo(f.dw)
        clipRect(0f, 0f, f.dw, f.dh) {
            kelp(g, sim)
            dimRays(g, sim, f)
            grass(g, sim, far = true)
            clam(g, sim)
            decor(g, sim)
            val intro = f.options.intro
            for (j in 0 until AquariumSim.FISH) {
                val i = order[j]
                if (intro < 1f) enterFish(g, sim, f, i, intro) else fish(g, sim, i)
            }
            grass(g, sim, far = false)
            bubbles(g, sim)
            snow(g, sim, f)
            surface(g, sim, f)
            tint(g, sim, f)
        }
    }

    // ------------------------------------------------------------------ static

    /** A ridge line across the frame (a far reef or a sand bank), filled down to the bottom. */
    private fun ridge(f: ThemeFrame, baseY: Float, amp: Float, seed: Int): Path = Path().apply {
        moveTo(-60f, f.dh + 10f)
        var x = -60f
        while (x <= f.dw + 60f) {
            val y = baseY - amp * (0.5f + 0.3f * sin(x * 0.006f + seed) + 0.2f * sin(x * 0.019f + seed * 2f)) - hash01((x / 40f).toInt(), seed) * amp * 0.25f
            lineTo(x, y)
            x += 20f
        }
        lineTo(f.dw + 60f, f.dh + 10f)
        close()
    }

    /** An irregular rock outline around (cx, by), [rw] wide, flat underneath, smoothed through midpoints. */
    private fun rock(cx: Float, by: Float, rw: Float, seed: Int): Path = Path().apply {
        val n = 11
        val xs = FloatArray(n); val ys = FloatArray(n)
        for (i in 0 until n) {
            val a = PI.toFloat() + i * PI.toFloat() / (n - 1)
            val r = 0.5f * (0.78f + 0.3f * hash01(i, seed))
            xs[i] = cx + cos(a) * rw * r
            ys[i] = by + sin(a) * rw * r * 0.62f
        }
        moveTo(xs[0], by)
        lineTo(xs[0], ys[0])
        for (i in 1 until n) quadraticTo(xs[i - 1], ys[i - 1], (xs[i - 1] + xs[i]) / 2f, (ys[i - 1] + ys[i]) / 2f)
        lineTo(xs[n - 1], ys[n - 1]); lineTo(xs[n - 1], by)
        close()
    }

    private fun DrawScope.staticLayer(g: AGeo, f: ThemeFrame) {
        // The water column: four bands of depth fog from the bright shallows to the deep.
        drawRect(Brush.verticalGradient(0f to AP.shallow, 0.18f to AP.upper, 0.45f to AP.middle, 0.72f to AP.deep, 1f to AP.abyss, startY = 0f, endY = f.dh), size = Size(f.dw, f.dh))
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0f)), startY = g.surface, endY = g.surface + 60f), Offset(0f, g.surface), Size(f.dw, 60f))
        // Fog layer 1: a far reef ridge, barely darker than the water.
        drawPath(ridge(f, g.horizon + 8f, 70f, 3), lerp(AP.middle, AP.fog, 0.5f).copy(alpha = 0.75f))
        // Fog layer 2: the seafloor recedes, sand brightening toward the viewer and fading into the blue.
        drawRect(Brush.verticalGradient(0f to lerp(AP.fog, AP.deep, 0.15f), 0.35f to lerp(AP.sandShade, AP.fog, 0.55f), 0.75f to lerp(AP.sand, AP.sandShade, 0.25f), 1f to AP.sand,
            startY = g.horizon, endY = f.dh), Offset(0f, g.horizon), Size(f.dw, f.dh - g.horizon))
        // Fog layer 3: mid reef boulders half lost in the blue.
        for (i in 0 until 7) {
            val x = g.cx - f.dw * 0.55f + i * f.dw * 0.18f + hash01(i, 44) * 80f
            val wd = 140f + hash01(i, 45) * 200f
            val y = g.horizon + 30f + hash01(i, 47) * 24f
            val p = rock(x, y, wd, 40 + i)
            drawPath(p, Brush.verticalGradient(listOf(lerp(AP.rockLit, AP.fog, 0.62f), lerp(AP.rockDark, AP.fog, 0.6f)), startY = y - wd * 0.3f, endY = y))
        }
        // Sand ripples (perspective: closer, wider, darker troughs), shell grit and pebbles.
        for (i in 0 until 46) {
            val v = (i / 46f)
            val y = g.horizon + 40f + v * v * (f.dh - g.horizon - 30f)
            val near = (y - g.horizon) / (f.dh - g.horizon)
            val x0 = hash01(i, 6) * f.dw - 60f
            val len = 80f + 200f * near
            drawArc(AP.sandShade.copy(alpha = 0.25f + 0.35f * near), 200f, 140f, false, Offset(x0, y), Size(len, 5f + 6f * near), style = Stroke(1f + 1.4f * near))
            drawArc(Color.White.copy(alpha = 0.18f * near), 210f, 120f, false, Offset(x0 + 4f, y - 1.5f), Size(len - 8f, 4f + 5f * near), style = Stroke(0.8f))
        }
        for (i in 0 until 90) {
            val px = hash01(i, 7) * f.dw; val py = g.sandY - 10f + hash01(i, 8).pow(0.7f) * (f.dh - g.sandY + 10f)
            val near = ((py - g.horizon) / (f.dh - g.horizon)).coerceIn(0f, 1f)
            drawOval(lerp(AP.rock, AP.sandShade, hash01(i, 9)).copy(alpha = 0.5f + 0.4f * near), Offset(px, py), Size((3f + hash01(i, 10) * 6f) * (0.5f + near), (2f + hash01(i, 11) * 3f) * (0.5f + near)))
        }
        // Fog layer 4: near rocks on the sand, lit from above, with a soft shadow on the seafloor and algae.
        for ((idx, pr) in listOf(230f to 170f, 720f to 210f, 800f to 110f, -70f to 260f, 1090f to 240f).withIndex()) {
            val (u, rw) = pr
            val cx = g.tx(u); val by = g.sandY + 16f
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent), Offset(cx + 10f, by + 4f), rw * 0.7f), Offset(cx - rw * 0.7f, by - 8f), Size(rw * 1.4f, 26f))
            val p = rock(cx, by, rw, 60 + idx)
            drawPath(p, Brush.verticalGradient(listOf(AP.rockLit, AP.rock, AP.rockDark), startY = by - rw * 0.32f, endY = by))
            // Crevices and encrusting algae.
            for (k in 0 until 5) {
                val x = cx - rw * 0.35f + hash01(k, 70 + idx) * rw * 0.7f
                drawLine(AP.rockDark.copy(alpha = 0.5f), Offset(x, by - rw * 0.06f), Offset(x + (hash01(k, 71) - 0.5f) * 20f, by - rw * 0.2f * hash01(k, 72) - 4f), 1.4f)
            }
            for (k in 0 until 26) {
                val a = PI.toFloat() * (0.15f + 0.7f * hash01(k, 80 + idx)); val d = hash01(k, 90 + idx).pow(0.5f) * rw * 0.42f
                drawCircle(lerp(Color(0xFF6F9A63), Color(0xFFB7C982), hash01(k, 52)).copy(alpha = 0.7f), 1.6f + hash01(k, 53) * 3f, Offset(cx + cos(a) * d, by - rw * 0.08f - sin(a) * d * 0.55f))
            }
        }
        // God rays from the surface, slanting with the sun.
        for (k in g.ray.indices) drawPath(g.ray[k], g.rayBrush, alpha = 0.75f + 0.25f * hash01(k, 9))
        // Sunlit sand: the caustic net the surface focuses onto the seafloor.
        withTransform({ translate(-60f, g.sandY - 30f) }) { causticPattern(f.dw + 120f, f.dh - g.sandY + 30f) }
        fronds(f)
    }

    /**
     * One wide strip of caustic net, drawn twice per frame at two slow drifts. The bright filaments are the
     * borders of a cellular (Voronoi) pattern, rasterised once: where the nearest and second-nearest seed are
     * almost equally far, light gathers. Cells are flattened and grow toward the viewer (perspective).
     */
    private fun DrawScope.causticPattern(wd: Float, ht: Float) {
        // Seeds on jittered rows; rows get taller and cells wider toward the viewer.
        val rowsY = FloatArray(40); val rowsS = FloatArray(40)
        var nRows = 0
        var y = -10f
        while (y < ht + 20f && nRows < 40) { val v = (y / ht).coerceIn(0f, 1f); val cell = 16f + 40f * v; rowsY[nRows] = y; rowsS[nRows] = cell; nRows++; y += cell * 0.42f }
        val perRow = (wd / 16f).toInt() + 4
        val sx = Array(nRows) { FloatArray(perRow) }; val sy = Array(nRows) { FloatArray(perRow) }; val cnt = IntArray(nRows)
        for (r in 0 until nRows) {
            val cell = rowsS[r]
            var x = -cell
            var c = 0
            while (x < wd + cell && c < perRow) {
                sx[r][c] = x + (hash01(r * 131 + c, 61) - 0.5f) * cell * 0.9f
                sy[r][c] = rowsY[r] + (hash01(r * 131 + c, 62) - 0.5f) * cell * 0.38f
                c++; x += cell
            }
            cnt[r] = c
        }
        val step = 2f
        var py = 0f
        var row = 0
        while (py < ht) {
            while (row + 1 < nRows && rowsY[row + 1] < py) row++
            val v = (py / ht).coerceIn(0f, 1f)
            val cell = 16f + 40f * v
            val ky = 1f / 0.42f
            var px = 0f
            while (px < wd) {
                var f1 = Float.MAX_VALUE; var f2 = Float.MAX_VALUE
                for (r in (row - 2).coerceAtLeast(0)..(row + 3).coerceAtMost(nRows - 1)) {
                    val cr = rowsS[r]
                    val c0 = ((px + cr) / cr).toInt()
                    for (c in (c0 - 2).coerceAtLeast(0)..(c0 + 2).coerceAtMost(cnt[r] - 1)) {
                        val dx = sx[r][c] - px; val dy = (sy[r][c] - py) * ky
                        val d = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (d < f1) { f2 = f1; f1 = d } else if (d < f2) f2 = d
                    }
                }
                val e = (f2 - f1) / cell
                if (e < 0.13f) {
                    val b = 1f - e / 0.13f
                    drawRect(AP.caustic.copy(alpha = b * b * (0.32f + 0.4f * v)), Offset(px, py), Size(step, step))
                }
                px += step
            }
            py += step
        }
    }

    // ------------------------------------------------------------------ moving

    private fun DrawScope.grass(g: AGeo, sim: AquariumSim, far: Boolean) {
        val lean = -sim.tilt * 2.2f
        val amp = if (sim.reducedMotion) 0f else (4f + 9f * sim.fan) * (if (sim.calm) 0.5f else 1f)
        for (i in g.grassX.indices) {
            val z = g.grassZ[i]
            if ((z < 0.55f) != far) continue
            val fogK = (1f - z) * 0.6f
            val segs = 9
            // A clump of two ribbon blades from one base, each with its own phase and curl.
            for (b in 0 until 2) {
                var x = g.grassX[i] + (b - 0.5f) * (6f + 6f * z); var y = g.sandY + 4f + z * 50f
                val len = g.grassH[i] * (0.6f + 0.6f * z) * (0.7f + 0.3f * hash01(i * 3 + b, 15)) / segs
                val col = lerp(lerp(AP.grass, AP.grassLit, hash01(i * 3 + b, 14) * 0.6f), AP.fog, fogK)
                val w0 = 4f + 6f * z
                val curl = (b - 0.5f) * 8f
                for (k in 0 until segs) {
                    val ku = k / segs.toFloat()
                    val ang = -90f + curl * ku + lean * ku + amp * sin(sim.plantPhase.toFloat() * 1.6f + i * 0.9f + b * 0.7f + k * 0.42f) * ku
                    val nx = x + cos(ang * PI.toFloat() / 180f) * len; val ny = y + sin(ang * PI.toFloat() / 180f) * len
                    drawLine(col, Offset(x, y), Offset(nx, ny), w0 * (1f - ku * 0.75f), cap = StrokeCap.Round)
                    if (k % 3 == 0) drawLine(AP.sunlight.copy(alpha = 0.22f * (1f - fogK)), Offset(x + w0 * 0.2f, y), Offset(nx + w0 * 0.2f, ny), 0.9f)
                    x = nx; y = ny
                }
            }
        }
    }

    /**
     * The god rays are baked into the base layer (the light falls steadily); at night, and while the climate
     * runs warm or cool, a single flat veil over the water takes them down instead of redrawing them.
     */
    private fun DrawScope.dimRays(g: AGeo, sim: AquariumSim, f: ThemeFrame) {
        val n = sim.night * 0.85f - 0.4f * sim.filterLamp * (1f - sim.night)
        if (n > 0.01f) drawRect(AP.middle.copy(alpha = 0.10f * n), Offset(0f, g.surface), Size(f.dw, 480f))
    }

    /** Kelp in the middle distance, half lost in the fog: tall stalks with blades, rising toward the light. */
    private fun DrawScope.kelp(g: AGeo, sim: AquariumSim) {
        val amp = if (sim.reducedMotion) 0f else (3f + 5f * sim.fan) * (if (sim.calm) 0.5f else 1f)
        val lean = -sim.tilt * 1.5f
        for (i in g.kelpX.indices) {
            var x = g.kelpX[i]; var y = g.horizon + 34f
            val col = lerp(Color(0xFF3E7A5A), AP.fog, 0.62f)
            val n = 15
            val step = (y - g.surface - 30f - g.kelpH[i]) / n
            for (k in 0 until n) {
                val ku = k / n.toFloat()
                val ang = -90f + lean * ku + amp * sin(sim.plantPhase.toFloat() * 0.8f + i * 1.3f + k * 0.3f) * ku
                val nx = x + cos(ang * PI.toFloat() / 180f) * step; val ny = y + sin(ang * PI.toFloat() / 180f) * step
                drawLine(col, Offset(x, y), Offset(nx, ny), 3.2f, cap = StrokeCap.Round)
                if (k >= 2) {
                    // A long blade at every node, alternating sides, trailing in the current.
                    val sd = if (k % 2 == 0) 1f else -1f
                    val bl = 46f - ku * 14f
                    withTransform({ rotate(ang + 90f * sd + sd * (-40f) + amp * 0.8f * sin(sim.plantPhase.toFloat() + k), Offset(nx, ny)) }) {
                        drawOval(col, Offset(nx, ny - 5f), Size(bl, 10f))
                    }
                }
                x = nx; y = ny
            }
        }
    }

    /** The reward: a scallop on the sand opens, and a pearl shows in its nacre. */
    private fun DrawScope.clam(g: AGeo, sim: AquariumSim) {
        val cx = g.tx(640f); val cy = g.sandY + 6f
        val o = sim.clamOpen()
        withTransform({ scale(1.45f, 1.45f, Offset(cx, cy)) }) { clamBody(g, cx, cy, o) }
    }

    private fun DrawScope.clamBody(g: AGeo, cx: Float, cy: Float, o: Float) {
        drawOval(Color.Black.copy(alpha = 0.18f), Offset(cx - 38f, cy - 2f), Size(76f, 12f))
        // Lower valve.
        drawArc(AP.shellDark, 0f, 180f, true, Offset(cx - 34f, cy - 16f), Size(68f, 26f))
        if (o > 0.01f) {
            drawArc(AP.nacre, 0f, 180f, true, Offset(cx - 28f, cy - 12f), Size(56f, 18f))
            val pr = 8f * o
            withTransform({ translate(cx, cy - 4f - 2f * o); scale(pr, pr, Offset.Zero) }) { drawCircle(g.pearlBrush, 1f, Offset.Zero) }
        }
        // Upper valve: a ribbed fan that hinges open at the back.
        rotate(-38f * o, Offset(cx + 30f, cy - 2f)) {
            drawArc(Brush.verticalGradient(listOf(lerp(AP.shell, Color.White, 0.25f), AP.shell, AP.shellDark), startY = cy - 30f, endY = cy), 180f, 180f, true, Offset(cx - 34f, cy - 30f), Size(68f, 56f))
            for (r in 0 until 9) {
                val a = (198f + r * 18f) * PI.toFloat() / 180f
                drawLine(AP.shellDark.copy(alpha = 0.55f), Offset(cx, cy - 2f), Offset(cx + cos(a) * 33f, cy - 2f + sin(a) * 27f), 1.2f)
            }
        }
    }

    private fun DrawScope.decor(g: AGeo, sim: AquariumSim) {
        for (k in 0 until sim.decor) {
            val x = g.tx(sim.decorX[k]); val y = g.ty(sim.decorY[k])
            when (sim.decorKind[k]) {
                0 -> { drawOval(AP.rock, Offset(x - 16f, y - 9f), Size(32f, 18f)); drawOval(AP.rockLit.copy(alpha = 0.5f), Offset(x - 10f, y - 8f), Size(16f, 5f)) }
                1 -> drawLine(Color(0xFF6E5A45), Offset(x - 26f, y), Offset(x + 26f, y - 12f), 7f, cap = StrokeCap.Round)
                else -> {
                    drawArc(Color(0xFFF2E1CF), 180f, 180f, true, Offset(x - 12f, y - 10f), Size(24f, 20f))
                    for (r in 0 until 5) drawLine(Color(0xFFC9A98C), Offset(x, y), Offset(x - 10f + r * 5f, y - 9f), 0.8f)
                }
            }
        }
    }

    /** Entrance accent: each fish glides in along its heading from beyond the frame, one after another. */
    private fun DrawScope.enterFish(g: AGeo, sim: AquariumSim, f: ThemeFrame, i: Int, intro: Float) {
        val e = Intro.out(intro, i * 0.25f / AquariumSim.FISH, 0.75f)
        if (e >= 1f) { fish(g, sim, i); return }
        val hx = g.tx(sim.fx[i]); val hy = g.ty(sim.fy[i])
        val h = sim.heading(i)
        val bx = -cos(h); val by = -sin(h)
        val toX = if (bx < -1e-3f) hx / -bx else if (bx > 1e-3f) (f.dw - hx) / bx else Float.MAX_VALUE
        val toY = if (by < -1e-3f) hy / -by else if (by > 1e-3f) (f.dh - hy) / by else Float.MAX_VALUE
        val back = minOf(minOf(toX, toY) + sim.species[i].length * sim.size[i] * FISH_SCALE * 1.2f, 640f) * (1f - e)
        withTransform({ translate(bx * back, by * back) }) { fish(g, sim, i) }
    }

    /**
     * A fish in its own unit frame: head at the origin, tail at x = −1, back toward −y. The body is one path with
     * a countershading gradient; a travelling wave bends the spine; a pass of the water's colour adds depth fog.
     */
    private fun DrawScope.fish(g: AGeo, sim: AquariumSim, i: Int) {
        val sp = sim.species[i]
        val z = depth[i]
        val len = sp.length * sim.size[i] * FISH_SCALE * (1f - 0.3f * z)
        val hx = g.tx(sim.fx[i]); val hy = g.ty(sim.fy[i])
        val h = sim.heading(i)
        val flip = if (cos(h) < 0f) -1f else 1f
        val skin = SKINS.getValue(sp)
        val segs = 8
        for (k in 0..segs) {
            val u = k / segs.toFloat()
            pt[k * 2] = -u
            pt[k * 2 + 1] = sim.wave(i, u) / len * flip
        }
        body.reset()
        for (k in 0..segs) {
            val hw = halfWidth(sp, k / segs.toFloat())
            if (k == 0) body.moveTo(pt[0] + 0.02f, pt[1]) else body.lineTo(pt[k * 2], pt[k * 2 + 1] - hw)
        }
        for (k in segs downTo 1) {
            val hw = halfWidth(sp, k / segs.toFloat())
            body.lineTo(pt[k * 2], pt[k * 2 + 1] + hw)
        }
        body.close()
        val tl = if (sp == Species.RIBBON) 0.06f else 0.24f
        val ex = pt[segs * 2]; val ey = pt[segs * 2 + 1]
        fin.reset()
        fin.moveTo(ex + 0.04f, ey)
        fin.lineTo(ex - tl, ey - tl * 0.85f)
        fin.lineTo(ex - tl * 0.62f, ey)
        fin.lineTo(ex - tl, ey + tl * 0.85f)
        fin.close()
        // Depth fog: the far and the deep take on the water's own colour (four steps, baked into the gradients).
        val fogA = 0.12f + 0.55f * z + 0.25f * ((hy - g.top) / 520f).coerceIn(0f, 1f)
        val fogStep = (fogA / 0.62f * (FOG_STEPS - 1)).roundToInt().coerceIn(0, FOG_STEPS - 1)
        val dim = 1f - sim.night * 0.35f
        withTransform({ translate(hx, hy); rotate(h * 180f / PI.toFloat(), Offset.Zero); scale(len, len * flip, Offset.Zero) }) {
            if (sp == Species.SAIL) {
                // Tall swept dorsal and anal fins from mid-body toward the tail.
                for (sg in SIDES) {
                    back.reset()
                    back.moveTo(pt[4], pt[5] + sg * 0.12f)
                    back.quadraticTo(pt[6] - 0.02f, pt[7] + sg * 0.62f, pt[10] - 0.26f, pt[11] + sg * 0.72f)
                    back.quadraticTo(pt[12] - 0.1f, pt[13] + sg * 0.3f, pt[12], pt[13] + sg * 0.08f)
                    back.close()
                    drawPath(back, skin.fin.copy(alpha = 0.6f * dim))
                }
            }
            if (sp == Species.STRIPE || sp == Species.ROWER) {
                // A small dorsal fin.
                back.reset()
                back.moveTo(pt[6], pt[7] - halfWidth(sp, 0.375f) * 0.9f)
                back.lineTo(pt[8] - 0.06f, pt[9] - halfWidth(sp, 0.5f) - 0.12f)
                back.lineTo(pt[10], pt[11] - halfWidth(sp, 0.625f) * 0.9f)
                back.close()
                drawPath(back, skin.fin.copy(alpha = 0.75f * dim))
            }
            drawPath(fin, skin.fin.copy(alpha = 0.85f * dim))
            drawPath(body, bodyBrush(sp, fogStep), alpha = dim)
            // Lateral line and a soft sheen on the flank (the light comes from the surface).
            drawLine(skin.line.copy(alpha = 0.45f * dim), Offset(pt[2], pt[3] - 0.01f), Offset(pt[12], pt[13] - 0.01f), 0.02f)
            when (sp) {
                Species.FLAT -> for (k in 1 until 7) drawCircle(skin.line.copy(alpha = 0.6f * dim), 0.022f, Offset(pt[k * 2], pt[k * 2 + 1] + ((k % 3) - 1) * 0.04f))
                Species.ROWER -> {
                    val rp = sin(sim.phase[i] * 1.6f) * 0.5f + 0.5f
                    drawLine(skin.fin.copy(alpha = 0.85f * dim), Offset(pt[4], pt[5] + 0.08f), Offset(pt[4] - 0.18f, pt[5] + 0.18f + 0.16f * rp), 0.07f, cap = StrokeCap.Round)
                }
                Species.SHRIMP -> {
                    drawLine(skin.flank.copy(alpha = 0.8f * dim), Offset(0f, 0f), Offset(0.9f, -0.35f), 0.03f)
                    drawLine(skin.flank.copy(alpha = 0.8f * dim), Offset(0f, 0f), Offset(0.9f, 0.25f), 0.03f)
                    for (k in 2 until 7 step 2) drawLine(skin.back.copy(alpha = 0.5f * dim), Offset(pt[k * 2], pt[k * 2 + 1] - 0.12f), Offset(pt[k * 2], pt[k * 2 + 1] + 0.12f), 0.025f)
                }
                else -> Unit
            }
            // Eye: a silver iris with a dark pupil (no catch-light).
            if (sp != Species.SHRIMP) {
                val er = minOf(0.05f, 3.4f / len)
                drawCircle(Color(0xFF9DB0AA).copy(alpha = dim), er, Offset(-0.09f, pt[3] - 0.03f))
                drawCircle(Color(0xFF101314).copy(alpha = dim), er * 0.62f, Offset(-0.09f, pt[3] - 0.03f))
            }
        }
    }

    private fun maxHalf(sp: Species): Float = when (sp) { Species.STRIPE -> 0.13f; Species.ROWER -> 0.30f; Species.RIBBON -> 0.05f; Species.FLAT -> 0.12f; Species.SAIL -> 0.22f; Species.SHRIMP -> 0.14f }

    private fun halfWidth(sp: Species, u: Float): Float {
        val base = maxHalf(sp)
        val prof = if (sp == Species.RIBBON) 1f - 0.6f * u else (sin(PI.toFloat() * (0.12f + 0.88f * u)) * (1f - 0.72f * u)).coerceAtLeast(0.06f)
        return base * prof
    }

    private fun DrawScope.bubbles(g: AGeo, sim: AquariumSim) {
        if (sim.reducedMotion) return
        val n = (3 + sim.fan * 16 + sim.charging * 6).toInt().coerceAtMost(26)
        val ph = sim.bubblePhase.toFloat()
        // A seep in the rocks on the left; bubbles wobble and grow as they rise.
        val vx = g.tx(230f); val vy = g.sandY - 40f
        for (k in 0 until n) {
            val u = ((ph * 0.35f + k / n.toFloat()) % 1f)
            val x = vx + sin(u * 18f + k) * (4f + 10f * u)
            val y = vy - u * (vy - g.surface - 6f)
            val r = (1.6f + 3.5f * sim.fan * hash01(k, 31) + 1f) * (0.7f + 0.6f * u)
            drawCircle(Color.White.copy(alpha = 0.10f), r, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.55f), r, Offset(x, y), style = ring)
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
        }
        // While charging: a steady column from a second seep on the right.
        if (sim.charging > 0.05f) for (k in 0 until 12) {
            val u = ((ph * 0.5f + k / 12f) % 1f)
            val x = g.tx(880f) + sin(u * 20f) * 3f
            val y = g.sandY - 30f - u * (g.sandY - 30f - g.surface)
            drawCircle(Color.White.copy(alpha = 0.55f * sim.charging), 2.2f + u, Offset(x, y), style = ring)
        }
    }

    /** Marine snow: specks sinking slowly and drifting with the water. */
    private fun DrawScope.snow(g: AGeo, sim: AquariumSim, f: ThemeFrame) {
        val t = if (sim.reducedMotion) 0f else sim.decoTime.toFloat() * (if (sim.calm) 0.5f else 1f)
        val a = 0.45f * (1f - sim.night * 0.6f)
        for (i in g.snowX.indices) {
            val y = (g.snowY[i] + t * (3f + (i % 5))) % 600f
            val x = g.snowX[i] + sin(t * 0.17f + i) * 14f
            drawCircle(Color(0xFFE6FBF6).copy(alpha = a * (0.4f + 0.6f * hash01(i, 24))), g.snowR[i], Offset(x, y))
        }
    }

    /** The surface from below: a silver mirror above a rippling line that leans with the acceleration. */
    private fun DrawScope.surface(g: AGeo, sim: AquariumSim, f: ThemeFrame) {
        val t = if (sim.reducedMotion) 0f else sim.decoTime.toFloat() * (if (sim.calm) 0.5f else 1f)
        withTransform({ if (abs(sim.tilt) > 0.02f) rotate(sim.tilt, Offset(g.cx, g.surface)) }) {
            val x0 = -100f; val x1 = f.dw + 100f
            val n = sim.night
            drawRect(g.mirrorBrush, Offset(x0, -90f), Size(x1 - x0, g.surface + 90f), alpha = 1f - 0.7f * n)
            withTransform({ scale(1f, 0.22f, Offset(g.cx - 220f, g.surface)) }) {
                drawCircle(g.snell, 360f, Offset(g.cx - 220f, g.surface), alpha = 1f - 0.85f * n)
            }
            wave.reset()
            var x = x0
            while (x <= x1) {
                val y = g.surface + sin(x * 0.021f + t * 1.1f) * 2.6f + sin(x * 0.047f - t * 0.7f) * 1.6f
                if (x == x0) wave.moveTo(x, y) else wave.lineTo(x, y)
                x += 24f
            }
            drawPath(wave, Color.White.copy(alpha = 0.75f * (1f - 0.6f * n)), style = rippleStroke)
            // Rain dimples and ripple rings, seen from below.
            if (sim.rain > 0.02f && !sim.reducedMotion) for (k in 0 until 12) {
                val u = ((sim.decoTime.toFloat() * (0.6f + hash01(k, 41) * 0.5f) + hash01(k, 42)) % 1f)
                val rx = 40f + hash01(k, 43) * (f.dw - 80f)
                drawOval(Color.White.copy(alpha = 0.6f * (1f - u) * sim.rain), Offset(rx - 30f * u, g.surface - 3f * u), Size(60f * u, 7f * u), style = ring)
            }
        }
    }

    private fun DrawScope.tint(g: AGeo, sim: AquariumSim, f: ThemeFrame) {
        val sz = Size(f.dw, f.dh)
        val w = sim.warmth + (if (sim.ac > 0.5f) -0.4f else 0f)
        if (w > 0.05f) drawRect(g.warmTint.copy(alpha = 0.08f * w), size = sz)
        if (w < -0.05f) drawRect(g.coolTint.copy(alpha = 0.10f * -w), size = sz)
        val m = sim.mode
        if (m < -0.02f) drawRect(g.ecoTint, size = sz, alpha = -m)
        if (m > 0.02f) drawRect(g.sportShaft, size = sz, alpha = m)
        if (sim.night > 0.01f) drawRect(g.nightTint, size = sz, alpha = sim.night)
        // Bioluminescent plankton: soft glows that swell and fade slowly (well under 1 Hz, never a flash).
        if (sim.plankton > 0.01f) for (k in g.plankX.indices) {
            val glow = (0.5f + 0.5f * sin(sim.decoTime.toFloat() * 0.5f + k * 1.3f)) * sim.plankton
            if (glow < 0.05f) continue
            val x = g.plankX[k] + sin(sim.decoTime.toFloat() * 0.08f + k) * 12f
            val y = g.plankY[k]
            val r = 6f + (k % 4) * 3f
            withTransform({ translate(x, y); scale(r, r, Offset.Zero) }) { drawCircle(g.glow, 1f, Offset.Zero, alpha = glow, blendMode = BlendMode.Plus) }
            drawCircle(AP.plankton.copy(alpha = 0.9f * glow), 1.3f, Offset(x, y))
        }
    }

    /** Out-of-focus kelp right in front of the viewer at both edges: dark and completely soft (baked, still). */
    private fun DrawScope.fronds(f: ThemeFrame) {
        val col = lerp(AP.deep, Color(0xFF0A2A24), 0.7f)
        for (side in 0..1) {
            val bx = if (side == 0) -70f else f.dw + 70f
            val dir = if (side == 0) 1f else -1f
            for (pass in 0 until 4) {
                var x = bx; var y = f.dh + 40f
                val wid = 150f - pass * 28f
                for (k in 0 until 7) {
                    val ang = -90f + dir * (10f + k * 4f) + 3f * sin(k * 0.4f + side)
                    val nx = x + cos(ang * PI.toFloat() / 180f) * 60f; val ny = y + sin(ang * PI.toFloat() / 180f) * 60f
                    drawLine(col.copy(alpha = 0.10f), Offset(x, y), Offset(nx, ny), wid * (1f - k * 0.1f), cap = StrokeCap.Round)
                    x = nx; y = ny
                }
            }
        }
    }
}

private val SIDES = floatArrayOf(-1f, 1f)
private const val FOG_STEPS = 4

/** Fish are drawn larger than their tank units (the viewer swims among them). */
private const val FISH_SCALE = 1.5f
