package io.vroomi.character.render.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.engine.EngineGeometry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The ink of the service-manual sheet. Day prints graphite linework and flat colour-coded systems on off-white
 * paper; night is the inverse, light linework on a dark slate sheet. Each system has one colour: intake pale
 * blue, exhaust oxide red, oil amber, coolant teal-green, electrical violet. No fault reds.
 */
internal class ManualInk(
    var paper: Color, var gridFine: Color, var gridMajor: Color, var line: Color, var lineSoft: Color,
    var cut: Color, var hatch: Color, var cavity: Color, var metal: Color, var metalLight: Color, var metalDark: Color,
    var intake: Color, var exhaust: Color, var oil: Color, var coolant: Color, var electric: Color, var electricLight: Color,
    var electricDark: Color, var eco: Color, var sport: Color, var stamp: Color, var lamp: Color, var cell: Color, var cellLow: Color,
    var fire: Color, var clean: Color, var magnetN: Color, var magnetS: Color, var belt: Color, var wash: Color,
) {
    /** This ink becomes [a] blended toward [b] by [t] (no allocation: colours are values). */
    fun set(a: ManualInk, b: ManualInk, t: Float) {
        paper = lerp(a.paper, b.paper, t); gridFine = lerp(a.gridFine, b.gridFine, t); gridMajor = lerp(a.gridMajor, b.gridMajor, t)
        line = lerp(a.line, b.line, t); lineSoft = lerp(a.lineSoft, b.lineSoft, t); cut = lerp(a.cut, b.cut, t); hatch = lerp(a.hatch, b.hatch, t)
        cavity = lerp(a.cavity, b.cavity, t); metal = lerp(a.metal, b.metal, t); metalLight = lerp(a.metalLight, b.metalLight, t)
        metalDark = lerp(a.metalDark, b.metalDark, t); intake = lerp(a.intake, b.intake, t); exhaust = lerp(a.exhaust, b.exhaust, t)
        oil = lerp(a.oil, b.oil, t); coolant = lerp(a.coolant, b.coolant, t); electric = lerp(a.electric, b.electric, t)
        electricLight = lerp(a.electricLight, b.electricLight, t); electricDark = lerp(a.electricDark, b.electricDark, t)
        eco = lerp(a.eco, b.eco, t); sport = lerp(a.sport, b.sport, t); stamp = lerp(a.stamp, b.stamp, t); lamp = lerp(a.lamp, b.lamp, t)
        cell = lerp(a.cell, b.cell, t); cellLow = lerp(a.cellLow, b.cellLow, t); fire = lerp(a.fire, b.fire, t); clean = lerp(a.clean, b.clean, t)
        magnetN = lerp(a.magnetN, b.magnetN, t); magnetS = lerp(a.magnetS, b.magnetS, t); belt = lerp(a.belt, b.belt, t); wash = lerp(a.wash, b.wash, t)
    }

    fun copy() = ManualInk(paper, gridFine, gridMajor, line, lineSoft, cut, hatch, cavity, metal, metalLight, metalDark, intake, exhaust, oil,
        coolant, electric, electricLight, electricDark, eco, sport, stamp, lamp, cell, cellLow, fire, clean, magnetN, magnetS, belt, wash)

    companion object {
        val DAY = ManualInk(
            paper = Color(0xFFF4F2EB), gridFine = Color(0xFFDCE3E6), gridMajor = Color(0xFFC2CED4), line = Color(0xFF1B1D20), lineSoft = Color(0xFF5E666C),
            cut = Color(0xFFFFFFFF), hatch = Color(0xFF2A2E33), cavity = Color(0xFFE6E9EA), metal = Color(0xFFC8CDD1), metalLight = Color(0xFFE4E7E9),
            metalDark = Color(0xFF8E979E), intake = Color(0xFF9CCDE8), exhaust = Color(0xFFC2543B), oil = Color(0xFFE3A532), coolant = Color(0xFF34A58A),
            electric = Color(0xFF8461C2), electricLight = Color(0xFFB79BE6), electricDark = Color(0xFF5B3E96), eco = Color(0xFF2E9C8A), sport = Color(0xFFE08A2E),
            stamp = Color(0xFF248F4E), lamp = Color(0xFFFFD9A0), cell = Color(0xFF34A58A), cellLow = Color(0xFFE3A532), fire = Color(0xFFE8833E),
            clean = Color(0xFF7FA9EA), magnetN = Color(0xFF6F98C9), magnetS = Color(0xFFC2543B), belt = Color(0xFF3A3F45), wash = Color(0xFFF4F2EB),
        )
        val NIGHT = ManualInk(
            paper = Color(0xFF131A21), gridFine = Color(0xFF19222A), gridMajor = Color(0xFF212C36), line = Color(0xFFAEBDC8), lineSoft = Color(0xFF6A7B88),
            cut = Color(0xFF1A232B), hatch = Color(0xFF5F717E), cavity = Color(0xFF161E25), metal = Color(0xFF2E3943), metalLight = Color(0xFF3A4651),
            metalDark = Color(0xFF232C35), intake = Color(0xFF4F8099), exhaust = Color(0xFF9A4C38), oil = Color(0xFFA27A2E), coolant = Color(0xFF2A7A68),
            electric = Color(0xFF775CAE), electricLight = Color(0xFF8E78BE), electricDark = Color(0xFF45346F), eco = Color(0xFF3A9C88), sport = Color(0xFFC07D36),
            stamp = Color(0xFF4FB874), lamp = Color(0xFFFFC98A), cell = Color(0xFF2A7A68), cellLow = Color(0xFFA27A2E), fire = Color(0xFFC6763A),
            clean = Color(0xFF6F8FC4), magnetN = Color(0xFF4A6F95), magnetS = Color(0xFF944C38), belt = Color(0xFF6A7B88), wash = Color(0xFF131A21),
        )
    }
}

