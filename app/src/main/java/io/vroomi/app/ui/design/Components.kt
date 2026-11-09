package io.vroomi.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.currentMotion
import io.vroomi.app.ui.motion.pressFeedback
import kotlinx.coroutines.launch

// =====================================================================================================================
// Ink: the text tiers on the current surface
// =====================================================================================================================

/** Text tiers. Tertiary (44 %) reaches only 2.8:1 on the light surface, so it is used for marks, never for text. */
object Ink {
    val primary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val secondary: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = Neutral.SECONDARY)
    val tertiary: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = Neutral.TERTIARY)
    val separator: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = Neutral.SEPARATOR)
    /** The fill of a quiet control (a track, the rail's selection). */
    val fill: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalDark.current) Neutral.FILL_DARK else Neutral.FILL_LIGHT)
}

@Composable
private fun Modifier.tapRemembered(role: Role, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = role, onClick = onClick)

@Composable
private fun Glyph(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

// =====================================================================================================================
// Buttons
// =====================================================================================================================

/**
 * The three button styles, one family: the same capsule, height, padding and type.
 * - [Primary]: the one action of a screen, filled with the accent.
 * - [Secondary]: every other action; a material capsule (it reads over a scene and on a surface).
 * - [Plain]: a quiet action on a surface (a list row, a sheet): the label in the accent, no container.
 */
enum class ButtonStyle { Primary, Secondary, Plain }

/** A capsule button. [compact]: the 52 dp size for rows and cards. [onScene]: a secondary over the live scene (blurred, lifted). */
@Composable
fun VButton(
    label: String,
    style: ButtonStyle,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    compact: Boolean = false,
    onScene: Boolean = false,
    onClick: () -> Unit,
) {
    val chrome = LocalChrome.current
    val h = if (compact) Dimens.buttonCompact else Dimens.button
    val content = when (style) {
        ButtonStyle.Primary -> chrome.onTint
        ButtonStyle.Secondary -> Ink.primary
        ButtonStyle.Plain -> chrome.tint
    }
    Row(
        modifier
            .height(h)
            .then(
                when (style) {
                    ButtonStyle.Primary -> Modifier.elevation(if (onScene) Elevation.Low else Elevation.None, Shapes.capsule).drawBehind {
                        drawRoundRect(chrome.tint, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f))
                    }
                    ButtonStyle.Secondary -> Modifier.material(if (onScene) MaterialToken.Regular else MaterialToken.Thin, Shapes.capsule, blur = onScene, shadow = onScene)
                    ButtonStyle.Plain -> Modifier
                },
            )
            .pressFeedback(Shapes.capsule)
            .tapRemembered(Role.Button, onClick = onClick)
            .padding(start = if (icon != null) (if (compact) Space.s16 else Space.s24) else (if (compact) Space.s20 else Space.s32), end = if (compact) Space.s20 else Space.s32),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Glyph(icon, content, if (compact) Dimens.iconSmall else Dimens.buttonIcon)
            Spacer(Modifier.width(if (compact) Space.s8 else Space.s12))
        }
        Text(label, style = if (compact) VType.callout.copy(fontWeight = VType.headline.fontWeight) else VType.headline, color = content, maxLines = 1)
    }
}

/**
 * The driving toggle: 168 × 96 dp, one tap, no confirmation, an icon over a 24 sp label. Off: the Thick material
 * (static while moving), the icon in the accent. On: a calm accent wash (the accent at 20 % over the surface,
 * with an accent hairline), its icon and label in the accent deepened to 4.5:1 on that wash. Feedback is the new
 * state only (nothing moves while driving).
 */
