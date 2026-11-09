# 주최 측 문의 메일 초안

- 받는 사람: appdevchallenge@hyundai-partners.com
- 제목: [차량용 앱 개발 공모전] 개발 환경 문의 – 차량 속도 / 공조 제어 퍼미션

---

안녕하세요. 차량용 앱 개발 공모전 참가를 준비하고 있습니다.
Pleos Connect SDK 사용과 관련해 세 가지 여쭙고 싶습니다.

1. **차량 속도와 가속 페달 값**
   Vehicle SDK 2.3.2에는 차량 속도와 가속 페달 개도 속성이 없는 것으로 확인했습니다(크루즈 목표 속도만 있음).
   저희 앱은 주행 속도에 맞춰 캐릭터가 걷고 뛰는 앱이라 차속이 필요합니다.
   - AAOS 표준 `CarPropertyManager`의 `VehiclePropertyIds.PERF_VEHICLE_SPEED`(퍼미션 `android.car.permission.CAR_SPEED`)를 Pleos App Market 앱에서 써도 되는지,
   - 안 된다면 권장하는 차속 취득 방법이 있는지(FusedLocation 위치 변화로 계산하는 방식이 괜찮은지)
   알려 주시면 감사하겠습니다.

2. **공조 제어 퍼미션**
   음성으로 "시원하게 해줘"라고 하면 풍량/온도를 바꾸는 기능을 넣으려 합니다(`HVAC.setHvacFanSpeed`, `setHvacTemperature`, 퍼미션 `pleos.car.permission.CONTROL_CAR_CLIMATE`).
   공모전 출품 앱에서 이 퍼미션을 선언·사용할 수 있는지, 사전 협의가 필요한지 궁금합니다.

3. **심사용 GitHub 계정**
   비공개 저장소로 제출할 경우 Collaborator로 초대할 심사용 GitHub 계정이 언제 공지되는지 알려 주시면 감사하겠습니다.

답변 주시면 그에 맞춰 개발하겠습니다. 감사합니다.

(이름·연락처는 보내는 메일 서명에만 넣고, 기획서 02 항목에는 넣지 않습니다.)
