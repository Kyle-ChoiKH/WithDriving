package io.vroomi.character.render.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.SizeMode
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.engine.EngineView
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import org.junit.Test

/** Renders the Cutaway theme to PNG on the JVM (docs/screenshots/engine_*). */
class EngineSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private val day = SceneOptions(hourOfDay = 14f)
    private val night = SceneOptions(hourOfDay = 21.5f)

    private fun car(kmh: Float, a: Float = 0f, gear: GearState = GearState.DRIVE, block: CarSignals.() -> CarSignals = { this }) =
        CarSignals(speedKmh = kmh, accelMps2 = a, gear = gear, moving = kmh > 0.5f || gear == GearState.DRIVE, fanLevel = 2, fanMax = 8,
            acOn = false, maxAcOn = false, setTempC = 22f, hvacAutoOn = false, defrostOn = false, wiperActive = false, headlightsOn = false,
            turnSignal = TurnSignal.NONE, drivingMode = DriveModeState.NORMAL, safetyWarningActive = false, brakePressed = false).block()

    private fun driving(c: CarSignals, j: Journey = Journey(active = true, tripSeq = 1, stars = 3, starSeq = 3)) =
        CharacterTarget(sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true), car = c, journey = j)

    private fun parked(c: CarSignals = car(0f, gear = GearState.PARK).copy(moving = false)) =
        CharacterTarget(sizeMode = SizeMode.FULL, car = c, journey = Journey())

    /** A drive that changes over time (seconds -> target), drawn at its end. */
    private fun script(
        name: String, seconds: Float, o: SceneOptions = day, forced: Powertrain = Powertrain.ICE,
        view: EngineView = EngineView.SIDE, neutralThrottle: Double = 0.0, probe: Boolean = false, at: (Float) -> CharacterTarget,
    ) {
        val rt = EngineRuntime()
        rt.probeWidestValues = probe
        rt.setView(view)
        val frames = (seconds * 60).toInt()
        val start = DEFAULT_NOW_MS - (seconds * 1000).toLong()
        for (i in 0 until frames) rt.step(1f / 60f, at(i / 60f), o, EnginePowertrain.AUTO, forced, start + i * 1000L / 60, neutralThrottle)
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm) }
        }
    }

    @Test fun sideIdleP() = script("side_idle_p", 3f) { parked() }
    @Test fun sideCruiseD6() = script("side_cruise_d6", 40f) { t -> driving(car((t * 4).coerceAtMost(70f), if (t < 17.5f) 1.1f else 0f)) }
    @Test fun sideLaunchTurbo() = script("side_launch_turbo", 6.4f) { t ->
        driving(car(if (t < 3) 60f else 60f + (t - 3) * 9f, if (t < 3) 0f else 2.6f) { copy(drivingMode = DriveModeState.SPORT) })
    }
    @Test fun sideFuelCut() = script("side_fuel_cut", 12f) { t -> driving(car(if (t < 10) 80f else 80f - (t - 10) * 6f, if (t < 10) 0f else -1.6f) { copy(brakePressed = t >= 10) }) }
    @Test fun sideAcCompressor() = script("side_ac_compressor", 4f) { driving(car(50f) { copy(acOn = true, setTempC = 18f, fanLevel = 4) }) }
    @Test fun sideRadiatorFanMax() = script("side_radiator_fan_max", 5f) { driving(car(40f) { copy(fanLevel = 8, setTempC = 27f) }) }
    @Test fun sideNight() = script("side_night", 4f, night) { driving(car(60f) { copy(headlightsOn = true, turnSignal = TurnSignal.LEFT) }) }
    @Test fun sideRainDefrost() = script("side_rain_defrost", 18.2f) { t -> driving(car(45f) { copy(wiperActive = true, defrostOn = t > 17f) }) }
    @Test fun sideReverseIdler() = script("side_reverse_idler", 3f) { driving(car(6f, gear = GearState.REVERSE)) }
    @Test fun frontSlicePowerStroke() = script("front_slice_power_stroke", 2.35f, view = EngineView.FRONT) { parked() }
    @Test fun evAccel() = script("ev_accel", 4f, forced = Powertrain.EV) { driving(car(50f, 2.2f) { copy(evBatteryPct = 64) }) }
    @Test fun evRegen() = script("ev_regen", 4f, forced = Powertrain.EV) { driving(car(70f, -2.0f) { copy(evBatteryPct = 64, brakePressed = true) }) }
    @Test fun evReverse() = script("ev_reverse", 3f, forced = Powertrain.EV) { driving(car(7f, gear = GearState.REVERSE) { copy(evBatteryPct = 64) }) }
    @Test fun evCharging() = script("ev_charging", 4f, forced = Powertrain.EV) { parked(car(0f, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = 82, acOn = true) }) }
    @Test fun sideStarComboCruise() = script("side_star_combo_cruise", 3.5f) { t ->
        val j = if (t < 2.9f) Journey(active = true, tripSeq = 1, stars = 4, starSeq = 4, combo = 4) else Journey(active = true, tripSeq = 1, stars = 5, starSeq = 5, combo = 5, comboSeq = 1)
        driving(car(90f) { copy(cruiseOn = true, drivingMode = DriveModeState.ECO) }, j)
    }
    @Test fun sideDoorLamp() = script("side_door_lamp", 3f, night) { parked(car(0f, gear = GearState.PARK) { copy(moving = false, doorOpen = true, headlightsOn = false) }) }
    @Test fun cornerSize() = script("corner_size", 3f) { driving(car(50f) { copy(safetyWarningActive = true) }).copy(sizeMode = SizeMode.CORNER) }
    @Test fun calmMode() = script("calm_mode", 4f, day.copy(calm = true)) { driving(car(90f)) }
    @Test fun safetyDim() = script("safety_dim", 4f) { t -> driving(car(60f) { copy(safetyWarningActive = t > 2.5f) }) }
    @Test fun introFrame() = script("intro_frame", 3f, day.copy(intro = 0.45f)) { parked() }
    @Test fun evNight() = script("ev_night", 4f, night, forced = Powertrain.EV) { driving(car(60f) { copy(headlightsOn = true, evBatteryPct = 64) }) }
    @Test fun parkedQuiet() = script("parked_quiet", 3f) { CharacterTarget() }
    /** Moving, with the widest value of every title-block cell (6,000 rpm, D8, 100 %; EV 16,000 rpm, D8, −60 kW). */
    @Test fun widestValuesIce() = script("widest_values_ice_moving", 4f, probe = true) { driving(car(120f, 2f) { copy(drivingMode = DriveModeState.SPORT) }) }
    @Test fun widestValuesEvNight() = script("widest_values_ev_night_moving", 4f, night, forced = Powertrain.EV, probe = true) { driving(car(120f, -2f) { copy(headlightsOn = true, evBatteryPct = 64) }) }
}

