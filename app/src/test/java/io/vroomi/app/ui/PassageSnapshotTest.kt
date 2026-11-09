package io.vroomi.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import io.vroomi.app.diary.TripRecord
import io.vroomi.app.ui.design.Ambience
import io.vroomi.app.ui.design.ChromeColors
import io.vroomi.app.ui.design.Layout
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalSceneBackdrop
import io.vroomi.app.ui.design.SceneBackdrop
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.motion.LocalMotion
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.MotionPrefs
import io.vroomi.app.ui.passage.HeroStills
import io.vroomi.app.ui.passage.PassageHint
import io.vroomi.app.ui.passage.PassagePhase
import io.vroomi.app.ui.passage.PassageStyle
import io.vroomi.app.ui.passage.SceneStage
import io.vroomi.app.ui.passage.StageController
import io.vroomi.app.ui.passage.StagePlacement
import io.vroomi.character.CharacterSkin
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.render.SceneStillRenderer
import io.vroomi.character.render.StillSpec
import io.vroomi.core.vehicle.sim.Scenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * The interface and its passages, frame by frame (docs/screenshots ui_*). Every passage frame is the real
 * [SceneStage] driven by the real [StageController]; the live scene is stood in for by the theme's still.
 */
class PassageSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = TestScreen.device,
        maxPercentDifference = 100.0,
    )

    // Root px (the stage is placed in px).
    private val w = TestScreen.pxW.toFloat()
    private val h = TestScreen.pxH.toFloat()
    private val rail = 132f * TestScreen.scale
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val renderer by lazy { SceneStillRenderer(createFontFamilyResolver(paparazzi.context), Density(1f)) }
    private val cache = HashMap<Triple<SceneTheme, CharacterSkin, Float>, ImageBitmap>()
    /** The theme's still; [intro] < 1 draws the theme's own entrance accent (the live scene during an entrance). */
    private fun still(t: SceneTheme, skin: CharacterSkin = CharacterSkin.VROOMI, intro: Float = 1f): ImageBitmap =
        cache.getOrPut(Triple(t, skin, intro)) { renderer.render(t, HeroStills.W, HeroStills.H, StillSpec(skin = skin, intro = intro)) }

    private fun stills(vararg t: SceneTheme): HeroStills = HeroStills(renderer, scope, synchronous = true).also { s -> t.forEach { s.put(it, still(it)) } }

    private fun shot(theme: SceneTheme, dark: Boolean = false, content: @Composable () -> Unit) {
        val chrome = ChromeColors(Ambience.of(theme), dark)
        io.vroomi.character.render.theme.ChromeZones.screenH = TestScreen.dpH
        paparazzi.snapshot { VroomiTheme(night = dark, theme = theme) {
            CompositionLocalProvider(LocalMotion provides MotionPrefs(still = true), LocalChrome provides chrome) {
                Box(Modifier.fillMaxSize().drawBehind { drawRect(chrome.backdrop) }) { content() }
            }
        } }
    }

    @Composable
    private fun LiveStill(t: SceneTheme?, modifier: Modifier, skin: CharacterSkin = CharacterSkin.VROOMI, intro: Float = 1f) {
        if (t == null) return
        Image(still(t, skin, intro), null, modifier, contentScale = ContentScale.Crop, filterQuality = if (t == SceneTheme.PIXEL) FilterQuality.None else FilterQuality.Low)
    }

    /** The stage at [rect] with the live scene laid out at [layout]. */
    @Composable
    private fun Stage(
        stage: StageController, stills: HeroStills, rect: Rect, layout: IntSize = IntSize(w.toInt(), h.toInt()), corner: Float = 0f,
        backdrop: SceneBackdrop = SceneBackdrop(), skin: CharacterSkin = CharacterSkin.VROOMI,
    ) {
        val chrome = LocalChrome.current
        CompositionLocalProvider(LocalSceneBackdrop provides backdrop) {
            SceneStage(stage, stills, StagePlacement(rect = { rect }, corner = { corner }), layout, { chrome.backdrop }, { chrome.tint }, backdrop) { m ->
                LiveStill(stage.live, m, skin, stage.intro.floatValue)
            }
        }
    }

    private fun ctx(skin: CharacterSkin = CharacterSkin.VROOMI) =
        PickerContext(skin, CalibreFinish.AUTO, EnginePowertrain.AUTO, io.vroomi.character.render.calibre.DEFAULT_NOW_MS, 0L)

    // ---------------------------------------------------------------- gallery

    @Composable
    private fun GalleryScreen(applied: SceneTheme, peek: SceneTheme? = null) {
        val i = ThemeRegistry.indexOf(applied)
        val shown = (i - 2..i + 2).filter { it in ThemeRegistry.all.indices }.map { ThemeRegistry.all[it].theme }
        val stills = remember { stills(*shown.toTypedArray()) }
        val state = remember {
            GalleryState(applied).also { s -> if (peek != null) { s.peek = peek; runBlocking { s.peekAnim.snapTo(1f) } } }
        }
        Row(Modifier.fillMaxSize()) {
            TabRail(Tab.FRIENDS) {}
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Gallery(state, applied, stills, chips = { ThemeOptionChips(applied, ctx()) {} }, onOpen = {}, staticStills = shown.associateWith { still(it) })
            }
        }
    }

    @Test fun ui_gallery() = shot(SceneTheme.CALIBRE) { GalleryScreen(SceneTheme.CALIBRE) }
    @Test fun ui_gallery_dark() = shot(SceneTheme.CALIBRE, dark = true) { GalleryScreen(SceneTheme.CALIBRE) }
    @Test fun ui_gallery_peek() = shot(SceneTheme.CALIBRE) { GalleryScreen(SceneTheme.CALIBRE, peek = SceneTheme.CALIBRE) }

    // ---------------------------------------------------------------- stage (one per theme)

    private fun newStage(t: SceneTheme) = StageController(t, scope) { Ambience.of(it).luminance }

    private fun stageShot(t: SceneTheme, dark: Boolean = false) = newStage(t).let { stage -> shot(t, dark) {
        val stills = remember { stills(t) }
        Stage(stage, stills, Rect(0f, 0f, w, h))
        StageChrome(t, shown = true, leaving = false, onClose = {}, onPrev = {}, onNext = {}, onApply = {}, onDemo = {},
            chips = { ThemeOptionChips(t, ctx()) {} })
    } }

    @Test fun ui_stage_kart() = stageShot(SceneTheme.KART)
    @Test fun ui_stage_paper() = stageShot(SceneTheme.PAPER)
    @Test fun ui_stage_calibre() = stageShot(SceneTheme.CALIBRE)
    @Test fun ui_stage_engine() = stageShot(SceneTheme.ENGINE)
    @Test fun ui_stage_music_box() = stageShot(SceneTheme.MUSIC_BOX)
    @Test fun ui_stage_marble() = stageShot(SceneTheme.MARBLE)
    @Test fun ui_stage_terrarium() = stageShot(SceneTheme.TERRARIUM)
    @Test fun ui_stage_aquarium() = stageShot(SceneTheme.AQUARIUM)
    @Test fun ui_stage_orbit() = stageShot(SceneTheme.ORBIT)
    @Test fun ui_stage_map() = stageShot(SceneTheme.TREASURE_MAP)
    @Test fun ui_stage_pixel() = stageShot(SceneTheme.PIXEL)
    @Test fun ui_stage_kart_dark() = stageShot(SceneTheme.KART, dark = true)
    @Test fun ui_stage_calibre_dark() = stageShot(SceneTheme.CALIBRE, dark = true)
    @Test fun ui_stage_orbit_dark() = stageShot(SceneTheme.ORBIT, dark = true)

    // ---------------------------------------------------------------- entrances and passages

    /** [from] -> [to] on the full stage, stopped at the entrance's time fraction [at]. */
    private fun passageShot(from: SceneTheme, to: SceneTheme, at: Float) = shot(to) {
        val stage = remember {
            StageController(from, scope) { Ambience.of(it).luminance }.also { s ->
                s.hint = PassageHint(PassageStyle.ENTRANCE)
                s.show(to)
                s.advanceForTest(3000f, still(from)) { o -> o.phase == PassagePhase.REVEALING && o.revealTime >= at }
            }
        }
        val stills = remember { stills(from, to) }
        Stage(stage, stills, Rect(0f, 0f, w, h))
    }

    private fun prev(t: SceneTheme): SceneTheme = ThemeRegistry.all[(ThemeRegistry.indexOf(t) + ThemeRegistry.all.size - 1) % ThemeRegistry.all.size].theme

    @Test fun ui_entrance_kart_50() = passageShot(prev(SceneTheme.KART), SceneTheme.KART, 0.5f)
    @Test fun ui_entrance_paper_50() = passageShot(prev(SceneTheme.PAPER), SceneTheme.PAPER, 0.5f)
    @Test fun ui_entrance_calibre_50() = passageShot(prev(SceneTheme.CALIBRE), SceneTheme.CALIBRE, 0.5f)
    @Test fun ui_entrance_engine_50() = passageShot(prev(SceneTheme.ENGINE), SceneTheme.ENGINE, 0.5f)
    @Test fun ui_entrance_music_box_50() = passageShot(prev(SceneTheme.MUSIC_BOX), SceneTheme.MUSIC_BOX, 0.5f)
    @Test fun ui_entrance_marble_50() = passageShot(prev(SceneTheme.MARBLE), SceneTheme.MARBLE, 0.5f)
    @Test fun ui_entrance_terrarium_50() = passageShot(prev(SceneTheme.TERRARIUM), SceneTheme.TERRARIUM, 0.5f)
    @Test fun ui_entrance_aquarium_50() = passageShot(prev(SceneTheme.AQUARIUM), SceneTheme.AQUARIUM, 0.5f)
    @Test fun ui_entrance_orbit_50() = passageShot(prev(SceneTheme.ORBIT), SceneTheme.ORBIT, 0.5f)
    @Test fun ui_entrance_map_50() = passageShot(prev(SceneTheme.TREASURE_MAP), SceneTheme.TREASURE_MAP, 0.5f)
    @Test fun ui_entrance_pixel_50() = passageShot(prev(SceneTheme.PIXEL), SceneTheme.PIXEL, 0.5f)

    @Test fun ui_passage_kart_calibre_25() = passageShot(SceneTheme.KART, SceneTheme.CALIBRE, 0.25f)
    @Test fun ui_passage_kart_calibre_50() = passageShot(SceneTheme.KART, SceneTheme.CALIBRE, 0.5f)
    @Test fun ui_passage_kart_calibre_75() = passageShot(SceneTheme.KART, SceneTheme.CALIBRE, 0.75f)
    @Test fun ui_passage_engine_music_box_25() = passageShot(SceneTheme.ENGINE, SceneTheme.MUSIC_BOX, 0.25f)
    @Test fun ui_passage_engine_music_box_50() = passageShot(SceneTheme.ENGINE, SceneTheme.MUSIC_BOX, 0.5f)
    @Test fun ui_passage_engine_music_box_75() = passageShot(SceneTheme.ENGINE, SceneTheme.MUSIC_BOX, 0.75f)
    @Test fun ui_passage_aquarium_pixel_25() = passageShot(SceneTheme.AQUARIUM, SceneTheme.PIXEL, 0.25f)
    @Test fun ui_passage_aquarium_pixel_50() = passageShot(SceneTheme.AQUARIUM, SceneTheme.PIXEL, 0.5f)
    @Test fun ui_passage_aquarium_pixel_75() = passageShot(SceneTheme.AQUARIUM, SceneTheme.PIXEL, 0.75f)
    @Test fun ui_passage_paper_map_25() = passageShot(SceneTheme.PAPER, SceneTheme.TREASURE_MAP, 0.25f)
    @Test fun ui_passage_paper_map_50() = passageShot(SceneTheme.PAPER, SceneTheme.TREASURE_MAP, 0.5f)
    @Test fun ui_passage_paper_map_75() = passageShot(SceneTheme.PAPER, SceneTheme.TREASURE_MAP, 0.75f)

    // ---------------------------------------------------------------- home, tabs, driving

    private val trip = TripRecord(1, 0, 1_200_000, 12.4f, 2, 0, 1, 0, 3, 0, 94, stars = 41, bestCombo = 22, biomes = 5)

    @Composable
    private fun HomeLayer(
        t: SceneTheme, chromeAlpha: Float = 1f, sceneRect: Rect = Rect(rail, 0f, w, h), bubble: String? = "오늘도 같이 달려요!",
        stage: StageController = homeStage, skin: CharacterSkin = CharacterSkin.VROOMI,
    ) {
        val stills = remember { stills(t) }
        Row(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha }) { TabRail(Tab.HOME) {} }
        Stage(stage, stills, sceneRect, IntSize(sceneRect.width.toInt(), sceneRect.height.toInt()), skin = skin)
        Box(Modifier.fillMaxSize().padding(start = 132.dp).padding(Space.parked).graphicsLayer { alpha = chromeAlpha }) {
            BondBadge(3, 128, 0.62f, "다음 레벨까지 7.4 km", Modifier.align(Alignment.TopStart))
            if (bubble != null) SpeechBubble(bubble, Modifier.align(Alignment.TopCenter))
            HomeActions(demo = false, listening = false, canTalk = true, modifier = Modifier.align(Alignment.BottomEnd), onDemo = {}, onTalk = {})
        }
    }

    @Test fun ui_home() = shot(SceneTheme.KART) { HomeLayer(SceneTheme.KART) }
    @Test fun ui_home_dark() = shot(SceneTheme.KART, dark = true) { HomeLayer(SceneTheme.KART) }
    @Test fun ui_home_engine() = shot(SceneTheme.ENGINE) { HomeLayer(SceneTheme.ENGINE, stage = remember { newStage(SceneTheme.ENGINE) }, bubble = null) }
    @Test fun ui_home_calibre_dark() = shot(SceneTheme.CALIBRE, dark = true) { HomeLayer(SceneTheme.CALIBRE, stage = remember { newStage(SceneTheme.CALIBRE) }, bubble = null) }
    @Test fun ui_home_orbit_dark() = shot(SceneTheme.ORBIT, dark = true) { HomeLayer(SceneTheme.ORBIT, stage = remember { newStage(SceneTheme.ORBIT) }, bubble = null) }
    @Test fun ui_home_map() = shot(SceneTheme.TREASURE_MAP) { HomeLayer(SceneTheme.TREASURE_MAP, stage = remember { newStage(SceneTheme.TREASURE_MAP) }, bubble = null) }

    @Test fun ui_home_to_tab_50() = shot(SceneTheme.KART) {
        // Halfway: the scene's bounds between full-bleed and the 42 % pane (the old frame covers the new layout),
        // the panel arriving on its horizontal axis.
        val pane = Rect(rail, 0f, rail + (w - rail) * Layout.SCENE_PANE, h)
        val home = Rect(rail, 0f, w, h)
        val k = Motion.Smooth.progress(Motion.Smooth.settleMs * 0.25)
        val mid = Rect(home.left, 0f, home.right + (pane.right - home.right) * k, h)
        val stage = remember { StageController(SceneTheme.KART, scope) { Ambience.of(it).luminance }.also { it.coverForTest(still(SceneTheme.KART), 1f) } }
        val stills = remember { stills(SceneTheme.KART) }
        Row(Modifier.fillMaxSize()) {
            TabRail(Tab.DIARY) {}
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(Layout.SCENE_PANE).fillMaxHeight())
                    Box(Modifier.weight(1f - Layout.SCENE_PANE).fillMaxHeight().drawBehind { drawRect(Color.White) }.graphicsLayer {
                        alpha = 0.5f; translationX = 12.dp.toPx()
                    }) { DiaryPanel(listOf(trip, trip.copy(id = 2, startMs = 86_400_000, score = 81, sketch = sketch()))) }
                }
            }
        }
        Stage(stage, stills, mid, IntSize(pane.width.toInt(), pane.height.toInt()))
    }

    @Test fun ui_friend_crossfade_50() = shot(SceneTheme.KART) {
        // Another friend, halfway: the old friend's frame fades out over the new one, which stays fully opaque
        // (the scene around them is the same, so it must not dip toward the backdrop).
        val stage = remember {
            newStage(SceneTheme.KART).also { it.coverForTest(still(SceneTheme.KART, CharacterSkin.VROOMI), 0.5f, keepsLive = true) }
        }
        HomeLayer(SceneTheme.KART, bubble = null, stage = stage, skin = CharacterSkin.SSUK)
    }

    private fun sketch(): List<Float> = (0 until 64).flatMap { i -> val t = i / 63f; listOf(t, 0.5f + 0.35f * kotlin.math.sin(t * 5f) * (1 - t)) }

    @Test fun ui_parked_to_moving_0() = shot(SceneTheme.KART) { HomeLayer(SceneTheme.KART) }

    @Test fun ui_parked_to_moving_50() = shot(SceneTheme.KART) {
        // 200 ms after the car starts moving: the parked chrome has faded (160 ms), the scene grows to full-bleed
        // (it started at 80 ms), the controls have not arrived yet (300 ms).
        val k = Motion.Smooth.progress(120.0)
        HomeLayer(SceneTheme.KART, chromeAlpha = 0f, sceneRect = Rect(rail * (1 - k), 0f, w, h), bubble = null)
    }

    @Test fun ui_parked_to_moving_100() = shot(SceneTheme.KART) {
        HomeLayer(SceneTheme.KART, chromeAlpha = 0f, sceneRect = Rect(0f, 0f, w, h), bubble = null)
        MovingOverlay("부드러운 주행 중", muted = false, calm = false, onMute = {}, onCalm = {}, demoCaption = null, demoProgress = null, onEndDemo = {})
    }

    private val orbitStage by lazy { newStage(SceneTheme.ORBIT) }
    private val homeStage by lazy { newStage(SceneTheme.KART) }

    @Test fun ui_moving_chip() = shot(SceneTheme.ORBIT, dark = true) {
        val stage = orbitStage
        val stills = remember { stills(SceneTheme.ORBIT) }
        Stage(stage, stills, Rect(0f, 0f, w, h))
        MovingOverlay("궤도 비행", muted = true, calm = false, onMute = {}, onCalm = {},
            demoCaption = "에코 모드 → 푸른 이온 불꽃", demoProgress = 0.42f, onEndDemo = {})
    }

    @Test fun ui_sheet_demo_picker() = shot(SceneTheme.KART) {
        HomeLayer(SceneTheme.KART, bubble = null)
        DemoPicker(
            listOf(
                Scenario("highlight", "브루미 하이라이트 (3분)", 172f, emptyList(), "출발부터 도착까지, 브루미가 반응하는 모든 순간을 한 번에"),
                Scenario("commute", "출근길 (3분)", 182f, emptyList(), "도시 출퇴근: 신호 대기, 깜빡이, 에어컨, 급제동, 고속도로"),
                Scenario("rainy_night", "비 오는 밤 퇴근길 (2분)", 122f, emptyList(), "전조등, 비, 서리 제거, 길안내, 도착 축하"),
            ),
            onPick = {}, onClose = {},
        )
    }

    @Test fun ui_sheet_demo_picker_dark() = shot(SceneTheme.KART, dark = true) {
        HomeLayer(SceneTheme.KART, bubble = null)
        DemoPicker(
            listOf(
                Scenario("highlight", "브루미 하이라이트 (3분)", 172f, emptyList(), "출발부터 도착까지, 브루미가 반응하는 모든 순간을 한 번에"),
                Scenario("commute", "출근길 (3분)", 182f, emptyList(), "도시 출퇴근: 신호 대기, 깜빡이, 에어컨, 급제동, 고속도로"),
                Scenario("rainy_night", "비 오는 밤 퇴근길 (2분)", 122f, emptyList(), "전조등, 비, 서리 제거, 길안내, 도착 축하"),
            ),
            onPick = {}, onClose = {},
        )
    }

    @Test fun ui_sheet_finish() = shot(SceneTheme.CALIBRE) {
        HomeLayer(SceneTheme.CALIBRE, bubble = null, stage = remember { newStage(SceneTheme.CALIBRE) })
        BottomSheet(true, onDismiss = {}) { OptionSheet(ThemeOption.FINISH, SceneTheme.CALIBRE, ctx()) }
    }

    @Test fun ui_sheet_sound_dark() = shot(SceneTheme.MUSIC_BOX, dark = true) {
        HomeLayer(SceneTheme.MUSIC_BOX, bubble = null, stage = remember { newStage(SceneTheme.MUSIC_BOX) })
        BottomSheet(true, onDismiss = {}) { OptionSheet(ThemeOption.SOUND, SceneTheme.MUSIC_BOX, ctx()) }
    }

    @Test fun ui_welcome() = shot(SceneTheme.KART) {
        HomeLayer(SceneTheme.KART, bubble = null)
        WelcomeCard(onDemo = {}, onStart = {})
    }

    @Test fun ui_welcome_dark() = shot(SceneTheme.KART, dark = true) {
        HomeLayer(SceneTheme.KART, bubble = null)
        WelcomeCard(onDemo = {}, onStart = {})
    }

    @Test fun ui_trip_card() = shot(SceneTheme.KART) {
        HomeLayer(SceneTheme.KART, bubble = null)
        Box(Modifier.fillMaxSize().padding(start = 132.dp).padding(Space.parked), contentAlignment = Alignment.TopEnd) { TripSummaryCard(trip) {} }
    }

    @Test fun ui_trip_card_dark() = shot(SceneTheme.KART, dark = true) {
        HomeLayer(SceneTheme.KART, bubble = null)
        Box(Modifier.fillMaxSize().padding(start = 132.dp).padding(Space.parked), contentAlignment = Alignment.TopEnd) { TripSummaryCard(trip.copy(demo = true, score = 78)) {} }
    }

    @Test fun ui_rail_indicator_mid() = shot(SceneTheme.AQUARIUM) {
        Row(Modifier.fillMaxSize()) { TabRail(Tab.DIARY, debugFrom = 0f, debugAt = 1.1f) {} }
    }

    /** A tab page as the app lays it out on the wide screen: the rail, the scene in the 42 % pane, the page beside it. */
    @Composable
    private fun PaneLayer(t: SceneTheme, tab: Tab, page: @Composable () -> Unit) {
        val stills = remember { stills(t) }
        val stage = remember { newStage(t) }
        Row(Modifier.fillMaxSize()) {
            TabRail(tab) {}
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(Layout.SCENE_PANE).fillMaxHeight())
                    val bg = MaterialTheme.colorScheme.background
                    Box(Modifier.weight(1f - Layout.SCENE_PANE).fillMaxHeight().drawBehind { drawRect(bg) }) { page() }
                }
            }
        }
        val pane = Rect(rail, 0f, rail + (w - rail) * Layout.SCENE_PANE, h)
        Stage(stage, stills, pane, IntSize(pane.width.toInt(), pane.height.toInt()))
    }

    @Test fun ui_settings() = shot(SceneTheme.TERRARIUM) {
        PaneLayer(SceneTheme.TERRARIUM, Tab.SETTINGS) { SettingsContent(io.vroomi.app.settings.Settings(voiceRare = true), {}, {}) }
    }

    @Test fun ui_settings_dark() = shot(SceneTheme.ORBIT, dark = true) {
        PaneLayer(SceneTheme.ORBIT, Tab.SETTINGS) { SettingsContent(io.vroomi.app.settings.Settings(calm = true), {}, {}) }
    }

    @Test fun ui_diary() = shot(SceneTheme.KART) {
        PaneLayer(SceneTheme.KART, Tab.DIARY) { DiaryPanel(listOf(trip.copy(sketch = sketch()), trip.copy(id = 2, startMs = 86_400_000, endMs = 86_400_000 + 1_500_000, score = 81, demo = true), trip.copy(id = 3, startMs = 2 * 86_400_000, endMs = 2 * 86_400_000 + 900_000, score = 64))) }
    }

    @Test fun ui_wardrobe_dark() = shot(SceneTheme.AQUARIUM, dark = true) {
        PaneLayer(SceneTheme.AQUARIUM, Tab.WARDROBE) { WardrobeContent(3, setOf(io.vroomi.character.Accessory.SCARF)) { _, _ -> } }
    }
}
