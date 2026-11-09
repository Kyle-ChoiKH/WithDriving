package io.vroomi.character.musicbox

import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The music box driven by the car (pure Kotlin): an 18-tine comb, a pinned cylinder that shows the real score
 * (bars are composed 4 ahead and rotate into view), a spring barrel wound one click per 100 m and a governor fan.
 *
 * - Driving: the cylinder turns with the musical clock; tempo = 60 + 66 × clamp(v ÷ 110), in 6 BPM steps,
 *   changed only on bar lines by at most ±12 BPM per bar. The spring only winds.
 * - Stopped (not in P): a fermata. The clock finishes its beat and holds.
 * - P: the box plays from its spring at (0.6 + 0.4 × spring) of the last tempo, one click per bar, and ends with
 *   a 2-bar ritardando on the tonic when the spring is empty.
 */
class MusicBoxSim : ThemeSim(), io.vroomi.character.theme.SoundSource {

    enum class BoxMode { STILL, WIND, FERMATA, SPRING, SPENT }

    var boxMode = BoxMode.STILL; private set
    /** Musical position in quarter notes. */
    var beats = 0.0; private set
    /** The position the drawing uses (stands still with reduced motion). */
    var visBeats = 0.0; private set
    var tempo = 72.0; private set
    private var lastDriveTempo = 84.0
    var playing = false; private set
    private var holdAtBeat = false

    // Spring barrel
    var clicks = 0; private set
    private var meters = 0.0
    private var lastTripKm = -1f
    var pawlAt = -100.0; private set
    var arborAngle = 0.0; private set
    var drumAngle = 0.0; private set
    /** The click spring flexes at the blinker's phase. */
    var clickSpringAt = -100.0; private set
    var clickSide = TurnSignal.NONE; private set
    /** Brass counter wheel: one notch per km milestone. */
    var counter = 0; private set
    var counterAt = -100.0; private set

    // Key
    var key = 0; private set
    val keyName: String get() = MusicBoxComposer.keyName(key)
    var keyChangeAt = -100.0; private set
    private var pendingKeyChange = false
    private var lastKeyChangeBar = -1000

    // Cylinder pins: one slot per bar position on the cylinder (8 bars per revolution).
    private val barPins = arrayOfNulls<List<PinNote>>(BARS_PER_REV)
    private val barIndexAt = IntArray(BARS_PER_REV) { Int.MIN_VALUE }
    private var composer = MusicBoxComposer(1)
    private var endBar = -1
    private var sixteenth = -1L

    // Tines
    val amp = FloatArray(MusicBoxComposer.TINES)
    val pluckAt = DoubleArray(MusicBoxComposer.TINES) { -100.0 }
    val glintAt = DoubleArray(MusicBoxComposer.TINES) { -100.0 }
    var damperAt = -100.0; private set

    /** Parked only: a close view of the comb and the pins (tap toggles); 0..1 zoom. */
    var closeView = false
    var zoom = 0f; private set

    // Governor
    var fanAngle = 0.0; private set
    var fanRps = 0.0; private set

    // Loose notes (a star while not playing): time, tine, velocity.
    private val looseT = DoubleArray(8) { -1.0 }
    private val looseTine = IntArray(8)

    // Sound events for the optional audio (midi, velocity, kind).
    private val evMidi = IntArray(EV_SIZE); private val evVel = FloatArray(EV_SIZE); private val evKind = IntArray(EV_SIZE)
    private var evHead = 0; private var evTail = 0

    private var stoppedFor = 0.0
    private var arrivedPending = false
    private var arrivalChipUntil = -1.0
    private var starChipUntil = -1.0
    private var keyChipUntil = -1.0
    private var lastStarBar = -100

    override val allChips: List<String> = listOf("도착", "조 바꿈", "별 장식음", "오르골 연주", "태엽이 다 풀림", "페르마타", "태엽 감는 중", "태엽 가득", "비 오는 날")

