# 실내 조명 연출 검증 (2026-11-12 밤, 에뮬레이터 debug)

값은 가상 콘솔 `/vehicle/state`의 `body.ambient_light`(`scripts/ambient.py`). 처음 값은 `{"mode":"On","color":"#ffffff","brightness_pct":70}`.

| 상황 | 콘솔 값 | 판정 |
|---|---|---|
| 설정 꺼짐(기본), 이번 화 | #ffffff 70 | 그대로(01) |
| 이번 화에서 "켜기"(제135화, 낮) | #ffe2b8 70 | 낮 = 따뜻한 흰색 |
| 설정 탭으로 이동 | #ffffff 70 | 되돌림 |
| 지난 화 탭(펼치기 전) | #ffffff 70 | 그대로 |
| 지난 화에서 밤 화 펼침 | #2b3a8c 35 | 밤 = 남색·어둡게(02) |
| D단 | #ffffff 70 | 되돌림 |
| 다시 P단 | #2b3a8c 35 | 다시 맞춤 |
| Pleos 홈 키·뒤로 키 | 그대로 | 차툰 창이 화면에 남아 RESUMED·visible(보고 있음) |
| 다른 앱(차량 제어)을 앞에 띄움 | 처음엔 그대로 → **고침** → 되돌림 | 화면이 안 보이면 Compose가 재구성을 멈춰 되돌리지 못했다. ON_STOP 이벤트에서 바로 되돌리게 고침 |
| 조명을 바꾼 채 앱 강제 종료(재설치) | 처음엔 남색으로 남음 → **고침** → #ffffff 70 | 바꾸기 전 값을 SharedPreferences에 적어 두고, 다음 시작(재설치 뒤 MY_PACKAGE_REPLACED로 깨어남) 때 되돌림. logcat `Restoring ambient light left changed by a previous run` |
| 처음 실행 직후 조명이 늦게 바뀜 | 8초 넘게 → **고침** → 2초 안 | 연결 전 호출이 늦음. 켜 둔 경우 앱 시작 때 차량 연결을 미리 엶 |

확인 못 한 것: 놀란 순간 화의 처음 1.5초 주황 번쩍임(단위 테스트로 장면만 확인), 실차 조명, release, 실제 출시 때 CONTROL_CAR_LIGHTS 앱마켓 승인.
