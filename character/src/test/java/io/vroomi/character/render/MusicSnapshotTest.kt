package io.vroomi.character.render

import io.vroomi.character.Journey
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import io.vroomi.character.theme.ThemeSim
import io.vroomi.character.render.theme.isKitTheme
import org.junit.Rule
import org.junit.Test
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.vroomi.character.calibre.CalibreFinish
import io.vroomi.character.render.calibre.CalibreSceneStill
import io.vroomi.character.render.engine.EngineSceneStill
import io.vroomi.character.engine.EnginePowertrain
import io.vroomi.character.engine.Powertrain

/**
 * Every kit theme while another app plays music (the reaction to "음악 재생"): drawn on the beat, a few seconds in, so
 * the notes are up. Compare each with the same drive without music in its own snapshot test.
 */
class MusicSnapshotTest {
    @get:Rule val paparazzi = KitShots.wide()

    /** On a beat: a whole number of beats after the start (the beat runs on decoration time). */
    private val onBeat = (5 * 60.0 / ThemeSim.MUSIC_BPM).toFloat()

    private val music = driving(car(50f) { copy(musicPlaying = true) })

    @Test fun kartHumsAlong() = paparazzi.snapshot("music_kart") {
        VroomiSceneStill(music, KitShots.day, Modifier.fillMaxSize(), seconds = 4f)
    }

    /** At night the notes pale so they stand out from the dark sky. */
    @Test fun kartHumsAlongAtNight() = paparazzi.snapshot("music_kart_night") {
        VroomiSceneStill(music, KitShots.night, Modifier.fillMaxSize(), seconds = 4f)
    }

    @Test fun calibreNotes() = paparazzi.snapshot("music_calibre") {
        CalibreSceneStill(music, KitShots.day, CalibreFinish.AUTO, Modifier.fillMaxSize(), seconds = onBeat)
    }

    @Test fun engineNotes() = paparazzi.snapshot("music_engine") {
        EngineSceneStill(music, KitShots.day, EnginePowertrain.AUTO, Modifier.fillMaxSize(), seconds = onBeat, forced = Powertrain.ICE)
    }

    @Test fun everyKitThemeSwaysToMusic() {
        for (theme in SceneTheme.entries.filter(::isKitTheme)) {
            KitShots.script<ThemeSim>(paparazzi, theme, "music_${theme.name.lowercase()}", onBeat) { t ->
                driving(car(50f) { copy(musicPlaying = true) }, Journey(active = true, tripSeq = 1, tripKm = 50f * t / 3600f, stars = 3, starSeq = 3))
            }
        }
    }
}