    val bar: Int get() = floor(beats / 4.0).toInt()
    /** Cylinder angle (rad): 8 bars per revolution. */
    val cylinderAngle: Double get() = visBeats / (4.0 * BARS_PER_REV) * 2 * PI
    val springFraction: Float get() = clicks / MAX_CLICKS.toFloat()

    fun pinsOf(bar: Int): List<PinNote>? {
        val i = Math.floorMod(bar, BARS_PER_REV)
        return if (barIndexAt[i] == bar) barPins[i] else null
    }

    override fun onStart(nowMs: Long) {
        startJourney()
    }

    override fun onTripStart() { startJourney() }

    private fun startJourney() {
        val seq = input.journey.tripSeq
        composer = MusicBoxComposer(seq * 31 + 7)
        key = MusicBoxComposer.KEYS[Math.floorMod(seq, MusicBoxComposer.KEYS.size)]
        lastTripKm = input.journey.tripKm
        meters = 0.0
        endBar = -1
        val b = bar
        for (k in 0..LOOKAHEAD) write(b + k)
    }

    private fun write(b: Int) {
        val i = Math.floorMod(b, BARS_PER_REV)
        barIndexAt[i] = b
        barPins[i] = composer.bar(b, energy())
    }

    private fun energy(): Float = (0.5f + accel * 0.18f + mode * 0.12f).coerceIn(0.15f, 1f)

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        windSpring()
        val c = car
        val inPark = gear == GearState.PARK
        val rolling = kmh >= 1.5f
        stoppedFor = if (rolling) 0.0 else stoppedFor + dt
        if (arrivedPending && inPark) arrivedPending = false

        val wanted = when {
            freeze > 0.5f -> boxMode
            inPark -> if (boxMode == BoxMode.SPENT) BoxMode.SPENT else if (clicks > 0 || endBar >= 0) BoxMode.SPRING else BoxMode.STILL
            rolling -> BoxMode.WIND
            stoppedFor > 1.2 && boxMode == BoxMode.WIND -> BoxMode.FERMATA
            boxMode == BoxMode.SPRING || boxMode == BoxMode.SPENT -> BoxMode.STILL
            else -> boxMode
        }
        if (wanted != boxMode) enter(wanted)
        if (!inPark && boxMode == BoxMode.SPENT) enter(BoxMode.STILL)

        // The clock.
        if (playing && ddt > 0) {
            val before = beats
            beats += ddt * tempo / 60.0
            if (!reducedMotion) visBeats += ddt * tempo / 60.0
            if (holdAtBeat && floor(beats) > floor(before)) { beats = floor(beats); playing = false; holdAtBeat = false }
            val newBar = floor(beats / 4.0).toInt()
            if (newBar > floor(before / 4.0).toInt()) onBar(newBar)
            playSlots()
            if (endBar >= 0 && beats >= endBar * 4.0 + 1.0) { playing = false; enter(BoxMode.SPENT) }
        }

        // Governor fan: follows the tempo while playing and coasts down after.
        val fanT = if (playing && !reducedMotion) min(2.6, 0.5 + tempo / 60.0 * 0.9) * (if (calm) 0.5 else 1.0) else 0.0
        fanRps += (fanT - fanRps) * min(1.0, ddt / 0.6)
        fanAngle += fanRps * ddt * 2 * PI
        if (boxMode == BoxMode.SPRING && playing) drumAngle += ddt * tempo / 60.0 * 0.05

        if (moving && kmh > 1f) closeView = false
        zoom = approach(zoom, if (closeView) 1f else 0f, (dt / 0.5).toFloat())

        // Tines ring down.
        for (t in amp.indices) if (amp[t] > 0f) amp[t] = (amp[t] * exp(-ddt / tineTau(t))).toFloat().let { if (it < 0.004f) 0f else it }

