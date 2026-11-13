package io.vroomi.character.pixel

import io.vroomi.character.Biome
import io.vroomi.character.Mood
import io.vroomi.character.pixel.PixelModels.HERO_X
import io.vroomi.character.pixel.PixelModels.HOP_S
import io.vroomi.character.pixel.PixelModels.SHARD_LEAD_S
import io.vroomi.character.pixel.sprites.PilotSprites.Pose
import io.vroomi.character.theme.SoundEvents
import io.vroomi.character.theme.SoundSource
import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pixel World (pure Kotlin): an original 8-bit side-scroller that the car plays by driving. Speed scrolls the
 * world (2.4 px/s per km/h, capped at 120 km/h), a helmeted pilot drives a boxy rover buggy, and every car signal
 * becomes a pixel event. There are no enemies, no score, no lives, no timer and no game over; speed is never rewarded.
 */
class PixelMechanics(private val sound: SoundEvents = SoundEvents()) : ThemeSim(), SoundSource by sound {

    val world = PixelWorld()
    val music = PixelMusic()

    /** Camera in layer-1.0 px (world x of the screen's left edge). */
    var camera = 0.0; private set
    /** Signed world speed (px/s). */
    var speed = 0f; private set
    var palette = PixelPalette.DAY; private set
    /** Animation clock (decoration seconds, standing still with reduced motion). */
    var anim = 0.0; private set
    var wheel = 0.0; private set

    // Hero.
    var hopStart = -100.0; private set
    val hero = PixelHero()
    var heroOffsetX = 0f; private set
    var bob = 0; private set

    // Gem shards: world x (or −1 floating), state 0 none, 1 flying, 2 floating down; when to catch.
    val shardX = DoubleArray(SHARDS); val shardCatch = DoubleArray(SHARDS) { -1.0 }; val shardState = IntArray(SHARDS)
    val shardSpawn = DoubleArray(SHARDS)
    var sparkleAt = -100.0; private set
    /** The gem row: up to 12 gems light left to right, then a fresh row starts. */
    var gems = 0; private set
    var gemRow = 0; private set

    // Particles (puffs, snow, dust): fixed pool.
    val partX = FloatArray(PARTS); val partY = FloatArray(PARTS); val partAge = FloatArray(PARTS) { -1f }; val partKind = IntArray(PARTS)
    private var puffTimer = 0.0
    private var snowTimer = 0.0

    // World decals and props (world x in layer-1.0 px).
    val skidX = DoubleArray(4) { -1e9 }
    private var skidN = 0
    val signX = DoubleArray(4) { -1e9 }; val signSide = IntArray(4)
    private var signN = 0
    val archX = DoubleArray(4) { -1e9 }; val archKm = IntArray(4)
    private var archN = 0
    var shakeAt = -100.0; private set
    var cheerUntil = -100.0; private set
    var garageX = -1f; private set
    var arrival = 0.0; private set
    private var arrived = false
    var pinwheel = 0.0; private set
    var flag = 0.0; private set
    var shimmer = 0.0; private set
    var tapAt = -100.0; private set
    /** Charging in P: the post and cable show even when the battery level is unknown. */
    var chargePost = false; private set
    /** Filled segments 0..5, or -1 when the level is unknown (or not charging). */
    var battery5 = -1; private set
    private var lastTurn = TurnSignal.NONE
    private var lastHopSound = -100.0

    private var starChipUntil = -1.0
    private var archChipUntil = -1.0
    private var arrivalChipUntil = -1.0

    override val allChips: List<String> = listOf("도착", "보석 조각!", "체크포인트", "충전 중", "비 오는 길", "밤길 운전", "후진", "도트 월드", "쉬는 중")

    override fun onStart(nowMs: Long) = startTrip()
    override fun onTripStart() = startTrip()

    private fun startTrip() {
        world.reset(input.journey.tripSeq * 131 + 17, input.journey.biome)
        music.reset(input.journey.tripSeq)
        arrived = false; arrival = 0.0; garageX = -1f; heroOffsetX = 0f
    }

    override fun onBiome(b: Biome) { world.biome = b; music.biome = b }

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        val c = car
        val parked = gear == GearState.PARK
        val reverse = gear == GearState.REVERSE
        // Arrival: roll into the garage, step out, wave, the door closes. Driving again resets it.
        if (arrived && moving && kmh > 3f) { arrived = false; arrival = 0.0; heroOffsetX = 0f }
        speed = if (arrived && parked) 0f else PixelModels.worldSpeed(if (speedKnown) kmh else null, reverse)
        val still = reducedMotion
        if (!still) { camera += speed * ddt; anim += ddt }
        wheel += speed * ddt / 6.0
        world.biome = biome
        for (layer in 1..4) world.generateVisible(layer, (camera * PixelModels.PARALLAX[layer]).toFloat())

