package io.vroomi.character.render.engine

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EngineGeometry
import io.vroomi.character.engine.EngineMechanics
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.engine.Stroke as CycleStroke
import io.vroomi.character.render.calibre.a
import io.vroomi.character.render.theme.ChromeZones
import io.vroomi.character.theme.ThemeModels
import io.vroomi.core.vehicle.model.GearState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen layout for one canvas size: the field the machine is drawn in and the telemetry display beside it.
 *
 * The display is a column on the right of a wide canvas (about 32 % of the width) and a band under the machine on
 * a narrow one. Its readout (model name, "추정값", three values) is what stays readable while driving, so it is
 * sized from the widest values it can show; the graphs fill the rest. The display always ends clear of the app's
 * chrome zones ([ChromeZones]) of every mode, so nothing it shows is ever under a button.
 */
internal class PanelLayout {
    var w = -1f; var h = -1f
    var margin = 0f
    var fieldL = 0f; var fieldT = 0f; var fieldR = 0f; var fieldB = 0f
    /** Design units → px of the machine (side view), and its centre. */
    var k = 1f; var cx = 0f; var cy = 0f
    /** The display's card, its readout and the graph area. */
    var cardL = 0f; var cardT = 0f; var cardR = 0f; var cardB = 0f
    var roL = 0f; var roT = 0f; var roR = 0f; var roB = 0f
    var gL = 0f; var gT = 0f; var gR = 0f; var gB = 0f
    var wide = true; private set
    /** False when the canvas leaves no room for graphs (a tiny miniature): the readout alone is shown. */
    var graphs = true; private set
    var pad = 0f; private set
    /** The left edges of the three value cells and their right end. */
    val cellX = FloatArray(4)
    /** Graph cells as (left, top, right, bottom): the dial, the strip beside it, and the two plots under them. */
    val dial = FloatArray(4); val strip = FloatArray(4); val plotA = FloatArray(4); val plotB = FloatArray(4)
    /** Curves of the plots in px, rebuilt with the layout. */
    val liftIn = Path(); val liftEx = Path(); val torque = Path(); val power = Path()
    var curve = Stroke(1f); private set
    var hair = Stroke(1f); private set
    /** Changes whenever the layout is rebuilt (part of the backdrop's cache key). */
    var version = 0; private set
    private var dens = 1f
    private var texts: EngineTexts? = null
    private var powertrain: Powertrain? = null
    private var model = -1
    private var sizeScale = 1f

