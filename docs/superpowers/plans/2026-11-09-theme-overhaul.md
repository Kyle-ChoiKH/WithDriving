# 테마 전면 개편 + 안전운전 등급 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- 테마 11개를 12개로 바꾼다(구슬 장치 삭제, 플라즈마·로봇 격납고 추가).
- 실물 테마 5개를 실사틱하게 다시 그린다.
- 테마는 모두 처음부터 열려 있고, 안전운전 등급(브론즈~다이아몬드)이 오를 때마다 모든 테마가 그 테마다운 방식으로 한 단계씩 업그레이드된다(12테마 × 5단계).

**Architecture:**
- 등급·단계 규칙은 app 모듈의 순수 Kotlin 객체(`SafetyGrade`, `ThemeAccess`)에 둔다.
- 단계는 `SceneOptions.model`(0~4)로 화가까지 내려간다. 화가는 단계별 레이어를 쌓아 그린다(단계 n = 단계 n−1 + 새 요소).
- 공용 파일(SceneTheme·Catalog·Registry·Ambience·Entrances·strings·Settings)은 Phase A에서 한 번에 바꾼다. 그 뒤 테마별 작업(Phase B)은 자기 파일만 건드려 병렬로 진행한다.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, Paparazzi 1.3.5(스냅샷), JUnit4, Gradle(build.ps1로 ASCII 경로 사본에서 빌드).

**Spec:** `docs/superpowers/specs/2026-11-09-theme-overhaul-design.md` — 구현자는 계획과 함께 반드시 읽는다. 테마별 단계 이름과 단계마다 새로 생기는 것은 spec 5장 표가 정답이다.

## Global Constraints

- 차는 읽기만 한다. 테마·화가 코드는 차량 제공자(`core/vehicle/provider`)를 import하지 않는다.
- 번쩍임은 FlashGuard를 거쳐 영역당 0.34초 간격(초당 3회 이하)으로 제한한다. 플라즈마 가닥 밝기 흔들림은 ±15% 이내의 부드러운 잡음으로 하고, 섬광·번개 연출은 금지한다.
- 칩·장면 글자에 `ThemeSim.FORBIDDEN_WORDS`("점검","경고","고장","℃","°C","기압","압력","kPa","bar")를 쓰지 않는다. 엔진 수치에는 "추정"을 붙인다.
- 주행 중 장면 안 글자는 24sp 이상만 쓴다. 앱 버튼 구역은 `ChromeZones`(왼쪽 위·오른쪽 아래)를 피한다.
- 110 km/h 이상에서는 모든 연출이 상한이다(`Thresholds.SPRINT_CAP_KMH`). 과속을 보상하지 않는다.
- 그림은 코드로만 그린다(외부 이미지 없음). 정적 질감은 `LayerSlot`에 캐시하고 캐시 키에 단계를 포함한다(공용 `LayerCache.BUDGET_BYTES` 48 MB).
- IP:
  - 브랜드 시계 형상(탱크·산토스, 로마 숫자+청색 카보숑)을 쓰지 않는다.
  - 건담 고유 요소(V자 뿔, 입 모양 페이스 마스크, 흰·파랑·빨강·노랑 배색, 모노아이, 무기)를 쓰지 않는다.
  - 상표 "Nixie"는 쓰지 않는다.
- 실사틱 = 빛 방향 하나의 음영 + 반사광 1~2줄 + 부드러운 접지 그림자 + 저대비 질감. 고대비 잔무늬, 의미 없는 장식 부품, 과포화 글로우는 금지한다.
- 단계 사다리: 단계 n은 단계 n−1과 눈에 보이게 달라야 하고, 그 차이는 spec 5장 표의 "새로 생기는 것"이다. 최고 단계(5)도 그리기 6 ms 안팎을 지킨다.
- 버전: `versionName "1.7.0-contest"`, `versionCode 8`.
- 등급 이름·조건(정확히):

  | 등급 | 이름 | 안전 거리 | 최근 점수 |
  |---|---|---|---|
  | 1 | 브론즈 | 0 km | — |
  | 2 | 실버 | 10 km | 75 |
  | 3 | 골드 | 30 km | 80 |
  | 4 | 플래티넘 | 60 km | 85 |
  | 5 | 다이아몬드 | 100 km | 90 |

- 테마 12개는 모두 처음부터 열려 있다. 등급 n → 모든 테마의 단계 1~n이 열린다(`model` 0..n−1).
- 빌드·테스트:
  - Windows(이 PC): `powershell -ExecutionPolicy Bypass -File .\build.ps1 -Work C:\vroomi-build\<X>\Vroomi -Task "<gradle tasks>"`. 작업마다 `<X>`를 다르게 한다. 동시 Gradle 빌드는 2개까지(메모리 32 GB).
  - Linux(클라우드): `./gradlew --no-daemon --console=plain <tasks>`. 준비는 `docs/superpowers/plans/cloud-agent-brief.md` 1장.
  - Paparazzi 결과는 빌드 사본의 `src/test/snapshots/images`에 생긴다. Windows에서는 다음 build.ps1의 robocopy /MIR가 지우므로 기록 직후 복사한다.
- git·GitHub(비공개 저장소 `Kyle-ChoiKH/WithDriving`):
  - 작업마다 브랜치 하나. PR #1이 main에 합쳐지기 전에는 `origin/phase-a/foundation`에서, 합쳐진 뒤에는 `origin/main`에서 딴다.
  - 브랜치 이름: Task 1+4 `phase-a/grade-ui`, Task 5~16 `theme/<theme 소문자>`(예: `theme/music_box`), Task 17 `phase-c/integration`.
  - main에 직접 push하지 않는다. 작업 끝에 PR(base main)을 연다. 병합은 사용자가 승인한 뒤에만 한다(자동 권한 검사가 검토 없는 병합을 막는다).
  - 커밋 끝줄 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, PR 본문 끝줄 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. 작성자는 GitHub noreply 메일. 실명·연락처·비밀(keystore, jks)은 커밋하지 않는다.
  - 기록한 PNG 중 작업이 이름 붙인 것만 `docs/screenshots/<짧은 이름>.png`로 커밋한다. `src/test/snapshots/`는 커밋하지 않는다.
  - CI(`.github/workflows/ci.yml`: testDebugUnitTest + assembleDebug)가 PR마다 통과해야 한다.
- 각 작업의 관문은 전체 테스트 통과 + 기록한 스냅샷을 직접 보고 확인하는 것이다.

## Review Focus

