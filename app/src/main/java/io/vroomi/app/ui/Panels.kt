package io.vroomi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vroomi.app.BuildConfig
import io.vroomi.app.R
import io.vroomi.app.diary.Bond
import io.vroomi.app.diary.TripRecord
import io.vroomi.app.runtime.VroomiRuntime
import io.vroomi.app.settings.CharacterSize
import io.vroomi.app.settings.Settings
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.ListRow
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalDark
import io.vroomi.app.ui.design.Radius
import io.vroomi.app.ui.design.RowDivider
import io.vroomi.app.ui.design.Section
import io.vroomi.app.ui.design.SegmentedControl
import io.vroomi.app.ui.design.Semantic
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.SwitchRow
import io.vroomi.app.ui.design.Tag
import io.vroomi.app.ui.design.VType
import io.vroomi.app.ui.design.grouped
import io.vroomi.app.ui.motion.Motion
import io.vroomi.app.ui.motion.pressFeedback
import io.vroomi.character.Accessory
import io.vroomi.core.vehicle.SourceMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A list page (wardrobe, settings): the page title and its content with the page margins. The content gets the
 * pane's inner width (to choose one or two columns).
 */
@Composable
private fun Page(title: String, subtitle: String? = null, content: @Composable ColumnScope.(pane: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val margin = Space.margin(maxWidth >= Dimens.wideBreak / 2)
        val inner = maxWidth - margin * 2
        val parts = rememberStagger(Unit, 2, Motion.STAGGER_MS)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = margin).padding(top = margin, bottom = Space.s40),
        ) {
            Box(Modifier.staggered(parts[0])) { PanelTitle(title, subtitle = subtitle) }
            Column(Modifier.staggered(parts[1])) { content(inner) }
        }
    }
}

// ---------------------------------------------------------------- Diary (parked only)

@Composable
fun DiaryPanel(trips: List<TripRecord>) {
    val parts = rememberStagger(Unit, 3, Motion.STAGGER_MS)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val margin = Space.margin(maxWidth >= Dimens.wideBreak / 2)
        Column(Modifier.fillMaxSize().padding(horizontal = margin).padding(top = margin)) {
            Box(Modifier.staggered(parts[0])) { PanelTitle(stringResource(R.string.tab_diary)) }
            if (trips.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = Space.s16).staggered(parts[1])) {
                    Text(stringResource(R.string.diary_empty_title), style = VType.title, color = Ink.primary)
                    Spacer(Modifier.height(Space.s12))
                    Text(stringResource(R.string.diary_empty_body), Modifier.widthIn(max = Dimens.readable), style = VType.body, color = Ink.secondary)
                }
            } else {
                LazyColumn(
                    Modifier.staggered(parts[1]),
                    verticalArrangement = Arrangement.spacedBy(Space.s12),
                    contentPadding = PaddingValues(bottom = Space.s40),
                ) {
                    itemsIndexed(trips, key = { _, t -> t.id }) { _, t -> TripCard(t) }
                }
            }
        }
    }
}

private val dateFmt = SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREAN)

