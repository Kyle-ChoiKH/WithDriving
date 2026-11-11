package io.vroomi.character.render.aquarium

import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.aquarium.AquariumSim
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.render.theme.KitShots.parked
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.character.render.theme.simulate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test

/** Renders the aquarium (docs/screenshots/aquarium_*). */
class AquariumSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private fun trip(t: Float, kmh: Float, stars: Int = 3, combo: Int = 0, km: Int = 0) =
        Journey(active = true, tripSeq = 2, tripKm = kmh * t / 3600f, stars = stars, starSeq = stars, comboSeq = combo, milestoneKm = km)
    private fun shot(name: String, s: Float, o: SceneOptions = KitShots.day, prepare: (AquariumSim) -> Unit = {}, at: (Float) -> CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.AQUARIUM, name, s, o, prepare, at = at)

    @Test fun dayCruise() = shot("aquarium_day_cruise", 12f) { t -> driving(car(60f) { copy(fanLevel = 2) }, trip(t, 60f, km = if (t > 6f) 1 else 0)) }
    @Test fun accelTilt() = shot("aquarium_accel_tilt", 5f) { t -> driving(car(30f + t * 9f, 2.4f) { copy(drivingMode = DriveModeState.SPORT) }, trip(t, 40f)) }
    @Test fun startle() = shot("aquarium_startle", 8.35f) { t ->
        driving(car(if (t < 8f) 60f else 30f, if (t < 8f) 0f else -6f) { copy(brakePressed = t >= 8f, harshBrakeSeq = if (t >= 8f) 1 else 0) }, trip(t, 60f))
    }
    @Test fun turnLeft() = shot("aquarium_turn_left", 9f) { t -> driving(car(50f) { copy(turnSignal = if (t > 2f) TurnSignal.LEFT else TurnSignal.NONE) }, trip(t, 50f)) }
    @Test fun bubblesFanMax() = shot("aquarium_bubbles_fan_max", 6f) { t -> driving(car(45f) { copy(fanLevel = 8, acOn = true, setTempC = 18f) }, trip(t, 45f)) }
    @Test fun rainSurface() = shot("aquarium_rain_surface", 6f) { t -> driving(car(45f) { copy(wiperActive = true) }, trip(t, 45f)) }
    @Test fun nightPlankton() = shot("aquarium_night_plankton", 6f, KitShots.night) { t -> driving(car(45f) { copy(headlightsOn = true) }, trip(t, 45f)) }
    @Test fun pearlStar() = shot("aquarium_pearl_star", 6f) { t -> driving(car(45f) { copy(drivingMode = DriveModeState.ECO) }, trip(t, 45f, stars = if (t > 4f) 4 else 3)) }
    @Test fun schoolRing() = shot("aquarium_school_ring_combo", 9f) { t -> driving(car(45f), trip(t, 45f, combo = if (t > 3f) 1 else 0)) }
    @Test fun arrivalFront() = shot("aquarium_arrival_front", 9f) { t ->
        parked(car(0f, gear = GearState.PARK) { copy(moving = false, arrivalSeq = if (t > 1f) 1 else 0) })
    }
    @Test fun charging() = shot("aquarium_charging_filter", 6f) { parked(car(0f, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = 58) }) }
    @Test fun reverse() = shot("aquarium_reverse", 8f) { t -> driving(car(8f, gear = GearState.REVERSE), trip(t, 8f)) }
    @Test fun corner() = shot("aquarium_corner", 6f) { t -> driving(car(50f), trip(t, 50f)).copy(sizeMode = SizeMode.CORNER) }
    @Test fun calm() = shot("aquarium_calm", 6f, SceneOptions(hourOfDay = 14f, calm = true)) { t -> driving(car(50f) { copy(fanLevel = 8) }, trip(t, 50f)) }
    @Test fun safetyDim() = shot("aquarium_safety_dim", 6f) { t -> driving(car(50f) { copy(safetyWarningActive = t > 4f) }, trip(t, 50f)) }
    @Test fun introSwimIn() = shot("aquarium_intro", 4f, SceneOptions(hourOfDay = 14f, intro = 0.35f)) { t -> driving(car(50f), trip(t, 50f)) }
    // ---- the upgrade ladder (spec 5.4)
    private fun lv(name: String, model: Int, o: SceneOptions = KitShots.day, block: io.vroomi.character.CarSignals.() -> io.vroomi.character.CarSignals = { this }) =
        shot(name, 8f, o.copy(model = model)) { t -> driving(car(50f) { copy(fanLevel = 2).block() }, trip(t, 50f)) }
    @Test fun lv1Day() = lv("aquarium_lv1_day", 0)
    @Test fun lv2Day() = lv("aquarium_lv2_day", 1)
    @Test fun lv3Day() = lv("aquarium_lv3_day", 2)
    @Test fun lv4Day() = lv("aquarium_lv4_day", 3)
    @Test fun lv5Day() = lv("aquarium_lv5_day", 4)
    @Test fun lv5Night() = lv("aquarium_lv5_night", 4, KitShots.night) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("aquarium_lv3_blinker_left", 2) { copy(turnSignal = TurnSignal.LEFT) }
    @Test fun lv3Brake() = shot("aquarium_lv3_brake", 8.35f, KitShots.day.copy(model = 2)) { t ->
        driving(car(if (t < 8f) 60f else 30f, if (t < 8f) 0f else -6f) { copy(brakePressed = t >= 8f, harshBrakeSeq = if (t >= 8f) 1 else 0) }, trip(t, 60f))
    }

    /** Moving: the scene has no text at all (nothing to fade). */
    @Test fun moving() = shot("aquarium_moving", 6f) { t -> driving(car(60f), trip(t, 60f)) }
}

