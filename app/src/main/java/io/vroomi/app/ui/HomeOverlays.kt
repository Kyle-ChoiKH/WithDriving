package io.vroomi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vroomi.app.R
import io.vroomi.app.diary.TripRecord
import io.vroomi.app.ui.design.ButtonStyle
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalDark
import io.vroomi.app.ui.design.MaterialToken
import io.vroomi.app.ui.design.RowDivider
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.Tag
import io.vroomi.app.ui.design.VButton
import io.vroomi.app.ui.design.VIcons
import io.vroomi.app.ui.design.VType
import io.vroomi.app.ui.design.grouped
import io.vroomi.app.ui.design.lifted
import io.vroomi.app.ui.design.material
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.pressFeedback
import io.vroomi.core.vehicle.sim.Scenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * First launch: who Vroomi is, and a one-tap demo drive (for the emulator and the judges). Rises 24 dp and scales
 * from 0.96; its three lines stagger by 60 ms and the buttons arrive last. A Thick material card (44 dp corners).
 */
@Composable
fun WelcomeCard(onDemo: () -> Unit, onStart: () -> Unit) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val chrome = LocalChrome.current
    val dark = LocalDark.current
    val rise = remember { Animatable(if (prefs.still) 1f else 0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, if (prefs.reduced) prefs.fade(Motion.REDUCED_MS) else prefs.spring(Motion.Smooth)) }
    val parts = rememberStagger(Unit, 5, Motion.WELCOME_STAGGER_MS, startMs = Motion.CONTENT_DELAY_MS)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = rise.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = if (dark) 0.40f else 0.22f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = Dimens.welcomeMax)
                .padding(Space.s24)
                .graphicsLayer {
                    val v = rise.value
                    if (!prefs.reduced) {
                        translationY = (1f - v) * Space.s24.toPx()
                        scaleX = 0.96f + 0.04f * v; scaleY = 0.96f + 0.04f * v
                    }
                }
                .lifted(Shapes.r44, { Color.Black }, { rise.value })
                .material(MaterialToken.Thick, Shapes.r44, blur = true, shadow = false)
                .padding(horizontal = Space.s48, vertical = Space.s40),
        ) {
            Text(stringResource(R.string.welcome_title), style = VType.title, color = Ink.primary)
            Spacer(Modifier.height(Space.s24))
            listOf(R.string.welcome_1, R.string.welcome_2, R.string.welcome_3).forEachIndexed { i, line ->
                Row(Modifier.padding(vertical = Space.s8).staggered(parts[i])) {
                    // The dot sits on the first line's optical centre (32 sp line height).
                    Box(Modifier.padding(top = 13.dp).size(Space.s8).drawBehind { drawCircle(chrome.tint) })
                    Spacer(Modifier.width(Space.s16))
                    Text(stringResource(line), style = VType.body, color = Ink.primary)
                }
            }
            Spacer(Modifier.height(Space.s16))
            Text(stringResource(R.string.welcome_safety), Modifier.padding(start = Space.s24).staggered(parts[3]), style = VType.callout, color = Ink.secondary)
            Spacer(Modifier.height(Space.s32))
            Row(Modifier.staggered(parts[4]), horizontalArrangement = Arrangement.spacedBy(Space.s12)) {
                VButton(stringResource(R.string.welcome_demo), ButtonStyle.Primary, icon = VIcons.Play, onClick = onDemo)
                VButton(stringResource(R.string.welcome_start), ButtonStyle.Secondary, onClick = onStart)
            }
        }
    }
}

/** Pick a bundled scenario (in a sheet); the car is simulated and captions explain each reaction. */
@Composable
fun DemoPicker(scenarios: List<Scenario>, onPick: (String) -> Unit, onClose: () -> Unit, visible: Boolean = true, progress: (Float) -> Unit = {}) {
    // On a pick the sheet collapses toward the bottom-right corner, where the demo dock will appear.
    BottomSheet(visible, onClose, collapseTo = TransformOrigin(1f, 1f), progress = progress) {
        DemoPickerContent(scenarios, onPick, visible)
    }
}

