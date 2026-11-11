package io.vroomi.app.ui

import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vroomi.app.BuildConfig
import io.vroomi.app.R
import io.vroomi.app.progress.Grade
import io.vroomi.app.progress.SafetyGrade
import io.vroomi.app.progress.ThemeAccess
import io.vroomi.app.runtime.EngineSound
import io.vroomi.app.runtime.MechanicalSound
import io.vroomi.app.runtime.VroomiRuntime
import io.vroomi.app.settings.CharacterSize
import io.vroomi.app.ui.design.Ambience
import io.vroomi.app.ui.design.ChromeColors
import io.vroomi.app.ui.design.ColorMath
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.LocalBlurEnabled
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalDark
import io.vroomi.app.ui.design.LocalSceneBackdrop
import io.vroomi.app.ui.design.MaterialToken
import io.vroomi.app.ui.design.Radius
import io.vroomi.app.ui.design.SceneBackdrop
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.VType
import io.vroomi.app.ui.design.material
import io.vroomi.app.ui.motion.LocalMotion
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.MotionPrefs
import io.vroomi.app.ui.motion.rememberHaptics
import io.vroomi.app.ui.passage.HeroStills
import io.vroomi.app.ui.passage.PassageHint
import io.vroomi.app.ui.passage.PassagePhase
import io.vroomi.app.ui.passage.PassageStyle
import io.vroomi.app.ui.passage.PullMath
import io.vroomi.app.ui.passage.SceneStage
import io.vroomi.app.ui.passage.StageController
import io.vroomi.app.ui.passage.StagePlacement
import io.vroomi.app.ui.passage.SwipeMath
import io.vroomi.character.CharacterTarget
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.Season
import io.vroomi.character.render.StillSpec
import io.vroomi.character.render.VroomiSceneState
import io.vroomi.character.render.calibre.CalibreSceneState
import io.vroomi.character.render.calibre.rememberCalibreSceneState
import io.vroomi.character.render.engine.EngineSceneState
import io.vroomi.character.render.engine.rememberEngineSceneState
import io.vroomi.character.render.rememberSceneStillRenderer
import io.vroomi.character.render.rememberVroomiSceneState
import io.vroomi.character.render.theme.ThemeSceneState
import io.vroomi.character.render.theme.isKitTheme
import io.vroomi.character.render.theme.releaseThemeLayers
import io.vroomi.character.render.theme.rememberThemeSceneState
import io.vroomi.character.theme.ThemeModels
import io.vroomi.core.voice.ListenState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

private val CharacterSize.scale: Float get() = when (this) { CharacterSize.SMALL -> 0.85f; CharacterSize.NORMAL -> 1f; CharacterSize.LARGE -> 1.15f }

/** Where the live scene sits on screen. */
private enum class Place { HOME, PANE, CARD, STAGE, FULL }

/**
 * The app's one screen. Layers, bottom to top: the backdrop; the base (rail, panels beside the scene, the
 * gallery); the one live scene in its stage ([SceneStage]); the chrome over the scene (home badges, the stage's
 * chrome, a panel over the scene on narrow screens); the overlays (moving controls, sheets, the welcome card).
 * Every change of theme, place and mode travels through the passages (docs: README "고급스러운 인터페이스와
 * 테마 전환").
 */
