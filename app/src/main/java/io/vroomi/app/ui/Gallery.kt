package io.vroomi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.app.R
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.ButtonStyle
import io.vroomi.app.ui.design.SegmentedControl
import io.vroomi.app.ui.design.Tag
import io.vroomi.app.ui.design.VButton
import io.vroomi.app.ui.design.elevation
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import io.vroomi.app.ui.design.Layout
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.Radius
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.SmoothCornerShape
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.VIcons
import io.vroomi.app.ui.design.VType
import io.vroomi.app.ui.design.lifted
import io.vroomi.app.ui.design.MaterialToken
import io.vroomi.app.ui.design.material
import io.vroomi.app.ui.motion.LocalMotion
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.pressFeedback
import io.vroomi.app.ui.passage.HeroStills
import io.vroomi.character.calibre.SceneTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The gallery's shelf geometry, readable while laying out and drawing (so the live scene follows its card
 * exactly while the shelf scrolls), plus the peek and the lift of a card.
 */
@Stable
internal class GalleryState(initial: SceneTheme) {
    val list = LazyListState(firstVisibleItemIndex = ThemeRegistry.indexOf(initial))
    var category by mutableStateOf<ThemeCategory?>(null)
    var originX by mutableFloatStateOf(0f)
    var originY by mutableFloatStateOf(0f)
    var viewportW by mutableFloatStateOf(0f)
    var cardW by mutableFloatStateOf(0f)
    var cardH by mutableFloatStateOf(0f)
    var gap by mutableFloatStateOf(0f)
    var parallaxMax by mutableFloatStateOf(0f)
    var themes by mutableStateOf(ThemeRegistry.all.map { it.theme })
    var peek by mutableStateOf<SceneTheme?>(null)
    val peekAnim = Animatable(0f)
    var lifting by mutableStateOf<SceneTheme?>(null)
    val liftAnim = Animatable(0f)

    /** The shelf for the chosen category; the applied theme's card is always there (its world is always on screen). */
    fun shelf(applied: SceneTheme): List<SceneTheme> {
        val c = category ?: return ThemeRegistry.all.map { it.theme }
        val inCat = ThemeRegistry.all.filter { it.category == c }.map { it.theme }
        return if (applied in inCat) inCat else listOf(applied) + inCat
    }

    /** The card's left edge relative to the viewport (px), extrapolated when it is scrolled out. */
    private fun cardLeft(theme: SceneTheme): Float? {
        val idx = themes.indexOf(theme)
        if (idx < 0) return null
        val info = list.layoutInfo
        val vis = info.visibleItemsInfo
        if (vis.isEmpty()) return null
        val start = -info.viewportStartOffset.toFloat()
        val hit = vis.firstOrNull { it.index == idx }
        if (hit != null) return start + hit.offset
        val ref = vis.first()
        return start + ref.offset + (idx - ref.index) * (cardW + gap)
    }

    fun cardRect(theme: SceneTheme): Rect? {
        val x = cardLeft(theme) ?: return null
        return Rect(originX + x, originY, originX + x + cardW, originY + cardH)
    }

    /** 0 at the centre, 1 a full card away (or further). */
    fun distance(theme: SceneTheme): Float {
        val x = cardLeft(theme) ?: return 1f
        val centre = x + cardW / 2f
        val pitch = (cardW + gap).coerceAtLeast(1f)
        return min(1f, abs(centre - viewportW / 2f) / pitch)
    }

    fun signedOffset(theme: SceneTheme): Float {
        val x = cardLeft(theme) ?: return 0f
        return x + cardW / 2f - viewportW / 2f
    }

    /** Focus scale, peek and lift of a card. */
    fun scaleOf(theme: SceneTheme): Float {
        val d = distance(theme)
        var s = 1f + (Layout.NEIGHBOUR_SCALE - 1f) * d
        if (peek == theme) s *= 1f + 0.04f * peekAnim.value
        if (lifting == theme) s *= 1f + 0.02f * liftAnim.value
        return s
    }

    fun dimOf(theme: SceneTheme): Float = (1f - Layout.NEIGHBOUR_BRIGHTNESS) * distance(theme)

    /** The image inside a card moves at 0.8 x the scroll (at most 24 dp): -0.2 x its distance from the centre. */
    fun parallaxOf(theme: SceneTheme): Float = (-signedOffset(theme) * Layout.PARALLAX).coerceIn(-parallaxMax, parallaxMax)

    fun indexInShelf(theme: SceneTheme): Int = themes.indexOf(theme)
}

