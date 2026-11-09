package io.vroomi.character.calibre

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The car drives the movement (pure Kotlin, no Compose): speed spins the rotor, the climate fan turns
 * the crown and winds the mainspring, the blinker strikes a gong, a trip runs the chronograph...
 * The car is only ever READ: this class has no way to reach a vehicle provider.
 *
 * Timekeeping is not here on purpose: hands and balance come from [CalibreTime] and the real clock,
 * so nothing in this class can make the watch show a wrong time.
 */
class CalibreMechanics(initialReserveH: Float = 41f) {

    enum class Event { JEWEL_GLINT, TRAIN_GLINT, CHRONO_START, CHRONO_STOP, CHRONO_RESET, HAMMER_LEFT, HAMMER_RIGHT, CHIME, SHOCK, CLICK, FULL_WIND }

    // ------------------------------------------------------------------ outputs (read by the painter)
    /** Rotor angle in radians in the watch frame: the direction of its heavy segment (π/2 = down). */
    var rotorAngle = PI / 2; private set
    var rotorOmega = 0.0; private set
    var crownAngle = 0.0; private set
    var crownRps = 0.0; private set
    var crownWheelAngle = 0.0; private set
    var ratchetAngle = 0.0; private set
    /** 0 (resting in a tooth) .. 1 (top of the tooth, about to drop). */
    var clickLift = 0f; private set
    var reverserA = 0.0; private set
    var reverserB = 0.0; private set
    /** Which reverser wheel drives the winding (0 = A for clockwise rotor, 1 = B for anticlockwise). */
    var reverserDriver = 0; private set
    var reserveH = initialReserveH.coerceIn(MIN_RESERVE_H, CalibreGeometry.POWER_RESERVE_H); private set
    /** The barrel is full and the bridle slips. */
    var slipping = false; private set
    var slipPhase = 0f; private set
    /** Shock-protection spring flex 1 -> 0 after a harsh brake (about two frames). */
    var shock = 0f; private set

    var chronoRunning = false; private set
    var chronoElapsedMs = 0L; private set
    var columnSteps = 0; private set
    /** Animated column-wheel angle (radians), follows [columnSteps]. */
    var columnAngle = 0.0; private set
    var pusher = 0f; private set
    var resetHammer = 0f; private set

    var hammerL = 0f; private set
    var hammerR = 0f; private set
    var gongL = 0f; private set
    var gongR = 0f; private set

    /** Decorative time (seconds); stops while a safety warning freezes decoration. */
    var decoTime = 0.0; private set
    var glintStart = -100.0; private set
    var trainGlintStart = -100.0; private set
    var kmDivisions = 0f; private set
    var crownGlint = 0f; private set

    var winder = 0f; private set
    /** Angle of the whole watch on the winder cradle (radians). */
    var winderAngle = 0f; private set
    var batteryPct: Int? = null; private set

    /** 0 cold white key light (17 °C) .. 1 warm gold (27 °C). */
    var warm = 0.5f; private set
    var cool = 0f; private set
    var night = 0f; private set
    var rain = 0f; private set
    var fog = 0f; private set
    /** Defrost clear line from the bottom of the crystal (0 .. 1). */
    var clear = 0f; private set
    var autoMark = 0f; private set

    var tone: CalibreTone = CalibreTone.YELLOW_GOLD; private set
    var prevTone: CalibreTone = CalibreTone.YELLOW_GOLD; private set
    /** Cross-fade from [prevTone] to [tone] (1 = done). */
    var toneBlend = 1f; private set

    /** 0 normal .. 1 frozen by a safety warning. */
    var freeze = 0f; private set

    /** Smoothed longitudinal acceleration and blinker steer, for the parallax. */
    var accelSmooth = 0f; private set
    var steerSmooth = 0f; private set

    var chip: String? = null; private set

    // ------------------------------------------------------------------ internal state
    private var initialized = false
    private var lastTripSeq = -1
    private var seenStarSeq = -1
    private var seenComboSeq = -1
    private var seenHarsh = -1
    private var seenArrival = -1
    private var chronoStartMs = 0L
    private var chronoBase = 0L
    private var blinkPhase = 0.0
    private var blinkActive = TurnSignal.NONE
    private val chimeAt = DoubleArray(3) { -1.0 }
    private val chimeRight = booleanArrayOf(true, true, false)
    private var lastFanLevel: Int? = null
    private var fanChangedAt = -100.0
    private var lastToothIndex = 0L
    private var chipKind = ChipKind.NONE
    private var chipSince = -100.0
    private var idleKind = ChipKind.RESERVE
    private var idleSince = 0.0
    private var glintChipUntil = -100.0
    private var time = 0.0 // real seconds since creation (chips, holds)
    private var lastStepMs = 0L
    /** Wall clock of the last drain: the barrel also runs down while the screen is not drawing. */
    private var lastDrainMs = Long.MIN_VALUE
    private val events = ArrayList<Event>()

