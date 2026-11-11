package io.vroomi.character.render.pixel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
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
import io.vroomi.character.pixel.PixelMechanics
import io.vroomi.character.pixel.PixelModels
import io.vroomi.character.pixel.PixelModels.CHUNK
import io.vroomi.character.pixel.PixelModels.HERO_X
import io.vroomi.character.pixel.PixelModels.ROAD_Y
import io.vroomi.character.pixel.PixelModels.ROVER_Y
import io.vroomi.character.pixel.PixelModels.VH
import io.vroomi.character.pixel.PixelModels.VW
import io.vroomi.character.pixel.PixelPalette
import io.vroomi.character.pixel.PixelThing
import io.vroomi.character.pixel.sprites.PilotSprites
import io.vroomi.character.pixel.sprites.PixelFont
import io.vroomi.character.pixel.sprites.HoverPadSprite
import io.vroomi.character.pixel.sprites.PixelVehicle
import io.vroomi.character.pixel.sprites.RoverSprite
import io.vroomi.character.pixel.sprites.ShipSprite
import io.vroomi.character.pixel.sprites.TrailerSprite
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Pixel World: every frame is drawn into one 480 × 180 buffer with integer pixel coordinates and the 16 master
 * colours only, then blitted once at an integer scale with nearest-neighbour sampling. Sprites are never scaled,
 * rotated or blended, and every sprite animation flips frames on a fixed 12 fps clock (never a tween).
 */
internal class PixelPainter : ThemePainter<PixelMechanics>() {
    override val minDesignWidth = 1000f
    override val backdrop = Color(0xFF120F1A)

    private val buf = PixelBuffer()
    private val sprites = PixelSpriteCache()
    private val pt = FloatArray(2)
    private val digits = IntArray(8)
    private var pal = PixelPalette.DAY
    private var textAlpha = 1f
    private var textClock = -1.0

    // Frame-flip state, sampled on the fixed sprite clock (12 ticks per decoration second, 6 in calm mode).
    private var heldTick = Long.MIN_VALUE
    private var wheelFrame = 0
    /** The vehicle of the upgrade step (set from the frame's model before rendering). */
    private var vehicle = PixelVehicle.ROVER
    /** The upgrade step (0..4): each one also adds something to the sky ([skySteps]). */
    private var step = 0
    private var pinFrame = 0
    private var flagFrame = 0
    private var gemFrame = 0

    override fun tap(sim: PixelMechanics, x: Float, y: Float) { sim.tapHero() }

    override fun drawPx(s: DrawScope, sim: PixelMechanics, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val sc = PixelModels.scale(f.w, f.h)
        legibility(sim, f, sc)
        vehicle = PixelVehicle.of(f.model)
        step = f.model.coerceIn(0, 4)
        render(sim, f)
        val (ox, oy) = PixelModels.offset(f.w, f.h, sc)
        // Letterbox in the sky and ground colours, so the bands read as more sky and more ground.
        s.drawRect(Color(pal.color(LAVENDER)), Offset(left, top), Size(f.w, f.h / 2f))
        s.drawRect(Color(groundBase(sim.biome)), Offset(left, top + f.h / 2f), Size(f.w, f.h / 2f))
        s.drawImage(buf.image, srcOffset = IntOffset.Zero, srcSize = IntSize(VW, VH),
            dstOffset = IntOffset((left + ox).toInt(), (top + oy).toInt()), dstSize = IntSize(VW * sc, VH * sc), filterQuality = FilterQuality.None)
    }

    override fun draw(s: DrawScope, sim: PixelMechanics, f: ThemeFrame, tx: ThemeTexts) = Unit

    /**
     * Text legibility while driving: the km sign's glyphs must show at least [MIN_TEXT_SP] tall on the display
     * (virtual glyph height × the integer scale, against the density), otherwise the text fades out over 0.3 s in
     * two dither steps while the car moves, and comes back when parked.
     */
    private fun legibility(sim: PixelMechanics, f: ThemeFrame, sc: Int) {
        val minPx = with(f.density) { MIN_TEXT_SP.sp.toPx() }
        val ok = KM_GLYPH_H * sc >= minPx
        val target = if (!sim.moving || ok) 1f else 0f
        // The first frame (a still, or a fresh scene) starts settled.
        val dt = if (textClock < 0.0) FADE_S else (sim.time - textClock).toFloat().coerceIn(0f, 0.1f)
        textClock = sim.time
        textAlpha = if (textAlpha < target) minOf(target, textAlpha + dt / FADE_S) else maxOf(target, textAlpha - dt / FADE_S)
    }

    /** Renders one frame and returns the (reused) 480 × 180 buffer: for detail sheets. */
    internal fun frame(sim: PixelMechanics, f: ThemeFrame): android.graphics.Bitmap { render(sim, f); return buf.bitmap }

    private fun c(i: Int) = pal.color(i)
    private fun lit(i: Int) = pal.lit(i)

    private fun groundBase(b: Biome): Int = when (b) {
        Biome.MOUNTAIN -> c(RUST); Biome.FIELD -> c(SLATE); Biome.PARK -> c(OCHRE); Biome.SEASIDE -> c(NIGHT_VIOLET); Biome.CITY -> c(SLATE)
    }

    // ------------------------------------------------------------------ the frame

    private fun render(sim: PixelMechanics, f: ThemeFrame) {
        pal = sim.palette
        clock(sim)
        val cam = sim.camera
        val cam0 = if (sim.calm) 0.0 else cam
        buf.canvas.save()
        buf.canvas.translate(sim.shake().toFloat(), 0f)
        buf.blit(sprites.sky(pal), 0, 0)
        skyDetails(sim, cam0 * PixelModels.PARALLAX[0])
        skySteps(sim, cam0 * PixelModels.PARALLAX[0])
        farRidge(sim, (cam0 * PixelModels.PARALLAX[1]).toInt())
        mid(sim, (cam0 * PixelModels.PARALLAX[2]).toInt())
        near(sim, (cam * PixelModels.PARALLAX[3]).toInt())
        ground(sim, cam)
        decals(sim, cam)
        hero(sim)
        shards(sim)
        particles(sim)
        weather(sim)
        gemRow(sim)
        buf.canvas.restore()
        intro(f.options.intro)
    }

    /** Samples every frame-flip animation on the fixed sprite clock, so nothing changes faster than 12 fps. */
    private fun clock(sim: PixelMechanics) {
        val fps = if (sim.calm) PixelModels.CALM_SPRITE_FPS else PixelModels.SPRITE_FPS
        val tick = floor(sim.anim * fps).toLong()
        if (tick == heldTick) return
        heldTick = tick
        wheelFrame = if (sim.reducedMotion) 0 else Math.floorMod(floor(sim.wheel).toInt(), 4)
        pinFrame = (sim.pinwheel * 2).toInt() and 1
        flagFrame = (sim.flag * 3).toInt() and 1
        gemFrame = (tick % 4).toInt()
    }

