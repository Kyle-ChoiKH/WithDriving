package io.vroomi.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vroomi.app.R
import io.vroomi.app.diary.TripRecord
import io.vroomi.app.progress.Grade
import io.vroomi.app.progress.SafetyGrade
import io.vroomi.app.progress.ThemeAccess
import io.vroomi.app.runtime.VroomiRuntime
import io.vroomi.app.ui.design.ColorMath
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.Layout
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.LocalDark
import io.vroomi.app.ui.design.Neutral
import io.vroomi.app.ui.design.ProgressBar
import io.vroomi.app.ui.design.RowDivider
import io.vroomi.app.ui.design.Section
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.Tag
import io.vroomi.app.ui.design.VIcons
import io.vroomi.app.ui.design.VType
import io.vroomi.app.ui.design.grouped
import io.vroomi.app.ui.motion.pressFeedback
import io.vroomi.app.ui.passage.HeroStills
import io.vroomi.character.Accessory
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.StillSpec
import io.vroomi.character.render.rememberSceneStillRenderer
import io.vroomi.character.theme.ThemeModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------- medals

/** The medal colours of the grades (spec: drawn as outlines only, never as a large fill). */
internal val Grade.medalArgb: Int get() = when (this) {
    Grade.BRONZE -> 0xFFB07A4A
    Grade.SILVER -> 0xFFA9B1BA
    Grade.GOLD -> 0xFFD4A53C
    Grade.PLATINUM -> 0xFF8FB7C9
    Grade.DIAMOND -> 0xFF7FD3F0
}.toInt()

/** The medal colour as a mark on the page: kept at 3:1 against the surface (a pale ring darkens a little on a white page). */
@Composable
internal fun medalColor(grade: Grade): Color = Color(ColorMath.ensureContrast(grade.medalArgb, Neutral.grouped(LocalDark.current), 3f))

/**
 * A grade's medal, outlines only: two ribbon strokes meeting a ring in the grade's colour, and (when [number]) a thin
 * inner ring with the grade's number. [dim]: a grade not reached yet.
 */
@Composable
internal fun Medal(grade: Grade, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, number: Boolean = true) {
    val base = medalColor(grade)
    val c = if (dim) base.copy(alpha = 0.45f) else base
    Box(
        modifier.size(size).drawBehind {
            val s = this.size.minDimension
            val w = max(1.5.dp.toPx(), s * 0.06f)
            val cx = this.size.width / 2f
            val cy = s * 0.60f
            val r = s * 0.30f
            drawLine(c, Offset(cx - s * 0.21f, s * 0.08f), Offset(cx - r * 0.35f, cy - r * 0.94f), w, StrokeCap.Round)
            drawLine(c, Offset(cx + s * 0.21f, s * 0.08f), Offset(cx + r * 0.35f, cy - r * 0.94f), w, StrokeCap.Round)
            drawCircle(c, r, Offset(cx, cy), style = Stroke(w))
            if (number) drawCircle(c.copy(alpha = c.alpha * 0.55f), r * 0.76f, Offset(cx, cy), style = Stroke(w * 0.55f))
        },
        contentAlignment = BiasAlignment(0f, 0.2f),
    ) {
        if (number) Text("${grade.level}", style = VType.headline.copy(fontSize = (size.value * 0.26f).sp, lineHeight = (size.value * 0.30f).sp), color = c)
    }
}

// ---------------------------------------------------------------- the grade from the diary

/** What the home badge and the grade page show, from the diary and the grade shown. */
internal class GradeStatus(val grade: Grade, val score: Int?, val safeKm: Float, val kmToNext: Float?, val stars: Int) {
    /** 0..1 towards the next grade: the lesser of its distance and its score condition (1 at diamond). */
    val progress: Float get() {
        val next = grade.next() ?: return 1f
        val km = (safeKm / next.needKm).coerceIn(0f, 1f)
        val sc = ((score ?: 0) / next.needScore.toFloat()).coerceIn(0f, 1f)
        return min(km, sc)
    }