@Composable
fun AppRoot(rt: VroomiRuntime) {
    val target by rt.target.collectAsStateWithLifecycle()
    val moving by rt.isMoving.collectAsStateWithLifecycle()
    val settings by rt.settingsStore.settings.collectAsStateWithLifecycle()
    val snapshot by rt.hub.snapshot.collectAsStateWithLifecycle()
    val lastLine by rt.lastLine.collectAsStateWithLifecycle()
    val trips by rt.diary.trips.collectAsStateWithLifecycle()
    val demo by rt.demo.collectAsStateWithLifecycle()
    val caption by rt.demoCaption.collectAsStateWithLifecycle()
    val progress by rt.demoProgress.collectAsStateWithLifecycle()
    val demoTheme by rt.demoTheme.collectAsStateWithLifecycle()
    val demoPowertrain by rt.demoPowertrain.collectAsStateWithLifecycle()
    val demoModel by rt.demoModel.collectAsStateWithLifecycle()
    val lastTrip by rt.lastTrip.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val kart = rememberVroomiSceneState()
    val calibre = rememberCalibreSceneState()
    val engine = rememberEngineSceneState()
    val dark = LocalDark.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // ---------------------------------------------------------------- motion preferences
    val systemReduced = remember {
        runCatching { AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val prefs = MotionPrefs(
        reduced = systemReduced || settings.calm,
        moving = moving,
        slow = if (BuildConfig.DEBUG && settings.devSlowMotion) Motion.SLOW_FACTOR else 1f,
        parked = !moving,
    )
    val currentPrefs by rememberUpdatedState(prefs)

    // ---------------------------------------------------------------- what is shown
    val applied = ThemeRegistry.resolve(settings.sceneTheme)
    var staged by remember { mutableStateOf<SceneTheme?>(null) }          // the stage is open on this theme (a preview)
    var applying by remember { mutableStateOf<SceneTheme?>(null) }        // P6: holds the theme until the setting is written
    var demoPreroll by remember { mutableStateOf<SceneTheme?>(null) }     // P7: the demo's theme arrives before the drive
    var demoForced by remember { mutableStateOf<SceneTheme?>(null) }      // a demo started from the stage shows that theme
    var afterglow by remember { mutableStateOf(false) }                   // P7: the demo's theme stays until the trip card goes
    val lastDemo = remember { arrayOfNulls<SceneTheme>(1) }
    val runningDemoTheme = demoForced ?: demoTheme?.let(ThemeRegistry::resolve)
    if (runningDemoTheme != null) lastDemo[0] = runningDemoTheme
    val desired = staged ?: applying ?: demoPreroll ?: runningDemoTheme ?: (if (afterglow) lastDemo[0] else null) ?: applied

    val stage = remember { StageController(desired, scope) { Ambience.of(it).luminance } }
    val renderer = rememberSceneStillRenderer()
    val stills = remember(renderer) { HeroStills(renderer, scope) }
    val clock by produceState(Calendar.getInstance()) { while (true) { delay(60_000); value = Calendar.getInstance() } }
    val hour = clock.get(Calendar.HOUR_OF_DAY) + clock.get(Calendar.MINUTE) / 60f
    val season = Season.ofMonth(clock.get(Calendar.MONTH) + 1)
    stills.spec = StillSpec(skin = settings.skin, finish = settings.calibreFinish, powertrain = settings.enginePowertrain, hourOfDay = hour, season = season)

    LaunchedEffect(stage) { stage.run { currentPrefs } }
    LaunchedEffect(desired) { stage.show(desired) }
    val safety = snapshot.safetyWarningActive == true
    LaunchedEffect(safety) { stage.safety(safety) }
    LaunchedEffect(moving) { stage.moving(moving) }

    // ---------------------------------------------------------------- chrome colours (Oklab, over the passage)
    val chrome = remember {
        ChromeColors(Ambience.of(desired), dark).also { c -> if (settings.launchBackdrop != 0) c.backdropArgb = settings.launchBackdrop }
    }
    chrome.dark = dark
    LaunchedEffect(stage, dark) {
        var startTint = chrome.tintArgb; var startBack = chrome.backdropArgb
        var lastTo: SceneTheme? = null
        snapshotFlow { stage.to to stage.tintMix.floatValue }.collect { (to, mix) ->
            if (to != lastTo) { startTint = chrome.tintArgb; startBack = chrome.backdropArgb; lastTo = to }
            val amb = Ambience.of(to ?: applied)
            chrome.tintArgb = ColorMath.lerpOklab(startTint, amb.tintFor(dark), mix)
            chrome.backdropArgb = ColorMath.lerpOklab(startBack, amb.backdrop, mix)
            chrome.onTintArgb = amb.onTint(dark)
        }
    }
    // Remember the shown theme's backdrop: the next launch fades to it.
    LaunchedEffect(stage.phase, stage.live) {
        val l = stage.live ?: return@LaunchedEffect
        if (stage.phase == PassagePhase.IDLE) {
            val b = Ambience.of(l).backdrop
            if (settings.launchBackdrop != b) rt.settingsStore.update { it.copy(launchBackdrop = b) }
        }
    }

    // ---------------------------------------------------------------- the live scene (the one live theme scene)
    val liveTheme = stage.live
    val kit = if (liveTheme != null && isKitTheme(liveTheme)) rememberThemeSceneState(liveTheme) { rt.settingsStore.readThemeState(it) } else null
    val host = SceneHost(
        theme = liveTheme ?: applied, kart = kart, calibre = calibre, finish = settings.calibreFinish,
        frameTime = BuildConfig.DEBUG && settings.devFrameTime, engine = engine, powertrain = settings.enginePowertrain,
        forcedPowertrain = demoPowertrain, kit = kit,
    )
    stage.onRelease = { keep ->
        releaseThemeLayers(if (keep != null && isKitTheme(keep)) keep else null)
        if (keep != SceneTheme.CALIBRE) calibre.releaseLayers()
        if (keep != SceneTheme.ENGINE) engine.releaseLayers()
    }
    CalibreKeeper(rt, calibre, liveTheme, moving)
    EngineKeeper(rt, engine, liveTheme)
    ThemeKeeper(rt, kit, liveTheme)
    ThemeSoundKeeper(rt, kit, liveTheme) { stage.phase == PassagePhase.IDLE || stage.phase == PassagePhase.SETTLING }

    // The upgrade step of the shown theme: a demo shows its own step, otherwise the user's choice for that theme.
    val shownTheme = liveTheme ?: applied
    // The grade shown (every grade in the developer preview) decides each theme's step; a locked step tapped in the
    // model sheet is only previewed ([modelPreview], never saved) until the stage, the tab or the car moves on.
    val grade = remember(trips, settings.gradeReached, settings.devAllGrades) { VroomiRuntime.shownGrade(trips, settings) }
    val realGrade = remember(trips, settings.gradeReached) { SafetyGrade.current(trips, settings.gradeReached) }
    var modelPreview by remember { mutableStateOf<Pair<SceneTheme, Int>?>(null) }
    val modelOf: (SceneTheme) -> Int = { t -> modelPreview?.takeIf { it.first == t }?.second ?: ThemeAccess.effectiveModel(grade, settings.models[t]) }
    val sceneModel = demoModel ?: modelOf(shownTheme)
    stills.modelOf = modelOf
    val options = SceneOptions(
        calm = settings.calm, reducedMotion = systemReduced, bubbleColor = MaterialTheme.colorScheme.surface,
        sizeScale = settings.size.scale, hourOfDay = hour, season = season, skin = settings.skin, model = sceneModel,
    )
    var firstFrameSeen by remember { mutableStateOf(false) }

    // ---------------------------------------------------------------- P8 choreography (parked <-> moving)
    var sceneMoving by remember { mutableStateOf(moving) }
    var controlsShown by remember { mutableStateOf(moving) }
    var parkedChrome by remember { mutableStateOf(!moving) }
    val parkedAlpha = remember { Animatable(if (moving) 0f else 1f) }
    var optionSheet by remember { mutableStateOf<ThemeOption?>(null) }
    var demoSheet by remember { mutableStateOf(false) }
    var sheetAmount by remember { mutableStateOf(0f) }
    // A previewed (locked) step ends when the stage, the tab or the parked state changes.
    LaunchedEffect(staged, tab, moving) { modelPreview = null }
    /** The model sheet's pick: an open step is saved (the best open one is "follow the grade"), a locked one is previewed. */
    fun pickModel(t: SceneTheme, m: Int) {
        val realMax = ThemeAccess.maxModel(realGrade)
        if ((BuildConfig.DEBUG && settings.devAllGrades) || m > realMax) { modelPreview = t to m; return }
        modelPreview = null
        rt.settingsStore.update { s -> s.copy(models = if (m >= realMax) s.models - t else s.models + (t to m)) }
    }
    LaunchedEffect(moving) {
        if (moving) {
            parkedChrome = false
            if (staged != null) { staged = null; if (demo && tab == Tab.THEMES) tab = Tab.HOME }
            optionSheet = null; demoSheet = false
            delay(prefs.ms(Motion.P8_SCENE_DELAY_MS)); sceneMoving = true
            delay(prefs.ms(Motion.P8_CONTROLS_DELAY_MS - Motion.P8_SCENE_DELAY_MS)); controlsShown = true
        } else {
            controlsShown = false
            delay(prefs.ms(Motion.P8_SCENE_DELAY_MS)); sceneMoving = false
            delay(prefs.ms(Motion.P8_SCENE_DELAY_MS)); parkedChrome = true
        }
    }
    LaunchedEffect(parkedChrome) {
        parkedAlpha.animateTo(if (parkedChrome) 1f else 0f, if (parkedChrome) prefs.fade(Motion.FADE_IN_MS) else prefs.fade(Motion.FADE_OUT_MS))
    }

    // ---------------------------------------------------------------- P1 launch
    val still = prefs.still
    val launch = remember { Animatable(if (still) 1f else 0f) }        // vroomi_night -> the theme's backdrop
    val sceneIn = remember { Animatable(if (still) 1f else 0f) }       // the scene fades up
    val chromeIn = remember { Animatable(if (still) 1f else 0f) }      // rail and badge rise 12 dp
    LaunchedEffect(Unit) { launch.animateTo(1f, prefs.fade(if (prefs.reduced) Motion.REDUCED_MS else Motion.LAUNCH_BACKDROP_MS)) }
    LaunchedEffect(firstFrameSeen, launch.isRunning) {
        if (!firstFrameSeen || launch.value < 1f || sceneIn.value >= 1f) return@LaunchedEffect
        launch { sceneIn.animateTo(1f, prefs.fade(if (prefs.reduced) Motion.REDUCED_MS else Motion.LAUNCH_SCENE_MS)) }
        delay(prefs.ms(Motion.LAUNCH_CHROME_DELAY_MS))
        chromeIn.animateTo(1f, if (prefs.reduced) prefs.fade(Motion.REDUCED_MS) else prefs.spring(Motion.Smooth))
    }
    // A safety net: never leave the screen dark if a first frame is not reported.
    LaunchedEffect(Unit) { delay(Motion.FIRST_FRAME_FALLBACK_MS.toLong()); firstFrameSeen = true }

    // ---------------------------------------------------------------- P7 demo afterglow and return
    LaunchedEffect(demo) {
        if (demo) { afterglow = true; return@LaunchedEffect }
        if (!afterglow) return@LaunchedEffect
        // These are StateFlows, not Compose state: collected directly (a snapshotFlow over .value would see only the
        // first value, and a demo ended mid-drive would keep its theme on screen for good).
        rt.isMoving.first { !it }
        withTimeoutOrNull(prefs.ms(Motion.TRIP_RETURN_MS)) { rt.lastTrip.first { it == null } }
        stage.hint = PassageHint(PassageStyle.ENTRANCE, intensity = 0.6f)
        afterglow = false; demoForced = null
    }

    val gallery = remember { GalleryState(applied) }
    val appliedRef = rememberUpdatedState(applied)
    var ghost by remember { mutableStateOf<GhostState?>(null) }
    var confirmChip by remember { mutableStateOf(false) }
    var chromeLeaving by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalMotion provides prefs, LocalChrome provides chrome) { VroomiTheme(night = dark, theme = stage.to ?: applied) {
        BoxWithConstraints(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Color(ColorMath.lerpOklab(VroomiColors.Night.toArgb(), chrome.backdropArgb, launch.value)))
            },
        ) {
            val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
            // The painters keep their readings out of the chrome's corners; they need the window's real height to tell
            // the live scene (always this tall) from a miniature of it, on any display density.
            val windowH = maxHeight.value
            androidx.compose.runtime.SideEffect { io.vroomi.character.render.theme.ChromeZones.screenH = windowH }
            val wide = maxWidth >= Dimens.wideBreak
            val railPx = with(density) { Dimens.railWidth.toPx() }
            val cardCorner = with(density) { Radius.r24.toPx() }
            val panelTab = tab == Tab.DIARY || tab == Tab.GRADE || tab == Tab.SETTINGS

            val place = when {
                sceneMoving -> Place.FULL
                tab == Tab.THEMES && staged != null -> Place.STAGE
                tab == Tab.THEMES -> Place.CARD
                panelTab && wide -> Place.PANE
                else -> Place.HOME
            }
            fun cardRect(theme: SceneTheme): Rect? = gallery.cardRect(theme)?.let { r ->
                val s = gallery.scaleOf(theme)
                Rect(r.center.x - r.width * s / 2f, r.center.y - r.height * s / 2f, r.center.x + r.width * s / 2f, r.center.y + r.height * s / 2f)
            }
            fun rectFor(p: Place): Rect? = when (p) {
                Place.FULL, Place.STAGE -> Rect(0f, 0f, wPx, hPx)
                Place.HOME -> Rect(railPx, 0f, wPx, hPx)
                Place.PANE -> Rect(railPx, 0f, railPx + (wPx - railPx) * io.vroomi.app.ui.design.Layout.SCENE_PANE, hPx)
                Place.CARD -> cardRect(appliedRef.value)
            }
            fun layoutFor(p: Place): IntSize = when (p) {
                Place.CARD, Place.STAGE, Place.FULL -> IntSize(wPx.toInt(), hPx.toInt())
                else -> rectFor(p)!!.let { IntSize(it.width.toInt(), it.height.toInt()) }
            }
            val rectAnim = remember { Animatable(rectFor(place) ?: Rect(railPx, 0f, wPx, hPx), Rect.VectorConverter) }
            val cornerAnim = remember { Animatable(if (place == Place.CARD) cardCorner else 0f) }
            var layout by remember { mutableStateOf(layoutFor(if (place == Place.CARD) Place.STAGE else place)) }
            var following by remember { mutableStateOf(false) }
            var snapNext by remember { mutableStateOf(false) }
            val pull = remember { Animatable(0f) }

            LaunchedEffect(place, wPx, hPx) {
                if (following) rectFor(Place.CARD)?.let { rectAnim.snapTo(it) }
                following = false
                val spec = if (place == Place.STAGE || place == Place.CARD) Motion.Passage else Motion.Smooth
                val r0 = snapshotFlow { rectFor(place) }.first { it != null }!!
                val newLayout = layoutFor(place)
                if (newLayout != layout) stage.coverForBounds { layout = newLayout }
                val corner = if (place == Place.CARD) cardCorner else 0f
                if (snapNext || prefs.reduced || still) {
                    snapNext = false
                    rectAnim.snapTo(r0); cornerAnim.snapTo(corner)
                } else {
                    launch { cornerAnim.animateTo(corner, prefs.spring(spec)) }
                    if (place == Place.CARD) {
                        // Follow the card while it settles, then exactly (the shelf may scroll).
                        val anim = launch { rectAnim.animateTo(r0, prefs.spring(spec)) }
                        launch { snapshotFlow { rectFor(place) }.collect { r -> if (r != null && anim.isActive) rectAnim.animateTo(r, prefs.spring(spec)) } }
                        anim.join()
                    } else rectAnim.animateTo(r0, prefs.spring(spec))
                }
                following = place == Place.CARD
                stage.uncover(prefs)
            }

            // The gallery's open/closed amount (recede of the shelf, the stage chrome's cue at 70 %).
            val stageAmount = remember { Animatable(0f) }
            LaunchedEffect(staged != null) {
                stageAmount.animateTo(if (staged != null) 1f else 0f, if (prefs.reduced) prefs.fade(Motion.REDUCED_MS) else prefs.spring(Motion.Passage))
            }

            val sceneBackdrop = remember { SceneBackdrop() }
            sceneBackdrop.blurAllowed = !moving
            val blurOk = LocalBlurEnabled.current

            // ------------------------------------------------ stage actions (P3 - P6)
            fun openCard(theme: SceneTheme) {
                if (moving) return
                haptics.select()
                if (theme != stage.live) {
                    stage.hint = PassageHint(PassageStyle.GROW)
                    stage.show(theme)
                    cardRect(theme)?.let { r ->
                        following = false
                        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { rectAnim.snapTo(r); cornerAnim.snapTo(cardCorner) }
                    }
                }
                staged = theme
            }
            fun closeStage() {
                val x = staged ?: return
                scope.launch { pull.animateTo(0f, prefs.spring(Motion.Smooth)) }
                if (x != applied && stage.live == x) {
                    // The previewed world contracts into its card as a still; the live scene returns to the applied card.
                    scope.launch {
                        val img = stage.captureScene()
                        ghost = GhostState(img, x)
                        stills.peek(applied)?.let { stage.holdSnapshot(it) }
                        stage.hint = PassageHint(PassageStyle.ENTRANCE, intensity = 0.5f, snapshotHeld = stills.peek(applied) != null)
                        snapNext = true
                        staged = null
                    }
                } else staged = null
            }
            fun step(dir: Int) {
                val cur = staged ?: return
                val i = ThemeRegistry.indexOf(cur) + dir
                if (i !in ThemeRegistry.all.indices) return
                haptics.select()
                stage.hint = PassageHint(PassageStyle.ENTRANCE, interrupt = true)
                staged = ThemeRegistry.all[i].theme
            }
            fun applyStaged() {
                val x = staged ?: return
                haptics.confirm()
                chromeLeaving = true
                applying = x
                staged = null
                tab = Tab.HOME
                scope.launch {
                    // Write the setting after the motion has started: never block the animation on storage.
                    withFrameNanos { }; withFrameNanos { }
                    rt.settingsStore.update { it.copy(sceneTheme = x) }
                    applying = null
                    chromeLeaving = false
                    confirmChip = true
                    delay(prefs.ms(Motion.CONFIRM_CHIP_MS)); confirmChip = false
                }
            }
            fun startDemo(id: String, theme: SceneTheme?) {
                optionSheet = null; demoSheet = false
                scope.launch {
                    val th = theme ?: rt.demoScenarios.firstOrNull { it.id == id }?.let { VroomiRuntime.demoThemeOf(it) }?.let(ThemeRegistry::resolve)
                    if (th != null && th != stage.live) {
                        demoPreroll = th
                        snapshotFlow { stage.live == th && stage.phase == PassagePhase.IDLE }.first { it }
                    }
                    if (theme != null) demoForced = theme
                    rt.startDemo(id)
                    demoPreroll = null
                }
            }

            // ------------------------------------------------ P5 swipe state
            var swiping by remember { mutableStateOf(false) }
            var swipeRaw by remember { mutableStateOf(0f) }
            val stageGestures = Modifier.stageGestures(
                enabled = place == Place.STAGE && !moving,
                onSwipeStart = {
                    swiping = true
                    scope.launch { stage.swipeSnap = stage.captureScene() }
                },
                onSwipe = { dx ->
                    val cur = staged
                    if (cur != null) {
                        val i = ThemeRegistry.indexOf(cur)
                        val hasPrev = i > 0; val hasNext = i < ThemeRegistry.all.size - 1
                        val dxDp = dx / density.density
                        val f = SwipeMath.follow(dxDp, hasPrev, hasNext) * density.density
                        swipeRaw = dx
                        val n = if (dx < 0f && hasNext) ThemeRegistry.all[i + 1].theme else if (dx > 0f && hasPrev) ThemeRegistry.all[i - 1].theme else null
                        if (n != stage.swipeNeighbour) { stage.swipeNeighbour = n; n?.let { stills.request(it) } }
                        scope.launch { stage.swipeOffset.snapTo(f) }
                    }
                },
                onSwipeEnd = { vx ->
                    val cur = staged
                    if (cur == null) { swiping = false } else {
                        val i = ThemeRegistry.indexOf(cur)
                        val hasPrev = i > 0; val hasNext = i < ThemeRegistry.all.size - 1
                        val d = SwipeMath.decide(swipeRaw / density.density, wPx / density.density, vx / density.density, hasPrev, hasNext)
                        scope.launch {
                            if (d != 0) {
                                val n = ThemeRegistry.all[i - d].theme
                                stage.swipeOffset.animateTo(d * wPx, prefs.spring(Motion.Smooth), initialVelocity = vx)
                                stage.hint = PassageHint(PassageStyle.SWIPE, snapshotHeld = true)
                                stage.show(n)
                                staged = n
                                haptics.select()
                            } else {
                                stage.swipeOffset.animateTo(0f, prefs.spring(Motion.Smooth), initialVelocity = vx)
                            }
                            stage.swipeSnap = null; stage.swipeNeighbour = null
                            stage.swipeOffset.snapTo(0f)
                            swiping = false
                        }
                    }
                },
                onPullStart = { },
                onPull = { dy -> scope.launch { pull.snapTo(PullMath.follow(dy / density.density) * density.density) } },
                onPullEnd = { vy ->
                    if (PullMath.closes(pull.value / density.density, hPx / density.density, vy / density.density)) closeStage()
                    else scope.launch { pull.animateTo(0f, prefs.spring(Motion.Smooth), initialVelocity = vy) }
                },
            )

            val placement = remember {
                StagePlacement(
                    rect = { if (following) (rectFor(Place.CARD) ?: rectAnim.value) else rectAnim.value },
                    corner = {
                        val pr = PullMath.progress(pull.value / density.density, hPx / density.density)
                        max(cornerAnim.value, cardCorner * pr)
                    },
                    containerScale = { PullMath.scale(PullMath.progress(pull.value / density.density, hPx / density.density)) },
                    containerShiftY = { pull.value },
                    dim = { if (following) gallery.dimOf(appliedRef.value) else 0f },
                    alpha = { sceneIn.value },
                )
            }

            // ------------------------------------------------ layers
            val blurBehind = blurOk && sheetAmount > 0.01f && !moving
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // This block re-runs on a state change before the next composition, so [blurBehind] can be a frame
                    // old while the amount is already 0 (a sheet that closes across a long frame). A blur of radius 0
                    // has no native effect and throws, so the radius is checked here as well.
                    val r = Dimens.sheetBlur.toPx() * sheetAmount
                    renderEffect = if (blurBehind && r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                },
            ) {
                CompositionLocalProvider(LocalSceneBackdrop provides sceneBackdrop) {
                    // Base: rail, panels beside the scene, the gallery (recedes behind the opening stage).
                    Row(
                        Modifier.fillMaxSize()
                            .blockTouches(!parkedChrome || moving)
                            .graphicsLayer { alpha = parkedAlpha.value },
                    ) {
                        val railShown = place != Place.STAGE && !sceneMoving
                        RailSlot(railShown, chromeIn.value) { TabRail(tab) { t -> if (t == Tab.THEMES) scope.launch { gallery.list.scrollToItem(gallery.shelf(applied).indexOf(applied).coerceAtLeast(0)) }; tab = t } }
                        Box(
                            Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                                val a = stageAmount.value
                                if (tab == Tab.THEMES && a > 0.001f && !prefs.reduced) {
                                    val s = 1f - 0.06f * a
                                    scaleX = s; scaleY = s
                                    if (blurOk) { val r = Dimens.recedeBlur.toPx() * a; renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null }
                                } else renderEffect = null
                            }.drawWithDim { val a = stageAmount.value; if (tab == Tab.THEMES) a * (if (blurOk) 0.30f else 0.45f) else 0f },
                        ) {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    val fromHome = initialState == Tab.HOME || targetState == Tab.HOME
                                    when {
                                        prefs.reduced || still -> fadeIn(prefs.fade(Motion.REDUCED_MS)) togetherWith fadeOut(prefs.fade(Motion.REDUCED_MS))
                                        fromHome -> (slideInHorizontally(prefs.spring(Motion.Smooth)) { with(density) { Space.s24.roundToPx() } } +
                                            fadeIn(prefs.fade(Motion.FADE_IN_MS, Motion.CONTENT_DELAY_MS))) togetherWith fadeOut(prefs.fade(Motion.FADE_FAST_MS))
                                        else -> (slideInVertically(prefs.spring(Motion.Smooth)) { with(density) { Space.s16.roundToPx() } } +
                                            fadeIn(prefs.fade(Motion.FADE_IN_MS, Motion.CONTENT_DELAY_MS))) togetherWith fadeOut(prefs.fade(Motion.FADE_FAST_MS))
                                    }
                                },
                                label = "tab",
                            ) { t ->
                                when (t) {
                                    Tab.THEMES -> Gallery(
                                        gallery, applied, stills,
                                        chips = { ThemeOptionChips(applied, pickerContext(rt, settings, grade, modelOf, ::pickModel)) { optionSheet = it } },
                                        onOpen = ::openCard,
                                        stepTag = { t -> ThemeModels.tag(modelOf(t)) },
                                    )
                                    Tab.DIARY, Tab.GRADE, Tab.SETTINGS -> if (wide) Row(Modifier.fillMaxSize()) {
                                        Spacer(Modifier.weight(io.vroomi.app.ui.design.Layout.SCENE_PANE).fillMaxHeight())
                                        Box(Modifier.weight(1f - io.vroomi.app.ui.design.Layout.SCENE_PANE).fillMaxHeight().background(MaterialTheme.colorScheme.background)) { PanelFor(t, rt, trips) }
                                    }
                                    Tab.HOME -> Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }

                    // The one live scene.
                    SceneStage(
                        stage, stills, placement, layout,
                        backdropColor = { chrome.backdrop }, tint = { chrome.tint }, sceneBackdrop = sceneBackdrop,
                        liveHidden = { swiping && stage.swipeSnap != null },
                        modifier = stageGestures,
                    ) { m ->
                        val lt = liveTheme
                        // The entrance accent is read here so only the live scene recomposes while it runs.
                        val intro = stage.intro.floatValue
                        if (lt != null) host.Draw(target, if (intro < 1f) options.copy(intro = intro) else options, m,
                            interactive = !moving && place != Place.CARD, onFirstFrame = { stage.firstFrame(lt); firstFrameSeen = true })
                    }

                    // The ghost of a previewed theme contracting into its card (P4).
                    GhostLayer(ghost, { t -> cardRect(t) }, cardCorner, prefs, stills) { ghost = null }

                    // Chrome over the scene.
                    Box(Modifier.fillMaxSize().blockTouches(!parkedChrome || moving).graphicsLayer { alpha = parkedAlpha.value }) {
                        AnimatedVisibility(tab == Tab.HOME && place == Place.HOME, enter = fadeIn(prefs.fade(Motion.FADE_IN_MS, Motion.CONTENT_DELAY_MS)), exit = fadeOut(prefs.fade(Motion.FADE_FAST_MS))) {
                            Box(Modifier.fillMaxSize().padding(start = Dimens.railWidth).graphicsLayer {
                                alpha = chromeIn.value; translationY = (1f - chromeIn.value) * Space.s12.toPx()
                            }) {
                                HomeChrome(rt, trips, lastLine, demo, caption, lastTrip, onDemo = { demoSheet = true })
                                if (confirmChip) ConfirmChip(stringResource(R.string.theme_changed), Modifier.align(Alignment.TopCenter).padding(top = Space.s24))
                            }
                        }
                        if (!wide && panelTab && place == Place.HOME) {
                            Box(Modifier.fillMaxSize().padding(start = Dimens.railWidth).material(MaterialToken.Thick, androidx.compose.ui.graphics.RectangleShape, blur = true)) { PanelFor(tab, rt, trips) }
                        }
                        val shownOnStage = staged
                        StageChrome(
                            theme = shownOnStage ?: applied,
                            shown = shownOnStage != null && place == Place.STAGE && stageAmount.value >= 0.7f && !swiping,
                            leaving = chromeLeaving,
                            onClose = ::closeStage,
                            onPrev = if (shownOnStage != null && ThemeRegistry.indexOf(shownOnStage) > 0) ({ step(-1) }) else null,
                            onNext = if (shownOnStage != null && ThemeRegistry.indexOf(shownOnStage) < ThemeRegistry.all.size - 1) ({ step(1) }) else null,
                            onApply = ::applyStaged,
                            onDemo = shownOnStage?.let { s -> ThemeRegistry.of(s).showcase?.let { id -> { startDemo(id, s) } } },
                            chips = { ThemeOptionChips(shownOnStage ?: applied, pickerContext(rt, settings, grade, modelOf, ::pickModel)) { optionSheet = it } },
                        )
                        // The applied card's step tag: its picture is the live scene drawn above the gallery, so the
                        // tag is drawn here, at the card's top right (the other cards draw their own).
                        if (tab == Tab.THEMES && place == Place.CARD && sceneIn.value >= 1f) {
                            StepTag(
                                ThemeModels.tag(modelOf(applied)),
                                Modifier.atTopEndOf({ cardRect(appliedRef.value) }, Space.s16).graphicsLayer { alpha = 1f - stageAmount.value },
                            )
                        }
                    }

                    // Moving: the chip, the demo captions and the two big toggles; they only fade.
                    val movingAlpha = remember { Animatable(if (moving) 1f else 0f) }
                    LaunchedEffect(controlsShown) { movingAlpha.animateTo(if (controlsShown) 1f else 0f, prefs.fade(if (controlsShown) Motion.FADE_IN_MS else Motion.FADE_OUT_MS)) }
                    if (movingAlpha.value > 0.001f || controlsShown) {
                        MovingOverlay(
                            chip = host.chip(target),
                            muted = settings.muted, calm = settings.calm,
                            onMute = { rt.settingsStore.update { it.copy(muted = !it.muted) } },
                            onCalm = { rt.settingsStore.update { it.copy(calm = !it.calm) } },
                            demoCaption = if (demo) caption else null,
                            demoProgress = if (demo) progress else null,
                            onEndDemo = { rt.stopDemo() },
                            sceneTheme = stage.live ?: applied,
                            modifier = Modifier.graphicsLayer { alpha = movingAlpha.value }.blockTouches(!controlsShown),
                        )
                    }

                    // Debug: the passage's state and frame time over the frame-time overlay.
                    if (BuildConfig.DEBUG && settings.devFrameTime) {
                        Text(
                            "passage ${stage.phase.name.lowercase()} · ${"%.1f".format(stage.frameMs.floatValue)} ms",
                            Modifier.align(Alignment.BottomStart).padding(start = Space.s12, bottom = Space.s40),
                            style = VType.callout, color = Color(io.vroomi.app.ui.design.Neutral.DEBUG_INK),
                        )
                    }
                }
            }

            // Overlays above the blur: sheets and the welcome card.
            val ctx = pickerContext(rt, settings, grade, modelOf, ::pickModel, previewing = { t -> modelPreview?.first == t }) { change -> stage.crossfade(prefs, change) }
            BottomSheet(optionSheet != null && !moving, onDismiss = { optionSheet = null }, progress = { sheetAmount = it }) {
                optionSheet?.let { o -> OptionSheet(o, staged ?: applied, ctx) }
            }
            DemoPicker(rt.demoScenarios, onPick = { startDemo(it, null) }, onClose = { demoSheet = false }, visible = demoSheet && !moving, progress = { if (optionSheet == null) sheetAmount = it })
            if (!settings.onboarded && !moving && sceneIn.value >= 1f) {
                WelcomeCard(
                    onDemo = { rt.settingsStore.update { it.copy(onboarded = true) }; startDemo("highlight", null) },
                    onStart = { rt.settingsStore.update { it.copy(onboarded = true) } },
                )
            }
        }
    } }
}