    // ------------------------------------------------------------------ sky and far ridge

    private fun skyDetails(sim: PixelMechanics, cam: Double) {
        val shift = cam.toInt()
        if (sim.night > 0.5f) {
            // A crescent moon and fixed stars (no twinkle).
            val mx = 70; val my = 34
            for (dy in -9..9) for (dx in -9..9) {
                val inMoon = dx * dx + dy * dy <= 81
                val inBite = (dx - 4) * (dx - 4) + (dy + 2) * (dy + 2) <= 64
                if (inMoon && !inBite) buf.dot(mx + dx, my + dy, lit(CREAM))
            }
            for (i in 0 until 46) {
                val x = Math.floorMod((PixelModels.hash(i, 3) * VW).toInt() - shift, VW)
                val y = 3 + (PixelModels.hash(i, 4) * 86).toInt()
                buf.dot(x, y, if (i % 5 == 0) lit(CREAM) else lit(LAVENDER))
                if (i % 9 == 0) { buf.dot(x - 1, y, lit(LAVENDER)); buf.dot(x + 1, y, lit(LAVENDER)); buf.dot(x, y - 1, lit(LAVENDER)); buf.dot(x, y + 1, lit(LAVENDER)) }
            }
            return
        }
        if (sim.rain < 0.5f) {
            // A big low sun cut by horizontal slits, high on the left (the key light of the world comes from there).
            val sx = 66; val sy = 50; val r = 20
            for (dy in -r..r) {
                if (dy == 5 || dy == 6 || dy == 10 || dy == 11 || dy == 14 || dy == 17) continue
                val half = sqrt((r * r - dy * dy).toDouble()).toInt()
                buf.rect(sx - half, sy + dy, half * 2 + 1, 1, c(CREAM))
            }
        }
        // Long flat strata (never puffy clouds), drifting very slowly.
        for (i in 0 until 6) {
            val len = 30 + (PixelModels.hash(i, 21) * 80).toInt()
            val x = Math.floorMod((PixelModels.hash(i, 22) * (VW + 160)).toInt() - shift, VW + 160) - 80
            val y = 14 + i * 13 + (PixelModels.hash(i, 23) * 6).toInt()
            val col = c(if (y < 50) LILAC else CREAM)
            buf.rect(x, y, len, 1, col)
            buf.rect(x + 6, y + 1, len - 18, 1, col)
        }
    }

    /**
     * What each upgrade step adds to the sky, so a step shows at a glance wherever the road is: 2 a ringed planet,
     * 3 floating islands, 4 a comet, 5 a mothership high overhead. All of it is still (the islands and the ship
     * only drift with the camera); lights are steady.
     */
    private fun skySteps(sim: PixelMechanics, cam: Double) {
        val night = sim.night > 0.5f
        if (step >= 1) {
            // The ringed planet, upper right; its far side in shade.
            val cx = 408; val cy = 31; val r = 13
            for (dy in -r..r) {
                val half = sqrt((r * r - dy * dy).toDouble()).toInt()
                val band = if (dy in -8..-6 || dy in 1..3 || dy == 8) LILAC else PINK
                buf.rect(cx - half, cy + dy, half * 2 + 1, 1, if (night) lit(band) else c(band))
                val shade = (half * 0.45f).toInt()
                if (shade > 0) buf.rect(cx + half - shade + 1, cy + dy, shade, 1, c(MAGENTA))
            }
            for (dx in -24..24) {
                val t = dx / 24.0
                val ry = (5.0 * sqrt(1.0 - t * t)).toInt()
                // The near half of the ring crosses the disc; the far half is hidden behind it.
                buf.dot(cx + dx, cy + 2 + ry, lit(CREAM))
                if (abs(dx) > r - 1) buf.dot(cx + dx, cy + 2 - ry, lit(CREAM))
                if (abs(dx) in 17..23) buf.dot(cx + dx, cy + 3 + ry, c(LILAC))
            }
        }
        if (step >= 2) {
            // Floating islands: a strip of green, rock tapering under it, one crystal each.
            val shift = (cam * 2.0).toInt()
            for (i in 0 until 3) {
                val w = 26 + i * 6
                val x = Math.floorMod(150 + i * 118 - shift, VW + 60) - 30
                val y = 62 + (i * 11) % 19
                buf.rect(x, y, w, 1, c(LIME)); buf.rect(x, y + 1, w, 1, c(JADE)); buf.rect(x + 1, y + 2, w - 2, 1, c(DEEP_JADE))
                var inset = 3
                for (row in 0 until 5) { buf.rect(x + inset, y + 3 + row, w - inset * 2, 1, c(if (row < 2) OCHRE else RUST)); inset += 2 + row }
                val gx = x + w / 3
                buf.rect(gx, y - 4, 2, 4, lit(PINK)); buf.dot(gx, y - 5, lit(CREAM)); buf.rect(gx + 2, y - 2, 1, 2, lit(MAGENTA))
                buf.rect(x + w - 8, y - 3, 1, 3, c(DEEP_JADE)); buf.rect(x + w - 9, y - 5, 3, 2, c(JADE))
            }
        }
        if (step >= 3) {
            // A comet: a bright head and a long dithered tail.
            val hx = 236; val hy = 24
            for (i in 1..54) {
                val x = hx - i; val y = hy - i / 4
                if (i < 14 || (i + y) % 2 == 0) buf.dot(x, y, if (i < 20) lit(CREAM) else lit(LILAC))
                if (i in 4..30 && i % 2 == 0) buf.dot(x, y + 1, lit(LILAC))
                if (i in 4..22 && i % 3 == 0) buf.dot(x, y - 1, lit(PINK))
            }
            buf.rect(hx - 1, hy - 1, 4, 3, lit(CREAM)); buf.rect(hx, hy - 2, 2, 5, lit(CREAM))
            // A small second moon, low on the right, with its dark side.
            val mx = 338; val my = 60; val mr = 13
            for (dy in -mr..mr) {
                val half = sqrt((mr * mr - dy * dy).toDouble()).toInt()
                buf.rect(mx - half, my + dy, half * 2 + 1, 1, lit(CREAM))
                val shade = (half * 0.7f).toInt()
                if (shade > 0) buf.rect(mx + half - shade + 1, my + dy, shade, 1, c(LILAC))
            }
            buf.rect(mx - 7, my - 4, 3, 3, c(LILAC)); buf.rect(mx - 3, my + 4, 2, 2, c(LILAC)); buf.dot(mx - 9, my + 2, c(LILAC))
        }
        if (step >= 4) {
            // The mothership, high overhead: a long hull, a bridge, a row of steady lights.
            val x = 262 - (cam * 0.5).toInt().coerceIn(-40, 40); val y = 6
            buf.rect(x + 34, y, 22, 3, c(DUSK)); buf.rect(x + 38, y - 2, 10, 2, c(DUSK)); buf.dot(x + 43, y - 4, lit(CREAM)); buf.rect(x + 43, y - 3, 1, 1, c(DUSK))
            buf.rect(x + 8, y + 3, 98, 2, c(DUSK))
            buf.rect(x, y + 5, 118, 4, c(NIGHT_VIOLET))
            buf.rect(x + 6, y + 9, 104, 2, c(INK)); buf.rect(x + 22, y + 11, 70, 1, c(INK))
            buf.rect(x, y + 6, 118, 1, c(MAGENTA))
            for (i in 0 until 11) buf.dot(x + 9 + i * 10, y + 7, lit(LIME))
            buf.rect(x - 3, y + 5, 3, 4, lit(PINK)); buf.rect(x - 5, y + 6, 2, 2, lit(CREAM))
            buf.rect(x + 52, y + 12, 14, 2, c(DUSK)); buf.rect(x + 56, y + 14, 6, 1, lit(LIME))
        }
    }