    /** The badge's line: what the next grade still needs ("다음 등급까지 12.3 km · 85점 필요"), or the safe distance at diamond. */
    @Composable
    fun badgeDetail(): String {
        val next = grade.next() ?: return stringResource(R.string.grade_safe_km, safeKm)
        val km = kmToNext ?: (next.needKm - safeKm).coerceAtLeast(0f)
        val scoreOk = score != null && score >= next.needScore
        return when {
            km > 0f && !scoreOk -> stringResource(R.string.grade_badge_km_score, km, next.needScore)
            km > 0f -> stringResource(R.string.next_level, km)
            else -> stringResource(R.string.grade_badge_score, next.needScore)
        }
    }

    companion object {
        fun of(trips: List<TripRecord>, grade: Grade): GradeStatus = GradeStatus(
            grade, SafetyGrade.recentScore(trips), SafetyGrade.safeKm(trips), SafetyGrade.kmToNext(trips, grade.level), SafetyGrade.totalStars(trips),
        )
    }
}

/** The step the grade page previews: the step the next grade opens, or the top step at diamond. */
internal fun gradePreviewModel(grade: Grade): Int = ThemeAccess.maxModel(grade.next() ?: grade)

/**
 * "%1$s이(가)" with the subject particle [word] takes: 플래티넘이, 골드가 (a final consonant takes 이). The string
 * resource keeps the neutral "이(가)" form.
 */
internal fun withSubjectParticle(text: String, word: String): String {
    val last = word.lastOrNull() ?: return text
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return text.replace(word + "이(가)", word + if (batchim) "이" else "가")
}

/** The kart's outfits by name. */
internal val Accessory.label: String get() = when (this) {
    Accessory.SCARF -> "드라이브 목도리"; Accessory.GOGGLES -> "동글 고글"; Accessory.BEANIE -> "니트 비니"
    Accessory.RAINCOAT -> "노란 우비"; Accessory.CABLE_LASSO -> "충전 케이블 올가미"
    else -> name
}

/** The outfit a grade opens (one per grade). */
private fun outfitOf(grade: Grade): Accessory? = Accessory.entries.firstOrNull { it.wardrobe && it.unlockLevel == grade.level }

// ---------------------------------------------------------------- the grade tab

/** The grade tab (parked only): the live grade from the diary, a still of the applied theme's next step. */
@Composable
fun GradePanel(rt: VroomiRuntime) {
    val settings by rt.settingsStore.settings.collectAsStateWithLifecycle()
    val trips by rt.diary.trips.collectAsStateWithLifecycle()
    val grade = VroomiRuntime.shownGrade(trips, settings)
    val theme = ThemeRegistry.resolve(settings.sceneTheme)
    val renderer = rememberSceneStillRenderer()
    val color = ThemeAccess.effectiveColor(grade, settings.colors[theme])
    val decor = ThemeAccess.effectiveDecor(grade, settings.decor[theme])
    val spec = StillSpec(skin = settings.skin, finish = settings.calibreFinish, powertrain = settings.enginePowertrain, model = gradePreviewModel(grade), color = color, decor = decor)
    // Drawn once off the main thread by the theme's own painter (a still, so nothing animates on this page).
    val still by produceState<ImageBitmap?>(null, theme, spec) {
        value = runCatching { withContext(Dispatchers.Default) { renderer.render(theme, HeroStills.W, HeroStills.H, spec) } }.getOrNull()
    }
    val chrome = LocalChrome.current
    GradeContent(
        grade, SafetyGrade.recentScore(trips), SafetyGrade.safeKm(trips), SafetyGrade.kmToNext(trips, grade.level), theme,
        nextPreview = { m ->
            Box(m.drawBehind { drawRect(chrome.backdrop) }) {
                still?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = if (theme == SceneTheme.PIXEL) FilterQuality.None else FilterQuality.Low) }
            }
        },
        worn = settings.wardrobe,
        chosen = ThemeAccess.effectiveModel(grade, settings.models[theme]),
        color = color,
        decor = decor,
        onDecor = { i ->
            val open = ThemeAccess.openDecor(SafetyGrade.current(trips, settings.gradeReached))
            if (i < open) rt.settingsStore.update { s -> s.copy(decor = s.decor + (theme to ((s.decor[theme] ?: 0) xor (1 shl i)))) }
        },
        // A colourway picked here is saved for this theme (its own colours are the unset state).
        onColor = { c ->
            val top = ThemeAccess.maxModel(SafetyGrade.current(trips, settings.gradeReached))
            if (c <= top) rt.settingsStore.update { s -> s.copy(colors = if (c == 0) s.colors - theme else s.colors + (theme to c)) }
        },
        // A version picked here is saved for this theme (the best open one means "follow the grade"), as in the theme tab.
        onPick = { m ->
            val top = ThemeAccess.maxModel(SafetyGrade.current(trips, settings.gradeReached))
            if (m <= top) rt.settingsStore.update { s -> s.copy(models = if (m >= top) s.models - theme else s.models + (theme to m)) }
        },
    ) { a, worn -> rt.settingsStore.update { s -> s.copy(wardrobe = if (worn) s.wardrobe - a else s.wardrobe + a) } }
}

