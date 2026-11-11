package io.vroomi.character.render.orbit

import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.orbit.OrbitSim
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.render.theme.KitShots.parked
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import io.vroomi.character.render.theme.simulate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Test

/** Renders the orbit theme (docs/screenshots/orbit_*). */
class OrbitSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private fun trip(t: Float, kmh: Float, stars: Int = 4, combo: Int = 0) =
        Journey(active = true, tripSeq = 2, tripKm = kmh * t / 3600f, stars = stars, starSeq = stars, comboSeq = combo, milestoneKm = (kmh * t / 3600f).toInt())
    private fun shot(name: String, s: Float, o: SceneOptions = KitShots.day, prepare: (OrbitSim) -> Unit = {}, at: (Float) -> CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.ORBIT, name, s, o, prepare, at = at)

    // ---- the upgrade ladder (spec 5.8)
    private fun lv(name: String, model: Int, o: SceneOptions = KitShots.day, block: io.vroomi.character.CarSignals.() -> io.vroomi.character.CarSignals = { this }) =
        shot(name, 8f, o.copy(model = model)) { t -> driving(car(50f) { copy(fanLevel = 2).block() }, trip(t, 50f)) }
    @Test fun lv1Day() = lv("orbit_lv1_day", 0)
    @Test fun lv2Day() = lv("orbit_lv2_day", 1)
    @Test fun lv3Day() = lv("orbit_lv3_day", 2)
    @Test fun lv4Day() = lv("orbit_lv4_day", 3)
    @Test fun lv5Day() = lv("orbit_lv5_day", 4)
    @Test fun lv5Night() = lv("orbit_lv5_night", 4, KitShots.night) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("orbit_lv3_blinker_left", 2) { copy(turnSignal = io.vroomi.core.vehicle.model.TurnSignal.LEFT) }
    @Test fun lv3Brake() = shot("orbit_lv3_brake", 8.4f, KitShots.day.copy(model = 2)) { t ->
        driving(car(if (t < 8f) 60f else 30f, if (t < 8f) 0f else -6f) { copy(brakePressed = t >= 8f, harshBrakeSeq = if (t >= 8f) 1 else 0) }, trip(t, 60f))
    }
    /** Right after setting off at step 1: the grid, the instrument column and the trail are already there. */
    @Test fun startLv1() = shot("orbit_start_lv1", 1.5f, KitShots.day.copy(model = 0)) { t -> driving(car(20f), trip(t, 20f, stars = 0)) }

    @Test fun cruise() = shot("orbit_cruise", 40f) { t -> driving(car(60f) { copy(evBatteryPct = 72) }, trip(t, 60f, stars = (t / 8f).toInt())) }
    @Test fun accelPlume() = shot("orbit_accel_plume", 12f) { t -> driving(car(30f + t * 4f, 2.2f) { copy(drivingMode = DriveModeState.ECO) }, trip(t, 50f)) }
    @Test fun retroBrake() = shot("orbit_retro_brake", 12f) { t -> driving(car(if (t < 11f) 60f else 40f, if (t < 11f) 0f else -2.4f) { copy(brakePressed = t > 11f) }, trip(t, 60f)) }
    @Test fun rcsLeft() = shot("orbit_rcs_left", 8.1f) { t -> driving(car(50f) { copy(turnSignal = if (t > 2f) TurnSignal.LEFT else TurnSignal.NONE) }, trip(t, 50f)) }
    @Test fun retrograde() = shot("orbit_retrograde_reverse", 30f) { t -> driving(car(8f, gear = GearState.REVERSE), trip(t, 30f)) }
    @Test fun dockedPark() = shot("orbit_docked_park", 20f) { t -> parked(car(0f, gear = GearState.PARK) { copy(moving = false) }, trip(t, 0f)) }
    @Test fun moonletsCombo() = shot("orbit_moonlets_combo", 30f) { t -> driving(car(60f), trip(t, 60f, stars = 4 + (t / 3f).toInt().coerceAtMost(8), combo = if (t > 26f) 1 else 0)) }
    @Test fun meteorRain() = shot("orbit_meteor_rain", 9.2f) { t -> driving(car(50f) { copy(wiperActive = true) }, trip(t, 50f)) }
    @Test fun nightNebula() = shot("orbit_night_nebula", 10f, KitShots.night) { t -> driving(car(55f) { copy(headlightsOn = true, drivingMode = DriveModeState.SPORT) }, trip(t, 55f), ) }
    @Test fun chargingPanels() = shot("orbit_charging_panels", 18f) { t ->
        parked(car(0f, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = 66) }, trip(t, 0f))
    }
    @Test fun solarWind() = shot("orbit_solar_wind_fan", 8f) { t -> driving(car(50f) { copy(fanLevel = 8) }, trip(t, 50f)) }
    @Test fun corner() = shot("orbit_corner", 6f) { t -> driving(car(50f), trip(t, 50f)).copy(sizeMode = SizeMode.CORNER) }
    @Test fun calm() = shot("orbit_calm", 6f, SceneOptions(hourOfDay = 14f, calm = true)) { t -> driving(car(50f), trip(t, 50f)) }
    @Test fun safetyDim() = shot("orbit_safety_dim", 8f) { t -> driving(car(50f) { copy(safetyWarningActive = t > 6f) }, trip(t, 50f, stars = 6)) }
    @Test fun introOrbitsStrokeOn() = shot("orbit_intro", 8f, SceneOptions(hourOfDay = 14f, intro = 0.4f)) { t -> driving(car(50f), trip(t, 50f, stars = 6)) }
    /** Moving: only the lap count stays, at 24 sp or more; the small telemetry has faded. */
    @Test fun moving() = shot("orbit_moving", 90f) { t -> driving(car(60f) { copy(evBatteryPct = 64) }, trip(t, 60f, stars = 5)) }
    /** Parked after the same drive: every telemetry line is back. */
    @Test fun parkedTelemetry() = shot("orbit_parked_telemetry", 92f) { t ->
        if (t < 90f) driving(car(60f) { copy(evBatteryPct = 64) }, trip(t, 60f, stars = 5))
        else parked(car(0f, gear = GearState.PARK) { copy(moving = false, evBatteryPct = 64) }, trip(90f, 60f, stars = 5))
    }
}