/** The previewed theme's still contracting into its card (P4, a preview that was not applied). */
internal class GhostState(val image: androidx.compose.ui.graphics.ImageBitmap?, val theme: SceneTheme)

@Composable
private fun GhostLayer(g: GhostState?, rectOf: (SceneTheme) -> Rect?, corner: Float, prefs: MotionPrefs, stills: HeroStills, done: () -> Unit) {
    if (g == null) return
    val img = g.image
    if (img == null) { LaunchedEffect(g) { done() }; return }
    val t = remember(g) { Animatable(0f) }
    val path = remember { androidx.compose.ui.graphics.Path() }
    LaunchedEffect(g) {
        t.animateTo(1f, prefs.spring(Motion.Passage))
        stills.put(g.theme, img)
        done()
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val full = Rect(0f, 0f, size.width, size.height)
        val end = rectOf(g.theme) ?: full
        val k = t.value
        val r = Rect(
            full.left + (end.left - full.left) * k, full.top + (end.top - full.top) * k,
            full.right + (end.right - full.right) * k, full.bottom + (end.bottom - full.bottom) * k,
        )
        val geo = io.vroomi.app.ui.design.SmoothCorners.geometry(r.width, r.height, corner * k, 0.6f)
        path.reset()
        path.moveTo(r.left + geo.startX, r.top + geo.startY)
        for (sg in geo.segs) if (sg.cubic) path.cubicTo(r.left + sg.x1, r.top + sg.y1, r.left + sg.x2, r.top + sg.y2, r.left + sg.x, r.top + sg.y) else path.lineTo(r.left + sg.x, r.top + sg.y)
        path.close()
        clipPath(path) {
            val f = max(r.width / img.width, r.height / img.height)
            drawImage(
                img, androidx.compose.ui.unit.IntOffset.Zero, IntSize(img.width, img.height),
                androidx.compose.ui.unit.IntOffset((r.left + (r.width - img.width * f) / 2f).toInt(), (r.top + (r.height - img.height * f) / 2f).toInt()),
                IntSize((img.width * f).toInt(), (img.height * f).toInt()),
            )
        }
    }
}

