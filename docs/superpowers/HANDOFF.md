# 인수인계 — 테마 전면 개편 (2026-11-10)

이 문서만 읽으면 다른 Claude 세션·계정이 이어서 진행할 수 있다. 이전 세션의 대화 기억은 없다고 가정한다.

## 1. 프로젝트

- **앱:** 위드라이빙(WithDriving). Pleos Connect(AAOS) 차량용 앱 공모전 출품작이다.
  - 차량 신호(속도, 깜빡이, 브레이크, 주행 거리)에 반응하는 오리지널 동승 장면을 그리는 앱이다.
  - 코드 안 이름은 아직 Vroomi(`io.vroomi.*`). applicationId는 `io.withdriving.app`.
- **저장소:** 비공개 `Kyle-ChoiKH/WithDriving`. 기본 브랜치 `main`.
- **마감:** 2026-11-15(일) 23:59 이메일 접수. 1차 발표 11-23, 2차 실연 PT 12-3~4(에뮬레이터 가능).
- **사용자와는 한국어로 쉽게 말한다.** 전문 용어는 풀어서 쓴다.

## 2. 사용자가 원한 것 (원문 요지)

- 각 테마가 확실히 구별되고, 테마마다 뚜렷한 특징을 갖게 다듬는다.
- 실물 테마는 실사풍으로 만든다.
  - 오토매틱 시계: 배경이 너무 어두우니 밝게.
  - 엔진룸: 공대생이 좋아할 만큼 디테일하게, 글꼴은 정갈하게.
  - 오르골: 고급 실제 오르골이 돌아가는 것처럼.
  - 사용자 말: "실사틱해야 된다는 거지, 완전 재현을 빡세게 해서 AI 티 나게 만들라는 건 아니야."
- 구슬 장치 테마는 삭제한다(완료). 새 테마 2개를 추가한다(뼈대 완료).
  - 플라즈마: 고전압 실험실, 전류·진공관 느낌.
  - 로봇 격납고: 오리지널 거대 로봇. 건담 요소는 금지.
- 탭 이름을 바꾼다: 친구 → **테마**, 옷장 → **등급**.
- 테마는 모두 처음부터 열려 있다. 안전운전 등급(브론즈~다이아몬드)이 오르면 **모든 테마가 그 테마다운 방식으로 한 단계씩 업그레이드**된다.
  - 12테마 × 5단계 = 60가지.
  - 꼬마차 테마는 옷 입히기와 배경이 늘어난다.
- **토큰을 아낀다.** 에이전트는 동시에 1~2개만 띄운다. 리뷰나 기계적인 일은 Sonnet/Haiku에 맡긴다. 큰 작업은 시작 전에 규모를 한 줄로 알린다.
  - 2026-11-09에 Opus 에이전트 2개를 병렬로 돌리다 5시간 한도에 걸려 일시정지했다.

## 3. 정답 문서

| 문서 | 내용 |
|---|---|
| `docs/superpowers/specs/2026-11-09-theme-overhaul-design.md` | 설계(spec). 등급 조건, 12테마 × 5단계 이름, 단계마다 새로 생기는 것, IP 규칙. **표가 정답** |
| `docs/superpowers/plans/2026-11-09-theme-overhaul.md` | 구현 계획 17개 작업. Global Constraints, 병렬 작업 규칙, 실행 현황, Task 17 병합 순서 |
| `docs/superpowers/plans/cloud-agent-brief.md` | Linux 환경 준비, 명령, 규칙, 보고서 형식 |

## 4. 지금 상태 (2026-11-11 밤 기준)