1. **v1.6에서 구슬 장치(MARBLE)를 쓰던 사용자:** 저장값 `sceneTheme=MARBLE`이면 오류 없이 꼬마차로 시작해야 한다 → Task 2 `parseThemeFallsBackToKart`.
2. **v1.6에서 업데이트한 사용자:** 기존 주행 기록으로 첫 실행 때 등급을 바로 계산해서, 이미 많이 달린 사람이 브론즈로 시작하지 않아야 한다 → Task 1 `gradeFromExistingTrips`.
3. **거리 0인 주행만 있는 기록:** 최근 점수 계산에서 0으로 나누지 않아야 한다 → Task 1 `zeroDistanceTripsDoNotDivideByZero`.
4. **다이어리 초기화로 등급이 내려간 뒤 예전에 고른 높은 단계:** 지금 열린 최고 단계로 낮춰 써야 한다 → Task 1 `storedModelClampsToUnlocked`.
5. **데모 드라이브 중 로봇 조립:** 실제 주행이 아니므로 저장되는 진행이 늘면 안 된다 → Task 11 `demoDoesNotAdvanceSavedAssembly`.

## 실행 현황 (2026-11-09 갱신)

| Task | 상태 | 브랜치 / PR |
|---|---|---|
| 2, 3 | 완료, CI 통과, 병합 승인 대기 | `phase-a/foundation` / PR #1 |
| 1 + 4 | 진행 중(이 PC) | `phase-a/grade-ui` |
| 5 | 진행 중(이 PC) | `theme/calibre` |
| 6 ~ 16 | 대기 | 클라우드가 저장소에 접근할 수 있으면 클라우드에서 한꺼번에. 아니면 이 PC에서 2개씩 |
| 17 | 대기 | 모든 PR이 합쳐진 뒤 |

실행 순서:
- Task 1+4와 Phase B는 서로 독립이다. Phase B는 Task 3의 `SceneOptions.model`만 쓴다.
- 우선순위는 사용자가 직접 짚은 테마 먼저: 5 시계 → 6 엔진룸 → 7 오르골 → 10 플라즈마 → 11 격납고. 그다음 8, 9, 12~16.

## 병렬 작업 규칙 (Phase B)

- 사다리 그림 헬퍼(1000 × 1875, 8:3 띠 5개, 띠마다 "Lv.n")는 **각 테마 스냅샷 테스트 파일 안에 private으로** 만든다. 공용 `KitShots.kt`는 고치지 않는다. 통합 때 하나로 모은다(Task 17).
- `PassageCostTest`는 고치지 않는다. 단계 5 비용 항목은 Task 17에서 한꺼번에 넣는다. 각 작업은 단계 5 그리기가 6 ms 안팎인지 스스로 잰다.
- `Ambience.kt`는 자기 테마 줄만 고친다.
- `theme_luminance.json`은 자기 브랜치에서 다시 만들어 커밋해도 된다. 충돌하면 Task 17에서 다시 만든다.
- `ScenarioGoldenTest`(Task 10·11 둘 다 추가)는 Task 17에서 손으로 합친다.
- 그 밖의 공용 파일(SceneTheme, ThemeCatalog, ThemeRegistry, Entrances, strings.xml, SettingsStore, SceneOptions)이 필요하면 고치지 말고 보고서에 적는다.
- 보고서 형식은 `cloud-agent-brief.md` 4장. 단계마다 "이전 단계와 달라진 점"을 그림에서 실제로 보이는 대로 한 줄씩 적는다.

---

## Phase A — 기반 (순서대로, 원본 폴더 하나에서)

### Task 1: 안전운전 등급·단계 규칙 (순수 로직)

**Files:**
- Create: `app/src/main/java/io/vroomi/app/progress/SafetyGrade.kt`, `app/src/main/java/io/vroomi/app/progress/ThemeAccess.kt`
- Modify: `app/src/main/java/io/vroomi/app/diary/TripModels.kt`(`object Bond` 삭제)
- Test: `app/src/test/java/io/vroomi/app/progress/SafetyGradeTest.kt`, `ThemeAccessTest.kt`. `DiaryTest.kt`의 `BondTest`는 삭제한다.

**Interfaces:**
- Produces:
  - `enum class Grade(val level: Int, val label: String, val needKm: Float, val needScore: Int)`:
    - 값: BRONZE(1,"브론즈",0f,0), SILVER(2,"실버",10f,75), GOLD(3,"골드",30f,80), PLATINUM(4,"플래티넘",60f,85), DIAMOND(5,"다이아몬드",100f,90)
    - `fun next(): Grade?`
    - `companion fun of(level: Int): Grade`(1~5로 자름)
  - `object SafetyGrade`:
    - `safeKm(trips: List<TripRecord>): Float` — 점수 70 이상, 데모 제외
    - `recentScore(trips): Int?` — 데모가 아닌 최근 10회의 거리 가중 평균을 반올림. 기록이 없으면 null, 총거리 0이면 단순 평균
    - `earned(trips): Grade` — 거리와 점수 조건을 모두 만족하는 가장 높은 등급
    - `current(trips, reached: Int): Grade` = max(earned, of(reached))
    - `totalStars(trips): Int`
    - `kmToNext(trips, reached: Int): Float?`
    - `outfits(grade): Set<Accessory>` — `Accessory.wardrobe && unlockLevel <= grade.level`
  - `object ThemeAccess`:
    - `const val STEPS = 5`
    - `maxModel(grade: Grade): Int` = grade.level − 1
    - `modelUnlockGrade(model: Int): Grade` = Grade.of(model + 1)
    - `effectiveModel(grade, choice: Int?): Int` — 고른 값이 있으면 maxModel까지 자르고, 없으면 maxModel

- [ ] **Step 1: 실패하는 테스트 작성** (`SafetyGradeTest`, 헬퍼 `trip(km, score, demo=false)`)

```kotlin
@Test fun bronzeWithNoTrips() { assertEquals(Grade.BRONZE, SafetyGrade.earned(emptyList())); assertNull(SafetyGrade.recentScore(emptyList())) }
@Test fun silverNeedsBothDistanceAndScore() {
    assertEquals(Grade.BRONZE, SafetyGrade.earned(listOf(trip(12f, 72))))   // 거리 OK, 점수 72 < 75
    assertEquals(Grade.SILVER, SafetyGrade.earned(listOf(trip(12f, 90))))
}
@Test fun recentScoreIsDistanceWeightedOverLastTen() {
    val trips = listOf(trip(30f, 90), trip(10f, 50)) + List(10) { trip(1f, 10) }  // 최신 순: 앞 10개만
    assertEquals(((30*90 + 10*50 + 8*10) / 48f).roundToInt(), SafetyGrade.recentScore(trips))
}
@Test fun zeroDistanceTripsDoNotDivideByZero() { assertEquals(80, SafetyGrade.recentScore(listOf(trip(0f, 70), trip(0f, 90)))) }
@Test fun demoTripsNeverCount() { assertEquals(Grade.BRONZE, SafetyGrade.earned(listOf(trip(200f, 100, demo = true)))) }
@Test fun gradeNeverDropsBelowReached() { assertEquals(Grade.GOLD, SafetyGrade.current(emptyList(), reached = 3)) }
@Test fun gradeFromExistingTrips() { assertEquals(Grade.DIAMOND, SafetyGrade.earned(List(10) { trip(11f, 95) })) }
@Test fun outfitsFollowGrade() { assertEquals(setOf(Accessory.SCARF, Accessory.GOGGLES, Accessory.BEANIE), SafetyGrade.outfits(Grade.GOLD)) }
```

