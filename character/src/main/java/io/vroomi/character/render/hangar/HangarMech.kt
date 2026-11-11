package io.vroomi.character.render.hangar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import io.vroomi.character.render.hash01
import io.vroomi.character.render.theme.drawTextCentered
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/**
 * The look of one upgrade step on the common HX frame (an original industrial frame, about 18 m): its paint, how
 * blunt or sleek its armour is, how wide its feet are, and its equipment. No weapons; one flat visor band for a
 * face, round-cornered shoulder blocks with a beacon, a reactor window in the chest.
 */
internal class MechStyle(
    val primary: Color, val secondary: Color, val accent: Color, val visor: Color,
    /** Corner chamfer of the plates, their width factor, how much they taper toward the lower end, the foot width. */
    val ch: Float, val bulk: Float, val taper: Float, val footW: Float,
    val camo: Boolean = false, val quilt: Boolean = false,
    val workLamps: Boolean = false, val toolArm: Boolean = false, val winch: Boolean = false, val searchlight: Boolean = false, val thrusters: Boolean = false,
) {
    companion object {
        /** Spec 5.12, Lv.1..5: work, maintenance, rescue, polar, survey. */
        val STEPS = arrayOf(
            MechStyle(Color(0xFF4B5159), Color(0xFF32373D), Color(0xFFF07A1A), Color(0xFFFFC46B), 10f, 1.12f, 1.0f, 80f),
            MechStyle(Color(0xFFE2DFD6), Color(0xFF4B5159), Color(0xFFF07A1A), Color(0xFF9FE3FF), 7f, 1.04f, 0.96f, 74f, workLamps = true, toolArm = true),
            MechStyle(Color(0xFFF26A1B), Color(0xFFECEAE4), Color(0xFF2E3338), Color(0xFF9FE3FF), 7f, 1.04f, 0.96f, 74f, winch = true, searchlight = true),
            MechStyle(Color(0xFFD5DADF), Color(0xFFAEB6BD), Color(0xFF5E6770), Color(0xFFBFF0FF), 8f, 1.10f, 1.0f, 106f, camo = true, quilt = true),
            MechStyle(Color(0xFFB9C0C8), Color(0xFF24606A), Color(0xFF2FB3A3), Color(0xFFE9C25A), 4f, 0.94f, 0.82f, 70f, thrusters = true),
        )
    }
}

/** Which colour of the style a plate takes. */
private const val P = 0
private const val S = 1
private const val A = 2

/** A plate of armour: its outline in robot coordinates (origin between the feet, up is −y), its paint role and its body group. */
private class Plate(val path: Path, val role: Int, val group: Int)

/** Body groups: they move a little differently (knee dip, head turn, a waving arm). */
private const val LEGS = 0
private const val THIGHS = 1
private const val BODY = 2
private const val ARM_L = 3
private const val ARM_R = 4
private const val HEAD = 5

/** Plate indices the drawing refers to (the fitting order: feet, shins, knees, thighs, pelvis, waist, chest, chest plate, shoulders, upper arms, lower arms, head). */
private const val SHOULDER_R = 13
private const val FOREARM_R = 17

/** A chamfered quad from its upper end (x0, y0, width w0) to its lower end (x1, y1, w1). */
private fun quad(x0: Float, y0: Float, w0: Float, x1: Float, y1: Float, w1: Float, ch: Float) = Path().apply {
    val c0 = min(ch, w0 * 0.34f); val c1 = min(ch, w1 * 0.34f)
    moveTo(x0 - w0 / 2 + c0, y0); lineTo(x0 + w0 / 2 - c0, y0); lineTo(x0 + w0 / 2, y0 + c0)
    lineTo(x1 + w1 / 2, y1 - c1); lineTo(x1 + w1 / 2 - c1, y1); lineTo(x1 - w1 / 2 + c1, y1); lineTo(x1 - w1 / 2, y1 - c1); lineTo(x0 - w0 / 2, y0 + c0); close()
}

