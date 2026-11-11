package io.vroomi.character.render.aquarium

import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.aquarium.AquariumSim
import io.vroomi.character.render.hash01
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The aquarium's upgrade ladder (spec 5.4). Every step keeps what the one before had and adds its own:
 * 1 sand shoal (open sand, the six fish), 2 seagrass garden (the meadow, far kelp, a swarm of small shrimp),
 * 3 coral reef (a coral colony and two kinds of reef fish), 4 shipwreck (a sunken wooden boat, fish going in and
 * out of it, one octopus), 5 kelp forest (kelp up to the surface and a sea turtle passing slowly).
 */
internal object AquaStep {
    const val GRASS_ALL = 18
    /** How many of the seagrass clumps stand: a few on the open sand, the whole meadow from step 2. */
    fun grassClumps(model: Int): Int = if (model >= 1) GRASS_ALL else 5
    fun garden(model: Int): Boolean = model >= 1
    fun reef(model: Int): Boolean = model >= 2
    fun wreck(model: Int): Boolean = model >= 3
    fun kelpForest(model: Int): Boolean = model >= 4
}

/** Where things stand, from the painter's geometry: tank units to design units. */
internal class StepGeo(val cx: Float, val sx: Float, val sandY: Float, val surface: Float, val dw: Float) {
    fun ux(u: Float): Float = cx + (u - AquariumSim.W / 2f) * sx
    /** The hole in the wreck's side that fish go in and out of. */
    val hole: Offset get() = Offset(ux(WRECK_U) - 92f * WRECK_K, sandY + 22f - 36f * WRECK_K)
}

private const val WRECK_U = 410f
private const val WRECK_K = 1.32f
private val FOG = Color(0xFF1C8191)
private val SUN = Color(0xFFF4FFE8)
private val HAIR = Stroke(1f)
private fun fog(c: Color, k: Float) = lerp(c, FOG, k)

// ------------------------------------------------------------------ baked: the wreck and the reef

internal fun DrawScope.stepStatic(g: StepGeo, model: Int) {
    if (AquaStep.wreck(model)) wreck(g)
    if (AquaStep.reef(model)) reef(g)
}

private val WOOD = fog(Color(0xFF6E5236), 0.22f)
private val WOOD_LIT = fog(Color(0xFFA08560), 0.22f)
private val WOOD_DARK = fog(Color(0xFF2E2418), 0.25f)

