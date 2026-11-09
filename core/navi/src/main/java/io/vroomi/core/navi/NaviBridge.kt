package io.vroomi.core.navi

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import io.vroomi.core.vehicle.model.NaviState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos NaviHelper 3.0.5 -> NaviState (guidance active, remaining distance, arrival).
 * API verified in NaviHelper-3.0.5-sources.jar:
 *   NaviHelper(context).initialize(onConnected, onFailed), addListener(listener), removeListener, release()
 *   NaviHelperEventListener (all callbacks have default bodies):
 *     onNavigationStatus(NavigationStatus: INACTIVE / INITIALIZING / FREE_DRIVE / ROUTE_DRIVE)
 *     onDrivingInfo(DrivingInfo(destination: DrivingState(distance: Int, duration: Int, ...)))
 *     onDestinationArrived(DestinationArrivedInfo)
 *     onRouteCancelled(), onRoutingCompleted(), onError(NaviErrorCode)
 * Vroomi only listens; it never starts, changes or cancels a route.
 * TODO(verify): whether passive events require pleos.car.permission.NAVI_ROUTE on the target image.
 */
class NaviBridge(private val context: Context) {
    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state.asStateFlow()

    private var helper: NaviHelper? = null

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            _state.update { it.copy(guidanceActive = status == NavigationStatus.ROUTE_DRIVE) }
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            _state.update { it.copy(remainingM = drivingInfo.destination.distance) }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _state.update {
                it.copy(guidanceActive = false, remainingM = 0, arrivedAtMs = System.currentTimeMillis())
            }
        }

        override fun onRouteCancelled() {
            _state.update { it.copy(guidanceActive = false, remainingM = null) }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    fun start() {
        if (helper != null) return
        try {
            val h = NaviHelper(context)
            h.addListener(listener)
            h.initialize(
                onConnected = {
                    Log.i(TAG, "NaviHelper connected")
                    _state.update { it.copy(guidanceActive = it.guidanceActive ?: false) }
                },
                onFailed = { t -> Log.i(TAG, "NaviHelper unavailable: ${t.message}") },
            )
            helper = h
        } catch (t: Throwable) {
            Log.i(TAG, "NaviHelper unavailable: ${t.message}")
        }
    }

    fun stop() {
        helper?.let { h ->
            runCatching { h.removeListener(listener) }
            runCatching { h.release() }
        }
        helper = null
    }

    private companion object { const val TAG = "VroomiNavi" }
}
