package io.vroomi.character.render.treasuremap

import io.vroomi.character.Biome
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.treasuremap.TreasureMapSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.route.LiveRoute
import org.junit.After
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

/** A scripted route (metres east, north) shared by the map snapshots and the demo. */
val MAP_ROUTE = floatArrayOf(0f, 0f, 400f, 0f, 700f, 150f, 900f, 450f, 1000f, 800f, 1300f, 1000f, 1700f, 1050f, 2000f, 900f,
    2200f, 600f, 2600f, 500f, 3000f, 650f, 3300f, 900f, 3500f, 1300f, 3600f, 1700f)

/** Renders the treasure map (docs/screenshots/map_*). */
class TreasureMapSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    @After fun clear() { LiveRoute.demoRoute = null }

    // ---- the upgrade ladder (spec 5.7)
    private fun lv(name: String, model: Int, o: SceneOptions = KitShots.day, seconds: Float = 150f, block: io.vroomi.character.CarSignals.() -> io.vroomi.character.CarSignals = { this }) {
        LiveRoute.demoRoute = MAP_ROUTE
        KitShots.script<TreasureMapSim>(paparazzi, SceneTheme.TREASURE_MAP, name, seconds, o.copy(model = model)) { t ->
            driving(car(60f) { block() }, Journey(active = true, tripSeq = 4, tripKm = 60f * t / 3600f, stars = (t / 40f).toInt(), starSeq = (t / 40f).toInt(), milestoneKm = (60f * t / 3600f).toInt(), biome = Biome.PARK))
        }
    }
    @Test fun lv1Day() = lv("map_lv1_day", 0)
    @Test fun lv2Day() = lv("map_lv2_day", 1)
    @Test fun lv3Day() = lv("map_lv3_day", 2)
    @Test fun lv4Day() = lv("map_lv4_day", 3)
    @Test fun lv5Day() = lv("map_lv5_day", 4)
    @Test fun lv5Night() = lv("map_lv5_night", 4, KitShots.night) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("map_lv3_blinker_left", 2) { copy(turnSignal = io.vroomi.core.vehicle.model.TurnSignal.LEFT) }
    @Test fun lv3Brake() = lv("map_lv3_brake", 2) { copy(brakePressed = true) }
    /** Right after setting off at step 1: the graticule, the rhumb net, the stains and the departure mark are there. */
    @Test fun startLv1() = lv("map_start_lv1", 0, seconds = 2f)

    /** A drive along [MAP_ROUTE] at [kmh] for [s] seconds; stars every [starEvery] s; biome by distance. */
    private fun journey(t: Float, kmh: Float, starEvery: Float = 0f, combos: Int = 0, dest: Int? = null): Journey {
        val km = kmh * t / 3600f
        val stars = if (starEvery > 0f) (t / starEvery).toInt() else 0
        val biome = when { km < 0.8f -> Biome.CITY; km < 1.6f -> Biome.PARK; km < 2.4f -> Biome.FIELD; km < 3.2f -> Biome.SEASIDE; else -> Biome.MOUNTAIN }
        return Journey(active = true, tripSeq = 4, tripKm = km, stars = stars, starSeq = stars, comboSeq = combos, milestoneKm = km.toInt(), biome = biome, destinationM = dest)
    }

    private fun shot(name: String, s: Float, o: SceneOptions = KitShots.day, demo: Boolean = true, prepare: (TreasureMapSim) -> Unit = {}, at: (Float) -> CharacterTarget) {
        LiveRoute.demoRoute = if (demo) MAP_ROUTE else null
        KitShots.script(paparazzi, SceneTheme.TREASURE_MAP, name, s, o, prepare, at = at)
    }

    @Test fun cityStart() = shot("map_city_start", 30f) { t -> driving(car(50f) { copy(turnSignal = if (t > 27f) TurnSignal.RIGHT else TurnSignal.NONE) }, journey(t, 50f)) }
    @Test fun longTrailPark() = shot("map_long_trail_park", 120f) { t -> driving(car(60f), journey(t, 60f)) }
    @Test fun starCombo() = shot("map_star_x_combo", 150f) { t -> driving(car(60f), journey(t, 60f, starEvery = 30f, combos = if (t > 140f) 1 else 0)) }
    @Test fun restStamp() = shot("map_rest_stamp", 110f) { t ->
        val k = if (t in 40f..80f) 0f else 50f
        val tt = if (t < 40f) t else if (t < 80f) 40f else t - 40f
        driving(car(k), journey(tt, 50f))
    }
    @Test fun rainSmudge() = shot("map_rain_smudge", 60f) { t -> driving(car(55f) { copy(wiperActive = true) }, journey(t, 55f)) }
    @Test fun nightCandle() = shot("map_night_candle", 90f, KitShots.night) { t -> driving(car(60f) { copy(headlightsOn = true) }, journey(t, 60f, starEvery = 40f)) }
    @Test fun destinationFlag() = shot("map_destination_flag", 160f) { t -> driving(car(70f), journey(t, 70f, starEvery = 50f, dest = 600)) }
    @Test fun arrivalRolled() = shot("map_arrival_rolled", 214f) { t ->
        val drive = t < 205f
        val c = car(if (drive) 60f else 0f, gear = if (drive) GearState.DRIVE else GearState.PARK) { copy(moving = drive, arrivalSeq = if (t > 206f) 1 else 0) }
        driving(c, journey(t.coerceAtMost(205f), 60f, starEvery = 45f)).copy(kart = io.vroomi.character.KartState(driving = drive))
    }
    @Test fun deadReckoning() = shot("map_dead_reckoning_doodle", 150f, demo = false) { t ->
        val turn = when { t in 30f..34f -> TurnSignal.LEFT; t in 70f..74f -> TurnSignal.RIGHT; t in 100f..103f -> TurnSignal.LEFT; else -> TurnSignal.NONE }
        driving(car(55f) { copy(turnSignal = turn) }, journey(t, 55f))
    }
    @Test fun corner() = shot("map_corner", 60f) { t -> driving(car(55f), journey(t, 55f)).copy(sizeMode = SizeMode.CORNER) }
    @Test fun calm() = shot("map_calm", 60f, SceneOptions(hourOfDay = 14f, calm = true)) { t -> driving(car(55f), journey(t, 55f)) }
    @Test fun safetyDim() = shot("map_safety_dim", 90f) { t -> driving(car(60f) { copy(safetyWarningActive = t > 88f) }, journey(t, 60f, starEvery = 30f)) }
    @Test fun introInkDraws() = shot("map_intro", 150f, SceneOptions(hourOfDay = 14f, intro = 0.45f)) { t -> driving(car(60f), journey(t, 60f, starEvery = 30f, combos = if (t > 140f) 1 else 0)) }
    /** Moving near the destination: the title and one note at 24 sp or more; the small labels have faded. */
    @Test fun moving() = shot("map_moving", 160f) { t -> driving(car(60f), journey(t, 60f, starEvery = 40f, dest = 700)) }
    /** Parked after a drive: place names, departure, scale bar and the cartouche note are all shown. */
    @Test fun parkedLabels() = shot("map_parked_labels", 164f) { t ->
        val drive = t < 160f
        driving(car(if (drive) 60f else 0f, gear = if (drive) GearState.DRIVE else GearState.PARK) { copy(moving = drive) }, journey(t.coerceAtMost(160f), 60f, starEvery = 40f))
            .copy(kart = io.vroomi.character.KartState(driving = drive))
    }
}

