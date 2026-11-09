package io.vroomi.core.vehicle.model

/** Every vehicle signal Vroomi may use. Behaviors declare which ones they need. */
enum class Capability {
    SPEED, BRAKE_PEDAL, GEAR,
    HVAC_FAN, HVAC_TEMP, HVAC_AC, HVAC_MAX_AC, HVAC_DEFROST, HVAC_MAX_DEFROST,
    HVAC_AIR_CLEANER, HVAC_RECIRC, HVAC_AUTO, SEAT_HEAT, SEAT_VENT,
    TURN_SIGNAL, WIPER, HEADLIGHTS, DOOR,
    EV_BATTERY, EV_CHARGE_PORT,
    DRIVING_MODE, CRUISE, ODOMETER, SAFETY_WARNING, NAVI,
    /** Writing HVAC values (needs CONTROL_CAR_CLIMATE + build flag). */
    HVAC_CONTROL,
}

data class CapabilityMap(
    val available: Set<Capability> = emptySet(),
    val unavailable: Set<Capability> = emptySet(),
) {
    fun has(c: Capability): Boolean = c in available
    fun hasAll(vararg cs: Capability): Boolean = cs.all { it in available }

    fun with(c: Capability, ok: Boolean): CapabilityMap =
        if (ok) copy(available = available + c, unavailable = unavailable - c)
        else copy(available = available - c, unavailable = unavailable + c)

    companion object {
        val ALL: CapabilityMap = CapabilityMap(available = Capability.entries.toSet())
    }
}
