package io.vroomi.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.vroomi.app.R
import io.vroomi.app.ui.design.BigToggle
import io.vroomi.app.ui.design.ButtonStyle
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalDark
import io.vroomi.app.ui.design.MaterialToken
import io.vroomi.app.ui.design.Neutral
import io.vroomi.app.ui.design.PageTitle
import io.vroomi.app.ui.design.ProgressBar
import io.vroomi.app.ui.design.RailItem
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.VButton
import io.vroomi.app.ui.design.VIcons
import io.vroomi.app.ui.design.VType
import io.vroomi.app.ui.design.material
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.pressFeedback
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

enum class Tab(val label: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, VIcons.Home),
    FRIENDS(R.string.tab_friends, VIcons.Worlds),
    DIARY(R.string.tab_diary, VIcons.Diary),
    WARDROBE(R.string.tab_wardrobe, VIcons.Wardrobe),
    SETTINGS(R.string.tab_settings, VIcons.Settings),
}

/** The text tiers (see [io.vroomi.app.ui.design.Ink]). */
internal val Ink get() = io.vroomi.app.ui.design.Ink

/**
 * The tab rail: a line icon over a small label per tab, and one selection plate (the quiet fill) that slides
 * between the items and stretches mid-flight. The rail is the page surface with a hairline towards the scene.
 */
@Composable
internal fun TabRail(selected: Tab, modifier: Modifier = Modifier, debugFrom: Float? = null, debugAt: Float? = null, onSelect: (Tab) -> Unit) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val dark = LocalDark.current
    val index = selected.ordinal.toFloat()
    val pos = remember { Animatable(index) }
    var from by remember { mutableFloatStateOf(index) }
    LaunchedEffect(index) { from = pos.value; pos.animateTo(index, prefs.spring(Motion.Snappy)) }
    val count = Tab.entries.size
    val plate = Ink.fill
    val hair = Ink.separator
    val step = Dimens.railItem + Space.s8
    Box(
        modifier.fillMaxHeight().width(Dimens.railWidth).drawBehind {
            drawRect(Color(Neutral.surface(dark)).copy(alpha = 0.96f))
            drawRect(hair, Offset(size.width - Dimens.hairline.toPx(), 0f), Size(Dimens.hairline.toPx(), size.height))
        },
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(Dimens.railItem * count + Space.s8 * (count - 1))) {
            Box(
                Modifier
                    .padding(horizontal = Space.s12)
                    .fillMaxWidth()
                    .height(Dimens.railItem)
                    .graphicsLayer {
                        val p = debugAt ?: pos.value
                        val f0 = debugFrom ?: from
                        translationY = p * step.toPx()
                        val span = abs(index - f0)
                        val prog = if (span < 0.01f) 1f else (abs(p - f0) / span).coerceIn(0f, 1f)
                        scaleY = 1f + 0.12f * sin(PI.toFloat() * prog) * min(span, 1f)
                    }
                    .drawBehind {
                        val r = 20.dp.toPx()
                        drawRoundRect(plate, cornerRadius = CornerRadius(r, r))
                    },
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                Tab.entries.forEach { t ->
                    RailItem(t.icon, stringResource(t.label), t == selected, Modifier.padding(horizontal = Space.s12)) { onSelect(t) }
                }
            }
        }
    }
}

/** A round icon button on Thin material: [visual] dp drawn, at least 64 dp to touch. */
@Composable
internal fun IconCircle(icon: ImageVector, label: String, modifier: Modifier = Modifier, visual: androidx.compose.ui.unit.Dp = Dimens.closeVisual, onClick: () -> Unit) {
    Box(
        modifier
            .size(Dimens.touch)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(visual).material(MaterialToken.Regular, Shapes.capsule, blur = true).pressFeedback(Shapes.capsule), contentAlignment = Alignment.Center) {
            Image(icon, null, Modifier.size(Dimens.iconSmall), colorFilter = ColorFilter.tint(Ink.primary))
        }
    }
}

/** A page's title (the large title role) with the space below it. */
@Composable
internal fun PanelTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    PageTitle(text, modifier.fillMaxWidth().padding(bottom = Space.s32), subtitle)
}

/**
 * The bond badge (top left, parked): a Regular material card with the level, the star count, a thin progress bar
 * in the accent and the distance to the next level. Its size is the chrome-zone contract's status corner.
 */