/** The narrowest canvas the theme supports (1080 × 720): the title block must still fit its widest values. */
class EngineNarrowSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1080, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun shot(name: String, o: SceneOptions, p: Powertrain, moving: Boolean) {
        val rt = EngineRuntime()
        rt.probeWidestValues = true
        val c = CarSignals(speedKmh = if (moving) 110f else 0f, accelMps2 = 0f, gear = if (moving) GearState.DRIVE else GearState.PARK, moving = moving,
            headlightsOn = o.hourOfDay > 20f, evBatteryPct = if (p == Powertrain.EV) 64 else null, safetyWarningActive = false)
        val t = CharacterTarget(sizeMode = SizeMode.AMBIENT, kart = KartState(driving = moving), car = c, journey = Journey(active = moving, tripSeq = 1))
        val start = DEFAULT_NOW_MS - 4000L
        for (i in 0 until 240) rt.step(1f / 60f, t, o, EnginePowertrain.AUTO, p, start + i * 1000L / 60)
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm) }
        }
    }

    @Test fun iceNightMoving() = shot("narrow_widest_ice_night_moving", SceneOptions(hourOfDay = 21.5f), Powertrain.ICE, true)
    @Test fun evDayMoving() = shot("narrow_widest_ev_day_moving", SceneOptions(hourOfDay = 14f), Powertrain.EV, true)
    @Test fun evDayParked() = shot("narrow_widest_ev_day_parked", SceneOptions(hourOfDay = 14f), Powertrain.EV, false)
}
