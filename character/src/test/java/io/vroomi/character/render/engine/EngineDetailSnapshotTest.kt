package io.vroomi.character.render.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import org.junit.Rule
import org.junit.Test

/** Square close-ups (1000 px, not downscaled by Paparazzi) to judge the drawing up close. */
class EngineDetailSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1000, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun car(kmh: Float, a: Float = 0f, gear: GearState = GearState.DRIVE) = CarSignals(speedKmh = kmh, accelMps2 = a, gear = gear, moving = true,
        fanLevel = 3, fanMax = 8, acOn = true, setTempC = 25f, drivingMode = DriveModeState.SPORT, safetyWarningActive = false, brakePressed = false, evBatteryPct = 64)

    private fun run(c: CarSignals, forced: Powertrain, seconds: Float, model: Int): EngineRuntime {
        val rt = EngineRuntime()
        val t = CharacterTarget(sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true), car = c, journey = Journey(active = true, tripSeq = 1))
        val start = DEFAULT_NOW_MS - (seconds * 1000).toLong()
        for (i in 0 until (seconds * 60).toInt()) rt.step(1f / 60f, t, SceneOptions(hourOfDay = 14f, model = model), EnginePowertrain.AUTO, forced, start + i * 1000L / 60)
        return rt
    }

    private fun shot(name: String, region: Rect, c: CarSignals, forced: Powertrain = Powertrain.ICE, seconds: Float = 5f, model: Int = 2) {
        val rt = run(c, forced, seconds, model)
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) { rt.drawDetail(this, tm, region) }
        }
    }

    /** The telemetry display as laid out on the 1920 x 720 screen, filling the square. Parked text is shown. */
    private fun panel(name: String, c: CarSignals, forced: Powertrain, model: Int) {
        val rt = run(c, forced, 6f, model)
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) { rt.drawPanelDetail(this, tm, 1920f * 1.5f, 720f * 1.5f) }
        }
    }

    @Test fun headValvetrain() = shot("macro_head_valvetrain", Rect(330f, 70f, 660f, 400f), car(60f, 1.4f), seconds = 5.3f)
    @Test fun turbo() = shot("macro_turbo", Rect(940f, 110f, 1240f, 410f), car(70f, 2.4f), seconds = 6f)
    @Test fun gearboxInset() = shot("macro_gearbox_inset", Rect(960f, 340f, 1280f, 660f), car(40f, 1.8f), seconds = 2.1f)
    @Test fun evStator() = shot("macro_ev_stator", Rect(200f, 60f, 680f, 540f), car(60f, 2f), Powertrain.EV)

    // ---- step 5 (spec 5.2)
    @Test fun telemetryLv5() = panel("engine_detail_telemetry_lv5", car(70f, 1.6f), Powertrain.ICE, 4)
    @Test fun telemetryEvLv5() = panel("engine_detail_telemetry_ev_lv5", car(70f, 1.6f), Powertrain.EV, 4)
    @Test fun itbLv5() = shot("engine_detail_itb_lv5", Rect(330f, 0f, 760f, 430f), car(60f, 1.4f), seconds = 5.3f, model = 4)
    @Test fun sohcLv1() = shot("engine_detail_sohc_lv1", Rect(330f, 70f, 660f, 400f), car(60f, 1.4f), seconds = 5.3f, model = 0)
    @Test fun evLv5() = shot("engine_detail_ev_lv5", Rect(200f, 60f, 680f, 540f), car(60f, 2f), Powertrain.EV, model = 4)
}