`ThemeAccessTest`:

```kotlin
@Test fun stepEqualsGrade() { assertEquals(listOf(0, 1, 2, 3, 4), Grade.entries.map { ThemeAccess.maxModel(it) }) }
@Test fun unlockGradeOfStep() { assertEquals(Grade.PLATINUM, ThemeAccess.modelUnlockGrade(3)) }
@Test fun storedModelClampsToUnlocked() { assertEquals(0, ThemeAccess.effectiveModel(Grade.BRONZE, choice = 4)) }
@Test fun noChoiceUsesBestUnlocked() { assertEquals(3, ThemeAccess.effectiveModel(Grade.PLATINUM, choice = null)) }
@Test fun lowerChoiceIsKept() { assertEquals(1, ThemeAccess.effectiveModel(Grade.DIAMOND, choice = 1)) }
```

- [ ] **Step 2: 실패 확인.** `build.ps1 -Task ":app:testDebugUnitTest --tests io.vroomi.app.progress.*"` → 컴파일 오류(SafetyGrade 없음)
- [ ] **Step 3: 구현.** Interfaces 그대로 만든다. `Bond`를 쓰던 곳을 `SafetyGrade`로 바꾼다:
  - `VroomiRuntime` 175·278행(옷 필터는 `SafetyGrade.outfits(grade)`)
  - `AppRoot` 720·748행
  - `Panels.WardrobePanel`
- [ ] **Step 4: 통과 확인.** 같은 명령 → PASS

### Task 2: 테마 목록 정리 — MARBLE 삭제, PLASMA·HANGAR 뼈대 추가

**Files:**
- Modify: `character/.../calibre/CalibreModels.kt`(`SceneTheme`: KART(true), CALIBRE, ENGINE, MUSIC_BOX, TERRARIUM, TREASURE_MAP, AQUARIUM, ORBIT, PAPER, PIXEL, PLASMA, HANGAR — 나머지 false)
- Delete:
  - `character/.../marble/`, `character/.../render/marble/`
  - `character/src/test/.../marble/MarbleTest.kt`, `render/marble/MarbleSnapshotTest.kt`
  - `app/src/main/assets/scenarios/marble_showcase.json`
- Create:
  - `character/.../plasma/PlasmaSim.kt`, `character/.../render/plasma/PlasmaPainter.kt`
  - `character/.../hangar/HangarSim.kt`, `character/.../render/hangar/HangarPainter.kt`
  - 뼈대: 어두운 배경 + 테마 이름이 보이는 최소 장면, `allChips` 1개
- Modify:
  - `ThemeCatalog`, `ThemeSims`(PLASMA·HANGAR 추가, MARBLE 제거)
  - `ThemeRegistry`(MARBLE 제거. PLASMA·HANGAR는 MACHINE 분류, `showcase = "plasma_showcase"/"hangar_showcase"`. 순서: KART, PAPER, CALIBRE, ENGINE, MUSIC_BOX, PLASMA, HANGAR, TERRARIUM, AQUARIUM, ORBIT, TREASURE_MAP, PIXEL)
  - `Ambience.kt`(MARBLE 줄 삭제, PLASMA tint `0xFFB45CFF`·backdrop `0xFF0E1016`, HANGAR tint `0xFFF07A1A`·backdrop `0xFF1B1D20`, 밝기는 측정 후 갱신)
  - `Entrances.kt`/`EntranceDraw.kt`(MARBLE 분기 삭제. PLASMA는 중심에서 퍼지는 원형 입장, HANGAR는 위에서 아래로 셔터 입장. 기존 원형·쓸기 헬퍼를 재사용한다)
  - `strings.xml`(marble 3개 삭제. 추가: `theme_plasma` "플라즈마 · 고전압 실험실", `theme_plasma_hint` "차속만큼 보랏빛 전류가 일렁이고, 깜빡이 쪽으로 모여요", `plasma_note`, `theme_hangar` "로봇 격납고", `theme_hangar_hint` "달린 거리만큼 나만의 거대 로봇이 조립돼요", `hangar_note`)
  - `SettingsStore`(테마 이름 파싱을 `internal fun parseTheme(name: String?): SceneTheme`로 빼서 테스트)
  - MARBLE을 참조하는 테스트(ScenarioGoldenTest, PassageSnapshotTest, ScreensSnapshotTest, ThemePassageTest, KitReelTest, PassageCostTest, ThemeLuminanceSnapshotTest)
  - `app/src/test/resources/theme_luminance.json`(VROOMI_LUMA=1로 다시 생성)
- Create: `app/src/main/assets/scenarios/plasma_showcase.json`, `hangar_showcase.json`(뼈대: 90초 순항·깜빡이·브레이크·별 몇 개, `"theme"` 필드 포함)

**Interfaces:**
- Produces:
  - `SceneTheme.PLASMA`, `SceneTheme.HANGAR`
  - `class PlasmaSim : ThemeSim`, `internal class PlasmaPainter : ThemePainter<PlasmaSim>`
  - `class HangarSim : ThemeSim`, `internal class HangarPainter : ThemePainter<HangarSim>`
  - `parseTheme(name: String?): SceneTheme`

- [ ] **Step 1: 실패하는 테스트.** `ThemeContractTest`에 추가:

```kotlin
@Test fun twelveThemesWithoutMarble() {
    assertEquals(12, ThemeRegistry.all.size)
    assertFalse(SceneTheme.entries.any { it.name == "MARBLE" })
    assertTrue(ThemeRegistry.has(SceneTheme.PLASMA) && ThemeRegistry.has(SceneTheme.HANGAR))
}
@Test fun parseThemeFallsBackToKart() { assertEquals(SceneTheme.KART, parseTheme("MARBLE")); assertEquals(SceneTheme.KART, parseTheme(null)) }
```

- [ ] **Step 2: 실패 확인.** `build.ps1 -Task ":app:testDebugUnitTest --tests io.vroomi.app.ThemeContractTest"` → FAIL
- [ ] **Step 3: 구현.** 위 파일들을 바꾼 뒤 `grep -rn "MARBLE\|Marble\|marble"`로 main·test에 남은 것이 0개인지 확인한다(문서는 Task 17).
- [ ] **Step 4: 밝기 측정값 재생성.** `$env:VROOMI_LUMA=1; build.ps1 -Task ":app:testDebugUnitTest --tests io.vroomi.app.ui.ThemeLuminanceSnapshotTest"` → 사본의 `theme_luminance.json`을 원본으로 복사한다.
- [ ] **Step 5: 전체 단위 테스트.** `build.ps1 -Task "testDebugUnitTest"` → 0 failures(Task 1 테스트 포함)

