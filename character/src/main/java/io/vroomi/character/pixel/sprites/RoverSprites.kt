package io.vroomi.character.pixel.sprites

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The original 8-bit rover buggy, side view facing right: a boxy magenta tub with an open roll cage and two
 * oversized knobby wheels (11 × 11, four rotation frames). One character per pixel. Codes: '.' transparent,
 * 'o' ink outline, 'c' roll cage, 'b' body, 'h' body highlight, 'y' side stripe, 'd' underside, 'r' tail lamp,
 * 'l' headlamp. The grid's top-left sits at (rover x + [BODY_DX], rover y + [BODY_DY]).
 */
object RoverSprite {
    const val BODY_DX = -4
    const val BODY_DY = -12

    val BODY = arrayOf(
        "..................................",
        "........cccccccccccc..............",
        "........c..........c..............",
        "........c...........c.............",
        "........c...........c.............",
        "........c............c............",
        "........c............c............",
        "........c.............c...........",
        "........c.............c...........",
        "........c..............c..........",
        "........c..............c..........",
        ".oooooooc..............c..........",
        "obbbbbbbc..............coooooooooo",
        "obhhhhhbcooooooooooooooohhhhhhhhho",
        "orbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbll",
        "orbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbll",
        "oyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyo",
        "obbbbbbbbodbbbbbbbbbbbodbbbbbbbbbo",
        "obbbbbbbbodbbbbbbbbbbbodbbbbbbbbbo",
        "oddddddddddddddddddddddddddddddddo",
        "oddddddddddddddddddddddddddddddddo",
        ".oooooooooooooooooooooooooooooooo.",
    )
    const val W = 34
    const val CODES = ".ocbhydrl"

    /** Wheel size and where each wheel's top-left sits relative to the rover x / y. */
    const val WHEEL = 11
    const val WHEEL_REAR_X = -1
    const val WHEEL_FRONT_X = 18
    const val WHEEL_Y = 5
    /** Wheel codes: 'o' ink (tread knobs and hub), 't' tyre, 'r' rim. */
    const val WHEEL_CODES = ".otr"

    /** Four wheel frames: the tread knobs and the hub notch step one notch per frame. */
    val WHEELS: List<Array<String>> = (0 until 4).map { wheel(it) }

    private fun wheel(f: Int): Array<String> = Array(WHEEL) { y ->
        val row = CharArray(WHEEL)
        for (x in 0 until WHEEL) {
            val dx = (x - 5).toDouble(); val dy = (y - 5).toDouble()
            val d = sqrt(dx * dx + dy * dy)
            val a = (atan2(dy, dx) + PI) / (2 * PI)
            row[x] = when {
                d > 5.6 -> '.'
                d > 4.5 -> if ((a * 12).toInt() % 2 == f % 2) 'o' else 't'
                d > 3.3 -> 't'
                d > 1.5 -> if (d > 2.2 && ((a * 4).toInt() + f) % 4 == 0) 'o' else 'r'
                else -> 't'
            }
        }
        String(row)
    }

    /** The pilot's seat: the pilot grid's top-left relative to the rover x / y. */
    const val SEAT_X = 5
    const val SEAT_Y = -10
}

/**
 * The tiny rover pilot: a round helmet with a solid visor (no face, no eyes) over a teal-green suit, 12 × 12 for the
 * upper body (row 0 is free so a hop can lift it) plus 12 × 5 legs when standing. Codes: '.' transparent,
 * 'o' ink, 'h' helmet shell, 'a' helmet band, 'v' visor, 'g' visor glare, 's' suit, 'S' suit shade, 'k' glove.
 */
object PilotSprites {
    enum class Pose(val frames: Int) { DRIVE(2), IDLE(2), HOP(3), WAVE(3), SURPRISE(1), SLEEPY(2), HAPPY(2), STEP_OUT(3) }

    const val W = 12
    const val H = 12
    const val CODES = ".ohavgsSk"

    val BASE = arrayOf(
        "............",
        "...ooooo....",
        "..oaaaaaoo..",
        ".ohhhhhhhho.",
        ".ohhhovvvgvo",
        ".ohhhovvgvvo",
        ".ohhhovvvvvo",
        "..ohhhoooooo",
        "..osssssso..",
        ".osssSsssskk",
        ".osssSssso..",
        ".ossssssso..",
    )

