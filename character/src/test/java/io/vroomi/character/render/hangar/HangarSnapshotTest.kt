package io.vroomi.character.render.hangar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.character.CarSignals
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.hangar.HangarSim
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.render.theme.KitShots.parked
import io.vroomi.character.render.theme.ThemeCatalog
import io.vroomi.character.render.theme.simulate
import io.vroomi.character.theme.ThemeModels
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import org.junit.Test

/** Renders the hangar theme (docs/screenshots/hangar_*). */
class HangarSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private fun trip(t: Float, kmh: Float, stars: Int = 3, km: Int = 0) =
        Journey(active = true, tripSeq = 2, tripKm = 0.3f + kmh * t / 3600f, stars = stars, starSeq = stars, milestoneKm = km)

    private fun shot(name: String, s: Float, o: SceneOptions = KitShots.day, state: String = "p=1.0;u=1", at: (Float) -> io.vroomi.character.CharacterTarget) =
        KitShots.script<HangarSim>(paparazzi, SceneTheme.HANGAR, name, s, o, prepare = { it.restore(state) }, at = at)

    private fun lv(name: String, model: Int, o: SceneOptions = KitShots.day, state: String = "p=1.0;u=1", block: CarSignals.() -> CarSignals = { this }) =
        shot(name, 8f, o.copy(model = model), state) { t -> driving(car(50f, 0.4f) { copy(fanLevel = 3).block() }, trip(t, 50f)) }

    // ---- the upgrade ladder (spec 5.12): the finished robot of each step
    @Test fun lv1Day() = lv("hangar_lv1_day", 0)
    @Test fun lv2Day() = lv("hangar_lv2_day", 1)
    @Test fun lv3Day() = lv("hangar_lv3_day", 2)
    @Test fun lv4Day() = lv("hangar_lv4_day", 3)
    @Test fun lv5Day() = lv("hangar_lv5_day", 4)
    @Test fun lv5Night() = lv("hangar_lv5_night", 4, KitShots.night) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("hangar_lv3_blinker_left", 2) { copy(turnSignal = TurnSignal.LEFT) }
    @Test fun lv3Brake() = shot("hangar_lv3_brake", 8f, KitShots.day.copy(model = 2)) { t ->
        driving(car(if (t < 6.5f) 60f else 30f, if (t < 6.5f) 0f else -6f) { copy(brakePressed = t >= 6.5f, harshBrakeSeq = if (t >= 6.5f) 1 else 0) }, trip(t, 55f))
    }

    // ---- the assembly, in order
    @Test fun stageFrame() = lv("hangar_stage_frame", 0, state = "p=0.14;u=1")
    @Test fun stageMachinery() = lv("hangar_stage_machinery", 0, state = "p=0.40;u=1")
    @Test fun stageArmor() = lv("hangar_stage_armor", 0, state = "p=0.60;u=1")
    @Test fun stagePaint() = lv("hangar_stage_paint", 2, state = "p=0.80;u=1")
    @Test fun stageMarkings() = lv("hangar_stage_markings", 2, state = "p=0.95;u=1")
    @Test fun complete() = lv("hangar_complete", 2, state = "p=1.0;u=2")

    // ---- the car's signals
    @Test fun starCrane() = shot("hangar_star_crane", 8f, KitShots.day.copy(model = 1), "p=0.6;u=1") { t -> driving(car(50f), trip(t, 50f, if (t > 5.8f) 4 else 3)) }
    @Test fun kmPlate() = shot("hangar_km_plate", 8f, KitShots.day.copy(model = 1), "p=0.6;u=1") { t -> driving(car(50f), trip(t, 50f, km = if (t > 5.5f) 1 else 0)) }
    @Test fun rainWet() = shot("hangar_rain_wet", 10f, KitShots.day.copy(model = 3)) { t -> driving(car(40f) { copy(wiperActive = true) }, trip(t, 40f)) }
    @Test fun accelSteam() = shot("hangar_accel_steam", 6f, KitShots.day.copy(model = 4)) { t -> driving(car(60f + t * 8f, 2.4f), trip(t, 80f)) }
    @Test fun arrivalWave() = shot("hangar_arrival_wave", 6f, KitShots.day.copy(model = 4)) { t ->
        if (t < 4f) driving(car(20f), trip(t, 20f)) else parked(car(0f, gear = io.vroomi.core.vehicle.model.GearState.PARK).copy(moving = false, arrivalSeq = 1), trip(4f, 20f))
    }
    @Test fun parkedWaiting() = shot("hangar_parked", 5f, KitShots.day.copy(model = 0), "p=0.33;u=1") { parked() }
    @Test fun safetyHold() = shot("hangar_safety_hold", 8f, KitShots.day.copy(model = 4)) { t -> driving(car(50f) { copy(safetyWarningActive = t > 6f) }, trip(t, 50f)) }
    @Test fun corner() = shot("hangar_corner", 6f, KitShots.day.copy(model = 4)) { t -> driving(car(50f), trip(t, 50f)).copy(sizeMode = SizeMode.CORNER) }
}

