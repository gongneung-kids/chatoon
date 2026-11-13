# 카툰 신호 탐침: 만화 소재가 될 신호와 차의 목소리·받아쓰기·조명이 에뮬레이터에서 되는가 (2026-11-12)

차가 그리는 4컷 만화(차툰)를 만들기 전에 확인한 것이다.
에뮬레이터 Pleos Connect v2.1.0, debug 빌드, 운전자 사용자 10. 음성 서비스(`ai.pleos.playground.caas`)는 4.2.10이고 음성 SDK 3.3.1이 요구하는 최소 버전은 4.1.29다(`logs/caas-version.txt`).

## 방법

- debug 전용 탐침 `VehicleProbeReceiver`를 넣고 방송으로 부른다(`scripts/probe.py`). 값은 logcat에만 남는다.
- 권한은 debug 매니페스트에만 더했다: `CAR_CLIMATE`, `CAR_SEATS`, `CONTROL_CAR_LIGHTS`, `TTS_SERVICE`, `STT_SERVICE`. 설치하면 모두 `granted=true`였다(실행 중 요청 없음).
- 읽기는 두 번 봤다. 그대로 읽기(`logs/read.txt`), 그리고 가상 콘솔에서 값을 바꾼 뒤 앱이 읽는 값이 따라 바뀌는지(`scripts/change_and_read.py`, `logs/change-and-read.txt`).
- 받아쓰기는 마이크 대신 음성 파일을 넣었다. macOS `say -v Yuna`로 만든 한국어 문장을 16kHz 16bit mono PCM으로 바꿔 `sendAudio`로 100ms(3,200바이트)씩 보냈다(문서의 예시 방식).

## 결과

| 대상 | SDK 함수 | 결과 | 근거 |
|---|---|---|---|
| 창문 위치 | `Window.getCurrentWindowPosition` | **됨.** 네 창문, 범위 0~10. 콘솔 100/50 → 앱 10/5 | change-and-read |
| 공조 설정 온도 | `HVAC.getHvacTemperature` | **됨.** 운전석·조수석, 15~30도. 콘솔 17 → 앱 17.0 | change-and-read |
| 공조 풍량 | `HVAC.getHvacFanSpeed` | **됨.** 1~8. 콘솔 8 → 앱 8 | change-and-read |
| 시트 열선·통풍 | `Seat.getHvacSeatTemperature` | **됨.** 콘솔 3 → 앱 3 | change-and-read |
| 문 열림, 트렁크 | `Door.getCurrentDoorPosition` | **됨.** 승객 문 0~1, 트렁크·보닛 0~100. 콘솔에서 열자 운전석 1, 트렁크 100 | change-and-read |
| 주행 가능 거리 | `Odometer.getRangeRemainingDistance` | **됨.** 콘솔 31000m → 앱 31000.0(미터로 옴) | change-and-read |
| 크루즈 상태 | `DrivingMode.getCruiseControlState` | 읽힘(`ENABLED`). 값이 따라 바뀌는지는 안 봄 | read |
| 안전벨트 | `Seat.getCurrentSeatBeltBuckledState` | 읽힘(`false`). 값이 따라 바뀌는지는 안 봄(콘솔에 항목 없음) | read |
| 좌석 점유 | `Seat.getCurrentSeatOccupancy` | **못 씀.** 콘솔은 네 자리 모두 빈 자리인데 앱은 다섯 자리 모두 `true`. 콘솔에서 바꿔도 그대로 | change-and-read |
| 시트 앞뒤 위치 | `Seat.getCurrentSeatForeAftPosition` | **못 씀.** 콘솔 40 → 앱 0 | change-and-read |
| 공조 전원 | `HVAC.getHvacPowerState` | 실패. 좌석 영역으로는 지원하지 않는다고 나옴(`area ID 0x1 not supported`). 전체 영역으로는 안 해 봄(11-13 `dumpsys car_service`: 전체 좌석 영역 0x75로는 설정돼 있음, `../2026-11-13-cabin-signals`) | read |
| 주행 모드 | `DrivingMode.getCurrentDrivingModeState` | 실패. 차량 속성 목록에 없음 | read |
| 실내 조명 읽기 | `Light.getAmbientLightBrightness/Color` | **됨.** 밝기 100, 색 58879(= `#00e5ff`, 콘솔과 같음). 스위치는 앱 `OFF`·콘솔 `On`으로 서로 다름 | read |
| 실내 조명 제어 | `Light.setAmbientLightBrightness/Color/Switch` | **됨.** 밝기 30·색 `#2979ff`로 바꾸자 콘솔이 `{"color": "#2979ff", "brightness_pct": 30}`이 됨. 되돌리자 원래 값 | 2026-11-12 실행 출력(아래) |
| 차의 목소리 | `TextToSpeech`(ON_DEVICE) | **됨(로그 기준).** init → onReady → onStart → rms 5092 → onDone. 음성 서비스 로그에 `onDeviceTtsPlayer(kr.chatoon.app:ondevice) - complete`, 화자 `seoyoon`, `ko_KR` | logs/tts.txt |
| 받아쓰기 | `SpeechToText`(ON_DEVICE, 음성 파일) | **됨.** 세 문장 모두 정확 | logs/stt.txt |

