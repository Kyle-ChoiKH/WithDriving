package io.vroomi.character.engine

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * The car drives the Cutaway scene (pure Kotlin, no Compose): speed, gear and drive mode turn a virtual
 * gearbox and the VE-4T (or the VE-M1 motor on an EV), the brake cuts fuel or regenerates, the climate
 * engages the compressor and the radiator fan, the blinker clicks a relay, stars burn clean...
 * The car is only ever READ: this class has no way to reach a vehicle provider.
 *
 * Everything drawn is in slow motion ([EngineGeometry.visualRps]); the estimated rpm itself is real-time.
 */
class EngineMechanics {

    enum class Event { IGNITION, SHIFT, CLUTCH_ON, RELAY, SHUTDOWN }

    val engine = EngineModel()
    val motor = MotorModel()
    private val detector = PowertrainDetector()

    var powertrain = Powertrain.ICE; private set
    /** The drawn engine has a turbo (upgrade step 3 and up); without one the spool-up chip is never shown. */
    var turbo = true
    /** The car's gear selector as last seen. */
    var gear = GearState.UNKNOWN; private set

    // ------------------------------------------------------------------ visual state (read by the painter)
    /** Engine cycle angle 0..720° (drawn, slowed down). */
    var cycleDeg = 0.0; private set
    /** Cumulative drawn crank revolutions (belt, pulleys, chain). */
    var crankRevs = 0.0; private set
    var visualRps = 0.0; private set
    /** Seconds (decoration time) of the last ignition of each cylinder. */
    val ignitionAt = DoubleArray(4) { -100.0 }
    /** The ignition of each cylinder burns clean (after a star). */
    val cleanBurn = BooleanArray(4)
    private var cleanPending = 0
    var combustionEvents = 0; private set

    var turboAngle = 0.0; private set
    var wastegate = 0f; private set
    var acPlate = 0f; private set
    var compressorAngle = 0.0; private set
    var fanAngle = 0.0; private set
    var fanRps = 0.0; private set
    private var fanRunOnUntil = -1.0
    /** 0 coolant bypasses the heater .. 1 routed through the heater core. */
    var heater = 0f; private set
    var coolantPhase = 0.0; private set
    var flowPhase = 0.0; private set
    var throttle = 0f; private set

    // EV
    var rotorAngle = 0.0; private set
    var electricalAngle = 0.0; private set
    var cellLevel = -1f; private set
    var charging = 0f; private set
    var regenGlow = 0f; private set

    // Cabin / environment
    var night = 0f; private set
    var rain = 0f; private set
    var fog = 0f; private set
    var clear = 0f; private set
    var lamp = 0f; private set
    var relayL = 0f; private set
    var relayR = 0f; private set
    var cruise = 0f; private set
    /** −1 ECO .. 0 NORMAL .. 1 SPORT, smoothed (accent colour). */
    var modeAccent = 0f; private set

    // One-shot effects
    var rockStart = -100.0; private set
    var starGlintStart = -100.0; private set
    var comboPulseStart = -100.0; private set
    var odometer = 0f; private set
    /** 1 → 0 over 250 ms after a shift (one clutch pack releases, the next engages). */
    var shiftAnim = 0f; private set
    var shiftFrom = 1; private set
    var reverseIdler = 0.0; private set

    /** 0 normal .. 1 frozen by a safety warning. */
    var freeze = 0f; private set
    var decoTime = 0.0; private set

    var chip: String? = null; private set

    // ------------------------------------------------------------------ internal
    private var initialized = false
    private var time = 0.0
    private var seenStar = -1
    private var seenCombo = -1
    private var seenHarsh = -1
    private var seenArrival = -1
    private var arrivedPending = false
    private var arrivedChipUntil = -1.0
    private var shutdownChipUntil = -1.0
    private var lastShifts = 0
    private var shiftChipUntil = -1.0
    private var acSince = -100.0
    private var lastAc = false
    private var blinkPhase = 0.0
    private var blinkActive = TurnSignal.NONE
    private var chipKind = ChipKind.NONE
    private var chipSince = -100.0
    private var lastBoost = 0.0
    private var spoolSince = -100.0
    private val events = ArrayList<Event>()

