package io.vroomi.core.vehicle.signal

/**
 * Every tunable number in one place (master prompt §3.3, §4, §8).
 * Tune on the Pleos Connect Emulator; unit tests pin the behaviour around these values.
 */
object Thresholds {
    // Sampling and speed filter
    const val SAMPLE_HZ = 10
    const val SPEED_EMA_ALPHA = 0.3f
    const val SPEED_MAX_KMH = 200f
    const val SPEED_ZERO_KMH = 1.5f

    // Acceleration (m/s^2)
    const val ACCEL_WINDOW_MS = 500L
    const val ACCEL_LPF_ALPHA = 0.5f
    const val BURST_MPS2 = 2.0f
    const val BURST_MIN_MS = 400L
    const val BURST_RESET_MPS2 = 1.0f
    const val HARSH_ACCEL_MPS2 = 3.0f
    const val HARSH_ACCEL_RESET_MPS2 = 2.0f
    const val HARSH_BRAKE_MPS2 = -3.5f
    const val HARSH_BRAKE_WITH_PEDAL_MPS2 = -3.0f
    const val HARSH_BRAKE_RESET_MPS2 = -2.0f

    // Gait bands (km/h) with hysteresis and dwell
    const val WALK_MIN_KMH = 1f
    const val JOG_MIN_KMH = 20f
    const val RUN_MIN_KMH = 60f
    const val SPRINT_MIN_KMH = 100f
    /** Above this Vroomi never looks more excited, so speeding is never rewarded. */
    const val SPRINT_CAP_KMH = 110f
    const val GAIT_HYSTERESIS_KMH = 3f
    const val MIN_DWELL_MS = 600L

    // Moving / parked mode
    const val MOVING_ENTER_KMH = 3f
    const val MOVING_EXIT_KMH = 1f
    const val MOVING_EXIT_HOLD_MS = 3_000L

    // Idle behaviour
    const val LONG_STOP_STRETCH_MS = 60_000L
    const val NAP_AFTER_PARKED_MS = 300_000L

    // Climate moods (set temperature, Celsius)
    const val COLD_SETPOINT_C = 21f
    const val WARM_SETPOINT_C = 26f

    // Navigation / energy
    const val NAVI_NEAR_M = 1_000
    const val LOW_BATTERY_PCT = 15

    // Smooth-driving score (§8)
    const val OVERSPEED_KMH = 110f
    const val SCORE_HARSH_ACCEL_PENALTY = 4
    const val SCORE_HARSH_BRAKE_PENALTY = 6
    const val SCORE_OVERSPEED_PER_MIN_PENALTY = 2
    const val CARE_SCORE_MIN = 70

    // Journey game layer (stars / scenery / km signs)
    /** One star per this many metres driven without a harsh event (speed above OVERSPEED_KMH does not count). */
    const val STAR_EVERY_M = 250f
    /** Stars in a row that make a combo (Vroomi does a little hop). */
    const val STAR_COMBO = 5
    /** The scenery changes every this many metres. */
    const val BIOME_EVERY_M = 800f

    // Trip detection
    const val TRIP_START_KMH = 5f
    const val TRIP_END_STOP_MS = 60_000L
    const val TRIP_MIN_DISTANCE_KM = 0.05f

    // Voice
    const val VOICE_GLOBAL_COOLDOWN_MOVING_MS = 120_000L
    const val VOICE_GLOBAL_COOLDOWN_PARKED_MS = 15_000L
    const val VOICE_EVENT_COOLDOWN_MS = 300_000L

    // Location-derived speed
    const val LOCATION_MAX_GAP_MS = 5_000L
    const val LOCATION_MIN_GAP_MS = 200L
    const val LOCATION_MAX_PLAUSIBLE_KMH = 250f
}
