package io.vroomi.character.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.Action
import io.vroomi.character.Biome
import io.vroomi.character.Blinker
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Gait
import io.vroomi.character.Journey
import io.vroomi.character.Mood
import io.vroomi.character.SizeMode
import io.vroomi.core.vehicle.signal.Thresholds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Little things Vroomi does on its own when nothing else is going on, so the scene never feels frozen. */
internal enum class Fidget { NONE, LOOK_BACK, HUM, LOOK_UP, DRUM, BIRD, YAWN, HOP }

/** Kart geometry in units of `s` (Vroomi's body height). Origin: road contact point under the kart centre. */
internal object KG {
    const val SEAT_X = -0.36f; const val SEAT_Y = -0.34f
    const val WHEEL_R = 0.19f; const val REAR_WHEEL_X = -0.52f; const val FRONT_WHEEL_X = 0.54f
    const val STEER_X = 0.17f; const val STEER_Y = -0.80f
    const val HALF_LEN = 0.92f
    const val HEAD_X = 0.86f; const val HEAD_Y = -0.40f
    const val TAIL_X = -0.86f
}

/**
 * All animation state of the scene. [step] advances it, the draw functions (World/Kart/Vroomi painters)
 * only read it. Normalized coordinates: x in screen widths, y in screen heights.
 */
internal class SceneRuntime(seed: Int = 7) {
    var time = 0f
    var phase = 0f
    val stride = Spring(0f, 40f, 12f)
    val sizeFactor = Spring(1f, 60f, 15f)
    val kartX = Spring(0.25f, 26f, 10f)
    val groundY = Spring(0.88f, 50f, 14f)
    val standX = Spring(0.56f, 30f, 11f)
    val lean = Spring(0f, 80f, 14f)
    val pitch = Spring(0f, 70f, 7f)
    val bounce = Spring(0f, 260f, 9f)
    val squashX = Spring(1f, 160f, 16f)
    val squashY = Spring(1f, 160f, 16f)
    val wind = Spring(0f, 20f, 9f)
    val rain = Spring(0f, 10f, 6f)
    val dark = Spring(0f, 6f, 5f)
    val glow = Spring(0f, 20f, 9f)
    val blushA = Spring(0f, 20f, 9f)
    val cornerA = Spring(0f, 40f, 12f)
    val faceTurn = Spring(0f, 40f, 12f)
    val lookUp = Spring(0f, 40f, 12f)
    val steer = Spring(0f, 50f, 12f)
    val underglow = Spring(0f, 10f, 6f)
    val brakeGlow = Spring(0f, 120f, 18f)
    val speedFx = Spring(0f, 8f, 6f)
    var seatU = 0f                       // 0 standing next to the kart .. 1 seated
    var groundCol = Color(0xFFD9DDD8)

    var scrollFar = 0.0; var scrollMid = 0.0; var scrollNear = 0.0
    var lastScrollDx = 0f
    var wheelAngle = 0f
    var blink = 0f; var nextBlink = 3f
    var propSpin = 0f
    var sailT = 0f
    var lastActionStart = -1L
    var actionAge = 0f
    var steadyS = 0f

    // Journey sync (the scene shows each star exactly once)
    var lastJourney = Journey()
    var seenStarSeq = -1; var seenMilestone = -1; var seenComboSeq = -1
    var meterFlash = 0f
    var collectPulse = 0f
    var finishShown = false

    // Scenery slots: new scenery scrolls in from the right instead of popping.
    var biomeCur = Biome.CITY; var biomePrev = Biome.CITY
    /** The road's new face ([KartStep.face], null for the plain road) for the current and the previous scenery, and the upgrade step. */
    var faceCur: Biome? = null; var facePrev: Biome? = null
    var model = 0
    var farBoundary = Int.MIN_VALUE; var midBoundary = Int.MIN_VALUE
    var biomeInit = false

    // Fidgets
    var fidget = Fidget.NONE; var fidgetT = 0f; var fidgetDur = 0f; var nextFidget = 4f
    var hopY = 0f

    var emitAcc = FloatArray(PType.entries.size)
    val particles = ParticleSystem(220, seed)
    val ents = Entities(24)
    val rnd get() = particles.rnd

