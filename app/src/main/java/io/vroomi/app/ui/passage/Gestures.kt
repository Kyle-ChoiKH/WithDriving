package io.vroomi.app.ui.passage

import kotlin.math.abs
import kotlin.math.sign

/** P5: the stage swipe between themes (pure, in dp so it does not depend on the display density). */
object SwipeMath {
    const val COMMIT_FRACTION = 0.30f
    const val COMMIT_VELOCITY = 800f
    const val RUBBER = 0.35f
    /** The outgoing scene follows the finger at 0.35 x and dims by up to 25 %. */
    const val OUTGOING_FOLLOW = 0.35f
    const val OUTGOING_DIM = 0.25f

    /**
     * The visible offset for a raw drag [rawDp] (negative = towards the next theme). Past the first or the last
     * theme the drag is damped by the 0.35 rubber band.
     */
    fun follow(rawDp: Float, hasPrev: Boolean, hasNext: Boolean): Float {
        val towardsNext = rawDp < 0f
        val allowed = if (towardsNext) hasNext else hasPrev
        return if (allowed) rawDp else rawDp * RUBBER
    }

    /** -1 (to the next theme), +1 (to the previous one) or 0 (spring back). Velocity is in dp/s, same sign as the drag. */
    fun decide(rawDp: Float, widthDp: Float, velocityDp: Float, hasPrev: Boolean, hasNext: Boolean): Int {
        if (widthDp <= 0f) return 0
        val dir = when {
            abs(velocityDp) > COMMIT_VELOCITY -> sign(velocityDp).toInt()
            abs(rawDp) >= COMMIT_FRACTION * widthDp -> sign(rawDp).toInt()
            else -> 0
        }
        // A fling against the drag never commits the drag's direction.
        if (dir != 0 && rawDp != 0f && dir != sign(rawDp).toInt() && abs(rawDp) >= COMMIT_FRACTION * widthDp) return 0
        return when (dir) {
            -1 -> if (hasNext) -1 else 0
            1 -> if (hasPrev) 1 else 0
            else -> 0
        }
    }
}

/** P4: pull the stage down to close it. */
object PullMath {
    const val CLOSE_FRACTION = 0.35f
    const val CLOSE_VELOCITY = 900f
    const val RUBBER_AFTER = 120f
    const val RUBBER = 0.35f
    const val MIN_SCALE = 0.86f

    /** The stage follows the finger; past 120 dp the 0.35 rubber band applies. Upward drags do not move it. */
    fun follow(rawDp: Float): Float {
        val d = rawDp.coerceAtLeast(0f)
        return if (d <= RUBBER_AFTER) d else RUBBER_AFTER + (d - RUBBER_AFTER) * RUBBER
    }

    /** 0 (stage) .. 1 (fully pulled) for the followed distance. */
    fun progress(followDp: Float, heightDp: Float): Float = if (heightDp <= 0f) 0f else (followDp / (heightDp * CLOSE_FRACTION)).coerceIn(0f, 1f)

    fun scale(progress: Float): Float = 1f - (1f - MIN_SCALE) * progress.coerceIn(0f, 1f)

    /** Close on release when the raw travel reaches 35 % of the height or the release is faster than 900 dp/s. */
    fun closes(rawDp: Float, heightDp: Float, velocityDp: Float): Boolean =
        velocityDp > CLOSE_VELOCITY || (heightDp > 0f && rawDp >= heightDp * CLOSE_FRACTION)
}
