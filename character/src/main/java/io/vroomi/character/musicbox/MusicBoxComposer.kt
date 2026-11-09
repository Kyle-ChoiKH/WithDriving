package io.vroomi.character.musicbox

import kotlin.math.abs

/**
 * The music box's composer (pure Kotlin, deterministic): 8 hand-written ORIGINAL two-bar motifs, a small table of
 * common functional progressions (common property) and rules that fit the motifs to the chords. Nothing here
 * quotes an existing tune; MusicBoxComposerTest compares the output with famous melodies.
 *
 * Pitch is a scale degree of the comb: tine t (0..17) sounds degree t of the current major key, so 0 is the
 * tonic, 7 the tonic an octave up, 14 two octaves up.
 */
class PinNote(val bar: Int, val slot: Int, val tine: Int, val vel: Float, val kind: Int = MELODY) {
    companion object {
        const val MELODY = 0
        const val BASS = 1
        const val GRACE = 2
    }
}

object MotifBank {
    /** Each motif: triples of (start in eighths 0..15 over two bars, length in eighths, scale-step offset from the chord root). */
    val MOTIFS: List<IntArray> = listOf(
        intArrayOf(0, 2, 0, 2, 1, 2, 3, 1, 3, 4, 2, 4, 6, 2, 2, 8, 2, 3, 10, 1, 2, 11, 1, 1, 12, 4, 2),
        intArrayOf(0, 1, 4, 1, 1, 3, 2, 2, 2, 4, 1, 0, 5, 1, 2, 6, 2, 4, 8, 3, 5, 11, 1, 4, 12, 2, 2, 14, 2, 0),
        intArrayOf(0, 2, 2, 2, 2, 4, 4, 1, 5, 5, 1, 4, 6, 2, 2, 8, 2, 1, 10, 2, 2, 12, 4, 0),
        intArrayOf(0, 1, 0, 1, 1, 4, 2, 2, 3, 4, 1, 2, 5, 1, 3, 6, 2, 4, 8, 1, 6, 9, 1, 5, 10, 2, 4, 12, 2, 2, 14, 2, 4),
        intArrayOf(0, 3, 4, 3, 1, 2, 4, 2, 3, 6, 2, 1, 8, 2, 2, 10, 1, 0, 11, 1, 1, 12, 4, 2),
        intArrayOf(0, 3, 2, 3, 1, 3, 4, 2, 4, 6, 1, 2, 7, 1, 0, 8, 3, 1, 11, 1, 2, 12, 2, 0, 14, 2, -1),
        intArrayOf(0, 1, 2, 1, 1, 3, 2, 1, 2, 3, 1, 1, 4, 2, 2, 6, 2, 4, 8, 2, 5, 10, 2, 4, 12, 2, 2, 14, 2, 3),
        intArrayOf(0, 2, 4, 2, 2, 2, 4, 2, 3, 6, 2, 1, 8, 2, 2, 10, 2, 0, 12, 4, 0),
    )

    /** First halves end on V (half cadence), second halves on V → I (authentic cadence). Roots as scale degrees. */
    val FIRST_HALF = listOf(intArrayOf(0, 5, 3, 4), intArrayOf(0, 3, 1, 4), intArrayOf(5, 3, 1, 4), intArrayOf(0, 2, 3, 4), intArrayOf(3, 0, 1, 4))
    val SECOND_HALF = listOf(intArrayOf(0, 5, 4, 0), intArrayOf(3, 1, 4, 0), intArrayOf(5, 1, 4, 0), intArrayOf(0, 3, 4, 0))
}

class MusicBoxComposer(private val seed: Int) {

    /** The chord root (scale degree 0..6) of [bar]. */
    fun chordOf(bar: Int): Int {
        val phrase = Math.floorDiv(bar, 8); val inPhrase = Math.floorMod(bar, 8)
        val table = if (inPhrase < 4) MotifBank.FIRST_HALF else MotifBank.SECOND_HALF
        val prog = table[pick(phrase, if (inPhrase < 4) 11 else 12, table.size)]
        return prog[inPhrase % 4]
    }

