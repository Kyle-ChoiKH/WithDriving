package io.vroomi.app.runtime

import android.app.Application
import io.vroomi.app.R
import io.vroomi.app.diary.DiaryStore
import io.vroomi.app.diary.TripRecord
import io.vroomi.app.diary.TripRecorder
import io.vroomi.app.progress.Grade
import io.vroomi.app.progress.SafetyGrade
import io.vroomi.app.settings.Settings
import io.vroomi.app.settings.SettingsStore
import io.vroomi.character.Action
import io.vroomi.character.CharacterDirector
import io.vroomi.character.CharacterTarget
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.theme.ThemeModels
import io.vroomi.core.navi.NaviBridge
import io.vroomi.core.vehicle.SourceMode
import io.vroomi.core.vehicle.VehicleHub
import io.vroomi.core.vehicle.sim.Scenario
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.provider.PleosVehicleProvider
import io.vroomi.core.vehicle.signal.Thresholds
import io.vroomi.core.vehicle.sim.SimulatedVehicleProvider
import io.vroomi.core.vehicle.speed.AaosSpeedSource
import io.vroomi.core.vehicle.speed.FusedLocationSpeedSource
import io.vroomi.core.vehicle.speed.SpeedSourceChain
import io.vroomi.core.vehicle.ux.UxRestrictionWatcher
import io.vroomi.core.voice.AndroidTtsSpeaker
import io.vroomi.core.voice.CommandRecognizer
import io.vroomi.core.voice.FallbackSpeaker
import io.vroomi.core.voice.IntentParser
import io.vroomi.core.voice.LinePriority
import io.vroomi.core.voice.PleosTtsSpeaker
import io.vroomi.core.voice.VoiceDirector
import io.vroomi.core.voice.VoiceGate
import io.vroomi.core.voice.VoiceIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Wires everything together (manual DI, one instance per process):
 * VehicleHub -> CharacterDirector -> CharacterTarget (UI) ; Director events -> VoiceDirector ;
 * VehicleHub -> TripRecorder -> DiaryStore.
 */
