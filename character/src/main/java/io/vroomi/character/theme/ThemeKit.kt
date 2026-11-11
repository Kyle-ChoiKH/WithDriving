package io.vroomi.character.theme

import io.vroomi.character.Biome
import io.vroomi.character.CarSignals
import io.vroomi.character.CharacterTarget
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.floor
import kotlin.math.min

/**
 * The shared kit of every procedural theme (pure Kotlin, no Compose): a fixed-step clock, the safety
 * freeze, the status chip holder, the flash limiter and the common reading of the car. A theme's own
 * simulation extends [ThemeSim]. The car is only ever READ: nothing here can reach a vehicle provider.
 */

/**
 * At most 3 pulses per second per screen region (the general flash threshold). Every glint, blink and pulse
 * asks [allow] first and is skipped when it would come too soon.
 */
class FlashGuard(regions: Int = REGIONS) {
    private val last = DoubleArray(regions) { -100.0 }
    var granted = 0; private set
    var denied = 0; private set
    /** Tests: sees every granted pulse (region, time). */
    var recorder: ((Int, Double) -> Unit)? = null

    fun allow(region: Int, time: Double): Boolean {
        val r = Math.floorMod(region, last.size)
        if (time - last[r] < MIN_GAP_S) { denied++; return false }
        last[r] = time
        granted++
        recorder?.invoke(r, time)
        return true
    }

    companion object {
        const val REGIONS = 16
        /** 0.34 s apart: never 4 pulses in any one-second window. */
        const val MIN_GAP_S = 0.34
    }
}

/** A status chip is held at least [holdS] (unless a safety warning clears it at once). */
class ChipHolder(private val holdS: Double = HOLD_S) {
    var current: String? = null; private set
    private var since = -100.0

    fun update(wanted: String?, time: Double, safety: Boolean): String? {
        if (safety) { current = null; since = -100.0; return null }
        if (wanted != current && (current == null || time - since >= holdS)) { current = wanted; since = time }
        return current
    }

    companion object { const val HOLD_S = 4.0 }
}

/** Runs a body in fixed substeps (1/120 s by default) for any frame dt (clamped to 0.1 s). */
class FixedStepper(val stepS: Double = 1.0 / 120.0) {
    private var acc = 0.0
    inline fun advance(frameDt: Float, body: (Double) -> Unit) {
        accumulate(frameDt)
        while (take()) body(stepS)
    }
    fun accumulate(frameDt: Float) { acc = min(acc + frameDt.coerceIn(0f, MAX_FRAME_DT).toDouble(), MAX_FRAME_DT * 1.5) }
    fun take(): Boolean { if (acc + 1e-9 < stepS) return false; acc -= stepS; return true }

    companion object { const val MAX_FRAME_DT = 0.1f }
}

/** The 0..1 safety freeze: fast in (0.35 s), slow out (1.2 s). The painter dims by 30 % × [value]. */
class SafetyFreeze {
    var value = 0f; private set
    fun step(dt: Double, safety: Boolean) {
        value = ThemeSim.approach(value, if (safety) 1f else 0f, (dt / if (safety) 0.35 else 1.2).toFloat())
    }
}

/** The base of every procedural theme simulation. Subclasses implement [advance] and [chooseChip]. */
abstract class ThemeSim {
    val flash = FlashGuard()
    private val chips = ChipHolder()
    private val stepper = FixedStepper()
    private val safetyFreeze = SafetyFreeze()

    /** Real seconds since start. */
    var time = 0.0; private set
    /** Decoration seconds (stand still during a safety warning). */
    var decoTime = 0.0; private set
    /** 0 normal .. 1 frozen by a safety warning. */
    val freeze: Float get() = safetyFreeze.value

    var chip: String? = null; private set
    var input: CharacterTarget = CharacterTarget(); private set
    val car: CarSignals get() = input.car
    var calm = false; private set
    var reducedMotion = false; private set
    var hourOfDay = 14f; private set
    var season = 1; private set

    // ---- common smoothed readings (0..1 unless noted)
    /** km/h, 0 when unknown ([speedKnown] tells). Smoothed (τ 0.25 s). */
    var kmh = 0f; private set
    var speedKnown = false; private set
    /** m/s², smoothed (τ 0.3 s); 0 when unknown. */
    var accel = 0f; private set
    var moving = false; private set
    var gear = GearState.UNKNOWN; private set
    var night = 0f; private set
    var rain = 0f; private set
    /** Mist on glass: builds in rain, clears from the bottom while defrosting ([clear] 0..1). */
    var fog = 0f; private set
    var clear = 0f; private set
    /** Fan level ÷ max (0 when off or unknown). */
    var fan = 0f; private set
    var ac = 0f; private set
    /** −1 cool (18 °C or below) .. 0 neutral .. +1 warm (26 °C or above); held when unknown. */
    var warmth = 0f; private set
    /** −1 ECO .. 0 NORMAL .. 1 SPORT. */
    var mode = 0f; private set
    var cruise = 0f; private set
    var charging = 0f; private set
    /** Battery 0..1, or −1 unknown. */
    var battery = -1f; private set
    /** Blinker lamps (soft on/off at the real 1.5 Hz phase). */
    var blinkL = 0f; private set
    var blinkR = 0f; private set
    var turn = TurnSignal.NONE; private set
    var biome = Biome.CITY; private set

