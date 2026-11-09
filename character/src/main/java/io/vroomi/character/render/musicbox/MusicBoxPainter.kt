package io.vroomi.character.render.musicbox

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.musicbox.MusicBoxComposer
import io.vroomi.character.musicbox.MusicBoxSim
import io.vroomi.character.musicbox.PinNote
import io.vroomi.character.render.hash01
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.character.render.theme.drawTextCentered
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The studio palette: an off-white seamless sweep, pale linen, pastel enamel (mint and powder pink), pale birch
 * and polished silver. No warm metal anywhere; night is one warm side lamp in deep warm grey.
 */
private object Studio {
    val sweepTop = Color(0xFFE9E7E3)
    val sweepMid = Color(0xFFF6F4F0)
    val linen = Color(0xFFEDE6DA)
    val linenDeep = Color(0xFFE2D9CA)
    val weave = Color(0xFFCDBFA8)
    val mint = Color(0xFFBEE3D2)
    val mintLight = Color(0xFFE6F5EE)
    val mintShade = Color(0xFF8FC2AC)
    val pink = Color(0xFFF3CCD1)
    val pinkShade = Color(0xFFDCA7AF)
    val birch = Color(0xFFEBDDC2)
    val birchLight = Color(0xFFF7EEDD)
    val birchShade = Color(0xFFD3BF9C)
    val silver = Color(0xFFCDD2D8)
    val silverLight = Color(0xFFF7F8FA)
    val silverMid = Color(0xFFB4BAC2)
    val silverDark = Color(0xFF7E858E)
    val graphite = Color(0xFF4B5058)
    val lead = Color(0xFF8D9299)
    val pearl = Color(0xFFFFF9F2)
    val pearlGlow = Color(0xFFFFF1E6)
    val ink = Color(0xFF5E6168)
    /** Night: one low warm lamp. */
    val lamp = Color(0xFFFFC98E)
}

/** Box geometry in box units (rebuilt only when the design width changes); [S] places the box on the sweep. */
private class BoxGeo(val dw: Float) {
    val x = dw / 2f
    // Case
    val boxL = x - 480f; val boxR = x + 480f; val boxT = 110f; val boxB = 470f; val frontB = 540f
    val innerL = x - 452f; val innerR = x + 452f; val innerT = 134f; val innerB = 452f
    val plateL = x - 432f; val plateR = x + 432f; val plateT = 150f; val plateB = 438f
    // Open lid behind the box (leaning back)
    val lidT = boxT - 196f
    // Cylinder (axis along x)
    val cylL = x - 250f; val cylR = x + 290f; val cylY = 232f; val cylRad = 62f
    val pitch = (cylR - cylL) / MusicBoxComposer.TINES
    /** Where pins meet the tine tips (angle below the front, rad). */
    val contact = 62.0 * PI / 180.0
    val tipY = cylY + cylRad * sin(contact).toFloat() + 3f
    val combTop = 372f; val combBottom = 420f
    // Barrel and ratchet
    val barrelX = x - 362f; val barrelY = 300f; val barrelR = 66f
    val ratchetR = 36f
    val pawlX = x - 300f; val pawlY = 226f
    val counterX = x - 366f; val counterY = 404f
    // Governor
    val govX = x + 372f; val govTop = 186f; val govBottom = 352f
    // Glass lid
    val glassL = x - 456f; val glassR = x + 456f; val glassT = 128f; val glassB = 456f

    fun tineX(t: Int): Float = cylL + (t + 0.5f) * pitch
    val tineW = pitch * 0.56f
    /** The comb's root line slants: bass tines (left) are longer. */
    fun rootY(t: Int): Float = combTop + 10f - t * (24f / (MusicBoxComposer.TINES - 1))