    fun update(w: Float, h: Float, sizeScale: Float, texts: EngineTexts, p: Powertrain, model: Int, density: Float = 1f) {
        if (w == this.w && h == this.h && sizeScale == this.sizeScale && texts === this.texts && p == this.powertrain && model == this.model && density == dens) return
        this.w = w; this.h = h; this.sizeScale = sizeScale; this.texts = texts; this.powertrain = p; this.model = model; dens = density
        version++
        margin = h * 0.035f
        pad = h * 0.016f
        val ice = p == Powertrain.ICE
        val need = FloatArray(3)
        for (i in 0..2) need[i] = widest(texts, p, model, i) + pad * 2.4f
        val needSum = need[0] + need[1] + need[2]
        val headW = texts.head(headName(ice, model)).size.width + texts.estimate().size.width + pad * 5f
        val roNeed = max(needSum, headW) + pad * 2f
        val roH = pad + texts.headH + pad * 0.5f + texts.cellH + pad * 0.1f + texts.valueH + pad * 0.9f + pad
        val zones = ChromeZones.all(w / dens, h / dens)

        wide = w / h >= 1.9f
        if (wide) {
            val col = max(w * 0.32f - margin, roNeed).coerceAtMost(w * 0.46f)
            cardR = w - margin; cardL = cardR - col; cardT = margin
            var bottom = h - margin
            for (z in zones) {
                val zl = z.left * dens; val zr = z.right * dens; val zt = z.top * dens
                if (zl < cardR && zr > cardL && zt > h * 0.4f) bottom = min(bottom, zt)
            }
            cardB = max(bottom, cardT + roH)
            roL = cardL; roR = cardR; roT = cardT; roB = roT + roH
            gL = cardL + pad * 1.4f; gR = cardR - pad * 1.4f; gT = roB + pad * 0.4f; gB = cardB - pad * 1.2f
            fieldL = margin; fieldT = margin; fieldR = cardL - h * 0.02f; fieldB = h - margin
        } else {
            val band = (h * 0.30f).coerceIn(250f * dens, 340f * dens).coerceAtMost(h * 0.42f)
            cardB = h - margin; cardT = cardB - band; cardL = margin
            var right = w - margin
            for (z in zones) {
                val zl = z.left * dens; val zt = z.top * dens; val zb = z.bottom * dens
                if (zt < cardB && zb > cardT && zl > w * 0.4f) right = min(right, zl)
            }
            cardR = max(right, cardL + min(roNeed, w - margin * 2))
            roL = cardL; roT = cardT; roR = min(cardR, roL + max(roNeed, (cardR - cardL) * 0.40f)); roB = min(cardB, roT + roH)
            gL = roR + pad * 1.4f; gR = cardR - pad * 1.4f; gT = cardT + pad * 1.2f; gB = cardB - pad * 1.2f
            fieldL = margin; fieldT = margin; fieldR = w - margin; fieldB = cardT - h * 0.015f
        }
        // Value cells: spare width goes to the cells in proportion to their need.
        val avail = roR - roL - pad * 2f
        var x = roL + pad
        for (i in 0..2) { cellX[i] = x; x += avail * need[i] / needSum }
        cellX[3] = roL + pad + avail

        val fw = fieldR - fieldL; val fh = fieldB - fieldT
        k = min(fh * 0.95f / EL.H, fw * 0.97f / EL.W) * sizeScale.coerceIn(0.85f, 1.05f)
        cx = (fieldL + fieldR) / 2f; cy = (fieldT + fieldB) / 2f

        // Graph cells. One row up to Lv.2, two rows from Lv.3.
        val gw = gR - gL; val gh = gB - gT
        graphs = gw >= h * 0.22f && gh >= h * 0.14f
        if (!graphs) {
            // No room for graphs (a small miniature): the card is the readout alone.
            if (wide) cardB = roB else { cardT = cardB - roH; roT = cardT; roB = cardB; cardR = roR; fieldB = cardT - h * 0.015f }
            val fh2 = fieldB - fieldT
            k = min(fh2 * 0.95f / EL.H, (fieldR - fieldL) * 0.97f / EL.W) * sizeScale.coerceIn(0.85f, 1.05f)
            cy = (fieldT + fieldB) / 2f
        }
        val gap = pad * 1.2f
        val rows = if (model >= 2) 2 else 1
        val rowH = if (rows == 2) (gh - gap) / 2f else min(gh, gw * 0.46f)
        val side = min(rowH, gw * 0.34f)
        set(dial, gL, gT, gL + side, gT + rowH)
        set(strip, gL + side + gap, gT, gR, gT + rowH)
        val t2 = gT + rowH + gap
        set(plotA, gL, t2, if (model >= 3) gL + (gw - gap) / 2f else gR, gB)
        set(plotB, gL + (gw + gap) / 2f, t2, gR, gB)
        curve = Stroke(max(1.5f, h * 0.0045f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        hair = Stroke(max(1f, h * 0.0016f))
        if (graphs && rows == 2) buildCurves(texts, ice, model)
    }

    private fun set(r: FloatArray, l: Float, t: Float, rr: Float, b: Float) { r[0] = l; r[1] = t; r[2] = rr; r[3] = b }

    /** The plot area of a cell: under its caption line, with room on the right for [rightInset]. */
    fun plotTop(r: FloatArray, texts: EngineTexts): Float = r[1] + texts.cellH + pad * 0.3f

    private fun buildCurves(texts: EngineTexts, ice: Boolean, model: Int) {
        liftIn.reset(); liftEx.reset(); torque.reset(); power.reset()
        // Plot A: valve lift over the 720° cycle (ICE). The boost bar takes the right end.
        val a = plotA
        val al = a[0]; val ar = a[2] - barW(); val at = plotTop(a, texts); val ab = a[3]
        if (ice) {
            for (i in 0..180) {
                val d = i * 4f
                val x = al + (ar - al) * d / 720f
                val yi = ab - (ab - at) * 0.92f * EngineGeometry.valveLift(d, true)
                val ye = ab - (ab - at) * 0.92f * EngineGeometry.valveLift(d, false)
                if (i == 0) { liftIn.moveTo(x, yi); liftEx.moveTo(x, ye) } else { liftIn.lineTo(x, yi); liftEx.lineTo(x, ye) }
            }
        }
        // Plot B: torque and power over speed, each scaled to its own peak.
        val b = plotB
        val bl = b[0]; val br = b[2]; val bt = plotTop(b, texts); val bb = b[3]
        val n = 70
        if (ice) {
            var maxT = 1f; var maxP = 1f
            for (i in 0..n) { val r = RPM_AXIS * i / n; maxT = max(maxT, EngineGeometry.torqueAt(r, model)); maxP = max(maxP, EngineGeometry.powerKwAt(r, model)) }
            for (i in 0..n) {
                val r = RPM_AXIS * i / n
                val x = bl + (br - bl) * i / n
                val yt = bb - (bb - bt) * 0.9f * EngineGeometry.torqueAt(r, model) / maxT
                val yp = bb - (bb - bt) * 0.9f * EngineGeometry.powerKwAt(r, model) / maxP
                if (i == 0) { torque.moveTo(x, yt); power.moveTo(x, yp) } else { torque.lineTo(x, yt); power.lineTo(x, yp) }
            }
        } else {
            for (i in 0..n) {
                val u = i / n.toFloat()
                val x = bl + (br - bl) * u
                val yt = bb - (bb - bt) * 0.9f * motorTorque(u)
                val yp = bb - (bb - bt) * 0.9f * (motorTorque(u) * u / MOTOR_BASE).coerceAtMost(1f)
                if (i == 0) { torque.moveTo(x, yt); power.moveTo(x, yp) } else { torque.lineTo(x, yt); power.lineTo(x, yp) }
            }
        }
    }

    /** Width kept at the right of plot A for the boost bar. */
    fun barW(): Float = pad * 2.6f

    private fun widest(t: EngineTexts, p: Powertrain, model: Int, k: Int): Float {
        val ice = p == Powertrain.ICE
        var m = t.cell(cellLabel(ice, model, k)).size.width.toFloat()
        when (k) {
            0 -> m = maxOf(m, t.rpm(if (ice) EngineGeometry.DISPLAY_MAX_RPM else EngineGeometry.MOTOR_MAX_RPM).size.width.toFloat())
            1 -> for (g in WIDEST_GEARS) m = maxOf(m, t.gear(g).size.width.toFloat())
            else -> if (ice) m = maxOf(m, t.percent(1.0).size.width.toFloat())
                else for (v in WIDEST_KW) m = maxOf(m, t.kw(v).size.width.toFloat())
        }
        return m
    }

    companion object {
        /** The speed axis of the torque plot (rpm). */
        const val RPM_AXIS = 7000f
        /** The motor's base speed as a share of its top speed: constant torque below, constant power above. */
        const val MOTOR_BASE = 0.3f
        /** Motor torque 0..1 at speed [u] 0..1. */
        fun motorTorque(u: Float): Float = if (u <= MOTOR_BASE) 1f else MOTOR_BASE / u
    }
}

/** Gear labels and kW values that set the cell widths (the widest each column can show). */
private val WIDEST_GEARS = arrayOf("D8", "R", "N", "P", "D")
private val WIDEST_KW = doubleArrayOf(-60.0, -9.0, 340.0, 288.0)

internal fun headName(ice: Boolean, model: Int): String = if (ice) ThemeModels.title(SceneTheme.ENGINE, model) else EvStep.NAMES[model.coerceIn(0, 4)]

private fun cellLabel(ice: Boolean, model: Int, k: Int): String = when (k) {
    0 -> "RPM"
    1 -> "기어"
    else -> if (!ice) "출력 kW" else if (IceStep.turbo(model)) "부스트" else "부하"
}

// ---------------------------------------------------------------- the studio backdrop (cached)

/**
 * The studio: a graphite room, a cool key light from the upper left, a warm rim light from the right. Painted
 * once per canvas size and light.
 */
internal fun DrawScope.printStudio(L: PanelLayout, P: EngineInk) {
    val w = L.w; val h = L.h
    drawRect(Brush.verticalGradient(0f to P.bgTop, 1f to P.bgBottom, startY = 0f, endY = h))
    drawRect(Brush.radialGradient(0f to Color(0xFFA6BED4).a(0.17f), 1f to Color.Transparent, center = Offset(w * 0.2f, -h * 0.15f), radius = w * 0.6f))
    drawRect(Brush.radialGradient(0f to Color(0xFFE6A868).a(0.08f), 1f to Color.Transparent, center = Offset(w * 1.03f, h * 0.6f), radius = w * 0.34f))
    drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.a(0.38f), center = Offset(w / 2f, h / 2f), radius = hypot(w, h) / 2f))
}

