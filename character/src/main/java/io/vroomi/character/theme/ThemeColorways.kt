package io.vroomi.character.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import io.vroomi.character.calibre.SceneTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One colourway of a theme: its name and how it recolours the theme's whole picture ([hueDeg] turns the hues,
 * [saturation] scales the colour, [r] [g] [b] tint the light).
 */
class Colorway(val name: String, val hueDeg: Float = 0f, val saturation: Float = 1f, val r: Float = 1f, val g: Float = 1f, val b: Float = 1f) {
    val plain: Boolean get() = hueDeg == 0f && saturation == 1f && r == 1f && g == 1f && b == 1f
}

/**
 * The colourways of the themes: five each, the first one the theme as it was drawn. Like the kart's wardrobe they
 * open with the safe-driving grade (one more per grade), and an open one can be chosen at any time. The kart itself
 * has its wardrobe and its friends instead.
 *
 * A colourway is a colour matrix over the finished picture, so it costs one layer and changes no drawing code; each
 * theme's set is chosen for what that theme is made of (a turn of hue for the coloured worlds, a tint of the light
 * for the pale ones).
 */
object ThemeColorways {
    const val COUNT = 5

    private val SETS: Map<SceneTheme, List<Colorway>> = mapOf(
        SceneTheme.CALIBRE to listOf(
            Colorway("아이보리 작업대"), Colorway("새벽 블루", saturation = 0.9f, r = 0.90f, g = 0.97f, b = 1.08f),
            Colorway("로즈 샴페인", r = 1.07f, g = 0.95f, b = 0.93f), Colorway("올리브 그린", saturation = 0.95f, r = 0.95f, g = 1.03f, b = 0.88f),
            Colorway("흑백 사진", saturation = 0.08f, r = 1.02f, b = 0.96f),
        ),
        SceneTheme.ENGINE to listOf(
            Colorway("레드 블록"), Colorway("코발트 블록", hueDeg = 215f), Colorway("레이싱 그린", hueDeg = 125f),
            Colorway("골드 블록", hueDeg = 42f, saturation = 1.05f), Colorway("설계도 블루", saturation = 0.12f, r = 0.86f, g = 0.96f, b = 1.12f),
        ),
        SceneTheme.MUSIC_BOX to listOf(
            Colorway("호두나무"), Colorway("체리 우드", hueDeg = -14f, saturation = 1.2f, r = 1.05f, g = 0.95f, b = 0.95f),
            Colorway("에보니", saturation = 0.35f, r = 0.82f, g = 0.84f, b = 0.90f), Colorway("민트 래커", hueDeg = 120f, saturation = 0.75f),
            Colorway("로열 블루", hueDeg = 195f, saturation = 0.9f),
        ),
        SceneTheme.PLASMA to listOf(
            Colorway("실험실 보라"), Colorway("석양", hueDeg = 60f, saturation = 1.05f), Colorway("심해", hueDeg = 150f),
            Colorway("라임", hueDeg = -150f), Colorway("흑백 필름", saturation = 0.1f),
        ),
        SceneTheme.HANGAR to listOf(
            Colorway("작업등"), Colorway("새벽 청색", r = 0.88f, g = 0.97f, b = 1.12f), Colorway("황혼 주황", saturation = 1.05f, r = 1.12f, g = 0.98f, b = 0.84f),
            Colorway("비상 적색", saturation = 0.9f, r = 1.18f, g = 0.86f, b = 0.84f), Colorway("극지 백색", saturation = 0.45f, r = 1.02f, g = 1.06f, b = 1.12f),
        ),
        SceneTheme.TERRARIUM to listOf(
            Colorway("초여름 정원"), Colorway("가을 정원", hueDeg = -72f, saturation = 1.05f), Colorway("벚꽃 정원", hueDeg = 205f, saturation = 0.9f),
            Colorway("청록 이끼", hueDeg = 48f), Colorway("달빛 정원", saturation = 0.3f, r = 0.9f, g = 0.98f, b = 1.12f),
        ),
        SceneTheme.AQUARIUM to listOf(
            Colorway("맑은 바다"), Colorway("코발트 바다", hueDeg = 38f), Colorway("에메랄드 라군", hueDeg = -42f),
            Colorway("보랏빛 심해", hueDeg = 95f), Colorway("산호빛 노을", hueDeg = 165f, saturation = 0.72f),
        ),
        SceneTheme.ORBIT to listOf(
            Colorway("녹색 관제"), Colorway("앰버 관제", hueDeg = -95f), Colorway("아이스 블루", hueDeg = 70f),
            Colorway("마젠타 관제", hueDeg = 170f), Colorway("백색 관제", saturation = 0.05f),
        ),
        SceneTheme.TREASURE_MAP to listOf(
            Colorway("양피지"), Colorway("푸른 해도", r = 0.90f, g = 0.97f, b = 1.08f), Colorway("장밋빛 지도", r = 1.06f, g = 0.94f, b = 0.95f),
            Colorway("올리브 고지도", r = 0.95f, g = 1.02f, b = 0.86f), Colorway("먹빛 지도", saturation = 0.1f),
        ),
        SceneTheme.PAPER to listOf(
            Colorway("핑크 매트"), Colorway("오렌지 매트", hueDeg = 55f), Colorway("민트 매트", hueDeg = 150f),
            Colorway("코발트 매트", hueDeg = -110f), Colorway("크라프트", saturation = 0.35f, r = 1.06f, b = 0.9f),
        ),
        SceneTheme.PIXEL to listOf(
            Colorway("라벤더 월드"), Colorway("선셋 월드", hueDeg = 95f), Colorway("민트 월드", hueDeg = -105f),
            Colorway("네이비 월드", hueDeg = -45f, r = 0.85f, g = 0.85f, b = 0.95f), Colorway("모노 그린", saturation = 0f, r = 0.78f, b = 0.62f),
        ),
    )