Task 1~16이 모두 끝나 PR로 올라가 있다. 모든 PR은 base main이고 `phase-a/foundation`(PR #1) 위에 쌓았다. 각 PR은 전체 `testDebugUnitTest` 통과와 스냅샷 직접 확인을 마쳤다.

| 작업 | 브랜치 | PR |
|---|---|---|
| Task 2+3: 테마 목록, 단계 배관 | `phase-a/foundation` | #1 |
| 계획 갱신 + 인수인계 문서 + `CLAUDE.md` | `chore/plan-update` | #2 |
| Task 1+4: 등급 규칙, 테마·등급 탭, 등급 화면 | `phase-a/grade-ui` | #3 |
| Task 5: 오토매틱 시계 | `theme/calibre` | #4 |
| Task 6: 엔진룸 | `theme/engine` | #5 |
| Task 7: 오르골 | `theme/music_box` | #6 |
| Task 10: 플라즈마(신규) | `theme/plasma` | #7 |
| Task 11: 로봇 격납고(신규) | `theme/hangar` | #8 |
| Task 8: 아쿠아리움 | `theme/aquarium` | #9 |
| Task 9: 유리병 정원 | `theme/terrarium` | #10 |
| Task 14: 작은 태양계 | `theme/orbit` | #11 |
| Task 16: 도트 월드 | `theme/pixel` | #12 |
| Task 13: 보물지도 | `theme/treasure_map` | #13 |
| Task 15: 종이 팝업북 | `theme/paper` | #14 |
| Task 12: 꼬마차 | `theme/kart` | #15 |
| Task 17: 통합, 문서, 버전 1.7.0-contest(8) | `phase-c/integration` | 통합 PR(base main). **이 PR 하나만 합치면 #1~#15가 전부 들어간다** |

Task 17에서 한 일(`phase-c/integration`, 위 14개 브랜치를 순서대로 병합한 뒤 작업):
- 공용 테스트: `PassageCostTest.stepFiveOfEveryTheme`(12개 테마 5단계의 레이어 48MB 이하·해제 확인, `build/reports/passage_cost_step5.txt`), `ScenarioGoldenTest`의 `plasmaShowcase`·`hangarShowcase`.
- 밝기 재측정(12개 테마, `Ambience.kt`와 `theme_luminance.json`이 일치), 전체 `testDebugUnitTest lint assembleDebug` 통과: 테스트 796개(단위 288 + 화면 508) 실패 0, lint 오류 0.
- 스냅샷 517장 재기록 → `docs/screenshots/` 갱신, `marble_*` 그림과 gif 삭제.
- 문서: README(등급·업그레이드 절, 플라즈마·격납고), 제안서, 독창성 문서, 영상 대본, 제출 점검표. 버전 1.7.0-contest(8).
- **에뮬레이터 확인**(Pleos Connect v2.1.0, `C:\vroomi-tools\android-sdk`, 디버그 빌드): 첫 실행, 등급 탭, 테마 탭, 업그레이드 단계 시트, 데모 목록 17개, 쇼케이스 10개(격납고·시계·엔진룸·유리병 정원·보물지도·아쿠아리움·태양계·플라즈마·팝업북·도트 월드, 모두 5단계) 재생. 모두 약 60fps·크래시 0(격납고 50초 3,178프레임 끊김 0.13%, 나머지는 12초 734프레임 안팎에 끊김 0~0.55%, 메모리 PSS 150~178MB). JVM에서 무겁게 나온 팝업북·시계 5단계도 에뮬레이터에서는 끊기지 않았다. 여기서 찾아 고친 버그 3개:
  1. 데모를 "데모 끝내기"로 중간에 멈추면 그 주행이 실제 주행으로 저장되어 등급 거리에 들어갔다 → `TripRecorder`가 데모가 섞인 주행을 데모로 표시(`DemoTripTest`).
  2. 시트가 닫히는 프레임이 길어지면(무거운 테마로 넘어갈 때) 흐림 반경 0으로 앱이 죽었다(`RenderEffect` "nativePtr is null") → `AppRoot`·`Materials`에서 반경 확인.
  3. 데모를 주행 중에 끝내면 홈이 원래 테마로 돌아오지 않았다(StateFlow를 `snapshotFlow`로 읽어 변화를 못 봄) → 흐름을 직접 수집.
- **에뮬레이터에서 못 본 것:** 오르골 쇼케이스, 일반 시나리오 6개의 끝까지 재생, 등급이 실제로 오르는 순간(실주행 10km 필요), 릴리스 빌드. 에뮬레이터 앱 데이터에는 버그 1을 고치기 전에 잘못 저장된 1km 주행이 하나 남아 있다(설정 → 다이어리 초기화로 지움).
- **하지 않은 것:** 사다리 헬퍼를 `KitShots.ladder()`로 모으기(동작에 영향 없어 생략), 릴리스 APK 서명 빌드(키는 본 체크아웃에만 있음).
- 5단계 한 프레임 시간(빌드 PC의 JVM 기준, 차량 아님)은 종이 팝업북 220ms, 시계 137ms로 다른 테마보다 길다. 실제 기기에서 끊기면 이 둘부터 본다.

알아 둘 점:
- 테마 PR이 고친 공용 파일은 `Ambience.kt`와 `theme_luminance.json`의 자기 테마 줄뿐이다(서로 이웃한 줄이라 병합 때 충돌이 나면 양쪽 줄을 모두 받는다). `theme/hangar`만 `AppRoot.kt`의 `ThemeKeeper`에 데모 알림 5줄을 더했다.
- 기존 테마의 사다리는 새 파일 `<Theme>Steps.kt`(또는 `KartSceneries.kt`, `VehicleSprites.kt`)에 단계 게이트와 소품을 두고, 기존 화가는 작은 치환만 했다.
- **꼬마차는 계획과 다르게 구현했다.** 공용 `Biome`·`JourneyTracker`·음성 대사를 건드리지 않고, 가로수길·도시·산길이 격바퀴(첫 바퀴 포함)마다 새 얼굴(벚꽃 강변·네온 야경·오로라 설원)로 나온다(`KartStep.variant`). 이유와 되돌리는 방법은 PR #15 본문에 있다.
- 저장소에 CRLF 파일과 LF 파일이 섞여 있다. 스크립트로 고칠 때는 원래 줄바꿈을 유지한다(`git ls-files --eol -m`에 `i/crlf w/lf`가 없어야 한다).
- 각 PR이 Task 17로 미룬 것: `ScenarioGoldenTest`의 신규 쇼케이스 항목(plasma, hangar), `PassageCostTest`의 단계 5 항목, 사다리 헬퍼를 `KitShots.ladder()`로 모으기, 쇼케이스 시나리오의 단계 지정.

## 5. 다음에 할 일 (순서대로)

**2026-11-11 낮: 통합 PR #16을 main에 병합했다(사용자 승인). #1~#15도 병합됨으로 처리됐고 main CI가 통과했다. 테마 개편은 main에 전부 들어가 있다.**

병합 뒤 본 체크아웃(main)에서 서명된 릴리스 APK를 빌드해 확인했다: `apk/Kyle_WithDriving_release.apk`(팀명 Kyle, 1.7.0-contest, versionCode 8, 19.8MB, v2 서명 확인). 에뮬레이터에 설치해 등급 탭, 테마 탭, 시계 쇼케이스(12초 735프레임, 끊김 0%, 크래시 0), 데모를 중간에 끝낸 뒤의 동작(트립 카드에 "데모" 표시, 등급 거리 그대로, 원래 테마로 복귀)을 봤다. 디버그 빌드와 릴리스 빌드는 서명이 달라 서로 덮어 설치되지 않는다(`adb uninstall io.withdriving.app` 후 설치).

**2026-11-11 저녁: 단계(버전) 디버깅.** 사용자가 전 등급을 연 빌드로 직접 보고 "등급별 변형이 안 보이고 허접한 게 많다, 열린 버전은 골라 쓸 수 있어야 한다, 등급 탭에서 옷장은 필요한 테마만"이라고 지적했다. 원인과 수정(`fix/step-visibility`):
- 기존 사다리 스냅샷은 유리한 주행 상태만 찍어서, 새 사용자가 보는 **주차·기록 0** 화면에서 단계가 같아 보이는 것을 못 잡았다. `FreshLadderSnapshotTest`(주차·첫 주행 상태의 5단계 한 장)와 `PassageCostTest.everyStepShowsWhenParked`(단계마다 화면의 0.5% 이상이 바뀌는지 측정, `build/reports/step_visibility.txt`)를 추가했다. **새 테마·단계를 만들면 이 두 테스트부터 본다.**
- 꼬마차: 고른 단계의 대표 풍경이 출발 구간과 주차 화면에 나온다(`KartStep.face`/`signature`), 네온·오로라는 시간과 무관하게 밤(`dusk`), 5단계는 퍼레이드 깃발(`drawParade`).
- 격납고: 조립 전에도 고른 기체의 설계 홀로그램(`Mech.blueprint`)과 벽의 주문판(`unitSign`, HX-1~5)이 보인다.
- 도트 월드: 단계별 하늘(고리 행성·떠 있는 섬·혜성과 둘째 달·모선). 태양계: 별밭·행성 대기·먼지 고리·성운(`OrbitExtras.light`). 플라즈마: 단계별 가스 색(`GAS`). 팝업북: 4단계 종이 열기구.
- 등급 탭: 버전 줄을 눌러 그 테마의 버전을 바로 고른다(저장), 옷과 옷장은 캐릭터가 있는 테마(꼬마차)에서만 보인다. 문구는 "업그레이드" → "버전"("버전 고르기").
- 전 등급을 연 상태로 보기(디버그 빌드): 에뮬레이터에서 `adb shell run-as io.withdriving.app --user 10`으로 `shared_prefs/vroomi_settings.xml`에 `<int name="gradeReached" value="5" />`를 넣는다.

**2026-11-11 밤: 등급이 오를 때의 변화를 더 크게**(`feat/bolder-ladders`). 사용자 요청: "등급이 바뀔 때 바뀌는 부분이 너무 적다. 변화가 눈에 드러나게 해서 안전운전으로 등급을 올리고 싶게 만들어라."
- 원칙: 단계는 소품 하나가 아니라 장면 전체를 바꾼다. `step_visibility.txt` 기준 모든 단계가 주차 화면의 0.9% 이상(대부분 2~10%)을 바꾼다(테스트 하한 0.9%).
- 도트 월드(하늘 요소 확대, 오로라, 모선 광선), 격납고(기체별 등짐 장비로 실루엣이 달라짐 + 기체 색 깃발), 팝업북(3단계 앞줄 꽃밭, 4단계 연), 보물지도(2단계 산·숲 기호), 시계(단계별 시곗줄, 자막 밑 종이 라벨).
- 주행 후 카드에 등급 안내(`GradeNudge`): 다음 등급까지 남은 거리·점수와 열리는 버전, 승급 축하, 데모는 등급에 안 들어간다는 문구.
- 밝기: 시계 0.659, 격납고 0.058.
- 아직 약한 곳: 보물지도 Lv.4(0.95%), 팝업북 Lv.4(1.2%), 태양계 Lv.4(1.4%), 엔진룸 Lv.4(1.9%).

**2026-11-11 밤: #18, #19 병합(사용자 승인) 후 릴리스 APK 교체.** 최신 main으로 `apk/Kyle_WithDriving_release.apk`를 다시 빌드했다(1.7.0-contest, versionCode 8, 19.9MB, v2 서명 확인). 에뮬레이터에 릴리스 빌드를 설치해 격납고 쇼케이스(12초 736프레임, 끊김 0%), 데모 중간 종료 후 원래 테마 복귀를 확인했다. 테스트 825개(단위 294 + 화면 531), 그림 539장.

**2026-11-11 심야: 색상 고르기 + 약한 단계 보강**(`feat/colorways`). 사용자 요청: "꼬마차 옷장처럼 다른 테마도 등급이 오를수록 고를 수 있는 색상·꾸미기를 열어 달라", "등급별 차등을 더 주고 부족한 테마를 보완해 달라".
- 색상(`character/.../theme/ThemeColorways.kt`): 꼬마차를 뺀 11개 테마에 이름 붙은 색상 5종. 완성된 그림에 색 행렬 한 겹(색상 회전·채도·빛 색조)을 씌우므로 그리기 코드는 그대로다. 등급마다 하나씩 열리고(`ThemeAccess.effectiveColor`: 고르지 않으면 기본색, 새 색이 저절로 입혀지지 않음), 설정에 `color.<THEME>`으로 저장한다.
- 화면: 테마 탭 칩 "색상 · 이름"과 색상 시트(`ColorOptions`), 등급 탭의 색상 카드(`ColorSection`, 옷장과 같은 모양). 실시간 장면은 `Modifier.colorway`, 정지 그림은 `StillSpec.color`.
- 보강: 엔진룸은 단계마다 방 조명(회색 작업장·청색 조립동·주황 다이노실·청록 시험실·진홍 피트), 태양계 4단계 넓은 먼지 고리, 팝업북 4단계 종이 무지개, 보물지도 2단계 산·숲을 크게·4단계 포르톨라노식 색 방위선과 둘째 나침반.
- 단계 변화량 하한을 1.5%로 올렸다(지금 최소 1.81% 보물지도 Lv.2). 테스트 841개 통과.
- 하지 않은 것: 색상 외의 "추가 아이템"(테마별 소품 고르기), 꼬마차의 색상(옷장·친구가 그 역할).

**2026-11-12 새벽: #21 병합(사용자 승인) 후 릴리스 APK 교체.** `apk/Kyle_WithDriving_release.apk`를 색상 기능이 들어간 main으로 다시 빌드했다(19.9MB, v2 서명 확인). 에뮬레이터: 릴리스 빌드 쇼케이스 12초 735프레임·끊김 0%, 디버그 빌드에서 색상을 입힌 채 아쿠아리움 쇼케이스 12초 734프레임·끊김 0.14%(색상 레이어의 주행 중 비용은 문제없음).

1. **제출 준비**(9장). 팀명은 **Kyle**로 정해졌다(`gradle.properties`의 `vroomi.teamName`). 코드를 고치면 `powershell -ExecutionPolicy Bypass -File .\build.ps1`로 다시 빌드해 `apk/Kyle_WithDriving_release.apk`를 새로 커밋한다(공모전 규정: 저장소 `apk/` 폴더에 release APK).
2. **에뮬레이터 마저 확인**(선택): 오르골 쇼케이스. 실행: `ANDROID_HOME=C:\vroomi-tools\android-sdk`, `emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load`, `adb install -r -g <apk>`.

각 테마 작업의 공통 단계(스냅샷 이름, 사다리 그림, 직접 보기, 반복, 올리기)는 계획의 "Phase B 공통 단계"와 "병렬 작업 규칙"에 있다. 요점:
- 사다리 헬퍼는 각 테스트 파일 안에 private으로 만들고 `KitShots.kt`는 고치지 않는다.
- `PassageCostTest`는 Task 17에서 고친다.
- `Ambience.kt`는 자기 테마 줄만 고친다.

## 6. 막힌 것

- **PR 병합:** Claude Code 자동 권한 검사가 `gh pr merge`를 "검토 없는 병합"으로 막는다. 우회하지 않는다. 사용자가 GitHub에서 직접 합치거나, 명시적으로 승인 규칙을 추가해야 한다.
- **클라우드 실행:**
  - 사용자가 GitHub를 Claude 계정에 연결했는데도 RemoteTrigger 생성이 `403 repo_access_denied`로 거부된다.
  - Claude GitHub 앱의 저장소 접근에 WithDriving이 들어갔는지 확인해야 한다(github.com/settings/installations → Claude → Configure). claude.ai/code 저장소 목록에 보이는지도 본다.
  - **다른 Claude 계정이면** 그 계정에서 GitHub 연결과 저장소 접근을 새로 해야 한다.
- **Chrome 확장 미연결:** 브라우저 자동화는 쓸 수 없다. 웹 페이지는 `Start-Process <url>`로 열어서 사용자에게 맡긴다.

## 7. 빌드·확인

**이 PC(Windows):**
- 툴체인은 `C:\vroomi-tools`(JDK17, Android SDK, Gradle 캐시)에 있다.
- 경로에 한글·공백이 있어 AGP가 실패하므로, `build.ps1`이 ASCII 사본으로 미러링한 뒤 빌드한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Work C:\vroomi-build\<X>\Vroomi -Task "testDebugUnitTest"
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Work C:\vroomi-build\<X>\Vroomi -Task ":character:recordPaparazziDebug --tests <Class>"
```

- 작업마다 `<X>`를 다르게 한다. 동시 Gradle 빌드는 2개까지(메모리 32 GB).
- 스냅샷 PNG는 사본의 `src/test/snapshots/images/`에 생긴다. 다음 빌드의 robocopy /MIR가 지우므로 기록 직후 복사한다.
- 멈춘 로컬 작업 폴더도 남아 있다. 이어서 써도 되고, GitHub 브랜치에서 새로 받아도 된다.
  - `.claude/worktrees/agent-a8aa59ec99c732df3` = `phase-a/grade-ui`
  - `.claude/worktrees/agent-aad175eaae10cd2d9` = `theme/calibre`

**Linux / 클라우드:** `cloud-agent-brief.md` 1장대로 준비한 뒤 `./gradlew --no-daemon --console=plain testDebugUnitTest`를 돌린다. GitHub Actions CI는 Linux에서 Paparazzi까지 통과한다.

**화면 확인:**
- 그림은 Paparazzi 스냅샷 PNG를 Read 도구로 직접 보고 판단한다. 각 그림 작업의 관문은 이것이다.
- 에뮬레이터는 Pleos_Connect_v2.1.0(2560×1440, 160dpi)이다. 콜드 부트가 필요하다. Git Bash에서 adb 경로는 `MSYS_NO_PATHCONV=1`을 붙인다.

## 8. 규칙 (어기면 안 됨)

- **git:**
  - 작업마다 브랜치를 따고 PR(base main)을 연다. main에 직접 push하지 않는다.
  - 커밋 끝줄은 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, PR 본문 끝줄은 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
  - 작성자는 `Kyle-ChoiKH` / `253865254+Kyle-ChoiKH@users.noreply.github.com`.
- **비밀·개인정보:**
  - `keystore.properties`, `signing/*.jks`는 커밋하지 않는다(git-ignored).
  - README, 커밋, 스크린샷, 영상에 실명·연락처·계정을 넣지 않는다.
- **IP:**
  - 브랜드 시계 형상 금지(탱크·산토스, 로마 숫자 + 청색 카보숑).
  - 건담 요소 금지(V자 뿔, 입 모양 마스크, 흰·파랑·빨강·노랑 배색, 모노아이, 무기).
  - 상표 "Nixie" 금지. 그림은 코드로만 그린다.
- **차량·안전:**
  - 차는 읽기만 한다.
  - 번쩍임은 영역당 초당 3회 이하(FlashGuard).
  - 금지어(`ThemeSim.FORBIDDEN_WORDS`: 점검, 경고, 고장, ℃, °C, 기압, 압력, kPa, bar)를 쓰지 않는다.
  - 주행 중 장면 글자는 24sp 이상으로 한다. 앱 버튼 구역(ChromeZones: 왼쪽 위, 오른쪽 아래)을 피한다.
  - 110 km/h 이상에서는 연출이 상한이다. 과속을 보상하지 않는다.
- **"실사틱":**
  - 빛 방향 하나의 음영, 반사광 1~2줄, 부드러운 접지 그림자, 저대비 질감으로 그린다.
  - 고대비 잔무늬, 의미 없는 장식, 과포화 글로우는 금지한다.
  - 단계 n은 n−1과 한눈에 달라야 한다.

## 9. 제출 전 남은 일 (11-15 23:59까지)

- 버전 `1.7.0-contest`(versionCode 8). release APK는 `apk/Kyle_WithDriving_release.apk`로 저장소에 들어 있다.
- 사용자가 정할 것:
  - keystore 백업
  - 접수 이메일(`appdevchallenge@hyundai-partners.com`, 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`)
  - 접수양식 docx(기획서)
  - 시연 영상(가산점)
- 비공개 저장소이므로 심사용 계정을 Collaborator로 초대해야 한다(계정명은 공지 확인).
- README, 기획서, 독창성 문서, 데모 대본, 체크리스트는 12개 테마·등급·업그레이드 기준으로 갱신했다(구슬 언급 0). "브루미"는 꼬마차 테마 캐릭터 이름으로만 남아 있다.
- `docs/superpowers/`, `CLAUDE.md`, 이 문서를 제출본에서 뺄지 사용자에게 묻는다.
- 알려진 작은 문제: 데모 자막 중 꼬마차용 문구가 다른 테마 데모에도 나온다. 아직 고치지 않았다.
