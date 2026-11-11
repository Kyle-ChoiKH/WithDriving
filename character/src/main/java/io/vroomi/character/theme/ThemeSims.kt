package io.vroomi.character.theme

import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.musicbox.MusicBoxSim

/** The simulation of every procedural (theme kit) theme, by theme. Null for KART, CALIBRE and ENGINE. */
object ThemeSims {
    fun create(theme: SceneTheme): ThemeSim? = when (theme) {
        SceneTheme.MUSIC_BOX -> MusicBoxSim()
        SceneTheme.TERRARIUM -> io.vroomi.character.terrarium.TerrariumSim()
        SceneTheme.TREASURE_MAP -> io.vroomi.character.treasuremap.TreasureMapSim()
        SceneTheme.AQUARIUM -> io.vroomi.character.aquarium.AquariumSim()
        SceneTheme.ORBIT -> io.vroomi.character.orbit.OrbitSim()
        SceneTheme.PAPER -> io.vroomi.character.paper.PaperSim()
        SceneTheme.PIXEL -> io.vroomi.character.pixel.PixelMechanics()
        SceneTheme.PLASMA -> io.vroomi.character.plasma.PlasmaSim()
        SceneTheme.HANGAR -> io.vroomi.character.hangar.HangarSim()
        else -> null
    }
}
