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

## 4. 지금 상태

| 작업 | 상태 | 브랜치 / PR |
|---|---|---|
| Task 2+3: 테마 목록(MARBLE 삭제, PLASMA·HANGAR 뼈대), 단계 배관(`SceneOptions.model`, 시나리오 `"model"`, 설정 저장, `ThemeModels`) | 완료. 테스트 554개 중 실패 0, CI 통과 | `phase-a/foundation` / **PR #1 병합 대기** |
| 계획 갱신 + 이 인수인계 문서 + `CLAUDE.md` + `.claude/` 무시 | 완료 | `chore/plan-update` / **PR #2** |
| Task 1: 등급 규칙(`Grade`, `SafetyGrade`, `ThemeAccess`, `Bond` 대체) | 커밋됨(4bb02df) | `phase-a/grade-ui` |
| Task 4: 테마·등급 탭, 등급 화면(`GradePanel`), 단계 시트, 갤러리 "Lv.n", 개발자 스위치 | **WIP 커밋**(3a3d7d9), 전체 테스트 전에 멈춤 | `phase-a/grade-ui` |
| Task 5: 오토매틱 시계(밝은 공방 + 5단계, `CalibreTourbillon` 신규) | **WIP 커밋**(09d5229), 2차 스냅샷을 검토하던 중 멈춤 | `theme/calibre` |
| Task 6~16: 나머지 테마 11개 사다리 | 시작 안 함 | `theme/<테마 소문자>` |
| Task 17: 통합, 문서, 버전 1.7.0-contest(8), 에뮬레이터 확인 | 시작 안 함 | `phase-c/integration` |

`phase-a/grade-ui`와 `theme/calibre`는 `phase-a/foundation` 위에서 땄다. PR #1이 합쳐지면 main으로 rebase한다.

## 5. 다음에 할 일 (순서대로)

1. **`phase-a/grade-ui` 마무리.**
   - 전체 `testDebugUnitTest`를 돌린다.
   - `ScreensSnapshotTest` 새 스냅샷 6장을 다시 기록하고 직접 본다: `grade_bronze`, `grade_gold`, `grade_diamond`, `gallery_steps_gold`, `sheet_model_calibre_silver`, `home_badge_gold`.
   - `VROOMI_SCREEN=1024x600@160`에서 등급 화면이 한 열로 접히는지 본다.
   - WIP 커밋을 정리하고 push한 뒤 PR(base main)을 연다.
2. **`theme/calibre` 마무리.**
   - 사다리 그림 `calibre_ladder`와 단계별 그림을 다시 기록해서 본다.
   - 낮 배경 밝기 0.6 이상, 밤 0.3 이상으로 맞추고 `theme_luminance.json`을 다시 만든다.
   - 전체 테스트를 돌리고 PR을 연다. 완료 조건은 계획 Task 5.
3. **Task 6 엔진룸 → 7 오르골 → 10 플라즈마 → 11 격납고 → 8, 9, 12~16.** 한 번에 1~2개씩 진행한다.
4. **Task 17 통합.** PR 병합은 사용자 승인 후에만 한다(6장).
5. **제출 준비**(9장).

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

- 버전 `1.7.0-contest`(versionCode 8). release APK를 `apk/팀명_WithDriving_release.apk`로 둔다. 지금은 `TEAMNAME` 자리표시자다.
- 사용자가 정할 것:
  - 팀명
  - keystore 백업
  - 접수 이메일(`appdevchallenge@hyundai-partners.com`, 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`)
  - 접수양식 docx(기획서)
  - 시연 영상(가산점)
- 비공개 저장소이므로 심사용 계정을 Collaborator로 초대해야 한다(계정명은 공지 확인).
- README, 기획서, 독창성 문서, 데모 대본, 체크리스트를 갱신한다(계획 Task 17).
  - README 뒷부분에 아직 "브루미"와 구슬 장치 언급이 남아 있다.
- `docs/superpowers/`, `CLAUDE.md`, 이 문서를 제출본에서 뺄지 사용자에게 묻는다.
- 알려진 작은 문제: 데모 자막 중 꼬마차용 문구가 다른 테마 데모에도 나온다. 아직 고치지 않았다.
