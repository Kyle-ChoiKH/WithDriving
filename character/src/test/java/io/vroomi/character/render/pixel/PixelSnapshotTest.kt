package io.vroomi.character.render.pixel

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.Biome
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.SizeMode
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.pixel.PixelColors
import io.vroomi.character.pixel.PixelMechanics
import io.vroomi.character.pixel.PixelModels
import io.vroomi.character.pixel.PixelPalette
import io.vroomi.character.pixel.sprites.PilotSprites
import io.vroomi.character.pixel.sprites.RoverSprite
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
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

/** Renders Pixel World (docs/screenshots/pixel_*). */
class PixelSnapshotTest {
    @get:Rule val paparazzi = KitShots.pixelWide()

    private fun o(hour: Float = 14f, calm: Boolean = false, intro: Float = 1f) = SceneOptions(hourOfDay = hour, calm = calm, intro = intro)
    private fun trip(t: Float, kmh: Float, biome: Biome = Biome.CITY, stars: Int = 0, combo: Int = 0, km: Int = 0, dest: Int? = null) =
        Journey(active = true, tripSeq = 2, tripKm = kmh * t / 3600f, stars = stars, starSeq = stars, comboSeq = combo, biome = biome, milestoneKm = km, destinationM = dest)
    private fun shot(name: String, s: Float, opt: SceneOptions = o(), prepare: (PixelMechanics) -> Unit = {}, at: (Float) -> CharacterTarget) =
        KitShots.script(paparazzi, SceneTheme.PIXEL, name, s, opt, prepare, at = at)

    // ---- the upgrade ladder (spec 5.10): the vehicle of each step
    private fun lv(name: String, model: Int, hour: Float = 14f, block: io.vroomi.character.CarSignals.() -> io.vroomi.character.CarSignals = { this }) =
        shot(name, 6f, o(hour).copy(model = model)) { t -> driving(car(50f) { block() }, trip(t, 50f)) }
    @Test fun lv1Day() = lv("pixel_lv1_day", 0)
    @Test fun lv2Day() = lv("pixel_lv2_day", 1)
    @Test fun lv3Day() = lv("pixel_lv3_day", 2)
    @Test fun lv4Day() = lv("pixel_lv4_day", 3)
    @Test fun lv5Day() = lv("pixel_lv5_day", 4)
    @Test fun lv5Night() = lv("pixel_lv5_night", 4, 21.5f) { copy(headlightsOn = true) }
    @Test fun lv3BlinkerLeft() = lv("pixel_lv3_blinker_left", 2) { copy(turnSignal = TurnSignal.LEFT) }
    @Test fun lv3Brake() = shot("pixel_lv3_brake", 4.15f, o().copy(model = 2)) { t ->
        driving(car(if (t < 4f) 60f else 30f, if (t < 4f) 0f else -6f) { copy(brakePressed = t >= 4f, harshBrakeSeq = if (t >= 4f) 1 else 0) }, trip(t, 60f))
    }
    @Test fun lv5Parked() = shot("pixel_lv5_parked", 3f, o().copy(model = 4)) { parked(car(0f, gear = GearState.PARK) { copy(moving = false) }) }

