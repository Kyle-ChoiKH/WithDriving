package io.vroomi.character.render.engine

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import io.vroomi.character.engine.EngineGeometry
import io.vroomi.character.engine.EngineMechanics
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.engine.Stroke as CycleStroke
import io.vroomi.character.render.calibre.a
import io.vroomi.character.render.hash01
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import io.vroomi.character.render.theme.drawMusicNotes

/**
 * Moving parts, built once in design units (the draw loop only transforms them and picks the colour), and the
 * light on each: a fixed brush in the part's own coordinates, laid over its flat colour. Parts that turn carry a
 * radial light (it looks the same at every angle); parts that slide carry the light of a cylinder seen side-on.
 */
internal object EngineParts {
    private val hw = EL.BORE / 2 - 1.5f
    val piston: Path = Path().apply {
        moveTo(-hw, 5f); quadraticTo(0f, -3f, hw, 5f); lineTo(hw, EL.PISTON_H - 6)
        lineTo(hw - 6, EL.PISTON_H); lineTo(-hw + 6, EL.PISTON_H); lineTo(-hw, EL.PISTON_H - 6); close()
    }
    /** Lv.4: a forged slipper piston (short skirt, cut away beside the pin bosses). */
    val pistonForged: Path = Path().apply {
        moveTo(-hw, 5f); quadraticTo(0f, -3f, hw, 5f); lineTo(hw, 31f); lineTo(hw - 13f, 37f); lineTo(hw - 13f, 54f)
        lineTo(hw - 21f, 60f); lineTo(-hw + 21f, 60f); lineTo(-hw + 13f, 54f); lineTo(-hw + 13f, 37f); lineTo(-hw, 31f); close()
    }
    val rod: Path = Path().apply { moveTo(-7f, 0f); lineTo(7f, 0f); lineTo(12f, EL.ROD); lineTo(-12f, EL.ROD); close() }
    /** Crank web with its counterweight; the throw points up (−y) at 0°. */
    val throwWeb: Path = Path().apply {
        addRoundRect(RoundRect(-17f, -EL.R - 15f, 17f, 15f, CornerRadius(17f, 17f)))
        val r = EL.R + 14f
        arcTo(Rect(Offset.Zero, r), 38f, 104f, true)
        arcTo(Rect(Offset.Zero, 24f), 142f, -104f, false); close()
    }
    /** Cam lobe with its nose pointing down (+y) toward the valve. */
    val cam: Path = Path().apply {
        moveTo(-13f, 0f); arcTo(Rect(Offset.Zero, 13f), 180f, 180f, false)
        quadraticTo(10f, 16f, 0f, 13f + EL.LIFT); quadraticTo(-10f, 16f, -13f, 0f); close()
    }
    /** Valve with its face centre at (0, 0) when closed. */
    val valve: Path = Path().apply {
        moveTo(-2.6f, EL.STEM_TOP - EL.SEAT); lineTo(2.6f, EL.STEM_TOP - EL.SEAT); lineTo(2.6f, -9f)
        lineTo(EL.VALVE_HALF, 0f); lineTo(-EL.VALVE_HALF, 0f); lineTo(-2.6f, -9f); close()
    }
    val turbine: Path = Path().apply {
        for (i in 0 until 11) {
            val a = i * 2 * PI.toFloat() / 11
            val c = cos(a); val s = sin(a)
            moveTo(c * 7, s * 7)
            quadraticTo(cos(a + 0.35f) * 24, sin(a + 0.35f) * 24, cos(a + 0.5f) * 36, sin(a + 0.5f) * 36)
            lineTo(cos(a + 0.62f) * 36, sin(a + 0.62f) * 36)
            quadraticTo(cos(a + 0.45f) * 22, sin(a + 0.45f) * 22, cos(a + 0.12f) * 7, sin(a + 0.12f) * 7); close()
        }
    }
    private fun magnet(m: Int) = Path().apply {
        val a = m * PI.toFloat() / 4
        fun p(r: Float, da: Float) = Offset(cos(a + da) * r, sin(a + da) * r)
        val p1 = p(EL.ROTOR_R - 30, -0.22f); val p2 = p(EL.ROTOR_R - 8, -0.17f); val p3 = p(EL.ROTOR_R - 8, 0.17f); val p4 = p(EL.ROTOR_R - 30, 0.22f)
        moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); close()
    }
    val magnetsN: Path = Path().apply { for (m in 1 until 8 step 2) addPath(magnet(m)) }
    val magnetsS: Path = Path().apply { for (m in 0 until 8 step 2) addPath(magnet(m)) }
    val pinion = gearPath(Offset.Zero, EL.PINION_R, 13)
    val idler = gearPath(Offset.Zero, EL.IDLER_R, 33)
    val idlerPinion = gearPath(Offset.Zero, EL.IDLER_PINION_R, 11)
    val finalGear = gearPath(Offset.Zero, EL.FINAL_R, 40)
    val reverseIdler = gearPath(Offset.Zero, 16f, 10)

    /** Bore plus pent-roof of each cylinder (clip for the gas and the flame). */
    val chamber = Array(4) {
        val x = EL.CYL_X[it]; val h = EL.BORE / 2
        Path().apply {
            moveTo(x - h, EL.BLOCK_B); lineTo(x - h, EL.DECK + 1); lineTo(x - h + 6, EL.SEAT); lineTo(x + h - 6, EL.SEAT)
            lineTo(x + h, EL.DECK + 1); lineTo(x + h, EL.BLOCK_B); close()
        }
    }
    /** The flame front: an eight-point burst (soft, never a flash). */
    val flame: Path = Path().apply {
        for (i in 0 until 16) {
            val a = i * PI.toFloat() / 8 - PI.toFloat() / 2
            val r = if (i % 2 == 0) 30f else 15f
            if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
        }
        close()
    }

    val dropX = FloatArray(46) { 30f + hash01(it, 41) * (EL.W - 60f) }
    val dropY = FloatArray(46) { 30f + hash01(it, 42) * (EL.H - 60f) }
    val dropR = FloatArray(46) { 3f + 7f * hash01(it, 43) }

    val beltLen: FloatArray by lazy { EL.lengths(EL.BELT_POINTS) }
    val chainLen: FloatArray by lazy { EL.lengths(EL.CHAIN_POINTS) }
    val intakeLen = EL.lengths(EL.INTAKE_PATH)
    val intakeNaLen = EL.lengths(EL.INTAKE_PATH_NA)
    val exhaustLen = EL.lengths(EL.EXHAUST_PATH)
    val heaterLen = EL.lengths(EL.HEATER)
    val hv1Len = EL.lengths(EL.HV_BAT_INV)
    val hv2Len = EL.lengths(EL.HV_INV_MOTOR)
    val hv3Len = EL.lengths(EL.HV_COMP)

    private val relayY = EL.FUSE_T + 62f
    val arrowL: Path = Path().apply { val x = EL.FUSE_L + 32f; moveTo(x - 10, relayY); lineTo(x + 6, relayY - 9); lineTo(x + 6, relayY + 9); close() }
    val arrowR: Path = Path().apply { val x = EL.FUSE_L + 68f; moveTo(x + 10, relayY); lineTo(x - 6, relayY - 9); lineTo(x - 6, relayY + 9); close() }
    /** A check mark (unit size, centred). */
    val check: Path = Path().apply { moveTo(-0.5f, 0f); lineTo(-0.15f, 0.38f); lineTo(0.55f, -0.42f) }

    val line = Stroke(1.1f, join = StrokeJoin.Round)
    val lineFine = Stroke(0.8f)
    val lineBold = Stroke(2.2f, join = StrokeJoin.Round)
    /** The check mark's stroke in its unit space (scaled by the caller). */
    val stampCheck = Stroke(0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val sleeve = Stroke(9f)
    val jacketsX = floatArrayOf(EL.CYL_X[0] - EL.BORE / 2 - 22f, (EL.CYL_X[0] + EL.CYL_X[1]) / 2, (EL.CYL_X[1] + EL.CYL_X[2]) / 2, (EL.CYL_X[2] + EL.CYL_X[3]) / 2, EL.CYL_X[3] + EL.BORE / 2 + 22f)

    // ---- the light on each part (its own coordinates)
    private fun tube(l: Float, r: Float) = Brush.horizontalGradient(
        0f to Color.Black.a(0.32f), 0.28f to Color.White.a(0.38f), 0.62f to Color.Transparent, 1f to Color.Black.a(0.40f), startX = l, endX = r)
    private fun dome(c: Offset, r: Float) = Brush.radialGradient(
        0f to Color.White.a(0.34f), 0.55f to Color.Transparent, 1f to Color.Black.a(0.36f), center = c, radius = r)
    val shadePiston = tube(-hw, hw)
    val shadeRod = tube(-12f, 12f)
    val shadeValve = tube(-EL.VALVE_HALF, EL.VALVE_HALF)
    val shadeWeb = dome(Offset(-6f, -10f), EL.R + 36f)
    val shadeBigEnd = dome(Offset(-6f, EL.ROD - 7f), 31f)
    val shadeCam = dome(Offset(-4f, -4f), 30f)
    val shadeTurbine = Brush.radialGradient(0f to Color.White.a(0.5f), 1f to Color.Black.a(0.3f), center = Offset.Zero, radius = 38f)
    val shadeRotor = dome(Offset(-EL.ROTOR_R * 0.3f, -EL.ROTOR_R * 0.35f), EL.ROTOR_R * 1.5f)
    val shadePinion = dome(Offset(-8f, -9f), EL.PINION_R * 1.6f)
    val shadeIdler = dome(Offset(-20f, -23f), EL.IDLER_R * 1.6f)
    val shadeFinal = dome(Offset(-24f, -28f), EL.FINAL_R * 1.6f)
}

