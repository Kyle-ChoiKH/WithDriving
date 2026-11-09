package io.vroomi.core.vehicle.speed

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.vroomi.core.vehicle.BuildConfig
import io.vroomi.core.vehicle.model.SpeedSourceKind
import kotlin.math.abs

/**
 * Standard AAOS vehicle speed (VehiclePropertyIds.PERF_VEHICLE_SPEED, m/s).
 *
 * Disabled by default: it needs android.car.permission.CAR_SPEED, which is NOT part of the Pleos
 * permission set. Enable only after the organizers confirm it is allowed on Pleos App Market:
 *   gradle.properties  vroomi.allowAaosSpeed=true
 *   AndroidManifest    <uses-permission android:name="android.car.permission.CAR_SPEED"/>
 * See docs/questions-to-organizers.md.
 */
class AaosSpeedSource(private val context: Context) : SpeedSource {
    override val kind = SpeedSourceKind.AAOS_PROPERTY

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null

    override fun start(onSpeed: (tsMs: Long, kmh: Float) -> Unit): Boolean {
        if (!BuildConfig.ALLOW_AAOS_SPEED) return false
        if (context.checkSelfPermission(CAR_SPEED) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val c = Car.createCar(context) ?: return false
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return false
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val mps = (value.value as? Float) ?: return
                    onSpeed(System.currentTimeMillis(), abs(mps) * 3.6f)
                }

                override fun onErrorEvent(propId: Int, zone: Int) {
                    Log.w(TAG, "speed property error $propId/$zone")
                }
            }
            val ok = pm.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            if (ok) {
                car = c; manager = pm; callback = cb
            } else {
                c.disconnect()
            }
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "AAOS speed unavailable: ${t.message}")
            false
        }
    }

    override fun stop() {
        runCatching { callback?.let { manager?.unregisterCallback(it) } }
        runCatching { car?.disconnect() }
        car = null; manager = null; callback = null
    }

    private companion object {
        const val TAG = "VroomiAaosSpeed"
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
