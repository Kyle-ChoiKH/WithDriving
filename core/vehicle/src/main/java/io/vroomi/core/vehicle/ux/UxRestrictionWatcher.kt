package io.vroomi.core.vehicle.ux

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Follows AAOS CarUxRestrictionsManager. When the platform says distraction optimization is required
 * (the car is moving), Vroomi switches to its ambient "moving" mode regardless of the speed source.
 * On non-automotive builds / missing car service it simply reports "not restricted".
 */
class UxRestrictionWatcher(private val context: Context) {
    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        try {
            val c = Car.createCar(context) ?: return
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            car = c; manager = m
            apply(m.currentCarUxRestrictions)
            m.registerListener { r -> apply(r) }
        } catch (t: Throwable) {
            Log.i(TAG, "UX restrictions unavailable: ${t.message}")
        }
    }

    private fun apply(r: CarUxRestrictions?) {
        _restricted.value = r?.isRequiresDistractionOptimization ?: false
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        car = null; manager = null
    }

    private companion object { const val TAG = "VroomiUx" }
}
