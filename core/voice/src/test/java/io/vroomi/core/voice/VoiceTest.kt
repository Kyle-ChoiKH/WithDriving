package io.vroomi.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {
    private val cases: List<Pair<String, VoiceIntent>> = listOf(
        "시원하게 해줘" to VoiceIntent.Cooler,
        "너무 더워" to VoiceIntent.Cooler,
        "덥다 덥다" to VoiceIntent.Cooler,
        "차 안이 후덥지근해" to VoiceIntent.Cooler,
        "온도 내려줘" to VoiceIntent.Cooler,
        "온도 좀 낮춰 줘" to VoiceIntent.Cooler,
        "따뜻하게 해줘" to VoiceIntent.Warmer,
        "추워" to VoiceIntent.Warmer,
        "좀 쌀쌀하네" to VoiceIntent.Warmer,
        "온도 올려줘" to VoiceIntent.Warmer,
        "히터 틀어줘" to VoiceIntent.Warmer,
        "바람 세게" to VoiceIntent.FanUp,
        "바람 더 강하게 해줘" to VoiceIntent.FanUp,
        "풍량 올려" to VoiceIntent.FanUp,
        "바람 약하게" to VoiceIntent.FanDown,
        "바람 좀 줄여줘" to VoiceIntent.FanDown,
        "송풍 낮춰" to VoiceIntent.FanDown,
        "바람 꺼줘" to VoiceIntent.FanOff,
        "바람 그만" to VoiceIntent.FanOff,
        "공기 청정 켜줘" to VoiceIntent.AirCleanerOn,
        "공기정화 해줘" to VoiceIntent.AirCleanerOn,
        "브루미 조용히" to VoiceIntent.Mute,
        "브루미 쉿" to VoiceIntent.Mute,
        "음소거" to VoiceIntent.Mute,
        "브루미 숨어" to VoiceIntent.Hide,
        "브루미 구석으로 가" to VoiceIntent.Hide,
        "브루미 나와" to VoiceIntent.Show,
        "오늘 얼마나 달렸어?" to VoiceIntent.TripSummary,
        "몇 킬로 왔어" to VoiceIntent.TripSummary,
        "주행 점수 알려줘" to VoiceIntent.TripSummary,
        "온도 22도로 맞춰줘" to VoiceIntent.SetTemperature(22f),
        "에어컨 24.5도" to VoiceIntent.SetTemperature(24.5f),
        "노래 틀어줘" to VoiceIntent.Unknown,
        "" to VoiceIntent.Unknown,
    )

    @Test fun parsesKoreanCommands() {
        assertTrue(cases.size >= 30)
        cases.forEach { (text, expected) -> assertEquals("'$text'", expected, IntentParser.parse(text)) }
    }
}

class VoiceDirectorTest {
    private class FakeSpeaker : Speaker {
        val said = ArrayList<String>()
        override fun speak(text: String) { said += text }
        override fun stop() {}
    }

    private val lines = mapOf(
        "fan_up" to listOf("후~ 시원하게!", "바람 간다!", "슝슝 바람 배달!"),
        "rain" to listOf("우비 입었어요!"),
        "long" to listOf("오늘은 정말 정말 아주 아주 긴 문장을 말해 볼게요"),
    )
    private fun director(s: Speaker) = VoiceDirector(s, { lines[it].orEmpty() }, 120_000, 15_000, 300_000)
    private val moving = VoiceGate(moving = true, muted = false, naviGuidanceActive = false, safetyWarningActive = false)

    @Test fun silentWhenMutedNaviOrSafety() {
        val sp = FakeSpeaker(); val d = director(sp)
        assertNull(d.request("fan_up", moving.copy(muted = true), 0))
        assertNull(d.request("fan_up", moving.copy(naviGuidanceActive = true), 0))
        assertNull(d.request("fan_up", moving.copy(safetyWarningActive = true), 0))
        assertTrue(sp.said.isEmpty())
    }

    @Test fun globalCooldownWhileMoving() {
        val sp = FakeSpeaker(); val d = director(sp)
        assertNotNull(d.request("fan_up", moving, 0))
        assertNull(d.request("rain", moving, 60_000))      // within 120 s
        assertNotNull(d.request("rain", moving, 121_000))
    }

    @Test fun perEventCooldownAndNoBackToBackRepeat() {
        val sp = FakeSpeaker(); val d = director(sp)
        val parked = moving.copy(moving = false)
        val a = d.request("fan_up", parked, 0)
        assertNull(d.request("fan_up", parked, 20_000))     // event cooldown 5 min
        val b = d.request("fan_up", parked, 400_000)
        assertNotNull(a); assertNotNull(b)
        assertTrue(a != b)
    }

    @Test fun movingLinesAreShortened() {
        val sp = FakeSpeaker(); val d = director(sp)
        val t = d.request("long", moving, 0)!!
        assertTrue(VoiceDirector.syllables(t) <= VoiceDirector.MAX_MOVING_SYLLABLES)
    }

    @Test fun answersSkipCooldownButNotMute() {
        val sp = FakeSpeaker(); val d = director(sp)
        d.request("fan_up", moving, 0)
        assertNotNull(d.answer("지금은 못 바꿔요", moving, 1_000))
        assertNull(d.answer("지금은 못 바꿔요", moving.copy(muted = true), 2_000))
    }
}
