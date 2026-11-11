package io.vroomi.character.render.engine

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.calibre.CalibreText
import io.vroomi.character.render.calibre.a
import io.vroomi.character.render.calibre.gearOutline
import io.vroomi.character.render.hash01
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Every static part of the cutaway (castings, red cut faces, pipes, the parts an upgrade step adds), painted once
 * into a bitmap at [k] px per design unit for one [powertrain], one light (day or night) and one upgrade step
 * [model] (0..4 = Lv.1..5).
 *
 * The look is a display engine in a dark studio: one key light from the upper left on every shape, a soft
 * low-contrast casting texture, cut faces in signal red with fine hatching, dark cavities so the bright moving
 * parts read against them.
 */
internal class EngineLayers(val k: Float, val powertrain: Powertrain, val night: Boolean, val model: Int, density: Density, text: CalibreText) {
    val bmp: ImageBitmap

    init {
        val w = (EL.W * k).toInt().coerceAtLeast(2)
        val h = (EL.H * k).toInt().coerceAtLeast(2)
        bmp = ImageBitmap(w, h)
        val ink = if (night) EngineInk.NIGHT else EngineInk.DAY
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
            scale(k, k, Offset.Zero) {
                if (powertrain == Powertrain.ICE) staticIce(text, ink, model) else staticEv(text, ink, model)
            }
        }
    }
}

/** What each upgrade step of the VE-4 adds (spec 5.2): the painter and the static layer read the same flags. */
internal object IceStep {
    fun dohc(model: Int) = model >= 1
    fun turbo(model: Int) = model >= 2
    fun directInjection(model: Int) = model >= 3
    fun race(model: Int) = model >= 4
    /** The mark cast into the cam cover. */
    val MARKS = arrayOf("VE-4 SOHC", "VE-4 DOHC", "VE-4T", "VE-4T GDI", "VE-4R")
}

/** What each upgrade step of the VE-M motor adds. */
internal object EvStep {
    fun oilJacket(model: Int) = model >= 1
    fun hairpin(model: Int) = model >= 2
    fun dual(model: Int) = model >= 3
    fun carbonRotor(model: Int) = model >= 4
    val MARKS = arrayOf("VE-M1", "VE-M1", "VE-M1H", "VE-M2", "VE-M2R")
    /** The step names shown in the telemetry heading (the ICE names come from ThemeModels). */
    val NAMES = arrayOf("VE-M1 단일 모터", "VE-M1 오일 냉각", "VE-M1H 헤어핀", "VE-M2 이중 모터", "VE-M2R 카본 로터")
}

// ---------------------------------------------------------------- helpers (design units)

internal fun rectPath(l: Float, t: Float, r: Float, b: Float, rad: Float = 0f) = Path().apply {
    addRoundRect(RoundRect(l, t, r, b, CornerRadius(rad, rad)))
}

private fun ovalPath(c: Offset, r: Float) = Path().apply { addOval(Rect(c, r)) }

private val OUTLINE = Stroke(1.2f, join = StrokeJoin.Round)
private val OUTLINE_FINE = Stroke(0.8f, join = StrokeJoin.Round)

/** The key light from the upper left across one shape: a soft highlight, then a soft shadow (no texture). */
private fun DrawScope.shade(p: Path, strength: Float = 1f) {
    val b = p.getBounds()
    drawPath(p, Brush.linearGradient(
        0f to Color.White.a(0.20f * strength), 0.42f to Color.Transparent, 1f to Color.Black.a(0.30f * strength),
        start = Offset(b.left, b.top), end = Offset(b.left + b.width * 0.22f, b.bottom),
    ))
}

/** Sand-cast texture: sparse low-contrast grains (never a pattern). */
private fun DrawScope.speckle(p: Path, seed: Int) {
    val b = p.getBounds()
    val n = (b.width * b.height / 60f).toInt().coerceIn(24, 1100)
    clipPath(p) {
        for (i in 0 until n) {
            val c = Offset(b.left + hash01(i, seed) * b.width, b.top + hash01(i, seed + 1) * b.height)
            drawCircle(if (hash01(i, seed + 2) > 0.5f) Color.White.a(0.06f) else Color.Black.a(0.08f), 0.5f + 0.8f * hash01(i, seed + 3), c)
        }
    }
}

private fun DrawScope.hatch(p: Path, color: Color, spacing: Float) {
    clipPath(p) {
        val b = p.getBounds()
        var x = b.left - b.height
        while (x < b.right) { drawLine(color, Offset(x, b.bottom), Offset(x + b.height, b.top), strokeWidth = 0.7f); x += spacing }
    }
}

/**
 * A sectioned casting: the casting's own skin shows as a rim around the cut, and the cut face is painted signal
 * red with fine 45° hatching (the display-engine convention).
 */
internal fun DrawScope.cutFace(p: Path, P: EngineInk, skin: Color = P.cast, spacing: Float = 5f) {
    drawPath(p, P.line, style = Stroke(10.6f, join = StrokeJoin.Round))
    drawPath(p, skin, style = Stroke(8.2f, join = StrokeJoin.Round))
    cutPlain(p, P, spacing)
}

/** A cut face without a skin rim (a web or a well inside a casting). */
internal fun DrawScope.cutPlain(p: Path, P: EngineInk, spacing: Float = 5f) {
    drawPath(p, P.cut)
    hatch(p, P.hatch, spacing)
    shade(p, 0.6f)
    drawPath(p, P.line.a(0.75f), style = OUTLINE_FINE)
}

/** A cavity (the inside of the engine): dark, with a soft shadow under its upper edge. */
internal fun DrawScope.cavity(p: Path, P: EngineInk) {
    drawPath(p, P.cavity)
    val b = p.getBounds()
    drawPath(p, Brush.verticalGradient(0f to Color.Black.a(0.45f), 1f to Color.Transparent, startY = b.top, endY = b.top + minOf(18f, b.height * 0.5f)))
    drawPath(p, P.line, style = OUTLINE_FINE)
}

