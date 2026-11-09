package io.vroomi.app

import io.vroomi.app.runtime.MovingDetector
import io.vroomi.character.CharacterDirector
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.CalibreMechanics
import io.vroomi.character.calibre.CalibreMechanics.Event
import io.vroomi.character.calibre.CalibreTone
import io.vroomi.core.vehicle.model.CapabilityMap
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.provider.VehicleSignalProvider
import io.vroomi.core.vehicle.signal.DrivingEventDetector
import io.vroomi.core.vehicle.signal.SignalProcessor
import io.vroomi.core.vehicle.sim.PARKED_CAR
import io.vroomi.core.vehicle.sim.ScenarioParser
import io.vroomi.core.vehicle.sim.ScenarioSampler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plays calibre_showcase.json through the app pipeline and the Calibre mechanics at 60 Hz:
 * every row of the car -> movement table must happen, and the car must never be written to.
 */
class CalibreShowcaseTest {

    /** A vehicle provider that only counts writes (the Calibre theme must make none). */
    private class CountingProvider : VehicleSignalProvider {
        val flow = MutableStateFlow(PARKED_CAR)
        override val snapshot: StateFlow<VehicleSnapshot> = flow
        override val capabilities: StateFlow<CapabilityMap> = MutableStateFlow(CapabilityMap.ALL)
        var writes = 0
        override suspend fun start() = Unit
        override suspend fun stop() = Unit
        override suspend fun setFanLevel(level: Int): Result<Unit> { writes++; return Result.success(Unit) }
        override suspend fun setTemperature(celsius: Float): Result<Unit> { writes++; return Result.success(Unit) }
    }

    @Test fun everyMappingHappensAndTheCarIsNeverWritten() {
        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/calibre_showcase.json").readText())
        assertEquals("칼리버 시계 쇼케이스 (2분)", scenario.title)
        val sampler = ScenarioSampler(scenario)
        val provider = CountingProvider()
        val processor = SignalProcessor(); val detector = DrivingEventDetector(); val moving = MovingDetector()
        val director = CharacterDirector()
        val mech = CalibreMechanics(30f)

        val events = HashSet<Event>()
        val tones = HashSet<CalibreTone>()
        var maxCrown = 0.0; var minOmega = 0.0; var maxOmega = 0.0; var maxRain = 0f; var cleared = false
        var nightSeen = false; var coolSeen = false; var warmSeen = false; var frozenNullChip = false; var winderSeen = false
        var maxElapsed = 0L; var chronoStoppedAtEnd = false
        val log = StringBuilder()
        var lastChip: String? = "-"

        val start = 1_000_000L
        val end = start + ((scenario.durationS + 4f) * 1000).toLong()
        var t = start
        var target = director.update(PARKED_CAR, false, false, emptySet(), start)
        var nextSample = start
        while (t <= end) {
            if (t >= nextSample) { // the app samples the car at 10 Hz
                nextSample += 100
                provider.flow.value = sampler.sample((t - start) / 1000f, PARKED_CAR, t, start)
                val raw = provider.snapshot.value
                val p = processor.onSpeedSample(t, raw.speedKmh ?: 0f)
                val s = raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2)
                detector.update(t, p.accelMps2, s.brakePressed).forEach { director.onDrivingEvent(it, t) }
                target = director.update(s, moving.update(s.speedKmh, s.gear, false, t), false, emptySet(), t)
            }
            mech.step(1f / 60f, target, t, 14f, CalibreFinish.AUTO)
            val ev = mech.drainEvents(); events += ev
            tones += mech.tone
            maxCrown = maxOf(maxCrown, mech.crownRps); minOmega = minOf(minOmega, mech.rotorOmega); maxOmega = maxOf(maxOmega, mech.rotorOmega)
            maxRain = maxOf(maxRain, mech.rain)
            if (mech.fog > 0.5f && mech.clear > 0.5f) cleared = true
            if (mech.night > 0.9f) nightSeen = true
            if (mech.cool > 0.9f && mech.warm < 0.1f) coolSeen = true
            if (mech.warm > 0.85f) warmSeen = true
            if (target.car.safetyWarningActive == true && mech.freeze > 0.9f && mech.chip == null) frozenNullChip = true
            if (mech.winder > 0.5f) winderSeen = true
            maxElapsed = maxOf(maxElapsed, mech.chronoElapsedMs)
            if (mech.chip != lastChip || ev.any { it != Event.CLICK }) {
                log.append("%7.2fs v=%5.1f rotor=%6.1frpm crown=%.2frps R=%5.2fh chip=%s %s\n".format(
                    (t - start) / 1000f, target.car.speedKmh ?: 0f, mech.rotorOmega * 60 / (2 * Math.PI), mech.crownRps, mech.reserveH, mech.chip,
                    ev.filter { it != Event.CLICK }))
                lastChip = mech.chip
            }
            t += 1000L / 60
        }
        chronoStoppedAtEnd = !mech.chronoRunning
        File("build/scenario-logs").mkdirs()
        File("build/scenario-logs/calibre_showcase_mechanics.txt").writeText(log.toString())

        assertEquals("the car is only read", 0, provider.writes)
        assertTrue("events=$events", events.containsAll(listOf(
            Event.CHRONO_START, Event.CHRONO_STOP, Event.HAMMER_LEFT, Event.CHIME, Event.SHOCK,
            Event.JEWEL_GLINT, Event.TRAIN_GLINT, Event.CLICK,
        )))
        assertTrue("crown=$maxCrown", maxCrown > 1.9)
        assertTrue("rotor fwd=$maxOmega", maxOmega * 60 / (2 * Math.PI) > 80)
        assertTrue("rotor reverse=$minOmega", minOmega < -0.3)
        assertTrue("tones=$tones", tones.containsAll(listOf(CalibreTone.YELLOW_GOLD, CalibreTone.ROSE_GOLD, CalibreTone.RHODIUM_BLUE)))
        assertTrue(maxRain > 0.9f && cleared && nightSeen && coolSeen && warmSeen && frozenNullChip)
        assertTrue("no EV charging in this drive", !winderSeen)
        assertTrue("chrono=$maxElapsed", maxElapsed > 100_000L && chronoStoppedAtEnd)
    }
}
