# 시연 도구의 카툰 신호 명령이 실제 가상 콘솔에서 되는가 (2026-11-12)

`tools/vc.py`에 더한 명령(창문, 공조 온도, 풍량, 시트 열선·통풍, 트렁크)을 Pleos Connect v2.1.0 에뮬레이터의 가상 콘솔에서 돌려 봤다. 카툰은 이 신호들을 만화의 소재로 쓰므로, 시연 때 콘솔에서 값을 바꿀 수 있어야 한다.

## 방법

- `check.py`: 콘솔을 한 번 열고 값을 차례로 바꾼다. 명령마다 도구가 상태를 다시 읽어 적용됐는지 확인한다. 끝나면 처음 값으로 되돌리고, 상태가 처음과 같은지 본다.
- 명령줄(`python3 tools/vc.py set …`)로도 값 하나를 바꾸고, 범위 밖 값과 숫자가 아닌 값을 넣어 거절되는지 봤다.
- 기어·속도·와이퍼와 앱은 건드리지 않았다(P단).

## 결과

| 명령 | 콘솔에 보낸 것 | 상태에서 읽힌 값 | 결과 |
|---|---|---|---|
| `set windows 100` / `50` / `0` | `/vehicle/body?window_fl=…&window_fr=…&window_rl=…&window_rr=…` | `body.windows` 네 곳 모두 100 / 50 / 0 | 됨 |
| `set temp 17` / `26.5` | `/vehicle/hvac?driver_temp=…` | `hvac.driver.temp_c` 17.0 / 26.5 | 됨 |
| `set fan 8` / `1` | `/vehicle/hvac?fan_speed=…` | `hvac.fan_speed` 8 / 1 | 됨 |
| `set seatheat 3` / `-2` | `/vehicle/hvac?driver_seat=…` | `hvac.driver.seat_temp` 3 / -2 | 됨 |
| `set trunk true` / `false` | `/vehicle/body?trunk=…` | `body.closures.trunk` true / false | 됨 |
| `set fan 9`, `set temp abc` | 보내지 않음 | 오류 문구와 종료 코드 1 | 거절됨 |

끝난 뒤 상태는 처음과 같았다(창문 0, 온도 22.0, 풍량 3, 열선 0, 트렁크 닫힘). 출력 원본은 `check-output.txt`, `set-command-output.txt`.

## 확인하지 못한 것

- 앱이 이 값들을 주행 중에 모아 한 화의 재료로 쓰는지는 이 실험의 범위가 아니다. 앱에서 값이 따라 읽히는 것은 같은 날 탐침에서 봤다(`../2026-11-12-cartoon-probe`).
- 창문 값의 방향(0이 닫힘, 100이 끝까지 내림). 콘솔 기본값이 0이고 안드로이드 차량 표준(WINDOW_POS)이 그 뜻이라 그렇게 봤지만, Pleos 문서에는 적혀 있지 않다.
- 조수석 쪽 값(`passenger_temp`, `passenger_seat`)과 문 하나씩 열기(`door_fl` 등)는 도구에 넣지 않았다.