/** The rail slides out (and back) with the stage and with driving; [appear] is the launch rise. */
@Composable
private fun RailSlot(shown: Boolean, appear: Float, content: @Composable () -> Unit) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val a = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(shown) { a.animateTo(if (shown) 1f else 0f, if (prefs.reduced) prefs.fade(Motion.REDUCED_MS) else prefs.spring(Motion.Smooth)) }
    Box(
        Modifier.fillMaxHeight().graphicsLayer {
            alpha = a.value * appear
            translationX = -(1f - a.value) * Dimens.railWidth.toPx()
            translationY = (1f - appear) * Space.s12.toPx()
        },
    ) { content() }
}

@Composable
private fun PanelFor(tab: Tab, rt: VroomiRuntime, trips: List<io.vroomi.app.diary.TripRecord>) {
    when (tab) {
        Tab.DIARY -> DiaryPanel(trips)
        Tab.GRADE -> GradePanel(rt)
        Tab.SETTINGS -> SettingsPanel(rt)
        else -> Unit
    }
}

@Composable
private fun pickerContext(
    rt: VroomiRuntime, s: io.vroomi.app.settings.Settings, grade: Grade, modelOf: (SceneTheme) -> Int, onModel: (SceneTheme, Int) -> Unit,
    previewing: (SceneTheme) -> Boolean = { false }, crossfade: (() -> Unit) -> Unit = { it() },
): PickerContext {
    val openedAt = remember { System.currentTimeMillis() }
    return PickerContext(
        s.skin, s.calibreFinish, s.enginePowertrain, stillAt = io.vroomi.character.render.calibre.DEFAULT_NOW_MS, openedAt = openedAt,
        onFinish = { f -> crossfade { rt.settingsStore.update { it.copy(calibreFinish = f) } } },
        onPowertrain = { p -> crossfade { rt.settingsStore.update { it.copy(enginePowertrain = p) } } },
        // Another friend (or finish, or powertrain, or step) crossfades into the live scene instead of cutting.
        onSkin = { skin -> if (skin != s.skin) crossfade { rt.settingsStore.update { it.copy(skin = skin) } } },
        themeSound = s.themeSound,
        onThemeSound = { on -> rt.settingsStore.update { it.copy(themeSound = on) } },
        grade = grade, modelOf = modelOf, previewing = previewing,
        onModel = { t, m -> if (m != modelOf(t)) crossfade { onModel(t, m) } else onModel(t, m) },
    )
}