/** The display's card: a dark glass panel with a hairline edge, and the rule between the readout and the graphs. */
internal fun DrawScope.drawCard(L: PanelLayout, P: EngineInk, va: Float = 1f) {
    if (va <= 0.01f) return
    val rad = CornerRadius(L.h * 0.022f, L.h * 0.022f)
    val tl = Offset(L.cardL, L.cardT); val sz = Size(L.cardR - L.cardL, L.cardB - L.cardT)
    drawRoundRect(P.panel.a(0.86f * va), tl, sz, rad)
    drawRoundRect(P.panelLine.a(va), tl, sz, rad, style = L.hair)
    if (L.graphs) {
        if (L.wide) drawLine(P.panelLine.a(va), Offset(L.roL + L.pad, L.roB), Offset(L.roR - L.pad, L.roB), strokeWidth = L.hair.width)
        else drawLine(P.panelLine.a(va), Offset(L.roR, L.cardT + L.pad), Offset(L.roR, L.cardB - L.pad), strokeWidth = L.hair.width)
    }
}

// ---------------------------------------------------------------- the display (per frame)

/**
 * The telemetry display: the readout and the graphs of the upgrade step. Every number is an estimate from the
 * simulation ([EngineMechanics]) and the heading says so ("추정값"). [small] is the alpha of text below the
 * driving minimum (it fades out while the car moves); [va] the alpha of the whole display.
 */
