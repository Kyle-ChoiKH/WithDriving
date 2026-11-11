package io.vroomi.character.musicbox

import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Journey
import io.vroomi.character.KartState
import io.vroomi.core.vehicle.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor

class MusicBoxComposerTest {

    private fun melody(c: MusicBoxComposer, phrase: Int): List<PinNote> =
        (phrase * 8 until phrase * 8 + 8).flatMap { b -> c.bar(b).filter { it.kind == PinNote.MELODY }.sortedBy { it.slot } }

    @Test fun deterministicForTheSameSeed() {
        val a = MusicBoxComposer(42); val b = MusicBoxComposer(42)
        for (bar in 0 until 64) {
            val x = a.bar(bar).map { Triple(it.slot, it.tine, it.kind) }
            val y = b.bar(bar).map { Triple(it.slot, it.tine, it.kind) }
            assertEquals(x, y)
        }
    }

    @Test fun chordTonesOnStrongBeatsRangesAndLeaps() {
        var strong = 0; var chordTone = 0
        for (seed in 1..20) {
            val c = MusicBoxComposer(seed)
            for (bar in 0 until 32) {
                val notes = c.bar(bar)
                val root = c.chordOf(bar)
                notes.filter { it.kind == PinNote.MELODY }.forEach { n ->
                    assertTrue("melody range ${n.tine}", n.tine in MusicBoxComposer.MELODY_LOW..MusicBoxComposer.MELODY_HIGH)
                    if (n.slot == 0 || n.slot == 8) {
                        strong++
                        if (Math.floorMod(n.tine - root, 7) in listOf(0, 2, 4)) chordTone++
                    }
                }
                notes.filter { it.kind == PinNote.BASS }.forEach { assertTrue(it.tine in 0..6) }
            }
            for (phrase in 0 until 4) {
                val m = melody(c, phrase)
                for (i in 1 until m.size) assertTrue("leap above an octave", abs(MusicBoxComposer.semitones(m[i].tine) - MusicBoxComposer.semitones(m[i - 1].tine)) <= 12)
            }
        }
        assertTrue("chord tones on strong beats: $chordTone / $strong", chordTone >= strong * 0.9)
    }

    @Test fun cadencesAtBarsFourAndEight() {
        for (seed in 1..20) {
            val c = MusicBoxComposer(seed)
            for (phrase in 0 until 4) {
                val b4 = c.bar(phrase * 8 + 3).filter { it.kind == PinNote.MELODY }.maxByOrNull { it.slot }!!
                assertTrue("half cadence on V (seed $seed)", Math.floorMod(b4.tine - 4, 7) in listOf(0, 2, 4))
                val b8 = c.bar(phrase * 8 + 7).filter { it.kind == PinNote.MELODY }.maxByOrNull { it.slot }!!
                assertEquals("authentic cadence on the tonic (seed $seed)", 0, Math.floorMod(b8.tine, 7))
                assertEquals(0, c.chordOf(phrase * 8 + 7))
                assertEquals(4, c.chordOf(phrase * 8 + 3))
            }
        }
    }

    /** Interval sequences (semitones) of melodies the composer must never reproduce. */
    private val famous = mapOf(
        "twinkle" to listOf(0, 7, 0, 2, 0, -2, -2, 0, -1, 0, -2, 0, -2),
        "birthday" to listOf(0, 2, -2, 5, -1, -4, 0, 2, -2, 7, -2),
        "ode" to listOf(0, 1, 2, 0, -2, -1, -2, -2, 0, 2, 2, 0, -2, 0),
        "elise" to listOf(-1, 1, -1, 1, -5, 3, -2, -3),
        "lullaby" to listOf(0, 3, -3, 0, 3, -3, 3, 5, -1, -2, 0, -2),
        "canon" to listOf(-2, -2, -1, -2, -2, 2, 2),
        "lamb" to listOf(-2, -2, 2, 2, 0, 0, -2, 0, 0, 2, 3, 0),
        "jacques" to listOf(2, 2, -4, 0, 2, 2, -4, 4, 1, 2),
        "bells" to listOf(0, 0, 0, 0, 0, 0, 3, -7, 2, 2),
        "march" to listOf(5, 0, 0, -5, 7, -3, 1),
        "westminster" to listOf(-4, 2, -7, 0, 7, 2, -4),
        "school_bell" to listOf(0, 2, 0, -2, 0, -3),
        "ringtone" to listOf(-2, 4, 2, -7, -2, 3, 2),
        "platformer" to listOf(0, 0, -4, 4, 3, -12),
    )

