# 퍼미션 표 (Vroomi)

검증 기준: Maven 저장소에서 받은 SDK 소스/바이너리(2026-11-01)
- `ai.pleos.playground:Vehicle:2.3.2` sources: `ai/pleos/playground/vehicle/constant/PleosPermission.kt`, 각 API의 `@RequiresPermission`
- `ai.pleos.playground:FusedLocation:1.1.3` + `fused-location-data:0.0.3` sources
- `ai.pleos.playground:NaviHelper:3.0.5` sources: `utils/PermissionUtils.kt`, `manager/ServiceManager.kt`
- `TextToSpeech:3.3.1`, `SpeechToText:3.3.1`: AAR 매니페스트(소스 비공개)

## 선언한 퍼미션 (`app/src/main/AndroidManifest.xml`)

| 퍼미션 | SDK / API | 읽기·제어 | 브루미가 쓰는 이유 | 없거나 승인 안 될 때 | 검증 |
|---|---|---|---|---|---|
| `pleos.car.permission.CAR_CLIMATE` | Vehicle `HVAC`: `checkHvacFanSpeedCapabilities`, `registerHvacFanSpeed`, `registerHvacTemperature`, `registerHvacAcState`, `registerHvacMaxAcState`, `registerHvacDefrosterState`, `registerHvacMaxDefrostState`, `registerHvacAirCleanerState`, `registerHvacRecirculationState`, `registerHvacAutoState` | 읽기 | 바람 불기, 입김/목도리, 서리 닦기, 반짝이, 엄지척 | 해당 반응만 꺼지고 나머지는 동작 | Vehicle 2.3.2 소스 `@RequiresPermission(CAR_CLIMATE)` |
| `pleos.car.permission.CAR_DRIVE` | `Brake.registerBrakePedalStatus`, `Gear.registerGearSelection`, `DrivingMode.registerDrivingModeState / registerCruiseControlState`, `Odometer.registerDistanceDriven` | 읽기 | 미끄러짐·급제동, 주차 판단(다이어리), 에코/스포츠, 크루즈, 주행 거리 | 브레이크 반응 없음, 주차는 "60초 정지"로 판단, 거리는 속도 적분 | 소스 확인 |
| `pleos.car.permission.CAR_LIGHTS` | `TurnSignal.registerTurnSignalState`, `Light.registerLowBeamLightState` | 읽기 | 손짓, 밤 팔레트·랜턴 | 손짓/랜턴 없음 (시스템 다크 모드는 따라감) | 소스 확인 |
| `pleos.car.permission.CAR_WINDOWS` | `Wiper.checkWiperStateCapabilities / registerWiperState` | 읽기 | 우비·빗방울 | 비 연출 없음 | 소스 확인 |
| `pleos.car.permission.CAR_DOORS` | `Door.checkDoorPositionCapabilities / registerDoorPosition` | 읽기 | 인사·작별 인사 | 인사 없음 | 소스 확인 |
| `pleos.car.permission.CAR_ENERGY` | `EvBattery.registerEvBatteryStateOfCharge / registerEvChargePortOpen` | 읽기 | 배고픔, 충전 중 에너지 먹기 | EV 연출 없음 | 소스 확인. 충전 상태 전용 리스너가 없어 "충전구 열림 + P단"으로 추정 (TODO(verify)) |
| `pleos.car.permission.CAR_SEATS` | `Seat.registerHvacSeatTemperature / registerHvacSeatVentilation` | 읽기 | 담요 | 담요 없음 | 소스 확인 |
| `pleos.car.permission.CAR_SAFETY` | `Safety.registerSeatBeltNotBuckledWarning` | 읽기 | 안전 경고 시 즉시 구석으로 숨고 무음 | 경고 연동 없음 (나머지 안전 규칙은 유지) | 소스 확인. `registerTelltales`는 `PRIVATE_CAR_SAFETY`라 사용 안 함 |
| `pleos.car.permission.FUSED_LOCATION` | FusedLocation `registerFusedLocationCallback` | 읽기 | **차속 계산** (위치 간 거리 ÷ 시간) | 차속 없음 → 걷기/뛰기 대신 숨쉬기 유지, 시뮬레이터로 시연 | FusedLocation 1.1.3 소스 |
| `pleos.car.permission.NAVI_ROUTE` | NaviHelper 이벤트 수신 (`onNavigationStatus`, `onDrivingInfo`, `onDestinationArrived`) | 읽기 | 길안내 중 조용히, 1km 두리번, 도착 축하 | 길안내 연출 없음 | `ServiceManager`의 조회 API가 `NAVI_ROUTE`를 확인. 수동 이벤트 수신에도 필요한지는 TODO(verify) |

음성 모듈(`core/voice/src/main/AndroidManifest.xml`)에서 선언해 앱으로 합쳐지는 퍼미션:

| 퍼미션 | SDK / API | 브루미가 쓰는 이유 | 없을 때 | 검증 |
|---|---|---|---|---|
| `pleos.car.permission.TTS_SERVICE` | TextToSpeech 3.3.1 `initialize`, `speak`, `stop`, `release` 등 | 브루미 목소리 | 플랫폼 TTS로 대체(`AndroidTtsSpeaker`), 그것도 없으면 무음 | AAR의 `@RequiresPermission` (lint가 발견) |
| `pleos.car.permission.STT_SERVICE` | SpeechToText 3.3.1 `initialize`, `request`, `stop` 등 | 말로 명령하기(주차 중 버튼) | 마이크 버튼이 숨겨짐 | AAR의 `@RequiresPermission` (lint가 발견) |