internal fun DrawScope.drawTelemetry(rt: EngineRuntime, texts: EngineTexts, L: PanelLayout, P: EngineInk, small: Float, va: Float) {
    if (va <= 0.01f) return
    val m = rt.mech
    val ice = m.powertrain == Powertrain.ICE
    val model = rt.model
    val pad = L.pad
    val probe = rt.probeWidestValues

    // Heading: the step's name, and on the right the word that makes every value below honest.
    val head = texts.head(headName(ice, model))
    val headY = L.roT + pad
    drawText(head, color = P.text, topLeft = Offset(L.roL + pad * 1.4f, headY), alpha = va)
    val est = texts.estimate()
    val estX = L.roR - pad * 1.4f - est.size.width
    drawText(est, color = P.textSoft, topLeft = Offset(estX, headY + (head.size.height - est.size.height) * 0.7f), alpha = va)
    // Reward: a green check beside it (a short press, held, then gone).
    val t = (m.decoTime - m.starGlintStart).toFloat()
    if (t in 0f..4f) {
        val s = texts.headH * 0.5f
        val press = if (t < 0.12f) 1.25f - t / 0.12f * 0.25f else 1f
        val alpha = (if (t > 3.5f) 1f - (t - 3.5f) / 0.5f else 1f) * va
        withTransform({ translate(estX - pad * 1.2f - s * 0.5f, headY + head.size.height * 0.55f); scale(s * press, s * press, Offset.Zero) }) {
            drawPath(EngineParts.check, P.stamp.a(alpha), style = EngineParts.stampCheck)
        }
    }

    // Values: labels above (they fade while driving), numbers in monospace on one baseline.
    val labelY = headY + head.size.height + pad * 0.5f
    val valueY = labelY + texts.cellH + pad * 0.1f
    val cx = L.cellX
    if (small > 0.01f) for (i in 0..2) drawText(texts.cell(cellLabel(ice, model, i)), color = P.textSoft, topLeft = Offset(cx[i] + pad * 0.4f, labelY), alpha = small * va)
    val rpm = if (probe) (if (ice) EngineGeometry.DISPLAY_MAX_RPM else EngineGeometry.MOTOR_MAX_RPM) else if (ice) m.engine.rpm else abs(m.motor.rpm)
    drawText(texts.rpm(rpm), color = P.text, topLeft = Offset(cx[0] + pad * 0.4f, valueY), alpha = va)
    drawText(texts.gear(if (probe) "D8" else if (ice) m.engine.gearLabel else gearLetter(m)), color = P.text, topLeft = Offset(cx[1] + pad * 0.4f, valueY), alpha = va)
    if (ice) {
        val v = if (probe) 1.0 else if (IceStep.turbo(model)) m.engine.boost else m.engine.load
        drawText(texts.percent(v), color = if (m.engine.fuelCut) P.textSoft else P.text, topLeft = Offset(cx[2] + pad * 0.4f, valueY), alpha = va)
    } else {
        drawText(texts.kw(if (probe) -60.0 else m.motor.powerKw), color = if (probe || m.motor.regen) P.eco else P.text, topLeft = Offset(cx[2] + pad * 0.4f, valueY), alpha = va)
    }
    // Under the values: the drive-mode line (ECO teal, SPORT amber); a combo sweeps it green once.
    val bandY = valueY + texts.valueH + pad * 0.35f
    val bandL = L.roL + pad * 1.4f; val bandW = L.roR - L.roL - pad * 2.8f
    val bandH = max(2f, L.h * 0.005f)
    drawRect(P.panelLine.a(va), Offset(bandL, bandY), Size(bandW, bandH))
    val acc = if (m.modeAccent < 0f) P.eco.a(-m.modeAccent * va) else P.sport.a(m.modeAccent * va)
    drawRect(acc, Offset(bandL, bandY), Size(bandW, bandH))
    val ct = (m.decoTime - m.comboPulseStart).toFloat()
    if (ct in 0f..3.4f) {
        val sweep = (ct / 1.2f).coerceAtMost(1f)
        val fade = if (ct > 2.9f) 1f - (ct - 2.9f) / 0.5f else 1f
        drawRect(P.stamp.a(fade * va), Offset(bandL, bandY), Size(bandW * sweep, bandH))
    }

    if (!L.graphs) return
    if (ice) iceGraphs(rt, texts, L, P, small, va) else evGraphs(rt, texts, L, P, small, va)
}