    val tinePaths = Array(MusicBoxComposer.TINES) { t ->
        val cx = tineX(t); val w = tineW * (1.08f - t * 0.014f)
        val root = rootY(t) + 2f
        Path().apply {
            moveTo(cx - w / 2f, root)
            lineTo(cx - w * 0.34f, tipY + 5f)
            quadraticTo(cx, tipY - 3f, cx + w * 0.34f, tipY + 5f)
            lineTo(cx + w / 2f, root)
            close()
        }
    }
    /** Polished steel tines lit from above: a wide soft highlight, not a hard ridge. */
    val tineBrushes = Array(MusicBoxComposer.TINES) { t ->
        val cx = tineX(t); val w = tineW * (1.08f - t * 0.014f)
        Brush.horizontalGradient(0f to Studio.silverDark, 0.28f to Studio.silver, 0.5f to Studio.silverLight, 0.75f to Studio.silver, 1f to Studio.silverMid,
            startX = cx - w / 2f, endX = cx + w / 2f)
    }
    val combBase = Path().apply {
        moveTo(cylL - 8f, rootY(0) + 2f); lineTo(cylR + 8f, rootY(MusicBoxComposer.TINES - 1) + 2f)
        lineTo(cylR + 8f, combBottom); lineTo(cylL - 8f, combBottom); close()
    }
    /** Polished silver cylinder: a broad top reflection of the softbox, a soft dark band below. */
    val cylBrush = Brush.verticalGradient(
        0f to Studio.silverMid, 0.16f to Studio.silverLight, 0.34f to Color.White, 0.5f to Studio.silver, 0.78f to Studio.silverMid, 1f to Studio.silverDark,
        startY = cylY - cylRad, endY = cylY + cylRad,
    )
    /** The softbox seen in the glass lid: one large, very soft rectangle. */
    val glassSheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0f), 0.18f to Color.White.copy(alpha = 0.16f), 0.34f to Color.White.copy(alpha = 0.10f), 0.5f to Color.White.copy(alpha = 0f),
        start = Offset(glassL, glassT), end = Offset(glassL + 420f, glassT + 260f),
    )
    val ecoPool = Brush.radialGradient(listOf(Studio.mint.copy(alpha = 0.22f), Studio.mint.copy(alpha = 0f)), Offset(x, 300f), 620f)
    val sportPool = Brush.radialGradient(listOf(Studio.pink.copy(alpha = 0.24f), Studio.pink.copy(alpha = 0f)), Offset(x, 300f), 620f)

    // Rain drops on the lid: fixed positions (deterministic).
    val dropX = FloatArray(DROPS) { glassL + 20f + hash01(it, 11) * (glassR - glassL - 40f) }
    val dropY = FloatArray(DROPS) { glassT + 14f + hash01(it, 12) * (glassB - glassT - 28f) }
    val dropR = FloatArray(DROPS) { 2.2f + hash01(it, 13) * 4.2f }
    val dropOrder = FloatArray(DROPS) { hash01(it, 14) }

    val spiral = Path()

    /** The open lid: a tall panel leaning back, a little narrower at the top. */
    val lidOuter = Path().apply {
        moveTo(boxL + 6f, boxT + 8f); lineTo(boxL + 34f, lidT); lineTo(boxR - 34f, lidT); lineTo(boxR - 6f, boxT + 8f); close()
    }
    val lidLining = Path().apply {
        moveTo(boxL + 34f, boxT + 2f); lineTo(boxL + 56f, lidT + 22f); lineTo(boxR - 56f, lidT + 22f); lineTo(boxR - 34f, boxT + 2f); close()
    }

    /** The whole box seen from the front (lid and case), for light that falls on it. */
    val silhouette = Path().apply {
        addPath(lidOuter)
        addRoundRect(androidx.compose.ui.geometry.RoundRect(boxL, boxT, boxR, frontB, CornerRadius(18f)))
    }

    // The box sits on the sweep at scale [S], its front bottom edge at [FLOOR].
    fun sx(u: Float) = x + (u - x) * S
    fun sy(v: Float) = FLOOR + (v - frontB) * S

    companion object {
        const val DROPS = 70
        const val S = 0.78f
        const val FLOOR = 556f
    }
}

/** The music box as a high-key studio product shot. Static parts are one cached bitmap; pins, tines and governor move. */
internal class MusicBoxPainter : ThemePainter<MusicBoxSim>() {
    override val minDesignWidth = 1040f
    override val backdrop = Color(0xFFEDEAE5)

    private var geo: BoxGeo? = null
    private val base = LayerSlot(SceneTheme.MUSIC_BOX)
    private val thin = Stroke(1.2f)
    private val hair = Stroke(0.8f)
    private val springStroke = Stroke(2.4f, cap = StrokeCap.Round)

    private var textsOf: ThemeTexts? = null
    private var plateText: TextLayoutResult? = null

    /** 0 parked .. 1 moving (linear, 0.3 s): words that carry information hide while the car moves. */
    private var drive = 0f
    private var driveT = -1.0

    private fun drivingFade(moving: Boolean, time: Double): Float {
        val target = if (moving) 1f else 0f
        drive = if (driveT < 0.0) target else {
            val step = ((time - driveT) / 0.3).toFloat().coerceIn(0f, 1f)
            if (drive < target) minOf(target, drive + step) else maxOf(target, drive - step)
        }
        driveT = time
        return drive
    }

    private fun geo(dw: Float): BoxGeo {
        val g = geo
        if (g != null && abs(g.dw - dw) < 0.5f) return g
        return BoxGeo(dw).also { geo = it }
    }

    private fun texts(tx: ThemeTexts) {
        if (textsOf === tx) return
        textsOf = tx
        plateText = tx.text.styled("mbx-plate", "no. 18", 17f, Studio.ink, FontFamily.SansSerif, FontWeight.Light, 0.36f)
    }

    override fun tap(sim: MusicBoxSim, x: Float, y: Float) { sim.closeView = !sim.closeView }

    override fun drawPx(s: DrawScope, sim: MusicBoxSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        if (sim.zoom > 0.001f) return // the close view draws everything in design units
        texts(tx)
        val g = geo(f.dw)
        val w = f.w.roundToInt(); val h = f.h.roundToInt()
        val key = (w.toLong() shl 20) xor h.toLong()
        val k = f.k
        val bmp = base.get(key, w, h, f.density) {
            withTransform({ scale(k, k, Offset.Zero) }) { staticLayer(g, f, tx) }
        }
        s.drawImage(bmp, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(bmp.width, bmp.height))
    }

    /** The entrance accent of this frame ([io.vroomi.character.render.SceneOptions.intro]). */
    private var intro = 1f