/**
 * Fixed design space of the drawing (units). The VE-4T is drawn at [S] units per millimetre, so pistons,
 * rods and crank come straight from [EngineGeometry]. Each cylinder is drawn as its own end-on slice
 * placed side by side (an explanatory cutaway convention), so the rods keep their true length.
 */
internal object EL {
    const val W = 1280f
    const val H = 640f
    val S = 1.45f
    val CYL_X = floatArrayOf(420f, 570f, 720f, 870f)
    const val CRANK_Y = 500f
    val R = (EngineGeometry.CRANK_R_MM * S).toFloat()
    val ROD = (EngineGeometry.ROD_MM * S).toFloat()
    val BORE = (EngineGeometry.BORE_MM * S).toFloat()
    const val CROWN_ABOVE_PIN = 30f
    const val PISTON_H = 74f
    val DECK = CRANK_Y - (R + ROD) - CROWN_ABOVE_PIN - 8f
    const val LINER = 6f

    // Head, valves, cams
    const val HEAD_TOP = 118f
    const val CAM_Y = 142f
    const val VALVE_DX = 24f
    const val VALVE_HALF = 16f
    /** Valve stem top (bucket) sits on the cam base circle (r 13). */
    const val STEM_TOP = CAM_Y + 13f
    /** Valve seats in the pent-roof chamber. */
    val SEAT get() = DECK - 12f
    val LIFT = (EngineGeometry.LIFT_MM * S).toFloat()
    const val COVER_TOP = 82f

    // Block
    const val BLOCK_L = 330f
    const val BLOCK_R = 960f
    const val BLOCK_B = 440f
    const val CASE_B = 562f

    // Intake and exhaust
    const val PLENUM_L = 360f
    const val PLENUM_R = 930f
    const val PLENUM_T = 30f
    const val PLENUM_B = 62f
    val THROTTLE = Offset(957f, 46f)
    val TURBO = Offset(1080f, 228f)
    const val TURBO_R = 50f
    const val EXH_Y = 172f
    const val CAT_L = 1050f
    const val CAT_R = 1110f
    const val CAT_T = 332f
    const val CAT_B = 440f

