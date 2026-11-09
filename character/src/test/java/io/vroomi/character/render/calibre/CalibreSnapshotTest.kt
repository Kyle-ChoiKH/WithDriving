package io.vroomi.character.render.calibre

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.CalibreView
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.Season
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import org.junit.Test

/** Renders the Calibre theme to PNG on the JVM (docs/screenshots/calibre_*). */
class CalibreSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private val day = SceneOptions(hourOfDay = 10.2f, season = Season.AUTUMN)
    private val night = SceneOptions(hourOfDay = 21.5f, season = Season.AUTUMN)

    private fun car(kmh: Float, fan: Int = 2, gear: GearState = GearState.DRIVE, block: CarSignals.() -> CarSignals = { this }) =
        CarSignals(speedKmh = kmh, accelMps2 = 0f, gear = gear, moving = kmh > 0.5f || gear == GearState.DRIVE, fanLevel = fan, fanMax = 8,
            acOn = false, maxAcOn = false, setTempC = 22f, hvacAutoOn = false, defrostOn = false, wiperActive = false, headlightsOn = false,
            turnSignal = TurnSignal.NONE, evCharging = false, evBatteryPct = 72, drivingMode = DriveModeState.NORMAL, safetyWarningActive = false).block()

    private fun driving(c: CarSignals, j: Journey = Journey(active = true, tripSeq = 1, stars = 4, starSeq = 4)) =
        CharacterTarget(sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true), car = c, journey = j)

    private fun parked(c: CarSignals = car(0f, gear = GearState.PARK).copy(moving = false)) =
        CharacterTarget(sizeMode = SizeMode.FULL, car = c, journey = Journey())

    private fun shot(
        name: String, t: CharacterTarget, o: SceneOptions = day, finish: CalibreFinish = CalibreFinish.AUTO,
        view: CalibreView = CalibreView.CASEBACK, seconds: Float = 3f, reserve: Float = 41f,
    ) = paparazzi.snapshot(name) {
        CalibreSceneStill(t, o, finish, Modifier.fillMaxSize(), view, seconds, reserveH = reserve)
    }

    @Test fun casebackDayCruise() = shot("caseback_day_cruise", driving(car(72f)))
    @Test fun casebackNight() = shot("caseback_night_lume", driving(car(55f) { copy(headlightsOn = true) }), night)
    @Test fun casebackRainCrystal() = shot("caseback_rain_crystal", driving(car(40f) { copy(wiperActive = true, headlightsOn = false) }), seconds = 16f)
    @Test fun casebackDefrost() = script("caseback_defrost_clearing", 17.3f) { ts ->
        driving(car(40f) { copy(wiperActive = ts < 15f, defrostOn = ts >= 16f) })
    }

    /** A scene that changes over time (seconds -> target), drawn at the end. */
    private fun script(name: String, seconds: Float, o: SceneOptions = day, at: (Float) -> CharacterTarget) {
        val rt = CalibreRuntime(41f)
        val frames = (seconds * 60).toInt()
        val start = DEFAULT_NOW_MS - (seconds * 1000).toLong()
        var last = at(0f)
        for (i in 0 until frames) { last = at(i / 60f); rt.step(1f / 60f, last, o, CalibreFinish.AUTO, start + i * 1000L / 60) }
        paparazzi.snapshot(name) {
            val tm = androidx.compose.ui.text.rememberTextMeasurer()
            val d = androidx.compose.ui.platform.LocalDensity.current
            val text = androidx.compose.runtime.remember { CalibreText(tm, d) }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm, text, DEFAULT_NOW_MS, java.time.ZoneId.of("Asia/Seoul")) }
        }
    }
    @Test fun casebackHvacWinding() = shot("caseback_hvac_winding", parked(car(0f, fan = 7, gear = GearState.PARK) { copy(moving = false, acOn = true, setTempC = 18f) }), seconds = 5f, reserve = 30f)
    @Test fun casebackChrono() = shot("caseback_chrono_running", driving(car(64f), Journey(active = true, tripSeq = 1, stars = 9, starSeq = 9)), seconds = 125f)
    @Test fun casebackEvWinder() = shot("caseback_ev_winder", parked(car(0f, fan = 0, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = 82) }), seconds = 4f)
    @Test fun casebackReverse() = shot("caseback_reverse", driving(car(7f, gear = GearState.REVERSE)), seconds = 3f)
    @Test fun casebackBlinkerLeft() = shot("caseback_blinker_left", driving(car(30f) { copy(turnSignal = TurnSignal.LEFT) }), seconds = 0.72f)
    @Test fun casebackWarmSport() = shot("caseback_sport_warm", driving(car(95f) { copy(drivingMode = DriveModeState.SPORT, setTempC = 27f) }), seconds = 4f)
    @Test fun dialDay() = shot("dial_day", parked(), view = CalibreView.DIAL)
    @Test fun dialNight() = shot("dial_night", parked(car(0f, gear = GearState.PARK) { copy(moving = false, headlightsOn = true, hvacAutoOn = true) }), night, view = CalibreView.DIAL)
    @Test fun finishYellow() = shot("finish_yellow_gold", parked(), finish = CalibreFinish.YELLOW_GOLD)
    @Test fun finishRose() = shot("finish_rose_gold", parked(), finish = CalibreFinish.ROSE_GOLD, view = CalibreView.DIAL)
    @Test fun finishRhodium() = shot("finish_rhodium_blue", parked(), finish = CalibreFinish.RHODIUM_BLUE)
    @Test fun finishRhodiumDial() = shot("finish_rhodium_blue_dial", parked(), finish = CalibreFinish.RHODIUM_BLUE, view = CalibreView.DIAL)
    @Test fun finishBlack() = shot("finish_black_dlc", parked(), finish = CalibreFinish.BLACK_DLC)
    @Test fun finishBlackDial() = shot("finish_black_dlc_dial", parked(), finish = CalibreFinish.BLACK_DLC, view = CalibreView.DIAL)
    @Test fun finishAutoEco() = shot("finish_auto_eco", driving(car(48f) { copy(drivingMode = DriveModeState.ECO) }))
    @Test fun cornerSize() = shot("corner_size", driving(car(50f) { copy(turnSignal = TurnSignal.HAZARD) }).copy(sizeMode = SizeMode.CORNER), seconds = 3f)
    @Test fun calmMode() = shot("calm_mode", driving(car(110f)), day.copy(calm = true))
    @Test fun safetyDim() = script("safety_dim", 4f) { ts -> driving(car(60f) { copy(safetyWarningActive = ts > 2.5f) }) }
    @Test fun introFrame() = shot("intro_frame", parked(), day.copy(intro = 0.45f), view = CalibreView.DIAL)
    @Test fun parkedQuiet() = shot("parked_quiet", CharacterTarget(sizeMode = SizeMode.FULL))
    @Test fun comboBevel() = script("combo_bevel_glint", 6.25f) { ts ->
        driving(car(70f), Journey(active = true, tripSeq = 1, stars = if (ts > 5.6f) 5 else 4, starSeq = if (ts > 5.6f) 5 else 4, comboSeq = if (ts > 5.7f) 1 else 0))
    }
}
