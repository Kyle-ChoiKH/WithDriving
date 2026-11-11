package io.vroomi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import io.vroomi.app.diary.TripRecord
import io.vroomi.app.ui.design.Ambience
import io.vroomi.app.ui.design.ChromeColors
import io.vroomi.app.ui.design.ColorMath
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalSceneBackdrop
import io.vroomi.app.ui.design.SceneBackdrop
import io.vroomi.app.ui.design.SmoothCorners
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.motion.LocalMotion
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.MotionPrefs
import io.vroomi.app.ui.passage.HeroStills
import io.vroomi.app.ui.passage.PassageHint
import io.vroomi.app.ui.passage.PassageStyle
import io.vroomi.app.ui.passage.SceneStage
import io.vroomi.app.ui.passage.StageController
import io.vroomi.app.ui.passage.StagePlacement
import io.vroomi.character.CharacterSkin
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneStillRenderer
import io.vroomi.character.render.StillSpec
import io.vroomi.core.vehicle.sim.Scenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max

/**
 * The passage reel (docs/media/theme_passage.gif), rendered only with VROOMI_REEL=1: launch, home, the gallery,
 * three themes entered, an arrow through all twelve, apply, a demo start, moving, parked, the trip card and the
 * user's own theme returning. Every theme change runs through the real [StageController]; the live worlds are
 * simulated stills that advance with the reel's clock.
 */
class PassageReelTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            // 960 x 360 px: the 1920 x 720 layout drawn at exactly 1/2 (no resampling later; pixel art stays on
            // an integer grid).
            screenWidth = 960, screenHeight = 360, xdpi = 160, ydpi = 160,
            density = com.android.resources.Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private val w = 1920f
    private val h = 720f
    private val rail = 132f
    private val home = Rect(rail, 0f, w, h)
    private val full = Rect(0f, 0f, w, h)
    private val frameMs = 125f
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val renderer by lazy { SceneStillRenderer(createFontFamilyResolver(paparazzi.context), Density(1f)) }
    private val stillCache = HashMap<SceneTheme, ImageBitmap>()
    private fun still(t: SceneTheme) = stillCache.getOrPut(t) { renderer.render(t, HeroStills.W, HeroStills.H, StillSpec(skin = CharacterSkin.VROOMI)) }

    /** Everything the frame shows (mutated between frames). */
    private inner class Scene {
        var launch = 1f
        var sceneAlpha = 1f
        var chrome = 1f
        var railShown = 1f
        var tab = Tab.HOME
        var railFrom: Float? = null
        var railAt: Float? = null
        var homeAlpha = 1f
        var bubble: String? = null
        var galleryAlpha = 0f
        var recede = 0f
        var rect: () -> Rect = { home }
        var corner = 0f
        var layout = IntSize(home.width.toInt(), home.height.toInt())
        var stageTheme: SceneTheme? = null
        var stageChrome = 0f
        var ghost: ImageBitmap? = null
        var ghostRect: () -> Rect = { full }
        var ghostCorner = 0f
        var confirm = 0f
        var sheet = 0f
        var sheetCollapse = false
        var moving = 0f
        var chip = "함께 헤엄"
        var caption: String? = null
        var trip = 0f
        var worldT = 0f
        var speed = 0f
    }

    private lateinit var s: Scene
    private lateinit var stage: StageController
    private lateinit var stills: HeroStills
    private lateinit var gallery: GalleryState
    private var frame = 0
    private val live = HashMap<SceneTheme, ImageBitmap>()

    private fun liveImage(t: SceneTheme): ImageBitmap =
        live.getOrPut(t) { renderer.render(t, HeroStills.W, HeroStills.H, StillSpec(skin = CharacterSkin.VROOMI, seconds = 1.5f + s.worldT, speedKmh = s.speed)) }

    private fun shoot() {
        live.clear()
        val chrome = ChromeColors(Ambience.of(stage.to ?: SceneTheme.KART), false)
        val prev = stage.from?.let { Ambience.of(it) } ?: Ambience.of(SceneTheme.KART)
        chrome.mix(prev, Ambience.of(stage.to ?: SceneTheme.KART), stage.tintMix.floatValue)
        paparazzi.snapshot("reel_%04d".format(frame++)) {
            VroomiTheme(night = false, theme = stage.to ?: SceneTheme.KART) {
                CompositionLocalProvider(LocalMotion provides MotionPrefs(still = true), LocalChrome provides chrome) {
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(1920.dp, 720.dp)
                            .graphicsLayer { scaleX = 0.5f; scaleY = 0.5f; transformOrigin = TransformOrigin(0f, 0f) },
                    ) { Frame() }
                }
            }
        }
        s.worldT += frameMs / 1000f
    }

    @Composable
    private fun Frame() {
        val chrome = LocalChrome.current
        val backdrop = SceneBackdrop()
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color(ColorMath.lerpOklab(0xFF0E1B2B.toInt(), chrome.backdropArgb, s.launch))) }) {
            CompositionLocalProvider(LocalSceneBackdrop provides backdrop) {
                // Base: the rail and the gallery (recedes behind the stage).
                Row(Modifier.fillMaxSize().graphicsLayer { alpha = s.chrome }) {
                    Box(Modifier.fillMaxHeight().graphicsLayer { alpha = s.railShown; translationX = -(1 - s.railShown) * rail }) {
                        TabRail(s.tab, debugFrom = s.railFrom, debugAt = s.railAt) {}
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                            alpha = s.galleryAlpha
                            val k = s.recede
                            scaleX = 1f - 0.06f * k; scaleY = 1f - 0.06f * k
                            if (k > 0.01f) renderEffect = BlurEffect(24.dp.toPx() * k, 24.dp.toPx() * k, TileMode.Clamp)
                        }.drawWithContent { drawContent(); if (s.recede > 0.003f) drawRect(Color.Black, alpha = 0.3f * s.recede) },
                    ) {
                        if (s.galleryAlpha > 0.001f) Gallery(gallery, SceneTheme.KART, stills, chips = { ThemeOptionChips(SceneTheme.KART, ctx()) {} }, onOpen = {},
                            staticStills = ThemeRegistry.all.associate { it.theme to still(it.theme) })
                    }
                }
                // The one live scene.
                SceneStage(
                    stage, stills,
                    StagePlacement(rect = s.rect, corner = { s.corner }, alpha = { s.sceneAlpha }, dim = { 0f }),
                    s.layout, { chrome.backdrop }, { chrome.tint }, backdrop,
                ) { m ->
                    val t = stage.live
                    if (t != null) Image(liveImage(t), null, m, contentScale = ContentScale.Crop, filterQuality = if (t == SceneTheme.PIXEL) FilterQuality.None else FilterQuality.Low)
                }
                // The ghost of a closing preview.
                s.ghost?.let { g ->
                    Canvas(Modifier.fillMaxSize()) {
                        val r = s.ghostRect()
                        val geo = SmoothCorners.geometry(r.width, r.height, s.ghostCorner, 0.6f)
                        val p = Path().apply {
                            moveTo(r.left + geo.startX, r.top + geo.startY)
                            for (sg in geo.segs) if (sg.cubic) cubicTo(r.left + sg.x1, r.top + sg.y1, r.left + sg.x2, r.top + sg.y2, r.left + sg.x, r.top + sg.y) else lineTo(r.left + sg.x, r.top + sg.y)
                            close()
                        }
                        clipPath(p) {
                            val f = max(r.width / g.width, r.height / g.height)
                            drawImage(g, IntOffset.Zero, IntSize(g.width, g.height), IntOffset((r.left + (r.width - g.width * f) / 2).toInt(), (r.top + (r.height - g.height * f) / 2).toInt()), IntSize((g.width * f).toInt(), (g.height * f).toInt()))
                        }
                    }
                }
                // Chrome over the scene.
                Box(Modifier.fillMaxSize().padding(start = 132.dp).padding(Space.parked).graphicsLayer { alpha = s.homeAlpha * s.chrome; translationY = (1 - s.chrome) * 12.dp.toPx() }) {
                    GradeBadge(io.vroomi.app.progress.Grade.GOLD, 128, 0.62f, "다음 등급까지 7.4 km", Modifier.align(Alignment.TopStart))
                    s.bubble?.let { SpeechBubble(it, Modifier.align(Alignment.TopCenter)) }
                    HomeActions(demo = false, listening = false, canTalk = true, modifier = Modifier.align(Alignment.BottomEnd), onDemo = {}, onTalk = {})
                    if (s.trip > 0.001f) Box(Modifier.align(Alignment.TopEnd).graphicsLayer { alpha = s.trip; translationX = (1 - s.trip) * 32.dp.toPx() }) { TripSummaryCard(trip) {} }
                }
                if (s.confirm > 0.001f) Box(Modifier.fillMaxSize().padding(top = Space.s24), contentAlignment = Alignment.TopCenter) {
                    ConfirmChip("테마를 바꿨어요", Modifier.graphicsLayer { alpha = s.confirm })
                }
                s.stageTheme?.let { t ->
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = s.stageChrome; translationY = (1 - s.stageChrome) * 8.dp.toPx() }) {
                        StageChrome(t, shown = true, leaving = false, onClose = {}, onPrev = {}, onNext = {}, onApply = {}, onDemo = {}, chips = { ThemeOptionChips(t, ctx()) {} })
                    }
                }
                if (s.moving > 0.001f) MovingOverlay(s.chip, muted = false, calm = false, onMute = {}, onCalm = {}, demoCaption = s.caption, demoProgress = 0.3f, onEndDemo = {},
                    modifier = Modifier.graphicsLayer { alpha = s.moving })
            }
            if (s.sheet > 0.001f) Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = s.sheet
                if (s.sheetCollapse) { transformOrigin = TransformOrigin(1f, 0f); scaleX = 0.4f + 0.6f * s.sheet; scaleY = 0.4f + 0.6f * s.sheet }
                else translationY = (1 - s.sheet) * 300.dp.toPx()
            }) {
                DemoPicker(io.vroomi.app.demo.DemoCatalog.sections(SceneTheme.KART, scenarios), "꼬마차와 친구들", onPick = {}, onHandsOn = {}, onClose = {})
            }
        }
    }

    private val trip = TripRecord(1, 0, 900_000, 9.6f, 2, 0, 0, 0, 2, 0, 96, stars = 31, bestCombo = 18, biomes = 4, demo = true)
    private val scenarios = listOf(
        Scenario("highlight", "브루미 하이라이트 (3분)", 172f, emptyList(), "출발부터 도착까지, 브루미가 반응하는 모든 순간을 한 번에"),
        Scenario("aquarium_showcase", "아쿠아리움 (2분)", 120f, emptyList(), "가속하면 물이 기울고, 별을 받으면 진주가 생겨요"),
        Scenario("commute", "출근길 (3분)", 182f, emptyList(), "도시 출퇴근: 신호 대기, 깜빡이, 에어컨, 급제동, 고속도로"),
    )

    private fun ctx() = PickerContext(CharacterSkin.VROOMI, io.vroomi.character.calibre.CalibreFinish.AUTO, io.vroomi.character.engine.EnginePowertrain.AUTO, io.vroomi.character.render.calibre.DEFAULT_NOW_MS, 0L)

    // ---------------------------------------------------------------- timeline helpers

    private fun lerp(a: Rect, b: Rect, k: Float) = Rect(a.left + (b.left - a.left) * k, a.top + (b.top - a.top) * k, a.right + (b.right - a.right) * k, a.bottom + (b.bottom - a.bottom) * k)
    private fun fade(tMs: Float, durMs: Float) = Motion.MovingCurve.ease((tMs / durMs).coerceIn(0f, 1f))
    private fun spring(token: io.vroomi.app.ui.motion.SpringToken, tMs: Float) = if (tMs <= 0f) 0f else token.progress(tMs.toDouble()).coerceIn(0f, 1f)

    /** Runs [frames] frames; [f] gets the time since the start of the segment (ms). The passage advances alongside. */
    private fun seg(frames: Int, f: (Float) -> Unit = {}) {
        for (i in 0 until frames) {
            val t = i * frameMs
            f(t)
            shoot()
            stage.advanceForTest(frameMs, stage.live?.let { liveImage(it) })
        }
    }

    private fun card(t: SceneTheme): Rect = gallery.cardRect(t)?.let { r ->
        val k = gallery.scaleOf(t)
        Rect(r.center.x - r.width * k / 2, r.center.y - r.height * k / 2, r.center.x + r.width * k / 2, r.center.y + r.height * k / 2)
    } ?: Rect(w / 2 - 365, 200f, w / 2 + 365, 474f)

    @Test fun passageReel() {
        assumeTrue(System.getenv("VROOMI_REEL") == "1")
        s = Scene()
        stage = StageController(SceneTheme.KART, scope) { Ambience.of(it).luminance }
        stills = HeroStills(renderer, scope, synchronous = true).also { st -> ThemeRegistry.all.forEach { st.put(it.theme, still(it.theme)) } }
        gallery = GalleryState(SceneTheme.KART)

        // 1. Launch: the night colour fades to the theme's backdrop, the scene fades up, the chrome rises 12 dp.
        seg(12) { t ->
            s.launch = fade(t, Motion.LAUNCH_BACKDROP_MS.toFloat())
            s.sceneAlpha = fade(t - Motion.LAUNCH_BACKDROP_MS, Motion.LAUNCH_SCENE_MS.toFloat())
            s.chrome = spring(Motion.Smooth, t - Motion.LAUNCH_BACKDROP_MS - Motion.LAUNCH_CHROME_DELAY_MS)
        }
        s.bubble = "오늘도 같이 달려요!"
        seg(5)

        // 2. The gallery: the live scene travels into its window (Passage), the shelf fades in, the rail capsule slides.
        s.bubble = null
        s.layout = IntSize(w.toInt(), h.toInt())
        stage.coverForTest(liveImage(SceneTheme.KART), 1f)
        seg(9) { t ->
            s.tab = Tab.THEMES; s.railFrom = 0f; s.railAt = spring(Motion.Snappy, t)
            s.homeAlpha = 1f - fade(t, Motion.FADE_FAST_MS.toFloat())
            s.galleryAlpha = fade(t - Motion.CONTENT_DELAY_MS, Motion.FADE_IN_MS.toFloat())
            val k = spring(Motion.Passage, t)
            s.rect = { lerp(home, card(SceneTheme.KART), k) }
            s.corner = 32f * k
            if (t >= 800f) stage.coverForTest(liveImage(SceneTheme.KART), (1f - (t - 800f) / Motion.SWAP_MS).coerceIn(0f, 1f))
        }
        stage.coverForTest(null, 0f)
        s.railFrom = null; s.railAt = null
        s.rect = { card(SceneTheme.KART) }
        seg(3)

        // 3. Three themes entered: CALIBRE and ENGINE grow out of their cards and close again; KART's live card grows.
        for (t in listOf(SceneTheme.CALIBRE, SceneTheme.ENGINE)) enterAndClose(t)
        enter(SceneTheme.KART)

        // 4. The arrows through all twelve, each with its own entrance.
        for (i in 1 until ThemeRegistry.all.size) {
            val next = ThemeRegistry.all[i].theme
            stage.hint = PassageHint(PassageStyle.ENTRANCE, interrupt = true)
            stage.show(next)
            s.stageTheme = next
            seg(7)
        }

        // 5. Apply PIXEL: the stage chrome dissolves, the stage relaxes into home, the rail returns.
        stage.coverForTest(liveImage(SceneTheme.PIXEL), 1f)
        s.layout = IntSize(home.width.toInt(), home.height.toInt())
        seg(8) { t ->
            s.stageChrome = 1f - fade(t, Motion.FADE_OUT_MS.toFloat())
            val k = spring(Motion.Smooth, t)
            s.rect = { lerp(full, home, k) }
            s.railShown = k
            s.galleryAlpha = 0f; s.recede = 0f
            s.tab = Tab.HOME
            s.homeAlpha = fade(t - 200f, Motion.FADE_IN_MS.toFloat())
            s.confirm = fade(t - 300f, Motion.FADE_IN_MS.toFloat())
            if (t >= 500f) stage.coverForTest(liveImage(SceneTheme.PIXEL), (1f - (t - 500f) / Motion.SWAP_MS).coerceIn(0f, 1f))
        }
        s.stageTheme = null
        stage.coverForTest(null, 0f)
        seg(6) { t -> s.confirm = 1f - fade(t - 400f, Motion.FADE_OUT_MS.toFloat()) }
        s.confirm = 0f

        // 6. A demo: the picker rises, a pick collapses it toward the badge, the demo's theme arrives first.
        seg(5) { t -> s.sheet = spring(Motion.Smooth, t) }
        seg(3)
        s.sheetCollapse = true
        stage.hint = PassageHint(PassageStyle.ENTRANCE)
        stage.show(SceneTheme.AQUARIUM)
        seg(8) { t -> s.sheet = 1f - spring(Motion.Smooth, t) }
        s.sheet = 0f; s.sheetCollapse = false

        // 7. Moving: the parked chrome fades first, the scene grows to full-bleed, then the controls fade in.
        s.speed = 55f
        s.caption = "가속하면 수면과 수초가 뒤로 기울어요"
        stage.coverForTest(liveImage(SceneTheme.AQUARIUM), 1f)
        s.layout = IntSize(w.toInt(), h.toInt())
        seg(7) { t ->
            s.chrome = 1f - fade(t, Motion.FADE_OUT_MS.toFloat())
            val k = spring(Motion.Smooth, t - Motion.P8_SCENE_DELAY_MS)
            s.rect = { lerp(home, full, k) }
            s.moving = fade(t - Motion.P8_CONTROLS_DELAY_MS, Motion.FADE_IN_MS.toFloat())
            if (t >= 600f) stage.coverForTest(liveImage(SceneTheme.AQUARIUM), (1f - (t - 600f) / Motion.SWAP_MS).coerceIn(0f, 1f))
        }
        stage.coverForTest(null, 0f)
        seg(8) { t -> if (t > 500f) s.chip = "물결이 기울어요" }

        // 8. Parked again: the toggles fade, the scene returns to home, the rail and badge come back; the trip card.
        s.speed = 0f; s.caption = null
        stage.coverForTest(liveImage(SceneTheme.AQUARIUM), 1f)
        s.layout = IntSize(home.width.toInt(), home.height.toInt())
        seg(7) { t ->
            s.moving = 1f - fade(t, Motion.FADE_OUT_MS.toFloat())
            val k = spring(Motion.Smooth, t - Motion.P8_SCENE_DELAY_MS)
            s.rect = { lerp(full, home, k) }
            s.chrome = spring(Motion.Smooth, t - 2 * Motion.P8_SCENE_DELAY_MS)
            if (t >= 600f) stage.coverForTest(liveImage(SceneTheme.AQUARIUM), (1f - (t - 600f) / Motion.SWAP_MS).coerceIn(0f, 1f))
        }
        stage.coverForTest(null, 0f)
        seg(8) { t -> s.trip = spring(Motion.Smooth, t) }
        seg(3) { t -> s.trip = 1f - fade(t, Motion.FADE_OUT_MS.toFloat()) }
        s.trip = 0f

        // 9. The user's own theme returns through a quiet passage.
        stage.hint = PassageHint(PassageStyle.ENTRANCE, intensity = 0.6f)
        stage.show(SceneTheme.PIXEL)
        seg(9)
    }

    /** P3: [t]'s card lifts, then grows into the stage while the gallery recedes; the stage chrome rises at 70 %. */
    private fun enter(t: SceneTheme) {
        val from = card(t)
        if (t != stage.live) { stage.hint = PassageHint(PassageStyle.GROW); stage.show(t) }
        s.stageTheme = t
        seg(9) { ms ->
            val k = spring(Motion.Passage, ms)
            s.rect = { lerp(from, full, k) }
            s.corner = 32f * (1 - k)
            s.recede = k
            s.railShown = 1f - k
            s.stageChrome = if (k >= 0.7f) fade(ms - 400f, Motion.FADE_IN_MS.toFloat()) else 0f
        }
        seg(4)
    }

    /** P4 for a preview: the previewed world contracts into its card as a still; the live scene returns quietly. */
    private fun enterAndClose(t: SceneTheme) {
        enter(t)
        s.ghost = liveImage(t)
        stills.put(t, liveImage(t))
        stage.holdSnapshot(still(SceneTheme.KART))
        stage.hint = PassageHint(PassageStyle.ENTRANCE, intensity = 0.5f, snapshotHeld = true)
        stage.show(SceneTheme.KART)
        s.rect = { card(SceneTheme.KART) }
        s.corner = 32f
        s.stageTheme = null
        seg(8) { ms ->
            val k = spring(Motion.Passage, ms)
            s.ghostRect = { lerp(full, card(t), k) }
            s.ghostCorner = 32f * k
            s.recede = 1 - k
            s.railShown = k
        }
        s.ghost = null
        seg(2)
    }
}
