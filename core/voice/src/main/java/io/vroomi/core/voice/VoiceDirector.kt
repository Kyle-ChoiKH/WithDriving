package io.vroomi.core.voice

/** Anything that can say a line out loud. */
interface Speaker {
    fun speak(text: String)
    fun stop()
    fun release() {}
}

/** Voice context that decides whether Vroomi may speak right now. */
data class VoiceGate(
    val moving: Boolean,
    val muted: Boolean,
    val naviGuidanceActive: Boolean,
    val safetyWarningActive: Boolean,
)

enum class LinePriority { LOW, NORMAL, HIGH }

/**
 * Picks a line and enforces the speaking rules (master prompt §2.3 and §7):
 * - total silence while muted, while a safety warning is active, or while navigation guidance runs
 * - while moving: at most one line per 120 s (global cooldown)
 * - per-event cooldown so the same event never repeats quickly
 * - variants never repeat back-to-back
 * Pure logic; time is passed in so tests can control it.
 */
class VoiceDirector(
    private val speaker: Speaker,
    private val lines: (eventKey: String) -> List<String>,
    private val globalCooldownMovingMs: Long = 120_000L,
    private val globalCooldownParkedMs: Long = 15_000L,
    private val eventCooldownMs: Long = 300_000L,
) {
    private var lastSpokenAtMs = Long.MIN_VALUE / 2
    private val lastEventAt = HashMap<String, Long>()
    private val lastVariant = HashMap<String, Int>()
    var lastSpokenText: String? = null
        private set

    /** @return the text that was spoken, or null when the rules said "stay quiet". */
    fun request(eventKey: String, gate: VoiceGate, nowMs: Long, priority: LinePriority = LinePriority.NORMAL): String? {
        if (gate.muted || gate.safetyWarningActive || gate.naviGuidanceActive) return null
        val global = if (gate.moving) globalCooldownMovingMs else globalCooldownParkedMs
        // A direct answer to the driver (HIGH) is allowed through the global cooldown.
        if (priority != LinePriority.HIGH && nowMs - lastSpokenAtMs < global) return null
        val lastE = lastEventAt[eventKey]
        if (priority != LinePriority.HIGH && lastE != null && nowMs - lastE < eventCooldownMs) return null

        val options = lines(eventKey)
        if (options.isEmpty()) return null
        val prev = lastVariant[eventKey] ?: -1
        var idx = ((nowMs / 997) % options.size).toInt()
        if (options.size > 1 && idx == prev) idx = (idx + 1) % options.size
        var text = options[idx]
        if (gate.moving) text = shorten(text)

        speaker.speak(text)
        lastSpokenAtMs = nowMs
        lastEventAt[eventKey] = nowMs
        lastVariant[eventKey] = idx
        lastSpokenText = text
        return text
    }

    /**
     * A direct answer to something the driver just asked. Skips the cooldowns (the driver asked),
     * but still respects mute, safety warnings and navigation guidance.
     */
    fun answer(text: String, gate: VoiceGate, nowMs: Long): String? {
        if (gate.muted || gate.safetyWarningActive || gate.naviGuidanceActive) return null
        val t = if (gate.moving) shorten(text) else text
        speaker.speak(t)
        lastSpokenAtMs = nowMs
        lastSpokenText = t
        return t
    }

    fun silence() = speaker.stop()

    companion object {
        const val MAX_MOVING_SYLLABLES = 12

        /** Korean syllable cap while moving (Hangul syllables + digits count, spaces/punctuation do not). */
        fun shorten(text: String): String {
            var count = 0
            val sb = StringBuilder()
            for (ch in text) {
                val counts = ch in '가'..'힣' || ch.isLetterOrDigit()
                if (counts) {
                    if (count == MAX_MOVING_SYLLABLES) break
                    count++
                }
                sb.append(ch)
            }
            return sb.toString().trimEnd()
        }

        fun syllables(text: String): Int = text.count { it in '가'..'힣' || it.isLetterOrDigit() }
    }
}