/** A sunken wooden boat, lying a little on its side and half in the sand: broken planking, standing ribs, a mast stump. */
private fun DrawScope.wreck(g: StepGeo) {
    val ox = g.ux(WRECK_U); val oy = g.sandY + 22f
    drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent), Offset(ox + 12f, oy + 4f), 210f), Offset(ox - 210f, oy - 14f), Size(440f, 40f))
    withTransform({ translate(ox, oy); rotate(-5f, Offset.Zero); scale(WRECK_K, WRECK_K, Offset.Zero) }) {
        // The far side's ribs, seen through the break amidships.
        for (k in 0 until 6) {
            val x = -44f + k * 19f
            val rib = Path().apply { moveTo(x, -14f); quadraticTo(x - 5f, -52f, x + 3f, -84f - 4f * hash01(k, 3)) }
            drawPath(rib, WOOD_DARK, style = Stroke(5f, cap = StrokeCap.Round))
            drawPath(rib, WOOD_LIT.copy(alpha = 0.35f), style = Stroke(1.2f, cap = StrokeCap.Round))
        }
        // The hull: transom at the stern (left), the stem rising at the bow, the planking torn away amidships.
        val hull = Path().apply {
            moveTo(-196f, -82f); lineTo(-150f, -78f); lineTo(-96f, -74f); lineTo(-58f, -71f)
            lineTo(-50f, -52f); lineTo(-34f, -58f); lineTo(-22f, -36f); lineTo(-4f, -30f); lineTo(12f, -40f); lineTo(30f, -28f); lineTo(46f, -50f); lineTo(58f, -44f); lineTo(66f, -66f)
            lineTo(120f, -72f); quadraticTo(170f, -84f, 204f, -112f)
            quadraticTo(196f, -40f, 150f, 4f); lineTo(-176f, 8f); quadraticTo(-190f, -30f, -196f, -82f); close()
        }
        drawPath(hull, Brush.verticalGradient(listOf(WOOD_LIT, WOOD, WOOD_DARK), startY = -100f, endY = 10f))
        clipPath(hull) {
            // Strakes following the sheer, and the butts where planks meet.
            for (k in 1..6) {
                val y = -84f + k * 14f
                val p = Path().apply { moveTo(-200f, y); quadraticTo(0f, y + 10f, 204f, y - 22f) }
                drawPath(p, WOOD_DARK.copy(alpha = 0.45f), style = Stroke(1.3f))
                drawPath(p, WOOD_LIT.copy(alpha = 0.16f), style = Stroke(0.9f), alpha = 1f)
                for (j in 0 until 5) { val x = -180f + hash01(k * 7 + j, 5) * 370f; drawLine(WOOD_DARK.copy(alpha = 0.4f), Offset(x, y - 12f), Offset(x, y), 1f) }
            }
            // The hole in the side: dark inside, a broken edge.
            val hole = Path().apply { moveTo(-118f, -48f); lineTo(-98f, -56f); lineTo(-74f, -50f); lineTo(-62f, -34f); lineTo(-72f, -18f); lineTo(-100f, -14f); lineTo(-122f, -26f); close() }
            drawPath(hole, Color(0xFF04141A).copy(alpha = 0.88f))
            drawPath(hole, WOOD_LIT.copy(alpha = 0.4f), style = Stroke(1.4f, join = StrokeJoin.Round))
            // Growth on the wood: soft low-contrast patches, denser toward the sand.
            for (k in 0 until 46) {
                val x = -190f + hash01(k, 61) * 380f; val y = -78f + hash01(k, 62) * 84f
                drawCircle(lerp(Color(0xFF5E8F6A), Color(0xFFA9B77A), hash01(k, 63)).copy(alpha = 0.10f + 0.22f * ((y + 78f) / 84f)), 2f + hash01(k, 64) * 5f, Offset(x, y))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, FOG.copy(alpha = 0.22f)), startY = -40f, endY = 10f), Offset(-200f, -40f), Size(410f, 52f))
        }
        // The gunwale's lit edge, the stem post and the rudder post.
        drawLine(WOOD_LIT, Offset(-196f, -82f), Offset(-58f, -71f), 3.4f, cap = StrokeCap.Round)
        val sheer = Path().apply { moveTo(66f, -66f); lineTo(120f, -72f); quadraticTo(170f, -84f, 204f, -112f) }
        drawPath(sheer, WOOD_LIT, style = Stroke(3.4f, cap = StrokeCap.Round))
        drawLine(WOOD_DARK, Offset(204f, -112f), Offset(210f, -128f), 6f, cap = StrokeCap.Round)
        drawLine(WOOD_DARK, Offset(-198f, -84f), Offset(-194f, -4f), 5f, cap = StrokeCap.Round)
        // The mast: a broken stump leaning aft, and its fallen top across the bow.
        drawLine(WOOD_DARK, Offset(84f, -68f), Offset(70f, -150f), 10f, cap = StrokeCap.Round)
        drawLine(WOOD, Offset(83f, -68f), Offset(69f, -150f), 6.4f, cap = StrokeCap.Round)
        drawLine(WOOD_LIT.copy(alpha = 0.6f), Offset(80f, -70f), Offset(67f, -148f), 1.4f)
        drawLine(WOOD_DARK, Offset(96f, -74f), Offset(238f, -18f), 7f, cap = StrokeCap.Round)
        drawLine(WOOD, Offset(96f, -75f), Offset(238f, -19f), 4f, cap = StrokeCap.Round)
        // Sand drifted against the keel.
        withTransform({ translate(6f, 8f); scale(1f, 0.085f, Offset.Zero) }) {
            drawCircle(Brush.radialGradient(0f to Color(0xFFCDBB8E).copy(alpha = 0.9f), 0.6f to Color(0xFFCDBB8E).copy(alpha = 0.55f), 1f to Color(0xFFCDBB8E).copy(alpha = 0f), center = Offset.Zero, radius = 230f), 230f, Offset.Zero)
        }
    }
}

