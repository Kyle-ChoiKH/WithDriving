package io.vroomi.character.render.paper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.vroomi.character.Action
import io.vroomi.character.Biome
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.paper.PaperSim
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.render.theme.KitShots.parked
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import org.junit.Rule
import io.vroomi.character.render.theme.simulate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Test

/** Renders the risograph pop-up book (docs/screenshots/paper_*). */
class PaperSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    private fun o(hour: Float = 14f, calm: Boolean = false, intro: Float = 1f) = SceneOptions(hourOfDay = hour, calm = calm, intro = intro)
    private fun trip(t: Float, kmh: Float, biome: Biome = Biome.CITY, stars: Int = 2, combo: Int = 0) =
        Journey(active = true, tripSeq = 2, tripKm = kmh * t / 3600f, stars = stars, starSeq = stars, comboSeq = combo, biome = biome, milestoneKm = (kmh * t / 3600f).toInt())
    private fun shot(name: String, s: Float, opt: SceneOptions = o(), prepare: (PaperSim) -> Unit = {}, at: (Float) -> CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.PAPER, name, s, opt, prepare, at = at)

    // ---- the upgrade ladder (spec 5.9)
    private fun lv(name: String, model: Int, opt: SceneOptions = o(), block: io.vroomi.character.CarSignals.() -> io.vroomi.character.CarSignals = { this }) =
        shot(name, 8f, opt.copy(model = model)) { t -> driving(car(50f) { block() }, trip(t, 50f)) }
    @Test fun lv1Day() = lv("paper_lv1_day", 0)
    @Test fun lv2Day() = lv("paper_lv2_day", 1)
    @Test fun lv3Day() = lv("paper_lv3_day", 2)
    @Test fun lv4Day() = lv("paper_lv4_day", 3)
    @Test fun lv5Day() = lv("paper_lv5_day", 4)
    @Test fun lv5Night() = lv("paper_lv5_night", 4, o().copy(hourOfDay = 21.5f)) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("paper_lv3_blinker_left", 2) { copy(turnSignal = io.vroomi.core.vehicle.model.TurnSignal.LEFT) }
    @Test fun lv3Brake() = shot("paper_lv3_brake", 8.4f, o().copy(model = 2)) { t ->
        driving(car(if (t < 8f) 60f else 30f, if (t < 8f) 0f else -6f) { copy(brakePressed = t >= 8f, harshBrakeSeq = if (t >= 8f) 1 else 0) }, trip(t, 60f))
    }
    @Test fun lv4ParkedCloudTab() = shot("paper_lv4_parked_cloud_tab", 9f, o().copy(model = 3)) { parked() }

    @Test fun cityCruise() = shot("paper_city_cruise", 8f) { t -> driving(car(50f) { copy(turnSignal = if (t > 7.6f) TurnSignal.RIGHT else TurnSignal.NONE) }, trip(t, 50f)) }
    @Test fun parkRain() = shot("paper_park_rain_threads", 8f) { t -> driving(car(45f) { copy(wiperActive = true) }, trip(t, 45f, Biome.PARK)) }
    @Test fun fieldSunWheel() = shot("paper_field_sun_wheel", 8f) { t -> driving(car(90f) { copy(fanLevel = 7, drivingMode = DriveModeState.ECO) }, trip(t, 90f, Biome.FIELD)) }
    @Test fun seasideNightMoon() = shot("paper_seaside_night_moon", 8f, o(hour = 21.5f)) { t ->
        driving(car(50f) { copy(headlightsOn = true) }, trip(t, 50f, Biome.SEASIDE))
    }
    @Test fun mountainDay() = shot("paper_mountain_day", 8f) { t -> driving(car(60f), trip(t, 60f, Biome.MOUNTAIN)) }
    @Test fun confettiReward() = shot("paper_confetti_reward", 6.5f) { t -> driving(car(45f), trip(t, 45f, Biome.PARK, stars = if (t > 6f) 3 else if (t > 5.4f) 2 else 1)) }
    @Test fun garlandCombo() = shot("paper_garland_combo", 6f) { t -> driving(car(45f), trip(t, 45f, Biome.MOUNTAIN, combo = if (t > 3f) 1 else 0)).copy(action = Action.CELEBRATE, actionStartedMs = 1L) }
    @Test fun biomeGutterRise() = shot("paper_biome_gutter_rise", 3.6f) { t -> driving(car(50f), trip(t, 50f, if (t > 3f) Biome.PARK else Biome.CITY)) }
    @Test fun pageFlipParked() = shot("paper_page_flip_parked", 0.55f, prepare = { it.requestFlip() }) { parked() }
    @Test fun harshTip() = shot("paper_harsh_tip", 6.25f) { t ->
        driving(car(if (t < 6f) 60f else 30f, if (t < 6f) 0f else -6f) { copy(brakePressed = t >= 6f, harshBrakeSeq = if (t >= 6f) 1 else 0) }, trip(t, 60f))
    }
    @Test fun arrivalClosed() = shot("paper_arrival_closed", 6f) { t -> parked(car(0f, gear = GearState.PARK) { copy(moving = false, arrivalSeq = if (t > 1f) 1 else 0) }) }
    @Test fun acSnow() = shot("paper_ac_snowflakes", 5f) { t -> driving(car(45f) { copy(acOn = true, setTempC = 18f) }, trip(t, 45f, Biome.MOUNTAIN)) }
    /** CORNER while moving: the cut-out title would render under 24 sp, so it fades out (it returns when parked). */
    @Test fun corner() = shot("paper_corner", 5f) { t -> driving(car(50f), trip(t, 50f)).copy(sizeMode = SizeMode.CORNER) }
    /** CORNER while parked: the title is back. */
    @Test fun cornerParked() = shot("paper_corner_parked", 3f) { parked().copy(sizeMode = SizeMode.CORNER) }
    @Test fun calm() = shot("paper_calm", 5f, o(calm = true)) { t -> driving(car(50f), trip(t, 50f)) }
    @Test fun introStandUp() = shot("paper_intro_stand_up", 3f, o(intro = 0.55f)) { t -> driving(car(50f), trip(t, 50f, Biome.PARK)) }

    /** The director sends the scene to CORNER during a safety warning; the book freezes and dims to 70 %. */
    @Test fun safetyFreeze() = shot("paper_safety_freeze", 4f) { t ->
        driving(car(50f) { copy(safetyWarningActive = t > 2f) }, trip(t, 50f)).let { if (t > 2f) it.copy(sizeMode = SizeMode.CORNER) else it }
    }
}