/** The armour of one style, built once, in the order it is fitted (feet first, head last). */
internal class Mech(val style: MechStyle) {
    private val b = style.bulk; private val t = style.taper; private val ch = style.ch
    private val plates: List<Plate> = buildList {
        for (s in floatArrayOf(-1f, 1f)) add(Plate(quad(s * 38f, -32f, 50f * b, s * 41f, 0f, style.footW, ch), S, LEGS))
        for (s in floatArrayOf(-1f, 1f)) add(Plate(quad(s * 36f, -126f, 48f * b, s * 38f, -30f, 40f * b * t, ch), P, LEGS))
        for (s in floatArrayOf(-1f, 1f)) add(Plate(quad(s * 36f, -142f, 34f, s * 36f, -108f, 34f, 10f), A, LEGS))
        for (s in floatArrayOf(-1f, 1f)) add(Plate(quad(s * 30f, -218f, 52f * b, s * 36f, -128f, 40f * b * t, ch), P, THIGHS))
        add(Plate(quad(0f, -254f, 98f * b, 0f, -206f, 70f * b, ch), S, BODY))
        add(Plate(quad(0f, -268f, 72f, 0f, -250f, 86f, 4f), A, BODY))
        add(Plate(quad(0f, -390f, 152f * b, 0f, -262f, 102f * b * t, ch + 3f), P, BODY))
        add(Plate(quad(0f, -374f, 88f, 0f, -292f, 64f * t, ch), S, BODY))
        add(Plate(quad(-100f, -394f, 56f * b, -100f, -338f, 56f * b, ch + 4f), A, ARM_L))
        add(Plate(quad(100f, -394f, 56f * b, 100f, -338f, 56f * b, ch + 4f), A, ARM_R))
        add(Plate(quad(-100f, -340f, 34f * b, -108f, -246f, 30f * b * t, ch), P, ARM_L))
        add(Plate(quad(100f, -340f, 34f * b, 108f, -246f, 30f * b * t, ch), P, ARM_R))
        add(Plate(quad(-108f, -252f, 40f * b, -112f, -152f, 34f * b * t, ch), S, ARM_L))
        add(Plate(quad(108f, -252f, 40f * b, 112f, -152f, 34f * b * t, ch), S, ARM_R))
        add(Plate(quad(0f, -442f, 50f, 0f, -396f, 60f, min(ch, 7f)), P, HEAD))
    }
    val plateCount: Int get() = plates.size

    private val out = Stroke(1.3f, join = StrokeJoin.Round)
    private val hair = Stroke(0.9f)
    private val holo = Stroke(1.5f, join = StrokeJoin.Round)
    private val reactorWindow = Path().apply { addOval(Rect(Offset(0f, -333f), 19f)) }
    private val bands = arrayOf(quad(-37f, -92f, 44f * b, -37.5f, -74f, 42f * b * t, 2f), quad(37f, -92f, 44f * b, 37.5f, -74f, 42f * b * t, 2f))
    private val stripe = quad(-100f, -372f, 56f * b, -100f, -362f, 56f * b, 1f)
    private val chevron = Path().apply { moveTo(-22f, -236f); lineTo(0f, -224f); lineTo(22f, -236f); lineTo(22f, -228f); lineTo(0f, -216f); lineTo(-22f, -228f); close() }

    /** What moves the robot this frame. */
    class Pose {
        var dip = 0f; var headTurn = 0f; var wave = 0f; var waveT = 0f
        var reactor = 0.2f; var fanDeg = 0f; var steam = 0f; var steamT = 0f
        var beaconL = 0f; var beaconR = 0f; var visor = 0.35f; var idleT = 0f; var flicker = 1f
        var unitText: TextLayoutResult? = null
        /** Seconds for the design hologram's scan line (0 holds it still). */
        var holoT = 0f
    }

    private fun color(role: Int): Color = when (role) { P -> style.primary; S -> style.secondary; else -> style.accent }