private fun DrawScope.branch(x: Float, y: Float, ang: Float, len: Float, w: Float, depth: Int, seed: Int, col: Color, tip: Color) {
    val nx = x + cos(ang) * len; val ny = y + sin(ang) * len
    drawLine(col, Offset(x, y), Offset(nx, ny), w, cap = StrokeCap.Round)
    drawLine(tip.copy(alpha = 0.35f), Offset(x - w * 0.22f, y - w * 0.1f), Offset(nx - w * 0.22f, ny - w * 0.1f), w * 0.28f, cap = StrokeCap.Round)
    if (depth == 0) { drawCircle(tip, w * 0.62f, Offset(nx, ny)); return }
    val spread = 0.34f + 0.22f * hash01(seed, depth)
    branch(nx, ny, ang - spread, len * 0.76f, w * 0.74f, depth - 1, seed * 2 + 1, col, tip)
    branch(nx, ny, ang + spread * 0.9f, len * 0.70f, w * 0.74f, depth - 1, seed * 2 + 2, col, tip)
}

/** Branching coral: a few trunks from one foot. */
private fun DrawScope.staghorn(x: Float, y: Float, size: Float, seed: Int, col: Color) {
    val tip = lerp(col, Color.White, 0.55f)
    for (k in 0 until 3) branch(x + (k - 1) * size * 0.16f, y, (-PI / 2).toFloat() + (k - 1) * 0.42f, size * 0.34f, size * 0.10f, 3, seed + k * 17, col, tip)
}

/** A brain coral: a low dome, lit from above, with faint meanders. */
private fun DrawScope.brain(x: Float, y: Float, r: Float, col: Color) {
    val dome = Path().apply { moveTo(x - r, y); cubicTo(x - r, y - r * 1.15f, x + r, y - r * 1.15f, x + r, y); close() }
    drawPath(dome, Brush.verticalGradient(listOf(lerp(col, Color.White, 0.35f), col, lerp(col, Color.Black, 0.45f)), startY = y - r * 0.9f, endY = y))
    clipPath(dome) {
        for (k in 0 until 7) {
            val yy = y - r * 0.12f - k * r * 0.115f
            val p = Path().apply {
                moveTo(x - r, yy)
                var xx = x - r
                var j = 0
                while (xx < x + r) { quadraticTo(xx + r * 0.08f, yy + (if (j % 2 == 0) -1f else 1f) * r * 0.07f, xx + r * 0.16f, yy); xx += r * 0.16f; j++ }
            }
            drawPath(p, Color.Black.copy(alpha = 0.16f), style = Stroke(1.2f))
        }
    }
}

/** A mound of lobed coral: overlapping rounded heads, each lit from above. */
private fun DrawScope.mound(x: Float, y: Float, w: Float, col: Color) {
    for (k in 0 until 11) {
        val u = hash01(k, 83); val v = hash01(k, 84)
        val r = w * (0.10f + 0.07f * hash01(k, 85))
        val c = Offset(x + (u - 0.5f) * w * 0.8f, y - r * 0.7f - v * w * 0.22f * (1f - 2f * kotlin.math.abs(u - 0.5f)))
        drawCircle(Brush.radialGradient(listOf(lerp(col, Color.White, 0.4f), col, lerp(col, Color.Black, 0.4f)), Offset(c.x - r * 0.3f, c.y - r * 0.45f), r * 1.5f), r, c)
    }
}

/** A sea fan: a flat lattice in one plane. */
private fun DrawScope.fan(x: Float, y: Float, r: Float, col: Color) {
    drawArc(col.copy(alpha = 0.34f), 200f, 140f, true, Offset(x - r, y - r), Size(r * 2f, r * 2f))
    for (k in 0..10) {
        val a = (200f + k * 14f) * PI.toFloat() / 180f
        drawLine(col.copy(alpha = 0.85f), Offset(x, y), Offset(x + cos(a) * r, y + sin(a) * r), if (k % 5 == 0) 2.2f else 1.1f, cap = StrokeCap.Round)
    }
    for (j in 1..4) drawArc(col.copy(alpha = 0.5f), 200f, 140f, false, Offset(x - r * j / 4.4f, y - r * j / 4.4f), Size(r * 2f * j / 4.4f, r * 2f * j / 4.4f), style = HAIR)
}

