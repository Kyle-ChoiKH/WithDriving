package io.vroomi.character.render.pixel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.asImageBitmap
import io.vroomi.character.Biome
import io.vroomi.character.pixel.PixelColors.CREAM
import io.vroomi.character.pixel.PixelColors.DEEP_JADE
import io.vroomi.character.pixel.PixelColors.DUSK
import io.vroomi.character.pixel.PixelColors.INK
import io.vroomi.character.pixel.PixelColors.JADE
import io.vroomi.character.pixel.PixelColors.LAVENDER
import io.vroomi.character.pixel.PixelColors.LILAC
import io.vroomi.character.pixel.PixelColors.LIME
import io.vroomi.character.pixel.PixelColors.MAGENTA
import io.vroomi.character.pixel.PixelColors.MIST
import io.vroomi.character.pixel.PixelColors.NIGHT_VIOLET
import io.vroomi.character.pixel.PixelColors.OCHRE
import io.vroomi.character.pixel.PixelColors.PINK
import io.vroomi.character.pixel.PixelColors.RUST
import io.vroomi.character.pixel.PixelColors.SAND
import io.vroomi.character.pixel.PixelColors.SLATE
import io.vroomi.character.pixel.PixelModels
import io.vroomi.character.pixel.PixelPalette
import io.vroomi.character.pixel.sprites.GemSprite
import io.vroomi.character.pixel.sprites.PilotSprites
import io.vroomi.character.pixel.sprites.HoverPadSprite
import io.vroomi.character.pixel.sprites.RoverSprite
import io.vroomi.character.pixel.sprites.ShipSprite
import io.vroomi.character.pixel.sprites.TrailerSprite

/** The one reused 480 × 180 offscreen screen (anti-alias and bitmap filtering off). */
internal class PixelBuffer {
    val bitmap: Bitmap = Bitmap.createBitmap(PixelModels.VW, PixelModels.VH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false; isDither = false }
    val image = bitmap.asImageBitmap()
    private val row = IntArray(PixelModels.VW)
    private val src = Rect()
    private val dst = Rect()

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        if (w <= 0 || h <= 0) return
        paint.color = color
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    fun dot(x: Int, y: Int, color: Int) = rect(x, y, 1, 1, color)

    /** Shifts one row of the frame right by one pixel (heat shimmer). No allocation. */
    fun shiftRowRight(y: Int) {
        if (y !in 0 until PixelModels.VH) return
        bitmap.getPixels(row, 0, PixelModels.VW, 0, y, PixelModels.VW, 1)
        bitmap.setPixels(row, 0, PixelModels.VW, 1, y, PixelModels.VW - 1, 1)
    }

    fun blit(b: Bitmap, x: Int, y: Int) {
        paint.color = -1
        canvas.drawBitmap(b, x.toFloat(), y.toFloat(), paint)
    }

    /** Copies the [w] × [h] part of [b] at ([sx], [sy]) to ([dx], [dy]) at 1:1. No allocation. */
    fun blitPart(b: Bitmap, sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
        if (w <= 0 || h <= 0) return
        paint.color = -1
        src.set(sx, sy, sx + w, sy + h); dst.set(dx, dy, dx + w, dy + h)
        canvas.drawBitmap(b, src, dst, paint)
    }
}

/**
 * Sprites and tiles parsed once from their string grids (or built once from integer rules) into Bitmaps, per
 * palette. Night is a palette swap of the same grids. Everything here is tiny (the largest is the 480 × 180 sky).
 */
internal class PixelSpriteCache {
    private val pilots = HashMap<Int, Bitmap>()
    private val legs = HashMap<Int, Bitmap>()
    private val rovers = HashMap<PixelPalette, Bitmap>()
    private val wheels = HashMap<Int, Bitmap>()
    private val ships = HashMap<PixelPalette, Bitmap>()
    private val trailers = HashMap<PixelPalette, Bitmap>()
    private val pads = HashMap<Int, Bitmap>()
    private val gems = HashMap<Int, Bitmap>()
    private val skies = HashMap<PixelPalette, Bitmap>()
    private val walls = HashMap<PixelPalette, Bitmap>()
    private val grounds = HashMap<Int, Bitmap>()
    private val bands = HashMap<Int, Bitmap>()

    fun pilot(pose: PilotSprites.Pose, frame: Int, p: PixelPalette): Bitmap {
        val key = (pose.ordinal * 4 + frame) * 8 + p.ordinal
        return pilots.getOrPut(key) {
            val grid = PilotSprites.frames(pose)[frame.coerceIn(0, pose.frames - 1)]
            decode(grid) { ch -> pilotColor(ch, p) }
        }
    }

    fun legs(frame: Int, p: PixelPalette): Bitmap = legs.getOrPut(frame * 8 + p.ordinal) {
        decode(PilotSprites.LEGS[frame.coerceIn(0, PilotSprites.LEGS.size - 1)]) { ch -> pilotColor(ch, p) }
    }

