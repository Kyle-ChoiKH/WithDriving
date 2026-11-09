package io.vroomi.core.vehicle

import io.vroomi.core.vehicle.model.Capability
import io.vroomi.core.vehicle.model.CapabilityMap
import io.vroomi.core.vehicle.model.NaviState
import io.vroomi.core.vehicle.model.SpeedSourceKind
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.provider.VehicleSignalProvider
import io.vroomi.core.vehicle.signal.DrivingEvent
import io.vroomi.core.vehicle.signal.DrivingEventDetector
import io.vroomi.core.vehicle.signal.SignalProcessor
import io.vroomi.core.vehicle.signal.Thresholds
import io.vroomi.core.vehicle.sim.SimulatedVehicleProvider
import io.vroomi.core.vehicle.speed.SpeedSourceChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SourceMode { AUTO, PLEOS, SIMULATOR }
enum class ActiveSource { PLEOS, SIMULATOR }

/**
 * Picks the active provider, filters speed at 10 Hz, derives acceleration, detects maneuvers and
 * merges navigation. Everything downstream (character, voice, diary) reads only from here.
 */
class VehicleHub(
    private val scope: CoroutineScope,
    private val pleos: VehicleSignalProvider?,
    val simulator: SimulatedVehicleProvider,
    private val speedChain: SpeedSourceChain?,
) {
    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _capabilities = MutableStateFlow(CapabilityMap())
    val capabilities: StateFlow<CapabilityMap> = _capabilities.asStateFlow()

    private val _events = MutableSharedFlow<DrivingEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DrivingEvent> = _events.asSharedFlow()

    private val _active = MutableStateFlow(ActiveSource.SIMULATOR)
    val active: StateFlow<ActiveSource> = _active.asStateFlow()

    private val _speedSource = MutableStateFlow(SpeedSourceKind.NONE)
    val speedSource: StateFlow<SpeedSourceKind> = _speedSource.asStateFlow()

    private val mode = MutableStateFlow(SourceMode.AUTO)
    private val navi = MutableStateFlow(NaviState())

    @Volatile private var realSpeedKmh: Float? = null
    private val processor = SignalProcessor()
    private val detector = DrivingEventDetector()
    private var loop: Job? = null
    private var startedAtMs = 0L

    fun start() {
        if (loop != null) return
        startedAtMs = System.currentTimeMillis()
        loop = scope.launch {
            pleos?.let { runCatching { it.start() } }
            simulator.start()
            speedChain?.let { chain ->
                _speedSource.value = chain.start { _, kmh -> realSpeedKmh = kmh }
            }
            while (isActive) {
                tick(System.currentTimeMillis())
                delay(1000L / Thresholds.SAMPLE_HZ)
            }
        }
    }

    fun stop() {
        loop?.cancel(); loop = null
        speedChain?.stop()
        scope.launch {
            pleos?.let { runCatching { it.stop() } }
            simulator.stop()
        }
    }

    fun setMode(m: SourceMode) {
        mode.value = m
    }

    fun updateNavi(state: NaviState) {
        navi.value = state
    }

    private fun chooseSource(now: Long): ActiveSource {
        val pleosCaps = pleos?.capabilities?.value
        val pleosAlive = pleosCaps != null && pleosCaps.available.isNotEmpty()
        return when (mode.value) {
            SourceMode.SIMULATOR -> ActiveSource.SIMULATOR
            SourceMode.PLEOS -> ActiveSource.PLEOS
            SourceMode.AUTO -> when {
                simulator.playing.value != null -> ActiveSource.SIMULATOR // a scenario was started on purpose
                pleosAlive -> ActiveSource.PLEOS
                now - startedAtMs < AUTO_GRACE_MS -> _active.value
                else -> ActiveSource.SIMULATOR
            }
        }
    }

    private fun tick(now: Long) {
        val src = chooseSource(now)
        if (src != _active.value) {
            processor.reset(); detector.reset()
            _active.value = src
        }
        val raw: VehicleSnapshot
        val caps: CapabilityMap
        if (src == ActiveSource.PLEOS && pleos != null) {
            val n = navi.value
            val speed = realSpeedKmh
            raw = pleos.snapshot.value.copy(
                speedKmh = speed,
                speedSource = if (speed != null) _speedSource.value else SpeedSourceKind.NONE,
                naviGuidanceActive = n.guidanceActive,
                naviRemainingM = n.remainingM,
                naviArrivedAtMs = n.arrivedAtMs,
            )
            caps = pleos.capabilities.value
                .with(Capability.SPEED, speed != null)
                .with(Capability.NAVI, n.guidanceActive != null)
        } else {
            raw = simulator.snapshot.value.copy(speedSource = SpeedSourceKind.SIMULATED)
            caps = simulator.capabilities.value
        }
        _capabilities.value = caps

        val rawSpeed = raw.speedKmh
        val out = if (rawSpeed != null) {
            val p = processor.onSpeedSample(now, rawSpeed)
            detector.update(now, p.accelMps2, raw.brakePressed).forEach { _events.tryEmit(it) }
            raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2, timestampMs = now)
        } else {
            raw.copy(accelMps2 = null, timestampMs = now)
        }
        _snapshot.value = out
    }

    suspend fun setFanLevel(level: Int): Result<Unit> =
        if (_active.value == ActiveSource.PLEOS && pleos != null) pleos.setFanLevel(level) else simulator.setFanLevel(level)

    suspend fun setTemperature(celsius: Float): Result<Unit> =
        if (_active.value == ActiveSource.PLEOS && pleos != null) pleos.setTemperature(celsius) else simulator.setTemperature(celsius)

    /** True when climate can actually be changed by Vroomi (simulator always, real car only if approved). */
    fun canControlClimate(): Boolean =
        _active.value == ActiveSource.SIMULATOR || _capabilities.value.has(Capability.HVAC_CONTROL)

    private companion object {
        const val AUTO_GRACE_MS = 4_000L
    }
}