    private fun farRidge(sim: PixelMechanics, c: Int) {
        val w = sim.world
        var x = 0
        while (x < VW) {
            // Runs of columns that share one chunk (one tileset).
            val chunk = Math.floorDiv(x + c, CHUNK)
            val end = minOf(VW, chunk * CHUNK + CHUNK - c)
            val b = w.biomeOf(1, chunk)
            if (b == Biome.SEASIDE) buf.blitPart(sprites.caveWall(pal), x, 0, end - x, 132, x, 0)
            val col = when (b) { Biome.MOUNTAIN -> OCHRE; Biome.SEASIDE -> NIGHT_VIOLET; Biome.PARK -> DUSK; else -> LAVENDER }
            for (xx in x until end) {
                val h = w.ridge(1, xx + c)
                buf.rect(xx, 132 - h, 1, h, c(col))
                if (b == Biome.MOUNTAIN && h > 16) buf.dot(xx, 132 - h, c(SAND))
                if (b == Biome.SEASIDE) ceiling(xx, xx + c)
            }
            x = end
        }
    }

    /** The crystal caves' ceiling: hanging rock with stalactites, a lit rim and a few glowing crystal tips. */
    private fun ceiling(x: Int, wx: Int) {
        val g = Math.floorDiv(wx, 7); val u = Math.floorMod(wx, 7)
        val len = 6 + (PixelModels.hash(g, 41) * 30).toInt()
        val taper = abs(u - 3)
        val roof = 12 + (PixelModels.hash(Math.floorDiv(wx, 23), 42) * 8).toInt()
        val depth = roof + maxOf(0, len - taper * (len / 3 + 2))
        buf.rect(x, 0, 1, depth, c(INK))
        buf.dot(x, depth, c(if (u < 3) LAVENDER else DUSK))
        if (taper == 0 && len > 26) { buf.dot(x, depth + 1, lit(PINK)); buf.dot(x, depth + 2, lit(MAGENTA)) }
    }

    // ------------------------------------------------------------------ mid tiles (parallax 0.35)

    private fun mid(sim: PixelMechanics, c: Int) {
        val w = sim.world
        val first = Math.floorDiv(c, CHUNK) - 2
        // The band first (so tall tiles of one chunk can overhang the next).
        for (k in first..first + VW / CHUNK + 3) buf.blit(sprites.band(w.biomeOf(2, k), pal), k * CHUNK - c, 132)
        val night = sim.night > 0.5f
        for (k in first..first + VW / CHUNK + 3) {
            val x = k * CHUNK - c
            val v = w.variant(2, k)
            when (w.thing(2, k)) {
                PixelThing.MESA -> mesa(x, 40 + v * 6, 22 + v * 5)
                PixelThing.BUTTE -> mesa(x + 6, 16 + v * 2, 34 + v * 4)
                PixelThing.SAGUARO -> saguaro(x + 12, 136, 22 + v * 4, v)
                PixelThing.POLE -> pole(x + 14)
                PixelThing.PINE_TALL -> pine(x + 15, 138, 30 + v * 5, false)
                PixelThing.PINE_SMALL -> pine(x + 15, 138, 18 + v * 3, false)
                PixelThing.CRYSTAL_BIG -> crystals(x + 8, 138, 18 + v * 5, night, false)
                PixelThing.STALAGMITE -> stalagmite(x + 14, 138, 16 + v * 4)
                PixelThing.ARCADE -> arcade(x, k, v, night)
                PixelThing.SIGN_TOWER -> signTower(x + 14, night)
                else -> Unit
            }
        }
    }

    /** A flat-topped mesa: sheer stepped cliffs, sand cap, strata lines, lit left face and a dithered right face. */
    private fun mesa(x: Int, topW: Int, h: Int) {
        val base = 136
        for (r in 0 until h) {
            val y = base - h + r
            val grow = if (r < h - 6) (r / 4) * 2 else (h - 6) / 4 * 2 + (r - (h - 6)) * 3
            val left = x + 6 - grow / 2; val wid = topW + grow
            buf.rect(left, y, wid, 1, c(RUST))
            buf.rect(left, y, 2, 1, c(OCHRE))
            val shadeW = wid / 3
            for (sx in left + wid - shadeW until left + wid) if (((sx + y) and 1) == 0) buf.dot(sx, y, c(DUSK))
            if (r > 2 && r % 6 == 0) buf.rect(left + 2, y, wid - shadeW - 2, 1, c(OCHRE))
        }
        buf.rect(x + 6, base - h, topW, 1, c(SAND))
    }

    private fun saguaro(x: Int, base: Int, h: Int, v: Int) {
        val top = base - h
        buf.rect(x, top + 1, 5, h - 1, c(JADE)); buf.rect(x + 1, top, 3, 1, c(JADE))
        buf.rect(x, top + 1, 1, h - 1, c(LIME)); buf.rect(x + 4, top + 1, 1, h - 1, c(DEEP_JADE)); buf.rect(x + 2, top + 2, 1, h - 3, c(DEEP_JADE))
        // Arms: out, then up (the left arm sits higher on odd variants).
        val ly = top + 8 + (v and 1) * 4; val ry = top + 12 - (v and 1) * 3
        buf.rect(x - 4, ly + 4, 4, 3, c(JADE)); buf.rect(x - 4, ly - 2, 3, 6, c(JADE)); buf.rect(x - 4, ly - 2, 1, 9, c(LIME))
        buf.rect(x + 5, ry + 4, 4, 3, c(JADE)); buf.rect(x + 6, ry - 3, 3, 7, c(JADE)); buf.rect(x + 8, ry - 3, 1, 10, c(DEEP_JADE))
        if (v == 3) { buf.dot(x + 2, top - 1, c(PINK)); buf.dot(x + 1, top, c(PINK)) }
    }