    override fun draw(s: DrawScope, sim: MusicBoxSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        intro = f.options.intro
        drivingFade(sim.moving, sim.time)
        texts(tx)
        val g = geo(f.dw)
        val z = sim.zoom
        if (z > 0.001f) {
            // Close view: the camera moves in toward the comb and the pins (parked only).
            val e = z * z * (3f - 2f * z)
            val sc = 1f + 1.6f * e
            val px = g.sx(g.x - 40f); val py = g.sy(g.tipY - 20f)
            withTransform({ translate((f.cx - px) * e, (f.cy - py) * e); scale(sc, sc, Offset(px, py)) }) {
                staticLayer(g, f, tx)
                scene(g, sim, f, tx)
            }
        } else scene(g, sim, f, tx)
    }

    private fun DrawScope.boxSpace(g: BoxGeo, block: DrawScope.() -> Unit) =
        withTransform({ translate(0f, BoxGeo.FLOOR - g.frontB); scale(BoxGeo.S, BoxGeo.S, Offset(g.x, g.frontB)) }, block)

    private fun DrawScope.scene(g: BoxGeo, sim: MusicBoxSim, f: ThemeFrame, tx: ThemeTexts) {
        boxSpace(g) {
            pins(g, sim)
            tines(g, sim)
            damper(g, sim)
            barrel(g, sim)
            governor(g, sim)
            keyPlate(g, sim, tx)
            arpeggio(g, sim)
            glass(g, sim)
        }
        light(g, sim, f)
    }

    // ------------------------------------------------------------------ static (cached)