/** 1000 x 1000 close-ups of the robot. */
class HangarDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    private fun shot(name: String, model: Int, state: String, zoom: Float = 1.9f, fy: Float = 0.2f) =
        KitShots.script<HangarSim>(paparazzi, SceneTheme.HANGAR, name, 8f, SceneOptions(hourOfDay = 14f, model = model), prepare = { it.restore(state) }, zoom = zoom, fx = 0.5f, fy = fy) { t ->
            driving(car(60f, 0.4f) { copy(headlightsOn = true) }, Journey(active = true, tripSeq = 3, tripKm = 0.3f + 60f * t / 3600f, stars = 3, starSeq = 3))
        }

    @Test fun robotLv1() = shot("hangar_detail_lv1", 0, "p=1.0;u=1")
    @Test fun robotLv3() = shot("hangar_detail_lv3", 2, "p=1.0;u=1")
    @Test fun robotLv5() = shot("hangar_detail_lv5", 4, "p=1.0;u=1")
    @Test fun machinery() = shot("hangar_detail_machinery", 0, "p=0.44;u=1")
}

/**
 * The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), each a small copy of
 * the 1920 x 720 driving scene (density 0.75, so the type keeps its proportion), labelled "Lv.n" top left.
 */
class HangarLadderSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = Density.LOW, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun strips(name: String, options: (Int) -> SceneOptions, state: (Int) -> String, label: (Int) -> String) {
        val runtimes = (0 until 5).map { i ->
            requireNotNull(ThemeCatalog.create(SceneTheme.HANGAR)).also { rt ->
                (rt.sim as HangarSim).restore(state(i))
                rt.simulate(8f, options(i), DEFAULT_NOW_MS) { t ->
                    driving(car(50f, 0.4f) { copy(fanLevel = 3) }, Journey(active = true, tripSeq = 2, tripKm = 0.3f + 50f * t / 3600f, stars = 3, starSeq = 3))
                }
            }
        }
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            Column(Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt ->
                    Box(Modifier.fillMaxWidth().aspectRatio(8f / 3f)) {
                        Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm) }
                        BasicText(
                            label(i),
                            Modifier.padding(start = 12.dp, top = 10.dp).background(Color(0xD9101316), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }

    @Test fun ladder() = strips("hangar_ladder", { SceneOptions(hourOfDay = 14f, model = it) }, { "p=1.0;u=1" }) { ThemeModels.tag(it) }

    /** The assembly of one unit (the rescue robot), stage by stage. */
    @Test fun assembly() = strips("hangar_assembly", { SceneOptions(hourOfDay = 14f, model = 2) },
        { "p=${floatArrayOf(0.14f, 0.40f, 0.62f, 0.82f, 1.0f)[it]};u=1" }) { arrayOf("frame", "machinery", "armour", "paint", "complete")[it] }
}