/** Draws a still into the current box, cropped to fill, with an optional horizontal shift and extra scale. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStill(img: ImageBitmap, shift: Float = 0f, extra: Float = 1f, pixel: Boolean = false) {
    val f = max(size.width / img.width, size.height / img.height) * extra
    val dw = img.width * f; val dh = img.height * f
    drawImage(
        img, IntOffset.Zero, IntSize(img.width, img.height),
        IntOffset(((size.width - dw) / 2f + shift).roundToInt(), ((size.height - dh) / 2f).roundToInt()), IntSize(dw.roundToInt(), dh.roundToInt()),
        filterQuality = if (pixel) FilterQuality.None else FilterQuality.Low,
    )
}

/**
 * The theme gallery (the THEMES tab, parked only), on a clear grid: the page title and the segmented category
 * control share the header row; below it, a snapping shelf of 8:3 windows centred on the focused card (the applied
 * theme's window is the live scene, drawn by the stage above it; every other card is a hero still). Only the
 * focused card shows its name, one-line hint and status, on one baseline grid; the applied theme's option chips
 * sit at the right end of its name row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Gallery(
    state: GalleryState,
    applied: SceneTheme,
    stills: HeroStills,
    chips: @Composable RowScope.() -> Unit,
    onOpen: (SceneTheme) -> Unit,
    modifier: Modifier = Modifier,
    staticStills: Map<SceneTheme, ImageBitmap>? = null,
    /** The upgrade step each card shows ("Lv.3", top right of the card; null: no tag). */
    stepTag: (SceneTheme) -> String? = { null },
) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val chrome = LocalChrome.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val shelf = state.shelf(applied)
    if (state.themes != shelf) state.themes = shelf
    @Suppress("UNUSED_VARIABLE") val v = stills.version
    val enter = rememberStagger(Unit, 3, Motion.STAGGER_MS)

    val dark = io.vroomi.app.ui.design.LocalDark.current
    val wall = androidx.compose.material3.MaterialTheme.colorScheme.background
    // The gallery's own room: the light wall in light mode, the shown theme's backdrop (deepened) in dark mode.
    BoxWithConstraints(modifier.fillMaxSize().drawBehind { drawRect(if (dark) chrome.backdrop else wall); if (dark) drawRect(Color.Black, alpha = 0.35f) }) {
        val wide = maxWidth >= Dimens.wideBreak
        val margin = Space.margin(wide)
        val peek = if (wide) Space.s64 * 2 else Space.s48
        val infoH = GalleryInfoHeight
        val headerH = Dimens.segmented
        // The card: about half the page tall, never so wide that the neighbours cannot peek in.
        val byHeight = minOf(maxHeight * Layout.CARD_HEIGHT, maxHeight - margin * 2 - headerH - Space.s32 - Space.s20 - infoH)
        val byWidth = (maxWidth - margin * 2 - peek * 2) / Layout.CARD_ASPECT
        val cardH = minOf(byHeight, byWidth)
        val cardW = cardH * Layout.CARD_ASPECT
        val gapDp = Space.s24
        val side = ((this.maxWidth - cardW) / 2).coerceAtLeast(margin)
        with(density) {
            state.cardW = cardW.toPx(); state.cardH = cardH.toPx(); state.gap = gapDp.toPx(); state.parallaxMax = Space.s24.toPx()
        }
        Column(Modifier.fillMaxSize().padding(top = margin, bottom = margin)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = margin).height(headerH).staggered(enter[0]),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.theme_title), Modifier.weight(1f), style = VType.largeTitle, color = Ink.primary)
                val options: List<Pair<ThemeCategory?, String>> = listOf<Pair<ThemeCategory?, String>>(null to stringResource(R.string.theme_cat_all)) +
                    ThemeCategory.entries.map { it to stringResource(it.label) }
                SegmentedControl(options, state.category) { c ->
                    state.category = c
                    scope.launch {
                        val target = if (c == null) ThemeRegistry.indexOf(applied) else 0
                        state.list.animateScrollToItem(target.coerceIn(0, (state.shelf(applied).size - 1).coerceAtLeast(0)))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            LazyRow(
                state = state.list,
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(gapDp),
                flingBehavior = rememberSnapFlingBehavior(state.list, SnapPosition.Center),
                modifier = Modifier
                    .fillMaxWidth()
                    .staggered(enter[1])
                    .onPlaced { c ->
                        val p = c.positionInRoot(); state.originX = p.x; state.originY = p.y; state.viewportW = c.size.width.toFloat()
                    },
            ) {
                items(shelf, key = { it.name }) { theme ->
                    val spec = ThemeRegistry.of(theme)
                    val still = staticStills?.get(theme) ?: stills.get(theme)
                    GalleryCard(
                        state, theme, stringResource(spec.title), stringResource(spec.hint),
                        applied = theme == applied, still = still, width = cardW, height = cardH,
                        chips = if (theme == applied) chips else null, step = stepTag(theme),
                        modifier = Modifier.animateItem(),
                        onOpen = {
                            scope.launch {
                                state.lifting = theme
                                state.liftAnim.snapTo(0f)
                                state.liftAnim.animateTo(1f, tween((Motion.CARD_LIFT_MS * prefs.slow).toInt()))
                                onOpen(theme)
                                state.liftAnim.animateTo(0f, prefs.spring(Motion.Smooth))
                                state.lifting = null
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1.3f))
        }
    }
}

/** The name row under a card (the title role's line). */
private val GalleryTitleRow = 40.dp
/** The text block under a card: the name row, the hint line and the row of option chips. */
private val GalleryInfoHeight = GalleryTitleRow + Space.s4 + 28.dp + Space.s12 + Dimens.chipHeight

@Composable
private fun GalleryCard(
    state: GalleryState, theme: SceneTheme, title: String, hint: String,
    applied: Boolean, still: ImageBitmap?, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp,
    chips: (@Composable RowScope.() -> Unit)?,
    step: String?,
    modifier: Modifier, onOpen: () -> Unit,
) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.theme_card_label, title)
    val ring = remember { SmoothCornerShape(Radius.r24 + Dimens.ringGap) }
    Column(
        modifier
            .width(width)
            .graphicsLayer {
                val s = state.scaleOf(theme)
                scaleX = s; scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, height.toPx() / (size.height.coerceAtLeast(1f)) / 2f)
            }
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .size(width, height)
                .elevation(io.vroomi.app.ui.design.Elevation.Low, Shapes.card)
                .lifted(Shapes.card, { chrome.backdrop }, { max(if (state.peek == theme) state.peekAnim.value else 0f, if (state.lifting == theme) state.liftAnim.value else 0f) })
                .drawBehind {
                    if (applied) {
                        val g = Dimens.ringGap.toPx(); val r = Dimens.ring.toPx()
                        val o = ring.createOutline(Size(size.width + 2 * g, size.height + 2 * g), layoutDirection, this)
                        translate(-g, -g) { drawOutline(o, chrome.tint, style = Stroke(r)) }
                    }
                }
                .pressFeedback(Shapes.card, large = true)
                .pointerInput(theme) {
                    detectTapGestures(
                        onLongPress = {
                            state.peek = theme
                            scope.launch {
                                state.peekAnim.snapTo(0f)
                                state.peekAnim.animateTo(1f, tween((Motion.PEEK_DRIFT_MS * prefs.slow).toInt(), easing = LinearEasing))
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (state.peek == theme) scope.launch { state.peekAnim.animateTo(0f, prefs.spring(Motion.Snappy)); state.peek = null }
                        },
                        onTap = { onOpen() },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val outline = Shapes.card.createOutline(size, layoutDirection, this)
                val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
                clipPath(path) {
                    drawRect(chrome.backdrop)
                    if (still != null) {
                        val drift = if (state.peek == theme) 1f + 0.02f * state.peekAnim.value else 1f
                        val extra = (1f + 2f * state.parallaxMax / size.width) * drift
                        drawStill(still, state.parallaxOf(theme), extra, theme == SceneTheme.PIXEL)
                    }
                    val d = state.dimOf(theme)
                    if (d > 0.003f) drawRect(Color.Black, alpha = d)
                }
            }
            // The step the theme shows (every theme is open; the grade decides how far it is upgraded).
            if (step != null) StepTag(step, Modifier.align(Alignment.TopEnd).padding(Space.s16).graphicsLayer { alpha = 1f - state.dimOf(theme) })
        }
        // Only the focused card speaks: its text fades with the distance from the centre (no text is ever cut
        // by the edge of the page).
        Spacer(Modifier.height(Space.s20))
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = (1f - state.distance(theme) * 1.6f).coerceIn(0f, 1f) }
                .blockWhen { state.distance(theme) > 0.4f },
        ) {
            Row(Modifier.fillMaxWidth().height(GalleryTitleRow), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = VType.title, color = Ink.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (applied) {
                    Spacer(Modifier.width(Space.s16))
                    Tag(stringResource(R.string.theme_current), check = true)
                }
            }
            Spacer(Modifier.height(Space.s4))
            Text(hint, style = VType.callout, color = Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The applied theme's option chips (the upgrade step first) on their own row, so the name is never cut.
            Spacer(Modifier.height(Space.s12))
            // The chips scroll sideways when the card is too narrow for them all (a split or upright window).
            Row(Modifier.height(Dimens.chipHeight).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s12), verticalAlignment = Alignment.CenterVertically) {
                chips?.invoke(this)
            }
        }
    }
}