@Composable
private fun DemoPickerContent(scenarios: List<Scenario>, onPick: (String) -> Unit, visible: Boolean) {
    val rows = rememberStagger(visible, 2, Motion.SHEET_STAGGER_MS)
    Column(Modifier.heightIn(max = Dimens.sheetMax).verticalScroll(rememberScrollState())) {
        Column(Modifier.staggered(rows[0])) { SheetHeader(stringResource(R.string.demo_drive), stringResource(R.string.demo_hint)) }
        // One grouped list: each scenario is a row, its action a round play button at the end.
        Column(Modifier.fillMaxWidth().staggered(rows[1]).grouped(Shapes.card)) {
            scenarios.forEachIndexed { i, sc ->
                if (i > 0) RowDivider()
                ScenarioRow(sc) { onPick(sc.id) }
            }
        }
    }
}

@Composable
private fun ScenarioRow(sc: Scenario, onPlay: () -> Unit) {
    val chrome = LocalChrome.current
    val play = stringResource(R.string.demo_play)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.listRow)
            .pressFeedback(Shapes.card, large = true)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onPlay)
            .padding(start = Space.s24, end = Space.s20, top = Space.s16, bottom = Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(sc.title, style = VType.headline, color = Ink.primary)
            if (sc.description.isNotBlank()) {
                Spacer(Modifier.height(Space.s4))
                Text(sc.description, style = VType.callout, color = Ink.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Space.s24))
        Box(
            Modifier.size(Dimens.buttonCompact).drawBehind { drawCircle(chrome.tint) }.semantics { contentDescription = "$play: ${sc.title}" },
            contentAlignment = Alignment.Center,
        ) {
            Image(VIcons.Play, null, Modifier.size(Dimens.iconSmall), colorFilter = ColorFilter.tint(chrome.onTint))
        }
    }
}

/**
 * Shown once on the parked home screen right after a trip ends; closes itself after 30 s. Slides 32 dp in from
 * the right with a fade; its numbers count up over at most 600 ms with tabular digits. The score sits in a thin
 * ring in its semantic colour.
 */
@Composable
fun TripSummaryCard(t: TripRecord, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    val prefs = io.vroomi.app.ui.motion.currentMotion()
    val dark = LocalDark.current
    val enter = remember { Animatable(if (prefs.still) 1f else 0f) }
    val count = remember { Animatable(if (prefs.still) 1f else 0f) }
    LaunchedEffect(t.id) {
        launch { enter.animateTo(1f, if (prefs.reduced) prefs.fade(Motion.REDUCED_MS) else prefs.spring(Motion.Smooth)) }
        if (prefs.reduced || !prefs.parked) count.snapTo(1f)
        else { delay(prefs.ms(Motion.CONTENT_DELAY_MS)); count.animateTo(1f, tween((Motion.COUNT_UP_MS * prefs.slow).toInt()) { Motion.MovingCurve.ease(it) }) }
    }
    LaunchedEffect(t.id) { delay(30_000); onDismiss() }
    val scoreC = Color(scoreColor(t.score, dark))
    val track = Ink.separator
    Column(
        modifier
            .width(Dimens.tripCard)
            .graphicsLayer {
                val v = enter.value
                alpha = v.coerceIn(0f, 1f)
                if (!prefs.reduced) translationX = (1f - v) * Space.s32.toPx()
            }
            .material(MaterialToken.Thick, Shapes.sheet, blur = true)
            .padding(Space.s32),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.trip_card_title), style = VType.title, color = Ink.primary, modifier = Modifier.weight(1f))
            if (t.demo) Tag(stringResource(R.string.demo_tag))
        }
        Spacer(Modifier.height(Space.s24))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(Dimens.scoreDisc).drawBehind {
                    val sw = 6.dp.toPx()
                    val inset = sw / 2f
                    val arc = Size(size.width - sw, size.height - sw)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                    drawArc(scoreC, -90f, 360f * (t.score / 100f).coerceIn(0f, 1f) * count.value, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
                },
                contentAlignment = Alignment.Center,
            ) {
                Text("${(t.score * count.value).roundToInt()}", style = VType.title.merge(VType.tabular), color = Ink.primary)
            }
            Spacer(Modifier.width(Space.s24))
            Column {
                Text(stringResource(R.string.trip_stars, (t.stars * count.value).roundToInt()), style = VType.headline.merge(VType.tabular), color = Ink.primary)
                Spacer(Modifier.height(Space.s4))
                Text("%.1f km · %s".format(t.distanceKm * count.value, t.sticker.label), style = VType.body.merge(VType.tabular), color = Ink.primary)
                Spacer(Modifier.height(Space.s4))
                Text(stringResource(R.string.trip_biomes, t.biomes), style = VType.callout, color = Ink.secondary)
            }
        }
        Spacer(Modifier.height(Space.s32))
        VButton(stringResource(R.string.ok), ButtonStyle.Primary, Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}