    private fun longestCommonRun(a: List<Int>, b: List<Int>): Int {
        var best = 0
        for (i in a.indices) for (j in b.indices) {
            var k = 0
            while (i + k < a.size && j + k < b.size && a[i + k] == b[j + k]) k++
            if (k > best) best = k
        }
        return best
    }

    @Test fun neverQuotesAFamousMelody() {
        // The motifs themselves, and 200 generated phrases.
        val sequences = ArrayList<List<Int>>()
        MotifBank.MOTIFS.forEach { m -> val d = (0 until m.size / 3).map { m[it * 3 + 2] }; sequences += d.zipWithNext { x, y -> MusicBoxComposer.semitones(y + 7) - MusicBoxComposer.semitones(x + 7) } }
        for (seed in 1..50) {
            val c = MusicBoxComposer(seed)
            for (phrase in 0 until 4) {
                val m = melody(c, phrase).map { MusicBoxComposer.semitones(it.tine) }
                sequences += m.zipWithNext { x, y -> y - x }
            }
        }
        assertTrue(sequences.size >= 208)
        for (s in sequences) for ((name, f) in famous) {
            val run = longestCommonRun(s, f)
            assertTrue("matches '$name' for $run intervals: $s", run < 6)
        }
    }
}

class MusicBoxSimTest {

    private class Drive(val sim: MusicBoxSim = MusicBoxSim()) {
        var now = 1_000_000L
        var km = 0f
        fun run(seconds: Float, kmh: Float, gear: GearState = GearState.DRIVE, journey: (Journey) -> Journey = { it }, each: () -> Unit = {}) {
            repeat((seconds * 60).toInt()) {
                km += kmh / 3600f / 60f
                val car = CarSignals(speedKmh = kmh, accelMps2 = 0f, gear = gear, moving = kmh > 0.5f)
                val t = CharacterTarget(kart = KartState(driving = kmh > 0.5f), car = car, journey = journey(Journey(active = true, tripSeq = 5, tripKm = km)))
                sim.step(1f / 60f, t, now, 14f)
                now += 1000L / 60
                each()
            }
        }
    }

    @Test fun tempoChangesOnlyOnBarLinesBySixesAndAtMostTwelve() {
        val d = Drive()
        var last = -1.0
        var changes = 0
        d.run(4f, 10f)
        last = d.sim.tempo
        for (v in listOf(30f, 70f, 110f, 120f, 40f)) d.run(12f, v) {
            val t = d.sim.tempo
            if (t != last && d.sim.boxMode == MusicBoxSim.BoxMode.WIND) {
                changes++
                val inBar = (d.sim.beats / 4.0) - floor(d.sim.beats / 4.0)
                assertTrue("tempo changed mid-bar at ${d.sim.beats}", inBar < 0.05)
                assertTrue("step ${t - last}", abs(t - last) <= 12.0 + 1e-6)
                assertEquals(0.0, t % 6.0, 1e-6)
                last = t
            }
        }
        assertTrue(changes >= 4)
        assertTrue("fast driving reaches 126 BPM", last <= 126.0)
    }

    @Test fun springWindsOneClickPer100mAndStopsAt60() {
        val d = Drive()
        d.run(36f, 100f) // 1 km
        assertTrue("clicks=${d.sim.clicks}", d.sim.clicks in 9..10)
        d.run(400f, 100f)
        assertEquals(60, d.sim.clicks)
    }

    @Test fun parkedBoxPlaysFromTheSpringSlowsAndEndsOnTheTonic() {
        val d = Drive()
        d.run(20f, 90f) // ~0.5 km → 5 clicks
        val wound = d.sim.clicks
        assertTrue(wound >= 4)
        d.run(3f, 0f)
        var plucks = 0
        var lastTineBeforeEnd = -1
        val before = d.sim.amp.copyOf()
        d.run(120f, 0f, GearState.PARK) {
            for (t in d.sim.amp.indices) { if (d.sim.amp[t] > before[t] + 0.05f) { plucks++; lastTineBeforeEnd = t }; before[t] = d.sim.amp[t] }
        }
        assertEquals(MusicBoxSim.BoxMode.SPENT, d.sim.boxMode)
        assertEquals(0, d.sim.clicks)
        assertTrue("it played: $plucks", plucks > 10)
        assertTrue(!d.sim.playing)
        assertEquals("the last note is a tonic", 0, Math.floorMod(lastTineBeforeEnd, 7))
    }