    /** True when the theme has colourways (every theme but the kart, which has its wardrobe). */
    fun has(theme: SceneTheme): Boolean = SETS.containsKey(theme)

    /** The theme's colourways in the order they open (empty for the kart). */
    fun of(theme: SceneTheme): List<Colorway> = SETS[theme].orEmpty()

    fun name(theme: SceneTheme, index: Int): String = of(theme).getOrNull(index)?.name.orEmpty()

    /**
     * The 4 x 5 colour matrix of colourway [index] of [theme] (rows R, G, B, A; values in 0..1 terms with the fifth
     * column 0), or null when the picture stays as it is.
     */
    fun matrix(theme: SceneTheme, index: Int): FloatArray? {
        val c = of(theme).getOrNull(index) ?: return null
        if (c.plain) return null
        val m = mul(mul(scale(c.r, c.g, c.b), saturation(c.saturation)), hue(c.hueDeg))
        return floatArrayOf(
            m[0], m[1], m[2], 0f, 0f,
            m[3], m[4], m[5], 0f, 0f,
            m[6], m[7], m[8], 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** [argb] as colourway [index] of [theme] shows it (for swatches and tests). */
    fun apply(theme: SceneTheme, index: Int, argb: Int): Int {
        val m = matrix(theme, index) ?: return argb
        val r = (argb shr 16 and 255) / 255f; val g = (argb shr 8 and 255) / 255f; val b = (argb and 255) / 255f
        fun ch(row: Int) = ((m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (argb and 0xFF000000.toInt()) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    }

    private const val LR = 0.213f
    private const val LG = 0.715f
    private const val LB = 0.072f

    /** A turn of hue that keeps each colour's luminance. */
    private fun hue(deg: Float): FloatArray {
        val a = deg * PI.toFloat() / 180f
        val c = cos(a); val s = sin(a)
        return floatArrayOf(
            LR + c * (1 - LR) + s * -LR, LG + c * -LG + s * -LG, LB + c * -LB + s * (1 - LB),
            LR + c * -LR + s * 0.143f, LG + c * (1 - LG) + s * 0.140f, LB + c * -LB + s * -0.283f,
            LR + c * -LR + s * -(1 - LR), LG + c * -LG + s * LG, LB + c * (1 - LB) + s * LB,
        )
    }

    private fun saturation(s: Float): FloatArray {
        val i = 1f - s
        return floatArrayOf(LR * i + s, LG * i, LB * i, LR * i, LG * i + s, LB * i, LR * i, LG * i, LB * i + s)
    }

    private fun scale(r: Float, g: Float, b: Float) = floatArrayOf(r, 0f, 0f, 0f, g, 0f, 0f, 0f, b)

    private fun mul(a: FloatArray, b: FloatArray): FloatArray = FloatArray(9) { i ->
        val row = i / 3; val col = i % 3
        a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col]
    }
}

/** Draws the content through colourway [index] of [theme] (one layer; nothing extra for the theme's own colours). */
fun Modifier.colorway(theme: SceneTheme, index: Int): Modifier {
    val m = ThemeColorways.matrix(theme, index) ?: return this
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix(m)) }
    return drawWithContent {
        drawIntoCanvas { it.saveLayer(Rect(0f, 0f, size.width, size.height), paint) }
        drawContent()
        drawIntoCanvas { it.restore() }
    }
}
