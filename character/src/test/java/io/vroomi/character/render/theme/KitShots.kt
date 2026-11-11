package io.vroomi.character.render.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
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
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal

/** Shared helpers for the kit themes' Paparazzi tests (1920x720 like the car display, or 1000x1000 detail). */
object KitShots {
    fun wide() = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    /** Pixel World at exactly 2× its 480 × 180 screen: stored without any resampling, so every pixel stays crisp. */
    fun pixelWide() = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 960, screenHeight = 360, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    fun square() = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1000, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    val day = SceneOptions(hourOfDay = 14f)
    val night = SceneOptions(hourOfDay = 21.5f)

    fun car(kmh: Float, a: Float = 0f, gear: GearState = GearState.DRIVE, block: CarSignals.() -> CarSignals = { this }) =
        CarSignals(speedKmh = kmh, accelMps2 = a, gear = gear, moving = kmh > 0.5f || gear == GearState.DRIVE, fanLevel = 2, fanMax = 8,
            acOn = false, maxAcOn = false, setTempC = 22f, hvacAutoOn = false, defrostOn = false, wiperActive = false, headlightsOn = false,
            turnSignal = TurnSignal.NONE, drivingMode = DriveModeState.NORMAL, safetyWarningActive = false, brakePressed = false).block()

    fun driving(c: CarSignals, j: Journey = Journey(active = true, tripSeq = 1, stars = 3, starSeq = 3)) =
        CharacterTarget(sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true), car = c, journey = j)

    fun parked(c: CarSignals = car(0f, gear = GearState.PARK).copy(moving = false), j: Journey = Journey(tripSeq = 1)) =
        CharacterTarget(sizeMode = SizeMode.FULL, car = c, journey = j)

    /** A drive that changes over time (seconds → target), simulated at 60 Hz and drawn at its end. */
    @Suppress("UNCHECKED_CAST")
    fun <S : ThemeSim> script(
        p: Paparazzi, theme: SceneTheme, name: String, seconds: Float, o: SceneOptions = day,
        prepare: (S) -> Unit = {}, zoom: Float = 1f, fx: Float = 0.5f, fy: Float = 0.5f, at: (Float) -> CharacterTarget,
    ) {
        val rt = requireNotNull(ThemeCatalog.create(theme))
        prepare(rt.sim as S)
        // A detail is framed by its place in the whole composition, not by what a window of this shape would show.
        rt.wholeComposition = zoom != 1f
        rt.simulate(seconds, o, DEFAULT_NOW_MS, at)
        p.snapshot(name) {
            val tm = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                if (zoom == 1f) rt.draw(this, tm)
                else scale(zoom, zoom, Offset(size.width * fx, size.height * fy)) { rt.draw(this, tm) }
            }
        }
    }
}