    @Test fun everyPluckHappensWhenItsPinMeetsTheTine() {
        val d = Drive()
        d.run(2f, 60f)
        val before = d.sim.amp.copyOf()
        var checked = 0
        d.run(30f, 60f) {
            val s = d.sim
            for (t in s.amp.indices) {
                if (s.amp[t] > before[t] + 0.05f && s.pluckAt[t] == s.decoTime) {
                    // The pin that just played is at the contact angle (within one substep of rotation).
                    val bar = floor(s.beats / 4.0).toInt()
                    val near = (bar - 1..bar).flatMap { b -> s.pinsOf(b).orEmpty() }.filter { it.tine == t }
                        .minOf { p -> abs(p.bar * 4.0 + p.slot / 4.0 - s.beats) }
                    val angle = near / (4.0 * MusicBoxSim.BARS_PER_REV) * 2 * PI
                    assertTrue("pin is ${angle * 180 / PI}° away", angle < 3.0 * PI / 180)
                    checked++
                }
                before[t] = s.amp[t]
            }
        }
        assertTrue(checked > 20)
    }

    @Test fun stoppingInDriveIsAFermata() {
        val d = Drive()
        d.run(10f, 50f)
        assertTrue(d.sim.playing)
        d.run(3.5f, 0f)
        assertEquals(MusicBoxSim.BoxMode.FERMATA, d.sim.boxMode)
        assertTrue(!d.sim.playing)
        assertEquals(0.0, d.sim.beats - floor(d.sim.beats), 1e-9)
    }

    @Test fun keyChangesOnAComboAtAPhraseStart() {
        val d = Drive()
        d.run(4f, 60f)
        val key = d.sim.key
        d.run(40f, 60f, journey = { it.copy(comboSeq = 1) })
        assertTrue("key ${d.sim.key} from $key", Math.floorMod(d.sim.key - key, 12) == 2)
    }

    @Test fun persistedSpringIsRestored() {
        val a = MusicBoxSim()
        a.restore("clicks=33;counter=4")
        assertEquals(33, a.clicks)
        assertEquals("clicks=33;counter=4", a.persist())
        a.restore("clicks=999")
        assertEquals(60, a.clicks)
    }

    // ---------------------------------------------------------------- upgrade steps (spec 5.3)

    @Test fun combTeethFollowStep() {
        assertEquals(listOf(18, 18, 36, 50, 72), (0..4).map { MusicBoxSim.teethFor(it) })
        val sim = MusicBoxSim()
        for (m in 0..4) {
            sim.model = m
            assertEquals(MusicBoxSim.teethFor(m), sim.combTeeth)
            // Every note keeps its own teeth, in pitch order, inside the comb.
            var last = -1
            for (t in 0 until MusicBoxComposer.TINES) {
                val teeth = (0 until 64).map { sim.toothOf(t, it / 16, it % 16) }.toSet()
                assertTrue("step $m note $t", teeth.all { it in 0 until sim.combTeeth })
                assertEquals("step $m note $t", sim.teethPerNote, teeth.size)
                assertTrue("step $m note $t", teeth.min() > last)
                last = teeth.max()
            }
        }
    }

    @Test fun bellsOnlyAtStepFive() {
        fun strikes(model: Int): Int {
            val sim = MusicBoxSim().also { it.model = model }
            val car = io.vroomi.character.CarSignals(speedKmh = 60f, accelMps2 = 0f, gear = io.vroomi.core.vehicle.model.GearState.DRIVE, moving = true, safetyWarningActive = false)
            for (i in 0 until 60 * 30) {
                val t = io.vroomi.character.CharacterTarget(kart = io.vroomi.character.KartState(driving = true), car = car,
                    journey = io.vroomi.character.Journey(active = true, tripSeq = 1, tripKm = 60f * i / 60f / 3600f))
                sim.step(1f / 60f, t, 1_000_000L + i * 16L, 14f)
            }
            return sim.bellStrikes
        }
        for (m in 0..3) assertEquals("step $m", 0, strikes(m))
        val top = strikes(4)
        assertTrue("bells ring at the top step: $top", top >= 8)
        // Never faster than the flash rule: three bells, each at most once per 0.34 s, in 30 s.
        assertTrue(top <= 3 * 30 * 3)
    }
}
