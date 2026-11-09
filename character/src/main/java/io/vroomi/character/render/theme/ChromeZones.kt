package io.vroomi.character.render.theme

import kotlin.math.max
import kotlin.math.min

/**
 * The chrome-zone contract between the app's interface and the scene painters.
 *
 * The app's chrome over a live scene lives in two corners only:
 * - the **status corner** (top left): the bond badge while parked, the status chip while moving;
 * - the **action corner** (bottom right): the stack of two home actions while parked; the big toggles while moving, with
 *   the demo caption stacked above them in a demo.
 *
 * Each zone is a rectangle in dp, relative to the scene's own rectangle (the scene is beside the rail while parked
 * and full-bleed while moving), and already includes the breathing room around the chrome. A painter keeps every
 * information-bearing element (titles, values, readings, labels, counters) out of the zones of the modes it can be
 * shown in; decorative drawing may pass under the chrome. The app places its chrome from the same numbers, so the
 * two sides agree by construction (a unit test in each module checks its half).
 *
 * Pure Kotlin: painters convert to their own units with [Zone.scaled].
 */
object ChromeZones {
    // ---------------------------------------------------------------- the chrome's own geometry (dp)

    /** Outer margin of the parked home chrome. */
    const val PARKED_MARGIN = 32f
    /** Outer margin of the moving chrome. */
    const val MOVING_MARGIN = 24f
    /** Breathing room a painter keeps between its information and the chrome. */
    const val CLEARANCE = 24f

    /** The parked bond badge (top left). */
    const val BADGE_W = 360f
    const val BADGE_H = 132f
    /** The parked home actions (bottom right): a right-aligned stack of two capsule buttons of equal width. */
    const val ACTIONS_W = 280f
    const val ACTIONS_H = 140f

    /** The moving status chip (top left): at most 12 characters at 26 sp. */
    const val CHIP_H = 64f
    const val CHIP_MAX_W = 400f
    /** A big toggle and the gap between toggles (bottom right). */
    const val TOGGLE_W = 168f
    const val TOGGLE_H = 96f
    const val TOGGLE_GAP = 16f
    /** The demo caption card, stacked above the toggles and exactly as wide as the row of three. */
    const val CAPTION_MAX_H = 112f
    const val CAPTION_GAP = 12f

    /** Width of a row of [n] big toggles. */
    fun toggleRowWidth(n: Int): Float = n * TOGGLE_W + (n - 1).coerceAtLeast(0) * TOGGLE_GAP

    /** The demo caption card's width: the row of three toggles (데모 끝내기, sound, calm). */
    val CAPTION_W: Float get() = toggleRowWidth(3)

    // ---------------------------------------------------------------- zones

    enum class Mode { PARKED, MOVING, MOVING_DEMO }

    /** A rectangle in the scene's coordinates (dp unless [scaled]). */
    data class Zone(val name: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        /** True when the rectangle [l, t, r, b] overlaps this zone (touching edges do not count). */
        fun intersects(l: Float, t: Float, r: Float, b: Float): Boolean = l < right && r > left && t < bottom && b > top

        /** This zone in other units: [k] units per dp, shifted by ([dx], [dy]) in those units. */
        fun scaled(k: Float, dx: Float = 0f, dy: Float = 0f): Zone = Zone(name, left * k + dx, top * k + dy, right * k + dx, bottom * k + dy)
    }

    /** The display height the chrome is designed for (dp). */
    const val REFERENCE_H = 720f
    /** A canvas shorter than this share of the window is a miniature of it (a hero still, a gallery card, a test strip). */
    const val MINIATURE_SHARE = 0.85f

    /**
     * The height of the app's window in dp. The app sets it from its root layout; the live scene is always laid out
     * at this height (full-bleed, beside the rail, or in the pane), so only miniatures are shorter. It defaults to the
     * design display ([REFERENCE_H]) for renders without the app.
     *
     * The chrome keeps its dp size on every display, so a real screen must never be mistaken for a miniature: on a
     * 1920 × 720 px display at 240 dpi (1280 × 480 dp) a fixed "shorter than N dp" rule would shrink the zones while
     * the buttons stay full size, and painters would put readings under them.
     */
    @Volatile var screenH: Float = REFERENCE_H
        set(value) { if (value > 0f) field = value }

