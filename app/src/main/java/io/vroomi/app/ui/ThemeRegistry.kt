package io.vroomi.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.vroomi.app.R
import io.vroomi.app.progress.Grade
import io.vroomi.app.progress.ThemeAccess
import io.vroomi.app.ui.design.Ambience
import io.vroomi.app.ui.design.ThemeAmbience
import io.vroomi.character.CharacterSkin
import io.vroomi.character.CharacterTarget
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.VroomiScene
import io.vroomi.character.render.calibre.CalibreScene
import io.vroomi.character.render.engine.EngineScene
import io.vroomi.character.render.theme.ThemeScene

/** Gallery groups. */
enum class ThemeCategory(@StringRes val label: Int) {
    CHARACTER(R.string.theme_cat_character),
    MACHINE(R.string.theme_cat_machine),
    NATURE(R.string.theme_cat_nature),
    RECORD(R.string.theme_cat_record),
    PLAY(R.string.theme_cat_play),
}

/** The options a theme offers, each opened from a chip into a sheet. [MODEL] (the upgrade step) comes first on every theme. */
enum class ThemeOption { MODEL, FRIEND, FINISH, POWERTRAIN, SOUND }

/**
 * What an option sheet needs. [stillAt] null = the live app (frame clock). [grade] is the grade shown (it decides
 * which steps the model sheet shows as open); [modelOf] the step a theme shows now; [previewing] whether that step
 * is only a preview (a locked step, never saved); [onModel] picks a step for a theme.
 */
internal class PickerContext(
    val skin: CharacterSkin,
    val finish: CalibreFinish,
    val powertrain: EnginePowertrain,
    val stillAt: Long?,
    val openedAt: Long,
    val onFinish: (CalibreFinish) -> Unit = {},
    val onPowertrain: (EnginePowertrain) -> Unit = {},
    val onSkin: (CharacterSkin) -> Unit = {},
    val themeSound: Boolean = false,
    val onThemeSound: (Boolean) -> Unit = {},
    val grade: Grade = Grade.BRONZE,
    val modelOf: (SceneTheme) -> Int = { ThemeAccess.maxModel(grade) },
    val previewing: (SceneTheme) -> Boolean = { false },
    val onModel: (SceneTheme, Int) -> Unit = { _, _ -> },
) {
    val live: Boolean get() = stillAt == null
    val nowMs: Long get() = stillAt ?: openedAt
}

/**
 * One theme: its card text, its options, its ambience and how its scene is drawn. SceneHost, the gallery and the
 * stage dispatch through [ThemeRegistry] instead of a when-branch per theme.
 */
internal class ThemeSpec(
    val theme: SceneTheme,
    @StringRes val title: Int,
    @StringRes val hint: Int,
    val category: ThemeCategory,
    /** The demo drive that shows the theme (null: none). */
    val showcase: String?,
    /** The theme's own options, opened from chips after the upgrade step; the stage shows [note] beside them. */
    ownOptions: List<ThemeOption> = emptyList(),
    @StringRes val note: Int? = null,
    val scene: @Composable (host: SceneHost, target: CharacterTarget, options: SceneOptions, modifier: Modifier, interactive: Boolean, onFirstFrame: () -> Unit) -> Unit,
    val chip: (host: SceneHost, target: CharacterTarget) -> String?,
) {
    /** Every option in chip order: the upgrade step ([ThemeOption.MODEL]) first on every theme, then the theme's own. */
    val options: List<ThemeOption> = listOf(ThemeOption.MODEL) + ownOptions.filter { it != ThemeOption.MODEL }

    val ambience: ThemeAmbience get() = Ambience.of(theme)
}

internal object ThemeRegistry {
    private val KART = ThemeSpec(
        SceneTheme.KART, R.string.theme_kart, R.string.theme_kart_hint, ThemeCategory.CHARACTER, "highlight",
        ownOptions = listOf(ThemeOption.FRIEND),
        scene = { host, t, o, m, _, first -> VroomiScene(t, o, m, host.kart, onFirstFrame = first) },
        chip = { _, t -> t.statusChip },
    )

    private val CALIBRE = ThemeSpec(
        SceneTheme.CALIBRE, R.string.theme_calibre, R.string.theme_calibre_hint, ThemeCategory.MACHINE, "calibre_showcase",
        ownOptions = listOf(ThemeOption.FINISH),
        scene = { host, t, o, m, i, first -> CalibreScene(t, o, host.finish, m, host.calibre, i, host.frameTime, onFirstFrame = first) },
        chip = { host, _ -> host.calibre.chip },
    )

