package io.vroomi.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.vroomi.app.R
import io.vroomi.app.progress.Grade
import io.vroomi.app.progress.ThemeAccess
import io.vroomi.app.ui.design.Dimens
import io.vroomi.app.ui.design.LocalChrome
import io.vroomi.app.ui.design.MaterialToken
import io.vroomi.app.ui.design.Shapes
import io.vroomi.app.ui.design.Space
import io.vroomi.app.ui.design.VType
import io.vroomi.app.ui.design.OptionChip
import io.vroomi.app.ui.design.grouped
import io.vroomi.app.ui.motion.pressFeedback
import io.vroomi.app.ui.passage.HeroStills
import io.vroomi.character.CharacterSkin
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.render.StillSpec
import io.vroomi.character.render.VroomiPortrait
import io.vroomi.character.render.calibre.CalibreMiniature
import io.vroomi.character.render.engine.EngineMiniature
import io.vroomi.character.render.rememberSceneStillRenderer
import io.vroomi.character.theme.ThemeModels

/**
 * A still gallery focused on [theme] with its option chips (previews and snapshot tests; the live app uses
 * [Gallery] with the live scene in the applied card).
 */
@Composable
internal fun FriendsPicker(
    selected: CharacterSkin,
    theme: SceneTheme = SceneTheme.KART,
    finish: CalibreFinish = CalibreFinish.AUTO,
    @Suppress("UNUSED_PARAMETER") live: Boolean = false,
    powertrain: EnginePowertrain = EnginePowertrain.AUTO,
    themeSound: Boolean = false,
    onThemeSound: (Boolean) -> Unit = {},
    onTheme: (SceneTheme) -> Unit = {},
    onFinish: (CalibreFinish) -> Unit = {},
    onPowertrain: (EnginePowertrain) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") initialScroll: Int = 0,
    grade: Grade = Grade.BRONZE,
    models: Map<SceneTheme, Int> = emptyMap(),
    onSelect: (CharacterSkin) -> Unit,
) {
    val current = ThemeRegistry.resolve(theme)
    val renderer = rememberSceneStillRenderer()
    val scope = rememberCoroutineScope()
    val stills = remember { HeroStills(renderer, scope, synchronous = true) }
    val state = remember(current) { GalleryState(current) }
    val spec = StillSpec(skin = selected, finish = finish, powertrain = powertrain)
    val modelOf: (SceneTheme) -> Int = { t -> ThemeAccess.effectiveModel(grade, models[t]) }
    val statics = remember(current, spec, grade, models) {
        val i = ThemeRegistry.indexOf(current)
        (i - 2..i + 2).filter { it in ThemeRegistry.all.indices }.associate { idx ->
            val t = ThemeRegistry.all[idx].theme
            t to renderer.render(t, HeroStills.W, HeroStills.H, spec.copy(model = modelOf(t)))
        }
    }
    val ctx = PickerContext(selected, finish, powertrain, io.vroomi.character.render.calibre.DEFAULT_NOW_MS, 0L, onFinish, onPowertrain, onSelect, themeSound, onThemeSound,
        grade = grade, modelOf = modelOf)
    // A preview carries the shown theme's own chrome colours.
    val dark = io.vroomi.app.ui.design.LocalDark.current
    val chrome = remember(current, dark) { io.vroomi.app.ui.design.ChromeColors(io.vroomi.app.ui.design.Ambience.of(current), dark) }
    androidx.compose.runtime.CompositionLocalProvider(io.vroomi.app.ui.design.LocalChrome provides chrome) {
        Gallery(state, current, stills, chips = { ThemeOptionChips(current, ctx) {} }, onOpen = onTheme, staticStills = statics,
            stepTag = { t -> ThemeModels.tag(modelOf(t)) })
    }
}

/** The chips of a theme's options (a theme without options has none; the stage shows its note). */
@Composable
internal fun RowScope.ThemeOptionChips(theme: SceneTheme, ctx: PickerContext, onOpen: (ThemeOption) -> Unit) {
    val spec = ThemeRegistry.of(theme)
    spec.options.forEach { o ->
        val label = when (o) {
            ThemeOption.MODEL -> ctx.modelOf(theme).let { m -> stringResource(R.string.chip_model, ThemeModels.tag(m), ThemeModels.title(theme, m)) }
            ThemeOption.COLOR -> stringResource(R.string.chip_color, io.vroomi.character.theme.ThemeColorways.name(theme, ctx.colorOf(theme)))
            ThemeOption.FRIEND -> stringResource(R.string.chip_friend, ctx.skin.label)
            ThemeOption.FINISH -> stringResource(R.string.chip_finish, ctx.finish.label)
            ThemeOption.POWERTRAIN -> stringResource(R.string.chip_powertrain, ctx.powertrain.label)
            ThemeOption.SOUND -> stringResource(if (ctx.themeSound) R.string.chip_sound_on else R.string.chip_sound_off)
        }
        OptionChip(label) { onOpen(o) }
    }
}