/** Places a child at the top-right corner of [rect] (root px, read while placing), [inset] inside it; hidden without a rect. */
private fun Modifier.atTopEndOf(rect: () -> Rect?, inset: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        val r = rect() ?: return@layout
        val i = inset.toPx()
        p.place((r.right - i - p.width).roundToInt(), (r.top + i).roundToInt())
    }
}

/** Home's chrome over the scene: the grade badge, the speech bubble, the demo and talk buttons, the trip card. */
@Composable
private fun HomeChrome(
    rt: VroomiRuntime, trips: List<io.vroomi.app.diary.TripRecord>, lastLine: String?, demo: Boolean, caption: String?,
    lastTrip: io.vroomi.app.diary.TripRecord?, onDemo: () -> Unit,
) {
    val listen by rt.recognizer.state.collectAsStateWithLifecycle()
    val partial by rt.recognizer.partial.collectAsStateWithLifecycle()
    val settings by rt.settingsStore.settings.collectAsStateWithLifecycle()
    val status = remember(trips, settings.gradeReached, settings.devAllGrades) { GradeStatus.of(trips, VroomiRuntime.shownGrade(trips, settings)) }
    // The chrome-zone contract: status top left, actions bottom right (io.vroomi.character.render.theme.ChromeZones).
    Box(Modifier.fillMaxSize().padding(Space.parked)) {
        GradeBadge(status.grade, status.stars, status.progress, status.badgeDetail(), Modifier.align(Alignment.TopStart))
        val bubble = when {
            demo && !caption.isNullOrBlank() -> caption
            listen == ListenState.LISTENING -> partial.ifBlank { stringResource(R.string.listening) }
            else -> lastLine
        }
        if (!bubble.isNullOrBlank()) SpeechBubble(bubble, Modifier.align(Alignment.TopCenter))
        HomeActions(
            demo = demo, listening = listen == ListenState.LISTENING, canTalk = listen != ListenState.UNAVAILABLE,
            modifier = Modifier.align(Alignment.BottomEnd),
            onDemo = { if (demo) rt.stopDemo() else onDemo() },
            onTalk = { if (listen == ListenState.LISTENING) rt.recognizer.cancel() else rt.recognizer.listen() },
        )
        lastTrip?.let { trip ->
            val nudge = remember(trip.id, trips, settings.gradeReached) { io.vroomi.app.progress.GradeNudge.of(trip, trips, settings.gradeReached) }
            TripSummaryCard(trip, Modifier.align(Alignment.TopEnd), nudge, ThemeRegistry.resolve(settings.sceneTheme)) { rt.dismissLastTrip() }
        }
    }
}

