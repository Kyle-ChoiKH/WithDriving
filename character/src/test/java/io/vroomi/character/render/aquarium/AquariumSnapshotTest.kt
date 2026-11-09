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
}