### Task 3: 단계 배관 (SceneOptions.model, 시나리오 "model", 설정 저장, 단계 이름)

**Files:**
- Create: `character/src/main/java/io/vroomi/character/theme/ThemeModels.kt`
- Modify:
  - `character/.../render/VroomiScene.kt`(`SceneOptions`에 `val model: Int = 0` 추가, 주석 "업그레이드 단계 0..4 = Lv.1..5")
  - `character/.../render/calibre/CalibreScene.kt`, `render/engine/EngineScene.kt`(SceneOptions.model을 런타임·화가까지 전달)
  - `core/vehicle/.../sim/Scenario.kt`(`val model: Int? = null`, JSON 파싱)
  - `app/.../settings/SettingsStore.kt`(`gradeReached: Int = 1`을 "gradeReached" 키로, `models: Map<SceneTheme, Int>`를 "model.<THEME>" 키로, `devAllGrades: Boolean = false`는 저장 안 함)
  - `app/.../runtime/VroomiRuntime.kt`:
    - 주행 기록 저장 시 `gradeReached = max(gradeReached, SafetyGrade.earned(trips).level)`
    - 다이어리 초기화 시 `gradeReached = 1`
    - 첫 실행(키 없음)에는 기존 기록에서 계산
    - 데모 중 `demoModel: StateFlow<Int?>`(시나리오 model ?: 4)
  - `AppRoot.kt`/`SceneHost`: SceneOptions를 만들 때 `model = demoModel ?: ThemeAccess.effectiveModel(grade, settings.models[theme])`. 개발자 "모든 등급" 상태면 grade=DIAMOND로 계산한다.
  - 기존 쇼케이스 JSON(삭제한 marble 제외)에 `"model": 4` 추가
- Test:
  - `core/vehicle/src/test/.../ScenarioTest.kt`(`modelFieldParsed`)
  - `character/src/test/.../theme/ThemeKitTest.kt`(`everyThemeHasFiveStepNames`)

**Interfaces:**
- Produces:
  - `object ThemeModels`:
    - `const val COUNT = 5`
    - `fun title(theme: SceneTheme, model: Int): String` — spec 5장 각 표의 "이름" 열 그대로(예: CALIBRE 0 → "VR-01 스틸", HANGAR 4 → "HX-5 탐사형")
    - `fun tag(model: Int): String` = "Lv.${model + 1}"
  - `SceneOptions.model: Int`
  - `Scenario.model: Int?`
  - `Settings.gradeReached`, `Settings.models`, `Settings.devAllGrades`
  - `VroomiRuntime.demoModel: StateFlow<Int?>`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// ScenarioTest — 파서 함수 이름은 Scenario.kt를 보고 맞춘다
@Test fun modelFieldParsed() { assertEquals(4, parseScenario("""{"id":"x","title":"t","durationS":1,"model":4,"keyframes":[]}""").model) }
// ThemeKitTest
@Test fun everyThemeHasFiveStepNames() {
    SceneTheme.entries.forEach { t ->
        val names = (0 until ThemeModels.COUNT).map { ThemeModels.title(t, it) }
        assertTrue(names.all { it.isNotBlank() }); assertEquals(5, names.toSet().size)
    }
}
```

- [ ] **Step 2: 실패 확인.** `build.ps1 -Task ":core:vehicle:testDebugUnitTest :character:testDebugUnitTest --tests *ThemeKitTest*"` → FAIL
- [ ] **Step 3: 구현.** 위 Files대로 만든다.
- [ ] **Step 4: 통과 확인.** `build.ps1 -Task "testDebugUnitTest"` → 0 failures

### Task 4: 화면 — 테마·등급 탭, 등급 화면, 단계 고르기

**Files:**
- Create: `app/src/main/java/io/vroomi/app/ui/GradePanel.kt`(`GradePanel(rt)` + 상태 없는 `GradeContent`)
- Modify:
  - `ui/Chrome.kt`:
    - `Tab.FRIENDS` → `Tab.THEMES`(label `tab_themes`)
    - `Tab.WARDROBE` → `Tab.GRADE`(label `tab_grade`, 아이콘 `VIcons.Medal` 신규)
    - `BondBadge` → `GradeBadge(grade: Grade, stars: Int, progress: Float, detail: String)`
  - `ui/design/Icons.kt`(Medal: 리본 두 줄 + 원 메달, 기존 아이콘과 같은 선 굵기)
  - `ui/AppRoot.kt`(탭 이름, 등급 배지)
  - `ui/Gallery.kt`(잠금 없음. 카드 오른쪽 위에 지금 적용 단계 `Tag` "Lv.n")
  - `ui/ThemeRegistry.kt`(`ThemeOption.MODEL`을 모든 테마의 첫 칩으로. 칩 문구 "업그레이드 · Lv.3 VR-01 쿠션")
  - `ui/Sheets.kt`(단계 시트: 5줄, 각 줄에 "Lv.n"·단계 이름·열리는 등급. 열리지 않은 줄은 누르면 무대 미리 보기만 하고 저장하지 않음)
  - `ui/Panels.kt`(WardrobeContent는 GradeContent 안 "꼬마차 옷장" 섹션으로 옮김)
  - `ui/DebugPanel.kt`("모든 등급 미리 열기" 스위치 → `devAllGrades`)
  - `strings.xml`:
    - `tab_friends`·`tab_wardrobe`를 `tab_themes` "테마"·`tab_grade` "등급"으로
    - `bond_level` 삭제, 추가: `grade_badge` "%1$s 등급", `locked_grade` "%1$s 등급에 열려요", `grade_score` "최근 안전 점수", `grade_safe_km` "안전 거리 %1$.1f km", `grade_next` "다음 등급 %1$s", `grade_need_km` "안전 거리 %1$.0f km", `grade_need_score` "최근 점수 %1$d 이상", `grade_rule` "급제동 −6 · 급가속 −4 · 110 km/h 초과 1분당 −2 (100점 만점)", `grade_next_upgrade` "%1$s이(가) 되면: %2$s", `grade_ladder` "등급별 업그레이드", `grade_outfits` "꼬마차 옷장", `chip_model` "업그레이드 · %1$s %2$s", `dev_all_grades` "모든 등급 미리 열기 (저장 안 함)"
    - `settings_reset_sub`의 "우정 레벨" → "등급", `wardrobe_hint` → "등급이 오르면 새 옷이 열려요.", `friends_hint`의 "옷장" → "등급"
- Test:
  - `app/src/test/.../ui/ScreensSnapshotTest.kt`: `grade_bronze`, `grade_gold`, `grade_diamond`, `gallery_steps_gold`, `sheet_model_calibre_silver`, `home_badge_gold` 추가
  - `ThemeContractTest`(`everyThemeOffersModelChip`)

**Interfaces:**
- Consumes: Task 1 `Grade`, `SafetyGrade`, `ThemeAccess`; Task 3 `ThemeModels`, `Settings.models`, `devAllGrades`
- Produces: `GradeContent(grade: Grade, score: Int?, safeKm: Float, kmToNext: Float?, theme: SceneTheme, nextPreview: @Composable (Modifier) -> Unit, worn: Set<Accessory>, onToggle: (Accessory, Boolean) -> Unit)`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun everyThemeOffersModelChip() { ThemeRegistry.all.forEach { assertEquals(ThemeOption.MODEL, it.options.first()) } }
```