/** From this pane width the grade page sets its status and the next upgrade side by side. */
private val GradeTwoColumns = 880.dp
/** Below this width a ladder row puts the step and the outfit under the grade. */
private val LadderCompact = 640.dp

/**
 * The grade page (stateless, for the app and the snapshots), spec 4.3: the grade's medal, the recent safe score and
 * the safe distance; the next grade's two conditions as progress bars and the score rule; a still of [theme]'s next
 * step ([nextPreview], 8:3) with what it becomes; the ladder of the five grades with the version of [theme] each
 * opens: an open version is picked by its row ([onPick]; [chosen] carries the check). The kart's outfits and its
 * wardrobe show only for a theme with a friend on screen. Two columns on a wide pane, one otherwise.
 */
@Composable
internal fun GradeContent(
    grade: Grade,
    score: Int?,
    safeKm: Float,
    kmToNext: Float?,
    theme: SceneTheme,
    nextPreview: @Composable (Modifier) -> Unit,
    worn: Set<Accessory>,
    chosen: Int = ThemeAccess.maxModel(grade),
    onPick: (Int) -> Unit = {},
    color: Int = 0,
    onColor: (Int) -> Unit = {},
    decor: Int = 0,
    onDecor: (Int) -> Unit = {},
    onToggle: (Accessory, Boolean) -> Unit,
) {
    Page(stringResource(R.string.tab_grade)) { pane ->
        if (pane >= GradeTwoColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s32)) {
                Box(Modifier.weight(0.54f)) { StatusSection(grade, score, safeKm, kmToNext) }
                Box(Modifier.weight(0.46f)) { PreviewSection(grade, theme, nextPreview) }
            }
        } else {
            Column(Modifier.widthIn(max = Dimens.readable)) {
                StatusSection(grade, score, safeKm, kmToNext)
                Spacer(Modifier.height(Space.s32))
                PreviewSection(grade, theme, nextPreview)
            }
        }
        Spacer(Modifier.height(Space.s32))
        LadderSection(grade, theme, compact = pane < LadderCompact, chosen = chosen, onPick = onPick)
        // What the grade opens to dress the theme with: its colourways, or (for the theme with a friend) the wardrobe.
        if (io.vroomi.character.theme.ThemeColorways.has(theme)) {
            Spacer(Modifier.height(Space.s32))
            ColorSection(grade, theme, color, onColor)
        }
        if (io.vroomi.character.theme.ThemeDecor.has(theme)) {
            Spacer(Modifier.height(Space.s32))
            DecorSection(grade, theme, decor, onDecor)
        }
        // Outfits are the friend's: only the theme with a friend on screen has a wardrobe.
        if (theme.hasCharacter) {
            Spacer(Modifier.height(Space.s32))
            WardrobeSection(grade, worn, onToggle)
        }
    }
}

/** A section header over a grouped card (the caption role, aligned with the card's text). */
@Composable
private fun Header(text: String) {
    Text(text, Modifier.padding(start = Space.s24, bottom = Space.s8), style = VType.caption, color = Ink.secondary, maxLines = 1)
}

