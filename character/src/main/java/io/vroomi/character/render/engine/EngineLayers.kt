package io.vroomi.character.render.engine

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.vroomi.character.engine.EngineGeometry
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.calibre.CalibreText
import io.vroomi.character.render.calibre.a
import io.vroomi.character.render.calibre.gearOutline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Every static part of the cutaway (housings, hatched cut faces, colour-coded pipes, dimensions, labels), printed
 * once into a bitmap at [k] px per design unit for one [powertrain] and one ink (day paper or night slate).
 */
internal class EngineLayers(val k: Float, val powertrain: Powertrain, val night: Boolean, density: Density, text: CalibreText) {
    val bmp: ImageBitmap

    init {
        val w = (EL.W * k).toInt().coerceAtLeast(2)
        val h = (EL.H * k).toInt().coerceAtLeast(2)
        bmp = ImageBitmap(w, h)
        val ink = if (night) ManualInk.NIGHT else ManualInk.DAY
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
            scale(k, k, Offset.Zero) {
                if (powertrain == Powertrain.ICE) staticIce(text, ink) else staticEv(text, ink)
            }
        }
    }
}

// ---------------------------------------------------------------- helpers (design units)

internal fun rectPath(l: Float, t: Float, r: Float, b: Float, rad: Float = 0f) = Path().apply {
    addRoundRect(RoundRect(l, t, r, b, CornerRadius(rad, rad)))
}

private val OUTLINE = Stroke(1.3f, join = StrokeJoin.Round)
private val OUTLINE_FINE = Stroke(0.9f, join = StrokeJoin.Round)

/** A section-cut face: a flat fill with 45° section hatching and a thin ink outline (drafting convention). */
internal fun DrawScope.cutFace(p: Path, P: ManualInk, spacing: Float = 6f, fill: Color = P.cut) {
    drawPath(p, fill)
    clipPath(p) {
        val b = p.getBounds()
        var x = b.left - b.height
        while (x < b.right) {
            drawLine(P.hatch.a(0.85f), Offset(x, b.bottom), Offset(x + b.height, b.top), strokeWidth = 0.8f)
            x += spacing
        }
    }
    drawPath(p, P.line, style = OUTLINE)
}

/** A cavity (the inside of the engine): a flat light tone and a fine outline. */
internal fun DrawScope.cavity(p: Path, P: ManualInk) {
    drawPath(p, P.cavity)
    drawPath(p, P.lineSoft, style = OUTLINE_FINE)
}

/** An outside surface (not cut): one flat tone and a thin ink outline. */
internal fun DrawScope.cast(p: Path, P: ManualInk, base: Color = P.metal) {
    drawPath(p, base)
    drawPath(p, P.line, style = OUTLINE)
}

/** A pipe along a polyline: a flat colour-coded band between two ink lines. */
internal fun DrawScope.pipe(pts: FloatArray, width: Float, base: Color, P: ManualInk) {
    val p = Path().apply { moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 } }
    drawPath(p, P.line, style = Stroke(width + 2.6f, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    drawPath(p, base, style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Round))
}

/** A closed band along sampled points (belt, chain). */
internal fun DrawScope.band(pts: FloatArray, width: Float, color: Color, P: ManualInk) {
    val p = Path().apply { moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }; close() }
    drawPath(p, P.line, style = Stroke(width + 2.4f, join = StrokeJoin.Round))
    drawPath(p, color, style = Stroke(width, join = StrokeJoin.Round))
}

/** Hidden edges (drafting convention): a thin dashed line. */
internal fun DrawScope.hidden(pts: FloatArray, color: Color) {
    val p = Path().apply { moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 } }
    drawPath(p, color, style = Stroke(1.1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 4f))))
}

/** A centre line (chain-dotted). */
private fun DrawScope.centreLine(a: Offset, b: Offset, P: ManualInk) {
    drawLine(P.lineSoft, a, b, strokeWidth = 0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 3f, 2f, 3f)))
}

private fun DrawScope.label(text: CalibreText, s: String, at: Offset, size: Float, color: Color, center: Boolean = false) {
    val l = text.styled("eng-l|$s|$size|${color.value}", s, size, color, ManualType.condensed, ManualType.bold, 0.04f)
    drawText(l, topLeft = if (center) Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f) else at)
}

/**
 * A dimension: extension lines and a line with two filled arrowheads. Its figure is drawn per frame
 * ([DIMENSIONS]), so it can fade while the car moves.
 */