private fun gearLetter(m: EngineMechanics): String = when (m.gear) {
    GearState.REVERSE -> "R"; GearState.DRIVE -> "D"; GearState.NEUTRAL -> "N"; else -> "P"
}

private fun DrawScope.caption(texts: EngineTexts, s: String, r: FloatArray, P: EngineInk, a: Float) {
    if (a > 0.01f) drawText(texts.cell(s), color = P.textSoft, topLeft = Offset(r[0], r[1]), alpha = a)
}

/** The dial of a rotating shaft: ring, twelve ticks, the top mark; returns nothing, the caller adds the pointer. */
private fun DrawScope.dialFace(c: Offset, R: Float, L: PanelLayout, P: EngineInk, va: Float) {
    drawCircle(P.textSoft.a(0.4f * va), R, c, style = L.hair)
    for (i in 0 until 12) {
        val a = i * PI.toFloat() / 6f
        val r0 = if (i % 3 == 0) R * 0.80f else R * 0.89f
        drawLine(P.textSoft.a(0.7f * va), Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * R, c.y + sin(a) * R), strokeWidth = L.hair.width)
    }
    drawLine(P.text.a(va), Offset(c.x, c.y - R * 1.0f), Offset(c.x, c.y - R * 0.74f), strokeWidth = L.curve.width)
}

