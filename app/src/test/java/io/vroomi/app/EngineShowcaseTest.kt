package io.vroomi.app

import io.vroomi.app.runtime.MovingDetector
import io.vroomi.app.runtime.VroomiRuntime
import io.vroomi.character.CharacterDirector
import io.vroomi.character.CharacterTarget
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EngineMechanics
import io.vroomi.character.engine.EngineMechanics.Event
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.engine.Powertrain
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
 * Plays both Cutaway showcases through the app pipeline and the engine mechanics at 60 Hz: every row of the
 * car → powertrain table must happen, and the car must never be written to.
 */
class EngineShowcaseTest {

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

    private class Run(val events: Set<Event>, val chips: Set<String>, val gears: Set<String>) {
        var writes = -1
        var fuelCut = false; var maxBoost = 0.0; var wastegate = false; var acClutch = false; var maxFan = 0.0; var heaterWarm = false
        var rock = false; var clean = false; var night = false; var rain = false; var cleared = false; var relay = false
        var reverseIdler = false; var shutdown = false; var kickdown = false; var maxRpm = 0.0; var odometer = 0f; var comboPulse = false
        var regen = false; var drive = false; var reverseRotor = false; var charging = false; var ecomp = false; var cellLevel = -1f
        var powertrain = mutableSetOf<Powertrain>(); var frozenNullChip = false
    }