    // Last drawn size, so step() can place particles in normalized space.
    var lastW = 1920f; var lastH = 720f
    var lastSeason = Season.SUMMER
    var skin = io.vroomi.character.CharacterSkin.VROOMI
    var lastOptions: SceneOptions? = null

    // Reused paths (no per-frame allocation)
    val bodyPath = Path(); val leafPath = Path(); val tmpPath = Path(); val tmpPath2 = Path(); val kartPath = Path()

    val aspect: Float get() = lastW / lastH

    /** Vroomi body height in px for the current layout. */
    fun unitPx(w: Float, h: Float, sizeScale: Float): Float = min(h * 0.34f * sizeFactor.value * sizeScale, w * 0.30f)

    fun kartCenterPx(w: Float, s: Float): Float {
        val half = KG.HALF_LEN * s
        val x = kartX.value * w
        return if (cornerA.value > 0.5f) x else x.coerceIn(half * 1.15f, w - half * 1.15f)
    }

    fun standPx(w: Float, s: Float, kartPx: Float): Float =
        maxOf(standX.value * w, kartPx + (KG.HALF_LEN + 0.62f) * s)

    // ------------------------------------------------------------------ simulation

    fun step(dt: Float, t: CharacterTarget, o: SceneOptions, nowMs: Long) {
        val amp = when { o.reducedMotion -> 0.35f; o.calm -> 0.5f; else -> 1f }
        time += dt
        lastSeason = o.season
        skin = o.skin
        lastOptions = o
        val k = t.kart
        val still = t.still
        val corner = t.sizeMode == SizeMode.CORNER
        val sp = t.gaitSpeedKmh
        val driving = k.driving

        // ---- layout
        sizeFactor.step(when (t.sizeMode) { SizeMode.FULL -> 1f; SizeMode.AMBIENT -> 0.95f; SizeMode.CORNER -> 0.34f }, dt)
        cornerA.step(if (corner) 1f else 0f, dt)
        kartX.step(when { corner -> 0.86f; driving -> 0.33f; else -> 0.24f }, dt)
        groundY.step(if (corner) 0.31f else 0.88f, dt)
        standX.step(if (corner) 0.90f else 0.56f, dt)
        seatU = approach(seatU, if (driving) 1f else 0f, dt / 0.8f)

        // ---- action bookkeeping
        if (t.actionStartedMs != lastActionStart) {
            lastActionStart = t.actionStartedMs
            onActionStart(t)
        }
        actionAge = if (t.actionStartedMs == 0L) 0f else ((nowMs - t.actionStartedMs) / 1000f).coerceAtLeast(0f)

        // ---- standing gait (only while not in the kart)
        val standing = seatU < 0.5f
        val freq = if (!standing || t.gait == Gait.IDLE || still) 0f else 1.2f + 2.3f * (sp / 110f).coerceIn(0f, 1f)
        phase = (phase + 2f * PI.toFloat() * freq * dt) % (2f * PI.toFloat() * 1000f)
        val strideTarget = when (t.gait) { Gait.IDLE -> 0f; Gait.WALK -> 0.45f; Gait.JOG -> 0.7f; Gait.RUN -> 0.9f; Gait.SPRINT -> 1f }
        stride.step(if (still || !standing) 0f else strideTarget * amp, dt)

        // ---- kart physics: pitch from acceleration, suspension bumps, wheel spin
        val accel = if (still || !driving) 0f else k.accel
        val spinExtra = if (t.action == Action.SPIN) -6f * sin((actionAge / 1.6f).coerceIn(0f, 1f) * PI.toFloat()) else 0f
        pitch.step(((-accel * 1.5f).coerceIn(-7f, 7f) + spinExtra) * amp, dt)
        if (driving && !still && sp > 2f && !o.calm) {
            val bumpRate = 0.4f + sp / 40f
            if (rnd.nextFloat() < bumpRate * dt) bounce.kick(-(0.15f + rnd.nextFloat() * 0.35f) * (0.4f + sp / 110f) * amp)
        }
        bounce.step(0f, dt)
        val dir = if (k.reverse) -1f else 1f
        if (!still) wheelAngle = (wheelAngle + dir * sp * 16f * dt) % 360f
        steer.step(when (k.blinker) { Blinker.LEFT -> -24f; Blinker.RIGHT -> 24f; else -> 0f }, dt)
        underglow.step(if (k.cruise) 1f else 0f, dt)
        brakeGlow.step(if (k.brake && driving) 1f else 0f, dt)
        speedFx.step((sp / Thresholds.SPRINT_CAP_KMH).coerceIn(0f, 1f), dt)
        steadyS = if (driving && sp > 50f && abs(accel) < 0.4f) steadyS + dt else 0f

        // ---- body lean / squash
        val seated = seatU >= 0.5f
        val (leanT, sx, sy) = when (t.action) {
            Action.BURST -> Triple(if (seated) -12f else 16f, 1.10f, 0.93f)
            Action.SKID -> Triple(if (seated) 12f else -14f, 1.05f, 0.95f)
            Action.TUMBLE -> Triple(if (seated) 24f * (1f - (actionAge / 1.4f).coerceIn(0f, 1f)) else 0f, 1f, 1f)
            Action.NAP -> Triple(0f, 1.10f, 0.82f)
            Action.STRETCH -> Triple(0f, 0.92f, 1.10f)
            Action.BLOW -> Triple(-4f, 1.06f, 1f)
            Action.CELEBRATE, Action.SPIN -> Triple(0f, 1f, 1f + 0.06f * abs(sin(time * 9f)))
            else -> {
                val base = if (seated) (-accel * 2.4f).coerceIn(-10f, 10f)
                else if (t.gait >= Gait.RUN) 8f else if (t.gait == Gait.JOG) 4f else 0f
                val hum = if (fidget == Fidget.HUM || fidget == Fidget.DRUM) sin(time * 2f * PI.toFloat() * 1.6f) * 4f else 0f
                Triple(base + hum, 1f, 1f)
            }
        }
        lean.step(if (still) 0f else leanT * amp, dt)
        squashX.step(1f + (sx - 1f) * amp, dt)
        squashY.step(1f + (sy - 1f) * amp, dt)

        // ---- overlays
        wind.step(t.overlays.windLevel, dt)
        rain.step(t.overlays.rain, dt)
        // The neon street and the aurora are night roads at any hour (the face of the scenery now arriving).
        dark.step(max(darkness(o.hourOfDay, t.overlays.night), KartStep.dusk(faceCur)), dt)
        glow.step(t.overlays.footGlow, dt)
        blushA.step(if (t.mood == Mood.HAPPY || t.mood == Mood.WARM) 0.25f else 0f, dt)

        // ---- scroll (frozen in calm mode or when still)
        val v = if (!o.calm && !still) dir * (sp / 100f) * 1.1f * amp else 0f // screen widths per second
        val dx = v * dt
        lastScrollDx = dx
        scrollNear += dx; scrollMid += dx * 0.35; scrollFar += dx * 0.06

        // ---- scenery
        model = o.model.coerceIn(0, 4)
        syncBiome(t.journey.biome, KartStep.face(model, t.journey.biome, t.journey.tripKm))
        val groundBiome = biomeAt(scrollMid + 0.33, MID_SLOT, midBoundary)
        val groundTarget = faceAt(scrollMid + 0.33, MID_SLOT, midBoundary)?.let { KartStep.ground(it) } ?: groundColor(groundBiome, o.season)
        groundCol = lerp(groundCol, groundTarget, 1f - exp(-dt * 1.5f))

        // ---- journey: stars, km signs, finish arch
        syncJourney(t, o, driving)
        stepEntities(dt, dx, t, o)
        if (meterFlash > 0f) meterFlash -= dt
        if (collectPulse > 0f) collectPulse -= dt

        // ---- fidgets
        stepFidget(dt, t, o, driving, sp)
        val fT = when (fidget) { Fidget.LOOK_BACK -> 1f; else -> 0f }
        faceTurn.step(if (t.action == Action.THUMBS_UP || t.action == Action.WAVE_HELLO || t.action == Action.WAVE_BYE) 0.6f else fT, dt)
        lookUp.step(if (fidget == Fidget.LOOK_UP || fidget == Fidget.BIRD || t.action == Action.LOOK_AHEAD) 1f else 0f, dt)
        hopY = if (fidget == Fidget.HOP) abs(sin(fidgetT / fidgetDur * PI.toFloat() * 2f)) else 0f

        // ---- blink, tuft, sails
        nextBlink -= dt
        if (nextBlink <= 0f) { blink = 0.12f; nextBlink = 3f + rnd.nextFloat() * 3f }
        if (blink > 0f) blink -= dt
        propSpin = (propSpin + dt * (40f + sp * 6f) * amp) % 360f
        sailT += dt * (3f + 10f * (sp / 110f) + 8f * wind.value) * amp

        emit(dt, t, o, amp, driving)
        particles.step(dt, dx)
    }