ScreensSnapshotTest에 새 스냅샷 6개(이름은 위)를 넣는다.
- [ ] **Step 2: 실패 확인.** `build.ps1 -Task ":app:testDebugUnitTest --tests io.vroomi.app.ThemeContractTest"` → FAIL
- [ ] **Step 3: 구현.**
  - 기존 `Page`·`Section`·`ProgressBar`·`Tag` 부품을 쓴다.
  - 메달 색: 브론즈 #B07A4A, 실버 #A9B1BA, 골드 #D4A53C, 플래티넘 #8FB7C9, 다이아몬드 #7FD3F0(외곽선만, 넓은 면 채움 금지).
  - "다음 업그레이드 미리 보기"는 지금 테마의 다음 단계를 `SceneStillRenderer`/`ThemeSceneStill`로 그린 8:3 그림 한 장이다.
  - "등급별 업그레이드"는 5줄로, 각 줄에 메달·등급 이름·지금 테마의 그 단계 이름·그 등급의 옷 이름을 넣는다.
- [ ] **Step 4: 통과·확인.** `build.ps1 -Task "testDebugUnitTest :app:recordPaparazziDebug --tests io.vroomi.app.ui.ScreensSnapshotTest"` → PASS. 새 PNG 6장을 복사해 Read로 직접 본다: 글자 잘림 없음, 단계 태그와 잠긴 단계 문구가 읽힘, 어두운 테마 바탕에서도 대비 충분.
- [ ] **Step 5: 작은 화면.** `VROOMI_SCREEN=1024x600@160`으로 같은 스냅샷을 다시 그려 등급 화면이 한 열로 접히는지 본다.

---

## Phase B — 테마별 단계 사다리 (서로 독립, 테마별 사본에서 병렬)

공통 단계(Task 5~16 모두 적용):

- **(a) 스냅샷 테스트 먼저:**
  - 테마의 스냅샷 테스트 파일에 `<theme>_lv1_day` … `<theme>_lv5_day`, `<theme>_lv5_night`, `<theme>_lv3_blinker_left`, `<theme>_lv3_brake`를 추가한다. `KitShots.wide()`, 주행 50 km/h, 별 3개, 시뮬레이션 6~12초.
  - `<theme>_ladder`도 추가한다: 1000 × 1875 세로 캔버스에 단계 1~5를 8:3 띠 5개로 위에서 아래로 그린 비교 그림. 띠마다 왼쪽 위에 작은 "Lv.n" 표시. 헬퍼는 자기 테스트 파일 안의 private 함수로 만든다(병렬 작업 규칙).
  - 단계는 `SceneOptions(hourOfDay=14f, model=n)`으로 넘긴다. 기존 스냅샷은 지우지 않고 새 모습으로 다시 기록한다(기본 단계 0).
- **(b) 구현:** spec 5장의 해당 절과 표 그대로. 단계 n의 그림 = 단계 n−1 그림 + 표의 "새로 생기는 것"(형태가 바뀌는 단계는 교체).
- **(c) 기록·직접 보기:** `build.ps1 -Work C:\vroomi-build\<X>\Vroomi -Task ":character:recordPaparazziDebug --tests <TestClass>"`로 기록하고, PNG를 scratchpad로 복사해 Read로 본다. 사다리 그림부터 보고, 아래를 확인한다:
  - 단계마다 새 요소가 한눈에 보이는가
  - 다른 테마와 확실히 구별되는가
  - 실사틱하되 과하지 않은가
  - 앱 버튼 구역을 피했는가
  - 밤에도 읽히는가
- **(d) 반복:** 고칠 점이 있으면 (b)~(c)를 반복한다. 최소 2회 본다.
- **(e) 마무리 테스트:** 전체 `testDebugUnitTest` → 0 failures. 단계 5 그리기 비용은 스스로 재서 보고서에 적는다(`PassageCostTest` 항목은 Task 17).
- **(f) 올리기:** 지정 PNG를 `docs/screenshots/`에 복사 → 커밋 → `git push -u origin theme/<theme>` → `gh pr create -R Kyle-ChoiKH/WithDriving --base main`(제목 "Theme: <THEME> … (plan Task N)", 본문에 단계별 달라진 점·테스트 결과·스크린샷 이름) → CI 통과 확인.

### Task 5: 오토매틱 시계 — 밝은 공방 + 사다리 (spec 5.1)

**Files:**
- Modify: `character/.../render/calibre/CalibreStudio.kt`(밝은 배경), `CalibreShapes.kt`(원형·쿠션·토노 외형), `CalibrePainter.kt`(단계별 브리지 장식·투각), `CalibreDial.kt`(기요셰·스켈레톤 문자판), `CalibrePalette.kt`, `CalibreFinishCache.kt`(캐시 키에 단계)
- Create: `character/.../render/calibre/CalibreTourbillon.kt`(단계 5: 6시 플라잉 투르비용, 케이지 1분 1바퀴, 밸런스 4Hz는 `CalibreTime` 사용)
- Test: `CalibreSnapshotTest`, `CalibreDetailSnapshotTest`(1000×1000: `calibre_detail_lv3_cushion`, `calibre_detail_lv5_tourbillon`)

**완료 조건:**
- 배경 평균 밝기는 0.6 이상(ThemeLuminanceSnapshotTest 값), 밤도 0.3 이상.
- 시계 아래에 부드러운 접지 그림자가 있다.
- 아래 글줄은 진한 회색이다.
- 마감 5종은 모든 단계에서 동작한다.

- [ ] (a) 스냅샷 추가 → (b) 구현 → (c) 기록·보기 → (d) 반복 → (e) 테스트

### Task 6: 엔진룸 — 절개 엔진 + 계측 화면 + 사다리 (spec 5.2)

**Files:**
- Modify: `character/.../render/engine/EnginePainter.kt`, `EngineLayers.kt`(재질 질감 캐시: 주물 반점, 가공 자국, 절단면 해칭), `EngineLayout.kt`(계측 패널 자리: 오른쪽 32%, 화면이 좁으면 아래)
- Create:
  - `character/.../render/engine/EngineTelemetry.kt`(계측 화면 전용 화가, 단계별 항목은 spec 5.2 표)
  - `character/.../render/engine/EngineType.kt`(표제 `FontFamily(android.graphics.Typeface.create("sans-serif-condensed", Typeface.NORMAL))`, 숫자 `FontFamily.Monospace`, 크기 3단계)