// ---------------------------------------------------------------- the whole scene

/** The wall colour of each step's room (null: the plain workshop). */
private val STUDIO = arrayOf<Color?>(null, Color(0xFF27425E), Color(0xFF5E4127), Color(0xFF1F5A52), Color(0xFF63232D))

internal fun EngineRuntime.draw(ds: DrawScope, tm: TextMeasurer, debugMs: Float? = null): Unit = with(ds) {
    val w = size.width; val h = size.height
    val m = mech
    val texts = texts(h, tm, this)
    ink.set(EngineInk.DAY, EngineInk.NIGHT, m.night)
    val P = ink
    // CORNER: the whole scene shrinks into a small card at the top right (the rest is the dark room).
    val c = ((1f - scale.value) / 0.7f).coerceIn(0f, 1f)
    val sw = w + (w * 0.30f - w) * c; val sh = h + (h * 0.30f - h) * c
    val sl = (w - sw - w * 0.02f * c); val st = h * 0.03f * c
    if (c > 0.01f) {
        drawRect(EngineInk.NIGHT.bgBottom)
        clip.reset()
        clip.addRoundRect(RoundRect(sl, st, sl + sw, st + sh, CornerRadius(sh * 0.06f, sh * 0.06f)))
        clipPath(clip) { withTransform({ translate(sl, st); scale(sw / w, sw / w, Offset.Zero) }) { drawScene(this@draw, texts, w, h, P, c) } }
    } else drawScene(this@draw, texts, w, h, P, 0f)
    debugMs?.let { val l = texts.text.get("draw %.1f ms".format(it), h * 0.03f, Color(0xFF9FE8B9)); drawText(l, topLeft = Offset(16f, h - l.size.height - 12f)) }
}

/**
 * The scene at full canvas size [w] × [h]: the studio, the machine and the telemetry display. [corner] is the
 * CORNER shrink (0..1). While the car moves, every small text fades out (driving text must be at least 24 sp);
 * the machine, the graphs as shapes and the display's readout stay.
 */
