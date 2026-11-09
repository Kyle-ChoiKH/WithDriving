package io.vroomi.character.render.calibre

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.character.CharacterDirector
import io.vroomi.character.CharacterTarget
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.CalibreMechanics
import io.vroomi.character.render.SceneOptions
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
import java.time.ZoneId

/**
 * 120 frames of the calibre_showcase demo (15 moments × 0.8 s at 10 fps) for docs/media/calibre_drive.gif.
 * Runs only with VROOMI_REEL=1.
 */
class CalibreReelTest {
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
            // Same rule as the app's MovingDetector: enter above 3 km/h, leave after 3 s below 1 km/h or in P.
            if (p.kmh >= 3f) { moving = true; stillSince = t }
            else if (p.kmh >= 1f) stillSince = t
            if (moving && (s.gear == GearState.PARK || t - stillSince > 3_000)) moving = false
            target = director.update(s, moving, false, emptySet(), t)
        }
    }

    private val json get() = File("../app/src/main/assets/scenarios/calibre_showcase.json").readText()

    @Test fun reel() {
        assumeTrue(System.getenv("VROOMI_REEL") == "1")
        val start = DEFAULT_NOW_MS - 145_000L

        // Pass 1: when does the star combo light the train?
        var comboAt = 118f
        run {
            val p = Pipeline(json); val m = CalibreMechanics(30f)
            var t = start
            while (t < start + 145_000L) {
                if ((t - start) % 100 < 17) p.sample(t, start)
                m.step(1f / 60f, p.target, t, 14f, CalibreFinish.AUTO)
                if (CalibreMechanics.Event.TRAIN_GLINT in m.drainEvents()) { comboAt = (t - start) / 1000f; break }
                t += 1000L / 60
            }
        }

        val moments = listOf(1.0f, 11.5f, 25.6f, 31.5f, 38.0f, 40.0f, 53.0f, 57.6f, 64.0f, 67.6f, 80.0f, 96.6f, 106.5f, comboAt - 0.1f, 138.8f)
        val p = Pipeline(json)
        val rt = CalibreRuntime(30f)
        val o = SceneOptions(hourOfDay = 14f)
        var frame = 0
        var t = start
        var next = 0
        while (next < moments.size && t < start + 146_000L) {
            if ((t - start) % 100 < 17) p.sample(t, start)
            rt.step(1f / 60f, p.target, o, CalibreFinish.AUTO, t)
            val ts = (t - start) / 1000f
            val m0 = moments[next]
            if (ts >= m0) {
                val k = ((ts - m0) * 10f).toInt()
                if (k >= 8) next++
                else if (k == frame % 8 && frame < (next + 1) * 8) {
                    val now = t
                    paparazzi.snapshot("calreel_%03d".format(frame)) {
                        val tm = rememberTextMeasurer()
                        val d = LocalDensity.current
                        val text = remember { CalibreText(tm, d) }
                        Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm, text, now, ZoneId.of("Asia/Seoul")) }
                    }
                    frame++
                }
            }
            t += 1000L / 60
        }
    }
}