    private fun pole(x: Int) {
        val top = 96
        buf.rect(x, top, 2, 40, c(DUSK)); buf.rect(x - 5, top + 3, 12, 2, c(DUSK)); buf.dot(x - 5, top + 2, c(INK)); buf.dot(x + 6, top + 2, c(INK))
        // Two sagging wires to the next pole (a fixed curve, one pixel per column).
        for (i in 0 until 64) {
            val sag = i * (64 - i) / 160
            buf.dot(x - 5 + i, top + 2 + sag, c(INK))
            if (i % 2 == 0) buf.dot(x + 6 + i, top + 2 + sag, c(INK))
        }
    }

    private fun pine(x: Int, base: Int, h: Int, outline: Boolean) {
        val tiers = 4
        val top = base - h
        buf.rect(x - 1, base - 5, 2, 5, c(OCHRE))
        val tierH = (h - 4) / tiers + 1
        for (r in 0 until h - 4) {
            val y = top + r
            val half = 1 + (r % tierH) / 2 + r / 6
            if (outline) buf.rect(x - half - 1, y, half * 2 + 2, 1, c(INK))
            buf.rect(x - half, y, half * 2, 1, c(DEEP_JADE))
            buf.rect(x - half, y, maxOf(1, half / 2), 1, c(JADE))
        }
    }

    /** A cluster of hexagonal prisms; at night they glow in their master colours. */
    private fun crystals(x: Int, base: Int, h: Int, night: Boolean, outline: Boolean) {
        val body = if (night) lit(MAGENTA) else c(MAGENTA)
        val light = if (night) lit(PINK) else c(PINK)
        for (i in 0 until 3) {
            val px = x + CRYSTAL_X[i]; val ph = h * CRYSTAL_H[i] / 6; val top = base - ph
            if (outline) { buf.rect(px - 1, top, 7, ph, c(INK)); buf.rect(px, top - 1, 5, 1, c(INK)) }
            buf.rect(px + 1, top, 3, 1, light); buf.rect(px, top + 1, 5, ph - 1, body)
            buf.rect(px, top + 1, 2, ph - 1, light)
            buf.rect(px + 2, top + 2, 1, maxOf(1, ph / 3), lit(CREAM))
        }
    }

    private fun stalagmite(x: Int, base: Int, h: Int) {
        for (r in 0 until h) {
            val half = r * 5 / h
            buf.rect(x - half, base - h + r, half * 2 + 1, 1, c(DUSK))
            buf.dot(x - half, base - h + r, c(LAVENDER))
        }
    }

    /**
     * The arcade town's halls (no window grids): a domed hall, a tall hall with a marquee of bulbs and a blade sign,
     * a low hall under a roof billboard, and a stepped facade. Each carries one sign icon (gem, ring, arrow or bolt).
     */
    private fun arcade(x: Int, k: Int, v: Int, night: Boolean) {
        val base = 136
        val glow = if (night) lit(PINK) else c(PINK)
        val glow2 = if (night) lit(LIME) else c(JADE)
        val bulb = if (night) lit(SAND) else c(CREAM)
        val icon = Math.floorMod(k * 7 + v, 4)
        // Doorways glow warm at night.
        val door = if (night) lit(SAND) else c(INK)
        when (v) {
            0 -> {
                // Domed hall with a ring sign on the drum.
                buf.rect(x + 1, base - 16, 28, 16, c(DUSK)); buf.rect(x + 26, base - 16, 3, 16, c(NIGHT_VIOLET))
                for (r in 0 until 12) { val half = sqrt((144 - (12 - r) * (12 - r)).toDouble()).toInt(); buf.rect(x + 15 - half, base - 28 + r, half * 2, 1, c(DUSK)); buf.dot(x + 15 - half, base - 28 + r, c(LAVENDER)) }
                buf.rect(x + 1, base - 17, 28, 1, c(LAVENDER))
                signIcon(x + 9, base - 27, icon, glow, glow2)
                buf.rect(x + 11, base - 9, 8, 9, door)
            }
            1 -> {
                // Tall hall: a marquee of bulbs along the roof and a blade sign on its corner.
                buf.rect(x + 2, base - 32, 24, 32, c(DUSK)); buf.rect(x + 23, base - 32, 3, 32, c(NIGHT_VIOLET)); buf.rect(x + 2, base - 32, 24, 1, c(LAVENDER))
                for (i in 0 until 6) buf.dot(x + 4 + i * 4, base - 30, bulb)
                buf.rect(x + 26, base - 30, 6, 22, c(INK))
                for (i in 0 until 3) buf.rect(x + 28, base - 28 + i * 7, 2, 5, if (i == 1) glow2 else glow)
                buf.rect(x + 8, base - 11, 10, 11, door)
                for (i in 0 until 5) buf.rect(x + 7 + i * 2, base - 14, 2, 3, if (i % 2 == 0) c(MAGENTA) else c(CREAM))
            }
            2 -> {
                // Low wide hall under a roof billboard on legs.
                buf.rect(x, base - 14, 31, 14, c(DUSK)); buf.rect(x, base - 14, 31, 1, c(LAVENDER)); buf.rect(x + 28, base - 13, 3, 13, c(NIGHT_VIOLET))
                buf.rect(x + 6, base - 18, 1, 4, c(INK)); buf.rect(x + 24, base - 18, 1, 4, c(INK))
                buf.rect(x + 3, base - 30, 25, 12, c(INK))
                signIcon(x + 9, base - 28, icon, glow, glow2)
                buf.rect(x + 5, base - 28, 1, 8, glow2); buf.rect(x + 25, base - 28, 1, 8, glow2)
                buf.rect(x + 12, base - 9, 8, 9, door)
            }
            else -> {
                // Stepped facade with chevron stripes.
                for (st in 0 until 3) buf.rect(x + 2 + st * 4, base - 14 - st * 7, 26 - st * 8, 14 + st * 7, c(DUSK))
                for (st in 0 until 3) buf.rect(x + 2 + st * 4, base - 14 - st * 7, 26 - st * 8, 1, c(LAVENDER))
                for (i in 0 until 3) { buf.rect(x + 9 + i * 4, base - 22, 2, 1, glow); buf.rect(x + 10 + i * 4, base - 21, 2, 1, glow) }
                signIcon(x + 9, base - 34, icon, glow, glow2)
                buf.rect(x + 11, base - 10, 8, 10, door)
            }
        }
    }

