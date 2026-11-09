package io.vroomi.character.render.pixel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.vroomi.character.CharacterTarget
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.pixel.PixelMechanics
import io.vroomi.character.render.SceneOptions
import io.vroomi.character.render.calibre.DEFAULT_NOW_MS
import io.vroomi.character.render.theme.ThemeMiniature
import io.vroomi.character.render.theme.ThemeRuntime
import io.vroomi.character.render.theme.ThemeScene
import io.vroomi.character.render.theme.ThemeSceneState
import io.vroomi.character.render.theme.ThemeSceneStill
import io.vroomi.character.render.theme.rememberThemeSceneState

/** Pixel World's scene state (the shared theme-kit state). */
typealias PixelSceneState = ThemeSceneState

/** Pixel World's runtime: the theme-kit runtime with its mechanics and painter. */
internal typealias PixelRuntime = ThemeRuntime<PixelMechanics>

internal fun newPixelRuntime(): PixelRuntime = ThemeRuntime(SceneTheme.PIXEL, PixelMechanics(), PixelPainter())

@Composable
fun rememberPixelSceneState(): PixelSceneState = rememberThemeSceneState(SceneTheme.PIXEL)

/** Pixel World driven by the car at 60 fps. [interactive] (parked only): tapping the hero makes it hop and wave. */
@Composable
fun PixelScene(target: CharacterTarget, options: SceneOptions, state: PixelSceneState, modifier: Modifier = Modifier, interactive: Boolean = false) =
    ThemeScene(target, options, state, modifier, interactive)

/** A still frame after simulating [seconds] of [target]. */
@Composable
fun PixelSceneStill(target: CharacterTarget, options: SceneOptions, modifier: Modifier = Modifier, seconds: Float = 3f, nowMs: Long = DEFAULT_NOW_MS) =
    ThemeSceneStill(SceneTheme.PIXEL, target, options, modifier, seconds, nowMs)

/** The picker card. */
@Composable
fun PixelMiniature(modifier: Modifier = Modifier, nowMs: Long? = null, live: Boolean = false) = ThemeMiniature(SceneTheme.PIXEL, modifier, nowMs, live)