    /** Standing legs (stand, stride, stand). */
    val LEGS = arrayOf(
        arrayOf("..ossssso...", "..oSSoSSo...", "..oSo.oSo...", "..oSo.oSo...", ".ooo..ooo..."),
        arrayOf("..ossssso...", "..oSSoSSo...", ".oSo...oSo..", ".oSo...oSo..", "ooo.....ooo."),
        arrayOf("..ossssso...", "..oSSoSSo...", "..oSo.oSo...", "..oSo.oSo...", ".ooo..ooo..."),
    )
    const val LEGS_H = 5

    /** Every frame of [pose] (derived from [BASE] by fixed rules). */
    fun frames(pose: Pose): List<Array<String>> = (0 until pose.frames).map { derive(pose, it) }

    private fun derive(pose: Pose, f: Int): Array<String> {
        val g = Array(H) { BASE[it].toCharArray() }
        when (pose) {
            Pose.DRIVE, Pose.IDLE -> if (f == 1) shift(g, 1)
            Pose.HOP -> if (f == 1) shift(g, -1)
            Pose.WAVE -> {
                // The front arm raised at three heights.
                put(g, 9, 10, 's'); put(g, 9, 11, '.')
                val r = 8 - f * 2
                put(g, r, 11, 'k'); put(g, r + 1, 11, 's'); put(g, r + 1, 10, 's')
            }
            Pose.SURPRISE -> { shift(g, -1); for (c in 6..10) if (g[3][c] == 'v') g[3][c] = 'g' }
            Pose.SLEEPY -> {
                // The helmet nods forward: the glare goes out.
                for (r in 0 until H) for (c in 0 until W) if (g[r][c] == 'g') g[r][c] = 'v'
                if (f == 1) shift(g, 1)
            }
            Pose.HAPPY -> {
                // Both gloves up.
                val r = if (f == 0) 5 else 6
                put(g, 9, 10, 's'); put(g, 9, 11, '.')
                put(g, r, 0, 'k'); put(g, r + 1, 0, 's'); put(g, r, 11, 'k'); put(g, r + 1, 11, 's')
            }
            Pose.STEP_OUT -> Unit
        }
        return Array(H) { String(g[it]) }
    }

    private fun put(g: Array<CharArray>, r: Int, c: Int, ch: Char) { if (r in 0 until H && c in 0 until W) g[r][c] = ch }

    private fun shift(g: Array<CharArray>, dy: Int) {
        val copy = Array(H) { g[it].copyOf() }
        for (r in 0 until H) for (c in 0 until W) {
            val sr = r - dy
            g[r][c] = if (sr in 0 until H) copy[sr][c] else '.'
        }
    }
}

/**
 * The reward: a gem shard (a faceted diamond outline), 9 × 9, in four spin frames that flip at a fixed rate.
 * Codes: '.' transparent, 'o' ink, 'w' glint, 'p' light facet, 'm' dark facet.
 */
object GemSprite {
    const val SIZE = 9
    const val CODES = ".owpm"
    val FRAMES = listOf(
        arrayOf("....o....", "...owo...", "..owppo..", ".owppmmo.", "oppppmmmo", ".opppmmo.", "..opmmo..", "...omo...", "....o...."),
        arrayOf("....o....", "...owo...", "...opo...", "..owpmo..", "..oppmo..", "..opmmo..", "...opo...", "...omo...", "....o...."),
        arrayOf("....o....", "....o....", "...owo...", "...opo...", "...opo...", "...omo...", "...omo...", "....o....", "....o...."),
        arrayOf("....o....", "...omo...", "...omo...", "..ompwo..", "..ommpo..", "..ommpo..", "...opo...", "...opo...", "....o...."),
    )
}

/** An original 5 × 7 bitmap font: digits and the few Latin marks used in the world ("3 KM"). */
object PixelFont {
    val GLYPHS: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'M' to arrayOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        ' ' to arrayOf(".....", ".....", ".....", ".....", ".....", ".....", "....."),
    )

    fun width(text: String): Int = text.length * 6 - 1
}
