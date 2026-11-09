package io.vroomi.character

import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.signal.DrivingEvent
import io.vroomi.core.vehicle.signal.Thresholds
import kotlin.math.min

/**
 * Applies the §4 signal -> behaviour table with priorities, hysteresis and dwell time.
 * Pure Kotlin: the caller passes the time, so it is fully unit-testable.
 */
class CharacterDirector {

    private var gait = Gait.IDLE
    private var gaitChangedAt = Long.MIN_VALUE / 2

    private data class Timed(val action: Action, val priority: Priority, val startedAt: Long, val endsAt: Long)
    private var timed: Timed? = null
    private var dizzyUntil = 0L
    private var happyUntil = 0L

    private var prev: VehicleSnapshot? = null
    private var stoppedSince: Long? = null
    private var stretchedThisStop = false
    private var lastTripEndedAt: Long? = null
    private var wasMovingRecently = false
    private var lastArrivalStamp: Long? = null
    private var started = false
    private var harshSeq = 0
    private var arrivalSeq = 0

    private val voice = ArrayList<VroomiEvent>()

    /** Stars, km signs and scenery of the current trip. */
    private val journey = JourneyTracker()
    private var starChipUntil = 0L
    private var comboChipUntil = 0L
    private var milestoneChipUntil = 0L

    /** Voice-worthy events since the last call. */
    fun drainVoiceEvents(): List<VroomiEvent> = ArrayList(voice).also { voice.clear() }

    fun onDrivingEvent(e: DrivingEvent, nowMs: Long) {
        when (e) {
            DrivingEvent.BURST -> {
                request(Action.BURST, Priority.P3, nowMs, 700)
                happyUntil = nowMs + 4_000
                voice += VroomiEvent.SMOOTH_BURST
            }
            DrivingEvent.HARSH_BRAKE -> {
                harshSeq++
                journey.onHarsh()
                request(Action.TUMBLE, Priority.P2, nowMs, 2_000)
                dizzyUntil = nowMs + 2_000
                voice += VroomiEvent.HARSH_BRAKE
            }
            DrivingEvent.HARSH_ACCEL -> {
                journey.onHarsh()
                request(Action.BURST, Priority.P3, nowMs, 500)
            }
        }
    }

    /** Immediate (optimistic) reaction, e.g. to a voice command, before the car confirms the change. */
    fun react(a: Action, nowMs: Long, durationMs: Long = 2_000) = request(a, Priority.P3, nowMs, durationMs)

    private var persistentAction: Action? = null
    private var persistentSince = 0L

    private fun request(a: Action, p: Priority, now: Long, durationMs: Long) {
        val cur = timed
        if (cur == null || now >= cur.endsAt || p.level <= cur.priority.level) {
            timed = Timed(a, p, now, now + durationMs)
        }
    }

