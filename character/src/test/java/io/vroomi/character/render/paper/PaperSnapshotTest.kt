package io.vroomi.character.render.paper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.vroomi.character.Action
import io.vroomi.character.Biome
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.paper.PaperSim
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.render.theme.KitShots.parked
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import org.junit.Test

/** Renders the risograph pop-up book (docs/screenshots/paper_*). */
class PaperSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private fun o(hour: Float = 14f, calm: Boolean = false, intro: Float = 1f) = SceneOptions(hourOfDay = hour, calm = calm, intro = intro)
    private fun trip(t: Float, kmh: Float, biome: Biome = Biome.CITY, stars: Int = 2, combo: Int = 0) =
        Journey(active = true, tripSeq = 2, tripKm = kmh * t / 3600f, stars = stars, starSeq = stars, comboSeq = combo, biome = biome, milestoneKm = (kmh * t / 3600f).toInt())
    private fun shot(name: String, s: Float, opt: SceneOptions = o(), prepare: (PaperSim) -> Unit = {}, at: (Float) -> CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.PAPER, name, s, opt, prepare, at = at)

    @Test fun cityCruise() = shot("paper_city_cruise", 8f) { t -> driving(car(50f) { copy(turnSignal = if (t > 7.6f) TurnSignal.RIGHT else TurnSignal.NONE) }, trip(t, 50f)) }
    @Test fun parkRain() = shot("paper_park_rain_threads", 8f) { t -> driving(car(45f) { copy(wiperActive = true) }, trip(t, 45f, Biome.PARK)) }
    @Test fun fieldSunWheel() = shot("paper_field_sun_wheel", 8f) { t -> driving(car(90f) { copy(fanLevel = 7, drivingMode = DriveModeState.ECO) }, trip(t, 90f, Biome.FIELD)) }
    @Test fun seasideNightMoon() = shot("paper_seaside_night_moon", 8f, o(hour = 21.5f)) { t ->
        driving(car(50f) { copy(headlightsOn = true) }, trip(t, 50f, Biome.SEASIDE))
    }
    @Test fun mountainDay() = shot("paper_mountain_day", 8f) { t -> driving(car(60f), trip(t, 60f, Biome.MOUNTAIN)) }
    @Test fun confettiReward() = shot("paper_confetti_reward", 6.5f) { t -> driving(car(45f), trip(t, 45f, Biome.PARK, stars = if (t > 6f) 3 else if (t > 5.4f) 2 else 1)) }
    @Test fun garlandCombo() = shot("paper_garland_combo", 6f) { t -> driving(car(45f), trip(t, 45f, Biome.MOUNTAIN, combo = if (t > 3f) 1 else 0)).copy(action = Action.CELEBRATE, actionStartedMs = 1L) }
    @Test fun biomeGutterRise() = shot("paper_biome_gutter_rise", 3.6f) { t -> driving(car(50f), trip(t, 50f, if (t > 3f) Biome.PARK else Biome.CITY)) }
    @Test fun pageFlipParked() = shot("paper_page_flip_parked", 0.55f, prepare = { it.requestFlip() }) { parked() }
    @Test fun harshTip() = shot("paper_harsh_tip", 6.25f) { t ->
        driving(car(if (t < 6f) 60f else 30f, if (t < 6f) 0f else -6f) { copy(brakePressed = t >= 6f, harshBrakeSeq = if (t >= 6f) 1 else 0) }, trip(t, 60f))
    }
    @Test fun arrivalClosed() = shot("paper_arrival_closed", 6f) { t -> parked(car(0f, gear = GearState.PARK) { copy(moving = false, arrivalSeq = if (t > 1f) 1 else 0) }) }
    @Test fun acSnow() = shot("paper_ac_snowflakes", 5f) { t -> driving(car(45f) { copy(acOn = true, setTempC = 18f) }, trip(t, 45f, Biome.MOUNTAIN)) }
    /** CORNER while moving: the cut-out title would render under 24 sp, so it fades out (it returns when parked). */
    @Test fun corner() = shot("paper_corner", 5f) { t -> driving(car(50f), trip(t, 50f)).copy(sizeMode = SizeMode.CORNER) }
    /** CORNER while parked: the title is back. */
    @Test fun cornerParked() = shot("paper_corner_parked", 3f) { parked().copy(sizeMode = SizeMode.CORNER) }
    @Test fun calm() = shot("paper_calm", 5f, o(calm = true)) { t -> driving(car(50f), trip(t, 50f)) }
    @Test fun introStandUp() = shot("paper_intro_stand_up", 3f, o(intro = 0.55f)) { t -> driving(car(50f), trip(t, 50f, Biome.PARK)) }

    /** The director sends the scene to CORNER during a safety warning; the book freezes and dims to 70 %. */
    @Test fun safetyFreeze() = shot("paper_safety_freeze", 4f) { t ->
        driving(car(50f) { copy(safetyWarningActive = t > 2f) }, trip(t, 50f)).let { if (t > 2f) it.copy(sizeMode = SizeMode.CORNER) else it }
    }
}

/** The pop-up mechanics up close, and the crane on the car's roof. */
class PaperDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    @Test fun popupMechanics() = paparazzi.snapshot("paper_popup_mechanics") {
        val painter = PaperPainter()
        Canvas(Modifier.fillMaxSize()) {
            val f = ThemeFrame().also { it.k = 1.2f; it.density = this }
            painter.drawMechanicsSheet(this, f)
        }
    }

    @Test fun crane() = KitShots.script<PaperSim>(paparazzi, SceneTheme.PAPER, "paper_detail_crane", 4f, SceneOptions(hourOfDay = 14f), zoom = 2.6f, fx = 0.3f, fy = 0.42f) {
        driving(car(40f) { copy(turnSignal = TurnSignal.LEFT) }, Journey(active = true, tripSeq = 3, biome = Biome.PARK))
    }
}
