package io.vroomi.character.render.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import app.cash.paparazzi.Paparazzi
import io.vroomi.character.CharacterDirector
import io.vroomi.character.CharacterTarget
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.route.LiveRoute
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
 * 120 frames (15 moments × 0.8 s at 10 fps) of a kit theme's showcase for docs/media/<theme>_drive.gif.
 * Runs only with VROOMI_REEL=1; VROOMI_REEL_THEME=<SceneTheme name> picks one theme (default: all).
 */
class KitReelTest {
    @get:Rule val paparazzi = KitShots.wide()

    @Test fun reel() {
        assumeTrue(System.getenv("VROOMI_REEL") == "1")
        val only = System.getenv("VROOMI_REEL_THEME")
        KitReels.ALL.filterKeys { only == null || it.name == only }.forEach { (theme, r) -> renderReel(paparazzi, theme, r) }
    }
}

/** Pixel World's reel on a 960 × 360 device (exactly 2×), so the frames are stored without resampling. */
class PixelReelTest {
    @get:Rule val paparazzi = KitShots.pixelWide()

    @Test fun reel() {
        assumeTrue(System.getenv("VROOMI_REEL") == "1")
        val only = System.getenv("VROOMI_REEL_THEME")
        assumeTrue(only == null || only == SceneTheme.PIXEL.name)
        renderReel(paparazzi, SceneTheme.PIXEL, KitReels.PIXEL)
    }
}

/** One reel: showcase, 15 moments in seconds, frame prefix and an optional restored state (a user's grown garden). */
internal class Reel(val id: String, val moments: List<Float>, val prefix: String, val restore: String? = null)

internal object KitReels {
    val ALL: Map<SceneTheme, Reel> = mapOf(
        SceneTheme.MUSIC_BOX to Reel("music_box_showcase",
            listOf(1.5f, 8f, 14f, 21f, 29f, 35f, 41f, 47f, 52f, 56f, 60f, 64.3f, 76f, 83f, 92f), "mbreel"),
        SceneTheme.TERRARIUM to Reel("terrarium_showcase",
            listOf(1.5f, 11f, 21f, 28f, 34.5f, 39f, 44f, 52f, 58.5f, 63.5f, 68.2f, 74f, 83f, 87f, 91f), "trreel",
            "g=26,40,18,15,13,18;b=2;u=2;r=2;s=1"),
        SceneTheme.TREASURE_MAP to Reel("map_showcase",
            listOf(2f, 14f, 25f, 32f, 44f, 54f, 70f, 90f, 97f, 104f, 108.4f, 115f, 125f, 131f, 136f), "mpreel"),
        SceneTheme.AQUARIUM to Reel("aquarium_showcase",
            listOf(2f, 8f, 13f, 20f, 26f, 31.5f, 38f, 46f, 53f, 58f, 63.3f, 70f, 75f, 81f, 88f), "aqreel"),
        SceneTheme.ORBIT to Reel("orbit_showcase",
            listOf(2f, 8f, 13f, 20f, 25f, 34f, 42f, 49f, 55f, 60f, 64.3f, 70f, 75f, 83f, 91f), "orreel"),
        SceneTheme.MARBLE to Reel("marble_showcase",
            listOf(2f, 10f, 16f, 25f, 30f, 35f, 43f, 52f, 60f, 68f, 72.3f, 78f, 85f, 92f, 99f), "mareel"),
        SceneTheme.PAPER to Reel("paper_showcase",
            listOf(1.5f, 8f, 13f, 16.3f, 21f, 26f, 32f, 39f, 46f, 54f, 62f, 66.4f, 72f, 81f, 85f), "ppreel"),
    )
    val PIXEL = Reel("pixel_showcase",
        listOf(2f, 8f, 13f, 22f, 31f, 39f, 45f, 49f, 56f, 60.3f, 65f, 76f, 83f, 90f, 99f), "pxreel")
}

internal class ReelPipeline(json: String) {
    val scenario = ScenarioParser.parse(json)
    private val sampler = ScenarioSampler(scenario)
    private val processor = SignalProcessor()
    private val detector = DrivingEventDetector()
    private val director = CharacterDirector()
    private var moving = false
    private var stillSince = 0L
    var target = CharacterTarget(); private set

    fun sample(t: Long, start: Long) {
        val raw = sampler.sample(((t - start) / 1000f).coerceAtMost(scenario.durationS), PARKED_CAR, t, start)
        val p = processor.onSpeedSample(t, raw.speedKmh ?: 0f)
        val s = raw.copy(speedKmh = p.kmh, accelMps2 = p.accelMps2)
        detector.update(t, p.accelMps2, s.brakePressed).forEach { director.onDrivingEvent(it, t) }
        if (p.kmh >= 3f) { moving = true; stillSince = t } else if (p.kmh >= 1f) stillSince = t
        if (moving && (s.gear == GearState.PARK || t - stillSince > 3_000)) moving = false
        target = director.update(s, moving, false, emptySet(), t)
    }
}

/** Renders 15 moments × 8 frames (10 fps) of [r] through the app pipeline. */
internal fun renderReel(paparazzi: Paparazzi, theme: SceneTheme, r: Reel) {
    val json = File("../app/src/main/assets/scenarios/${r.id}.json").readText()
    val p = ReelPipeline(json)
    val dur = (p.scenario.durationS * 1000).toLong()
    val start = DEFAULT_NOW_MS - dur
    val rt = requireNotNull(ThemeCatalog.create(theme))
    r.restore?.let { rt.sim.restore(it) }
    LiveRoute.demoRoute = p.scenario.route
    val o = SceneOptions(hourOfDay = 14f)
    var frame = 0
    var t = start
    var next = 0
    while (next < r.moments.size && t < start + dur + 2_000L) {
        if ((t - start) % 100 < 17) p.sample(t, start)
        rt.step(1f / 60f, p.target, o, t)
        val ts = (t - start) / 1000f
        val m0 = r.moments[next]
        if (ts >= m0) {
            val k = ((ts - m0) * 10f).toInt()
            if (k >= 8) next++
            else if (k == frame % 8 && frame < (next + 1) * 8) {
                paparazzi.snapshot("%s_%03d".format(r.prefix, frame)) {
                    val tm = rememberTextMeasurer()
                    Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm) }
                }
                frame++
            }
        }
        t += 1000L / 60
    }
    LiveRoute.demoRoute = null
}
