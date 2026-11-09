package io.vroomi.character.render

import kotlin.math.PI
import kotlin.math.sin

/**
 * Optional entrance accents driven by [SceneOptions.intro], the linear time of a theme's entrance
 * (0 = its first frame, 1 = normal). Painters take an accent path only while intro < 1, so a normal frame
 * is drawn exactly as before; every helper reaches its rest value at 1.
 */
internal object Intro {
    /** Cubic ease-out of the window [from, from + span] of intro (0 before it, 1 after it). */
    fun out(intro: Float, from: Float = 0f, span: Float = 1f): Float {
        val x = ((intro - from) / span).coerceIn(0f, 1f)
        val u = 1f - x
        return 1f - u * u * u
    }

    /** Ease-out with a small overshoot (about 5 %) over [from, from + span]: paper standing up. */
    fun back(intro: Float, from: Float, span: Float): Float {
        val x = ((intro - from) / span).coerceIn(0f, 1f)
        val s = 1.2f
        val u = x - 1f
        return 1f + (s + 1f) * u * u * u + s * u * u
    }

    /** One soft bump inside [from, 1]: 0 outside it, 1 at its middle (a suspension dip). */
    fun bump(intro: Float, from: Float): Float {
        if (intro <= from || intro >= 1f) return 0f
        return sin(PI.toFloat() * (intro - from) / (1f - from))
    }
}
