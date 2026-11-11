package io.vroomi.character.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.SizeMode
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.character.render.calibre.draw as drawCalibre
import io.vroomi.character.render.engine.draw as drawEngine
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.ThemeCatalog
import io.vroomi.character.render.theme.simulate
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.theme.ThemeModels
import org.junit.Rule
import org.junit.Test

/**
 * The upgrade ladder as a new user meets it: a fresh install (no stored theme state, no trip), the car parked, the
 * five steps of one theme stacked in one picture. Every step has to differ at a glance here, because this is exactly
 * what the step picker shows when a step is chosen on the stage. A second picture shows the same ladder 20 s into the
 * first drive.
 */
class FreshLadderSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = Density.LOW, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun options(model: Int) = SceneOptions(hourOfDay = 14f, model = model)

    private fun kit(theme: SceneTheme, name: String, seconds: Float, at: (Float) -> CharacterTarget) {
        val runtimes = (0 until ThemeModels.COUNT).map { i ->
            requireNotNull(ThemeCatalog.create(theme)).also { it.simulate(seconds, options(i), DEFAULT_NOW_MS, at) }
        }
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            Column(Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt -> Cell(i) { Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm) } } }
            }
        }
    }

    private fun kart(name: String, seconds: Float, at: (Float) -> CharacterTarget) {
        val frames = (seconds * 60).toInt()
        val runs = (0 until ThemeModels.COUNT).map { i ->
            val rt = SceneRuntime(seed = 3)
            var target = at(0f)
            for (f in 0 until frames) { target = at(f / 60f); rt.step(1f / 60f, target, options(i), 1_000_000L + f * 16L) }
            rt to target
        }
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            val cache = remember { TextCache() }
            Column(Modifier.fillMaxSize()) {
                runs.forEachIndexed { i, (rt, target) -> Cell(i) { Canvas(Modifier.fillMaxSize()) { rt.draw(this, target, options(i), tm, cache) } } }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Cell(i: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        Box(Modifier.fillMaxWidth().aspectRatio(8f / 3f)) {
            content()
            BasicText(
                ThemeModels.tag(i),
                Modifier.padding(start = 12.dp, top = 10.dp).background(Color(0xD9101316), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                style = TextStyle(color = Color.White, fontSize = 15.sp),
            )
        }
    }

    private fun parked(theme: SceneTheme) = kit(theme, "fresh_${theme.name.lowercase()}_parked", 4f) { KitShots.parked() }
    private fun firstDrive(theme: SceneTheme) = kit(theme, "fresh_${theme.name.lowercase()}_drive", 20f) { t ->
        KitShots.driving(KitShots.car(50f), Journey(active = true, tripSeq = 1, tripKm = 50f * t / 3600f, stars = 1, starSeq = 1))
    }

    @Test fun kartParked() = kart("fresh_kart_parked", 3f) { CharacterTarget(sizeMode = SizeMode.FULL, kart = KartState(driving = false), journey = Journey()) }
    @Test fun kartDrive() = kart("fresh_kart_drive", 20f) { t ->
        CharacterTarget(gaitSpeedKmh = 50f, sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true),
            journey = Journey(active = true, tripSeq = 1, tripKm = 50f * t / 3600f, stars = 1, starSeq = 1))
    }

    private fun calibre(name: String, seconds: Float, at: (Float) -> CharacterTarget) {
        val runtimes = (0 until ThemeModels.COUNT).map { i ->
            io.vroomi.character.render.calibre.CalibreRuntime(41f).also { rt ->
                val frames = (seconds * 60).toInt()
                val start = DEFAULT_NOW_MS - (seconds * 1000).toLong()
                for (f in 0 until frames) rt.step(1f / 60f, at(f / 60f), options(i), io.vroomi.character.calibre.CalibreFinish.AUTO, start + f * 1000L / 60)
            }
        }
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            val d = androidx.compose.ui.platform.LocalDensity.current
            val text = remember { io.vroomi.character.render.calibre.CalibreText(tm, d) }
            Column(Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt -> Cell(i) { Canvas(Modifier.fillMaxSize()) { rt.drawCalibre(this, tm, text, DEFAULT_NOW_MS, java.time.ZoneId.of("Asia/Seoul")) } } }
            }
        }
    }

    private fun engine(name: String, seconds: Float, at: (Float) -> CharacterTarget) {
        val runtimes = (0 until ThemeModels.COUNT).map { i ->
            io.vroomi.character.render.engine.EngineRuntime().also { rt ->
                val frames = (seconds * 60).toInt()
                val start = DEFAULT_NOW_MS - (seconds * 1000).toLong()
                for (f in 0 until frames) rt.step(1f / 60f, at(f / 60f), options(i), io.vroomi.character.engine.EnginePowertrain.AUTO, io.vroomi.character.engine.Powertrain.ICE, start + f * 1000L / 60)
            }
        }
        paparazzi.snapshot(name) {
            val tm = rememberTextMeasurer()
            Column(Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt -> Cell(i) { Canvas(Modifier.fillMaxSize()) { rt.drawEngine(this, tm) } } }
            }
        }
    }

    @Test fun calibreParked() = calibre("fresh_calibre_parked", 4f) { KitShots.parked() }
    @Test fun engineParked() = engine("fresh_engine_parked", 4f) { KitShots.parked() }
    @Test fun musicBoxParked() = parked(SceneTheme.MUSIC_BOX)
    @Test fun plasmaParked() = parked(SceneTheme.PLASMA)
    @Test fun hangarParked() = parked(SceneTheme.HANGAR)
    @Test fun aquariumParked() = parked(SceneTheme.AQUARIUM)
    @Test fun terrariumParked() = parked(SceneTheme.TERRARIUM)
    @Test fun orbitParked() = parked(SceneTheme.ORBIT)
    @Test fun pixelParked() = parked(SceneTheme.PIXEL)
    @Test fun mapParked() = parked(SceneTheme.TREASURE_MAP)
    @Test fun paperParked() = parked(SceneTheme.PAPER)

    @Test fun musicBoxDrive() = firstDrive(SceneTheme.MUSIC_BOX)
    @Test fun plasmaDrive() = firstDrive(SceneTheme.PLASMA)
    @Test fun hangarDrive() = firstDrive(SceneTheme.HANGAR)
    @Test fun aquariumDrive() = firstDrive(SceneTheme.AQUARIUM)
    @Test fun terrariumDrive() = firstDrive(SceneTheme.TERRARIUM)
    @Test fun orbitDrive() = firstDrive(SceneTheme.ORBIT)
    @Test fun pixelDrive() = firstDrive(SceneTheme.PIXEL)
    @Test fun mapDrive() = firstDrive(SceneTheme.TREASURE_MAP)
    @Test fun paperDrive() = firstDrive(SceneTheme.PAPER)
}