/** Tube sponges: a cluster of open tubes. */
private fun DrawScope.tubes(x: Float, y: Float, col: Color) {
    for (k in 0 until 4) {
        val tx = x + (k - 1.5f) * 13f; val h = 30f + hash01(k, 91) * 26f; val w = 11f
        drawRect(Brush.horizontalGradient(listOf(lerp(col, Color.White, 0.3f), col, lerp(col, Color.Black, 0.4f)), startX = tx - w / 2, endX = tx + w / 2), Offset(tx - w / 2, y - h), Size(w, h))
        drawOval(lerp(col, Color.Black, 0.6f), Offset(tx - w / 2, y - h - 3f), Size(w, 6f))
        drawOval(lerp(col, Color.White, 0.4f), Offset(tx - w / 2, y - h - 3f), Size(w, 6f), style = HAIR)
    }
}

/** The colony: on and around the rocks to the right, a smaller group by the rock to the left. */
private fun DrawScope.reef(g: StepGeo) {
    val y = g.sandY
    mound(g.ux(935f), y + 44f, 110f, Color(0xFFD9B45E))
    fan(g.ux(770f), y - 8f, 62f, Color(0xFF8E5FB0))
    staghorn(g.ux(700f), y - 30f, 120f, 3, Color(0xFFE08A5B))
    staghorn(g.ux(815f), y - 4f, 84f, 40, Color(0xFFD9799A))
    tubes(g.ux(655f), y + 38f, Color(0xFFE2B84A))
    brain(g.ux(862f), y + 44f, 44f, Color(0xFF7FA66B))
    brain(g.ux(160f), y + 40f, 34f, Color(0xFFC9906B))
    staghorn(g.ux(262f), y - 16f, 78f, 71, Color(0xFFB9A2E0))
    fan(g.ux(120f), y + 30f, 44f, Color(0xFFD0705E))
}

// ------------------------------------------------------------------ alive: drawn every frame

private val DISC_BODY = Brush.horizontalGradient(0f to Color(0xFFF0B63A), 0.55f to Color(0xFFF7D866), 1f to Color(0xFFFFF6DA), startX = -0.5f, endX = 0.5f)
private val DAMSEL_BODY = Brush.verticalGradient(listOf(Color(0xFF1F55C9), Color(0xFF3F8CF0), Color(0xFF9BD2FF)), startY = -0.17f, endY = 0.17f)
private val SILVER_BODY = Brush.verticalGradient(listOf(fog(Color(0xFF2B5D6E), 0.3f), fog(Color(0xFFB4D5DB), 0.3f), fog(Color(0xFFF4FAF6), 0.3f)), startY = -0.14f, endY = 0.14f)
private val TAIL = Path()