    // Gearbox (behind the engine, on the crank axis)
    const val GB_L = 975f
    const val GB_R = 1270f
    const val GB_T = 470f
    const val GB_B = 630f

    // Timing chain and front accessories
    val CHAIN_CRANK = Offset(292f, CRANK_Y)
    const val CHAIN_CRANK_R = 14f
    val CHAIN_CAM = Offset(292f, CAM_Y)
    const val CHAIN_CAM_R = 28f
    val P_CRANK = Pulley(200f, CRANK_Y, 44f)
    val P_PUMP = Pulley(240f, 300f, 24f)
    val P_ALT = Pulley(172f, 206f, 30f)
    val P_AC = Pulley(128f, 410f, 32f)
    /** Belt pulleys in loop order (clockwise on screen). */
    val BELT = listOf(P_ALT, P_PUMP, P_CRANK, P_AC)

    // Radiator and fan (fan seen edge-on)
    const val RAD_L = 14f
    const val RAD_R = 42f
    const val RAD_T = 140f
    const val RAD_B = 580f
    val FAN = Offset(76f, 360f)
    const val FAN_R = 118f

    // Fuse box with the blinker relay
    const val FUSE_L = 1168f
    const val FUSE_R = 1268f
    const val FUSE_T = 14f
    const val FUSE_B = 96f

    // Heater core and hose
    val HEATER = floatArrayOf(326f, 150f, 300f, 104f, 236f, 104f, 236f, 64f)
    const val HEATER_L = 140f
    const val HEATER_R = 236f
    const val HEATER_T = 30f
    const val HEATER_B = 92f

    // ---------------------------------------------------------------- EV (VE-M1)
    val MOTOR = Offset(440f, 300f)
    const val HOUSING_R = 232f
    const val STATOR_R = 214f
    const val STATOR_IN = 142f
    const val ROTOR_R = 134f
    const val SHAFT_R = 26f
    val PINION = Offset(720f, 300f)
    const val PINION_R = 26f
    val IDLER = Offset(790f, 360f)
    const val IDLER_R = 66f
    const val IDLER_PINION_R = 22f
    val FINAL = Offset(846f, 446f)
    const val FINAL_R = 80f
    const val RED_L = 650f
    const val RED_R = 950f
    const val RED_T = 222f
    const val RED_B = 540f
    const val INV_L = 700f
    const val INV_R = 1000f
    const val INV_T = 60f
    const val INV_B = 200f
    val INV_DIAL = Offset(850f, 130f)
    const val BAT_L = 120f
    const val BAT_R = 1262f
    const val BAT_T = 558f
    const val BAT_B = 626f
    const val CELLS = 24
    val ECOMP = Offset(1140f, 300f)
    const val ECOMP_R = 44f

    /** Polylines (x, y pairs) along which particles and power pulses travel. */
    val INTAKE_PATH = floatArrayOf(1080f, 178f, 1080f, 46f, 985f, 46f, 930f, 46f, 380f, 46f)
    val EXHAUST_PATH = floatArrayOf(964f, EXH_Y, 1030f, EXH_Y, 1040f, 218f, 1080f, 278f, 1080f, CAT_B, 1080f, 458f, 1270f, 458f)
    val HV_BAT_INV = floatArrayOf(1030f, BAT_T, 1030f, 130f, INV_R, 130f)
    val HV_INV_MOTOR = floatArrayOf(INV_L, 110f, 600f, 110f, 572f, 112f)
    val HV_COMP = floatArrayOf(ECOMP.x, BAT_T, ECOMP.x, ECOMP.y + ECOMP_R)