/**
 * A card's upgrade-step tag ("Lv.3") over its picture: a small capsule of the static Regular material (opaque enough
 * to read over any scene) with the step in primary ink.
 */
@Composable
internal fun StepTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.height(32.dp).material(MaterialToken.Regular, Shapes.capsule, blur = false, shadow = false).padding(horizontal = Space.s12),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = VType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold).merge(VType.tabular), color = Ink.primary, maxLines = 1)
    }
}

/** Swallows touches while [block] is true (read when a touch starts): faded text never takes a tap. */
private fun Modifier.blockWhen(block: () -> Boolean): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (block()) ev.changes.forEach { it.consume() }
        }
    }
}

/**
 * The stage's chrome (parked only): the close button, the arrows, the position ticks and one Regular material
 * panel: the theme's name and hint on the left, its note in the middle, and the actions on the right (the option
 * chips and "데모로 보기" as quiet secondaries, "이 테마로 달리기" as the one primary). [shown] rises it in,
 * staggered; [leaving] dissolves it (a fade and 8 dp down) when the theme is applied.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StageChrome(
    theme: SceneTheme,
    shown: Boolean,
    leaving: Boolean,
    onClose: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onApply: () -> Unit,
    onDemo: (() -> Unit)?,
    chips: @Composable RowScope.() -> Unit,
) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val chrome = LocalChrome.current
    val parts = rememberStagger(shown, 4, Motion.STAGGER_MS, startMs = 0)
    val leave = remember { Animatable(0f) }
    LaunchedEffect(leaving) { leave.animateTo(if (leaving) 1f else 0f, prefs.fade((Motion.FADE_OUT_MS))) }
    if (!shown) return
    val index = ThemeRegistry.indexOf(theme)
    val count = ThemeRegistry.all.size
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - leave.value
            translationY = leave.value * Space.s8.toPx()
        },
    ) {
        IconCircle(VIcons.Close, stringResource(R.string.stage_close), Modifier.align(Alignment.TopStart).padding(Space.s20).staggered(parts[0]), onClick = onClose)
        if (onPrev != null) IconCircle(VIcons.ChevronLeft, stringResource(R.string.stage_prev), Modifier.align(Alignment.CenterStart).padding(start = Space.s16).staggered(parts[1]), visual = Dimens.arrowVisual, onClick = onPrev)
        if (onNext != null) IconCircle(VIcons.ChevronRight, stringResource(R.string.stage_next), Modifier.align(Alignment.CenterEnd).padding(end = Space.s16).staggered(parts[1]), visual = Dimens.arrowVisual, onClick = onNext)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Space.s24)) {
            // Eleven ticks on a small capsule: where this theme sits in the shelf.
            val inactive = Ink.tertiary
            val positionLabel = stringResource(R.string.stage_position, index + 1, count)
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = Space.s16)
                    .staggered(parts[2])
                    .material(MaterialToken.Regular, Shapes.capsule, blur = true, shadow = false)
                    .padding(horizontal = Space.s12, vertical = Space.s8)
                    .semantics { contentDescription = positionLabel },
            ) {
                Canvas(Modifier.size(Dimens.tickWide + (Dimens.tick + Space.s8) * (count - 1), Dimens.tick)) {
                    val t = Dimens.tick.toPx(); val wide = Dimens.tickWide.toPx(); val g = Space.s8.toPx()
                    var x = 0f
                    for (i in 0 until count) {
                        val wi = if (i == index) wide else t
                        drawRoundRect(if (i == index) chrome.tint else inactive, Offset(x, 0f), Size(wi, t), CornerRadius(t / 2f, t / 2f))
                        x += wi + g
                    }
                }
            }
            // The text crossfades with the passage (it never slides). [note]: the theme's note beside the name.
            val text: @Composable (Modifier, Boolean) -> Unit = { m, withNote ->
                androidx.compose.animation.AnimatedContent(
                    theme, m,
                    transitionSpec = { androidx.compose.animation.fadeIn(prefs.fade(Motion.CAPTION_MS)) togetherWith androidx.compose.animation.fadeOut(prefs.fade(Motion.FADE_FAST_MS)) },
                    label = "stageTitle",
                ) { t ->
                    val sp = ThemeRegistry.of(t)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(sp.title), style = VType.largeTitle, color = Ink.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(Space.s4))
                            Text(stringResource(sp.hint), style = VType.body, color = Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (withNote) sp.note?.let { n ->
                            Spacer(Modifier.width(Space.s32))
                            Box(Modifier.width(Dimens.hairline).height(Space.s64).drawBehind { drawRect(Color.Gray.copy(alpha = 0.25f)) })
                            Spacer(Modifier.width(Space.s32))
                            // The note shares the room with the name (the option chips take theirs first), so the
                            // theme's name is never cut.
                            Text(stringResource(n), Modifier.weight(1f, fill = false).widthIn(max = Dimens.stageNote), style = VType.callout, color = Ink.secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            val buttons: @Composable RowScope.() -> Unit = {
                if (onDemo != null) VButton(stringResource(R.string.stage_demo), ButtonStyle.Secondary, icon = VIcons.Play, onClick = onDemo)
                VButton(stringResource(R.string.stage_apply), ButtonStyle.Primary, onClick = onApply)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().staggered(parts[3])) {
                val panel = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.stagePanel)
                    .material(MaterialToken.Regular, Shapes.sheet, blur = true)
                    .padding(horizontal = Space.s32, vertical = Space.s24)
                if (maxWidth >= StagePanelOneRow) {
                    // One row: the name, hint and note on the left, the option chips and the buttons on the right.
                    Row(panel, verticalAlignment = Alignment.CenterVertically) {
                        text(Modifier.weight(1f), true)
                        Spacer(Modifier.width(Space.s32))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s12), verticalAlignment = Alignment.CenterVertically) {
                            chips()
                            buttons()
                        }
                    }
                } else {
                    // A narrower display: the name with the buttons, then the option chips with the note, so neither
                    // the theme's name nor "이 테마로 달리기" is ever cut and the panel stays clear of the arrows.
                    Column(panel) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            text(Modifier.weight(1f), false)
                            Spacer(Modifier.width(Space.s24))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.s12), verticalAlignment = Alignment.CenterVertically, content = buttons)
                        }
                        Spacer(Modifier.height(Space.s16))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The chips scroll sideways when a narrow window cannot hold them all.
                            Row(Modifier.weight(1f, fill = false).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s12), verticalAlignment = Alignment.CenterVertically, content = chips)
                            // The note needs room of its own: a window too narrow for both shows the chips.
                            if (this@BoxWithConstraints.maxWidth >= StagePanelNote) ThemeRegistry.of(theme).note?.let { n ->
                                Spacer(Modifier.width(Space.s24))
                                Text(stringResource(n), Modifier.widthIn(max = Dimens.stageNote), style = VType.callout, color = Ink.secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** From this width the stage panel keeps its text and its actions (the upgrade chip first) on one row. */
