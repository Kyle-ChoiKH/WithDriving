package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import io.vroomi.character.calibre.CalibreGeometry
import io.vroomi.character.calibre.CalibreGeometry.Pivot
import io.vroomi.character.calibre.CalibreGeometry.Wheel
import io.vroomi.character.calibre.CalibreTone
import io.vroomi.character.render.hash01
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Watch case geometry in units of the movement radius (caseback and dial views share it). */
internal object CW {
    const val CASE_OUT = 1.20f
    const val BEZEL_IN = 1.075f
    const val GASKET = 1.035f
    const val LUG_TIP = 1.47f
    const val HALF = 1.52f          // half size of the case bitmaps
    /** The lume ring in the case-back flange, between the gasket and the bezel. */
    const val LUME_RING = 1.055f
}

/** A small part rendered once, drawn every frame rotated about its own centre. */
internal class Part(val bmp: ImageBitmap, val half: Float)

/**
 * Every static layer of one finish, rendered once into bitmaps at movement radius [u] px and
 * re-rendered only when the key (size, tone, light hour) changes. Dial layers are made on demand.
 */
internal class CalibreLayers(
    val u: Float,
    val tone: CalibreTone,
    private val density: Density,
    private val tm: TextMeasurer,
) {
    val pal = CalPal.of(tone)
    /** One soft key light, upper left, for every finish (a studio, not a room). */
    val light = KeyLight.STUDIO
    val half = ceil(CW.HALF * u)
    val c = Offset(half, half)

    val base: ImageBitmap = render(half) { drawBase() }
    val bridges: ImageBitmap = render(half) { drawBridges() }
    val cock: ImageBitmap = render(half) { drawCock() }
    val rotor: Part = part(CalibreGeometry.ROTOR_R + 0.02f) { o, r -> drawRotor(o) }
    val wheels: Map<Wheel, Part> = Wheel.entries.associateWith { w -> part(w.pivot.r + 0.01f) { o, _ -> drawWheel(o, w) } }
    val escape: Part = part(CalibreGeometry.ESCAPE.r + 0.01f) { o, _ -> drawEscape(o) }
    val ratchet: Part = part(CalibreGeometry.RATCHET.r + 0.01f) { o, _ -> drawRatchet(o) }
    val crownWheel: Part = part(CalibreGeometry.CROWN_WHEEL.r + 0.01f) { o, _ -> drawCrownWheel(o) }
    val column: Part = part(CalibreGeometry.COLUMN.r + 0.01f) { o, _ -> drawColumnWheel(o) }

    /** Rotor outline around (0,0), heavy segment toward +x (for its shadow and sheen). */
    val rotorPath: Path = rotorShape(Offset.Zero, u)

    /** Levers, fork, click... pre-built once per size (no per-frame path building). */
    val parts: MovingParts by lazy { MovingParts(this) }
    val hands: DialHands by lazy { DialHands(u) }
    val crystal: Path by lazy { circlePath(c, CW.GASKET * u) }
    val lumeRingStroke by lazy { Stroke(width = u * 0.012f) }
    val lumeHaloStroke by lazy { Stroke(width = u * 0.05f) }
    val moonClip: Path by lazy {
        val y = c.y + (DG.MOON.y + DG.MOON_H / 2) * u
        val dx = DG.MOON_W * 0.5f * u
        subtract(moonAperture(c, u), union(circlePath(Offset(c.x - dx, y), DG.MOON_H * 0.52f * u), circlePath(Offset(c.x + dx, y), DG.MOON_H * 0.52f * u)))
    }

    /** Anisotropic streak of the circular-grained rotor: along the light, fixed to the screen. */
    val rotorSheen: Brush by lazy {
        val px = -light.y; val py = light.x
        Brush.linearGradient(
            0f to Color.Transparent, 0.40f to Color.Transparent, 0.5f to Color.White.a(0.38f), 0.60f to Color.Transparent, 1f to Color.Transparent,
            start = Offset(-px * u * 0.55f, -py * u * 0.55f), end = Offset(px * u * 0.55f, py * u * 0.55f),
        )
    }

    private var dialCache: ImageBitmap? = null
    val dial: ImageBitmap get() = dialCache ?: render(half) { drawDialBase(this@CalibreLayers) }.also { dialCache = it }

    fun p(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)
    fun p(v: Pivot) = p(v.x, v.y)

    // ------------------------------------------------------------------ helpers

    private fun render(halfPx: Float, block: DrawScope.() -> Unit): ImageBitmap {
        val n = (halfPx * 2).toInt().coerceAtLeast(2)
        val bmp = ImageBitmap(n, n)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bmp), Size(n.toFloat(), n.toFloat()), block)
        return bmp
    }

    private fun part(rUnits: Float, block: DrawScope.(Offset, Float) -> Unit): Part {
        val h = ceil(rUnits * u + 2f)
        val bmp = render(h) { block(Offset(h, h), rUnits * u) }
        return Part(bmp, h)
    }

    internal fun engraving(text: String, sizeU: Float, weight: FontWeight = FontWeight.Medium, spacing: Float = 0.12f, serif: Boolean = true, smallCaps: Boolean = false) =
        tm.measure(if (smallCaps) smallCaps(text, sizeU * u) else AnnotatedString(text), TextStyle(
            fontSize = with(density) { (sizeU * u).toSp() }, fontWeight = weight, letterSpacing = spacing.em,
            fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
        ))

    /** Small capitals: the first letter of each word full size, the rest at 78 % (capitals only, no lower case). */
    private fun smallCaps(text: String, px: Float): AnnotatedString = buildAnnotatedString {
        var start = true
        for (ch in text.uppercase()) {
            if (ch == ' ' || ch == '·' || ch == '-') { append(ch); start = true; continue }
            if (start || !ch.isLetter()) append(ch)
            else withStyle(SpanStyle(fontSize = with(density) { (px * 0.78f).toSp() })) { append(ch) }
            start = false
        }
    }

    /** Engraving centred at [at], rotated to [deg]; a dark lower edge sells the cut. */
    internal fun DrawScope.engrave(text: String, at: Offset, deg: Float, sizeU: Float, color: Color = pal.engrave, serif: Boolean = true, smallCaps: Boolean = false, spacing: Float = 0.12f) {
        val l = engraving(text, sizeU, serif = serif, smallCaps = smallCaps, spacing = spacing)
        rotate(deg, at) {
            val tl = Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f)
            drawText(l, color = Color.Black.a(0.55f), topLeft = tl + Offset(0.8f, 1.1f))
            drawText(l, color = color, topLeft = tl)
        }
    }

    // ------------------------------------------------------------------ base: lugs, case back, plate

    private fun lugPath(sx: Float, sy: Float): Path = Path().apply {
        fun pt(x: Float, y: Float) = Offset(c.x + sx * x * u, c.y + sy * y * u)
        val a = pt(0.50f, 0.98f); val b = pt(0.86f, 0.70f)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        // Outer flank sweeps down and in to a narrow tip
        val c1 = pt(0.88f, 1.02f); val c2 = pt(0.78f, 1.26f); val t1 = pt(0.74f, CW.LUG_TIP)
        cubicTo(c1.x, c1.y, c2.x, c2.y, t1.x, t1.y)
        val t2 = pt(0.58f, CW.LUG_TIP)
        val tc = pt(0.66f, CW.LUG_TIP + 0.022f)
        quadraticTo(tc.x, tc.y, t2.x, t2.y)
        val c3 = pt(0.55f, 1.24f)
        quadraticTo(c3.x, c3.y, a.x, a.y)
        close()
    }

    internal fun DrawScope.drawLugs(m: Metal) {
        for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
            val lp = lugPath(sx, sy)
            softShadow(lp, -light.x * u * 0.03f, -light.y * u * 0.03f, 0.4f)
            drawPath(lp, metalBrush(m, c, u * 1.4f, light, 0.9f))
            clipPath(lp) {
                // Brushed along the lug, with a polished facet on the outer flank
                var x = 0.45f
                while (x < 0.95f) {
                    val xx = c.x + sx * x * u
                    drawLine(if (((x * 400).toInt()) % 2 == 0) Color.White.a(0.05f) else Color.Black.a(0.05f), Offset(xx, c.y + sy * 0.6f * u), Offset(xx, c.y + sy * 1.5f * u), 1f)
                    x += 0.006f
                }
                drawPath(Path().apply {
                    val f0 = Offset(c.x + sx * 0.80f * u, c.y + sy * 0.80f * u); val f1 = Offset(c.x + sx * 0.70f * u, c.y + sy * CW.LUG_TIP * u)
                    moveTo(f0.x, f0.y); lineTo(c.x + sx * 0.92f * u, c.y + sy * 0.80f * u); lineTo(c.x + sx * 0.78f * u, c.y + sy * CW.LUG_TIP * u); lineTo(f1.x, f1.y); close()
                }, Brush.linearGradient(listOf(m.light.a(0.55f), m.base.a(0.1f)), start = Offset(c.x, c.y + sy * 0.8f * u), end = Offset(c.x, c.y + sy * 1.5f * u)))
            }
            bevel(lp, c, u * 1.4f, light, (u * 0.008f).coerceAtLeast(1.2f))
        }
    }

    private fun DrawScope.drawBase() {
        val m = pal.case
        drawLugs(m)
        // Case back: polished bezel with eight screws, a gasket, then the sapphire window
        softShadow(circlePath(c, CW.CASE_OUT * u), -light.x * u * 0.05f, -light.y * u * 0.05f, 0.45f)
        drawCircle(metalBrush(m, c, CW.CASE_OUT * u, light), CW.CASE_OUT * u, c)
        rotate(light.deg + 45f, c) { drawCircle(turnedBrush(m, c), CW.CASE_OUT * u * 0.995f, c, alpha = 0.35f) }
        drawCircle(Color.Black.a(0.35f), CW.BEZEL_IN * u, c, style = Stroke(width = u * 0.008f))
        drawCircle(lerp(m.dark, Color.Black, 0.3f), CW.BEZEL_IN * u, c)
        drawCircle(Color(0xFF16171A), CW.GASKET * u, c)
        // A fine lume ring set in the flange: pale by day, the only light at night.
        drawCircle(CalPal.lumeDay.a(0.8f), CW.LUME_RING * u, c, style = Stroke(width = u * 0.010f))
        for (i in 0 until 8) {
            val a = (i * 45f + 22.5f) * PI.toFloat() / 180f
            screw(pol(c, 1.14f * u, a), u * 0.026f, hash01(i, 5) * 180f, light)
        }
        // Case-back engraving between the screws (allowed words only)
        val words = listOf("CALIBRE VR-01", "28 800 A/h", "72H", "ADJUSTED TO YOUR DRIVE")
        val angles = listOf(-90f, 0f, 180f, 90f)
        val sizes = listOf(0.045f, 0.04f, 0.045f, 0.032f)
        words.forEachIndexed { i, w -> engraveArc(w, 1.135f, angles[i], sizes[i]) }

        // Main plate with perlage (circular graining)
        val plate = circlePath(c, u)
        clipPath(plate) {
            drawCircle(pal.plate.base, u, c)
            val sp = u * 0.042f
            var row = 0
            var y = c.y - u
            while (y < c.y + u + sp) {
                var x = c.x - u + if (row % 2 == 0) 0f else sp * 0.5f
                while (x < c.x + u + sp) {
                    val o = Offset(x, y)
                    drawCircle(
                        Brush.radialGradient(
                            0f to pal.plate.light.a(0.9f), 0.55f to pal.plate.base, 0.85f to pal.plate.dark.a(0.85f), 1f to pal.plate.base,
                            center = Offset(x + light.x * sp * 0.18f, y + light.y * sp * 0.18f), radius = sp * 0.62f,
                        ),
                        sp * 0.62f, o,
                    )
                    x += sp * 0.72f
                }
                y += sp * 0.62f; row++
            }
            // Sinks under the wheels and the barrel (depth)
            for (w in Wheel.entries) drawCircle(Color.Black.a(0.28f), (w.pivot.r + 0.02f) * u, p(w.pivot))
            drawCircle(Color.Black.a(0.35f), (CalibreGeometry.BARREL.r + 0.02f) * u, p(CalibreGeometry.BARREL))
            drawCircle(Color.Black.a(0.30f), (CalibreGeometry.BALANCE.r + 0.025f) * u, p(CalibreGeometry.BALANCE))
            drawCircle(Color.Black.a(0.25f), (CalibreGeometry.ESCAPE.r + 0.02f) * u, p(CalibreGeometry.ESCAPE))
            // Inner shadow cast by the case
            drawCircle(Brush.radialGradient(0.86f to Color.Transparent, 1f to Color.Black.a(0.55f), center = c, radius = u), u, c)
        }
        bevel(plate, c, u, light, u * 0.006f)
    }

    private fun DrawScope.engraveArc(text: String, rU: Float, centreDeg: Float, sizeU: Float) {
        // Read upright: text in the lower half runs the other way round.
        val flip = centreDeg in 1f..179f
        val step = 0.052f * u / (rU * u) * 180f / PI.toFloat() * (sizeU / 0.045f)
        val chars = text.toCharArray()
        val total = step * (chars.size - 1)
        chars.forEachIndexed { i, ch ->
            if (ch == ' ') return@forEachIndexed
            val a = if (flip) centreDeg + total / 2 - i * step else centreDeg - total / 2 + i * step
            val at = pol(c, rU * u, a * PI.toFloat() / 180f)
            engrave(ch.toString(), at, if (flip) a - 90f else a + 90f, sizeU, color = lerp(pal.case.dark, Color.Black, 0.2f), serif = true)
        }
    }

    // ------------------------------------------------------------------ bridges

    internal fun barrelBridge(): Path {
        val g = CalibreGeometry
        val shape = union(
            circlePath(p(g.BARREL), 0.355f * u),
            capsule(p(g.BARREL), p(-0.70f, -0.58f), 0.12f * u),
            capsule(p(g.BARREL), p(-0.82f, 0.16f), 0.11f * u),
            circlePath(p(-0.70f, -0.58f), 0.12f * u),
        )
        return intersect(subtract(shape, circlePath(p(g.BARREL), 0.278f * u)), circlePath(c, 0.992f * u))
    }

    internal fun trainBridge(): Path {
        val g = CalibreGeometry
        val w = 0.072f * u
        return intersect(union(
            capsule(p(g.THIRD), p(g.FOURTH), w), capsule(p(g.FOURTH), p(g.ESCAPE), w * 0.9f),
            capsule(p(g.THIRD), p(-0.62f, 0.56f), w), capsule(p(g.FOURTH), p(-0.28f, 0.90f), w),
            circlePath(p(g.THIRD), 0.07f * u), circlePath(p(g.FOURTH), 0.075f * u), circlePath(p(g.ESCAPE), 0.06f * u),
        ), circlePath(c, 0.992f * u))
    }

    internal fun chronoBridge(): Path {
        val g = CalibreGeometry
        val w = 0.066f * u
        return intersect(union(
            capsule(p(g.RUNNER), p(g.COUNTER), w), capsule(p(g.COUNTER), p(0.62f, -0.76f), w),
            capsule(p(g.RUNNER), p(-0.02f, -0.92f), w), capsule(p(g.RUNNER), p(0.33f, -0.38f), w * 0.85f),
            circlePath(p(g.RUNNER), 0.07f * u), circlePath(p(g.COUNTER), 0.06f * u),
        ), circlePath(c, 0.992f * u))
    }

    internal fun palletBridge(): Path {
        val g = CalibreGeometry
        return intersect(union(capsule(p(g.PALLET), p(0.30f, 0.88f), 0.042f * u), circlePath(p(g.PALLET), 0.05f * u)), circlePath(c, 0.992f * u))
    }

    private fun DrawScope.bridge(shape: Path, angleDeg: Float, m: Metal) {
        softShadow(shape, -light.x * u * 0.022f, -light.y * u * 0.022f, 0.42f)
        cotesDeGeneve(shape, c, u, m, angleDeg, u * 0.085f)
        bevel(shape, c, u, light, (u * 0.009f).coerceAtLeast(1.3f))
    }

    private fun DrawScope.drawBridges() {
        val g = CalibreGeometry
        val m = pal.bridge
        bridge(barrelBridge(), -32f, m)
        bridge(trainBridge(), -32f, m)
        bridge(chronoBridge(), -32f, m)
        bridge(palletBridge(), -32f, m)

        // Jewels set in the bridges (balance, column wheel and reversers are drawn with their parts)
        val sink = lerp3(pal.bridge)
        listOf(2, 3, 4, 5, 8, 9).forEach { i -> val j = g.JEWELS[i]; jewel(p(j.x, j.y), j.r * u, light, sink) }
        // Gold chatons held by two tiny screws
        for (i in 12 until g.JEWELS.size) {
            val j = g.JEWELS[i]; val o = p(j.x, j.y)
            drawCircle(metalBrush(CalPal.yellowGold, o, j.r * u * 2.6f, light), j.r * u * 2.5f, o)
            bevel(circlePath(o, j.r * u * 2.5f), o, j.r * u * 2.5f, light, 1f)
            jewel(o, j.r * u, light, CalPal.yellowGold)
            for (k in 0..1) screw(pol(o, j.r * u * 3.4f, (k * 180f + 40f + i * 20f) * PI.toFloat() / 180f), u * 0.016f, i * 37f, light)
        }
        // Bridge screws
        listOf(p(-0.70f, -0.58f) to 20f, p(-0.80f, 0.12f) to 75f, p(-0.62f, 0.56f) to 130f, p(-0.28f, 0.88f) to 15f,
            p(0.62f, -0.76f) to 160f, p(-0.02f, -0.90f) to 95f, p(0.30f, 0.86f) to 50f, p(-0.05f, -0.30f) to 140f,
        ).forEach { (o, a) -> screw(o, u * 0.03f, a, light) }

        // Engravings
        val arm1a = p(g.BARREL); val arm1b = p(-0.82f, 0.16f)
        engraveAlong("CALIBRE VR-01", arm1a, arm1b, 0.62f, 0.058f)
        val arm2a = p(g.BARREL); val arm2b = p(-0.70f, -0.58f)
        engraveAlong("AUTOMATIC", arm2a, arm2b, 0.62f, 0.042f)
        engraveAlong(CalibreGeometry.jewelEngraving(), p(g.THIRD), p(-0.62f, 0.56f), 0.62f, 0.042f)
        engraveAlong("28 800 A/h", p(g.RUNNER), p(-0.02f, -0.92f), 0.6f, 0.036f)
    }

    private fun DrawScope.engraveAlong(text: String, a: Offset, b: Offset, at: Float, sizeU: Float) {
        var deg = atan2(b.y - a.y, b.x - a.x) * 180f / PI.toFloat()
        if (deg > 90f) deg -= 180f
        if (deg < -90f) deg += 180f
        engrave(text, Offset(a.x + (b.x - a.x) * at, a.y + (b.y - a.y) * at), deg, sizeU)
    }

    private fun lerp3(m: Metal) = Metal(lerp(m.base, CalPal.rhodium.base, 0.5f), lerp(m.light, Color.White, 0.3f), m.dark)

    // ------------------------------------------------------------------ balance cock (above the balance)

    internal fun cockShape(): Path {
        val g = CalibreGeometry
        return intersect(union(
            circlePath(p(g.BALANCE), 0.072f * u),
            capsule(p(g.BALANCE), p(0.70f, 0.31f), 0.052f * u),
            capsule(p(0.70f, 0.31f), p(0.92f, 0.27f), 0.085f * u),
        ), circlePath(c, 0.992f * u))
    }

    private fun DrawScope.drawCock() {
        val g = CalibreGeometry
        val s = cockShape()
        softShadow(s, -light.x * u * 0.035f, -light.y * u * 0.035f, 0.5f)
        cotesDeGeneve(s, c, u, pal.bridge, 58f, u * 0.06f)
        bevel(s, c, u, light, (u * 0.009f).coerceAtLeast(1.3f))
        // Regulator index (black-polished steel) and its engraved scale
        val b = p(g.BALANCE)
        val tip = p(0.66f, 0.205f)
        drawLine(CalPal.blackSteel.dark, b, tip, strokeWidth = u * 0.022f, cap = StrokeCap.Round)
        drawLine(CalPal.blackSteel.light.a(0.8f), b + Offset(light.x, light.y), tip + Offset(light.x, light.y), strokeWidth = u * 0.006f, cap = StrokeCap.Round)
        engrave("−", p(0.62f, 0.165f), 0f, 0.05f)
        engrave("+", p(0.73f, 0.215f), 0f, 0.05f)
        screw(p(0.86f, 0.285f), u * 0.032f, 30f, light)
        jewel(b, g.JEWELS[6].r * u, light, lerp3(pal.bridge))
    }

    // ------------------------------------------------------------------ rotor (cached facing +x)

    private fun DrawScope.drawRotor(o: Offset) {
        val r = CalibreGeometry.ROTOR_R * u
        val s = rotorShape(o, u)
        val m = pal.rotor
        drawPath(s, Brush.radialGradient(0f to m.light, 0.35f to m.base, 0.8f to lerp(m.base, m.dark, 0.35f), 1f to m.base, center = o, radius = r))
        clipPath(s) {
            circularGrain(o, r * 0.14f, r, (u * 0.012f).coerceAtLeast(1.4f), 0.07f)
            // Heavy rim: a slightly darker, thicker band
            drawCircle(m.dark.a(0.25f), r * 0.90f, o, style = Stroke(width = r * 0.14f))
            drawCircle(m.light.a(0.5f), r * 0.828f, o, style = Stroke(width = (u * 0.006f).coerceAtLeast(1f)))
        }
        bevel(s, o, r, KeyLight(-PI.toFloat() / 2), (u * 0.008f).coerceAtLeast(1.2f))
        // Engraving around the rim, upright when the rotor hangs down
        val text = "CALIBRE VR-01 · AUTOMATIC"
        val step = 4.1f
        val total = step * (text.length - 1)
        text.forEachIndexed { i, ch ->
            if (ch == ' ') return@forEachIndexed
            val a = total / 2 - i * step
            val at = pol(o, r * 0.895f, a * PI.toFloat() / 180f)
            val l = engraving(ch.toString(), 0.05f)
            rotate(a - 90f, at) {
                val tl = Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f)
                drawText(l, color = m.dark.a(0.9f), topLeft = tl + Offset(0.6f, 0.8f))
                drawText(l, color = lerp(m.light, Color.White, 0.2f), topLeft = tl)
            }
        }
        // Fine engraved km scale along the outer edge (a marker steps one division per km)
        for (k in 0..30) {
            val a = (-78f + k * 5.2f) * PI.toFloat() / 180f
            val len = if (k % 5 == 0) 0.035f else 0.018f
            drawLine(m.dark.a(0.8f), pol(o, r * 0.985f, a), pol(o, r * (0.985f - len), a), strokeWidth = 1f)
        }
    }

    // ------------------------------------------------------------------ wheels

    private fun DrawScope.drawWheel(o: Offset, w: Wheel) {
        val r = w.pivot.r * u
        val m = pal.wheel
        val shape = evenOdd(gearOutline(o, r, w.teeth, (r * 2.4f / w.teeth * 1.1f).coerceAtLeast(1.2f)))
        addWindows(shape, o, r * 0.80f, r * 0.30f, w.spokes, 9f, 14f, w.ordinal * 17f)
        drawPath(shape, Brush.radialGradient(0f to m.light, 0.6f to m.base, 1f to lerp(m.base, m.dark, 0.4f), center = o, radius = r))
        clipPath(shape) { sunray(o, r, 120, 0.06f) }
        drawCircle(m.light.a(0.6f), r * 0.80f, o, style = Stroke(width = 1f))
        drawCircle(m.dark.a(0.7f), r * 0.86f, o, style = Stroke(width = 1f))
        // Hub and pinion
        drawCircle(lerp(m.base, m.dark, 0.3f), r * 0.30f, o)
        drawCircle(CalPal.blackSteel.base, r * 0.13f, o)
        if (w == Wheel.RUNNER_W || w == Wheel.COUNTER_W) heart(o, r * 0.26f)
    }

    /** Chronograph reset heart (black-polished). */
    private fun DrawScope.heart(o: Offset, s: Float) {
        val p = Path().apply {
            moveTo(o.x, o.y + s)
            cubicTo(o.x - s * 1.2f, o.y + s * 0.1f, o.x - s * 0.8f, o.y - s * 1.0f, o.x, o.y - s * 0.45f)
            cubicTo(o.x + s * 0.8f, o.y - s * 1.0f, o.x + s * 1.2f, o.y + s * 0.1f, o.x, o.y + s)
            close()
        }
        drawPath(p, CalPal.blackSteel.base)
        drawPath(p, Color.White.a(0.55f), style = Stroke(width = 0.8f))
    }

    private fun DrawScope.drawEscape(o: Offset) {
        val r = CalibreGeometry.ESCAPE.r * u
        val n = CalibreGeometry.ESCAPE_TEETH
        val shape = evenOdd(Path().apply {
            for (i in 0 until n) {
                val a = i * 2f * PI.toFloat() / n
                val step = 2f * PI.toFloat() / n
                val root = pol(o, r * 0.72f, a)
                val tip = pol(o, r, a + step * 0.35f)
                val club = pol(o, r * 0.96f, a + step * 0.55f)
                val back = pol(o, r * 0.74f, a + step * 0.62f)
                if (i == 0) moveTo(root.x, root.y) else lineTo(root.x, root.y)
                lineTo(tip.x, tip.y); lineTo(club.x, club.y); lineTo(back.x, back.y)
            }
            close()
        })
        addWindows(shape, o, r * 0.6f, r * 0.26f, 4, 10f, 0f)
        drawPath(shape, metalBrush(pal.wheel, o, r, light))
        drawCircle(CalPal.blackSteel.base, r * 0.14f, o)
    }

    private fun DrawScope.drawRatchet(o: Offset) {
        val r = CalibreGeometry.RATCHET.r * u
        val m = if (tone == CalibreTone.BLACK_DLC) CalPal.dlc else CalPal.rhodium
        val shape = evenOdd(gearOutline(o, r, CalibreGeometry.RATCHET_TEETH, r * 0.07f, ratchet = true))
        addWindows(shape, o, r * 0.80f, r * 0.36f, 5, 7f, 18f, 10f)
        drawPath(shape, Brush.radialGradient(0f to m.light, 0.7f to m.base, 1f to m.dark, center = o, radius = r))
        clipPath(shape) { sunray(o, r, 220, 0.09f) }
        drawCircle(m.light.a(0.7f), r * 0.80f, o, style = Stroke(width = 1.2f))
        drawCircle(m.dark.a(0.7f), r * 0.36f, o, style = Stroke(width = 1.2f))
        screw(o, r * 0.2f, 35f, light)
    }

    private fun DrawScope.drawCrownWheel(o: Offset) {
        val r = CalibreGeometry.CROWN_WHEEL.r * u
        val m = if (tone == CalibreTone.BLACK_DLC) CalPal.dlc else CalPal.rhodium
        val shape = evenOdd(gearOutline(o, r, CalibreGeometry.CROWN_WHEEL_TEETH, r * 0.14f))
        addWindows(shape, o, r * 0.72f, r * 0.40f, 3, 14f, 22f)
        drawPath(shape, Brush.radialGradient(0f to m.light, 0.7f to m.base, 1f to m.dark, center = o, radius = r))
        clipPath(shape) { sunray(o, r, 90, 0.09f) }
        screw(o, r * 0.32f, 110f, light)
    }

    private fun DrawScope.drawColumnWheel(o: Offset) {
        val r = CalibreGeometry.COLUMN.r * u
        val n = CalibreGeometry.COLUMNS
        drawPath(gearOutline(o, r, n * 3, r * 0.12f, ratchet = true), CalPal.blackSteel.dark)
        // Seven polished columns
        for (i in 0 until n) {
            val a = i * 2f * PI.toFloat() / n
            val pc = pol(o, r * 0.70f, a)
            drawCircle(Brush.radialGradient(listOf(Color.White, CalPal.rhodium.base, CalPal.rhodium.dark), pc + Offset(light.x, light.y) * (r * 0.06f), r * 0.24f), r * 0.2f, pc)
        }
        drawCircle(metalBrush(CalPal.rhodium, o, r * 0.5f, light), r * 0.45f, o)
        drawCircle(CalPal.ruby.base, r * 0.13f, o)
        drawCircle(Color.White.a(0.8f), r * 0.04f, o + Offset(light.x, light.y) * (r * 0.05f))
    }

    companion object {
        /** Half rotor: heavy segment toward +x, with an original turbine-blade skeleton. */
        fun rotorShape(o: Offset, u: Float): Path {
            val r = CalibreGeometry.ROTOR_R * u
            val hub = CalibreGeometry.BEARING_R * u * 1.12f
            val p = Path().apply {
                arcTo(Rect(o, r), -90f, 180f, true)
                lineTo(o.x, o.y + hub)
                arcTo(Rect(o, hub), 90f, 180f, false) // a ring around the bearing
                close()
            }
            // Three windows between blades that lean like a turbine (original pattern)
            val windows = Path()
            val tw = 12f
            for ((a0, a1) in listOf(-78f to -34f, -24f to 24f, 34f to 78f)) {
                windows.arcTo(Rect(o, r * 0.765f), a0, a1 - a0, true)
                windows.arcTo(Rect(o, r * 0.33f), a1 + tw, -(a1 - a0), false)
                windows.close()
            }
            return subtract(p, windows)
        }
    }
}