private fun DrawScope.drawScene(rt: EngineRuntime, texts: EngineTexts, w: Float, h: Float, P: EngineInk, corner: Float) {
    val m = rt.mech
    val small = 1f - rt.drive
    val o = rt.lastOptions
    val L = rt.layout
    L.update(w, h, o.sizeScale, texts, m.powertrain, rt.model, density)
    // The studio (cached): day and night cross-fade while the light changes.
    val n = m.night
    if (n < 0.999f) drawImage(rt.sheetLayer(w, h, false, this))
    if (n > 0.001f) drawImage(rt.sheetLayer(w, h, true, this), alpha = n)
    // Each step stands in its own room: a grey workshop, a blue assembly hall, a warm dyno cell, a teal test lab,
    // a crimson pit garage. The room's light washes the wall behind the machine (stronger at the top) and lays a
    // stripe of the room's colour along the floor; the drawing's inks stay as they are.
    STUDIO.getOrNull(rt.model)?.let { room ->
        drawRect(Brush.verticalGradient(0f to room.copy(alpha = 0.62f), 0.7f to room.copy(alpha = 0.30f), 1f to room.copy(alpha = 0.12f), startY = 0f, endY = h))
        drawRect(androidx.compose.ui.graphics.lerp(room, Color.White, 0.35f).copy(alpha = 0.55f), Offset(0f, h * 0.955f), Size(w, h * 0.012f))
    }

    val zf = FRONT_ZOOM + (1f - FRONT_ZOOM) * rt.zoom.value.coerceIn(0f, 1f)
    val front = zf > 1.25f
    val k = L.k
    val focus = if (m.powertrain == Powertrain.ICE) Offset(EL.CYL_X[0], 360f) else EL.MOTOR
    val fx = EL.W / 2 + (focus.x - EL.W / 2) * (zf - 1f) / (FRONT_ZOOM - 1f)
    val fy = EL.H / 2 + (focus.y - EL.H / 2) * (zf - 1f) / (FRONT_ZOOM - 1f)
    val rock = if (o.reducedMotion) 0f else m.rock()
    val layerK = k * (if (front) FRONT_ZOOM else 1f)
    val dayLayer = if (n < 0.999f) rt.layers(layerK, m.powertrain, false, this, texts.text) else null
    val nightLayer = if (n > 0.001f) rt.layers(layerK, m.powertrain, true, this, texts.text) else null

    clipRect(L.fieldL, L.fieldT, L.fieldR, L.fieldB) {
        withTransform({
            translate(L.cx, L.cy)
            scale(k * zf, k * zf, Offset.Zero)
            translate(-fx, -fy)
        }) {
            withTransform({ translate(rock * 6f, 0f); rotate(rock * 0.5f, Offset(EL.W / 2, EL.CRANK_Y)) }) {
                withTransform({ scale(1f / (dayLayer ?: nightLayer)!!.k, 1f / (dayLayer ?: nightLayer)!!.k, Offset.Zero) }) {
                    dayLayer?.let { drawImage(it.bmp) }
                    nightLayer?.let { drawImage(it.bmp, alpha = n) }
                }
                if (o.intro < 1f) {
                    // Entrance accent: the moving parts settle into their seats along straight assembly lines, at
                    // a constant rate (no easing), from 10 dp above.
                    val e = ((o.intro - 0.15f) / 0.75f).coerceIn(0f, 1f)
                    val lift = (1f - e) * 10f * density / (k * zf)
                    withTransform({ translate(0f, -lift) }) {
                        if (m.powertrain == Powertrain.ICE) drawIce(rt, P) else drawEv(rt, P)
                        drawCommon(rt, P)
                    }
                } else {
                    if (m.powertrain == Powertrain.ICE) drawIce(rt, P) else drawEv(rt, P)
                    drawCommon(rt, P)
                }
            }
            if (small > 0.01f) drawDrawingText(rt, texts, P, small)
            drawGlass(m, P)
            // Music in the car: warm notes rise from the engine bay like heat shimmer.
            drawMusicNotes(EL.W * 0.5f, 120f, Color(0xFFFFE6B0), rt.listening, rt.noteT, size = 44f, rise = 110f, glow = Color(0xFFFFA94D))
            val fa = (1f - rt.zoom.value).coerceIn(0f, 1f)
            if (fa > 0.02f) drawFrontSection(m.powertrain, fa, small, texts, P)
        }
    }

    // The display stays while driving (its readout is sized to the driving minimum), except in the tiny CORNER card.
    val va = 1f - corner * rt.drive
    drawCard(L, P, va)
    drawTelemetry(rt, texts, L, P, small, va)
    val sm = if (m.visualRps != 0.0) {
        val rpm = if (m.powertrain == Powertrain.ICE) m.engine.rpm else abs(m.motor.rpm)
        texts.slowMotion(EngineGeometry.slowMotionFactor(rpm, abs(m.visualRps)))
    } else null
    if (small > 0.01f) sm?.let { drawText(it, color = P.textSoft, topLeft = Offset(L.fieldL + h * 0.02f, L.fieldB - it.size.height - h * 0.012f), alpha = small) }

    // The door opened while parked switches the work lamp on: a warm pool on the scene.
    if (m.lamp > 0.01f) drawRect(rt.lampPool(w, h), alpha = (0.8f * m.lamp).coerceAtMost(1f))
    if (m.freeze > 0.01f) drawRect(Color.Black.a(0.30f * m.freeze))
    // SIDE ↔ FRONT: the view changes behind a short linear dip to the room's colour.
    if (rt.fade < 1f) drawRect(P.bgBottom.a(1f - abs(rt.fade * 2f - 1f)))
}

/** FRONT view: everything but cylinder 1 (or the motor) falls back into the dark; a frame and the section's name. */
private fun DrawScope.drawFrontSection(p: Powertrain, a: Float, small: Float, texts: EngineTexts, P: EngineInk) {
    val l: Float; val r: Float; val t = 70f; val b = 616f
    if (p == Powertrain.ICE) { l = EL.CYL_X[0] - 96f; r = EL.CYL_X[0] + 96f } else { l = EL.MOTOR.x - EL.HOUSING_R - 12f; r = EL.MOTOR.x + EL.HOUSING_R + 12f }
    val wash = P.bgBottom.a(0.82f * a)
    drawRect(wash, Offset(-EL.W, -EL.H), Size(l + EL.W, EL.H * 3))
    drawRect(wash, Offset(r, -EL.H), Size(EL.W * 2, EL.H * 3))
    drawRect(wash, Offset(l, -EL.H), Size(r - l, t + EL.H))
    drawRect(wash, Offset(l, b), Size(r - l, EL.H * 2))
    drawRect(P.textSoft.a(a), Offset(l, t), Size(r - l, b - t), style = EngineParts.lineBold)
    val label = texts.section(if (p == Powertrain.ICE) "단면 A–A · 1번 실린더" else "단면 B–B · 모터")
    drawText(label, color = P.text, topLeft = Offset(r + 14f, 520f), alpha = a * small)
}

/** A part: its flat colour, the light on it, a thin outline. */
private fun DrawScope.part(path: Path, fill: Color, light: Brush, edge: Color) {
    drawPath(path, fill); drawPath(path, light); drawPath(path, edge, style = EngineParts.line)
}

// ---------------------------------------------------------------- VE-4

