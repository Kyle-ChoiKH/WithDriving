package io.vroomi.character.render.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.character.CharacterDirector
import io.vroomi.character.CharacterTarget
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.signal.DrivingEventDetector
import io.vroomi.core.vehicle.signal.SignalProcessor
import io.vroomi.core.vehicle.sim.PARKED_CAR
import io.vroomi.core.vehicle.sim.ScenarioParser
import io.vroomi.core.vehicle.sim.ScenarioSampler
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * 120 frames of the engine_showcase demo (15 moments x 0.8 s at 10 fps) for docs/media/engine_drive.gif.
 * Runs only with VROOMI_REEL=1.
 */
class EngineReelTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private class Pipeline(json: String) {
        val scenario = ScenarioParser.parse(json)
        private val sampler = ScenarioSampler(scenario)
        private val processor = SignalProcessor()
        private val detector = DrivingEventDetector()
        private val director = CharacterDirector()
        private var moving = false
        private var stillSince = 0L
        var target = CharacterTarget(); private set

        fun sample(t: Long, start: Long) {
            val raw = sampler.sample((t - start) / 1000f, PARKED_CAR, t, start)
            val p = processor.onSpeedSample(t, raw.speedKmh ?: 0f)
            val s = raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2)
            detector.update(t, p.accelMps2, s.brakePressed).forEach { director.onDrivingEvent(it, t) }
            if (p.kmh >= 3f) { moving = true; stillSince = t } else if (p.kmh >= 1f) stillSince = t
            if (moving && (s.gear == GearState.PARK || t - stillSince > 3_000)) moving = false
            target = director.update(s, moving, false, emptySet(), t)
        }
    }

    @Test fun reel() {
        assumeTrue(System.getenv("VROOMI_REEL") == "1")
        val json = File("../app/src/main/assets/scenarios/engine_showcase.json").readText()
        val start = DEFAULT_NOW_MS - 126_000L
        val moments = listOf(2.0f, 10.5f, 13.6f, 31.0f, 38.0f, 46.5f, 57.0f, 63.2f, 76.0f, 84.0f, 90.0f, 96.0f, 99.0f, 114.0f, 119.6f)
        val p = Pipeline(json)
        val rt = EngineRuntime()
        val o = SceneOptions(hourOfDay = 14f)
        var frame = 0
        var t = start
        var next = 0
        while (next < moments.size && t < start + 127_000L) {
            if ((t - start) % 100 < 17) p.sample(t, start)
            rt.step(1f / 60f, p.target, o, EnginePowertrain.AUTO, Powertrain.ICE, t)
            val ts = (t - start) / 1000f
            val m0 = moments[next]
            if (ts >= m0) {
                val k = ((ts - m0) * 10f).toInt()
                if (k >= 8) next++
                else if (k == frame % 8 && frame < (next + 1) * 8) {
                    paparazzi.snapshot("engreel_%03d".format(frame)) {
                        val tm = rememberTextMeasurer()
                        Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm) }
                    }
                    frame++
                }
            }
            t += 1000L / 60
        }
    }
}