@Composable
private fun TripCard(t: TripRecord) {
    val chrome = LocalChrome.current
    val dark = LocalDark.current
    val fill = Ink.fill
    Row(
        Modifier.fillMaxWidth().grouped(Shapes.card).padding(Space.s20),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The route sketch as a thumbnail (64 normalised points; never coordinates), or the score.
        val sketch = t.sketch
        Box(
            Modifier.size(Dimens.diaryThumb).drawBehind { val r = Radius.r16.toPx(); drawRoundRect(fill, cornerRadius = CornerRadius(r, r)) },
            contentAlignment = Alignment.Center,
        ) {
            if (sketch != null && sketch.size >= 4) {
                Canvas(Modifier.fillMaxSize().padding(Space.s16)) {
                    val p = Path()
                    for (i in 0 until sketch.size / 2) {
                        val x = sketch[i * 2] * size.width; val y = sketch[i * 2 + 1] * size.height
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, chrome.tint, style = Stroke(Dimens.ring.toPx() * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(chrome.tint, Dimens.ring.toPx() * 2f, Offset(sketch[0] * size.width, sketch[1] * size.height))
                }
            } else {
                Text("${t.score}", style = VType.title.merge(VType.tabular), color = Color(scoreColor(t.score, dark)))
            }
        }
        Spacer(Modifier.width(Space.s24))
        Column(Modifier.weight(1f)) {
            Text(dateFmt.format(Date(t.startMs)), style = VType.headline, color = Ink.primary)
            Spacer(Modifier.height(Space.s4))
            Text("%.1f km · %d분 · %d점".format(t.distanceKm, t.durationMin, t.score), style = VType.body.merge(VType.tabular), color = Ink.primary)
            Spacer(Modifier.height(Space.s4))
            Text(
                "별 ${t.stars}개 · 풍경 ${t.biomes}곳 · 부드러운 가속 ${t.bursts} · 급제동 ${t.harshBrakes}" +
                    (if (t.climateMoments > 0) " · 에어컨 바람 ${t.climateMoments}번" else "") +
                    (if (t.rainMoments > 0) " · 비 ${t.rainMoments}번" else ""),
                style = VType.callout.merge(VType.tabular), color = Ink.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.s16))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            if (t.demo) Tag(stringResource(R.string.demo_tag))
            Tag(t.sticker.label, accent = false)
        }
    }
}

/** The score's colour as text (positive from 90, caution from 70, critical below), readable on the surface. */
internal fun scoreColor(score: Int, dark: Boolean): Int = when {
    score >= 90 -> Semantic.positive(dark)
    score >= 70 -> Semantic.caution(dark)
    else -> Semantic.critical(dark)
}

// ---------------------------------------------------------------- Wardrobe

private val Accessory.label: String get() = when (this) {
    Accessory.SCARF -> "드라이브 목도리"; Accessory.GOGGLES -> "동글 고글"; Accessory.BEANIE -> "니트 비니"
    Accessory.RAINCOAT -> "노란 우비"; Accessory.CABLE_LASSO -> "충전 케이블 올가미"
    else -> name
}

@Composable
fun WardrobePanel(rt: VroomiRuntime, careKm: Float) {
    val settings by rt.settingsStore.settings.collectAsStateWithLifecycle()
    WardrobeContent(Bond.level(careKm), settings.wardrobe) { a, worn ->
        rt.settingsStore.update { s -> s.copy(wardrobe = if (worn) s.wardrobe - a else s.wardrobe + a) }
    }
}

/** The wardrobe's grid (stateless, for the app and the snapshots). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WardrobeContent(level: Int, wardrobe: Set<Accessory>, onToggle: (Accessory, Boolean) -> Unit) {
    val chrome = LocalChrome.current
    Page(stringResource(R.string.tab_wardrobe), subtitle = stringResource(R.string.wardrobe_hint)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s16), verticalArrangement = Arrangement.spacedBy(Space.s16)) {
            Accessory.entries.filter { it.wardrobe }.forEach { a ->
                val unlocked = a.unlockLevel <= level
                val worn = a in wardrobe
                Column(
                    Modifier
                        .width(Dimens.wardrobeCard)
                        .grouped(Shapes.card)
                        .drawBehind { if (worn) drawOutline(Shapes.card.createOutline(size, layoutDirection, this), chrome.tint, style = Stroke(Dimens.ring.toPx())) }
                        .pressFeedback(Shapes.card, enabled = unlocked)
                        .clickable(enabled = unlocked, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox) { onToggle(a, worn) }
                        .heightIn(min = Dimens.listRow + Space.s32)
                        .padding(Space.s24),
                ) {
                    Text(a.label, style = VType.headline, color = if (unlocked) Ink.primary else Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Space.s12))
                    when {
                        !unlocked -> Text(stringResource(R.string.locked_at, a.unlockLevel), style = VType.callout, color = Ink.secondary)
                        worn -> Tag(stringResource(R.string.wardrobe_worn), check = true)
                        else -> Text(stringResource(R.string.wardrobe_wear), style = VType.callout, color = Ink.secondary)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings (parked only)

@Composable
fun SettingsPanel(rt: VroomiRuntime) {
    val s by rt.settingsStore.settings.collectAsStateWithLifecycle()
    SettingsContent(s, onUpdate = { f -> rt.settingsStore.update(f) }, onReset = { rt.diary.clear() }) { DebugPanel(rt) }
}

/**
 * The settings page (stateless, for the app and the snapshots): grouped inset sections with headers and short
 * footnotes, rows of title and subtitle with full-size switches, and the segmented choices. Two columns when the
 * pane is wide enough, otherwise one readable column.
 */
@Composable
internal fun SettingsContent(
    s: Settings,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onReset: () -> Unit,
    debug: @Composable () -> Unit = {},
) {
    var taps by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    val critical = Color(Semantic.critical(LocalDark.current))
    Page(stringResource(R.string.tab_settings)) { pane ->
        val voice: @Composable ColumnScope.() -> Unit = {
            Section(stringResource(R.string.settings_sec_voice), stringResource(R.string.settings_voice_foot)) {
                SwitchRow(stringResource(R.string.settings_voice), stringResource(R.string.settings_voice_sub), !s.muted) { on -> onUpdate { it.copy(muted = !on) } }
                RowDivider()
                SwitchRow(stringResource(R.string.settings_voice_rare), stringResource(R.string.settings_voice_rare_sub), s.voiceRare) { on -> onUpdate { it.copy(voiceRare = on) } }
                RowDivider()
                SwitchRow(stringResource(R.string.settings_quiet_theme_voice), stringResource(R.string.settings_quiet_theme_voice_sub), s.quietThemeVoice) { on -> onUpdate { it.copy(quietThemeVoice = on) } }
            }
        }
        val display: @Composable ColumnScope.() -> Unit = {
            Section(stringResource(R.string.settings_sec_display), null) {
                SwitchRow(stringResource(R.string.settings_calm_title), stringResource(R.string.settings_calm_sub), s.calm) { on -> onUpdate { it.copy(calm = on) } }
                RowDivider()
                ListRow(stringResource(R.string.settings_size), stringResource(R.string.settings_size_sub)) {
                    SegmentedControl(CharacterSize.entries.map { it to it.label }, s.size) { v -> onUpdate { it.copy(size = v) } }
                }
            }
        }
        val vehicle: @Composable ColumnScope.() -> Unit = {
            Section(stringResource(R.string.settings_sec_vehicle), stringResource(R.string.settings_vehicle_foot)) {
                ListRow(stringResource(R.string.settings_source), stringResource(R.string.settings_source_sub))
                Box(Modifier.padding(start = Space.s24, end = Space.s24, bottom = Space.s24)) {
                    SegmentedControl(
                        listOf(SourceMode.AUTO to "자동", SourceMode.PLEOS to "실제 차량", SourceMode.SIMULATOR to "시뮬레이터"),
                        s.sourceMode, Modifier.fillMaxWidth(), equal = true,
                    ) { v -> onUpdate { it.copy(sourceMode = v) } }
                }
            }
        }
        val data: @Composable ColumnScope.() -> Unit = {
            Section(stringResource(R.string.settings_sec_data), stringResource(R.string.settings_data_foot)) {
                ListRow(
                    if (confirmReset) stringResource(R.string.settings_reset_confirm) else stringResource(R.string.settings_reset),
                    if (confirmReset) null else stringResource(R.string.settings_reset_sub),
                    titleColor = critical,
                    onClick = { if (confirmReset) { onReset(); confirmReset = false } else confirmReset = true },
                )
            }
            Spacer(Modifier.height(Space.s16))
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                Modifier.padding(start = Space.s24).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { taps++ }.padding(vertical = Space.s12),
                style = VType.callout, color = Ink.secondary,
            )
            if (taps >= 5) debug()
        }
        if (pane >= Dimens.twoColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s32)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s32)) { voice(); display() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s32)) { vehicle(); data() }
            }
        } else {
            Column(Modifier.widthIn(max = Dimens.readable), verticalArrangement = Arrangement.spacedBy(Space.s32)) { voice(); display(); vehicle(); data() }
        }
    }
}

/** A labelled switch row (the developer panel). */
@Composable
internal fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(label, null, checked, onChange)
}

/** A full-width secondary action (the developer panel). */
@Composable
internal fun BigButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val critical = Color(Semantic.critical(LocalDark.current))
    Box(
        Modifier
            .heightIn(min = Dimens.touchMoving)
            .grouped(Shapes.card)
            .pressFeedback(Shapes.card)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s24, vertical = Space.s16),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = VType.headline, color = if (danger) critical else Ink.primary)
    }
}