private fun DrawScope.drawIce(rt: EngineRuntime, P: EngineInk) {
    val m = rt.mech
    val e = m.engine
    val p = EngineParts
    val model = rt.model
    val dohc = IceStep.dohc(model); val turbo = IceStep.turbo(model); val gdi = IceStep.directInjection(model); val race = IceStep.race(model)

    drawFan(m, P)
    drawHeaterFlow(rt, P)

    // Accessory belt ribs and pulley spokes (driven by the crank)
    val beltSpeed = m.crankRevs * 2 * PI * EL.P_CRANK.r
    ticks(EL.BELT_POINTS, p.beltLen, beltSpeed.toFloat(), 9f, 3f, P.lineSoft, 5f, rt)
    for (bi in EL.BELT.indices) {
        val pl = EL.BELT[bi]
        val deg = (m.crankRevs * 360.0 * EL.P_CRANK.r / pl.r % 360.0).toFloat()
        val isAc = pl === EL.P_AC
        rotate(if (isAc) 0f else deg, pl.c) {
            for (s in 0 until 3) rotate(s * 120f, pl.c) { drawLine(P.line, pl.c, Offset(pl.x, pl.y - pl.r * 0.68f), strokeWidth = 2.2f, cap = StrokeCap.Butt) }
        }
        if (isAc) {
            // The pulley always turns with the belt; the clutch plate (centre) only when engaged.
            rotate(deg, pl.c) { drawLine(P.line, Offset(pl.x, pl.y - pl.r * 0.86f), Offset(pl.x, pl.y - pl.r * 0.6f), strokeWidth = 2f) }
            drawCircle(lerp(P.metalDark, P.intake, m.acPlate), pl.r * 0.45f, pl.c)
            drawCircle(P.line, pl.r * 0.45f, pl.c, style = p.lineFine)
            rotate((m.compressorAngle * 180 / PI).toFloat(), pl.c) {
                drawLine(P.line, Offset(pl.x - pl.r * 0.4f, pl.y), Offset(pl.x + pl.r * 0.4f, pl.y), strokeWidth = 2f)
            }
        }
    }
    if (m.night > 0.05f) drawCircle(P.electric.a(0.7f * m.night), 8f, EL.P_ALT.c)

    // Timing chain (cam sprocket has twice the teeth: half speed); Lv.2: the phaser's vanes turn with it
    ticks(EL.CHAIN_POINTS, p.chainLen, (m.crankRevs * 2 * PI * EL.CHAIN_CRANK_R).toFloat(), 6f, 3f, P.line, 3f, rt)
    val camDeg0 = (m.crankRevs * 180.0 % 360.0).toFloat()
    rotate(camDeg0, EL.CHAIN_CAM) {
        if (dohc) for (v in 0 until 4) rotate(v * 90f, EL.CHAIN_CAM) { drawLine(P.line.a(0.75f), Offset(EL.CHAIN_CAM.x, EL.CHAIN_CAM.y - 6.5f), Offset(EL.CHAIN_CAM.x, EL.CHAIN_CAM.y - 14f), strokeWidth = 2f) }
        drawLine(P.line, Offset(EL.CHAIN_CAM.x, EL.CHAIN_CAM.y - 17f), Offset(EL.CHAIN_CAM.x, EL.CHAIN_CAM.y - EL.CHAIN_CAM_R + 4), strokeWidth = 2f)
    }

    // Cylinders: gas, flame, crank, rod, piston, valves, cams
    val clean = m.cleanBurn
    val rodColor = if (race) P.titanium else P.metalLight
    val pistonPath = if (gdi) p.pistonForged else p.piston
    for (i in 0 until 4) {
        val x = EL.CYL_X[i]
        val phi = EngineGeometry.phase(m.cycleDeg, i)
        val th = EngineGeometry.crankDeg(m.cycleDeg, i) * PI / 180
        val pinY = (EL.CRANK_Y - EngineGeometry.pinHeightMm(th) * EL.S).toFloat()
        val crownY = pinY - EL.CROWN_ABOVE_PIN
        val half = EL.BORE / 2
        val stroke = EngineGeometry.stroke(phi)
        val gas = when {
            !e.running -> Color.Transparent
            stroke == CycleStroke.INTAKE -> P.intake.a(if (e.fuelCut) 0.22f else 0.40f)
            stroke == CycleStroke.COMPRESSION -> P.intake.a(if (e.fuelCut) 0.30f else 0.62f)
            stroke == CycleStroke.EXHAUST -> if (e.fuelCut) P.intake.a(0.22f) else P.gasHot.a(0.42f)
            else -> if (e.fuelCut) P.intake.a(0.26f) else P.fire.a(0.30f)
        }
        clipPath(p.chamber[i]) { clipRect(x - half, EL.DECK - 13f, x + half, crownY + 2f) {
            drawRect(gas, Offset(x - half, EL.DECK - 13f), Size(EL.BORE, crownY - EL.DECK + 15f))
            val g = m.glow(i)
            if (g > 0.01f) translate(x, EL.DECK + 8f) {
                drawPath(p.flame, (if (clean[i]) P.clean else P.fire).a(g.coerceAtMost(1f)))
                drawPath(p.flame, P.white.a(0.55f * g.coerceAtMost(1f)), style = p.lineFine)
            }
        } }
        // Crank throw
        val thDeg = (th * 180 / PI).toFloat()
        rotate(thDeg, Offset(x, EL.CRANK_Y)) { translate(x, EL.CRANK_Y) { part(p.throwWeb, P.metal, p.shadeWeb, P.line) } }
        val cpx = x + (EL.R * sin(th)).toFloat(); val cpy = EL.CRANK_Y - (EL.R * cos(th)).toFloat()
        // Rod from the piston pin to the crank pin (true length); titanium at Lv.5
        val rodDeg = (atan2(-(cpx - x).toDouble(), (cpy - pinY).toDouble()) * 180 / PI).toFloat()
        rotate(rodDeg, Offset(x, pinY)) { translate(x, pinY) {
            part(p.rod, rodColor, p.shadeRod, P.line)
            drawCircle(rodColor, 22f, Offset(0f, EL.ROD)); drawCircle(p.shadeBigEnd, 22f, Offset(0f, EL.ROD)); drawCircle(P.line, 22f, Offset(0f, EL.ROD), style = p.line)
        } }
        drawCircle(P.metal, 13f, Offset(cpx, cpy)); drawCircle(P.line, 13f, Offset(cpx, cpy), style = p.line)
        drawCircle(Color.White.a(0.3f), 4f, Offset(cpx - 4f, cpy - 4.5f))
        // Piston with rings and pin; the forged piston (Lv.4) wears a dark skirt coating
        translate(x, crownY) {
            part(pistonPath, P.metalLight, p.shadePiston, P.line)
            for (rg in 0 until 3) drawLine(P.line.a(0.8f), Offset(-half + 2, 11f + rg * 6f), Offset(half - 2, 11f + rg * 6f), strokeWidth = 1f)
            if (gdi) drawRect(P.lineSoft.a(0.85f), Offset(-half + 16f, 41f), Size(EL.BORE - 32f, 13f))
            drawCircle(P.metal, 9f, Offset(0f, EL.CROWN_ABOVE_PIN)); drawCircle(P.line, 9f, Offset(0f, EL.CROWN_ABOVE_PIN), style = p.line)
            drawCircle(Color.White.a(0.35f), 2.8f, Offset(-2.6f, EL.CROWN_ABOVE_PIN - 3f))
        }
        // Valves, springs, and the cams (two shafts) or the single cam with rockers (Lv.1)
        for (side in 0..1) {
            val sx = if (side == 0) -1f else 1f
            val vx = x + sx * EL.VALVE_DX
            val open = if (side == 0) EngineGeometry.INTAKE_OPEN else EngineGeometry.EXHAUST_OPEN
            val close = if (side == 0) EngineGeometry.INTAKE_CLOSE else EngineGeometry.EXHAUST_CLOSE
            val lf = (EngineGeometry.lift(phi, open, close) * EL.LIFT).toFloat()
            if (lf > 1f) drawRect((if (side == 0) P.intake else P.gasHot).a(0.75f * lf / EL.LIFT), Offset(vx - EL.VALVE_HALF, EL.SEAT), Size(EL.VALVE_HALF * 2, 10f))
            translate(vx, EL.SEAT + lf) { part(p.valve, P.metalLight, p.shadeValve, P.line) }
            val top = EL.STEM_TOP + 9f + lf; val bottom = EL.DECK - 22f
            var yy = top; val n = 7; val step = (bottom - top) / n
            for (s in 0 until n) {
                val x0 = vx + if (s % 2 == 0) -9f else 9f; val x1 = vx + if (s % 2 == 0) 9f else -9f
                drawLine(P.metal, Offset(x0, yy), Offset(x1, yy + step), strokeWidth = 1.5f); yy += step
            }
            drawRect(P.metal, Offset(vx - 10, EL.STEM_TOP + lf - 1f), Size(20f, 9f))
            drawRect(P.line, Offset(vx - 10, EL.STEM_TOP + lf - 1f), Size(20f, 9f), style = p.line)
            val camDeg = (EngineGeometry.camRad(phi, open, close) * 180 / PI).toFloat()
            if (dohc) {
                rotate(camDeg, Offset(vx, EL.CAM_Y)) { translate(vx, EL.CAM_Y) { part(p.cam, P.metal, p.shadeCam, P.line) } }
                drawCircle(P.line, 3f, Offset(vx, EL.CAM_Y))
            } else {
                // One shaft over the cylinder centre: the lobe lifts the rocker's inner end, its outer end opens the valve.
                rotate(camDeg + 180f, Offset(x, EL.CAM_Y)) { translate(x, EL.CAM_Y) { part(p.cam, P.metal, p.shadeCam, P.line) } }
                val inner = Offset(x + sx * 5f, EL.CAM_Y - 15f - lf * 0.45f); val outer = Offset(vx, EL.STEM_TOP + lf - 2f)
                drawLine(P.line, inner, outer, strokeWidth = 7.4f, cap = StrokeCap.Round)
                drawLine(P.metalLight, inner, outer, strokeWidth = 5.2f, cap = StrokeCap.Round)
            }
        }
        if (!dohc) drawCircle(P.line, 3f, Offset(x, EL.CAM_Y))
        // Spark (only at ignition): a small white point with a violet halo
        if (e.running && !e.fuelCut && (phi > 712.0 || phi < 6.0)) { drawCircle(P.electricLight.a(0.6f), 7f, Offset(x, EL.DECK - 6f)); drawCircle(P.white, 3.2f, Offset(x, EL.DECK - 6f)) }
        // Fuel: sprayed into the port during intake, or (Lv.4) straight into the chamber late in the compression
        if (e.running && !e.fuelCut) {
            if (gdi) {
                if (phi in 596.0..676.0) {
                    val s = ((phi - 596.0) / 80.0).toFloat()
                    for (ray in -1..1) for (d in 1..4) {
                        val u = d / 4f * (0.4f + 0.6f * s)
                        drawCircle(P.white.a(0.8f * (1f - s * 0.7f)), 1.5f, Offset(x - half + 14f + u * 46f, EL.DECK - 9f + u * (26f + ray * 13f)))
                    }
                }
            } else if (phi in 380.0..470.0) {
                val s = ((phi - 380.0) / 90.0).toFloat()
                for (d in 0 until 5) drawCircle(P.white.a(0.75f * (1 - s)), 1.6f, Offset(x - half + 16 + d * 4f + s * 10f, EL.DECK - 8f + d * 2.2f + s * 8f))
            }
        }
    }

    // Intake air and exhaust gas: flow arrows along their paths
    val flow = m.flowPhase.toFloat()
    val nIn = (6 + 10 * e.load).toInt()
    val inPath = if (turbo) EL.INTAKE_PATH else EL.INTAKE_PATH_NA
    val inLen = if (turbo) p.intakeLen else p.intakeNaLen
    for (q in 0 until nIn) chevron(inPath, inLen, (q.toFloat() / nIn + flow * 0.6f) * inLen.last(), P.intake, P, rt)
    if (e.running) {
        val col = if (e.fuelCut) P.intake else P.gasHot
        for (q in 0 until 9) chevron(EL.EXHAUST_PATH, p.exhaustLen, (q / 9f + flow * 0.5f) * p.exhaustLen.last(), col, P, rt)
    }
    // Throttle: one plate in the throttle body, or (Lv.5) a plate in every trumpet
    if (race) {
        for (i in 0 until 4) {
            val c = Offset(EL.CYL_X[i] - EL.VALVE_DX, 64f)
            rotate(-90f + m.throttle * 75f, c) { drawLine(P.line, Offset(c.x - 9f, c.y), Offset(c.x + 9f, c.y), strokeWidth = 2.6f) }
        }
    } else {
        rotate(-90f + m.throttle * 75f, EL.THROTTLE) { drawLine(P.metalLight, Offset(EL.THROTTLE.x - 13, EL.THROTTLE.y), Offset(EL.THROTTLE.x + 13, EL.THROTTLE.y), strokeWidth = 2.8f) }
    }
    // Lv.3: turbo wheel and wastegate flap
    if (turbo) {
        rotate((m.turboAngle * 180 / PI % 360).toFloat(), EL.TURBO) { translate(EL.TURBO.x, EL.TURBO.y) {
            drawPath(p.turbine, P.metalLight); drawPath(p.turbine, p.shadeTurbine); drawPath(p.turbine, P.line, style = p.lineFine)
        } }
        drawCircle(P.metalDark, 6f, EL.TURBO); drawCircle(P.line, 6f, EL.TURBO, style = p.lineFine)
        val wg = Offset(1100f, 268f)
        rotate(-50f * m.wastegate, wg) { drawLine(P.metalLight, wg, Offset(wg.x + 18, wg.y), strokeWidth = 3f, cap = StrokeCap.Butt) }
        drawCircle(P.line, 3f, wg)
    }

    // Cam cover band: ECO teal, SPORT amber, NORMAL plain; cruise adds a second band
    val acc = if (m.modeAccent < 0f) lerp(P.metalDark, P.eco, -m.modeAccent) else lerp(P.metalDark, P.sport, m.modeAccent)
    drawRect(acc, Offset(EL.BLOCK_L + 30, EL.HEAD_TOP - 6), Size(EL.BLOCK_R - EL.BLOCK_L - 60, 4f))
    if (m.cruise > 0.01f) drawRect(P.eco.a(m.cruise), Offset(EL.BLOCK_L + 30, EL.HEAD_TOP - 1), Size(EL.BLOCK_R - EL.BLOCK_L - 60, 2f))

    drawGearboxInset(rt, P)
}

