package io.vroomi.character.theme

import io.vroomi.character.calibre.SceneTheme

/**
 * The five upgrade steps of every theme ([io.vroomi.character.render.SceneOptions.model] 0..4 = Lv.1..5) and their
 * names, exactly as the step tables of the theme overhaul design give them (the proposal lists them per theme).
 * The names are shown as they are in the model sheet and the grade tab.
 */
object ThemeModels {
    const val COUNT = 5

    /** The name of step [model] (0..4, clamped) of [theme]. */
    fun title(theme: SceneTheme, model: Int): String = names(theme)[model.coerceIn(0, COUNT - 1)]

    /** The step's tag: "Lv.1" .. "Lv.5". */
    fun tag(model: Int): String = "Lv.${model + 1}"

    private fun names(theme: SceneTheme): List<String> = when (theme) {
        SceneTheme.KART -> KART
        SceneTheme.CALIBRE -> CALIBRE
        SceneTheme.ENGINE -> ENGINE
        SceneTheme.MUSIC_BOX -> MUSIC_BOX
        SceneTheme.TERRARIUM -> TERRARIUM
        SceneTheme.TREASURE_MAP -> TREASURE_MAP
        SceneTheme.AQUARIUM -> AQUARIUM
        SceneTheme.ORBIT -> ORBIT
        SceneTheme.PAPER -> PAPER
        SceneTheme.PIXEL -> PIXEL
        SceneTheme.PLASMA -> PLASMA
        SceneTheme.HANGAR -> HANGAR
    }

    // Spec 5.1 – 5.12, in step order.
    private val CALIBRE = listOf("VR-01 스틸", "VR-01 데코", "VR-01 쿠션", "VR-01 토노 스켈레톤", "VR-01 투르비용")
    private val ENGINE = listOf("VE-4 SOHC", "VE-4 DOHC", "VE-4T 터보", "VE-4T 직분사", "VE-4R 레이스")
    private val MUSIC_BOX = listOf("18음 소형 상자", "18음 래커 상자", "36음 상감 상자", "50음 이중 빗", "72음 종 오르골")
    private val AQUARIUM = listOf("모래 여울", "수초 정원", "산호초", "난파선", "켈프 숲")
    private val TERRARIUM = listOf("작은 유리병", "코르크 병", "큰 플라스크", "유리 큐브", "유리 온실 돔")
    private val KART = listOf("동네 길", "벚꽃 강변", "네온 야경", "오로라 설원", "퍼레이드 꼬마차")
    private val TREASURE_MAP = listOf("먹 해도", "나침반 해도", "수채 지도", "장식 지도", "금박 지도")
    private val ORBIT = listOf("녹색 벡터", "달 궤도", "2색 관제", "고리 행성", "우주정거장")
    private val PAPER = listOf("2도 인쇄", "3도 인쇄", "3층 팝업", "당김 탭", "특별판")
    private val PIXEL = listOf("탐사차", "화물 탐사차", "호버 탐사차", "제트 호버", "작은 탐사선")
    private val PLASMA = listOf("플라즈마 볼", "네온 계수기", "진공관 앰프", "오실로스코프", "야곱의 사다리")
    private val HANGAR = listOf("HX-1 작업형", "HX-2 정비형", "HX-3 구조형", "HX-4 극지형", "HX-5 탐사형")
}
