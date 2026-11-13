package io.vroomi.core.vehicle.model

enum class TurnSignal { NONE, LEFT, RIGHT, HAZARD }

enum class GearState { UNKNOWN, PARK, REVERSE, NEUTRAL, DRIVE }

enum class DriveModeState { UNKNOWN, NORMAL, ECO, SPORT }

/** Where the current speed value comes from (see docs/permissions.md, §3.2 of the master prompt). */
enum class SpeedSourceKind { NONE, AAOS_PROPERTY, FUSED_LOCATION, SIMULATED }

/**
 * One immutable picture of everything Vroomi can sense about the car.
 * Every field is nullable (or UNKNOWN) so that a missing capability is explicit
 * and never confused with "off" / "zero".
 */
data class VehicleSnapshot(
    val timestampMs: Long = 0L,
    // Motion
    val speedKmh: Float? = null,
    val speedSource: SpeedSourceKind = SpeedSourceKind.NONE,
    val accelMps2: Float? = null,
    val brakePressed: Boolean? = null,
    val gear: GearState = GearState.UNKNOWN,
    // Climate (HVAC)
    val fanLevel: Int? = null,
    val fanLevelRange: IntRange? = null,
    val setTempC: Float? = null,
    val cabinTempC: Float? = null,
    val acOn: Boolean? = null,
    val maxAcOn: Boolean? = null,
    val defrostOn: Boolean? = null,
    val maxDefrostOn: Boolean? = null,
    val airCleanerOn: Boolean? = null,
    val recircOn: Boolean? = null,
    val hvacAutoOn: Boolean? = null,
    val seatHeatLevel: Int? = null,
    val seatVentLevel: Int? = null,
    // Body
    val turnSignal: TurnSignal? = null,
    val wiperActive: Boolean? = null,
    val headlightsOn: Boolean? = null,
    val doorOpen: Boolean? = null,
    // Energy
    val evCharging: Boolean? = null,
    val evBatteryPct: Int? = null,
    // Driving mode
    val drivingMode: DriveModeState = DriveModeState.UNKNOWN,
    val cruiseOn: Boolean? = null,
    // Navigation (filled from NaviHelper by core:navi)
    val naviGuidanceActive: Boolean? = null,
    val naviRemainingM: Int? = null,
    val naviArrivedAtMs: Long? = null,
    // Safety
    val safetyWarningActive: Boolean? = null,
    // Media: another app is playing music (from Android's playback callback, no permission; null = unknown)
    val musicPlaying: Boolean? = null,
    // Odometer (total distance driven, km)
    val odometerKm: Float? = null,
) {
    val isStationary: Boolean get() = (speedKmh ?: 0f) < 1f
}

/** Navigation facts merged into the snapshot. */
data class NaviState(
    val guidanceActive: Boolean? = null,
    val remainingM: Int? = null,
    val arrivedAtMs: Long? = null,
)
