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

    private fun shot(name: String, region: Rect, c: CarSignals, forced: Powertrain = Powertrain.ICE, seconds: Float = 5f) {
        val rt = EngineRuntime()
        val t = CharacterTarget(sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true), car = c, journey = Journey(active = true, tripSeq = 1))
        val start = DEFAULT_NOW_MS - (seconds * 1000).toLong()
        for (i in 0 until (seconds * 60).toInt()) rt.step(1f / 60f, t, SceneOptions(hourOfDay = 14f), EnginePowertrain.AUTO, forced, start + i * 1000L / 60)
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) { rt.drawDetail(this, tm, region) }
        }
    }

    @Test fun headValvetrain() = shot("macro_head_valvetrain", Rect(330f, 70f, 660f, 400f), car(60f, 1.4f), seconds = 5.3f)
    @Test fun turbo() = shot("macro_turbo", Rect(940f, 110f, 1240f, 410f), car(70f, 2.4f), seconds = 6f)
    @Test fun gearboxInset() = shot("macro_gearbox_inset", Rect(960f, 340f, 1280f, 660f), car(40f, 1.8f), seconds = 2.1f)
    @Test fun evStator() = shot("macro_ev_stator", Rect(200f, 60f, 680f, 540f), car(60f, 2f), Powertrain.EV)
}