private fun DrawScope.pointer(c: Offset, R: Float, rad: Float, color: Color, width: Float) {
    // 0 rad = top, clockwise.
    drawLine(color, c, Offset(c.x + sin(rad) * R, c.y - cos(rad) * R), strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.iceGraphs(rt: EngineRuntime, texts: EngineTexts, L: PanelLayout, P: EngineInk, small: Float, va: Float) {
    val m = rt.mech
    val model = rt.model
    val sa = small * va
    val phase1 = EngineGeometry.phase(m.cycleDeg, 0)

    // 1. Crank angle (every step). Lv.5 adds the spark advance as an arc before the top mark.
    val d = L.dial
    caption(texts, "크랭크각", d, P, sa)
    val top = L.plotTop(d, texts)
    val R = min(d[2] - d[0], d[3] - top) * 0.46f
    val c = Offset((d[0] + d[2]) / 2f, top + (d[3] - top) / 2f)
    dialFace(c, R, L, P, va)
    if (IceStep.race(model)) {
        val adv = EngineGeometry.ignitionAdvanceDeg(m.engine.rpm, m.engine.load)
        val r = R * 1.1f
        drawArc(P.electricLight.a(va), -90f - adv, adv, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = L.curve)
    }
    val crank = (phase1 % 360.0 * PI / 180.0).toFloat()
    pointer(c, R * 0.82f, crank, P.sport.a(va), L.curve.width * 1.3f)
    drawCircle(P.text.a(va), L.curve.width * 1.6f, c)
    if (sa > 0.01f) {
        // Under the hub: the crank angle, or (Lv.5) the spark advance the arc shows.
        val n = if (IceStep.race(model)) texts.advance(EngineGeometry.ignitionAdvanceDeg(m.engine.rpm, m.engine.load).toInt()) else texts.deg((phase1 % 360.0).toInt())
        val ty = c.y + R * 0.34f
        drawRect(P.panel.a(0.72f * sa), Offset(c.x - n.size.width / 2f - 3f, ty), Size(n.size.width + 6f, n.size.height.toFloat()))
        drawText(n, color = P.text, topLeft = Offset(c.x - n.size.width / 2f, ty), alpha = sa)
    }

    // 2. Stroke strip, Lv.2: the four cylinders over one 720° cycle; the cursor is the engine's cycle angle.
    if (IceStep.dohc(model)) {
        val s = L.strip
        caption(texts, "행정 · 점화 순서 1-3-4-2", s, P, sa)
        val t0 = L.plotTop(s, texts)
        val numW = texts.cellH * 0.9f
        val l = s[0] + numW; val w = s[2] - l
        val rowH = (s[3] - t0) / 4f
        val segW = w / 4f
        val cycle = EngineGeometry.wrap720(m.cycleDeg)
        val cur = (cycle / 180.0).toInt().coerceIn(0, 3)
        for (i in 0 until 4) {
            val y = t0 + rowH * i
            if (sa > 0.01f) { val n = texts.cell(CYL[i]); drawText(n, color = P.textSoft, topLeft = Offset(s[0], y + (rowH - n.size.height) / 2f), alpha = sa) }
            for (q in 0 until 4) {
                val col = when (EngineGeometry.stroke(EngineGeometry.phase(q * 180.0 + 90.0, i))) {
                    CycleStroke.POWER -> P.fire; CycleStroke.EXHAUST -> P.exhaust; CycleStroke.INTAKE -> P.intake; else -> P.electricLight
                }
                drawRect(col.a((if (q == cur) 1f else 0.34f) * va), Offset(l + segW * q + 1f, y + rowH * 0.16f), Size(segW - 2f, rowH * 0.68f))
            }
        }
        val x = l + w * (cycle / 720.0).toFloat()
        drawLine(P.text.a(va), Offset(x, t0), Offset(x, s[3]), strokeWidth = L.curve.width * 0.8f)
    }

    // 3. Valve lift, Lv.3: exhaust and intake over the cycle, with cylinder 1's position; the boost bar beside it.
    if (IceStep.turbo(model)) {
        val a = L.plotA
        caption(texts, "밸브 리프트 · 부스트", a, P, sa)
        val t0 = L.plotTop(a, texts)
        val r = a[2] - L.barW()
        drawLine(P.textSoft.a(0.45f * va), Offset(a[0], a[3]), Offset(r, a[3]), strokeWidth = L.hair.width)
        for (q in 0..4) { val x = a[0] + (r - a[0]) * q / 4f; drawLine(P.textSoft.a(0.45f * va), Offset(x, a[3]), Offset(x, a[3] - L.pad * 0.5f), strokeWidth = L.hair.width) }
        drawPath(L.liftEx, P.gasHot.a(va), style = L.curve)
        drawPath(L.liftIn, P.intake.a(va), style = L.curve)
        val x = a[0] + (r - a[0]) * (phase1 / 720.0).toFloat()
        drawLine(P.text.a(0.7f * va), Offset(x, t0), Offset(x, a[3]), strokeWidth = L.hair.width)
        val hgt = (a[3] - t0) * 0.92f
        val li = EngineGeometry.valveLift(phase1.toFloat(), true); val le = EngineGeometry.valveLift(phase1.toFloat(), false)
        if (li > 0.01f) drawCircle(P.intake.a(va), L.curve.width * 1.7f, Offset(x, a[3] - hgt * li))
        if (le > 0.01f) drawCircle(P.gasHot.a(va), L.curve.width * 1.7f, Offset(x, a[3] - hgt * le))
        // Boost bar
        val bl = a[2] - L.barW() * 0.62f; val bw = L.barW() * 0.62f
        drawRect(P.panelLine.a(va), Offset(bl, t0), Size(bw, a[3] - t0))
        val b = m.engine.boost.toFloat().coerceIn(0f, 1f)
        drawRect(P.intake.a(va), Offset(bl, a[3] - (a[3] - t0) * b), Size(bw, (a[3] - t0) * b))
    }

    // 4. Torque and power over speed, Lv.4: the engine's present speed as a point on the torque curve.
    if (IceStep.directInjection(model)) {
        val b = L.plotB
        caption(texts, "토크 · 출력", b, P, sa)
        val t0 = L.plotTop(b, texts)
        drawLine(P.textSoft.a(0.45f * va), Offset(b[0], b[3]), Offset(b[2], b[3]), strokeWidth = L.hair.width)
        drawLine(P.textSoft.a(0.45f * va), Offset(b[0], t0), Offset(b[0], b[3]), strokeWidth = L.hair.width)
        drawPath(L.power, P.textSoft.a(0.9f * va), style = L.curve)
        drawPath(L.torque, P.sport.a(va), style = L.curve)
        val rpm = m.engine.rpm.toFloat().coerceIn(0f, PanelLayout.RPM_AXIS)
        var maxT = 1f
        for (i in 0..14) maxT = max(maxT, EngineGeometry.torqueAt(PanelLayout.RPM_AXIS * i / 14f, model))
        val x = b[0] + (b[2] - b[0]) * rpm / PanelLayout.RPM_AXIS
        val y = b[3] - (b[3] - t0) * 0.9f * (EngineGeometry.torqueAt(rpm, model) / maxT).coerceAtMost(1f)
        drawLine(P.text.a(0.5f * va), Offset(x, y), Offset(x, b[3]), strokeWidth = L.hair.width)
        drawCircle(P.panel.a(va), L.curve.width * 2.6f, Offset(x, y))
        drawCircle(P.sport.a(va), L.curve.width * 1.9f, Offset(x, y))
    }
}

private val CYL = arrayOf("1", "2", "3", "4")

private fun DrawScope.evGraphs(rt: EngineRuntime, texts: EngineTexts, L: PanelLayout, P: EngineInk, small: Float, va: Float) {
    val m = rt.mech
    val model = rt.model
    val sa = small * va
    val amp = min(1f, abs(m.motor.powerKw).toFloat() / 50f)

    // 1. Rotor angle (every step); from Lv.2 the three phase currents as phasors.
    val d = L.dial
    caption(texts, "회전자 각", d, P, sa)
    val top = L.plotTop(d, texts)
    val R = min(d[2] - d[0], d[3] - top) * 0.46f
    val c = Offset((d[0] + d[2]) / 2f, top + (d[3] - top) / 2f)
    dialFace(c, R, L, P, va)
    if (EvStep.oilJacket(model)) for (k in 0 until 3) {
        val col = when (k) { 0 -> P.electric; 1 -> P.electricLight; else -> P.electricDark }
        pointer(c, R * (0.2f + 0.5f * amp), (m.electricalAngle + k * 2 * PI / 3).toFloat(), col.a(va), L.curve.width)
    }
    pointer(c, R * 0.82f, m.rotorAngle.toFloat(), P.sport.a(va), L.curve.width * 1.3f)
    drawCircle(P.text.a(va), L.curve.width * 1.6f, c)

    // 2. Phase currents, Lv.2: three sines, their amplitude follows the power.
    if (EvStep.oilJacket(model)) {
        val s = L.strip
        caption(texts, "3상 전류", s, P, sa)
        val t0 = L.plotTop(s, texts)
        val mid = (t0 + s[3]) / 2f; val half = (s[3] - t0) * 0.44f * (0.15f + 0.85f * amp)
        drawLine(P.textSoft.a(0.4f * va), Offset(s[0], mid), Offset(s[2], mid), strokeWidth = L.hair.width)
        val n = 40
        for (k in 0 until 3) {
            val col = when (k) { 0 -> P.electric; 1 -> P.electricLight; else -> P.electricDark }
            var px = s[0]; var py = mid - half * sin(m.electricalAngle + k * 2 * PI / 3).toFloat()
            for (i in 1..n) {
                val x = s[0] + (s[2] - s[0]) * i / n
                val y = mid - half * sin(m.electricalAngle + k * 2 * PI / 3 + i * 4.0 * PI / n).toFloat()
                drawLine(col.a(va), Offset(px, py), Offset(x, y), strokeWidth = L.curve.width * 0.8f, cap = StrokeCap.Round)
                px = x; py = y
            }
        }
    }

    // 3. Power flow, Lv.3: drive to the right (amber), regeneration to the left (teal). Lv.4 splits front and rear.
    if (EvStep.hairpin(model)) {
        val a = L.plotA
        caption(texts, if (EvStep.dual(model)) "출력 흐름 · 앞 / 뒤" else "출력 흐름", a, P, sa)
        val t0 = L.plotTop(a, texts)
        val bars = if (EvStep.dual(model)) 2 else 1
        val zero = a[0] + (a[2] - a[0]) * 0.25f
        val bh = (a[3] - t0) / bars
        val p = m.motor.powerKw.toFloat()
        for (i in 0 until bars) {
            val share = if (bars == 1) 1f else if (i == 0) 0.6f else 0.4f
            val y = t0 + bh * i + bh * 0.2f
            drawRect(P.panelLine.a(va), Offset(a[0], y), Size(a[2] - a[0], bh * 0.6f))
            val v = p * share
            if (v >= 0f) drawRect(P.sport.a(va), Offset(zero, y), Size((a[2] - zero) * (v / (200f * share)).coerceAtMost(1f), bh * 0.6f))
            else { val wdt = (zero - a[0]) * (-v / (60f * share)).coerceAtMost(1f); drawRect(P.eco.a(va), Offset(zero - wdt, y), Size(wdt, bh * 0.6f)) }
        }
        drawLine(P.text.a(0.8f * va), Offset(zero, t0), Offset(zero, a[3]), strokeWidth = L.hair.width)
    }

    // 4. Torque and power over speed, Lv.4: the motor's present speed on the torque curve.
    if (EvStep.dual(model)) {
        val b = L.plotB
        caption(texts, "토크 · 출력", b, P, sa)
        val t0 = L.plotTop(b, texts)
        drawLine(P.textSoft.a(0.45f * va), Offset(b[0], b[3]), Offset(b[2], b[3]), strokeWidth = L.hair.width)
        drawLine(P.textSoft.a(0.45f * va), Offset(b[0], t0), Offset(b[0], b[3]), strokeWidth = L.hair.width)
        drawPath(L.power, P.textSoft.a(0.9f * va), style = L.curve)
        drawPath(L.torque, P.sport.a(va), style = L.curve)
        val u = (abs(m.motor.rpm) / EngineGeometry.MOTOR_MAX_RPM).toFloat().coerceIn(0f, 1f)
        val x = b[0] + (b[2] - b[0]) * u
        val y = b[3] - (b[3] - t0) * 0.9f * PanelLayout.motorTorque(u)
        drawLine(P.text.a(0.5f * va), Offset(x, y), Offset(x, b[3]), strokeWidth = L.hair.width)
        drawCircle(P.panel.a(va), L.curve.width * 2.6f, Offset(x, y))
        drawCircle(P.sport.a(va), L.curve.width * 1.9f, Offset(x, y))
    }
}