    // ---- one-shot event times (decoTime) for painters
    var harshAt = -100.0; private set
    var starAt = -100.0; private set
    var comboAt = -100.0; private set
    var milestoneAt = -100.0; private set
    var arrivalAt = -100.0; private set
    var stars = 0; private set

    private var initialized = false
    private var seenStar = 0
    private var seenCombo = 0
    private var seenHarsh = 0
    private var seenArrival = 0
    private var seenKm = 0
    private var seenTrip = 0
    private var blinkPhase = 0.0
    private var blinkSide = TurnSignal.NONE
    private var lastBlinkOn = false

    /** Every chip text this theme can show (the honesty test reads it). */
    abstract val allChips: List<String>

    /** [season] 0 spring, 1 summer, 2 autumn, 3 winter (from the device date). */
    fun step(frameDt: Float, target: CharacterTarget, nowMs: Long, hourOfDay: Float, calm: Boolean = false, reducedMotion: Boolean = false, season: Int = 1) {
        input = target
        this.calm = calm; this.reducedMotion = reducedMotion; this.hourOfDay = hourOfDay; this.season = season
        if (!initialized) {
            initialized = true
            val c = target.car; val j = target.journey
            seenStar = j.starSeq; seenCombo = j.comboSeq; seenHarsh = c.harshBrakeSeq; seenArrival = c.arrivalSeq
            seenKm = j.milestoneKm; seenTrip = j.tripSeq
            night = if (isNight(c, hourOfDay)) 1f else 0f
            warmth = warmthOf(c.setTempC) ?: 0f
            kmh = c.speedKmh ?: 0f
            biome = j.biome
            stars = j.stars
            onStart(nowMs)
        }
        stepper.advance(frameDt) { dt -> substep(dt, nowMs) }
        val safety = car.safetyWarningActive == true
        chip = chips.update(if (safety) null else chooseChip(), time, safety)
    }

    private fun substep(dt: Double, nowMs: Long) {
        val c = car; val j = input.journey
        time += dt
        safetyFreeze.step(dt, c.safetyWarningActive == true)
        val ddt = dt * (1f - freeze)
        decoTime += ddt

        speedKnown = c.speedKmh != null
        val v = c.speedKmh ?: 0f
        kmh += ((v - kmh) * min(1.0, dt / 0.25)).toFloat()
        accel += (((c.accelMps2 ?: 0f) - accel) * min(1.0, dt / 0.3)).toFloat()
        moving = input.kart.driving || c.moving
        gear = c.gear
        night = approach(night, if (isNight(c, hourOfDay)) 1f else 0f, (dt / 1.5).toFloat())
        rain = approach(rain, if (c.wiperActive == true) 1f else 0f, (ddt / 1.5).toFloat())
        if (c.defrostOn == true) {
            clear = approach(clear, 1f, (dt / DEFROST_S).toFloat())
            if (clear >= 1f) fog = approach(fog, 0f, (dt / 0.3).toFloat())
        } else {
            val humid = rain > 0.5f || ac > 0.5f
            fog = approach(fog, if (humid) 0.7f else 0f, (dt / if (humid) 14.0 else 6.0).toFloat())
            if (fog < 0.02f) clear = 0f
        }
        val fanMax = (c.fanMax ?: 8).coerceAtLeast(1)
        fan = approach(fan, ((c.fanLevel ?: 0).toFloat() / fanMax).coerceIn(0f, 1f), (dt / 0.8).toFloat())
        ac = approach(ac, if (c.acOn == true || c.maxAcOn == true) 1f else 0f, (dt / 0.6).toFloat())
        warmthOf(c.setTempC)?.let { warmth = approach(warmth, it, (dt / 2.0).toFloat()) }
        val modeT = when (c.drivingMode) { DriveModeState.ECO -> -1f; DriveModeState.SPORT -> 1f; else -> 0f }
        mode = approach(mode, modeT, (dt / 1.5).toFloat())
        cruise = approach(cruise, if (c.cruiseOn == true) 1f else 0f, (dt / 0.6).toFloat())
        charging = approach(charging, if (c.evCharging == true) 1f else 0f, (dt / 1.0).toFloat())
        val pct = c.evBatteryPct
        battery = if (pct == null) -1f else if (battery < 0f) pct / 100f else approach(battery, pct / 100f, (dt * 0.2).toFloat())

        // Blinker: the car's 1.5 Hz rhythm, soft lamps, one flash-guarded pulse per "on" edge.
        val t = c.turnSignal ?: TurnSignal.NONE
        if (t != blinkSide) { blinkSide = t; blinkPhase = 0.0 }
        turn = t
        if (t != TurnSignal.NONE) blinkPhase += ddt * BLINK_HZ
        val on = t != TurnSignal.NONE && (blinkPhase % 1.0) < 0.5
        if (on && !lastBlinkOn && flash.allow(REGION_BLINK, time)) onBlink(t)
        lastBlinkOn = on
        blinkL = approach(blinkL, if (on && (t == TurnSignal.LEFT || t == TurnSignal.HAZARD)) 1f else 0f, (dt / 0.08).toFloat())
        blinkR = approach(blinkR, if (on && (t == TurnSignal.RIGHT || t == TurnSignal.HAZARD)) 1f else 0f, (dt / 0.08).toFloat())

        // Journey and events (each fires once).
        if (j.tripSeq != seenTrip) { seenTrip = j.tripSeq; onTripStart() }
        if (c.harshBrakeSeq != seenHarsh) { seenHarsh = c.harshBrakeSeq; harshAt = decoTime; onHarsh() }
        if (c.arrivalSeq != seenArrival) { seenArrival = c.arrivalSeq; arrivalAt = decoTime; onArrival() }
        if (j.starSeq != seenStar) { val more = j.starSeq > seenStar; seenStar = j.starSeq; if (more) { starAt = decoTime; onStar() } }
        if (j.comboSeq != seenCombo) { val more = j.comboSeq > seenCombo; seenCombo = j.comboSeq; if (more) { comboAt = decoTime; onCombo() } }
        if (j.milestoneKm != seenKm) { val more = j.milestoneKm > seenKm; seenKm = j.milestoneKm; if (more) { milestoneAt = decoTime; onMilestone(j.milestoneKm) } }
        if (j.biome != biome) { biome = j.biome; onBiome(j.biome) }
        stars = j.stars

        advance(dt, ddt, nowMs)
    }