    private fun pilotColor(ch: Char, p: PixelPalette): Int = when (ch) {
        'o' -> p.color(INK); 'h' -> p.color(CREAM); 'a' -> p.color(JADE); 'v' -> p.color(NIGHT_VIOLET)
        'g' -> p.color(LAVENDER); 's' -> p.color(JADE); 'S' -> p.color(DEEP_JADE); 'k' -> p.color(CREAM)
        else -> 0
    }

    fun rover(p: PixelPalette): Bitmap = rovers.getOrPut(p) {
        decode(RoverSprite.BODY) { ch ->
            when (ch) {
                'o' -> p.color(INK); 'c' -> p.color(SLATE); 'b' -> p.color(MAGENTA); 'h' -> p.color(PINK)
                'y' -> p.color(SAND); 'd' -> p.color(DUSK); 'r' -> p.color(PINK); 'l' -> p.color(CREAM)
                else -> 0
            }
        }
    }

    /** The survey ship of step 5: the rover's colours on a new hull. */
    fun ship(p: PixelPalette): Bitmap = ships.getOrPut(p) {
        decode(ShipSprite.BODY) { ch ->
            when (ch) {
                'o' -> p.color(INK); 'c' -> p.color(LAVENDER); 'b' -> p.color(MAGENTA); 'h' -> p.color(PINK)
                'y' -> p.color(SAND); 'd' -> p.color(DUSK); 'r' -> p.color(PINK); 'l' -> p.color(CREAM); 'm' -> p.color(MIST)
                else -> 0
            }
        }
    }

    /** The cargo trailer of step 2. */
    fun trailer(p: PixelPalette): Bitmap = trailers.getOrPut(p) {
        decode(TrailerSprite.BODY) { ch ->
            when (ch) {
                'o' -> p.color(INK); 'b' -> p.color(SLATE); 'y' -> p.color(SAND); 'k' -> p.color(OCHRE); 'j' -> p.color(JADE); 'w' -> p.color(CREAM)
                else -> 0
            }
        }
    }

    /** A hover pad (steps 3 and 4), two glow frames; the glow keeps its lit colour at night. */
    fun pad(frame: Int, p: PixelPalette): Bitmap = pads.getOrPut((frame and 1) * 8 + p.ordinal) {
        decode(HoverPadSprite.FRAMES[frame and 1]) { ch ->
            when (ch) { 'o' -> p.color(INK); 'd' -> p.color(DUSK); 'm' -> p.color(MIST); 'g' -> p.lit(LIME); else -> 0 }
        }
    }

    fun wheel(frame: Int, p: PixelPalette): Bitmap = wheels.getOrPut(frame * 8 + p.ordinal) {
        decode(RoverSprite.WHEELS[frame and 3]) { ch ->
            when (ch) { 'o' -> p.color(INK); 't' -> p.color(SLATE); 'r' -> p.color(MIST); else -> 0 }
        }
    }

    /** Gem frames 0..3 (spin) in lit colours (the same in every mood); 4 = an empty, unlit slot of the gem row. */
    fun gem(frame: Int, p: PixelPalette): Bitmap = gems.getOrPut(frame * 8 + p.ordinal) {
        val grid = GemSprite.FRAMES[if (frame in 0..3) frame else 0]
        val unlit = frame == 4
        decode(grid) { ch ->
            when (ch) {
                'o' -> p.lit(INK)
                'w' -> if (unlit) p.color(DUSK) else p.lit(CREAM)
                'p' -> if (unlit) p.color(DUSK) else p.lit(PINK)
                'm' -> if (unlit) p.color(NIGHT_VIOLET) else p.lit(MAGENTA)
                else -> 0
            }
        }
    }

