package io.vroomi.app.ui.design

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Translucent surfaces over the live scene, in three weights. Each has a tint (alpha for light and dark), a backdrop
 * blur radius (API 31 and later, parked only), a 1 px hairline, a 2.5 % grain and the shadow of its elevation.
 * Without blur (API 28 to 30, and always while moving) the tint is 0.18 more opaque, so the surface still reads as
 * intentional.
 *
 * - [Thin]: controls on another surface or on the scene (chips, the stage's small buttons); no shadow.
 * - [Regular]: floating chrome over the scene (the bond badge, the stage panel, the rail); a soft shadow.
 * - [Thick]: things that lift above everything (sheets, the welcome card, the driving chrome); a deeper shadow.
 */
enum class MaterialToken(val lightAlpha: Float, val darkAlpha: Float, val blur: Dp, val elevation: Elevation) {
    Thin(0.62f, 0.70f, 20.dp, Elevation.None),
    Regular(0.80f, 0.78f, 24.dp, Elevation.Low),
    Thick(0.90f, 0.88f, 24.dp, Elevation.High);

    /** The tint as ARGB for [dark] mode; [blurred] = a real backdrop blur is drawn under it. */
    fun fill(dark: Boolean, blurred: Boolean): Int {
        val a = ((if (dark) darkAlpha else lightAlpha) + if (blurred) 0f else FALLBACK_EXTRA).coerceAtMost(0.97f)
        val base = if (dark) Neutral.SURFACE_DARK else Neutral.SURFACE_LIGHT
        return ((a * 255).roundToInt() shl 24) or (base and 0xFFFFFF)
    }

    companion object {
        const val FALLBACK_EXTRA = 0.18f
        const val GRAIN_ALPHA = 0.025f
        const val HAIRLINE_DARK = 0.12f
        const val HAIRLINE_LIGHT = 0.07f
        /** The blurred copy is drawn at half scale (and never more than two blurred surfaces at once). */
        const val BLUR_SCALE = 0.5f
    }
}

/**
 * A natural two-layer shadow: a tight ambient contact shadow and a soft key shadow below it. Dark mode deepens the
 * alpha (a shadow on a dark scene needs more to read); the hairline does the rest of the work there.
 */
enum class Elevation(val ambientY: Dp, val ambientBlur: Dp, val ambientAlpha: Float, val keyY: Dp, val keyBlur: Dp, val keyAlpha: Float) {
    None(0.dp, 0.dp, 0f, 0.dp, 0.dp, 0f),
    Low(1.dp, 3.dp, 0.05f, 6.dp, 18.dp, 0.08f),
    High(2.dp, 6.dp, 0.06f, 14.dp, 36.dp, 0.13f);

    fun alphaScale(dark: Boolean): Float = if (dark) 2.2f else 1f
}

/**
 * Where the live scene is drawn, so a material can draw a blurred copy of what is behind it. The stage updates
 * these while drawing; materials read them while drawing (no recomposition).
 */
@Stable
class SceneBackdrop {
    var layer: GraphicsLayer? = null
    /** Root position of the scene's (0, 0) after the container's scale, and that scale. */
    var originX = 0f
    var originY = 0f
    var scale = 1f
    /** False while moving: no per-frame blur while driving. */
    var blurAllowed by mutableStateOf(true)
}

val LocalSceneBackdrop = staticCompositionLocalOf<SceneBackdrop?> { null }
/** A real blur exists from API 31 (RenderEffect). Tests may switch it off to record the fallback. */
val LocalBlurEnabled = staticCompositionLocalOf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
val LocalDark = staticCompositionLocalOf { false }

/** One small tile of noise, generated once in code (no asset). */
internal object Grain {
    private var tile: ImageBitmap? = null
    fun brush(): ShaderBrush {
        val t = tile ?: ImageBitmap(64, 64).also { img ->
            val c = androidx.compose.ui.graphics.Canvas(img)
            val p = Paint()
            val rnd = Random(7)
            for (y in 0 until 64) for (x in 0 until 64) {
                val v = rnd.nextFloat()
                p.color = if (v > 0.5f) Color.White.copy(alpha = (v - 0.5f) * 2f) else Color.Black.copy(alpha = (0.5f - v) * 2f)
                c.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, p)
            }
            tile = img
        }
        return ShaderBrush(ImageShader(t, TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * A material surface in [shape]. [blur]: draw a blurred copy of the live scene behind it (only where the surface
 * sits over the scene; at most two at once). Falls back to the static tint when blur is unavailable or not allowed.
 * [shadow]: draw the token's elevation shadow (off for a surface that sits inside another one).
 */
fun Modifier.material(token: MaterialToken, shape: Shape, blur: Boolean = false, shadow: Boolean = true): Modifier = composed {
    val dark = LocalDark.current
    val backdrop = LocalSceneBackdrop.current
    val canBlur = blur && LocalBlurEnabled.current && backdrop != null && backdrop.blurAllowed
    val blurLayer = if (canBlur) rememberGraphicsLayer() else null
    val pos = remember { FloatArray(2) }
    val density = LocalDensity.current
    val blurPx = with(density) { token.blur.toPx() }
    val hairPx = with(density) { Dimens.hairline.toPx() }
    this
        .then(if (shadow && token.elevation != Elevation.None) Modifier.elevation(token.elevation, shape) else Modifier)
        .onGloballyPositioned { val p = it.positionInRoot(); pos[0] = p.x; pos[1] = p.y }
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val grain = Grain.brush()
            val hair = if (dark) Color.White.copy(alpha = MaterialToken.HAIRLINE_DARK) else Color.Black.copy(alpha = MaterialToken.HAIRLINE_LIGHT)
            onDrawBehind {
                val src = backdrop?.layer
                val blurred = blurLayer != null && src != null
                if (blurred) {
                    val q = MaterialToken.BLUR_SCALE
                    blurLayer!!.renderEffect = BlurEffect(blurPx * q, blurPx * q, TileMode.Clamp)
                    val dx = pos[0] - backdrop!!.originX
                    val dy = pos[1] - backdrop.originY
                    val s = backdrop.scale
                    blurLayer.record(IntSize(ceil(size.width * q).toInt().coerceAtLeast(1), ceil(size.height * q).toInt().coerceAtLeast(1))) {
                        scale(q, q, Offset.Zero) { translate(-dx, -dy) { scale(s, s, Offset.Zero) { drawLayer(src!!) } } }
                    }
                    clipPath(path) { scale(1f / q, 1f / q, Offset.Zero) { drawLayer(blurLayer) } }
                }
                drawPath(path, Color(token.fill(dark, blurred)))
                drawPath(path, grain, alpha = MaterialToken.GRAIN_ALPHA)
                drawPath(path, hair, style = Stroke(hairPx))
            }
        }
}

/**
 * An opaque grouped surface on a page (a settings section, a diary entry): the grouped colour, the hairline and,
 * in light mode, a barely-there contact shadow.
 */
fun Modifier.grouped(shape: Shape): Modifier = composed {
    val dark = LocalDark.current
    val hairPx = with(LocalDensity.current) { Dimens.hairline.toPx() }
    this
        .then(if (!dark) Modifier.elevation(Elevation.Low, shape, strength = 0.6f) else Modifier)
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val hair = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            onDrawBehind {
                drawPath(path, Color(Neutral.grouped(dark)))
                drawPath(path, hair, style = Stroke(hairPx))
            }
        }
}

/** The shadow of [e] around [shape] (drawn outside the bounds; [strength] scales its alpha). */
fun Modifier.elevation(e: Elevation, shape: Shape, strength: Float = 1f): Modifier = composed {
    val density = LocalDensity.current
    val dark = LocalDark.current
    val ay = with(density) { e.ambientY.toPx() }; val ar = with(density) { e.ambientBlur.toPx() }
    val ky = with(density) { e.keyY.toPx() }; val kr = with(density) { e.keyBlur.toPx() }
    val ambient = remember(ar) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { if (ar > 0f) maskFilter = BlurMaskFilter(ar, BlurMaskFilter.Blur.NORMAL) } }
    val key = remember(kr) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { if (kr > 0f) maskFilter = BlurMaskFilter(kr, BlurMaskFilter.Blur.NORMAL) } }
    val k = e.alphaScale(dark) * strength
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }.asAndroidPath()
        onDrawBehind {
            ambient.color = Color.Black.copy(alpha = (e.ambientAlpha * k).coerceAtMost(1f)).toArgb()
            key.color = Color.Black.copy(alpha = (e.keyAlpha * k).coerceAtMost(1f)).toArgb()
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save(); n.translate(0f, ay); n.drawPath(path, ambient); n.restore()
                n.save(); n.translate(0f, ky); n.drawPath(path, key); n.restore()
            }
        }
    }
}

