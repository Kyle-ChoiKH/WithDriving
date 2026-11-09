package io.vroomi.character.render.calibre

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.vroomi.character.render.Season
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.theme.ThemeModels
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import org.junit.Test

/** Renders the Calibre theme to PNG on the JVM (docs/screenshots/calibre_*). */
class CalibreSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private val day = SceneOptions(hourOfDay = 10.2f, season = Season.AUTUMN)
    private val night = SceneOptions(hourOfDay = 21.5f, season = Season.AUTUMN)

    private fun car(kmh: Float, fan: Int = 2, gear: GearState = GearState.DRIVE, block: CarSignals.() -> CarSignals = { this }) =
        CarSignals(speedKmh = kmh, accelMps2 = 0f, gear = gear, moving = kmh > 0.5f || gear == GearState.DRIVE, fanLevel = fan, fanMax = 8,
            acOn = false, maxAcOn = false, setTempC = 22f, hvacAutoOn = false, defrostOn = false, wiperActive = false, headlightsOn = false,
            turnSignal = TurnSignal.NONE, evCharging = false, evBatteryPct = 72, drivingMode = DriveModeState.NORMAL, safetyWarningActive = false).block()

    private fun driving(c: CarSignals, j: Journey = Journey(active = true, tripSeq = 1, stars = 4, starSeq = 4)) =
        CharacterTarget(sizeMode = SizeMode.AMBIENT, kart = KartState(driving = true), car = c, journey = j)

    private fun parked(c: CarSignals = car(0f, gear = GearState.PARK).copy(moving = false)) =
        CharacterTarget(sizeMode = SizeMode.FULL, car = c, journey = Journey())

    private fun shot(
        name: String, t: CharacterTarget, o: SceneOptions = day, finish: CalibreFinish = CalibreFinish.AUTO,
        view: CalibreView = CalibreView.CASEBACK, seconds: Float = 3f, reserve: Float = 41f,
    ) = paparazzi.snapshot(name) {
        CalibreSceneStill(t, o, finish, Modifier.fillMaxSize(), view, seconds, reserveH = reserve)
    }

    @Test fun casebackDayCruise() = shot("caseback_day_cruise", driving(car(72f)))
    @Test fun casebackNight() = shot("caseback_night_lume", driving(car(55f) { copy(headlightsOn = true) }), night)
    @Test fun casebackRainCrystal() = shot("caseback_rain_crystal", driving(car(40f) { copy(wiperActive = true, headlightsOn = false) }), seconds = 16f)
    @Test fun casebackDefrost() = script("caseback_defrost_clearing", 17.3f) { ts ->
        driving(car(40f) { copy(wiperActive = ts < 15f, defrostOn = ts >= 16f) })
    }

    /** A scene that changes over time (seconds -> target), drawn at the end. */
    private fun script(name: String, seconds: Float, o: SceneOptions = day, at: (Float) -> CharacterTarget) {
        val rt = CalibreRuntime(41f)
        val frames = (seconds * 60).toInt()
        val start = DEFAULT_NOW_MS - (seconds * 1000).toLong()
        var last = at(0f)
        for (i in 0 until frames) { last = at(i / 60f); rt.step(1f / 60f, last, o, CalibreFinish.AUTO, start + i * 1000L / 60) }
        paparazzi.snapshot(name) {
            val tm = androidx.compose.ui.text.rememberTextMeasurer()
            val d = androidx.compose.ui.platform.LocalDensity.current
            val text = androidx.compose.runtime.remember { CalibreText(tm, d) }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm, text, DEFAULT_NOW_MS, java.time.ZoneId.of("Asia/Seoul")) }
        }
    }
    @Test fun casebackHvacWinding() = shot("caseback_hvac_winding", parked(car(0f, fan = 7, gear = GearState.PARK) { copy(moving = false, acOn = true, setTempC = 18f) }), seconds = 5f, reserve = 30f)
    @Test fun casebackChrono() = shot("caseback_chrono_running", driving(car(64f), Journey(active = true, tripSeq = 1, stars = 9, starSeq = 9)), seconds = 125f)
    @Test fun casebackEvWinder() = shot("caseback_ev_winder", parked(car(0f, fan = 0, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = 82) }), seconds = 4f)
    @Test fun casebackReverse() = shot("caseback_reverse", driving(car(7f, gear = GearState.REVERSE)), seconds = 3f)
    @Test fun casebackBlinkerLeft() = shot("caseback_blinker_left", driving(car(30f) { copy(turnSignal = TurnSignal.LEFT) }), seconds = 0.72f)
    @Test fun casebackWarmSport() = shot("caseback_sport_warm", driving(car(95f) { copy(drivingMode = DriveModeState.SPORT, setTempC = 27f) }), seconds = 4f)
    @Test fun dialDay() = shot("dial_day", parked(), view = CalibreView.DIAL)
    @Test fun dialNight() = shot("dial_night", parked(car(0f, gear = GearState.PARK) { copy(moving = false, headlightsOn = true, hvacAutoOn = true) }), night, view = CalibreView.DIAL)
    @Test fun finishYellow() = shot("finish_yellow_gold", parked(), finish = CalibreFinish.YELLOW_GOLD)
    @Test fun finishRose() = shot("finish_rose_gold", parked(), finish = CalibreFinish.ROSE_GOLD, view = CalibreView.DIAL)
    @Test fun finishRhodium() = shot("finish_rhodium_blue", parked(), finish = CalibreFinish.RHODIUM_BLUE)
    @Test fun finishRhodiumDial() = shot("finish_rhodium_blue_dial", parked(), finish = CalibreFinish.RHODIUM_BLUE, view = CalibreView.DIAL)
    @Test fun finishBlack() = shot("finish_black_dlc", parked(), finish = CalibreFinish.BLACK_DLC)
    @Test fun finishBlackDial() = shot("finish_black_dlc_dial", parked(), finish = CalibreFinish.BLACK_DLC, view = CalibreView.DIAL)
    @Test fun finishAutoEco() = shot("finish_auto_eco", driving(car(48f) { copy(drivingMode = DriveModeState.ECO) }))
    @Test fun cornerSize() = shot("corner_size", driving(car(50f) { copy(turnSignal = TurnSignal.HAZARD) }).copy(sizeMode = SizeMode.CORNER), seconds = 3f)
    @Test fun calmMode() = shot("calm_mode", driving(car(110f)), day.copy(calm = true))
    @Test fun safetyDim() = script("safety_dim", 4f) { ts -> driving(car(60f) { copy(safetyWarningActive = ts > 2.5f) }) }
    @Test fun introFrame() = shot("intro_frame", parked(), day.copy(intro = 0.45f), view = CalibreView.DIAL)
    @Test fun parkedQuiet() = shot("parked_quiet", CharacterTarget(sizeMode = SizeMode.FULL))
    @Test fun comboBevel() = script("combo_bevel_glint", 6.25f) { ts ->
        driving(car(70f), Journey(active = true, tripSeq = 1, stars = if (ts > 5.6f) 5 else 4, starSeq = if (ts > 5.6f) 5 else 4, comboSeq = if (ts > 5.7f) 1 else 0))
    }

    // ---------------------------------------------------------------- the upgrade ladder (spec 5.1)

    /** Lv.n ([model] = n - 1) in the afternoon, driving at 50 km/h with three stars, drawn after 8 s. */
    private fun lv(model: Int, hour: Float = 14f) = SceneOptions(hourOfDay = hour, model = model)
    private fun cruise(block: CarSignals.() -> CarSignals = { this }) = KitShots.driving(KitShots.car(50f).block())

    @Test fun lv1Day() = script("calibre_lv1_day", 8f, lv(0)) { cruise() }
    @Test fun lv2Day() = script("calibre_lv2_day", 8f, lv(1)) { cruise() }
    @Test fun lv3Day() = script("calibre_lv3_day", 8f, lv(2)) { cruise() }
    @Test fun lv4Day() = script("calibre_lv4_day", 8f, lv(3)) { cruise() }
    @Test fun lv5Day() = script("calibre_lv5_day", 8f, lv(4)) { cruise() }
    @Test fun lv5Night() = script("calibre_lv5_night", 8f, lv(4, 21.5f)) { cruise { copy(headlightsOn = true) } }
    /** The left blinker: the left hammer has just struck its gong (the second strike, 0.67 s after the first). */
    @Test fun lv3BlinkerLeft() = script("calibre_lv3_blinker_left", 8f, lv(2)) { ts -> cruise { if (ts >= 7.30f) copy(turnSignal = TurnSignal.LEFT) else this } }
    /** Braking from 50 km/h for the last second: the balance's cap jewel catches the light, the glass reflection slides. */
    @Test fun lv3Brake() = script("calibre_lv3_brake", 8f, lv(2)) { ts ->
        if (ts < 7f) cruise() else KitShots.driving(KitShots.car(50f - (ts - 7f) * 14f, a = -4f) { copy(brakePressed = true) })
    }

    /** Every step and finish draws (the finish is independent of the step). */
    @Test fun everyFinishAtEveryStep() {
        val w = 960; val h = 360
        val tm = androidx.compose.ui.text.TextMeasurer(androidx.compose.ui.text.font.createFontFamilyResolver(paparazzi.context), androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr)
        val text = CalibreText(tm, androidx.compose.ui.unit.Density(1f))
        for (model in 0 until 5) for (finish in CalibreFinish.entries) for (view in CalibreView.entries) {
            val rt = CalibreRuntime(41f)
            rt.setView(view)
            for (i in 0 until 30) rt.step(1f / 60f, parked(), lv(model), finish, DEFAULT_NOW_MS - 500L + i * 16L)
            val img = androidx.compose.ui.graphics.ImageBitmap(w, h)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(img), androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat())) {
                rt.draw(this, tm, text, DEFAULT_NOW_MS, java.time.ZoneId.of("Asia/Seoul"))
            }
            org.junit.Assert.assertEquals("Lv.${model + 1} $finish $view", model, rt.model)
        }
    }

    /**
     * The bright bench: the gallery still's mean relative luminance (as ThemeLuminanceSnapshotTest measures it) is at
     * least 0.6 by day and 0.3 at night, at every step. Writes build/reports/calibre_luminance.txt.
     */
    @Test fun brightBench() {
        val renderer = io.vroomi.character.render.SceneStillRenderer(androidx.compose.ui.text.font.createFontFamilyResolver(paparazzi.context), androidx.compose.ui.unit.Density(1f))
        val out = StringBuilder("calibre still luminance (480 x 180)\n")
        for (model in 0 until 5) {
            val day = meanLuminance(renderer.render(io.vroomi.character.calibre.SceneTheme.CALIBRE, 480, 180, io.vroomi.character.render.StillSpec(model = model)))
            val night = meanLuminance(renderer.render(io.vroomi.character.calibre.SceneTheme.CALIBRE, 480, 180, io.vroomi.character.render.StillSpec(model = model, hourOfDay = 21.5f)))
            out.append("Lv.%d day %.3f night %.3f\n".format(model + 1, day, night))
            org.junit.Assert.assertTrue("Lv.${model + 1} day $day", day >= 0.6f)
            org.junit.Assert.assertTrue("Lv.${model + 1} night $night", night >= 0.3f)
        }
        java.io.File("build/reports").mkdirs()
        java.io.File("build/reports/calibre_luminance.txt").writeText(out.toString())
        println(out)
    }

    /** Draw time per step at 1920 x 720 on this JVM (layoutlib, not the car's GPU). Writes build/reports/calibre_step_cost.txt. */
    @Test fun drawCostByStep() {
        val w = 1920; val h = 720
        val d = androidx.compose.ui.unit.Density(1.5f)
        val tm = androidx.compose.ui.text.TextMeasurer(androidx.compose.ui.text.font.createFontFamilyResolver(paparazzi.context), d, androidx.compose.ui.unit.LayoutDirection.Ltr)
        val text = CalibreText(tm, d)
        val img = androidx.compose.ui.graphics.ImageBitmap(w, h)
        val s = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        val ms = FloatArray(5)
        for (model in 0 until 5) {
            val rt = CalibreRuntime(41f)
            for (i in 0 until 120) rt.step(1f / 60f, cruise(), lv(model), CalibreFinish.AUTO, DEFAULT_NOW_MS - 2000L + i * 16L)
            fun frame(k: Int) = s.draw(d, androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat())) {
                rt.draw(this, tm, text, DEFAULT_NOW_MS + k * 16L, java.time.ZoneId.of("Asia/Seoul"))
            }
            repeat(3) { frame(it) } // layers built, JIT warm
            val n = 12
            val t0 = System.nanoTime()
            for (k in 0 until n) frame(10 + k)
            ms[model] = (System.nanoTime() - t0) / 1e6f / n
        }
        val out = ms.withIndex().joinToString("\n", "calibre draw cost at 1920 x 720 (JVM layoutlib, not the car)\n") { (i, v) -> "Lv.%d %.1f ms".format(i + 1, v) }
        java.io.File("build/reports").mkdirs()
        java.io.File("build/reports/calibre_step_cost.txt").writeText(out + "\n")
        println(out)
        org.junit.Assert.assertTrue(out, ms[4] < ms[0] * 2f + 10f)
    }

    private fun meanLuminance(img: androidx.compose.ui.graphics.ImageBitmap): Float {
        val px = IntArray(img.width * img.height)
        img.readPixels(px)
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        var sum = 0.0
        for (c in px) sum += 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
        return (sum / px.size).toFloat()
    }
}

