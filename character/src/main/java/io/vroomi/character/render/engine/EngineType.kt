package io.vroomi.character.render.engine

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface

/**
 * The type of the telemetry display: headings in the system's condensed sans, numbers in the system's monospace
 * (so digits keep their columns while they change). Three sizes only, as shares of the screen height.
 */
internal object EngineType {
    val condensed: FontFamily = FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)))
    val mono: FontFamily = FontFamily.Monospace
    val regular = FontWeight.Normal
    val medium = FontWeight.Medium
    val bold = FontWeight.Bold

    /** Values (the readout's numbers), headings (the model name), captions (cell labels, graph names). */
    const val LARGE = 0.066f
    const val MEDIUM = 0.048f
    const val SMALL = 0.025f
}