/**
 * The two-layer shadow of a lifted thing (a lifted card, a sheet, the opening stage): ambient (y 2, blur 6, 6 %)
 * and key (y 14, blur 36, 12 %), tinted with the theme's dark tone instead of black. [amount] 0..1 fades it.
 */
fun Modifier.lifted(shape: Shape, tone: () -> Color, amount: () -> Float): Modifier = composed {
    val density = LocalDensity.current
    val ambientY = with(density) { 2.dp.toPx() }; val ambientR = with(density) { 6.dp.toPx() }
    val keyY = with(density) { 14.dp.toPx() }; val keyR = with(density) { 36.dp.toPx() }
    val ambient = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(ambientR, BlurMaskFilter.Blur.NORMAL) } }
    val key = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(keyR, BlurMaskFilter.Blur.NORMAL) } }
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }.asAndroidPath()
        onDrawBehind {
            val a = amount().coerceIn(0f, 1f)
            if (a > 0.01f) {
                val t = tone()
                ambient.color = t.copy(alpha = 0.06f * a).toArgb()
                key.color = t.copy(alpha = 0.12f * a).toArgb()
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    n.save(); n.translate(0f, ambientY); n.drawPath(path, ambient); n.restore()
                    n.save(); n.translate(0f, keyY); n.drawPath(path, key); n.restore()
                }
            }
        }
    }
}