    private fun play(id: String): Run {
        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/$id.json").readText())
        assertEquals(SceneTheme.ENGINE, VroomiRuntime.demoThemeOf(scenario))
        val forced = Powertrain.valueOf(scenario.powertrain!!)
        val sampler = ScenarioSampler(scenario)
        val provider = CountingProvider()
        val processor = SignalProcessor(); val detector = DrivingEventDetector(); val moving = MovingDetector()
        val director = CharacterDirector()
        val mech = EngineMechanics()
        val events = HashSet<Event>(); val chips = HashSet<String>(); val gears = HashSet<String>()
        val log = StringBuilder()
        var lastChip: String? = "-"
        val start = 1_000_000L
        val end = start + ((scenario.durationS + 6f) * 1000).toLong()
        var t = start
        var target = director.update(PARKED_CAR, false, false, emptySet(), start)
        var nextSample = start
        val r = Run(events, chips, gears)
        var lastGear = 0
        while (t <= end) {
            if (t >= nextSample) {
                nextSample += 100
                provider.flow.value = sampler.sample((t - start) / 1000f, PARKED_CAR, t, start)
                val raw = provider.snapshot.value
                val p = processor.onSpeedSample(t, raw.speedKmh ?: 0f)
                val s = raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2)
                detector.update(t, p.accelMps2, s.brakePressed).forEach { director.onDrivingEvent(it, t) }
                target = director.update(s, moving.update(s.speedKmh, s.gear, false, t), false, emptySet(), t)
            }
            mech.step(1f / 60f, target, t, 14f, EnginePowertrain.AUTO, forced)
            val ev = mech.drainEvents(); events += ev
            mech.chip?.let { chips += it }
            r.powertrain += mech.powertrain
            val e = mech.engine
            if (forced == Powertrain.ICE) {
                gears += e.gearLabel
                val g = e.gearbox.gear
                if (g < lastGear && e.load > 0.85) r.kickdown = true
                lastGear = g
                if (e.fuelCut) r.fuelCut = true
                r.maxBoost = maxOf(r.maxBoost, e.boost); if (e.wastegateOpen) r.wastegate = true
                if (mech.acPlate > 0.9f) r.acClutch = true
                if (mech.heater > 0.9f) r.heaterWarm = true
                if (mech.reverseIdler != 0.0) r.reverseIdler = true
                if (!e.running) r.shutdown = true
                r.maxRpm = maxOf(r.maxRpm, e.rpm)
                if (mech.cleanBurn.any { it }) r.clean = true
                if ((mech.decoTime - mech.comboPulseStart) in 0.0..1.6) r.comboPulse = true
            } else {
                if (mech.motor.regen) r.regen = true
                if (mech.motor.driving) r.drive = true
                if (mech.motor.rpm < -100) r.reverseRotor = true
                if (mech.charging > 0.9f) r.charging = true
                if (mech.acPlate > 0.9f) r.ecomp = true
                r.cellLevel = maxOf(r.cellLevel, mech.cellLevel)
            }
            r.maxFan = maxOf(r.maxFan, mech.fanRps)
            if (kotlin.math.abs(mech.rock()) > 0.3f) r.rock = true
            if (mech.night > 0.9f) r.night = true
            if (mech.rain > 0.9f) r.rain = true
            if (mech.fog > 0.3f && mech.clear > 0.5f) r.cleared = true
            if (mech.relayL > 0.9f) r.relay = true
            r.odometer = maxOf(r.odometer, mech.odometer)
            if (target.car.safetyWarningActive == true && mech.chip == null) r.frozenNullChip = true
            if (mech.chip != lastChip || ev.any { it != Event.IGNITION }) {
                log.append("%7.2fs v=%5.1f %s rpm=%6.0f load=%.2f boost=%.2f kW=%6.1f chip=%s %s\n".format(
                    (t - start) / 1000f, target.car.speedKmh ?: 0f, if (forced == Powertrain.ICE) e.gearLabel else target.car.gear.name,
                    if (forced == Powertrain.ICE) e.rpm else mech.motor.rpm, e.load, e.boost, mech.motor.powerKw, mech.chip, ev.filter { it != Event.IGNITION }))
                lastChip = mech.chip
            }
            t += 1000L / 60
        }
        File("build/scenario-logs").mkdirs()
        File("build/scenario-logs/${id}_mechanics.txt").writeText(log.toString())
        r.writes = provider.writes
        return r
    }

    @Test fun engineShowcase_everyMappingHappens_andTheCarIsNeverWritten() {
        val r = play("engine_showcase")
        assertEquals("the car is only read", 0, r.writes)
        assertEquals(setOf(Powertrain.ICE), r.powertrain)
        assertTrue("gears=${r.gears}", r.gears.containsAll(listOf("P", "D1", "D2", "D3", "R")))
        assertTrue("events=${r.events}", r.events.containsAll(listOf(Event.IGNITION, Event.SHIFT, Event.CLUTCH_ON, Event.RELAY, Event.SHUTDOWN)))
        assertTrue("fuel cut and A/C clutch", r.fuelCut && r.acClutch)
        assertTrue("boost=${r.maxBoost}", r.maxBoost > 0.5)
        assertTrue("kickdown", r.kickdown)
        assertTrue("fan=${r.maxFan}", r.maxFan > 1.0)
        assertTrue(r.rock && r.night && r.rain && r.cleared && r.relay && r.reverseIdler && r.shutdown)
        assertTrue("clean burn after a star", r.clean)
        assertTrue("chips=${r.chips}", r.chips.containsAll(listOf("공회전", "연료 차단", "에어컨 클러치", "도착")))
        assertTrue("rpm=${r.maxRpm}", r.maxRpm in 2500.0..6000.0)
    }

    @Test fun edriveShowcase_everyMappingHappens_andTheCarIsNeverWritten() {
        val r = play("edrive_showcase")
        assertEquals("the car is only read", 0, r.writes)
        assertEquals(setOf(Powertrain.EV), r.powertrain)
        assertTrue(r.drive && r.regen && r.reverseRotor && r.ecomp && r.charging)
        assertTrue("cells=${r.cellLevel}", r.cellLevel in 0.6f..0.75f)
        assertTrue("chips=${r.chips}", r.chips.containsAll(listOf("회생 제동", "충전 중", "전동 컴프레서")))
        assertTrue("no combustion on an EV", Event.IGNITION !in r.events)
    }

    @Test fun showcasesAreListedAfterCalibre() {
        val ids = listOf("calibre_showcase", "engine_showcase", "edrive_showcase")
        val orders = ids.map { ScenarioParser.parse(File("src/main/assets/scenarios/$it.json").readText()).order }
        assertEquals(listOf(1, 2, 3), orders)
        // Older demos keep the user's own theme.
        assertEquals(null, VroomiRuntime.demoThemeOf(ScenarioParser.parse(File("src/main/assets/scenarios/highlight.json").readText())))
        assertEquals(SceneTheme.CALIBRE, VroomiRuntime.demoThemeOf(ScenarioParser.parse(File("src/main/assets/scenarios/calibre_showcase.json").readText())))
        assertTrue(CharacterTarget().car.brakePressed == null)
    }
}
