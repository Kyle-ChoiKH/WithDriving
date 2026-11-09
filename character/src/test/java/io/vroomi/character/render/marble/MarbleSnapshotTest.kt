package io.vroomi.character.render.marble

import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.marble.MarbleSim
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.render.theme.KitShots.parked
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import org.junit.Test

/** Renders the marble machine (docs/screenshots/marble_*). */
class MarbleSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private fun trip(t: Float, kmh: Float, stars: Int = 0, combo: Int = 0) =
        Journey(active = true, tripSeq = 2, tripKm = kmh * t / 3600f, stars = stars, starSeq = stars, comboSeq = combo, milestoneKm = (kmh * t / 3600f).toInt())
    private fun shot(name: String, s: Float, o: SceneOptions = KitShots.day, prepare: (MarbleSim) -> Unit = {}, at: (Float) -> CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.MARBLE, name, s, o, prepare, at = at)

    @Test fun cruise() = shot("marble_cruise", 40f) { t -> driving(car(70f), trip(t, 70f)) }
    @Test fun brakeQueue() = shot("marble_brake_queue", 40f) { t -> driving(car(60f) { copy(brakePressed = t > 22f) }, trip(t, 60f)) }
    @Test fun turnLeftGate() = shot("marble_turn_left_gate", 40f) { t -> driving(car(60f) { copy(turnSignal = TurnSignal.LEFT) }, trip(t, 60f)) }
    @Test fun reverseLoop() = shot("marble_reverse_loop", 50f) { t -> driving(car(if (t < 30f) 60f else 8f, gear = if (t < 30f) GearState.DRIVE else GearState.REVERSE), trip(t, 60f)) }
    @Test fun wiperPendulum() = shot("marble_wiper_pendulum", 45f) { t -> driving(car(60f) { copy(wiperActive = true, turnSignal = TurnSignal.LEFT) }, trip(t, 60f)) }
    @Test fun fanWindmill() = shot("marble_fan_windmill", 20f) { t -> driving(car(50f) { copy(fanLevel = 8, acOn = true, setTempC = 18f, drivingMode = DriveModeState.ECO) }, trip(t, 50f)) }
    @Test fun stripedMarbleStar() = shot("marble_striped_marble_star", 30f) { t -> driving(car(60f), trip(t, 60f, stars = if (t > 26f) 2 else if (t > 23f) 1 else 0)) }
    @Test fun chimeCombo() = shot("marble_chime_combo", 70f) { t -> driving(car(70f) { copy(drivingMode = DriveModeState.SPORT) }, trip(t, 70f, stars = (t / 6f).toInt().coerceAtMost(6), combo = if (t > 40f) 1 else 0)) }
    @Test fun nightLamps() = shot("marble_night_lamps", 30f, KitShots.night) { t -> driving(car(60f) { copy(headlightsOn = true) }, trip(t, 60f)) }
    @Test fun arrivalHopper() = shot("marble_arrival_hopper", 120f) { t ->
        val drive = t < 40f
        val c = car(if (drive) 60f else 0f, gear = if (drive) GearState.DRIVE else GearState.PARK) { copy(moving = drive, arrivalSeq = if (t > 41f) 1 else 0) }
        driving(c, trip(t.coerceAtMost(40f), 60f, stars = if (t > 10f) 3 else 0)).copy(kart = io.vroomi.character.KartState(driving = drive))
    }
    @Test fun harshStoppers() = shot("marble_harsh_stoppers", 30.3f) { t -> driving(car(60f) { copy(harshBrakeSeq = if (t > 30f) 1 else 0) }, trip(t, 60f)) }
    @Test fun corner() = shot("marble_corner", 20f) { t -> driving(car(60f), trip(t, 60f)).copy(sizeMode = SizeMode.CORNER) }
    @Test fun calm() = shot("marble_calm", 30f, SceneOptions(hourOfDay = 14f, calm = true)) { t -> driving(car(60f), trip(t, 60f)) }
    @Test fun comboFlip() = shot("marble_combo_gates_flip", 30.4f) { t -> driving(car(60f), trip(t, 60f, stars = 2, combo = if (t > 29.9f) 1 else 0)) }
    @Test fun safetyDim() = shot("marble_safety_dim", 20f) { t -> driving(car(60f) { copy(safetyWarningActive = t > 18f) }, trip(t, 60f)) }
    @Test fun intro() = shot("marble_intro", 3f, SceneOptions(hourOfDay = 14f, intro = 0.35f)) { parked() }
    @Test fun parkedNull() = shot("marble_parked_quiet", 3f) { io.vroomi.character.CharacterTarget() }
}

/** 1000x1000 details: the screw lift, the flip gate, a marble on the rails. */
class MarbleDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    @Test fun lift() = KitShots.script<MarbleSim>(paparazzi, SceneTheme.MARBLE, "marble_detail_lift", 30f, zoom = 2.6f, fx = 0.07f, fy = 0.45f) { driving(car(60f)) }
    @Test fun gate() = KitShots.script<MarbleSim>(paparazzi, SceneTheme.MARBLE, "marble_detail_gate", 40f, zoom = 3f, fx = 0.56f, fy = 0.26f) {
        driving(car(60f) { copy(turnSignal = TurnSignal.RIGHT) })
    }
}