private fun DrawScope.dimension(P: ManualInk, x0: Float, x1: Float, y: Float, ext0: Float) {
    drawLine(P.lineSoft, Offset(x0, ext0), Offset(x0, y + 5f), strokeWidth = 0.7f)
    drawLine(P.lineSoft, Offset(x1, ext0), Offset(x1, y + 5f), strokeWidth = 0.7f)
    drawLine(P.line, Offset(x0, y), Offset(x1, y), strokeWidth = 0.8f)
    for ((x, dir) in listOf(x0 to 1f, x1 to -1f)) {
        val head = Path().apply { moveTo(x, y); lineTo(x + dir * 9f, y - 3f); lineTo(x + dir * 9f, y + 3f); close() }
        drawPath(head, P.line)
    }
}

/** A dimension figure: its text and the centre of the dimension line (design units). */
internal class DimFigure(val text: String, val x: Float, val y: Float)

private val BLOCK_MM = ((EL.BLOCK_R - EL.BLOCK_L) / EL.S).roundToInt()
private val MOTOR_MM = ((EL.HOUSING_R * 2) / EL.S).roundToInt()
internal val ICE_DIMENSIONS = arrayOf(
    DimFigure("$BLOCK_MM", (EL.BLOCK_L + EL.BLOCK_R) / 2f, 628f),
    DimFigure("Ø${EngineGeometry.BORE_MM.roundToInt()}", EL.CYL_X[3], 72f),
)
internal val EV_DIMENSIONS = arrayOf(DimFigure("Ø$MOTOR_MM", EL.MOTOR.x, 546f))

private fun DrawScope.radiator(P: ManualInk) {
    val core = rectPath(EL.RAD_L, EL.RAD_T, EL.RAD_R, EL.RAD_B, 2f)
    drawPath(core, P.metalLight)
    clipPath(core) {
        var y = EL.RAD_T + 3
        while (y < EL.RAD_B) { drawLine(P.lineSoft, Offset(EL.RAD_L, y), Offset(EL.RAD_R, y), strokeWidth = 0.7f); y += 5f }
        drawRect(P.coolant.a(0.55f), Offset((EL.RAD_L + EL.RAD_R) / 2 - 2f, EL.RAD_T), Size(4f, EL.RAD_B - EL.RAD_T))
    }
    drawPath(core, P.line, style = OUTLINE)
    cast(rectPath(EL.RAD_L - 4, EL.RAD_T - 22, EL.RAD_R + 4, EL.RAD_T, 3f), P, P.coolant)
    cast(rectPath(EL.RAD_L - 4, EL.RAD_B, EL.RAD_R + 4, EL.RAD_B + 22, 3f), P, P.coolant)
    // Fan shroud (edge-on) and the fan motor
    cast(rectPath(EL.FAN.x - 12, EL.FAN.y - EL.FAN_R - 10, EL.FAN.x + 12, EL.FAN.y + EL.FAN_R + 10, 6f), P, P.cavity)
    cast(rectPath(EL.FAN.x + 6, EL.FAN.y - 22, EL.FAN.x + 34, EL.FAN.y + 22, 4f), P, P.electricLight)
}

private fun DrawScope.fuseBox(P: ManualInk) {
    cast(rectPath(EL.FUSE_L, EL.FUSE_T, EL.FUSE_R, EL.FUSE_B, 4f), P, P.metalLight)
    for (i in 0 until 5) cast(rectPath(EL.FUSE_L + 10 + i * 17, EL.FUSE_T + 12, EL.FUSE_L + 22 + i * 17, EL.FUSE_T + 32, 1f), P, P.electricLight)
    cavity(rectPath(EL.FUSE_L + 14, EL.FUSE_T + 42, EL.FUSE_L + 86, EL.FUSE_T + 82, 2f), P)
}

// ---------------------------------------------------------------- VE-4T (ICE)

