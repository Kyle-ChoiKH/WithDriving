package io.vroomi.character.render.musicbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.musicbox.MusicBoxSim
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.simulate
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.render.theme.KitShots.parked
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import org.junit.Test

/** Renders the music box theme (docs/screenshots/music_box_*). */
class MusicBoxSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private fun trip(t: Float, kmh: Float, stars: Int = 3) = Journey(active = true, tripSeq = 2, tripKm = kmh * t / 3600f, stars = stars, starSeq = stars)

    private fun shot(name: String, s: Float, o: SceneOptions = KitShots.day, prepare: (MusicBoxSim) -> Unit = {}, at: (Float) -> io.vroomi.character.CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.MUSIC_BOX, name, s, o, prepare, at = at)

    @Test fun cruise() = shot("music_box_cruise", 40f) { t ->
        val k = (t * 3f).coerceAtMost(60f)
        driving(car(k, if (t < 20) 0.8f else 0f) { copy(fanLevel = 3) }, trip(t, 50f, if (t > 37.6f) 4 else 3))
    }

    @Test fun closePins() = shot("music_box_close_pins", 6f, prepare = { it.restore("clicks=40"); it.closeView = true }) { parked() }

    @Test fun parkedUnwinding() = shot("music_box_parked_unwinding", 9f, prepare = { it.restore("clicks=14") }) { parked() }

    @Test fun nightLid() = shot("music_box_night_lid", 10f, KitShots.night) { t ->
        driving(car(45f) { copy(headlightsOn = true, turnSignal = if (t > 8.6f) TurnSignal.LEFT else TurnSignal.NONE) }, trip(t, 45f))
    }

    @Test fun rainLid() = shot("music_box_rain_lid", 20f) { t ->
        driving(car(40f) { copy(wiperActive = true, defrostOn = t > 18.6f) }, trip(t, 40f))
    }

    @Test fun sportCombo() = shot("music_box_sport_key_change", 30f) { t ->
        driving(car(80f, 0.4f) { copy(drivingMode = DriveModeState.SPORT) }, trip(t, 80f).copy(comboSeq = if (t > 2f) 1 else 0))
    }

    @Test fun harshDamper() = shot("music_box_harsh_damper", 8.3f) { t ->
        driving(car(if (t < 8f) 60f else 30f, if (t < 8f) 0f else -6f) { copy(brakePressed = t >= 8f, harshBrakeSeq = if (t >= 8f) 1 else 0) }, trip(t, 60f))
    }

    @Test fun corner() = shot("music_box_corner", 6f) { t -> driving(car(50f), trip(t, 50f)).copy(sizeMode = SizeMode.CORNER) }

    @Test fun calm() = shot("music_box_calm", 10f, SceneOptions(hourOfDay = 14f, calm = true)) { t -> driving(car(50f), trip(t, 50f)) }

    @Test fun safetyDim() = shot("music_box_safety_dim", 10f) { t -> driving(car(50f) { copy(safetyWarningActive = t > 8.5f) }, trip(t, 50f)) }

    @Test fun intro() = shot("music_box_intro", 4f, SceneOptions(hourOfDay = 14f, intro = 0.4f)) { t -> driving(car(50f), trip(t, 50f)) }

    @Test fun pearlCombo() = shot("music_box_pearl_combo", 12.6f) { t ->
        driving(car(50f), trip(t, 50f, if (t > 11.9f) 4 else 3).copy(comboSeq = if (t > 12.1f) 1 else 0))
    }

    @Test fun ecoPark() = shot("music_box_eco_cruise", 12f) { t -> driving(car(35f) { copy(drivingMode = DriveModeState.ECO) }, trip(t, 35f)) }

    // ---- the upgrade ladder (spec 5.3): driving at 50 km/h, three stars
    private fun lv(name: String, model: Int, s: Float = 10f, o: SceneOptions = KitShots.day, block: io.vroomi.character.CarSignals.() -> io.vroomi.character.CarSignals = { this }) =
        shot(name, s, o.copy(model = model), prepare = { it.model = model }) { t -> driving(car(50f, block = block), trip(t, 50f)) }
    @Test fun lv1Day() = lv("music_box_lv1_day", 0)
    @Test fun lv2Day() = lv("music_box_lv2_day", 1)
    @Test fun lv3Day() = lv("music_box_lv3_day", 2)
    @Test fun lv4Day() = lv("music_box_lv4_day", 3)
    @Test fun lv5Day() = lv("music_box_lv5_day", 4, 10.05f)
    @Test fun lv5Night() = lv("music_box_lv5_night", 4, 10.05f, KitShots.night) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("music_box_lv3_blinker_left", 2, 8.7f) { copy(turnSignal = TurnSignal.LEFT) }
    @Test fun lv3Brake() = shot("music_box_lv3_brake", 8.3f, KitShots.day.copy(model = 2), prepare = { it.model = 2 }) { t ->
        driving(car(if (t < 8f) 60f else 30f, if (t < 8f) 0f else -6f) { copy(brakePressed = t >= 8f, harshBrakeSeq = if (t >= 8f) 1 else 0) }, trip(t, 60f))
    }
}

