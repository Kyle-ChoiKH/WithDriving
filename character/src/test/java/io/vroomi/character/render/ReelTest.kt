package io.vroomi.character.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.character.Action
import io.vroomi.character.Biome
import io.vroomi.character.Blinker
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Gait
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.Overlays
import io.vroomi.character.SizeMode
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * A 12-second animated reel (10 fps frames) for the README GIF and for checking motion without an emulator.
 * Runs only with the environment variable VROOMI_REEL=1 (it renders ~120 frames).
 */
class ReelTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private val t0 = 1_000_000L

    /** The story: parked hello -> hop in -> smooth start -> star -> left blinker -> brake -> rain with headlights. */
    private fun at(ts: Float): CharacterTarget {
        val ms = { s: Float -> t0 + (s * 1000).toLong() }
        val stars = when { ts < 5.2f -> 2; else -> 3 }
        val j = Journey(stars = stars, starSeq = stars, combo = stars, biome = if (ts < 8f) Biome.CITY else Biome.PARK,
            milestoneKm = if (ts < 9.5f) 0 else 1)
        return when {
            ts < 1.6f -> CharacterTarget(action = Action.WAVE_HELLO, actionStartedMs = ms(0f), sizeMode = SizeMode.FULL)
            ts < 2.6f -> CharacterTarget(gait = Gait.WALK, gaitSpeedKmh = 2f, sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true), journey = j)
            ts < 4.0f -> CharacterTarget(gait = Gait.JOG, gaitSpeedKmh = 10f + (ts - 2.6f) * 18f, action = Action.BURST, actionStartedMs = ms(2.6f),
                sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true, accel = 2.4f), journey = j, mood = io.vroomi.character.Mood.HAPPY)
            ts < 6.5f -> CharacterTarget(gait = Gait.JOG, gaitSpeedKmh = 38f, sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true), journey = j)
            ts < 8.5f -> CharacterTarget(gait = Gait.JOG, gaitSpeedKmh = 32f, action = Action.POINT_LEFT, actionStartedMs = ms(6.5f),
                sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true, blinker = Blinker.LEFT), journey = j)
            ts < 9.6f -> CharacterTarget(gait = Gait.JOG, gaitSpeedKmh = 22f, action = Action.SKID, actionStartedMs = ms(8.5f),
                sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true, brake = true, accel = -2.4f), journey = j)
            else -> CharacterTarget(gait = Gait.JOG, gaitSpeedKmh = 40f, sizeMode = SizeMode.AMBIENT,
                overlays = Overlays(rain = 0.7f, night = true), kart = KartState(driving = true, wipers = true, headlights = true),
                accessories = setOf(io.vroomi.character.Accessory.RAINCOAT, io.vroomi.character.Accessory.LANTERN), journey = j)
        }
    }

    @Test fun reel() {
        assumeTrue(System.getenv("VROOMI_REEL") == "1")
        val o = SceneOptions(hourOfDay = 14f, season = Season.AUTUMN)
        val rt = SceneRuntime(seed = 5)
        val total = 12f
        val steps = (total * 60).toInt()
        for (i in 0 until steps) {
            val ts = i / 60f
            val target = at(ts)
            rt.step(1f / 60f, target, o, t0 + (ts * 1000).toLong())
            if (i % 6 == 0) {
                val frame = i / 6
                paparazzi.snapshot("reel_%03d".format(frame)) {
                    val tm = rememberTextMeasurer()
                    val cache = remember { TextCache() }
                    Canvas(Modifier.fillMaxSize()) { rt.draw(this, target, o, tm, cache) }
                }
            }
        }
    }
}
