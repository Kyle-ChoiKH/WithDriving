package io.vroomi.character.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.vroomi.character.CharacterSkin
import io.vroomi.character.CharacterTarget
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.CalibreView
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.engine.Powertrain
import io.vroomi.character.render.calibre.CalibreRuntime
import io.vroomi.character.render.calibre.CalibreText
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.character.render.calibre.draw
import io.vroomi.character.render.engine.EngineRuntime
import io.vroomi.character.render.engine.draw
import io.vroomi.character.render.engine.settle
import io.vroomi.character.render.theme.LayerCache
import io.vroomi.character.render.theme.newThemeRuntime
import io.vroomi.character.render.theme.simulate
import java.time.ZoneId

/** What a still frame shows (a parked scene in the afternoon by default). */
data class StillSpec(
    val skin: CharacterSkin = CharacterSkin.VROOMI,
    val finish: CalibreFinish = CalibreFinish.AUTO,
    val powertrain: EnginePowertrain = EnginePowertrain.AUTO,
    val hourOfDay: Float = 14f,
    val season: Season = Season.SUMMER,
    val seconds: Float = 1.5f,
    val nowMs: Long = DEFAULT_NOW_MS,
    /** > 0: the car is driving at this speed (km/h) for the whole simulated time (reels). */
    val speedKmh: Float = 0f,
    /** The entrance accent to draw ([SceneOptions.intro]); 1 = the normal frame. */
    val intro: Float = 1f,
    /** The upgrade step to draw ([SceneOptions.model], 0..4 = Lv.1..5). */
    val model: Int = 0,
    /** The theme's colourway ([io.vroomi.character.theme.ThemeColorways], 0 = its own colours). */
    val color: Int = 0,
)

/**
 * Renders a still frame of any theme into an [ImageBitmap] without composition, with the same runtimes and
 * painters the live scenes and the *SceneStill composables use. The app keeps a few of these as hero stills
 * (gallery cards, swipes). One instance is used from one thread at a time; every call builds its own runtime,
 * so a still never touches the live scene's state.
 */
class SceneStillRenderer(fontFamilyResolver: FontFamily.Resolver, private val density: Density) {
    private val tm = TextMeasurer(fontFamilyResolver, density, LayoutDirection.Ltr)
    private val zone = ZoneId.of("Asia/Seoul")

    fun render(theme: SceneTheme, w: Int, h: Int, spec: StillSpec = StillSpec()): ImageBitmap {
        val img = ImageBitmap(w.coerceAtLeast(2), h.coerceAtLeast(2))
        LayerCache.scoped {
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) {
                val m = io.vroomi.character.theme.ThemeColorways.matrix(theme, spec.color)
                if (m == null) drawStill(this, theme, spec)
                else {
                    val paint = androidx.compose.ui.graphics.Paint().apply { colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m)) }
                    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
                    drawStill(this, theme, spec)
                    drawContext.canvas.restore()
                }
            }
        }
        return img
    }

    private fun drawStill(ds: DrawScope, theme: SceneTheme, spec: StillSpec) {
        val target = if (spec.speedKmh > 0f) CharacterTarget(
            gait = io.vroomi.character.Gait.JOG, gaitSpeedKmh = spec.speedKmh, sizeMode = SizeMode.AMBIENT,
            kart = io.vroomi.character.KartState(driving = true),
            car = io.vroomi.character.CarSignals(speedKmh = spec.speedKmh, accelMps2 = 0f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true, safetyWarningActive = false),
            journey = io.vroomi.character.Journey(active = true, tripSeq = 1),
        ) else CharacterTarget(sizeMode = SizeMode.FULL)
        val options = SceneOptions(hourOfDay = spec.hourOfDay, season = spec.season, skin = spec.skin, intro = spec.intro, model = spec.model)
        val frames = (spec.seconds * 60).toInt()
        val start = spec.nowMs - (spec.seconds * 1000).toLong()
        when (theme) {
            SceneTheme.KART -> {
                val r = SceneRuntime(seed = 3)
                for (i in 0 until frames) r.step(1f / 60f, target, options, start + i * 1000L / 60)
                r.draw(ds, target, options, tm, TextCache())
            }
            SceneTheme.CALIBRE -> {
                val r = CalibreRuntime()
                r.setView(CalibreView.CASEBACK)
                for (i in 0 until frames) r.step(1f / 60f, target, options, spec.finish, start + i * 1000L / 60)
                r.draw(ds, tm, CalibreText(tm, density), spec.nowMs, zone)
            }
            SceneTheme.ENGINE -> {
                val r = EngineRuntime()
                val forced = if (spec.powertrain == EnginePowertrain.EV) Powertrain.EV else null
                for (i in 0 until frames) r.step(1f / 60f, target, options, spec.powertrain, forced, start + i * 1000L / 60)
                settle(r)
                r.draw(ds, tm)
            }
            else -> {
                val r = newThemeRuntime(theme) ?: return
                r.simulate(spec.seconds, options, spec.nowMs) { target }
                r.draw(ds, tm)
            }
        }
    }
}

@Composable
fun rememberSceneStillRenderer(): SceneStillRenderer {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    return remember(resolver, density) { SceneStillRenderer(resolver, density) }
}
