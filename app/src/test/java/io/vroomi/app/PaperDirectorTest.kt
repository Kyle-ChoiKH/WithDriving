package io.vroomi.app

import io.vroomi.app.runtime.MovingDetector
import io.vroomi.character.CharacterDirector
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.paper.PaperSim
import io.vroomi.core.vehicle.signal.DrivingEventDetector
import io.vroomi.core.vehicle.signal.SignalProcessor
import io.vroomi.core.vehicle.sim.PARKED_CAR
import io.vroomi.core.vehicle.sim.ScenarioParser
import io.vroomi.core.vehicle.sim.ScenarioSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** PAPER reuses the KART director unchanged: the director's output is identical whether the paper book reads it or not. */
class PaperDirectorTest {
    private fun stream(withPaper: Boolean): List<String> {
        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/paper_showcase.json").readText())
        val sampler = ScenarioSampler(scenario)
        val processor = SignalProcessor(); val detector = DrivingEventDetector(); val moving = MovingDetector()
        val director = CharacterDirector()
        val paper = PaperSim()
        val out = ArrayList<String>()
        val start = 1_000_000L
        var t = start
        while (t <= start + (scenario.durationS * 1000).toLong()) {
            val raw = sampler.sample((t - start) / 1000f, PARKED_CAR, t, start)
            val p = processor.onSpeedSample(t, raw.speedKmh ?: 0f)
            val s = raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2)
            detector.update(t, p.accelMps2, s.brakePressed).forEach { director.onDrivingEvent(it, t) }
            val target = director.update(s, moving.update(s.speedKmh, s.gear, false, t), false, emptySet(), t)
            if (withPaper) repeat(6) { paper.step(1f / 60f, target, t, 14f) }
            out += "${target.gait} ${target.action} ${target.mood} ${target.statusChip} ${target.kart} ${target.journey}"
            t += 100
        }
        return out
    }

    @Test fun directorOutputIsIdenticalToKart() {
        val kart = stream(false); val paper = stream(true)
        assertEquals(kart.size, paper.size)
        assertEquals(kart, paper)
        // The book has its own cast (an origami car and crane), so the friend does not speak there by default.
        assertTrue(!SceneTheme.PAPER.hasCharacter)
    }
}
