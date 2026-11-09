package io.vroomi.character.theme

import io.vroomi.character.render.theme.ChromeZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chrome-zone contract: the corners, the miniature scaling and the nudge painters use to clear the zones. */
class ChromeZonesTest {
    private val screens = listOf(1920f to 720f, 1788f to 720f, 1280f to 720f)

    @Test fun zonesSitInTheirCornersAndNeverMeet() {
        for ((w, h) in screens) for (mode in ChromeZones.Mode.entries) {
            val z = ChromeZones.zones(mode, w, h)
            assertEquals(2, z.size)
            val (status, action) = z
            // Status: top left; action: bottom right.
            assertEquals(0f, status.left, 0f); assertEquals(0f, status.top, 0f)
            assertEquals(w, action.right, 0f); assertEquals(h, action.bottom, 0f)
            assertFalse("$mode $w: the corners meet", status.intersects(action.left, action.top, action.right, action.bottom))
            // The middle of the screen and the other two corners stay free for the scene.
            for (zone in z) {
                assertFalse("$mode centre", zone.intersects(w / 2f - 40f, h / 2f - 40f, w / 2f + 40f, h / 2f + 40f))
                assertFalse("$mode top right", zone.intersects(w - 200f, 0f, w, 140f))
                assertFalse("$mode bottom left", zone.intersects(0f, h - 140f, 200f, h))
            }
        }
    }

    @Test fun demoDockHoldsTheCaptionAboveThreeToggles() {
        val dock = ChromeZones.zones(ChromeZones.Mode.MOVING_DEMO, 1920f, 720f)[1]
        val toggles = ChromeZones.zones(ChromeZones.Mode.MOVING, 1920f, 720f)[1]
        assertTrue(dock.width > toggles.width && dock.height > toggles.height)
        assertEquals(ChromeZones.toggleRowWidth(3), ChromeZones.CAPTION_W, 0f)
        assertEquals(ChromeZones.MOVING_MARGIN + ChromeZones.CAPTION_W + ChromeZones.CLEARANCE, dock.width, 0.001f)
    }

    @Test fun aMiniatureKeepsTheLayoutOfTheScreenItStandsFor() {
        val full = ChromeZones.all(1920f, 720f)
        val half = ChromeZones.all(960f, 360f)
        assertEquals(full.size, half.size)
        for (i in full.indices) {
            assertEquals(full[i].left / 2f, half[i].left, 0.01f)
            assertEquals(full[i].top / 2f, half[i].top, 0.01f)
            assertEquals(full[i].right / 2f, half[i].right, 0.01f)
            assertEquals(full[i].bottom / 2f, half[i].bottom, 0.01f)
        }
        assertTrue(ChromeZones.all(0f, 0f).isEmpty())
    }

    @Test fun aShortDenseDisplayIsAScreenNotAMiniature() {
        // 1920 × 720 px at 240 dpi is a 1280 × 480 dp window: the chrome there is full size, so the zones must be too.
        for ((w, h) in listOf(1280f to 480f, 1442f to 541f, 1024f to 600f, 800f to 480f)) {
            val z = ChromeZones.zones(ChromeZones.Mode.MOVING_DEMO, w, h, screen = h)
            val dock = z[1]
            assertEquals(ChromeZones.MOVING_MARGIN + ChromeZones.CAPTION_W + ChromeZones.CLEARANCE, dock.width, 0.001f)
            assertEquals(w, dock.right, 0f); assertEquals(h, dock.bottom, 0f)
            assertEquals(ChromeZones.MOVING_MARGIN + ChromeZones.CHIP_H + ChromeZones.CLEARANCE, z[0].height, 0.001f)
        }
        // A still of that window (960 × 360 px at density 1) stands for it, scaled by 360 / 480.
        val full = ChromeZones.Mode.entries.flatMap { m -> ChromeZones.zones(m, 1280f, 480f, screen = 480f) }
        val still = ChromeZones.Mode.entries.flatMap { m -> ChromeZones.zones(m, 960f, 360f, screen = 480f) }
        for (i in full.indices) {
            assertEquals(full[i].left * 0.75f, still[i].left, 0.01f)
            assertEquals(full[i].bottom * 0.75f, still[i].bottom, 0.01f)
        }
        // The default window is the design display.
        assertEquals(ChromeZones.REFERENCE_H, ChromeZones.screenH, 0f)
    }

    @Test fun currentPicksTheModesOfTheMoment() {
        val parked = ChromeZones.current(false, 1920f, 720f).map { it.name }
        val moving = ChromeZones.current(true, 1920f, 720f).map { it.name }
        assertEquals(listOf("status", "actions"), parked)
        assertTrue(moving.containsAll(listOf("chip", "toggles", "dock")))
    }

    @Test fun nudgeMovesTheShortestWayOutAndStaysInBounds() {
        val zones = ChromeZones.all(1920f, 720f)
        val out = FloatArray(2)
        // A label that would sit under the action corner moves out of it, and only as far as needed.
        ChromeZones.nudge(zones, 1700f, 650f, 160f, 30f, 0f, 0f, 1920f - 160f, 720f - 30f, out)
        assertTrue(ChromeZones.clear(zones, out[0], out[1], out[0] + 160f, out[1] + 30f))
        assertTrue(out[0] in 0f..1760f && out[1] in 0f..690f)
        // A label already clear stays where it is.
        ChromeZones.nudge(zones, 900f, 300f, 160f, 30f, 0f, 0f, 1760f, 690f, out)
        assertEquals(900f, out[0], 0f); assertEquals(300f, out[1], 0f)
    }
}
