package io.vroomi.app.ui

import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.passage.PassagePhase
import io.vroomi.app.ui.passage.PassageRequest
import io.vroomi.app.ui.passage.PassageStyle
import io.vroomi.app.ui.passage.PullMath
import io.vroomi.app.ui.passage.SwipeMath
import io.vroomi.character.calibre.SceneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** The passage state machine, its interruptions and the stage gestures (pure, no Android). */
class ThemePassageTest {
    private val flat: (SceneTheme) -> Float = { 0.2f }
    private val lum: (SceneTheme) -> Float = { t -> io.vroomi.app.ui.design.Ambience.of(t).luminance }

    @Test fun fullSequenceFromRequestBackToIdle() {
        val s = PassageSim(flat, SceneTheme.KART)
        s.request(PassageRequest(SceneTheme.CALIBRE))
        var soundOut = s.p.out.soundOut
        var soundIn = 0
        var t = 0f
        while (t < 3000f) {
            s.step(); t += s.dt
            soundOut = soundOut || s.p.out.soundOut
            if (s.p.out.soundIn) soundIn++
            if (s.p.isIdle) break
        }
        val order = s.phases.distinct()
        assertEquals(listOf(PassagePhase.CAPTURING, PassagePhase.RELEASING, PassagePhase.PREWARMING, PassagePhase.REVEALING, PassagePhase.SETTLING, PassagePhase.IDLE), order)
        assertEquals(SceneTheme.CALIBRE, s.p.out.live)
        assertEquals(0f, s.p.out.snapshotAlpha, 0f)
        assertEquals(1f, s.p.out.tintMix, 0f)
        assertTrue("old sound fades at the start", soundOut)
        assertEquals("new sound starts once, in Settling", 1, soundIn)
        assertTrue("whole passage under 1.4 s: $t", t < 1400f)
    }

