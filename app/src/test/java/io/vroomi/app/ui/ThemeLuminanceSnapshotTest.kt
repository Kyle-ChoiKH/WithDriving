package io.vroomi.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import io.vroomi.app.ui.design.ColorMath
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.SceneStillRenderer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Measures each theme's mean relative luminance from its still (the same still the gallery shows) and keeps
 * app/src/test/resources/theme_luminance.json in step: with VROOMI_LUMA=1 it writes the file, otherwise it
 * checks the file against a fresh measurement. Also records the strip of stills with their values.
 */
class ThemeLuminanceSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 720, xdpi = 160, ydpi = 160,
            density = com.android.resources.Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun meanLuminance(img: ImageBitmap): Float {
        val px = IntArray(img.width * img.height)
        img.readPixels(px)
        var sum = 0.0
        for (c in px) sum += ColorMath.luminance(c or 0xFF000000.toInt())
        return (sum / px.size).toFloat()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun themeLuminance() {
        val renderer = SceneStillRenderer(createFontFamilyResolver(paparazzi.context), Density(1f))
        val themes = ThemeRegistry.all.map { it.theme }
        val stills = themes.associateWith { renderer.render(it, 480, 180) }
        val values = stills.mapValues { (_, img) -> meanLuminance(img) }
        val file = File("src/test/resources/theme_luminance.json")
        if (System.getenv("VROOMI_LUMA") == "1") {
            file.parentFile?.mkdirs()
            val o = JSONObject()
            values.forEach { (t, v) -> o.put(t.name, Math.round(v * 1000.0) / 1000.0) }
            file.writeText(o.toString(2) + "\n")
        } else {
            val o = JSONObject(file.readText())
            for (t in themes) assertEquals("$t", o.getDouble(t.name).toFloat(), values.getValue(t), 0.02f)
        }
        paparazzi.snapshot {
            FlowRow(Modifier.fillMaxSize().background(Color(0xFF101418)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { t ->
                    Column {
                        Image(stills.getValue(t), null, Modifier.size(360.dp, 135.dp))
                        Text("${t.name}  L=%.3f".format(values.getValue(t)), color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }
    }

    @Suppress("unused") private val unused = SceneTheme.KART
}
