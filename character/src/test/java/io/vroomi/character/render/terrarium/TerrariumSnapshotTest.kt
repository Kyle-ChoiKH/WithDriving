package io.vroomi.character.render.terrarium

import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.Season
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.render.theme.KitShots.parked
import io.vroomi.character.terrarium.TerrariumSim
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

/** Renders the glass-jar garden (docs/screenshots/terrarium_*). */
class TerrariumSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private val grown: (TerrariumSim) -> Unit = { it.restore("g=26,40,18,15,13,18;b=2;u=2;r=2;s=1") }
    private val young: (TerrariumSim) -> Unit = { it.restore("g=8,12,6,5,5,4;b=0;u=1;r=0;s=0") }
    private fun o(season: Season, hour: Float = 14f, calm: Boolean = false) = SceneOptions(hourOfDay = hour, season = season, calm = calm)
    private fun trip(t: Float, kmh: Float) = Journey(active = true, tripSeq = 3, tripKm = kmh * t / 3600f, stars = 3, starSeq = 3)
    private fun shot(name: String, s: Float, opt: SceneOptions, prepare: (TerrariumSim) -> Unit = grown, at: (Float) -> CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.TERRARIUM, name, s, opt, prepare, at = at)

    // ---- the upgrade ladder (spec 5.5)
    private fun lv(name: String, model: Int, opt: SceneOptions = o(Season.SPRING), block: io.vroomi.character.CarSignals.() -> io.vroomi.character.CarSignals = { this }) =
        shot(name, 8f, opt.copy(model = model)) { t -> driving(car(50f) { copy(fanLevel = 3).block() }, trip(t, 50f)) }
    @Test fun lv1Day() = lv("terrarium_lv1_day", 0)
    @Test fun lv2Day() = lv("terrarium_lv2_day", 1)
    @Test fun lv3Day() = lv("terrarium_lv3_day", 2)
    @Test fun lv4Day() = lv("terrarium_lv4_day", 3)
    @Test fun lv5Day() = lv("terrarium_lv5_day", 4)
    @Test fun lv5Night() = lv("terrarium_lv5_night", 4, o(Season.SUMMER, 21.5f)) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("terrarium_lv3_blinker_left", 2) { copy(turnSignal = TurnSignal.LEFT) }
    @Test fun lv3Brake() = shot("terrarium_lv3_brake", 8.4f, o(Season.SPRING).copy(model = 2)) { t ->
        driving(car(if (t < 8f) 60f else 30f, if (t < 8f) 0f else -6f) { copy(brakePressed = t >= 8f, harshBrakeSeq = if (t >= 8f) 1 else 0) }, trip(t, 60f))
    }

    @Test fun springCruise() = shot("terrarium_spring_cruise", 8f, o(Season.SPRING), young) { t ->
        driving(car(50f) { copy(fanLevel = 3, turnSignal = if (t > 1f && t < 6f) TurnSignal.RIGHT else TurnSignal.NONE) }, trip(t, 50f))
    }
    @Test fun summerRainInside() = shot("terrarium_summer_rain_inside", 12f, o(Season.SUMMER)) { t -> driving(car(40f) { copy(wiperActive = true) }, trip(t, 40f)) }
    @Test fun autumnNightFireflies() = shot("terrarium_autumn_night_fireflies", 6f, o(Season.AUTUMN, 21.5f)) { t -> driving(car(45f) { copy(headlightsOn = true) }, trip(t, 45f)) }
    @Test fun winterFrost() = shot("terrarium_winter_frost", 6f, o(Season.WINTER)) { t -> driving(car(40f) { copy(setTempC = 17f) }, trip(t, 40f)) }
    @Test fun bloomCombo() = shot("terrarium_bloom_combo", 3f, o(Season.SPRING)) { t ->
        driving(car(45f) { copy(drivingMode = DriveModeState.ECO) }, trip(t, 45f).copy(comboSeq = if (t > 1.2f) 1 else 0))
    }
    @Test fun defrostClear() = shot("terrarium_defrost_clear", 21.4f, o(Season.AUTUMN)) { t -> driving(car(40f) { copy(acOn = true, defrostOn = t > 20f) }, trip(t, 40f)) }
    @Test fun harshSlosh() = shot("terrarium_harsh_slosh", 6.25f, o(Season.SUMMER)) { t ->
        driving(car(if (t < 6f) 60f else 30f, if (t < 6f) 0f else -6f) { copy(brakePressed = t >= 6f, harshBrakeSeq = if (t >= 6f) 1 else 0) }, trip(t, 60f))
    }
    @Test fun accelTilt() = shot("terrarium_accel_lean", 4f, o(Season.SUMMER)) { t -> driving(car(30f + t * 8f, 2.2f) { copy(drivingMode = DriveModeState.SPORT) }, trip(t, 40f)) }
    @Test fun chargingLamp() = shot("terrarium_charging_lamp", 8f, o(Season.WINTER, 19f)) {
        parked(car(0f, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = 72) })
    }
    @Test fun arrivalEvening() = shot("terrarium_arrival_moth", 6f, o(Season.SUMMER, 17f)) { t ->
        parked(car(0f, gear = GearState.PARK) { copy(moving = false, arrivalSeq = if (t > 0.5f) 1 else 0) })
    }
    @Test fun corner() = shot("terrarium_corner", 4f, o(Season.SUMMER)) { t -> driving(car(50f), trip(t, 50f)).copy(sizeMode = SizeMode.CORNER) }
    @Test fun calm() = shot("terrarium_calm", 4f, o(Season.SUMMER, calm = true)) { t -> driving(car(50f) { copy(fanLevel = 8) }, trip(t, 50f)) }
    @Test fun safetyDim() = shot("terrarium_safety_dim", 5f, o(Season.SUMMER)) { t -> driving(car(50f) { copy(safetyWarningActive = t > 3f) }, trip(t, 50f)) }
    @Test fun introFocusPull() = shot("terrarium_intro", 3f, o(Season.SPRING).copy(intro = 0.3f)) { t -> driving(car(50f), trip(t, 50f)) }
    /** Moving: the scene has no text at all (nothing to fade). */
    @Test fun moving() = shot("terrarium_moving", 6f, o(Season.SUMMER)) { t -> driving(car(60f), trip(t, 60f)) }
}

