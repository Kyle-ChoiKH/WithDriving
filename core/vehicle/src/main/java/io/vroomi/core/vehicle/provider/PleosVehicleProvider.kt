package io.vroomi.core.vehicle.provider

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacAirCleanerStateListener
import ai.pleos.playground.vehicle.listener.HvacAutoStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacMaxAcStateListener
import ai.pleos.playground.vehicle.listener.HvacMaxDefrostStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacSeatVentilationListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import io.vroomi.core.vehicle.BuildConfig
import io.vroomi.core.vehicle.model.Capability
import io.vroomi.core.vehicle.model.CapabilityMap
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Real car signals through Pleos Vehicle SDK 2.3.2.
 *
 * Every signature here was checked against Vehicle-2.3.2-sources.jar (ai.pleos.playground:Vehicle:2.3.2):
 * - Vehicle(context).initialize()/release(); calls made after initialize() wait for the service binding.
 * - Each domain: check…Capabilities(onComplete, onFailed) -> CapabilityConfig.accessType, then register…(listener).
 * - Listeners extend VehicleListener and must implement onFailed(e).
 * Missing capability / permission => the signal stays null and Vroomi uses a neutral fallback.
 *
 * Note: there is no speed and no accelerator-pedal property in this SDK. Speed comes from SpeedSourceChain.
 */
@SuppressLint("MissingPermission") // Permissions are declared in the app manifest and requested at start;
// every call is wrapped so a missing permission only disables that one capability.
class PleosVehicleProvider(private val context: Context) : VehicleSignalProvider {

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _caps = MutableStateFlow(CapabilityMap())
    override val capabilities: StateFlow<CapabilityMap> = _caps.asStateFlow()

    private var vehicle: Vehicle? = null
    private val cleanups = mutableListOf<() -> Unit>()

    private var hvacArea: SeatArea? = null
    private var fanRange: IntRange? = null
    private var tempRange: ClosedFloatingPointRange<Float>? = null
    private var hvacWritable = false
    private val doorPositions = HashMap<DoorArea, Int>()
    private val doorMins = HashMap<DoorArea, Int>()
    private var chargePortOpen: Boolean? = null

    override suspend fun start() = withContext(Dispatchers.Main) {
        if (vehicle != null) return@withContext
        val v = try {
            Vehicle(context).also { it.initialize() }
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle service unavailable: ${t.message}")
            return@withContext
        }
        vehicle = v
        setupBrake(v)
        setupGear(v)
        setupHvac(v)
        setupTurnSignal(v)
        setupWiper(v)
        setupHeadlights(v)
        setupDoors(v)
        setupEv(v)
        setupDrivingMode(v)
        setupSeat(v)
        setupSafety(v)
        setupOdometer(v)
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        cleanups.forEach { runCatching { it() } }
        cleanups.clear()
        // release() only after unregister calls have been issued (SDK asks not to release with calls pending)
        runCatching { vehicle?.release() }
        vehicle = null
    }

    // ---------------------------------------------------------------- helpers

    private fun mark(c: Capability, ok: Boolean) = _caps.update { it.with(c, ok) }

    private fun readable(cfg: CapabilityConfig) =
        cfg.accessType == AccessType.READ || cfg.accessType == AccessType.READ_WRITE

    private fun writable(cfg: CapabilityConfig) =
        cfg.accessType == AccessType.WRITE || cfg.accessType == AccessType.READ_WRITE

