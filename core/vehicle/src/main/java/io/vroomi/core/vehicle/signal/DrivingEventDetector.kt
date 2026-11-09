package io.vroomi.core.vehicle.signal

enum class DrivingEvent { BURST, HARSH_ACCEL, HARSH_BRAKE }

/**
 * Turns the acceleration stream into discrete maneuvers. Each maneuver fires once and re-arms only
 * after acceleration returns toward zero, so one long brake never counts twice.
 * Shared by the character director and the trip recorder so their counts always agree.
 */
class DrivingEventDetector {
    private var burstSinceMs: Long? = null
    private var burstArmed = true
    private var harshAccelArmed = true
    private var harshBrakeArmed = true

    fun reset() {
        burstSinceMs = null
        burstArmed = true
        harshAccelArmed = true
        harshBrakeArmed = true
    }

    fun update(tsMs: Long, accelMps2: Float, brakePressed: Boolean?): List<DrivingEvent> {
        val out = ArrayList<DrivingEvent>(2)

        // Harsh acceleration
        if (harshAccelArmed && accelMps2 > Thresholds.HARSH_ACCEL_MPS2) {
            out += DrivingEvent.HARSH_ACCEL
            harshAccelArmed = false
        } else if (!harshAccelArmed && accelMps2 < Thresholds.HARSH_ACCEL_RESET_MPS2) {
            harshAccelArmed = true
        }

        // Smooth-but-strong burst (sustained, not harsh)
        if (accelMps2 > Thresholds.BURST_MPS2) {
            val since = burstSinceMs ?: tsMs.also { burstSinceMs = it }
            if (burstArmed && tsMs - since >= Thresholds.BURST_MIN_MS && harshAccelArmed) {
                out += DrivingEvent.BURST
                burstArmed = false
            }
        } else {
            burstSinceMs = null
            if (accelMps2 < Thresholds.BURST_RESET_MPS2) burstArmed = true
        }

        // Harsh braking
        val harsh = accelMps2 < Thresholds.HARSH_BRAKE_MPS2 ||
            (brakePressed == true && accelMps2 < Thresholds.HARSH_BRAKE_WITH_PEDAL_MPS2)
        if (harshBrakeArmed && harsh) {
            out += DrivingEvent.HARSH_BRAKE
            harshBrakeArmed = false
        } else if (!harshBrakeArmed && accelMps2 > Thresholds.HARSH_BRAKE_RESET_MPS2) {
            harshBrakeArmed = true
        }
        return out
    }
}