private val StagePanelOneRow = 1500.dp
/** Below this width the stage panel shows the option chips without the theme's note beside them. */
private val StagePanelNote = 1100.dp

/** The drag handling of the stage: axis-locked horizontal swipe (P5) or downward pull (P4). Taps pass through. */
internal fun Modifier.stageGestures(
    enabled: Boolean,
    onSwipeStart: () -> Unit, onSwipe: (dxPx: Float) -> Unit, onSwipeEnd: (vxPx: Float) -> Unit,
    onPullStart: () -> Unit, onPull: (dyPx: Float) -> Unit, onPullEnd: (vyPx: Float) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var axis = 0 // 0 undecided, 1 horizontal, 2 vertical
        var total = Offset.Zero
        while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            tracker.addPosition(ch.uptimeMillis, ch.position)
            val d = ch.position - ch.previousPosition
            total += d
            if (axis == 0 && total.getDistance() > slop) {
                axis = if (abs(total.x) > abs(total.y)) 1 else if (total.y > 0f) 2 else -1
                if (axis == 1) onSwipeStart() else if (axis == 2) onPullStart()
            }
            if (axis == 1) { ch.consume(); onSwipe(total.x) }
            if (axis == 2) { ch.consume(); onPull(total.y) }
            if (!ch.pressed) break
        }
        val v = tracker.calculateVelocity()
        if (axis == 1) onSwipeEnd(v.x)
        if (axis == 2) onPullEnd(v.y)
    }
}
