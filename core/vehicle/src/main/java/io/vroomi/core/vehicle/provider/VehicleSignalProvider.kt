package io.vroomi.core.vehicle.provider

import io.vroomi.core.vehicle.model.CapabilityMap
import io.vroomi.core.vehicle.model.VehicleSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * The only door between Vroomi and the car. Every SDK hides behind this interface so the app keeps
 * working (through the simulator) when a signal, a permission or the whole Pleos service is missing.
 *
 * [snapshot] carries RAW speed; VehicleHub filters it and adds acceleration.
 */
interface VehicleSignalProvider {
    val snapshot: StateFlow<VehicleSnapshot>
    val capabilities: StateFlow<CapabilityMap>

    suspend fun start()
    suspend fun stop()

    suspend fun setFanLevel(level: Int): Result<Unit>
    suspend fun setTemperature(celsius: Float): Result<Unit>
}

class ControlNotAllowedException(message: String) : Exception(message)