/** The content of an option sheet. */
@Composable
internal fun OptionSheet(option: ThemeOption, theme: SceneTheme, ctx: PickerContext) {
    val spec = ThemeRegistry.of(theme)
    Column(Modifier.heightIn(max = Dimens.sheetMax).verticalScroll(rememberScrollState())) {
        when (option) {
            ThemeOption.MODEL -> ModelOptions(theme, ctx)
            ThemeOption.COLOR -> ColorOptions(theme, ctx)
            ThemeOption.FRIEND -> FriendOptions(ctx)
            ThemeOption.FINISH -> CalibreOptions(ctx)
            ThemeOption.POWERTRAIN -> EngineOptions(ctx)
            ThemeOption.SOUND -> ThemeSoundOptions(ctx, spec.note ?: R.string.theme_sound_hint)
        }
    }
}

/** KART, PAPER and PIXEL: which friend rides along. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FriendOptions(ctx: PickerContext) {
    val selected = ctx.skin
    SheetHeader(stringResource(R.string.friends_title), stringResource(R.string.friends_hint))
    Text("${selected.label} · ${selected.intro}", style = VType.headline, color = Ink.primary)
    listOf(false to R.string.friends_cute, true to R.string.friends_mech).forEach { (mech, title) ->
        Spacer(Modifier.height(Space.s24))
        Text(stringResource(title), Modifier.padding(start = Space.s4), style = VType.caption, color = Ink.secondary)
        Spacer(Modifier.height(Space.s12))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s12), verticalArrangement = Arrangement.spacedBy(Space.s12)) {
            CharacterSkin.entries.filter { it.mechanical == mech }.forEach { skin ->
                PickCard(skin == selected, { ctx.onSkin(skin) }) {
                    VroomiPortrait(skin, Modifier.size(Dimens.portrait))
                    Text(skin.label, style = VType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), textAlign = TextAlign.Center, color = Ink.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalibreOptions(ctx: PickerContext) {
    SheetHeader(stringResource(R.string.calibre_finish), stringResource(R.string.calibre_finish_hint))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s12), verticalArrangement = Arrangement.spacedBy(Space.s12)) {
        CalibreFinish.entries.forEach { f ->
            PickCard(f == ctx.finish, { ctx.onFinish(f) }) {
                // Every card is drawn by the real painter (a still frame keeps the car display light).
                CalibreMiniature(f, Modifier.size(Dimens.miniature, Dimens.miniature * 2 / 3).clip(Shapes.r16), nowMs = ctx.nowMs)
                Text(f.label, style = VType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), textAlign = TextAlign.Center, color = Ink.primary)
            }
        }
    }
    Spacer(Modifier.height(Space.s16))
    Text(stringResource(R.string.calibre_tap_flip), style = VType.callout, color = Ink.secondary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EngineOptions(ctx: PickerContext) {
    SheetHeader(stringResource(R.string.engine_powertrain), stringResource(R.string.engine_powertrain_hint))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s12), verticalArrangement = Arrangement.spacedBy(Space.s12)) {
        EnginePowertrain.entries.forEach { p ->
            PickCard(p == ctx.powertrain, { ctx.onPowertrain(p) }) {
                EngineMiniature(p, Modifier.size(Dimens.miniature, Dimens.miniature * 2 / 3).clip(Shapes.r16), nowMs = ctx.nowMs)
                Text(p.label, style = VType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), textAlign = TextAlign.Center, color = Ink.primary)
                Text(p.intro, Modifier.padding(horizontal = Space.s8), style = VType.caption, color = Ink.secondary, textAlign = TextAlign.Center)
            }
        }
    }
    Spacer(Modifier.height(Space.s16))
    Text(stringResource(R.string.engine_tap_view), style = VType.callout, color = Ink.secondary)
}

/** Kit themes with sound: the "테마 소리" switch (off by default) in a section, the theme's note as its footnote. */
@Composable
internal fun ThemeSoundOptions(ctx: PickerContext, @androidx.annotation.StringRes note: Int) {
    SheetHeader(stringResource(R.string.theme_sound), null)
    io.vroomi.app.ui.design.Section(header = null, footnote = stringResource(note)) {
        io.vroomi.app.ui.design.SwitchRow(stringResource(R.string.unmute), stringResource(R.string.theme_sound_hint), ctx.themeSound, ctx.onThemeSound)
    }
}

/** A one-line note for a theme without options. */
@Composable
internal fun ThemeNote(text: String) {
    Text(text, style = VType.callout, color = Ink.secondary)
}

/** A sheet's title and its one-line explanation, with the space below them. */
@Composable
internal fun SheetHeader(title: String, hint: String?) {
    Text(title, style = VType.title, color = Ink.primary)
    if (hint != null) {
        Spacer(Modifier.height(Space.s8))
        Text(hint, style = VType.callout, color = Ink.secondary)
    }
    Spacer(Modifier.height(Space.s24))
}

/** A choice card inside an option sheet: the grouped surface; the chosen one carries a 2 dp accent ring. */
@Composable
internal fun PickCard(on: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val chrome = LocalChrome.current
    Column(
        Modifier
            .width(Dimens.miniature + Space.s32)
            .grouped(Shapes.card)
            .drawBehind { if (on) drawOutline(Shapes.card.createOutline(size, layoutDirection, this), chrome.tint, style = Stroke(Dimens.ring.toPx())) }
            .pressFeedback(Shapes.card)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s8),
    ) { content() }
}
