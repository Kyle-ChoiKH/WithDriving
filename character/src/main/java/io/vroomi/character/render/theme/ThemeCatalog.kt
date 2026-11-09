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
        SceneTheme.MARBLE -> ThemeRuntime(theme, io.vroomi.character.marble.MarbleSim(), io.vroomi.character.render.marble.MarblePainter())
        SceneTheme.PAPER -> ThemeRuntime(theme, io.vroomi.character.paper.PaperSim(), io.vroomi.character.render.paper.PaperPainter())
        SceneTheme.PIXEL -> io.vroomi.character.render.pixel.newPixelRuntime()
        else -> null
    }

    fun supports(theme: SceneTheme): Boolean = theme in SUPPORTED

    val SUPPORTED: List<SceneTheme> = listOf(SceneTheme.MUSIC_BOX, SceneTheme.TERRARIUM, SceneTheme.TREASURE_MAP, SceneTheme.AQUARIUM, SceneTheme.ORBIT, SceneTheme.MARBLE, SceneTheme.PAPER, SceneTheme.PIXEL)
}
