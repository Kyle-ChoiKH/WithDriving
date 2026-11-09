package io.vroomi.app.ui

import io.vroomi.app.ui.design.Ambience
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.passage.PassageRequest
import io.vroomi.app.ui.passage.PassageStyle
import io.vroomi.app.ui.passage.SwipeMath
import io.vroomi.character.calibre.SceneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The luminance and flash rule over every ordered pair of the twelve themes (132 pairs) at 60 fps, with the real
 * passage curves and each theme's measured luminance:
 * - the screen's mean luminance never changes by more than 10 % of full scale between two frames;
 * - never more than three luminance reversals of 10 % or more inside any one second;
 * - a passage from dark to bright (a rise of 0.3 or more) spends at least 450 ms on the change.
 */
class LuminanceFlashTest {
    private val lum = PassageSim.measured()
    private val themes = ThemeRegistry.all.map { it.theme }

    private class Variant(val name: String, val style: PassageStyle, val intensity: Float = 1f, val moving: Boolean = false, val reduced: Boolean = false)

    private val variants = listOf(
        Variant("entrance", PassageStyle.ENTRANCE),
        Variant("quiet 0.5", PassageStyle.ENTRANCE, 0.5f),
        Variant("return 0.6", PassageStyle.ENTRANCE, 0.6f),
        Variant("moving", PassageStyle.ENTRANCE, moving = true),
        Variant("reduced", PassageStyle.ENTRANCE, reduced = true),
    )

    private fun reversals(trace: List<Float>, threshold: Float = 0.10f): List<Int> {
        // Turning points whose swing from the previous extreme is at least the threshold.
        val out = ArrayList<Int>()
        val start = trace.first()
        var lo = start; var hi = start
        var dir = 0
        var ext = start
        for (i in 1 until trace.size) {
            val v = trace[i]
            when (dir) {
                0 -> {
                    lo = minOf(lo, v); hi = maxOf(hi, v)
                    if (hi - start >= threshold) { dir = 1; ext = hi } else if (start - lo >= threshold) { dir = -1; ext = lo }
                }
                1 -> if (v > ext) ext = v else if (ext - v >= threshold) { out += i; dir = -1; ext = v }
                else -> if (v < ext) ext = v else if (v - ext >= threshold) { out += i; dir = 1; ext = v }
            }
        }
        return out
    }

    private fun check(name: String, from: SceneTheme, to: SceneTheme, trace: List<Float>) {
        val steps = trace.zipWithNext { a, b -> abs(b - a) }
        val max = steps.maxOrNull() ?: 0f
        assertTrue("$name $from -> $to: frame step %.3f".format(max), max <= 0.10f)
        val rev = reversals(trace)
        for (i in rev.indices) {
            val inWindow = rev.count { it in rev[i] until rev[i] + 60 }
            assertTrue("$name $from -> $to: $inWindow reversals in a second", inWindow <= 3)
        }
        val l0 = lum.getValue(from); val l1 = lum.getValue(to)
        if (l1 - l0 >= 0.3f) {
            val first = trace.indexOfFirst { abs(it - l0) > 0.01f }
            val last = trace.indexOfLast { abs(it - l1) > 0.01f }
            val ms = (last - first) * 1000f / 60f
            assertTrue("$name $from -> $to: dark to bright in %.0f ms".format(ms), ms >= 450f)
        }
    }

    @Test fun everyOrderedPairPassesTheFlashRule() {
        var pairs = 0
        for (v in variants) for (from in themes) for (to in themes) {
            if (from == to) continue
            val s = PassageSim({ lum.getValue(it) }, from)
            s.p.reducedMotion = v.reduced
            if (v.moving) s.p.moving(true)
            s.step()
            s.request(PassageRequest(to, v.style, v.intensity))
            s.runUntilIdle()
            s.run(100f)
            check(v.name, from, to, s.trace)
            pairs++
        }
        assertEquals(12 * 11 * variants.size, pairs)
    }

    @Test fun swipeCommitPassesTheFlashRule() {
        // P5: the neighbour's still slides in from 30 % to 100 % on the Smooth spring (the finger left at the
        // commit threshold), the outgoing world dims by up to 25 %, then the live scene replaces the still.
        for (from in themes) for (to in themes) {
            if (from == to) continue
            val l0 = lum.getValue(from); val l1 = lum.getValue(to)
            val trace = ArrayList<Float>()
            for (i in 0..18) { val f = SwipeMath.COMMIT_FRACTION * i / 18f; trace += (1 - f) * l0 * (1 - SwipeMath.OUTGOING_DIM * f / SwipeMath.COMMIT_FRACTION) + f * l1 }
            var t = 0.0
            while (t <= Motion.Smooth.settleMs + 100) {
                val f = SwipeMath.COMMIT_FRACTION + (1 - SwipeMath.COMMIT_FRACTION) * Motion.Smooth.progress(t)
                trace += (1 - f) * l0 * (1 - SwipeMath.OUTGOING_DIM) + f * l1
                t += 1000.0 / 60.0
            }
            val s = PassageSim({ lum.getValue(it) }, from)
            s.p.request(PassageRequest(to, PassageStyle.SWIPE, snapshotHeld = true))
            s.runUntilIdle()
            trace += s.trace
            val steps = trace.zipWithNext { a, b -> abs(b - a) }
            assertTrue("swipe $from -> $to: %.3f".format(steps.max()), steps.max() <= 0.10f)
        }
    }

    @Test fun ambienceLuminanceMatchesTheMeasurement() {
        for (t in themes) assertEquals("$t", lum.getValue(t), Ambience.of(t).luminance, 0.03f)
    }
}
