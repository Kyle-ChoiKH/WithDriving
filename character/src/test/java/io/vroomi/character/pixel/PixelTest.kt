package io.vroomi.character.pixel

import io.vroomi.character.Biome
import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.character.pixel.sprites.GemSprite
import io.vroomi.character.pixel.sprites.PilotSprites
import io.vroomi.character.pixel.sprites.PixelFont
import io.vroomi.character.pixel.sprites.HoverPadSprite
import io.vroomi.character.pixel.sprites.PixelVehicle
import io.vroomi.character.pixel.sprites.RoverSprite
import io.vroomi.character.pixel.sprites.ShipSprite
import io.vroomi.character.pixel.sprites.TrailerSprite
import io.vroomi.core.vehicle.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelTest {

    private class Drive(val sim: PixelMechanics = PixelMechanics()) {
        var now = 1_000_000L; var stars = 0; var biome = Biome.CITY
        fun run(seconds: Float, kmh: Float?, gear: GearState = GearState.DRIVE, block: (CarSignals) -> CarSignals = { it }, each: () -> Unit = {}) {
            repeat((seconds * 60).toInt()) {
                val c = block(CarSignals(speedKmh = kmh, gear = gear, moving = (kmh ?: 0f) > 0.5f))
                sim.step(1f / 60f, CharacterTarget(kart = KartState(driving = (kmh ?: 0f) > 0.5f), car = c,
                    journey = Journey(active = true, tripSeq = 3, stars = stars, starSeq = stars, biome = biome)), now, 14f)
                now += 1000L / 60
                each()
            }
        }
    }

    @Test fun worldIsDeterministicAndABiomeChangeOnlyAffectsNewChunks() {
        val a = PixelWorld(5); val b = PixelWorld(5)
        for (c in -10..200) { assertEquals(a.thing(3, c), b.thing(3, c)); assertEquals(a.ridge(1, c * 7), b.ridge(1, c * 7)) }
        val w = PixelWorld(9)
        w.biome = Biome.CITY
        w.generateVisible(3, 0f)
        val before = (0..10).map { w.biomeOf(3, it) }
        w.biome = Biome.SEASIDE
        w.generateVisible(3, 0f)
        assertEquals("visible chunks keep their biome", before, (0..10).map { w.biomeOf(3, it) })
        w.generateVisible(3, 2000f)
        assertEquals("new chunks take the new biome", Biome.SEASIDE, w.biomeOf(3, 2000 / PixelModels.CHUNK + 5))
    }

    @Test fun speedMapping() {
        assertEquals(240f, PixelModels.worldSpeed(100f, false), 1e-3f)
        assertEquals(PixelModels.worldSpeed(120f, false), PixelModels.worldSpeed(200f, false), 1e-3f)
        assertTrue(PixelModels.worldSpeed(10f, true) < 0f)
        assertEquals(0f, PixelModels.worldSpeed(null, false), 0f)
        val d = Drive(); d.run(3f, null)
        assertEquals("unknown speed never scrolls", 0.0, d.sim.camera, 0.0)
    }

    @Test fun integerScaleAndLetterbox() {
        assertEquals(4, PixelModels.scale(1920f, 720f))
        assertEquals(2, PixelModels.scale(1280f, 480f))
        assertEquals(1, PixelModels.scale(576f, 216f))
        val (ox, oy) = PixelModels.offset(1920f, 720f, 4)
        assertEquals(0, ox); assertEquals(0, oy)
        val (cx, _) = PixelModels.offset(1000f, 720f, 2)
        assertEquals(20, cx)
        // A box smaller than 480 × 180 crops instead of shrinking: the road row and the hero stay inside.
        val (sx, sy) = PixelModels.offset(288f, 108f, 1)
        assertEquals(-80, sx); assertEquals(-72, sy)
        assertTrue("hero inside", PixelModels.HERO_X + sx in 0..(288 - 30))
        assertTrue("road inside", PixelModels.ROAD_Y + sy in 0 until 108)
        // A 16:9, split or upright window keeps the world large and crops its right side: the hero stays in view.
        assertEquals(4, PixelModels.scale(1280f, 720f))
        assertEquals(3, PixelModels.scale(1008f, 720f))
        assertEquals(1, PixelModels.scale(540f, 960f))
        val (wx, wy) = PixelModels.offset(1280f, 720f, 4)
        assertEquals(-320, wx); assertEquals(0, wy)
        assertTrue("hero inside the 16:9 window", PixelModels.HERO_X * 4 + wx in 0..(1280 - 30 * 4))
    }

    @Test fun hopApexMeetsTheShard() {
        for (kmh in listOf(0f, 30f, 60f, 100f)) {
            val d = Drive()
            d.run(3f, kmh)
            d.stars = 1
            var met = false
            val p = FloatArray(2)
            d.run(PixelModels.SHARD_LEAD_S.toFloat() + 0.3f, kmh) {
                val s = d.sim
                for (i in 0 until PixelMechanics.SHARDS) if (s.shardScreen(i, p)) {
                    val dueIn = s.shardCatch[i] - s.decoTime
                    if (dueIn in 0.0..(1.0 / 60.0)) {
                        assertEquals("x at $kmh km/h", PixelModels.HERO_X + PixelMechanics.PILOT_CX, p[0], 1.6f)
                        if (kmh > 0f) assertEquals("apex at $kmh km/h", PixelModels.HOP_APEX, s.hop(), 1f)
                        met = true
                    }
                }
            }
            assertTrue("shard caught at $kmh", met && d.sim.gems == 1)
        }
    }

    @Test fun spriteFrameRateAndFlashLimit() {
        var changes = 0; var last = -1
        for (k in 0 until 600) { val f = PixelModels.frame(k / 60.0, 30, 4); if (f != last) { changes++; last = f } }
        assertTrue("≤ 12 fps: $changes in 10 s", changes <= 121)
        val d = Drive()
        val times = ArrayList<Double>()
        d.sim.flash.recorder = { r, t -> if (r == io.vroomi.character.theme.ThemeSim.REGION_STAR) times += t }
        repeat(60) { d.stars++; d.run(1f, 60f) }
        for (i in 3 until times.size) assertTrue(times[i] - times[i - 3] >= 1.0)
    }

    @Test fun spriteDataIsWellFormed() {
        for (pose in PilotSprites.Pose.entries) {
            val frames = PilotSprites.frames(pose)
            assertEquals(pose.frames, frames.size)
            for (g in frames) {
                assertEquals(PilotSprites.H, g.size)
                g.forEach { row -> assertEquals(PilotSprites.W, row.length); row.forEach { ch -> assertTrue("$pose $ch", ch in PilotSprites.CODES) } }
            }
        }
        PilotSprites.LEGS.forEach { l -> assertEquals(PilotSprites.LEGS_H, l.size); l.forEach { assertEquals(PilotSprites.W, it.length) } }
        ShipSprite.BODY.also { assertEquals(ShipSprite.H, it.size) }.forEach { row -> assertEquals(ShipSprite.W, row.length); row.forEach { assertTrue(it in ShipSprite.CODES) } }
        TrailerSprite.BODY.also { assertEquals(TrailerSprite.H, it.size) }.forEach { row -> assertEquals(TrailerSprite.W, row.length); row.forEach { assertTrue(it in TrailerSprite.CODES) } }
        HoverPadSprite.FRAMES.forEach { f -> assertEquals(HoverPadSprite.H, f.size); f.forEach { row -> assertEquals(HoverPadSprite.W, row.length); row.forEach { assertTrue(it in HoverPadSprite.CODES) } } }
        RoverSprite.BODY.forEach { row -> assertEquals(RoverSprite.W, row.length); row.forEach { assertTrue(it in RoverSprite.CODES) } }
        RoverSprite.WHEELS.forEach { w -> assertEquals(RoverSprite.WHEEL, w.size); w.forEach { row -> assertEquals(RoverSprite.WHEEL, row.length); row.forEach { assertTrue(it in RoverSprite.WHEEL_CODES) } } }
        GemSprite.FRAMES.forEach { g -> assertEquals(GemSprite.SIZE, g.size); g.forEach { row -> assertEquals(GemSprite.SIZE, row.length); row.forEach { assertTrue(it in GemSprite.CODES) } } }
        PixelFont.GLYPHS.values.forEach { g -> assertEquals(7, g.size); g.forEach { assertEquals(5, it.length) } }
        PixelPalette.entries.forEach { assertEquals(16, it.hex.size) }
    }

    @Test fun oneFixedSixteenColourPalette() {
        assertEquals(16, PixelColors.MASTER.size)
        assertEquals("16 distinct master colours", 16, PixelColors.MASTER.toSet().size)
        // Every mood only remaps the master colours; none adds one.
        PixelPalette.entries.forEach { p -> p.hex.forEach { assertTrue("${p.name} ${Integer.toHexString(it)}", it in PixelColors.MASTER) } }
        // Not the house teal or orange of the KART world.
        val kart = listOf(0xFF3CC8B4.toInt(), 0xFF0E5A51.toInt(), 0xFFF2A93B.toInt())
        PixelColors.MASTER.forEach { assertTrue(it !in kart) }
    }

    @Test fun originality() {
        PixelPalette.entries.forEach { p -> assertTrue(p.hex.none { it == PixelPalette.FORBIDDEN_SKY }) }
        val words = listOf("SCORE", "LIFE", "LIVES", "GAME OVER", "1UP", "WORLD", "TIME", "COIN", "VROOMI")
        (PixelMechanics().allChips + PixelFont.GLYPHS.keys.joinToString("") + "3 KM").forEach { s ->
            words.forEach { w -> assertTrue("'$s' contains $w", !s.uppercase().contains(w)) }
        }
        // The in-scene font cannot spell the wordmark at all.
        assertTrue("VROI".any { it !in PixelFont.GLYPHS.keys })
        // No sound effect contains the B5 → E6 two-note leap (a famous coin sound).
        for (sfx in PixelSound.ALL_SFX) {
            val notes = (0 until sfx.size / 4).map { sfx[it * 4] }
            for (i in 1 until notes.size) assertTrue(!(notes[i - 1] == 83 && notes[i] == 88))
        }
    }

    @Test fun musicChangesTempoOnBarLinesAndStaysOriginal() {
        val m = PixelMusic()
        val out = io.vroomi.character.theme.SoundEvents()
        var lastBar = 0; var lastTempo = m.tempo
        repeat(60 * 60) {
            m.step(1.0 / 60.0, if (it < 1200) 0f else 90f, it >= 1200, out)
            if (m.tempo != lastTempo) { assertTrue("tempo changed on a bar line", m.bar != lastBar); lastTempo = m.tempo }
            lastBar = m.bar
            while (out.takeEvent() >= 0) Unit
        }
        assertEquals(120.0, m.tempo, 0.0)
        // No run of 6 equal intervals with a famous platformer theme (E E E C E G).
        val famous = listOf(0, 0, -4, 4, 3, -12)
        for (b in Biome.entries) for (set in 0..1) {
            val iv = PixelMusic.loopMelody(b, set).zipWithNext { x, y -> y - x }
            for (i in 0..iv.size - 6) assertTrue(iv.subList(i, i + 6) != famous)
        }
    }

    @Test fun aTapHopsOnceWithOneSound() {
        val d = Drive(); d.run(2f, 0f, GearState.PARK)
        while (d.sim.takeEvent() >= 0) Unit
        d.sim.tapHero()
        var hops = 0
        d.run(1f, 0f, GearState.PARK) {
            while (true) {
                val i = d.sim.takeEvent(); if (i < 0) break
                if (d.sim.eventMidi(i) == 67 && PixelSound.channel(d.sim.eventKind(i)) == PixelSound.TRIANGLE) hops++
            }
        }
        assertEquals(1, hops)
        // Driving: a tap does nothing.
        val m = Drive(); m.run(3f, 50f)
        m.sim.tapHero()
        assertEquals(0f, m.sim.hop(), 0f)
    }

    @Test fun chargingShowsThePostEvenWhenTheLevelIsUnknown() {
        val known = Drive(); known.run(2f, 0f, GearState.PARK, block = { it.copy(evCharging = true, evBatteryPct = 64) })
        assertTrue(known.sim.chargePost); assertEquals(4, known.sim.battery5)
        val unknown = Drive(); unknown.run(2f, 0f, GearState.PARK, block = { it.copy(evCharging = true, evBatteryPct = null) })
        assertTrue("post without a level", unknown.sim.chargePost); assertEquals(-1, unknown.sim.battery5)
        assertEquals("충전 중", unknown.sim.chip)
        val notCharging = Drive(); notCharging.run(2f, 0f, GearState.PARK, block = { it.copy(evCharging = null, evBatteryPct = 64) })
        assertTrue(!notCharging.sim.chargePost && notCharging.sim.battery5 == -1)
    }

    @Test fun safetyFreezeStopsTheWorld() {
        val d = Drive(); d.run(3f, 60f)
        d.run(1f, 60f, block = { it.copy(safetyWarningActive = true) })
        val cam = d.sim.camera
        d.run(2f, 60f, block = { it.copy(safetyWarningActive = true) })
        assertTrue("frozen: ${d.sim.camera - cam}", abs(d.sim.camera - cam) < 1.0)
        assertEquals(null, d.sim.chip)
    }

    /** The ladder: the vehicle follows the upgrade step; only the first two roll on wheels. */
    @Test fun vehicleFollowsStep() {
        assertEquals(listOf(PixelVehicle.ROVER, PixelVehicle.CARGO, PixelVehicle.HOVER, PixelVehicle.JET, PixelVehicle.SHIP), (0 until 5).map { PixelVehicle.of(it) })
        assertEquals(PixelVehicle.SHIP, PixelVehicle.of(9))
        assertEquals(listOf(true, true, false, false, false), PixelVehicle.entries.map { it.wheels })
        // The hover's glow row ends one pixel above its shadow on the road; the ship's pods end four above the ground.
        val roverY = PixelModels.ROVER_Y
        assertEquals(PixelModels.ROAD_Y - 2, roverY + PixelVehicle.HOVER.dy + HoverPadSprite.Y + HoverPadSprite.H - 1)
        assertEquals(PixelModels.ROAD_Y - 5, roverY + PixelVehicle.SHIP.dy + RoverSprite.BODY_DY + ShipSprite.H - 2)
    }
}