/**
 * The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), each a small copy of
 * the 1920 x 720 driving scene (density 0.75, so the type keeps its proportion), labelled "Lv.n" top left.
 */
class CalibreLadderSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = Density.LOW, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun ladder(name: String, seconds: Float, options: (Int) -> SceneOptions, at: (Float) -> CharacterTarget) {
        val runtimes = (0 until 5).map { model ->
            CalibreRuntime(41f).also { rt ->
                val frames = (seconds * 60).toInt()
                val start = DEFAULT_NOW_MS - (seconds * 1000).toLong()
                for (i in 0 until frames) rt.step(1f / 60f, at(i / 60f), options(model), CalibreFinish.AUTO, start + i * 1000L / 60)
            }
        }
        paparazzi.snapshot(name) {
            val tm = androidx.compose.ui.text.rememberTextMeasurer()
            val d = androidx.compose.ui.platform.LocalDensity.current
            val text = androidx.compose.runtime.remember { CalibreText(tm, d) }
            Column(Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt ->
                    Box(Modifier.fillMaxWidth().aspectRatio(8f / 3f)) {
                        Canvas(Modifier.fillMaxSize()) { rt.draw(this, tm, text, DEFAULT_NOW_MS, java.time.ZoneId.of("Asia/Seoul")) }
                        BasicText(
                            ThemeModels.tag(i),
                            Modifier.padding(start = 12.dp, top = 10.dp)
                                .background(Color(0xD92A2622), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }

    @Test fun ladder() = ladder("calibre_ladder", 8f, { SceneOptions(hourOfDay = 14f, model = it) }) { KitShots.driving(KitShots.car(50f)) }
}
