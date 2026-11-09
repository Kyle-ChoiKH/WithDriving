package io.vroomi.character.treasuremap

import io.vroomi.character.Biome
import io.vroomi.character.theme.ThemeSim
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.route.LiveRoute
import io.vroomi.core.vehicle.route.RouteMath
import io.vroomi.core.vehicle.route.TripSketch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The treasure-map travel journal (pure Kotlin). It inks only the path BEHIND the car, in metres east and north
 * of the journey start, from (1) the demo route, (2) fresh on-device FusedLocation fixes ([LiveRoute]) or
 * (3) dead reckoning from speed, turning about 70° after each blinker sequence. It never shows a route ahead,
 * turn arrows, street names or coordinates, and it never stores a position: a finished trip hands only a
 * normalised 64-point sketch to the diary ([TripSketch]).
 */
class TreasureMapSim : ThemeSim() {

    enum class Source { DEMO, LIVE, RECKONED }

    // Trail (metres) and when each point was inked (decoration seconds).
    val xs = FloatArray(CAP); val ys = FloatArray(CAP); val inkAt = FloatArray(CAP)
    /** Ink width at each point (a calm line: 1.8..3.0 at any speed). */
    val inkW = FloatArray(CAP)
    var count = 0; private set
    var source = Source.RECKONED; private set
    var headX = 0f; private set
    var headY = 0f; private set
    /** Direction of travel (rad, 0 = east, counter-clockwise), smoothed. */
    var heading = 0.0; private set

    // Marks (metres).
    val markX = FloatArray(MARKS); val markY = FloatArray(MARKS); val markAt = FloatArray(MARKS); val markGroup = IntArray(MARKS)
    var marks = 0; private set
    val linkFrom = IntArray(MARKS); val linkTo = IntArray(MARKS)
    var links = 0; private set
    val stampX = FloatArray(STAMPS); val stampY = FloatArray(STAMPS)
    var stamps = 0; private set
    val spatX = FloatArray(STAMPS); val spatY = FloatArray(STAMPS); val spatSeed = IntArray(STAMPS)
    var spats = 0; private set
    val tickX = FloatArray(TICKS); val tickY = FloatArray(TICKS); val tickKm = IntArray(TICKS); val tickAng = FloatArray(TICKS)
    var ticks = 0; private set
    val doodleX = FloatArray(DOODLES); val doodleY = FloatArray(DOODLES); val doodleKind = IntArray(DOODLES); val doodleSide = FloatArray(DOODLES)
    var doodles = 0; private set

    // Camera (metres): centre and half-extent, smoothed (τ 2 s).
    var camX = 0f; private set
    var camY = 0f; private set
    var camR = MIN_HALF_EXTENT; private set
    /** Half extents across and down (metres); the painter fits both into the sheet. */
    var camRx = MIN_HALF_EXTENT; private set
    var camRy = MIN_HALF_EXTENT * 0.45f; private set
    /** Parked only: false = the whole journey (default), true = the trail around the token. */
    var trailView = false

    var nearDestination = 0f; private set
    /** 0..1 roll-up after arrival. */
    var roll = 0f; private set
    var rolled = false; private set

    private var tripStartM = 0f
    private var lastAppendX = 0f; private var lastAppendY = 0f
    private var srcOffX = 0f; private var srcOffY = 0f
    private var lastSource: Source? = null
    private var stoppedFor = 0.0
    private var lastStampAt = -1000.0
    private var distSinceDoodle = 0f
    private var lastMeters = 0f
    private var turnLeft = 0.0
    private var group = 0
    private var groupStart = 0
    private var sketchSent = false
    private var wasActive = false
    private val pt = FloatArray(2)

    private var stampChipUntil = -1.0
    private var markChipUntil = -1.0
    private var linkChipUntil = -1.0
    private var arrivalChipUntil = -1.0

    override val allChips: List<String> = listOf("도착", "보물 표시", "보물 잇기", "쉼터 도장", "목적지 근처", "비에 번짐", "그림 지도", "지도를 그려요")

    override fun onStart(nowMs: Long) = startJourney()
    override fun onTripStart() = startJourney()

    private fun startJourney() {
        LiveRoute.markJourneyStart()
        count = 0; marks = 0; links = 0; stamps = 0; spats = 0; ticks = 0; doodles = 0
        headX = 0f; headY = 0f; srcOffX = 0f; srcOffY = 0f; lastSource = null
        heading = 0.0; turnLeft = 0.0
        tripStartM = input.journey.tripKm * 1000f
        lastMeters = 0f; distSinceDoodle = 0f
        roll = 0f; rolled = false; sketchSent = false
        group = 0; groupStart = 0
        camX = 0f; camY = 0f; camR = MIN_HALF_EXTENT
        append(0f, 0f)
    }

