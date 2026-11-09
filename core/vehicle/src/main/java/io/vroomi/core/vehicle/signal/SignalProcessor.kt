package io.vroomi.core.vehicle.signal

import kotlin.math.max
import kotlin.math.min

data class ProcessedSpeed(val kmh: Float, val accelMps2: Float)

/**
 * Speed smoothing + acceleration estimate (master prompt §3.3).
 *
 * - EMA (alpha 0.3) over raw km/h, clamped 0..200, values under 1.5 km/h become 0.
 * - acceleration = d(speed m/s)/dt over a ~0.5 s window of filtered speed, then low-pass filtered.
 *
 * The Vehicle SDK v2.3.2 has no speed / accelerator-pedal property, so "how hard the driver presses"
 * is inferred from this derivative (plus the brake pedal status, which is read directly).
 */
class SignalProcessor {
    private var ema: Float? = null
    private val history = ArrayDeque<Pair<Long, Float>>() // (ms, filtered m/s)
    private var accelLpf = 0f

    fun reset() {
        ema = null
        history.clear()
        accelLpf = 0f
    }

    fun onSpeedSample(tsMs: Long, rawKmh: Float): ProcessedSpeed {
        val clamped = min(max(rawKmh, 0f), Thresholds.SPEED_MAX_KMH)
        val prev = ema
        val filtered = if (prev == null) clamped
        else prev + Thresholds.SPEED_EMA_ALPHA * (clamped - prev)
        ema = filtered
        val outKmh = if (filtered < Thresholds.SPEED_ZERO_KMH) 0f else filtered

        val mps = filtered / 3.6f
        history.addLast(tsMs to mps)
        while (history.size > 2 && tsMs - history.first().first > Thresholds.ACCEL_WINDOW_MS) {
            history.removeFirst()
        }
        val first = history.first()
        val dtS = (tsMs - first.first) / 1000f
        val rawAccel = if (dtS >= 0.05f) (mps - first.second) / dtS else 0f
        accelLpf += Thresholds.ACCEL_LPF_ALPHA * (rawAccel - accelLpf)
        return ProcessedSpeed(outKmh, accelLpf)
    }
}