internal fun DrawScope.staticIce(text: CalibreText, P: ManualInk) {
    radiator(P)

    // Heater core and its hose (coolant flow drawn per frame)
    cast(rectPath(EL.HEATER_L, EL.HEATER_T, EL.HEATER_R, EL.HEATER_B, 3f), P, P.metalLight)
    clipPath(rectPath(EL.HEATER_L + 6, EL.HEATER_T + 6, EL.HEATER_R - 6, EL.HEATER_B - 6)) {
        var x = EL.HEATER_L + 8; while (x < EL.HEATER_R) { drawLine(P.lineSoft, Offset(x, EL.HEATER_T), Offset(x, EL.HEATER_B), strokeWidth = 0.7f); x += 5f }
    }
    pipe(EL.HEATER, 8f, P.coolant, P)

    // Accessory brackets and pulley faces (spokes and belt ribs move per frame)
    for (p in EL.BELT) drawLine(P.lineSoft, p.c, Offset(EL.BLOCK_L - 10, p.y.coerceIn(220f, 520f)), strokeWidth = 4f, cap = StrokeCap.Butt)
    for (p in EL.BELT) {
        drawCircle(P.metal, p.r, p.c); drawCircle(P.line, p.r, p.c, style = OUTLINE)
        drawCircle(P.lineSoft, p.r * 0.72f, p.c, style = OUTLINE_FINE)
    }
    drawCircle(P.electricLight, EL.P_ALT.r * 0.5f, EL.P_ALT.c); drawCircle(P.line, EL.P_ALT.r * 0.5f, EL.P_ALT.c, style = OUTLINE_FINE)
    band(EL.BELT_POINTS, 7f, P.belt, P)

    // Timing chain case (cut) with its cavity
    cutFace(rectPath(266f, 108f, 322f, EL.CASE_B, 6f), P)
    cavity(rectPath(274f, 112f, 314f, EL.CASE_B - 6, 12f), P)
    drawCircle(P.metal, EL.CHAIN_CAM_R, EL.CHAIN_CAM); drawCircle(P.line, EL.CHAIN_CAM_R, EL.CHAIN_CAM, style = OUTLINE)
    drawCircle(P.metal, EL.CHAIN_CRANK_R, EL.CHAIN_CRANK); drawCircle(P.line, EL.CHAIN_CRANK_R, EL.CHAIN_CRANK, style = OUTLINE)
    band(EL.CHAIN_POINTS, 5f, P.metalDark, P)

    // Block and crankcase (cut)
    cutFace(rectPath(EL.BLOCK_L, EL.DECK, EL.BLOCK_R, EL.CASE_B, 4f), P)
    cavity(rectPath(EL.BLOCK_L + 16, EL.BLOCK_B - 6, EL.BLOCK_R - 16, EL.CASE_B - 10, 10f), P)
    val halfB = EL.BORE / 2
    for (i in 0 until 4) {
        val x = EL.CYL_X[i]
        cavity(rectPath(x - halfB, EL.DECK, x + halfB, EL.BLOCK_B, 0f), P)
        for (sx in listOf(-1f, 1f)) {
            val l = if (sx < 0) x - halfB - EL.LINER else x + halfB
            drawRect(P.metalDark, Offset(l, EL.DECK), Size(EL.LINER, EL.BLOCK_B - EL.DECK - 12))
            drawRect(P.line, Offset(l, EL.DECK), Size(EL.LINER, EL.BLOCK_B - EL.DECK - 12), style = OUTLINE_FINE)
        }
        centreLine(Offset(x, EL.HEAD_TOP - 30f), Offset(x, EL.CASE_B + 30f), P)
    }
    // Water jackets (coolant teal-green)
    for (x in EngineParts.jacketsX) {
        val p = rectPath(x - 5f, EL.DECK + 10, x + 5f, EL.DECK + 150, 3f)
        drawPath(p, P.coolant.a(0.45f)); drawPath(p, P.line, style = OUTLINE_FINE)
    }
    // Main bearing webs (cut) and journals
    val webs = floatArrayOf(EL.CYL_X[0] - 75, EL.CYL_X[0] + 75, EL.CYL_X[1] + 75, EL.CYL_X[2] + 75, EL.CYL_X[3] + 75)
    for (x in webs) {
        cutFace(rectPath(x - 10, EL.BLOCK_B - 6, x + 10, EL.CASE_B - 10, 2f), P, 5f)
        drawCircle(P.cavity, 17f, Offset(x, EL.CRANK_Y)); drawCircle(P.line, 17f, Offset(x, EL.CRANK_Y), style = OUTLINE_FINE)
    }
    centreLine(Offset(EL.BLOCK_L - 40f, EL.CRANK_Y), Offset(EL.BLOCK_R + 40f, EL.CRANK_Y), P)
    // Oil pan with the oil bath (oil amber)
    val pan = Path().apply {
        moveTo(EL.BLOCK_L + 15, EL.CASE_B - 8); lineTo(EL.BLOCK_R - 15, EL.CASE_B - 8); lineTo(EL.BLOCK_R - 30, 600f)
        lineTo(EL.BLOCK_R - 44, 608f); lineTo(EL.BLOCK_L + 44, 608f); lineTo(EL.BLOCK_L + 30, 600f); close()
    }
    drawPath(pan, P.metalLight)
    clipPath(pan) { drawRect(P.oil, Offset(EL.BLOCK_L, 584f), Size(EL.BLOCK_R - EL.BLOCK_L, 30f)) }
    drawPath(pan, P.line, style = OUTLINE)
    drawLine(P.line, Offset(EL.BLOCK_L + 34, 584f), Offset(EL.BLOCK_R - 34, 584f), strokeWidth = 0.8f)

    // Cylinder head (cut) with valve galleries, plug wells and pent-roof chambers
    cutFace(rectPath(EL.BLOCK_L - 4, EL.HEAD_TOP, EL.BLOCK_R + 4, EL.DECK + 2, 3f), P)
    for (i in 0 until 4) {
        val x = EL.CYL_X[i]
        cavity(rectPath(x - 48, EL.HEAD_TOP + 4, x + 48, EL.DECK - 18, 6f), P)
        cavity(Path().apply { moveTo(x - halfB, EL.DECK + 1); lineTo(x - halfB + 6, EL.DECK - 12); lineTo(x + halfB - 6, EL.DECK - 12); lineTo(x + halfB, EL.DECK + 1); close() }, P)
        for (sx in listOf(-1f, 1f)) drawRect(P.metalDark, Offset(x + sx * EL.VALVE_DX - 13, EL.DECK - 22), Size(26f, 4f))
        cutFace(rectPath(x - 8, EL.HEAD_TOP + 2, x + 8, EL.DECK - 12, 2f), P, 4f)
        // Spark plug: white ceramic, electrical violet terminal
        drawRect(P.cut, Offset(x - 4, EL.HEAD_TOP + 6), Size(8f, 50f)); drawRect(P.line, Offset(x - 4, EL.HEAD_TOP + 6), Size(8f, 50f), style = OUTLINE_FINE)
        drawRect(P.electric, Offset(x - 4, EL.HEAD_TOP + 2), Size(8f, 8f))
        drawRect(P.metal, Offset(x - 5, EL.HEAD_TOP + 56), Size(10f, EL.DECK - 12 - EL.HEAD_TOP - 56))
        drawRect(P.line, Offset(x - 5, EL.HEAD_TOP + 56), Size(10f, EL.DECK - 12 - EL.HEAD_TOP - 56), style = OUTLINE_FINE)
        drawLine(P.line, Offset(x - halfB + 2, EL.DECK - 30), Offset(x - halfB + 14, EL.DECK - 10), strokeWidth = 5f, cap = StrokeCap.Butt)
    }
    drawLine(P.metalDark, Offset(EL.BLOCK_L, EL.CAM_Y), Offset(EL.BLOCK_R, EL.CAM_Y), strokeWidth = 4f)
    for (i in 0 until 4) {
        val x = EL.CYL_X[i]
        hidden(floatArrayOf(x + EL.VALVE_DX, EL.DECK - 12, x + 62, EL.EXH_Y), P.exhaust)
        hidden(floatArrayOf(x - EL.VALVE_DX, EL.PLENUM_B, x - EL.VALVE_DX, EL.COVER_TOP), P.intake)
    }
    hidden(floatArrayOf(EL.CYL_X[0] + 62, EL.EXH_Y, EL.BLOCK_R + 4, EL.EXH_Y), P.exhaust)

    // Cam cover (not cut) with the maker's mark
    cast(rectPath(EL.BLOCK_L + 10, EL.COVER_TOP, EL.BLOCK_R - 10, EL.HEAD_TOP + 4, 10f), P, P.metalLight)
    for (i in 0 until 4) { drawCircle(P.metal, 4.5f, Offset(EL.CYL_X[i], EL.COVER_TOP + 10)); drawCircle(P.line, 4.5f, Offset(EL.CYL_X[i], EL.COVER_TOP + 10), style = OUTLINE_FINE) }
    label(text, "VE-4T", Offset((EL.BLOCK_L + EL.BLOCK_R) / 2, EL.COVER_TOP + 20f), 16f, P.line, center = true)

    // Intake (pale blue): plenum, throttle body, intercooler pipe
    cast(rectPath(EL.PLENUM_L, EL.PLENUM_T, EL.PLENUM_R, EL.PLENUM_B, 8f), P, P.intake)
    pipe(floatArrayOf(985f, 46f, 1080f, 46f, 1080f, EL.TURBO.y - EL.TURBO_R + 2), 20f, P.intake, P)
    cast(rectPath(928f, 24f, 986f, 68f, 4f), P, P.metalLight)
    drawCircle(P.cavity, 15f, EL.THROTTLE); drawCircle(P.line, 15f, EL.THROTTLE, style = OUTLINE_FINE)

    // Exhaust (oxide red): manifold, turbine housing, downpipe, catalyst, tailpipe
    pipe(floatArrayOf(EL.BLOCK_R + 2, EL.EXH_Y, 1022f, EL.EXH_Y, 1040f, 206f), 22f, P.exhaust, P)
    pipe(floatArrayOf(1080f, EL.TURBO.y + 40, 1080f, EL.CAT_T), 24f, P.exhaust, P)
    pipe(floatArrayOf(1080f, EL.CAT_B, 1080f, 458f, 1272f, 458f), 16f, P.exhaust, P)
    cutFace(Path().apply { addOval(Rect(EL.TURBO, EL.TURBO_R + 6)) }, P, 5f)
    cavity(Path().apply { addOval(Rect(EL.TURBO, 40f)) }, P)
    cutFace(rectPath(EL.CAT_L, EL.CAT_T, EL.CAT_R, EL.CAT_B, 6f), P, 5f)
    val brick = rectPath(EL.CAT_L + 8, EL.CAT_T + 10, EL.CAT_R - 8, EL.CAT_B - 10, 2f)
    drawPath(brick, P.exhaust.a(0.35f))
    clipPath(brick) { var x = EL.CAT_L + 8; while (x < EL.CAT_R) { drawLine(P.lineSoft, Offset(x, EL.CAT_T), Offset(x, EL.CAT_B), strokeWidth = 0.7f); x += 4f } }
    drawPath(brick, P.line, style = OUTLINE_FINE)

    // Gearbox: bellhousing and case (cut) with the inset cavity and clutch-pack slots
    cutFace(Path().apply { moveTo(EL.BLOCK_R - 4, EL.BLOCK_B + 10); lineTo(EL.GB_L + 18, EL.GB_T); lineTo(EL.GB_L + 18, EL.GB_B - 10); lineTo(EL.BLOCK_R - 4, EL.CASE_B - 4); close() }, P)
    cutFace(rectPath(EL.GB_L, EL.GB_T, EL.GB_R, EL.GB_B, 8f), P)
    cavity(rectPath(EL.GB_L + 14, EL.GB_T + 12, EL.GB_R - 14, EL.GB_B - 12, 5f), P)
    for (i in 0 until 9) {
        val x = EL.GB_L + 26 + i * 27f
        drawRect(P.paper, Offset(x, EL.GB_T + 26), Size(21f, 46f)); drawRect(P.lineSoft, Offset(x, EL.GB_T + 26), Size(21f, 46f), style = OUTLINE_FINE)
    }
    drawRect(P.paper, Offset(EL.GB_R - 120, EL.GB_B - 54), Size(52f, 30f)); drawRect(P.lineSoft, Offset(EL.GB_R - 120, EL.GB_B - 54), Size(52f, 30f), style = OUTLINE_FINE)

    fuseBox(P)

    // Dimensions: block length and bore (true millimetres of the VE-4T)
    dimension(P, EL.BLOCK_L, EL.BLOCK_R, 628f, 612f)
    dimension(P, EL.CYL_X[3] - halfB, EL.CYL_X[3] + halfB, 72f, 66f)
}