    /** One fixed substep: [dt] real seconds, [ddt] decoration seconds (0 while frozen). */
    protected abstract fun advance(dt: Double, ddt: Double, nowMs: Long)
    /** The wanted chip (the holder keeps each at least 4 s). */
    protected abstract fun chooseChip(): String?

    protected open fun onStart(nowMs: Long) {}
    protected open fun onTripStart() {}
    protected open fun onHarsh() {}
    protected open fun onArrival() {}
    protected open fun onStar() {}
    protected open fun onCombo() {}
    protected open fun onMilestone(km: Int) {}
    protected open fun onBiome(b: Biome) {}
    protected open fun onBlink(side: TurnSignal) {}

    /** Optional state kept across app restarts (a short text, no personal data). */
    open fun persist(): String? = null
    open fun restore(state: String) {}

    /** Seconds since a one-shot [at] in decoration time (large when it never happened). */
    fun since(at: Double): Double = decoTime - at

    val parked: Boolean get() = gear == GearState.PARK || (!moving && kmh < 1f)
    val stopped: Boolean get() = kmh < 1.5f

    companion object {
        const val BLINK_HZ = 1.5
        const val DEFROST_S = 2.5
        const val REGION_BLINK = 0
        const val REGION_STAR = 1
        const val REGION_COMBO = 2
        const val REGION_MILESTONE = 3
        const val REGION_SCENE = 4
        const val REGION_SCENE2 = 5
        const val REGION_SCENE3 = 6

        fun isNight(c: CarSignals, hour: Float): Boolean = c.headlightsOn == true || hour < 6.5f || hour >= 18.5f

        fun warmthOf(setTempC: Float?): Float? = setTempC?.let { ((it - 22f) / 4f).coerceIn(-1f, 1f) }

        fun approach(v: Float, target: Float, step: Float): Float =
            if (v < target) minOf(target, v + step) else maxOf(target, v - step)

        fun approach(v: Double, target: Double, step: Double): Double =
            if (v < target) minOf(target, v + step) else maxOf(target, v - step)

        fun smooth01(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
        fun frac(x: Double): Double = x - floor(x)

        /** Words a chip or on-screen label must never contain (the honesty rule). */
        val FORBIDDEN_WORDS = listOf("점검", "경고", "고장", "℃", "°C", "기압", "압력", "kPa", "bar")
    }
}

/** A theme that makes optional sound: events since the last drain (midi note, velocity 0..1, kind). */
interface SoundSource {
    /** Index of the next event, or −1 when none is left. */
    fun takeEvent(): Int
    fun eventMidi(i: Int): Int
    fun eventVel(i: Int): Float
    /** 0 tone, 1 soft tick (ratchet, governor click), 2 blinker tick. */
    fun eventKind(i: Int): Int
}

/** A small ring of sound events (no allocation). */
class SoundEvents(private val size: Int = 64) : SoundSource {
    private val midi = IntArray(size); private val vel = FloatArray(size); private val kind = IntArray(size)
    private var head = 0; private var tail = 0
    fun push(m: Int, v: Float, k: Int) {
        val i = head % size
        midi[i] = m; vel[i] = v; kind[i] = k
        head++
        if (head - tail > size) tail = head - size
    }
    override fun takeEvent(): Int = if (tail >= head) -1 else (tail++ % size)
    override fun eventMidi(i: Int) = midi[i]
    override fun eventVel(i: Int) = vel[i]
    override fun eventKind(i: Int) = kind[i]
}
