package io.vroomi.character.render.hangar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.hangar.HangarSim
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import io.vroomi.character.render.theme.drawMusicNotes

/**
 * HANGAR: a lit hangar and the user's own robot on its gantry (spec 5.12). The robot is built by the distance
 * driven: frame, inner machinery, armour, paint, markings; the upgrade step chooses which HX it is (work,
 * maintenance, rescue, polar, survey). It stands 80 % of the screen tall between the gantry's towers; workers'
 * silhouettes give its size.
 *
 * The bay (walls, roof, floor, towers) is one cached bitmap. The robot, the crane, the clamps, the door's weather,
 * the lights and the workers are drawn every frame.
 */
internal class HangarPainter : ThemePainter<HangarSim>() {
    override val minDesignWidth = 1200f
    override val focusWidth = 860f
    override fun headroom(sim: HangarSim): Color = Color(0xFF0B0C0F)
    override val backdrop = Color(0xFF14161A)

    private val base = LayerSlot(SceneTheme.HANGAR)
    private val mechs = arrayOfNulls<Mech>(MechStyle.STEPS.size)
    private val pose = Mech.Pose()
    private var textsOf: ThemeTexts? = null
    private val unitTexts = HashMap<Int, TextLayoutResult>()

    private fun mech(model: Int): Mech = mechs[model] ?: Mech(MechStyle.STEPS[model]).also { mechs[model] = it }

    /** The unit's number as painted on the shoulder block ("01", "02", ...). */
    private fun unitText(tx: ThemeTexts, unit: Int, model: Int): TextLayoutResult {
        if (textsOf !== tx) { textsOf = tx; unitTexts.clear() }
        return unitTexts.getOrPut(unit) {
            tx.get("%02d".format(unit.coerceIn(1, 99)), 22f, Color(0xFFF4F2EC), bold = true)
        }
    }

    override fun drawPx(s: DrawScope, sim: HangarSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val w = f.w.roundToInt(); val h = f.h.roundToInt()
        val key = (w.toLong() shl 20) xor h.toLong()
        val k = f.k
        val bmp = base.get(key, w, h, f.density) {
            withTransform({ scale(k, k, Offset.Zero) }) { bayStatic(f.dw, f.dh, f.cx) }
        }
        s.drawImage(bmp, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(bmp.width, bmp.height))
    }

    override fun draw(s: DrawScope, sim: HangarSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        val cx = f.cx
        val t = sim.decoTime
        bayDoor(cx, sim.night, sim.rain, t)
        bayVent(cx, sim.ventAngle)
        bayWet(f.dw, f.dh, cx, sim.rain)
        bayNight(f.dw, f.dh, cx, sim.night)

        // The unit on order: its code and its colours on the wall, whatever the state of the build.
        val unitStyle = MechStyle.STEPS[f.model]
        for (side in floatArrayOf(-1f, 1f)) bayBanner(cx + side * 268f, unitStyle, sim.night)
        unitSign(cx - 352f, 214f, unitStyle, signText(tx, f.model))

        // Workers for scale: one crosses the floor in front, one keeps to the catwalk, one waits by the tower.
        val walk = (t * (if (sim.parked) 0.035 else 0.02)).let { (it % 2.0).toFloat() }
        val wx = cx - 420f + 300f * (if (walk < 1f) walk else 2f - walk)
        worker(wx, Bay.FEET + 34f, sin(t * 3.0).toFloat())
        worker(cx - Bay.GANTRY + 26f + 14f * sin(t * 0.21).toFloat(), 352f, 0f)
        worker(cx + Bay.GANTRY + 62f, Bay.FEET + 10f, 0f)

        // The robot.
        val stage = sim.stage
        // A finished robot shifts its weight a little while it waits (decoration time stands still during a safety hold).
        val idle = if (stage >= 5 && !sim.reducedMotion && !sim.calm) (0.06 + 0.06 * sin(t * 0.9)).toFloat() else 0f
        // Music in the car: the robot keeps time with its knees.
        val groove = if (sim.reducedMotion) 0f else 0.12f * sim.listening * sim.beat
        pose.dip = (sim.kneeDip + idle + groove).coerceAtMost(1f)
        pose.headTurn = sim.headTurn
        pose.wave = sim.wave; pose.waveT = t.toFloat()
        pose.reactor = sim.reactor
        pose.fanDeg = ((sim.fanAngle * 180.0 / PI) % 360.0).toFloat()
        pose.steam = sim.steam; pose.steamT = t.toFloat()
        pose.beaconL = sim.beaconL; pose.beaconR = sim.beaconR
        pose.visor = sim.visor
        pose.idleT = if (stage >= 5 && !sim.calm) t.toFloat() else 0f
        pose.flicker = (0.93 + 0.07 * sin(t * 1.9)).toFloat()
        pose.unitText = unitText(tx, sim.unit, f.model)
        pose.holoT = if (sim.calm || sim.reducedMotion) 0f else t.toFloat()
        val m = mech(f.model)
        withTransform({ translate(cx, Bay.FEET); scale(Bay.ROBOT, Bay.ROBOT, Offset.Zero) }) { with(m) { draw(stage, sim.stageProgress, pose) } }

        drawMusicNotes(cx + 90f, 190f, Color.White, sim.listening, t, glow = Color(0xFFFFB347))

        // The clamps and the amber lamp (after harsh braking), in front of the robot's waist.
        clamps(cx, sim.clamp, sim.lampAngle)

        // The crane: it fits a small part for a star and lifts an armour plate for a kilometre; otherwise it waits.
        val km = sim.since(sim.milestoneAt).toFloat()
        val st = sim.since(sim.starAt).toFloat()
        when {
            km in 0f..LIFT_S -> {
                val u = km / LIFT_S
                crane(cx - 150f, 330f - 250f * smooth(u), plate = true)
            }
            st in 0f..FIT_S -> {
                val u = st / FIT_S
                val x = cx + 300f - 236f * smooth((u / 0.35f).coerceAtMost(1f)) + 236f * smooth(((u - 0.75f) / 0.25f).coerceIn(0f, 1f))
                val drop = 24f + 110f * (smooth(((u - 0.3f) / 0.2f).coerceIn(0f, 1f)) - smooth(((u - 0.55f) / 0.2f).coerceIn(0f, 1f)))
                crane(x, drop, plate = false)
            }
            else -> crane(cx + 300f, 24f, plate = false)
        }
    }

    /** "HX-1" .. "HX-5", large enough to read at a glance. */
    private fun signText(tx: ThemeTexts, model: Int): TextLayoutResult {
        if (textsOf !== tx) { textsOf = tx; unitTexts.clear() }
        return unitTexts.getOrPut(-1 - model) { tx.get("HX-%d".format(model + 1), 36f, Color(0xFFF4F2EC), bold = true) }
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private companion object {
        const val LIFT_S = 5f
        const val FIT_S = 4.5f
    }
}