@Composable
internal fun BondBadge(level: Int, stars: Int, progress: Float, detail: String, modifier: Modifier = Modifier) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val fill = remember { Animatable(progress) }
    LaunchedEffect(progress) { fill.animateTo(progress, prefs.spring(Motion.Smooth)) }
    Column(
        modifier
            .width(Dimens.badgeWidth)
            .heightIn(max = Dimens.badgeHeight)
            .material(MaterialToken.Regular, Shapes.card, blur = true)
            .padding(horizontal = Space.s24, vertical = Space.s20),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.bond_level, level), Modifier.weight(1f), style = VType.headline, color = Ink.primary, maxLines = 1)
            Image(VIcons.Star, null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(VroomiColors.Amber))
            Spacer(Modifier.width(Space.s4))
            Text(stringResource(R.string.stars_total, stars), style = VType.callout.merge(VType.tabular), color = Ink.secondary, maxLines = 1)
        }
        Spacer(Modifier.height(Space.s16))
        ProgressBar({ fill.value })
        Spacer(Modifier.height(Space.s12))
        Text(detail, style = VType.callout.merge(VType.tabular), color = Ink.secondary, maxLines = 1)
    }
}

/**
 * The home actions (bottom right, parked): a right-aligned stack of two equal capsules, the secondary above the
 * primary. Its size is the chrome-zone contract's action corner.
 */
@Composable
internal fun HomeActions(
    demo: Boolean, listening: Boolean, canTalk: Boolean, modifier: Modifier = Modifier,
    onDemo: () -> Unit, onTalk: () -> Unit,
) {
    Column(modifier.width(Dimens.actionsWidth), verticalArrangement = Arrangement.spacedBy(Space.s12)) {
        if (canTalk && !demo) {
            VButton(
                stringResource(if (listening) R.string.listening else R.string.talk),
                if (listening) ButtonStyle.Primary else ButtonStyle.Secondary,
                Modifier.fillMaxWidth(), icon = VIcons.Mic, onScene = true, onClick = onTalk,
            )
        }
        VButton(
            stringResource(if (demo) R.string.demo_end else R.string.demo_drive),
            if (demo) ButtonStyle.Secondary else ButtonStyle.Primary,
            Modifier.fillMaxWidth(), icon = if (demo) VIcons.Stop else VIcons.Play, onScene = true, onClick = onDemo,
        )
    }
}

/**
 * The speech bubble (parked): grows from below, near the friend's head (scale 0.9 to 1 on Snappy); the text fades
 * in 80 ms later. A new line crossfades while the bubble's width springs. Thick material.
 */