/** An outside casting surface: matte tone, sand-cast grains, the key light, a thin outline. */
internal fun DrawScope.cast(p: Path, P: EngineInk, base: Color = P.cast, seed: Int = 7) {
    drawPath(p, base)
    speckle(p, seed)
    shade(p)
    drawPath(p, P.line, style = OUTLINE)
}

/** A machined or moulded surface (no grain): tone, key light, outline. */
internal fun DrawScope.smooth(p: Path, P: EngineInk, base: Color, strength: Float = 1f) {
    drawPath(p, base)
    shade(p, strength)
    drawPath(p, P.line, style = OUTLINE)
}

/** A turned disc (pulley, sprocket, journal): a satin face lit from the upper left, a darker rim. */
internal fun DrawScope.disc(c: Offset, r: Float, P: EngineInk, base: Color = P.metal) {
    drawCircle(base, r, c)
    drawCircle(Brush.radialGradient(0f to Color.White.a(0.30f), 0.55f to Color.Transparent, 1f to Color.Black.a(0.32f), center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.5f), r, c)
    drawCircle(P.line, r, c, style = OUTLINE)
}

private fun polyline(pts: FloatArray, close: Boolean = false) = Path().apply {
    moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
    if (close) close()
}

/** A tube along a polyline: outline, body, one soft highlight along its length. */
internal fun DrawScope.pipe(pts: FloatArray, width: Float, base: Color, P: EngineInk) {
    val p = polyline(pts)
    drawPath(p, P.line, style = Stroke(width + 2.4f, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    drawPath(p, base, style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    drawPath(p, Color.Black.a(0.22f), style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    drawPath(p, base, style = Stroke(width * 0.62f, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    drawPath(p, Color.White.a(0.26f), style = Stroke(width * 0.2f, cap = StrokeCap.Butt, join = StrokeJoin.Round))
}

/** A braided steel hose: a steel tube with the weave as short alternating marks. */
private fun DrawScope.braid(pts: FloatArray, width: Float, P: EngineInk) {
    pipe(pts, width, P.metalDark, P)
    val p = polyline(pts)
    drawPath(p, P.metalLight.a(0.75f), style = Stroke(width * 0.7f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f, 2.4f))))
    drawPath(p, P.line.a(0.35f), style = Stroke(width * 0.7f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.8f, 3.2f), 2f)))
}

/** A closed band along sampled points (belt, chain). */
internal fun DrawScope.band(pts: FloatArray, width: Float, color: Color, P: EngineInk) {
    val p = polyline(pts, close = true)
    drawPath(p, P.line, style = Stroke(width + 2.2f, join = StrokeJoin.Round))
    drawPath(p, color, style = Stroke(width, join = StrokeJoin.Round))
    drawPath(p, Color.White.a(0.10f), style = Stroke(width * 0.3f, join = StrokeJoin.Round))
}

/** Carbon fibre: near-black, a faint twill weave, one soft gloss band. */
private fun DrawScope.carbon(p: Path, P: EngineInk) {
    drawPath(p, P.carbon)
    clipPath(p) {
        val b = p.getBounds()
        var x = b.left - b.height
        while (x < b.right + b.height) {
            drawLine(Color.White.a(0.055f), Offset(x, b.bottom), Offset(x + b.height, b.top), strokeWidth = 1.6f)
            drawLine(Color.White.a(0.04f), Offset(x, b.top), Offset(x + b.height, b.bottom), strokeWidth = 1.6f)
            x += 5f
        }
        drawRect(Brush.verticalGradient(0f to Color.White.a(0.16f), 1f to Color.Transparent, startY = b.top, endY = b.top + b.height * 0.45f), Offset(b.left, b.top), Size(b.width, b.height * 0.45f))
    }
    drawPath(p, P.line, style = OUTLINE)
}

/** The soft contact shadow on the studio floor under the machine. */
private fun DrawScope.floorShadow(l: Float, r: Float, y: Float) {
    val c = Offset((l + r) / 2f, y); val rx = (r - l) / 2f
    scale(1f, 0.045f, c) {
        drawCircle(Brush.radialGradient(0f to Color.Black.a(0.6f), 0.65f to Color.Black.a(0.3f), 1f to Color.Transparent, center = c, radius = rx), rx, c)
    }
}

private fun DrawScope.label(text: CalibreText, s: String, at: Offset, size: Float, color: Color, center: Boolean = false) {
    val l = text.styled("eng-l|$s|$size|${color.value}", s, size, color, EngineType.condensed, EngineType.bold, 0.06f)
    drawText(l, topLeft = if (center) Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f) else at)
}

private fun DrawScope.radiator(P: EngineInk) {
    val core = rectPath(EL.RAD_L, EL.RAD_T, EL.RAD_R, EL.RAD_B, 2f)
    drawPath(core, P.metal)
    clipPath(core) {
        var y = EL.RAD_T + 3
        while (y < EL.RAD_B) { drawLine(P.line.a(0.55f), Offset(EL.RAD_L, y), Offset(EL.RAD_R, y), strokeWidth = 0.7f); y += 4f }
    }
    shade(core, 0.8f)
    drawPath(core, P.line, style = OUTLINE)
    smooth(rectPath(EL.RAD_L - 4, EL.RAD_T - 22, EL.RAD_R + 4, EL.RAD_T, 3f), P, P.rubber)
    smooth(rectPath(EL.RAD_L - 4, EL.RAD_B, EL.RAD_R + 4, EL.RAD_B + 22, 3f), P, P.rubber)
    // Fan shroud (edge-on) and the fan motor
    cavity(rectPath(EL.FAN.x - 12, EL.FAN.y - EL.FAN_R - 10, EL.FAN.x + 12, EL.FAN.y + EL.FAN_R + 10, 6f), P)
    drawPath(rectPath(EL.FAN.x - 12, EL.FAN.y - EL.FAN_R - 10, EL.FAN.x + 12, EL.FAN.y + EL.FAN_R + 10, 6f), P.lineSoft, style = Stroke(2f))
    smooth(rectPath(EL.FAN.x + 6, EL.FAN.y - 22, EL.FAN.x + 34, EL.FAN.y + 22, 4f), P, P.metalDark)
}

private fun DrawScope.fuseBox(P: EngineInk) {
    smooth(rectPath(EL.FUSE_L, EL.FUSE_T, EL.FUSE_R, EL.FUSE_B, 4f), P, P.rubber, 0.7f)
    val fuses = arrayOf(P.sport, P.eco, P.electricLight, P.oil, P.intake)
    for (i in 0 until 5) smooth(rectPath(EL.FUSE_L + 10 + i * 17, EL.FUSE_T + 12, EL.FUSE_L + 22 + i * 17, EL.FUSE_T + 32, 1f), P, fuses[i], 0.6f)
    cavity(rectPath(EL.FUSE_L + 14, EL.FUSE_T + 42, EL.FUSE_L + 86, EL.FUSE_T + 82, 2f), P)
}

// ---------------------------------------------------------------- VE-4 (ICE)

internal fun DrawScope.staticIce(text: CalibreText, P: EngineInk, model: Int) {
    val dohc = IceStep.dohc(model); val turbo = IceStep.turbo(model); val gdi = IceStep.directInjection(model); val race = IceStep.race(model)
    // Lv.1 is a cast-iron block; from Lv.2 the block is aluminium.
    val skin = if (model == 0) P.iron else P.cast
    val halfB = EL.BORE / 2

    floorShadow(EL.RAD_L - 10f, EL.GB_R + 6f, 624f)
    radiator(P)

    // Lv.5: the dry-sump tank and its braided line (the line runs behind the gearbox)
    if (race) {
        braid(floatArrayOf((EL.TANK_L + EL.TANK_R) / 2f, EL.TANK_B - 4f, (EL.TANK_L + EL.TANK_R) / 2f, EL.GB_T + 20f), 9f, P)
        val tank = rectPath(EL.TANK_L, EL.TANK_T, EL.TANK_R, EL.TANK_B, 14f)
        drawPath(tank, P.metalLight)
        drawPath(tank, Brush.horizontalGradient(0f to Color.Black.a(0.38f), 0.3f to Color.White.a(0.22f), 0.55f to Color.Transparent, 1f to Color.Black.a(0.42f), startX = EL.TANK_L, endX = EL.TANK_R))
        drawPath(tank, P.line, style = OUTLINE)
        for (y in floatArrayOf(EL.TANK_T + 34f, EL.TANK_B - 34f)) drawLine(P.line.a(0.45f), Offset(EL.TANK_L + 1f, y), Offset(EL.TANK_R - 1f, y), strokeWidth = 0.9f)
        // Sight glass with the oil level
        val sx = (EL.TANK_L + EL.TANK_R) / 2f
        val glass = rectPath(sx - 5f, EL.TANK_T + 50f, sx + 5f, EL.TANK_B - 50f, 5f)
        drawPath(glass, P.cavity)
        clipPath(glass) { drawRect(P.oil, Offset(sx - 5f, EL.TANK_T + 110f), Size(10f, EL.TANK_B - EL.TANK_T)) }
        drawLine(Color.White.a(0.5f), Offset(sx - 2.4f, EL.TANK_T + 56f), Offset(sx - 2.4f, EL.TANK_B - 56f), strokeWidth = 1f)
        drawPath(glass, P.line, style = OUTLINE_FINE)
        smooth(rectPath(sx - 12f, EL.TANK_T - 10f, sx + 12f, EL.TANK_T + 2f, 3f), P, P.metalDark)
    }

    // Heater core and its hose (coolant flow drawn per frame); the hose is braided steel from Lv.4
    val heater = rectPath(EL.HEATER_L, EL.HEATER_T, EL.HEATER_R, EL.HEATER_B, 3f)
    drawPath(heater, P.metal)
    clipPath(rectPath(EL.HEATER_L + 6, EL.HEATER_T + 6, EL.HEATER_R - 6, EL.HEATER_B - 6)) {
        var x = EL.HEATER_L + 8; while (x < EL.HEATER_R) { drawLine(P.line.a(0.5f), Offset(x, EL.HEATER_T), Offset(x, EL.HEATER_B), strokeWidth = 0.7f); x += 4f }
    }
    shade(heater, 0.8f)
    drawPath(heater, P.line, style = OUTLINE)
    if (gdi) braid(EL.HEATER, 8f, P) else pipe(EL.HEATER, 8f, P.rubber, P)

    // Accessory brackets and pulley faces (spokes and belt ribs move per frame)
    for (p in EL.BELT) drawLine(P.metalDark, p.c, Offset(EL.BLOCK_L - 10, p.y.coerceIn(220f, 520f)), strokeWidth = 5f, cap = StrokeCap.Butt)
    // Alternator body behind its pulley: the copper windings show through the vents
    disc(EL.P_ALT.c, EL.P_ALT.r * 1.28f, P, P.cast)
    drawCircle(P.copper, EL.P_ALT.r * 1.08f, EL.P_ALT.c, style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))))
    for (p in EL.BELT) {
        disc(p.c, p.r, P, P.metal)
        drawCircle(P.line.a(0.5f), p.r * 0.72f, p.c, style = OUTLINE_FINE)
    }
    band(EL.BELT_POINTS, 7f, P.belt, P)

    // Timing chain case (cut) with its cavity
    cutFace(rectPath(266f, 108f, 322f, EL.CASE_B, 6f), P, skin)
    cavity(rectPath(274f, 112f, 314f, EL.CASE_B - 6, 12f), P)
    disc(EL.CHAIN_CAM, EL.CHAIN_CAM_R, P, P.metal)
    disc(EL.CHAIN_CRANK, EL.CHAIN_CRANK_R, P, P.metal)
    // Lv.2: the cam phaser of the variable valve timing (a gold-anodised hub on the cam sprocket)
    if (dohc) { disc(EL.CHAIN_CAM, 15f, P, P.gold); drawCircle(P.line.a(0.6f), 6f, EL.CHAIN_CAM, style = OUTLINE_FINE) }
    band(EL.CHAIN_POINTS, 5f, P.metalDark, P)

    // Block and crankcase (cut), then the head on it (cut): both castings before any cavity, so no rim crosses a bore
    cutFace(rectPath(EL.BLOCK_L, EL.DECK, EL.BLOCK_R, EL.CASE_B, 4f), P, skin)
    cutFace(rectPath(EL.BLOCK_L - 4, EL.HEAD_TOP, EL.BLOCK_R + 4, EL.DECK + 2, 3f), P, P.cast)
    cavity(rectPath(EL.BLOCK_L + 16, EL.BLOCK_B - 6, EL.BLOCK_R - 16, EL.CASE_B - 10, 10f), P)
    for (i in 0 until 4) {
        val x = EL.CYL_X[i]
        cavity(rectPath(x - halfB, EL.DECK, x + halfB, EL.BLOCK_B, 0f), P)
        // Pressed-in liners (the aluminium blocks only)
        if (model >= 1) for (sx in listOf(-1f, 1f)) {
            val l = if (sx < 0) x - halfB - EL.LINER else x + halfB
            drawRect(P.metalDark, Offset(l, EL.DECK), Size(EL.LINER, EL.BLOCK_B - EL.DECK - 12))
            drawRect(P.line, Offset(l, EL.DECK), Size(EL.LINER, EL.BLOCK_B - EL.DECK - 12), style = OUTLINE_FINE)
        }
    }
    // Water jackets
    for (x in EngineParts.jacketsX) {
        val p = rectPath(x - 5f, EL.DECK + 10, x + 5f, EL.DECK + 150, 3f)
        drawPath(p, P.cavity); drawPath(p, P.coolant.a(0.55f)); drawPath(p, P.line, style = OUTLINE_FINE)
    }
    // Main bearing webs (cut), bronze shells and the steel journals
    val webs = floatArrayOf(EL.CYL_X[0] - 75, EL.CYL_X[0] + 75, EL.CYL_X[1] + 75, EL.CYL_X[2] + 75, EL.CYL_X[3] + 75)
    for (x in webs) {
        cutPlain(rectPath(x - 10, EL.BLOCK_B - 6, x + 10, EL.CASE_B - 10, 2f), P, 4f)
        val c = Offset(x, EL.CRANK_Y)
        drawCircle(P.bronze, 19f, c); drawCircle(P.line, 19f, c, style = OUTLINE_FINE)
        disc(c, 14.5f, P, P.metal)
    }
    // Sump: a wet pan with its oil bath, or (Lv.5) the shallow dry-sump plate with scavenge ports
    if (race) {
        val plate = rectPath(EL.BLOCK_L + 15, EL.CASE_B - 8, EL.BLOCK_R - 15, EL.CASE_B + 16, 4f)
        smooth(plate, P, P.metalLight)
        for (i in 0 until 3) {
            val x = EL.BLOCK_L + 110f + i * 205f
            drawPath(rectPath(x, EL.CASE_B - 2, x + 44f, EL.CASE_B + 9, 4f), P.cavity)
            drawLine(P.oil, Offset(x + 6f, EL.CASE_B + 5f), Offset(x + 38f, EL.CASE_B + 5f), strokeWidth = 2f)
        }
    } else {
        val pan = Path().apply {
            moveTo(EL.BLOCK_L + 15, EL.CASE_B - 8); lineTo(EL.BLOCK_R - 15, EL.CASE_B - 8); lineTo(EL.BLOCK_R - 30, 600f)
            lineTo(EL.BLOCK_R - 44, 608f); lineTo(EL.BLOCK_L + 44, 608f); lineTo(EL.BLOCK_L + 30, 600f); close()
        }
        drawPath(pan, P.line, style = Stroke(7f, join = StrokeJoin.Round))
        drawPath(pan, P.metalDark, style = Stroke(4.8f, join = StrokeJoin.Round))
        drawPath(pan, P.cavity)
        clipPath(pan) {
            // The oil bath: amber, darker with depth, one glossy line on its surface (the oil film).
            drawRect(Brush.verticalGradient(0f to P.oil, 1f to lerp(P.oil, Color.Black, 0.45f), startY = 584f, endY = 610f), Offset(EL.BLOCK_L, 584f), Size(EL.BLOCK_R - EL.BLOCK_L, 30f))
            drawLine(Color.White.a(0.55f), Offset(EL.BLOCK_L + 60, 585.5f), Offset(EL.BLOCK_R - 170, 585.5f), strokeWidth = 1.3f)
            drawLine(Color.White.a(0.22f), Offset(EL.BLOCK_L + 120, 590f), Offset(EL.BLOCK_R - 300, 590f), strokeWidth = 1f)
        }
    }

    // Cylinder head: valve galleries, plug wells and pent-roof chambers
    for (i in 0 until 4) {
        val x = EL.CYL_X[i]
        cavity(rectPath(x - 48, EL.HEAD_TOP + 4, x + 48, EL.DECK - 18, 6f), P)
        cavity(Path().apply { moveTo(x - halfB, EL.DECK + 1); lineTo(x - halfB + 6, EL.DECK - 12); lineTo(x + halfB - 6, EL.DECK - 12); lineTo(x + halfB, EL.DECK + 1); close() }, P)
        // Valve seats (bronze inserts)
        for (sx in listOf(-1f, 1f)) drawRect(P.bronze, Offset(x + sx * EL.VALVE_DX - 13, EL.DECK - 22), Size(26f, 4f))
        cutPlain(rectPath(x - 8, EL.HEAD_TOP + 2, x + 8, EL.DECK - 12, 2f), P, 4f)
        // Spark plug: white ceramic, a steel terminal and shell
        drawRect(P.white, Offset(x - 4, EL.HEAD_TOP + 6), Size(8f, 50f)); drawRect(P.line, Offset(x - 4, EL.HEAD_TOP + 6), Size(8f, 50f), style = OUTLINE_FINE)
        drawRect(Color.Black.a(0.18f), Offset(x + 1, EL.HEAD_TOP + 6), Size(3f, 50f))
        drawRect(P.metalDark, Offset(x - 4, EL.HEAD_TOP + 2), Size(8f, 8f))
        drawRect(P.metal, Offset(x - 5, EL.HEAD_TOP + 56), Size(10f, EL.DECK - 12 - EL.HEAD_TOP - 56))
        drawRect(P.line, Offset(x - 5, EL.HEAD_TOP + 56), Size(10f, EL.DECK - 12 - EL.HEAD_TOP - 56), style = OUTLINE_FINE)
        if (gdi) {
            // Lv.4: a direct injector reaching into the chamber (gold body, steel tip)
            val a0 = Offset(x - halfB - 3, EL.DECK - 42); val a1 = Offset(x - halfB + 13, EL.DECK - 10)
            drawLine(P.line, a0, a1, strokeWidth = 9f, cap = StrokeCap.Butt)
            drawLine(P.gold, a0, a1, strokeWidth = 6.6f, cap = StrokeCap.Butt)
            drawLine(Color.White.a(0.35f), Offset(a0.x - 1.2f, a0.y + 0.6f), Offset(a1.x - 1.2f, a1.y + 0.6f), strokeWidth = 1.2f)
            drawLine(P.metalLight, Offset(a1.x - 3f, a1.y - 6f), a1, strokeWidth = 3.4f, cap = StrokeCap.Butt)
        } else {
            // Port injector
            val a0 = Offset(x - halfB + 2, EL.DECK - 30); val a1 = Offset(x - halfB + 14, EL.DECK - 10)
            drawLine(P.line, a0, a1, strokeWidth = 6.6f, cap = StrokeCap.Butt)
            drawLine(P.metal, a0, a1, strokeWidth = 4.4f, cap = StrokeCap.Butt)
        }
        // Lv.1: rocker shafts either side of the single cam
        if (!dohc) for (sx in listOf(-1f, 1f)) disc(Offset(x + sx * 15f, EL.CAM_Y + 7f), 3.4f, P, P.metalDark)
    }
    drawLine(P.metalDark, Offset(EL.BLOCK_L, EL.CAM_Y), Offset(EL.BLOCK_R, EL.CAM_Y), strokeWidth = 4f)
    drawLine(Color.White.a(0.2f), Offset(EL.BLOCK_L, EL.CAM_Y - 1f), Offset(EL.BLOCK_R, EL.CAM_Y - 1f), strokeWidth = 1f)

    // Intake runners into the head
    if (!race) for (i in 0 until 4) {
        val x = EL.CYL_X[i] - EL.VALVE_DX
        cast(rectPath(x - 9f, EL.PLENUM_B - 3f, x + 9f, EL.COVER_TOP + 3f, 2f), P, P.cast, 31 + i)
    }

    // Cam cover (not cut) with the maker's mark: cast aluminium, carbon fibre at Lv.5
    val cover = rectPath(EL.BLOCK_L + 10, EL.COVER_TOP, EL.BLOCK_R - 10, EL.HEAD_TOP + 4, 10f)
    if (race) carbon(cover, P) else cast(cover, P, lerp(P.cast, P.metalLight, 0.45f), 11)
    for (i in 0 until 4) disc(Offset(EL.CYL_X[i] + 52f, EL.COVER_TOP + 11), 4.2f, P, P.metalDark)
    label(text, IceStep.MARKS[model], Offset((EL.BLOCK_L + EL.BLOCK_R) / 2, EL.COVER_TOP + 20f), 15f, if (race) P.metalLight else P.line.a(0.85f), center = true)
    // Lv.4: the high-pressure fuel pump on the cover
    if (gdi) {
        smooth(rectPath(EL.BLOCK_R - 62f, EL.COVER_TOP - 17f, EL.BLOCK_R - 34f, EL.COVER_TOP + 2f, 4f), P, P.gold)
        drawLine(P.metalLight, Offset(EL.BLOCK_R - 48f, EL.COVER_TOP - 17f), Offset(EL.BLOCK_R - 48f, EL.COVER_TOP - 23f), strokeWidth = 4f)
    }

    // Intake: plenum and throttle body, or (Lv.5) individual throttle trumpets in a carbon airbox
    if (race) {
        val box = rectPath(EL.PLENUM_L - 16f, 14f, EL.PLENUM_R + 8f, EL.COVER_TOP - 4f, 10f)
        carbon(box, P)
        cavity(rectPath(EL.PLENUM_L - 6f, 20f, EL.PLENUM_R - 2f, EL.COVER_TOP - 9f, 6f), P)
        for (i in 0 until 4) {
            val cx = EL.CYL_X[i] - EL.VALVE_DX
            val horn = Path().apply {
                moveTo(cx - 21f, 25f); quadraticTo(cx - 11f, 31f, cx - 10f, 44f); lineTo(cx - 10f, EL.COVER_TOP + 3f)
                lineTo(cx + 10f, EL.COVER_TOP + 3f); lineTo(cx + 10f, 44f); quadraticTo(cx + 11f, 31f, cx + 21f, 25f); close()
            }
            drawPath(horn, P.metalLight)
            drawPath(horn, Brush.horizontalGradient(0f to Color.Black.a(0.35f), 0.3f to Color.White.a(0.3f), 0.6f to Color.Transparent, 1f to Color.Black.a(0.4f), startX = cx - 21f, endX = cx + 21f))
            drawPath(horn, P.line, style = OUTLINE)
        }
    } else {
        val plenum = rectPath(EL.PLENUM_L, EL.PLENUM_T, EL.PLENUM_R, EL.PLENUM_B, 8f)
        cast(plenum, P, P.cast, 17)
        clipPath(plenum) { var x = EL.PLENUM_L + 40f; while (x < EL.PLENUM_R - 20f) { drawLine(P.line.a(0.22f), Offset(x, EL.PLENUM_T), Offset(x, EL.PLENUM_B), strokeWidth = 1.2f); x += 38f } }
        cast(rectPath(928f, 24f, 986f, 68f, 4f), P, lerp(P.cast, P.metalLight, 0.5f), 19)
        drawCircle(P.cavity, 15f, EL.THROTTLE); drawCircle(P.line, 15f, EL.THROTTLE, style = OUTLINE_FINE)
    }
    if (turbo) {
        // Lv.3: the charge pipe from the compressor through the intercooler, with the blow-off valve
        pipe(floatArrayOf(if (race) EL.PLENUM_R + 6f else 985f, 46f, 1080f, 46f, 1080f, EL.TURBO.y - EL.TURBO_R + 2), 20f, P.metalLight, P)
        val coreP = rectPath(EL.IC_L, EL.IC_T, EL.IC_R, EL.IC_B, 2f)
        drawPath(coreP, P.metal)
        clipPath(coreP) {
            var y = EL.IC_T + 2f; while (y < EL.IC_B) { drawLine(P.line.a(0.55f), Offset(EL.IC_L, y), Offset(EL.IC_R, y), strokeWidth = 0.7f); y += 3.4f }
            for (t in 1..3) drawLine(P.metalLight, Offset(EL.IC_L + (EL.IC_R - EL.IC_L) * t / 4f, EL.IC_T), Offset(EL.IC_L + (EL.IC_R - EL.IC_L) * t / 4f, EL.IC_B), strokeWidth = 2f)
        }
        shade(coreP, 0.8f)
        drawPath(coreP, P.line, style = OUTLINE)
        cast(rectPath(EL.IC_L - 3f, EL.IC_T - 9f, EL.IC_R + 3f, EL.IC_T + 1f, 3f), P, P.cast, 23)
        cast(rectPath(EL.IC_L - 3f, EL.IC_B - 1f, EL.IC_R + 3f, EL.IC_B + 9f, 3f), P, P.cast, 24)
        smooth(rectPath(EL.BOV.x - 11f, EL.BOV.y - 12f, EL.BOV.x + 11f, EL.BOV.y + 12f, 4f), P, P.metalLight)
        smooth(rectPath(EL.BOV.x - 8f, EL.BOV.y - 18f, EL.BOV.x + 8f, EL.BOV.y - 11f, 2f), P, P.metalDark)
    } else {
        // Naturally aspirated: a moulded air box with its paper filter
        pipe(floatArrayOf(985f, 46f, EL.AIRBOX_L + 4f, 46f), 20f, P.rubber, P)
        smooth(rectPath(EL.AIRBOX_L, EL.AIRBOX_T, EL.AIRBOX_R, EL.AIRBOX_B, 6f), P, P.rubber, 0.8f)
        val win = rectPath(EL.AIRBOX_L + 16f, EL.AIRBOX_T + 9f, EL.AIRBOX_R - 12f, EL.AIRBOX_B - 9f, 3f)
        drawPath(win, P.cavity)
        clipPath(win) {
            var x = EL.AIRBOX_L + 18f
            while (x < EL.AIRBOX_R - 12f) { drawLine(P.ceramic, Offset(x, EL.AIRBOX_B - 10f), Offset(x + 4f, EL.AIRBOX_T + 10f), strokeWidth = 1.4f); drawLine(P.ceramic.a(0.6f), Offset(x + 4f, EL.AIRBOX_T + 10f), Offset(x + 8f, EL.AIRBOX_B - 10f), strokeWidth = 1.4f); x += 8f }
        }
        drawPath(win, P.line, style = OUTLINE_FINE)
    }

    // Exhaust: heat-tinted steel (wrapped in gold foil at Lv.5), the turbine housing from Lv.3, catalyst, tailpipe
    val hot = if (race) P.gold else P.exhaust
    val manifold = floatArrayOf(EL.BLOCK_R + 2, EL.EXH_Y, 1022f, EL.EXH_Y, 1040f, 206f)
    pipe(manifold, 22f, hot, P)
    if (race) drawPath(polyline(manifold), P.line.a(0.28f), style = Stroke(19f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7f))))
    if (turbo) {
        pipe(floatArrayOf(1080f, EL.TURBO.y + 40, 1080f, EL.CAT_T), 24f, P.exhaust, P)
        cutFace(ovalPath(EL.TURBO, EL.TURBO_R + 6), P, P.iron, 4f)
        cavity(ovalPath(EL.TURBO, 40f), P)
        if (race) {
            // The gold heat shield around the turbine
            val r = EL.TURBO_R + 17f
            val tl = Offset(EL.TURBO.x - r, EL.TURBO.y - r); val sz = Size(r * 2, r * 2)
            drawArc(P.line, 92f, 176f, false, tl, sz, style = Stroke(9f, cap = StrokeCap.Butt))
            drawArc(P.gold, 93f, 174f, false, tl, sz, style = Stroke(6.6f, cap = StrokeCap.Butt))
            drawArc(Color.White.a(0.3f), 150f, 70f, false, tl, sz, style = Stroke(1.4f))
        }
    } else {
        pipe(floatArrayOf(1040f, 206f, 1080f, 252f, 1080f, EL.CAT_T), 22f, P.exhaust, P)
    }
    pipe(floatArrayOf(1080f, EL.CAT_B, 1080f, 458f, 1272f, 458f), 16f, P.metal, P)
    cutFace(rectPath(EL.CAT_L, EL.CAT_T, EL.CAT_R, EL.CAT_B, 6f), P, P.metalLight, 4f)
    val brick = rectPath(EL.CAT_L + 8, EL.CAT_T + 10, EL.CAT_R - 8, EL.CAT_B - 10, 2f)
    drawPath(brick, P.ceramic)
    clipPath(brick) {
        var x = EL.CAT_L + 8; while (x < EL.CAT_R) { drawLine(P.line.a(0.4f), Offset(x, EL.CAT_T), Offset(x, EL.CAT_B), strokeWidth = 0.6f); x += 3.5f }
        var y = EL.CAT_T + 10; while (y < EL.CAT_B) { drawLine(P.line.a(0.25f), Offset(EL.CAT_L, y), Offset(EL.CAT_R, y), strokeWidth = 0.6f); y += 3.5f }
    }
    shade(brick, 0.7f)
    drawPath(brick, P.line, style = OUTLINE_FINE)

    // Gearbox: bellhousing and case (cut) with the inset cavity and clutch-pack slots
    cutFace(Path().apply { moveTo(EL.BLOCK_R - 4, EL.BLOCK_B + 10); lineTo(EL.GB_L + 18, EL.GB_T); lineTo(EL.GB_L + 18, EL.GB_B - 10); lineTo(EL.BLOCK_R - 4, EL.CASE_B - 4); close() }, P, P.cast)
    cutFace(rectPath(EL.GB_L, EL.GB_T, EL.GB_R, EL.GB_B, 8f), P, P.cast)
    cavity(rectPath(EL.GB_L + 14, EL.GB_T + 12, EL.GB_R - 14, EL.GB_B - 12, 5f), P)
    val slot = lerp(P.cavity, P.lineSoft, 0.45f)
    for (i in 0 until 9) {
        val x = EL.GB_L + 26 + i * 27f
        drawRect(slot, Offset(x, EL.GB_T + 26), Size(21f, 46f)); drawRect(P.lineSoft, Offset(x, EL.GB_T + 26), Size(21f, 46f), style = OUTLINE_FINE)
    }
    drawRect(slot, Offset(EL.GB_R - 120, EL.GB_B - 54), Size(52f, 30f)); drawRect(P.lineSoft, Offset(EL.GB_R - 120, EL.GB_B - 54), Size(52f, 30f), style = OUTLINE_FINE)

    fuseBox(P)
}