    private enum class ChipKind { NONE, FULL, GLINT, WINDER, CROWN, CHRONO, RESERVE }

    fun drainEvents(): List<Event> = ArrayList(events).also { events.clear() }

    /** Restores the persisted reserve: real time keeps running while the app is closed. */
    fun restoreReserve(savedH: Float, savedAtMs: Long, nowMs: Long) {
        reserveH = reserveAfter(savedH, savedAtMs, nowMs)
    }

    fun step(
        frameDt: Float,
        target: CharacterTarget,
        nowMs: Long,
        hourOfDay: Float,
        finish: CalibreFinish,
        calm: Boolean = false,
        reducedMotion: Boolean = false,
    ) {
        val dt = frameDt.coerceIn(0f, MAX_FRAME_DT).toDouble()
        val c = target.car
        val j = target.journey
        time += dt
        lastStepMs = nowMs

        // ---- safety: decoration freezes, timekeeping does not
        val safety = c.safetyWarningActive == true
        freeze = approach(freeze, if (safety) 1f else 0f, (dt / if (safety) 0.35 else 1.2).toFloat())
        val ddt = dt * (1f - freeze)
        decoTime += ddt

        if (!initialized) {
            initialized = true
            seenStarSeq = j.starSeq; seenComboSeq = j.comboSeq
            seenHarsh = c.harshBrakeSeq; seenArrival = c.arrivalSeq
            lastFanLevel = c.fanLevel
            tone = finish.tone(c.drivingMode); prevTone = tone; toneBlend = 1f
            warm = warmTarget(c)
            night = if (isNight(c, hourOfDay)) 1f else 0f
            if (c.evCharging == true) winder = 1f
        }

        stepRotor(ddt, c, calm, reducedMotion)
        stepWinding(dt, ddt, c, reducedMotion)
        stepChrono(dt, j, nowMs)
        stepHammers(ddt, c, calm)
        stepJourneyFx(j)
        stepCabin(dt, ddt, c, hourOfDay, finish)
        chip = chooseChip(c)
        // Nobody may be draining (sound off): keep only the latest events.
        while (events.size > 64) events.removeAt(0)
    }

    // ------------------------------------------------------------------ rotor

    private fun stepRotor(ddt: Double, c: CarSignals, calm: Boolean, reducedMotion: Boolean) {
        val v = c.speedKmh ?: 0f
        val stationary = v < 1f
        val dir = if (c.gear == GearState.REVERSE) -1.0 else 1.0
        val cap = if (calm) CALM_RPM else MAX_RPM
        val targetRpm = if (stationary) 0.0 else min(cap, BASE_RPM + RPM_PER_KMH * v)
        val target = dir * targetRpm / 60.0 * 2 * PI
        val a = (c.accelMps2 ?: 0f).coerceIn(-8f, 8f).toDouble()

        if (c.harshBrakeSeq != seenHarsh) {
            seenHarsh = c.harshBrakeSeq
            shock = 1f
            rotorOmega -= dir * HARSH_KICK
            events += Event.SHOCK
        }
        shock = approach(shock, 0f, (ddt / SHOCK_S).toFloat())
        accelSmooth += ((a.toFloat() - accelSmooth) * min(1.0, ddt * 3)).toFloat()

        // Winder cradle: the whole watch turns, so "down" moves in the watch frame.
        val rest = PI / 2 - winderAngle

        if (reducedMotion) return // only hands, balance and crown move
        var left = ddt
        val k = if (targetRpm > 0.0) K_DRIVE else K_REST
        while (left > 1e-9) {
            val h = min(SUBSTEP, left)
            val alpha = k * (target - rotorOmega) - GRAVITY * sin(rotorAngle - rest) - ACCEL_COUPLING * a * cos(rotorAngle) - DAMPING * rotorOmega
            rotorOmega += alpha * h
            val dTheta = rotorOmega * h
            rotorAngle += dTheta
            // Bidirectional winding: both directions wind the mainspring.
            val revs = abs(dTheta) / (2 * PI)
            reserveH += (revs * ROTOR_WIND_H).toFloat()
            ratchetAngle += abs(dTheta) / CalibreGeometry.ROTOR_PER_RATCHET
            left -= h
        }
        rotorAngle = wrap(rotorAngle)
        reverserDriver = if (rotorOmega >= 0) 0 else 1
        // The rotor pinion turns both reversers; the clutched one drives, the other idles slower.
        reverserA += -rotorOmega * ddt * (if (reverserDriver == 0) 2.2 else 0.9)
        reverserB += -rotorOmega * ddt * (if (reverserDriver == 1) 2.2 else 0.9)
    }

