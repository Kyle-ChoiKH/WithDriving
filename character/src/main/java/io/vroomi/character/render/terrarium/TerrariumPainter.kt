package io.vroomi.character.render.terrarium

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.Intro
import io.vroomi.character.render.hash01
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.character.render.theme.drawNative
import io.vroomi.character.terrarium.PlantGen
import io.vroomi.character.terrarium.TerrariumSim
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import io.vroomi.character.render.theme.drawMusicNotes

/*
 * TERRARIUM, art direction: a shallow-focus nature photograph. A glass jar garden stands outdoors on a mossy
 * stone in natural daylight; the jar and the moss around its foot are the focal plane, everything behind is a
 * strongly defocused garden of soft masses and large bokeh discs (tinted by the season), and the stone's front
 * edge falls out of focus again. Light comes from the upper right; the glass shows refraction bands, sharp
 * window highlights, green-tinted thick edges, a caustic in its shadow and condensation droplets. No text.
 */

/** The theme's private palette (photographic: daylight greens, sky, soil, stone and glass). */
private object TP {
    // Defocused garden per season: top (sky through leaves), mid, low, deep, and the season's bokeh tints.
    val top = arrayOf(Color(0xFFF2F4EE), Color(0xFFE6F1E6), Color(0xFFF4ECDC), Color(0xFFEDF2F6))
    val mid = arrayOf(Color(0xFFC6DDB3), Color(0xFFADD08F), Color(0xFFD8BE86), Color(0xFFC3D3DD))
    val low = arrayOf(Color(0xFF86B468), Color(0xFF66994A), Color(0xFFAE8440), Color(0xFF8CA5B0))
    val deep = arrayOf(Color(0xFF466F3B), Color(0xFF34602F), Color(0xFF66522A), Color(0xFF55707A))
    val bokehA = arrayOf(Color(0xFFF6BCD0), Color(0xFFE4F3A2), Color(0xFFF2A851), Color(0xFFC9DDF2))
    val bokehB = arrayOf(Color(0xFFFFF0F5), Color(0xFFFFFFF0), Color(0xFFFFDDA0), Color(0xFFF5FAFF))
    // Moonlit garden.
    val nTop = Color(0xFF15253B)
    val nMid = Color(0xFF12223A)
    val nLow = Color(0xFF0D1B2C)
    val nDeep = Color(0xFF07111C)
    val nBokehA = Color(0xFF8EA9CF)
    val nBokehB = Color(0xFFC9D8EE)
    // Stone and moss (day / night).
    val stone = Color(0xFF8D8F86); val stoneLit = Color(0xFFBDBDB1); val stoneDark = Color(0xFF55594F)
    val nStone = Color(0xFF34404F); val nStoneLit = Color(0xFF5C6E86); val nStoneDark = Color(0xFF151C26)
    val moss = Color(0xFF6E973A); val mossLit = Color(0xFFB2D265); val mossDark = Color(0xFF3B5B22)
    val nMoss = Color(0xFF2C4639); val nMossLit = Color(0xFF557A73); val nMossDark = Color(0xFF15241D)
    val lichen = Color(0xFFC9CDA8)
    // Inside the jar.
    val soil = Color(0xFF4B3527); val soilDark = Color(0xFF2A1C14); val charcoal = Color(0xFF242120)
    val pebble = Color(0xFFA5A49B); val pebbleDark = Color(0xFF5E5D56)
    val stem = Color(0xFF5A6B33); val bark = Color(0xFF6E5238)
    val water = Color(0xFF7FA7A6); val waterDeep = Color(0xFF3B5A5B)
    val glassEdge = Color(0xFFBFE6D2)
    val sun = Color(0xFFFFE3AE)
    val moon = Color(0xFFCADBF5)
    val firefly = Color(0xFFE8F69A)
    val ladybug = Color(0xFFC8402C)
    val beetle = Color(0xFF1C1814)
    val moth = Color(0xFFB4A88F)
}

/** Season leaf colours (0 spring, 1 summer, 2 autumn, 3 winter): nothing ever withers, winter is a cool evergreen. */
private val LEAF = arrayOf(Color(0xFF7DB843), Color(0xFF3E7F2E), Color(0xFFC9712A), Color(0xFF3F6F55))
private val LEAF_LIT = arrayOf(Color(0xFFC3E58A), Color(0xFF86C25A), Color(0xFFEFB25A), Color(0xFF86AE98))
private val PETAL = arrayOf(Color(0xFFF7B7CF), Color(0xFFFFFFFF), Color(0xFFF5C451), Color(0xFFF4F1EA))

private class TGeo(val dw: Float, val kind: Int) {
    /** The jar stands a little right of centre (a photographer's third), more so on wide canvases. */
    val x = dw / 2f + max(0f, dw - 900f) * 0.10f
    /** The vessel of this upgrade step (TerrariumSteps.kt): the measures below are its own. */
    val v = Vessel(kind, x)
    val base = v.base
    val half = v.half
    val shoulder = v.shoulder
    val neckHalf = v.neckHalf
    val neckY = v.neckY
    val lipY = v.lipY
    val ground = v.ground
    val q = v.q

    /** The boulder under the jar: a lumpy dome whose crown is the focal plane. */
    val sx = x + 14f
    val sr = 470f
    fun stoneTop(px: Float): Float {
        val u = (px - sx) / sr
        val e = max(0f, abs(u) - 0.45f)
        return base - 2f + e * e * 260f + sin(px * 0.031f + 1f) * 5f * e + sin(px * 0.011f) * 2f
    }
    /** Depth of the moss cushion below the stone's top edge (clumpy, thicker on the crown). */
    fun mossDepth(px: Float): Float {
        val u = abs(px - sx) / sr
        return ((64f + 26f * sin(px * 0.017f + 1f) + 12f * sin(px * 0.052f)) * (1.25f - 0.75f * u)).coerceAtLeast(8f)
    }

    val jar = v.path(0f)
    val inner = v.path(7f)
    val stone = Path().apply {
        val x0 = sx - sr * 1.25f; val x1 = sx + sr * 1.25f
        moveTo(x0, 660f)
        var px = x0
        while (px <= x1) { lineTo(px, stoneTop(px)); px += 6f }
        lineTo(x1, 660f)
        close()
    }

    // Plant bases (design units).
    val fern = Offset(x - 138f, ground + 2f)
    val sapling = Offset(x - 18f, ground - 2f)
    val ivy = Offset(x - 178f, 222f)
    val succulent = Offset(x + 102f, ground + 13f)
    val flower = Offset(x + 172f, ground + 2f)
    /** The pond comes with the stream (step 3); before that it lies far outside the frame. */
    val pond = if (TerraStep.stream(kind)) Rect(x + 4f, ground - 5f, x + 112f, ground + 15f) else Rect(-9000f, -9000f, -8990f, -8990f)
    val pondPath = Path().apply { addOval(pond) }
    val ladyLeafA = Offset(x - 206f * q, ground + 2f)
    val bladeCtrl = Offset(x - 200f * q, ground - 140f * q)
    val ladyLeafB = Offset(x - 108f * q, ground - 118f * q)
    val blade = Path().apply {
        moveTo(ladyLeafA.x - 5f, ladyLeafA.y)
        quadraticTo(bladeCtrl.x - 6f, bladeCtrl.y, ladyLeafB.x, ladyLeafB.y)
        quadraticTo(bladeCtrl.x + 8f, bladeCtrl.y + 10f, ladyLeafA.x + 5f, ladyLeafA.y)
        close()
    }
    val bladeRib = Path().apply { moveTo(ladyLeafA.x, ladyLeafA.y); quadraticTo(bladeCtrl.x + 1f, bladeCtrl.y + 5f, ladyLeafB.x, ladyLeafB.y) }