/** The pop-up mechanics up close, and the crane on the car's roof. */
class PaperDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()

    @Test fun popupMechanics() = paparazzi.snapshot("paper_popup_mechanics") {
        val painter = PaperPainter()
        Canvas(Modifier.fillMaxSize()) {
            val f = ThemeFrame().also { it.k = 1.2f; it.density = this }
            painter.drawMechanicsSheet(this, f)
        }
    }

    @Test fun crane() = KitShots.script<PaperSim>(paparazzi, SceneTheme.PAPER, "paper_detail_crane", 4f, SceneOptions(hourOfDay = 14f), zoom = 2.6f, fx = 0.3f, fy = 0.42f) {
        driving(car(40f) { copy(turnSignal = TurnSignal.LEFT) }, Journey(active = true, tripSeq = 3, biome = Biome.PARK))
    }
}

/** The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), labelled "Lv.n" top left. */
class PaperLadderSnapshotTest {
    @get:Rule val paparazzi = app.cash.paparazzi.Paparazzi(
        deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = com.android.resources.Density.LOW, orientation = com.android.resources.ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @Test fun ladder() {
        val runtimes = (0 until 5).map { i ->
            requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.PAPER)).also { rt ->
                rt.simulate(8f, SceneOptions(hourOfDay = 14f, model = i), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                    driving(car(50f), io.vroomi.character.Journey(active = true, tripSeq = 2, tripKm = 50f * t / 3600f, stars = 2, starSeq = 2))
                }
            }
        }
        paparazzi.snapshot("paper_ladder") {
            val tm = androidx.compose.ui.text.rememberTextMeasurer()
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                runtimes.forEachIndexed { i, rt ->
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().aspectRatio(8f / 3f)) {
                        androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) { rt.draw(this, tm) }
                        androidx.compose.foundation.text.BasicText(
                            io.vroomi.character.theme.ThemeModels.tag(i),
                            androidx.compose.ui.Modifier.padding(start = 12.dp, top = 10.dp).background(androidx.compose.ui.graphics.Color(0xD9101316), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                            style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }
}

/** The ladder's rules. */
class PaperStepTest {
    @Test fun stepsAreCumulative() {
        val has = (0 until 5).map { m -> listOf(PaperStep.threeInks(m), PaperStep.farPlane(m), PaperStep.pullTab(m), PaperStep.special(m)).count { it } }
        org.junit.Assert.assertEquals(listOf(0, 1, 2, 3, 4), has)
    }

    @Test fun pullTabOnlyFromStepFour() {
        org.junit.Assert.assertEquals(listOf(false, false, false, true, true), (0 until 5).map { PaperStep.pullTab(it) })
    }

    /** The cloud strip is pulled only while parked (the page-flip rule): driving leaves it where it was. */
    @Test fun cloudTabMovesOnlyWhileParked() {
        fun slideAfter(parkedCar: Boolean): Float {
            val rt = requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.PAPER))
            rt.simulate(12f, SceneOptions(hourOfDay = 14f, model = 3), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                if (parkedCar) parked() else driving(car(50f), io.vroomi.character.Journey(active = true, tripSeq = 2, tripKm = 50f * t / 3600f))
            }
            return (rt.sim as PaperSim).cloudSlide
        }
        org.junit.Assert.assertEquals(PaperSim().cloudSlide, slideAfter(parkedCar = false), 1e-6f)
        org.junit.Assert.assertTrue(kotlin.math.abs(slideAfter(parkedCar = true) - PaperSim().cloudSlide) > 0.05f)
    }

    @Test fun inksFollowTheStep() {
        PaperInk.use(0); org.junit.Assert.assertEquals(PaperInk.RED, PaperInk.PINK); org.junit.Assert.assertEquals(PaperInk.RED_TINT, PaperInk.YELLOW)
        PaperInk.use(2); org.junit.Assert.assertEquals(PaperInk.RED, PaperInk.PINK); org.junit.Assert.assertEquals(PaperInk.YELLOW_INK, PaperInk.YELLOW)
        PaperInk.use(4); org.junit.Assert.assertEquals(PaperInk.FLUO, PaperInk.PINK)
    }
}