    // ------------------------------------------------------------------ crown, ratchet, reserve

    private fun stepWinding(dt: Double, ddt: Double, c: CarSignals, reducedMotion: Boolean) {
        val wanted = crownTargetRps(c)
        crownRps += (wanted - crownRps) * min(1.0, dt * 6)
        if (wanted == 0.0 && crownRps < 0.01) crownRps = 0.0
        if (c.fanLevel != lastFanLevel) {
            if (c.fanLevel != null && (c.fanLevel > (lastFanLevel ?: 0))) fanChangedAt = time
            lastFanLevel = c.fanLevel
        }
        val crownTurns = crownRps * dt
        crownAngle += crownTurns * 2 * PI
        crownWheelAngle -= crownTurns * 2 * PI * CalibreGeometry.WINDING_PINION / CalibreGeometry.CROWN_WHEEL_TEETH
        ratchetAngle += crownTurns * 2 * PI * CalibreGeometry.ratchetPerCrownTurn()
        reserveH += (crownTurns * CROWN_WIND_H).toFloat()

        val maxed = c.maxAcOn == true || (c.fanLevel != null && c.fanLevel >= (c.fanMax ?: DEFAULT_FAN_MAX))
        crownGlint = if (maxed && crownRps > 0) ((crownGlint + ddt * 0.8) % 1.0).toFloat() else 0f

        // Winder cradle (EV charging)
        winder = approach(winder, if (c.evCharging == true) 1f else 0f, (dt / 1.5).toFloat())
        batteryPct = c.evBatteryPct
        winderAngle = (winder * 0.55 * sin(decoTime * 2 * PI / 7.0)).toFloat()
        if (winder > 0.5f) reserveH += (dt * WINDER_H_PER_S * winder).toFloat()

        // Real (wall-clock) time drains the barrel, also across pauses of the frame clock.
        if (lastDrainMs == Long.MIN_VALUE) lastDrainMs = lastStepMs
        val elapsedMs = (lastStepMs - lastDrainMs).coerceIn(0L, 7L * 24 * 3_600_000)
        lastDrainMs = lastStepMs
        reserveH -= elapsedMs / 3_600_000f
        val winding = crownRps > 0.0 || abs(rotorOmega) > 0.3 || winder > 0.5f
        val full = reserveH >= CalibreGeometry.POWER_RESERVE_H
        if (full && winding && !slipping) events += Event.FULL_WIND
        slipping = full && winding
        if (slipping) slipPhase = ((slipPhase + ddt * 3.0) % 1.0).toFloat()
        reserveH = reserveH.coerceIn(MIN_RESERVE_H, CalibreGeometry.POWER_RESERVE_H)

        // Click rides up each ratchet tooth and drops into the next one.
        val toothF = ratchetAngle / (2 * PI / CalibreGeometry.RATCHET_TEETH)
        val tooth = floor(toothF).toLong()
        clickLift = (toothF - tooth).toFloat()
        if (tooth != lastToothIndex) { lastToothIndex = tooth; events += Event.CLICK }
        if (reducedMotion && crownRps == 0.0) clickLift = 0f
    }

    // ------------------------------------------------------------------ chronograph

    private fun stepChrono(dt: Double, j: Journey, nowMs: Long) {
        if (j.active && !chronoRunning && j.tripSeq != lastTripSeq) {
            lastTripSeq = j.tripSeq
            if (chronoBase > 0L) { // flyback: reset first
                chronoBase = 0L
                columnSteps++
                resetHammer = 1f
                events += Event.CHRONO_RESET
            }
            chronoRunning = true
            chronoStartMs = nowMs
            columnSteps++
            pusher = 1f
            events += Event.CHRONO_START
        } else if (!j.active && chronoRunning) {
            chronoBase += (nowMs - chronoStartMs).coerceAtLeast(0L)
            chronoRunning = false
            columnSteps++
            pusher = 1f
            events += Event.CHRONO_STOP
        }
        chronoElapsedMs = chronoBase + if (chronoRunning) (nowMs - chronoStartMs).coerceAtLeast(0L) else 0L
        val wantCol = columnSteps * 2 * PI / CalibreGeometry.COLUMNS
        columnAngle += (wantCol - columnAngle) * min(1.0, dt * 14)
        pusher = approach(pusher, 0f, (dt / 0.35).toFloat())
        resetHammer = approach(resetHammer, 0f, (dt / 0.6).toFloat())
    }