    override fun advance(dt: Double, ddt: Double, nowMs: Long) {
        checkTurnEnd()
        val j = input.journey
        val meters = j.tripKm * 1000f - tripStartM
        val dMeters = (meters - lastMeters).coerceAtLeast(0f)
        lastMeters = max(lastMeters, meters)

        // Where is the car now (metres from the start)?
        val demo = LiveRoute.demoRoute
        val src = when {
            demo != null -> Source.DEMO
            LiveRoute.fresh() -> Source.LIVE
            else -> Source.RECKONED
        }
        when (src) {
            Source.DEMO -> RouteMath.along(demo!!, meters, pt)
            Source.LIVE -> { pt[0] = LiveRoute.east; pt[1] = LiveRoute.north }
            Source.RECKONED -> {
                // Turn about 70° along a smooth arc after a blinker sequence ends.
                if (turnLeft != 0.0) {
                    val step = (TURN_RATE * dt).coerceAtMost(kotlin.math.abs(turnLeft)) * Math.signum(turnLeft)
                    if (dMeters > 0f || kmh > 1f) { heading += step; turnLeft -= step }
                }
                pt[0] = headX - srcOffX + (cos(heading) * dMeters).toFloat()
                pt[1] = headY - srcOffY + (sin(heading) * dMeters).toFloat()
            }
        }
        if (src != lastSource) {
            // Keep the ink continuous when the source changes (a lost fix, the demo ending).
            if (lastSource != null) { srcOffX = headX - pt[0]; srcOffY = headY - pt[1] }
            lastSource = src
            source = src
        }
        val nx = pt[0] + srcOffX; val ny = pt[1] + srcOffY
        val moved = hypot(nx - headX, ny - headY)
        if (moved > 0.05f && src != Source.RECKONED) {
            val h = atan2((ny - headY).toDouble(), (nx - headX).toDouble())
            heading = blendAngle(heading, h, min(1.0, moved / 12.0))
        }
        headX = nx; headY = ny
        if (hypot(nx - lastAppendX, ny - lastAppendY) >= APPEND_M) append(nx, ny)

        // Rest stamp after 30 s stopped (at most one per 2 minutes).
        val stoppedNow = kmh < 1.5f && j.active && gear != GearState.PARK
        stoppedFor = if (stoppedNow) stoppedFor + dt else 0.0
        if (stoppedFor > REST_S && decoTime - lastStampAt > REST_GAP_S && stamps < STAMPS) {
            stampX[stamps] = headX; stampY[stamps] = headY; stamps++; lastStampAt = decoTime; stampChipUntil = time + 4.0
        }

        // Landmark doodles every 250 m on alternating sides, by biome.
        distSinceDoodle += dMeters
        if (distSinceDoodle >= DOODLE_EVERY_M && doodles < DOODLES) {
            distSinceDoodle = 0f
            doodleX[doodles] = headX; doodleY[doodles] = headY
            doodleKind[doodles] = biome.ordinal
            doodleSide[doodles] = if (doodles % 2 == 0) 1f else -1f
            doodles++
        }

        nearDestination = approach(nearDestination, if ((j.destinationM ?: Int.MAX_VALUE) <= NEAR_DEST_M && !rolled) 1f else 0f, (dt / 0.6).toFloat())

        // Camera: fit the whole trail (or the recent trail while parked in trail view), min extent, τ 2 s.
        fitCamera(dt)

        // Arrival: the map rolls up; the sketch goes to the diary once.
        if (rolled && moving && kmh > 3f) rolled = false
        roll = approach(roll, if (rolled) 1f else 0f, (ddt / ROLL_S).toFloat())
        if (j.active) wasActive = true
        if (wasActive && !j.active) { wasActive = false; sendSketch() }
    }

    private fun fitCamera(dt: Double) {
        var minX = headX; var maxX = headX; var minY = headY; var maxY = headY
        val recent = trailView && !moving
        val from = if (recent) max(0, count - 80) else 0
        for (i in from until count) { minX = min(minX, xs[i]); maxX = max(maxX, xs[i]); minY = min(minY, ys[i]); maxY = max(maxY, ys[i]) }
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
        val minHalf = if (recent) MIN_HALF_EXTENT * 0.5f else MIN_HALF_EXTENT
        val half = max(max(maxX - minX, maxY - minY) / 2f * 1.18f, minHalf)
        val hx = max((maxX - minX) / 2f * 1.18f, minHalf)
        val hy = max((maxY - minY) / 2f * 1.18f, minHalf * 0.45f)
        val k = (1.0 - exp(-dt / CAMERA_TAU_S)).toFloat()
        camX += (cx - camX) * k; camY += (cy - camY) * k; camR += (half - camR) * k
        camRx += (hx - camRx) * k; camRy += (hy - camRy) * k
    }