    fun update(
        s: VehicleSnapshot,
        moving: Boolean,
        userCorner: Boolean,
        wardrobe: Set<Accessory>,
        nowMs: Long,
    ): CharacterTarget {
        val p = prev
        if (!started) {
            started = true
            voice += VroomiEvent.START
        }
        val speed = s.speedKmh ?: 0f

        // ---- edge detection (row by row) -------------------------------------------------------
        if (p != null) {
            // 7: brake pedal pressed while rolling -> skid
            if (s.brakePressed == true && p.brakePressed != true && speed > Thresholds.MOVING_ENTER_KMH) {
                request(Action.SKID, Priority.P2, nowMs, 900)
            }
            // 9/10: fan up / down
            val f = s.fanLevel; val pf = p.fanLevel
            if (f != null && pf != null) {
                if (f > pf) {
                    request(Action.BLOW, Priority.P3, nowMs, 2_500)
                    voice += VroomiEvent.FAN_UP
                } else if (f < pf && f == 0) {
                    request(Action.EXHALE, Priority.P4, nowMs, 1_200)
                }
            }
            // 11/12: temperature crossing
            val t = s.setTempC; val pt = p.setTempC
            if (t != null && pt != null) {
                if (t <= Thresholds.COLD_SETPOINT_C && pt > Thresholds.COLD_SETPOINT_C) voice += VroomiEvent.TEMP_COLD
                if (t >= Thresholds.WARM_SETPOINT_C && pt < Thresholds.WARM_SETPOINT_C) voice += VroomiEvent.TEMP_WARM
            }
            // 11: Max A/C switched on -> big blow like a little fan
            if (s.maxAcOn == true && p.maxAcOn != true) {
                request(Action.BLOW, Priority.P3, nowMs, 2_500)
                voice += VroomiEvent.TEMP_COLD
            }
            // 13: defroster
            if ((s.defrostOn == true || s.maxDefrostOn == true) && p.defrostOn != true && p.maxDefrostOn != true) {
                request(Action.DEFROST_WIPE, Priority.P3, nowMs, 3_500)
            }
            // 16: HVAC auto
            if (s.hvacAutoOn == true && p.hvacAutoOn != true) request(Action.THUMBS_UP, Priority.P5, nowMs, 1_500)
            // 19: rain
            if (s.wiperActive == true && p.wiperActive != true) voice += VroomiEvent.RAIN
            // 21: door opened while parked
            if (s.doorOpen == true && p.doorOpen != true && !moving) {
                val bye = lastTripEndedAt?.let { nowMs - it < 10 * 60_000 } ?: false
                request(if (bye) Action.WAVE_BYE else Action.WAVE_HELLO, Priority.P3, nowMs, 2_500)
                voice += if (bye) VroomiEvent.DOOR_GOODBYE else VroomiEvent.DOOR_HELLO
            }
            // 22: charging started
            if (s.evCharging == true && p.evCharging != true) voice += VroomiEvent.CHARGING
            // 23: low battery crossing (only when parked or slow)
            val b = s.evBatteryPct; val pb = p.evBatteryPct
            if (b != null && pb != null && b <= Thresholds.LOW_BATTERY_PCT && pb > Thresholds.LOW_BATTERY_PCT && speed < 10f) {
                voice += VroomiEvent.LOW_BATTERY
            }
        }
        // 26: arrival
        val arrived = s.naviArrivedAtMs
        if (arrived != null && arrived != lastArrivalStamp) {
            lastArrivalStamp = arrived
            arrivalSeq++
            request(Action.CELEBRATE, Priority.P2, nowMs, 3_000)
            happyUntil = nowMs + 5_000
            voice += VroomiEvent.ARRIVAL
        }

        // ---- journey (stars / km signs / scenery) ---------------------------------------------
        val naviM = if (s.naviGuidanceActive == true) s.naviRemainingM else null
        val j = journey.update(speed, moving, s.gear, naviM, nowMs)
        for (e in journey.drainEvents()) when (e) {
            JourneyTracker.Event.STAR -> starChipUntil = nowMs + 3_000
            JourneyTracker.Event.COMBO -> {
                request(Action.SPIN, Priority.P4, nowMs, 1_600)
                happyUntil = maxOf(happyUntil, nowMs + 3_000)
                comboChipUntil = nowMs + 4_000
                voice += VroomiEvent.STAR_COMBO
            }
            JourneyTracker.Event.MILESTONE -> {
                milestoneChipUntil = nowMs + 4_000
                voice += VroomiEvent.MILESTONE
            }
            JourneyTracker.Event.BIOME -> voice += VroomiEvent.forBiome(j.biome)
        }

        // ---- trip / stop bookkeeping -----------------------------------------------------------
        // "Stopped" = not rolling (also at a red light in D). "Parked" = the app is in parked mode.
        val stationary = speed < 1f
        if (moving) wasMovingRecently = true
        if (!stationary) {
            stoppedSince = null
            stretchedThisStop = false
        } else if (stoppedSince == null) {
            stoppedSince = nowMs
        }
        val stoppedFor = stoppedSince?.let { nowMs - it } ?: 0L
        if (!moving && wasMovingRecently && (s.gear == GearState.PARK || stoppedFor > Thresholds.TRIP_END_STOP_MS)) {
            wasMovingRecently = false
            lastTripEndedAt = nowMs
        }
        if (stationary && !stretchedThisStop && stoppedFor >= Thresholds.LONG_STOP_STRETCH_MS && stoppedFor < Thresholds.NAP_AFTER_PARKED_MS) {
            stretchedThisStop = true
            request(Action.STRETCH, Priority.P4, nowMs, 2_500)
            voice += VroomiEvent.LONG_STOP
        }

        // ---- gait with hysteresis + dwell ------------------------------------------------------
        val capped = min(speed, Thresholds.SPRINT_CAP_KMH)
        val wanted = nextGait(gait, speed)
        if (wanted != gait && nowMs - gaitChangedAt >= Thresholds.MIN_DWELL_MS) {
            gait = wanted
            gaitChangedAt = nowMs
        }

        // ---- persistent actions (rows 17, 18, 22, 27, 1-nap) -----------------------------------
        val turn = s.turnSignal
        val safety = s.safetyWarningActive == true
        val hazard = turn == TurnSignal.HAZARD
        val naviNear = s.naviGuidanceActive == true && (s.naviRemainingM ?: Int.MAX_VALUE) < Thresholds.NAVI_NEAR_M
        val napping = !moving && stoppedFor >= Thresholds.NAP_AFTER_PARKED_MS

        val cur = timed?.takeIf { nowMs < it.endsAt }
        val persistent: Pair<Action, Priority>? = when {
            hazard -> Action.HAZARD_WAVE to Priority.P1
            turn == TurnSignal.LEFT -> Action.POINT_LEFT to Priority.P2
            turn == TurnSignal.RIGHT -> Action.POINT_RIGHT to Priority.P2
            s.evCharging == true && !moving -> Action.EAT_ENERGY to Priority.P3
            naviNear -> Action.LOOK_AHEAD to Priority.P4
            napping -> Action.NAP to Priority.P5
            else -> null
        }
        if (persistent?.first != persistentAction) {
            persistentAction = persistent?.first
            persistentSince = nowMs
        }
        val (action, actionPrio, actionStart) = when {
            persistent != null && (cur == null || persistent.second.level < cur.priority.level) ->
                Triple(persistent.first, persistent.second, persistentSince)
            cur != null -> Triple(cur.action, cur.priority, cur.startedAt)
            else -> Triple(Action.NONE, Priority.P5, nowMs)
        }

        // ---- mood --------------------------------------------------------------------------------
        val temp = s.setTempC
        val coldByTemp = temp != null && temp <= Thresholds.COLD_SETPOINT_C
        val warmByTemp = temp != null && temp >= Thresholds.WARM_SETPOINT_C
        val cold = coldByTemp || s.maxAcOn == true || (s.acOn == true && !warmByTemp)
        val warm = !coldByTemp && s.maxAcOn != true && warmByTemp
        val lowBattery = (s.evBatteryPct ?: 100) <= Thresholds.LOW_BATTERY_PCT && s.evCharging != true
        val mood = when {
            nowMs < dizzyUntil -> Mood.DIZZY
            lowBattery -> Mood.HUNGRY
            napping -> Mood.SLEEPY
            nowMs < happyUntil -> Mood.HAPPY
            cold -> Mood.COLD
            warm -> Mood.WARM
            s.hvacAutoOn == true -> Mood.RELAXED
            else -> Mood.NEUTRAL
        }

        // ---- overlays ------------------------------------------------------------------------------
        val range = s.fanLevelRange
        val fanMax = range?.last?.takeIf { it > 0 } ?: 8
        val wind = ((s.fanLevel ?: 0).toFloat() / fanMax).coerceIn(0f, 1f)
        val overlays = Overlays(
            windLevel = wind,
            rain = if (s.wiperActive == true) 0.7f else 0f,
            snow = cold,
            steam = warm,
            sparkle = s.airCleanerOn == true,
            recircSwirl = s.recircOn == true,
            night = s.headlightsOn == true,
            speedLines = capped >= Thresholds.RUN_MIN_KMH,
            footGlow = (capped / Thresholds.SPRINT_CAP_KMH).coerceIn(0f, 1f),
        )

        // ---- accessories ---------------------------------------------------------------------------
        val acc = HashSet<Accessory>(wardrobe)
        if (s.wiperActive == true) acc += Accessory.RAINCOAT
        if (warm) acc += Accessory.SCARF
        if (s.headlightsOn == true) acc += Accessory.LANTERN
        if (s.naviGuidanceActive == true) acc += Accessory.MAP_HAT
        when (s.drivingMode) {
            DriveModeState.ECO -> acc += Accessory.LEAF
            DriveModeState.SPORT -> acc += Accessory.GOGGLES
            else -> Unit
        }
        if (s.cruiseOn == true && moving) acc += Accessory.SUNGLASSES
        if ((s.seatHeatLevel ?: 0) > 0) acc += Accessory.BLANKET
        if (s.evCharging == true) acc += Accessory.CABLE_LASSO

        // ---- size / stillness / chip ---------------------------------------------------------------
        val size = when {
            safety || hazard || userCorner -> SizeMode.CORNER
            moving -> SizeMode.AMBIENT
            else -> SizeMode.FULL
        }
        val chip = if (!moving) null else when {
            safety -> null
            naviNear -> "곧 도착해요"
            mood == Mood.DIZZY -> "살살 가요~"
            nowMs < comboChipUntil -> "별 ${j.combo}개 연속!"
            nowMs < milestoneChipUntil -> "벌써 ${j.milestoneKm}km 왔어요"
            action == Action.BLOW || wind >= 0.5f -> "바람 솔솔~"
            s.wiperActive == true -> "비 조심해요"
            nowMs < starChipUntil -> "별 ${j.stars}개 모았어요"
            s.cruiseOn == true -> "크루즈 중"
            stationary -> "잠깐 쉬어 가요"
            else -> "부드러운 주행 중"
        }?.take(12)

        val kart = KartState(
            driving = moving,
            brake = s.brakePressed == true || ((s.accelMps2 ?: 0f) < -1.5f && speed > 2f),
            blinker = when (turn) {
                TurnSignal.LEFT -> Blinker.LEFT; TurnSignal.RIGHT -> Blinker.RIGHT
                TurnSignal.HAZARD -> Blinker.HAZARD; else -> Blinker.NONE
            },
            headlights = s.headlightsOn == true,
            wipers = s.wiperActive == true,
            cruise = s.cruiseOn == true && moving,
            style = when (s.drivingMode) {
                DriveModeState.ECO -> KartStyle.ECO; DriveModeState.SPORT -> KartStyle.SPORT; else -> KartStyle.NORMAL
            },
            accel = (s.accelMps2 ?: 0f).coerceIn(-6f, 6f),
            reverse = s.gear == GearState.REVERSE,
            defrost = s.defrostOn == true || s.maxDefrostOn == true,
            charging = s.evCharging == true,
            batteryPct = s.evBatteryPct,
        )

        prev = s
        return CharacterTarget(
            gait = gait,
            gaitSpeedKmh = if (gait == Gait.IDLE) 0f else capped,
            action = if (safety) Action.NONE else action,
            actionStartedMs = actionStart,
            actionPriority = actionPrio,
            mood = mood,
            overlays = overlays,
            accessories = acc,
            sizeMode = size,
            still = safety,
            statusChip = chip,
            kart = kart,
            journey = j,
            car = CarSignals.from(s, moving, harshSeq, arrivalSeq),
        )
    }

    companion object {
        private fun lower(g: Gait): Float = when (g) {
            Gait.IDLE -> 0f
            Gait.WALK -> Thresholds.WALK_MIN_KMH
            Gait.JOG -> Thresholds.JOG_MIN_KMH
            Gait.RUN -> Thresholds.RUN_MIN_KMH
            Gait.SPRINT -> Thresholds.SPRINT_MIN_KMH
        }

        private fun hysteresis(g: Gait): Float =
            if (g == Gait.WALK) 0.5f else Thresholds.GAIT_HYSTERESIS_KMH

        /** Moves at most as far as needed, using rising/falling thresholds lower±H. */
        fun nextGait(current: Gait, speed: Float): Gait {
            var g = current
            val all = Gait.entries
            while (g.ordinal < all.lastIndex) {
                val up = all[g.ordinal + 1]
                if (speed >= lower(up) + hysteresis(up)) g = up else break
            }
            while (g.ordinal > 0) {
                if (speed < lower(g) - hysteresis(g)) g = all[g.ordinal - 1] else break
            }
            return g
        }
    }
}
