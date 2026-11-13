package io.vroomi.character.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.vroomi.character.Action
import io.vroomi.character.CharacterSkin
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Gait
import io.vroomi.character.SizeMode
import kotlin.math.PI
import kotlin.math.sin

enum class Season { SPRING, SUMMER, AUTUMN, WINTER;
    companion object {
        /** Month 1..12 -> season (Korea). */
        fun ofMonth(month: Int): Season = when (month) { 3, 4, 5 -> SPRING; 6, 7, 8 -> SUMMER; 9, 10, 11 -> AUTUMN; else -> WINTER }
    }
}

/** Scene options decided by the app (settings / platform / clock). */
data class SceneOptions(
    /** In-app Calm Mode: half amplitude, no particles while moving, frozen background, no fidgets. */
    val calm: Boolean = false,
    /** System "remove animations" setting. */
    val reducedMotion: Boolean = false,
    /** Corner bubble background color (theme surface). */
    val bubbleColor: Color = Color(0xFFFFFFFF),
    /** User "character size" setting (0.85 small, 1 normal, 1.15 large). */
    val sizeScale: Float = 1f,
    /** Local time of day (0..24): sky colour, sun position, stars. */
    val hourOfDay: Float = 13f,
    /** Tree colours and drifting leaves/petals/flakes. */
    val season: Season = Season.SUMMER,
    /** Which friend rides in the kart (15 themes). */
    val skin: CharacterSkin = CharacterSkin.VROOMI,
    /**
     * Entrance progress of a theme passage: 0 = the start of the theme's entrance, 1 = normal.
     * Painters may read it for an optional entrance accent; the default 1 never changes a frame.
     */
    val intro: Float = 1f,
    /**
     * The theme's upgrade step: 0..4 = Lv.1..5 ([io.vroomi.character.theme.ThemeModels]). Every painter reads it
     * (kit themes as `f.options.model` or `f.model`, CALIBRE and ENGINE as their runtime's `model`, read from the
     * options of its last step; KART's runtime gets the same options). The default 0 is Lv.1.
     */
    val model: Int = 0,
)

/**
 * Vroomi driving its own little kart through a world that changes with the trip, drawn procedurally
 * (no bitmap art) on a Compose Canvas. A frame clock drives springs and particles at 60 fps
 * (30 fps when parked and idle).
 */
@Composable
fun VroomiScene(
    target: CharacterTarget,
    options: SceneOptions,
    modifier: Modifier = Modifier,
    state: VroomiSceneState = rememberVroomiSceneState(),
    onFirstFrame: (() -> Unit)? = null,
) {
    val rt = state.rt
    val drawn = remember { BooleanArray(2) } // [0] drawn once, [1] reported
    val tm = rememberTextMeasurer()
    val cache = remember { TextCache() }
    val currentTarget by rememberUpdatedState(target)
    val currentOptions by rememberUpdatedState(options)
    var frame by remember { mutableLongStateOf(0L) }
    val currentFirst by rememberUpdatedState(onFirstFrame)

    LaunchedEffect(Unit) {
        var last = 0L
        var skip = false
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                val t = currentTarget
                val idle = t.gait == Gait.IDLE && t.action == Action.NONE && t.sizeMode == SizeMode.FULL &&
                    !t.kart.driving && rt.particles.count == 0 && rt.fidget == Fidget.NONE && rt.seatU == 0f &&
                    currentOptions.intro >= 1f // an entrance accent runs at the full frame rate
                skip = idle && !skip // 30 fps while idle and parked
                rt.step(dt, t, currentOptions, System.currentTimeMillis())
                if (!skip) frame = now
                if (drawn[0] && !drawn[1]) { drawn[1] = true; currentFirst?.invoke() }
            }
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val f = frame // read -> redraw every frame
        rt.draw(this, currentTarget, currentOptions, tm, cache)
        drawn[0] = true
    }
}

/**
 * Animation state of the scene. Hoist it (one per screen) so the parked and driving layouts share it:
 * Vroomi then hops into the kart when driving starts and back out when the car is parked.
 */
class VroomiSceneState internal constructor(internal val rt: SceneRuntime)