SDK 지원 중단(deprecated) 경고: Vehicle 2.3.2에서 `Gear.registerGearSelection`, `EvBattery.registerEvChargePortOpen`은 "Google 표준 속성(GEAR_SELECTION, EV_CHARGE_PORT_OPEN)을 쓰라"는 deprecated 표시가 있습니다. 현재 버전에서는 동작하므로 사용하되, 다음 SDK에서 대체 API로 바꿉니다.

SDK AAR이 자동으로 합쳐 넣는 퍼미션: `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`, `ai.pleos.playground.service.location.fused.FUSED_LOCATION_BINDING`, `android.permission.INTERNET`(TTS/STT의 CCG 검증용, AAR 매니페스트 주석 기준).

엔진룸 테마용 SDK 확인 (2026-11-02, Gradle 캐시의 Vehicle 2.3.2 · vehicle-aidl 1.1.1 · vehicle-data 1.1.11 바이너리): `ENGINE_RPM`, `ENGINE_COOLANT_TEMP`, `ENGINE_OIL_TEMP`, `ENGINE_OIL_PRESSURE`, `ENGINE_STATE`, `HVAC_ACTUAL_FAN_SPEED_RPM` 같은 속성 ID가 `ai.pleos.playground.vehicle.constant.PleosPropertyIds`에 있지만, 이를 읽는 함수는 내부(난독화된) 헬퍼뿐입니다. 공개 인터페이스(`Brake`, `CarInfo`, `Display`, `Door`, `DrivingMode`, `EvBattery`, `Fuel`, `Gear`, `HVAC`, `Light`, `Odometer`, `Safety`, `Seat`, `SideMirror`, `SteeringWheel`, `Tire`, `Toll`, `TurnSignal`, `Window`, `Wiper`)에는 엔진 회전수·모터 속도·모터 출력·배터리 전류를 주는 함수가 없습니다(`Safety`의 오일 "레벨" 상태, `Fuel`의 연비, `EvBattery`의 효율만 있음). 그래서 엔진룸 테마의 숫자는 모두 추정값이며, **새 퍼미션은 추가하지 않았습니다.**

## 선언하지 않은 퍼미션 (승인 후에만)

| 퍼미션 | 용도 | 켜는 방법 |
|---|---|---|
| `pleos.car.permission.CONTROL_CAR_CLIMATE` | 음성 명령으로 실차 `setHvacFanSpeed`, `setHvacTemperature` | Pleos App Market 승인 → 매니페스트 주석 해제 + `vroomi.allowHvacControl=true` |
| `android.car.permission.CAR_SPEED` | AAOS 표준 `PERF_VEHICLE_SPEED` (정확한 차속) | 주최 측 확인 → 매니페스트 주석 해제 + `vroomi.allowAaosSpeed=true` |

승인 전에는 "시원하게 해줘"에 브루미가 "직접 조절해 주세요~"로 답하고 `set…`을 호출하지 않습니다. 시뮬레이터에서는 제어가 그대로 시연됩니다.

## 쓰지 않는 것

- `RECORD_AUDIO`: 마이크는 Pleos 음성 서비스(CaaS)가 다룹니다. STT AAR이 요구하지 않습니다. 실기기에서 필요하면 추가합니다 (TODO(verify)).
- `PRIVATE_*`, 나머지 `CONTROL_*`: 사용하지 않습니다.

## 음악 재생 감지

- 새 퍼미션은 없습니다. 안드로이드 공개 API `AudioManager.registerAudioPlaybackCallback`(API 26+)만 씁니다(`app/audio/MusicWatcher.kt`).
- 알 수 있는 것은 **지금 다른 앱이 음악을 재생 중인지** 하나뿐입니다. 곡 제목, 가수, 재생한 앱은 알 수 없고, 소리를 듣거나 녹음하지 않습니다. 저장·전송도 없습니다.
- 음악으로 세는 것: 용도가 미디어이거나 용도를 밝히지 않은 재생. 말소리(음성 안내, TTS)와 알림·효과음으로 밝힌 재생은 뺍니다. 위드라이빙 자신의 테마 소리도 뺍니다.
- 박자는 곡에서 읽은 것이 아니라 고정된 96 BPM입니다. 감지가 안 되는 환경에서는 음악 반응만 나오지 않고 나머지는 그대로 동작합니다.
- 확인: Pleos Connect Emulator v2.1.0에서 기본 음악 앱 재생 → 반응, 일시정지 → 멈춤(2026-11-13).

## 보물지도 테마와 위치

- 새 퍼미션은 없습니다. 이미 선언한 `pleos.car.permission.FUSED_LOCATION` 하나만 씁니다(차속 계산과 같은 콜백).
- 위치를 받는 즉시 **여정 첫 위치 기준 동·북 미터**로 바꿉니다(`core/vehicle/route/LiveRoute.kt`). 위도·경도는 그 변환에 필요한 출발점만 메모리에 잠시 두며, 저장·로그·화면 표시·전송을 하지 않습니다.
- 지도 테마가 보는 길은 상대 미터 점들이고 메모리에만 있습니다. 여행이 끝나면 **원점을 지우고 단위 정사각형으로 줄인 64개 점**만 여행 기록(`TripRecord.sketch`)에 남습니다.
- 테스트: 투영 오차 20km에서 1% 미만, 여행 기록·지도 클래스에 위도·경도 필드 없음, 시나리오 로그에 좌표 패턴 없음(`MapPrivacyTest`).