        palette = when {
            night > 0.5f -> PixelPalette.NIGHT
            rain > 0.5f -> PixelPalette.RAIN
            mode < -0.5f -> PixelPalette.MEADOW
            mode > 0.5f -> PixelPalette.SUNSET
            else -> PixelPalette.DAY
        }

        // Idle bob (1 px at 1 Hz) in P and N.
        bob = if ((parked || gear == GearState.NEUTRAL) && !still && !arrived) (if (sin(anim * 2 * Math.PI) > 0) 1 else 0) else 0
        // Music in the car: the pilot nods on the beat (one pixel), parked or driving.
        if (listening > 0.5f && !still && !arrived && beat > 0.5f) bob = 1

        if (hopStart > lastHopSound && decoTime >= hopStart) { lastHopSound = hopStart; music.sfx(PixelSound.HOP) }
        // Gem shards: caught at the hop's apex (driving) or after floating down (parked).
        for (i in 0 until SHARDS) {
            if (shardState[i] == 0) continue
            if (decoTime >= shardCatch[i]) {
                shardState[i] = 0
                addGem()
                if (flash.allow(REGION_STAR, time)) sparkleAt = decoTime
                music.sfx(PixelSound.STAR)
            }
        }

        // Particles.
        val calmOrStill = calm || still
        if (!calmOrStill) {
            val electric = battery >= 0f || c.evCharging != null
            if (moving && accel > 0.3f && speed > 0f) {
                puffTimer += ddt
                val every = max(0.25, 1.0 / (accel * 2.0))
                if (puffTimer >= every) { puffTimer = 0.0; spawn(KIND_PUFF, HERO_X - 3f, PixelModels.ROVER_Y + 9f) }
            } else if (parked && !electric && !arrived) {
                puffTimer += ddt
                if (puffTimer >= 2.0) { puffTimer = 0.0; spawn(KIND_PUFF, HERO_X - 3f, PixelModels.ROVER_Y + 9f) }
            }
            if (ac > 0.5f) {
                snowTimer += ddt
                if (snowTimer >= 0.4) { snowTimer = 0.0; spawn(KIND_SNOW, HERO_X + 24f, PixelModels.ROVER_Y + 7f) }
            }
        }
        for (i in 0 until PARTS) {
            if (partAge[i] < 0f) continue
            partAge[i] += ddt.toFloat()
            partX[i] -= (speed * ddt).toFloat() + (if (partKind[i] == KIND_SNOW) -6f * ddt.toFloat() else 2f * ddt.toFloat())
            partY[i] -= (if (partKind[i] == KIND_SNOW) -4f else 7f) * ddt.toFloat()
            if (partAge[i] > 1.6f) partAge[i] = -1f
        }

        // Signs on blinker sequences, the garage near the destination.
        if (turn != lastTurn) {
            if (turn != TurnSignal.NONE) { val k = signN % 4; signX[k] = camera + PixelModels.VW + 12; signSide[k] = turn.ordinal; signN++ }
            lastTurn = turn
        }
        val dest = input.journey.destinationM
        garageX = when {
            arrived -> HERO_X + 46f
            dest != null && dest <= 300 && input.journey.active -> HERO_X + 46f + dest
            else -> -1f
        }
        if (arrived && parked) {
            arrival += ddt
            heroOffsetX = (min(1.0, arrival / 2.0) * 46.0).toFloat()
        }

        pinwheel += ddt * (0.5 + 10.0 * fan) * (if (calm) 0.5 else 1.0)
        flag += ddt * (1.0 + 4.0 * fan)
        shimmer += ddt * 0.8
        chargePost = c.evCharging == true && !moving
        battery5 = if (chargePost && battery >= 0f) ((battery * 5f) + 0.999f).toInt().coerceIn(0, 5) else -1