@Composable
fun BigToggle(label: String, icon: ImageVector?, on: Boolean, modifier: Modifier = Modifier, role: Role = Role.Switch, onClick: () -> Unit) {
    val chrome = LocalChrome.current
    val dark = LocalDark.current
    val wash = ColorMath.over((((if (dark) 0.24f else 0.18f) * 255).toInt() shl 24) or (chrome.tintArgb and 0xFFFFFF), Neutral.surface(dark))
    val onInk = Color(ColorMath.ensureContrast(chrome.tintArgb, wash, 4.6f))
    val ink = if (on) onInk else Ink.primary
    Column(
        modifier
            .size(width = Dimens.bigToggleW, height = Dimens.bigToggleH)
            .then(
                if (on) Modifier.elevation(Elevation.High, Shapes.toggle).drawBehind {
                    val o = Shapes.toggle.createOutline(size, layoutDirection, this)
                    drawOutline(o, Color(wash))
                    drawOutline(o, chrome.tint.copy(alpha = 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f.dp.toPx()))
                }
                else Modifier.material(MaterialToken.Thick, Shapes.toggle),
            )
            .pressFeedback(Shapes.toggle)
            .tapRemembered(role, onClick = onClick)
            .semantics { if (role == Role.Switch) stateDescription = if (on) "켜짐" else "꺼짐" }
            .padding(horizontal = Space.s12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Glyph(icon, if (on) ink else chrome.tint, Dimens.bigToggleIcon)
            Spacer(Modifier.height(Space.s4))
        }
        Text(label, style = VType.drive, textAlign = TextAlign.Center, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =====================================================================================================================
// Lists, sections, switches
// =====================================================================================================================

/**
 * A grouped inset section: an optional header above (caption, secondary), the rows on one grouped surface
 * (rows separated by inset hairlines, added by [SectionScope.Row]-style callers with [RowDivider]), and an
 * optional footnote below.
 */
@Composable
fun Section(header: String?, footnote: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(header, Modifier.padding(start = Space.s24, bottom = Space.s8), style = VType.caption, color = Ink.secondary)
        }
        Column(Modifier.fillMaxWidth().grouped(Shapes.card), content = content)
        if (footnote != null) {
            Text(footnote, Modifier.padding(start = Space.s24, end = Space.s24, top = Space.s8), style = VType.callout, color = Ink.secondary)
        }
    }
}

/** A hairline between rows, inset from the leading edge (it never runs under the row's leading text margin). */
@Composable
fun RowDivider(inset: Dp = Space.s24) {
    val c = Ink.separator
    Box(Modifier.fillMaxWidth().padding(start = inset).height(Dimens.hairline).drawBehind { drawRect(c) })
}

/**
 * A list row: a title with an optional subtitle on the leading side, an optional [trailing] control. At least
 * 80 dp tall. [onClick]: the whole row is the target (a switch row toggles from anywhere on it).
 */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    role: Role = Role.Button,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.listRow)
            .then(if (onClick != null) Modifier.pressFeedback(Shapes.card, large = true).tapRemembered(role, onClick = onClick) else Modifier)
            .padding(horizontal = Space.s24, vertical = Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = VType.headline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = if (titleColor == Color.Unspecified) Ink.primary else titleColor)
            if (subtitle != null) {
                Spacer(Modifier.height(Space.s4))
                Text(subtitle, style = VType.callout, color = Ink.secondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.s24))
            trailing()
        }
    }
}

/** A row with a switch; the whole row toggles. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title, subtitle, role = Role.Switch, onClick = { onChange(!checked) }) { VSwitch(checked, onChange) }
}

/**
 * A switch drawn in code: a 64 × 38 dp capsule track (the accent when on, the quiet fill when off) and a white
 * thumb with a soft shadow that slides on the Snappy spring. 48 dp to touch.
 */
@Composable
fun VSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val prefs = currentMotion()
    val chrome = LocalChrome.current
    val dark = LocalDark.current
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { if (prefs.still) pos.snapTo(if (checked) 1f else 0f) else pos.animateTo(if (checked) 1f else 0f, prefs.spring(Motion.Snappy)) }
    val off = Color(Neutral.ink(dark)).copy(alpha = if (dark) 0.28f else 0.16f)
    val density = LocalDensity.current
    val thumbShadow = remember(density) { Elevation.Low }
    Box(
        modifier
            .size(Dimens.switchW, Dimens.touchMin)
            .tapRemembered(Role.Switch) { onChange(!checked) }
            .semantics { stateDescription = if (checked) "켜짐" else "꺼짐" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(Dimens.switchW, Dimens.switchH).drawBehind {
                val p = pos.value
                val r = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(androidx.compose.ui.graphics.lerp(off, chrome.tint, p), cornerRadius = r)
                val inset = Dimens.switchThumbInset.toPx()
                val d = size.height - inset * 2
                val x = inset + (size.width - inset * 2 - d) * p
                val c = Offset(x + d / 2f, size.height / 2f)
                drawCircle(Color.Black.copy(alpha = 0.10f * thumbShadow.alphaScale(dark)), d / 2f, c + Offset(0f, 1.5f.dp.toPx()))
                drawCircle(Color.White, d / 2f, c)
            },
        )
    }
}

// =====================================================================================================================
// Segmented control
// =====================================================================================================================