    private fun append(x: Float, y: Float) {
        if (count == CAP) simplify()
        xs[count] = x; ys[count] = y; inkAt[count] = decoTime.toFloat()
        inkW[count] = 1.8f + 1.2f * (kmh / 120f).coerceIn(0f, 1f)
        count++
        lastAppendX = x; lastAppendY = y
    }

    /** Douglas–Peucker at 5 m (then coarser) when the trail fills up; the newest ink keeps its time. */
    private fun simplify() {
        var tol = APPEND_M
        while (true) {
            val keep = RouteMath.douglasPeucker(xs, ys, count, tol)
            var n = 0
            for (i in 0 until count) if (keep[i]) { xs[n] = xs[i]; ys[n] = ys[i]; inkAt[n] = inkAt[i]; inkW[n] = inkW[i]; n++ }
            count = n
            if (count < CAP * 3 / 4) return
            tol *= 2f
        }
    }

    override fun onStar() {
        if (marks >= MARKS) return
        markX[marks] = headX; markY[marks] = headY; markAt[marks] = decoTime.toFloat(); markGroup[marks] = group
        marks++
        markChipUntil = time + 4.0
    }

    override fun onCombo() {
        // A dotted line links the marks of this combo (since the last combo), then a new group starts.
        for (i in max(groupStart, 0) until marks - 1) if (links < MARKS) { linkFrom[links] = i; linkTo[links] = i + 1; links++ }
        group++; groupStart = marks
        linkChipUntil = time + 4.0
    }

    override fun onHarsh() {
        if (spats < STAMPS) { spatX[spats] = headX; spatY[spats] = headY; spatSeed[spats] = spats * 31 + 7; spats++ }
    }

    override fun onMilestone(km: Int) {
        if (ticks < TICKS) { tickX[ticks] = headX; tickY[ticks] = headY; tickKm[ticks] = km; tickAng[ticks] = heading.toFloat(); ticks++ }
    }

    override fun onArrival() {
        arrivalChipUntil = time + 6.0
        rolled = true
        sendSketch()
    }

    override fun onBiome(b: Biome) { distSinceDoodle = DOODLE_EVERY_M } // a new landscape is drawn at once

    private var lastSeenTurn = TurnSignal.NONE

    /** Dead reckoning: the blinker sequence ended → schedule the turn. */
    private fun checkTurnEnd() {
        val t = turn
        if (lastSeenTurn != TurnSignal.NONE && lastSeenTurn != TurnSignal.HAZARD && t == TurnSignal.NONE) {
            turnLeft += if (lastSeenTurn == TurnSignal.LEFT) TURN_RAD else -TURN_RAD
        }
        lastSeenTurn = t
    }

    override fun chooseChip(): String? {
        return when {
            time < arrivalChipUntil -> "도착"
            time < linkChipUntil -> "보물 잇기"
            time < markChipUntil -> "보물 표시"
            time < stampChipUntil -> "쉼터 도장"
            nearDestination > 0.5f -> "목적지 근처"
            moving && rain > 0.5f -> "비에 번짐"
            moving && source == Source.RECKONED && kmh > 3f -> "그림 지도"
            moving && kmh > 3f -> "지도를 그려요"
            else -> null
        }
    }

    private fun sendSketch() {
        if (sketchSent || count < 2) return
        sketchSent = true
        TripSketch.offer(RouteMath.normalise64(xs, ys, count))
    }

    /** Seconds since point [i] was inked (fresh ink is dark and glossy for 3 s). */
    fun inkAge(i: Int): Float = (decoTime - inkAt[i]).toFloat()

    companion object {
        const val CAP = 1600
        const val MARKS = 40
        const val STAMPS = 20
        const val TICKS = 60
        const val DOODLES = 120
        const val APPEND_M = 5f
        const val REST_S = 30.0
        const val REST_GAP_S = 120.0
        const val DOODLE_EVERY_M = 250f
        const val NEAR_DEST_M = 1000
        const val MIN_HALF_EXTENT = 300f
        const val CAMERA_TAU_S = 2.0
        const val ROLL_S = 2.5
        const val TURN_RAD = 70.0 * PI / 180.0
        /** rad/s along the arc (a 70° turn in about 3 s). */
        const val TURN_RATE = TURN_RAD / 3.0

        fun blendAngle(a: Double, b: Double, t: Double): Double {
            var d = b - a
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            return a + d * t
        }
    }
}