    private fun DrawScope.staticLayer(g: BoxGeo, f: ThemeFrame, tx: ThemeTexts) {
        // Seamless sweep: the wall curves into the linen without a horizon line; the softbox brightens the middle.
        val horizon = 430f
        drawRect(Brush.verticalGradient(0f to Studio.sweepTop, 0.55f to Studio.sweepMid, 0.8f to Studio.linen, 1f to Studio.linenDeep, startY = 0f, endY = f.dh), size = Size(f.dw, f.dh))
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)), Offset(g.x, 120f), max(f.dw, 900f) * 0.55f), size = Size(f.dw, f.dh))
        // Linen weave on the floor, fading out where the sweep rises.
        var y = horizon
        var i = 0
        while (y < f.dh) {
            val a = ((y - horizon) / 120f).coerceIn(0f, 1f) * 0.16f
            drawLine(Studio.weave.copy(alpha = a * (0.6f + 0.4f * hash01(i, 7))), Offset(0f, y), Offset(f.dw, y), 0.6f)
            y += 3.2f; i++
        }
        var x = 0f
        i = 0
        while (x < f.dw) {
            drawLine(Studio.weave.copy(alpha = 0.06f + 0.04f * hash01(i, 9)), Offset(x, horizon + 60f), Offset(x, f.dh), 0.6f)
            x += 3.6f; i++
        }
        // Gentle contact shadow: wide and faint, darkest right under the front edge.
        val sl = g.sx(g.boxL); val sr = g.sx(g.boxR)
        drawOval(Brush.radialGradient(listOf(Color(0xFF8F8576).copy(alpha = 0.30f), Color(0xFF8F8576).copy(alpha = 0f)), Offset(g.x, BoxGeo.FLOOR + 2f), (sr - sl) * 0.62f),
            Offset(sl - 80f, BoxGeo.FLOOR - 40f), Size(sr - sl + 160f, 80f))
        drawRect(Color(0xFF7A7064).copy(alpha = 0.22f), Offset(sl + 6f, BoxGeo.FLOOR - 4f), Size(sr - sl - 12f, 6f))
        boxSpace(g) { box(g, tx) }
        windingKey(g.sx(g.boxR) + 64f, BoxGeo.FLOOR - 12f)
    }

    /** A polished silver winding key lying on the linen beside the box (the studio's one prop). */
    private fun DrawScope.windingKey(kx: Float, ky: Float) {
        drawOval(Color(0xFF8F8576).copy(alpha = 0.22f), Offset(kx - 50f, ky + 6f), Size(120f, 14f))
        withTransform({ rotate(-10f, Offset(kx, ky)) }) {
            drawRoundRect(Brush.verticalGradient(listOf(Studio.silverLight, Studio.silverMid), startY = ky - 4f, endY = ky + 4f), Offset(kx - 4f, ky - 4f), Size(64f, 8f), CornerRadius(4f))
            drawRoundRect(Studio.silverMid, Offset(kx + 56f, ky - 7f), Size(14f, 14f), CornerRadius(3f))
            for (sgn in listOf(-1f, 1f)) {
                val top = ky + (if (sgn < 0) -30f else 2f)
                drawOval(Brush.radialGradient(listOf(Color.White, Studio.silver, Studio.silverMid), Offset(kx - 30f, top + 8f), 34f), Offset(kx - 46f, top), Size(46f, 28f))
                drawOval(Studio.pink, Offset(kx - 34f, top + 8f), Size(22f, 12f))
            }
        }
    }

    private fun DrawScope.box(g: BoxGeo, tx: ThemeTexts) {
        // Open lid: mint enamel outside edge, a birch frame and a powder pink lining facing the camera.
        drawPath(g.lidOuter, Brush.verticalGradient(listOf(Studio.mintLight, Studio.mint, Studio.mintShade), startY = g.lidT, endY = g.boxT))
        drawPath(g.lidLining, Brush.verticalGradient(listOf(lerp(Studio.pink, Color.White, 0.35f), Studio.pink, Studio.pinkShade), startY = g.lidT, endY = g.boxT))
        drawPath(g.lidLining, Studio.birch, style = Stroke(10f))
        // The softbox seen in the satin lining: one large, very soft diagonal reflection.
        clipPath(g.lidLining) {
            drawRect(Brush.linearGradient(0f to Color.White.copy(alpha = 0f), 0.35f to Color.White.copy(alpha = 0.32f), 0.6f to Color.White.copy(alpha = 0f),
                start = Offset(g.boxL + 60f, g.lidT), end = Offset(g.boxL + 520f, g.boxT)), Offset(g.boxL, g.lidT), Size(g.boxR - g.boxL, g.boxT - g.lidT))
        }
        // A soft quilted seam on the lining.
        for (k in 1..3) {
            val yy = g.lidT + 22f + k * 44f
            drawLine(Color.White.copy(alpha = 0.35f), Offset(g.boxL + 70f + k * 4f, yy), Offset(g.boxR - 70f - k * 4f, yy), 1.2f)
        }
        // Case: mint enamel front with a broad soft reflection.
        drawRoundRect(Brush.verticalGradient(0f to Studio.mintLight, 0.35f to Studio.mint, 1f to Studio.mintShade, startY = g.boxB - 12f, endY = g.frontB),
            Offset(g.boxL, g.boxB - 12f), Size(g.boxR - g.boxL, g.frontB - g.boxB + 12f), CornerRadius(18f))
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(g.boxL + 40f, g.boxB + 4f), Size(g.boxR - g.boxL - 80f, 12f), CornerRadius(6f))
        // Birch rim (pale, with a faint straight grain).
        drawRoundRect(Brush.verticalGradient(listOf(Studio.birchLight, Studio.birch, Studio.birchShade), startY = g.boxT, endY = g.boxB),
            Offset(g.boxL, g.boxT), Size(g.boxR - g.boxL, g.boxB - g.boxT), CornerRadius(18f))
        for (i in 0 until 26) {
            val yy = g.boxT + 4f + hash01(i, 21) * (g.boxB - g.boxT - 8f)
            drawLine(Studio.birchShade.copy(alpha = 0.22f), Offset(g.boxL + 8f, yy), Offset(g.boxR - 8f, yy + (hash01(i, 22) - 0.5f) * 6f), 0.6f)
        }
        // Powder pink lined well and the polished silver bedplate.
        drawRoundRect(Studio.pink, Offset(g.innerL, g.innerT), Size(g.innerR - g.innerL, g.innerB - g.innerT), CornerRadius(12f))
        drawRoundRect(Studio.pinkShade.copy(alpha = 0.6f), Offset(g.innerL, g.innerT), Size(g.innerR - g.innerL, 8f), CornerRadius(6f))
        drawRoundRect(Brush.linearGradient(0f to Studio.silverLight, 0.4f to Studio.silver, 0.62f to Studio.silverLight, 1f to Studio.silverMid, start = Offset(g.plateL, g.plateT), end = Offset(g.plateR, g.plateB)),
            Offset(g.plateL, g.plateT), Size(g.plateR - g.plateL, g.plateB - g.plateT), CornerRadius(10f))
        drawRoundRect(Studio.silverDark.copy(alpha = 0.35f), Offset(g.plateL, g.plateT), Size(g.plateR - g.plateL, g.plateB - g.plateT), CornerRadius(10f), style = Stroke(1.4f))
        // Bedplate screws: tiny silver domes.
        for ((sx, sy) in listOf(g.plateL + 16f to g.plateT + 16f, g.plateR - 16f to g.plateT + 16f, g.plateL + 16f to g.plateB - 16f, g.plateR - 16f to g.plateB - 16f)) dome(sx, sy, 5f)

        // Cylinder bearings and the cylinder body.
        for (bx in listOf(g.cylL - 22f, g.cylR + 22f)) {
            drawRoundRect(Brush.horizontalGradient(listOf(Studio.silverMid, Studio.silverLight, Studio.silver), startX = bx - 12f, endX = bx + 12f),
                Offset(bx - 12f, g.cylY - 18f), Size(24f, 120f), CornerRadius(8f))
            dome(bx, g.cylY, 6f)
        }
        drawRect(g.cylBrush, Offset(g.cylL, g.cylY - g.cylRad), Size(g.cylR - g.cylL, g.cylRad * 2f))
        for (ex in listOf(g.cylL, g.cylR)) {
            drawOval(Brush.verticalGradient(listOf(Studio.silverLight, Studio.silverMid), startY = g.cylY - g.cylRad, endY = g.cylY + g.cylRad),
                Offset(ex - 7f, g.cylY - g.cylRad), Size(14f, g.cylRad * 2f))
        }
        drawOval(Brush.verticalGradient(listOf(Studio.silverLight, Studio.silverDark), startY = g.cylY - g.cylRad - 14f, endY = g.cylY + g.cylRad + 14f),
            Offset(g.cylR + 4f, g.cylY - g.cylRad - 14f), Size(12f, (g.cylRad + 14f) * 2f))
        // Comb base: a satin silver block, three domed screws, lead weights under the bass tines.
        drawPath(g.combBase, Brush.verticalGradient(listOf(Studio.silverLight, Studio.silver, Studio.silverMid), startY = g.combTop - 14f, endY = g.combBottom))
        drawLine(Color.White, Offset(g.cylL - 8f, g.rootY(0) + 3f), Offset(g.cylR + 8f, g.rootY(MusicBoxComposer.TINES - 1) + 3f), 1.2f)
        drawLine(Studio.silverDark.copy(alpha = 0.5f), Offset(g.cylL - 8f, g.combBottom), Offset(g.cylR + 8f, g.combBottom), 1.2f)
        for (t in 0 until 3) dome(g.cylL + 40f + t * (g.cylR - g.cylL - 80f) / 2f, (g.combTop + g.combBottom) / 2f + 10f, 6f)
        for (t in 0 until 6) {
            val cx = g.tineX(t)
            drawRoundRect(Studio.lead, Offset(cx - g.tineW * 0.75f, g.rootY(t) - 22f), Size(g.tineW * 1.5f, 16f), CornerRadius(4f))
        }
        // Barrel drum (silver) and the governor bracket.
        drawCircle(Brush.radialGradient(listOf(Studio.silverLight, Studio.silver, Studio.silverMid), Offset(g.barrelX - 20f, g.barrelY - 24f), g.barrelR * 1.3f), g.barrelR, Offset(g.barrelX, g.barrelY))
        drawCircle(Color(0xFFE4E7EA), g.barrelR - 14f, Offset(g.barrelX, g.barrelY))
        drawCircle(Studio.silverMid, g.barrelR - 14f, Offset(g.barrelX, g.barrelY), style = Stroke(1.2f))
        drawRoundRect(Brush.horizontalGradient(listOf(Studio.silverMid, Studio.silverLight, Studio.silver), startX = g.govX - 30f, endX = g.govX + 30f),
            Offset(g.govX - 30f, g.govBottom - 6f), Size(60f, 22f), CornerRadius(8f))
        drawRoundRect(Studio.silver, Offset(g.govX - 26f, g.govTop + 26f), Size(52f, 10f), CornerRadius(5f))
        drawCircle(Color(0xFFE4E7EA), 20f, Offset(g.counterX, g.counterY))
        // The enamel maker's plate on the front: "no. 18" in thin lowercase, widely spaced.
        val pw = 150f; val ph = 30f; val pcx = g.x + 300f; val pcy = 506f
        drawRoundRect(Studio.mintShade, Offset(pcx - pw / 2f, pcy - ph / 2f + 2f), Size(pw, ph), CornerRadius(ph / 2f))
        drawRoundRect(Color.White, Offset(pcx - pw / 2f, pcy - ph / 2f), Size(pw, ph), CornerRadius(ph / 2f))
        plateText?.let { drawTextCentered(it, pcx, pcy) }
        // Key plaque: a powder pink enamel oval on the front.
        drawOval(Studio.pinkShade, Offset(g.x - 40f, 490f), Size(80f, 36f))
        drawOval(Studio.pink, Offset(g.x - 40f, 488f), Size(80f, 36f))
        // Inner glass lid: a thin silver frame.
        drawRoundRect(Studio.silverMid.copy(alpha = 0.8f), Offset(g.glassL, g.glassT), Size(g.glassR - g.glassL, g.glassB - g.glassT), CornerRadius(12f), style = Stroke(2.4f))
    }

    /** A small polished silver dome (screw head or pivot): soft top light, no slot. */
    private fun DrawScope.dome(x: Float, y: Float, r: Float) {
        drawCircle(Studio.silverDark.copy(alpha = 0.25f), r, Offset(x + 0.6f, y + 1.2f))
        drawCircle(Brush.radialGradient(listOf(Color.White, Studio.silver, Studio.silverMid), Offset(x - r * 0.3f, y - r * 0.4f), r * 1.4f), r, Offset(x, y))
    }

    // ------------------------------------------------------------------ moving parts

    /** Pins on the visible face of the cylinder: angle from the musical position (8 bars per revolution). */
    private fun DrawScope.pins(g: BoxGeo, sim: MusicBoxSim) {
        // Entrance accent: a playing cylinder spins up from rest. It starts ahead by what a linear speed ramp
        // over the entrance loses, so the pins never turn backwards: lead = tempo x D x (1 - intro)^2 / 2.
        val now = if (intro < 1f && sim.playing) {
            val left = (1.0 - intro)
            sim.visBeats + sim.tempo / 60.0 * SPIN_UP_S * left * left / 2.0
        } else sim.visBeats
        // Reward: the pins at the plucking line light pearl-white for a moment.
        val st = sim.since(sim.starAt).toFloat()
        val pearlNow = if (st in 0f..1.6f && !sim.calm) (if (st < 0.1f) st / 0.1f else exp(-(st - 0.1f) / 0.6f)) else 0f
        val bar = kotlin.math.floor(now / 4.0).toInt()
        clipRect(g.cylL, g.cylY - g.cylRad, g.cylR, g.cylY + g.cylRad) {
            for (b in bar - 2..bar + MusicBoxSim.LOOKAHEAD) {
                val list = sim.pinsOf(b) ?: continue
                for (i in list.indices) {
                    val p = list[i]
                    val pb = b * 4.0 + p.slot / 4.0
                    val theta = g.contact - (pb - now) / (4.0 * MusicBoxSim.BARS_PER_REV) * 2 * PI
                    val c = cos(theta).toFloat()
                    if (c < 0.06f) continue
                    val y = g.cylY + g.cylRad * sin(theta).toFloat()
                    val x = g.tineX(p.tine)
                    val r = 3.2f
                    val h = 1.6f + 5.2f * c
                    val shade = (0.4f + 0.6f * c).coerceIn(0f, 1f)
                    val near = abs(theta - g.contact) < 0.3
                    val grace = p.kind == PinNote.GRACE
                    val pearl = if (grace) 1f else if (near) pearlNow else 0f
                    if (pearl > 0.02f) drawCircle(Studio.pink.copy(alpha = 0.5f * pearl * c), 9f, Offset(x, y))
                    drawOval(Studio.silverDark.copy(alpha = 0.35f * c), Offset(x - r + 1f, y - h / 2f + 1.8f), Size(r * 2f + 0.6f, h))
                    drawOval(lerp(lerp(Studio.graphite, Studio.silverDark, shade * 0.6f), Studio.pearl, pearl), Offset(x - r, y - h / 2f), Size(r * 2f, h))
                    drawOval(lerp(Studio.silver, Studio.pearl, pearl), Offset(x - r * 0.5f, y - h / 2f + 0.3f), Size(r * 1.0f, h * 0.45f))
                }
            }
        }
    }

    /** Tines: still steel; a plucked tine shows a soft damped envelope (2 Hz or less, never its real frequency). */
    private fun DrawScope.tines(g: BoxGeo, sim: MusicBoxSim) {
        val calmK = if (sim.calm) 0.5f else 1f
        for (t in 0 until MusicBoxComposer.TINES) {
            val a = if (sim.reducedMotion) 0f else sim.amp[t] * calmK
            val age = (sim.decoTime - sim.pluckAt[t]).toFloat()
            val sway = if (a > 0.01f) a * 2.6f * sin(2f * PI.toFloat() * 1.6f * age) else 0f
            val cx = g.tineX(t)
            if (a > 0.02f) drawLine(Color.White.copy(alpha = 0.30f * a), Offset(cx, g.combTop), Offset(cx + sway, g.tipY + 2f), g.tineW + 6f * a, cap = StrokeCap.Round)
            withTransform({ if (sway != 0f) translate(sway * 0.35f, 0f) }) {
                drawPath(g.tinePaths[t], g.tineBrushes[t])
                drawPath(g.tinePaths[t], Studio.silverDark.copy(alpha = 0.7f), style = hair)
            }
            // Pearl glint at the tip: 40 ms in, 400 ms out (flash-guarded in the simulation).
            val ga = (sim.decoTime - sim.glintAt[t]).toFloat()
            if (ga in 0f..1.2f && !sim.calm) {
                val e = if (ga < 0.04f) ga / 0.04f else exp(-(ga - 0.04f) / 0.4f)
                drawCircle(Studio.pearl.copy(alpha = 0.7f * e), 5f + 3f * e, Offset(cx, g.tipY + 2f))
            }
        }
    }

    /** Combo: a short arpeggio of lit pins along the plucking line, bass to treble. */
    private fun DrawScope.arpeggio(g: BoxGeo, sim: MusicBoxSim) {
        if (sim.calm) return
        val t = sim.since(sim.comboAt).toFloat()
        if (t < 0f || t > 1.8f) return
        val y = g.cylY + g.cylRad * sin(g.contact).toFloat()
        for (k in 0 until ARPEGGIO.size) {
            val s = t - k * 0.14f
            if (s < 0f || s > 0.9f) continue
            val e = if (s < 0.06f) s / 0.06f else exp(-(s - 0.06f) / 0.3f)
            val x = g.tineX(ARPEGGIO[k])
            drawCircle(Studio.pink.copy(alpha = 0.55f * e), 11f, Offset(x, y))
            drawCircle(Studio.pearl.copy(alpha = e), 4.6f, Offset(x, y))
            drawCircle(Studio.silverDark.copy(alpha = 0.5f * e), 4.6f, Offset(x, y), style = hair)
        }
    }

    /** Harsh braking: a powder pink felt damper presses the tines for a moment. */
    private fun DrawScope.damper(g: BoxGeo, sim: MusicBoxSim) {
        val t = sim.since(sim.damperAt).toFloat()
        if (t < 0f || t > 1.4f) return
        val a = if (t < 0.15f) t / 0.15f else (1f - (t - 0.15f) / 1.25f)
        drawRoundRect(Studio.pink.copy(alpha = 0.95f * a), Offset(g.cylL, g.tipY + 10f), Size(g.cylR - g.cylL, 16f), CornerRadius(8f))
        drawRoundRect(Studio.pinkShade.copy(alpha = 0.8f * a), Offset(g.cylL, g.tipY + 22f), Size(g.cylR - g.cylL, 4f), CornerRadius(2f))
    }

    /** The spring barrel: mainspring coils (tight when wound), ratchet, pawl, click spring and the km counter. */
    private fun DrawScope.barrel(g: BoxGeo, sim: MusicBoxSim) {
        val wound = sim.springFraction
        val inner = 12f; val outer = g.barrelR - 18f
        val r0 = inner + (1f - wound) * (outer - 24f - inner)
        val r1 = r0 + 20f + 4f * wound
        val turns = 6
        val p = g.spiral
        p.reset()
        val steps = turns * 28
        val rot = sim.drumAngle.toFloat()
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val ang = u * turns * 2f * PI.toFloat() + rot
            val r = r0 + (r1 - r0) * u
            val px = g.barrelX + cos(ang) * r; val py = g.barrelY + sin(ang) * r
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        drawPath(p, Studio.silverMid, style = springStroke)
        drawPath(p, Color.White.copy(alpha = 0.6f), style = hair)
        val arbor = sim.arborAngle.toFloat()
        rotate(arbor * 180f / PI.toFloat(), Offset(g.barrelX, g.barrelY)) {
            val teeth = MusicBoxSim.RATCHET_TEETH
            for (i in 0 until teeth) {
                val a0 = i * 2f * PI.toFloat() / teeth
                val a1 = a0 + 2f * PI.toFloat() / teeth * 0.75f
                drawLine(Studio.silverMid, Offset(g.barrelX + cos(a0) * (g.ratchetR - 6f), g.barrelY + sin(a0) * (g.ratchetR - 6f)),
                    Offset(g.barrelX + cos(a1) * g.ratchetR, g.barrelY + sin(a1) * g.ratchetR), 2.2f)
            }
            drawCircle(Studio.silver, g.ratchetR - 6f, Offset(g.barrelX, g.barrelY))
            drawCircle(Studio.silverMid, g.ratchetR - 6f, Offset(g.barrelX, g.barrelY), style = thin)
            drawRect(Studio.silverDark, Offset(g.barrelX - 5f, g.barrelY - 5f), Size(10f, 10f))
        }
        val pt = sim.since(sim.pawlAt).toFloat()
        val lift = if (pt in 0f..0.25f) sin(pt / 0.25f * PI.toFloat()) * 6f else 0f
        val tipX = g.barrelX + 18f; val tipY = g.barrelY - g.ratchetR + 2f - lift
        drawLine(Studio.silver, Offset(g.pawlX, g.pawlY), Offset(tipX, tipY), 5f, cap = StrokeCap.Round)
        drawLine(Studio.silverDark.copy(alpha = 0.6f), Offset(g.pawlX, g.pawlY), Offset(tipX, tipY), 1f)
        dome(g.pawlX, g.pawlY, 6f)
        val ct = sim.since(sim.clickSpringAt).toFloat()
        val flex = if (ct in 0f..0.3f) sin(ct / 0.3f * PI.toFloat()) * 5f else 0f
        drawLine(Studio.silverMid, Offset(g.pawlX + 4f, g.pawlY - 4f), Offset(g.pawlX + 30f, g.pawlY - 18f - flex), 1.8f, cap = StrokeCap.Round)
        val ca = sim.counter * 36f + run { val t = sim.since(sim.counterAt).toFloat(); if (t in 0f..0.4f) -36f * (1f - t / 0.4f) else 0f }
        rotate(ca, Offset(g.counterX, g.counterY)) {
            for (i in 0 until 10) {
                val a = i * 36f * PI.toFloat() / 180f
                drawLine(Studio.silverMid, Offset(g.counterX + cos(a) * 6f, g.counterY + sin(a) * 6f), Offset(g.counterX + cos(a) * 16f, g.counterY + sin(a) * 16f), 2.4f, cap = StrokeCap.Round)
            }
            drawCircle(Studio.mint, 6f, Offset(g.counterX, g.counterY))
        }
    }

    /** Governor: endless screw and a two-blade air-brake fan (a soft disc above 2 rev/s). */
    private fun DrawScope.governor(g: BoxGeo, sim: MusicBoxSim) {
        val x = g.govX
        drawLine(Studio.silverMid, Offset(x, g.govTop + 10f), Offset(x, g.govBottom), 4f)
        val ph = ((sim.fanAngle / (2 * PI)) % 1.0).toFloat()
        var y = g.govTop + 60f + ph * 9f
        while (y < g.govBottom - 20f) {
            drawLine(Studio.silverLight, Offset(x - 9f, y), Offset(x + 9f, y + 5f), 2.2f)
            drawLine(Studio.silverDark.copy(alpha = 0.5f), Offset(x - 9f, y + 1.6f), Offset(x + 9f, y + 6.6f), 0.8f)
            y += 9f
        }
        val rps = sim.fanRps.toFloat()
        val fy = g.govTop + 8f
        if (rps > 2f) {
            val a = ((rps - 2f) / 0.6f).coerceIn(0f, 1f)
            drawOval(Studio.silverLight.copy(alpha = 0.35f + 0.2f * a), Offset(x - 60f, fy - 11f), Size(120f, 22f))
        }
        if (rps <= 2.3f) {
            val c = cos(sim.fanAngle).toFloat()
            val sn = sin(sim.fanAngle).toFloat()
            val bw = 58f * abs(c)
            val alpha = if (rps > 2f) 1f - (rps - 2f) / 0.3f else 1f
            val side = if (c >= 0f) 1f else -1f
            drawRoundRect(lerp(Studio.silver, Studio.silverLight, (sn + 1f) / 2f).copy(alpha = alpha), Offset(x, fy - 11f), Size(bw * side, 22f), CornerRadius(8f))
            drawRoundRect(lerp(Studio.silverMid, Studio.silver, (1f - sn) / 2f).copy(alpha = alpha), Offset(x, fy - 11f), Size(-bw * side, 22f), CornerRadius(8f))
        }
        dome(x, fy, 7f)
    }

    /**
     * The key plaque: the key letter in a thin face; it glows softly after a key change. The letter is information
     * too small for the driving minimum (24 sp), so it hides while the car moves (the "no. 18" maker's plate stays).
     */
    private fun DrawScope.keyPlate(g: BoxGeo, sim: MusicBoxSim, tx: ThemeTexts) {
        val t = sim.since(sim.keyChangeAt).toFloat()
        if (t in 0f..2f) drawOval(Studio.pearl.copy(alpha = 0.6f * (1f - t / 2f)), Offset(g.x - 46f, 484f), Size(92f, 44f))
        val a = 1f - drive
        if (a > 0.01f) drawTextCentered(tx.get(sim.keyName, 22f, Studio.ink), g.x, 506f, alpha = a)
    }

    /** The inner glass lid: the softbox reflection, rain drops, mist that clears from the bottom while defrosting. */
    private fun DrawScope.glass(g: BoxGeo, sim: MusicBoxSim) {
        drawRect(g.glassSheen, Offset(g.glassL, g.glassT), Size(g.glassR - g.glassL, g.glassB - g.glassT))
        val fog = sim.fog
        if (fog > 0.01f) {
            val clearH = (g.glassB - g.glassT) * sim.clear
            val top = g.glassT; val bottom = g.glassB - clearH
            if (bottom > top) drawRect(Color.White.copy(alpha = 0.42f * fog), Offset(g.glassL, top), Size(g.glassR - g.glassL, bottom - top))
            if (clearH > 2f) for (i in 1..3) drawRect(Color.White.copy(alpha = 0.10f * fog * (4 - i)), Offset(g.glassL, bottom + (i - 1) * 5f), Size(g.glassR - g.glassL, 5f))
        }
        val rain = sim.rain
        if (rain > 0.01f) {
            for (i in 0 until BoxGeo.DROPS) {
                if (g.dropOrder[i] > rain) continue
                val x = g.dropX[i]; val y = g.dropY[i]
                val r = g.dropR[i]
                drawCircle(Studio.silverDark.copy(alpha = 0.16f), r, Offset(x + 0.6f, y + 1.2f))
                drawCircle(Color.White.copy(alpha = 0.35f), r, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.95f), r * 0.3f, Offset(x - r * 0.35f, y - r * 0.38f))
            }
        }
    }

    /**
     * Room light. Night multiplies the whole shot by a deep warm grey that is lighter on the lamp side (less
     * light, warmer light), then the low lamp at the left adds its warm pool. ECO and SPORT tint the light a little.
     */
    private fun DrawScope.light(g: BoxGeo, sim: MusicBoxSim, f: ThemeFrame) {
        val n = sim.night
        if (n > 0.01f) {
            nightLayers(g, f)
            drawRect(dimmer!!, size = Size(f.dw, f.dh), alpha = n, blendMode = BlendMode.Multiply)
            // The lamp sits low at the left: a flattened warm pool on the linen and the wall above it.
            withTransform({ scale(1f, 0.55f, lampAt) }) { drawCircle(lampPool!!, f.dw * 0.32f, lampAt, alpha = n, blendMode = BlendMode.Plus) }
            // It grazes the box from the side: the left faces glow, falling off toward the right.
            boxSpace(g) { drawPath(g.silhouette, sideLight!!, alpha = n, blendMode = BlendMode.Plus) }
        }
        val m = sim.mode
        if (m < -0.02f) drawRect(g.ecoPool, size = Size(f.dw, f.dh), alpha = -m)
        if (m > 0.02f) drawRect(g.sportPool, size = Size(f.dw, f.dh), alpha = m)
    }

    private var dimmer: Brush? = null
    private var lampPool: Brush? = null
    private var sideLight: Brush? = null
    private var lampAt = Offset.Zero
    private var nightW = -1f

    /** Built once per width: the multiplying dimmer and the lamp's additive pool. */
    private fun nightLayers(g: BoxGeo, f: ThemeFrame) {
        if (nightW == f.dw && dimmer != null) return
        nightW = f.dw
        lampAt = Offset(g.sx(g.boxL) - 110f, 520f)
        dimmer = Brush.radialGradient(
            0f to Color(0xFF7A6553), 0.22f to Color(0xFF4A3D33), 0.5f to Color(0xFF2A2420), 1f to Color(0xFF1A1715),
            center = lampAt, radius = f.dw * 0.75f,
        )
        lampPool = Brush.radialGradient(listOf(Studio.lamp.copy(alpha = 0.20f), Studio.lamp.copy(alpha = 0.05f), Studio.lamp.copy(alpha = 0f)), lampAt, f.dw * 0.32f)
        sideLight = Brush.horizontalGradient(0f to Studio.lamp.copy(alpha = 0.34f), 0.12f to Studio.lamp.copy(alpha = 0.16f), 0.45f to Studio.lamp.copy(alpha = 0.04f), 1f to Studio.lamp.copy(alpha = 0f),
            startX = g.boxL, endX = g.boxR)
    }
}

/** The music box entrance (s) the spin-up assumes: no shorter than the real one, so pins never turn back. */
private const val SPIN_UP_S = 0.75

/** Tines lit in turn by the combo arpeggio (bass to treble). */
private val ARPEGGIO = intArrayOf(3, 6, 9, 11, 14, 17)