    /** A 12 × 8 lit sign icon: 0 gem, 1 ring, 2 arrow, 3 bolt. */
    private fun signIcon(x: Int, y: Int, icon: Int, glow: Int, glow2: Int) {
        buf.rect(x - 1, y - 1, 14, 10, c(INK))
        when (icon) {
            0 -> { buf.rect(x + 4, y + 1, 4, 1, glow); buf.rect(x + 3, y + 2, 6, 2, glow); buf.rect(x + 4, y + 4, 4, 1, glow); buf.rect(x + 5, y + 5, 2, 2, glow) }
            1 -> { buf.rect(x + 3, y + 1, 6, 1, glow2); buf.rect(x + 3, y + 6, 6, 1, glow2); buf.rect(x + 2, y + 2, 1, 4, glow2); buf.rect(x + 9, y + 2, 1, 4, glow2) }
            2 -> { buf.rect(x + 1, y + 3, 8, 2, glow); buf.rect(x + 8, y + 1, 1, 6, glow); buf.rect(x + 9, y + 2, 1, 4, glow); buf.rect(x + 10, y + 3, 1, 2, glow) }
            else -> { buf.rect(x + 6, y, 2, 3, glow2); buf.rect(x + 4, y + 3, 5, 1, glow2); buf.rect(x + 4, y + 4, 2, 4, glow2) }
        }
    }

    private fun signTower(x: Int, night: Boolean) {
        buf.rect(x, 88, 2, 48, c(SLATE))
        for (i in 0 until 3) {
            val y = 90 + i * 11
            val col = if (night) lit(TOWER_LIT[i]) else c(TOWER_DAY[i])
            buf.rect(x - 4, y, 10, 8, c(INK))
            buf.rect(x - 3, y + 1, 8, 1, col); buf.rect(x - 3, y + 6, 8, 1, col); buf.rect(x - 3, y + 1, 1, 6, col); buf.rect(x + 4, y + 1, 1, 6, col)
        }
    }

    // ------------------------------------------------------------------ near props (parallax 0.6)

    private fun near(sim: PixelMechanics, c: Int) {
        val w = sim.world
        val first = Math.floorDiv(c, CHUNK) - 1
        val night = sim.night > 0.5f
        for (k in first..first + VW / CHUNK + 2) {
            val x = k * CHUNK - c
            val v = w.variant(3, k)
            when (w.thing(3, k)) {
                PixelThing.BOULDER -> {
                    buf.rect(x + 9, 140, 14, 10, c(INK)); buf.rect(x + 8, 142, 16, 8, c(INK))
                    buf.rect(x + 10, 141, 12, 9, c(RUST)); buf.rect(x + 9, 143, 14, 7, c(RUST))
                    buf.rect(x + 10, 141, 5, 2, c(OCHRE)); buf.rect(x + 9, 143, 2, 3, c(OCHRE)); buf.rect(x + 18, 146, 4, 3, c(DUSK))
                }
                PixelThing.ROCK_STACK -> {
                    for (i in 0 until 3) {
                        val y = 142 - i * 7; val wid = 12 - i * 2 + (v and 1) * 2
                        buf.rect(x + 16 - wid / 2 - 1, y - 1, wid + 2, 8, c(INK))
                        buf.rect(x + 16 - wid / 2, y, wid, 6, c(if (i == 1) OCHRE else RUST)); buf.rect(x + 16 - wid / 2, y, wid, 1, c(SAND))
                    }
                }
                PixelThing.BARREL_CACTUS -> {
                    buf.rect(x + 11, 140, 10, 10, c(INK)); buf.rect(x + 12, 141, 8, 9, c(JADE))
                    buf.rect(x + 12, 141, 2, 9, c(LIME)); buf.rect(x + 15, 142, 1, 8, c(DEEP_JADE)); buf.rect(x + 18, 142, 1, 8, c(DEEP_JADE))
                    buf.rect(x + 14, 139, 4, 2, c(PINK)); buf.dot(x + 15, 138, c(MAGENTA))
                }
                PixelThing.MILE_POST -> {
                    buf.rect(x + 14, 134, 5, 16, c(INK)); buf.rect(x + 15, 135, 3, 15, c(CREAM)); buf.rect(x + 15, 135, 3, 3, c(MAGENTA))
                }
                PixelThing.PINE_SMALL -> pine(x + 16, 150, 20 + v * 3, true)
                PixelThing.LOG -> {
                    buf.rect(x + 7, 144, 18, 6, c(INK)); buf.rect(x + 8, 145, 16, 5, c(OCHRE)); buf.rect(x + 8, 145, 16, 1, c(SAND))
                    buf.rect(x + 22, 145, 2, 5, c(RUST)); buf.dot(x + 22, 147, c(SAND))
                    if (v > 1) { buf.dot(x + 12, 143, c(LIME)); buf.dot(x + 13, 142, c(LIME)) }
                }
                PixelThing.CRYSTAL_SMALL -> crystals(x + 9, 150, 10 + v * 2, night, true)
                PixelThing.GLOW_POOL -> {
                    val col = if (night) lit(LAVENDER) else c(LAVENDER)
                    buf.rect(x + 6, 147, 22, 3, c(INK)); buf.rect(x + 7, 147, 20, 2, col); buf.rect(x + 10, 147, 6, 1, if (night) lit(CREAM) else c(LILAC))
                }
                PixelThing.STALAGMITE -> stalagmite(x + 16, 150, 12 + v * 2)
                PixelThing.NEON_POST -> {
                    buf.rect(x + 15, 128, 2, 22, c(INK))
                    val ring = if (night) lit(PINK) else c(PINK)
                    buf.rect(x + 12, 122, 8, 1, ring); buf.rect(x + 12, 128, 8, 1, ring); buf.rect(x + 11, 123, 1, 5, ring); buf.rect(x + 20, 123, 1, 5, ring)
                    if (night) for (yy in 132 until 150 step 2) for (xx in 9 until 24 step 2) if (((xx + yy) and 3) == 0) buf.dot(x + xx, yy, lit(PINK))
                }
                PixelThing.KIOSK -> {
                    buf.rect(x + 8, 136, 18, 14, c(INK)); buf.rect(x + 9, 137, 16, 13, c(SLATE))
                    for (i in 0 until 4) buf.rect(x + 8 + i * 5, 134, 5, 3, if (i % 2 == 0) c(MAGENTA) else c(CREAM))
                    buf.rect(x + 11, 139, 8, 5, if (night) lit(SAND) else c(MIST)); buf.rect(x + 21, 140, 2, 10, c(DUSK))
                }
                else -> Unit
            }
        }
    }

    // ------------------------------------------------------------------ ground (parallax 1)

