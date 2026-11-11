package io.vroomi.character.pixel.sprites

/**
 * The dot world's upgrade ladder (spec 5.10): the vehicle changes with the step, always in the same 16 colours.
 * 1 the rover, 2 the rover pulling a small cargo trailer, 3 a hover rover (pads instead of wheels, its shadow one
 * pixel below), 4 the jet hover (a nozzle and a tail of exhaust dots), 5 a small survey ship flying four pixels
 * above the ground.
 */
enum class PixelVehicle(
    /** It rolls on wheels (otherwise it floats and casts a shadow line on the road). */
    val wheels: Boolean,
    /** How far below the rover's line its sprite sits (px), so that the gap under it comes out right. */
    val dy: Int,
) {
    ROVER(true, 0), CARGO(true, 0), HOVER(false, 2), JET(false, 2), SHIP(false, 1);

    companion object {
        fun of(model: Int): PixelVehicle = entries[model.coerceIn(0, entries.size - 1)]
    }
}

private fun grid(w: Int, h: Int, paint: (Array<CharArray>) -> Unit): Array<String> {
    val g = Array(h) { CharArray(w) { '.' } }
    paint(g)
    return Array(h) { String(g[it]) }
}

private fun Array<CharArray>.h(row: Int, c0: Int, c1: Int, ch: Char) { for (c in c0..c1) if (row in indices && c in this[row].indices) this[row][c] = ch }
private fun Array<CharArray>.p(row: Int, col: Int, ch: Char) = h(row, col, col, ch)

/**
 * The cargo trailer, side view: a low bed with a stripe, two crates on it; it rolls on one of the rover's wheels.
 * Codes: '.' transparent, 'o' ink, 'b' bed, 'y' stripe, 'k' ochre crate, 'j' jade crate, 'w' crate strap.
 * The grid's top-left sits at (rover x + [DX], rover y + [DY]).
 */
object TrailerSprite {
    const val W = 18
    const val H = 9
    const val DX = -28
    const val DY = -2
    const val WHEEL_X = -25
    const val CODES = ".obykjw"
    val BODY: Array<String> = grid(W, H) { g ->
        g.h(0, 2, 7, 'o'); for (r in 1..3) { g.h(r, 2, 7, 'k'); g.p(r, 2, 'o'); g.p(r, 7, 'o'); g.p(r, 4, 'w') }
        g.h(1, 9, 14, 'o'); for (r in 2..3) { g.h(r, 9, 14, 'j'); g.p(r, 9, 'o'); g.p(r, 14, 'o'); g.p(r, 12, 'w') }
        g.h(4, 0, 17, 'o')
        for (r in 5..7) { g.h(r, 0, 17, 'b'); g.p(r, 0, 'o'); g.p(r, 17, 'o') }
        g.h(6, 1, 16, 'y')
        g.h(8, 0, 17, 'o')
    }
}

/**
 * A hover pad (it takes a wheel's place): a flat pod with a bright rim and a row of glow dots under it, in two
 * frames (the dots step sideways). Codes: '.' transparent, 'o' ink, 'd' pod, 'm' rim, 'g' glow.
 */
object HoverPadSprite {
    const val W = 11
    const val H = 5
    const val Y = 6
    const val CODES = ".odmg"
    val FRAMES: List<Array<String>> = (0 until 2).map { f ->
        grid(W, H) { g ->
            g.h(0, 1, 9, 'o')
            g.h(1, 0, 10, 'd'); g.p(1, 0, 'o'); g.p(1, 10, 'o')
            g.h(2, 0, 10, 'm'); g.p(2, 0, 'o'); g.p(2, 10, 'o')
            g.h(3, 1, 9, 'o')
            for (c in 1 + f..9 step 2) g.p(4, c, 'g')
        }
    }
}

/**
 * The small survey ship, side view facing right, on the rover's own 34-wide grid (same seat, same lamp places):
 * a bubble canopy over the pilot, a tail fin, a slim hull with the side stripe, two thruster pods underneath.
 * Codes: '.' transparent, 'o' ink, 'c' canopy, 'b' hull, 'h' hull highlight, 'y' stripe, 'd' underside,
 * 'r' tail lamp, 'l' headlamp, 'm' thruster pod.
 */
object ShipSprite {
    const val W = 34
    const val H = 22
    const val CODES = ".ocbhydrlm"
    /** Where the thruster pods' glow goes, relative to the rover x / y. */
    const val POD_REAR_X = 4
    const val POD_FRONT_X = 17
    const val POD_Y = 9
    val BODY: Array<String> = grid(W, H) { g ->
        // The canopy: a bubble from the hull's top over the seat.
        g.h(1, 10, 19, 'c'); g.h(2, 8, 9, 'c'); g.h(2, 20, 21, 'c'); g.p(3, 7, 'c'); g.p(3, 22, 'c'); g.p(4, 6, 'c'); g.p(4, 23, 'c')
        for (r in 5..11) g.p(r, 5, 'c')
        for (r in 5..6) g.p(r, 24, 'c'); for (r in 7..8) g.p(r, 25, 'c'); for (r in 9..10) g.p(r, 26, 'c'); g.p(11, 27, 'c')
        // The tail fin.
        g.p(8, 1, 'o'); g.h(9, 0, 2, 'o'); g.p(9, 1, 'b'); g.h(10, 0, 3, 'o'); g.h(10, 1, 2, 'b'); g.h(11, 0, 4, 'o'); g.h(11, 1, 3, 'b')
        // The hull: it narrows toward the nose and underneath.
        g.h(12, 0, 30, 'o'); g.h(12, 1, 3, 'b')
        g.h(13, 0, 32, 'h'); g.p(13, 0, 'o'); g.h(13, 1, 3, 'b'); g.h(13, 31, 32, 'o')
        for (r in 14..15) { g.h(r, 0, 33, 'b'); g.p(r, 0, 'o'); g.p(r, 1, 'r'); g.h(r, 32, 33, 'l') }
        g.h(16, 0, 33, 'y'); g.p(16, 0, 'o'); g.p(16, 33, 'o')
        g.h(17, 1, 32, 'b'); g.p(17, 1, 'o'); g.p(17, 32, 'o')
        g.h(18, 2, 30, 'd'); g.p(18, 2, 'o'); g.p(18, 30, 'o')
        g.h(19, 3, 29, 'o')
        // Thruster pods.
        g.h(20, 7, 11, 'o'); g.h(20, 8, 10, 'm'); g.h(20, 20, 24, 'o'); g.h(20, 21, 23, 'm')
    }
}
