package io.vroomi.character.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.theme.ThemeDecor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every theme at the window shapes the car's system can give the app: the wide display (8:3), 16:9, the split
 * window (about 7:5), a square, and two upright windows (3:4 and 9:16). One row per theme, each shape drawn at the
 * same height, at the top step with every prop on: the subject has to stay whole and centred in all of them.
 */
class AspectSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1760, screenHeight = 800, xdpi = 160, ydpi = 160,
            density = com.android.resources.Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun row(theme: SceneTheme) {
        val renderer = SceneStillRenderer(createFontFamilyResolver(paparazzi.context), Density(1f))
        val spec = StillSpec(model = 4, decor = (1 shl ThemeDecor.COUNT) - 1)
        val stills = SHAPES.map { (w, h) -> renderer.render(theme, w, h, spec) }
        paparazzi.snapshot("aspect_${theme.name.lowercase()}") {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Color(0xFF202226)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (line in listOf(0..1, 2..5)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in line) {
                        val (w, h) = SHAPES[i]
                        Image(stills[i], null, Modifier.height(384.dp).width((384f * w / h).dp), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
                    }
                }
            }
        }
    }

    @Test fun kart() = row(SceneTheme.KART)
    @Test fun calibre() = row(SceneTheme.CALIBRE)
    @Test fun engine() = row(SceneTheme.ENGINE)
    @Test fun musicBox() = row(SceneTheme.MUSIC_BOX)
    @Test fun plasma() = row(SceneTheme.PLASMA)
    @Test fun hangar() = row(SceneTheme.HANGAR)
    @Test fun terrarium() = row(SceneTheme.TERRARIUM)
    @Test fun aquarium() = row(SceneTheme.AQUARIUM)
    @Test fun orbit() = row(SceneTheme.ORBIT)
    @Test fun map() = row(SceneTheme.TREASURE_MAP)
    @Test fun paper() = row(SceneTheme.PAPER)
    @Test fun pixel() = row(SceneTheme.PIXEL)

    private companion object {
        /** Canvas sizes in px: 8:3, 16:9, 7:5, 1:1, 3:4, 9:16. */
        val SHAPES = listOf(1920 to 720, 1280 to 720, 1008 to 720, 720 to 720, 720 to 960, 540 to 960)
    }
}

/** The props' rules: three per theme, none for the kart, names that fit a card. */
class ThemeDecorTest {
    @Test fun threePerThemeAndNoneForTheKart() {
        for (theme in SceneTheme.entries) {
            val names = ThemeDecor.names(theme)
            if (theme == SceneTheme.KART) { assertFalse(ThemeDecor.has(theme)); assertTrue(names.isEmpty()); continue }
            assertTrue(ThemeDecor.has(theme))
            assertEquals("$theme", ThemeDecor.COUNT, names.size)
            assertEquals("$theme names differ", ThemeDecor.COUNT, names.distinct().size)
            assertTrue("$theme names fit a card", names.all { it.isNotBlank() && it.length <= 8 })
        }
        // Silver opens the first prop, gold the second, platinum the third.
        assertEquals(listOf(2, 3, 4), (0 until ThemeDecor.COUNT).map { ThemeDecor.unlockLevel(it) })
    }
}