- Modify: `character/.../engine/EngineGeometry.kt`(순수 함수 추가: `valveLift(crankDeg: Float, intake: Boolean): Float`(0~1), `torqueAt(rpm: Float, model: Int): Float`)
- Test:
  - `EngineMechanicsTest`(`intakePeakNear470`, `exhaustPeakNear250`, `raceTorqueAboveBase`: torqueAt 최대값 model 4 > model 0)
  - `EngineSnapshotTest`
  - `EngineDetailSnapshotTest`(`engine_detail_telemetry_lv5`, `engine_detail_itb_lv5`, `engine_detail_ev_lv5`)

**완료 조건:**
- 낮 배경도 흑연색이고 금속은 밝다.
- 절단면은 빨강 + 가는 해칭이다.
- 계측 숫자는 고정폭이고 정렬선이 맞는다. 주행 중 글자는 24sp 이상이다.
- EV 사다리도 같은 화법이다.

- [ ] (a) 스냅샷 + 순수 함수 테스트 추가 → (b) 구현 → (c) 기록·보기 → (d) 반복 → (e) 테스트

### Task 7: 오르골 — 원목 오르골 + 사다리 (spec 5.3)

**Files:**
- Modify: `character/.../render/musicbox/MusicBoxPainter.kt`
- Create:
  - `character/.../render/musicbox/MusicBoxWood.kt`(나뭇결·래커 반사·상감 무늬 캐시)
  - `character/.../render/musicbox/MusicBoxMovement.kt`(황동 실린더·핀·강철 빗 튕김·조속기·이중 빗·종 3개와 해머)
- Modify: `character/.../musicbox/MusicBoxSim.kt` — 빗살 수를 단계에 맞추고(18/18/36/50/72, 작곡 음역은 기존 작곡기를 빗살 수로 자름), 단계 5에서만 종 타격 이벤트를 낸다.
- Test:
  - `MusicBoxTest`(`combTeethFollowStep`: [18,18,36,50,72], `bellsOnlyAtStepFive`)
  - `MusicBoxSnapshotTest`

**완료 조건:**
- 배경이 따뜻한 갈색이다(완전 검정 아님).
- 나뭇결이 보이되 저대비다.
- 핀이 회전에 맞춰 빛을 받고, 빗살이 튕긴 뒤 감쇠한다.
- 명판 글자는 세리프다.

- [ ] (a)~(e)

### Task 8: 아쿠아리움 — 수중 사진 + 사다리 (spec 5.4)

**Files:**
- Modify: `character/.../render/aquarium/AquariumPainter.kt`, `character/.../aquarium/AquariumSim.kt`(단계별 소품·생물 추가: 새우 무리, 열대어 2종, 문어, 바다거북. 기존 물고기 로직 유지)
- Create: `character/.../render/aquarium/AquariumLight.kt`(빛무늬 질감 캐시를 흘려 그림, 빛줄기, 부유물)
- Test:
  - `AquariumTest`(`speciesCountFollowsStep`: 단계 3에서 열대어 2종 추가, `turtleOnlyAtStepFive`)
  - `AquariumSnapshotTest`

**완료 조건:**
- 깊이 안개가 있다.
- 빛무늬 변화는 느리고 부드럽다.
- 바다거북은 화면을 40초 이상 걸려 가로지른다.

- [ ] (a)~(e)

### Task 9: 유리병 정원 — 유리 정원 + 사다리 (spec 5.5)

**Files:**
- Modify: `character/.../render/terrarium/TerrariumPainter.kt`
- Create: `character/.../render/terrarium/TerrariumGlass.kt`(용기 5종 외형·굴절 어긋남·반사 띠·물방울)
- Test: `TerrariumSnapshotTest`, `TerrariumTest`(기존 성장 테스트가 모든 단계에서 통과)

**완료 조건:**
- 유리 너머 배경이 가장자리에서 살짝 어긋난다.
- 흙 층이 보인다.
- 단계 3 물길은 흐르고, 단계 5 돔은 철제 프레임이다.
- 성장 저장은 그대로다.

- [ ] (a)~(e)

### Task 10: 플라즈마 (신규, spec 5.11)

**Files:**
- Modify(Task 2 뼈대를 채움): `character/.../plasma/PlasmaSim.kt`, `character/.../render/plasma/PlasmaPainter.kt`
- Create: `character/.../render/plasma/PlasmaInstruments.kt`(네온 숫자관·진공관·오실로스코프·야곱의 사다리)
- Modify: `app/src/main/assets/scenarios/plasma_showcase.json`(90초. 정지 → 출발 → 왼쪽 깜빡이 → 순항 → 브레이크 → 별·콤보 → 1 km, `"theme":"PLASMA","model":4`, 한국어 자막)
- Test:
  - `character/src/test/.../plasma/PlasmaTest.kt`
  - `character/src/test/.../render/plasma/PlasmaSnapshotTest.kt`
  - `ScenarioGoldenTest`에 plasmaShowcase

**Interfaces:**
- Produces(`PlasmaSim`):
  - `val filaments: Int`(4~14)
  - `val reach: Float`(0.5 수축 ~ 1)
  - `val gather: Float`(−1 왼쪽 ~ 1 오른쪽)
  - `val flicker: Float`(0.85~1.0)
  - `val odometerM: Int`
  - `val ladder: Float`(0~1, 1 km마다 0으로)
  - `val tubeGlow: Float`
  - `val scopeHz: Float`, `val scopeAmp: Float`
  - `allChips`: "전류가 일렁여요", "왼쪽으로 모여요", "오른쪽으로 모여요", "전류가 움츠러들어요", "빛점이 남았어요"

- [ ] **Step 1: 실패하는 테스트** (`PlasmaTest`, `ThemeKitTest`의 기존 스크립트 헬퍼를 쓴다)

```kotlin
@Test fun filamentsCapAtSprint() { assertEquals(run(kmh = 110f).filaments, run(kmh = 180f).filaments); assertTrue(run(kmh = 110f).filaments <= 14) }
@Test fun stoppedHasFour() { assertEquals(4, run(kmh = 0f).filaments) }
@Test fun leftBlinkerGathersLeft() { assertTrue(run(kmh = 40f, turn = TurnSignal.LEFT).gather < -0.5f) }
@Test fun brakeShrinks() { assertTrue(run(kmh = 40f, brake = true).reach < 0.7f) }
@Test fun flickerStaysWithin15Percent() { val s = sweep(seconds = 30f); assertTrue(s.minFlicker >= 0.85f && s.maxFlicker <= 1.0f) }
@Test fun noMoreThanThreePulsesPerSecond() { /* FlashGuard.recorder로 허가된 펄스 시각을 모아 어느 1초 창에서도 영역당 3회 이하인지 단언 */ }
@Test fun ladderResetsEachKm() { /* 1.0 km를 지나면 ladder < 0.1 */ }
```

