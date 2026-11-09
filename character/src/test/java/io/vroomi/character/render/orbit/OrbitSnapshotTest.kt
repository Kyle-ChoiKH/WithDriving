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
import org.junit.Test

/** Renders the orbit theme (docs/screenshots/orbit_*). */
class OrbitSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private fun trip(t: Float, kmh: Float, stars: Int = 4, combo: Int = 0) =
        Journey(active = true, tripSeq = 2, tripKm = kmh * t / 3600f, stars = stars, starSeq = stars, comboSeq = combo, milestoneKm = (kmh * t / 3600f).toInt())
    private fun shot(name: String, s: Float, o: SceneOptions = KitShots.day, prepare: (OrbitSim) -> Unit = {}, at: (Float) -> CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.ORBIT, name, s, o, prepare, at = at)

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
}