class VroomiRuntime(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Director, recorder and voice are confined to one thread: no locks needed. */
    private val logic = Executors.newSingleThreadExecutor { r -> Thread(r, "vroomi-logic") }.asCoroutineDispatcher()

    val settingsStore = SettingsStore(app)
    val simulator = SimulatedVehicleProvider(scope, app.assets)
    private val pleos = PleosVehicleProvider(app)
    private val speedChain = SpeedSourceChain(listOf(AaosSpeedSource(app), FusedLocationSpeedSource(app)))
    val hub = VehicleHub(scope, pleos, simulator, speedChain)
    private val navi = NaviBridge(app)
    private val ux = UxRestrictionWatcher(app)
    private val director = CharacterDirector()
    private val moving = MovingDetector()
    private val recorder = TripRecorder()
    val diary = DiaryStore(File(app.filesDir, "diary.json"))

    private val speaker = FallbackSpeaker(PleosTtsSpeaker(app), AndroidTtsSpeaker(app))
    private val lineCache = HashMap<String, List<String>>()
    private val voice = VoiceDirector(
        speaker = speaker,
        lines = { key -> lineCache.getOrPut(key) { loadLines(key) } },
        globalCooldownMovingMs = Thresholds.VOICE_GLOBAL_COOLDOWN_MOVING_MS,
        globalCooldownParkedMs = Thresholds.VOICE_GLOBAL_COOLDOWN_PARKED_MS,
        eventCooldownMs = Thresholds.VOICE_EVENT_COOLDOWN_MS,
    )
    val recognizer = CommandRecognizer(app) { text -> scope.launch(logic) { onCommand(text) } }

    private val _target = MutableStateFlow(CharacterTarget())
    val target: StateFlow<CharacterTarget> = _target.asStateFlow()
    private val _isMoving = MutableStateFlow(false)
    val isMoving: StateFlow<Boolean> = _isMoving.asStateFlow()
    private val _lastLine = MutableStateFlow<String?>(null)
    val lastLine: StateFlow<String?> = _lastLine.asStateFlow()

    /** The trip that just ended (shown once as a card on the parked home screen). */
    private val _lastTrip = MutableStateFlow<TripRecord?>(null)
    val lastTrip: StateFlow<TripRecord?> = _lastTrip.asStateFlow()

    /** Demo drive: a bundled scenario played through the simulator with on-screen captions. */
    private val _demo = MutableStateFlow(false)
    val demo: StateFlow<Boolean> = _demo.asStateFlow()
    val demoCaption: StateFlow<String?> get() = simulator.caption
    val demoProgress: StateFlow<Float> get() = simulator.progress
    val demoScenarios: List<Scenario> by lazy { simulator.scenarios() }
    /** The hands-on demo: the user's presses drive the simulated car ([startHandsOn]). */
    val handsOn = io.vroomi.app.demo.HandsOnDrive { change -> simulator.setManual(change) }
    private var handsJob: kotlinx.coroutines.Job? = null
    /** A demo can show a theme for its duration only (the Calibre and engine showcases); null = the user's own. */
    /** When the last voice line was spoken (theme sound ducks for a few seconds after it). */
    @Volatile var lastSpokeAtMs = 0L
        private set

    private val _demoTheme = MutableStateFlow<SceneTheme?>(null)
    val demoTheme: StateFlow<SceneTheme?> = _demoTheme.asStateFlow()
    /** The powertrain a demo forces in the Cutaway theme; null = the user's own setting. */
    private val _demoPowertrain = MutableStateFlow<Powertrain?>(null)
    val demoPowertrain: StateFlow<Powertrain?> = _demoPowertrain.asStateFlow()
    /**
     * The upgrade step a demo shows, whatever the grade (the scenario's "model", else the top step); null when no demo
     * runs, so the scene uses the user's own step.
     */
    private val _demoModel = MutableStateFlow<Int?>(null)
    val demoModel: StateFlow<Int?> = _demoModel.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        diary.load()
        // The first launch after an update (no grade stored yet): the grade the existing trips already earn, so a driver
        // with many safe kilometres does not start again from bronze.
        if (!settingsStore.hasStoredGrade()) raiseGrade()
        hub.setMode(settingsStore.settings.value.sourceMode)
        hub.start()
        navi.start()
        ux.start()

        scope.launch { navi.state.collect { hub.updateNavi(it) } }
        io.vroomi.app.audio.MusicWatcher(app) { hub.updateMusic(it) }.start()
        scope.launch { settingsStore.settings.map { it.sourceMode }.distinctUntilChanged().collect { hub.setMode(it) } }
        scope.launch(logic) {
            hub.events.collect { e ->
                val now = System.currentTimeMillis()
                director.onDrivingEvent(e, now)
                recorder.onEvent(e)
            }
        }
        scope.launch(logic) { hub.snapshot.collect { s -> onSnapshot(s) } }
        // The bundled demo drives are read off the main thread, before the picker first asks for them.
        scope.launch(Dispatchers.IO) { demoScenarios }
        // The demo ends with its scenario; it also stops at once if the real car starts driving.
        scope.launch { simulator.playing.collect { if (it == null && _demo.value) endDemo() } }
        scope.launch { ux.restricted.collect { if (it && _demo.value) stopDemo() } }
    }

    fun startDemo(id: String) {
        startDemo(simulator.loadScenario(id) ?: return)
    }

    /** Plays [scenario] (a bundled one, or a lesson built for the present theme) as a demo drive. */
    fun startDemo(scenario: Scenario) {
        if (handsOn.running.value) stopDemo()
        _lastTrip.value = null
        hub.setMode(SourceMode.SIMULATOR)
        _demoTheme.value = demoThemeOf(scenario)
        _demoPowertrain.value = scenario.powertrain?.let { p -> Powertrain.entries.find { it.name == p } }
        _demoModel.value = demoModelOf(scenario)
        io.vroomi.core.vehicle.route.LiveRoute.demoRoute = scenario.route
        _demo.value = true
        simulator.play(scenario)
    }

    /**
     * The hands-on demo of [theme]: the simulated car drives by itself and the user works its controls from the
     * strip of buttons ([handsOn]); it runs until the user ends it (or the real car starts driving).
     */
    fun startHandsOn(theme: SceneTheme) {
        if (_demo.value) stopDemo()
        _lastTrip.value = null
        hub.setMode(SourceMode.SIMULATOR)
        _demoTheme.value = theme
        _demoPowertrain.value = if (theme == SceneTheme.ENGINE) Powertrain.ICE else null
        _demoModel.value = ThemeModels.COUNT - 1
        io.vroomi.core.vehicle.route.LiveRoute.demoRoute = demoScenarios.firstOrNull { it.id in io.vroomi.app.demo.DemoCatalog.showcaseIds(theme) }?.route
        _demo.value = true
        handsOn.start(theme)
        handsJob = scope.launch {
            while (true) { kotlinx.coroutines.delay(HANDS_TICK_MS); handsOn.tick(HANDS_TICK_MS / 1000f) }
        }
    }

    fun stopDemo() {
        handsJob?.cancel(); handsJob = null
        handsOn.stop()
        simulator.stopScenario()
        endDemo()
    }

    private fun endDemo() {
        _demo.value = false
        _demoTheme.value = null
        _demoPowertrain.value = null
        _demoModel.value = null
        io.vroomi.core.vehicle.route.LiveRoute.demoRoute = null
        hub.setMode(settingsStore.settings.value.sourceMode)
    }

    fun dismissLastTrip() { _lastTrip.value = null }

    /** Stores the grade the trips earn when it is above the one reached so far (a grade never drops). */
    private fun raiseGrade() {
        val earned = SafetyGrade.earned(diary.trips.value).level
        if (earned > settingsStore.settings.value.gradeReached) settingsStore.update { it.copy(gradeReached = max(it.gradeReached, earned)) }
    }

    /** "다이어리 초기화": the trips go, and the grade starts again from bronze (the chosen steps are kept, clamped when shown). */
    fun resetDiary() {
        diary.clear()
        settingsStore.update { it.copy(gradeReached = 1) }
    }

    /** Typed command from the debug panel (same path as speech). */
    fun command(text: String) {
        scope.launch(logic) { onCommand(text) }
    }

    private fun onSnapshot(s: VehicleSnapshot) {
        val now = System.currentTimeMillis()
        val settings = settingsStore.settings.value
        val isMoving = moving.update(s.speedKmh, s.gear, ux.restricted.value, now)
        _isMoving.value = isMoving

        val wardrobe = settings.wardrobe.intersect(SafetyGrade.outfits(shownGrade(diary.trips.value, settings)))
        _target.value = director.update(s, isMoving, settings.userCorner, wardrobe, now)

        val gate = gate(s, isMoving)
        // Every theme but KART has no friend on screen, so it stays quiet unless the user wants its lines.
        val calibreQuiet = !(_demoTheme.value ?: settings.sceneTheme).hasCharacter && !settings.quietThemeVoice
        director.drainVoiceEvents().forEach { ev ->
            if (calibreQuiet && ev.lineKey !in ALWAYS_KEYS) return@forEach
            // "Speak rarely" keeps only the moments that matter most.
            if (settings.voiceRare && ev.lineKey !in RARE_KEYS) return@forEach
            voice.request(ev.lineKey, gate, now)?.let { _lastLine.value = it; lastSpokeAtMs = now }
        }

        // The recorder marks a trip with any demo driving in it, so "End demo" pressed mid-drive cannot leave a real trip.
        recorder.onSnapshot(s, now, _demo.value)?.let { trip ->
            val j = _target.value.journey
            // The map theme hands over a normalised 64-point sketch of the trail (no coordinates).
            val sketch = io.vroomi.core.vehicle.route.TripSketch.take()?.toList()
            val full = trip.copy(stars = j.stars, bestCombo = j.bestCombo, biomes = j.biomesSeen, demo = trip.demo || _demo.value, sketch = sketch)
            diary.add(full)
            raiseGrade()
            _lastTrip.value = full
        }
    }

    private fun gate(s: VehicleSnapshot, isMoving: Boolean): VoiceGate {
        val st = settingsStore.settings.value
        return VoiceGate(
            moving = isMoving,
            muted = st.muted,
            naviGuidanceActive = s.naviGuidanceActive == true,
            safetyWarningActive = s.safetyWarningActive == true,
        )
    }

    // ------------------------------------------------------------------ voice commands (§7)

    private suspend fun onCommand(text: String) {
        val now = System.currentTimeMillis()
        val s = hub.snapshot.value
        val g = gate(s, _isMoving.value)
        when (val intent = IntentParser.parse(text)) {
            VoiceIntent.Cooler -> climate(now, g, "reply_cooler", Action.BLOW) {
                val t = s.setTempC
                if (t != null) hub.setTemperature(t - 1.5f) else hub.setFanLevel((s.fanLevel ?: 0) + 1)
            }
            VoiceIntent.Warmer -> climate(now, g, "reply_warmer", Action.THUMBS_UP) {
                hub.setTemperature((s.setTempC ?: 22f) + 1.5f)
            }
            is VoiceIntent.SetTemperature -> climate(now, g, "reply_cooler", Action.THUMBS_UP) {
                hub.setTemperature(intent.celsius)
            }
            VoiceIntent.FanUp -> climate(now, g, "reply_fan_up", Action.BLOW) { hub.setFanLevel((s.fanLevel ?: 0) + 1) }
            VoiceIntent.FanDown -> climate(now, g, "reply_fan_down", Action.EXHALE) { hub.setFanLevel(((s.fanLevel ?: 1) - 1).coerceAtLeast(0)) }
            VoiceIntent.FanOff -> climate(now, g, "reply_fan_off", Action.EXHALE) { hub.setFanLevel(0) }
            VoiceIntent.AirCleanerOn -> {
                // Air cleaner control is not wired to the real car yet (needs CONTROL_CAR_CLIMATE): simulator only.
                if (hub.active.value == io.vroomi.core.vehicle.ActiveSource.SIMULATOR) {
                    simulator.setManual { it.copy(airCleanerOn = true) }
                    say("reply_air", g, now)
                } else say("reply_manual", g, now)
            }
            VoiceIntent.Mute -> settingsStore.update { it.copy(muted = true) }
            VoiceIntent.Hide -> { say("reply_hide", g, now); settingsStore.update { it.copy(userCorner = true) } }
            VoiceIntent.Show -> { settingsStore.update { it.copy(userCorner = false) }; say("reply_show", g, now) }
            VoiceIntent.TripSummary -> {
                val last = diary.trips.value.firstOrNull()
                val line = if (last == null) "아직 기록이 없어요" else
                    "${"%.1f".format(last.distanceKm)}킬로, 부드러움 ${last.score}점, 별 ${last.stars}개예요"
                voice.answer(line, g, now)?.let { _lastLine.value = it }
            }
            VoiceIntent.Unknown -> say("reply_unknown", g, now)
        }
    }

    private suspend fun climate(
        now: Long, g: VoiceGate, replyKey: String, reaction: Action, change: suspend () -> Result<Unit>,
    ) {
        if (!hub.canControlClimate()) {
            say("reply_manual", g, now)
            return
        }
        director.react(reaction, now)          // optimistic animation first
        val r = change()
        if (r.isSuccess) say(replyKey, g, now) else {
            director.react(Action.EXHALE, System.currentTimeMillis(), 1_000) // revert the excitement
            say("reply_fail", g, now)
        }
    }

    private fun say(key: String, g: VoiceGate, now: Long) {
        val options = lineCache.getOrPut(key) { loadLines(key) }
        if (options.isEmpty()) return
        val text = options[((now / 1000) % options.size).toInt()]
        voice.answer(text, g, now)?.let { _lastLine.value = it }
    }

    private fun loadLines(key: String): List<String> {
        val id = LINE_IDS[key] ?: return emptyList()
        return app.resources.getStringArray(id).toList()
    }

    fun summaryForUi(): String {
        val trips = diary.trips.value
        val km = SafetyGrade.safeKm(trips)
        return "${trips.size}번의 주행 · 부드러운 거리 ${(km * 10).roundToInt() / 10f}km"
    }

    companion object {
        private const val HANDS_TICK_MS = 100L

        /** A scenario's own "theme" field wins; older Calibre demos are recognised by their id. */
        fun demoThemeOf(s: Scenario): SceneTheme? =
            s.theme?.let { t -> SceneTheme.entries.find { it.name == t } } ?: if (s.id.startsWith("calibre")) SceneTheme.CALIBRE else null

        /**
         * The grade the app shows: the reached grade, or every grade while the developer preview is on (debug builds
         * only; never stored).
         */
        fun shownGrade(trips: List<TripRecord>, s: Settings): Grade =
            if (io.vroomi.app.BuildConfig.DEBUG && s.devAllGrades) Grade.DIAMOND else SafetyGrade.current(trips, s.gradeReached)

        /** A demo shows its scenario's "model" (clamped to 0..4), else the top step: demos and showcases play whatever the grade. */
        fun demoModelOf(s: Scenario): Int = (s.model ?: (ThemeModels.COUNT - 1)).coerceIn(0, ThemeModels.COUNT - 1)

        private val RARE_KEYS = setOf("start", "arrival", "low_battery", "hello", "goodbye")
        /** Lines that keep playing in every theme (they carry information, not character). */
        private val ALWAYS_KEYS = setOf("low_battery")

        private val LINE_IDS = mapOf(
            "start" to R.array.line_start, "smooth" to R.array.line_smooth,
            "harsh_brake" to R.array.line_harsh_brake, "fan_up" to R.array.line_fan_up,
            "temp_cold" to R.array.line_temp_cold, "temp_warm" to R.array.line_temp_warm,
            "rain" to R.array.line_rain, "arrival" to R.array.line_arrival, "rest" to R.array.line_rest,
            "low_battery" to R.array.line_low_battery, "charging" to R.array.line_charging,
            "hello" to R.array.line_hello, "goodbye" to R.array.line_goodbye,
            "combo" to R.array.line_combo, "milestone" to R.array.line_milestone,
            "biome_city" to R.array.line_biome_city, "biome_park" to R.array.line_biome_park,
            "biome_field" to R.array.line_biome_field, "biome_seaside" to R.array.line_biome_seaside,
            "biome_mountain" to R.array.line_biome_mountain,
            "reply_cooler" to R.array.line_reply_cooler, "reply_warmer" to R.array.line_reply_warmer,
            "reply_fan_up" to R.array.line_reply_fan_up, "reply_fan_down" to R.array.line_reply_fan_down,
            "reply_fan_off" to R.array.line_reply_fan_off, "reply_air" to R.array.line_reply_air,
            "reply_manual" to R.array.line_reply_manual, "reply_fail" to R.array.line_reply_fail,
            "reply_unknown" to R.array.line_reply_unknown, "reply_hide" to R.array.line_reply_hide,
            "reply_show" to R.array.line_reply_show,
        )
    }
}