    private fun ground(sim: PixelMechanics, cam: Double) {
        val c = cam.toInt()
        val first = Math.floorDiv(c, CHUNK)
        for (k in first..first + VW / CHUNK + 1) buf.blit(sprites.ground(sim.world.biomeOf(4, k), pal), k * CHUNK - c, ROAD_Y)
        // Heat shimmer on the far ground: every other pixel row around the horizon is offset by one pixel, and the rows
        // swap slowly (0.4 Hz). The hero is drawn later, so it never wobbles.
        if (sim.warmth >= 0.99f) {
            val off = if (sim.reducedMotion) 0 else (sim.shimmer.toInt() % 2)
            for (y in ROAD_Y - 22 until ROAD_Y + 4) if ((y + off) % 2 == 0) buf.shiftRowRight(y)
        }
        // Puddles (rain), each with a one-row glint.
        if (sim.rain > 0.5f) for (i in 0 until 3) {
            val x = Math.floorMod(i * 170 - c, VW + 60) - 30
            buf.rect(x, ROAD_Y + 18, 26, 3, c(LILAC)); buf.rect(x + 4, ROAD_Y + 18, 8, 1, c(CREAM))
        }
    }

    private fun decals(sim: PixelMechanics, cam: Double) {
        for (i in sim.skidX.indices) {
            val x = (sim.skidX[i] - cam).toInt()
            if (x in -40..VW) { buf.rect(x - 16, ROAD_Y + 9, 20, 1, c(INK)); buf.rect(x - 14, ROAD_Y + 11, 16, 1, c(INK)) }
        }
        for (i in sim.signX.indices) {
            val x = (sim.signX[i] - cam).toInt()
            if (x !in -20..VW) continue
            // A roadside arrow board on a post: which way the blinker points (both ways for hazard).
            buf.rect(x, 126, 2, 24, c(INK)); buf.rect(x - 8, 115, 18, 12, c(INK)); buf.rect(x - 7, 116, 16, 10, c(MAGENTA))
            val left = sim.signSide[i] == TurnSignal.LEFT.ordinal
            val both = sim.signSide[i] == TurnSignal.HAZARD.ordinal
            val a = c(CREAM)
            if (left || both) { buf.rect(x - 3, 120, 7, 2, a); buf.rect(x - 4, 119, 1, 4, a); buf.rect(x - 5, 120, 1, 2, a) }
            if (!left || both) { buf.rect(x - 2, 120, 7, 2, a); buf.rect(x + 5, 119, 1, 4, a); buf.rect(x + 6, 120, 1, 2, a) }
        }
        for (i in sim.archX.indices) {
            val x = (sim.archX[i] - cam).toInt()
            if (x !in -80..VW + 10) continue
            if (abs(x - HERO_X) < 6) sim.cheer()
            // A checkpoint gate: two pylons with lamp tops holding a banner with "N KM" in double-size glyphs. The
            // banner rides high enough to pass over the app's driving dock (ChromeZones) as it scrolls by.
            val g = GATE_TOP
            for (side in GATE_SIDES) {
                buf.rect(x + side - 1, g + 5, 5, 150 - (g + 5), c(INK)); buf.rect(x + side, g + 6, 3, 150 - (g + 6), c(SLATE)); buf.rect(x + side, g + 6, 1, 150 - (g + 6), c(MIST))
                buf.rect(x + side - 1, g, 5, 5, c(INK)); buf.rect(x + side, g + 1, 3, 3, lit(SAND))
            }
            buf.rect(x - 34, g + 8, 70, 22, c(INK)); buf.rect(x - 33, g + 9, 68, 20, c(MAGENTA))
            km(sim.archKm[i], x + 1, g + 12, c(CREAM))
        }
        if (sim.garageX > 0f) {
            val x = sim.garageX.toInt()
            if (x < VW + 40) hangar(x, sim.arrival > 5.0)
        }
        if (sim.chargePost) {
            // A charging pylon beside the rover and a cable; the 5-segment gauge only when the level is known.
            val x = HERO_X + 36
            buf.rect(x - 1, 123, 10, 27, c(INK)); buf.rect(x, 124, 8, 26, c(MIST)); buf.rect(x, 124, 8, 3, c(MAGENTA)); buf.rect(x + 7, 127, 1, 23, c(SLATE)); buf.rect(x + 2, 129, 4, 5, lit(JADE))
            for (cx in 0 until 8) buf.dot(x - 8 + cx, 141 + (if (cx in 2..5) 1 else 0), c(INK))
            if (sim.battery5 >= 0) for (sg in 0 until 5) buf.rect(x - 2 + sg * 3, 118, 2, 4, if (sg < sim.battery5) lit(LIME) else c(INK))
        }
    }

    /** The arrival hangar: a ribbed half-barrel with a roll-up door (open, or rolled down when [closed]). */
    private fun hangar(x: Int, closed: Boolean) {
        for (r in 0 until 34) {
            val y = 116 + r
            val half = if (r < 12) 10 + r * 11 / 12 else 22
            buf.rect(x + 20 - half - 1, y, half * 2 + 2, 1, c(INK))
            buf.rect(x + 20 - half, y, half * 2, 1, c(SLATE))
            buf.rect(x + 20 - half, y, 2, 1, c(MIST))
        }
        for (rib in 0 until 5) buf.rect(x + 2 + rib * 9, 120, 1, 30, c(DUSK))
        buf.rect(x + 4, 126, 32, 24, c(INK))
        if (closed) for (s in 0 until 6) { buf.rect(x + 5, 127 + s * 4, 30, 3, c(MIST)); buf.rect(x + 5, 129 + s * 4, 30, 1, c(DUSK)) }
        buf.rect(x + 14, 118, 12, 4, c(MAGENTA))
    }

    // ------------------------------------------------------------------ the rover and its pilot