@Composable
private fun StatusSection(grade: Grade, score: Int?, safeKm: Float, kmToNext: Float?) {
    val next = grade.next()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Below this width the score leaves the medal's row for one of its own, so the grade's name is never cut.
        val sideBySide = maxWidth >= 480.dp
        Column(Modifier.fillMaxWidth()) {
            Header(stringResource(R.string.grade_current))
            Column(Modifier.fillMaxWidth().grouped(Shapes.card).padding(Space.s24)) {
                val scoreText = score?.toString() ?: "—"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Medal(grade, 64.dp)
                    Spacer(Modifier.width(Space.s20))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.grade_badge, grade.label), style = VType.title, color = Ink.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(Space.s4))
                        Text(stringResource(R.string.grade_safe_km, safeKm), style = VType.callout.merge(VType.tabular), color = Ink.secondary, maxLines = 1)
                    }
                    if (sideBySide) {
                        Spacer(Modifier.width(Space.s16))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.grade_score), style = VType.caption, color = Ink.secondary, maxLines = 1)
                            Spacer(Modifier.height(Space.s4))
                            Text(scoreText, style = VType.number, color = Ink.primary, maxLines = 1)
                        }
                    }
                }
                if (!sideBySide) {
                    Spacer(Modifier.height(Space.s16))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.grade_score), Modifier.weight(1f), style = VType.body, color = Ink.secondary, maxLines = 1)
                        Text(scoreText, style = VType.number, color = Ink.primary, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(Space.s20))
                RowDivider(inset = 0.dp)
                Spacer(Modifier.height(Space.s20))
                if (next == null) {
                    Text(stringResource(R.string.grade_top), style = VType.headline, color = Ink.primary)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Medal(next, 28.dp, number = false)
                        Spacer(Modifier.width(Space.s12))
                        Text(stringResource(R.string.grade_next, next.label), style = VType.headline, color = Ink.primary, maxLines = 1)
                    }
                    Spacer(Modifier.height(Space.s16))
                    val kmMet = safeKm >= next.needKm
                    val left = kmToNext ?: (next.needKm - safeKm).coerceAtLeast(0f)
                    Condition(
                        stringResource(R.string.grade_need_km, next.needKm),
                        if (kmMet) stringResource(R.string.grade_met) else stringResource(R.string.grade_km_left, left),
                        safeKm / next.needKm, kmMet,
                    )
                    Spacer(Modifier.height(Space.s16))
                    val scoreMet = score != null && score >= next.needScore
                    Condition(
                        stringResource(R.string.grade_need_score, next.needScore),
                        if (scoreMet) stringResource(R.string.grade_met) else (score?.toString() ?: "—"),
                        (score ?: 0) / next.needScore.toFloat(), scoreMet,
                    )
                }
            }
            Text(stringResource(R.string.grade_rule), Modifier.padding(start = Space.s24, end = Space.s24, top = Space.s8), style = VType.callout, color = Ink.secondary)
        }
    }
}

/** One condition of the next grade: what it needs, where it stands (the accent and a check once met) and a bar. */
@Composable
private fun Condition(label: String, value: String, fraction: Float, met: Boolean) {
    val chrome = LocalChrome.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = VType.body, color = Ink.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(Space.s12))
            if (met) {
                Image(VIcons.Check, null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(chrome.tint))
                Spacer(Modifier.width(Space.s4))
            }
            Text(
                value, style = VType.callout.merge(VType.tabular).copy(fontWeight = FontWeight.SemiBold),
                color = if (met) chrome.tint else Ink.secondary, maxLines = 1,
            )
        }
        Spacer(Modifier.height(Space.s8))
        ProgressBar({ fraction.coerceIn(0f, 1f) })
    }
}