@Composable
fun rememberVroomiSceneState(): VroomiSceneState = remember { VroomiSceneState(SceneRuntime()) }

/** Measured text is cached per (text, size): no per-frame text layout. */
internal class TextCache {
    private val map = HashMap<String, TextLayoutResult>()
    fun get(tm: TextMeasurer, text: String, pxSize: Float, density: Density): TextLayoutResult {
        val key = "$text|${pxSize.toInt()}"
        return map.getOrPut(key) {
            if (map.size > 64) map.clear()
            tm.measure(AnnotatedString(text), TextStyle(fontSize = with(density) { pxSize.toSp() }, fontWeight = FontWeight.Bold))
        }
    }
}

// ------------------------------------------------------------------ composition of one frame

internal fun SceneRuntime.draw(ds: DrawScope, t: CharacterTarget, o: SceneOptions, tm: TextMeasurer?, cache: TextCache) = with(ds) {
    val w = size.width; val h = size.height
    lastW = w; lastH = h
    val rt = this@draw
    val n = dark.value.coerceIn(0f, 1f)

    drawSky(rt, o)
    drawFar(rt, o)
    drawGroundAndMid(rt, o)
    drawRoadsideSigns(rt, tm, cache)
    drawRoad(rt, o)
    drawParticles(rt, w, h, n, behind = true)

    val corner = cornerA.value.coerceIn(0f, 1f)
    val s = unitPx(w, h, o.sizeScale)
    // Entrance accent (SceneOptions.intro < 1): the kart rolls in 120 dp from the left with its wheels turning
    // and settles with a 2 dp suspension dip. At intro = 1 this block changes nothing.
    val entering = o.intro < 1f
    val rollIn = if (entering) (1f - Intro.out(o.intro, 0f, 0.75f)) * 120f * density else 0f
    val kx = if (entering) kartCenterPx(w, s) - rollIn else kartCenterPx(w, s)
    val ky = groundY.value * h
    val bounceY = if (entering) bounce.value * s + Intro.bump(o.intro, 0.62f) * 2f * density else bounce.value * s
    val wheelRest = wheelAngle
    if (entering) wheelAngle = wheelRest - rollIn / (KG.WHEEL_R * s) * (180f / PI.toFloat())

    if (corner > 0.01f) {
        val c = Offset(kx, ky - s * 0.72f)
        drawCircle(o.bubbleColor.copy(alpha = 0.88f * corner), s * 1.25f, c)
        drawCircle(Pal.outline.copy(alpha = 0.35f * corner), s * 1.25f, c, style = Stroke(width = 3f))
    }

    val seatRel = Offset(KG.SEAT_X * s, KG.SEAT_Y * s)
    val standAbs = standPx(w, s, kx)
    val standRel = Offset(lerp1(standAbs - kx, 0f, corner), 0f)
    val u = smooth01(seatU)
    val arc = -sin(PI.toFloat() * seatU) * s * 0.55f
    val vo = Offset(lerp1(standRel.x, seatRel.x, u), lerp1(standRel.y, seatRel.y, u) + arc)
    val behindTub = seatU >= 0.5f
    val poseSeated = seatU >= 0.85f
    val showKart = !(corner > 0.5f && !behindTub)

    // Ground effects (shadow, underglow) stay on the road.
    if (showKart) withTransform({ translate(kx, ky); scale(s, s, Offset.Zero) }) { drawKartGround(rt, n) }
    else drawOval(Color.Black.alpha(0.12f), Offset(kx - s * 0.4f, ky - s * 0.04f), Size(s * 0.8f, s * 0.08f))

    withTransform({
        translate(kx, ky + bounceY)
        rotate(pitch.value, Offset(0f, -KG.WHEEL_R * s))
    }) {
        if (showKart) scale(s, s, Offset.Zero) {
            drawHeadlightCone(rt, t, n)
            drawCharger(rt, t, n)
            drawKartBack(rt, t, n, poseSeated)
        }
        if (behindTub) {
            drawVroomiBackArm(rt, t, vo.x, vo.y, s, n, poseSeated)
            drawVroomiBody(rt, t, vo.x, vo.y, s, n, poseSeated)
        }
        if (showKart) scale(s, s, Offset.Zero) {
            if (poseSeated) drawKartBlanket(t)
            drawKartBody(rt, t, n)
            drawKartDash(rt, t, n)
        }
        if (behindTub) drawVroomiFrontArm(rt, t, vo.x, vo.y, s, n, poseSeated)
        else drawVroomiBody(rt, t, vo.x, vo.y, s, n, false)
        if (showKart) scale(s, s, Offset.Zero) { drawTurnSign(rt, t) }
    }

    val headX = kx + vo.x + s * 0.1f
    val headY = ky + vo.y - s * 0.62f
    drawBird(rt, headX, headY, s, n)
    if (t.overlays.recircSwirl && corner < 0.5f) drawRecirc(rt, headX - s * 0.1f, headY + s * 0.05f, s)
    drawRoadEntities(rt)
    if (t.overlays.speedLines && corner < 0.5f && !o.calm) drawSpeedLines(rt)
    drawParticles(rt, w, h, n, behind = false)
    if (entering) wheelAngle = wheelRest
}