    @Test fun introRunsWithTheEntranceOnlyAndRestsOtherwise() {
        // The live scene's own accent (SceneOptions.intro): 0 on the target's first frame, rising with time, 1 at rest.
        val s = PassageSim(flat, SceneTheme.KART)
        s.request(PassageRequest(SceneTheme.ORBIT))
        val seen = ArrayList<Float>()
        var guard = 0
        while (!s.p.isIdle && guard++ < 400) {
            s.step()
            if (s.p.out.live == SceneTheme.ORBIT && !s.p.isIdle) seen += s.p.out.intro
            else assertEquals("only the target theme gets the accent", 1f, s.p.out.intro, 0f)
        }
        assertEquals(0f, seen.first(), 0f)
        assertTrue("never goes back", seen.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(1f, s.p.out.intro, 0f)

        // A crossfade (reduced motion, driving, the demo) never plays it.
        val c = PassageSim(flat, SceneTheme.KART)
        c.request(PassageRequest(SceneTheme.ORBIT, PassageStyle.CROSSFADE))
        repeat(120) { c.step(); assertEquals(1f, c.p.out.intro, 0f) }

        // A safety warning ends it at once.
        val w = PassageSim(flat, SceneTheme.KART)
        w.request(PassageRequest(SceneTheme.ORBIT))
        while (w.p.phaseNow != PassagePhase.REVEALING) w.step()
        w.run(100f)
        assertTrue(w.p.out.intro < 1f)
        w.p.safety(true)
        assertEquals(1f, w.p.out.intro, 0f)
        repeat(30) { w.step(); assertEquals(1f, w.p.out.intro, 0f) }
    }

    @Test fun neverTwoLiveScenes() {
        val rnd = Random(11)
        val all = SceneTheme.entries.filter { it != SceneTheme.KART }.plus(SceneTheme.KART)
        repeat(30) {
            val s = PassageSim(lum, SceneTheme.KART)
            repeat(400) { f ->
                if (rnd.nextInt(14) == 0) {
                    val r = PassageRequest(all[rnd.nextInt(all.size)], PassageStyle.entries[rnd.nextInt(4)])
                    if (rnd.nextBoolean()) s.p.interrupt(r) else s.p.request(r)
                }
                if (rnd.nextInt(90) == 0) s.p.safety(rnd.nextBoolean())
                if (rnd.nextInt(120) == 0) s.p.moving(rnd.nextBoolean())
                s.step()
            }
            // A different theme is never mounted in the frame right after another: a null frame comes between.
            for (i in 1 until s.lives.size) {
                val a = s.lives[i - 1]; val b = s.lives[i]
                assertFalse("two live scenes at frame $i: $a -> $b", a != null && b != null && a != b)
            }
        }
    }

    @Test fun interruptDuringRevealContinuesFromWhatIsOnScreen() {
        val base = PassageSim(lum, SceneTheme.KART)
        base.request(PassageRequest(SceneTheme.ORBIT))
        base.runUntilIdle()
        val normal = base.trace.zipWithNext { a, b -> abs(b - a) }.max()

        val s = PassageSim(lum, SceneTheme.KART)
        s.request(PassageRequest(SceneTheme.ORBIT))
        while (s.p.phaseNow != PassagePhase.REVEALING) s.step()
        s.run(250f)
        val before = s.trace.size
        s.p.interrupt(PassageRequest(SceneTheme.TERRARIUM))
        s.runUntilIdle()
        assertEquals(SceneTheme.TERRARIUM, s.p.out.live)
        val steps = s.trace.zipWithNext { a, b -> abs(b - a) }
        // Around the interrupt the screen does not jump: no step larger than a normal frame's change.
        for (i in (before - 2) until (before + 8)) assertTrue("step $i ${steps[i]} > $normal", steps[i] <= normal + 1e-3f)
        assertTrue(steps.max() <= maxOf(normal, 0.1f) + 1e-3f)
    }

    @Test fun safetyFinishesWithin150msOpacityOnly() {
        val s = PassageSim(flat, SceneTheme.KART)
        s.request(PassageRequest(SceneTheme.MARBLE))
        while (s.p.phaseNow != PassagePhase.REVEALING) s.step()
        s.run(200f)
        val scale = s.p.out.snapshotScale; val reveal = s.p.out.reveal; val dim = s.p.out.snapshotDim
        s.p.safety(true)
        var t = 0f
        while (!s.p.isIdle) {
            s.step(); t += s.dt
            if (!s.p.isIdle) {
                assertEquals("scale frozen", scale, s.p.out.snapshotScale, 0f)
                assertEquals("mask frozen", reveal, s.p.out.reveal, 0f)
                assertEquals("dim frozen", dim, s.p.out.snapshotDim, 0f)
            }
            assertTrue("finished in $t ms", t <= Motion.SAFETY_FINISH_MS + s.dt)
        }
        assertEquals(SceneTheme.MARBLE, s.p.out.live)
        // While the warning lasts, a new request does not start any animation.
        s.request(PassageRequest(SceneTheme.ORBIT))
        s.run(300f)
        assertTrue(s.p.isIdle)
        assertEquals(SceneTheme.MARBLE, s.p.out.live)
        s.p.safety(false)
        s.runUntilIdle()
        assertEquals(SceneTheme.ORBIT, s.p.out.live)
    }

    @Test fun movingTurnsEveryPassageIntoAnOpacityCrossfade() {
        val s = PassageSim(lum, SceneTheme.KART)
        s.p.moving(true)
        for (t in listOf(SceneTheme.PAPER, SceneTheme.ORBIT, SceneTheme.PIXEL)) {
            s.request(PassageRequest(t, PassageStyle.ENTRANCE))
            while (true) {
                s.step()
                val o = s.p.out
                assertEquals(PassageStyle.CROSSFADE, o.style)
                assertEquals(1f, o.snapshotScale, 0f)
                assertEquals(0f, o.snapshotBlur, 0f)
                assertEquals(0f, o.snapshotDim, 0f)
                assertFalse(o.maskOn)
                if (s.p.isIdle) break
            }
        }
    }

    @Test fun reducedMotionIsA200msCrossfade() {
        val s = PassageSim(flat, SceneTheme.KART)
        s.p.reducedMotion = true
        s.request(PassageRequest(SceneTheme.CALIBRE))
        s.runUntilIdle()
        val revealing = s.phases.count { it == PassagePhase.REVEALING }
        assertEquals(Motion.REDUCED_MS / (1000f / 60f), revealing.toFloat(), 1.5f)
        // Two themes far apart in brightness take the calm 450 ms instead (the flash rule).
        val d = PassageSim(lum, SceneTheme.ORBIT)
        d.p.reducedMotion = true
        d.request(PassageRequest(SceneTheme.KART))
        d.runUntilIdle()
        assertTrue(d.phases.count { it == PassagePhase.REVEALING } * d.dt >= Motion.CALM_CROSSFADE_MS - d.dt)
    }

    @Test fun oneRunsAndOneWaits() {
        val s = PassageSim(flat, SceneTheme.KART)
        s.request(PassageRequest(SceneTheme.PAPER))
        s.step()
        s.request(PassageRequest(SceneTheme.CALIBRE))
        s.request(PassageRequest(SceneTheme.ENGINE)) // replaces the waiting CALIBRE
        assertEquals(SceneTheme.ENGINE, s.p.pending?.target)
        val shown = LinkedHashSet<SceneTheme>().apply { add(SceneTheme.KART) }
        var t = 0f
        while (t < 5000f) { s.step(); t += s.dt; s.p.out.live?.let { shown += it }; if (s.p.isIdle && s.p.pending == null) break }
        assertEquals(listOf(SceneTheme.KART, SceneTheme.PAPER, SceneTheme.ENGINE), shown.toList())
        assertNull(s.p.pending)
    }

    @Test fun swipeCommitsByDistanceOrVelocityAndRubberBands() {
        val w = 1920f
        assertEquals(0, SwipeMath.decide(-0.29f * w, w, 0f, true, true))
        assertEquals(-1, SwipeMath.decide(-0.31f * w, w, 0f, true, true))
        assertEquals(1, SwipeMath.decide(0.31f * w, w, 0f, true, true))
        assertEquals(-1, SwipeMath.decide(-40f, w, -900f, true, true))
        assertEquals(0, SwipeMath.decide(-40f, w, -700f, true, true))
        // No neighbour: never commits, and the drag is damped by 0.35.
        assertEquals(0, SwipeMath.decide(-0.6f * w, w, -2000f, true, false))
        assertEquals(-100f * SwipeMath.RUBBER, SwipeMath.follow(-100f, true, false), 1e-4f)
        assertEquals(-100f, SwipeMath.follow(-100f, true, true), 1e-4f)
    }

    @Test fun pullClosesByDistanceOrVelocity() {
        val h = 720f
        assertEquals(100f, PullMath.follow(100f), 1e-4f)
        assertEquals(120f + 80f * PullMath.RUBBER, PullMath.follow(200f), 1e-4f)
        assertTrue(PullMath.closes(0.36f * h, h, 0f))
        assertFalse(PullMath.closes(0.30f * h, h, 0f))
        assertTrue(PullMath.closes(40f, h, 950f))
        assertEquals(PullMath.MIN_SCALE, PullMath.scale(1f), 1e-4f)
        assertEquals(1f, PullMath.scale(0f), 1e-4f)
    }
}
