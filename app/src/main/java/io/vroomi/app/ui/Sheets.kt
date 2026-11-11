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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vroomi.app.R
import io.vroomi.app.progress.Grade
import io.vroomi.app.progress.ThemeAccess
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalDark
import io.vroomi.app.ui.design.MaterialToken
import io.vroomi.app.ui.design.RowDivider
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.Tag
import io.vroomi.app.ui.design.VIcons
import io.vroomi.app.ui.design.VType
import io.vroomi.app.ui.design.grouped
import io.vroomi.app.ui.design.lifted
import io.vroomi.app.ui.design.material
import io.vroomi.app.ui.motion.LocalMotion
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.pressFeedback
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.theme.ThemeModels
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

// ---------------------------------------------------------------- the upgrade step (model) sheet

/**
 * The upgrade-step sheet of [theme] (chip "업그레이드 · Lv.3 VR-01 쿠션"): five rows, each with its "Lv.n" tag, the
 * step's name and the grade that opens it. An open step is chosen (and saved). A locked one ("플래티넘 등급에
 * 열려요") can be tapped to preview it on the stage, but it is never saved or applied.
 */
@Composable
internal fun ModelOptions(theme: SceneTheme, ctx: PickerContext) {
    SheetHeader(stringResource(R.string.model_title), stringResource(R.string.model_hint))
    val shown = ctx.modelOf(theme)
    val max = ThemeAccess.maxModel(ctx.grade)
    Column(Modifier.fillMaxWidth().grouped(Shapes.card)) {
        for (m in 0 until ThemeAccess.STEPS) {
            if (m > 0) RowDivider()
            ModelRow(
                tag = ThemeModels.tag(m), title = ThemeModels.title(theme, m), unlock = ThemeAccess.modelUnlockGrade(m),
                open = m <= max, chosen = m == shown, previewing = m == shown && ctx.previewing(theme),
            ) { ctx.onModel(theme, m) }
        }
    }
}

@Composable
private fun ModelRow(tag: String, title: String, unlock: Grade, open: Boolean, chosen: Boolean, previewing: Boolean, onClick: () -> Unit) {
    val chrome = LocalChrome.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.listRow)
            .pressFeedback(Shapes.card, large = true)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = chosen }
            .padding(start = Space.s24, end = Space.s24, top = Space.s12, bottom = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag(tag, accent = chosen)
        Spacer(Modifier.width(Space.s16))
        Column(Modifier.weight(1f)) {
            Text(title, style = VType.headline, color = if (open) Ink.primary else Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.s4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Medal(unlock, 20.dp, number = false)
                Spacer(Modifier.width(Space.s8))
                Text(
                    stringResource(if (open) R.string.grade_badge else R.string.locked_grade, unlock.label),
                    style = VType.callout, color = Ink.secondary, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(Space.s16))
        when {
            previewing -> Tag(stringResource(R.string.model_previewing), accent = false)
            chosen -> Image(VIcons.Check, null, Modifier.size(Dimens.icon), colorFilter = ColorFilter.tint(chrome.tint))
        }
    }
}