    private fun groupDy(group: Int, dip: Float): Float = when (group) { LEGS -> 0f; THIGHS -> dip * 3.5f; else -> dip * 7f }

    /**
     * Draws the robot at assembly [stage] (0 frame, 1 inner machinery, 2 armour, 3 paint, 4 markings, 5 complete),
     * [sp] of the way through that stage. The origin is between the feet.
     */
    fun DrawScope.draw(stage: Int, sp: Float, pose: Pose) {
        val dip = pose.dip
        val body = dip * 7f
        // The design hologram stands in the gantry until the armour is on: the chosen unit is seen from the first metre.
        if (stage < 3) blueprint(if (stage == 2) 0.85f * (1f - sp) + 0.25f else 1f, pose.holoT)
        // Behind the body: radiator fins, the survey unit's attitude nozzles, steam from the back vents.
        if (stage >= 1) translate(0f, body) { back(stage, sp, pose) }

        frame(if (stage == 0) sp else 1f, dip)
        if (stage >= 1) machinery(if (stage == 1) sp else 1f, dip, pose)

        if (stage >= 2) {
            val n = plates.size
            val fitted = if (stage == 2) ceil(sp * n).toInt().coerceIn(1, n) else n
            val painted = if (stage == 3) ceil(sp * n).toInt().coerceIn(0, n) else if (stage > 3) n else 0
            for (i in 0 until fitted) {
                val pl = plates[i]
                val col = if (i < painted) color(pl.role) else if (pl.role == P) PRIMER else PRIMER_DARK
                val dy = groupDy(pl.group, dip)
                val dx = if (pl.group == HEAD) pose.headTurn * 6f else 0f
                if (i == FOREARM_R && pose.wave > 0.01f) {
                    // The lower arm waves: it swings up about the elbow.
                    rotate(-118f * pose.wave + 16f * sin(pose.waveT * 6f) * pose.wave, Offset(108f, -250f + dy)) { translate(dx, dy) { plate(pl.path, col, i < painted && pl.role == P) } }
                } else translate(dx, dy) { plate(pl.path, col, i < painted && pl.role == P) }
                if (i == 11 && fitted > 11) translate(0f, body) { chestDetail(pose, painted > 11) }
            }
            translate(0f, body) {
                if (fitted > SHOULDER_R) shoulders(pose)
                if (fitted > FOREARM_R) hands(pose, dip)
                if (fitted >= n) head(pose)
                if (stage >= 4) markings(if (stage == 4) sp else 1f, pose)
            }
        }
    }