    private fun onActionStart(t: CharacterTarget) {
        val (cx, cy) = vroomiHeadN()
        when (t.action) {
            Action.CELEBRATE, Action.SPIN -> {
                val n = if (t.action == Action.SPIN) 26 else 44
                repeat(n) {
                    val ang = rnd.nextFloat() * PI.toFloat()
                    particles.spawn(PType.CONFETTI, cx, cy - 0.05f, cos(ang) * 0.35f, -sin(ang) * 0.6f, 2.2f, 0.010f, rnd.nextFloat())
                }
                if (t.action == Action.SPIN) bounce.kick(-1.4f)
                if (t.action == Action.CELEBRATE && t.kart.driving && !finishShown) {
                    ents.spawn(EType.FINISH, kartX.value + 0.17f); finishShown = true
                }
            }
            Action.BURST, Action.SKID -> repeat(8) {
                val (rx, ry) = rearWheelN()
                particles.spawn(PType.DUST, rx - 0.01f, ry, -0.12f - rnd.nextFloat() * 0.2f, -0.12f * rnd.nextFloat(), 0.8f, 0.012f)
            }
            Action.TUMBLE -> bounce.kick(1.2f)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ scenery

    fun syncBiome(b: Biome, face: Biome? = null) {
        if (!biomeInit) { biomeCur = b; biomePrev = b; faceCur = face; facePrev = face; biomeInit = true; return }
        if (b == biomeCur && face == faceCur) return
        // A step chosen while the car stands still changes the whole scene at once (nothing scrolls in to replace it).
        if (b == biomeCur && lastScrollDx == 0f) { faceCur = face; facePrev = face; return }
        biomePrev = biomeCur; facePrev = faceCur
        biomeCur = b; faceCur = face
        // Everything already on screen keeps the old scenery; new slots arrive from the right edge.
        farBoundary = floorDiv(scrollFar + 1.05, FAR_SLOT) + 1
        midBoundary = floorDiv(scrollMid + 1.05, MID_SLOT) + 1
    }

    fun biomeAt(pos: Double, slot: Double, boundary: Int): Biome =
        if (floorDiv(pos, slot) >= boundary) biomeCur else biomePrev

    fun faceAt(pos: Double, slot: Double, boundary: Int): Biome? =
        if (floorDiv(pos, slot) >= boundary) faceCur else facePrev

    // ------------------------------------------------------------------ journey sync

    private fun syncJourney(t: CharacterTarget, o: SceneOptions, driving: Boolean) {
        val j = t.journey
        lastJourney = j
        if (seenStarSeq < 0) { seenStarSeq = j.starSeq; seenMilestone = j.milestoneKm; seenComboSeq = j.comboSeq; return }
        val animate = driving && !o.calm && !o.reducedMotion
        if (j.starSeq > seenStarSeq) {
            if (animate) {
                val n = min(3, j.starSeq - seenStarSeq)
                for (i in 0 until n) ents.spawn(EType.STAR, 1.06f + i * 0.14f)
            } else onStarCollected()
            seenStarSeq = j.starSeq
        } else if (j.starSeq < seenStarSeq) seenStarSeq = j.starSeq
        if (j.milestoneKm > seenMilestone) {
            if (animate) ents.spawn(EType.KM_SIGN, 1.12f, j.milestoneKm)
            seenMilestone = j.milestoneKm
        } else if (j.milestoneKm < seenMilestone) seenMilestone = j.milestoneKm
        if (j.comboSeq > seenComboSeq) seenComboSeq = j.comboSeq
        if (!driving && seatU <= 0f) {
            // Parked: nothing left on the road, the next trip starts fresh.
            if (ents.count > 0) ents.clear()
            finishShown = false
        }
    }

    /** Stars still floating toward Vroomi (not yet in the meter). */
    fun starsInFlight(): Int = ents.countOf(EType.STAR)

    /** Filled slots of the 5-star meter on the kart door. */
    fun meterFill(): Int {
        if (meterFlash > 0f) return Thresholds.STAR_COMBO
        val c = (lastJourney.combo - starsInFlight()).coerceAtLeast(0)
        return c % Thresholds.STAR_COMBO
    }

    private fun onStarCollected() {
        collectPulse = 0.35f
        val c = (lastJourney.combo - starsInFlight()).coerceAtLeast(0)
        if (c > 0 && c % Thresholds.STAR_COMBO == 0) meterFlash = 1.8f
    }

    private fun stepEntities(dt: Float, dx: Float, t: CharacterTarget, o: SceneOptions) {
        val kx = kartX.value
        var i = 0
        while (i < ents.count) {
            ents.age[i] += dt
            ents.x[i] -= dx * (if (ents.type[i] == EType.KM_SIGN) 0.92f else 1f)
            val type = ents.type[i]
            if (type == EType.STAR && ents.x[i] <= kx + 0.015f) {
                val (hx, hy) = starWorldN(ents.x[i], ents.age[i])
                ents.removeAt(i)
                onStarCollected()
                if (!o.reducedMotion) repeat(12) {
                    val a = rnd.nextFloat() * 2f * PI.toFloat()
                    val sp = 0.15f + rnd.nextFloat() * 0.2f
                    particles.spawn(PType.STARPOP, hx, hy, cos(a) * sp / aspect, sin(a) * sp, 0.7f, 0.010f + rnd.nextFloat() * 0.006f)
                }
                continue
            }
            if (ents.x[i] < -0.35f || ents.x[i] > 1.6f || (type == EType.FINISH && ents.age[i] > 12f)) { ents.removeAt(i); continue }
            i++
        }
    }

    /** Normalized position of a floating star pickup at screen x. */
    fun starWorldN(x: Float, age: Float): Pair<Float, Float> {
        val sN = 0.34f * sizeFactor.value
        val y = groundY.value - sN * 0.98f + sin(age * 5f) * sN * 0.05f
        return x to y
    }

    // ------------------------------------------------------------------ fidgets

    private fun stepFidget(dt: Float, t: CharacterTarget, o: SceneOptions, driving: Boolean, sp: Float) {
        val settled = seatU == 0f || seatU == 1f
        val eligible = t.action == Action.NONE && !t.still && t.sizeMode != SizeMode.CORNER &&
            !o.calm && !o.reducedMotion && settled && t.mood != Mood.DIZZY && t.mood != Mood.SLEEPY
        if (fidget != Fidget.NONE) {
            fidgetT += dt
            if (fidgetT >= fidgetDur || !eligible) { fidget = Fidget.NONE; fidgetT = 0f }
            return
        }
        if (!eligible) return
        nextFidget -= dt
        if (nextFidget > 0f) return
        nextFidget = 5f + rnd.nextFloat() * 6f
        val options: Array<Fidget> = when {
            driving && steadyS > 45f -> arrayOf(Fidget.YAWN, Fidget.HUM, Fidget.LOOK_BACK, Fidget.BIRD)
            driving && sp > 3f -> arrayOf(Fidget.LOOK_BACK, Fidget.HUM, Fidget.LOOK_UP, Fidget.DRUM, Fidget.BIRD)
            driving -> arrayOf(Fidget.LOOK_BACK, Fidget.DRUM, Fidget.HUM)
            else -> arrayOf(Fidget.LOOK_BACK, Fidget.HUM, Fidget.LOOK_UP, Fidget.HOP)
        }
        start(options[rnd.nextInt(options.size)])
    }

    /** Also used by tests to pose a specific fidget. */
    fun start(f: Fidget) {
        fidget = f
        fidgetT = 0f
        fidgetDur = when (f) { Fidget.BIRD -> 5.2f; Fidget.YAWN -> 3.2f; Fidget.HOP -> 1.2f; else -> 3f }
    }

    // ------------------------------------------------------------------ particles

    private fun rate(type: PType, perSecond: Float, dt: Float): Int {
        val i = type.ordinal
        emitAcc[i] += perSecond * dt
        val n = emitAcc[i].toInt()
        emitAcc[i] -= n
        return n
    }

    /** Vroomi's head (mouth area) in normalized coordinates. */
    fun vroomiHeadN(): Pair<Float, Float> {
        val sN = 0.34f * sizeFactor.value
        val sW = sN / aspect
        val seated = seatU >= 0.5f
        return if (seated) {
            (kartX.value + (KG.SEAT_X + 0.20f) * sW) to (groundY.value + (KG.SEAT_Y - 0.55f) * sN)
        } else {
            (standX.value.coerceAtLeast(kartX.value + (KG.HALF_LEN + 0.62f) * sW) + 0.08f * sW) to (groundY.value - 0.58f * sN)
        }
    }

    fun rearWheelN(): Pair<Float, Float> {
        val sN = 0.34f * sizeFactor.value
        return (kartX.value + KG.REAR_WHEEL_X * sN / aspect) to (groundY.value - 0.02f)
    }

    private fun emit(dt: Float, t: CharacterTarget, o: SceneOptions, amp: Float, driving: Boolean) {
        val corner = t.sizeMode == SizeMode.CORNER
        if (o.reducedMotion || (o.calm && driving) || t.still) return
        val r = rnd
        val sN = 0.34f * sizeFactor.value
        val sW = sN / aspect
        val (mouthX, mouthY) = vroomiHeadN()
        val blowing = t.action == Action.BLOW || wind.value > 0.4f
        val sp = t.gaitSpeedKmh

        repeat(rate(PType.WIND, if (wind.value > 0.05f) 10f + 35f * wind.value else 0f, dt)) {
            val sy = if (blowing) mouthY + (r.nextFloat() - 0.5f) * 0.12f else 0.1f + r.nextFloat() * 0.6f
            val sx = if (blowing) mouthX + 0.03f else -0.05f
            particles.spawn(PType.WIND, sx, sy, (0.45f + 0.7f * wind.value) * amp, (r.nextFloat() - 0.5f) * 0.05f, 1.6f, 0.03f + 0.05f * wind.value)
        }
        if (t.overlays.snow) {
            repeat(rate(PType.SNOW, 5f, dt)) { particles.spawn(PType.SNOW, r.nextFloat() * 1.1f, -0.02f, -0.02f - sp / 900f, 0.10f + r.nextFloat() * 0.05f, 7f, 0.004f + r.nextFloat() * 0.003f) }
            repeat(rate(PType.BREATH, 0.9f, dt)) { particles.spawn(PType.BREATH, mouthX + 0.01f, mouthY, 0.10f, -0.02f, 1.4f, 0.018f) }
        }
        if (t.overlays.steam) repeat(rate(PType.STEAM, 2.5f, dt)) {
            particles.spawn(PType.STEAM, mouthX - 0.02f + (r.nextFloat() - 0.5f) * 0.04f, mouthY - sN * 0.5f, (r.nextFloat() - 0.5f) * 0.04f, -0.08f, 1.8f, 0.014f)
        }
        if (rain.value > 0.05f) repeat(rate(PType.RAIN, 80f * rain.value, dt)) {
            particles.spawn(PType.RAIN, r.nextFloat() * 1.15f, -0.05f, -0.06f - sp / 500f, 1.3f, 1.2f, 0.035f)
        }
        if (t.overlays.sparkle) repeat(rate(PType.SPARKLE, 4f, dt)) {
            particles.spawn(PType.SPARKLE, mouthX + (r.nextFloat() - 0.6f) * 0.2f, mouthY + (r.nextFloat() - 0.5f) * sN, 0f, -0.01f, 1.1f, 0.008f)
        }
        if (t.action == Action.EAT_ENERGY) repeat(rate(PType.ENERGY, 4f, dt)) {
            particles.spawn(PType.ENERGY, mouthX + 0.14f, mouthY + (r.nextFloat() - 0.5f) * 0.08f, -0.13f, 0f, 1.1f, 0.007f)
        }
        if (t.action == Action.NAP) repeat(rate(PType.ZZZ, 0.7f, dt)) {
            particles.spawn(PType.ZZZ, mouthX + 0.02f, mouthY - sN * 0.45f, 0.03f, -0.05f, 2.6f, 0.016f)
        }
        val orgel = skin == io.vroomi.character.CharacterSkin.ORGEL && driving && sp > 3f
        if (fidget == Fidget.HUM || fidget == Fidget.DRUM || orgel) repeat(rate(PType.NOTE, if (orgel) 1.1f else 1.6f, dt)) {
            particles.spawn(PType.NOTE, mouthX + 0.01f, mouthY - sN * 0.15f, 0.03f + r.nextFloat() * 0.03f, -0.09f, 1.8f, 0.016f, r.nextFloat())
        }
        if (corner) return
        if (driving && seatU >= 1f) {
            val (rx, ry) = rearWheelN()
            val tailX = kartX.value + KG.TAIL_X * sW
            // EV "zap" puffs while speeding up (never smoke)
            val acc = t.kart.accel
            if (acc > 0.6f && sp > 2f) repeat(rate(PType.ZAP, 6f + acc * 6f, dt)) {
                particles.spawn(PType.ZAP, tailX, groundY.value - sN * (0.25f + r.nextFloat() * 0.2f), -0.08f - r.nextFloat() * 0.06f, (r.nextFloat() - 0.5f) * 0.04f, 0.6f, 0.006f + r.nextFloat() * 0.005f)
            }
            if (rain.value > 0.2f && sp > 8f) repeat(rate(PType.SPLASH, 6f + sp / 8f, dt)) {
                val front = r.nextBoolean()
                val wx = if (front) kartX.value + KG.FRONT_WHEEL_X * sW else rx
                particles.spawn(PType.SPLASH, wx - 0.012f, ry, -0.06f - r.nextFloat() * 0.08f, -0.10f - r.nextFloat() * 0.08f, 0.5f, 0.004f)
            }
            // Seasonal drift (a few leaves/petals/flakes past the road)
            val fallRate = when (o.season) { Season.AUTUMN -> 0.9f; Season.SPRING -> 1.1f; Season.WINTER -> 1.4f; Season.SUMMER -> 0f }
            if (sp > 5f && !t.overlays.snow) repeat(rate(PType.FALL, fallRate, dt)) {
                particles.spawn(PType.FALL, 1.05f, 0.25f + r.nextFloat() * 0.45f, -0.18f - sp / 400f, 0.05f + r.nextFloat() * 0.05f, 7f, 0.009f, r.nextFloat())
            }
        }
    }

    companion object {
        const val FAR_SLOT = 0.42
        const val MID_SLOT = 0.17

        /** 0 day .. 1 night, from the local hour and the headlights. */
        fun darkness(hour: Float, headlights: Boolean): Float {
            val h = ((hour % 24f) + 24f) % 24f
            val byHour = when {
                h < 5f -> 1f
                h < 7f -> 1f - (h - 5f) / 2f
                h < 17.5f -> 0f
                h < 19.5f -> (h - 17.5f) / 2f
                else -> 1f
            }
            return maxOf(byHour * 0.85f, if (headlights) 0.85f else 0f)
        }

        fun groundColor(b: Biome, season: Season): Color = when (b) {
            Biome.CITY -> Color(0xFFD6DBD6)
            Biome.PARK -> when (season) { Season.AUTUMN -> Color(0xFFD9DDA6); Season.WINTER -> Color(0xFFEAF1F2); else -> Color(0xFFBFE3B4) }
            Biome.FIELD -> when (season) { Season.AUTUMN -> Color(0xFFE6D69A); Season.WINTER -> Color(0xFFE8EEEE); else -> Color(0xFFD7E8A4) }
            Biome.SEASIDE -> Color(0xFFF1E3B8)
            Biome.MOUNTAIN -> when (season) { Season.WINTER -> Color(0xFFDCE7EA); Season.AUTUMN -> Color(0xFFB8C98F); else -> Color(0xFFA9CFA0) }
        }
    }
}
