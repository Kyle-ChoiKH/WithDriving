package io.vroomi.character.render.theme

import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.musicbox.MusicBoxSim
import io.vroomi.character.render.musicbox.MusicBoxPainter

/** The procedural themes built on the kit: one simulation and one painter each. */
internal object ThemeCatalog {
    fun create(theme: SceneTheme): ThemeRuntime<*>? = when (theme) {
        SceneTheme.MUSIC_BOX -> ThemeRuntime(theme, MusicBoxSim(), MusicBoxPainter())
        SceneTheme.TERRARIUM -> ThemeRuntime(theme, io.vroomi.character.terrarium.TerrariumSim(), io.vroomi.character.render.terrarium.TerrariumPainter())
        SceneTheme.TREASURE_MAP -> ThemeRuntime(theme, io.vroomi.character.treasuremap.TreasureMapSim(), io.vroomi.character.render.treasuremap.TreasureMapPainter())
        SceneTheme.AQUARIUM -> ThemeRuntime(theme, io.vroomi.character.aquarium.AquariumSim(), io.vroomi.character.render.aquarium.AquariumPainter())
        SceneTheme.ORBIT -> ThemeRuntime(theme, io.vroomi.character.orbit.OrbitSim(), io.vroomi.character.render.orbit.OrbitPainter())
        SceneTheme.PAPER -> ThemeRuntime(theme, io.vroomi.character.paper.PaperSim(), io.vroomi.character.render.paper.PaperPainter())
        SceneTheme.PIXEL -> io.vroomi.character.render.pixel.newPixelRuntime()
        SceneTheme.PLASMA -> ThemeRuntime(theme, io.vroomi.character.plasma.PlasmaSim(), io.vroomi.character.render.plasma.PlasmaPainter())
        SceneTheme.HANGAR -> ThemeRuntime(theme, io.vroomi.character.hangar.HangarSim(), io.vroomi.character.render.hangar.HangarPainter())
        else -> null
    }

    fun supports(theme: SceneTheme): Boolean = theme in SUPPORTED

    val SUPPORTED: List<SceneTheme> = listOf(
        SceneTheme.MUSIC_BOX, SceneTheme.TERRARIUM, SceneTheme.TREASURE_MAP, SceneTheme.AQUARIUM, SceneTheme.ORBIT,
        SceneTheme.PAPER, SceneTheme.PIXEL, SceneTheme.PLASMA, SceneTheme.HANGAR,
    )
}
