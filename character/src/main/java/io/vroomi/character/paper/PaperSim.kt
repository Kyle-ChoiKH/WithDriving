package io.vroomi.character.paper

import io.vroomi.character.Action
import io.vroomi.character.Biome
import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * A risograph-printed pop-up book (pure Kotlin): an origami car with a folded crane on its roof drives along a
 * kraft-paper route. The director, the journey and the biomes are the shared ones, unchanged: this class only
 * READS them and decides how the paper moves. Paper only turns on hinges (the crane rocks on its fold, the car
 * tips on its rear wheel); page flips happen only while parked.
 */
class PaperSim : ThemeSim() {

    /** Scenery strips slide with the speed (design units). */
    var strip = 0.0; private set
    var sunAngle = 0.0; private set
    /** The pull-tab that turns the sun wheel (0..1 along its slot). */
    var tab = 0f; private set
    /** Step 4's cloud strip, 0..1 along its slot: it is pulled only while parked (the page-flip rule). */
    var cloudSlide = 0.35f; private set
    private var cloudPhase = 1.27
    var wheelAngle = 0.0; private set
    var dustAt = -100.0; private set
    private var lastBrake = false
    var tipAt = -100.0; private set
    var rainSwing = 0.0; private set
    var leafPhase = 0.0; private set
    var cone = 0f; private set
    var moon = 0f; private set

    // Pop-ups.
    val starPopAt = DoubleArray(STARS) { -100.0 }
    var starCount = 0; private set
    var garlandAt = -100.0; private set
    var bookmark = 0; private set
    var bookmarkAt = -100.0; private set
    var biomeNow = Biome.CITY; private set
    var biomeBefore = Biome.CITY; private set
    var riseAt = -100.0; private set

    // Book.
    var flipAt = -100.0; private set
    var spread = 0; private set
    var closed = 0f; private set
    private var arrived = false

    // The crane's hinge angle (deg) and the car's bounce.
    var hingeAngle = 0f; private set
    var bounce = 0f; private set
    var happy = false; private set

    private var starChipUntil = -1.0
    private var garlandChipUntil = -1.0
    private var pageChipUntil = -1.0
    private var arrivalChipUntil = -1.0

    override val allChips: List<String> = listOf("도착", "색종이 톡", "종이 가랜드", "새 장면", "다음 장", "책갈피", "종이 비", "달밤 이야기", "책을 펼쳐요")

    override fun onStart(nowMs: Long) { biomeNow = input.journey.biome; biomeBefore = biomeNow }

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        val t = input
        val drive = moving && kmh > 1f
        val calmK = if (calm) 0.5 else 1.0
        val v = if (reducedMotion) 0.0 else kmh.toDouble()
        val dir = if (gear == GearState.REVERSE) -1.0 else 1.0
        strip += v / 3.6 * 9.0 * ddt * dir * calmK
        tab = approach(tab, (kmh / 120f).coerceIn(0f, 1f), (dt / 0.6).toFloat())
        if (parked && !reducedMotion) { cloudPhase += ddt * 0.22 * calmK; cloudSlide = (0.5 - 0.5 * kotlin.math.cos(cloudPhase)).toFloat() }
        sunAngle += ddt * (0.05 + 0.6 * tab) * calmK
        wheelAngle += v / 3.6 * ddt * 0.9 * dir
        val brake = car.brakePressed == true && kmh > 3f
        if (brake && !lastBrake) dustAt = decoTime
        lastBrake = brake
        rainSwing += ddt * 2 * PI / 2.2 * rain * calmK
        leafPhase += ddt * (0.3 + 2.5 * fan) * calmK
        cone = approach(cone, if (car.headlightsOn == true) 1f else 0f, (dt / 0.6).toFloat())
        moon = approach(moon, night, (dt / 1.5).toFloat())

        // Hinge: a bounce on the paper road, a little lean into acceleration, a joyful rock for actions.
        happy = t.action != Action.NONE
        val rock = if (happy) sin(decoTime * 2 * PI * 0.9) * 8.0 else 0.0
        val tip = since(tipAt).let { x -> if (x in 0.0..1.6) exp(-x / 0.35) * sin(2 * PI * 1.3 * x) * 18.0 else 0.0 }
        val lean = (-accel * 3f).coerceIn(-6f, 6f)
        hingeAngle = ((rock + tip + lean) * calmK).toFloat().coerceIn(-MAX_JOINT, MAX_JOINT)
        bounce = if (drive && !reducedMotion) (sin(strip * 0.05) * 3.0).toFloat() else 0f

        // Arrival: the book closes gently; driving opens it again.
        if (arrived && drive) arrived = false
        closed = approach(closed, if (arrived && !drive) 1f else 0f, (dt / CLOSE_S).toFloat())
    }

    override fun onBiome(b: Biome) {
        // While driving the next scene rises from the gutter (no page turn while driving).
        biomeBefore = biomeNow; biomeNow = b; riseAt = decoTime; pageChipUntil = time + 4.0
    }

    override fun onStar() {
        if (!flash.allow(REGION_STAR, time)) return
        starPopAt[starCount % STARS] = decoTime; starCount++
        starChipUntil = time + 4.0
    }

    override fun onCombo() { garlandAt = decoTime; garlandChipUntil = time + 5.0 }
    override fun onHarsh() { tipAt = decoTime }
    override fun onMilestone(km: Int) { bookmark = km; bookmarkAt = decoTime }
    override fun onArrival() { arrived = true; arrivalChipUntil = time + 6.0 }

    /** Parked only: turn to a fresh spread (1.2 s). Returns whether the page turns. */
    fun requestFlip(): Boolean {
        if (moving && kmh > 1f || since(flipAt) < FLIP_S || closed > 0.1f) return false
        flipAt = decoTime; spread++
        pageChipUntil = time + 3.0
        return true
    }

    /** 0..1 progress of a running page flip, or −1. */
    fun flip(): Float { val x = since(flipAt); return if (x < 0 || x > FLIP_S) -1f else (x / FLIP_S).toFloat() }

    /** 0..1 rise of the newest pop-up scene, 1 when settled. */
    fun rise(): Float = min(1.0, since(riseAt) / RISE_S).toFloat()

    override fun chooseChip(): String? = when {
        time < arrivalChipUntil -> "도착"
        time < garlandChipUntil -> "종이 가랜드"
        time < starChipUntil -> "색종이 톡"
        time < pageChipUntil -> if (moving) "새 장면" else "다음 장"
        since(bookmarkAt) in 0.0..4.0 -> "책갈피"
        moving && rain > 0.5f -> "종이 비"
        moving && night > 0.5f -> "달밤 이야기"
        moving && kmh > 3f -> "책을 펼쳐요"
        else -> null
    }

    companion object {
        const val STARS = 6
        const val MAX_JOINT = 25f
        const val FLIP_S = 1.2
        const val RISE_S = 1.5
        const val CLOSE_S = 2.5
    }
}