받아쓰기 결과

| 넣은 문장 | 받아쓴 글 |
|---|---|
| 면접 보러 갔어요 | 면접 보러 갔어요 |
| 앞차가 너무 느려서요 | 앞차가 너무 느려서요 |
| 한눈팔다가 사고 날 뻔했어요 | 한눈 팔다가 사고 날 뻔했어요 |

실내 조명 제어 때의 출력

```
제어 전 콘솔: {"mode": "On", "color": "#00e5ff", "brightness_pct": 100}
   ambient switch set -> ON
   ambient bright set -> 30
   ambient color set -> 2718207
제어 후 콘솔: {"mode": "On", "color": "#2979ff", "brightness_pct": 30}
되돌린 뒤 콘솔: {"mode": "On", "color": "#00e5ff", "brightness_pct": 100}
```

## 판정

- 1차 사건으로 쓸 수 있는 새 신호: 창문, 공조 온도·풍량, 시트 열선, 문·트렁크 열림, 주행 가능 거리. 그때 앱이 읽던 기어·와이퍼·문 잠금·주행거리·조향·방향지시등·속도와 합친다. (뒤에 만화에 쓰지 않는 문 잠금·방향지시등은 수집에서 뺐고, 문·트렁크·주행 가능 거리도 기록하지 않는다.)
- 뒷자리 손님(좌석 점유)과 낯선 운전자(시트 위치)는 에뮬레이터에서 값이 따라오지 않아 시연할 수 없다. 사건 사전에는 "실차 확인 필요"로 남기고 1차 구현에서 뺀다.
- 실내 조명 연출, 차의 목소리, 받아쓰기는 1차 범위에 넣을 수 있다.

## 확인하지 못한 것

- 차의 목소리를 귀로 듣지 않았다. 이벤트와 음성 서비스의 재생 기록으로만 봤다.
- 받아쓰기를 마이크로 해 보지 않았다. 에뮬레이터의 마이크 입력을 켜야 한다(문서: Extended Controls > Microphone).
- 와이퍼 빠르기 단계. 지금 앱은 켜짐 여부만 읽는다.
- 주행 중(D단)에 조명 제어·목소리·받아쓰기가 막히는지. 카툰은 P단에서만 쓰지만 규제 동작은 따로 봐야 한다.
- release(R8) 빌드에서 음성 SDK가 도는지. R8이 Pleos SDK 안의 Gson을 깨뜨린 전례가 있다.
- 조명 제어 권한(`CONTROL_CAR_LIGHTS`)은 에뮬레이터에서는 그냥 주어지지만 실제 출시에는 앱마켓 승인이 필요하다.