    private enum class ChipKind { NONE, ARRIVED, FUEL_CUT, REGEN, CHARGING, SHIFT, SPOOL, AC, CRUISE, IDLE, DRIVE, MOTOR, STOPPED }

    fun drainEvents(): List<Event> = ArrayList(events).also { events.clear() }

    /**
     * [choice]: the user's powertrain setting; [forced] overrides it (a demo). [neutralThrottle] free-revs
     * the engine in P/N (tests and simulator only).
     */
    fun step(
        frameDt: Float, target: CharacterTarget, nowMs: Long, hourOfDay: Float,
        choice: EnginePowertrain, forced: Powertrain? = null,
        calm: Boolean = false, reducedMotion: Boolean = false, neutralThrottle: Double = 0.0,
    ) {
        val dt = frameDt.coerceIn(0f, MAX_FRAME_DT).toDouble()
        val c = target.car
        val j = target.journey
        time += dt
        gear = c.gear

        val detected = detector.update(c.evBatteryPct, c.evCharging, j.active, nowMs)
        powertrain = forced ?: when (choice) { EnginePowertrain.ICE -> Powertrain.ICE; EnginePowertrain.EV -> Powertrain.EV; EnginePowertrain.AUTO -> detected }

        val safety = c.safetyWarningActive == true
        freeze = approach(freeze, if (safety) 1f else 0f, (dt / if (safety) 0.35 else 1.2).toFloat())
        val ddt = dt * (1f - freeze)
        decoTime += ddt

        if (!initialized) {
            initialized = true
            seenStar = j.starSeq; seenCombo = j.comboSeq; seenHarsh = c.harshBrakeSeq; seenArrival = c.arrivalSeq
            night = if (isNight(c, hourOfDay)) 1f else 0f
            heater = heaterTarget(c, 0f)
            odometer = j.milestoneKm.toFloat()
            lastAc = c.acOn == true || c.maxAcOn == true
        }

        // ---- models: real time (the gauge never freezes)
        engine.step(dt, c, neutralThrottle)
        motor.step(dt, c)

        // ---- arrival: in P the engine shuts down (crank coasts, the fan runs on)
        if (c.arrivalSeq != seenArrival) { seenArrival = c.arrivalSeq; arrivedPending = true; arrivedChipUntil = time + 6.0 }
        if (arrivedPending && c.gear == GearState.PARK) {
            arrivedPending = false
            if (powertrain == Powertrain.ICE && engine.running) {
                engine.shutDown(); fanRunOnUntil = time + FAN_RUN_ON_S; shutdownChipUntil = time + 6.0
                events += Event.SHUTDOWN
            }
        }

        stepMechanism(ddt, c, calm, reducedMotion)
        stepJourney(c, j)
        stepCabin(dt, ddt, c, hourOfDay)
        chip = chooseChip(c)
        while (events.size > 64) events.removeAt(0)
    }