    /**
     * The design hologram of the finished unit: its plates in their own colours, see-through, with bright edges, the
     * visor band, the reactor ring and the unit's equipment in outline. One scan line climbs it slowly ([t] seconds;
     * 0 holds it still). Its light is steady: nothing blinks.
     */
    private fun DrawScope.blueprint(amount: Float, t: Float) {
        if (amount <= 0.01f) return
        // The light the projection throws on the floor.
        drawOval(Brush.radialGradient(listOf(HOLO.copy(alpha = 0.22f * amount), HOLO.copy(alpha = 0f)), Offset(0f, 4f), 150f), Offset(-150f, -14f), Size(300f, 36f))
        for (pl in plates) {
            val c = color(pl.role)
            drawPath(pl.path, lerp(c, HOLO, 0.25f).copy(alpha = 0.34f * amount))
            drawPath(pl.path, lerp(c, Color.White, 0.45f).copy(alpha = 0.80f * amount), style = holo)
        }
        drawRect(style.visor.copy(alpha = 0.75f * amount), Offset(-22f, -425f), Size(44f, 11f))
        drawCircle(HOLO.copy(alpha = 0.70f * amount), 19f, Offset(0f, -333f), style = holo)
        drawCircle(HOLO.copy(alpha = 0.22f * amount), 12f, Offset(0f, -333f))
        val line = lerp(style.accent, Color.White, 0.5f).copy(alpha = 0.75f * amount)
        if (style.workLamps) for (s in floatArrayOf(-1f, 1f)) drawRect(line, Offset(s * 122f - 7f, -382f), Size(14f, 12f), style = holo)
        if (style.toolArm) { drawRect(line, Offset(103f, -152f), Size(18f, 30f), style = holo); drawLine(line, Offset(112f, -122f), Offset(112f, -100f), 2f) }
        if (style.searchlight) drawCircle(line, 12f, Offset(118f, -412f), style = holo)
        if (style.winch) { drawCircle(line, 15f, Offset(-62f, -232f), style = holo); drawLine(line, Offset(-75f, -232f), Offset(-75f, -154f), 1.4f) }
        if (style.thrusters) for (s in floatArrayOf(-1f, 1f)) drawRect(line, Offset(s * 132f - 9f, -414f), Size(18f, 30f), style = holo)
        // Scan lines across the figure, and the one that climbs.
        var y = -440f
        while (y < 0f) { drawLine(HOLO.copy(alpha = 0.07f * amount), Offset(-150f, y), Offset(150f, y), 1f); y += 9f }
        val sy = -((t * 34f) % 450f)
        drawLine(Color.White.copy(alpha = 0.55f * amount), Offset(-150f, sy), Offset(150f, sy), 1.4f)
        drawRect(Brush.verticalGradient(listOf(HOLO.copy(alpha = 0f), HOLO.copy(alpha = 0.16f * amount)), startY = sy - 26f, endY = sy), Offset(-150f, sy - 26f), Size(300f, 26f))
    }

    /** One plate: its side face (the robot stands three-quarters on), its face, the light on it, its edge. */
    private fun DrawScope.plate(p: Path, col: Color, pattern: Boolean) {
        translate(8f, 1.5f) { drawPath(p, lerp(col, Color.Black, 0.5f)) }
        drawPath(p, col)
        if (pattern && style.camo) clipPath(p) {
            val bb = p.getBounds()
            for (k in 0 until 4) {
                val c = Offset(bb.left + hash01(k, bb.top.toInt()) * bb.width, bb.top + hash01(k + 7, bb.left.toInt()) * bb.height)
                drawOval(if (k % 2 == 0) style.secondary else style.accent.copy(alpha = 0.55f), Offset(c.x - 22f, c.y - 12f), Size(44f, 24f))
            }
        }
        if (pattern && style.quilt) clipPath(p) {
            val bb = p.getBounds()
            var y = bb.top + 12f
            while (y < bb.bottom) { drawLine(Color.Black.copy(alpha = 0.16f), Offset(bb.left, y), Offset(bb.right, y), 1f); y += 13f }
        }
        drawPath(p, SHADE)
        drawPath(p, EDGE, style = out)
    }

    /** The bare frame: struts and joints, built up from the feet. */
    private fun DrawScope.frame(sp: Float, dip: Float) {
        val n = ceil(sp * FRAME.size).toInt().coerceIn(1, FRAME.size)
        for (i in 0 until n) {
            val f = FRAME[i]
            val dy0 = if (f[1] < -130f) dip * (if (f[1] < -220f) 7f else 3.5f) else 0f
            val dy1 = if (f[3] < -130f) dip * (if (f[3] < -220f) 7f else 3.5f) else 0f
            val a = Offset(f[0], f[1] + dy0); val bb = Offset(f[2], f[3] + dy1)
            drawLine(FRAME_DARK, a, bb, f[4] + 2.4f, cap = StrokeCap.Round)
            drawLine(FRAME_STEEL, a, bb, f[4], cap = StrokeCap.Round)
            drawLine(FRAME_LIGHT, Offset(a.x - f[4] * 0.22f, a.y), Offset(bb.x - f[4] * 0.22f, bb.y), 1.2f, cap = StrokeCap.Round)
            drawCircle(FRAME_DARK, f[4] * 0.62f + 1.5f, bb); drawCircle(FRAME_JOINT, f[4] * 0.62f, bb)
        }
    }