/** A small fish in its unit frame (nose at +0.5, tail at −0.5), [kind] 0 reef disc, 1 damsel, 2 silver. */
private fun DrawScope.smallFish(x: Float, y: Float, len: Float, right: Boolean, wag: Float, kind: Int, alpha: Float) {
    if (alpha <= 0.02f) return
    withTransform({ translate(x, y); scale(if (right) len else -len, len, Offset.Zero) }) {
        val half = when (kind) { 0 -> 0.34f; 1 -> 0.17f; else -> 0.14f }
        TAIL.reset(); TAIL.moveTo(-0.34f, 0f); TAIL.lineTo(-0.56f, -half * 0.8f + wag); TAIL.lineTo(-0.48f, wag); TAIL.lineTo(-0.56f, half * 0.8f + wag); TAIL.close()
        when (kind) {
            0 -> {
                drawPath(TAIL, Color(0xFFF7E08A), alpha = alpha * 0.9f)
                drawOval(Color(0xFFF7E08A), Offset(-0.34f, -half - 0.07f), Size(0.5f, 0.2f), alpha = alpha * 0.8f)
                drawOval(DISC_BODY, Offset(-0.38f, -half), Size(0.86f, half * 2f), alpha = alpha)
                drawRect(Color(0xFF26262C), Offset(0.26f, -half * 0.72f), Size(0.07f, half * 1.44f), alpha = alpha * 0.75f)
                drawRect(Color(0xFF26262C), Offset(-0.30f, -half * 0.6f), Size(0.05f, half * 1.2f), alpha = alpha * 0.35f)
            }
            1 -> {
                drawPath(TAIL, Color(0xFFF5D24A), alpha = alpha)
                drawOval(DAMSEL_BODY, Offset(-0.40f, -half), Size(0.9f, half * 2f), alpha = alpha)
                drawLine(Color(0xFFBFE6FF), Offset(-0.26f, -0.02f), Offset(0.3f, -0.03f), 0.022f, alpha = alpha * 0.6f)
            }
            else -> {
                drawPath(TAIL, fog(Color(0xFFCDE6EA), 0.3f), alpha = alpha * 0.85f)
                drawOval(SILVER_BODY, Offset(-0.42f, -half), Size(0.92f, half * 2f), alpha = alpha)
            }
        }
        drawCircle(Color(0xFFDDE6E2), 0.042f, Offset(0.33f, -half * 0.18f), alpha = alpha)
        drawCircle(Color(0xFF101314), 0.026f, Offset(0.33f, -half * 0.18f), alpha = alpha)
    }
}

private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

/** What lives behind the main fish: the turtle, the middle kelp, the fish at the wreck, the octopus. */
internal fun DrawScope.stepBehind(g: StepGeo, model: Int, t: Float, plant: Float, sway: Float) {
    if (AquaStep.kelpForest(model)) {
        for (k in KELP_MID.indices) kelpStalk(g, g.ux(KELP_MID[k]), g.sandY - 6f, plant, sway, 20 + k, near = false)
        turtle(g, t)
    }
    if (AquaStep.wreck(model)) {
        val h = g.hole
        for (k in 0 until 3) {
            val u = (t * 0.06f + k / 3f) % 1f
            val s = smooth(if (u < 0.5f) u * 2f else 2f - u * 2f)
            val out = Offset(h.x - 150f + k * 60f, h.y - 96f - k * 34f)
            val x = h.x + (out.x - h.x) * s + 8f * sin(t * 0.9f + k * 2f)
            val y = h.y + (out.y - h.y) * s
            smallFish(x, y, 34f - 4f * k, right = (u < 0.5f) == (out.x > h.x), wag = 0.07f * sin(t * 7f + k), kind = 2, alpha = (s * 4f).coerceIn(0f, 1f))
        }
    }
}

/** What lives in front: the reef fish, the shrimp, the near kelp. */
internal fun DrawScope.stepFront(g: StepGeo, model: Int, t: Float, plant: Float, sway: Float) {
    if (AquaStep.reef(model)) {
        val y = g.sandY
        for (k in 0 until 2) {
            val a = t * 0.13f + k * 2.3f
            smallFish(g.ux(790f) + 120f * sin(a), y - 120f - 62f * k + 20f * sin(t * 0.21f + k), 50f - 6f * k, right = cos(a) > 0f, wag = 0.05f * sin(t * 5f + k), kind = 0, alpha = 1f)
        }
        for (k in 0 until 5) {
            val a = t * 0.19f + k * 1.3f
            smallFish(g.ux(705f) + 64f * sin(a) + 14f * sin(t * 0.5f + k), y - 96f - 15f * k + 10f * sin(t * 0.31f + k * 0.7f), 24f, right = cos(a) > 0f, wag = 0.08f * sin(t * 8f + k), kind = 1, alpha = 1f)
        }
    }
    if (AquaStep.wreck(model)) octopus(g.ux(596f), g.sandY + 58f, t)
    if (AquaStep.garden(model)) shrimp(g, t)
    if (AquaStep.kelpForest(model)) for (k in KELP_NEAR.indices) kelpStalk(g, g.ux(KELP_NEAR[k]), g.sandY + 40f, plant, sway, k, near = true)
}

