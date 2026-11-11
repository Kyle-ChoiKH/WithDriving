package io.vroomi.character.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.character.Accessory
import io.vroomi.character.Action
import io.vroomi.character.Biome
import io.vroomi.character.Blinker
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Gait
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.KartStyle
import io.vroomi.character.Mood
import io.vroomi.character.Overlays
import io.vroomi.character.SizeMode
import org.junit.Rule
import org.junit.Test

/**
 * Renders key moments of the scene to PNG on the JVM (no emulator needed).
 * build.ps1 -Task ":character:recordPaparazziDebug" writes them to character/src/test/snapshots/images.
 */
class SceneSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private val autumnDay = SceneOptions(hourOfDay = 14f, season = Season.AUTUMN)
    private val t0 = 1_000_000L

    private fun drive(
        kmh: Float = 45f, gait: Gait = Gait.JOG, biome: Biome = Biome.CITY, action: Action = Action.NONE,
        kart: KartState = KartState(driving = true), mood: Mood = Mood.NEUTRAL, overlays: Overlays = Overlays(),
        acc: Set<Accessory> = emptySet(), journey: Journey = Journey(stars = 3, starSeq = 3, combo = 3),
        size: SizeMode = SizeMode.AMBIENT, actionAt: Long = t0,
    ) = CharacterTarget(
        gait = gait, gaitSpeedKmh = kmh, action = action, actionStartedMs = actionAt, mood = mood,
        overlays = overlays.copy(speedLines = overlays.speedLines || kmh >= 60f, footGlow = kmh / 110f),
        accessories = acc, sizeMode = size, kart = kart, journey = journey.copy(biome = biome),
    )

    /** Simulates [seconds] at 60 fps; [at] lets a scene change over time (seconds -> target). */
    private fun shot(
        name: String, o: SceneOptions = autumnDay, seconds: Float = 3f,
        late: (SceneRuntime) -> Unit = {}, at: (Float) -> CharacterTarget,
    ) {
        val rt = SceneRuntime(seed = 3)
        val frames = (seconds * 60).toInt()
        var target = at(0f)
        for (i in 0 until frames) {
            val ts = i / 60f
            target = at(ts)
            if (i == frames - 60) late(rt)
            rt.step(1f / 60f, target, o, t0 + (ts * 1000).toLong())
        }
        val finalTarget = target
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            val cache = remember { TextCache() }
            Canvas(Modifier.fillMaxSize()) { rt.draw(this, finalTarget, o, tm, cache) }
        }
    }

    // ---- the upgrade ladder (spec 5.6): the new roads and the parade roadster
    private fun day(model: Int, hour: Float = 14f) = SceneOptions(hourOfDay = hour, season = Season.SUMMER, model = model)
    @Test fun kartLv1Day() = shot("kart_lv1_day", o = day(0), at = { drive(biome = Biome.PARK) })
    @Test fun kartBlossom() = shot("kart_blossom", o = day(1), at = { drive(biome = Biome.PARK) })
    @Test fun kartLv2Day() = shot("kart_lv2_day", o = day(1), at = { drive(biome = Biome.PARK) })
    @Test fun kartNeonNight() = shot("kart_neon_night", o = day(2, 21f), at = { drive(biome = Biome.CITY, kart = KartState(driving = true, headlights = true)) })
    @Test fun kartLv3Day() = shot("kart_lv3_day", o = day(2), at = { drive(biome = Biome.CITY) })
    @Test fun kartAurora() = shot("kart_aurora", o = day(3, 21f), at = { drive(biome = Biome.MOUNTAIN, kart = KartState(driving = true, headlights = true)) })
    @Test fun kartLv4Day() = shot("kart_lv4_day", o = day(3), at = { drive(biome = Biome.MOUNTAIN) })
    @Test fun kartLv5Roadster() = shot("kart_lv5_roadster", o = day(4), at = { drive(biome = Biome.SEASIDE) })
    @Test fun kartLv5Day() = shot("kart_lv5_day", o = day(4), at = { drive(biome = Biome.PARK) })
    @Test fun kartLv5Night() = shot("kart_lv5_night", o = day(4, 21f), at = { drive(biome = Biome.CITY, kart = KartState(driving = true, headlights = true)) })
    @Test fun kartLv3BlinkerLeft() = shot("kart_lv3_blinker_left", o = day(2), at = { drive(biome = Biome.CITY, kart = KartState(driving = true, blinker = io.vroomi.character.Blinker.LEFT)) })
    @Test fun kartLv3Brake() = shot("kart_lv3_brake", o = day(2), at = { drive(biome = Biome.CITY, kart = KartState(driving = true, brake = true)) })
    /** The second lap brings the classic road back: step 4 on the mountain road, one whole rotation later. */
    @Test fun kartLv4SecondLapClassic() = shot("kart_lv4_second_lap_classic", o = day(3), at = { drive(biome = Biome.MOUNTAIN, journey = Journey(stars = 3, starSeq = 3, combo = 3, tripKm = 7.4f)) })

    @Test fun parkedHello() = shot("01_parked_hello", at = {
        CharacterTarget(action = Action.WAVE_HELLO, actionStartedMs = t0, sizeMode = SizeMode.FULL)
    })

    @Test fun driveCityHum() = shot("02_drive_city_hum", late = { it.start(Fidget.HUM) }, at = { drive() })

    @Test fun brake() = shot("03_brake", at = { ts ->
        if (ts < 2.5f) drive() else drive(kmh = 30f, action = Action.SKID, actionAt = t0 + 2500, kart = KartState(driving = true, brake = true, accel = -2.6f))
    })

    @Test fun leftTurn() = shot("04_left_turn", at = {
        drive(kmh = 25f, action = Action.POINT_LEFT, kart = KartState(driving = true, blinker = Blinker.LEFT))
    })

    @Test fun fanCold() = shot("05_fan_cold", at = {
        drive(action = Action.BLOW, mood = Mood.COLD, overlays = Overlays(windLevel = 0.6f, snow = true), acc = setOf(Accessory.SCARF))
    })

    @Test fun rainNight() = shot("06_rain_night", o = SceneOptions(hourOfDay = 21f, season = Season.AUTUMN), at = {
        drive(kmh = 40f, overlays = Overlays(rain = 0.7f, night = true),
            kart = KartState(driving = true, wipers = true, headlights = true),
            acc = setOf(Accessory.RAINCOAT, Accessory.LANTERN, Accessory.MAP_HAT))
    })

    @Test fun highwaySport() = shot("07_highway_sport_seaside", at = {
        drive(kmh = 105f, gait = Gait.SPRINT, biome = Biome.SEASIDE, kart = KartState(driving = true, style = KartStyle.SPORT, accel = 0.9f),
            acc = setOf(Accessory.GOGGLES))
    })

    @Test fun cruiseSunset() = shot("08_cruise_mountain_winter_sunset", o = SceneOptions(hourOfDay = 17.6f, season = Season.WINTER), at = {
        drive(kmh = 90f, gait = Gait.RUN, biome = Biome.MOUNTAIN, kart = KartState(driving = true, cruise = true), acc = setOf(Accessory.SUNGLASSES, Accessory.BLANKET))
    })

    @Test fun starPickup() = shot("09_star_pickup", seconds = 2.6f, at = { ts ->
        val j = if (ts < 1.5f) Journey(stars = 4, starSeq = 4, combo = 4) else Journey(stars = 5, starSeq = 5, combo = 5, comboSeq = 1)
        drive(kmh = 50f, journey = j)
    })

    @Test fun arrival() = shot("10_arrival_celebrate", at = { ts ->
        if (ts < 1.5f) drive(kmh = 12f, gait = Gait.WALK) else drive(kmh = 4f, gait = Gait.WALK, action = Action.CELEBRATE, actionAt = t0 + 1500, mood = Mood.HAPPY)
    })

    @Test fun charging() = shot("11_parked_charging", at = {
        CharacterTarget(action = Action.EAT_ENERGY, actionStartedMs = t0, sizeMode = SizeMode.FULL,
            accessories = setOf(Accessory.CABLE_LASSO), kart = KartState(charging = true, batteryPct = 24))
    })

    @Test fun cornerHazard() = shot("12_corner_hazard", at = {
        drive(kmh = 20f, action = Action.HAZARD_WAVE, size = SizeMode.CORNER, kart = KartState(driving = true, blinker = Blinker.HAZARD))
    })

    @Test fun harshTumble() = shot("13_harsh_brake_dizzy", at = { ts ->
        if (ts < 2.4f) drive(kmh = 50f) else drive(kmh = 20f, action = Action.TUMBLE, actionAt = t0 + 2400, mood = Mood.DIZZY,
            kart = KartState(driving = true, brake = true, accel = -4.5f))
    })

    @Test fun ecoBird() = shot("14_eco_field_bird_spring", o = SceneOptions(hourOfDay = 10f, season = Season.SPRING), seconds = 4f,
        late = { it.start(Fidget.BIRD) }, at = {
            drive(kmh = 55f, biome = Biome.FIELD, kart = KartState(driving = true, style = KartStyle.ECO), acc = setOf(Accessory.LEAF))
        })

    @Test fun lookBack() = shot("15_park_lookback", late = { it.start(Fidget.LOOK_BACK) }, at = {
        drive(kmh = 35f, biome = Biome.PARK)
    })

    @Test fun hopIn() = shot("16_hop_in", seconds = 2.4f, at = { ts ->
        if (ts < 2.0f) CharacterTarget(sizeMode = SizeMode.FULL) else drive(kmh = 4f, gait = Gait.WALK)
    })

    @Test fun nightNap() = shot("17_parked_night_nap", o = SceneOptions(hourOfDay = 23f, season = Season.AUTUMN), at = {
        CharacterTarget(action = Action.NAP, actionStartedMs = t0, mood = Mood.SLEEPY, sizeMode = SizeMode.FULL, overlays = Overlays(night = true))
    })

    @Test fun comboSpin() = shot("18_combo_spin", seconds = 2.2f, at = { ts ->
        if (ts < 1.4f) drive(kmh = 45f, journey = Journey(stars = 9, starSeq = 9, combo = 9))
        else drive(kmh = 45f, action = Action.SPIN, actionAt = t0 + 1400, mood = Mood.HAPPY, journey = Journey(stars = 10, starSeq = 10, combo = 10, comboSeq = 2))
    })

    @Test fun kmSign() = shot("19_km_sign_defrost", seconds = 2.4f, at = { ts ->
        val j = if (ts < 1.2f) Journey(milestoneKm = 2) else Journey(milestoneKm = 3, tripKm = 3f)
        drive(kmh = 60f, gait = Gait.RUN, journey = j, action = Action.DEFROST_WIPE, actionAt = t0 + 1200, kart = KartState(driving = true, defrost = true))
    })
}