internal val GEAR_TILES = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "R")

// ---------------------------------------------------------------- VE-M (EV)

internal fun DrawScope.staticEv(text: CalibreText, P: EngineInk, model: Int) {
    val jacket = EvStep.oilJacket(model); val hairpin = EvStep.hairpin(model); val dual = EvStep.dual(model); val top = EvStep.carbonRotor(model)

    floorShadow(EL.RAD_L - 10f, EL.BAT_R + 6f, 630f)
    radiator(P)

    // Battery pack (cut) with cell slots
    cutFace(rectPath(EL.BAT_L, EL.BAT_T, EL.BAT_R, EL.BAT_B, 6f), P, P.cast)
    val cw = (EL.BAT_R - EL.BAT_L - 24) / EL.CELLS
    for (i in 0 until EL.CELLS) cavity(rectPath(EL.BAT_L + 12 + i * cw + 2, EL.BAT_T + 10, EL.BAT_L + 12 + (i + 1) * cw - 2, EL.BAT_B - 10, 2f), P)

    // Reduction gear case (cut) with cavity
    cutFace(rectPath(EL.RED_L, EL.RED_T, EL.RED_R, EL.RED_B, 10f), P, P.cast)
    cavity(rectPath(EL.RED_L + 14, EL.RED_T + 14, EL.RED_R - 14, EL.RED_B - 14, 8f), P)

    // Motor housing (cut); Lv.2: the oil cooling jacket, amber channels between the housing and the stator
    cutFace(ovalPath(EL.MOTOR, EL.HOUSING_R), P, P.cast, 5f)
    if (jacket) {
        val r = EL.HOUSING_R - 9f
        drawCircle(P.line, r, EL.MOTOR, style = Stroke(10f))
        drawCircle(P.cavity, r, EL.MOTOR, style = Stroke(7.6f))
        drawCircle(P.oil, r, EL.MOTOR, style = Stroke(7.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 6f))))
        drawCircle(Color.White.a(0.3f), r - 1.6f, EL.MOTOR, style = Stroke(1.1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 6f))))
    }
    // Stator core (laminations) with its slots
    val core = Path().apply { addOval(Rect(EL.MOTOR, EL.STATOR_R)); addOval(Rect(EL.MOTOR, EL.STATOR_IN)); fillType = PathFillType.EvenOdd }
    drawPath(core, P.metal)
    var r = EL.STATOR_IN + 6
    while (r < EL.STATOR_R) { drawCircle(P.line.a(0.28f), r, EL.MOTOR, style = Stroke(0.6f)); r += 6f }
    shade(core, 0.9f)
    for (s in 0 until 48) {
        val deg = s / 48f * 360f
        if (hairpin) {
            // Lv.3: rectangular slots for the hairpin bars
            rotate(deg, EL.MOTOR) {
                val p = rectPath(EL.MOTOR.x + EL.STATOR_IN + 7f, EL.MOTOR.y - 5.6f, EL.MOTOR.x + EL.STATOR_IN + 53f, EL.MOTOR.y + 5.6f, 1.5f)
                drawPath(p, P.cavity); drawPath(p, P.line, style = OUTLINE_FINE)
            }
        } else {
            val a = deg * PI.toFloat() / 180f
            val c = Offset(EL.MOTOR.x + cos(a) * (EL.STATOR_IN + 30), EL.MOTOR.y + sin(a) * (EL.STATOR_IN + 30))
            drawCircle(P.cavity, 8.5f, c); drawCircle(P.line, 8.5f, c, style = OUTLINE_FINE)
        }
    }
    drawPath(core, P.line, style = OUTLINE)
    drawCircle(P.cavity, EL.STATOR_IN - 1, EL.MOTOR)
    drawCircle(P.line, EL.STATOR_IN - 1, EL.MOTOR, style = OUTLINE_FINE)
    // Lv.3: the welded crown of the hairpin ends (copper)
    if (hairpin) {
        drawCircle(P.line, EL.STATOR_IN + 1.5f, EL.MOTOR, style = Stroke(6.4f))
        drawCircle(P.copper, EL.STATOR_IN + 1.5f, EL.MOTOR, style = Stroke(4.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 2.5f))))
    }
    // Shaft to the reduction gears
    val shaft = rectPath(EL.MOTOR.x, EL.MOTOR.y - 10, EL.PINION.x, EL.MOTOR.y + 10)
    drawPath(shaft, P.metal)
    drawPath(shaft, Brush.verticalGradient(0f to Color.Black.a(0.3f), 0.3f to Color.White.a(0.3f), 0.6f to Color.Transparent, 1f to Color.Black.a(0.4f), startY = EL.MOTOR.y - 10, endY = EL.MOTOR.y + 10))
    drawPath(shaft, P.line, style = OUTLINE_FINE)

    // Inverter: a finned aluminium case, three power modules and the phasor window
    cast(rectPath(EL.INV_L, EL.INV_T, EL.INV_R, EL.INV_B, 6f), P, P.cast, 41)
    var fx = EL.INV_L + 14f
    while (fx < EL.INV_R - 10f) { drawLine(P.line.a(0.35f), Offset(fx, EL.INV_T + 4f), Offset(fx, EL.INV_T + 16f), strokeWidth = 1.6f); fx += 7f }
    drawCircle(P.panel, 56f, EL.INV_DIAL); drawCircle(P.line, 56f, EL.INV_DIAL, style = OUTLINE)
    drawCircle(P.metalDark, 57.5f, EL.INV_DIAL, style = Stroke(2.2f))
    for (k in 0 until 3) {
        val a = k * 2f * PI.toFloat() / 3f - PI.toFloat() / 2
        drawLine(P.textSoft.a(0.35f), EL.INV_DIAL, Offset(EL.INV_DIAL.x + cos(a) * 56, EL.INV_DIAL.y + sin(a) * 56), strokeWidth = 0.7f)
    }
    for (k in 0 until 3) smooth(rectPath(EL.INV_L + 20, EL.INV_T + 24 + k * 34f, EL.INV_L + 90, EL.INV_T + 46 + k * 34f, 2f), P, P.rubber, 0.8f)
    label(text, EvStep.MARKS[model], Offset(EL.INV_L + 18, EL.INV_B - 30), 15f, P.line.a(0.85f))

    // High-voltage cables (orange; flow pulses are drawn per frame). Lv.5: silver-plated busbars to the motor
    pipe(EL.HV_BAT_INV, 9f, P.hv, P)
    if (top) {
        for (dy in floatArrayOf(-6.5f, 0f, 6.5f)) {
            val pts = EL.HV_INV_MOTOR.copyOf(); var i = 1; while (i < pts.size) { pts[i] += dy; i += 2 }
            pipe(pts, 4f, P.white, P)
        }
    } else pipe(EL.HV_INV_MOTOR, 9f, P.hv, P)
    pipe(EL.HV_COMP, 7f, P.hv, P)
    cutFace(ovalPath(EL.ECOMP, EL.ECOMP_R), P, P.cast, 4f)
    cavity(ovalPath(EL.ECOMP, EL.ECOMP_R - 12), P)
    smooth(rectPath(EL.BAT_R - 46, EL.BAT_T - 30, EL.BAT_R - 6, EL.BAT_T + 4, 3f), P, P.rubber, 0.8f)
    drawCircle(P.hv, 6f, Offset(EL.BAT_R - 26, EL.BAT_T - 14))

    // Lv.4: the rear motor
    if (dual) {
        cutFace(ovalPath(EL.MOTOR2, EL.MOTOR2_R), P, P.cast, 4f)
        val core2 = Path().apply { addOval(Rect(EL.MOTOR2, EL.MOTOR2_R - 8f)); addOval(Rect(EL.MOTOR2, EL.MOTOR2_R - 32f)); fillType = PathFillType.EvenOdd }
        drawPath(core2, P.metal); shade(core2, 0.9f)
        for (s in 0 until 24) {
            val a = s / 24f * 2f * PI.toFloat()
            val c = Offset(EL.MOTOR2.x + cos(a) * (EL.MOTOR2_R - 20f), EL.MOTOR2.y + sin(a) * (EL.MOTOR2_R - 20f))
            drawCircle(P.cavity, 5.2f, c); drawCircle(P.copper, 3.6f, c)
        }
        drawPath(core2, P.line, style = OUTLINE)
        drawCircle(P.cavity, EL.MOTOR2_R - 33f, EL.MOTOR2); drawCircle(P.line, EL.MOTOR2_R - 33f, EL.MOTOR2, style = OUTLINE_FINE)
    }

    fuseBox(P)
}

/** Gear outline path (design units) for the reduction gears and the reverse idler. */
internal fun gearPath(c: Offset, r: Float, teeth: Int): Path = gearOutline(c, r, teeth, max(3f, r * 0.12f))
