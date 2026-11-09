package io.vroomi.character.engine

/** The user's choice in the Cutaway theme. AUTO detects an EV from the battery signals. */
enum class EnginePowertrain(val label: String, val intro: String) {
    AUTO("자동", "배터리 신호로 골라요"),
    ICE("엔진", "직렬 4기통 터보 VE-4T"),
    EV("전기 모터", "8극 영구자석 모터 VE-M1"),
}

/** What is actually drawn. */
enum class Powertrain { ICE, EV }

/** SIDE: the whole powertrain (the only view while driving). FRONT: a large slice through cylinder 1 or the motor. */
enum class EngineView { SIDE, FRONT }

/** Four-stroke cycle phases of one cylinder. */
enum class Stroke { POWER, EXHAUST, INTAKE, COMPRESSION }

/**
 * AUTO detection: EV when evBatteryPct or evCharging was known (non-null) at any time in the last
 * 10 s. The answer is fixed for a whole journey: it may only change while no journey is running.
 */
class PowertrainDetector {
    private var lastEvSignalMs = Long.MIN_VALUE
    private var locked: Powertrain? = null
    var current: Powertrain = Powertrain.ICE; private set

    fun update(evBatteryPct: Int?, evCharging: Boolean?, journeyActive: Boolean, nowMs: Long): Powertrain {
        if (evBatteryPct != null || evCharging != null) lastEvSignalMs = nowMs
        val seen = lastEvSignalMs != Long.MIN_VALUE && nowMs - lastEvSignalMs <= WINDOW_MS
        val detected = if (seen) Powertrain.EV else Powertrain.ICE
        if (journeyActive) {
            if (locked == null) locked = detected
            current = locked!!
        } else {
            locked = null
            current = detected
        }
        return current
    }

    companion object { const val WINDOW_MS = 10_000L }
}
