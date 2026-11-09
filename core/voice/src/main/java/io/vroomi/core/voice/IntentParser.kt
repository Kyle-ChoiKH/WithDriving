package io.vroomi.core.voice

/** What the driver asked Vroomi to do (rule-based, on-device, no cloud LLM). */
sealed interface VoiceIntent {
    data object Cooler : VoiceIntent
    data object Warmer : VoiceIntent
    data object FanUp : VoiceIntent
    data object FanDown : VoiceIntent
    data object FanOff : VoiceIntent
    data object AirCleanerOn : VoiceIntent
    data object Mute : VoiceIntent
    data object Hide : VoiceIntent
    data object Show : VoiceIntent
    data object TripSummary : VoiceIntent
    data class SetTemperature(val celsius: Float) : VoiceIntent
    data object Unknown : VoiceIntent
}

/**
 * Korean keyword + slot parser. Order matters: specific phrases are checked before generic ones
 * ("바람 꺼" before "바람", "온도 22도" before "시원").
 */
object IntentParser {
    private val tempRegex = Regex("""(\d{2})(?:\.(\d))?\s*도""")

    fun parse(utterance: String): VoiceIntent {
        val t = utterance.replace(" ", "").replace("~", "").replace(".", "").replace("?", "").lowercase()
        if (t.isBlank()) return VoiceIntent.Unknown

        // Explicit temperature, e.g. "온도 22도로 해줘", "에어컨 24도"
        tempRegex.find(utterance)?.let { m ->
            val whole = m.groupValues[1].toFloat()
            val frac = m.groupValues[2].takeIf { it.isNotEmpty() }?.toFloat()?.div(10f) ?: 0f
            val c = whole + frac
            if (c in 16f..32f && (t.contains("온도") || t.contains("에어컨") || t.contains("히터") || t.contains("도로") || t.contains("맞춰"))) {
                return VoiceIntent.SetTemperature(c)
            }
        }

        // Vroomi itself
        if (t.contains("브루미") || t.contains("vroomi")) {
            if (t.contains("조용") || t.contains("쉿") || t.contains("음소거") || t.contains("말하지마")) return VoiceIntent.Mute
            if (t.contains("숨어") || t.contains("숨기") || t.contains("작아") || t.contains("구석")) return VoiceIntent.Hide
            if (t.contains("나와") || t.contains("보여") || t.contains("커져")) return VoiceIntent.Show
        }
        if (t.contains("조용히해") || t.contains("음소거")) return VoiceIntent.Mute

        // Trip questions
        if ((t.contains("얼마나") || t.contains("몇킬로") || t.contains("몇키로")) && (t.contains("달렸") || t.contains("왔") || t.contains("갔") || t.contains("운전"))) {
            return VoiceIntent.TripSummary
        }
        if (t.contains("오늘주행") || t.contains("주행요약") || t.contains("운전점수") || t.contains("주행점수")) return VoiceIntent.TripSummary

        // Air cleaner
        if (t.contains("공기청정") || t.contains("공기정화") || t.contains("공기깨끗")) return VoiceIntent.AirCleanerOn

        // Fan
        val fan = t.contains("바람") || t.contains("풍량") || t.contains("송풍")
        if (fan) {
            if (t.contains("꺼") || t.contains("끄") || t.contains("멈춰") || t.contains("그만")) return VoiceIntent.FanOff
            if (t.contains("약") || t.contains("줄여") || t.contains("낮춰") || t.contains("살살") || t.contains("작게")) return VoiceIntent.FanDown
            if (t.contains("세게") || t.contains("강") || t.contains("올려") || t.contains("높여") || t.contains("많이") || t.contains("더")) return VoiceIntent.FanUp
        }

        // Temperature: "온도 (좀) 내려/낮춰" vs "온도 (좀) 올려/높여", then feelings
        val temp = t.contains("온도")
        if (temp && (t.contains("내려") || t.contains("낮춰") || t.contains("낮게"))) return VoiceIntent.Cooler
        if (temp && (t.contains("올려") || t.contains("높여") || t.contains("높게"))) return VoiceIntent.Warmer
        if (t.contains("시원") || t.contains("더워") || t.contains("덥다") || t.contains("더운") || t.contains("후덥") || t.contains("차갑게")) {
            return VoiceIntent.Cooler
        }
        if (t.contains("따뜻") || t.contains("추워") || t.contains("춥다") || t.contains("추운") || t.contains("쌀쌀") || t.contains("히터")) {
            return VoiceIntent.Warmer
        }
        return VoiceIntent.Unknown
    }
}