    /**
     * The zones of [mode] for a scene of [w] × [h] dp. A miniature canvas (shorter than [MINIATURE_SHARE] of
     * [screenH]) gets the zones of the window it stands for, scaled down with it, so a still keeps the live scene's
     * layout.
     */
    fun zones(mode: Mode, w: Float, h: Float): List<Zone> = zones(mode, w, h, screenH)

    /** [zones] for a window [screen] dp tall. */
    fun zones(mode: Mode, w: Float, h: Float, screen: Float): List<Zone> {
        if (h <= 0f || w <= 0f) return emptyList()
        if (h >= screen * MINIATURE_SHARE) return screenZones(mode, w, h)
        val k = h / screen
        return screenZones(mode, w / k, screen).map { it.scaled(k) }
    }

    private fun screenZones(mode: Mode, w: Float, h: Float): List<Zone> = when (mode) {
        Mode.PARKED -> listOf(
            Zone("status", 0f, 0f, min(w, PARKED_MARGIN + BADGE_W + CLEARANCE), min(h, PARKED_MARGIN + BADGE_H + CLEARANCE)),
            Zone("actions", max(0f, w - (PARKED_MARGIN + ACTIONS_W + CLEARANCE)), max(0f, h - (PARKED_MARGIN + ACTIONS_H + CLEARANCE)), w, h),
        )
        Mode.MOVING -> listOf(
            chip(w, h),
            Zone("toggles", max(0f, w - (MOVING_MARGIN + toggleRowWidth(2) + CLEARANCE)), max(0f, h - (MOVING_MARGIN + TOGGLE_H + CLEARANCE)), w, h),
        )
        Mode.MOVING_DEMO -> listOf(
            chip(w, h),
            Zone(
                "dock", max(0f, w - (MOVING_MARGIN + CAPTION_W + CLEARANCE)),
                max(0f, h - (MOVING_MARGIN + TOGGLE_H + CAPTION_GAP + CAPTION_MAX_H + CLEARANCE)), w, h,
            ),
        )
    }

    private fun chip(w: Float, h: Float) =
        Zone("chip", 0f, 0f, min(w, MOVING_MARGIN + CHIP_MAX_W + CLEARANCE), min(h, MOVING_MARGIN + CHIP_H + CLEARANCE))

    /** The zones of the modes a scene may be shown in: [moving] picks the moving pair (demo or not), else parked. */
    fun current(moving: Boolean, w: Float, h: Float): List<Zone> =
        if (moving) zones(Mode.MOVING, w, h) + zones(Mode.MOVING_DEMO, w, h) else zones(Mode.PARKED, w, h)

    /** Every zone of every mode (for elements that are laid out once, whatever the mode). */
    fun all(w: Float, h: Float): List<Zone> = Mode.entries.flatMap { zones(it, w, h) }

    /** True when [l, t, r, b] stays clear of every zone in [zones]. */
    fun clear(zones: List<Zone>, l: Float, t: Float, r: Float, b: Float): Boolean = zones.none { it.intersects(l, t, r, b) }

    /**
     * Moves a box of [bw] × [bh] whose preferred top-left is ([x], [y]) the least distance needed so it clears
     * [zones], staying inside [minX]..[maxX] × [minY]..[maxY] (the top-left's range). Tries the four ways out of
     * each zone it hits and keeps the nearest that is clear; returns the preferred spot if none is. [out] = (x, y).
     */
    fun nudge(zones: List<Zone>, x: Float, y: Float, bw: Float, bh: Float, minX: Float, minY: Float, maxX: Float, maxY: Float, out: FloatArray) {
        out[0] = x.coerceIn(minX, max(minX, maxX)); out[1] = y.coerceIn(minY, max(minY, maxY))
        if (clear(zones, out[0], out[1], out[0] + bw, out[1] + bh)) return
        var bestD = Float.MAX_VALUE; var bx = out[0]; var by = out[1]
        for (z in zones) {
            val cands = floatArrayOf(
                z.left - bw, out[1], z.right, out[1], out[0], z.top - bh, out[0], z.bottom,
            )
            for (i in 0 until 4) {
                val cx = cands[i * 2].coerceIn(minX, max(minX, maxX)); val cy = cands[i * 2 + 1].coerceIn(minY, max(minY, maxY))
                if (!clear(zones, cx, cy, cx + bw, cy + bh)) continue
                val d = (cx - x) * (cx - x) + (cy - y) * (cy - y)
                if (d < bestD) { bestD = d; bx = cx; by = cy }
            }
        }
        out[0] = bx; out[1] = by
    }
}