    private fun stepMechanism(ddt: Double, c: CarSignals, calm: Boolean, reducedMotion: Boolean) {
        val cap = if (calm) EngineGeometry.CALM_VISUAL_RPS else EngineGeometry.MAX_VISUAL_RPS
        if (powertrain == Powertrain.ICE) {
            visualRps = if (reducedMotion) 0.0 else min(cap, EngineGeometry.visualRps(engine.rpm))
            val before = cycleDeg
            cycleDeg = EngineGeometry.wrap720(cycleDeg + visualRps * 360.0 * ddt)
            crankRevs += visualRps * ddt
            // Ignition: each cylinder fires when its phase passes 0° (compression TDC).
            if (visualRps > 0) for (i in 0 until 4) {
                val p0 = EngineGeometry.phase(before, i); val p1 = EngineGeometry.phase(cycleDeg, i)
                if (p1 < p0 && engine.running && !engine.fuelCut) {
                    ignitionAt[i] = decoTime
                    cleanBurn[i] = cleanPending > 0
                    if (cleanPending > 0) cleanPending--
                    combustionEvents++
                    events += Event.IGNITION
                }
            }
            turboAngle += (0.12 + 1.6 * engine.boost) * (if (reducedMotion) 0.0 else 1.0) * ddt * 2 * PI
            wastegate = approach(wastegate, if (engine.wastegateOpen) 1f else 0f, (ddt / 0.25).toFloat())
            val acOn = engine.acClutch
            if (acOn && !lastAc) { acSince = time; events += Event.CLUTCH_ON }
            lastAc = acOn
            acPlate = approach(acPlate, if (acOn) 1f else 0f, (ddt / 0.15).toFloat())
            compressorAngle += acPlate * visualRps * 1.25 * ddt * 2 * PI
            throttle = approach(throttle, engine.load.toFloat(), (ddt / 0.15).toFloat())
            flowPhase += (0.25 + engine.load) * visualRps.coerceAtLeast(0.2) * ddt
            if (engine.gearbox.shifts != lastShifts) {
                shiftFrom = engine.gearbox.previousGear; lastShifts = engine.gearbox.shifts
                shiftAnim = 1f; shiftChipUntil = time + 3.0; events += Event.SHIFT
            }
            shiftAnim = approach(shiftAnim, 0f, (ddt / Gearbox.SHIFT_ANIM_S).toFloat())
            if (c.gear == GearState.REVERSE) reverseIdler += visualRps * 1.6 * ddt * 2 * PI
            if (engine.boost > lastBoost + 0.002 && engine.boost > 0.3) { if (time - spoolSince > 6) spoolSince = time }
            lastBoost = engine.boost
        } else {
            visualRps = if (reducedMotion) 0.0 else EngineGeometry.motorVisualRps(motor.rpm).coerceIn(-cap, cap)
            rotorAngle += visualRps * ddt * 2 * PI
            electricalAngle = rotorAngle * EngineGeometry.MOTOR_POLE_PAIRS
            val pw = motor.powerKw
            flowPhase += (0.3 + min(1.5, abs(pw) / 40.0)) * (if (pw >= 0) 1.0 else -1.0) * ddt
            regenGlow = approach(regenGlow, if (motor.regen) 1f else 0f, (ddt / 0.4).toFloat())
            compressorAngle += (if (motor.compressorOn) 0.9 else 0.0) * ddt * 2 * PI
            val acOn = motor.compressorOn
            if (acOn && !lastAc) acSince = time
            lastAc = acOn
            acPlate = approach(acPlate, if (acOn) 1f else 0f, (ddt / 0.3).toFloat())
            if (c.gear == GearState.REVERSE) reverseIdler += visualRps * ddt * 2 * PI
        }

        // Radiator fan: proportional to the climate fan level, runs on after shutdown.
        val fanMax = (c.fanMax ?: 8).coerceAtLeast(1)
        val runOn = time < fanRunOnUntil
        val fanT = if (reducedMotion) 0.0 else when {
            runOn -> 1.0
            c.fanLevel != null && c.fanLevel > 0 -> 0.25 + 0.95 * (c.fanLevel.toDouble() / fanMax).coerceIn(0.0, 1.0)
            else -> 0.0
        } * (if (calm) 0.5 else 1.0)
        fanRps += (fanT - fanRps) * min(1.0, ddt * 1.5)
        fanAngle += fanRps * ddt * 2 * PI
    }

    private fun stepJourney(c: CarSignals, j: io.vroomi.character.Journey) {
        if (c.harshBrakeSeq != seenHarsh) { seenHarsh = c.harshBrakeSeq; rockStart = decoTime }
        if (j.starSeq != seenStar) {
            if (j.starSeq > seenStar) { starGlintStart = decoTime; cleanPending = 4 }
            seenStar = j.starSeq
        }
        if (j.comboSeq != seenCombo) {
            if (j.comboSeq > seenCombo) comboPulseStart = decoTime
            seenCombo = j.comboSeq
        }
        odometer = approach(odometer, j.milestoneKm.toFloat(), 0.02f)
    }