        music.step(ddt, kmh, moving && !arrived, sound)
    }

    private fun spawn(kind: Int, x: Float, y: Float) {
        for (i in 0 until PARTS) if (partAge[i] < 0f) { partAge[i] = 0f; partX[i] = x; partY[i] = y; partKind[i] = kind; return }
    }

    private fun addGem() {
        gems++
        if (gems > MAX_GEMS) { gems = 1; gemRow++ }
    }

    override fun onStar() {
        starChipUntil = time + 4.0
        if (calm || reducedMotion) { addGem(); return }
        val i = (0 until SHARDS).firstOrNull { shardState[it] == 0 } ?: return
        shardSpawn[i] = decoTime
        shardCatch[i] = decoTime + SHARD_LEAD_S
        if (speed > 1f) {
            // The shard waits where the hop's apex meets it SHARD_LEAD_S from now.
            shardX[i] = camera + HERO_X + PILOT_CX + speed * SHARD_LEAD_S
            shardState[i] = 1
            hopStart = decoTime + SHARD_LEAD_S - HOP_S / 2
        } else shardState[i] = 2
    }

    override fun onCombo() { /* the caught gems already light the row; the painter sweeps the row on a combo */ }

    override fun onHarsh() {
        shakeAt = decoTime
        val k = skidN % 4; skidX[k] = camera + HERO_X; skidN++
    }

    override fun onMilestone(km: Int) {
        val k = archN % 4; archX[k] = camera + PixelModels.VW + 16; archKm[k] = km; archN++
        archChipUntil = time + 4.0
        music.sfx(PixelSound.CHECKPOINT)
    }

    override fun onArrival() { arrived = true; arrival = 0.0; arrivalChipUntil = time + 6.0; music.sfx(PixelSound.ARRIVAL) }

    /** Parked only: tapping the hero makes it hop and wave (the hop sound plays once, when the hop starts). */
    fun tapHero() { if (!moving || kmh < 1f) { hopStart = decoTime; tapAt = decoTime } }

    /** Hop height of the rover now (px). */
    fun hop(): Float = PixelModels.hopHeight((decoTime - hopStart) / HOP_S)

    /** One damped 2-px shake after a harsh brake (300 ms). */
    fun shake(): Int {
        val t = decoTime - shakeAt
        if (t < 0 || t > 0.3) return 0
        return (2.0 * exp(-t / 0.1) * sin(t * 60.0)).toInt()
    }

    /** Shard screen position (x, y) or null when there is none. */
    fun shardScreen(i: Int, out: FloatArray): Boolean {
        when (shardState[i]) {
            1 -> { out[0] = (shardX[i] - camera).toFloat(); out[1] = SHARD_Y; return true }
            2 -> {
                val u = ((decoTime - shardSpawn[i]) / SHARD_LEAD_S).coerceIn(0.0, 1.0).toFloat()
                out[0] = HERO_X + PILOT_CX; out[1] = 6f + (SHARD_Y + PixelModels.HOP_APEX - 6f) * u
                return true
            }
        }
        return false
    }

    /** Door open while parked (outside the arrival scene): the pilot leans out of the rover and waves. */
    val leansOut: Boolean get() = car.doorOpen == true && !moving && !arrived

    /** The pilot's pose and frame for the painter. */
    fun pose(): Pose = when {
        arrived && arrival > 2.0 && arrival < 3.5 -> Pose.STEP_OUT
        arrived && arrival >= 3.5 -> Pose.WAVE
        decoTime - shakeAt in 0.0..1.2 -> Pose.SURPRISE
        decoTime - tapAt in 0.0..1.5 -> Pose.WAVE
        car.doorOpen == true && !moving -> Pose.WAVE
        decoTime < cheerUntil || hop() > 0f -> if (hop() > 0f) Pose.HOP else Pose.HAPPY
        input.mood == Mood.HUNGRY || input.mood == Mood.SLEEPY -> Pose.SLEEPY
        moving && abs(speed) > 0.5f -> Pose.DRIVE
        else -> Pose.IDLE
    }

    fun poseFrame(p: Pose): Int = when (p) {
        Pose.HOP -> min(2, ((decoTime - hopStart) / HOP_S * 3).toInt().coerceAtLeast(0))
        Pose.STEP_OUT -> min(2, ((arrival - 2.0) / 0.5).toInt().coerceAtLeast(0))
        else -> PixelModels.frame(anim, if (calm) PixelModels.CALM_SPRITE_FPS else 6, p.frames)
    }

    /** Called by the painter when a checkpoint gate passes the hero: the pilot cheers. */
    fun cheer() { if (decoTime > cheerUntil) cheerUntil = decoTime + 2.0 }

    override fun chooseChip(): String? = when {
        time < arrivalChipUntil -> "도착"
        time < starChipUntil -> "보석 조각!"
        time < archChipUntil -> "체크포인트"
        charging > 0.5f -> "충전 중"
        moving && gear == GearState.REVERSE -> "후진"
        moving && night > 0.5f -> "밤길 운전"
        moving && rain > 0.5f -> "비 오는 길"
        moving && kmh > 3f -> "도트 월드"
        else -> null
    }

    companion object {
        const val SHARDS = 4
        const val PARTS = 24
        const val MAX_GEMS = 12
        const val KIND_PUFF = 0
        const val KIND_SNOW = 1
        /** The pilot's helmet centre x relative to the hero x. */
        const val PILOT_CX = 10f
        /** Shard height: the pilot's helmet at the top of a hop. */
        const val SHARD_Y = (PixelModels.ROVER_Y - 9 - 10).toFloat()
    }
}

/** The hero's small state used by tests (where the pilot's helmet is). */
class PixelHero {
    val headX: Float get() = HERO_X + PixelMechanics.PILOT_CX
}
