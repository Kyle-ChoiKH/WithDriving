package io.vroomi.app.demo

import io.vroomi.app.runtime.MovingDetector
import io.vroomi.app.ui.ThemeRegistry
import io.vroomi.character.CharacterDirector
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.signal.DrivingEvent
import io.vroomi.core.vehicle.signal.DrivingEventDetector
import io.vroomi.core.vehicle.signal.SignalProcessor
import io.vroomi.core.vehicle.sim.PARKED_CAR
import io.vroomi.core.vehicle.sim.Scenario
import io.vroomi.core.vehicle.sim.ScenarioParser
import io.vroomi.core.vehicle.sim.ScenarioSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The demo drives of every theme: what the picker offers, what the lessons script, what the hands-on drive does. */
class DemoCatalogTest {
    private val bundled: List<Scenario> = File("src/main/assets/scenarios").listFiles { f -> f.name.endsWith(".json") }!!
        .map { ScenarioParser.parse(it.readText()) }.sortedBy { it.order }
    private val themes = ThemeRegistry.all.map { it.theme }

    private fun captions(s: Scenario): List<Pair<Float, String>> = s.keyframes.mapNotNull { f -> (f.values["caption"] as? String)?.let { f.t to it } }

    /** The caption's width in ems (a Hangul syllable is one em): the dock's card holds two lines of about twenty. */
    private fun ems(s: String): Float = s.sumOf { c -> (if (c.code > 0x2E80) 1.0 else if (c == ' ') 0.3 else 0.56) }.toFloat()

    @Test fun everyThemeOffersItsShowcaseAndLessons() {
        for (theme in themes) {
            val sections = DemoCatalog.sections(theme, bundled)
            assertEquals("$theme starts with its showcase", "한눈에 보기", sections.first().title)
            assertEquals("$theme showcases", DemoCatalog.showcaseIds(theme), sections.first().items.map { it.scenario.id })
            // The showcase the theme tab starts is the one the picker lists first.
            assertEquals("$theme", ThemeRegistry.of(theme).showcase, DemoCatalog.showcaseIds(theme).first())
            val lessons = sections.first { it.title == "조작별로 보기" }.items
            assertTrue("$theme has ${lessons.size} lessons", lessons.size >= 2)
            assertEquals("$theme lesson ids differ", lessons.size, lessons.map { it.scenario.id }.distinct().size)
            // A lesson plays on its own theme, so picking one never changes the scene.
            assertTrue(lessons.all { it.scenario.theme == theme.name })
            // The stories written for the kart are offered with the kart only.
            assertEquals("$theme", theme == SceneTheme.KART, sections.any { s -> s.items.any { it.scenario.id in DemoCatalog.KART_STORIES } })
            // No other theme's showcase is listed.
            val foreign = themes.filter { it != theme }.flatMap { DemoCatalog.showcaseIds(it) }
            assertTrue("$theme", sections.none { s -> s.items.any { it.scenario.id in foreign } })
        }
    }

    @Test fun everyCaptionFitsTheDock() {
        val all = bundled.flatMap { s -> captions(s).map { "${s.id}: ${it.second}" } } +
            themes.flatMap { t -> Lesson.entries.mapNotNull { DemoCatalog.lesson(t, it) }.flatMap { s -> captions(s).map { "${s.id}: ${it.second}" } } }
        for (c in all) assertTrue("too long (${ems(c.substringAfter(": "))} em): $c", ems(c.substringAfter(": ")) <= 32f)
        for (cue in HandsOnDrive.CONTROLS) assertTrue(HandsOnDrive.label(cue), HandsOnDrive.label(cue).length <= 6)
    }

    @Test fun aReactionIsAPlainPhraseAndTurnsComeInPairs() {
        for (theme in themes) {
            assertNotNull("$theme start", ThemeReactions.of(theme, Cue.START))
            assertNotNull("$theme arrive", ThemeReactions.of(theme, Cue.ARRIVE))
            assertEquals("$theme turns", ThemeReactions.of(theme, Cue.TURN_LEFT) != null, ThemeReactions.of(theme, Cue.TURN_RIGHT) != null)
            for (cue in ThemeReactions.cues(theme)) {
                val r = ThemeReactions.of(theme, cue)!!
                assertTrue("$theme $cue", r.isNotBlank() && "→" !in r)
                assertEquals("${cue.action} → $r", ThemeReactions.caption(theme, cue))
            }
        }
        // A theme without a reaction says nothing instead of guessing.
        assertNull(ThemeReactions.caption(SceneTheme.PLASMA, Cue.LIGHTS))
        assertNull(DemoCatalog.lesson(SceneTheme.PLASMA, Lesson.MODES))
    }