/** The ladder's rules: each step adds one road, and the new faces take every other lap. */
class KartStepTest {
    @org.junit.Test fun biomeCycleFollowsStep() {
        org.junit.Assert.assertEquals(listOf(5, 6, 7, 8, 8), (0 until 5).map { KartStep.roads(it) })
        org.junit.Assert.assertEquals(listOf(false, false, false, false, true), (0 until 5).map { KartStep.roadster(it) })
    }

    @org.junit.Test fun newFacesTakeEveryOtherLap() {
        // Step 1 never shows a new face.
        for (b in Biome.entries) org.junit.Assert.assertFalse(KartStep.variant(0, b, 0.5f))
        // Step 4: the tree-lined road, the city and the mountain road show theirs on the first lap (4 km), not on the second.
        for (b in listOf(Biome.PARK, Biome.CITY, Biome.MOUNTAIN)) {
            org.junit.Assert.assertTrue(KartStep.variant(3, b, 1.0f))
            org.junit.Assert.assertFalse(KartStep.variant(3, b, 5.0f))
            org.junit.Assert.assertTrue(KartStep.variant(3, b, 9.0f))
        }
        org.junit.Assert.assertFalse(KartStep.variant(4, Biome.FIELD, 1.0f)); org.junit.Assert.assertFalse(KartStep.variant(4, Biome.SEASIDE, 1.0f))
        // Step 2 has the blossom road only.
        org.junit.Assert.assertTrue(KartStep.variant(1, Biome.PARK, 1.0f)); org.junit.Assert.assertFalse(KartStep.variant(1, Biome.CITY, 1.0f))
    }

    /** A trip (and the parked scene) begins on the road the chosen step added, so the five steps differ at once. */
    @org.junit.Test fun chosenStepShowsAtTheStart() {
        val start = (0 until 5).map { KartStep.face(it, Biome.CITY, 0f) }
        org.junit.Assert.assertEquals(listOf(null, Biome.PARK, Biome.CITY, Biome.MOUNTAIN, null), start)
        org.junit.Assert.assertEquals(5, (0 until 5).map { start[it] to KartStep.roadster(it) }.distinct().size)
        // The neon street and the aurora bring their own night; the blossom road and the plain roads keep the hour.
        org.junit.Assert.assertTrue(KartStep.dusk(Biome.CITY) > 0.7f && KartStep.dusk(Biome.MOUNTAIN) > 0.7f)
        org.junit.Assert.assertEquals(0f, KartStep.dusk(Biome.PARK), 0f); org.junit.Assert.assertEquals(0f, KartStep.dusk(null), 0f)
    }
}
