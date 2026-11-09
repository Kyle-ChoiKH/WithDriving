package io.vroomi.app.ui.passage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.vroomi.app.ui.design.Grain
import io.vroomi.app.ui.design.SceneBackdrop
import io.vroomi.app.ui.design.SmoothCorners
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.pixel.PixelModels
import kotlin.math.max
import kotlin.math.roundToInt

/** Where the scene's container is and how its content sits inside it (all read while laying out or drawing). */
class StagePlacement(
    /** Container rectangle in root px. */
    val rect: () -> Rect,
    /** Corner radius in px. */
    val corner: () -> Float,
    /** Horizontal shift of the content inside the container (gallery parallax), px. */
    val contentShift: () -> Float = { 0f },
    /** Extra scale of the whole container about its centre (gallery focus, peek, pull to close). */
    val containerScale: () -> Float = { 1f },
    /** Vertical offset of the whole container (pull to close), px. */
    val containerShiftY: () -> Float = { 0f },
    /** Black over the container (gallery neighbours at 70 % brightness). */
    val dim: () -> Float = { 0f },
    /** Overall opacity of the container (launch fade-up). */
    val alpha: () -> Float = { 1f },
)

/** A rounded-rectangle shape whose radius is read when the outline is built (no new shape per frame). */
private class LiveCornerShape(private val radius: () -> Float) : Shape {
    private val path = androidx.compose.ui.graphics.Path()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = radius()
        if (r <= 0.5f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val g = SmoothCorners.geometry(size.width, size.height, r, 0.6f)
        path.reset()
        path.moveTo(g.startX, g.startY)
        for (s in g.segs) if (s.cubic) path.cubicTo(s.x1, s.y1, s.x2, s.y2, s.x, s.y) else path.lineTo(s.x, s.y)
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * The one place the live theme scene is drawn. The scene composable never leaves this host (it is never
 * recreated when its place on screen changes); the host moves and resizes. Inside, bottom to top: the backdrop,
 * the incoming theme's still, the live scene (recorded into a graphics layer every frame, so a snapshot and the
 * materials' backdrop blur are always at hand), the outgoing snapshot, the live scene again through the entrance
 * mask, the entrance's own effects, the swipe, and the cover used while the layout size changes.
 */
@Composable
fun SceneStage(
    stage: StageController,
    stills: HeroStills,
    placement: StagePlacement,
    /** The live scene's layout size in px (the stage size in the gallery, the place's own size elsewhere). */
    layoutSize: IntSize,
    backdropColor: () -> Color,
    tint: () -> Color,
    sceneBackdrop: SceneBackdrop,
    /** Gestures on the container (the stage's swipe and pull); none where the scene must let touches through. */
    modifier: Modifier = Modifier,
    /** Hide the live scene (a swipe holds the world still). */
    liveHidden: () -> Boolean = { false },
    live: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val sceneLayer = rememberGraphicsLayer()
    val stageLayer = rememberGraphicsLayer()
    val renderer = remember { EntranceRenderer() }
    val shape = remember(placement) { LiveCornerShape(placement.corner) }
    val layerPaint = remember { Paint() }
    val hairPx = with(density) { io.vroomi.app.ui.design.Dimens.hairline.toPx() }
    val rollPx = with(density) { io.vroomi.app.ui.design.Dimens.rollEdge.toPx() }
    val grain = remember { Grain.brush() }
    stage.sceneLayer = sceneLayer
    stage.stageLayer = stageLayer
    sceneBackdrop.layer = stageLayer
    val lw = layoutSize.width.coerceAtLeast(1)
    val lh = layoutSize.height.coerceAtLeast(1)

    fun fill(cw: Float, ch: Float) = max(cw / lw, ch / lh)

    /** Draws the scene layer the way the live host shows it (centred, filling the container). */
    fun DrawScope.drawScene(layer: GraphicsLayer) {
        val f = fill(size.width, size.height)
        val tx = (size.width - lw * f) / 2f + placement.contentShift()
        val ty = (size.height - lh * f) / 2f
        withTransform({ translate(tx, ty); scale(f, f, Offset.Zero) }) { drawLayer(layer) }
    }

    fun DrawScope.drawCropped(img: ImageBitmap, alpha: Float, scale: Float = 1f, shiftX: Float = 0f, pixel: Boolean = false) {
        if (alpha <= 0.003f) return
        val f = max(size.width / img.width, size.height / img.height) * scale
        val dw = img.width * f; val dh = img.height * f
        drawImage(
            img, IntOffset.Zero, IntSize(img.width, img.height),
            IntOffset(((size.width - dw) / 2f + shiftX + placement.contentShift()).roundToInt(), ((size.height - dh) / 2f).roundToInt()),
            IntSize(dw.roundToInt(), dh.roundToInt()),
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = if (pixel) FilterQuality.None else FilterQuality.Low,
        )
    }

    Box(
        Modifier
            .layout { measurable, _ ->
                val r = placement.rect()
                val w = r.width.roundToInt().coerceAtLeast(1); val h = r.height.roundToInt().coerceAtLeast(1)
                val p = measurable.measure(Constraints.fixed(w, h))
                // The position is read again while placing: a card's place is known only after the shelf is placed.
                layout(w, h) { val q = placement.rect(); p.place(IntOffset(q.left.roundToInt(), q.top.roundToInt())) }
            }
            .then(modifier)
            .graphicsLayer {
                val s = placement.containerScale()
                scaleX = s; scaleY = s
                translationY = placement.containerShiftY()
                alpha = placement.alpha()
                this.shape = shape
                clip = true
                val r = placement.rect()
                sceneBackdrop.originX = r.left + (1f - s) * r.width / 2f
                sceneBackdrop.originY = r.top + (1f - s) * r.height / 2f + placement.containerShiftY()
                sceneBackdrop.scale = s
            }
            .drawWithContent {
                stageLayer.record { this@drawWithContent.drawContent() }
                drawLayer(stageLayer)
                val d = placement.dim()
                if (d > 0.003f) drawRect(Color.Black, alpha = d)
            },
    ) {
        // Backdrop and the incoming theme's still (a growing card, a committed swipe).
        Canvas(Modifier.fillMaxSize()) {
            drawRect(backdropColor())
            val hero = stage.heroAlpha.floatValue
            val t = stage.to
            if (hero > 0.003f && t != null) stills.peek(t)?.let { drawCropped(it, hero, pixel = t == SceneTheme.PIXEL) }
        }
        // The live scene: laid out at its own size, scaled to fill, recorded into the scene layer every frame.
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .requiredSize(with(density) { lw.toDp() }, with(density) { lh.toDp() })
                    .graphicsLayer {
                        val r = placement.rect()
                        val f = fill(r.width, r.height)
                        scaleX = f; scaleY = f
                        translationX = placement.contentShift()
                    }
                    .drawWithContent {
                        sceneLayer.record { this@drawWithContent.drawContent() }
                        val cover = if (stage.coverKeepsLive) 0f else stage.boundsAlpha.value
                        val a = if (liveHidden()) 0f else stage.baseAlpha.floatValue * (1f - cover)
                        if (a >= 0.999f) drawLayer(sceneLayer)
                        else if (a > 0.003f) {
                            layerPaint.alpha = a
                            drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), layerPaint) }
                            drawLayer(sceneLayer)
                            drawIntoCanvas { it.restore() }
                        }
                    },
            ) {
                live(Modifier.fillMaxSize())
            }
        }
        // The outgoing snapshot, receding.
        Canvas(Modifier.fillMaxSize()) {
            val a = stage.snapshotAlpha.floatValue
            if (a <= 0.003f) return@Canvas
            val img = stage.snapshot ?: stage.snapshotFallback?.let { stills.peek(it) } ?: return@Canvas
            drawCropped(img, a, stage.snapshotScale.floatValue, pixel = stage.from == SceneTheme.PIXEL && stage.snapshot == null)
            val dim = stage.snapshotDim.floatValue
            if (dim > 0.003f) drawRect(Color.Black, alpha = dim * a)
        }
        // The incoming scene through its entrance.
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            val t = stage.to
            alpha = if (t == null) 0f else Entrances.inAlpha(t, stage.revealTime.floatValue) * stage.maskAlpha.floatValue
        }) {
            if (!stage.maskOn) return@Canvas
            val t = stage.to ?: return@Canvas
            val f = fill(size.width, size.height)
            val ps = PixelModels.scale(lw.toFloat(), lh.toFloat())
            val (ox, oy) = PixelModels.offset(lw.toFloat(), lh.toFloat(), ps)
            val tx = (size.width - lw * f) / 2f + placement.contentShift()
            val ty = (size.height - lh * f) / 2f
            renderer.drawMasked(this, t, stage.reveal.floatValue, stage.intensity, ps * f, tx + ox * f, ty + oy * f) { drawScene(sceneLayer) }
        }
        // Effects over both worlds (section line, rolled edge, water line, mist).
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = stage.maskAlpha.floatValue }) {
            if (!stage.maskOn) return@Canvas
            val t = stage.to ?: return@Canvas
            renderer.drawOver(this, t, stage.reveal.floatValue, stage.revealTime.floatValue, stage.intensity, tint(), hairPx, rollPx, grain)
        }
        // P5: the frozen outgoing world follows the finger at 0.35 x; the neighbour's still at 1 x.
        Canvas(Modifier.fillMaxSize()) {
            val snap = stage.swipeSnap ?: return@Canvas
            val off = stage.swipeOffset.value
            val w = size.width
            drawCropped(snap, 1f, shiftX = off * SwipeMath.OUTGOING_FOLLOW)
            val dim = (kotlin.math.abs(off) / w / SwipeMath.COMMIT_FRACTION).coerceIn(0f, 1f) * SwipeMath.OUTGOING_DIM
            if (dim > 0.003f) drawRect(Color.Black, alpha = dim)
            val n = stage.swipeNeighbour ?: return@Canvas
            val img = stills.peek(n)
            val x = if (off < 0f) w + off else -w + off
            if (img != null) drawCropped(img, 1f, shiftX = x, pixel = n == SceneTheme.PIXEL)
            else drawRect(backdropColor(), Offset(x, 0f), size)
        }
        // A change of layout size: the old frame covers the scene until the bounds settle.
        Canvas(Modifier.fillMaxSize()) {
            val img = stage.boundsSnap ?: return@Canvas
            drawCropped(img, stage.boundsAlpha.value)
        }
    }
}
