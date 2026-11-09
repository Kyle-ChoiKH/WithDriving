package io.vroomi.character.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import io.vroomi.character.Accessory
import io.vroomi.character.Action
import io.vroomi.character.Biome
import io.vroomi.character.Blinker
import io.vroomi.character.CharacterSkin
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Gait
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.KartStyle
import io.vroomi.character.Mood
import io.vroomi.character.Overlays
import io.vroomi.character.SizeMode
import org.junit.Rule
import org.junit.Test

/** All 15 friends: a portrait sheet and one driving moment each (docs/screenshots/skin_*.png). */
class SkinSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private val t0 = 1_000_000L

    @Test fun portraits() {
        paparazzi.snapshot("portraits") {
            Column(Modifier.fillMaxSize().background(Color(0xFFEFF7F4)).padding(8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                CharacterSkin.entries.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { skin ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                VroomiPortrait(skin, Modifier.height(120.dp).fillMaxWidth(0.19f))
                                BasicText(skin.label, style = TextStyle(fontSize = 13.sp, color = Color(0xFF14211F)))
                            }
                        }
                    }
                }
            }
        }
    }

    private data class Moment(val o: SceneOptions, val t: CharacterTarget, val fidget: Fidget? = null)

    private fun moment(skin: CharacterSkin): Moment {
        fun d(kmh: Float = 45f, biome: Biome = Biome.CITY, action: Action = Action.NONE, kart: KartState = KartState(driving = true),
              mood: Mood = Mood.NEUTRAL, ov: Overlays = Overlays(), acc: Set<Accessory> = emptySet()) = CharacterTarget(
            gait = if (kmh > 60) Gait.RUN else Gait.JOG, gaitSpeedKmh = kmh, action = action, actionStartedMs = t0 + 2000, mood = mood,
            overlays = ov.copy(speedLines = kmh >= 60f), accessories = acc, sizeMode = SizeMode.AMBIENT, kart = kart,
            journey = Journey(stars = 3, starSeq = 3, combo = 3, biome = biome),
        )
        val day = SceneOptions(skin = skin, hourOfDay = 14f, season = Season.AUTUMN)
        return when (skin) {
            CharacterSkin.VROOMI -> Moment(day, d())
            CharacterSkin.SSUK -> Moment(day, d(biome = Biome.PARK), Fidget.HUM)
            CharacterSkin.EOLEUM -> Moment(day.copy(season = Season.WINTER), d(action = Action.BLOW, mood = Mood.COLD, ov = Overlays(windLevel = 0.6f, snow = true), biome = Biome.MOUNTAIN))
            CharacterSkin.MUNGE -> Moment(day, d(ov = Overlays(rain = 0.7f), kart = KartState(driving = true, wipers = true)))
            CharacterSkin.DOTOL -> Moment(day, d(biome = Biome.FIELD), Fidget.LOOK_BACK)
            CharacterSkin.NUT -> Moment(day, d(kmh = 25f, action = Action.POINT_LEFT, kart = KartState(driving = true, blinker = Blinker.LEFT)))
            CharacterSkin.CHOCHIM -> Moment(day.copy(hourOfDay = 17.5f), d(kmh = 70f, biome = Biome.MOUNTAIN))
            CharacterSkin.PISTON -> Moment(day, d(kmh = 100f, biome = Biome.SEASIDE, kart = KartState(driving = true, style = KartStyle.SPORT), acc = setOf(Accessory.GOGGLES)))
            CharacterSkin.BOOST -> Moment(day, d(kmh = 40f, action = Action.BURST, kart = KartState(driving = true, accel = 2.4f), mood = Mood.HAPPY))
            CharacterSkin.TOPNI -> Moment(day, d(biome = Biome.FIELD))
            CharacterSkin.BANEUL -> Moment(day, d(kmh = 80f, biome = Biome.SEASIDE))
            CharacterSkin.TAEYEOP -> Moment(day, d(biome = Biome.PARK), Fidget.DRUM)
            CharacterSkin.ORGEL -> Moment(day.copy(hourOfDay = 18.2f), d(biome = Biome.PARK))
            CharacterSkin.PILA -> Moment(day.copy(hourOfDay = 21f), d(kmh = 60f, ov = Overlays(night = true), kart = KartState(driving = true, headlights = true)))
            CharacterSkin.NACHIM -> Moment(day, d(kmh = 30f, biome = Biome.SEASIDE, action = Action.POINT_RIGHT, kart = KartState(driving = true, blinker = Blinker.RIGHT)))
        }
    }

    @Test fun driving() {
        CharacterSkin.entries.forEachIndexed { idx, skin ->
            val m = moment(skin)
            val rt = SceneRuntime(seed = 3)
            for (i in 0 until 180) {
                if (i == 120) m.fidget?.let { rt.start(it) }
                rt.step(1f / 60f, m.t, m.o, t0 + i * 1000L / 60)
            }
            paparazzi.snapshot("skin_%02d_%s".format(idx + 1, skin.name.lowercase())) {
                val tm = rememberTextMeasurer()
                val cache = remember { TextCache() }
                Canvas(Modifier.fillMaxSize()) { rt.draw(this, m.t, m.o, tm, cache) }
            }
        }
    }
}