private val SHRIMP = Color(0xFFF0B293)

/** A swarm of small glass shrimp over the meadow: they hang in the water and hop a little. */
private fun DrawScope.shrimp(g: StepGeo, t: Float) {
    for (k in 0 until 11) {
        val bx = g.ux(560f) + (hash01(k, 51) - 0.5f) * 150f; val by = g.sandY - 60f - hash01(k, 52) * 80f
        val hop = (0.5f + 0.5f * sin(t * 0.9f + k * 1.7f)).let { it * it * it }
        val x = bx + 10f * sin(t * 0.23f + k); val y = by - 12f * hop
        val d = if (k % 3 == 0) -1f else 1f
        val body = Path().apply { moveTo(x - 7f * d, y + 1f); quadraticTo(x - 1f * d, y - 5f, x + 6f * d, y - 1f) }
        drawPath(body, SHRIMP.copy(alpha = 0.75f), style = Stroke(3.2f, cap = StrokeCap.Round))
        drawPath(body, Color.White.copy(alpha = 0.5f), style = Stroke(1f, cap = StrokeCap.Round))
        drawLine(SHRIMP.copy(alpha = 0.7f), Offset(x + 6f * d, y - 1f), Offset(x + 15f * d, y - 6f), 0.8f)
        drawLine(SHRIMP.copy(alpha = 0.7f), Offset(x + 6f * d, y - 1f), Offset(x + 15f * d, y + 1f), 0.8f)
        drawCircle(Color(0xFF101314), 0.9f, Offset(x + 5f * d, y - 2f))
    }
}

private val OCT = Color(0xFFC8674A)
private val OCT_DARK = Color(0xFF7E3A2B)
private val OCT_PALE = Color(0xFFF0C2A6)

/** The octopus sits on the sand by the wreck: its mantle breathes and its arms curl slowly. */
private fun DrawScope.octopus(x: Float, y: Float, t: Float) {
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - 62f, y - 5f), Size(124f, 16f))
    // The mantle: a rounded sack leaning back behind the head; it breathes.
    val br = 1f + 0.035f * sin(t * 1.2f)
    val mx = x - 13f; val my = y - 50f
    withTransform({ rotate(-24f, Offset(mx, my)); scale(br, 1f / br, Offset(mx, my)) }) {
        drawOval(OCT_DARK, Offset(mx - 27f, my - 33f), Size(54f, 66f))
        drawOval(Brush.verticalGradient(listOf(lerp(OCT, Color.White, 0.32f), OCT, OCT_DARK), startY = my - 32f, endY = my + 34f), Offset(mx - 25.6f, my - 31.6f), Size(51.2f, 63.2f))
        for (k in 0 until 10) drawCircle(OCT_DARK.copy(alpha = 0.20f), 1.6f + hash01(k, 71) * 2.2f, Offset(mx - 15f + hash01(k, 72) * 30f, my - 20f + hash01(k, 73) * 34f))
    }
    // Arms: out along the sand, the tips curling up and over.
    for (a in 0 until 6) {
        val side = if (a % 2 == 0) -1f else 1f
        val pair = a / 2
        var px = x + side * 9f; var py = y - 12f
        var ang = (if (side < 0f) PI.toFloat() else 0f) + side * (0.42f - 0.10f * pair)
        val seg = 13f - pair * 2.2f
        for (j in 0 until 8) {
            ang += side * -(0.015f + 0.062f * j * (0.75f + 0.22f * pair)) + side * 0.06f * sin(t * 0.8f + a * 1.1f + j * 0.5f)
            val nx = px + cos(ang) * seg; val ny = (py + sin(ang) * seg).coerceAtMost(y + 2f)
            val w = 10.5f * (1f - j / 9f)
            drawLine(OCT_DARK, Offset(px, py), Offset(nx, ny), w + 1.6f, cap = StrokeCap.Round)
            drawLine(if (pair == 0) OCT else lerp(OCT, OCT_DARK, 0.14f * pair), Offset(px, py), Offset(nx, ny), w, cap = StrokeCap.Round)
            if (j in 1..5) drawCircle(OCT_PALE.copy(alpha = 0.75f), w * 0.17f, Offset((px + nx) / 2f, (py + ny) / 2f + w * 0.28f))
            px = nx; py = ny
        }
    }
    // The head in front of the mantle, and the eyes (a pale lid, a level slit).
    drawOval(OCT_DARK, Offset(x - 20f, y - 34f), Size(40f, 30f))
    drawOval(Brush.verticalGradient(listOf(lerp(OCT, Color.White, 0.2f), OCT), startY = y - 34f, endY = y - 4f), Offset(x - 18.8f, y - 32.8f), Size(37.6f, 27.6f))
    for (s in floatArrayOf(-1f, 1f)) {
        drawOval(OCT_PALE, Offset(x + s * 10f - 5f, y - 30f), Size(10f, 8f))
        drawLine(Color(0xFF1A1210), Offset(x + s * 10f - 3f, y - 26f), Offset(x + s * 10f + 3f, y - 26f), 2.2f, cap = StrokeCap.Round)
    }
}

