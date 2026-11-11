package io.vroomi.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import io.vroomi.app.R
import org.junit.Rule
import org.junit.Test

/** The launcher icon as launchers show it: its two layers under a rounded-square and a round mask, large and small, and the themed version. */
class IconSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1000, screenHeight = 360, xdpi = 160, ydpi = 160, density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        maxPercentDifference = 100.0,
    )

    /** An adaptive icon [size] across: the 108 dp layers show their middle 72 dp under [mask]. */
    @androidx.compose.runtime.Composable
    private fun Icon(size: Dp, mask: Shape, mono: Color? = null) {
        Box(Modifier.size(size).clip(mask).background(mono?.let { Color(0xFF1F2A28) } ?: Color.Transparent), contentAlignment = Alignment.Center) {
            val full = size * 1.5f
            if (mono == null) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(full))
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(full))
            } else Image(painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.requiredSize(full), colorFilter = ColorFilter.tint(mono))
        }
    }

    @Test fun app_icon() = paparazzi.snapshot("app_icon") {
        Row(Modifier.fillMaxSize().background(Color(0xFFEDEFF2)), horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(240.dp, RoundedCornerShape(27))
            Icon(240.dp, CircleShape)
            Icon(96.dp, RoundedCornerShape(27))
            Icon(56.dp, RoundedCornerShape(27))
            Icon(160.dp, RoundedCornerShape(27), mono = Color(0xFF9FE8D6))
        }
    }
}