    /** Inner machinery: the reactor's core, hydraulic cylinders at knees and ankles, actuators, cable runs. */
    private fun DrawScope.machinery(sp: Float, dip: Float, pose: Pose) {
        val n = ceil(sp * 6f).toInt().coerceIn(1, 6)
        val body = dip * 7f
        // 1. reactor core
        val rc = Offset(0f, -333f + body)
        drawCircle(FRAME_DARK, 23f, rc)
        drawCircle(Brush.radialGradient(listOf(REACTOR.copy(alpha = 0.35f + 0.65f * pose.reactor * pose.flicker), REACTOR_DEEP), rc, 20f), 19f, rc)
        if (n >= 2) for (s in floatArrayOf(-1f, 1f)) {
            // 2. knee hydraulics: the rod slides into its cylinder when the knees dip
            val top = Offset(s * 58f, -196f + dip * 3.5f); val bot = Offset(s * 56f, -70f)
            val mid = Offset((top.x + bot.x) / 2f, (top.y + bot.y) / 2f + dip * 6f)
            drawLine(ROD, top, mid, 3.2f, cap = StrokeCap.Round)
            drawLine(FRAME_DARK, mid, bot, 9f, cap = StrokeCap.Round); drawLine(CYL, mid, bot, 6.6f, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.3f), Offset(mid.x - 1.6f, mid.y), Offset(bot.x - 1.6f, bot.y), 1f)
        }
        if (n >= 3) for (s in floatArrayOf(-1f, 1f)) {
            // 3. ankle hydraulics
            val top = Offset(s * 60f, -62f); val bot = Offset(s * 62f, -10f)
            drawLine(ROD, top, Offset(top.x, -40f), 2.8f); drawLine(FRAME_DARK, Offset(top.x, -40f), bot, 8f, cap = StrokeCap.Round); drawLine(CYL, Offset(top.x, -40f), bot, 5.6f, cap = StrokeCap.Round)
        }
        if (n >= 4) for (s in floatArrayOf(-1f, 1f)) {
            // 4. shoulder and elbow actuators
            for (c in arrayOf(Offset(s * 100f, -366f + body), Offset(s * 108f, -248f + body))) {
                drawCircle(FRAME_DARK, 14.5f, c)
                drawCircle(Brush.radialGradient(listOf(FRAME_LIGHT, FRAME_STEEL), Offset(c.x - 4f, c.y - 5f), 18f), 13f, c)
                drawCircle(FRAME_DARK, 4f, c)
            }
        }
        if (n >= 5) {
            // 5. cable runs: amber down the spine to the hips, grey along the arms
            for (s in floatArrayOf(-1f, 1f)) {
                drawLine(CABLE, Offset(s * 9f, -310f + body), Offset(s * 9f, -252f + body), 2.2f)
                drawLine(CABLE, Offset(s * 9f, -252f + body), Offset(s * 30f, -222f + dip * 3.5f), 2.2f)
                drawLine(CABLE_GREY, Offset(s * 60f, -360f + body), Offset(s * 92f, -350f + body), 2f)
                drawLine(CABLE_GREY, Offset(s * 112f, -330f + body), Offset(s * 116f, -260f + body), 2f)
            }
        }
        if (n >= 6) for (s in floatArrayOf(-1f, 1f)) {
            // 6. cooling fans either side of the reactor
            val c = Offset(s * 50f, -300f + body)
            drawCircle(FRAME_DARK, 14f, c)
            rotate(pose.fanDeg * s, c) { for (k in 0 until 3) rotate(k * 120f, c) { drawLine(FRAME_LIGHT, c, Offset(c.x, c.y - 11f), 3.2f, cap = StrokeCap.Round) } }
            drawCircle(FRAME_STEEL, 14f, c, style = hair)
        }
    }

    /** Behind the body: radiator fins above the shoulders, the survey unit's nozzles, steam under acceleration. */
    private fun DrawScope.back(stage: Int, sp: Float, pose: Pose) {
        for (s in floatArrayOf(-1f, 1f)) {
            for (k in 0 until 5) {
                val x = s * (56f + k * 6f)
                drawLine(FRAME_DARK, Offset(x, -418f + k * 2f), Offset(x, -384f), 3.4f)
                drawLine(FRAME_STEEL, Offset(x - 0.6f, -418f + k * 2f), Offset(x - 0.6f, -384f), 1.2f)
            }
            if (style.thrusters && stage >= 3) {
                // Attitude nozzles: a soft steady blue (no flame).
                val c = Offset(s * 132f, -398f)
                drawRect(FRAME_STEEL, Offset(c.x - 9f, c.y - 16f), Size(18f, 30f))
                drawRect(Color.Black.copy(alpha = 0.5f), Offset(c.x - 9f, c.y - 16f), Size(18f, 30f), style = hair)
                drawCircle(Brush.radialGradient(listOf(NOZZLE.copy(alpha = 0.55f * pose.flicker), NOZZLE.copy(alpha = 0f)), Offset(c.x, c.y + 18f), 22f), 22f, Offset(c.x, c.y + 18f))
                drawOval(NOZZLE.copy(alpha = 0.85f * pose.flicker), Offset(c.x - 6f, c.y + 12f), Size(12f, 5f))
            }
            if (pose.steam > 0.02f) for (k in 0 until 4) {
                val u = ((pose.steamT * 0.5f + k * 0.25f) % 1f)
                drawCircle(Color.White.copy(alpha = 0.16f * pose.steam * (1f - u)), 9f + 16f * u, Offset(s * (70f + 10f * u), -420f - 50f * u))
            }
        }
    }

    /** The reactor window in the chest plate and the vents beside it. */
    private fun DrawScope.chestDetail(pose: Pose, painted: Boolean) {
        val rc = Offset(0f, -333f)
        drawCircle(EDGE, 22f, rc)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * pose.reactor * pose.flicker), REACTOR.copy(alpha = 0.5f + 0.5f * pose.reactor * pose.flicker), REACTOR_DEEP), rc, 21f), 19f, rc)
        drawCircle(Brush.radialGradient(listOf(REACTOR.copy(alpha = 0.30f * pose.reactor * pose.flicker), REACTOR.copy(alpha = 0f)), rc, 54f), 54f, rc)
        drawPath(reactorWindow, if (painted) style.accent else PRIMER_DARK, style = Stroke(3f))
        drawArc(Color.White.copy(alpha = 0.35f), 200f, 60f, false, Offset(rc.x - 15f, rc.y - 15f), Size(30f, 30f), style = hair)
        for (s in floatArrayOf(-1f, 1f)) {
            val c = Offset(s * 52f, -300f)
            drawCircle(EDGE, 13f, c)
            rotate(pose.fanDeg * s, c) { for (k in 0 until 3) rotate(k * 120f, c) { drawLine(FRAME_LIGHT, c, Offset(c.x, c.y - 10f), 3f, cap = StrokeCap.Round) } }
            for (k in -1..1) drawLine(EDGE, Offset(c.x - 12f, c.y + k * 6f), Offset(c.x + 12f, c.y + k * 6f), 1.4f)
        }
    }

    /** On the shoulder blocks: the beacons (the car's blinkers), the maintenance unit's work lamps, the rescue unit's searchlight. */
    private fun DrawScope.shoulders(pose: Pose) {
        for (s in floatArrayOf(-1f, 1f)) {
            val on = if (s < 0f) pose.beaconL else pose.beaconR
            val c = Offset(s * 100f, -398f)
            drawRect(EDGE, Offset(c.x - 9f, c.y - 1f), Size(18f, 5f))
            if (on > 0.02f) drawCircle(Brush.radialGradient(listOf(BEACON.copy(alpha = 0.6f * on), BEACON.copy(alpha = 0f)), c, 40f), 40f, c)
            drawArc(lerp(Color(0xFF6A4A1E), BEACON, on), 180f, 180f, true, Offset(c.x - 8f, c.y - 8f), Size(16f, 16f))
            if (style.workLamps) {
                val l = Offset(s * 122f, -376f)
                drawRect(EDGE, Offset(l.x - 7f, l.y - 6f), Size(14f, 12f))
                drawRect(LAMP, Offset(l.x - 5f, l.y + 1f), Size(10f, 4f))
                val cone = Path().apply { moveTo(l.x - 5f, l.y + 5f); lineTo(l.x + 5f, l.y + 5f); lineTo(l.x + s * 6f + 44f, l.y + 150f); lineTo(l.x + s * 6f - 44f, l.y + 150f); close() }
                drawPath(cone, Brush.verticalGradient(listOf(LAMP.copy(alpha = 0.20f), LAMP.copy(alpha = 0f)), startY = l.y, endY = l.y + 150f))
            }
        }
        if (style.searchlight) {
            val l = Offset(118f, -412f)
            val beam = Path().apply { moveTo(l.x, l.y - 7f); lineTo(l.x + 230f, l.y + 30f); lineTo(l.x + 210f, l.y + 110f); lineTo(l.x, l.y + 7f); close() }
            drawPath(beam, Brush.horizontalGradient(listOf(LAMP.copy(alpha = 0.22f), LAMP.copy(alpha = 0f)), startX = l.x, endX = l.x + 230f))
            drawCircle(EDGE, 12f, l); drawCircle(Brush.radialGradient(listOf(Color.White, LAMP), l, 10f), 9f, l)
        }
    }

    /** Gripper hands; the maintenance unit carries a tool on its right arm instead; the rescue unit's winch hangs at its hip. */
    private fun DrawScope.hands(pose: Pose, dip: Float) {
        for (s in floatArrayOf(-1f, 1f)) {
            if (s > 0f && pose.wave > 0.01f) continue // the waving arm's hand is drawn with it
            val w = Offset(s * 112f, -150f)
            if (s > 0f && style.toolArm) {
                drawRect(FRAME_DARK, Offset(w.x - 9f, w.y - 2f), Size(18f, 30f)); drawRect(CYL, Offset(w.x - 7f, w.y), Size(14f, 26f))
                drawLine(ROD, Offset(w.x, w.y + 28f), Offset(w.x, w.y + 50f), 3f, cap = StrokeCap.Round)
                drawRect(style.accent, Offset(w.x - 9f, w.y + 8f), Size(18f, 4f))
            } else for (k in floatArrayOf(-1f, 1f)) {
                val p = Path().apply { moveTo(w.x + k * 9f, w.y - 2f); lineTo(w.x + k * 13f, w.y + 22f); lineTo(w.x + k * 4f, w.y + 36f) }
                drawPath(p, FRAME_DARK, style = Stroke(8.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(p, FRAME_STEEL, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (style.winch) {
            val c = Offset(-62f, -232f)
            drawCircle(EDGE, 15f, c); drawCircle(FRAME_STEEL, 13f, c)
            for (k in 0 until 4) drawCircle(CABLE_GREY, 4f + k * 2.4f, c, style = hair)
            drawLine(CABLE_GREY, Offset(c.x - 13f, c.y), Offset(c.x - 13f, c.y + 78f), 1.6f)
            val hook = Path().apply { moveTo(c.x - 13f, c.y + 78f); quadraticTo(c.x - 13f, c.y + 92f, c.x - 5f, c.y + 90f) }
            drawPath(hook, style.secondary, style = Stroke(3.4f, cap = StrokeCap.Round))
        }
    }

    /** The head's visor band (it brightens with the headlights; a finished robot runs a slow check across it) and its mast. */
    private fun DrawScope.head(pose: Pose) {
        val dx = pose.headTurn * 6f
        drawRect(EDGE, Offset(-14f + dx * 0.5f, -398f), Size(28f, 12f))
        val l = -22f + dx + pose.headTurn * 3f
        drawRect(EDGE, Offset(l - 2f, -427f), Size(48f, 15f))
        drawRect(style.visor.copy(alpha = 0.35f + 0.65f * pose.visor), Offset(l, -425f), Size(44f, 11f))
        drawRect(Brush.radialGradient(listOf(style.visor.copy(alpha = 0.5f * pose.visor), style.visor.copy(alpha = 0f)), Offset(l + 22f, -419f), 46f), Offset(l - 24f, -450f), Size(92f, 62f))
        val sweep = ((pose.idleT * 0.35f) % 1f)
        if (pose.idleT > 0f) drawRect(Color.White.copy(alpha = 0.55f), Offset(l + sweep * 38f, -425f), Size(6f, 11f))
        drawLine(FRAME_STEEL, Offset(24f + dx, -438f), Offset(27f + dx, -462f), 2f, cap = StrokeCap.Round)
    }

    /** Markings, in the order they are applied: bands on the shins, a shoulder stripe, the unit's number, a hip chevron. */
    private fun DrawScope.markings(sp: Float, pose: Pose) {
        val n = ceil(sp * 4f).toInt().coerceIn(1, 4)
        val mark = if (style.accent.luminanceApprox() < 0.25f) style.secondary else style.accent
        translate(0f, -pose.dip * 7f) { for (p in bands) drawPath(p, mark) }
        if (n >= 2) drawPath(stripe, if (style.primary.luminanceApprox() > 0.6f) EDGE else Color(0xFFECEAE4))
        if (n >= 3) pose.unitText?.let { drawTextCentered(it, 100f, -352f) }
        if (n >= 4) drawPath(chevron, mark)
    }

    private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    companion object {
        val PRIMER = Color(0xFF8B9096)
        val PRIMER_DARK = Color(0xFF73787F)
        val EDGE = Color(0xFF14161A)
        val FRAME_DARK = Color(0xFF1C1F24)
        val FRAME_STEEL = Color(0xFF596069)
        val FRAME_LIGHT = Color(0xFF99A1AB)
        val FRAME_JOINT = Color(0xFF7B838D)
        val ROD = Color(0xFFD9DEE4)
        val CYL = Color(0xFF8E96A0)
        val CABLE = Color(0xFFE0A02A)
        val CABLE_GREY = Color(0xFF8FA0B2)
        val REACTOR = Color(0xFF7FE0FF)
        val REACTOR_DEEP = Color(0xFF0E3440)
        val BEACON = Color(0xFFFFB03A)
        val LAMP = Color(0xFFFFF0CF)
        val NOZZLE = Color(0xFF8FD0FF)
        val HOLO = Color(0xFF7FE0FF)
        /** The hangar's key light from the upper left, across the robot's width. */
        val SHADE = Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.17f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.30f), startX = -140f, endX = 140f)

        /** The frame's struts (x0, y0, x1, y1, width), in the order they are erected: feet, legs, hips, spine, shoulders, arms, head. */
        val FRAME = arrayOf(
            floatArrayOf(-64f, -4f, -14f, -4f, 9f), floatArrayOf(14f, -4f, 64f, -4f, 9f),
            floatArrayOf(-38f, -30f, -36f, -124f, 11f), floatArrayOf(38f, -30f, 36f, -124f, 11f),
            floatArrayOf(-36f, -124f, -30f, -226f, 12f), floatArrayOf(36f, -124f, 30f, -226f, 12f),
            floatArrayOf(-34f, -226f, 34f, -226f, 12f),
            floatArrayOf(0f, -226f, 0f, -388f, 14f),
            floatArrayOf(-100f, -366f, 100f, -366f, 11f),
            floatArrayOf(-100f, -366f, -108f, -248f, 9f), floatArrayOf(100f, -366f, 108f, -248f, 9f),
            floatArrayOf(-108f, -248f, -112f, -152f, 8f), floatArrayOf(108f, -248f, 112f, -152f, 8f),
            floatArrayOf(0f, -388f, 0f, -420f, 8f),
        )
    }
}