@Composable
internal fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val grow = remember { Animatable(if (prefs.still) 1f else 0.9f) }
    val fade = remember { Animatable(if (prefs.still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (prefs.still) return@LaunchedEffect
        if (prefs.reduced) { grow.snapTo(1f); fade.animateTo(1f, prefs.fade(Motion.REDUCED_MS)); return@LaunchedEffect }
        coroutineScope {
            launch { grow.animateTo(1f, prefs.spring(Motion.Snappy)) }
            delay(prefs.ms(80))
            fade.animateTo(1f, prefs.fade(Motion.FADE_IN_MS))
        }
    }
    Box(
        modifier
            .widthIn(max = Dimens.bubbleMax)
            .graphicsLayer {
                scaleX = grow.value; scaleY = grow.value
                alpha = ((grow.value - 0.9f) / 0.1f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .material(MaterialToken.Thick, Shapes.card, blur = true)
            .animateContentSize(prefs.spring(Motion.Snappy))
            .padding(horizontal = Space.s24, vertical = Space.s16),
    ) {
        AnimatedContent(text, transitionSpec = { fadeIn(prefs.fade(Motion.FADE_IN_MS)) togetherWith fadeOut(prefs.fade(Motion.FADE_FAST_MS)) }, label = "bubble") { line ->
            Text(line, Modifier.graphicsLayer { alpha = fade.value }, style = VType.headline, textAlign = TextAlign.Center, color = Ink.primary)
        }
    }
}

/** A short confirmation (parked only), for example "테마를 바꿨어요": a check in the accent and the line. */
@Composable
internal fun ConfirmChip(text: String, modifier: Modifier = Modifier) {
    val chrome = LocalChrome.current
    Row(
        modifier.height(Dimens.chipHeight).material(MaterialToken.Thick, Shapes.capsule).padding(start = Space.s16, end = Space.s24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(VIcons.Check, null, Modifier.size(Dimens.iconSmall), colorFilter = ColorFilter.tint(chrome.tint))
        Spacer(Modifier.width(Space.s8))
        Text(text, style = VType.headline, color = Ink.primary)
    }
}

/**
 * Moving (ambient) mode, laid out by the chrome-zone contract: the status chip in the status corner (top left)
 * and the dock in the action corner (bottom right): the big toggles in one row, and in a demo the caption card
 * above them, exactly as wide as the row. Nothing slides: everything here only fades. The chip's text crossfades
 * over 250 ms and its width eases over 250 ms; every material is static (no blur while driving).
 */
@Composable
internal fun MovingOverlay(
    chip: String?, muted: Boolean, calm: Boolean, onMute: () -> Unit, onCalm: () -> Unit,
    demoCaption: String?, demoProgress: Float?, onEndDemo: () -> Unit,
    modifier: Modifier = Modifier,
    sceneTheme: io.vroomi.character.calibre.SceneTheme? = null,
) {
    // Over a dark scene (CALIBRE's velvet, ORBIT's display) the driving chrome is the night chrome even by day:
    // a light dock on a black scene would be the brightest thing in the car.
    val amb = sceneTheme?.let { io.vroomi.app.ui.design.Ambience.of(it) }
    if (amb != null && !LocalDark.current && amb.luminance < DARK_SCENE_LUMINANCE) {
        val night = remember(amb) { io.vroomi.app.ui.design.ChromeColors(amb, true) }
        VroomiTheme(night = true, theme = amb.theme) {
            androidx.compose.runtime.CompositionLocalProvider(io.vroomi.app.ui.design.LocalChrome provides night) {
                MovingOverlay(chip, muted, calm, onMute, onCalm, demoCaption, demoProgress, onEndDemo, modifier, null)
            }
        }
        return
    }
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    Box(modifier.fillMaxSize().padding(Space.moving)) {
        if (!chip.isNullOrBlank()) StatusChip(chip, Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
            if (demoProgress != null) {
                DemoCaption(demoCaption, demoProgress, prefs)
                Spacer(Modifier.height(Dimens.captionGap))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.bigToggleGap)) {
                if (demoProgress != null) BigToggle(stringResource(R.string.demo_end), VIcons.Stop, on = false, role = Role.Button, onClick = onEndDemo)
                BigToggle(stringResource(if (muted) R.string.unmute else R.string.mute), if (muted) VIcons.SpeakerOff else VIcons.Speaker, muted, onClick = onMute)
                BigToggle(stringResource(if (calm) R.string.calm_off else R.string.calm), VIcons.Calm, calm, onClick = onCalm)
            }
        }
    }
}

/** A scene darker than this (mean relative luminance) gets the night driving chrome. */
internal const val DARK_SCENE_LUMINANCE = 0.12f

/** The moving status chip: at most 12 characters at 26 sp, after a small accent dot. */
@Composable
internal fun StatusChip(text: String, modifier: Modifier = Modifier) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val chrome = LocalChrome.current
    Row(
        modifier
            .height(Dimens.statusChipH)
            .widthIn(max = Dimens.statusChipMaxW)
            .material(MaterialToken.Thick, Shapes.capsule)
            .animateContentSize(androidx.compose.animation.core.tween((Motion.CHIP_MS * prefs.slow).toInt()))
            .padding(start = Space.s20, end = Space.s24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Dimens.statusDot).drawBehind { drawCircle(chrome.tint) })
        Spacer(Modifier.width(Space.s12))
        // Always 12 characters or fewer (CharacterDirector guarantees it).
        AnimatedContent(text.take(12), transitionSpec = { fadeIn(prefs.fade(Motion.CHIP_MS)) togetherWith fadeOut(prefs.fade(Motion.CHIP_MS)) }, label = "chip") {
            Text(it, style = VType.driveChip, color = Ink.primary, maxLines = 1)
        }
    }
}

/**
 * The demo caption (moving, demo only): one quiet card in the dock, as wide as the row of three toggles. "데모" in
 * the accent leads the line; the drive's progress runs along the bottom as a hairline bar. At most two lines.
 */
@Composable
private fun DemoCaption(caption: String?, progress: Float, prefs: io.vroomi.app.ui.motion.MotionPrefs) {
    val chrome = LocalChrome.current
    val tag = stringResource(R.string.demo_tag)
    val badge = stringResource(R.string.demo_badge)
    Column(
        Modifier
            .width(Dimens.captionW)
            .heightIn(max = Dimens.captionMaxH)
            .material(MaterialToken.Thick, Shapes.card)
            .padding(start = Space.s24, end = Space.s24, top = Space.s16, bottom = Space.s16),
    ) {
        AnimatedContent(caption, transitionSpec = { fadeIn(prefs.fade(Motion.CAPTION_MS)) togetherWith fadeOut(prefs.fade(Motion.CAPTION_MS)) }, label = "caption") { line ->
            val text = buildAnnotatedString {
                withStyle(SpanStyle(color = chrome.tint, fontWeight = VType.drive.fontWeight)) { append(tag) }
                append("  ")
                append(if (line.isNullOrBlank()) badge else line)
            }
            Text(text, style = VType.driveBody, color = Ink.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Space.s12))
        ProgressBar({ progress }, height = Dimens.progressThin)
    }
}

/** Aligns a child to the bottom-start corner (helper for overlays that take a BoxScope). */
internal fun Modifier.bottomStart(scope: BoxScope): Modifier = with(scope) { this@bottomStart.align(Alignment.BottomStart) }

/** True in dark mode (for drawing code). */
@Composable
internal fun isDark(): Boolean = LocalDark.current

internal val ZeroOffset = Offset.Zero
