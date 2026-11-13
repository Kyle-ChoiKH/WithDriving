package io.vroomi.character.render.musicbox

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
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
import io.vroomi.character.render.theme.drawMusicNotes

/**
 * Box geometry in box units for one upgrade step (rebuilt when the design width or the step changes). The step
 * sets the comb's teeth, the box's size on the table ([S]) and, at the top step, the bay for the bells.
 */
private class BoxGeo(val dw: Float, val model: Int) {
    val teeth = MusicBoxSim.teethFor(model)
    /** The bay at the right of the movement for the three bells (Lv.5). */
    val ext = if (model >= 4) 190f else 0f
    val S = SCALE[model]
    val x = dw / 2f
    // Case
    val boxL = x - 480f; val boxR = x + 480f + ext; val boxT = 110f; val boxB = 470f; val frontB = 540f
    val innerL = x - 452f; val innerR = x + 452f + ext; val innerT = 134f; val innerB = 452f
    val plateL = x - 432f; val plateR = x + 432f + ext; val plateT = 150f; val plateB = 438f
    val mid = (boxL + boxR) / 2f
    // Open lid behind the box (leaning back)
    val lidT = boxT - 196f
    // Cylinder (axis along x)
    val cylL = x - 250f; val cylR = x + 290f; val cylY = 232f; val cylRad = 62f
    val pitch = (cylR - cylL) / teeth
    /** Where pins meet the tooth tips (angle below the front, rad). */
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
    val glassL = x - 456f; val glassR = x + 456f + ext; val glassT = 128f; val glassB = 456f
    // Bells (Lv.5): three in a column in the bay, their hammers on a rail between the governor and the bay
    val bellX = innerR - 92f; val bellR = 37f
    val railX = x + 452f + 30f
    fun bellY(i: Int): Float = 198f + i * 92f

    fun tineX(j: Int): Float = cylL + (j + 0.5f) * pitch
    val tineW = pitch * (if (teeth > 36) 0.64f else 0.56f)
    /** The comb's root line slants: bass teeth (left) are longer. */
    fun rootY(j: Int): Float = combTop + 10f - j * (24f / (teeth - 1))
    /** The x of note [t]'s first tooth (the arpeggio lights notes). */
    fun noteX(t: Int): Float = tineX(MusicBoxSim.firstTooth(teeth, t))
    /** The note each tooth sounds (−1: an idle tooth of the 50-tooth comb). */
    val noteOf = IntArray(teeth) { -1 }.also { a ->
        val per = teeth / MusicBoxComposer.TINES
        for (t in 0 until MusicBoxComposer.TINES) for (v in 0 until per) a[MusicBoxSim.firstTooth(teeth, t) + v] = t
    }
    /** Lv.4 and up: two combs side by side; the base is split after this tooth (−1: one comb). */
    val splitAfter = if (model >= 3) teeth / 2 - 1 else -1
    val pinR = minOf(3.2f, pitch * 0.40f)

    private fun tineWidth(j: Int) = tineW * (1.08f - j * 0.25f / teeth)
    val tinePaths = Array(teeth) { j ->
        val cx = tineX(j); val w = tineWidth(j)
        val root = rootY(j) + 2f
        Path().apply {
            moveTo(cx - w / 2f, root)
            lineTo(cx - w * 0.34f, tipY + 5f)
            quadraticTo(cx, tipY - 3f, cx + w * 0.34f, tipY + 5f)
            lineTo(cx + w / 2f, root)
            close()
        }
    }
    /** Polished steel teeth lit from above: a wide soft highlight, not a hard ridge. */
    val tineBrushes = Array(teeth) { j ->
        val cx = tineX(j); val w = tineWidth(j)
        Brush.horizontalGradient(0f to Room.silverDark, 0.28f to Room.silver, 0.5f to Room.silverLight, 0.75f to Room.silver, 1f to Room.silverMid,
            startX = cx - w / 2f, endX = cx + w / 2f)
    }
    val combBases: List<Path> = run {
        fun base(j0: Int, j1: Int, l: Float, r: Float) = Path().apply {
            moveTo(l, rootY(j0) + 2f); lineTo(r, rootY(j1) + 2f); lineTo(r, combBottom); lineTo(l, combBottom); close()
        }
        if (splitAfter < 0) listOf(base(0, teeth - 1, cylL - 8f, cylR + 8f))
        else {
            val gx = (tineX(splitAfter) + tineX(splitAfter + 1)) / 2f
            listOf(base(0, splitAfter, cylL - 8f, gx - 3f), base(splitAfter + 1, teeth - 1, gx + 3f, cylR + 8f))
        }
    }
    /** The brass cylinder under the lamp: a bright band near the top, falling to a warm dark underside. */
    val cylBrush = Brush.verticalGradient(
        0f to Room.brassMid, 0.14f to Room.brassLight, 0.3f to Color(0xFFFFF1C4), 0.48f to Room.brass, 0.78f to Room.brassMid, 1f to Room.brassDark,
        startY = cylY - cylRad, endY = cylY + cylRad,
    )
    /** The lamp seen in the glass lid: one large, very soft band. */
    val glassSheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0f), 0.18f to Room.lamp.copy(alpha = 0.13f), 0.34f to Room.lamp.copy(alpha = 0.07f), 0.5f to Color.White.copy(alpha = 0f),
        start = Offset(glassL, glassT), end = Offset(glassL + 420f, glassT + 260f),
    )
    val ecoPool = Brush.radialGradient(listOf(Color(0xFF9CD0AE).copy(alpha = 0.16f), Color(0xFF9CD0AE).copy(alpha = 0f)), Offset(x, 300f), 620f)
    val sportPool = Brush.radialGradient(listOf(Color(0xFFF0A860).copy(alpha = 0.18f), Color(0xFFF0A860).copy(alpha = 0f)), Offset(x, 300f), 620f)

    // Rain drops on the glass: fixed positions (deterministic).
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
    val front = Path().apply { addRoundRect(RoundRect(boxL, boxB - 12f, boxR, frontB, CornerRadius(14f))) }
    val rim = Path().apply { addRoundRect(RoundRect(boxL, boxT, boxR, boxB, CornerRadius(14f))) }

    /** The whole box seen from the front (lid and case), for light that falls on it. */
    val silhouette = Path().apply {
        addPath(lidOuter)
        addRoundRect(RoundRect(boxL, boxT, boxR, frontB, CornerRadius(14f)))
    }

    // The box sits on the table at scale [S], centred, its front bottom edge at [FLOOR].
    fun sx(u: Float) = x + (u - mid) * S
    fun sy(v: Float) = FLOOR + (v - frontB) * S

    companion object {
        const val DROPS = 70
        const val FLOOR = 556f
        /** The box's size on the table for each step: a small box, the same lacquered, a large box, the same, the table box. */
        val SCALE = floatArrayOf(0.62f, 0.62f, 0.78f, 0.78f, 0.80f)
    }
}

