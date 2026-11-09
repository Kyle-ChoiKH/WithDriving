package io.vroomi.character.calibre

/**
 * Calibre VR-01: an original automatic chronograph movement (layout invented for Vroomi, not a copy
 * of any real calibre). Tooth counts make the going train exact; positions are in units of the
 * movement radius, seen from the caseback (x right, y down, crown on the LEFT).
 */
object CalibreGeometry {
    // ---- regulating organ
    const val BEATS_PER_HOUR = 28_800
    const val BEATS_PER_SECOND = 8
    const val BALANCE_HZ = 4.0
    const val ESCAPE_TEETH = 20

    // ---- going train (wheel teeth / pinion leaves)
    const val BARREL_TEETH = 80
    const val CENTRE_PINION = 10
    const val CENTRE_TEETH = 80
    const val THIRD_PINION = 10
    const val THIRD_TEETH = 75
    const val FOURTH_PINION = 10
    const val FOURTH_TEETH = 84
    const val ESCAPE_PINION = 7

    // ---- keyless and automatic winding
    const val WINDING_PINION = 16
    const val CROWN_WHEEL_TEETH = 24
    const val RATCHET_TEETH = 60
    /** Rotor turns per ratchet-wheel turn through the reverser and reduction wheels. */
    const val ROTOR_PER_RATCHET = 40.0

    // ---- chronograph
    const val COLUMNS = 7
    const val RUNNER_TEETH = 60
    const val COUNTER_TEETH = 30

    const val POWER_RESERVE_H = 72f

    /** Revolutions per hour of each wheel (centre wheel = minute hand = 1 rph). */
    const val CENTRE_RPH = 1.0
    fun barrelRph(): Double = CENTRE_RPH * CENTRE_PINION / BARREL_TEETH
    fun thirdRph(): Double = CENTRE_RPH * CENTRE_TEETH / THIRD_PINION
    fun fourthRph(): Double = thirdRph() * THIRD_TEETH / FOURTH_PINION
    fun escapeRph(): Double = fourthRph() * FOURTH_TEETH / ESCAPE_PINION
    /** Two beats per escape tooth (one on each pallet stone). */
    fun beatsPerHour(): Double = escapeRph() * ESCAPE_TEETH * 2
    fun escapeRpm(): Double = escapeRph() / 60.0
    fun fourthRpm(): Double = fourthRph() / 60.0

    /** Ratchet-wheel turns per crown turn (the crown wheel is an idler between them). */
    fun ratchetPerCrownTurn(): Double = WINDING_PINION.toDouble() / RATCHET_TEETH

    // ---- layout (caseback view), units of movement radius R
    class Pivot(val x: Float, val y: Float, val r: Float)

    val CENTRE = Pivot(0f, 0f, 0.180f)
    val BARREL = Pivot(-0.240f, -0.237f, 0.300f)
    val RATCHET = Pivot(-0.240f, -0.237f, 0.265f)
    val CROWN_WHEEL = Pivot(-0.599f, -0.159f, 0.105f)
    val THIRD = Pivot(-0.100f, 0.176f, 0.169f)
    val FOURTH = Pivot(-0.040f, 0.356f, 0.189f)
    val ESCAPE = Pivot(0.130f, 0.471f, 0.085f)
    val PALLET = Pivot(0.262f, 0.470f, 0.10f)
    val BALANCE = Pivot(0.470f, 0.360f, 0.235f)
    val COLUMN = Pivot(0.600f, -0.360f, 0.080f)
    val RUNNER = Pivot(0.160f, -0.500f, 0.150f)
    val COUNTER = Pivot(0.430f, -0.640f, 0.075f)
    val REVERSER_A = Pivot(0.205f, -0.150f, 0.066f)
    val REVERSER_B = Pivot(0.300f, -0.030f, 0.066f)
    const val ROTOR_R = 0.965f
    const val BEARING_R = 0.120f

    /** Stem height (y) and the crown position outside the case, caseback view. */
    const val STEM_Y = -0.02f

    /**
     * Every jewel that is drawn on the caseback side, in glint order: the first seven follow the
     * going train from the barrel to the balance (a star travels along them).
     */
    val JEWELS: List<Pivot> = listOf(
        Pivot(-0.240f, -0.237f, 0.022f), // barrel arbor (under the ratchet screw)
        Pivot(0.000f, 0.000f, 0.022f),   // centre wheel (under the rotor bearing)
        THIRD.copyAsJewel(),
        FOURTH.copyAsJewel(),
        ESCAPE.copyAsJewel(),
        PALLET.copyAsJewel(),
        BALANCE.copyAsJewel(0.026f),     // balance cap jewel
        COLUMN.copyAsJewel(),
        RUNNER.copyAsJewel(),
        COUNTER.copyAsJewel(),
        REVERSER_A.copyAsJewel(0.016f),
        REVERSER_B.copyAsJewel(0.016f),
        // Screwed gold chatons on the bridges
        Pivot(-0.520f, -0.455f, 0.020f),
        Pivot(-0.540f, 0.500f, 0.020f),
        Pivot(0.550f, -0.715f, 0.018f),
        Pivot(0.330f, -0.380f, 0.018f),
        Pivot(0.284f, 0.700f, 0.018f),
    )

    /** Indices into [JEWELS] from the barrel down the train to the balance. */
    val TRAIN_PATH = intArrayOf(0, 1, 2, 3, 4, 5, 6)

    /** The engraving tells the truth: the number of jewels drawn. */
    fun jewelEngraving(): String = "${JEWELS.size} JEWELS"

    private fun Pivot.copyAsJewel(r: Float = 0.018f) = Pivot(x, y, r)

    /** Wheels that turn under the bridges (drawn as rotating cached bitmaps). */
    enum class Wheel(val pivot: Pivot, val teeth: Int, val spokes: Int) {
        CENTRE_W(CENTRE, CENTRE_TEETH, 4),
        THIRD_W(THIRD, THIRD_TEETH, 5),
        FOURTH_W(FOURTH, FOURTH_TEETH, 4),
        RUNNER_W(RUNNER, RUNNER_TEETH, 5),
        COUNTER_W(COUNTER, COUNTER_TEETH, 3),
        REVERSER_A_W(REVERSER_A, 28, 3),
        REVERSER_B_W(REVERSER_B, 28, 3),
    }
}
