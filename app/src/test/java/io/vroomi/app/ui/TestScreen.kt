package io.vroomi.app.ui

import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import com.android.resources.ScreenOrientation

/**
 * The display the whole-screen snapshots are drawn on. The default is the design display, 1920 × 720 px at 160 dpi
 * (1920 × 720 dp). `VROOMI_SCREEN=<w>x<h>@<dpi>` (for example `1920x720@240`) draws the same screens on another
 * car display, to check the chrome and the scenes on smaller or denser screens.
 */
internal object TestScreen {
    private val spec: Triple<Int, Int, Int> = run {
        val m = System.getenv("VROOMI_SCREEN")?.let { Regex("""(\d+)x(\d+)@(\d+)""").matchEntire(it.trim()) }
        if (m == null) Triple(1920, 720, 160) else Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    /** Width and height in px. */
    val pxW: Int get() = spec.first
    val pxH: Int get() = spec.second
    val dpi: Int get() = spec.third
    /** px per dp. */
    val scale: Float get() = dpi / 160f

    private fun density(dpi: Int): Density = when (dpi) {
        120 -> Density.LOW
        160 -> Density.MEDIUM
        213 -> Density.TV
        240 -> Density.HIGH
        320 -> Density.XHIGH
        else -> error("VROOMI_SCREEN: use 120, 160, 213, 240 or 320 dpi")
    }

    /** Height in dp; whole-screen snapshots set it as the app does ([io.vroomi.character.render.theme.ChromeZones.screenH]). */
    val dpH: Float get() = pxH / scale

    val device: DeviceConfig
        get() = DeviceConfig.PIXEL_5.copy(
            screenWidth = pxW, screenHeight = pxH, xdpi = dpi, ydpi = dpi,
            density = density(dpi), orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        )
}
