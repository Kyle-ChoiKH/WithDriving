package io.vroomi.core.vehicle.sim

import android.content.res.AssetManager
import io.vroomi.core.vehicle.model.CapabilityMap
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.SpeedSourceKind
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.provider.VehicleSignalProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The resting car the simulator starts from: parked, mild climate, nothing active. */
val PARKED_CAR = VehicleSnapshot(
    speedKmh = 0f, speedSource = SpeedSourceKind.SIMULATED, brakePressed = false, gear = GearState.PARK,
    fanLevel = 1, fanLevelRange = 0..8, setTempC = 23f, cabinTempC = 23f, acOn = false, maxAcOn = false,
    defrostOn = false, maxDefrostOn = false, airCleanerOn = false, recircOn = false, hvacAutoOn = false,
    seatHeatLevel = 0, seatVentLevel = 0, turnSignal = TurnSignal.NONE, wiperActive = false,
    headlightsOn = false, doorOpen = false, evCharging = false, evBatteryPct = 72, cruiseOn = false,
    naviGuidanceActive = false, safetyWarningActive = false, odometerKm = 12_345f,
)

/**
 * Scripted drives (assets/scenarios/\*.json) + live manual overrides from the debug panel.
 * Used on the emulator, for the demo video and as a fallback at the PT.
 */
class SimulatedVehicleProvider(
    private val scope: CoroutineScope,
    private val assets: AssetManager?,
) : VehicleSignalProvider {

    private val manual = MutableStateFlow(PARKED_CAR)
    private val _snapshot = MutableStateFlow(PARKED_CAR)
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()
    override val capabilities: StateFlow<CapabilityMap> = MutableStateFlow(CapabilityMap.ALL)

    private val _playing = MutableStateFlow<Scenario?>(null)
    val playing: StateFlow<Scenario?> = _playing.asStateFlow()
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    /** Demo caption for the current moment of the scenario (null between captions). */
    private val _caption = MutableStateFlow<String?>(null)
    val caption: StateFlow<String?> = _caption.asStateFlow()

    private var ticker: Job? = null
    private var playJob: Job? = null
    private var odometerAcc = 0.0

    override suspend fun start() {
        if (ticker != null) return
        ticker = scope.launch {
            var last = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                if (_playing.value == null) {
                    val m = manual.value
                    odometerAcc += (m.speedKmh ?: 0f) * (now - last) / 3_600_000.0
                    _snapshot.value = m.copy(
                        timestampMs = now,
                        odometerKm = (m.odometerKm ?: 0f) + odometerAcc.toFloat(),
                    )
                }
                last = now
                delay(TICK_MS)
            }
        }
    }

    override suspend fun stop() {
        ticker?.cancel(); ticker = null
        stopScenario()
    }

    fun scenarioIds(): List<String> =
        assets?.list("scenarios")?.filter { it.endsWith(".json") }?.map { it.removeSuffix(".json") }?.sorted()
            ?: emptyList()

    /** All bundled scenarios, in demo-picker order. */
    fun scenarios(): List<Scenario> = scenarioIds().mapNotNull { loadScenario(it) }.sortedBy { it.order }

    fun loadScenario(id: String): Scenario? = runCatching {
        val text = assets!!.open("scenarios/$id.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        ScenarioParser.parse(text)
    }.getOrNull()

    fun play(scenario: Scenario) {
        stopScenario()
        _playing.value = scenario
        val sampler = ScenarioSampler(scenario)
        val startedAt = System.currentTimeMillis()
        val base = PARKED_CAR
        var odo = (base.odometerKm ?: 0f).toDouble()
        playJob = scope.launch {
            var last = startedAt
            while (isActive) {
                val now = System.currentTimeMillis()
                val t = (now - startedAt) / 1000f
                val s = sampler.sample(t, base, now, startedAt)
                odo += (s.speedKmh ?: 0f) * (now - last) / 3_600_000.0
                last = now
                _snapshot.value = s.copy(odometerKm = odo.toFloat())
                _progress.value = if (scenario.durationS > 0) (t / scenario.durationS).coerceIn(0f, 1f) else 1f
                _caption.value = sampler.captionAt(t)
                if (t >= scenario.durationS) break
                delay(TICK_MS)
            }
            manual.value = _snapshot.value.copy(speedKmh = 0f, brakePressed = false, gear = GearState.PARK)
            _caption.value = null
            _playing.value = null
        }
    }

    fun stopScenario() {
        playJob?.cancel(); playJob = null
        if (_playing.value != null) manual.value = _snapshot.value.copy(speedKmh = 0f, brakePressed = false, gear = GearState.PARK)
        _playing.value = null
        _progress.value = 0f
        _caption.value = null
    }

    /** Live override from the debug panel (stops any running scenario). */
    fun setManual(change: (VehicleSnapshot) -> VehicleSnapshot) {
        if (_playing.value != null) stopScenario()
        manual.update(change)
    }

    override suspend fun setFanLevel(level: Int): Result<Unit> {
        setManual { s -> s.copy(fanLevel = level.coerceIn(s.fanLevelRange ?: 0..8)) }
        return Result.success(Unit)
    }

    override suspend fun setTemperature(celsius: Float): Result<Unit> {
        setManual { it.copy(setTempC = celsius.coerceIn(16f, 30f)) }
        return Result.success(Unit)
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
