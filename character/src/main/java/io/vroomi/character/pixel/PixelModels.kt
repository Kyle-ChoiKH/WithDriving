package io.vroomi.character.pixel

import io.vroomi.character.Biome
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Constants and small pure functions of Pixel World (pure Kotlin). */
object PixelModels {
    /** The virtual screen (8:3). */
    const val VW = 480
    const val VH = 180
    /** Virtual px per second for each km/h (100 km/h → 240 px/s), capped at 120 km/h. */
    const val PX_PER_KMH = 2.4f
    const val CAP_KMH = 120f
    /** Parallax factors: sky details, far ridge, mid tiles, near props, ground and hero. */
    val PARALLAX = floatArrayOf(0.05f, 0.15f, 0.35f, 0.6f, 1.0f)
    const val CHUNK = 32
    const val HERO_X = 120
    const val ROAD_Y = 150
    /** The rover body line (its chassis top is one row below; the wheels touch the ground). */
    const val ROVER_Y = ROAD_Y - 14
    /** Sprite animation: at most 12 frames per second (6 in calm mode). */
    const val SPRITE_FPS = 12
    const val CALM_SPRITE_FPS = 6
    /** A hop: 0.5 s parabola with a 10 px apex. */
    const val HOP_S = 0.5
    const val HOP_APEX = 10f
    /** Shards spawn so that the hop's apex meets them 1.2 s later. */
    const val SHARD_LEAD_S = 1.2

    /** Signed world speed in virtual px/s. Null speed is unknown → 0 (no scrolling). Reverse is negative. */
    fun worldSpeed(kmh: Float?, reverse: Boolean): Float {
        val v = (kmh ?: 0f).coerceIn(0f, CAP_KMH) * PX_PER_KMH
        return if (reverse) -v else v
    }

    /** The least width of the world (virtual px) a window shows: a narrower window gets a smaller scale instead. */
    const val MIN_VIEW_W = 320

    /**
     * Integer scale for a canvas of [w] × [h] px: max(1, floor(min(w ÷ 320, h ÷ 180))). On the wide display this is
     * the whole 480 px world; a 16:9, split or upright window keeps the world large and loses its right side (down
     * to [MIN_VIEW_W] px), since the hero rides at the left.
     */
    fun scale(w: Float, h: Float): Int = max(1, floor(min(w / MIN_VIEW_W, h / VH)).toInt())

    /**
     * Top-left offset (canvas px) that centres the scaled screen in [w] × [h]. In a box narrower or lower than the
     * scaled screen it is cropped instead of shrunk: the bottom stays (road and hero) and the hero keeps 40 virtual
     * px of room on its left.
     */
    fun offset(w: Float, h: Float, s: Int): Pair<Int, Int> {
        val ox = if (w >= VW * s) ((w - VW * s) / 2f).toInt() else -((HERO_X - 40) * s).coerceIn(0, (VW * s - w).toInt())
        val oy = if (h >= VH * s) ((h - VH * s) / 2f).toInt() else (h - VH * s).toInt()
        return ox to oy
    }

    /** The sprite frame index for an animation running at [fps] (capped at 12) after [t] seconds. */
    fun frame(t: Double, fps: Int, frames: Int): Int = ((floor(t * min(fps, SPRITE_FPS)).toLong()) % frames).toInt()

    /** Hop height (px, up positive) at [u] 0..1 through the hop. */
    fun hopHeight(u: Double): Float = if (u <= 0.0 || u >= 1.0) 0f else (HOP_APEX * 4.0 * u * (1.0 - u)).toFloat()

    /** Deterministic hash → [0, 1). */
    fun hash(a: Int, b: Int, c: Int = 0): Float {
        var x = a * 73856093 xor b * 19349663 xor c * 83492791
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0x7fffffff) / 2147483648f
    }
}

/**
 * World object kinds of the five tilesets (all scenery of the drive; there are no enemies and no coins).
 * Canyon mesas, desert highway, pine ridge, crystal caves and the neon arcade town.
 */
enum class PixelThing {
    NONE,
    MESA, BUTTE, BOULDER, ROCK_STACK,
    SAGUARO, BARREL_CACTUS, POLE, MILE_POST,
    PINE_TALL, PINE_SMALL, LOG,
    CRYSTAL_BIG, CRYSTAL_SMALL, STALAGMITE, GLOW_POOL,
    ARCADE, SIGN_TOWER, NEON_POST, KIOSK,
}

/**
 * Which tileset each journey biome shows, and what it puts on the near layer and the mid layer.
 * CITY → neon arcade town, PARK → pine ridge, FIELD → desert highway, SEASIDE → crystal caves, MOUNTAIN → canyon mesas.
 */
object PixelBiomes {
    fun near(b: Biome): Array<PixelThing> = when (b) {
        Biome.CITY -> arrayOf(PixelThing.NEON_POST, PixelThing.KIOSK, PixelThing.NONE, PixelThing.NEON_POST, PixelThing.NONE)
        Biome.PARK -> arrayOf(PixelThing.PINE_SMALL, PixelThing.LOG, PixelThing.PINE_SMALL, PixelThing.NONE, PixelThing.PINE_SMALL)
        Biome.FIELD -> arrayOf(PixelThing.BARREL_CACTUS, PixelThing.MILE_POST, PixelThing.NONE, PixelThing.BARREL_CACTUS, PixelThing.NONE)
        Biome.SEASIDE -> arrayOf(PixelThing.CRYSTAL_SMALL, PixelThing.GLOW_POOL, PixelThing.STALAGMITE, PixelThing.NONE, PixelThing.CRYSTAL_SMALL)
        Biome.MOUNTAIN -> arrayOf(PixelThing.BOULDER, PixelThing.ROCK_STACK, PixelThing.NONE, PixelThing.BOULDER, PixelThing.NONE)
    }
    fun skyline(b: Biome): Array<PixelThing> = when (b) {
        Biome.CITY -> arrayOf(PixelThing.ARCADE, PixelThing.ARCADE, PixelThing.ARCADE, PixelThing.SIGN_TOWER, PixelThing.NONE)
        Biome.PARK -> arrayOf(PixelThing.PINE_TALL, PixelThing.PINE_TALL, PixelThing.PINE_SMALL)
        Biome.FIELD -> arrayOf(PixelThing.SAGUARO, PixelThing.POLE, PixelThing.NONE)
        Biome.SEASIDE -> arrayOf(PixelThing.CRYSTAL_BIG, PixelThing.STALAGMITE, PixelThing.CRYSTAL_BIG)
        Biome.MOUNTAIN -> arrayOf(PixelThing.MESA, PixelThing.BUTTE, PixelThing.NONE)
    }
}