/** The words engraved on the front plate of each step (classical serif capitals). */
private val PLATE = arrayOf("SERENADE · 18", "SERENADE · 18", "SERENADE · 36", "HARMONIE · 50", "CARILLON · 72")

/**
 * The music box as an open wooden box under a warm lamp. The upgrade step changes the box itself: matte walnut,
 * then lacquer with a glass inner lid and velvet, then a larger inlaid box with 36 teeth and a visible governor,
 * then two combs with brass mounts and a brass plate, then the table box with three bells and a gilt line.
 * Static parts are one cached bitmap; pins, teeth, governor and hammers move.
 */
internal class MusicBoxPainter : ThemePainter<MusicBoxSim>() {
    override val minDesignWidth = 1040f
    override val focusWidth = 960f
    override fun headroom(sim: MusicBoxSim): Color = Room.wallTop
    override val backdrop = Color(0xFF241A14)

    private var geo: BoxGeo? = null
    private val base = LayerSlot(SceneTheme.MUSIC_BOX)
    private val thin = Stroke(1.2f)
    private val hair = Stroke(0.8f)
    private val springStroke = Stroke(2.4f, cap = StrokeCap.Round)

    private var textsOf: ThemeTexts? = null
    private val plateTexts = arrayOfNulls<TextLayoutResult>(PLATE.size)

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

    private fun geo(dw: Float, model: Int): BoxGeo {
        val g = geo
        if (g != null && abs(g.dw - dw) < 0.5f && g.model == model) return g
        nightW = -1f
        return BoxGeo(dw, model).also { geo = it }
    }

    private fun texts(tx: ThemeTexts) {
        if (textsOf === tx) return
        textsOf = tx
        for (i in PLATE.indices) plateTexts[i] = tx.text.styled("mbx-plate-$i", PLATE[i], 15f, Room.ink, FontFamily.Serif, FontWeight.Medium, 0.22f)
    }

    override fun tap(sim: MusicBoxSim, x: Float, y: Float) { sim.closeView = !sim.closeView }

    override fun drawPx(s: DrawScope, sim: MusicBoxSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        sim.model = f.model
        if (sim.zoom > 0.001f) return // the close view draws everything in design units
        texts(tx)
        val g = geo(f.dw, f.model)
        val w = f.w.roundToInt(); val h = f.h.roundToInt()
        val key = ((w.toLong() shl 20) xor h.toLong()) * 8 + f.model
        val k = f.k
        val bmp = base.get(key, w, h, f.density) {
            withTransform({ scale(k, k, Offset.Zero) }) { staticLayer(g, f) }
        }
        s.drawImage(bmp, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(bmp.width, bmp.height))
    }

    /** The entrance accent of this frame ([io.vroomi.character.render.SceneOptions.intro]). */
    private var intro = 1f

    override fun draw(s: DrawScope, sim: MusicBoxSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        sim.model = f.model
        intro = f.options.intro
        drivingFade(sim.moving, sim.time)
        texts(tx)
        val g = geo(f.dw, f.model)
        val z = sim.zoom
        if (z > 0.001f) {
            // Close view: the camera moves in toward the comb and the pins (parked only).
            val e = z * z * (3f - 2f * z)
            val sc = 1f + 1.6f * e
            val px = g.sx(g.x - 40f); val py = g.sy(g.tipY - 20f)
            withTransform({ translate((f.cx - px) * e, (f.cy - py) * e); scale(sc, sc, Offset(px, py)) }) {
                staticLayer(g, f)
                scene(g, sim, f, tx)
            }
        } else scene(g, sim, f, tx)
    }

    private fun DrawScope.boxSpace(g: BoxGeo, block: DrawScope.() -> Unit) =
        withTransform({ translate(g.x - g.mid * g.S, BoxGeo.FLOOR - g.frontB * g.S); scale(g.S, g.S, Offset.Zero) }, block)

    private fun DrawScope.scene(g: BoxGeo, sim: MusicBoxSim, f: ThemeFrame, tx: ThemeTexts) {
        boxSpace(g) {
            pins(g, sim)
            tines(g, sim)
            damper(g, sim)
            barrel(g, sim)
            governor(g, sim)
            if (g.model >= 4) bells(g, sim)
            keyPlate(g, sim, tx)
            arpeggio(g, sim)
            glass(g, sim)
        }
        light(g, sim, f)
        // Music in the car: the box listens, and golden notes rise from under its glass.
        drawMusicNotes(g.x + 120f, BoxGeo.FLOOR - 250f, Color(0xFFFFF0C8), sim.listening, sim.decoTime, glow = Color(0xFFE0A84A))
    }

