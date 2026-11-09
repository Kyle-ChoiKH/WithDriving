package io.vroomi.app

import io.vroomi.app.runtime.MovingDetector
import io.vroomi.app.runtime.VroomiRuntime
import io.vroomi.app.settings.parseTheme
import io.vroomi.app.ui.ThemeRegistry
import io.vroomi.character.CharacterDirector
import io.vroomi.character.CharacterTarget
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.theme.isKitTheme
import io.vroomi.character.theme.ThemeSim
import io.vroomi.character.theme.ThemeSims
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The contract every procedural theme keeps, checked over every registered kit theme: the car is never
 * written, chips are short and honest, no chip shows during a safety warning, no screen region pulses more
 * than 3 times a second, and a car whose every signal is unknown still gives a resting scene.
 */
class ThemeContractTest {

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

    class Run(val theme: SceneTheme) {
        var writes = -1
        val chips = HashSet<String>()
        var chipDuringSafety = false
        var frozeOnSafety = false
        val pulses = HashMap<Int, MutableList<Double>>()
        lateinit var sim: ThemeSim
    }

    private val kitThemes: List<SceneTheme> get() = ThemeRegistry.all.map { it.theme }.filter { isKitTheme(it) }

    /** Plays [scenarioId] through the app pipeline at 60 Hz, then 6 s of a safety warning. */
    fun play(theme: SceneTheme, scenarioId: String): Run {
        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/$scenarioId.json").readText())
        assertEquals("$scenarioId shows its theme", theme, VroomiRuntime.demoThemeOf(scenario))
        val sampler = ScenarioSampler(scenario)
        val provider = CountingProvider()
        val processor = SignalProcessor(); val detector = DrivingEventDetector(); val moving = MovingDetector()
        val director = CharacterDirector()
        val sim = requireNotNull(ThemeSims.create(theme))
        val r = Run(theme); r.sim = sim
        sim.flash.recorder = { region, t -> r.pulses.getOrPut(region) { ArrayList() } += t }
        val start = 1_000_000L
        val playEnd = start + ((scenario.durationS + 4f) * 1000).toLong()
        val end = playEnd + 6_000
        var t = start
        var target = director.update(PARKED_CAR, false, false, emptySet(), start)
        var nextSample = start
        val log = StringBuilder()
        var lastChip: String? = "-"
        while (t <= end) {
            if (t >= nextSample) {
                nextSample += 100
                provider.flow.value = sampler.sample(((t - start) / 1000f).coerceAtMost(scenario.durationS), PARKED_CAR, t, start)
                val raw = provider.snapshot.value
                val p = processor.onSpeedSample(t, raw.speedKmh ?: 0f)
                val s = raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2)
                detector.update(t, p.accelMps2, s.brakePressed).forEach { director.onDrivingEvent(it, t) }
                target = director.update(s, moving.update(s.speedKmh, s.gear, false, t), false, emptySet(), t)
            }
            val safety = t > playEnd
            val shown = if (safety) target.copy(car = target.car.copy(safetyWarningActive = true)) else target
            sim.step(1f / 60f, shown, t, 14f)
            sim.chip?.let { r.chips += it }
            if (sim.chip != lastChip) {
                log.append("%7.2fs v=%5.1f %s chip=%s%s\n".format((t - start) / 1000f, shown.car.speedKmh ?: 0f, shown.car.gear.name, sim.chip,
                    if (safety) " (safety)" else ""))
                lastChip = sim.chip
            }
            if (safety && t > playEnd + 200 && sim.chip != null) r.chipDuringSafety = true
            if (safety && sim.freeze > 0.95f) r.frozeOnSafety = true
            t += 1000L / 60
        }
        r.writes = provider.writes
        File("build/scenario-logs").mkdirs()
        File("build/scenario-logs/${scenarioId}_theme.txt").writeText(log.toString())
        return r
    }

    private fun assertContract(r: Run) {
        val name = r.theme.name
        assertEquals("$name: the car is only read", 0, r.writes)
        (r.chips + r.sim.allChips).forEach { c ->
            assertTrue("$name chip too long: $c", c.length <= 12)
            ThemeSim.FORBIDDEN_WORDS.forEach { w -> assertTrue("$name chip '$c' contains '$w'", w !in c) }
        }
        assertTrue("$name: chips $r.chips are all listed in allChips", r.sim.allChips.containsAll(r.chips))
        assertTrue("$name: no chip during a safety warning", !r.chipDuringSafety)
        assertTrue("$name: decoration freezes on a safety warning", r.frozeOnSafety)
        r.pulses.forEach { (region, times) ->
            for (i in 3 until times.size) assertTrue("$name: region $region pulses 4 times within 1 s", times[i] - times[i - 3] >= 1.0)
        }
    }

    @Test fun everyKitThemeKeepsTheContract() {
        kitThemes.forEach { theme ->
            val id = requireNotNull(ThemeRegistry.of(theme).showcase)
            assertContract(play(theme, id))
        }
    }

    @Test fun unknownSignalsGiveARestingScene() {
        kitThemes.forEach { theme ->
            val sim = requireNotNull(ThemeSims.create(theme))
            val empty = CharacterTarget()
            var t = 2_000_000L
            repeat(60 * 30) { sim.step(1f / 60f, empty, t, 14f); t += 1000L / 60 }
            assertTrue("${theme.name} rests when nothing is known", !sim.moving && sim.kmh == 0f && !sim.speedKnown)
            sim.chip?.let { c -> assertTrue(c.length <= 12) }
        }
    }

    @Test fun twelveThemesWithoutMarble() {
        assertEquals(12, ThemeRegistry.all.size)
        assertFalse(SceneTheme.entries.any { it.name == "MARBLE" })
        assertTrue(ThemeRegistry.has(SceneTheme.PLASMA) && ThemeRegistry.has(SceneTheme.HANGAR))
        assertEquals(SceneTheme.entries.toSet(), ThemeRegistry.all.map { it.theme }.toSet())
    }

    @Test fun parseThemeFallsBackToKart() {
        assertEquals(SceneTheme.KART, parseTheme("MARBLE")); assertEquals(SceneTheme.KART, parseTheme(null))
        assertEquals(SceneTheme.HANGAR, parseTheme("HANGAR"))
    }

    @Test fun demosShowTheirOwnStep() {
        File("src/main/assets/scenarios").listFiles { f -> f.name.endsWith("_showcase.json") }!!.forEach { f ->
            assertEquals("${f.name} shows the top step", 4, ScenarioParser.parse(f.readText()).model)
        }
        val plain = ScenarioParser.parse(File("src/main/assets/scenarios/commute.json").readText())
        assertEquals(4, VroomiRuntime.demoModelOf(plain))
        assertEquals(1, VroomiRuntime.demoModelOf(plain.copy(model = 1)))
        assertEquals(4, VroomiRuntime.demoModelOf(plain.copy(model = 9)))
    }

    @Test fun registryCatalogAndSimsAgree() {
        ThemeRegistry.all.forEach { spec ->
            if (isKitTheme(spec.theme)) assertTrue("${spec.theme} has a simulation", ThemeSims.create(spec.theme) != null)
            else assertNull(ThemeSims.create(spec.theme))
            spec.showcase?.let { assertTrue("$it exists", File("src/main/assets/scenarios/$it.json").exists()) }
        }
        // A stored theme that is not built falls back to KART, and KART is always first.
        SceneTheme.entries.forEach { assertTrue(ThemeRegistry.has(ThemeRegistry.resolve(it))) }
        assertEquals(SceneTheme.KART, ThemeRegistry.all.first().theme)
        // Voice: only themes with a character speak their lines by default.
        assertEquals(listOf(SceneTheme.KART), SceneTheme.entries.filter { it.hasCharacter })
    }
}
