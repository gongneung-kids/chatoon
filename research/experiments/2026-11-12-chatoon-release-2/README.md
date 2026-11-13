# 차툰 release(R8) 실주행 검증 2차: 3D 그림·조명·주차 방향·차 색 뒤 (2026-11-12 밤, 커밋 8790c8b9)

release APK 19.0MB(그림 198장 포함). debug를 지우고 release 설치 → 동의 → 속도 권한 → `../2026-11-12-chatoon-release/scripts/release_drive.py`로 주행.

| 확인 | 결과 | 근거 |
|---|---|---|
| assets/art webp 읽기(R8) | 됨. 컷마다 무대(골목·도로·주차장), 급정거 그림(바퀴 자국, 끼익) | 01 |
| 급감속 → 질문 카드 | 됨 | 01 |
| 후면 주차 기록(R → P) | 됨. 일기 "뒤로 넣었다", 근거 "마지막 기어 R", 차 앞이 통로 쪽 그림 | 02 |
| 조명 연출(R8, Vehicle Light) | 됨. 낮 화를 보면 `#ffe2b8 70`, 설정 탭에서 원래 값(`#2b3a8c 35`)으로 되돌림 | 03, 콘솔 body.ambient_light |
| 성능 `vc.py measure 60`(앱 뒤, 속도 권한, 신호 변경 116회) | CPU 코어 1개 기준 2.07%(4코어 0.52%), PSS 평균 64.6MB·최대 79.6MB → 통과 | 아래 |
| 앱을 앞에 두고 화 여러 개를 펼친 뒤 | `dumpsys meminfo`: TOTAL PSS 85.0MB(Native Heap 62.9MB, 그림 디코딩) → 기준 400MB 안 | |

참고
- 시작 조명이 이미 `#2b3a8c 35`였다: 앞서 debug 앱이 밤 화 조명을 켠 채 지워져(앱 삭제는 되돌릴 기회가 없고 저장한 원래 값도 함께 지워짐) 그 값이 남았다. 가상 콘솔 `/vehicle/body`는 조명 값을 받지 않아 흰색으로 되돌리지 못했다.
- 낮 화를 만들려고 시계를 14:00으로 바꿨다(`auto_time 0` + `cmd alarm set-time`, 끝나고 `auto_time 1`). 바꾼 직후 바로 출발하면 기록 시계가 주차 중 다시 맞추기 전이라 밤으로 기록된다(RecordingClock 설계대로). 주차 중 와이퍼를 켰다 꺼서 시계를 맞춘 뒤 달렸다.
- logcat의 FATAL `ai.pleos.service.ui` DeadObjectException은 Pleos 시스템 서비스의 것으로 차툰과 무관.
- 확인 못 한 것: 놀란 화의 조명 번쩍임(1.5초 안에 읽지 못함, 단위 테스트로 장면만), 실차, 실제 스피커·마이크.
