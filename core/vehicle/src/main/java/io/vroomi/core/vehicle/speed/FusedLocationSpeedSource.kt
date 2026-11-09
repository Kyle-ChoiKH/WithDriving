package io.vroomi.core.vehicle.speed

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import io.vroomi.core.vehicle.model.SpeedSourceKind
import io.vroomi.core.vehicle.route.LiveRoute

/**
 * Speed derived from Pleos FusedLocation SDK 1.1.3 fixes.
 * API verified in FusedLocation-1.1.3-sources.jar:
 *   FusedLocation(context).isSupported(), initialize(), registerFusedLocationCallback(listener),
 *   unregisterFusedLocationCallback(listener), release();
 *   FusedLocationListener.onChanged(LocationData?) / onError(Exception)
 *   permission: pleos.car.permission.FUSED_LOCATION
 * Location data never leaves the device and is not stored.
 */
class FusedLocationSpeedSource(private val context: Context) : SpeedSource {
    override val kind = SpeedSourceKind.FUSED_LOCATION

    private var fused: FusedLocation? = null
    private var listener: FusedLocationListener? = null
    private val estimator = LocationSpeedEstimator()

    @SuppressLint("MissingPermission") // checked right below with checkSelfPermission
    override fun start(onSpeed: (tsMs: Long, kmh: Float) -> Unit): Boolean {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "FUSED_LOCATION not granted, skipping location speed")
            return false
        }
        val f = FusedLocation(context)
        if (!f.isSupported()) {
            Log.i(TAG, "FusedLocation not supported on this device")
            return false
        }
        f.initialize()
        val l = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                location ?: return // documented: null when no location is available
                val kmh = estimator.onFix(location.latitude, location.longitude, location.timestampMillis)
                // The same fix feeds the on-device route sketcher (metres from the journey start, memory only).
                LiveRoute.onFix(location.latitude, location.longitude)
                if (kmh != null) onSpeed(System.currentTimeMillis(), kmh)
            }

            override fun onError(exception: Exception) {
                Log.w(TAG, "FusedLocation error: ${exception.message}")
            }
        }
        f.registerFusedLocationCallback(l)
        fused = f
        listener = l
        return true
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        val f = fused ?: return
        listener?.let { runCatching { f.unregisterFusedLocationCallback(it) } }
        runCatching { f.release() }
        fused = null
        listener = null
        estimator.reset()
        LiveRoute.clear()
    }

    private companion object {
        const val TAG = "VroomiLocSpeed"
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}