/** Moving marks along a closed loop (belt ribs, chain links). */
private fun DrawScope.ticks(pts: FloatArray, len: FloatArray, offset: Float, spacing: Float, mark: Float, color: Color, width: Float, rt: EngineRuntime) {
    val total = len.last()
    val n = (total / spacing).toInt()
    val off = ((offset % spacing) + spacing) % spacing
    for (i in 0 until n) {
        val d = i * spacing + off
        EL.pointAt(pts, len, d, rt.pt); EL.pointAt(pts, len, d + mark, rt.pt2)
        drawLine(color, Offset(rt.pt[0], rt.pt[1]), Offset(rt.pt2[0], rt.pt2[1]), strokeWidth = width, cap = StrokeCap.Butt)
    }
}

/** A flow arrow (an open chevron) at distance [d] along an open polyline, pointing downstream: its colour over a dark edge. */
private fun DrawScope.chevron(pts: FloatArray, len: FloatArray, d: Float, color: Color, P: EngineInk, rt: EngineRuntime) {
    val total = len.last()
    val s = ((d % total) + total) % total
    if (s + 4f > total) return
    EL.pointAt(pts, len, s, rt.pt); EL.pointAt(pts, len, s + 4f, rt.pt2)
    val dx = rt.pt2[0] - rt.pt[0]; val dy = rt.pt2[1] - rt.pt[1]
    val l = hypot(dx, dy).coerceAtLeast(1e-3f)
    val ux = dx / l * 6f; val uy = dy / l * 6f
    val tip = Offset(rt.pt2[0], rt.pt2[1])
    val a = Offset(tip.x - ux - uy * 0.7f, tip.y - uy + ux * 0.7f); val b = Offset(tip.x - ux + uy * 0.7f, tip.y - uy - ux * 0.7f)
    drawLine(P.line.a(0.8f), tip, a, strokeWidth = 3.6f, cap = StrokeCap.Round)
    drawLine(P.line.a(0.8f), tip, b, strokeWidth = 3.6f, cap = StrokeCap.Round)
    drawLine(color, tip, a, strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(color, tip, b, strokeWidth = 2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawFan(m: EngineMechanics, P: EngineInk) {
    // Edge-on blades: their tips move up and down as the fan turns (uniform rotation)
    val a0 = m.fanAngle
    for (b in 0 until 5) {
        val a = a0 + b * 2 * PI / 5
        val y = EL.FAN.y + (EL.FAN_R * 0.88f * cos(a)).toFloat()
        val wdt = (4f + 10f * abs(sin(a)).toFloat())
        drawRect(P.metal, Offset(EL.FAN.x - wdt / 2, y - 14f), Size(wdt, 28f))
        drawRect(P.line, Offset(EL.FAN.x - wdt / 2, y - 14f), Size(wdt, 28f), style = EngineParts.lineFine)
    }
    drawCircle(P.metalDark, 6f, EL.FAN)
    // Air through the radiator core: short pale blue dashes, proportional to the fan
    if (m.fanRps > 0.02) for (s in 0 until 6) {
        val y = EL.RAD_T + 40 + s * 80f
        val x = EL.RAD_L - 10 + ((m.fanAngle * 9 + s * 13) % 60).toFloat()
        drawLine(P.intake, Offset(x, y), Offset(x + 14, y), strokeWidth = 2.5f, cap = StrokeCap.Butt)
    }
}

private fun DrawScope.drawHeaterFlow(rt: EngineRuntime, P: EngineInk) {
    val m = rt.mech
    val p = EngineParts
    val running = if (m.powertrain == Powertrain.ICE) m.engine.running else true
    if (!running) return
    val heat = m.heater
    val col = lerp(P.coolant, P.sport, heat)
    if (m.powertrain == Powertrain.ICE) {
        for (q in 0 until 6) chevron(EL.HEATER, p.heaterLen, (q / 6f + m.coolantPhase.toFloat() * 0.4f) * p.heaterLen.last(), col, P, rt)
        // Coolant in the block jackets
        val ph = (m.coolantPhase * 40 % 30).toFloat()
        for (x in p.jacketsX) {
            var y = EL.DECK + 14 + ph
            while (y < EL.DECK + 146) { drawLine(P.white.a(0.55f), Offset(x, y), Offset(x, y + 8), strokeWidth = 2f); y += 30f }
        }
    }
}

private fun DrawScope.drawGearboxInset(rt: EngineRuntime, P: EngineInk) {
    val m = rt.mech
    val e = m.engine
    val sel = when (e.gearLabel) { "P" -> 0; "N" -> 0; "R" -> 9; else -> e.gearbox.gear }
    val shift = m.shiftAnim
    for (i in 0 until 9) {
        val x = EL.GB_L + 26 + i * 27f
        val idx = i + 1
        val on = when {
            idx == sel -> 1f - shift
            idx == m.shiftFrom && sel in 1..8 && shift > 0f -> shift
            else -> 0f
        }
        if (on > 0.01f) {
            // Clutch pack: plates pressed together when engaged (oil amber plates)
            val col = if (idx == 9) P.eco else P.oil
            for (pl in 0 until 5) {
                val gap = 2f + 4f * (1f - on)
                drawRect(col.a(0.4f + 0.6f * on), Offset(x + 2f, EL.GB_T + 30 + pl * (4f + gap)), Size(17f, 3f))
            }
        }
    }
    // Output shaft (turns with the wheels; through the idler in R) and the odometer drum
    val y = EL.GB_B - 40f
    drawRect(P.metalDark, Offset(EL.GB_L + 18, y - 4), Size(EL.GB_R - 130 - EL.GB_L - 18, 8f))
    drawLine(Color.White.a(0.28f), Offset(EL.GB_L + 18, y - 2), Offset(EL.GB_R - 130, y - 2), strokeWidth = 1f)
    val off = (m.crankRevs * 40 % 14).toFloat()
    var x = EL.GB_L + 22 + off
    while (x < EL.GB_R - 132) { drawLine(P.line, Offset(x, y - 4), Offset(x, y + 4), strokeWidth = 1.2f); x += 14f }
    val idler = Offset(EL.GB_R - 40, y)
    rotate((m.reverseIdler * 180 / PI % 360).toFloat(), idler) { translate(idler.x, idler.y) {
        drawPath(EngineParts.reverseIdler, if (e.gearLabel == "R") P.eco else P.metal)
        drawPath(EngineParts.reverseIdler, P.line, style = EngineParts.lineFine)
    } }
    val drumL = EL.GB_R - 120f; val drumT = EL.GB_B - 54f
    clipRect(drumL, drumT, drumL + 52f, drumT + 30f) {
        val frac = m.odometer - kotlin.math.floor(m.odometer)
        for (k in -1..3) {
            val yy = drumT + 15f + (k - frac) * 10f
            drawLine(P.textSoft, Offset(drumL + 8, yy), Offset(drumL + 44, yy), strokeWidth = 1f)
        }
    }
}

// ---------------------------------------------------------------- VE-M

/** The rotor in its own coordinates: laminated core, eight magnets, lightening holes, the shaft. */
private fun DrawScope.rotor(P: EngineInk, carbonSleeve: Boolean) {
    val p = EngineParts
    drawCircle(P.metalLight, EL.ROTOR_R, Offset.Zero)
    drawCircle(p.shadeRotor, EL.ROTOR_R, Offset.Zero)
    drawCircle(P.line, EL.ROTOR_R, Offset.Zero, style = p.line)
    drawPath(p.magnetsN, P.magnetN); drawPath(p.magnetsS, P.magnetS)
    drawPath(p.magnetsN, P.line, style = p.lineFine); drawPath(p.magnetsS, P.line, style = p.lineFine)
    for (h in 0 until 6) { val a = h * PI / 3; val o = Offset((cos(a) * 62).toFloat(), (sin(a) * 62).toFloat()); drawCircle(P.cavity, 9f, o); drawCircle(P.line, 9f, o, style = p.lineFine) }
    if (carbonSleeve) {
        // Lv.5: the wound carbon sleeve that holds the magnets at speed
        drawCircle(P.carbon, EL.ROTOR_R - 4.5f, Offset.Zero, style = p.sleeve)
        for (i in 0 until 40) {
            val a = i * PI.toFloat() / 20f
            drawLine(Color.White.a(0.13f), Offset(cos(a) * (EL.ROTOR_R - 8.5f), sin(a) * (EL.ROTOR_R - 8.5f)), Offset(cos(a + 0.09f) * (EL.ROTOR_R - 0.5f), sin(a + 0.09f) * (EL.ROTOR_R - 0.5f)), strokeWidth = 1.4f)
        }
        drawCircle(P.line, EL.ROTOR_R, Offset.Zero, style = p.line)
    }
    drawCircle(P.metal, EL.SHAFT_R, Offset.Zero); drawCircle(P.line, EL.SHAFT_R, Offset.Zero, style = p.line)
    drawLine(P.line, Offset.Zero, Offset(0f, -EL.SHAFT_R + 4), strokeWidth = 2.5f)
}

private fun DrawScope.drawEv(rt: EngineRuntime, P: EngineInk) {
    val m = rt.mech
    val mo = m.motor
    val p = EngineParts
    val model = rt.model
    val hairpin = EvStep.hairpin(model); val dual = EvStep.dual(model); val top = EvStep.carbonRotor(model)
    drawFan(m, P)
    drawHeaterFlow(rt, P)

    // Stator windings: copper that brightens with each phase's current (round wire, or hairpin bars from Lv.3)
    val amp = (min(1.0, abs(mo.powerKw) / 50.0) * 0.8 + if (abs(mo.rpm) > 5) 0.2 else 0.0).toFloat()
    for (s in 0 until EngineGeometry.STATOR_SLOTS) {
        val a = s / 48.0 * 2 * PI
        val ph = (s / 2) % 3
        val cur = abs(sin(m.electricalAngle + ph * 2 * PI / 3 - a * EngineGeometry.MOTOR_POLE_PAIRS)).toFloat()
        val col = lerp(P.copper, P.white, 0.45f * cur * amp).a(0.55f + 0.45f * cur * amp)
        if (hairpin) {
            rotate((s / 48f * 360f), EL.MOTOR) {
                for (b in 0 until 4) drawRect(col, Offset(EL.MOTOR.x + EL.STATOR_IN + 9f + b * 11f, EL.MOTOR.y - 4.2f), Size(9.4f, 8.4f))
            }
        } else {
            val c = Offset(EL.MOTOR.x + (cos(a) * (EL.STATOR_IN + 30)).toFloat(), EL.MOTOR.y + (sin(a) * (EL.STATOR_IN + 30)).toFloat())
            drawCircle(col, 7f, c)
        }
    }
    // Rotor with magnets (N blue, S red), shaft
    val deg = (m.rotorAngle * 180 / PI % 360).toFloat()
    rotate(deg, EL.MOTOR) { translate(EL.MOTOR.x, EL.MOTOR.y) { rotor(P, top) } }
    // Lv.4: the rear motor turns with the front one
    if (dual) {
        val s = (EL.MOTOR2_R - 35f) / EL.ROTOR_R
        withTransform({ translate(EL.MOTOR2.x, EL.MOTOR2.y); rotate(deg, Offset.Zero); scale(s, s, Offset.Zero) }) { rotor(P, top) }
    }

    // Reduction gears: pinion on the motor shaft → idler (66/26) → final (80/22): about 9.2 : 1
    val pin = deg
    val idl = -deg * EL.PINION_R / EL.IDLER_R
    val fin = deg * (EL.PINION_R / EL.IDLER_R) * (EL.IDLER_PINION_R / EL.FINAL_R)
    gear(p.finalGear, EL.FINAL, fin, p.shadeFinal, P)
    gear(p.idler, EL.IDLER, idl, p.shadeIdler, P)
    gear(p.idlerPinion, EL.IDLER, idl, p.shadePinion, P)
    gear(p.pinion, EL.PINION, pin, p.shadePinion, P)

    // Inverter phasors (amplitude follows |P|)
    val len = 10f + 40f * min(1f, abs(mo.powerKw).toFloat() / 50f)
    for (k in 0 until 3) {
        val a = m.electricalAngle + k * 2 * PI / 3
        val col = when (k) { 0 -> P.electric; 1 -> P.electricLight; else -> P.electricDark }
        drawLine(col, EL.INV_DIAL, Offset(EL.INV_DIAL.x + (cos(a) * len).toFloat(), EL.INV_DIAL.y + (sin(a) * len).toFloat()), strokeWidth = 4f, cap = StrokeCap.Butt)
    }
    drawCircle(P.text, 4f, EL.INV_DIAL)

    // Power flow: battery → inverter → motor (drive) or back (regeneration, teal)
    val active = abs(mo.powerKw) > 0.5
    if (active) {
        val col = if (mo.regen) P.eco else P.white
        val f = m.flowPhase.toFloat() * 120f
        pulses(EL.HV_BAT_INV, p.hv1Len, f, col, 3f, rt)
        pulses(EL.HV_INV_MOTOR, p.hv2Len, f, if (top && !mo.regen) P.hv else col, 3f, rt)
    }
    rotate((m.compressorAngle * 180 / PI % 360).toFloat(), EL.ECOMP) {
        for (s in 0 until 3) rotate(s * 120f, EL.ECOMP) { drawLine(lerp(P.metal, P.intake, m.acPlate), EL.ECOMP, Offset(EL.ECOMP.x, EL.ECOMP.y - EL.ECOMP_R + 16), strokeWidth = 5f, cap = StrokeCap.Butt) }
    }
    drawCircle(P.metalDark, 5f, EL.ECOMP)
    if (m.acPlate > 0.02f && !dual) pulses(EL.HV_COMP, p.hv3Len, m.flowPhase.toFloat() * 60f, P.white, 2.5f, rt)

    // Battery cells: filled to the real percentage when it is known (amber at 15 % or below, never red)
    val cw = (EL.BAT_R - EL.BAT_L - 24) / EL.CELLS
    val lvl = m.cellLevel
    for (i in 0 until EL.CELLS) {
        val x0 = EL.BAT_L + 12 + i * cw + 4; val top0 = EL.BAT_T + 12; val hgt = EL.BAT_B - EL.BAT_T - 24
        if (lvl >= 0f) drawRect(if (lvl <= 0.15f) P.cellLow else P.cell, Offset(x0, top0 + hgt * (1f - lvl)), Size(cw - 8, hgt * lvl))
        val sp = (m.decoTime * 6 + i * 0.7).toFloat() % 8f
        if (m.regenGlow > 0.05f && sp < 0.6f) drawRect(P.eco.a(m.regenGlow * (1f - sp / 0.6f)), Offset(x0, top0), Size(cw - 8, 4f))
    }
    // Charging: cable from the right edge into the port, energy flowing in
    if (m.charging > 0.02f) {
        val port = Offset(EL.BAT_R - 26, EL.BAT_T - 14)
        drawLine(P.line.a(m.charging), Offset(EL.W, 470f), port, strokeWidth = 10f, cap = StrokeCap.Butt)
        drawLine(P.hv.a(m.charging), Offset(EL.W, 470f), port, strokeWidth = 7f, cap = StrokeCap.Butt)
        for (q in 0 until 4) {
            val t = ((m.decoTime * 0.8 + q / 4.0) % 1.0).toFloat()
            drawCircle(P.white.a(m.charging), 2.5f, Offset(EL.W + (port.x - EL.W) * t, 470f + (port.y - 470f) * t))
        }
    }
    // Reverse: the rotor itself turns backwards (no idler); a direction ring on the shaft end
    if (m.visualRps < 0) drawCircle(P.eco, EL.SHAFT_R + 6, EL.MOTOR, style = EngineParts.lineBold)
}

private fun DrawScope.gear(path: Path, c: Offset, deg: Float, light: Brush, P: EngineInk) {
    rotate(deg % 360f, c) { translate(c.x, c.y) {
        part(path, P.metalLight, light, P.line)
        drawCircle(P.metal, 7f, Offset.Zero); drawCircle(P.line, 7f, Offset.Zero, style = EngineParts.lineFine)
        drawLine(P.line, Offset.Zero, Offset(0f, -12f), strokeWidth = 1.6f)
    } }
}

/** Dashes travelling along an open polyline (sign of [offset] sets the direction). */
private fun DrawScope.pulses(pts: FloatArray, len: FloatArray, offset: Float, color: Color, width: Float, rt: EngineRuntime) {
    val total = len.last()
    val spacing = 26f
    val n = (total / spacing).toInt()
    val off = ((offset % spacing) + spacing) % spacing
    for (i in 0 until n) {
        val d = i * spacing + off
        if (d + 10f > total) continue
        EL.pointAt(pts, len, d, rt.pt); EL.pointAt(pts, len, d + 10f, rt.pt2)
        drawLine(color, Offset(rt.pt[0], rt.pt[1]), Offset(rt.pt2[0], rt.pt2[1]), strokeWidth = width, cap = StrokeCap.Butt)
    }
}

// ---------------------------------------------------------------- common to both

private fun DrawScope.drawCommon(rt: EngineRuntime, P: EngineInk) {
    val m = rt.mech
    // Blinker relay in the fuse box: contacts close with the car's own blinker rhythm
    val cy = EL.FUSE_T + 62f
    for (side in 0..1) {
        val on = if (side == 0) m.relayL else m.relayR
        val x = EL.FUSE_L + 32f + side * 36f
        if (on > 0.01f || m.relayL + m.relayR > 0f) {
            drawPath(if (side == 0) EngineParts.arrowL else EngineParts.arrowR, lerp(P.lineSoft, P.sport, 0.2f + 0.8f * on))
            drawPath(if (side == 0) EngineParts.arrowL else EngineParts.arrowR, P.line, style = EngineParts.lineFine)
        } else {
            drawCircle(P.textSoft, 3f, Offset(x, cy))
        }
    }
    val arm = if (m.relayL > 0.5f || m.relayR > 0.5f) 0f else 14f
    rotate(arm, Offset(EL.FUSE_L + 50f, EL.FUSE_T + 46f)) { drawLine(P.metalLight, Offset(EL.FUSE_L + 50f, EL.FUSE_T + 46f), Offset(EL.FUSE_L + 50f, EL.FUSE_T + 74f), strokeWidth = 2f) }
}

/** The studio's glass in front of the machine: rain beads on it, mist clears from the bottom while defrosting. */
private fun DrawScope.drawGlass(m: EngineMechanics, P: EngineInk) {
    if (m.fog > 0.01f) {
        val clearTop = EL.H * (1f - m.clear)
        drawRect(P.mist.a(0.34f * m.fog), Offset(-EL.W, -EL.H), Size(EL.W * 3, clearTop + EL.H))
        if (m.clear > 0.01f && m.clear < 0.99f) drawLine(P.mist.a(0.5f * m.fog), Offset(-EL.W, clearTop), Offset(EL.W * 2, clearTop), strokeWidth = 1f)
    }
    if (m.rain > 0.01f) {
        val p = EngineParts
        for (i in p.dropX.indices) {
            val r = p.dropR[i]
            val c = Offset(p.dropX[i], p.dropY[i])
            drawCircle(Color.White.a(0.10f * m.rain), r, c)
            drawCircle(P.mist.a(0.5f * m.rain), r, c, style = EngineParts.lineFine)
            drawCircle(P.white.a(0.85f * m.rain), r * 0.28f, Offset(c.x - r * 0.35f, c.y - r * 0.35f))
        }
    }
}

/** Small print inside the machine (design units): the gear-tile numbers. It fades while the car moves. */
private fun DrawScope.drawDrawingText(rt: EngineRuntime, texts: EngineTexts, P: EngineInk, a: Float) {
    if (rt.mech.powertrain != Powertrain.ICE) return
    for (i in GEAR_TILES.indices) {
        val l = texts.design(GEAR_TILES[i], 11f)
        drawText(l, color = P.textSoft, topLeft = Offset(EL.GB_L + 36.5f + i * 27f - l.size.width / 2f, EL.GB_T + 84f - l.size.height / 2f), alpha = a)
    }
}

/** Close-up of a design-space [region] filling the canvas (detail snapshots). */
internal fun EngineRuntime.drawDetail(ds: DrawScope, tm: TextMeasurer, region: Rect): Unit = with(ds) {
    val texts = texts(size.height, tm, this)
    ink.set(EngineInk.DAY, EngineInk.NIGHT, mech.night)
    val P = ink
    val k = minOf(size.width / region.width, size.height / region.height)
    val layer = layers(k, mech.powertrain, mech.night > 0.5f, this, texts.text)
    drawRect(Brush.verticalGradient(0f to P.bgTop, 1f to P.bgBottom, startY = 0f, endY = size.height))
    withTransform({ scale(k, k, Offset.Zero); translate(-region.left, -region.top) }) {
        clipRect(0f, 0f, EL.W, EL.H) {
            withTransform({ scale(1f / layer.k, 1f / layer.k, Offset.Zero) }) { drawImage(layer.bmp) }
            if (mech.powertrain == Powertrain.ICE) drawIce(this@drawDetail, P) else drawEv(this@drawDetail, P)
            drawCommon(this@drawDetail, P)
            drawDrawingText(this@drawDetail, texts, P, 1f)
            drawGlass(mech, P)
        }
    }
}

/**
 * Close-up of the telemetry display as it is laid out on a [vw] × [vh] px screen, filling the canvas (detail
 * snapshots).
 */
internal fun EngineRuntime.drawPanelDetail(ds: DrawScope, tm: TextMeasurer, vw: Float, vh: Float): Unit = with(ds) {
    val texts = texts(vh, tm, this)
    ink.set(EngineInk.DAY, EngineInk.NIGHT, mech.night)
    val P = ink
    val L = layout
    L.update(vw, vh, 1f, texts, mech.powertrain, model, density)
    val k = minOf(size.width / (L.cardR - L.cardL), size.height / (L.cardB - L.cardT)) * 0.96f
    drawRect(P.bgBottom)
    withTransform({
        translate(size.width / 2f, size.height / 2f); scale(k, k, Offset.Zero)
        translate(-(L.cardL + L.cardR) / 2f, -(L.cardT + L.cardB) / 2f)
    }) {
        drawCard(L, P)
        drawTelemetry(this@drawPanelDetail, texts, L, P, 1f, 1f)
    }
}

internal const val FRONT_ZOOM = 1.5f