    private fun stepCabin(dt: Double, ddt: Double, c: CarSignals, hourOfDay: Float) {
        night = approach(night, if (isNight(c, hourOfDay)) 1f else 0f, (dt / 1.5).toFloat())
        rain = approach(rain, if (c.wiperActive == true) 1f else 0f, (ddt / 1.5).toFloat())
        val defrost = c.defrostOn == true
        if (defrost) {
            clear = approach(clear, 1f, (dt / DEFROST_S).toFloat())
            if (clear >= 1f) fog = approach(fog, 0f, (dt / 0.3).toFloat())
        } else {
            val humid = rain > 0.5f
            fog = approach(fog, if (humid) 0.7f else 0f, (dt / if (humid) 14.0 else 6.0).toFloat())
            if (fog < 0.02f) clear = 0f
        }
        heater = approach(heater, heaterTarget(c, heater), (dt / 2.0).toFloat())
        coolantPhase += ddt * (0.2 + 0.6 * visualRps.coerceAtLeast(0.0).coerceAtMost(1.4))
        val parked = !c.moving
        lamp = approach(lamp, if (parked && c.doorOpen == true) 1f else 0f, (dt / 0.3).toFloat())
        cruise = approach(cruise, if (c.cruiseOn == true) 1f else 0f, (dt / 0.6).toFloat())
        val modeT = when (c.drivingMode) { io.vroomi.core.vehicle.model.DriveModeState.ECO -> -1f; io.vroomi.core.vehicle.model.DriveModeState.SPORT -> 1f; else -> 0f }
        modeAccent = approach(modeAccent, modeT, (dt / 1.5).toFloat())
        if (powertrain == Powertrain.EV) {
            val pct = c.evBatteryPct
            cellLevel = if (pct == null) -1f else if (cellLevel < 0f) pct / 100f else approach(cellLevel, pct / 100f, (dt * 0.2).toFloat())
            charging = approach(charging, if (c.evCharging == true) 1f else 0f, (dt / 1.0).toFloat())
        }

        // Relay in the fuse box: contacts close for half of each blinker period (1.5 Hz, like the car).
        val turn = c.turnSignal ?: TurnSignal.NONE
        if (turn != blinkActive) { blinkActive = turn; blinkPhase = 0.0 }
        if (turn != TurnSignal.NONE) {
            val before = floor(blinkPhase)
            blinkPhase += ddt * BLINK_HZ
            if (floor(blinkPhase) > before) events += Event.RELAY
        }
        val on = turn != TurnSignal.NONE && (blinkPhase % 1.0) < 0.5
        relayL = approach(relayL, if (on && (turn == TurnSignal.LEFT || turn == TurnSignal.HAZARD)) 1f else 0f, (dt / 0.06).toFloat())
        relayR = approach(relayR, if (on && (turn == TurnSignal.RIGHT || turn == TurnSignal.HAZARD)) 1f else 0f, (dt / 0.06).toFloat())
    }

    /** Engine rock after a harsh brake: one damped wobble, at most 1 (× 6 px in the painter). */
    fun rock(): Float {
        val t = decoTime - rockStart
        if (t < 0 || t > 2.0) return 0f
        return (exp(-t / 0.35) * sin(2 * PI * 2.2 * t)).toFloat()
    }

    /** Combustion glow of a cylinder 0..1: about 60 ms attack and 350 ms decay, scaled by load. */
    fun glow(cylinder: Int): Float {
        val t = decoTime - ignitionAt[cylinder]
        if (t < 0 || t > 1.4) return 0f
        val env = if (t < ATTACK_S) t / ATTACK_S else exp(-(t - ATTACK_S) / DECAY_S)
        return (env * (0.35 + 0.65 * engine.load)).toFloat()
    }

    // ------------------------------------------------------------------ status chip