    @Test fun cityDayCruise() = shot("pixel_city_day_cruise", 6f) { t -> driving(car(50f), trip(t, 50f)) }
    @Test fun cityNightNeon() = shot("pixel_city_night_neon", 6f, o(21.5f)) { t -> driving(car(50f) { copy(headlightsOn = true) }, trip(t, 50f)) }
    @Test fun parkRainWipers() = shot("pixel_park_rain_wipers", 6f) { t -> driving(car(45f) { copy(wiperActive = true) }, trip(t, 45f, Biome.PARK)) }
    @Test fun fieldMeadowEco() = shot("pixel_field_meadow_eco", 6f) { t -> driving(car(60f) { copy(drivingMode = DriveModeState.ECO) }, trip(t, 60f, Biome.FIELD)) }
    @Test fun seasideSunsetSport() = shot("pixel_seaside_sunset_sport", 6f) { t -> driving(car(80f) { copy(drivingMode = DriveModeState.SPORT) }, trip(t, 80f, Biome.SEASIDE)) }
    @Test fun mountainNight() = shot("pixel_mountain_night_headlights", 6f, o(21.5f)) { t -> driving(car(55f) { copy(headlightsOn = true) }, trip(t, 55f, Biome.MOUNTAIN)) }
    @Test fun mountainDay() = shot("pixel_mountain_day_canyon", 6f) { t -> driving(car(55f), trip(t, 55f, Biome.MOUNTAIN)) }
    @Test fun gemHop() = shot("pixel_gem_hop", 4.1f) { t -> driving(car(60f), trip(t, 60f, stars = if (t > 3f) 1 else 0)) }
    @Test fun comboGemRow() = shot("pixel_combo_gem_row", 12.3f) { t -> driving(car(60f), trip(t, 60f, stars = (t / 1.5f).toInt(), combo = if (t > 12f) 1 else 0)) }
    /** Moving at 2× scale on a 240 dpi screen: the 14 px km glyphs are under 24 sp, so the sign's text is hidden while moving. */
    @Test fun checkpointKm() = shot("pixel_checkpoint_km", 3.1f) { t -> driving(car(60f), trip(t, 60f, km = if (t > 1.5f) 3 else 2)) }
    /** The same small screen once parked: the km text fades back in. */
    @Test fun checkpointKmParked() = shot("pixel_checkpoint_km_parked", 3.2f) { t ->
        if (t < 2.2f) driving(car(60f), trip(t, 60f, km = if (t > 0.5f) 3 else 2))
        else parked(car(0f, gear = GearState.PARK) { copy(moving = false) }, trip(2.2f, 60f, km = 3))
    }
    @Test fun harshBrake() = shot("pixel_harsh_brake", 4.15f) { t ->
        driving(car(if (t < 4f) 60f else 30f, if (t < 4f) 0f else -6f) { copy(brakePressed = t >= 4f, harshBrakeSeq = if (t >= 4f) 1 else 0) }, trip(t, 60f))
    }
    @Test fun reverse() = shot("pixel_reverse", 4f) { t -> driving(car(8f, gear = GearState.REVERSE), trip(t, 8f)) }
    @Test fun turnLeft() = shot("pixel_turn_left", 2.2f) { t -> driving(car(50f) { copy(turnSignal = TurnSignal.LEFT) }, trip(t, 50f)) }
    @Test fun arrivalGarage() = shot("pixel_arrival_garage", 9f) { t ->
        parked(car(0f, gear = GearState.PARK) { copy(moving = false, arrivalSeq = if (t > 0.5f) 1 else 0) }, trip(0f, 0f))
    }
    @Test fun arrivalStepOut() = shot("pixel_arrival_step_out", 3.2f) { t ->
        parked(car(0f, gear = GearState.PARK) { copy(moving = false, arrivalSeq = if (t > 0.5f) 1 else 0) }, trip(0f, 0f, Biome.PARK))
    }
    @Test fun charging() = shot("pixel_charging", 3f) { parked(car(0f, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = 64) }) }
    @Test fun acSnowFanMax() = shot("pixel_ac_snow_fan_max", 4f) { t -> driving(car(50f, 0.8f) { copy(fanLevel = 8, acOn = true, setTempC = 17f, cruiseOn = true) }, trip(t, 50f)) }
    @Test fun calm() = shot("pixel_calm", 3f, o(calm = true)) { t -> driving(car(50f), trip(t, 50f)) }
    @Test fun introEntrance() = shot("pixel_intro_entrance", 3f, o(intro = 0.45f)) { t -> driving(car(50f), trip(t, 50f, Biome.FIELD)) }

    // The remaining rows of the mapping table, so each one is visible in at least one image.
    @Test fun hazardHeat() = shot("pixel_hazard_heat", 2.9f) { t -> driving(car(30f) { copy(turnSignal = TurnSignal.HAZARD, setTempC = 28f) }, trip(t, 30f)) }
    @Test fun doorLowBattery() = shot("pixel_door_low_battery", 3f) {
        parked(car(0f, gear = GearState.PARK) { copy(moving = false, doorOpen = true, evBatteryPct = 12, evCharging = false) })
    }
    @Test fun chargingUnknownLevel() = shot("pixel_charging_unknown", 3f) { parked(car(0f, gear = GearState.PARK) { copy(moving = false, evCharging = true, evBatteryPct = null) }) }
}

/** CORNER size on the real 1920 × 720 display: the top-right 30 % at scale 1. */
class PixelCornerSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()
    @Test fun corner() = KitShots.script<PixelMechanics>(paparazzi, SceneTheme.PIXEL, "pixel_corner", 3f) { t ->
        driving(car(50f), Journey(active = true, tripSeq = 2, tripKm = 50f * t / 3600f)).copy(sizeMode = SizeMode.CORNER)
    }

    /** Moving on the full 1920 × 720 display (scale 4): the km glyphs are 56 px, above 24 sp, so the sign stays readable. */
    @Test fun movingKmSign() = KitShots.script<PixelMechanics>(paparazzi, SceneTheme.PIXEL, "pixel_moving_km_sign", 3.1f) { t ->
        driving(car(60f), Journey(active = true, tripSeq = 2, tripKm = 60f * t / 3600f, milestoneKm = if (t > 1.5f) 3 else 2))
    }

    /** The director sends the scene to CORNER during a safety warning (as in KART); the world freezes and dims to 70 %. */
    @Test fun safetyFreeze() = KitShots.script<PixelMechanics>(paparazzi, SceneTheme.PIXEL, "pixel_safety_freeze", 4f) { t ->
        val c = car(50f) { copy(safetyWarningActive = t > 2f) }
        driving(c, Journey(active = true, tripSeq = 2, tripKm = 50f * t / 3600f)).let { if (t > 2f) it.copy(sizeMode = SizeMode.CORNER) else it }
    }
}

/** 1000x1000 details: the rover and pilot at 7×, a sheet of the five tilesets (day and night), a zoom per tileset. */
class PixelDetailSnapshotTest {
    @get:Rule val paparazzi = KitShots.square()
    private val sprites = PixelSpriteCache()