- [ ] **Step 2: 실패 확인.** `build.ps1 -Work ... -Task ":character:testDebugUnitTest --tests *PlasmaTest*"` → FAIL
- [ ] **Step 3: 시뮬레이션 구현.** 가닥 수 = 4 + round(10 × min(kmh,110)/110). 나머지는 spec 5.11 표 그대로.
- [ ] **Step 4: 화가 구현.** 공통 단계 (a)~(e)를 따른다.
  - 가닥: 중심 → 유리면 사이를 노이즈로 휜 3~4마디 곡선. 넓은 반투명 + 가는 흰 중심의 2겹.
  - 유리구: 반사 띠 2줄 + 가장자리 프레넬.
  - 단계별 장치는 spec 표 순서대로 책상 위에 왼쪽에서 오른쪽으로 놓는다.
- [ ] **Step 5: 통과 확인.** PlasmaTest, ThemeContractTest, ScenarioGoldenTest → PASS

### Task 11: 로봇 격납고 (신규, spec 5.12)

**Files:**
- Modify(뼈대를 채움): `character/.../hangar/HangarSim.kt`, `character/.../render/hangar/HangarPainter.kt`
- Create:
  - `character/.../render/hangar/HangarMech.kt`(공통 골격 HX + 단계별 장갑·장비·도색 5종 + 조립 단계 마스크)
  - `character/.../render/hangar/HangarBay.kt`(콘크리트·갠트리·크레인·조명 캐시)
- Modify: `app/src/main/assets/scenarios/hangar_showcase.json`(100초, `"theme":"HANGAR","model":4`). 데모가 시작되면 sim은 `demo = true`가 되고, 데모 동안만 진행값 0.6(장갑 단계)에서 출발해 거리에 따라 완성까지 보여 준다. 저장된 실제 진행값은 건드리지 않는다.
- Test:
  - `character/src/test/.../hangar/HangarTest.kt`
  - `character/src/test/.../render/hangar/HangarSnapshotTest.kt`(공통 + `hangar_stage_frame`, `hangar_stage_armor`, `hangar_complete`)
  - `ScenarioGoldenTest`에 hangarShowcase

**Interfaces:**
- Produces(`HangarSim`):
  - `val progress: Float`(0~1, 이번 기체)
  - `val unit: Int`(1호기부터)
  - `val stage: Int`(0 골격, 1 내부 기계, 2 장갑, 3 도색, 4 마킹, 5 완성)
  - `val reactor: Float`, `val fan: Float`, `val steam: Float`
  - `val beaconL: Float`, `val beaconR: Float`, `val headTurn: Float`(−1~1), `val kneeDip: Float`, `val clamp: Float`
  - `persist()` = "p=<progress>;u=<unit>"
  - `var demo: Boolean`(true면 저장값이 바뀌지 않음)
  - `fun startDemo(progress: Float = 0.6f)`
  - `allChips`: "조립 중", "장갑을 달아요", "도색 중", "완성!", "동력로 가동", "정비 대기"