/** 1000x1000 details: the craft, the station ring, the planet limb. */
class OrbitDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    @Test fun craft() = KitShots.script<OrbitSim>(paparazzi, SceneTheme.ORBIT, "orbit_detail_craft", 18f, zoom = 3.4f, fx = 0.85f, fy = 0.30f) { t ->
        parked(car(0f, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = 70) })
    }
    @Test fun planet() = KitShots.script<OrbitSim>(paparazzi, SceneTheme.ORBIT, "orbit_detail_planet", 4f, zoom = 2.6f, fx = 0.5f, fy = 0.30f) { driving(car(40f)) }

    private fun step(name: String, model: Int, zoom: Float, fx: Float, fy: Float) = KitShots.script<OrbitSim>(paparazzi, SceneTheme.ORBIT, name, 8f,
        SceneOptions(hourOfDay = 14f, model = model), zoom = zoom, fx = fx, fy = fy) { driving(car(50f)) }
    @Test fun ringsMoon() = step("orbit_detail_rings_lv4", 3, 2.0f, 0.5f, 0.2f)
    @Test fun column() = step("orbit_detail_column_lv1", 0, 2.4f, 0.14f, 0.24f)
    @Test fun stationLv5() = step("orbit_detail_station_lv5", 4, 2.4f, 0.72f, 0.2f)
}

/** The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), labelled "Lv.n" top left. */
class OrbitLadderSnapshotTest {
    @get:Rule val paparazzi = app.cash.paparazzi.Paparazzi(
        deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = com.android.resources.Density.LOW, orientation = com.android.resources.ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @Test fun ladder() {
        val runtimes = (0 until 5).map { i ->
            requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.ORBIT)).also { rt ->
                rt.simulate(8f, SceneOptions(hourOfDay = 14f, model = i), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                    driving(car(50f) { copy(fanLevel = 2) }, io.vroomi.character.Journey(active = true, tripSeq = 2, tripKm = 50f * t / 3600f, stars = 4, starSeq = 4))
                }
            }
        }
        paparazzi.snapshot("orbit_ladder") {
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

/** The ladder's rules: steps add up, and one kilometre is one lap at every step. */
class OrbitStepTest {
    @Test fun stepsAreCumulative() {
        val has = (0 until 5).map { m -> listOf(OrbitStep.moon(m), OrbitStep.duo(m), OrbitStep.rings(m), OrbitStep.station(m)).count { it } }
        org.junit.Assert.assertEquals(listOf(0, 1, 2, 3, 4), has)
    }

    @Test fun orbitPerKmUnchangedAtEveryStep() {
        val angles = (0 until 5).map { i ->
            val rt = requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.ORBIT))
            val start = (rt.sim as OrbitSim).angle
            rt.simulate(60f, SceneOptions(hourOfDay = 14f, model = i), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                driving(car(60f), io.vroomi.character.Journey(active = true, tripSeq = 1, tripKm = 60f * t / 3600f))
            }
            (rt.sim as OrbitSim).angle - start
        }
        for (a in angles) org.junit.Assert.assertEquals(2 * Math.PI, a, 0.08)
    }
}