    /** The notes of one bar (16 sixteenth slots), melody and bass. */
    fun bar(bar: Int, energy: Float = 0.5f): List<PinNote> {
        val out = ArrayList<PinNote>(16)
        val phrase = Math.floorDiv(bar, 8); val inPhrase = Math.floorMod(bar, 8)
        val unit = inPhrase / 2
        val root = chordOf(bar)
        val motif = MotifBank.MOTIFS[pick(phrase, 20 + unit, MotifBank.MOTIFS.size)]
        val invert = pick(phrase, 40 + unit, 5) == 0
        val unitRoot = chordOf(bar - (inPhrase % 2))
        val register = 7 + if (pick(phrase, 60 + unit, 3) == 0) 2 else 0
        val half = inPhrase % 2
        val hist = ArrayList<Int>(24)
        melodyTail(bar)?.let { t -> t.forEach { if (it >= 0) hist += it } }
        var prev = if (hist.isEmpty()) -1 else hist.last()
        var lastLeap = 0
        var repeats = 0
        val n = motif.size / 3
        val firstOffset = motif[2]
        // Notes of this bar from the two-bar motif.
        var lastIndex = -1
        for (i in 0 until n) { val s = motif[i * 3]; if (s / 8 == half) lastIndex = i }
        for (i in 0 until n) {
            val start = motif[i * 3]
            if (start / 8 != half) continue
            val off = motif[i * 3 + 2]
            val shaped = if (invert) firstOffset - (off - firstOffset) else off
            var deg = register + unitRoot + shaped
            val slot = (start % 8) * 2
            val strong = slot == 0 || slot == 8
            // Leaps: at most a sixth, and a step back after a leap.
            if (prev >= 0) {
                while (deg - prev > 5) deg -= 7
                while (prev - deg > 5) deg += 7
                if (abs(lastLeap) > 3) deg = prev - Integer.signum(lastLeap)
            }
            deg = deg.coerceIn(MELODY_LOW, MELODY_HIGH)
            // Strong beats land on a chord tone (the nearest one is at most two steps away).
            if (strong) deg = nearestChordTone(deg, root)
            if (i == lastIndex && prev >= 0) {
                when (inPhrase) {
                    3 -> deg = nearestChordTone(prev, 4)                  // half cadence on V
                    7 -> deg = nearestChordTone(prev, 0, rootOnly = true) // authentic cadence on I
                }
            }
            // No note is struck three times in a row: move to the next chord tone (strong) or a step (weak).
            if (prev >= 0 && deg == prev && i != lastIndex) {
                repeats++
                if (repeats >= 2 || strong) {
                    val dir = if (deg >= MELODY_HIGH - 2) -1 else 1
                    deg = if (strong) nextChordTone(deg, root, dir) else (deg + dir).coerceIn(MELODY_LOW, MELODY_HIGH)
                    repeats = 0
                }
            } else repeats = 0
            // No short loops: a note that would make the last six notes repeat with a period of 2 or 3 moves a step.
            if (i != lastIndex && loops(hist, deg)) {
                val dir = if (deg >= MELODY_HIGH - 1) -1 else 1
                deg = if (strong) nextChordTone(deg, root, dir) else (deg + dir).coerceIn(MELODY_LOW, MELODY_HIGH)
            }
            if (prev >= 0) lastLeap = deg - prev
            hist += deg
            out += PinNote(bar, slot, deg, (0.62f + 0.28f * energy).coerceIn(0f, 1f), PinNote.MELODY)
            prev = deg
        }
        // Bass: the root on beat 1, the fifth on beat 3 (low tines).
        out += PinNote(bar, 0, root, 0.5f + 0.15f * energy, PinNote.BASS)
        if (inPhrase != 7) out += PinNote(bar, 8, (root + 4) % 7, 0.42f + 0.12f * energy, PinNote.BASS)
        if (hist.isNotEmpty()) rememberTail(bar, hist)
        return out.distinctBy { it.slot * 32 + it.tine }
    }

    /** The last six melody degrees of each composed bar (memoised; −1 pads). */
    private val tails = HashMap<Int, IntArray>()