private fun lerp1(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun DrawScope.drawRecirc(rt: SceneRuntime, cx: Float, cy: Float, s: Float) {
    val r = s * 0.85f
    rotate(rt.time * 40f, Offset(cx, cy)) {
        drawArc(Pal.outline.copy(alpha = 0.25f), 0f, 300f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(width = s * 0.02f, cap = StrokeCap.Round))
        drawCircle(Pal.outline.copy(alpha = 0.3f), s * 0.025f, Offset(cx + r, cy))
    }
}

private fun DrawScope.drawParticles(rt: SceneRuntime, w: Float, h: Float, n: Float, behind: Boolean) {
    val p = rt.particles
    for (i in 0 until p.count) {
        val type = p.type[i] ?: continue
        val isBehind = type == PType.SNOW || type == PType.FALL
        if (isBehind != behind) continue
        val a = (p.life[i] / p.maxLife[i]).coerceIn(0f, 1f)
        val x = p.x[i] * w; val y = p.y[i] * h; val sz = p.size[i] * h
        when (type) {
            PType.WIND -> drawLine(lerp(Color.White, Color(0xFFBDEDE4), n).copy(alpha = 0.55f * a), Offset(x, y), Offset(x - sz * 2.2f, y), strokeWidth = 3f, cap = StrokeCap.Round)
            PType.BREATH -> drawCircle(Pal.frost.copy(alpha = 0.5f * a), sz * (1.6f - a), Offset(x, y))
            PType.SNOW -> drawCircle(Color.White.copy(alpha = 0.85f * a), sz, Offset(x, y))
            PType.STEAM -> drawCircle(Pal.warm.copy(alpha = 0.35f * a), sz * (1.8f - a), Offset(x, y))
            PType.RAIN -> drawLine(Color(0xFF9FC9E6).copy(alpha = 0.6f), Offset(x, y), Offset(x - sz * 0.15f, y + sz), strokeWidth = 2f)
            PType.SPARKLE -> {
                val r = sz * (0.5f + 0.5f * sin(a * PI.toFloat()))
                drawLine(Pal.sparkle.copy(alpha = a), Offset(x - r, y), Offset(x + r, y), strokeWidth = 2f)
                drawLine(Pal.sparkle.copy(alpha = a), Offset(x, y - r), Offset(x, y + r), strokeWidth = 2f)
            }
            PType.DUST -> drawCircle(Color(0xFFB9A88F).copy(alpha = 0.5f * a), sz, Offset(x, y))
            PType.CONFETTI -> {
                val c = when ((p.hue[i] * 4).toInt()) { 0 -> Pal.sole; 1 -> Pal.body; 2 -> Pal.scarf; else -> Pal.raincoat }
                drawRect(c.copy(alpha = a), Offset(x, y), Size(sz * 1.4f, sz * 0.8f))
            }
            PType.ENERGY -> drawCircle(Pal.energy.copy(alpha = a), sz, Offset(x, y))
            PType.ZZZ -> {
                val z = sz
                val c = Pal.outline.copy(alpha = 0.6f * a)
                drawLine(c, Offset(x, y), Offset(x + z, y), strokeWidth = 2.5f)
                drawLine(c, Offset(x + z, y), Offset(x, y + z), strokeWidth = 2.5f)
                drawLine(c, Offset(x, y + z), Offset(x + z, y + z), strokeWidth = 2.5f)
            }
            PType.NOTE -> {
                // Indigo to red by day; against the night sky the same notes pale towards moonlight so they still read.
                // A note stays solid for most of its life and fades only at the end (a plain fade left it faint throughout).
                val c = lerp(lerp(Pal.note, Color(0xFFE4574B), p.hue[i]), Color(0xFFF4EEFF), 0.62f * n).alpha(0.9f * (a * 1.8f).coerceAtMost(1f))
                drawOval(c, Offset(x - sz * 0.5f, y - sz * 0.3f), Size(sz, sz * 0.7f))
                drawLine(c, Offset(x + sz * 0.45f, y), Offset(x + sz * 0.45f, y - sz * 1.4f), strokeWidth = 3f)
                drawLine(c, Offset(x + sz * 0.45f, y - sz * 1.4f), Offset(x + sz * 1.0f, y - sz * 1.0f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
            PType.ZAP -> drawCircle(Pal.energy.alpha(0.7f * a), sz * (0.6f + a * 0.6f), Offset(x, y))
            PType.SPLASH -> drawCircle(Color(0xFFCFE6F5).alpha(0.7f * a), sz, Offset(x, y))
            PType.STARPOP -> drawStar(rt, Offset(x, y), sz, Pal.star.alpha(a), null)
            PType.FALL -> {
                val col = fallColor(rt, p.hue[i])
                rotate(p.life[i] * 120f + p.hue[i] * 360f, Offset(x, y)) {
                    drawOval(col.night(n, 0.5f).alpha(a.coerceAtMost(0.9f)), Offset(x - sz, y - sz * 0.45f), Size(sz * 2f, sz * 0.9f))
                }
            }
        }
    }
}

/** The scene remembers the last season it drew with, so FALL particles match the trees. */
private fun fallColor(rt: SceneRuntime, hue: Float): Color = when (rt.lastSeason) {
    Season.AUTUMN -> if (hue < 0.5f) Color(0xFFF2B33D) else Color(0xFFE9824A)
    Season.SPRING -> Color(0xFFF7B7C9)
    Season.WINTER -> Color.White
    Season.SUMMER -> Color(0xFF6CC24A)
}

/**
 * A still frame of the scene after simulating [seconds] of [target] (no frame clock).
 * For Compose previews, screenshots and the app's snapshot tests.
 */
@Composable
fun VroomiSceneStill(target: CharacterTarget, options: SceneOptions, modifier: Modifier = Modifier, seconds: Float = 2.5f, nowMs: Long = 1_000_000L) {
    val rt = remember(target, options, seconds) {
        SceneRuntime(seed = 3).also { r ->
            val frames = (seconds * 60).toInt()
            for (i in 0 until frames) r.step(1f / 60f, target, options, nowMs + i * 1000L / 60)
        }
    }
    val tm = rememberTextMeasurer()
    val cache = remember { TextCache() }
    Canvas(modifier) { rt.draw(this, target, options, tm, cache) }
}

/** Just the friend (no road), standing and waving: for the theme picker. */
@Composable
fun VroomiPortrait(skin: CharacterSkin, modifier: Modifier = Modifier, night: Boolean = false) {
    val o = SceneOptions(skin = skin, hourOfDay = if (night) 22f else 13f)
    val target = CharacterTarget(sizeMode = SizeMode.FULL, action = Action.WAVE_HELLO, actionStartedMs = 1_000_000L)
    val rt = remember(skin, night) {
        SceneRuntime(seed = 3).also { r -> for (i in 0 until 90) r.step(1f / 60f, target, o, 1_000_000L + i * 16L) }
    }
    Canvas(modifier) {
        val s = minOf(size.height * 0.58f, size.width * 0.55f)
        drawVroomiBody(rt, target, size.width * 0.52f, size.height * 0.94f, s, 0f, false)
    }
}