/**
 * A segmented control: a quiet capsule track and one raised thumb (the grouped surface with a soft shadow) that
 * slides to the chosen segment on the Snappy spring. Segments size to their labels (at least 88 dp) unless
 * [equal]. Labels are 20 sp; the chosen one is semibold.
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    equal: Boolean = false,
    onSelect: (T) -> Unit,
) {
    val prefs = currentMotion()
    val dark = LocalDark.current
    val density = LocalDensity.current
    val xs = remember(options.size) { mutableStateListOf<Float>().apply { repeat(options.size) { add(0f) } } }
    val ws = remember(options.size) { mutableStateListOf<Float>().apply { repeat(options.size) { add(0f) } } }
    val sel = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(sel, ws[sel], xs[sel]) {
        if (!placed || ws[sel] <= 0f || prefs.still) { x.snapTo(xs[sel]); w.snapTo(ws[sel]); placed = ws[sel] > 0f; return@LaunchedEffect }
        launch { x.animateTo(xs[sel], prefs.spring(Motion.Snappy)) }
        w.animateTo(ws[sel], prefs.spring(Motion.Snappy))
    }
    val track = Ink.fill
    val thumb = Color(if (dark) 0xFF3A3E45.toInt() else Neutral.GROUPED_LIGHT)
    val inset = Dimens.segmentedInset
    Box(
        modifier
            .height(Dimens.segmented)
            .drawBehind { drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)) }
            .padding(inset),
    ) {
        Box(
            Modifier
                .height(Dimens.segmented - inset * 2)
                .graphicsLayer { translationX = x.value }
                .width(with(density) { w.value.toDp() })
                .elevation(Elevation.Low, Shapes.capsule, strength = if (dark) 0.6f else 1f)
                .drawBehind { drawRoundRect(thumb, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)) },
        )
        Row(Modifier.then(if (equal) Modifier.fillMaxWidth() else Modifier)) {
            options.forEachIndexed { i, (v, text) ->
                val on = i == sel
                Box(
                    Modifier
                        .then(if (equal) Modifier.weight(1f) else Modifier.widthIn(min = 88.dp))
                        .height(Dimens.segmented - inset * 2)
                        .onPlaced { lc ->
                            xs[i] = lc.parentLayoutCoordinates?.localPositionOf(lc, Offset.Zero)?.x ?: 0f
                            ws[i] = lc.size.width.toFloat()
                        }
                        .tapRemembered(Role.RadioButton) { onSelect(v) }
                        .semantics { this.selected = on }
                        .padding(horizontal = Space.s20),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text, style = VType.callout.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                        color = if (on) Ink.primary else Ink.secondary, maxLines = 1,
                    )
                }
            }
        }
    }
}

// =====================================================================================================================
// Chips, tags, progress
// =====================================================================================================================

/** An option chip (a label and value that opens a sheet): a compact secondary capsule with a chevron. */
@Composable
fun OptionChip(label: String, modifier: Modifier = Modifier, onScene: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .height(Dimens.chipHeight)
            .material(if (onScene) MaterialToken.Regular else MaterialToken.Thin, Shapes.capsule, blur = onScene, shadow = false)
            .pressFeedback(Shapes.capsule)
            .tapRemembered(Role.Button, onClick = onClick)
            .padding(start = Space.s20, end = Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = VType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = Ink.primary, maxLines = 1)
        Spacer(Modifier.width(Space.s8))
        Glyph(VIcons.ChevronDown, Ink.secondary, 18.dp)
    }
}

/** A small status tag ("지금 테마", "데모"): the accent with a check, or a quiet neutral capsule. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, accent: Boolean = true, check: Boolean = false) {
    val chrome = LocalChrome.current
    val fill = Ink.fill
    Row(
        modifier
            .height(32.dp)
            .drawBehind { drawRoundRect(if (accent) chrome.tint else fill, cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)) }
            .padding(start = if (check) Space.s8 else Space.s12, end = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (accent) chrome.onTint else Ink.primary
        if (check) { Glyph(VIcons.Check, c, 18.dp); Spacer(Modifier.width(Space.s4)) }
        Text(text, style = VType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c, maxLines = 1)
    }
}

/** A thin capsule progress bar: the quiet track and the accent fill. [fraction] is read while drawing. */
@Composable
fun ProgressBar(fraction: () -> Float, modifier: Modifier = Modifier, height: Dp = Dimens.progress, track: Color = Ink.separator) {
    val chrome = LocalChrome.current
    Box(
        modifier.fillMaxWidth().height(height).drawBehind {
            val r = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(track, cornerRadius = r)
            val w = size.width * fraction().coerceIn(0f, 1f)
            if (w > 0.5f) drawRoundRect(chrome.tint, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
        },
    )
}

/** A rail item: a 28 dp line icon over a 20 sp label; the chosen one in the accent (icon) and primary ink (label). */
@Composable
fun RailItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val chrome = LocalChrome.current
    Column(
        modifier
            .fillMaxWidth()
            .height(Dimens.railItem)
            .pressFeedback(Shapes.r24)
            .tapRemembered(Role.Tab, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(icon, if (selected) chrome.tint else Ink.secondary, Dimens.railIcon)
        Spacer(Modifier.height(Space.s4))
        Text(
            label,
            style = VType.callout.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
            color = if (selected) Ink.primary else Ink.secondary, maxLines = 1,
        )
    }
}

/** A page title with an optional subtitle (the large title role). */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier) {
        Text(text, style = VType.largeTitle, color = Ink.primary)
        if (subtitle != null) {
            Spacer(Modifier.height(Space.s4))
            Text(subtitle, style = VType.callout, color = Ink.secondary)
        }
    }
}

