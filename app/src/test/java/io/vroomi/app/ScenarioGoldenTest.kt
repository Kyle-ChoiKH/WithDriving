package io.vroomi.app

import io.vroomi.app.diary.TripRecord
import io.vroomi.app.diary.TripRecorder
import io.vroomi.app.runtime.MovingDetector
import io.vroomi.character.Accessory
import io.vroomi.character.Action
import io.vroomi.character.CharacterDirector
import io.vroomi.character.Gait
import io.vroomi.character.Mood
import io.vroomi.character.SizeMode
import io.vroomi.character.VroomiEvent
import io.vroomi.core.vehicle.signal.DrivingEvent
import io.vroomi.core.vehicle.signal.DrivingEventDetector
import io.vroomi.core.vehicle.signal.SignalProcessor
import io.vroomi.core.vehicle.sim.PARKED_CAR
import io.vroomi.core.vehicle.sim.ScenarioParser
import io.vroomi.core.vehicle.sim.ScenarioSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plays every assets/scenarios/\*.json through the same pipeline the app uses
 * (sampler -> SignalProcessor -> DrivingEventDetector -> MovingDetector -> CharacterDirector -> TripRecorder)
 * at 10 Hz of simulated time, checks the expected behaviours, and writes a state-transition log to
 * build/scenario-logs/<id>.txt for review.
 */
class ScenarioGoldenTest {

    private data class Run(
        val gaits: Set<Gait>, val actions: Set<Action>, val moods: Set<Mood>, val sizes: Set<SizeMode>,
        val accessories: Set<Accessory>, val voice: List<VroomiEvent>, val driving: List<DrivingEvent>,
        val trips: List<TripRecord>, val maxChip: Int,
        val maxStars: Int, val maxCombo: Int, val biomes: Int, val maxKm: Int, val kartBrake: Boolean, val kartBlink: Boolean,
        val captions: Int,
    )