    // ------------------------------------------------------------------ hammers and gongs

    private fun stepHammers(ddt: Double, c: CarSignals, calm: Boolean) {
        val turn = c.turnSignal ?: TurnSignal.NONE
        if (turn != blinkActive) {
            blinkActive = turn
            blinkPhase = if (turn == TurnSignal.NONE) 0.0 else 0.999 // strike right away
        }
        if (turn != TurnSignal.NONE) {
            val before = floor(blinkPhase)
            blinkPhase += ddt * BLINK_HZ
            if (floor(blinkPhase) > before) {
                if (turn == TurnSignal.LEFT || turn == TurnSignal.HAZARD) strike(left = true)
                if (turn == TurnSignal.RIGHT || turn == TurnSignal.HAZARD) strike(left = false)
            }
        }
        if (c.arrivalSeq != seenArrival) {
            seenArrival = c.arrivalSeq
            chimeAt[0] = decoTime; chimeAt[1] = decoTime + 0.35; chimeAt[2] = decoTime + 0.85
            events += Event.CHIME
        }
        for (i in 0 until 3) if (chimeAt[i] >= 0 && decoTime >= chimeAt[i]) {
            chimeAt[i] = -1.0
            strike(left = !chimeRight[i])
        }
        val hd = (ddt / 0.22).toFloat()
        hammerL = approach(hammerL, 0f, hd); hammerR = approach(hammerR, 0f, hd)
        val g = (1.0 - min(1.0, ddt * 3.0)).toFloat()
        gongL *= g; gongR *= g
        if (calm) { gongL = 0f; gongR = 0f }
        val steerT = when (turn) { TurnSignal.LEFT -> -1f; TurnSignal.RIGHT -> 1f; else -> 0f }
        steerSmooth += ((steerT - steerSmooth) * min(1.0, ddt * 2.5)).toFloat()
    }

    private fun strike(left: Boolean) {
        if (left) { hammerL = 1f; gongL = 1f; events += Event.HAMMER_LEFT } else { hammerR = 1f; gongR = 1f; events += Event.HAMMER_RIGHT }
    }

    // ------------------------------------------------------------------ stars, combos, km

    private fun stepJourneyFx(j: Journey) {
        if (j.starSeq != seenStarSeq) {
            if (j.starSeq > seenStarSeq) { glintStart = decoTime; glintChipUntil = time + 3.0; events += Event.JEWEL_GLINT }
            seenStarSeq = j.starSeq
        }
        if (j.comboSeq != seenComboSeq) {
            if (j.comboSeq > seenComboSeq) { trainGlintStart = decoTime; glintChipUntil = time + 3.0; events += Event.TRAIN_GLINT }
            seenComboSeq = j.comboSeq
        }
        kmDivisions = approach(kmDivisions, j.milestoneKm.toFloat(), 0.02f)
    }

    // ------------------------------------------------------------------ light, crystal, finish

    private fun stepCabin(dt: Double, ddt: Double, c: CarSignals, hourOfDay: Float, finish: CalibreFinish) {
        warm = approach(warm, warmTarget(c), (dt / 3.0).toFloat())
        cool = approach(cool, if (c.acOn == true || c.maxAcOn == true) 1f else 0f, (dt / 3.0).toFloat())
        night = approach(night, if (isNight(c, hourOfDay)) 1f else 0f, (dt / 1.5).toFloat())
        autoMark = approach(autoMark, if (c.hvacAutoOn == true) 1f else 0f, (dt / 0.8).toFloat())
        rain = approach(rain, if (c.wiperActive == true) 1f else 0f, (ddt / 1.5).toFloat())

        val defrost = c.defrostOn == true
        if (defrost) {
            clear = approach(clear, 1f, (dt / DEFROST_S).toFloat())
            if (clear >= 1f) fog = approach(fog, 0f, (dt / 0.3).toFloat())
        } else {
            val humid = rain > 0.5f
            fog = approach(fog, if (humid) 0.75f else 0f, (dt / if (humid) 14.0 else 6.0).toFloat())
            if (fog < 0.02f) clear = 0f
        }

        val t = finish.tone(c.drivingMode)
        if (t != tone) { prevTone = tone; tone = t; toneBlend = 0f }
        toneBlend = approach(toneBlend, 1f, (dt / 1.5).toFloat())
    }