/** 1000x1000 details: ink on parchment, the compass rose. */
class TreasureMapDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()
    @After fun clear() { LiveRoute.demoRoute = null }

    @Test fun ink() {
        LiveRoute.demoRoute = MAP_ROUTE
        KitShots.script<TreasureMapSim>(paparazzi, SceneTheme.TREASURE_MAP, "map_detail_ink", 120f, zoom = 2.2f, fx = 0.5f, fy = 0.5f) { t ->
            driving(car(60f), Journey(active = true, tripSeq = 5, tripKm = 60f * t / 3600f, stars = (t / 30f).toInt(), starSeq = (t / 30f).toInt(), biome = Biome.PARK))
        }
    }

    @Test fun compass() {
        LiveRoute.demoRoute = MAP_ROUTE
        KitShots.script<TreasureMapSim>(paparazzi, SceneTheme.TREASURE_MAP, "map_detail_compass", 40f, zoom = 3f, fx = 0.86f, fy = 0.22f) { t ->
            driving(car(60f), Journey(active = true, tripSeq = 5, tripKm = 60f * t / 3600f))
        }
    }
}

/** The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875), labelled "Lv.n" top left. */
class TreasureMapLadderSnapshotTest {
    @get:Rule val paparazzi = app.cash.paparazzi.Paparazzi(
        deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = com.android.resources.Density.LOW, orientation = com.android.resources.ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @After fun clear() { LiveRoute.demoRoute = null }

    @Test fun ladder() {
        LiveRoute.demoRoute = MAP_ROUTE
        val runtimes = (0 until 5).map { i ->
            requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.TREASURE_MAP)).also { rt ->
                rt.simulate(150f, SceneOptions(hourOfDay = 14f, model = i), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                    driving(car(60f), Journey(active = true, tripSeq = 4, tripKm = 60f * t / 3600f, stars = (t / 40f).toInt(), starSeq = (t / 40f).toInt(), biome = Biome.PARK))
                }
            }
        }
        paparazzi.snapshot("map_ladder") {
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

/** The ladder's rule: every step keeps what the one before had. */
class MapStepTest {
    @Test fun stepsAreCumulative() {
        val has = (0 until 5).map { m -> listOf(MapStep.rose(m), MapStep.wash(m), MapStep.decor(m), MapStep.gold(m)).count { it } }
        org.junit.Assert.assertEquals(listOf(0, 1, 2, 3, 4), has)
    }
}