        // Loose notes.
        for (i in looseT.indices) if (looseT[i] >= 0 && decoTime >= looseT[i]) { pluck(looseTine[i], 0.5f, KIND_TINE); looseT[i] = -1.0 }
    }

    private fun enter(m: BoxMode) {
        when (m) {
            BoxMode.WIND -> {
                playing = true; holdAtBeat = false; endBar = -1
                if (boxMode == BoxMode.STILL || boxMode == BoxMode.SPENT) tempo = quantize(targetTempo())
            }
            BoxMode.FERMATA -> holdAtBeat = true
            BoxMode.SPRING -> {
                playing = true; holdAtBeat = false
                tempo = (0.6 + 0.4 * springFraction) * lastDriveTempo
            }
            BoxMode.SPENT, BoxMode.STILL -> { playing = false; holdAtBeat = false }
        }
        boxMode = m
    }

    private fun targetTempo(): Double = 60.0 + 66.0 * (kmh / 110f).coerceIn(0f, 1f)
    private fun quantize(t: Double): Double = (t / 6.0).roundToInt() * 6.0

    private fun onBar(b: Int) {
        when (boxMode) {
            BoxMode.WIND -> {
                val target = quantize(targetTempo())
                tempo += (target - tempo).coerceIn(-12.0, 12.0)
                lastDriveTempo = tempo
            }
            BoxMode.SPRING -> {
                if (endBar < 0) {
                    if (clicks > 0) { clicks--; pawlAt = decoTime; event(0, 0.35f, KIND_PAWL) }
                    tempo = (0.6 + 0.4 * springFraction) * lastDriveTempo
                    if (clicks == 0) {
                        endBar = b + 2
                        val ending = composer.endingBars(b + 1)
                        for (k in 1..2) {
                            val i = Math.floorMod(b + k, BARS_PER_REV)
                            barIndexAt[i] = b + k
                            barPins[i] = ending.filter { it.bar == b + k }
                        }
                    }
                } else {
                    tempo *= 0.82 // the last bars slow down (ritardando)
                }
            }
            else -> Unit
        }
        if (pendingKeyChange && Math.floorMod(b, 8) == 0 && b - lastKeyChangeBar >= 16 && endBar < 0) {
            pendingKeyChange = false; lastKeyChangeBar = b
            key += 2; if (key > 7) key -= 12
            keyChangeAt = decoTime; keyChipUntil = time + 5.0
        }
        if (endBar < 0 || b + LOOKAHEAD < endBar) write(b + LOOKAHEAD)
    }

    private fun playSlots() {
        val six = floor(beats * 4.0).toLong()
        if (sixteenth < 0) sixteenth = six - 1
        while (sixteenth < six) {
            sixteenth++
            val b = Math.floorDiv(sixteenth, 16L).toInt()
            val slot = Math.floorMod(sixteenth, 16L).toInt()
            val pins = pinsOf(b) ?: continue
            for (p in pins) if (p.slot == slot) pluck(p.tine, p.vel, KIND_TINE)
        }
    }

    private fun pluck(tine: Int, vel: Float, kind: Int) {
        amp[tine] = max(amp[tine], vel)
        pluckAt[tine] = decoTime
        if (!calm && flash.allow(REGION_TINES + tine / 3, time)) glintAt[tine] = decoTime
        event(60 + key + MusicBoxComposer.semitones(tine), vel, kind)
    }

    private fun event(midi: Int, vel: Float, kind: Int) {
        val i = evHead % EV_SIZE
        evMidi[i] = midi; evVel[i] = vel; evKind[i] = kind
        evHead++
        if (evHead - evTail > EV_SIZE) evTail = evHead - EV_SIZE
    }

    /** Sound events since the last call: midi note, velocity 0..1 and kind ([KIND_TINE], [KIND_PAWL], [KIND_TICK]). */
    inline fun drainSound(out: (midi: Int, vel: Float, kind: Int) -> Unit) {
        while (true) {
            val e = takeEvent()
            if (e < 0) return
            out(eventMidi(e), eventVel(e), eventKind(e))
        }
    }
    override fun takeEvent(): Int = if (evTail >= evHead) -1 else (evTail++ % EV_SIZE)
    override fun eventMidi(i: Int) = evMidi[i]
    override fun eventVel(i: Int) = evVel[i]
    override fun eventKind(i: Int) = evKind[i]

    private fun windSpring() {
        val km = input.journey.tripKm
        if (lastTripKm < 0f || km < lastTripKm) { lastTripKm = km; return }
        meters += (km - lastTripKm) * 1000.0
        lastTripKm = km
        while (meters >= METERS_PER_CLICK) {
            meters -= METERS_PER_CLICK
            if (clicks < MAX_CLICKS) {
                clicks++; pawlAt = decoTime; arborAngle += 2 * PI / RATCHET_TEETH
                event(0, 0.25f, KIND_PAWL)
            }
        }
    }

    override fun onStar() {
        starChipUntil = time + 4.0
        if (playing && boxMode == BoxMode.WIND) {
            val slot = Math.floorMod(floor(beats * 4.0).toLong(), 16L).toInt()
            val target = bar + if (slot < 11) 1 else 2
            if (target == lastStarBar) return
            lastStarBar = target
            val first = pinsOf(target)?.firstOrNull { it.slot == 0 && it.kind == PinNote.MELODY }?.tine ?: 9
            val run = composer.graceRun(target, first)
            val i = Math.floorMod(target - 1, BARS_PER_REV)
            if (barIndexAt[i] == target - 1) barPins[i] = (barPins[i] ?: emptyList()).filter { it.slot < 13 } + run
        } else {
            for (k in 0 until 3) { looseT[k] = decoTime + 0.12 * k; looseTine[k] = 9 + 2 * k }
        }
    }

    override fun onCombo() { pendingKeyChange = true }

    override fun onHarsh() {
        damperAt = decoTime
        for (t in amp.indices) amp[t] *= 0.2f
    }

    override fun onMilestone(km: Int) { counter++; counterAt = decoTime; pawlAt = decoTime; event(0, 0.4f, KIND_PAWL) }

    override fun onArrival() { arrivedPending = true; arrivalChipUntil = time + 6.0 }

    override fun onBlink(side: TurnSignal) { clickSpringAt = decoTime; clickSide = side; event(0, 0.3f, KIND_TICK) }

    override fun chooseChip(): String? = when {
        time < arrivalChipUntil -> "도착"
        time < keyChipUntil -> "조 바꿈"
        time < starChipUntil -> "별 장식음"
        boxMode == BoxMode.SPRING -> "오르골 연주"
        boxMode == BoxMode.SPENT -> "태엽이 다 풀림"
        boxMode == BoxMode.FERMATA -> "페르마타"
        boxMode == BoxMode.WIND && rain > 0.5f -> "비 오는 날"
        boxMode == BoxMode.WIND -> if (clicks >= MAX_CLICKS) "태엽 가득" else "태엽 감는 중"
        else -> null
    }

    override fun persist(): String = "clicks=$clicks;counter=$counter"

    override fun restore(state: String) {
        state.split(';').forEach { kv ->
            val (k, v) = kv.split('=').let { if (it.size == 2) it[0] to it[1] else return@forEach }
            when (k) {
                "clicks" -> v.toIntOrNull()?.let { clicks = it.coerceIn(0, MAX_CLICKS) }
                "counter" -> v.toIntOrNull()?.let { counter = it.coerceAtLeast(0) }
            }
        }
    }

    /** Ring time of a tine in seconds (lower tines ring longer). */
    fun tineTau(t: Int): Double = 1.5 * (1.0 / 2.0.pow(MusicBoxComposer.semitones(t) / 12.0)).pow(0.5)

    companion object {
        const val BARS_PER_REV = 8
        const val LOOKAHEAD = 4
        const val MAX_CLICKS = 60
        const val METERS_PER_CLICK = 100.0
        const val RATCHET_TEETH = 20
        const val EV_SIZE = 64
        const val KIND_TINE = 0
        const val KIND_PAWL = 1
        const val KIND_TICK = 2
        const val REGION_TINES = 8
    }
}
