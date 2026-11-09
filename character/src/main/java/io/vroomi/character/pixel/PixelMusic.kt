package io.vroomi.character.pixel

import io.vroomi.character.Biome
import io.vroomi.character.theme.SoundEvents

/**
 * Chiptune sound data (pure Kotlin): original 8-bar loops and sound effects, written as data. Event kinds encode
 * the channel and length: kind = channel | (length in 10 ms units shl 2) | (duty shl 12).
 * Channels: 0 and 1 pulse, 2 triangle, 3 noise. No existing melody is used, and no sound effect contains the
 * B5 → E6 two-note leap of a famous coin sound.
 */
object PixelSound {
    const val PULSE_A = 0
    const val PULSE_B = 1
    const val TRIANGLE = 2
    const val NOISE = 3
    /** Duty codes: 0 = 12.5 %, 1 = 25 %, 2 = 50 %. */
    fun kind(channel: Int, lengthMs: Int, duty: Int = 1): Int = channel or ((lengthMs / 10).coerceIn(1, 1000) shl 2) or (duty shl 12)
    fun channel(kind: Int): Int = kind and 3
    fun lengthMs(kind: Int): Int = ((kind shr 2) and 1023) * 10
    fun duty(kind: Int): Int = (kind shr 12) and 3

    /** (midi, start ms, length ms, channel) per effect. Star: a rising C-major arpeggio, 30 ms each. */
    val STAR = intArrayOf(84, 0, 30, PULSE_B, 88, 30, 30, PULSE_B, 91, 60, 30, PULSE_B, 96, 90, 60, PULSE_B)
    /** Hop: one soft triangle note (never a rising sweep). */
    val HOP = intArrayOf(67, 0, 90, TRIANGLE)
    /** Checkpoint: an original four-note figure. */
    val CHECKPOINT = intArrayOf(72, 0, 90, PULSE_A, 74, 100, 90, PULSE_A, 79, 200, 90, PULSE_A, 77, 300, 220, PULSE_A)
    /** Arrival: a 2 s cadence (V → I), lead over a triangle bass. */
    val ARRIVAL = intArrayOf(
        74, 0, 380, PULSE_A, 55, 0, 380, TRIANGLE,
        71, 400, 380, PULSE_A, 55, 400, 380, TRIANGLE,
        72, 800, 1150, PULSE_A, 48, 800, 1150, TRIANGLE, 76, 800, 1150, PULSE_B,
    )
    val ALL_SFX: List<IntArray> = listOf(STAR, HOP, CHECKPOINT, ARRIVAL)
}

/** The generative loop: 8 bars, tempo by speed bucket (changes only on a bar line), key by biome. */
class PixelMusic {
    var biome = Biome.CITY
    var tempo = 80.0; private set
    var bar = 0; private set
    private var step = 0          // eighth notes within the bar (0..7)
    private var acc = 0.0
    private var seed = 1
    // Effects in flight: up to 16 scheduled notes.
    private val sfxAt = DoubleArray(16) { -1.0 }
    private val sfxMidi = IntArray(16); private val sfxLen = IntArray(16); private val sfxCh = IntArray(16)
    private var clock = 0.0

    fun reset(tripSeq: Int) { seed = tripSeq * 7 + 3; bar = 0; step = 0; acc = 0.0 }

    /** Tempo bucket: stopped 80, up to 50 km/h 100, faster 120 BPM. */
    fun bucket(kmh: Float, moving: Boolean): Double = when { !moving || kmh < 1.5f -> 80.0; kmh <= 50f -> 100.0; else -> 120.0 }

    fun step(dt: Double, kmh: Float, moving: Boolean, out: SoundEvents) {
        clock += dt
        // Scheduled effect notes.
        for (i in sfxAt.indices) if (sfxAt[i] >= 0 && clock >= sfxAt[i]) { out.push(sfxMidi[i], 0.6f, PixelSound.kind(sfxCh[i], sfxLen[i])); sfxAt[i] = -1.0 }
        acc += dt * tempo / 60.0 * 2.0
        while (acc >= 1.0) {
            acc -= 1.0
            playStep(moving, out)
            step++
            if (step == 8) { step = 0; bar = (bar + 1) % 8; tempo = bucket(kmh, moving) }
        }
    }

    /** Queues an effect (from the mechanics). */
    fun sfx(data: IntArray) {
        var k = 0
        while (k + 3 < data.size) {
            val slot = sfxAt.indexOfFirst { it < 0 }
            if (slot < 0) return
            sfxAt[slot] = clock + data[k + 1] / 1000.0; sfxMidi[slot] = data[k]; sfxLen[slot] = data[k + 2]; sfxCh[slot] = data[k + 3]
            k += 4
        }
    }

    private fun playStep(moving: Boolean, out: SoundEvents) {
        val root = KEY[biome.ordinal]
        val minor = biome == Biome.MOUNTAIN
        val chord = (if (minor) PROG_MINOR else PROG_MAJOR)[bar]
        val stepMs = (60000.0 / tempo / 2.0).toInt()
        // Bass on every quarter.
        if (step % 2 == 0) out.push(36 + root + scale(chord, minor), 0.5f, PixelSound.kind(PixelSound.TRIANGLE, stepMs * 2 - 20))
        // Lead from the phrase data ('-' rest, digits = scale steps above the chord root).
        val phrase = PHRASES[(bar / 4 + seed) % PHRASES.size][bar % 4]
        val ch = phrase[step]
        if (ch != '-') {
            val deg = chord + (ch - '0')
            out.push(60 + root + scale(deg, minor), 0.45f, PixelSound.kind(PixelSound.PULSE_A, stepMs - 15, 1))
        }
        // Soft noise hat on the off-beats while driving.
        if (moving && step % 2 == 1) out.push(90, 0.18f, PixelSound.kind(PixelSound.NOISE, 30))
    }

    private fun scale(deg: Int, minor: Boolean): Int {
        val s = if (minor) MINOR else MAJOR
        val o = Math.floorDiv(deg, 7); val d = Math.floorMod(deg, 7)
        return s[d] + 12 * o
    }

    companion object {
        /** Key roots (semitones above C) by biome: CITY C, PARK F, FIELD G, SEASIDE D, MOUNTAIN A minor. */
        val KEY = intArrayOf(0, 5, 7, 2, -3)
        val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        /** Chord roots (scale degrees) for the 8 bars. */
        val PROG_MAJOR = intArrayOf(0, 4, 5, 3, 0, 4, 3, 4)
        val PROG_MINOR = intArrayOf(0, 5, 2, 6, 0, 5, 6, 4)
        /** Original phrases: 2 × 4 bars of eighth notes. */
        val PHRASES = arrayOf(
            arrayOf("02-4-2-1", "4-2-0-2-", "0-24-5-4", "2-1-0---"),
            arrayOf("4-5-4-2-", "1-2-4---", "5-4-2-4-", "2-1-2-0-"),
        )

        /** The lead notes (midi) of a whole loop, for the originality test. */
        fun loopMelody(biome: Biome, phraseSet: Int): List<Int> {
            val m = PixelMusic(); m.biome = biome
            val minor = biome == Biome.MOUNTAIN
            val out = ArrayList<Int>()
            for (bar in 0 until 8) {
                val chord = (if (minor) PROG_MINOR else PROG_MAJOR)[bar]
                val phrase = PHRASES[(bar / 4 + phraseSet) % PHRASES.size][bar % 4]
                for (ch in phrase) if (ch != '-') out += 60 + KEY[biome.ordinal] + m.scale(chord + (ch - '0'), minor)
            }
            return out
        }
    }
}