private val KELP_MID = floatArrayOf(150f, 345f, 610f, 885f)
private val KELP_NEAR = floatArrayOf(-34f, 52f, 968f, 1040f)
private val KELP_NEAR_COL = Color(0xFF4F6230)
private val KELP_NEAR_LIT = Color(0xFF9AA85C)
private val KELP_MID_COL = fog(Color(0xFF55703A), 0.5f)

/** A kelp stalk from the sand up to the surface, where it bends over and trails; a blade and a float at every node. */
private fun DrawScope.kelpStalk(g: StepGeo, x0: Float, y0: Float, plant: Float, sway: Float, i: Int, near: Boolean) {
    val n = 16
    val rise = (y0 - g.surface - 8f)
    val step = rise / (n - 2.5f)
    val col = if (near) KELP_NEAR_COL else KELP_MID_COL
    val lit = if (near) KELP_NEAR_LIT else fog(KELP_NEAR_LIT, 0.55f)
    val w = if (near) 6.5f else 3.6f
    val bl = if (near) 96f else 58f
    val bw = if (near) 11f else 7f
    var x = x0; var y = y0
    val dir = if (hash01(i, 5) > 0.5f) 1f else -1f
    for (k in 0 until n) {
        val ku = k / n.toFloat()
        // Upright through the water, then over along the surface.
        val over = smooth((ku - 0.78f) / 0.2f)
        val ang = -90f + dir * 78f * over + (6f * sin(i * 1.7f + k * 0.5f) + sway * sin(plant * 0.8f + i * 1.3f + k * 0.3f)) * (0.3f + ku)
        val nx = x + cos(ang * PI.toFloat() / 180f) * step; val ny = (y + sin(ang * PI.toFloat() / 180f) * step).coerceAtLeast(g.surface + 5f)
        drawLine(col, Offset(x, y), Offset(nx, ny), w * (1f - 0.4f * ku), cap = StrokeCap.Round)
        if (k >= 1) {
            // One blade a node, alternating sides; it hangs down and out, and straight down where the stalk lies along the surface.
            val sd = if (k % 2 == 0) 1f else -1f
            val droop = 90f - sd * (58f - 16f * hash01(i * 31 + k, 7)) * (1f - over)
            val rot = droop + sway * 0.9f * sin(plant + k + i)
            val len = bl * (0.75f + 0.25f * hash01(i * 31 + k, 8)) * (1f - 0.3f * over)
            rotate(rot, Offset(nx, ny)) {
                drawOval(col, Offset(nx + 2f, ny - bw / 2f), Size(len, bw))
                drawLine(lit.copy(alpha = if (near) 0.38f else 0.22f), Offset(nx + 8f, ny - bw * 0.12f), Offset(nx + len * 0.86f, ny - bw * 0.12f), 1f)
            }
            drawCircle(lit, if (near) 3f else 2f, Offset(nx, ny))
        }
        x = nx; y = ny
    }
}