    @Test fun aLessonPlaysOnlyWhatTheThemeReactsToAndEndsParked() {
        for (theme in themes) for (lesson in Lesson.entries) {
            val s = DemoCatalog.lesson(theme, lesson) ?: continue
            val name = s.id
            assertTrue("$name sorted", s.keyframes.zipWithNext().all { (a, b) -> a.t <= b.t })
            assertTrue("$name lasts ${s.durationS}s", s.durationS in 20f..150f)
            val cues = lesson.cues.filter { ThemeReactions.of(theme, it) != null }
            assertEquals("$name captions", cues.map { ThemeReactions.caption(theme, it) }, captions(s).map { it.second })
            // Captions do not run into each other: each one has at least five seconds.
            assertTrue("$name caption gaps", captions(s).zipWithNext().all { (a, b) -> b.first - a.first >= 5f })
            val end = ScenarioSampler(s).sample(s.durationS, PARKED_CAR, 0L, 0L)
            assertEquals(name, GearState.PARK, end.gear)
            assertEquals(name, 0f, end.speedKmh ?: -1f, 0.01f)
            assertTrue("$name leaves nothing on", end.brakePressed == false && end.wiperActive == false && end.headlightsOn == false && end.defrostOn == false &&
                end.acOn == false && end.cruiseOn == false && end.turnSignal == TurnSignal.NONE && end.drivingMode == DriveModeState.NORMAL)
            assertEquals(name, 22f, end.setTempC ?: 0f, 0.01f)
        }
    }

    /** What the car's own detectors make of a scripted drive. */
    private class Drive(val harshBrakes: Int, val harshAccels: Int, val firstStarAt: Float?)