    private inline fun guard(c: Capability, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$c unavailable: ${t.message}")
            mark(c, false)
        }
    }

    private fun failed(c: Capability): (Exception) -> Unit = { e ->
        Log.w(TAG, "$c failed: ${e.message}")
        mark(c, false)
    }

    // ---------------------------------------------------------------- Brake (CAR_DRIVE)

    private fun setupBrake(v: Vehicle) = guard(Capability.BRAKE_PEDAL) {
        val brake = v.getBrake()
        brake.checkBrakePedalStatusCapabilities(
            onComplete = { cfg ->
                if (readable(cfg)) guard(Capability.BRAKE_PEDAL) {
                    val l = object : BrakePedalStatusListener {
                        override fun onBrakePedalStatusUpdated(status: Boolean?) =
                            _snapshot.update { it.copy(brakePressed = status) }

                        override fun onFailed(e: Exception) = failed(Capability.BRAKE_PEDAL)(e)
                    }
                    brake.registerBrakePedalStatus(l)
                    cleanups += { brake.unregisterBrakePedalStatus(l) }
                    mark(Capability.BRAKE_PEDAL, true)
                } else mark(Capability.BRAKE_PEDAL, false)
            },
            onFailed = failed(Capability.BRAKE_PEDAL),
        )
    }

    // ---------------------------------------------------------------- Gear (CAR_DRIVE, no capability check API)

    private fun setupGear(v: Vehicle) = guard(Capability.GEAR) {
        val gear = v.getGear()
        val l = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                mark(Capability.GEAR, true)
                _snapshot.update { it.copy(gear = gear.toGearState()) }
            }

            override fun onFailed(e: Exception) = failed(Capability.GEAR)(e)
        }
        gear.registerGearSelection(l)
        cleanups += { gear.unregisterGearSelection(l) }
    }

    // ---------------------------------------------------------------- HVAC (CAR_CLIMATE / CONTROL_CAR_CLIMATE)

    private fun setupHvac(v: Vehicle) = guard(Capability.HVAC_FAN) {
        val hvac = v.getHVAC()

        hvac.checkHvacFanSpeedCapabilities(
            onComplete = { cfg, bounds ->
                val area = bounds.keys.firstOrNull()
                if (area != null && readable(cfg)) guard(Capability.HVAC_FAN) {
                    hvacArea = area
                    val b = bounds.getValue(area)
                    fanRange = b.minValue..b.maxValue
                    hvacWritable = writable(cfg)
                    mark(Capability.HVAC_CONTROL, hvacWritable && BuildConfig.ALLOW_HVAC_CONTROL)
                    _snapshot.update { it.copy(fanLevelRange = fanRange) }
                    val l = object : HvacFanSpeedListener {
                        override fun onHvacFanSpeedUpdated(speed: Int?) =
                            _snapshot.update { it.copy(fanLevel = speed) }

                        override fun onFailed(e: Exception) = failed(Capability.HVAC_FAN)(e)
                    }
                    hvac.registerHvacFanSpeed(area, l)
                    cleanups += { hvac.unregisterHvacFanSpeed(area, l) }
                    mark(Capability.HVAC_FAN, true)
                    setupHvacSeatAreaSignals(v, area)
                } else mark(Capability.HVAC_FAN, false)
            },
            onFailed = failed(Capability.HVAC_FAN),
        )

        hvac.checkHvacTemperatureCapabilities(
            onComplete = { cfg, bounds, _ ->
                val area = bounds.keys.firstOrNull()
                if (area != null && readable(cfg)) guard(Capability.HVAC_TEMP) {
                    val b = bounds.getValue(area)
                    tempRange = b.minValue..b.maxValue
                    val l = object : HvacTemperatureValueListener {
                        override fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?) =
                            _snapshot.update { it.copy(setTempC = celsius) }

                        override fun onFailed(e: Exception) = failed(Capability.HVAC_TEMP)(e)
                    }
                    hvac.registerHvacTemperature(area, l)
                    cleanups += { hvac.unregisterHvacTemperature(area, l) }
                    mark(Capability.HVAC_TEMP, true)
                } else mark(Capability.HVAC_TEMP, false)
            },
            onFailed = failed(Capability.HVAC_TEMP),
        )

        hvac.checkHvacDefrosterCapabilities(
            onComplete = { cfg, windows ->
                val area: WindowArea? = windows.firstOrNull()
                if (area != null && readable(cfg)) guard(Capability.HVAC_DEFROST) {
                    val l = object : HvacDefrosterStateListener {
                        override fun onHvacDefrosterStateUpdated(state: Boolean?) =
                            _snapshot.update { it.copy(defrostOn = state) }

                        override fun onFailed(e: Exception) = failed(Capability.HVAC_DEFROST)(e)
                    }
                    hvac.registerHvacDefrosterState(area, l)
                    cleanups += { hvac.unregisterHvacDefrosterState(area, l) }
                    mark(Capability.HVAC_DEFROST, true)
                } else mark(Capability.HVAC_DEFROST, false)
            },
            onFailed = failed(Capability.HVAC_DEFROST),
        )
    }

    /** Boolean HVAC states that are all keyed by the driver-side SeatArea. */
    private fun setupHvacSeatAreaSignals(v: Vehicle, area: SeatArea) {
        val hvac = v.getHVAC()
        guard(Capability.HVAC_AC) {
            val l = object : HvacAcStateListener {
                override fun onHvacAcStateUpdated(state: Boolean?) {
                    mark(Capability.HVAC_AC, true); _snapshot.update { it.copy(acOn = state) }
                }
                override fun onFailed(e: Exception) = failed(Capability.HVAC_AC)(e)
            }
            hvac.registerHvacAcState(area, l); cleanups += { hvac.unregisterHvacAcState(area, l) }
        }
        guard(Capability.HVAC_MAX_AC) {
            val l = object : HvacMaxAcStateListener {
                override fun onHvacMaxAcStateUpdated(state: Boolean?) {
                    mark(Capability.HVAC_MAX_AC, true); _snapshot.update { it.copy(maxAcOn = state) }
                }
                override fun onFailed(e: Exception) = failed(Capability.HVAC_MAX_AC)(e)
            }
            hvac.registerHvacMaxAcState(area, l); cleanups += { hvac.unregisterHvacMaxAcState(area, l) }
        }
        guard(Capability.HVAC_MAX_DEFROST) {
            val l = object : HvacMaxDefrostStateListener {
                override fun onHvacMaxDefrostStateUpdated(state: Boolean?) {
                    mark(Capability.HVAC_MAX_DEFROST, true); _snapshot.update { it.copy(maxDefrostOn = state) }
                }
                override fun onFailed(e: Exception) = failed(Capability.HVAC_MAX_DEFROST)(e)
            }
            hvac.registerHvacMaxDefrostState(area, l); cleanups += { hvac.unregisterHvacMaxDefrostState(area, l) }
        }
        guard(Capability.HVAC_AIR_CLEANER) {
            val l = object : HvacAirCleanerStateListener {
                override fun onHvacAirCleanerStateUpdated(state: Boolean?) {
                    mark(Capability.HVAC_AIR_CLEANER, true); _snapshot.update { it.copy(airCleanerOn = state) }
                }
                override fun onFailed(e: Exception) = failed(Capability.HVAC_AIR_CLEANER)(e)
            }
            hvac.registerHvacAirCleanerState(area, l); cleanups += { hvac.unregisterHvacAirCleanerState(area, l) }
        }
        guard(Capability.HVAC_RECIRC) {
            val l = object : HvacRecirculationStateListener {
                // The SDK names this callback "AutoRecirculation" although it is the recirculation listener.
                override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) {
                    mark(Capability.HVAC_RECIRC, true); _snapshot.update { it.copy(recircOn = state) }
                }
                override fun onFailed(e: Exception) = failed(Capability.HVAC_RECIRC)(e)
            }
            hvac.registerHvacRecirculationState(area, l); cleanups += { hvac.unregisterHvacRecirculationState(area, l) }
        }
        guard(Capability.HVAC_AUTO) {
            val l = object : HvacAutoStateListener {
                override fun onHvacAutoStateUpdated(state: Boolean?) {
                    mark(Capability.HVAC_AUTO, true); _snapshot.update { it.copy(hvacAutoOn = state) }
                }
                override fun onFailed(e: Exception) = failed(Capability.HVAC_AUTO)(e)
            }
            hvac.registerHvacAutoState(area, l); cleanups += { hvac.unregisterHvacAutoState(area, l) }
        }
    }

    // ---------------------------------------------------------------- Turn signal / lights (CAR_LIGHTS)

    private fun setupTurnSignal(v: Vehicle) = guard(Capability.TURN_SIGNAL) {
        val ts = v.getTurnSignal()
        ts.checkTurnSignalStateCapabilities(
            onComplete = { cfg ->
                if (readable(cfg)) guard(Capability.TURN_SIGNAL) {
                    val l = object : TurnSignalStateListener {
                        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) =
                            _snapshot.update { it.copy(turnSignal = state.toTurnSignal()) }

                        override fun onFailed(e: Exception) = failed(Capability.TURN_SIGNAL)(e)
                    }
                    ts.registerTurnSignalState(l)
                    cleanups += { ts.unregisterTurnSignalState(l) }
                    mark(Capability.TURN_SIGNAL, true)
                } else mark(Capability.TURN_SIGNAL, false)
            },
            onFailed = failed(Capability.TURN_SIGNAL),
        )
    }

    private fun setupHeadlights(v: Vehicle) = guard(Capability.HEADLIGHTS) {
        val light = v.getLight()
        light.checkLowBeamLightStateCapabilities(
            onComplete = { cfg, _ ->
                if (readable(cfg)) guard(Capability.HEADLIGHTS) {
                    val l = object : LightStateListener {
                        override fun onLightStateUpdated(state: LightState?) =
                            _snapshot.update { it.copy(headlightsOn = state?.let { s -> s == LightState.ON }) }

                        override fun onFailed(e: Exception) = failed(Capability.HEADLIGHTS)(e)
                    }
                    light.registerLowBeamLightState(l)
                    cleanups += { light.unregisterLowBeamLightState(l) }
                    mark(Capability.HEADLIGHTS, true)
                } else mark(Capability.HEADLIGHTS, false)
            },
            onFailed = failed(Capability.HEADLIGHTS),
        )
    }

    // ---------------------------------------------------------------- Wiper (CAR_WINDOWS)

    private fun setupWiper(v: Vehicle) = guard(Capability.WIPER) {
        val wiper = v.getWiper()
        wiper.checkWiperStateCapabilities(
            onComplete = { cfg, areas ->
                val area = areas.keys.firstOrNull()
                if (area != null && readable(cfg)) guard(Capability.WIPER) {
                    val l = object : WiperStateListener {
                        override fun onWiperStateUpdated(state: WiperState?) =
                            _snapshot.update { it.copy(wiperActive = state?.let { s -> s == WiperState.ON }) }

                        override fun onFailed(e: Exception) = failed(Capability.WIPER)(e)
                    }
                    wiper.registerWiperState(area, l)
                    cleanups += { wiper.unregisterWiperState(area, l) }
                    mark(Capability.WIPER, true)
                } else mark(Capability.WIPER, false)
            },
            onFailed = failed(Capability.WIPER),
        )
    }

    // ---------------------------------------------------------------- Doors (CAR_DOORS)

    private fun setupDoors(v: Vehicle) = guard(Capability.DOOR) {
        val door = v.getDoor()
        door.checkDoorPositionCapabilities(
            onComplete = { cfg, bounds ->
                if (bounds.isNotEmpty() && readable(cfg)) guard(Capability.DOOR) {
                    bounds.forEach { (area, b) ->
                        doorMins[area] = b.minValue
                        val l = object : DoorPositionListener {
                            override fun onPositionUpdated(pos: Int?) {
                                synchronized(doorPositions) {
                                    if (pos == null) doorPositions.remove(area) else doorPositions[area] = pos
                                    val open = doorPositions.any { (a, p) -> p > (doorMins[a] ?: 0) }
                                    _snapshot.update { it.copy(doorOpen = open) }
                                }
                            }

                            override fun onFailed(e: Exception) = failed(Capability.DOOR)(e)
                        }
                        door.registerDoorPosition(area, l)
                        cleanups += { door.unregisterDoorPosition(area, l) }
                    }
                    mark(Capability.DOOR, true)
                } else mark(Capability.DOOR, false)
            },
            onFailed = failed(Capability.DOOR),
        )
    }

    // ---------------------------------------------------------------- EV battery (CAR_ENERGY)

    private fun setupEv(v: Vehicle) {
        val ev = v.getEvBattery()
        guard(Capability.EV_BATTERY) {
            ev.checkEvBatteryStateOfChargeCapabilities(
                onComplete = { cfg ->
                    if (readable(cfg)) guard(Capability.EV_BATTERY) {
                        val l = object : EvBatteryStateOfChargeListener {
                            override fun onEvBatteryStateOfChargeUpdated(value: Int?) =
                                _snapshot.update { it.copy(evBatteryPct = value) }

                            override fun onFailed(e: Exception) = failed(Capability.EV_BATTERY)(e)
                        }
                        ev.registerEvBatteryStateOfCharge(l)
                        cleanups += { ev.unregisterEvBatteryStateOfCharge(l) }
                        mark(Capability.EV_BATTERY, true)
                    } else mark(Capability.EV_BATTERY, false)
                },
                onFailed = failed(Capability.EV_BATTERY),
            )
        }
        guard(Capability.EV_CHARGE_PORT) {
            ev.checkEvChargePortOpenCapabilities(
                onComplete = { cfg ->
                    if (readable(cfg)) guard(Capability.EV_CHARGE_PORT) {
                        val l = object : EvChargePortOpenListener {
                            override fun onEvChargePortOpenUpdated(isOpened: Boolean?) {
                                chargePortOpen = isOpened
                                // Heuristic: an open charge port while in Park means "charging".
                                // TODO(verify): a dedicated charge-state listener, if a later SDK exposes one.
                                _snapshot.update {
                                    it.copy(evCharging = isOpened?.let { o -> o && it.gear == GearState.PARK })
                                }
                            }

                            override fun onFailed(e: Exception) = failed(Capability.EV_CHARGE_PORT)(e)
                        }
                        ev.registerEvChargePortOpen(l)
                        cleanups += { ev.unregisterEvChargePortOpen(l) }
                        mark(Capability.EV_CHARGE_PORT, true)
                    } else mark(Capability.EV_CHARGE_PORT, false)
                },
                onFailed = failed(Capability.EV_CHARGE_PORT),
            )
        }
    }

    // ---------------------------------------------------------------- Driving mode / cruise (CAR_DRIVE)

    private fun setupDrivingMode(v: Vehicle) {
        val dm = v.getDrivingMode()
        guard(Capability.DRIVING_MODE) {
            dm.checkDrivingModeStateCapabilities(
                onComplete = { cfg ->
                    if (readable(cfg)) guard(Capability.DRIVING_MODE) {
                        val l = object : DrivingModeStateListener {
                            override fun onDrivingModeStateUpdated(state: DrivingModeType?) =
                                _snapshot.update { it.copy(drivingMode = state.toDriveMode()) }

                            override fun onFailed(e: Exception) = failed(Capability.DRIVING_MODE)(e)
                        }
                        dm.registerDrivingModeState(l)
                        cleanups += { dm.unregisterDrivingModeState(l) }
                        mark(Capability.DRIVING_MODE, true)
                    } else mark(Capability.DRIVING_MODE, false)
                },
                onFailed = failed(Capability.DRIVING_MODE),
            )
        }
        guard(Capability.CRUISE) {
            dm.checkCruiseControlStateCapabilities(
                onComplete = { cfg ->
                    if (readable(cfg)) guard(Capability.CRUISE) {
                        val l = object : CruiseControlStateListener {
                            override fun onCruiseControlStateUpdated(state: CruiseControl?) =
                                _snapshot.update { it.copy(cruiseOn = state?.let { s -> s == CruiseControl.ACTIVATED }) }

                            override fun onFailed(e: Exception) = failed(Capability.CRUISE)(e)
                        }
                        dm.registerCruiseControlState(l)
                        cleanups += { dm.unregisterCruiseControlState(l) }
                        mark(Capability.CRUISE, true)
                    } else mark(Capability.CRUISE, false)
                },
                onFailed = failed(Capability.CRUISE),
            )
        }
    }

    // ---------------------------------------------------------------- Seat heater / ventilation (CAR_SEATS)

    private fun setupSeat(v: Vehicle) {
        val seat = v.getSeat()
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        guard(Capability.SEAT_HEAT) {
            val l = object : HvacSeatTemperatureListener {
                override fun onHvacSeatTemperatureUpdated(temperature: Int?) {
                    mark(Capability.SEAT_HEAT, true); _snapshot.update { it.copy(seatHeatLevel = temperature) }
                }
                override fun onFailed(e: Exception) = failed(Capability.SEAT_HEAT)(e)
            }
            seat.registerHvacSeatTemperature(driver, l); cleanups += { seat.unregisterHvacSeatTemperature(driver, l) }
        }
        guard(Capability.SEAT_VENT) {
            val l = object : HvacSeatVentilationListener {
                override fun onHvacSeatVentilationUpdated(ventilation: Int?) {
                    mark(Capability.SEAT_VENT, true); _snapshot.update { it.copy(seatVentLevel = ventilation) }
                }
                override fun onFailed(e: Exception) = failed(Capability.SEAT_VENT)(e)
            }
            seat.registerHvacSeatVentilation(driver, l); cleanups += { seat.unregisterHvacSeatVentilation(driver, l) }
        }
    }

    // ---------------------------------------------------------------- Safety (CAR_SAFETY)
    // Telltales need PRIVATE_CAR_SAFETY (not for 3rd-party apps), so the public seat-belt warning is used.

    private fun setupSafety(v: Vehicle) = guard(Capability.SAFETY_WARNING) {
        val safety = v.getSafety()
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        val l = object : SeatBeltNotBuckledWarningListener {
            override fun onSeatBeltNotBuckledWarningUpdated(state: Boolean?) {
                mark(Capability.SAFETY_WARNING, true)
                _snapshot.update { it.copy(safetyWarningActive = state) }
            }

            override fun onFailed(e: Exception) = failed(Capability.SAFETY_WARNING)(e)
        }
        safety.registerSeatBeltNotBuckledWarning(driver, l)
        cleanups += { safety.unregisterSeatBeltNotBuckledWarning(driver, l) }
    }

    // ---------------------------------------------------------------- Odometer (CAR_DRIVE)

    private fun setupOdometer(v: Vehicle) = guard(Capability.ODOMETER) {
        val odo = v.getOdometer()
        val l = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                mark(Capability.ODOMETER, true)
                _snapshot.update { it.copy(odometerKm = value) }
            }

            override fun onFailed(e: Exception) = failed(Capability.ODOMETER)(e)
        }
        odo.registerDistanceDriven(l)
        cleanups += { odo.unregisterDistanceDriven(l) }
    }

    // ---------------------------------------------------------------- Control (CONTROL_CAR_CLIMATE)

    override suspend fun setFanLevel(level: Int): Result<Unit> {
        val v = vehicle ?: return Result.failure(IllegalStateException("vehicle not connected"))
        val area = hvacArea ?: return Result.failure(IllegalStateException("no HVAC area"))
        if (!BuildConfig.ALLOW_HVAC_CONTROL || !hvacWritable) {
            return Result.failure(ControlNotAllowedException("HVAC control not approved"))
        }
        val range = fanRange ?: return Result.failure(IllegalStateException("fan range unknown"))
        val target = level.coerceIn(range.first, range.last)
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                try {
                    v.getHVAC().setHvacFanSpeed(
                        area, target,
                        onComplete = { _, _ -> if (cont.isActive) cont.resume(Result.success(Unit)) },
                        onFailed = { e -> if (cont.isActive) cont.resume(Result.failure(e)) },
                    )
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(Result.failure(t))
                }
            }
        }
    }

    override suspend fun setTemperature(celsius: Float): Result<Unit> {
        val v = vehicle ?: return Result.failure(IllegalStateException("vehicle not connected"))
        val area = hvacArea ?: return Result.failure(IllegalStateException("no HVAC area"))
        if (!BuildConfig.ALLOW_HVAC_CONTROL || !hvacWritable) {
            return Result.failure(ControlNotAllowedException("HVAC control not approved"))
        }
        val r = tempRange
        val target = if (r != null) celsius.coerceIn(r.start, r.endInclusive) else celsius
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                try {
                    v.getHVAC().setHvacTemperature(
                        area, target, TemperatureUnit.CELSIUS,
                        onComplete = { _, _ -> if (cont.isActive) cont.resume(Result.success(Unit)) },
                        onFailed = { e -> if (cont.isActive) cont.resume(Result.failure(e)) },
                    )
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(Result.failure(t))
                }
            }
        }
    }

    private companion object {
        const val TAG = "VroomiPleosVehicle"
    }
}

internal fun VehicleGear?.toGearState(): GearState = when (this) {
    null, VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
    VehicleGear.GEAR_PARK -> GearState.PARK
    VehicleGear.GEAR_REVERSE -> GearState.REVERSE
    VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
    else -> GearState.DRIVE // D and manual gears 1..9
}

internal fun TurnSignalValue?.toTurnSignal(): TurnSignal? = when (this) {
    null -> null
    TurnSignalValue.OFF -> TurnSignal.NONE
    TurnSignalValue.LEFT -> TurnSignal.LEFT
    TurnSignalValue.RIGHT -> TurnSignal.RIGHT
}

internal fun DrivingModeType?.toDriveMode(): DriveModeState = when (this) {
    null -> DriveModeState.UNKNOWN
    DrivingModeType.NORMAL -> DriveModeState.NORMAL
    DrivingModeType.ECO -> DriveModeState.ECO
    DrivingModeType.SPORT -> DriveModeState.SPORT
}
