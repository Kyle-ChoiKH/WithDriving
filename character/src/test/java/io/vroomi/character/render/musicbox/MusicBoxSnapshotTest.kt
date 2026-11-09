package io.vroomi.character.render.musicbox

import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.musicbox.MusicBoxSim
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.theme.KitShots
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
}

/** 1000x1000 details: the comb with the pins, and the whole box. */
class MusicBoxDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    @Test fun comb() = KitShots.script<MusicBoxSim>(paparazzi, SceneTheme.MUSIC_BOX, "music_box_detail_comb", 6f,
        prepare = { it.restore("clicks=40"); it.closeView = true }) { parked() }

    @Test fun box() = KitShots.script<MusicBoxSim>(paparazzi, SceneTheme.MUSIC_BOX, "music_box_detail_box", 12f) { t ->
        driving(car(50f), Journey(active = true, tripSeq = 3, tripKm = 50f * t / 3600f + 0.4f))
    }
}