/**
 * The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), each a small copy of
 * the 1920 x 720 driving scene (density 0.75, so the type keeps its proportion), labelled "Lv.n" top left.
 */
class MusicBoxLadderSnapshotTest {
    @get:Rule val paparazzi = app.cash.paparazzi.Paparazzi(
        deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = com.android.resources.Density.LOW, orientation = com.android.resources.ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @Test fun ladder() {
        val runtimes = (0 until 5).map { model ->
            requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.MUSIC_BOX)).also { rt ->
                (rt.sim as MusicBoxSim).model = model
                rt.simulate(10.05f, SceneOptions(hourOfDay = 14f, model = model), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                    driving(car(50f), Journey(active = true, tripSeq = 2, tripKm = 50f * t / 3600f, stars = 3, starSeq = 3))
                }
            }
        }
        paparazzi.snapshot("music_box_ladder") {
            val tm = androidx.compose.ui.text.rememberTextMeasurer()
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt ->
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().aspectRatio(8f / 3f)) {
                        androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) { rt.draw(this, tm) }
                        androidx.compose.foundation.text.BasicText(
                            io.vroomi.character.theme.ThemeModels.tag(i),
                            androidx.compose.ui.Modifier.padding(start = 12.dp, top = 10.dp)
                                .background(androidx.compose.ui.graphics.Color(0xD9101316), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }
}

/** 1000x1000 details: the comb with the pins, and the whole box. */
class MusicBoxDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    @Test fun comb() = KitShots.script<MusicBoxSim>(paparazzi, SceneTheme.MUSIC_BOX, "music_box_detail_comb", 6f,
        prepare = { it.restore("clicks=40"); it.closeView = true }) { parked() }

    @Test fun box() = KitShots.script<MusicBoxSim>(paparazzi, SceneTheme.MUSIC_BOX, "music_box_detail_box", 12f) { t ->
        driving(car(50f), Journey(active = true, tripSeq = 3, tripKm = 50f * t / 3600f + 0.4f))
    }

    @Test fun bellsLv5() = KitShots.script<MusicBoxSim>(paparazzi, SceneTheme.MUSIC_BOX, "music_box_detail_bells_lv5", 10.05f, SceneOptions(hourOfDay = 14f, model = 4),
        prepare = { it.model = 4 }, zoom = 2.4f, fx = 0.86f, fy = 0.55f) { t ->
        driving(car(50f), Journey(active = true, tripSeq = 3, tripKm = 50f * t / 3600f + 0.4f))
    }

    @Test fun combLv4() = KitShots.script<MusicBoxSim>(paparazzi, SceneTheme.MUSIC_BOX, "music_box_detail_comb_lv4", 6f, SceneOptions(hourOfDay = 14f, model = 3),
        prepare = { it.model = 3; it.restore("clicks=40"); it.closeView = true }) { parked() }
}