- 진행 규칙:
  - 실제 주행 거리 30 km = progress 1. 빨리 달려도 거리만큼만 오른다.
  - 완성되면 다음 거리부터 unit+1, progress 0.
  - 단계 경계: 0.2, 0.45, 0.7, 0.9, 1.0

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun thirtyKmCompletesOneUnit() { val s = drive(km = 30f, kmh = 60f); assertEquals(1f, s.progress, 0.01f); assertEquals(5, s.stage) }
@Test fun speedDoesNotRushAssembly() { assertEquals(drive(km = 3f, kmh = 40f).progress, drive(km = 3f, kmh = 160f).progress, 0.005f) }
@Test fun persistRestoreRoundTrip() { val a = drive(km = 7f); val b = HangarSim().apply { restore(a.persist()!!) }; assertEquals(a.progress, b.progress, 1e-3f); assertEquals(a.unit, b.unit) }
@Test fun demoDoesNotAdvanceSavedAssembly() { val s = HangarSim().apply { restore("p=0.2;u=1"); demo = true; startDemo() }; driveOn(s, km = 5f); assertEquals(0.2f, parseP(s.persist()!!), 1e-3f) }
@Test fun nextUnitAfterComplete() { val s = drive(km = 31f); assertEquals(2, s.unit); assertTrue(s.progress < 0.05f) }
@Test fun leftBlinkerTurnsHeadLeft() { assertTrue(run(turn = TurnSignal.LEFT).headTurn < -0.3f) }
```

- [ ] **Step 2: 실패 확인.** `build.ps1 -Work ... -Task ":character:testDebugUnitTest --tests *HangarTest*"` → FAIL
- [ ] **Step 3: 시뮬레이션 구현.** 데모 플래그는 앱이 데모 중에 sim에 설정한다. 앱 쪽 저장 경로(`saveThemeState`)가 데모 중에도 불리는지 확인하고, 불린다면 데모 중에는 막는다.
- [ ] **Step 4: 화가 구현.** 공통 단계 (a)~(e)를 따른다.
  - 로봇은 화면 높이의 80%로, 갠트리 사이에 정면 3/4 각도로 선다.
  - 조립 단계는 부품 그룹별로 그린다(골격 → 액추에이터·배선 → 회색 프라이머 장갑 → 도색 → 번호 "01").
  - 작업자 실루엣 2~3명이 천천히 움직인다.
  - 업그레이드 단계 1~5의 장갑·장비·도색은 spec 5.12 표대로.
  - spec 3장 금지 요소가 없는지 스냅샷에서 직접 확인한다.
- [ ] **Step 5: 통과 확인.** HangarTest, ThemeContractTest, ScenarioGoldenTest → PASS

### Task 12: 꼬마차 — 길·꼬마차 사다리 (spec 5.6)

**Files:**
- Modify:
  - `character/.../CharacterModels.kt`(`Biome`에 `BLOSSOM("벚꽃 강변")`, `NEON("네온 야경")`, `AURORA("오로라 설원")` 추가)
  - `character/.../JourneyTracker.kt`(순환 목록을 단계로 결정: 0 기존 5종, 1 +BLOSSOM, 2 +NEON, 3 +AURORA, 4 같음)
  - `character/.../render/WorldPainter.kt`(새 풍경 3종의 팔레트·소품)
  - `character/.../render/KartPainter.kt`(단계 5: 크롬 휠·별 장식 깃발의 클래식 로드스터 차체)
  - `VroomiScene.kt`(model을 JourneyTracker·KartPainter에 전달)
  - `VroomiEvent`에 새 풍경 3종 + 음성 대사 리소스 각 2개
- Test:
  - `JourneyTrackerTest`(`biomeCycleFollowsStep`: 단계 0은 5종, 3은 8종)
  - `SceneSnapshotTest`(공통 이름 + `kart_blossom`, `kart_neon_night`, `kart_aurora`, `kart_lv5_roadster`)

**완료 조건:**
- 네온 간판은 깜빡이지 않는다(정적 발광, 글자 없음).
- 오로라는 천천히 일렁인다.
- 단계 0의 동작은 기존 스냅샷과 같다.

- [ ] (a)~(e)

### Task 13: 보물지도 — 해도 강화 + 사다리 (spec 5.7)

**Files:** `character/.../render/treasuremap/TreasureMapPainter.kt`, Test `TreasureMapSnapshotTest`(공통 + `map_start_lv1`)

**완료 조건:**
- 출발 직후(`map_start_lv1`)에도 방위선 망·장식·얼룩이 보여 빈 화면이 아니다.
- 개인정보 테스트(`MapPrivacyTest`)가 통과한다.

- [ ] (a)~(e)

### Task 14: 작은 태양계 — 관제 모니터 강화 + 사다리 (spec 5.8)

**Files:** `character/.../render/orbit/OrbitPainter.kt`, `character/.../orbit/OrbitSim.kt`(달·위성·소행성·정거장 궤도 추가), Test `OrbitSnapshotTest`, `OrbitTest`(`orbitPerKmUnchangedAtEveryStep`)

**완료 조건:**
- 단계 1에서도 화면이 비지 않는다(계측 열·격자·잔광 꼬리).
- 주사선은 4% 이하로 옅다.
- 1 km = 1바퀴 규칙은 그대로다.

- [ ] (a)~(e)

### Task 15: 종이 팝업북 — 리소 인쇄 강화 + 사다리 (spec 5.9)

**Files:** `character/.../render/paper/PaperPainter.kt`, `PaperInk.kt`, `character/.../paper/PaperSim.kt`(단계 4 당김 탭 위치만), Test `PaperSnapshotTest`, `PaperTest`(`pullTabOnlyFromStepFour`)

**완료 조건:**
- 종이 섬유 결과 접힌 자리 그림자가 보인다.
- 판 어긋남은 2~3dp다.
- 당김 탭은 주차 중에만 움직인다(책장 규칙과 같음).

- [ ] (a)~(e)

### Task 16: 도트 월드 — 탈것 사다리 (spec 5.10)

**Files:** `character/.../pixel/sprites/RoverSprites.kt`(화물 트레일러·호버·제트 호버·탐사선 16×16 프레임, 같은 16색 팔레트), `character/.../pixel/PixelMechanics.kt`(단계 → 탈것), `render/pixel/PixelPainter.kt`, Test `PixelTest`(`vehicleFollowsStep`), `PixelSnapshotTest`(`KitShots.pixelWide()`로 공통 이름)

**완료 조건:**
- 정수배 확대를 지킨다(960×360 스냅샷에서 번짐 없음).
- 호버의 그림자는 1px 아래, 탐사선은 지면 위 4px이다.

- [ ] (a)~(e)

---

## Phase C — 통합

### Task 17: 합치기·문서·최종 빌드·에뮬레이터 확인

**Files:**
- Modify:
  - `app/build.gradle.kts`(versionCode 8, versionName "1.7.0-contest")
  - `README.md`(테마 표 12개, 등급·업그레이드 절, 사다리 그림 몇 장)
  - `docs/proposal_02_ko.md`(주요 기능에 등급·업그레이드, 기대효과에 "안전운전 보상 → 리텐션, 보험사 안전운전 특약 연계 확장")
  - `docs/character-originality.md`(시계 케이스 형상, 로봇 HX, 플라즈마 장치 독창성 절 추가, 구슬 장치 절 삭제)
  - `docs/demo-video-script.md`, `docs/submission-checklist.md`(등급·신규 테마·단계 확인 항목)
  - `.work/theme-pack-progress.md`(진행 기록)
- Test: 전체

- [ ] **Step 1: PR 합치기(사용자 승인 후).**
  - 순서: PR #1 → `phase-a/grade-ui` → 테마 PR(번호 순).
  - 각 PR은 합치기 전에 최신 main으로 rebase하고 CI 통과를 다시 확인한다.
  - 충돌 처리: `theme_luminance.json`은 어느 쪽이든 받고 Step 2에서 다시 만든다. `ScenarioGoldenTest`는 두 테스트를 모두 남긴다. 그 밖의 충돌은 원인을 기록한다.
  - 합친 뒤 `phase-c/integration` 브랜치에서 아래 Step을 진행한다.
- [ ] **Step 1b: 공용 테스트 정리.**
  - 테마별 private 사다리 헬퍼를 `KitShots.ladder()` 하나로 모으고 각 테스트가 그것을 쓰게 바꾼다(스냅샷 결과는 같아야 한다).
  - `PassageCostTest`에 12개 테마의 단계 5(`model = 4`) 비용 항목을 넣고, 기존 한도 안인지 확인한다.
- [ ] **Step 2: 밝기 재측정.** `VROOMI_LUMA=1`로 ThemeLuminanceSnapshotTest를 돌려 json을 갱신한 뒤 LuminanceFlashTest(12개 테마 132쌍) → PASS. 실패하는 쌍이 있으면 Entrances의 BRIGHT_ENTRANCE_MS 규칙이 적용되는지 확인한다.
- [ ] **Step 3: 전체 빌드.** `build.ps1`(testDebugUnitTest assembleRelease) 뒤 `build.ps1 -Task "lint"` → 테스트 0 failures, lint 0 errors. 테스트 수·APK 크기를 기록한다.
- [ ] **Step 4: 스냅샷 정리.** `:character:recordPaparazziDebug :app:recordPaparazziDebug` → `docs/screenshots/`를 갱신하고, marble_* 스냅샷 파일을 삭제한다.
- [ ] **Step 5: 에뮬레이터 확인.**
  - Pleos 에뮬레이터 콜드 부트 → `adb install -r -g apk/TEAMNAME_WithDriving_release.apk`(패키지 `io.withdriving.app`) → 실행.
  - 아래를 각각 `adb exec-out screencap -p`로 캡처해 직접 본다: 테마 탭(단계 태그), 등급 탭, 시계·엔진룸·오르골·플라즈마·격납고 쇼케이스 재생 화면.
  - 확인할 것: 크래시 없음, 10초 프레임 통계(`dumpsys gfxinfo`) jank 0% 근처.
- [ ] **Step 6: 문서 갱신.** 위 Files의 문서를 고친다. `grep -rn "구슬\|marble"`로 구슬 장치 언급을 찾아 0개로 만든다(이 계획·spec 파일 제외).
