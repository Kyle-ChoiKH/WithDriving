package io.vroomi.character.render.plasma

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
import io.vroomi.character.plasma.PlasmaSim
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

/** Renders the plasma theme (docs/screenshots/plasma_*). */
class PlasmaSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    /** 0.72 km into the trip at the end of a 10 s shot (the ladder's arc is well up its rods). */
    private fun trip(t: Float, kmh: Float, stars: Int = 3, combo: Int = 0) =
        Journey(active = true, tripSeq = 2, tripKm = 0.58f + kmh * t / 3600f, stars = stars, starSeq = stars, comboSeq = combo)

    private fun shot(name: String, s: Float, o: SceneOptions = KitShots.day, at: (Float) -> io.vroomi.character.CharacterTarget) =
        KitShots.script<PlasmaSim>(paparazzi, SceneTheme.PLASMA, name, s, o, at = at)

    private fun lv(name: String, model: Int, o: SceneOptions = KitShots.day, kmh: Float = 50f, block: CarSignals.() -> CarSignals = { this }) =
        shot(name, 10f, o.copy(model = model)) { t -> driving(car(kmh, 0.8f) { copy(fanLevel = 4).block() }, trip(t, kmh)) }

    @Test fun lv1Day() = lv("plasma_lv1_day", 0)
    @Test fun lv2Day() = lv("plasma_lv2_day", 1)
    @Test fun lv3Day() = lv("plasma_lv3_day", 2)
    @Test fun lv4Day() = lv("plasma_lv4_day", 3)
    @Test fun lv5Day() = lv("plasma_lv5_day", 4)
    @Test fun lv5Night() = lv("plasma_lv5_night", 4, KitShots.night) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("plasma_lv3_blinker_left", 2) { copy(turnSignal = TurnSignal.LEFT) }
    @Test fun lv3Brake() = shot("plasma_lv3_brake", 10f, KitShots.day.copy(model = 2)) { t ->
        driving(car(if (t < 8.5f) 60f else 60f - (t - 8.5f) * 12f, if (t < 8.5f) 0f else -3f) { copy(brakePressed = t >= 8.5f) }, trip(t, 55f))
    }

    @Test fun parkedFour() = shot("plasma_parked", 5f, KitShots.day.copy(model = 4)) { parked() }
    @Test fun sprintCap() = lv("plasma_sprint_cap", 4, kmh = 120f)
    @Test fun rightBlinker() = lv("plasma_blinker_right", 4) { copy(turnSignal = TurnSignal.RIGHT) }
    @Test fun starPoint() = shot("plasma_star_point", 10f, KitShots.day.copy(model = 4)) { t -> driving(car(50f), trip(t, 50f, if (t > 8.6f) 4 else 3)) }
    @Test fun comboTurn() = shot("plasma_combo_turn", 10f, KitShots.day.copy(model = 4)) { t -> driving(car(50f), trip(t, 50f, combo = if (t > 7f) 1 else 0)) }
    @Test fun rain() = shot("plasma_rain", 12f, KitShots.day.copy(model = 4)) { t -> driving(car(40f) { copy(wiperActive = true) }, trip(t, 40f)) }
    @Test fun safetyHold() = shot("plasma_safety_hold", 10f, KitShots.day.copy(model = 4)) { t -> driving(car(50f) { copy(safetyWarningActive = t > 8f) }, trip(t, 50f)) }
    @Test fun calm() = shot("plasma_calm", 10f, SceneOptions(hourOfDay = 14f, calm = true, model = 4)) { t -> driving(car(50f), trip(t, 50f)) }
    @Test fun corner() = shot("plasma_corner", 6f, KitShots.day.copy(model = 4)) { t -> driving(car(50f), trip(t, 50f)).copy(sizeMode = SizeMode.CORNER) }
}

/** 1000 x 1000 close-ups: the globe, and the instruments either side. */
class PlasmaDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    private fun shot(name: String, zoom: Float, fx: Float, fy: Float, block: CarSignals.() -> CarSignals = { this }) =
        KitShots.script<PlasmaSim>(paparazzi, SceneTheme.PLASMA, name, 10f, SceneOptions(hourOfDay = 14f, model = 4), zoom = zoom, fx = fx, fy = fy) { t ->
            driving(car(70f, 1.2f) { copy(fanLevel = 6).block() }, Journey(active = true, tripSeq = 3, tripKm = 12.58f + 70f * t / 3600f, stars = 3, starSeq = 3))
        }

    @Test fun globe() = shot("plasma_detail_globe", 2.0f, 0.5f, 0.2f)
    @Test fun globeTouch() = shot("plasma_detail_globe_touch", 2.0f, 0.5f, 0.2f) { copy(turnSignal = TurnSignal.LEFT) }
    @Test fun counterAndScope() = shot("plasma_detail_counter_scope", 2.2f, 0.13f, 0.36f)
    @Test fun valvesAndLadder() = shot("plasma_detail_valves_ladder", 2.2f, 0.87f, 0.32f)
}

/**
 * The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), each a small copy of
 * the 1920 x 720 driving scene (density 0.75, so the type keeps its proportion), labelled "Lv.n" top left.
 */
class PlasmaLadderSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = Density.LOW, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @Test fun ladder() {
        val runtimes = (0 until 5).map { model ->
            requireNotNull(ThemeCatalog.create(SceneTheme.PLASMA)).also { rt ->
                rt.simulate(10f, SceneOptions(hourOfDay = 14f, model = model), DEFAULT_NOW_MS) { t ->
                    driving(car(50f, 0.8f) { copy(fanLevel = 4) }, Journey(active = true, tripSeq = 2, tripKm = 0.58f + 50f * t / 3600f, stars = 3, starSeq = 3))
                }
            }
        }
        paparazzi.snapshot("plasma_ladder") {
            val tm = rememberTextMeasurer()
            Column(Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt ->
                    Box(Modifier.fillMaxWidth().aspectRatio(8f / 3f)) {
                        Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm) }
                        BasicText(
                            ThemeModels.tag(i),
                            Modifier.padding(start = 12.dp, top = 10.dp).background(Color(0xD9101316), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }
}