    // ------------------------------------------------------------------ static (cached)

    private fun DrawScope.staticLayer(g: BoxGeo, f: ThemeFrame) {
        // The room: a warm brown wall falling into shadow, a wooden table, the lamp's round pool from the upper left.
        val horizon = 418f
        drawRect(Brush.verticalGradient(0f to Room.wallTop, 1f to Room.wallMid, startY = 0f, endY = horizon), size = Size(f.dw, horizon))
        val table = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, horizon, f.dw, f.dh)) }
        drawPath(table, Brush.verticalGradient(0f to Room.table, 1f to Room.tableDeep, startY = horizon, endY = f.dh))
        clipPath(table) {
            for (i in 0 until 46) {
                val y = horizon + (i + hash01(i, 5)) / 46f * (f.dh - horizon)
                drawLine(Room.grain.copy(alpha = 0.10f + 0.12f * hash01(i, 6)), Offset(0f, y), Offset(f.dw, y + (hash01(i, 7) - 0.5f) * 10f), if (hash01(i, 8) > 0.8f) 1.6f else 0.7f)
            }
        }
        drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, horizon + 0.5f), Offset(f.dw, horizon + 0.5f), 1f)
        val lampAt = Offset(g.x - f.dw * 0.16f, 40f)
        drawRect(Brush.radialGradient(0f to Room.lamp.copy(alpha = 0.44f), 0.4f to Room.lamp.copy(alpha = 0.17f), 1f to Room.lamp.copy(alpha = 0f), center = lampAt, radius = max(f.dw, 900f) * 0.62f), size = Size(f.dw, f.dh))
        drawRect(Brush.radialGradient(0.5f to Color.Transparent, 1f to Room.shadow.copy(alpha = 0.42f), center = Offset(g.x, f.dh * 0.45f), radius = f.dw * 0.62f), size = Size(f.dw, f.dh))
        // Soft contact shadow on the table, a little to the right (the lamp is at the left).
        val sl = g.sx(g.boxL); val sr = g.sx(g.boxR)
        drawOval(Brush.radialGradient(listOf(Room.shadow.copy(alpha = 0.55f), Room.shadow.copy(alpha = 0f)), Offset((sl + sr) / 2f + 20f, BoxGeo.FLOOR + 4f), (sr - sl) * 0.62f),
            Offset(sl - 60f, BoxGeo.FLOOR - 34f), Size(sr - sl + 160f, 76f))
        drawRect(Room.shadow.copy(alpha = 0.45f), Offset(sl + 6f, BoxGeo.FLOOR - 3f), Size(sr - sl - 12f, 6f))
        boxSpace(g) { box(g) }
        windingKey(g.sx(g.boxR) + 64f, BoxGeo.FLOOR - 10f)
    }

    /** A brass winding key lying on the table beside the box (the room's one prop). */
    private fun DrawScope.windingKey(kx: Float, ky: Float) {
        drawOval(Room.shadow.copy(alpha = 0.4f), Offset(kx - 50f, ky + 6f), Size(124f, 14f))
        withTransform({ rotate(-10f, Offset(kx, ky)) }) {
            drawRoundRect(Brush.verticalGradient(listOf(Room.brassLight, Room.brassMid), startY = ky - 4f, endY = ky + 4f), Offset(kx - 4f, ky - 4f), Size(64f, 8f), CornerRadius(4f))
            drawRoundRect(Room.brassMid, Offset(kx + 56f, ky - 7f), Size(14f, 14f), CornerRadius(3f))
            for (sgn in listOf(-1f, 1f)) {
                val top = ky + (if (sgn < 0) -30f else 2f)
                drawOval(Brush.radialGradient(listOf(Room.brassLight, Room.brass, Room.brassMid), Offset(kx - 30f, top + 8f), 34f), Offset(kx - 46f, top), Size(46f, 28f))
                drawOval(Room.tableDeep, Offset(kx - 34f, top + 8f), Size(22f, 12f))
            }
        }
    }

    private fun DrawScope.box(g: BoxGeo) {
        val m = g.model
        val wood = if (m >= 1) Wood.MAHOGANY else Wood.WALNUT
        val velvet = m >= 1
        // Open lid: wood outside, and facing the camera its inside: plain wood in the first box, burgundy velvet after.
        woodPanel(g.lidOuter, wood, 31)
        if (velvet) {
            drawPath(g.lidLining, Brush.verticalGradient(listOf(Room.velvetLight, Room.velvet, Room.velvetShade), startY = g.lidT, endY = g.boxT))
            // The nap of the velvet catches the lamp: one large, very soft diagonal bloom, and three tufted seams.
            clipPath(g.lidLining) {
                drawRect(Brush.linearGradient(0f to Color.White.copy(alpha = 0f), 0.35f to Room.lamp.copy(alpha = 0.20f), 0.6f to Color.White.copy(alpha = 0f),
                    start = Offset(g.boxL + 60f, g.lidT), end = Offset(g.boxL + 520f, g.boxT)), Offset(g.boxL, g.lidT), Size(g.boxR - g.boxL, g.boxT - g.lidT))
            }
            for (k in 1..3) {
                val yy = g.lidT + 22f + k * 44f
                drawLine(Room.velvetShade.copy(alpha = 0.6f), Offset(g.boxL + 70f + k * 4f, yy), Offset(g.boxR - 70f - k * 4f, yy), 1.4f)
                drawLine(Room.velvetLight.copy(alpha = 0.5f), Offset(g.boxL + 70f + k * 4f, yy + 1.6f), Offset(g.boxR - 70f - k * 4f, yy + 1.6f), 0.8f)
            }
        } else {
            woodPanel(g.lidLining, Wood(Room.walnutShade, Room.walnut, Color(0xFF3A2618), false), 33)
        }
        drawPath(g.lidLining, wood.shade, style = Stroke(10f))
        drawPath(g.lidLining, wood.light.copy(alpha = 0.5f), style = Stroke(1.2f))
        // Lv.3: a string of inlay along the top of the lid. Lv.5: a gilt line inside the lid's frame.
        if (m >= 2) inlayString(g.boxL + 70f, g.boxR - 70f, g.lidT + 11f, 9f)
        if (m >= 4) {
            val gilt = Path().apply { moveTo(g.boxL + 52f, g.boxT - 10f); lineTo(g.boxL + 70f, g.lidT + 34f); lineTo(g.boxR - 70f, g.lidT + 34f); lineTo(g.boxR - 52f, g.boxT - 10f); close() }
            drawPath(gilt, Room.gold, style = Stroke(1.6f))
        }

        // Case front and the rim around the well.
        woodPanel(g.front, wood, 21)
        woodPanel(g.rim, Wood(wood.light, lerp(wood.light, Color.White, 0.12f), wood.base, wood.lacquer), 23)
        drawPath(g.rim, wood.shade.copy(alpha = 0.6f), style = Stroke(1.2f))
        drawPath(g.front, wood.shade.copy(alpha = 0.7f), style = Stroke(1.2f))
        if (m >= 2) inlayString(g.boxL + 30f, g.boxR - 30f, g.boxB + 3f, 7f, 14f)
        if (m >= 4) {
            drawRoundRect(Room.gold, Offset(g.boxL + 12f, g.boxB + 12f), Size(g.boxR - g.boxL - 24f, g.frontB - g.boxB - 22f), CornerRadius(6f), style = Stroke(1.5f))
            // The table box stands on four turned brass feet (two show).
            for (fx in listOf(g.boxL + 46f, g.boxR - 46f)) plate(fx - 24f, g.frontB - 2f, 48f, 15f, 6f, Room.brassLight, Room.brass, Room.brassDark)
        }
        // The well: the first box shows bare wood, the others a velvet lining; then the brass bedplate.
        val well = if (velvet) Room.velvet else Room.walnutShade
        drawRoundRect(well, Offset(g.innerL, g.innerT), Size(g.innerR - g.innerL, g.innerB - g.innerT), CornerRadius(10f))
        drawRoundRect(Room.shadow.copy(alpha = 0.5f), Offset(g.innerL, g.innerT), Size(g.innerR - g.innerL, 10f), CornerRadius(6f))
        plate(g.plateL, g.plateT, g.plateR - g.plateL, g.plateB - g.plateT, 8f, Room.brassLight, Room.brass, Room.brassMid)
        drawRoundRect(Room.brassDark.copy(alpha = 0.6f), Offset(g.plateL, g.plateT), Size(g.plateR - g.plateL, g.plateB - g.plateT), CornerRadius(8f), style = Stroke(1.4f))
        // A brushed finish along the bedplate: a few long faint lines.
        for (i in 0 until 22) {
            val yy = g.plateT + 6f + hash01(i, 51) * (g.plateB - g.plateT - 12f)
            drawLine(Room.brassDark.copy(alpha = 0.10f), Offset(g.plateL + 6f, yy), Offset(g.plateR - 6f, yy), 0.6f)
        }
        for ((sx, sy) in listOf(g.plateL + 16f to g.plateT + 16f, g.plateR - 16f to g.plateT + 16f, g.plateL + 16f to g.plateB - 16f, g.plateR - 16f to g.plateB - 16f)) dome(sx, sy, 5f)

        // Cylinder bearings and the brass cylinder.
        for (bx in listOf(g.cylL - 22f, g.cylR + 22f)) {
            drawRoundRect(Brush.horizontalGradient(listOf(Room.silverMid, Room.silverLight, Room.silver), startX = bx - 12f, endX = bx + 12f),
                Offset(bx - 12f, g.cylY - 18f), Size(24f, 120f), CornerRadius(8f))
            dome(bx, g.cylY, 6f)
        }
        drawRect(Room.shadow.copy(alpha = 0.35f), Offset(g.cylL, g.cylY + g.cylRad - 4f), Size(g.cylR - g.cylL, 16f))
        drawRect(g.cylBrush, Offset(g.cylL, g.cylY - g.cylRad), Size(g.cylR - g.cylL, g.cylRad * 2f))
        for (ex in listOf(g.cylL, g.cylR)) {
            drawOval(Brush.verticalGradient(listOf(Room.brassLight, Room.brassMid), startY = g.cylY - g.cylRad, endY = g.cylY + g.cylRad),
                Offset(ex - 7f, g.cylY - g.cylRad), Size(14f, g.cylRad * 2f))
        }
        drawOval(Brush.verticalGradient(listOf(Room.silverLight, Room.silverDark), startY = g.cylY - g.cylRad - 14f, endY = g.cylY + g.cylRad + 14f),
            Offset(g.cylR + 4f, g.cylY - g.cylRad - 14f), Size(12f, (g.cylRad + 14f) * 2f))
        // Comb base (two from Lv.4): a satin steel block with domed screws; lead weights under the bass teeth.
        for (b in g.combBases) {
            val bb = b.getBounds()
            drawPath(b, Brush.verticalGradient(listOf(Room.silverLight, Room.silver, Room.silverMid), startY = g.combTop - 14f, endY = g.combBottom))
            drawLine(Room.silverDark.copy(alpha = 0.6f), Offset(bb.left, g.combBottom), Offset(bb.right, g.combBottom), 1.2f)
            val screws = if (g.combBases.size == 2) 2 else 3
            for (t in 0 until screws) dome(bb.left + 34f + t * (bb.width - 68f) / (screws - 1), (g.combTop + g.combBottom) / 2f + 10f, 6f)
        }
        for (j in 0 until g.teeth / 3) {
            val cx = g.tineX(j)
            drawRoundRect(Room.lead, Offset(cx - g.tineW * 0.75f, g.rootY(j) - 22f), Size(g.tineW * 1.5f, 16f), CornerRadius(minOf(4f, g.tineW * 0.5f)))
        }
        // Barrel drum (brass) and the governor bracket.
        drawCircle(Brush.radialGradient(listOf(Room.brassLight, Room.brass, Room.brassMid), Offset(g.barrelX - 20f, g.barrelY - 24f), g.barrelR * 1.3f), g.barrelR, Offset(g.barrelX, g.barrelY))
        drawCircle(Room.brassDark, g.barrelR, Offset(g.barrelX, g.barrelY), style = Stroke(1.2f))
        drawCircle(Color(0xFF2C2118), g.barrelR - 14f, Offset(g.barrelX, g.barrelY))
        drawCircle(Room.brassDark, g.barrelR - 14f, Offset(g.barrelX, g.barrelY), style = Stroke(1.2f))
        drawRoundRect(Brush.horizontalGradient(listOf(Room.brassMid, Room.brassLight, Room.brass), startX = g.govX - 30f, endX = g.govX + 30f),
            Offset(g.govX - 30f, g.govBottom - 6f), Size(60f, 22f), CornerRadius(8f))
        drawRoundRect(Room.brass, Offset(g.govX - 26f, g.govTop + 26f), Size(52f, 10f), CornerRadius(5f))
        // Up to Lv.2 the governor's fan sits under a small brass cover; from Lv.3 it is in the open.
        if (m < 2) {
            plate(g.govX - 40f, g.govTop - 12f, 80f, 40f, 9f, Room.brassLight, Room.brass, Room.brassMid)
            dome(g.govX - 28f, g.govTop + 8f, 3.4f); dome(g.govX + 28f, g.govTop + 8f, 3.4f)
        }
        drawCircle(Color(0xFF2C2118), 20f, Offset(g.counterX, g.counterY))
        drawCircle(Room.brassDark, 20f, Offset(g.counterX, g.counterY), style = Stroke(1.2f))

        // Lv.5: three bells in the bay, on a brass rail.
        if (m >= 4) {
            drawRoundRect(Brush.horizontalGradient(listOf(Room.brassMid, Room.brassLight, Room.brass), startX = g.railX - 5f, endX = g.railX + 5f),
                Offset(g.railX - 5f, g.plateT + 14f), Size(10f, g.plateB - g.plateT - 28f), CornerRadius(5f))
            for (i in 0 until MusicBoxSim.BELLS) {
                val c = Offset(g.bellX, g.bellY(i)); val r = g.bellR - i * 3f
                drawCircle(Room.shadow.copy(alpha = 0.4f), r + 2f, Offset(c.x + 2f, c.y + 4f))
                drawCircle(Brush.radialGradient(0f to Color(0xFFFFF3C8), 0.35f to Room.brassLight, 0.75f to Room.brass, 1f to Room.brassDark, center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.5f), r, c)
                drawCircle(Room.brassDark.copy(alpha = 0.7f), r, c, style = Stroke(1.2f))
                drawCircle(Room.brassDark.copy(alpha = 0.35f), r * 0.62f, c, style = Stroke(0.9f))
                dome(c.x, c.y, 5f)
            }
        }

        // The front plate: an ivory inlay with engraved serif capitals; a screwed brass plate from Lv.4.
        val pw = 196f; val ph = 30f; val pcx = g.boxR - 180f; val pcy = 508f
        if (m >= 3) {
            plate(pcx - pw / 2f, pcy - ph / 2f, pw, ph, 4f, Room.brassLight, Room.brass, Room.brassMid)
            dome(pcx - pw / 2f + 8f, pcy, 2.6f); dome(pcx + pw / 2f - 8f, pcy, 2.6f)
        } else {
            drawRoundRect(Room.ebony, Offset(pcx - pw / 2f - 1.5f, pcy - ph / 2f - 1.5f), Size(pw + 3f, ph + 3f), CornerRadius(5f))
            drawRoundRect(Brush.verticalGradient(listOf(Room.ivory, lerp(Room.ivory, Room.maple, 0.5f)), startY = pcy - ph / 2f, endY = pcy + ph / 2f), Offset(pcx - pw / 2f, pcy - ph / 2f), Size(pw, ph), CornerRadius(4f))
        }
        plateTexts[m]?.let { drawTextCentered(it, pcx, pcy) }
        // Key plaque: an oval on the front (ivory, brass from Lv.4).
        val kx = g.mid
        if (m >= 3) {
            drawOval(Room.shadow.copy(alpha = 0.4f), Offset(kx - 40f, 490f), Size(80f, 36f))
            drawOval(Brush.linearGradient(listOf(Room.brassLight, Room.brass, Room.brassMid), Offset(kx - 40f, 488f), Offset(kx + 40f, 524f)), Offset(kx - 40f, 488f), Size(80f, 36f))
        } else {
            drawOval(Room.ebony, Offset(kx - 41.5f, 486.5f), Size(83f, 39f))
            drawOval(Room.ivory, Offset(kx - 40f, 488f), Size(80f, 36f))
        }
        // Lv.4: brass corner mounts on the front.
        if (m >= 3) {
            cornerMount(g.boxL + 3f, g.boxB - 9f, 1f, 1f); cornerMount(g.boxR - 3f, g.boxB - 9f, -1f, 1f)
            cornerMount(g.boxL + 3f, g.frontB - 3f, 1f, -1f); cornerMount(g.boxR - 3f, g.frontB - 3f, -1f, -1f)
        }
        // Lv.2: the inner glass lid in a thin brass frame.
        if (m >= 1) drawRoundRect(Room.brass.copy(alpha = 0.9f), Offset(g.glassL, g.glassT), Size(g.glassR - g.glassL, g.glassB - g.glassT), CornerRadius(12f), style = Stroke(2.4f))
    }

    // ------------------------------------------------------------------ moving parts

    /** Steel pins on the visible face of the brass cylinder: angle from the musical position (8 bars per revolution). */
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
        val r = g.pinR
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
                    val x = g.tineX(sim.toothOf(p.tine, b, p.slot))
                    val h = 1.6f + 5.2f * c
                    // The pin turns into the lamp near the top of the cylinder and out of it below.
                    val lit = (0.25f + 0.75f * (1f - sin(theta).toFloat()) / 2f * 1.6f).coerceIn(0f, 1f)
                    val near = abs(theta - g.contact) < 0.3
                    val grace = p.kind == PinNote.GRACE
                    val pearl = if (grace) 1f else if (near) pearlNow else 0f
                    if (pearl > 0.02f) drawCircle(Room.lamp.copy(alpha = 0.5f * pearl * c), 9f, Offset(x, y))
                    drawOval(Room.shadow.copy(alpha = 0.45f * c), Offset(x - r + 1f, y - h / 2f + 1.8f), Size(r * 2f + 0.6f, h))
                    drawOval(lerp(lerp(Room.graphite, Room.silver, lit * 0.7f), Room.pearl, pearl), Offset(x - r, y - h / 2f), Size(r * 2f, h))
                    drawOval(lerp(Room.silverLight, Room.pearl, pearl).copy(alpha = 0.35f + 0.65f * lit), Offset(x - r * 0.5f, y - h / 2f + 0.3f), Size(r * 1.0f, h * 0.45f))
                }
            }
        }
    }

    /** Teeth: still steel; a plucked tooth shows a soft damped swing (2 Hz or less, never its real frequency). */
    private fun DrawScope.tines(g: BoxGeo, sim: MusicBoxSim) {
        val calmK = if (sim.calm) 0.5f else 1f
        for (j in 0 until g.teeth) {
            val t = g.noteOf[j]
            val live = t >= 0 && sim.lastTooth[t] == j
            val a = if (!live || sim.reducedMotion) 0f else sim.amp[t] * calmK
            val cx = g.tineX(j)
            var sway = 0f
            if (a > 0.01f) {
                val age = (sim.decoTime - sim.pluckAt[t]).toFloat()
                sway = a * 2.6f * sin(2f * PI.toFloat() * 1.6f * age)
                drawLine(Color.White.copy(alpha = 0.30f * a), Offset(cx, g.combTop), Offset(cx + sway, g.tipY + 2f), g.tineW + 6f * a, cap = StrokeCap.Round)
            }
            withTransform({ if (sway != 0f) translate(sway * 0.35f, 0f) }) {
                drawPath(g.tinePaths[j], g.tineBrushes[j])
                drawPath(g.tinePaths[j], Room.silverDark.copy(alpha = 0.7f), style = hair)
            }
            // A glint at the tip: 40 ms in, 400 ms out (flash-guarded in the simulation).
            if (live && !sim.calm) {
                val ga = (sim.decoTime - sim.glintAt[t]).toFloat()
                if (ga in 0f..1.2f) {
                    val e = if (ga < 0.04f) ga / 0.04f else exp(-(ga - 0.04f) / 0.4f)
                    drawCircle(Room.pearl.copy(alpha = 0.7f * e), 5f + 3f * e, Offset(cx, g.tipY + 2f))
                }
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
            val x = g.noteX(ARPEGGIO[k])
            drawCircle(Room.lamp.copy(alpha = 0.55f * e), 11f, Offset(x, y))
            drawCircle(Room.pearl.copy(alpha = e), 4.6f, Offset(x, y))
            drawCircle(Room.silverDark.copy(alpha = 0.5f * e), 4.6f, Offset(x, y), style = hair)
        }
    }

    /** Harsh braking: a burgundy felt damper presses the teeth for a moment. */
    private fun DrawScope.damper(g: BoxGeo, sim: MusicBoxSim) {
        val t = sim.since(sim.damperAt).toFloat()
        if (t < 0f || t > 1.4f) return
        val a = if (t < 0.15f) t / 0.15f else (1f - (t - 0.15f) / 1.25f)
        drawRoundRect(Room.velvetLight.copy(alpha = 0.95f * a), Offset(g.cylL, g.tipY + 10f), Size(g.cylR - g.cylL, 16f), CornerRadius(8f))
        drawRoundRect(Room.velvetShade.copy(alpha = 0.8f * a), Offset(g.cylL, g.tipY + 22f), Size(g.cylR - g.cylL, 4f), CornerRadius(2f))
    }

    /** The spring barrel: blued mainspring coils (tight when wound), brass ratchet, steel pawl, click spring and the km counter. */
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
        drawPath(p, Room.blued, style = springStroke)
        drawPath(p, Color.White.copy(alpha = 0.45f), style = hair)
        val arbor = sim.arborAngle.toFloat()
        rotate(arbor * 180f / PI.toFloat(), Offset(g.barrelX, g.barrelY)) {
            val teeth = MusicBoxSim.RATCHET_TEETH
            for (i in 0 until teeth) {
                val a0 = i * 2f * PI.toFloat() / teeth
                val a1 = a0 + 2f * PI.toFloat() / teeth * 0.75f
                drawLine(Room.brassMid, Offset(g.barrelX + cos(a0) * (g.ratchetR - 6f), g.barrelY + sin(a0) * (g.ratchetR - 6f)),
                    Offset(g.barrelX + cos(a1) * g.ratchetR, g.barrelY + sin(a1) * g.ratchetR), 2.2f)
            }
            drawCircle(Room.brass, g.ratchetR - 6f, Offset(g.barrelX, g.barrelY))
            drawCircle(Room.brassDark, g.ratchetR - 6f, Offset(g.barrelX, g.barrelY), style = thin)
            drawRect(Room.silverDark, Offset(g.barrelX - 5f, g.barrelY - 5f), Size(10f, 10f))
        }
        val pt = sim.since(sim.pawlAt).toFloat()
        val lift = if (pt in 0f..0.25f) sin(pt / 0.25f * PI.toFloat()) * 6f else 0f
        val tipX = g.barrelX + 18f; val tipY = g.barrelY - g.ratchetR + 2f - lift
        drawLine(Room.silver, Offset(g.pawlX, g.pawlY), Offset(tipX, tipY), 5f, cap = StrokeCap.Round)
        drawLine(Room.silverDark.copy(alpha = 0.6f), Offset(g.pawlX, g.pawlY), Offset(tipX, tipY), 1f)
        dome(g.pawlX, g.pawlY, 6f)
        val ct = sim.since(sim.clickSpringAt).toFloat()
        val flex = if (ct in 0f..0.3f) sin(ct / 0.3f * PI.toFloat()) * 5f else 0f
        drawLine(Room.blued, Offset(g.pawlX + 4f, g.pawlY - 4f), Offset(g.pawlX + 30f, g.pawlY - 18f - flex), 1.8f, cap = StrokeCap.Round)
        val ca = sim.counter * 36f + run { val t = sim.since(sim.counterAt).toFloat(); if (t in 0f..0.4f) -36f * (1f - t / 0.4f) else 0f }
        rotate(ca, Offset(g.counterX, g.counterY)) {
            for (i in 0 until 10) {
                val a = i * 36f * PI.toFloat() / 180f
                drawLine(Room.brass, Offset(g.counterX + cos(a) * 6f, g.counterY + sin(a) * 6f), Offset(g.counterX + cos(a) * 16f, g.counterY + sin(a) * 16f), 2.4f, cap = StrokeCap.Round)
            }
            drawCircle(Room.silver, 6f, Offset(g.counterX, g.counterY))
        }
    }

    /** Governor: endless screw and, from Lv.3 where it is in the open, a two-blade air-brake fan (a soft disc above 2 rev/s). */
    private fun DrawScope.governor(g: BoxGeo, sim: MusicBoxSim) {
        val x = g.govX
        val top = if (g.model < 2) g.govTop + 30f else g.govTop + 10f
        drawLine(Room.silverMid, Offset(x, top), Offset(x, g.govBottom), 4f)
        val ph = ((sim.fanAngle / (2 * PI)) % 1.0).toFloat()
        var y = g.govTop + 60f + ph * 9f
        while (y < g.govBottom - 20f) {
            drawLine(Room.silverLight, Offset(x - 9f, y), Offset(x + 9f, y + 5f), 2.2f)
            drawLine(Room.silverDark.copy(alpha = 0.5f), Offset(x - 9f, y + 1.6f), Offset(x + 9f, y + 6.6f), 0.8f)
            y += 9f
        }
        if (g.model < 2) return
        val rps = sim.fanRps.toFloat()
        val fy = g.govTop + 8f
        if (rps > 2f) {
            val a = ((rps - 2f) / 0.6f).coerceIn(0f, 1f)
            drawOval(Room.brassLight.copy(alpha = 0.30f + 0.2f * a), Offset(x - 60f, fy - 11f), Size(120f, 22f))
        }
        if (rps <= 2.3f) {
            val c = cos(sim.fanAngle).toFloat()
            val sn = sin(sim.fanAngle).toFloat()
            val bw = 58f * abs(c)
            val alpha = if (rps > 2f) 1f - (rps - 2f) / 0.3f else 1f
            val side = if (c >= 0f) 1f else -1f
            drawRoundRect(lerp(Room.brass, Room.brassLight, (sn + 1f) / 2f).copy(alpha = alpha), Offset(x, fy - 11f), Size(bw * side, 22f), CornerRadius(8f))
            drawRoundRect(lerp(Room.brassMid, Room.brass, (1f - sn) / 2f).copy(alpha = alpha), Offset(x, fy - 11f), Size(-bw * side, 22f), CornerRadius(8f))
        }
        dome(x, fy, 7f)
    }

    /** Lv.5: the small hammers. A struck bell's hammer swings in once and falls back; the bell keeps a soft ring of light. */
    private fun DrawScope.bells(g: BoxGeo, sim: MusicBoxSim) {
        for (i in 0 until MusicBoxSim.BELLS) {
            val c = Offset(g.bellX, g.bellY(i)); val r = g.bellR - i * 3f
            val t = sim.since(sim.bellAt[i]).toFloat()
            val swing = if (t in 0f..0.3f && !sim.reducedMotion) sin(t / 0.3f * PI.toFloat()) else 0f
            if (t in 0f..1.6f && !sim.calm) {
                val e = if (t < 0.05f) t / 0.05f else exp(-(t - 0.05f) / 0.5f)
                drawCircle(Room.pearl.copy(alpha = 0.34f * e), r * (0.72f + 0.2f * (1f - e)), c, style = Stroke(2.2f))
            }
            val pivot = Offset(g.railX, c.y - 22f)
            val rest = Offset(c.x - r - 12f, c.y - 8f); val hit = Offset(c.x - r + 3f, c.y - 2f)
            val head = Offset(rest.x + (hit.x - rest.x) * swing, rest.y + (hit.y - rest.y) * swing)
            drawLine(Room.silverDark.copy(alpha = 0.6f), Offset(pivot.x + 1f, pivot.y + 2f), Offset(head.x + 1f, head.y + 2f), 2.6f, cap = StrokeCap.Round)
            drawLine(Room.silver, pivot, head, 2.4f, cap = StrokeCap.Round)
            dome(head.x, head.y, 5.2f, Room.brassLight, Room.brass, Room.brassDark)
            dome(pivot.x, pivot.y, 4f)
        }
    }

    /**
     * The key plaque: the key letter engraved in serif; it glows softly after a key change. The letter is information
     * too small for the driving minimum (24 sp), so it hides while the car moves (the engraved front plate stays).
     */
    private fun DrawScope.keyPlate(g: BoxGeo, sim: MusicBoxSim, tx: ThemeTexts) {
        val t = sim.since(sim.keyChangeAt).toFloat()
        if (t in 0f..2f) drawOval(Room.pearl.copy(alpha = 0.6f * (1f - t / 2f)), Offset(g.mid - 46f, 484f), Size(92f, 44f))
        val a = 1f - drive
        if (a > 0.01f) drawTextCentered(tx.get(sim.keyName, 22f, Room.ink), g.mid, 506f, alpha = a)
    }

    /** The glass over the movement (from Lv.2): the lamp's reflection; rain drops and mist that clears from the bottom while defrosting. */
    private fun DrawScope.glass(g: BoxGeo, sim: MusicBoxSim) {
        if (g.model >= 1) drawRect(g.glassSheen, Offset(g.glassL, g.glassT), Size(g.glassR - g.glassL, g.glassB - g.glassT))
        val fog = sim.fog
        if (fog > 0.01f) {
            val clearH = (g.glassB - g.glassT) * sim.clear
            val top = g.glassT; val bottom = g.glassB - clearH
            if (bottom > top) drawRect(Color.White.copy(alpha = 0.30f * fog), Offset(g.glassL, top), Size(g.glassR - g.glassL, bottom - top))
            if (clearH > 2f) for (i in 1..3) drawRect(Color.White.copy(alpha = 0.07f * fog * (4 - i)), Offset(g.glassL, bottom + (i - 1) * 5f), Size(g.glassR - g.glassL, 5f))
        }
        val rain = sim.rain
        if (rain > 0.01f) {
            for (i in 0 until BoxGeo.DROPS) {
                if (g.dropOrder[i] > rain) continue
                val x = g.dropX[i]; val y = g.dropY[i]
                val r = g.dropR[i]
                drawCircle(Room.shadow.copy(alpha = 0.25f), r, Offset(x + 0.6f, y + 1.2f))
                drawCircle(Color.White.copy(alpha = 0.22f), r, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.9f), r * 0.3f, Offset(x - r * 0.35f, y - r * 0.38f))
            }
        }
    }

    /**
     * Room light. By day a window fills the room a little; at night only the lamp is left: the picture is
     * multiplied by a warm tone that is lighter on the lamp side (it never goes black), and the lamp adds its pool
     * and grazes the box from the left. ECO and SPORT tint the light a little.
     */
    private fun DrawScope.light(g: BoxGeo, sim: MusicBoxSim, f: ThemeFrame) {
        val n = sim.night
        if (n > 0.01f) {
            nightLayers(g, f)
            drawRect(dimmer!!, size = Size(f.dw, f.dh), alpha = n, blendMode = BlendMode.Multiply)
            withTransform({ scale(1f, 0.6f, lampAt) }) { drawCircle(lampPool!!, f.dw * 0.36f, lampAt, alpha = n, blendMode = BlendMode.Plus) }
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

    /** Built once per width and step: the multiplying dimmer and the lamp's additive pool. */
    private fun nightLayers(g: BoxGeo, f: ThemeFrame) {
        if (nightW == f.dw && dimmer != null) return
        nightW = f.dw
        lampAt = Offset(g.sx(g.boxL) - 90f, 300f)
        dimmer = Brush.radialGradient(
            0f to Color(0xFFE6D6C2), 0.3f to Color(0xFFB09C88), 0.65f to Color(0xFF7C6C5E), 1f to Color(0xFF5C4F45),
            center = lampAt, radius = f.dw * 0.8f,
        )
        lampPool = Brush.radialGradient(listOf(Room.lamp.copy(alpha = 0.20f), Room.lamp.copy(alpha = 0.06f), Room.lamp.copy(alpha = 0f)), lampAt, f.dw * 0.36f)
        sideLight = Brush.horizontalGradient(0f to Room.lamp.copy(alpha = 0.26f), 0.12f to Room.lamp.copy(alpha = 0.12f), 0.45f to Room.lamp.copy(alpha = 0.03f), 1f to Room.lamp.copy(alpha = 0f),
            startX = g.boxL, endX = g.boxR)
    }
}

/** The music box entrance (s) the spin-up assumes: no shorter than the real one, so pins never turn back. */
private const val SPIN_UP_S = 0.75

/** Notes lit in turn by the combo arpeggio (bass to treble). */
private val ARPEGGIO = intArrayOf(3, 6, 9, 11, 14, 17)