/** Blocks touches to everything under this node (parked chrome while moving, hidden controls). */
private fun Modifier.blockTouches(block: Boolean): Modifier = if (!block) this else pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
}

/** Black over a layer, read while drawing. */
private fun Modifier.drawWithDim(amount: () -> Float): Modifier = drawWithContent {
    drawContent()
    val a = amount()
    if (a > 0.003f) drawRect(Color.Black, alpha = a)
}

/** Which scene layer is shown and the state each keeps. Drawing dispatches through [ThemeRegistry]. */
internal class SceneHost(
    val theme: SceneTheme,
    val kart: VroomiSceneState,
    val calibre: CalibreSceneState,
    val finish: CalibreFinish,
    val frameTime: Boolean,
    val engine: EngineSceneState,
    val powertrain: EnginePowertrain,
    val forcedPowertrain: Powertrain?,
    /** The state of the shown procedural (theme kit) theme, null for KART, CALIBRE and ENGINE. */
    val kit: ThemeSceneState? = null,
) {
    /** [interactive]: parked only (the watch flips; the engine toggles its close-up; kit themes get taps). */
    @Composable
    fun Draw(target: CharacterTarget, options: SceneOptions, modifier: Modifier, interactive: Boolean, onFirstFrame: () -> Unit = {}) {
        ThemeRegistry.of(theme).scene(this, target, options, modifier, interactive, onFirstFrame)
    }

    /** The status chip of the shown theme. */
    fun chip(target: CharacterTarget): String? = ThemeRegistry.of(theme).chip(this, target)
}

