package io.vroomi.character.plasma

import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * PLASMA: a high-voltage plasma globe on a dark lab bench (spec 5.11), driven by the car. Pure Kotlin.
 *
 * - Speed: the number of filaments (4 standing still .. 14 at 110 km/h and above) and how fast they wander.
 * - Blinker: the filaments gather on that side of the glass, as if a hand touched it; each blink leaves one soft
 *   bright point there (flash-guarded by the kit).
 * - Brake: the filaments shrink toward the electrode and dim.
 * - Acceleration: the valves' filaments glow (from Lv.3). Fan level: the scope's amplitude (from Lv.4).
 * - Distance: the counter tubes (from Lv.2); every kilometre the ladder's arc climbs once more (Lv.5).
 * - Star: one point of light rests on the glass for a while. Combo: the whole fan of filaments turns once, slowly.
 *
 * Nothing flashes: the filaments' brightness only breathes within ±15 % ([flicker] 0.85..1), there is no
 * lightning, and every brightening point goes through the kit's flash guard.
 */
class PlasmaSim : ThemeSim() {

    /** 4 + round(10 × min(km/h, 110) ÷ 110): 4 standing still, 14 at the cap. */
    val filaments: Int get() = MIN_FILAMENTS + (10f * min(kmh, SPRINT_CAP_KMH) / SPRINT_CAP_KMH).roundToInt()
    /** How far the filaments reach toward the glass: 1 normally, about 0.55 while braking (and when held by a safety warning). */
    var reach = 1f; private set
    /** −1 gathered at the left of the glass .. 0 spread evenly .. 1 gathered at the right. */
    var gather = 0f; private set
    /** The filaments' brightness, a slow breath within 0.85..1.0 (never a flicker). */
    var flicker = 0.925f; private set
    /** The wander clock of the filaments (it runs with the speed, capped at 110 km/h; decoration time). */
    var phase = 0.0; private set
    /** Trip distance in metres (the counter tubes show its last five digits). */
    var odometerM = 0; private set
    /** The ladder's arc: 0 at the foot .. 1 at the top, the fraction of the present kilometre (back to 0 at each one). */
    var ladder = 0f; private set
    /** The valves' filament glow 0.3..1 (it follows the acceleration). */
    var tubeGlow = 0.3f; private set
    /** The scope's trace: cycles across the screen (with the speed) and amplitude 0.25..1 (with the fan). */
    val scopeHz: Float get() = 1.5f + 4.5f * min(kmh, SPRINT_CAP_KMH) / SPRINT_CAP_KMH
    val scopeAmp: Float get() = 0.25f + 0.75f * fan
    /** The scope's sweep clock (seconds of decoration time). */
    var scopeT = 0.0; private set
    /** The whole fan's slow turn after a combo: 0..2π over [SPIN_S], then 0 again. */
    val spin: Float get() {
        val t = since(comboAt).toFloat()
        if (t < 0f || t > SPIN_S || calm) return 0f
        val u = t / SPIN_S
        return (u * u * (3f - 2f * u)) * 2f * PI.toFloat()
    }
    /** The last touch point left by a blink: its time (decoration) and side. */
    var touchAt = -100.0; private set
    var touchSide = TurnSignal.NONE; private set
    /** Where the star's point of light rests on the glass (radians from the top, clockwise). */
    var starAngle = 0.6f; private set
    /** 0 normal .. 1 braking (smoothed), for the painter's dimming. */
    var braking = 0f; private set

    override val allChips: List<String> = listOf(CHIP_FLOW, CHIP_LEFT, CHIP_RIGHT, CHIP_BRAKE, CHIP_STAR)

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        val c = car
        val brake = c.brakePressed == true
        braking = approach(braking, if (brake) 1f else 0f, (dt / 0.35).toFloat())
        val held = freeze
        reach = approach(reach, if (brake) BRAKE_REACH else 1f - 0.4f * held, (dt / 0.5).toFloat())
        val side = when (turn) { TurnSignal.LEFT -> -1f; TurnSignal.RIGHT -> 1f; else -> 0f }
        gather = approach(gather, side, (dt / 0.6).toFloat())

        val v = min(kmh, SPRINT_CAP_KMH) / SPRINT_CAP_KMH
        val rate = (if (calm) 0.5 else 1.0) * (0.25 + 0.75 * v)
        if (!reducedMotion) phase += ddt * rate
        scopeT += ddt
        // A slow breath: two incommensurate sines, always inside 0.85..1.0.
        flicker = (0.925 + 0.045 * sin(decoTime * 1.7) + 0.03 * sin(decoTime * 2.9 + 1.3)).toFloat().coerceIn(0.85f, 1f)
        tubeGlow = approach(tubeGlow, (0.3f + 0.7f * (accel / 2.5f).coerceIn(0f, 1f)), (dt / 0.7).toFloat())

        val km = input.journey.tripKm.coerceAtLeast(0f)
        odometerM = (km * 1000f).toInt()
        ladder = (km - floor(km)).coerceIn(0f, 1f)
    }

    override fun onBlink(side: TurnSignal) {
        if (side == TurnSignal.LEFT || side == TurnSignal.RIGHT) { touchAt = decoTime; touchSide = side }
    }

    override fun onStar() { starAngle = (0.5f + (stars * 2.399963f) % 5.2f) }

    override fun chooseChip(): String? = when {
        car.brakePressed == true && kmh > 3f -> CHIP_BRAKE
        turn == TurnSignal.LEFT -> CHIP_LEFT
        turn == TurnSignal.RIGHT -> CHIP_RIGHT
        since(starAt) in 0.0..STAR_S.toDouble() -> CHIP_STAR
        moving && kmh > 3f -> CHIP_FLOW
        else -> null
    }

    companion object {
        const val CHIP_FLOW = "전류가 일렁여요"
        const val CHIP_LEFT = "왼쪽으로 모여요"
        const val CHIP_RIGHT = "오른쪽으로 모여요"
        const val CHIP_BRAKE = "전류가 움츠러들어요"
        const val CHIP_STAR = "빛점이 남았어요"
        const val MIN_FILAMENTS = 4
        const val MAX_FILAMENTS = 14
        /** Above this speed nothing grows any more (speeding is never rewarded). */
        const val SPRINT_CAP_KMH = 110f
        const val BRAKE_REACH = 0.55f
        /** How long the star's point of light rests on the glass (s). */
        const val STAR_S = 5f
        /** The combo's slow turn (s for one revolution). */
        const val SPIN_S = 7f
    }
}
