package io.vroomi.app.runtime

import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.signal.Thresholds

/**
 * Moving (ambient UI) vs parked (full UI).
 * - Enter "moving" above 3 km/h, or whenever AAOS says distraction optimization is required.
 * - Stopped at a red light in D / R / N stays "moving": the driver is still driving, so no menus appear.
 * - Gear P means parked right away.
 * - If the gear is unknown, leave "moving" only after 3 s below 1 km/h.
 */
class MovingDetector {
    var moving = false
        private set
    private var belowSince: Long? = null

    fun update(speedKmh: Float?, gear: GearState, uxRestricted: Boolean, nowMs: Long): Boolean {
        val v = speedKmh ?: 0f
        if (uxRestricted || v >= Thresholds.MOVING_ENTER_KMH) {
            moving = true
            belowSince = null
            return true
        }
        if (!moving) return false
        when (gear) {
            GearState.PARK -> { moving = false; belowSince = null }
            GearState.DRIVE, GearState.REVERSE, GearState.NEUTRAL -> belowSince = null
            GearState.UNKNOWN -> {
                if (v < Thresholds.MOVING_EXIT_KMH) {
                    val since = belowSince ?: nowMs.also { belowSince = it }
                    if (nowMs - since >= Thresholds.MOVING_EXIT_HOLD_MS) {
                        moving = false
                        belowSince = null
                    }
                } else {
                    belowSince = null
                }
            }
        }
        return moving
    }
}