    private fun hero(sim: PixelMechanics) {
        val hop = sim.hop().toInt()
        val kx = HERO_X + sim.heroOffsetX.toInt()
        val v = vehicle
        val ky = ROVER_Y - hop + sim.bob + v.dy
        val pose = sim.pose()
        val standing = pose == PilotSprites.Pose.STEP_OUT || pose == PilotSprites.Pose.WAVE && sim.arrival > 3.5
        val lean = sim.leansOut && !standing // door open while parked: lean out of the seat and wave
        val frame = sim.poseFrame(pose)
        val fx = kx + RoverSprite.SEAT_X + if (standing) 30 else if (lean) -3 else 0
        val fy = if (standing) ROAD_Y - PilotSprites.H - PilotSprites.LEGS_H + 1 else ky + RoverSprite.SEAT_Y + if (lean) -1 else 0
        buf.blit(sprites.pilot(pose, frame, pal), fx, fy)
        if (standing) buf.blit(sprites.legs(if (pose == PilotSprites.Pose.STEP_OUT) frame else 0, pal), fx, fy + PilotSprites.H)
        // Visor mist (the defrost clears it from the bottom) and frost on the helmet when it is cold.
        if (sim.fog > 0.1f) {
            val clearRows = (sim.clear * 3f).toInt()
            for (r in 0 until 3 - clearRows) buf.rect(fx + 6, fy + 4 + r, 5, 1, c(MIST))
        }
        if (sim.warmth <= -0.99f) { buf.dot(fx + 2, fy + 3, c(CREAM)); buf.dot(fx + 3, fy + 2, c(CREAM)) }
        if (sim.arrival > 5.0) return // the hangar door is down
        val wf = wheelFrame
        // A floating vehicle's shadow stays on the road (it does not hop with it).
        if (!v.wheels) buf.rect(kx - 1, ROAD_Y, 28, 1, c(INK))
        if (v == PixelVehicle.SHIP) {
            buf.blit(sprites.ship(pal), kx + RoverSprite.BODY_DX, ky + RoverSprite.BODY_DY)
            for (px in intArrayOf(ShipSprite.POD_REAR_X, ShipSprite.POD_FRONT_X)) buf.rect(kx + px + (wf and 1), ky + ShipSprite.POD_Y, 2, 1, lit(if (wf % 2 == 0) LIME else CREAM))
        } else {
            if (v == PixelVehicle.CARGO) {
                buf.blit(sprites.trailer(pal), kx + TrailerSprite.DX, ky + TrailerSprite.DY)
                buf.blit(sprites.wheel(wf, pal), kx + TrailerSprite.WHEEL_X, ky + RoverSprite.WHEEL_Y)
                buf.rect(kx - 10, ky + 5, 6, 1, c(INK))
            }
            buf.blit(sprites.rover(pal), kx + RoverSprite.BODY_DX, ky + RoverSprite.BODY_DY)
            if (v.wheels) {
                buf.blit(sprites.wheel(wf, pal), kx + RoverSprite.WHEEL_REAR_X, ky + RoverSprite.WHEEL_Y)
                buf.blit(sprites.wheel(wf, pal), kx + RoverSprite.WHEEL_FRONT_X, ky + RoverSprite.WHEEL_Y)
            } else {
                buf.blit(sprites.pad(wf, pal), kx + RoverSprite.WHEEL_REAR_X, ky + HoverPadSprite.Y)
                buf.blit(sprites.pad(wf, pal), kx + RoverSprite.WHEEL_FRONT_X, ky + HoverPadSprite.Y)
            }
            if (v == PixelVehicle.JET) {
                // The nozzle at the tail, and exhaust dots trailing behind while it moves.
                buf.rect(kx - 8, ky + 1, 4, 1, c(INK)); buf.rect(kx - 8, ky + 6, 4, 1, c(INK)); buf.rect(kx - 8, ky + 2, 1, 4, c(INK))
                buf.rect(kx - 7, ky + 2, 3, 4, c(SLATE)); buf.rect(kx - 7, ky + 2, 3, 1, c(MIST))
                if (sim.kmh > 1f && !sim.reducedMotion) for (i in 0 until 5) {
                    val col = if (i < 2) CREAM else if (i < 4) SAND else PINK
                    buf.dot(kx - 10 - i * 3 - (wf % 3), ky + 3 + ((i + wf) and 1), lit(col))
                }
            }
        }
        // Lamps.
        val braking = sim.car.brakePressed == true || sim.accel < -0.6f
        if (braking) { buf.rect(kx - 3, ky + 2, 1, 2, lit(PINK)); buf.dot(kx - 4, ky + 2, lit(MAGENTA)); buf.dot(kx - 4, ky + 3, lit(MAGENTA)) }
        if (sim.blinkL > 0.5f || sim.blinkR > 0.5f) {
            // Blinker lamps at the rear and front corners. The side view shows the same pair for either side;
            // the roadside arrow board tells the direction (both ways for hazard).
            buf.rect(kx - 4, ky + 4, 1, 2, lit(SAND)); buf.rect(kx + 29, ky + 4, 1, 2, lit(SAND))
        }
        if (sim.gear == GearState.REVERSE) {
            buf.rect(kx - 4, ky + 1, 1, 2, lit(CREAM))
            for (ch in 0 until 2) {
                val cx = kx - 12 - ch * 7; val cy = ROAD_Y + 4
                buf.dot(cx, cy + 2, lit(CREAM)); buf.dot(cx + 1, cy + 1, lit(CREAM)); buf.dot(cx + 1, cy + 3, lit(CREAM))
                buf.dot(cx + 2, cy, lit(CREAM)); buf.dot(cx + 2, cy + 4, lit(CREAM))
            }
        }
        if (sim.night > 0.5f) {
            buf.rect(kx + 28, ky + 2, 2, 2, lit(CREAM))
            // A dithered headlamp beam.
            for (yy in 0 until 12) for (xx in 0 until 46) if (yy * 46 / 12 <= xx + 8 && ((xx + yy) and 1) == 0) buf.dot(kx + 31 + xx, ky + 1 + yy - xx / 8, lit(SAND))
        }
        if (sim.warmth <= -0.99f) { buf.dot(kx - 3, ky + 1, c(CREAM)); buf.dot(kx + 29, ky + 1, c(CREAM)) }
        // A whip antenna with a pinwheel (fan) and a pennant (cruise).
        buf.rect(kx - 2, ky - 13, 1, 12, c(INK))
        if (pinFrame == 0) { buf.dot(kx - 3, ky - 14, c(PINK)); buf.dot(kx - 1, ky - 14, c(JADE)) } else { buf.dot(kx - 2, ky - 15, c(PINK)); buf.dot(kx - 2, ky - 13, c(JADE)) }
        if (sim.cruise > 0.5f) { buf.rect(kx - 8, ky - 12 + flagFrame, 6, 1, c(SAND)); buf.rect(kx - 7, ky - 11 + flagFrame, 4, 1, c(SAND)) }
        // A "!" bubble after a harsh brake, and a small pink battery when it is low.
        if (sim.decoTime - sim.shakeAt in 0.0..1.2) {
            buf.rect(fx + 12, fy - 8, 5, 8, c(INK)); buf.rect(fx + 13, fy - 7, 3, 6, c(CREAM)); buf.rect(fx + 14, fy - 6, 1, 3, c(INK)); buf.dot(fx + 14, fy - 2, c(INK))
        }
        if (sim.battery in 0f..0.15f && !sim.chargePost) { buf.rect(fx + 12, fy - 5, 7, 4, c(INK)); buf.rect(fx + 13, fy - 4, 2, 2, lit(PINK)); buf.dot(fx + 19, fy - 4, c(INK)) }
        if (sim.car.brakePressed == true && sim.kmh > 5f && (floor(sim.anim * 8).toInt() % 2 == 0)) {
            buf.rect(kx - 5, ky + 13, 3, 2, c(OCHRE)); buf.dot(kx - 7, ky + 12, c(OCHRE))
        }
    }