    private val ENGINE = ThemeSpec(
        SceneTheme.ENGINE, R.string.theme_engine, R.string.theme_engine_hint, ThemeCategory.MACHINE, "engine_showcase",
        ownOptions = listOf(ThemeOption.POWERTRAIN),
        scene = { host, t, o, m, i, first -> EngineScene(t, o, host.powertrain, m, host.engine, host.forcedPowertrain, i, host.frameTime, onFirstFrame = first) },
        chip = { host, _ -> host.engine.chip },
    )

    /** A procedural theme drawn by the theme kit. */
    private fun kit(
        theme: SceneTheme, @StringRes title: Int, @StringRes hint: Int, category: ThemeCategory, showcase: String,
        ownOptions: List<ThemeOption> = emptyList(), @StringRes note: Int? = null,
    ) = ThemeSpec(
        theme, title, hint, category, showcase, ownOptions = ownOptions, note = note,
        scene = { host, t, o, m, i, first -> host.kit?.let { ThemeScene(t, o, it, m, i, host.frameTime, onFirstFrame = first) } },
        chip = { host, _ -> host.kit?.chip },
    )

    private val MUSIC_BOX = kit(SceneTheme.MUSIC_BOX, R.string.theme_music_box, R.string.theme_music_box_hint, ThemeCategory.MACHINE, "music_box_showcase",
        ownOptions = listOf(ThemeOption.SOUND), note = R.string.music_box_note)
    private val TERRARIUM = kit(SceneTheme.TERRARIUM, R.string.theme_terrarium, R.string.theme_terrarium_hint, ThemeCategory.NATURE, "terrarium_showcase", note = R.string.terrarium_note)
    private val TREASURE_MAP = kit(SceneTheme.TREASURE_MAP, R.string.theme_map, R.string.theme_map_hint, ThemeCategory.RECORD, "map_showcase", note = R.string.map_note)
    private val AQUARIUM = kit(SceneTheme.AQUARIUM, R.string.theme_aquarium, R.string.theme_aquarium_hint, ThemeCategory.NATURE, "aquarium_showcase", note = R.string.aquarium_note)
    private val ORBIT = kit(SceneTheme.ORBIT, R.string.theme_orbit, R.string.theme_orbit_hint, ThemeCategory.NATURE, "orbit_showcase", note = R.string.orbit_note)
    private val PLASMA = kit(SceneTheme.PLASMA, R.string.theme_plasma, R.string.theme_plasma_hint, ThemeCategory.MACHINE, "plasma_showcase",
        note = R.string.plasma_note)
    private val HANGAR = kit(SceneTheme.HANGAR, R.string.theme_hangar, R.string.theme_hangar_hint, ThemeCategory.MACHINE, "hangar_showcase",
        note = R.string.hangar_note)
    // Every theme but KART is its own world with its own cast, so the friend picker belongs to KART only.
    private val PAPER = kit(SceneTheme.PAPER, R.string.theme_paper, R.string.theme_paper_hint, ThemeCategory.PLAY, "paper_showcase",
        note = R.string.paper_note)
    private val PIXEL = kit(SceneTheme.PIXEL, R.string.theme_pixel, R.string.theme_pixel_hint, ThemeCategory.PLAY, "pixel_showcase",
        ownOptions = listOf(ThemeOption.SOUND), note = R.string.pixel_note)

    /** Every theme in gallery (and swipe) order. */
    val all: List<ThemeSpec> = listOf(KART, PAPER, CALIBRE, ENGINE, MUSIC_BOX, PLASMA, HANGAR, TERRARIUM, AQUARIUM, ORBIT, TREASURE_MAP, PIXEL)

    fun of(theme: SceneTheme): ThemeSpec = all.firstOrNull { it.theme == theme } ?: KART

    fun has(theme: SceneTheme): Boolean = all.any { it.theme == theme }

    fun indexOf(theme: SceneTheme): Int = all.indexOfFirst { it.theme == theme }.coerceAtLeast(0)

    /** A stored theme that is not built (or not known) falls back to KART. */
    fun resolve(theme: SceneTheme): SceneTheme = if (has(theme)) theme else SceneTheme.KART
}