/** 1000x1000 details: one fish of each species, a plant with the caustics. */
class AquariumDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    @Test fun species() = KitShots.script<AquariumSim>(paparazzi, SceneTheme.AQUARIUM, "aquarium_detail_species", 2f,
        prepare = { it.lineup = true }, zoom = 1.6f, fx = 0.5f, fy = 0.36f) { driving(car(30f)) }

    @Test fun plantCaustics() = KitShots.script<AquariumSim>(paparazzi, SceneTheme.AQUARIUM, "aquarium_detail_plant", 6f,
        zoom = 2.4f, fx = 0.12f, fy = 0.5f) { driving(car(40f) { copy(fanLevel = 5) }) }

    private fun step(name: String, model: Int, zoom: Float, fx: Float, fy: Float) = KitShots.script<AquariumSim>(paparazzi, SceneTheme.AQUARIUM, name, 8f,
        SceneOptions(hourOfDay = 14f, model = model), zoom = zoom, fx = fx, fy = fy) { driving(car(40f) { copy(fanLevel = 3) }) }
    @Test fun reef() = step("aquarium_detail_reef_lv3", 2, 2.2f, 0.76f, 0.5f)
    @Test fun wreck() = step("aquarium_detail_wreck_lv4", 3, 2.0f, 0.44f, 0.5f)
    @Test fun turtle() = step("aquarium_detail_turtle_lv5", 4, 2.0f, 0.24f, 0.2f)
    @Test fun shrimp() = step("aquarium_detail_shrimp_lv2", 1, 2.6f, 0.55f, 0.55f)
}

/** The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), labelled "Lv.n" top left. */
class AquariumLadderSnapshotTest {
    @get:Rule val paparazzi = app.cash.paparazzi.Paparazzi(
        deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = com.android.resources.Density.LOW, orientation = com.android.resources.ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @Test fun ladder() {
        val runtimes = (0 until 5).map { i ->
            requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.AQUARIUM)).also { rt ->
                rt.simulate(8f, SceneOptions(hourOfDay = 14f, model = i), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                    driving(car(50f) { copy(fanLevel = 2) }, Journey(active = true, tripSeq = 2, tripKm = 50f * t / 3600f, stars = 3, starSeq = 3))
                }
            }
        }
        paparazzi.snapshot("aquarium_ladder") {
            val tm = androidx.compose.ui.text.rememberTextMeasurer()
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt ->
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().aspectRatio(8f / 3f)) {
                        androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) { rt.draw(this, tm) }
                        androidx.compose.foundation.text.BasicText(
                            io.vroomi.character.theme.ThemeModels.tag(i),
                            androidx.compose.ui.Modifier.padding(start = 12.dp, top = 10.dp).background(androidx.compose.ui.graphics.Color(0xD9101316), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                            style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }
}

/** The ladder's rule: every step keeps what the one before had. */
class AquaStepTest {
    @Test fun stepsAreCumulative() {
        val has = (0 until 5).map { m -> listOf(AquaStep.garden(m), AquaStep.reef(m), AquaStep.wreck(m), AquaStep.kelpForest(m)).count { it } }
        org.junit.Assert.assertEquals(listOf(0, 1, 2, 3, 4), has)
        org.junit.Assert.assertTrue(AquaStep.grassClumps(0) < AquaStep.grassClumps(1))
        org.junit.Assert.assertEquals(AquaStep.GRASS_ALL, AquaStep.grassClumps(4))
    }
}
