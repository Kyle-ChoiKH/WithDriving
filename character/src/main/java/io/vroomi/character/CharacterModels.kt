package io.vroomi.character

import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot

/** Engine/locomotion intensity tier (§4 rows 1–5). In the kart it sets wheel spin, bounce and speed effects. */
enum class Gait { IDLE, WALK, JOG, RUN, SPRINT }

/** One-shot or persistent body actions, layered above locomotion. */
enum class Action {
    NONE, BURST, SKID, TUMBLE, POINT_LEFT, POINT_RIGHT, HAZARD_WAVE, WAVE_HELLO, WAVE_BYE, CELEBRATE,
    DEFROST_WIPE, STRETCH, NAP, LOOK_AHEAD, THUMBS_UP, EAT_ENERGY, BLOW, EXHALE,
    /** Five smooth stars in a row: arms up, the kart does a little hop. */
    SPIN,
}

enum class Mood { NEUTRAL, HAPPY, COLD, WARM, DIZZY, SLEEPY, HUNGRY, RELAXED }

enum class SizeMode { FULL, AMBIENT, CORNER }

enum class Accessory(val unlockLevel: Int, val wardrobe: Boolean) {
    SCARF(1, true), GOGGLES(2, true), BEANIE(3, true), RAINCOAT(4, true), CABLE_LASSO(5, true),
    // Contextual props (never chosen manually)
    MAP_HAT(0, false), LEAF(0, false), LANTERN(0, false), SUNGLASSES(0, false), BLANKET(0, false),
}

/** Priority: 1 is the most important (interrupts everything), 5 the least. */
enum class Priority(val level: Int) { P1(1), P2(2), P3(3), P4(4), P5(5) }

data class Overlays(
    val windLevel: Float = 0f,     // 0..1, from fan level / range
    val rain: Float = 0f,          // 0..1
    val snow: Boolean = false,
    val steam: Boolean = false,
    val sparkle: Boolean = false,
    val recircSwirl: Boolean = false,
    val night: Boolean = false,
    val speedLines: Boolean = false,
    val footGlow: Float = 0f,      // 0..1
)

enum class Blinker { NONE, LEFT, RIGHT, HAZARD }

enum class KartStyle { NORMAL, ECO, SPORT }

/**
 * Vroomi's own little electric kart mirrors the real car, so the driver and Vroomi drive "together":
 * brake lights, blinkers, wipers, headlights, nose dip / lift from acceleration, reverse, charging.
 */
data class KartState(
    /** Vroomi is sitting in the kart (moving mode). When false the kart is parked next to Vroomi. */
    val driving: Boolean = false,
    val brake: Boolean = false,
    val blinker: Blinker = Blinker.NONE,
    val headlights: Boolean = false,
    val wipers: Boolean = false,
    val cruise: Boolean = false,
    val style: KartStyle = KartStyle.NORMAL,
    /** Filtered longitudinal acceleration, m/s² (+ speeding up, − slowing down). */
    val accel: Float = 0f,
    val reverse: Boolean = false,
    /** Windshield defroster is on (Vroomi wipes the fogged glass). */
    val defrost: Boolean = false,
    val charging: Boolean = false,
    val batteryPct: Int? = null,
)

/** The scenery Vroomi's road passes through. It changes with distance driven, never with location. */
enum class Biome(val label: String) { CITY("도시"), PARK("가로수길"), FIELD("들판"), SEASIDE("바닷가"), MOUNTAIN("산길") }

/** Light game layer of one trip: smooth distance turns into stars, km signs and changing scenery. */
data class Journey(
    val tripKm: Float = 0f,
    /** Stars collected this trip (one per 250 m without harsh braking or acceleration). */
    val stars: Int = 0,
    /** Increments on every new star, so the scene can spawn exactly one pickup per star. */
    val starSeq: Int = 0,
    /** Stars in a row since the last harsh event. */
    val combo: Int = 0,
    /** Increments every 5 stars in a row. */
    val comboSeq: Int = 0,
    val bestCombo: Int = 0,
    /** Whole kilometres driven this trip (a roadside km sign passes for each). */
    val milestoneKm: Int = 0,
    val biome: Biome = Biome.CITY,
    val biomesSeen: Int = 1,
    /** Remaining navigation distance while guidance is active. */
    val destinationM: Int? = null,
    /** A trip is in progress (from the first metres until Park / a long stop). */
    val active: Boolean = false,
    /** Increments every time a new trip starts (the Calibre chronograph starts on it). */
    val tripSeq: Int = 0,
)

/**
 * Raw car signals for themes that show the car "as is" (the Calibre watch, the Cutaway engine). Null keeps meaning
 * "unknown": it is never turned into off or zero here.
 */
