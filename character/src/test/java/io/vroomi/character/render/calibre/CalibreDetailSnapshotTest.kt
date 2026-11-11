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
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import org.junit.Test

/**
 * Square close-ups (1000 px, not downscaled by Paparazzi) to judge the finishing up close,
 * and for the README "macro" pictures.
 */
class CalibreDetailSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1000, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private val car = CarSignals(speedKmh = 0f, gear = GearState.PARK, fanLevel = 6, fanMax = 8, acOn = false, setTempC = 22f,
        turnSignal = TurnSignal.NONE, drivingMode = DriveModeState.NORMAL, safetyWarningActive = false)

    private fun shot(name: String, finish: CalibreFinish, view: CalibreView, hour: Float = 10.2f, c: CarSignals = car, seconds: Float = 2.5f, model: Int = 0) = paparazzi.snapshot(name) {
        CalibreSceneStill(
            CharacterTarget(sizeMode = SizeMode.FULL, kart = KartState(), car = c, journey = Journey()),
            SceneOptions(hourOfDay = hour, model = model), finish, Modifier.fillMaxSize(), view, seconds = seconds,
        )
    }

    /** Lv.3: the cushion case and the engine-turned dial, up close. */
    @Test fun lv3Cushion() = shot("calibre_detail_lv3_cushion", CalibreFinish.RHODIUM_BLUE, CalibreView.DIAL, 14f, model = 2)
    /** Lv.5: the flying tourbillon at six, the gold chatons and the skeleton rotor, from the back. */
    @Test fun lv5Tourbillon() = shot("calibre_detail_lv5_tourbillon", CalibreFinish.YELLOW_GOLD, CalibreView.CASEBACK, 14f, model = 4)
    /** Lv.5 from the front: the skeleton dial with the tourbillon in its opening at six. */
    @Test fun lv5Dial() = shot("calibre_detail_lv5_dial", CalibreFinish.YELLOW_GOLD, CalibreView.DIAL, 14f, model = 4)

    @Test fun macroCaseback() = shot("macro_caseback_yellow", CalibreFinish.YELLOW_GOLD, CalibreView.CASEBACK)
    @Test fun macroDial() = shot("macro_dial_yellow", CalibreFinish.YELLOW_GOLD, CalibreView.DIAL)
    @Test fun macroRhodium() = shot("macro_caseback_rhodium", CalibreFinish.RHODIUM_BLUE, CalibreView.CASEBACK, 15.5f)
    @Test fun macroRain() = shot("macro_rain_defrost", CalibreFinish.YELLOW_GOLD, CalibreView.CASEBACK, 13f, car.copy(wiperActive = true, fanLevel = 8), seconds = 9f)
    @Test fun macroNight() = shot("macro_dial_night", CalibreFinish.RHODIUM_BLUE, CalibreView.DIAL, 21f, car.copy(headlightsOn = true, hvacAutoOn = true))
    @Test fun macroBlack() = shot("macro_dial_black", CalibreFinish.BLACK_DLC, CalibreView.DIAL, 16f)
}