    private fun rememberTail(bar: Int, hist: List<Int>) {
        if (tails.size > 256) tails.clear()
        tails[bar] = IntArray(TAIL) { k -> hist.getOrElse(hist.size - TAIL + k) { -1 } }
    }

    private fun loops(hist: List<Int>, next: Int): Boolean {
        val seq = hist.takeLast(6) + next
        if (seq.size < 6) return false
        for (p in 2..3) {
            var same = true
            for (k in p until seq.size) if (seq[k] != seq[k - p]) { same = false; break }
            if (same) return true
        }
        return false
    }

    /** The two cadence bars of a spent spring: V then the tonic chord (arpeggiated, ending on the tonic). */
    fun endingBars(firstBar: Int): List<PinNote> = listOf(
        PinNote(firstBar, 0, 4, 0.5f, PinNote.BASS), PinNote(firstBar, 0, 11, 0.6f), PinNote(firstBar, 4, 13, 0.55f),
        PinNote(firstBar, 8, 1, 0.45f, PinNote.BASS), PinNote(firstBar, 8, 12, 0.55f), PinNote(firstBar, 12, 8, 0.5f),
        PinNote(firstBar + 1, 0, 0, 0.5f, PinNote.BASS), PinNote(firstBar + 1, 0, 7, 0.6f), PinNote(firstBar + 1, 0, 14, 0.6f),
    )

    /** A rising run of three sixteenths into the downbeat of [bar] (a star). */
    fun graceRun(bar: Int, target: Int): List<PinNote> =
        (1..3).map { k -> PinNote(bar - 1, 12 + k, (target - 4 + k).coerceIn(MELODY_LOW, MELODY_HIGH), 0.5f, PinNote.GRACE) }

    /** The last melody degrees of the bar before [bar] (composing earlier bars if needed; null before bar 0). */
    private fun melodyTail(bar: Int): IntArray? {
        if (bar <= 0) return null
        tails[bar - 1]?.let { return it }
        // Compose the previous bars from the start of the previous phrase (deterministic, memoised).
        val from = maxOf(0, (Math.floorDiv(bar - 1, 8) - 1) * 8)
        for (b in from until bar) if (tails[b] == null) this.bar(b)
        return tails[bar - 1]
    }

    private fun nearestChordTone(deg: Int, root: Int, rootOnly: Boolean = false): Int {
        var best = deg; var bestD = Int.MAX_VALUE
        for (d in deg - 4..deg + 4) {
            val rel = Math.floorMod(d - root, 7)
            val ok = if (rootOnly) rel == 0 else rel == 0 || rel == 2 || rel == 4
            if (ok && d in MELODY_LOW..MELODY_HIGH) { val dd = abs(d - deg); if (dd < bestD) { bestD = dd; best = d } }
        }
        return best
    }

    /** The next chord tone above ([dir] 1) or below (−1) [deg], inside the melody range. */
    private fun nextChordTone(deg: Int, root: Int, dir: Int): Int {
        var d = deg + dir
        while (d in MELODY_LOW..MELODY_HIGH) {
            if (Math.floorMod(d - root, 7) in CHORD) return d
            d += dir
        }
        return deg
    }

    private fun pick(phrase: Int, salt: Int, n: Int): Int {
        var x = seed * 73856093 xor phrase * 19349663 xor salt * 83492791
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return Math.floorMod(x, n)
    }

    companion object {
        const val TINES = 18
        const val MELODY_LOW = 5
        const val MELODY_HIGH = 17
        private val CHORD = intArrayOf(0, 2, 4)
        private const val TAIL = 6
        private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        /** Semitones above the tonic of a comb tine. */
        fun semitones(tine: Int): Int = MAJOR[tine % 7] + 12 * (tine / 7)
        /** Keys of a journey: C, D, F, G, B♭ (semitones above C). */
        val KEYS = intArrayOf(0, 2, 5, 7, -2)
        val KEY_NAMES = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
        fun keyName(semis: Int): String = KEY_NAMES[Math.floorMod(semis, 12)]
    }
}