    // Condensation droplets (fixed pattern, larger ones lower where they gather).
    val dropX = FloatArray(240) { x - half + 14f + hash01(it, 81) * (half * 2f - 28f) }
    val dropY = FloatArray(240) { neckY + 26f + hash01(it, 82).pow(0.7f) * (base - neckY - 50f) }
    val dropR = FloatArray(240) { 0.9f + hash01(it, 83) * hash01(it, 84) * 4.2f * (0.5f + (dropY[it] - neckY) / (base - neckY)) }
    val fireX = FloatArray(TerrariumSim.FIREFLIES) { x + (-168f + it * 64f + hash01(it, 91) * 26f) * q }
    val fireY = FloatArray(TerrariumSim.FIREFLIES) { ground - 142f * q + hash01(it, 92) * 104f * q }
    /** Out-of-focus fireflies in the garden behind (large soft glows). */
    val farFireX = FloatArray(7) { hash01(it, 93) * dw }
    val farFireY = FloatArray(7) { 90f + hash01(it, 94) * 300f }
    /** Spots on the moss where the bloom wave opens little flowers. */
    val waveX = FloatArray(14) { x - half + 30f + it * ((half * 2f - 60f) / 13f) + (hash01(it, 95) - 0.5f) * 10f }

    val sunPool = Brush.radialGradient(listOf(TP.sun.copy(alpha = 0.34f), TP.sun.copy(alpha = 0f)), Offset(dw * 0.92f, -60f), 980f)
    val coolPool = Brush.radialGradient(listOf(Color(0xFFDDEBFF).copy(alpha = 0.30f), Color.Transparent), Offset(dw * 0.92f, -60f), 980f)
    val golden = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFB25E).copy(alpha = 0.34f)), startX = dw * 0.25f, endX = dw)
    val ecoPool = Brush.radialGradient(listOf(Color(0xFFD7F5C2).copy(alpha = 0.20f), Color.Transparent), Offset(x, 320f), 620f)
    val sportPool = Brush.radialGradient(listOf(Color(0xFFFFC46B).copy(alpha = 0.22f), Color.Transparent), Offset(dw, 60f), 760f)
    val rainVeil = Brush.verticalGradient(listOf(Color(0xFFB7C6CF).copy(alpha = 0.30f), Color(0xFF7D8E98).copy(alpha = 0.20f)), startY = 0f, endY = 600f)
    val interiorNight = Brush.verticalGradient(listOf(Color(0xFF0B1A33).copy(alpha = 0.34f), Color(0xFF060E1C).copy(alpha = 0.50f)), startY = neckY, endY = base)
    val fireGlow = Brush.radialGradient(listOf(TP.firefly.copy(alpha = 0.55f), TP.firefly.copy(alpha = 0f)), Offset.Zero, 1f)
    val focusVeil = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.25f)), Offset(x, 320f), 520f)
}

/** The glass-jar garden painter: a shallow-focus outdoor photograph of a jar on a mossy stone. */
internal class TerrariumPainter : ThemePainter<TerrariumSim>() {
    override val minDesignWidth = 900f
    override val focusWidth = 640f
    override val backdrop = Color(0xFF1E2B1F)

    private var geo: TGeo? = null
    private val baseDay = LayerSlot(SceneTheme.TERRARIUM)
    private val baseNight = LayerSlot(SceneTheme.TERRARIUM)
    private val glassTop = LayerSlot(SceneTheme.TERRARIUM)
    private val succulentLayer = LayerSlot(SceneTheme.TERRARIUM)
    private val plants = Array(3) { LayerSlot(SceneTheme.TERRARIUM) }
    private val plantOrigin = Array(3) { FloatArray(2) }
    private val succOrigin = FloatArray(2)
    private val hair = Stroke(0.8f)
    private val rim = Stroke(1.1f)

    private fun geo(dw: Float, kind: Int): TGeo {
        val g = geo
        if (g != null && abs(g.dw - dw) < 0.5f && g.kind == kind) return g
        return TGeo(dw, kind).also { geo = it }
    }

    override fun drawPx(s: DrawScope, sim: TerrariumSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val g = geo(f.dw, f.model)
        val w = f.w.roundToInt(); val h = f.h.roundToInt(); val k = f.k
        val key = (w.toLong() shl 24) xor (h.toLong() shl 4) xor sim.season.toLong() xor (f.model.toLong() shl 44)
        val n = sim.night
        val intro = f.options.intro
        val off = IntOffset(left.roundToInt(), top.roundToInt())
        if (intro < 1f) {
            // Entrance accent: a focus pull. The lens breathes from 4 % larger while the garden settles into focus.
            val z = 1f + 0.04f * (1f - Intro.out(intro, 0f, 0.9f))
            withTransformAround(s, z, left + f.w / 2f, top + f.h / 2f) { drawBases(s, sim, g, f, key, n, off) }
        } else drawBases(s, sim, g, f, key, n, off)
    }

    private inline fun withTransformAround(s: DrawScope, z: Float, cx: Float, cy: Float, block: () -> Unit) {
        s.withTransform({ scale(z, z, Offset(cx, cy)) }) { block() }
    }

    private fun drawBases(s: DrawScope, sim: TerrariumSim, g: TGeo, f: ThemeFrame, key: Long, n: Float, off: IntOffset) {
        val w = f.w.roundToInt(); val h = f.h.roundToInt(); val k = f.k
        if (n < 0.99f) {
            val day = baseDay.get(key, w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { staticBase(g, f, sim.season, false) } }
            s.drawImage(day, dstOffset = off, dstSize = IntSize(day.width, day.height))
        }
        if (n > 0.01f) {
            val night = baseNight.get(key, w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { staticBase(g, f, sim.season, true) } }
            s.drawImage(night, dstOffset = off, dstSize = IntSize(night.width, night.height), alpha = n.coerceIn(0f, 1f))
        }
    }

    override fun draw(s: DrawScope, sim: TerrariumSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        val g = geo(f.dw, f.model)
        daylight(g, sim, f)
        farFireflies(g, sim)
        clipPath(g.inner) {
            // The planting follows the upgrade step (TerraStep): each step keeps the one before and adds its own.
            val kind = g.kind
            causticsOnSoil(g, sim)
            if (TerraStep.stream(kind)) {
                stream(g.v, if (sim.reducedMotion) 0f else sim.decoTime.toFloat() * (if (sim.calm) 0.5f else 1f), sim.night)
                pond(g, sim)
            }
            moss(g, sim)
            if (!TerraStep.planted(kind)) sprouts(g.v, sim.sway * sin(sim.swayPhase).toFloat() * 3f - sim.tilt * 0.6f, LEAF[sim.season], LEAF_LIT[sim.season])
            if (TerraStep.stream(kind)) plant(1, g, sim, f, PlantGen.Kind.SAPLING, g.sapling, TerrariumSim.SAPLING, 4.0f)
            if (TerraStep.planted(kind)) plant(0, g, sim, f, PlantGen.Kind.FERN, g.fern, TerrariumSim.FERN, 3.2f)
            if (TerraStep.vines(kind)) plant(2, g, sim, f, PlantGen.Kind.IVY, g.ivy, TerrariumSim.IVY, 2.1f)
            if (TerraStep.planted(kind)) succulent(g, sim, f)
            if (TerraStep.bridge(kind)) flowerPlant(g, sim)
            bloomWave(g, sim)
            ladybug(g, sim)
            rainCloud(g, sim)
            dew(g, sim)
            if (sim.night > 0.01f) drawRect(g.interiorNight, Offset(g.x - g.half, g.lipY), Size(g.half * 2f, g.base - g.lipY), alpha = sim.night)
            fireflies(g, sim)
        }
        condensation(g, sim)
        val k = f.k
        val nightKey = if (sim.night > 0.5f) 1L else 0L
        val gk = (f.w.roundToInt().toLong() shl 24) xor (f.h.roundToInt().toLong() shl 4) xor (sim.season.toLong() shl 1) xor nightKey xor (f.model.toLong() shl 44)
        val top = glassTop.get(gk, f.w.roundToInt(), f.h.roundToInt(), f.density) { withTransform({ scale(k, k, Offset.Zero) }) { glassOverlay(g, f, sim.season, nightKey == 1L) } }
        drawNative(top, f, 0f, 0f)
        moth(g, sim)
        // Music in the car: the flower sways on the beat and little notes rise from the jar like fireflies.
        drawMusicNotes(g.x + g.half * 0.45f, g.lipY - 6f, Color(0xFFFFF4C2), sim.listening, sim.decoTime, glow = Color(0xFFFFD86B))
        val intro = f.options.intro
        if (intro < 1f) drawRect(g.focusVeil, size = Size(f.dw, f.dh), alpha = 1f - Intro.out(intro, 0.05f, 0.8f))
    }