private val SHELL_TOP = fog(Color(0xFF4F5E3C), 0.25f)
private val SHELL_LOW = fog(Color(0xFF93905A), 0.25f)
private val SKIN = fog(Color(0xFF7C8A60), 0.25f)
private val SKIN_DARK = fog(Color(0xFF45523A), 0.25f)
private val PLASTRON = fog(Color(0xFFE0D6A6), 0.25f)
private val CARAPACE = Path().apply { moveTo(-0.44f, 0.03f); cubicTo(-0.36f, -0.25f, 0.16f, -0.30f, 0.36f, -0.02f); cubicTo(0.2f, 0.11f, -0.26f, 0.12f, -0.44f, 0.03f); close() }
private val FLIPPER = Path().apply { moveTo(0f, -0.035f); cubicTo(-0.16f, -0.02f, -0.36f, 0.06f, -0.47f, 0.2f); cubicTo(-0.30f, 0.16f, -0.12f, 0.09f, 0.035f, 0.035f); close() }
private val SHELL_BRUSH = Brush.verticalGradient(listOf(lerp(SHELL_TOP, SUN, 0.22f), SHELL_TOP, SHELL_LOW), startY = -0.27f, endY = 0.1f)

/** A sea turtle crossing the open water slowly, right to left, in the middle distance. */
private fun DrawScope.turtle(g: StepGeo, t: Float) {
    val span = g.dw + 480f
    val x = g.dw + 240f - ((t * 13f + span * 0.60f) % span)
    val y = 196f + 16f * sin(t * 0.23f)
    val beat = sin(t * 1.15f)
    withTransform({ translate(x, y); rotate(3f * sin(t * 0.23f + 1.2f), Offset.Zero); scale(-170f, 170f, Offset.Zero) }) {
        // Far flippers, then the body, then the near ones.
        rotate(8f + 26f * beat, Offset(0.2f, 0.02f)) { withTransform({ translate(0.2f, 0.02f) }) { drawPath(FLIPPER, SKIN_DARK) } }
        withTransform({ translate(-0.38f, 0.04f); rotate(-28f + 8f * beat, Offset.Zero); scale(0.5f, 0.5f, Offset.Zero) }) { drawPath(FLIPPER, SKIN_DARK) }
        // Neck and head.
        drawLine(SKIN, Offset(0.3f, -0.02f), Offset(0.45f, -0.045f), 0.085f, cap = StrokeCap.Round)
        drawOval(SKIN, Offset(0.40f, -0.105f), Size(0.17f, 0.115f))
        drawOval(PLASTRON.copy(alpha = 0.6f), Offset(0.42f, -0.03f), Size(0.13f, 0.035f))
        drawCircle(Color(0xFF14181A), 0.013f, Offset(0.515f, -0.058f))
        // The shell: a low dome, lit from above, the plastron pale underneath, faint scutes.
        drawPath(CARAPACE, SHELL_BRUSH)
        clipPath(CARAPACE) {
            drawRect(PLASTRON, Offset(-0.5f, 0.055f), Size(1f, 0.1f))
            for (k in 0 until 5) {
                val sx = -0.30f + k * 0.14f
                drawLine(SKIN_DARK.copy(alpha = 0.45f), Offset(sx, -0.26f), Offset(sx + 0.05f, 0.055f), 0.008f)
            }
            drawLine(SKIN_DARK.copy(alpha = 0.4f), Offset(-0.42f, -0.07f), Offset(0.32f, -0.09f), 0.008f)
        }
        drawPath(CARAPACE, SKIN_DARK.copy(alpha = 0.6f), style = Stroke(0.008f))
        withTransform({ translate(-0.36f, 0.05f); rotate(-18f - 8f * beat, Offset.Zero); scale(0.55f, 0.55f, Offset.Zero) }) { drawPath(FLIPPER, SKIN) }
        rotate(-4f - 30f * beat, Offset(0.22f, 0.04f)) {
            withTransform({ translate(0.22f, 0.04f) }) {
                drawPath(FLIPPER, SKIN)
                drawPath(FLIPPER, SKIN_DARK.copy(alpha = 0.5f), style = Stroke(0.007f))
            }
        }
    }
}