/** 1000x1000 details: the glass with condensation, and a fern up close. */
class TerrariumDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    @Test fun glass() = KitShots.script<TerrariumSim>(paparazzi, SceneTheme.TERRARIUM, "terrarium_detail_glass", 18f, SceneOptions(hourOfDay = 14f, season = Season.WINTER),
        prepare = { it.restore("g=26,40,18,15,13,18;b=2;u=2;r=2;s=1") }, zoom = 1.6f, fx = 0.42f, fy = 0.35f) { driving(car(40f) { copy(acOn = true) }) }

    @Test fun fern() = KitShots.script<TerrariumSim>(paparazzi, SceneTheme.TERRARIUM, "terrarium_detail_fern", 4f, SceneOptions(hourOfDay = 14f, season = Season.SUMMER),
        prepare = { it.restore("g=40,40,18,15,20,18;b=3;u=1;r=3;s=1") }, zoom = 2.4f, fx = 0.36f, fy = 0.42f) { parked() }

    private fun step(name: String, model: Int, zoom: Float, fx: Float, fy: Float) = KitShots.script<TerrariumSim>(paparazzi, SceneTheme.TERRARIUM, name, 8f,
        SceneOptions(hourOfDay = 14f, season = Season.SPRING, model = model), prepare = { it.restore("g=26,40,18,15,13,18;b=2;u=2;r=2;s=1") }, zoom = zoom, fx = fx, fy = fy) { driving(car(40f) { copy(fanLevel = 3) }) }
    @Test fun cork() = step("terrarium_detail_cork_lv2", 1, 1.7f, 0.5f, 0.22f)
    @Test fun streamFlask() = step("terrarium_detail_stream_lv3", 2, 2.2f, 0.48f, 0.38f)
    @Test fun bridgeBox() = step("terrarium_detail_bridge_lv4", 3, 2.0f, 0.56f, 0.38f)
    @Test fun wardian() = step("terrarium_detail_case_lv5", 4, 1.5f, 0.5f, 0.31f)
}

/** The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), labelled "Lv.n" top left. */
class TerrariumLadderSnapshotTest {
    @get:Rule val paparazzi = app.cash.paparazzi.Paparazzi(
        deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = com.android.resources.Density.LOW, orientation = com.android.resources.ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @Test fun ladder() {
        val runtimes = (0 until 5).map { i ->
            requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.TERRARIUM)).also { rt ->
                (rt.sim as TerrariumSim).restore("g=26,40,18,15,13,18;b=2;u=2;r=2;s=1")
                rt.simulate(8f, SceneOptions(hourOfDay = 14f, season = Season.SPRING, model = i), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                    driving(car(50f) { copy(fanLevel = 3) }, Journey(active = true, tripSeq = 3, tripKm = 50f * t / 3600f, stars = 3, starSeq = 3))
                }
            }
        }
        paparazzi.snapshot("terrarium_ladder") {
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
class TerraStepTest {
    @Test fun stepsAreCumulative() {
        val has = (0 until 5).map { m -> listOf(TerraStep.planted(m), TerraStep.stream(m), TerraStep.bridge(m), TerraStep.vines(m)).count { it } }
        org.junit.Assert.assertEquals(listOf(0, 1, 2, 3, 4), has)
    }

    @Test fun everyVesselHoldsItsGarden() {
        for (kind in 0 until 5) {
            val v = Vessel(kind, 800f)
            org.junit.Assert.assertTrue("vessel $kind", v.lipY < v.neckY + 0.5f && v.neckY < v.shoulder + 0.5f && v.shoulder < v.ground && v.ground < v.base)
            org.junit.Assert.assertTrue("vessel $kind", v.neckHalf <= v.half)
        }
    }
}