    // ------------------------------------------------------------------ static

    /** The defocused garden behind: soft masses, then large bokeh discs (season tint), all in a lens blur. */
    private fun DrawScope.garden(g: TGeo, f: ThemeFrame, season: Int, night: Boolean) {
        val top = if (night) TP.nTop else TP.top[season]
        val mid = if (night) TP.nMid else TP.mid[season]
        val low = if (night) TP.nLow else TP.low[season]
        val deep = if (night) TP.nDeep else TP.deep[season]
        val w = max(f.dw, 900f)
        drawRect(Brush.verticalGradient(0f to top, 0.32f to mid, 0.66f to low, 1f to deep, startY = 0f, endY = f.dh), size = Size(f.dw, f.dh))
        // Large soft masses of foliage (out of focus: no edges at all).
        for (i in 0 until 16) {
            val cx = hash01(i, 101) * w; val cy = 150f + hash01(i, 102) * 420f
            val r = 130f + hash01(i, 103) * 200f
            val c = lerp(low, deep, hash01(i, 104))
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.75f), c.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        // Light coming through the canopy (upper area, toward the sun on the right).
        for (i in 0 until 9) {
            val cx = (0.35f + 0.7f * hash01(i, 111)) * w; val cy = hash01(i, 112) * 240f
            val r = 110f + hash01(i, 113) * 170f
            val c = lerp(top, if (night) TP.nBokehA else TP.bokehB[season], 0.4f)
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = if (night) 0.18f else 0.7f), c.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        // A darker hedge behind the jar, so the glass edges and highlights read against it.
        val hedge = lerp(deep, Color.Black, if (night) 0.3f else 0.15f)
        withTransform({ scale(1.3f, 0.75f, Offset(g.x - 40f, 340f)) }) {
            drawCircle(Brush.radialGradient(listOf(hedge.copy(alpha = 0.75f), hedge.copy(alpha = 0f)), Offset(g.x - 40f, 340f), 400f), 400f, Offset(g.x - 40f, 340f))
        }
        // Bokeh discs: specular points rendered by the open aperture. Flat inside, a slightly brighter edge that
        // falls off over one or two units; they gather where light breaks through the leaves (upper right).
        val count = if (night) 16 else 58
        for (i in 0 until count) {
            val sunward = hash01(i, 126)
            val cx = if (sunward < 0.6f) (0.45f + 0.6f * hash01(i, 121)) * w else hash01(i, 121) * w
            val cy = 10f + hash01(i, 122).pow(1.5f) * 400f
            val r = 10f + hash01(i, 123).pow(2.4f) * 66f
            val c = if (night) lerp(TP.nBokehA, TP.nBokehB, hash01(i, 124)) else lerp(TP.bokehA[season], TP.bokehB[season], hash01(i, 124))
            val a = if (night) 0.06f + hash01(i, 125) * 0.12f else 0.10f + hash01(i, 125) * 0.32f * (1f - cy / 600f)
            drawCircle(Brush.radialGradient(0f to c.copy(alpha = a * 0.85f), 0.86f to c.copy(alpha = a), 0.97f to c.copy(alpha = a * 1.1f), 1f to c.copy(alpha = 0f),
                center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
        }
        // Foreground leaves in front of the lens, completely defocused (frames the shot at the lower left).
        val fl = if (night) Color(0xFF050B12) else lerp(deep, Color(0xFF1D2C17), 0.5f)
        drawCircle(Brush.radialGradient(listOf(fl.copy(alpha = 0.92f), fl.copy(alpha = 0f)), Offset(-40f, f.dh + 30f), 330f), 330f, Offset(-40f, f.dh + 30f))
        val fr = if (night) Color(0xFF0A1626) else lerp(low, top, 0.35f)
        drawCircle(Brush.radialGradient(listOf(fr.copy(alpha = 0.55f), fr.copy(alpha = 0f)), Offset(f.dw + 60f, -30f), 260f), 260f, Offset(f.dw + 60f, -30f))
    }

    private fun DrawScope.staticBase(g: TGeo, f: ThemeFrame, season: Int, night: Boolean) {
        garden(g, f, season, night)
        stone(g, f, night)
        // Behind the glass: the garden magnified by the jar (a pre-baked lens), then the soil in section.
        clipPath(g.inner) {
            withTransform({ scale(1.10f, 1.10f, Offset(g.x, 300f)) }) { garden(g, f, season, night) }
            soil(g, season, night)
        }
    }

    /** The mossy stone: the focal plane is at the jar's foot; detail fades with distance from it. */
    private fun DrawScope.stone(g: TGeo, f: ThemeFrame, night: Boolean) {
        val lit = if (night) TP.nStoneLit else TP.stoneLit
        val mid = if (night) TP.nStone else TP.stone
        val dark = if (night) TP.nStoneDark else TP.stoneDark
        val moss = if (night) TP.nMoss else TP.moss
        val mossLit = if (night) TP.nMossLit else TP.mossLit
        val mossDark = if (night) TP.nMossDark else TP.mossDark
        // Rock: lit from the upper right (moonlight from the upper left at night), falling into shade below.
        val keyX = if (night) g.sx - g.sr * 0.5f else g.sx + g.sr * 0.55f
        drawPath(g.stone, Brush.verticalGradient(listOf(mid, dark, lerp(dark, Color.Black, 0.35f)), startY = g.base, endY = f.dh + 30f))
        drawPath(g.stone, Brush.radialGradient(listOf(lit.copy(alpha = 0.55f), lit.copy(alpha = 0f)), Offset(keyX, g.base + 20f), g.sr * 0.8f))
        clipPath(g.stone) {
            // Rough face: pits and grain (soft: the face is in front of the focal plane).
            for (i in 0 until 800) {
                val px = g.sx - g.sr * 1.2f + hash01(i, 131) * g.sr * 2.4f
                val py = g.stoneTop(px) + 10f + hash01(i, 132).pow(0.8f) * 150f
                val focus = sharpness(g, px, py)
                val r = 0.8f + hash01(i, 133) * 1.8f + (1f - focus) * 3f
                drawCircle(lerp(dark, lit, hash01(i, 134)).copy(alpha = 0.16f + 0.30f * focus), r, Offset(px, py))
            }
            // Lichen: crusty pale patches made of many tiny lobes.
            val lc = if (night) TP.nStoneLit else TP.lichen
            for (i in 0 until 10) {
                val px = g.sx - g.sr * 0.95f + hash01(i, 135) * g.sr * 1.9f
                val py = g.stoneTop(px) + g.mossDepth(px) + 14f + hash01(i, 136) * 40f
                val r = 7f + hash01(i, 137) * 11f
                for (k in 0 until 26) {
                    val a = hash01(i * 31 + k, 138) * 6.28f; val d = hash01(i * 31 + k, 139).pow(0.6f) * r
                    drawCircle(lc.copy(alpha = 0.30f), 1.1f + hash01(i * 31 + k, 140) * 2f, Offset(px + cos(a) * d, py + sin(a) * d * 0.7f))
                }
            }
            // The moss cushion over the crown: dense near the top edge, ragged clumps where it ends.
            for (i in 0 until 7000) {
                val px = g.sx - g.sr * 1.2f + hash01(i, 141) * g.sr * 2.4f
                val top = g.stoneTop(px)
                val depth = g.mossDepth(px)
                val v = hash01(i, 142).pow(1.3f)
                val py = top + v * depth * (0.85f + 0.3f * hash01(i, 146))
                val focus = sharpness(g, px, py)
                val edge = ((v - 0.7f) / 0.3f).coerceIn(0f, 1f)
                val r = (1.1f + hash01(i, 143) * 1.8f + (1f - focus) * 2.6f) * (1f - 0.45f * edge)
                val sun = if (night) 0.5f - (px - g.sx) / g.sr * 0.4f else 0.5f + (px - g.sx) / g.sr * 0.4f
                val c = lerp(lerp(mossDark, moss, hash01(i, 144) * (1f - v * 0.6f)), mossLit, ((1f - v) * sun * hash01(i, 145)).coerceIn(0f, 1f))
                drawCircle(c.copy(alpha = 1f - 0.6f * edge), r, Offset(px, py))
            }
        }
        // Tufts stand up along the top edge against the defocused garden (the outline is never a clean line).
        for (i in 0 until 1100) {
            val px = g.sx - g.sr * 1.15f + hash01(i, 181) * g.sr * 2.3f
            val focus = sharpness(g, px, g.stoneTop(px))
            val py = g.stoneTop(px) + 3f - hash01(i, 183).pow(1.6f) * (4f + 8f * (0.5f + 0.5f * sin(px * 0.09f))) * (0.4f + 0.6f * focus)
            val r = 1.0f + hash01(i, 184) * 2f + (1f - focus) * 2f
            val sun = if (night) 0.5f - (px - g.sx) / g.sr * 0.4f else 0.5f + (px - g.sx) / g.sr * 0.4f
            drawCircle(lerp(lerp(mossDark, moss, hash01(i, 185)), mossLit, hash01(i, 186) * sun), r, Offset(px, py))
        }
        // Moss sporophytes: fine stalks with capsules, only in the sharp zone near the jar's foot.
        for (i in 0 until 120) {
            val px = g.sx - g.sr * 0.8f + hash01(i, 151) * g.sr * 1.6f
            if (abs(px - g.x) < g.half + 4f) continue
            val py = g.stoneTop(px) + 2f + hash01(i, 156) * 10f
            val focus = sharpness(g, px, py)
            if (focus < 0.25f) continue
            val hgt = 8f + hash01(i, 152) * 14f
            val lean = (hash01(i, 153) - 0.3f) * 6f
            drawLine((if (night) TP.nMossLit else Color(0xFFB59050)).copy(alpha = 0.9f * focus), Offset(px, py), Offset(px + lean, py - hgt), 0.7f)
            drawOval((if (night) TP.nMossLit else Color(0xFF8A5A2E)).copy(alpha = focus), Offset(px + lean - 1.3f, py - hgt - 2.8f), Size(2.6f, 3.8f))
            if (!night) drawCircle(Color(0xFFFFE9B8).copy(alpha = 0.7f * focus), 0.7f, Offset(px + lean + 0.5f, py - hgt - 2f))
        }
        // Contact shadow of the jar, the cast shadow toward the lower left, and the caustic the glass focuses into it.
        clipPath(g.stone) {
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = if (night) 0.30f else 0.42f), Color.Transparent), Offset(g.x - 190f, g.base + 12f), 300f),
                Offset(g.x - 470f, g.base - 6f), Size(600f, 46f))
            val cc = if (night) TP.moon else TP.sun
            drawArc(Brush.radialGradient(listOf(cc.copy(alpha = if (night) 0.20f else 0.70f), cc.copy(alpha = 0f)), Offset(g.x - 260f * g.q, g.base + 12f), 120f),
                200f, 140f, false, Offset(g.x - 370f * g.q, g.base + 2f), Size(220f * g.q, 22f), style = Stroke(7f, cap = StrokeCap.Round))
            drawArc(cc.copy(alpha = if (night) 0.25f else 0.8f), 215f, 110f, false, Offset(g.x - 360f * g.q, g.base + 5f), Size(200f * g.q, 16f), style = Stroke(1.6f, cap = StrokeCap.Round))
        }
        drawOval(Color.Black.copy(alpha = 0.40f), Offset(g.x - g.half - 6f, g.base - 5f), Size(g.half * 2f + 12f, 12f))
        // The stone's front falls out of focus (a soft haze toward the bottom of the frame).
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, (if (night) TP.nDeep else lerp(TP.stoneDark, Color(0xFF2E3B26), 0.5f)).copy(alpha = 0.6f)), startY = g.base + 50f, endY = f.dh),
            Offset(0f, g.base + 50f), Size(f.dw, f.dh - g.base - 50f))
    }

    /** 1 at the focal plane (the jar's foot), falling toward 0 with distance in depth (screen y) and across. */
    private fun sharpness(g: TGeo, px: Float, py: Float): Float {
        val dy = (py - g.base) / 80f
        val dx = (px - g.x) / 560f
        return (1f - (dy * dy + dx * dx * 0.6f)).coerceIn(0f, 1f)
    }

    private fun DrawScope.soil(g: TGeo, season: Int, night: Boolean) {
        val l = g.x - g.half; val wd = g.half * 2f
        drawRect(Brush.verticalGradient(listOf(TP.soil, TP.soilDark), startY = g.ground, endY = 430f), Offset(l, g.ground - 4f), Size(wd, 434f - g.ground))
        for (i in 0 until 160) {
            val px = l + hash01(i, 7) * wd; val py = g.ground + 4f + hash01(i, 8) * 52f
            drawCircle(lerp(TP.soilDark, Color(0xFF7A5B40), hash01(i, 9)).copy(alpha = 0.7f), 0.8f + hash01(i, 10) * 2f, Offset(px, py))
        }
        drawRect(TP.charcoal, Offset(l, 432f), Size(wd, 10f))
        for (i in 0 until 40) {
            val px = l + 6f + (i % 20) * (wd / 20f) + hash01(i, 11) * 10f
            val py = 450f + (i / 20) * 12f + hash01(i, 12) * 3f
            val r = 6.5f + hash01(i, 13) * 5f
            drawOval(Brush.radialGradient(listOf(TP.pebble, TP.pebbleDark), Offset(px + r * 0.35f, py - r * 0.35f), r * 1.7f), Offset(px - r, py - r * 0.7f), Size(r * 2f, r * 1.4f))
        }
        // Pebbles on the surface.
        for (i in 0 until (if (TerraStep.planted(g.kind)) 12 else 0)) {
            val px = l + 30f + hash01(i, 14) * (wd - 60f)
            val r = 4f + hash01(i, 15) * 5f
            drawOval(Brush.radialGradient(listOf(Color(0xFFD2CEC2), Color(0xFF77736A)), Offset(px + 2f, g.ground - 3f), r * 1.5f), Offset(px - r, g.ground - r * 0.4f), Size(r * 2f, r * 1.2f))
        }
        // Driftwood branch the ivy hangs from.
        if (TerraStep.vines(g.kind)) {
            drawLine(TP.bark, Offset(g.x - g.half + 12f, 240f), Offset(g.x - 128f, 214f), 9f, cap = StrokeCap.Round)
            drawLine(Color(0xFFA8916F), Offset(g.x - g.half + 16f, 237f), Offset(g.x - 130f, 211f), 2f, cap = StrokeCap.Round)
        }
        stepSoil(g.v, night, LEAF[season], LEAF_LIT[season], PETAL[season])
    }

    /** Glass in sharp focus: refraction bands, window highlights, thick green edges, lid, frost, the moss in front. */
    private fun DrawScope.glassOverlay(g: TGeo, f: ThemeFrame, season: Int, night: Boolean) {
        val spec = if (night) TP.moon else Color.White
        val specA = if (night) 0.45f else 1f
        clipPath(g.jar) {
            // Refraction: the glass wall bends the garden near the silhouette (darker, denser bands).
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF0E1A10).copy(alpha = 0.34f), Color.Transparent), startX = g.x - g.half - 4f, endX = g.x - g.half + 34f),
                Offset(g.x - g.half - 10f, g.lipY), Size(50f, g.base - g.lipY))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF0E1A10).copy(alpha = 0.30f)), startX = g.x + g.half - 34f, endX = g.x + g.half + 4f),
                Offset(g.x + g.half - 40f, g.lipY), Size(50f, g.base - g.lipY))
            drawRect(Color(0xFFDDF0E6).copy(alpha = if (night) 0.03f else 0.06f), Offset(g.x - g.half - 10f, g.lipY), Size(g.half * 2f + 20f, g.base - g.lipY))
            // The sun's window highlight on the right: sharp outer edge, soft inner falloff.
            val hx = g.x + g.half - 52f
            drawOval(Brush.horizontalGradient(listOf(spec.copy(alpha = 0f), spec.copy(alpha = 0.30f * specA), spec.copy(alpha = 0.62f * specA)), startX = hx - 22f, endX = hx + 10f),
                Offset(hx - 22f, g.shoulder + 4f), Size(32f, 220f))
            drawOval(spec.copy(alpha = 0.20f * specA), Offset(hx + 17f, g.shoulder + 30f), Size(3.5f, 160f))
            // A broad soft reflection of the sky on the left, and a long thin streak.
            drawRect(Brush.horizontalGradient(listOf(spec.copy(alpha = 0f), spec.copy(alpha = 0.16f * specA), spec.copy(alpha = 0f)), startX = g.x - g.half + 18f, endX = g.x - g.half + 78f),
                Offset(g.x - g.half + 18f, g.shoulder - 10f), Size(60f, g.base - g.shoulder - 10f))
            drawOval(spec.copy(alpha = 0.26f * specA), Offset(g.x - g.half + 92f, g.shoulder + 40f), Size(2.4f, 170f))
            // Shoulder highlight (the curved glass catches the sky).
            if (g.kind <= 2) drawArc(Brush.horizontalGradient(listOf(spec.copy(alpha = 0f), spec.copy(alpha = 0.75f * specA)), startX = g.x, endX = g.x + g.half),
                290f, 60f, false, Offset(g.x - g.half + 10f, g.neckY - 4f), Size(g.half * 2f - 20f, 170f), style = Stroke(5f, cap = StrokeCap.Round))
            if (season == 3) {
                // Winter: frost feathers along the bottom and the sides of the glass.
                for (i in 0 until 180) {
                    val side = i % 2 == 0
                    val px = if (side) g.x - g.half + hash01(i, 21) * 50f else g.x + g.half - hash01(i, 21) * 50f
                    val py = g.shoulder + hash01(i, 22) * (g.base - g.shoulder)
                    val l = 4f + hash01(i, 23) * 10f
                    val a = hash01(i, 24) * 6.28f
                    drawLine(Color.White.copy(alpha = 0.42f), Offset(px, py), Offset(px + cos(a) * l, py + sin(a) * l), 0.8f)
                    drawLine(Color.White.copy(alpha = 0.30f), Offset(px + cos(a) * l * 0.5f, py + sin(a) * l * 0.5f), Offset(px + cos(a + 0.8f) * l * 0.9f, py + sin(a + 0.8f) * l * 0.9f), 0.6f)
                }
            }
        }
        // Thick glass reads green at its edges; the base is a heavy slab.
        drawPath(g.jar, TP.glassEdge.copy(alpha = if (night) 0.28f else 0.55f), style = Stroke(2.6f))
        drawPath(g.inner, spec.copy(alpha = 0.20f * specA), style = Stroke(1f))
        if (g.kind <= 2) drawRect(Brush.verticalGradient(listOf(TP.glassEdge.copy(alpha = 0.10f), TP.glassEdge.copy(alpha = if (night) 0.25f else 0.45f)), startY = g.base - 14f, endY = g.base),
            Offset(g.x - g.half + 16f, g.base - 14f), Size(g.half * 2f - 32f, 14f))
        if (g.kind <= 2) drawLine(spec.copy(alpha = 0.7f * specA), Offset(g.x - g.half + 30f, g.base - 13f), Offset(g.x + g.half - 30f, g.base - 13f), 1.2f)
        if (g.kind == 0) {
            // Glass lid: a thick disc with a blown knob.
            val lidL = g.x - g.neckHalf - 14f; val lidW = (g.neckHalf + 14f) * 2f
            drawOval(TP.glassEdge.copy(alpha = if (night) 0.18f else 0.30f), Offset(lidL, g.lipY - 12f), Size(lidW, 20f))
            drawOval(spec.copy(alpha = 0.55f * specA), Offset(lidL, g.lipY - 12f), Size(lidW, 20f), style = Stroke(1.4f))
            drawRect(TP.glassEdge.copy(alpha = if (night) 0.20f else 0.38f), Offset(lidL, g.lipY - 2f), Size(lidW, 6f))
            drawLine(spec.copy(alpha = 0.8f * specA), Offset(g.x + 30f, g.lipY - 9f), Offset(g.x + g.neckHalf, g.lipY - 6f), 1.6f, cap = StrokeCap.Round)
            val kc = Offset(g.x, g.lipY - 30f)
            drawLine(TP.glassEdge.copy(alpha = 0.5f), Offset(g.x, g.lipY - 12f), Offset(g.x, kc.y + 10f), 7f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF223322).copy(alpha = 0.10f), TP.glassEdge.copy(alpha = 0.55f)), Offset(kc.x - 4f, kc.y + 4f), 20f), 16f, kc)
            drawCircle(spec.copy(alpha = 0.9f * specA), 3.2f, Offset(kc.x + 6f, kc.y - 6f))
            drawArc(spec.copy(alpha = 0.6f * specA), 300f, 70f, false, Offset(kc.x - 13f, kc.y - 13f), Size(26f, 26f), style = Stroke(1.6f))
        } else vesselTop(g.v, spec, specA, TP.glassEdge, night)
        // Moss in front of the glass foot (the jar sits in it).
        val moss = if (night) TP.nMoss else TP.moss
        val mossLit = if (night) TP.nMossLit else TP.mossLit
        val mossDark = if (night) TP.nMossDark else TP.mossDark
        for (i in 0 until 700) {
            val px = g.x - g.half - 30f + hash01(i, 161) * (g.half * 2f + 60f)
            val py = g.base + 1f + hash01(i, 162).pow(1.6f) * 14f - (1f - abs(px - g.x) / (g.half + 40f)).coerceIn(0f, 1f) * 4f
            val r = 1.2f + hash01(i, 163) * 2.2f
            drawCircle(lerp(lerp(mossDark, moss, hash01(i, 164)), mossLit, hash01(i, 165) * 0.7f), r, Offset(px, py))
        }
        // A photographic vignette (the lens darkens its corners a little).
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (night) 0.45f else 0.20f)), Offset(f.dw / 2f, f.dh * 0.48f), max(f.dw, 900f) * 0.72f),
            size = Size(f.dw, f.dh))
    }

    // ------------------------------------------------------------------ moving

    /** Daylight: the sun's colour temperature follows the climate, the evening turns golden, rain greys the garden. */
    private fun DrawScope.daylight(g: TGeo, sim: TerrariumSim, f: ThemeFrame) {
        val day = 1f - sim.night
        if (day < 0.01f) return
        val w = sim.warmth
        val a = sim.lamp * day
        drawRect(g.sunPool, size = Size(f.dw, f.dh), alpha = a * (0.45f + 0.55f * max(w, 0f)))
        if (w < 0f) drawRect(g.coolPool, size = Size(f.dw, f.dh), alpha = a * -w)
        if (sim.evening > 0.01f) drawRect(g.golden, size = Size(f.dw, f.dh), alpha = sim.evening * day)
        val m = sim.mode
        if (m < -0.02f) drawRect(g.ecoPool, size = Size(f.dw, f.dh), alpha = -m * day)
        if (m > 0.02f) drawRect(g.sportPool, size = Size(f.dw, f.dh), alpha = m * day)
        if (sim.rain > 0.02f) drawRect(g.rainVeil, size = Size(f.dw, f.dh), alpha = sim.rain * day)
    }

    /** Fireflies in the garden behind: out of focus, so each is a large soft glow (≤ 0.5 Hz, never a flash). */
    private fun DrawScope.farFireflies(g: TGeo, sim: TerrariumSim) {
        if (sim.night < 0.05f) return
        for (i in g.farFireX.indices) {
            val a = sim.firefly(i + 2) * sim.night
            if (a < 0.02f) continue
            val t = sim.decoTime.toFloat()
            val x = g.farFireX[i] + sin(t * 0.13f + i) * 30f
            val y = g.farFireY[i] + cos(t * 0.11f + i * 2f) * 18f
            val r = 16f + (i % 3) * 7f
            withTransform({ translate(x, y); scale(r, r, Offset.Zero) }) { drawCircle(g.fireGlow, 1f, Offset.Zero, alpha = a * 0.8f) }
        }
    }

    /** Sunlight focused by the glass wanders over the soil (changes at 0.3 Hz). */
    private fun DrawScope.causticsOnSoil(g: TGeo, sim: TerrariumSim) {
        if (sim.reducedMotion) return
        val day = (1f - sim.night) * sim.lamp
        if (day < 0.05f) return
        val ph = sim.decoTime.toFloat() * 2f * PI.toFloat() * 0.3f * (if (sim.calm) 0.5f else 1f)
        for (i in 0 until 7) {
            val px = g.x - 150f + i * 46f + sin(ph + i) * 6f
            val py = g.ground + 6f + (i % 3) * 5f
            val a = (0.5f + 0.5f * sin(ph * 0.7f + i * 1.9f)) * 0.22f * day
            drawOval(TP.sun.copy(alpha = a), Offset(px - 12f, py - 2.5f), Size(24f, 5f))
        }
    }

    private fun DrawScope.pond(g: TGeo, sim: TerrariumSim) {
        val r = g.pond
        drawOval(TP.waterDeep, Offset(r.left, r.top), Size(r.width, r.height))
        // The surface leans (tilt + slosh): a lighter band whose slope follows the angle.
        val ang = (sim.tilt + sim.slosh() * 3.5f).coerceIn(-6f, 6f)
        clipPath(g.pondPath) {
            rotate(ang, Offset(r.center.x, r.center.y)) {
                drawRect(TP.water, Offset(r.left - 20f, r.center.y - 4f), Size(r.width + 40f, 30f))
                drawLine(Color.White.copy(alpha = 0.7f), Offset(r.left, r.center.y - 3f), Offset(r.right, r.center.y - 3f), 1.2f)
                drawLine(TP.sun.copy(alpha = 0.6f), Offset(r.center.x + 8f, r.center.y - 2f), Offset(r.right - 16f, r.center.y - 2f), 2.4f, cap = StrokeCap.Round)
            }
        }
    }

    private fun DrawScope.moss(g: TGeo, sim: TerrariumSim) {
        val gr = sim.fraction(TerrariumSim.MOSS)
        val n = (60 + gr * 220).toInt()
        val winter = sim.season == 3
        for (i in 0 until n) {
            val u = hash01(i, 50)
            val px = g.x - g.half + 16f + u * (g.half * 2f - 32f)
            // No moss on the water: the pond, and the stream's bed with the spring's stones to its left.
            if (px > g.pond.left - 100f && px < g.pond.right + 4f) continue
            val mound = sin(u * 9f) * 0.5f + 0.5f
            val py = g.ground + 4f - mound * (6f + 16f * gr) - hash01(i, 51) * 5f
            val r = 2.6f + hash01(i, 52) * 3.6f * (0.6f + 0.6f * gr)
            val base = if (winter) Color(0xFF4C7350) else TP.moss
            drawCircle(lerp(lerp(TP.mossDark, base, hash01(i, 53)), TP.mossLit, hash01(i, 54) * 0.6f), r, Offset(px, py))
        }
    }

    /** One L-system plant: cached as a bitmap per growth step, swaying around its base. */
    private fun DrawScope.plant(slot: Int, g: TGeo, sim: TerrariumSim, f: ThemeFrame, kind: PlantGen.Kind, at: Offset, plant: Int, scale: Float) {
        val growth = sim.fraction(plant)
        val gq = (growth * 24f).roundToInt()
        val rings = if (kind == PlantGen.Kind.SAPLING) sim.rings else 0
        val season = sim.season
        val key = (gq.toLong() shl 40) xor (rings.toLong() shl 32) xor (season.toLong() shl 28) xor (f.w.roundToInt().toLong() shl 12) xor f.h.roundToInt().toLong()
        val k = f.k
        val origin = plantOrigin[slot]
        val img = plants[slot].cached(key) ?: renderPlant(slot, key, kind, growth, rings, season, scale, k, f, origin)
        // Sway around the base (fan), and lean against the acceleration like the pond.
        val sw = sim.sway * sin(sim.swayPhase + slot * 1.3).toFloat() * (if (kind == PlantGen.Kind.IVY) 4f else 2.2f)
        val lean = -sim.tilt * 0.6f
        val intro = f.options.intro
        if (intro < 1f) {
            // Entrance accent: the plants rise from 98 % to 100 % out of the soil, one after another.
            val rise = 0.98f + 0.02f * Intro.out(intro, 0.15f + slot * 0.12f, 0.6f)
            withTransform({ rotate(sw + lean, at); scale(rise, rise, at) }) { drawNative(img, f, at.x + origin[0], at.y + origin[1]) }
        } else withTransform({ rotate(sw + lean, at) }) { drawNative(img, f, at.x + origin[0], at.y + origin[1]) }
    }

    private fun renderPlant(slot: Int, key: Long, kind: PlantGen.Kind, growth: Float, rings: Int, season: Int, scale: Float, k: Float, f: ThemeFrame, origin: FloatArray): ImageBitmap {
        val segs = PlantGen.segments(kind, 11 + slot * 7, growth, scale)
        var minX = 0f; var maxX = 0f; var minY = 0f; var maxY = 0f
        for (sg in segs) { minX = min(minX, min(sg.x0, sg.x1)); maxX = max(maxX, max(sg.x0, sg.x1)); minY = min(minY, min(sg.y0, sg.y1)); maxY = max(maxY, max(sg.y0, sg.y1)) }
        val pad = 16f
        val wD = (maxX - minX) + pad * 2; val hD = (maxY - minY) + pad * 2
        // Bitmap origin in design units relative to the base (y down on screen = −y of the plant).
        origin[0] = minX - pad; origin[1] = -maxY - pad
        val leaf = LEAF[season]; val lit = LEAF_LIT[season]
        val stemC = if (kind == PlantGen.Kind.SAPLING) TP.bark else TP.stem
        return plants[slot].render(key, (wD * k).roundToInt().coerceAtLeast(2), (hD * k).roundToInt().coerceAtLeast(2), f.density) {
            withTransform({ scale(k, k, Offset.Zero); translate(-(minX - pad), maxY + pad) }) {
                // Stems first (darker underside line under a lit top line: daylight from the upper right).
                for (sg in segs) if (!sg.leaf) {
                    drawLine(stemC, Offset(sg.x0, -sg.y0), Offset(sg.x1, -sg.y1), sg.w, cap = StrokeCap.Round)
                }
                for (sg in segs) if (!sg.leaf && sg.w > 1.2f) {
                    drawLine(lerp(stemC, lit, 0.3f), Offset(sg.x0 + sg.w * 0.2f, -sg.y0), Offset(sg.x1 + sg.w * 0.2f, -sg.y1), sg.w * 0.3f, cap = StrokeCap.Butt)
                }
                for (sg in segs) if (sg.leaf) {
                    val dx = sg.x1 - sg.x0; val dy = -(sg.y1 - sg.y0)
                    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(2f)
                    val ang = atan2(dy, dx) * 180f / PI.toFloat()
                    val h = PlantGen.hash(slot, sg.order)
                    val col = lerp(leaf, lit, h * 0.45f)
                    // A leaf facing the sun is brighter on its upper half; translucent leaves glow at the tip.
                    val up = (-dy / len).coerceIn(0f, 1f)
                    withTransform({ rotate(ang, Offset(sg.x0, -sg.y0)) }) {
                        drawOval(lerp(col, Color.Black, 0.18f), Offset(sg.x0, -sg.y0 - sg.w * 0.5f), Size(len * 1.3f, sg.w))
                        drawOval(lerp(col, lit, 0.35f + 0.4f * up), Offset(sg.x0 + len * 0.08f, -sg.y0 - sg.w * 0.5f), Size(len * 1.15f, sg.w * 0.55f))
                        drawLine(lerp(col, Color.White, 0.35f).copy(alpha = 0.6f), Offset(sg.x0, -sg.y0), Offset(sg.x0 + len * 1.2f, -sg.y0), 0.45f)
                    }
                }
                // Sapling: one extra ring of leaves per km milestone, around the crown.
                if (kind == PlantGen.Kind.SAPLING && rings > 0) {
                    val top = segs.maxByOrNull { it.y1 } ?: return@withTransform
                    for (r in 0 until rings) for (i in 0 until 7) {
                        val a = (i * 51f + r * 23f) * PI.toFloat() / 180f
                        val rr = 18f + r * 6f
                        val cx = top.x1 + cos(a) * rr; val cy = -top.y1 - sin(a) * rr * 0.6f
                        drawOval(lerp(leaf, lit, (i % 3) / 3f), Offset(cx - 6f, cy - 3f), Size(12f, 6f))
                        drawOval(lit.copy(alpha = 0.6f), Offset(cx - 4f, cy - 3f), Size(8f, 3f))
                    }
                }
                if (season == 0 && kind == PlantGen.Kind.SAPLING) {
                    // Spring blossoms on the sapling.
                    var i = 0
                    for (sg in segs) if (sg.leaf) { if (i % 4 == 0) { drawCircle(PETAL[0], 2.8f, Offset(sg.x1, -sg.y1)); drawCircle(Color.White.copy(alpha = 0.8f), 1.1f, Offset(sg.x1 + 0.6f, -sg.y1 - 0.6f)) }; i++ }
                }
            }
        }
    }

    /** The rosette is cached per growth step (it only changes when it grows). */
    private fun DrawScope.succulent(g: TGeo, sim: TerrariumSim, f: ThemeFrame) {
        val q = (sim.fraction(TerrariumSim.SUCCULENT) * 20f).roundToInt()
        val k = f.k
        val pad = 44f
        val key = (q.toLong() shl 32) xor (f.w.roundToInt().toLong() shl 12) xor f.h.roundToInt().toLong()
        val img = succulentLayer.cached(key) ?: run {
            val segs = PlantGen.rosette(5, q / 20f)
            succOrigin[0] = -pad; succOrigin[1] = -pad
            succulentLayer.render(key, (pad * 2f * k).roundToInt(), (pad * 2f * k).roundToInt(), f.density) {
                withTransform({ scale(k, k, Offset.Zero); translate(pad, pad) }) {
                    for (sg in segs) {
                        val dx = sg.x1; val dy = sg.y1
                        val len = sqrt(dx * dx + dy * dy) * 1.6f
                        val ang = atan2(-dy, dx) * 180f / PI.toFloat()
                        rotate(ang, Offset.Zero) {
                            drawOval(Brush.verticalGradient(listOf(Color(0xFFB9D9C6), Color(0xFF6E9C86), Color(0xFF3C6553)), startY = -sg.w * 0.8f, endY = sg.w * 0.8f),
                                Offset(0f, -sg.w * 0.8f), Size(len, sg.w * 1.6f))
                            drawOval(Color(0xFF29473A).copy(alpha = 0.6f), Offset(0f, -sg.w * 0.8f), Size(len, sg.w * 1.6f), style = Stroke(0.8f))
                            drawOval(Color(0xFFD99191).copy(alpha = 0.45f), Offset(len * 0.80f, -sg.w * 0.25f), Size(len * 0.18f, sg.w * 0.45f))
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(len * 0.2f, -sg.w * 0.45f), Offset(len * 0.75f, -sg.w * 0.45f), 0.9f)
                        }
                    }
                }
            }
        }
        drawNative(img, f, g.succulent.x + succOrigin[0], g.succulent.y + succOrigin[1])
    }

    private fun DrawScope.flowerPlant(g: TGeo, sim: TerrariumSim) {
        val at = g.flower
        val h = 76f + 100f * sim.fraction(TerrariumSim.FLOWER)
        val sw = sim.sway * sin(sim.swayPhase + 2.0).toFloat() * 2.4f - sim.tilt * 0.6f + 7f * sim.listening * (sim.beat - 0.5f) * (if (sim.reducedMotion) 0f else 1f)
        val petal = PETAL[sim.season]
        val leaf = LEAF[sim.season]; val lit = LEAF_LIT[sim.season]
        rotate(sw, at) {
            drawLine(TP.stem, at, Offset(at.x, at.y - h), 2.6f, cap = StrokeCap.Round)
            drawOval(leaf, Offset(at.x - 38f, at.y - h * 0.3f - 7f), Size(38f, 14f))
            drawOval(lit.copy(alpha = 0.7f), Offset(at.x - 34f, at.y - h * 0.3f - 7f), Size(30f, 6f))
            drawOval(leaf, Offset(at.x, at.y - h * 0.5f - 7f), Size(34f, 13f))
            drawOval(lit.copy(alpha = 0.7f), Offset(at.x + 4f, at.y - h * 0.5f - 7f), Size(26f, 5f))
            // Opened flowers along short side stems; buds (from stars) are closed and swell in over 1.5 s.
            val total = sim.blooms + sim.buds
            for (i in 0 until total) {
                val side = if (i % 2 == 0) -1f else 1f
                val yy = at.y - h + i * 16f
                val xx = at.x + side * (13f + (i % 3) * 8f)
                drawLine(TP.stem, Offset(at.x, yy + 8f), Offset(xx, yy), 1.4f)
                if (i < sim.blooms) {
                    val open = if (i == sim.blooms - 1) ((sim.since(sim.bloomAt) / 1.8).toFloat()).coerceIn(0.15f, 1f) else 1f
                    val e = 1f - (1f - open) * (1f - open)
                    for (p in 0 until 6) {
                        val a = (p * 60f + i * 17f) * PI.toFloat() / 180f
                        val px = xx + cos(a) * 5.8f * e; val py = yy + sin(a) * 4.6f * e
                        withTransform({ rotate(p * 60f + i * 17f, Offset(px, py)) }) {
                            drawOval(lerp(petal, Color(0xFF8A6A70), 0.12f), Offset(px - 5.2f * e, py - 2.6f), Size(10.4f * e, 5.2f))
                            drawOval(Color.White.copy(alpha = 0.35f), Offset(px - 3f * e, py - 2f), Size(6f * e, 2.4f))
                        }
                    }
                    drawCircle(Color(0xFFE9B53C), 3f * e, Offset(xx, yy))
                    drawCircle(Color(0xFF8A5A1E), 3f * e, Offset(xx, yy), style = hair)
                } else {
                    val newest = i == total - 1
                    val grow = if (newest) ((sim.since(sim.sproutAt) / 1.5).toFloat()).coerceIn(0f, 1f) else 1f
                    val gs = 0.3f + 0.7f * grow
                    drawOval(lerp(leaf, petal, 0.35f), Offset(xx - 4f * gs, yy - 7f * gs), Size(8f * gs, 14f * gs))
                    drawOval(Color.White.copy(alpha = 0.3f), Offset(xx - 1f * gs, yy - 6f * gs), Size(3f * gs, 8f * gs))
                }
            }
        }
    }

    /** The combo: a short bloom wave runs across the moss, tiny flowers open and close again in 4 s. */
    private fun DrawScope.bloomWave(g: TGeo, sim: TerrariumSim) {
        val t = sim.since(sim.comboAt).toFloat()
        if (t < 0f || t > 4.5f) return
        val front = t / 1.6f
        val petal = PETAL[sim.season]
        for (i in g.waveX.indices) {
            val u = i / (g.waveX.size - 1f)
            val local = ((front - u) / 0.35f).coerceIn(0f, 1f) * (1f - ((t - 3.2f) / 1.3f).coerceIn(0f, 1f))
            if (local < 0.02f) continue
            val x = g.waveX[i]
            if (x > g.pond.left - 6f && x < g.pond.right + 6f) continue
            val y = g.ground - 4f - (i % 3) * 4f
            val r = 4.2f * local
            for (p in 0 until 4) {
                val a = (p * 90f + i * 23f) * PI.toFloat() / 180f
                drawCircle(petal, r * 0.62f, Offset(x + cos(a) * r * 0.62f, y + sin(a) * r * 0.62f))
            }
            drawCircle(Color(0xFFF2CF55), r * 0.35f, Offset(x, y))
        }
    }

    private fun DrawScope.ladybug(g: TGeo, sim: TerrariumSim) {
        // A long grass blade arching from the ground; the beetle walks along it toward the blinker's side.
        drawPath(g.blade, LEAF[sim.season])
        drawPath(g.bladeRib, LEAF_LIT[sim.season].copy(alpha = 0.8f), style = hair)
        val u = 0.25f + 0.7f * sim.ladybug
        val a = g.ladyLeafA; val c = g.bladeCtrl; val b = g.ladyLeafB
        val om = 1f - u
        val x = om * om * a.x + 2f * om * u * c.x + u * u * b.x
        val y = om * om * a.y + 2f * om * u * c.y + u * u * b.y - 5f
        val step = if (sim.ladybugWalking) sin(sim.decoTime.toFloat() * 14f) * 0.8f else 0f
        drawOval(TP.beetle, Offset(x - 5f, y - 4f + step), Size(10f, 8f))
        drawOval(TP.ladybug, Offset(x - 4.5f, y - 4.2f + step), Size(9f, 7f))
        drawOval(Color.White.copy(alpha = 0.45f), Offset(x + 0.5f, y - 3.8f + step), Size(3f, 1.6f))
        drawLine(TP.beetle, Offset(x, y - 4f + step), Offset(x, y + 2.8f + step), 0.8f)
        drawCircle(TP.beetle, 1f, Offset(x - 2.2f, y - 1f + step)); drawCircle(TP.beetle, 1f, Offset(x + 2.2f, y + step))
    }

    private fun DrawScope.rainCloud(g: TGeo, sim: TerrariumSim) {
        val r = sim.cloudRain
        if (r < 0.02f) return
        val cy = g.neckY + 46f
        for (i in 0 until 7) drawCircle(Color.White.copy(alpha = 0.32f * r), 20f + (i % 3) * 7f, Offset(g.x - 72f + i * 24f, cy + (i % 2) * 7f))
        if (sim.reducedMotion) return
        for (i in 0 until 22) {
            val sp = 0.7f + hash01(i, 60) * 0.5f
            val t = ((sim.decoTime * sp + hash01(i, 61)) % 1.0).toFloat()
            val x = g.x - 80f + hash01(i, 62) * 160f
            val y = cy + 18f + t * (g.ground - cy - 30f)
            drawLine(Color(0xFFE2F1F7).copy(alpha = 0.75f * r), Offset(x, y), Offset(x, y + 7f), 1.1f)
        }
    }

    private fun DrawScope.dew(g: TGeo, sim: TerrariumSim) {
        val t = sim.since(sim.sloshAt).toFloat()
        if (t < 0f || t > 1.2f || sim.reducedMotion) return
        for (i in 0 until 5) {
            val x = g.x - 170f + i * 58f + hash01(i, 70) * 20f
            val y = g.ground - 120f + hash01(i, 71) * 40f + 0.5f * 400f * t * t
            if (y > g.ground) continue
            drawCircle(Color(0xFFE4F5FF).copy(alpha = 0.85f), 2.2f, Offset(x, y))
        }
    }

    /** Fireflies inside the jar, in focus: a small core in a soft halo (≤ 0.5 Hz, never a flash). */
    private fun DrawScope.fireflies(g: TGeo, sim: TerrariumSim) {
        for (i in 0 until TerrariumSim.FIREFLIES) {
            val a = sim.firefly(i)
            if (a < 0.02f) continue
            val t = sim.decoTime.toFloat()
            val x = g.fireX[i] + sin(t * 0.21f + i) * 22f
            val y = g.fireY[i] + cos(t * 0.17f + i * 2f) * 14f
            withTransform({ translate(x, y); scale(13f, 13f, Offset.Zero) }) { drawCircle(g.fireGlow, 1f, Offset.Zero, alpha = a, blendMode = BlendMode.Plus) }
            drawCircle(TP.firefly.copy(alpha = 0.95f * a), 2.2f, Offset(x, y))
        }
    }

    /** Condensation: a haze with droplets (dark refracting edge, bright point); the defrost clears it from below. */
    private fun DrawScope.condensation(g: TGeo, sim: TerrariumSim) {
        val c = sim.condensation
        if (c < 0.01f) return
        val clearH = (g.base - g.neckY) * sim.clear
        clipPath(g.inner) {
            clipRect(g.x - g.half, g.neckY, g.x + g.half, g.base - clearH) {
                drawRect(Color.White.copy(alpha = 0.16f * c), Offset(g.x - g.half, g.neckY), Size(g.half * 2f, g.base - g.neckY))
                val n = (g.dropX.size * c).toInt()
                for (i in 0 until n) {
                    val r = g.dropR[i]
                    val p = Offset(g.dropX[i], g.dropY[i])
                    drawCircle(Color(0xFF1C2A1C).copy(alpha = 0.16f), r, Offset(p.x, p.y + r * 0.15f))
                    drawCircle(Color.White.copy(alpha = 0.22f), r * 0.8f, p)
                    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.28f, Offset(p.x + r * 0.3f, p.y - r * 0.35f))
                }
                // A few large drops slide slowly down (they gather, run and stop; at most 30 units per second).
                if (!sim.reducedMotion) for (i in 0 until 4) {
                    val sp = (if (sim.calm) 0.5f else 1f) * (10f + i * 6f)
                    val run = 200f
                    val y0 = g.shoulder + 10f + ((sim.decoTime.toFloat() * sp + i * 60f) % run)
                    val x0 = g.x - g.half + 70f + i * 105f
                    drawLine(Color.White.copy(alpha = 0.18f * c), Offset(x0, g.shoulder + 10f), Offset(x0, y0), 2.4f, cap = StrokeCap.Round)
                    drawCircle(Color(0xFF1C2A1C).copy(alpha = 0.18f * c), 4.2f, Offset(x0, y0 + 0.6f))
                    drawCircle(Color.White.copy(alpha = 0.35f * c), 3.4f, Offset(x0, y0))
                    drawCircle(Color.White.copy(alpha = 0.9f * c), 1.2f, Offset(x0 + 1.2f, y0 - 1.4f))
                }
            }
        }
    }

    /** After arrival, a moth rests on the glass lid. */
    private fun DrawScope.moth(g: TGeo, sim: TerrariumSim) {
        val m = sim.moth
        if (m < 0.05f) return
        val mx = g.v.mothAt.x; val my = g.v.mothAt.y
        drawOval(TP.moth.copy(alpha = m), Offset(mx - 9f, my - 4f), Size(9f, 7f))
        drawOval(TP.moth.copy(alpha = m), Offset(mx, my - 4f), Size(9f, 7f))
        drawLine(Color(0xFF4A4034).copy(alpha = m), Offset(mx, my - 4f), Offset(mx, my + 3f), 1.4f)
        drawLine(Color(0xFF4A4034).copy(alpha = m), Offset(mx, my - 4f), Offset(mx - 3f, my - 8f), 0.6f)
        drawLine(Color(0xFF4A4034).copy(alpha = m), Offset(mx, my - 4f), Offset(mx + 3f, my - 8f), 0.6f)
    }
}
