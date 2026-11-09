package io.vroomi.character.calibre

import io.vroomi.core.vehicle.model.DriveModeState

/** Scene themes. [hasCharacter]: a friend is on screen (KART only; every other theme is its own world), so voice lines follow the KART rules. New themes are appended (persisted by name). */
enum class SceneTheme(val hasCharacter: Boolean) {
    KART(true), CALIBRE(false), ENGINE(false),
    MUSIC_BOX(false), TERRARIUM(false), TREASURE_MAP(false), AQUARIUM(false), ORBIT(false), MARBLE(false), PAPER(false),
    PIXEL(false),
}

/** Which side of the watch faces the driver. CASEBACK is the default while driving. */
enum class CalibreView { CASEBACK, DIAL }

/** The metal and dial combination actually drawn. */
enum class CalibreTone { YELLOW_GOLD, ROSE_GOLD, RHODIUM_BLUE, BLACK_DLC }

/** User choice of finish (future paid items). AUTO follows the car's driving mode. */
enum class CalibreFinish(val label: String, val intro: String) {
    AUTO("주행 모드 연동", "에코는 로듐, 스포츠는 로즈골드, 일반은 옐로골드"),
    YELLOW_GOLD("옐로골드", "옐로골드 로터와 미드나이트 블루 문자판"),
    ROSE_GOLD("로즈골드", "로즈골드 로터와 실버 오팔린 문자판"),
    RHODIUM_BLUE("로듐 · 블루", "로듐 플레이트, 블루 스틸, 블루 에나멜 문자판"),
    BLACK_DLC("블랙 DLC", "검은 코팅 브리지, 금색 각인, 살몬 문자판");

    /** The tone to draw for this finish in [mode]. */
    fun tone(mode: DriveModeState): CalibreTone = when (this) {
        AUTO -> when (mode) {
            DriveModeState.ECO -> CalibreTone.RHODIUM_BLUE
            DriveModeState.SPORT -> CalibreTone.ROSE_GOLD
            else -> CalibreTone.YELLOW_GOLD
        }
        YELLOW_GOLD -> CalibreTone.YELLOW_GOLD
        ROSE_GOLD -> CalibreTone.ROSE_GOLD
        RHODIUM_BLUE -> CalibreTone.RHODIUM_BLUE
        BLACK_DLC -> CalibreTone.BLACK_DLC
    }
}