    private fun drive(s: Scenario): Drive {
        val sampler = ScenarioSampler(s)
        val processor = SignalProcessor(); val detector = DrivingEventDetector(); val moving = MovingDetector(); val director = CharacterDirector()
        var brakes = 0; var accels = 0; var star: Float? = null
        val start = 1_000_000L
        var t = start
        while (t <= start + ((s.durationS + 3f) * 1000).toLong()) {
            val raw = sampler.sample((t - start) / 1000f, PARKED_CAR, t, start)
            val p = processor.onSpeedSample(t, raw.speedKmh ?: 0f)
            val snap = raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2)
            detector.update(t, p.accelMps2, snap.brakePressed).forEach {
                if (it == DrivingEvent.HARSH_BRAKE) brakes++
                if (it == DrivingEvent.HARSH_ACCEL) accels++
                director.onDrivingEvent(it, t)
            }
            val target = director.update(snap, moving.update(snap.speedKmh, snap.gear, false, t), userCorner = false, wardrobe = emptySet(), nowMs = t)
            if (star == null && target.journey.stars > 0) star = (t - start) / 1000f
            t += 100
        }
        return Drive(brakes, accels, star)
    }

    @Test fun aLessonDrivesSmoothlyExceptForItsHarshStop() {
        for (theme in themes) for (lesson in Lesson.entries) {
            val s = DemoCatalog.lesson(theme, lesson) ?: continue
            val d = drive(s)
            val harsh = if (lesson.cues.contains(Cue.HARSH) && ThemeReactions.of(theme, Cue.HARSH) != null) 1 else 0
            assertEquals("${s.id} harsh brakes", harsh, d.harshBrakes)
            assertEquals("${s.id} harsh accelerations", 0, d.harshAccels)
            // "부드럽게 달리기": the star arrives while its caption is on screen.
            val starAt = captions(s).firstOrNull { it.second.startsWith(Cue.STAR.action) }?.first
            if (starAt != null) {
                assertNotNull("${s.id} earns a star", d.firstStarAt)
                assertTrue("${s.id} star at ${d.firstStarAt}s, caption at ${starAt}s", d.firstStarAt!! in (starAt - 1f)..(starAt + 8f))
            }
        }
    }

    // ------------------------------------------------------------------ hands-on

    private class Car {
        var snapshot: VehicleSnapshot = PARKED_CAR
        val drive = HandsOnDrive { change -> snapshot = change(snapshot) }
        fun run(seconds: Float) { repeat((seconds * 10).toInt()) { drive.tick(0.1f) } }
    }

    @Test fun handsOnOffersOnlyWhatTheThemeReactsTo() {
        for (theme in themes) {
            val car = Car(); car.drive.start(theme)
            assertEquals("$theme", HandsOnDrive.CONTROLS.filter { ThemeReactions.of(theme, it) != null }, car.drive.controls)
            assertTrue("$theme has controls", car.drive.controls.size >= 4)
            assertEquals(ThemeReactions.caption(theme, Cue.START), car.drive.caption.value)
            // A control the theme has no reaction for does nothing.
            (HandsOnDrive.CONTROLS - car.drive.controls.toSet()).firstOrNull()?.let { missing ->
                val before = car.snapshot
                car.drive.press(missing)
                assertEquals(before, car.snapshot)
            }
        }
    }

    @Test fun handsOnDrivesTheSimulatedCarSmoothly() {
        val car = Car(); car.drive.start(SceneTheme.KART)
        assertEquals(GearState.DRIVE, car.snapshot.gear)
        car.run(8f)
        assertEquals(HandsOnDrive.CRUISE_KMH, car.snapshot.speedKmh!!, 0.01f)
        // Faster: 30 km/h more, never quicker than 8 km/h a second (no harsh acceleration).
        car.drive.press(Cue.ACCEL)
        assertEquals(ThemeReactions.caption(SceneTheme.KART, Cue.ACCEL), car.drive.caption.value)
        var last = car.snapshot.speedKmh!!
        repeat(60) { car.drive.tick(0.1f); assertTrue(car.snapshot.speedKmh!! - last <= 0.81f); last = car.snapshot.speedKmh!! }
        assertEquals(70f, car.snapshot.speedKmh!!, 0.01f)
        assertFalse(Cue.ACCEL in car.drive.on.value)
        // A harsh stop: the pedal is down while the speed falls fast, and released after.
        car.drive.press(Cue.HARSH)
        car.drive.tick(0.1f)
        assertTrue(car.snapshot.brakePressed == true && car.snapshot.speedKmh!! < 68f)
        car.run(4f)
        assertEquals(25f, car.snapshot.speedKmh!!, 0.01f)
        assertTrue(car.snapshot.brakePressed == false)
        // Stopping holds for a moment, then the car sets off again by itself.
        car.drive.press(Cue.STOP)
        car.run(5f)
        assertEquals(0f, car.snapshot.speedKmh!!, 0.01f)
        car.run(HandsOnDrive.STOP_HOLD_S + 6f)
        assertEquals(HandsOnDrive.CRUISE_KMH, car.snapshot.speedKmh!!, 0.01f)
        // An old caption leaves.
        assertNull(car.drive.caption.value)
    }

    @Test fun handsOnTogglesAndReleases() {
        val car = Car(); car.drive.start(SceneTheme.CALIBRE)
        car.drive.press(Cue.TURN_LEFT)
        assertEquals(TurnSignal.LEFT, car.snapshot.turnSignal)
        car.drive.press(Cue.TURN_RIGHT)
        assertEquals(TurnSignal.RIGHT, car.snapshot.turnSignal)
        assertEquals(setOf(Cue.TURN_RIGHT), car.drive.on.value)
        car.run(HandsOnDrive.BLINK_S + 0.5f)
        assertEquals(TurnSignal.NONE, car.snapshot.turnSignal)
        // Cooling and warming exclude each other; pressing one again switches it off.
        car.drive.press(Cue.COOL)
        assertTrue(car.snapshot.acOn == true && car.snapshot.setTempC == 18f)
        car.drive.press(Cue.WARM)
        assertTrue(car.snapshot.acOn == false && car.snapshot.setTempC == 27f)
        assertEquals(ThemeReactions.caption(SceneTheme.CALIBRE, Cue.WARM), car.drive.caption.value)
        car.drive.press(Cue.WARM)
        assertEquals(22f, car.snapshot.setTempC)
        car.drive.press(Cue.WIPER); car.drive.press(Cue.LIGHTS); car.drive.press(Cue.SPORT)
        assertTrue(car.snapshot.wiperActive == true && car.snapshot.headlightsOn == true && car.snapshot.drivingMode == DriveModeState.SPORT)
        car.drive.press(Cue.ECO)
        assertEquals(DriveModeState.ECO, car.snapshot.drivingMode)
        // Ending parks the car with everything off.
        car.drive.stop()
        assertFalse(car.drive.running.value)
        assertEquals(PARKED_CAR, car.snapshot)
        assertTrue(car.drive.on.value.isEmpty())
    }
}
