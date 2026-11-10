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
 * The colours of the cutaway studio. The room is graphite by day and by night; the metals are bright. Cut faces
 * are signal red with fine hatching, the way a display engine is painted. Fluids keep one colour each (air blue,
 * hot gas orange, oil amber, coolant teal-green); high-voltage parts are orange. No fault reds.
 */
internal class EngineInk(
    var paper: Color, var bgTop: Color, var bgBottom: Color, var line: Color, var lineSoft: Color, var text: Color, var textSoft: Color,
    var cut: Color, var hatch: Color, var cavity: Color, var metal: Color, var metalLight: Color, var metalDark: Color, var cast: Color,
    var iron: Color, var white: Color, var intake: Color, var gasHot: Color, var exhaust: Color, var oil: Color, var coolant: Color, var rubber: Color,
    var electric: Color, var electricLight: Color, var electricDark: Color, var hv: Color, var eco: Color, var sport: Color, var stamp: Color,
    var lamp: Color, var cell: Color, var cellLow: Color, var fire: Color, var clean: Color, var magnetN: Color, var magnetS: Color, var belt: Color,
    var copper: Color, var gold: Color, var bronze: Color, var titanium: Color, var carbon: Color, var ceramic: Color, var panel: Color,
    var panelLine: Color, var mist: Color,
) {
    /** This ink becomes [a] blended toward [b] by [t] (no allocation: colours are values). */
    fun set(a: EngineInk, b: EngineInk, t: Float) {
        paper = lerp(a.paper, b.paper, t); bgTop = lerp(a.bgTop, b.bgTop, t); bgBottom = lerp(a.bgBottom, b.bgBottom, t); line = lerp(a.line, b.line, t)
        lineSoft = lerp(a.lineSoft, b.lineSoft, t); text = lerp(a.text, b.text, t); textSoft = lerp(a.textSoft, b.textSoft, t); cut = lerp(a.cut, b.cut, t)
        hatch = lerp(a.hatch, b.hatch, t); cavity = lerp(a.cavity, b.cavity, t); metal = lerp(a.metal, b.metal, t); metalLight = lerp(a.metalLight, b.metalLight, t)
        metalDark = lerp(a.metalDark, b.metalDark, t); cast = lerp(a.cast, b.cast, t); iron = lerp(a.iron, b.iron, t); white = lerp(a.white, b.white, t)
        intake = lerp(a.intake, b.intake, t); gasHot = lerp(a.gasHot, b.gasHot, t); exhaust = lerp(a.exhaust, b.exhaust, t); oil = lerp(a.oil, b.oil, t)
        coolant = lerp(a.coolant, b.coolant, t); rubber = lerp(a.rubber, b.rubber, t); electric = lerp(a.electric, b.electric, t); electricLight = lerp(a.electricLight, b.electricLight, t)
        electricDark = lerp(a.electricDark, b.electricDark, t); hv = lerp(a.hv, b.hv, t); eco = lerp(a.eco, b.eco, t); sport = lerp(a.sport, b.sport, t)
        stamp = lerp(a.stamp, b.stamp, t); lamp = lerp(a.lamp, b.lamp, t); cell = lerp(a.cell, b.cell, t); cellLow = lerp(a.cellLow, b.cellLow, t)
        fire = lerp(a.fire, b.fire, t); clean = lerp(a.clean, b.clean, t); magnetN = lerp(a.magnetN, b.magnetN, t); magnetS = lerp(a.magnetS, b.magnetS, t)
        belt = lerp(a.belt, b.belt, t); copper = lerp(a.copper, b.copper, t); gold = lerp(a.gold, b.gold, t); bronze = lerp(a.bronze, b.bronze, t)
        titanium = lerp(a.titanium, b.titanium, t); carbon = lerp(a.carbon, b.carbon, t); ceramic = lerp(a.ceramic, b.ceramic, t); panel = lerp(a.panel, b.panel, t)
        panelLine = lerp(a.panelLine, b.panelLine, t); mist = lerp(a.mist, b.mist, t)
    }

    fun copy() = EngineInk(
        paper, bgTop, bgBottom, line, lineSoft, text, textSoft, cut, hatch, cavity, metal, metalLight, metalDark, cast, iron, white, intake, gasHot,
        exhaust, oil, coolant, rubber, electric, electricLight, electricDark, hv, eco, sport, stamp, lamp, cell, cellLow, fire, clean, magnetN,
        magnetS, belt, copper, gold, bronze, titanium, carbon, ceramic, panel, panelLine, mist,
    )

    companion object {
        val DAY = EngineInk(
            paper = Color(0xFF1C2024), bgTop = Color(0xFF262B30), bgBottom = Color(0xFF14171A), line = Color(0xFF0A0C0E), lineSoft = Color(0xFF3B4249),
            text = Color(0xFFE8ECEF), textSoft = Color(0xFF95A0AA), cut = Color(0xFFC2281F), hatch = Color(0xFF7C1611), cavity = Color(0xFF111418),
            metal = Color(0xFFAAB2B9), metalLight = Color(0xFFD8DDE1), metalDark = Color(0xFF6C757D), cast = Color(0xFF99A1A8),
            iron = Color(0xFF565C62), white = Color(0xFFF3F5F6), intake = Color(0xFF72BCEA), gasHot = Color(0xFFE9743A), exhaust = Color(0xFF8C6A58),
            oil = Color(0xFFE2A22C), coolant = Color(0xFF2FAE92), rubber = Color(0xFF23272B), electric = Color(0xFF9B78E4),
            electricLight = Color(0xFFC0A8F2), electricDark = Color(0xFF6A4DB0), hv = Color(0xFFF08A24), eco = Color(0xFF37B9A2),
            sport = Color(0xFFEB952F), stamp = Color(0xFF52C980), lamp = Color(0xFFFFD9A0), cell = Color(0xFF2FAE92), cellLow = Color(0xFFE2A22C),
            fire = Color(0xFFFFA04E), clean = Color(0xFF94BBFF), magnetN = Color(0xFF5F92D0), magnetS = Color(0xFFC65A40), belt = Color(0xFF17191C),
            copper = Color(0xFFC98048), gold = Color(0xFFD0A74C), bronze = Color(0xFFB28E58), titanium = Color(0xFFA59B90), carbon = Color(0xFF1D2024),
            ceramic = Color(0xFFCDBB9E), panel = Color(0xFF0C0F12), panelLine = Color(0xFF2D343A), mist = Color(0xFFB9C5CD),
        )
        /** Night: the key light is lower, the room darker; the instruments and the fluids keep their colours. */
        val NIGHT = EngineInk(
            paper = Color(0xFF14171A), bgTop = Color(0xFF1B1F23), bgBottom = Color(0xFF0E1012), line = Color(0xFF0A0C0E), lineSoft = Color(0xFF2D3036),
            text = Color(0xFFE8ECEF), textSoft = Color(0xFF95A0AA), cut = Color(0xFF911D16), hatch = Color(0xFF5D100C), cavity = Color(0xFF0C0E11),
            metal = Color(0xFF7F8388), metalLight = Color(0xFFA1A3A6), metalDark = Color(0xFF51565C), cast = Color(0xFF73777C),
            iron = Color(0xFF414448), white = Color(0xFFF3F5F6), intake = Color(0xFF72BCEA), gasHot = Color(0xFFE9743A), exhaust = Color(0xFF694E41),
            oil = Color(0xFFA97720), coolant = Color(0xFF24806C), rubber = Color(0xFF1B1C1F), electric = Color(0xFF9B78E4),
            electricLight = Color(0xFFC0A8F2), electricDark = Color(0xFF503882), hv = Color(0xFFF08A24), eco = Color(0xFF37B9A2),
            sport = Color(0xFFEB952F), stamp = Color(0xFF52C980), lamp = Color(0xFFFFD9A0), cell = Color(0xFF24806C), cellLow = Color(0xFFA97720),
            fire = Color(0xFFFFA04E), clean = Color(0xFF94BBFF), magnetN = Color(0xFF486C99), magnetS = Color(0xFF94422F), belt = Color(0xFF131214),
            copper = Color(0xFF965E35), gold = Color(0xFF9B7B38), bronze = Color(0xFF856941), titanium = Color(0xFF7C726A), carbon = Color(0xFF17171A),
            ceramic = Color(0xFF998A74), panel = Color(0xFF0C0F12), panelLine = Color(0xFF2D343A), mist = Color(0xFFB9C5CD),
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

    // ---------------------------------------------------------------- parts that arrive with an upgrade step
    /** Air filter box of the naturally aspirated steps (Lv.1, 2). */
    const val AIRBOX_L = 1016f
    const val AIRBOX_R = 1150f
    const val AIRBOX_T = 20f
    const val AIRBOX_B = 72f
    val INTAKE_PATH_NA = floatArrayOf(1010f, 46f, 985f, 46f, 930f, 46f, 380f, 46f)
    /** Intercooler on the charge pipe and the blow-off valve beside it (Lv.3 and up). */
    const val IC_L = 1048f
    const val IC_R = 1112f
    const val IC_T = 82f
    const val IC_B = 152f
    val BOV = Offset(1022f, 24f)
    /** Dry-sump oil tank (Lv.5). */
    const val TANK_L = 1188f
    const val TANK_R = 1244f
    const val TANK_T = 150f
    const val TANK_B = 400f
    /** The rear motor of the dual-motor steps (EV Lv.4, 5). */
    val MOTOR2 = Offset(1140f, 452f)
    const val MOTOR2_R = 84f

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
