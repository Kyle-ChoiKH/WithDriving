package io.vroomi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.vroomi.app.R
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalDark
import io.vroomi.app.ui.design.MaterialToken
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.lifted
import io.vroomi.app.ui.design.material
import io.vroomi.app.ui.motion.LocalMotion
import io.vroomi.app.ui.motion.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Animatables 0 -> 1 for [count] children, started [stepMs] apart after [startMs] whenever [key] changes. */
@Composable
internal fun rememberStagger(key: Any?, count: Int, stepMs: Int, startMs: Int = Motion.CONTENT_DELAY_MS): List<Animatable<Float, AnimationVector1D>> {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val anims = remember(count) { List(count) { Animatable(if (prefs.still) 1f else 0f) } }
    LaunchedEffect(key) {
        if (prefs.still) return@LaunchedEffect
        anims.forEach { it.snapTo(0f) }
        anims.forEachIndexed { i, a ->
            launch {
                delay(prefs.ms(startMs + i * stepMs))
                a.animateTo(1f, if (prefs.reduced) prefs.fade(Motion.REDUCED_MS) else prefs.spring(Motion.Smooth))
            }
        }
    }
    return anims
}

/** Fades a child in and lets it rise by at most [rise] (readable text never travels more than 12 dp). */
internal fun Modifier.staggered(a: Animatable<Float, AnimationVector1D>, rise: Dp = Space.s12): Modifier = graphicsLayer {
    val v = a.value
    alpha = v.coerceIn(0f, 1f)
    translationY = (1f - v) * rise.toPx()
}

/**
 * A sheet that rises from the bottom edge on Smooth over a scrim (35 % in dark, 25 % in light). Drag it down to
 * dismiss (distance or velocity). Its content staggers by 30 ms. [collapseTo]: on close it shrinks toward that
 * point of the screen instead (the demo badge's corner). [progress] reports 0..1 for the backdrop blur.
 */
@Composable
internal fun BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    collapseTo: TransformOrigin? = null,
    progress: (Float) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val dark = LocalDark.current
    val chrome = LocalChrome.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val p = remember { Animatable(if (prefs.still && visible) 1f else 0f) }
    val drag = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(visible) }
    var closingToPoint by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true; closingToPoint = false
            drag.snapTo(0f)
            p.animateTo(1f, if (prefs.reduced) prefs.fade(Motion.REDUCED_MS) else prefs.spring(Motion.Smooth))
        } else if (mounted) {
            closingToPoint = collapseTo != null
            p.animateTo(0f, if (prefs.reduced) prefs.fade(Motion.REDUCED_MS) else prefs.spring(Motion.Smooth))
            mounted = false
        }
    }
    LaunchedEffect(Unit) { snapshotFlow { if (mounted) p.value else 0f }.collect(progress) }
    if (!mounted) return
    val scrim = if (dark) 0.40f else 0.22f
    val closeLabel = stringResource(R.string.sheet_close)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = closeLabel },
        )
        val dragState = rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(Space.s24)
                .widthIn(max = Dimens.sheetMax)
                .fillMaxWidth()
                .graphicsLayer {
                    val v = p.value
                    if (closingToPoint && collapseTo != null) {
                        transformOrigin = collapseTo
                        scaleX = 0.4f + 0.6f * v; scaleY = 0.4f + 0.6f * v
                        alpha = v.coerceIn(0f, 1f)
                    } else if (prefs.reduced) {
                        alpha = v.coerceIn(0f, 1f)
                    } else {
                        translationY = (1f - v) * (size.height + Space.s24.toPx()) + drag.value
                    }
                }
                .draggable(
                    dragState, Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val vDp = velocity / density.density
                        if (drag.value > with(density) { Space.s56.toPx() } * 2f || vDp > 900f) onDismiss()
                        else drag.animateTo(0f, prefs.spring(Motion.Smooth), initialVelocity = velocity)
                    },
                )
                .lifted(Shapes.sheet, { Color.Black }, { p.value })
                .material(MaterialToken.Thick, Shapes.sheet, shadow = false)
                .padding(start = Space.s40, end = Space.s40, top = Space.s16, bottom = Space.s32),
        ) {
            // Grabber: a hint that the sheet can be pulled down.
            val grab = Ink.tertiary
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(Dimens.grabber, Dimens.grabberH)
                    .drawBehind { drawRoundRect(grab, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)) },
            )
            Box(Modifier.padding(top = Space.s24)) { Column { content() } }
        }
    }
}
