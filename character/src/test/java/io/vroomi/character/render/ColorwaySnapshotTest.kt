package io.vroomi.character.render

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.theme.ThemeColorways
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The five colourways of each theme in one picture (the theme at its third step, parked), and their rules. */
class ColorwaySnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000, screenHeight = 1875, xdpi = 120, ydpi = 120,
            density = com.android.resources.Density.LOW, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun sheet(theme: SceneTheme) {
        val renderer = SceneStillRenderer(createFontFamilyResolver(paparazzi.context), Density(1f))
        val stills = (0 until ThemeColorways.COUNT).map { renderer.render(theme, 1000, 375, StillSpec(model = 2, color = it)) }
        paparazzi.snapshot("colorways_${theme.name.lowercase()}") {
            Column(Modifier.fillMaxSize()) {
                stills.forEachIndexed { i, img ->
                    Box(Modifier.fillMaxWidth().aspectRatio(8f / 3f)) {
                        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
                        BasicText(
                            ThemeColorways.name(theme, i),
                            Modifier.padding(start = 12.dp, top = 10.dp).background(Color(0xD9101316), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                            style = TextStyle(color = Color.White, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
    }

    /**
     * Props under a colourway: things keep their own colours (the aquarium's gold chest stays gold in every sea),
     * props in the theme's own ink follow it (the orbit display's saucer turns with the display).
     */
    private fun props(theme: SceneTheme) {
        val renderer = SceneStillRenderer(createFontFamilyResolver(paparazzi.context), Density(1f))
        val all = (1 shl io.vroomi.character.theme.ThemeDecor.COUNT) - 1
        val stills = (0 until ThemeColorways.COUNT).map { renderer.render(theme, 1000, 375, StillSpec(model = 2, color = it, decor = all)) }
        paparazzi.snapshot("colorway_props_${theme.name.lowercase()}") {
            Column(Modifier.fillMaxSize()) {
                stills.forEach { img -> Image(img, null, Modifier.fillMaxWidth().aspectRatio(8f / 3f), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low) }
            }
        }
    }

    @Test fun propsAquarium() = props(SceneTheme.AQUARIUM)
    @Test fun propsOrbit() = props(SceneTheme.ORBIT)

    @Test fun calibre() = sheet(SceneTheme.CALIBRE)
    @Test fun engine() = sheet(SceneTheme.ENGINE)
    @Test fun musicBox() = sheet(SceneTheme.MUSIC_BOX)
    @Test fun plasma() = sheet(SceneTheme.PLASMA)
    @Test fun hangar() = sheet(SceneTheme.HANGAR)
    @Test fun terrarium() = sheet(SceneTheme.TERRARIUM)
    @Test fun aquarium() = sheet(SceneTheme.AQUARIUM)
    @Test fun orbit() = sheet(SceneTheme.ORBIT)
    @Test fun map() = sheet(SceneTheme.TREASURE_MAP)
    @Test fun paper() = sheet(SceneTheme.PAPER)
    @Test fun pixel() = sheet(SceneTheme.PIXEL)
}

/** The colourways' rules: five per theme, the first one the theme's own colours, none for the kart. */
class ThemeColorwaysTest {
    @Test fun fivePerThemeAndTheFirstIsTheThemeItself() {
        for (theme in SceneTheme.entries) {
            val set = ThemeColorways.of(theme)
            if (theme == SceneTheme.KART) {
                assertFalse(ThemeColorways.has(theme)); assertTrue(set.isEmpty()); assertNull(ThemeColorways.matrix(theme, 1))
                continue
            }
            assertTrue(ThemeColorways.has(theme))
            assertEquals("$theme", ThemeColorways.COUNT, set.size)
            assertEquals("$theme names differ", ThemeColorways.COUNT, set.map { it.name }.distinct().size)
            assertTrue("$theme names fit a chip", set.all { it.name.isNotBlank() && it.name.length <= 9 })
            assertTrue(set[0].plain); assertNull(ThemeColorways.matrix(theme, 0))
            for (i in 1 until ThemeColorways.COUNT) {
                val m = ThemeColorways.matrix(theme, i)
                assertNotNull("$theme $i", m); assertEquals(20, m!!.size)
                // Alpha is never touched.
                assertEquals(1f, m[18], 0f); assertEquals(0f, m[15] + m[16] + m[17] + m[19], 0f)
            }
        }
    }

    @Test fun aColourwayRecoloursAndKeepsGreyNearGrey() {
        val teal = 0xFF2FB3A3.toInt()
        assertEquals(teal, ThemeColorways.apply(SceneTheme.AQUARIUM, 0, teal))
        assertNotEquals(teal, ThemeColorways.apply(SceneTheme.AQUARIUM, 3, teal))
        // A turn of hue leaves a neutral grey where it was (the matrix keeps luminance).
        val grey = ThemeColorways.apply(SceneTheme.AQUARIUM, 3, 0xFF808080.toInt())
        for (shift in intArrayOf(16, 8, 0)) assertTrue(kotlin.math.abs((grey shr shift and 255) - 128) <= 3)
        // Out of range: the theme's own colours.
        assertNull(ThemeColorways.matrix(SceneTheme.AQUARIUM, 9))
    }
}