@Composable
private fun PreviewSection(grade: Grade, theme: SceneTheme, nextPreview: @Composable (Modifier) -> Unit) {
    val next = grade.next()
    val model = gradePreviewModel(grade)
    Column(Modifier.fillMaxWidth()) {
        Header(stringResource(if (next != null) R.string.grade_preview else R.string.grade_all_open))
        Column(Modifier.fillMaxWidth().grouped(Shapes.card).padding(Space.s8)) {
            nextPreview(Modifier.fillMaxWidth().aspectRatio(Layout.CARD_ASPECT).clip(Shapes.r16))
            Row(Modifier.padding(start = Space.s16, end = Space.s16, top = Space.s16, bottom = Space.s12), verticalAlignment = Alignment.CenterVertically) {
                Tag(ThemeModels.tag(model), accent = false)
                Spacer(Modifier.width(Space.s12))
                val name = ThemeModels.title(theme, model)
                val caption = if (next == null) name else withSubjectParticle(stringResource(R.string.grade_next_upgrade, next.label, name), next.label)
                Text(caption, style = VType.headline, color = Ink.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LadderSection(grade: Grade, theme: SceneTheme, compact: Boolean, chosen: Int, onPick: (Int) -> Unit) {
    Section(stringResource(R.string.grade_ladder), stringResource(if (theme.hasCharacter) R.string.grade_ladder_foot else R.string.grade_ladder_foot_plain)) {
        Grade.entries.forEachIndexed { i, g ->
            if (i > 0) RowDivider()
            val m = ThemeAccess.maxModel(g)
            LadderRow(g, theme, current = g == grade, open = g.level <= grade.level, compact = compact, picked = m == chosen) { onPick(m) }
        }
    }
}

/**
 * One grade of the ladder: its medal and name, the step of [theme] it opens ("Lv.4" and the step's name) and the
 * kart's outfit (only for a theme with a friend). The grade shown sits on a quiet plate; grades not reached yet are
 * dimmed. An open row is a choice: pressing it puts that version of the theme on ([picked] shows the check).
 */
@Composable
private fun LadderRow(g: Grade, theme: SceneTheme, current: Boolean, open: Boolean, compact: Boolean, picked: Boolean, onClick: () -> Unit) {
    val plate = Ink.fill
    val chrome = LocalChrome.current
    val m = ThemeAccess.maxModel(g)
    val ink = if (open) Ink.primary else Ink.secondary
    val outfit = if (theme.hasCharacter) outfitOf(g)?.label.orEmpty() else ""
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                if (current) {
                    val ix = 8.dp.toPx(); val iy = 6.dp.toPx(); val r = Radius16.toPx()
                    drawRoundRect(plate, Offset(ix, iy), Size(size.width - 2 * ix, size.height - 2 * iy), CornerRadius(r, r))
                }
            }
            .pressFeedback(Shapes.card, enabled = open, large = true)
            .clickable(enabled = open, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.s24, vertical = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Medal(g, 36.dp, dim = !open, number = false)
        Spacer(Modifier.width(Space.s16))
        if (compact) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.label, style = VType.headline, color = ink, maxLines = 1)
                    Spacer(Modifier.width(Space.s12))
                    Text(outfit, style = VType.callout, color = Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${ThemeModels.tag(m)} ${ThemeModels.title(theme, m)}", style = VType.callout, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(g.label, Modifier.width(132.dp), style = VType.headline, color = ink, maxLines = 1)
            Spacer(Modifier.width(Space.s12))
            Tag(ThemeModels.tag(m), accent = picked)
            Spacer(Modifier.width(Space.s12))
            Text(ThemeModels.title(theme, m), Modifier.weight(1f), style = VType.body, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(Space.s16))
            Text(outfit, style = VType.callout, color = Ink.secondary, maxLines = 1)
        }
        // The version in use.
        Spacer(Modifier.width(Space.s12))
        Box(Modifier.size(Dimens.icon)) { if (picked) Image(VIcons.Check, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(chrome.tint)) }
    }
}

private val Radius16 = 16.dp

/**
 * The theme's colourways, laid out like the kart's wardrobe: one card each with its swatch, the grade that opens a
 * locked one, and a ring round the one in use. Pressing an open card puts that colourway on the theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSection(grade: Grade, theme: SceneTheme, chosen: Int, onColor: (Int) -> Unit) {
    val chrome = LocalChrome.current
    val top = ThemeAccess.maxModel(grade)
    val base = io.vroomi.app.ui.design.Ambience.of(theme).tint
    Column(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.grade_colors))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s16), verticalArrangement = Arrangement.spacedBy(Space.s16)) {
            io.vroomi.character.theme.ThemeColorways.of(theme).forEachIndexed { i, c ->
                val unlocked = i <= top
                val worn = i == chosen
                Column(
                    Modifier
                        .width(Dimens.wardrobeCard)
                        .grouped(Shapes.card)
                        .drawBehind { if (worn) drawOutline(Shapes.card.createOutline(size, layoutDirection, this), chrome.tint, style = Stroke(Dimens.ring.toPx())) }
                        .pressFeedback(Shapes.card, enabled = unlocked)
                        .clickable(enabled = unlocked, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onColor(i) }
                        .heightIn(min = Dimens.listRow + Space.s32)
                        .padding(Space.s24),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Swatch(androidx.compose.ui.graphics.Color(io.vroomi.character.theme.ThemeColorways.apply(theme, i, base)), 28.dp, dim = !unlocked)
                        Spacer(Modifier.width(Space.s12))
                        Text(c.name, style = VType.headline, color = if (unlocked) Ink.primary else Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(Space.s12))
                    when {
                        !unlocked -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Medal(Grade.of(i + 1), 20.dp, number = false)
                            Spacer(Modifier.width(Space.s8))
                            Text(stringResource(R.string.locked_grade, Grade.of(i + 1).label), style = VType.callout, color = Ink.secondary, maxLines = 1)
                        }
                        worn -> Tag(stringResource(R.string.color_worn), check = true)
                        else -> Text(stringResource(R.string.color_wear), style = VType.callout, color = Ink.secondary)
                    }
                }
            }
        }
        Text(stringResource(R.string.grade_colors_foot), Modifier.padding(start = Space.s24, end = Space.s24, top = Space.s8), style = VType.callout, color = Ink.secondary)
    }
}

/** The theme's props, laid out like the kart's wardrobe: a card each, the grade that opens a locked one, a ring round those in use. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecorSection(grade: Grade, theme: SceneTheme, mask: Int, onDecor: (Int) -> Unit) {
    val chrome = LocalChrome.current
    val open = ThemeAccess.openDecor(grade)
    Column(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.grade_decor))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s16), verticalArrangement = Arrangement.spacedBy(Space.s16)) {
            io.vroomi.character.theme.ThemeDecor.names(theme).forEachIndexed { i, name ->
                val unlocked = i < open
                val worn = mask and (1 shl i) != 0
                val unlock = Grade.of(io.vroomi.character.theme.ThemeDecor.unlockLevel(i))
                Column(
                    Modifier
                        .width(Dimens.wardrobeCard)
                        .grouped(Shapes.card)
                        .drawBehind { if (worn) drawOutline(Shapes.card.createOutline(size, layoutDirection, this), chrome.tint, style = Stroke(Dimens.ring.toPx())) }
                        .pressFeedback(Shapes.card, enabled = unlocked)
                        .clickable(enabled = unlocked, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox) { onDecor(i) }
                        .heightIn(min = Dimens.listRow + Space.s32)
                        .padding(Space.s24),
                ) {
                    Text(name, style = VType.headline, color = if (unlocked) Ink.primary else Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Space.s12))
                    when {
                        !unlocked -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Medal(unlock, 20.dp, number = false)
                            Spacer(Modifier.width(Space.s8))
                            Text(stringResource(R.string.locked_grade, unlock.label), style = VType.callout, color = Ink.secondary, maxLines = 1)
                        }
                        worn -> Tag(stringResource(R.string.decor_on), check = true)
                        else -> Text(stringResource(R.string.decor_off), style = VType.callout, color = Ink.secondary)
                    }
                }
            }
        }
        Text(stringResource(R.string.grade_decor_foot), Modifier.padding(start = Space.s24, end = Space.s24, top = Space.s8), style = VType.callout, color = Ink.secondary)
    }
}

/** The kart's wardrobe (moved here from its own tab): one card per outfit, the grade that opens a locked one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WardrobeSection(grade: Grade, wardrobe: Set<Accessory>, onToggle: (Accessory, Boolean) -> Unit) {
    val chrome = LocalChrome.current
    val open = SafetyGrade.outfits(grade)
    Column(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.grade_outfits))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s16), verticalArrangement = Arrangement.spacedBy(Space.s16)) {
            Accessory.entries.filter { it.wardrobe }.forEach { a ->
                val unlocked = a in open
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
                        !unlocked -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Medal(Grade.of(a.unlockLevel), 20.dp, number = false)
                            Spacer(Modifier.width(Space.s8))
                            Text(stringResource(R.string.locked_grade, Grade.of(a.unlockLevel).label), style = VType.callout, color = Ink.secondary, maxLines = 1)
                        }
                        worn -> Tag(stringResource(R.string.wardrobe_worn), check = true)
                        else -> Text(stringResource(R.string.wardrobe_wear), style = VType.callout, color = Ink.secondary)
                    }
                }
            }
        }
        Text(stringResource(R.string.wardrobe_hint), Modifier.padding(start = Space.s24, end = Space.s24, top = Space.s8), style = VType.callout, color = Ink.secondary)
    }
}
