package io.vroomi.character.render

import io.vroomi.character.CharacterTarget
import io.vroomi.character.render.theme.KitShots
import io.vroomi.character.render.theme.KitShots.car
import io.vroomi.character.render.theme.KitShots.driving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The friend in the kart and the music in the car: it hums along at once, in bouts, and stops with the music. */
class KartMusicTest {
    /** The scene runtime builds graphics paths, which need the layout library this rule loads. */
    @get:Rule val paparazzi = KitShots.wide()

    private val music = driving(car(50f) { copy(musicPlaying = true) })
    private val quiet = driving(car(50f) { copy(musicPlaying = false) })

    private fun SceneRuntime.run(seconds: Float, t: CharacterTarget, each: (Fidget) -> Unit = {}) {
        repeat((seconds * 60).toInt()) { step(1f / 60f, t, KitShots.day, 0L); each(fidget) }
    }

    @Test fun humsAtOnceAndStopsWithTheMusic() {
        val rt = SceneRuntime(seed = 3)
        rt.run(6f, quiet)
        rt.start(Fidget.NONE)
        rt.run(0.5f, music)
        assertEquals(Fidget.HUM, rt.fidget)
        rt.run(0.1f, quiet)
        assertNotEquals(Fidget.HUM, rt.fidget)
    }

    @Test fun aLongDriveWithMusicIsNotOneLongHum() {
        val rt = SceneRuntime(seed = 3)
        rt.run(6f, quiet)
        val seen = HashMap<Fidget, Int>()
        var frames = 0
        rt.run(240f, music) { seen.merge(it, 1, Int::plus); frames++ }
        val hum = (seen[Fidget.HUM] ?: 0) / frames.toFloat()
        assertTrue("humming share $hum of $seen", hum in 0.25f..0.7f)
        assertTrue("eyes open between bouts: $seen", (seen[Fidget.NONE] ?: 0) / frames.toFloat() > 0.2f)
        assertTrue("other fidgets still happen: $seen", seen.keys.any { it != Fidget.HUM && it != Fidget.NONE })
    }
}
