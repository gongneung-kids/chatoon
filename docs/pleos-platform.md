# Pleos 플랫폼 정리

수집일: **2026-10-30**. 출처는 [Pleos Playground 개발 문서](https://document.pleos.ai/) 공개 페이지 951개이며, 원문 텍스트는 수집본에 있습니다. 공모전 앱을 기획·개발할 때 바로 필요한 내용만 요약했습니다.

## 한눈에 보기

| 항목 | 내용 |
| --- | --- |
| Pleos | 현대차그룹의 소프트웨어 중심 모빌리티 브랜드 (42dot) |
| Pleos Connect | 차량용 인포테인먼트 OS. Android Automotive 기반. 첫 적용은 더 뉴 그랜저, 디 올 뉴 아반떼 |
| 플랫폼 종류 | Connect-S, Connect-L, Connect-W (`android.os.Build.DEVICE`로 구분, 실차에서만 값이 나옴) |
| 지역 코드 | `product.sw.target_region` = KOR / EUR / USA·CAN |
| 앱 개발 | Android API 28 이상, Gradle 8.0 이상, Java·Kotlin, 에뮬레이터 v2.1.0 (2026-10-19) |
| AI 비서 | Gleo AI. 말한 사람의 좌석을 인식하고, 먼저 제안(proactive suggestion)도 함 |

## 외부 개발자(공모전 팀)가 쓸 수 있는 것

함수 단위 전체 목록은 [SDK 기능 사전](sdk-reference.md)에 있습니다.

| 이름 | 하는 일 | 공모전에서 쓸 만한 기능 |
| --- | --- | --- |
| **Vehicle SDK** (v2.3.2) | 차량 상태 읽기·제어·변경 알림 (20개 도메인) | 공조·창문·시트·조명·와이퍼·도어 제어, 기어·브레이크·연료·배터리·경고등·타이어 읽기 |
| **NaviHelper SDK** (v3.0.5) | Pleos 내비게이션과 연동 | 목적지·경유지 설정, 도착 예정 시간·남은 거리, **목적지·경유지 도착 이벤트**, 단속카메라·위험 지점, 현재 도로 제한속도, 정체 정보, 통행료, 트립 요약, 집·회사 즐겨찾기, 장소 검색, 전기차 충전 계획 |
| **FusedLocation SDK** | 차량 위치 | 현재 위치, **지오펜스 진입·이탈 알림(ProximityAlert)** |
| **TextToSpeech SDK** | 음성 출력 | 언어·화자 설정, **오디오 영역(좌석) 지정** |
| **SpeechToText SDK** | 음성 인식 | 앱 자체 음성 명령 |
| **Gleo Interface** | 앱 기능을 Gleo AI에 등록 | `assets/schema.json`에 함수 최대 3개, Gleo가 호출하면 앱이 처리하고 결과 반환. 화면 없이 백그라운드(`service`) 실행 가능, 7초 안에 응답 |

### 쓸 수 없는 것

- **Pleos Platform SDK 1.0** (2026-10-23, CloudHub·Pleos Location·Connected Car Status·Window State 등): `signature|privileged` 권한이라 차량 플랫폼 승인이 필요합니다. 일반 앱은 사용할 수 없습니다.
- **LLM SDK**: 에뮬레이터 v2.1.0에서 제공이 종료됐습니다.
- **비상등·트렁크 API**: Vehicle SDK에 없습니다.
- 휴대폰 기능 일부: 전화·문자·캘린더·주소록·자이로 등은 차량에서 제한될 수 있습니다.

## 반드시 지켜야 하는 규칙

| 규칙 | 내용 |
| --- | --- |
| 주행 중 금지 | 시트·도어·차량 설정 조작, 영상, QR 로그인 (전 지역) |
| 미디어 앱 | 동영상·게임·브라우저는 정차 중에만. 주행이 시작되면 UI를 숨김 |
| 주행 중 동작 | 주행 중 동작 허가(Distraction Optimization)를 선언하지 않은 앱은 주행 중에 실행할 수 없음 |
| CPU | 주행 중 내비게이션이 아닌 앱 **5% 미만**, 정차 중 25% 이하 |
| RAM | 400MB 이하 |
| 반응 속도 | 터치 반응 0.25초, 버튼 2초, 실행·로딩 10초 이내 |
| 백그라운드 | 금지는 아니며 실행 조건·중단 정책을 정하고, 상주·타이머·폴링을 최소화해야 함 |
| 차량 제어 권한 | Vehicle SDK 권한은 **앱마켓과 사전 협의·승인된 경우에만** 선언 가능. 미승인 권한이 있으면 심사 탈락 |
| 하드웨어 | 차량 하드웨어에 직접 접근 금지. SDK만 사용 |
| 네트워크 | 주행 중 끊김에 대비해 캐시·재시도·오프라인 안내 |
| 화면 | 레터박스 없이 화면을 채우고, 창 크기·방향이 바뀌어도 기능이 같아야 함 |

## 화면 구조

- 운전석 가까이에 고정된 **Driving view**가 있고, 앱은 이 영역을 가리거나 닫을 수 없습니다.
- 앱 영역에는 창이 1~2개 들어갑니다. portrait는 창 1개, landscape는 창 2개입니다.
- 조수석 앱 패널(`APP_PASSENGER`)과 이벤트 위젯 패널(`EVENT_WIDGET`)이 따로 있습니다. 상태 조회는 특권 SDK를 써야 합니다.
- Gleo 응답 화면은 앱보다 위에 뜨고, 넓은 화면에서는 말한 사람 좌석 가까이에 표시됩니다.
- 조그 다이얼(CCP)로도 조작합니다.

## 수익화

- **인앱 결제**: 플랫폼 전용 Payment SDK를 써야 하고, 현재 준비 중입니다.
- **광고**: 정차 상태, 보조 화면, 승객 화면으로 제한될 수 있습니다.

## 에뮬레이터에서 흉내 낼 수 있는 것 (Virtual Console)

| 영역 | 신호 |
| --- | --- |
| Motion | 기어, 브레이크, 핸들 각도, 주행거리, 핸들 열선 |
| ADAS | 크루즈 대기·작동, 목표 속도 |
| Convenience | 도어, 창문, 트렁크, 시트, 조명, 와이퍼, 하이패스, 글로브박스 |
| Safety | 경고등 (주차브레이크, 안전벨트, ABS 등) |
| Energy | 주행 가능 거리, 배터리, 충전 상태·전류, 목표 충전량, 충전구 |
| Climate | 공조 전원·온도·풍량·풍향, 내기순환, 성에 제거, 시트 열선·통풍, 공기청정 |

## 실제 확인한 상태 (2026-10-30)

**Playground 프로젝트의 SDK 상태**

| SDK | 상태 |
| --- | --- |
| Vehicle State (차량 상태 읽기) | **사용 가능** (신청 불필요) |
| NaviHelper | **사용 가능** (신청 불필요) |
| FusedLocation, TextToSpeech, SpeechToText | Coming Soon (신청 불가) |
| **Vehicle Control (차량 제어)** | **Coming Soon (신청 불가)** |

**에뮬레이터에서 확인한 신호 구성**: 문 잠금 영역은 `[보닛]`, `[1·2열 문 4개 묶음]` 두 개. 경고등 조회(`getTelltalesInfo`)는 `CAR_SAFETY`를 선언해도 권한 오류(특권 권한 필요로 보임). 연료(%)는 이 전기차 에뮬레이터에서 미지원.

→ Playground의 "Coming Soon"은 **앱마켓 출시용 권한 승인**이 아직 열리지 않았다는 뜻입니다.

**에뮬레이터에서는 차량 제어가 됩니다 (2026-10-30 직접 확인).**

- SDK는 공개 Maven(`ai.pleos.playground:Vehicle:2.3.2`)에서 받으며 제어(set) API가 포함돼 있습니다.
- 에뮬레이터에서 `pleos.car.permission.CONTROL_CAR_*` 권한은 `normal` 등급이라 매니페스트에 선언만 하면 자동 승인됩니다 (SDK 소스 주석에도 "protection level: normal").
- 우리 앱(`app/`)으로 운전석 창문을 열고(`setWindowPosition`), 차량이 바뀐 값을 콜백으로 다시 알려주는 것까지 확인했습니다. 창문 허용 범위는 0~10입니다. 값을 넣기 전에 `check...Capabilities`로 범위를 먼저 물어야 합니다.
- 실제 출시에는 앱마켓 권한 승인이 필요하므로 기획서 로드맵에 적습니다.

**에뮬레이터 (M1 Pro 맥에서 실행 확인)**

- Pleos Connect v2.1.0 이미지(`system-images;android-34;pleos_car_emulator;arm64-v8a`, 약 19GB) 설치·부팅 성공
- 설치된 시스템 앱: Pleos 내비(`ai.umos.maps.android.navigation.app`), 차량 서비스, Ambient AI 음성 인식(`ai.umos.ambientai`), TTS 서비스(`ai.pleos.playground.caas`). **Gleo 화면 앱은 없음**(모델 제공자만 있음). 다만 Gleo 응답 방송(`ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`)을 받는 수신자는 `ai.umos.ambientai`(사용자 10)로 등록돼 있음(2026-11-01 `dumpsys activity broadcasts` 확인). 앱은 이 패키지가 있으면 그 패키지에만, 없으면 공식 예제처럼 패키지 지정 없이 응답을 보냄 → 요청 흉내와 응답 전달까지는 시험 가능, 실제 음성 호출은 시험 어려움
- 인터넷 연결 됨
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/`

**팀원 에뮬레이터 설치 순서**

1. Android Studio 설치 → 설정 마법사 Standard → 라이선스 동의
2. SDK Manager → SDK Update Sites → `+` → URL `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml` (윈도우는 `crp-sys-img.xml`)
3. SDK Platforms → Show Package Details → API 34 → **Pleos Connect v2.1.0 APIs** 체크 → 42dot 라이선스 동의
4. Device Manager → Automotive → Pleos Connect v2.1.0, **Cold boot**, RAM 4GB
5. **Virtual Console이 `emulator_inactive`(503)를 돌려주면** 브라우저로 `http://127.0.0.1:8088/`을 한 번 열어 활성화합니다.
6. **앱 저장 파일은 운전자 사용자(user 10) 폴더에 있습니다**: `adb shell run-as <패키지> --user 10 ls files/` (그냥 `run-as`는 user 0을 봄)
7. 첫 실행 때 adb가 `unauthorized`면 `adb pubkey ~/.android/adbkey > ~/.android/adbkey.pub` 후 에뮬레이터 재시작

## Pleos Connect 기본 기능 (아이디어가 겹치면 안 되는 목록)

출처: [현대차 Connect-S(Pleos Connect) 사용설명서](https://ownersmanual.hyundai.com/ivi/Connect-S/AVNT/KOR/Korean/006_Drivingmodes_2.html) 68페이지, 수집본은 `research/pleos-manual/`. 2026-10-30 기준.

| 분류 | 기본 기능 |
| --- | --- |
| AI·검색 | Gleo AI(대화, 음성 명령, 대화 기록), 통합 검색 |
| 내비 | 지도, 목적지 검색, 즐겨 찾는 장소, 다른 경로, 경로 편집 |
| 주차·주행 영상 | 후방 뷰, 서라운드 뷰, 빌트인 캠(녹화·영상 보기·휴대폰 연결) |
| 공조 | 앞좌석·뒷좌석 공조 화면 |
| 주행 모드 | 드라이브 모드, 터레인 모드, **나만의 모드(MY DRIVE MODE)** |
| **상황별 차량 모드** | **루틴(자동·수동, 기본 루틴 템플릿, 타이머, 실행 중지·원상 복구)**, **스테이 모드**(시동 없이 편의 장치, 목적지 도착 후 P단이면 자동 제안), **세차 모드**(미러 접힘·창문 닫힘·와이퍼 정지·내기순환 등), **중립 주차**, **발레 모드** |
| 차량 관리 | 차량 진단, 타이어 정보, 서비스 예약, 소프트웨어 업데이트(예약 포함) |
| 결제·서비스 | 하이패스(결제 카드), 블루링크, 앱 마켓 |
| 개인화 | 사용자 프로필, 컨트롤 영역·앱 라이브러리·슬림 디스플레이·HUD 편집 |
| 미디어·통신 | 라디오, 블루투스 오디오, DMB, 전화·메시지(읽어주기·보내기), Android Auto, Apple CarPlay |

**규칙**: 새 아이디어는 이 표, 테슬라·벤츠 기능, 이전 공모전 우수작 16개와 먼저 대조합니다. 기능 하나가 겹치는 것은 괜찮지만, **앱의 핵심 가치가 이 표에 있으면 탈락**입니다.

## 그 밖의 Pleos 서비스 (참고)

- **Connect Mobile**: 차주용 폰 앱. 원격 제어(공조·충전·V2L·편의), 예약, 차량 공유
- **Vehicle Data API**: 차주가 API 키를 받아 자기 차 데이터(위치·속도·배터리·운행 상태)를 서버에서 조회. 사업자용도 있음
- **Fleet API**: 법인 차량 관리. 예약·운전자·운행·정비·이상 이벤트는 "지원 예정"

## 기능 전수 대조(2026-11-02)

Playground 문서 952개를 기능별로 대조했다. 판정과 근거:

| 구분 | 기능 | 상태 |
|---|---|---|
| 사용 중 | Vehicle SDK 하향등·와이퍼·문 잠금·누적 주행거리, AAOS 기어·속도(PERF_VEHICLE_SPEED)·운전 제한, NaviHelper 화면 요청(경로 옵션·**휴게소 검색 SearchPlace**·**내비 설정 Settings**(과속 경고 안내)), Gleo App Action 3함수, Back 숨김, CCP 미지원 선언(`com.hmg.rotary.ROTARY_SUPPORTED=false`) | 에뮬레이터 확인 |
| 에뮬레이터 미지원 확인 | 안전벨트 미착용 경고 `hasSeatBeltNotBuckledWarning`, 엔진오일 `getEngineOilLevelState`, 브레이크액 `getBrakeFluidLevelState`, 워셔액 `hasLowWasherFluidWarning` | 호출하면 "property ID is not in carPropertyConfig list"(2026-11-02 탐침). 콘솔의 Telltales "Seat Belt"는 이 속성에 연결되지 않음. 실차 확인 전에는 기능으로 쓰지 않음 |
| 로터리(CCP) | `cmd car_service inject-rotary`로 주입하면 포커스가 차량 왼쪽 패널에서만 움직이고 Compose 화면으로 들어오지 않음 | 사용자 설치 앱은 미지원 허용 → 선언하고 터치로 모든 조작 완결 |
| 쓰지 않음(2026-11-03 제거) | 경고등 조회 `getTelltalesInfo`·`CAR_SAFETY` 권한: 특권 권한이 필요해 일반 앱에서는 항상 실패 | 명세 경고등 행 |
| 사용 중(2026-11-03 추가) | NaviHelper `onCurrentRoadSpeedLimit`(제한속도를 넘은 시간, 명세 §17). 내비 권한 없이 1초마다 옴(에뮬레이터 확인, 값은 0만 — 에뮬레이터 내비가 움직이지 않음) | 단위 테스트, 실차 확인 필요 |
| 쓰지 않음(2026-11-03 결정) | NaviHelper 재탐색 `onReRouteEnded`: 인지 저하 연상(의료 표현 금지 원칙)·의도적 경로 변경과 구분 불가·에뮬레이터 재현 불가. `getCurrentLocationInfo`: NAVI_ROUTE 권한 필요(위치 미수집 원칙) | 탐침 기록 research/experiments/2026-11-03-navi-events |
| 후보(설계 검토 중) | 차 공식 트립 거리·시간, 좌석 점유(동승 비율, 새 권한 CAR_SEATS) | 판정 신뢰도·권한 추가 여부 검토 |
| 쓰지 않음(원칙) | 모든 제어 API(공조·창문·시트·미러 등), 위치·목적지 조회(FusedLocation, NaviHelper 위치·즐겨찾기 정보), VIN, 에어백, Platform SDK(특권 권한), Vehicle Data·Fleet API(서버·사업자용), PRIVATE_* 권한 API | 최소 권한·위치 미수집·차량 미제어 원칙 |

