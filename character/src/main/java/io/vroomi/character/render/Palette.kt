package io.vroomi.character.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Every colour of the scene. Original palette (teal character, coral kart), no third-party art. */
internal object Pal {
    // Vroomi
    val body = Color(0xFF3CC8B4); val belly = Color(0xFFFFF4DC); val outline = Color(0xFF0E5A51)
    val eye = Color(0xFF1B2423); val sole = Color(0xFFF2A93B); val blush = Color(0xFFFFB59A)
    val frost = Color(0xFFBFE6FF); val warm = Color(0xFFFFB067); val scarf = Color(0xFFE4574B)
    val raincoat = Color(0xFFF6C945); val beanie = Color(0xFFE07A5F); val goggles = Color(0xFF3A4A5A)
    val leaf = Color(0xFF6CC24A); val paper = Color(0xFFFFF8E7); val blanket = Color(0xFFB98BD6)
    val cable = Color(0xFF39434A); val sparkle = Color(0xFFFFF3B0); val energy = Color(0xFF9CF7E6)
    val star = Color(0xFFFFD34D); val starEdge = Color(0xFFD98E0B); val note = Color(0xFF5B6CFF)

    // Kart
    val kartNormal = Color(0xFFFF8C5A); val kartSport = Color(0xFFE5483F); val kartEco = Color(0xFF86C96A)
    val kartLine = Color(0xFF5B2A1E); val tire = Color(0xFF2B2F33); val hub = Color(0xFFE9EEF0)
    val glass = Color(0xFFCDEFFF); val brakeOn = Color(0xFFFF3B30); val brakeOff = Color(0xFFA33A30)
    val amber = Color(0xFFFFB020); val headlight = Color(0xFFFFF4C2); val underglow = Color(0xFF7FE3FF)

    // World
    val trunk = Color(0xFF7A5A43); val road = Color(0xFF5E6B6A); val roadNight = Color(0xFF2A3436)
    val dash = Color(0xFFF3F1E6); val curb = Color(0xFFB9C2C0)
    val nightTint = Color(0xFF15222B)
    val signGreen = Color(0xFF2E8B57); val signBlue = Color(0xFF2F6DB5)
}

internal fun Color.dim(f: Float): Color = Color(red * f, green * f, blue * f, alpha)

/** Pushes a day colour toward the night tint by darkness [n] (0 day .. 1 night). */
internal fun Color.night(n: Float, k: Float = 0.68f): Color = lerp(this, Pal.nightTint, (n * k).coerceIn(0f, 1f))

internal fun Color.alpha(a: Float): Color = copy(alpha = (alpha * a).coerceIn(0f, 1f))

/** Deterministic hash -> [0, 1). Same slot index always draws the same prop. */
internal fun hash01(k: Int, salt: Int = 0): Float {
    var x = k * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0x7fffffff) / 2147483648f
}

internal fun smooth01(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

internal fun approach(v: Float, target: Float, maxStep: Float): Float =
    if (v < target) minOf(target, v + maxStep) else maxOf(target, v - maxStep)

internal fun floorDiv(a: Double, b: Double): Int = kotlin.math.floor(a / b).toInt()