data class CarSignals(
    val speedKmh: Float? = null,
    val accelMps2: Float? = null,
    val gear: GearState = GearState.UNKNOWN,
    val moving: Boolean = false,
    val fanLevel: Int? = null,
    /** Top of fanLevelRange, null when the range is unknown. */
    val fanMax: Int? = null,
    val acOn: Boolean? = null,
    val maxAcOn: Boolean? = null,
    val setTempC: Float? = null,
    val hvacAutoOn: Boolean? = null,
    /** Windshield defrost or max defrost (null only when both are unknown). */
    val defrostOn: Boolean? = null,
    val wiperActive: Boolean? = null,
    val headlightsOn: Boolean? = null,
    val turnSignal: TurnSignal? = null,
    val evCharging: Boolean? = null,
    val evBatteryPct: Int? = null,
    val drivingMode: DriveModeState = DriveModeState.UNKNOWN,
    val safetyWarningActive: Boolean? = null,
    val brakePressed: Boolean? = null,
    val cruiseOn: Boolean? = null,
    val doorOpen: Boolean? = null,
    /** Increments on every harsh-braking event. */
    val harshBrakeSeq: Int = 0,
    /** Increments on every navigation arrival. */
    val arrivalSeq: Int = 0,
) {
    companion object {
        fun from(s: VehicleSnapshot, moving: Boolean, harshBrakeSeq: Int = 0, arrivalSeq: Int = 0) = CarSignals(
            speedKmh = s.speedKmh, accelMps2 = s.accelMps2, gear = s.gear, moving = moving,
            fanLevel = s.fanLevel, fanMax = s.fanLevelRange?.last?.takeIf { it > 0 },
            acOn = s.acOn, maxAcOn = s.maxAcOn, setTempC = s.setTempC, hvacAutoOn = s.hvacAutoOn,
            defrostOn = when {
                s.defrostOn == true || s.maxDefrostOn == true -> true
                s.defrostOn == null && s.maxDefrostOn == null -> null
                else -> false
            },
            wiperActive = s.wiperActive, headlightsOn = s.headlightsOn, turnSignal = s.turnSignal,
            evCharging = s.evCharging, evBatteryPct = s.evBatteryPct, drivingMode = s.drivingMode,
            safetyWarningActive = s.safetyWarningActive, brakePressed = s.brakePressed, cruiseOn = s.cruiseOn, doorOpen = s.doorOpen,
            harshBrakeSeq = harshBrakeSeq, arrivalSeq = arrivalSeq,
        )
    }
}

data class CharacterTarget(
    val gait: Gait = Gait.IDLE,
    /** Speed driving the animation, already capped at SPRINT_CAP_KMH. */
    val gaitSpeedKmh: Float = 0f,
    val action: Action = Action.NONE,
    val actionStartedMs: Long = 0L,
    val actionPriority: Priority = Priority.P5,
    val mood: Mood = Mood.NEUTRAL,
    val overlays: Overlays = Overlays(),
    val accessories: Set<Accessory> = emptySet(),
    val sizeMode: SizeMode = SizeMode.FULL,
    /** P1 events: Vroomi freezes in place. */
    val still: Boolean = false,
    /** At most 12 characters, shown only while moving. */
    val statusChip: String? = null,
    val kart: KartState = KartState(),
    val journey: Journey = Journey(),
    /** The same car, unfiltered (used by the Calibre theme). */
    val car: CarSignals = CarSignals(),
)

/** Things worth (maybe) saying out loud. The VoiceDirector decides whether it actually speaks. */
enum class VroomiEvent(val lineKey: String) {
    START("start"), SMOOTH_BURST("smooth"), HARSH_BRAKE("harsh_brake"), FAN_UP("fan_up"),
    TEMP_COLD("temp_cold"), TEMP_WARM("temp_warm"), RAIN("rain"), ARRIVAL("arrival"),
    LONG_STOP("rest"), LOW_BATTERY("low_battery"), CHARGING("charging"),
    DOOR_HELLO("hello"), DOOR_GOODBYE("goodbye"),
    STAR_COMBO("combo"), MILESTONE("milestone"),
    BIOME_CITY("biome_city"), BIOME_PARK("biome_park"), BIOME_FIELD("biome_field"),
    BIOME_SEASIDE("biome_seaside"), BIOME_MOUNTAIN("biome_mountain");

    companion object {
        fun forBiome(b: Biome): VroomiEvent = when (b) {
            Biome.CITY -> BIOME_CITY; Biome.PARK -> BIOME_PARK; Biome.FIELD -> BIOME_FIELD
            Biome.SEASIDE -> BIOME_SEASIDE; Biome.MOUNTAIN -> BIOME_MOUNTAIN
        }
    }
}