    private fun play(id: String): Run {
        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/$id.json").readText())
        val sampler = ScenarioSampler(scenario)
        val processor = SignalProcessor()
        val detector = DrivingEventDetector()
        val moving = MovingDetector()
        val director = CharacterDirector()
        val recorder = TripRecorder()

        val gaits = HashSet<Gait>(); val actions = HashSet<Action>(); val moods = HashSet<Mood>()
        val sizes = HashSet<SizeMode>(); val acc = HashSet<Accessory>()
        val voice = ArrayList<VroomiEvent>(); val driving = ArrayList<DrivingEvent>(); val trips = ArrayList<TripRecord>()
        var maxChip = 0
        var maxStars = 0; var maxCombo = 0; var biomes = 0; var maxKm = 0; var kartBrake = false; var kartBlink = false
        val captions = HashSet<String>()
        val log = StringBuilder()
        var lastKey = ""

        val start = 1_000_000L
        var t = start
        val end = start + ((scenario.durationS + 3f) * 1000).toLong()
        while (t <= end) {
            val raw = sampler.sample((t - start) / 1000f, PARKED_CAR, t, start)
            val p = processor.onSpeedSample(t, raw.speedKmh ?: 0f)
            val s = raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2)
            detector.update(t, p.accelMps2, s.brakePressed).forEach {
                driving += it; director.onDrivingEvent(it, t); recorder.onEvent(it)
            }
            val m = moving.update(s.speedKmh, s.gear, false, t)
            val target = director.update(s, m, userCorner = false, wardrobe = emptySet(), nowMs = t)
            voice += director.drainVoiceEvents()
            recorder.onSnapshot(s, t)?.let { trips += it }

            gaits += target.gait; actions += target.action; moods += target.mood; sizes += target.sizeMode
            acc += target.accessories
            maxChip = maxOf(maxChip, target.statusChip?.length ?: 0)
            val j = target.journey
            maxStars = maxOf(maxStars, j.stars); maxCombo = maxOf(maxCombo, j.comboSeq); biomes = maxOf(biomes, j.biomesSeen)
            maxKm = maxOf(maxKm, j.milestoneKm)
            kartBrake = kartBrake || target.kart.brake; kartBlink = kartBlink || target.kart.blinker != io.vroomi.character.Blinker.NONE
            sampler.captionAt((t - start) / 1000f)?.let { captions += it }
            val key = "${target.gait} ${target.action} ${target.mood} ${target.sizeMode} chip=${target.statusChip} " +
                "stars=${j.stars} ${j.biome} km=${j.milestoneKm}"
            if (key != lastKey) {
                log.append("%7.1fs  v=%5.1f a=%5.2f  %s\n".format((t - start) / 1000f, p.kmh, p.accelMps2, key))
                lastKey = key
            }
            t += 100
        }
        log.append("\nvoice events: $voice\ndriving events: $driving\ntrips: $trips\n")
        File("build/scenario-logs").mkdirs()
        File("build/scenario-logs/$id.txt").writeText(log.toString())
        return Run(gaits, actions, moods, sizes, acc, voice, driving, trips, maxChip, maxStars, maxCombo, biomes, maxKm, kartBrake, kartBlink, captions.size)
    }

    @Test fun commute() {
        val r = play("commute")
        assertTrue(r.gaits.containsAll(listOf(Gait.IDLE, Gait.WALK, Gait.JOG, Gait.RUN)))
        assertTrue("actions=${r.actions}", r.actions.containsAll(listOf(
            Action.WAVE_HELLO, Action.BURST, Action.POINT_LEFT, Action.POINT_RIGHT, Action.BLOW, Action.TUMBLE, Action.WAVE_BYE,
        )))
        assertTrue(Mood.COLD in r.moods && Mood.DIZZY in r.moods)
        assertTrue(SizeMode.AMBIENT in r.sizes && SizeMode.FULL in r.sizes)
        assertTrue("voice=${r.voice}", r.voice.containsAll(listOf(
            VroomiEvent.START, VroomiEvent.DOOR_HELLO, VroomiEvent.SMOOTH_BURST, VroomiEvent.FAN_UP,
            VroomiEvent.TEMP_COLD, VroomiEvent.HARSH_BRAKE, VroomiEvent.DOOR_GOODBYE,
        )))
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertEquals("one trip", 1, r.trips.size)
        val trip = r.trips.first()
        assertTrue("distance=${trip.distanceKm}", trip.distanceKm in 1.5f..4f)
        assertTrue(trip.harshBrakes >= 1 && trip.bursts >= 1)
        assertTrue(trip.score in 70..96)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun highlight() {
        val r = play("highlight")
        assertTrue("actions=${r.actions}", r.actions.containsAll(listOf(
            Action.WAVE_HELLO, Action.BURST, Action.POINT_LEFT, Action.POINT_RIGHT, Action.BLOW, Action.SKID, Action.TUMBLE,
            Action.DEFROST_WIPE, Action.LOOK_AHEAD, Action.CELEBRATE, Action.WAVE_BYE,
        )))
        assertTrue(r.accessories.containsAll(listOf(Accessory.RAINCOAT, Accessory.LANTERN,
            Accessory.GOGGLES, Accessory.SUNGLASSES, Accessory.LEAF, Accessory.MAP_HAT)))
        assertTrue(Mood.COLD in r.moods && Mood.DIZZY in r.moods)
        assertTrue("stars=${r.maxStars}", r.maxStars >= 6)
        assertTrue("combo=${r.maxCombo}", r.maxCombo >= 1)
        assertTrue("biomes=${r.biomes}", r.biomes >= 3)
        assertTrue("km=${r.maxKm}", r.maxKm >= 2)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("captions=${r.captions}", r.captions >= 20)
        assertEquals(1, r.trips.size)
        assertTrue(r.trips.first().harshBrakes >= 1)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun calibreShowcase() {
        val r = play("calibre_showcase")
        assertTrue("actions=${r.actions}", r.actions.containsAll(listOf(Action.BURST, Action.POINT_LEFT, Action.BLOW, Action.TUMBLE, Action.DEFROST_WIPE, Action.CELEBRATE)))
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue("stars=${r.maxStars}", r.maxStars >= 8)
        assertTrue("combo=${r.maxCombo}", r.maxCombo >= 1)
        assertTrue(r.kartBlink)
        assertTrue("captions=${r.captions}", r.captions >= 15)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun musicBoxShowcase() {
        val r = play("music_box_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 15)
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("km=${r.maxKm}", r.maxKm >= 1)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun terrariumShowcase() {
        val r = play("terrarium_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 15)
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("km=${r.maxKm}", r.maxKm >= 1)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun mapShowcase() {
        val r = play("map_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 12)
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("km=${r.maxKm}", r.maxKm >= 1)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun aquariumShowcase() {
        val r = play("aquarium_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 15)
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("km=${r.maxKm}", r.maxKm >= 1)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun orbitShowcase() {
        val r = play("orbit_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 14)
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("km=${r.maxKm}", r.maxKm >= 1)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun paperShowcase() {
        val r = play("paper_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 13)
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("km=${r.maxKm}", r.maxKm >= 1)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun pixelShowcase() {
        val r = play("pixel_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 15)
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("km=${r.maxKm}", r.maxKm >= 1)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun plasmaShowcase() {
        val r = play("plasma_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 8)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("stars=${r.maxStars}", r.maxStars >= 2)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun hangarShowcase() {
        val r = play("hangar_showcase")
        assertTrue("captions=${r.captions}", r.captions >= 8)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("stars=${r.maxStars}", r.maxStars >= 2)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun engineShowcase() {
        val r = play("engine_showcase")
        assertTrue("actions=${r.actions}", r.actions.containsAll(listOf(Action.SKID, Action.POINT_LEFT, Action.BLOW, Action.TUMBLE, Action.DEFROST_WIPE, Action.CELEBRATE)))
        assertTrue(DrivingEvent.HARSH_BRAKE in r.driving)
        assertTrue("stars=${r.maxStars}", r.maxStars >= 3)
        assertTrue(r.kartBrake && r.kartBlink)
        assertTrue("captions=${r.captions}", r.captions >= 15)
        assertEquals(1, r.trips.size)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun edriveShowcase() {
        val r = play("edrive_showcase")
        assertTrue(r.kartBrake)
        assertTrue("captions=${r.captions}", r.captions >= 7)
        assertTrue(Action.EAT_ENERGY in r.actions)
        assertTrue(r.maxChip <= 12)
    }

    @Test fun rainyNight() {
        val r = play("rainy_night")
        assertTrue(r.accessories.containsAll(listOf(Accessory.RAINCOAT, Accessory.LANTERN, Accessory.MAP_HAT)))
        assertTrue("actions=${r.actions}", r.actions.containsAll(listOf(Action.DEFROST_WIPE, Action.LOOK_AHEAD, Action.CELEBRATE)))
        assertTrue(r.voice.containsAll(listOf(VroomiEvent.RAIN, VroomiEvent.ARRIVAL)))
        assertNotNull(r.trips.firstOrNull())
    }

    @Test fun evCharge() {
        val r = play("ev_charge")
        assertTrue(Mood.HUNGRY in r.moods)
        assertTrue(Action.EAT_ENERGY in r.actions)
        assertTrue(Accessory.CABLE_LASSO in r.accessories)
        assertTrue("voice=${r.voice}", r.voice.containsAll(listOf(VroomiEvent.LOW_BATTERY, VroomiEvent.CHARGING, VroomiEvent.DOOR_GOODBYE)))
    }

    @Test fun familyTrip() {
        val r = play("family_trip")
        assertTrue(r.accessories.containsAll(listOf(Accessory.LEAF, Accessory.SUNGLASSES, Accessory.MAP_HAT)))
        assertTrue(Gait.SPRINT in r.gaits)
        assertTrue(r.actions.containsAll(listOf(Action.CELEBRATE, Action.THUMBS_UP)))
        assertTrue(Mood.COLD in r.moods)
        assertEquals(1, r.trips.size)
    }
}
