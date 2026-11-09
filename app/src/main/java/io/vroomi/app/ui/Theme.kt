package io.vroomi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.vroomi.app.ui.design.Ambience
import io.vroomi.app.ui.design.ColorMath
import io.vroomi.app.ui.design.LocalDark
import io.vroomi.app.ui.design.Neutral
import io.vroomi.character.calibre.SceneTheme

/** The brand colours (kept); the chrome borrows its accent from the shown theme ([Ambience]). */
object VroomiColors {
    val Teal = Color(0xFF3CC8B4)
    val DeepTeal = Color(0xFF0E5A51)
    val Amber = Color(0xFFF2A93B)
    val Cream = Color(0xFFFFF4DC)
    val Night = Color(0xFF0E1B2B)
    val NightSurface = Color(0xFF172838)
    val Good = Color(0xFF3BAA6E)
    val Warn = Color(0xFFE0A030)
    val Bad = Color(0xFFD9604C)
}

/** Car-sized type: anything visible while moving is at least 24 sp. */
private val CarType = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 24.sp),
    bodyMedium = TextStyle(fontSize = 22.sp),
    labelLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
)

/**
 * Material 3 restyled with Vroomi's tokens: the cool neutral surfaces and the shown theme's tint as the one
 * accent ([theme]; the passage animates the chrome's own copy of it, this one follows when it settles).
 */
@Composable
fun VroomiTheme(night: Boolean = isSystemInDarkTheme(), theme: SceneTheme = SceneTheme.KART, content: @Composable () -> Unit) {
    val amb = Ambience.of(theme)
    val tint = Color(amb.tintFor(night))
    val onTint = Color(amb.onTint(night))
    val surface = Color(if (night) Neutral.SURFACE_DARK else Neutral.SURFACE_LIGHT)
    val ink = Color(if (night) Neutral.INK_DARK else Neutral.INK_LIGHT)
    val inkSecondary = Color(ColorMath.over(((Neutral.SECONDARY * 255).toInt() shl 24) or ((if (night) Neutral.INK_DARK else Neutral.INK_LIGHT) and 0xFFFFFF), if (night) Neutral.SURFACE_DARK else Neutral.SURFACE_LIGHT))
    val variant = Color(if (night) Neutral.RAMP[7] else Neutral.RAMP[1])
    val scheme = if (night) darkColorScheme(
        primary = tint, onPrimary = onTint, secondary = VroomiColors.Amber, onSecondary = Color(0xFF2A1A00),
        background = Color(Neutral.BACKGROUND_DARK), onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = variant, onSurfaceVariant = inkSecondary,
    ) else lightColorScheme(
        primary = tint, onPrimary = onTint, secondary = VroomiColors.Amber, onSecondary = Color(0xFF2A1A00),
        background = Color(Neutral.BACKGROUND_LIGHT), onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = variant, onSurfaceVariant = inkSecondary,
    )
    CompositionLocalProvider(LocalDark provides night) {
        MaterialTheme(colorScheme = scheme, typography = CarType, content = content)
    }
}