/**
 * The optional theme sound (off by default, "테마 소리"). It plays only while the theme is shown, settled and
 * the app is in the foreground. When a passage starts the old theme's sound fades out over 300 ms (the engine's
 * own safety fade) and is released; the new theme's sound starts in Settling with the usual 1.5 s fade-in.
 */
@Composable
private fun ThemeSoundKeeper(rt: VroomiRuntime, kit: ThemeSceneState?, theme: SceneTheme?, settled: () -> Boolean) {
    val settings by rt.settingsStore.settings.collectAsStateWithLifecycle()
    val currentSettings by rememberUpdatedState(settings)
    val currentSim by rememberUpdatedState(kit?.sim as? io.vroomi.character.theme.SoundSource)
    val currentTheme by rememberUpdatedState(theme)
    val currentSettled by rememberUpdatedState(settled)
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        var engine: io.vroomi.app.audio.ThemeAudioEngine? = null
        var engineTheme: SceneTheme? = null
        var fadingSince = 0L
        try {
            while (true) {
                delay(16)
                val s = currentSettings
                val sim = currentSim
                val th = currentTheme
                val foreground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                val wanted = s.themeSound && !s.muted && foreground && sim != null
                val ok = currentSettled()
                val snap = rt.hub.snapshot.value
                val duck = snap.naviGuidanceActive == true || System.currentTimeMillis() - rt.lastSpokeAtMs < 4_000
                val e = engine
                if (e != null && (engineTheme != th || !ok)) {
                    // Passage: fade the old theme out over 300 ms, then release it.
                    if (fadingSince == 0L) fadingSince = System.currentTimeMillis()
                    e.update(true, duck, true)
                    if (System.currentTimeMillis() - fadingSince >= 320) { e.release(); engine = null; engineTheme = null; fadingSince = 0L }
                } else if (e != null) {
                    e.update(wanted, duck, snap.safetyWarningActive == true)
                    if (!wanted) { e.release(); engine = null; engineTheme = null }
                } else if (wanted && ok && th != null) {
                    val synth = if (th == SceneTheme.PIXEL) io.vroomi.app.audio.ChipSynth() else io.vroomi.app.audio.MusicBoxSynth()
                    engine = io.vroomi.app.audio.ThemeAudioEngine(context, synth).also { it.reopen() }
                    engineTheme = th
                }
                if (sim != null) while (true) {
                    val ev = sim.takeEvent()
                    if (ev < 0) break
                    if (engineTheme == th && fadingSince == 0L) engine?.note(sim.eventMidi(ev), sim.eventVel(ev), sim.eventKind(ev))
                }
            }
        } finally { engine?.release() }
    }
}