    private fun chooseChip(c: CarSignals): String? {
        if (c.safetyWarningActive == true) { chipKind = ChipKind.NONE; return null }
        val ice = powertrain == Powertrain.ICE
        val stopped = (c.speedKmh ?: 0f) < 1f
        val wanted = when {
            time < arrivedChipUntil || time < shutdownChipUntil -> ChipKind.ARRIVED
            ice && engine.fuelCut -> ChipKind.FUEL_CUT
            !ice && motor.regen && !stopped -> ChipKind.REGEN
            !ice && charging > 0.5f -> ChipKind.CHARGING
            ice && time < shiftChipUntil && c.gear == GearState.DRIVE -> ChipKind.SHIFT
            ice && turbo && time - spoolSince < 4.0 -> ChipKind.SPOOL
            time - acSince < 6.0 -> ChipKind.AC
            c.cruiseOn == true -> ChipKind.CRUISE
            ice && engine.running && stopped -> ChipKind.IDLE
            ice && engine.running -> ChipKind.DRIVE
            !ice && !stopped -> ChipKind.MOTOR
            else -> ChipKind.STOPPED
        }
        if (wanted != chipKind && (chipKind == ChipKind.NONE || time - chipSince >= CHIP_HOLD_S)) { chipKind = wanted; chipSince = time }
        return when (chipKind) {
            ChipKind.NONE -> null
            ChipKind.ARRIVED -> "도착"
            ChipKind.FUEL_CUT -> "연료 차단"
            ChipKind.REGEN -> "회생 제동"
            ChipKind.CHARGING -> "충전 중"
            ChipKind.SHIFT -> SHIFT_CHIPS[engine.gearLabel] ?: "변속"
            ChipKind.SPOOL -> "터보 스풀업"
            ChipKind.AC -> if (ice) "에어컨 클러치" else "전동 컴프레서"
            ChipKind.CRUISE -> "크루즈"
            ChipKind.IDLE -> "공회전"
            ChipKind.DRIVE -> DRIVE_CHIPS[engine.gearLabel] ?: "주행"
            ChipKind.MOTOR -> "모터 구동"
            ChipKind.STOPPED -> if (ice) "엔진 정지" else "모터 정지"
        }
    }

    companion object {
        const val MAX_FRAME_DT = 0.1f
        const val ATTACK_S = 0.06
        const val DECAY_S = 0.35
        const val FAN_RUN_ON_S = 8.0
        const val DEFROST_S = 2.5
        const val BLINK_HZ = 1.5
        const val CHIP_HOLD_S = 4.0

        private val NOT_DRIVE = mapOf("R" to "후진", "N" to "공회전", "P" to "공회전")
        private val SHIFT_CHIPS: Map<String, String> = EngineModel.D_LABELS.associateWith { "$it 변속" } + NOT_DRIVE
        private val DRIVE_CHIPS: Map<String, String> = EngineModel.D_LABELS.associateWith { "$it 주행" } + NOT_DRIVE

        /** Every text the chip can show (the honesty test checks them). */
        val ALL_CHIPS: List<String> = listOf("도착", "연료 차단", "회생 제동", "충전 중", "터보 스풀업", "에어컨 클러치", "전동 컴프레서",
            "크루즈", "공회전", "후진", "모터 구동", "엔진 정지", "모터 정지", "변속", "주행") + SHIFT_CHIPS.values + DRIVE_CHIPS.values

        fun isNight(c: CarSignals, hour: Float): Boolean = c.headlightsOn == true || hour < 6.5f || hour >= 18.5f

        /** 24 °C or above: coolant through the heater core; 20 °C or below: bypass; in between it blends. */
        fun heaterTarget(c: CarSignals, current: Float): Float = c.setTempC?.let { ((it - 20f) / 4f).coerceIn(0f, 1f) } ?: current

        private fun approach(v: Float, target: Float, step: Float): Float =
            if (v < target) minOf(target, v + step) else maxOf(target, v - step)
    }
}