internal val GEAR_TILES = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "R")

// ---------------------------------------------------------------- VE-M1 (EV)

internal fun DrawScope.staticEv(text: CalibreText, P: ManualInk) {
    radiator(P)

    // Battery pack (cut) with cell slots
    cutFace(rectPath(EL.BAT_L, EL.BAT_T, EL.BAT_R, EL.BAT_B, 6f), P)
    val cw = (EL.BAT_R - EL.BAT_L - 24) / EL.CELLS
    for (i in 0 until EL.CELLS) cavity(rectPath(EL.BAT_L + 12 + i * cw + 2, EL.BAT_T + 10, EL.BAT_L + 12 + (i + 1) * cw - 2, EL.BAT_B - 10, 2f), P)

    // Reduction gear case (cut) with cavity
    cutFace(rectPath(EL.RED_L, EL.RED_T, EL.RED_R, EL.RED_B, 10f), P)
    cavity(rectPath(EL.RED_L + 14, EL.RED_T + 14, EL.RED_R - 14, EL.RED_B - 14, 8f), P)

    // Motor housing (cut), stator core (laminations) with slots
    cutFace(Path().apply { addOval(Rect(EL.MOTOR, EL.HOUSING_R)) }, P, 6f)
    val core = Path().apply {
        addOval(Rect(EL.MOTOR, EL.STATOR_R))
        addOval(Rect(EL.MOTOR, EL.STATOR_IN))
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    drawPath(core, P.metalLight)
    var r = EL.STATOR_IN + 6
    while (r < EL.STATOR_R) { drawCircle(P.lineSoft.a(0.6f), r, EL.MOTOR, style = Stroke(0.6f)); r += 6f }
    for (s in 0 until 48) {
        val a = s / 48f * 2f * PI.toFloat()
        val c = Offset(EL.MOTOR.x + cos(a) * (EL.STATOR_IN + 30), EL.MOTOR.y + sin(a) * (EL.STATOR_IN + 30))
        drawCircle(P.cavity, 8.5f, c); drawCircle(P.lineSoft, 8.5f, c, style = OUTLINE_FINE)
    }
    drawPath(core, P.line, style = OUTLINE)
    drawCircle(P.cavity, EL.STATOR_IN - 1, EL.MOTOR)
    drawCircle(P.line, EL.STATOR_IN - 1, EL.MOTOR, style = OUTLINE_FINE)
    // Shaft to the reduction gears
    drawRect(P.metal, Offset(EL.MOTOR.x, EL.MOTOR.y - 10), Size(EL.PINION.x - EL.MOTOR.x, 20f))
    drawRect(P.line, Offset(EL.MOTOR.x, EL.MOTOR.y - 10), Size(EL.PINION.x - EL.MOTOR.x, 20f), style = OUTLINE_FINE)
    centreLine(Offset(EL.MOTOR.x - EL.HOUSING_R - 30f, EL.MOTOR.y), Offset(EL.PINION.x + 60f, EL.MOTOR.y), P)
    centreLine(Offset(EL.MOTOR.x, EL.MOTOR.y - EL.HOUSING_R - 24f), Offset(EL.MOTOR.x, EL.MOTOR.y + EL.HOUSING_R + 16f), P)

    // Inverter (electrical violet) with the phasor dial
    cast(rectPath(EL.INV_L, EL.INV_T, EL.INV_R, EL.INV_B, 6f), P, P.electricLight)
    drawCircle(P.paper, 56f, EL.INV_DIAL); drawCircle(P.line, 56f, EL.INV_DIAL, style = OUTLINE)
    for (k in 0 until 3) {
        val a = k * 2f * PI.toFloat() / 3f - PI.toFloat() / 2
        drawLine(P.lineSoft, EL.INV_DIAL, Offset(EL.INV_DIAL.x + cos(a) * 56, EL.INV_DIAL.y + sin(a) * 56), strokeWidth = 0.7f)
    }
    for (k in 0 until 3) cast(rectPath(EL.INV_L + 20, EL.INV_T + 24 + k * 34f, EL.INV_L + 90, EL.INV_T + 46 + k * 34f, 2f), P, P.electric)
    label(text, "VE-M1", Offset(EL.INV_L + 18, EL.INV_B - 30), 15f, P.line)

    // HV cables (electrical violet; flow pulses are drawn per frame)
    pipe(EL.HV_BAT_INV, 9f, P.electric, P)
    pipe(EL.HV_INV_MOTOR, 9f, P.electric, P)
    pipe(EL.HV_COMP, 7f, P.electric, P)
    cutFace(Path().apply { addOval(Rect(EL.ECOMP, EL.ECOMP_R)) }, P, 5f)
    cavity(Path().apply { addOval(Rect(EL.ECOMP, EL.ECOMP_R - 12)) }, P)
    cast(rectPath(EL.BAT_R - 46, EL.BAT_T - 30, EL.BAT_R - 6, EL.BAT_T + 4, 3f), P, P.electricLight)

    fuseBox(P)

    dimension(P, EL.MOTOR.x - EL.HOUSING_R, EL.MOTOR.x + EL.HOUSING_R, 546f, 300f)
}

/** Gear outline path (design units) for the reduction gears and the reverse idler. */
internal fun gearPath(c: Offset, r: Float, teeth: Int): Path = gearOutline(c, r, teeth, max(3f, r * 0.12f))