    private fun DrawScope.blow(b: Bitmap, x: Float, y: Float, s: Float) {
        for (yy in 0 until b.height) for (xx in 0 until b.width) {
            val c = b.getPixel(xx, yy)
            if ((c ushr 24) != 0) drawRect(Color(c), Offset(x + xx * s, y + yy * s), Size(s, s))
        }
    }

    @Test fun rover() = paparazzi.snapshot("pixel_rover_8x") {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(PixelColors.MASTER[PixelColors.LILAC]))
            PixelPalette.entries.forEachIndexed { i, p ->
                val ox = 30f + (i % 2) * 480f; val oy = 20f + (i / 2) * 320f
                drawRect(Color(p.color(PixelColors.LAVENDER)), Offset(ox - 10f, oy - 10f), Size(260f, 220f))
                val s = 7f
                blow(sprites.pilot(PilotSprites.Pose.DRIVE, 0, p), ox + (4 + RoverSprite.SEAT_X) * s, oy + (12 + RoverSprite.SEAT_Y) * s, s)
                blow(sprites.rover(p), ox, oy, s)
                blow(sprites.wheel(0, p), ox + (4 + RoverSprite.WHEEL_REAR_X) * s, oy + (12 + RoverSprite.WHEEL_Y) * s, s)
                blow(sprites.wheel(2, p), ox + (4 + RoverSprite.WHEEL_FRONT_X) * s, oy + (12 + RoverSprite.WHEEL_Y) * s, s)
            }
            // Every pilot frame (pose by pose), the legs and the gem spin at 4×.
            var col = 0
            for (pose in PilotSprites.Pose.entries) for (f in 0 until pose.frames) {
                blow(sprites.pilot(pose, f, PixelPalette.DAY), 500f + (col % 9) * 52f, 680f + (col / 9) * 60f, 4f); col++
            }
            for (f in 0 until 3) blow(sprites.legs(f, PixelPalette.DAY), 500f + f * 52f, 820f, 4f)
            for (f in 0 until 4) blow(sprites.gem(f, PixelPalette.DAY), 680f + f * 40f, 830f, 4f)
            blow(sprites.gem(4, PixelPalette.DAY), 860f, 830f, 4f)
        }
    }

    /** The five tilesets, day (left) and night (right), at 1:1 virtual pixels (replaces the old cast sheet). */
    @Test fun tilesetSheet() = paparazzi.snapshot("pixel_tileset_sheet") {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(PixelColors.MASTER[PixelColors.INK]))
            Biome.entries.forEachIndexed { row, b ->
                for (col in 0..1) {
                    val sim = PixelMechanics(); val painter = PixelPainter()
                    val night = col == 1
                    val target = driving(car(40f) { if (night) copy(headlightsOn = true) else this }, Journey(active = true, tripSeq = 7, biome = b))
                    repeat(150) { sim.step(1f / 60f, target, DEFAULT_NOW_MS + it * 16L, if (night) 21.5f else 14f) }
                    val bmp = painter.frame(sim, ThemeFrame())
                    drawImage(bmp.asImageBitmap(), srcOffset = IntOffset.Zero, srcSize = IntSize(PixelModels.VW, PixelModels.VH),
                        dstOffset = IntOffset(14 + col * 494, 12 + row * 196), dstSize = IntSize(PixelModels.VW, PixelModels.VH), filterQuality = FilterQuality.None)
                }
            }
        }
    }

    private fun tiles(b: Biome) = KitShots.script<PixelMechanics>(paparazzi, SceneTheme.PIXEL, "pixel_tilesets_${b.name.lowercase()}", 3f, SceneOptions(hourOfDay = 14f), zoom = 2f, fx = 0.5f, fy = 0.4f) { t ->
        driving(car(40f), Journey(active = true, tripSeq = 7, tripKm = 40f * t / 3600f, biome = b))
    }
    @Test fun tilesCity() = tiles(Biome.CITY)
    @Test fun tilesPark() = tiles(Biome.PARK)
    @Test fun tilesField() = tiles(Biome.FIELD)
    @Test fun tilesSeaside() = tiles(Biome.SEASIDE)
    @Test fun tilesMountain() = tiles(Biome.MOUNTAIN)
}

/** The upgrade ladder in one picture: Lv.1 to Lv.5 as five stacked 8:3 strips (1000 x 1875, each 960 x 360 at scale 2). */
class PixelLadderSnapshotTest {
    @get:Rule val paparazzi = app.cash.paparazzi.Paparazzi(
        deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = com.android.resources.Density.LOW, orientation = com.android.resources.ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @Test fun ladder() {
        val runtimes = (0 until 5).map { i ->
            requireNotNull(io.vroomi.character.render.theme.ThemeCatalog.create(SceneTheme.PIXEL)).also { rt ->
                rt.simulate(6f, SceneOptions(hourOfDay = 14f, model = i), io.vroomi.character.render.calibre.DEFAULT_NOW_MS) { t ->
                    driving(car(50f), Journey(active = true, tripSeq = 2, tripKm = 50f * t / 3600f))
                }
            }
        }
        paparazzi.snapshot("pixel_ladder") {
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
