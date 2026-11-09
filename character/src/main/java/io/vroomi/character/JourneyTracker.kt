package io.vroomi.character

import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.signal.Thresholds

/**
 * The light game layer of a trip, from speed only (no location is used or stored):
 * - one star per [Thresholds.STAR_EVERY_M] metres; a harsh brake/acceleration restarts the current star
 *   and the combo, speeding above [Thresholds.OVERSPEED_KMH] earns nothing
 * - five stars in a row = combo (Vroomi celebrates)
 * - a km sign per kilometre, and new scenery every [Thresholds.BIOME_EVERY_M] metres
 * A journey starts when the car starts moving after a finished trip and ends in Park
 * (or after a 60 s stop when the gear is unknown). Pure Kotlin; time is passed in.
 */
class JourneyTracker {
    enum class Event { STAR, COMBO, MILESTONE, BIOME }

    private var active = false
    private var ended = true
    private var lastMs: Long? = null
    private var stoppedSince: Long? = null
    private var tripM = 0.0
    private var segmentM = 0.0
    private var stars = 0
    private var starSeq = 0
    private var combo = 0
    private var comboSeq = 0
    private var bestCombo = 0
    private var milestone = 0
    private var biomeIdx = 0
    private var tripSeq = 0
    private val seen = HashSet<Biome>().apply { add(Biome.CITY) }
    private val events = ArrayList<Event>()

    val biome: Biome get() = Biome.entries[biomeIdx]

    fun drainEvents(): List<Event> = ArrayList(events).also { events.clear() }

    /** Harsh brake or acceleration: the star in progress and the combo restart (nothing is taken away). */
    fun onHarsh() {
        segmentM = 0.0
        combo = 0
    }

    fun update(speedKmh: Float, moving: Boolean, gear: GearState, destinationM: Int?, nowMs: Long): Journey {
        val dtMs = lastMs?.let { (nowMs - it).coerceIn(0L, 1_000L) } ?: 0L
        lastMs = nowMs

        if (moving && ended) start()
        if (active) {
            val d = speedKmh.coerceAtLeast(0f) / 3.6 * dtMs / 1000.0
            tripM += d
            if (speedKmh <= Thresholds.OVERSPEED_KMH) segmentM += d
            if (segmentM >= Thresholds.STAR_EVERY_M) {
                segmentM -= Thresholds.STAR_EVERY_M
                stars++; starSeq++; combo++
                if (combo > bestCombo) bestCombo = combo
                events += Event.STAR
                if (combo % Thresholds.STAR_COMBO == 0) { comboSeq++; events += Event.COMBO }
            }
            val km = (tripM / 1000.0).toInt()
            if (km > milestone) { milestone = km; events += Event.MILESTONE }
            val b = ((tripM / Thresholds.BIOME_EVERY_M).toInt()) % Biome.entries.size
            if (b != biomeIdx) {
                biomeIdx = b
                seen += Biome.entries[b]
                events += Event.BIOME
            }
        }

        // End of trip: Park, or a long stop when the gear is unknown (same rule as the diary).
        val stopped = speedKmh < 1f
        stoppedSince = if (stopped) stoppedSince ?: nowMs else null
        val longStop = gear == GearState.UNKNOWN && (stoppedSince?.let { nowMs - it >= Thresholds.TRIP_END_STOP_MS } ?: false)
        if (active && !moving && (gear == GearState.PARK || longStop)) {
            active = false
            ended = true
        }

        return Journey(
            tripKm = (tripM / 1000.0).toFloat(),
            stars = stars, starSeq = starSeq, combo = combo, comboSeq = comboSeq, bestCombo = bestCombo,
            milestoneKm = milestone, biome = biome, biomesSeen = seen.size,
            destinationM = destinationM,
            active = active, tripSeq = tripSeq,
        )
    }

    private fun start() {
        active = true
        ended = false
        tripSeq++
        tripM = 0.0; segmentM = 0.0
        stars = 0; combo = 0; bestCombo = 0; milestone = 0
        // starSeq / comboSeq keep counting up across trips so the scene never replays an old star.
        biomeIdx = 0
        seen.clear(); seen += Biome.CITY
    }
}