    // ------------------------------------------------------------------ status chip

    private fun chooseChip(c: CarSignals): String? {
        if (c.safetyWarningActive == true) { chipKind = ChipKind.NONE; return null }
        val crownNew = crownRps > 0.0 && time - fanChangedAt < 8.0
        // While nothing happens, alternate reserve and chronograph every 10 s.
        if (time - idleSince > 10.0) {
            idleSince = time
            idleKind = if (idleKind == ChipKind.RESERVE && chronoRunning && chronoElapsedMs >= 60_000) ChipKind.CHRONO else ChipKind.RESERVE
        }
        val wanted = when {
            slipping && (crownRps > 0.0 || winder > 0.5f) -> ChipKind.FULL
            time < glintChipUntil -> ChipKind.GLINT
            winder > 0.5f -> ChipKind.WINDER
            crownNew -> ChipKind.CROWN
            idleKind == ChipKind.CHRONO && chronoRunning -> ChipKind.CHRONO
            else -> ChipKind.RESERVE
        }
        // Hold every chip at least 4 s so nothing flickers.
        if (wanted != chipKind && (chipKind == ChipKind.NONE || time - chipSince >= CHIP_HOLD_S)) {
            chipKind = wanted
            chipSince = time
        }
        return when (chipKind) {
            ChipKind.NONE -> null
            ChipKind.FULL -> "태엽 가득"
            ChipKind.GLINT -> "보석 반짝!"
            ChipKind.WINDER -> "와인더 충전 중"
            ChipKind.CROWN -> "크라운 감는 중"
            ChipKind.CHRONO -> "크로노 ${chronoElapsedMs / 60_000}분"
            ChipKind.RESERVE -> "리저브 ${reserveH.roundToInt()}시간"
        }?.take(12)
    }

    companion object {
        const val BASE_RPM = 6.0
        const val RPM_PER_KMH = 0.9
        const val MAX_RPM = 110.0
        const val CALM_RPM = 30.0
        const val ROTOR_WIND_H = 0.0035
        const val CROWN_WIND_H = 0.02
        const val WINDER_H_PER_S = 0.004
        const val MIN_RESERVE_H = 6f
        const val DEFAULT_FAN_MAX = 8
        const val BLINK_HZ = 1.5
        const val CHIP_HOLD_S = 4.0
        const val DEFROST_S = 2.5
        const val MAX_FRAME_DT = 0.1f

        private const val SUBSTEP = 1.0 / 120.0
        private const val K_DRIVE = 4.0
        private const val K_REST = 2.2
        private const val GRAVITY = 2.4
        private const val ACCEL_COUPLING = 0.35
        private const val DAMPING = 0.25
        private const val HARSH_KICK = 2.0
        private const val SHOCK_S = 0.035

        /** Crown speed (rev/s) from the fan: 0.4 + 1.6 × level/max; 0 when the fan is off or unknown. */
        fun crownTargetRps(c: CarSignals): Double {
            val max = c.fanMax ?: DEFAULT_FAN_MAX
            if (c.maxAcOn == true) return 2.0
            val f = c.fanLevel ?: return 0.0
            if (f <= 0) return 0.0
            return 0.4 + 1.6 * (f.toDouble() / max).coerceIn(0.0, 1.0)
        }

        /** Rotor target speed (rpm) for a speed, before direction and calm cap. */
        fun rotorTargetRpm(kmh: Float): Double = if (kmh < 1f) 0.0 else min(MAX_RPM, BASE_RPM + RPM_PER_KMH * kmh)

        /** The reserve after the app was closed for a while (never below the 6 h floor). */
        fun reserveAfter(savedH: Float, savedAtMs: Long, nowMs: Long): Float {
            val elapsedH = ((nowMs - savedAtMs).coerceAtLeast(0L)) / 3_600_000f
            return (savedH - elapsedH).coerceIn(MIN_RESERVE_H, CalibreGeometry.POWER_RESERVE_H)
        }

        fun warmTarget(c: CarSignals): Float = c.setTempC?.let { ((it - 17f) / 10f).coerceIn(0f, 1f) } ?: 0.5f

        fun isNight(c: CarSignals, hour: Float): Boolean = c.headlightsOn == true || hour < 6.5f || hour >= 18.5f

        private fun approach(v: Float, target: Float, step: Float): Float =
            if (v < target) minOf(target, v + step) else maxOf(target, v - step)

        private fun wrap(a: Double): Double { val t = a % (2 * PI); return if (t < 0) t + 2 * PI else t }
    }
}