/**
 * Kit themes: keeps their small persisted state (for example the terrarium's growth) across restarts and
 * releases the cached layers of every theme that is not shown.
 */
@Composable
private fun ThemeKeeper(rt: VroomiRuntime, kit: ThemeSceneState?, theme: SceneTheme?) {
    LaunchedEffect(theme) { releaseThemeLayers(if (theme != null && isKitTheme(theme)) theme else null) }
    if (kit == null) return
    // The hangar's robot is built by real distance only: a demo shows its own build, and the saved one stands still.
    val demo by rt.demo.collectAsStateWithLifecycle()
    LaunchedEffect(kit, demo) {
        (kit.sim as? io.vroomi.character.hangar.HangarSim)?.let { if (demo) it.startDemo() else it.demo = false }
    }
    LaunchedEffect(kit) {
        while (true) {
            delay(30_000)
            kit.sim.persist()?.let { rt.settingsStore.saveThemeState(kit.theme, it) }
        }
    }
    DisposableEffect(kit) { onDispose { kit.sim.persist()?.let { rt.settingsStore.saveThemeState(kit.theme, it) } } }
}

/**
 * The optional engine sound (developer panel only, default off): a very quiet hum that follows the
 * estimated rpm (ICE only), the A/C clutch click and the blinker relay click. Ducked by navigation,
 * a safety warning and mute.
 */
@Composable
private fun EngineKeeper(rt: VroomiRuntime, engine: EngineSceneState, theme: SceneTheme?) {
    val settings by rt.settingsStore.settings.collectAsStateWithLifecycle()
    val currentTheme by rememberUpdatedState(theme)
    val currentSettings by rememberUpdatedState(settings)
    LaunchedEffect(engine) {
        var sound: EngineSound? = null
        try {
            while (true) {
                delay(40)
                val events = engine.drainEvents()
                val s = currentSettings
                if (s.mechSound && currentTheme == SceneTheme.ENGINE) {
                    val snap = rt.hub.snapshot.value
                    val ducked = s.muted || snap.naviGuidanceActive == true || snap.safetyWarningActive == true
                    (sound ?: EngineSound().also { sound = it }).onFrame(events, engine.estimatedRpm, ducked)
                } else if (sound != null) { sound?.release(); sound = null }
            }
        } finally { sound?.release() }
    }
}

/**
 * Keeps the Calibre power reserve across app restarts (real time drains it meanwhile) and feeds the
 * optional mechanical sound. The reserve is saved every 30 s and when the screen goes away.
 */
@Composable
private fun CalibreKeeper(rt: VroomiRuntime, calibre: CalibreSceneState, theme: SceneTheme?, moving: Boolean) {
    val settings by rt.settingsStore.settings.collectAsStateWithLifecycle()
    val currentTheme by rememberUpdatedState(theme)
    val currentMoving by rememberUpdatedState(moving)
    val currentSettings by rememberUpdatedState(settings)
    LaunchedEffect(calibre) {
        rt.settingsStore.readReserve()?.let { (h, at) -> calibre.restoreReserve(h, at, System.currentTimeMillis()) }
        var lastSave = System.currentTimeMillis()
        var sound: MechanicalSound? = null
        try {
            while (true) {
                delay(40)
                val now = System.currentTimeMillis()
                val events = calibre.drainEvents()
                val s = currentSettings
                if (s.mechSound && currentTheme == SceneTheme.CALIBRE) {
                    val snap = rt.hub.snapshot.value
                    val ducked = s.muted || snap.naviGuidanceActive == true || snap.safetyWarningActive == true
                    (sound ?: MechanicalSound().also { sound = it }).onFrame(events, parked = !currentMoving, ducked = ducked, nowMs = now)
                } else if (sound != null) { sound?.release(); sound = null }
                // Only a running watch changes the reserve; otherwise the old stamp lets real time drain it.
                if (currentTheme == SceneTheme.CALIBRE && now - lastSave >= 30_000) { lastSave = now; rt.settingsStore.saveReserve(calibre.reserveHours, now) }
            }
        } finally { sound?.release() }
    }
    DisposableEffect(calibre) { onDispose { if (currentTheme == SceneTheme.CALIBRE) rt.settingsStore.saveReserve(calibre.reserveHours, System.currentTimeMillis()) } }
}