    /** Belt loop (outer tangents and arcs) sampled every few units, built once. */
    val BELT_POINTS: FloatArray by lazy { loop(BELT, 6f) }
    /** Timing chain loop. */
    val CHAIN_POINTS: FloatArray by lazy { loop(listOf(Pulley(CHAIN_CAM.x, CHAIN_CAM.y, CHAIN_CAM_R), Pulley(CHAIN_CRANK.x, CHAIN_CRANK.y, CHAIN_CRANK_R)), 5f) }

    /** Cumulative lengths of a polyline (x, y pairs). */
    fun lengths(p: FloatArray): FloatArray {
        val n = p.size / 2
        val out = FloatArray(n)
        for (i in 1 until n) out[i] = out[i - 1] + hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1])
        return out
    }

    /** Point at distance [d] along polyline [p] with cumulative lengths [len]; writes into [out] (no allocation). */
    fun pointAt(p: FloatArray, len: FloatArray, d: Float, out: FloatArray) {
        val total = len[len.size - 1]
        var s = d % total; if (s < 0) s += total
        var i = 1
        while (i < len.size - 1 && len[i] < s) i++
        val seg = (len[i] - len[i - 1]).coerceAtLeast(1e-3f)
        val u = ((s - len[i - 1]) / seg).coerceIn(0f, 1f)
        out[0] = p[2 * i - 2] + (p[2 * i] - p[2 * i - 2]) * u
        out[1] = p[2 * i - 1] + (p[2 * i + 1] - p[2 * i - 1]) * u
    }

    /**
     * A closed belt around pulleys given in clockwise order: outer tangent lines joined by arcs,
     * sampled as a closed polyline (first point repeated at the end).
     */
    private fun loop(ps: List<Pulley>, step: Float): FloatArray {
        val n = ps.size
        // Outer tangent from a to b for a clockwise loop: the belt lies on the left of the travel direction.
        val tStart = Array(n) { Offset.Zero }; val tEnd = Array(n) { Offset.Zero }
        for (i in 0 until n) {
            val a = ps[i]; val b = ps[(i + 1) % n]
            val dx = b.x - a.x; val dy = b.y - a.y; val d = hypot(dx, dy)
            val base = atan2(dy, dx)
            val off = acos(((a.r - b.r) / d).coerceIn(-1f, 1f))
            val ang = base - off // normal pointing outward (to the left of travel in screen coords)
            tStart[i] = Offset(a.x + a.r * cos(ang), a.y + a.r * sin(ang))
            tEnd[i] = Offset(b.x + b.r * cos(ang), b.y + b.r * sin(ang))
        }
        val pts = ArrayList<Float>()
        for (i in 0 until n) {
            // Line from tStart[i] to tEnd[i]
            val s = tStart[i]; val e = tEnd[i]
            val len = hypot(e.x - s.x, e.y - s.y); val k = (len / step).toInt().coerceAtLeast(1)
            for (j in 0 until k) { pts += s.x + (e.x - s.x) * j / k; pts += s.y + (e.y - s.y) * j / k }
            // Arc around pulley i+1 from tEnd[i] to tStart[i+1] (clockwise on screen = increasing angle)
            val p = ps[(i + 1) % n]
            var a0 = atan2(e.y - p.y, e.x - p.x)
            val next = tStart[(i + 1) % n]
            var a1 = atan2(next.y - p.y, next.x - p.x)
            while (a1 < a0) a1 += (2 * PI).toFloat()
            if (a1 - a0 > 2 * PI - 1e-3) a1 -= (2 * PI).toFloat()
            val arcLen = abs(a1 - a0) * p.r; val m = (arcLen / step).toInt().coerceAtLeast(1)
            for (j in 0 until m) { val a = a0 + (a1 - a0) * j / m; pts += p.x + p.r * cos(a); pts += p.y + p.r * sin(a) }
            a0 = a1
        }
        pts += pts[0]; pts += pts[1]
        return pts.toFloatArray()
    }
}

internal class Pulley(val x: Float, val y: Float, val r: Float) {
    val c: Offset get() = Offset(x, y)
}