    // ------------------------------------------------------------------ gems

    private fun shards(sim: PixelMechanics) {
        for (i in 0 until PixelMechanics.SHARDS) {
            if (!sim.shardScreen(i, pt)) continue
            buf.blit(sprites.gem(if (sim.calm) 0 else gemFrame, pal), pt[0].toInt() - 4, pt[1].toInt() - 4)
        }
        val t = sim.decoTime - sim.sparkleAt
        if (t in 0.0..0.34 && !sim.calm) {
            // A short pixel sparkle (the flash guard granted it): two quantised steps, never a fade.
            val x = HERO_X + PixelMechanics.PILOT_CX.toInt(); val y = PixelMechanics.SHARD_Y.toInt()
            val r = if (t < 0.17) 5 else 8
            buf.rect(x - r, y, 3, 1, lit(CREAM)); buf.rect(x + r - 2, y, 3, 1, lit(CREAM))
            buf.rect(x, y - r, 1, 3, lit(CREAM)); buf.rect(x, y + r - 2, 1, 3, lit(CREAM))
            buf.dot(x - r / 2, y - r / 2, lit(PINK)); buf.dot(x + r / 2, y - r / 2, lit(PINK)); buf.dot(x - r / 2, y + r / 2, lit(PINK)); buf.dot(x + r / 2, y + r / 2, lit(PINK))
        }
    }

    /**
     * The gem row (top right, clear of the app's status corner; ChromeZones): every caught gem lights the next
     * slot, left to right. A combo sends one light step along the whole row (10 slots per second). There is no
     * score, no lives and no counter.
     */
    private fun gemRow(sim: PixelMechanics) {
        val n = sim.gems
        if (n <= 0) return
        val sweep = ((sim.decoTime - sim.comboAt) * 10).toInt()
        val x0 = VW - 10 - PixelMechanics.MAX_GEMS * 12 + 2
        for (k in 0 until PixelMechanics.MAX_GEMS) {
            val x = x0 + k * 12; val y = 8
            buf.blit(sprites.gem(if (k < n) 0 else 4, pal), x, y)
            if (k == sweep && !sim.calm) { buf.rect(x + 4, y - 3, 1, 2, lit(CREAM)); buf.rect(x + 4, y + 10, 1, 2, lit(CREAM)); buf.rect(x - 3, y + 4, 2, 1, lit(CREAM)); buf.rect(x + 10, y + 4, 2, 1, lit(CREAM)) }
        }
    }

    private fun particles(sim: PixelMechanics) {
        if (sim.calm) return
        for (i in 0 until PixelMechanics.PARTS) {
            if (sim.partAge[i] < 0f) continue
            val x = sim.partX[i].toInt(); val y = sim.partY[i].toInt()
            if (sim.partKind[i] == PixelMechanics.KIND_SNOW) buf.dot(x, y, lit(CREAM))
            else { val s = if (sim.partAge[i] < 0.5f) 2 else 3; buf.rect(x - s / 2, y - s / 2, s, s, c(LILAC)) }
        }
    }

    private fun weather(sim: PixelMechanics) {
        if (sim.rain < 0.5f || sim.reducedMotion) return
        val slant = (abs(sim.speed) / 120f).toInt().coerceIn(0, 2)
        val step = (floor(sim.anim * PixelModels.SPRITE_FPS) * 12).toInt()
        for (i in 0 until 70) {
            val x0 = (PixelModels.hash(i, 51) * VW).toInt()
            val y = Math.floorMod((PixelModels.hash(i, 52) * VH).toInt() + step, VH)
            val x = Math.floorMod(x0 - y * slant / 3, VW)
            buf.rect(x, y, 1, 3, c(LILAC))
        }
    }

    /**
     * Entrance accent (intro < 1): the screen is revealed left to right in whole 16-pixel tile columns, one column per
     * step, with a dithered leading column. At intro = 1 nothing is drawn.
     */
    private fun intro(intro: Float) {
        if (intro >= 1f) return
        val cols = VW / 16
        val shown = (intro.coerceIn(0f, 1f) * (cols + 1)).toInt()
        val ink = PixelPalette.DAY.lit(INK)
        for (col in shown until cols) {
            val x = col * 16
            if (col == shown) { for (y in 0 until VH) for (xx in 0 until 16 step 2) buf.dot(x + xx + (y and 1), y, ink) }
            else buf.rect(x, 0, 16, VH, ink)
        }
    }

    /** "N KM" centred on [cx] at 2 × 2 dots, without building a string; hidden or dithered by the legibility fade. */
    private fun km(value: Int, cx: Int, y: Int, color: Int) {
        if (textAlpha < 0.34f) return
        val half = textAlpha < 0.67f
        var v = value.coerceIn(0, 99_999); var n = 0
        do { digits[n++] = v % 10; v /= 10 } while (v > 0)
        val adv = 6 * KM_DOT
        var x = cx - ((n + 3) * adv - KM_DOT) / 2
        for (i in n - 1 downTo 0) { glyph('0' + digits[i], x, y, color, half); x += adv }
        x += adv
        glyph('K', x, y, color, half); glyph('M', x + adv, y, color, half)
    }

    private fun glyph(ch: Char, x: Int, y: Int, color: Int, half: Boolean) {
        val g = PixelFont.GLYPHS[ch] ?: return
        for (r in g.indices) for (col in g[r].indices) {
            if (g[r][col] != '#') continue
            if (half && ((r + col) and 1) == 1) continue
            buf.rect(x + col * KM_DOT, y + r * KM_DOT, KM_DOT, KM_DOT, color)
        }
    }

    private companion object {
        val CRYSTAL_X = intArrayOf(0, 5, 11)
        /** Crystal heights in sixths of the cluster height. */
        val CRYSTAL_H = intArrayOf(4, 6, 3)
        val TOWER_LIT = intArrayOf(PINK, LIME, SAND)
        val TOWER_DAY = intArrayOf(PINK, JADE, SAND)
        val GATE_SIDES = intArrayOf(-38, 36)
        /** The checkpoint gate's lamp tops (the banner hangs 8 px below them). */
        const val GATE_TOP = 83
        /** Glyph size of the km sign: the 5 × 7 font at 2 × 2 pixels per dot. */
        const val KM_DOT = 2
        const val KM_GLYPH_H = 7 * KM_DOT
        /** The smallest in-scene text allowed while moving (sp), and its fade time. */
        const val MIN_TEXT_SP = 24f
        const val FADE_S = 0.3f
    }
}