    /**
     * The sky: three colour bands joined by 4 × 4 ordered (Bayer) dither steps. Day: lavender → lilac → sand haze.
     * Each mood's remap turns the same bitmap into dusk, eco, night or rain.
     */
    fun sky(p: PixelPalette): Bitmap = skies.getOrPut(p) {
        val w = PixelModels.VW; val h = PixelModels.VH
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val th = BAYER[(y and 3) * 4 + (x and 3)]
            px[y * w + x] = when {
                y < 26 -> p.color(LAVENDER)
                y < 58 -> if (th < (y - 26) / 2) p.color(LILAC) else p.color(LAVENDER)
                y < 84 -> p.color(LILAC)
                y < 116 -> if (th < (y - 84) / 2) p.color(SAND) else p.color(LILAC)
                else -> p.color(SAND)
            }
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** The crystal caves' back wall (opaque, it hides the sky): dark at the ceiling, glowing lilac towards the floor. */
    fun caveWall(p: PixelPalette): Bitmap = walls.getOrPut(p) {
        val w = PixelModels.VW; val h = 132
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val th = BAYER[(y and 3) * 4 + (x and 3)]
            px[y * w + x] = when {
                y < 24 -> p.color(NIGHT_VIOLET)
                y < 56 -> if (th < (y - 24) / 2) p.color(DUSK) else p.color(NIGHT_VIOLET)
                y < 76 -> p.color(DUSK)
                y < 108 -> if (th < (y - 76) / 2) p.color(LAVENDER) else p.color(DUSK)
                else -> if (th < (y - 108) / 3) p.color(LILAC) else p.color(LAVENDER)
            }
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** One 32 × 30 ground tile of a tileset, from the ground line (y 150) down. */
    fun ground(b: Biome, p: PixelPalette): Bitmap = grounds.getOrPut(b.ordinal * 8 + p.ordinal) {
        val w = PixelModels.CHUNK; val h = PixelModels.VH - PixelModels.ROAD_Y
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = groundPixel(b, p, x, y)
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun groundPixel(b: Biome, p: PixelPalette, x: Int, y: Int): Int = when (b) {
        Biome.MOUNTAIN -> {
            // Canyon: rust rock blocks (16 × 8, offset every row) with a lit top edge and dark seams.
            val row = y / 8; val bx = (x + if (row % 2 == 1) 8 else 0) % 16
            when {
                y < 2 -> p.color(OCHRE)
                y % 8 == 2 || bx == 0 -> p.color(NIGHT_VIOLET)
                y % 8 == 3 || bx == 1 -> p.color(OCHRE)
                (x * 7 + y * 3) % 23 == 0 -> p.color(OCHRE)
                else -> p.color(RUST)
            }
        }
        Biome.FIELD -> {
            // Desert highway: a sand shoulder, an ink edge, charcoal asphalt with reflector studs and a solid edge line.
            when {
                y < 3 -> if ((x + y) % 5 == 0) p.color(OCHRE) else p.color(SAND)
                y == 3 -> p.color(INK)
                y == 13 && x in 14..15 -> p.color(CREAM)
                y == 25 -> p.color(SAND)
                ((x * 5 + y * 11) % 17 == 0) -> p.color(INK)
                else -> p.color(SLATE)
            }
        }
        Biome.PARK -> {
            // Pine ridge: a jagged lime and teal-green grass lip over ochre soil with stones.
            val tuft = intArrayOf(0, 1, 2, 1, 0, 2, 3, 1)[x % 8]
            when {
                y < tuft -> 0
                y < 3 -> p.color(LIME)
                y < 5 -> p.color(JADE)
                y == 5 -> p.color(DEEP_JADE)
                (x % 11 == 3 && y % 9 == 4) || (x % 13 == 8 && y % 7 == 2) -> p.color(RUST)
                ((x + y) and 3) == 0 && y < 9 -> p.color(RUST)
                else -> p.color(OCHRE)
            }
        }
        Biome.SEASIDE -> {
            // Crystal caves: violet rock tiles with crystal flecks.
            val ty = y / 8; val tx = (x + if (ty % 2 == 1) 4 else 0) / 8
            when {
                y == 0 -> p.color(LAVENDER)
                y < 3 -> p.color(DUSK)
                (x * 3 + y * 5) % 29 == 0 -> p.lit(PINK)
                (tx + ty) % 2 == 0 -> p.color(DUSK)
                else -> p.color(NIGHT_VIOLET)
            }
        }
        Biome.CITY -> {
            // Arcade town: a magenta kerb and a charcoal pavement of square slabs.
            when {
                y < 2 -> p.color(MAGENTA)
                y == 2 -> p.color(INK)
                y % 9 == 3 || x % 16 == 0 -> p.color(INK)
                y % 9 == 4 || x % 16 == 1 -> p.color(MIST)
                else -> p.color(SLATE)
            }
        }
    }

    /** The mid-ground band (32 × 18, y 132..150) of a tileset: a dithered top edge into the sky. */
    fun band(b: Biome, p: PixelPalette): Bitmap = bands.getOrPut(b.ordinal * 8 + p.ordinal) {
        val w = PixelModels.CHUNK; val h = 18
        val (top, body, deep) = when (b) {
            Biome.MOUNTAIN -> Triple(OCHRE, OCHRE, RUST)
            Biome.FIELD -> Triple(SAND, SAND, OCHRE)
            Biome.PARK -> Triple(JADE, DEEP_JADE, DEEP_JADE)
            Biome.SEASIDE -> Triple(LAVENDER, DUSK, NIGHT_VIOLET)
            Biome.CITY -> Triple(MIST, SLATE, DUSK)
        }
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val th = BAYER[(y and 3) * 4 + (x and 3)]
            px[y * w + x] = when {
                y < 4 -> if (th < y * 4 + 2) p.color(top) else 0
                y < 10 -> p.color(body)
                else -> if (th < (y - 10) * 2) p.color(deep) else p.color(body)
            }
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private inline fun decode(grid: Array<String>, color: (Char) -> Int): Bitmap {
        val h = grid.size; val w = grid[0].length
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = color(grid[y][x])
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    companion object {
        /** 4 × 4 ordered dither thresholds (0..15). */
        val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    }
}
