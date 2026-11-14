# 제출한 APK 파일 그대로 실행 확인 (2026-11-13 21시)

release 4차 확인(25fdcdc0)은 제출 APK보다 앞선 빌드였다. 그래서 제출 저장소 `gongneung-kids/chatoon`의 최종 커밋
`6fb2a17c6d8829e28014102ed6fefbfb4d59b6a2`에 들어 있는 `apk/공릉동아이들_차툰_release.apk`를 내려받아 그 파일 그대로 설치해 돌렸다.

## 파일

| 항목 | 값 |
|---|---|
| sha256 | `589ada2556a8e08b79432420b8a73397ae77b0dc7ebcd2b1c226a8487b4f6c5c` (16,123,988바이트) |
| 패키지·버전 | `kr.chatoon.app` 1.0.0 (versionCode 1), minSdk 28, targetSdk 34 |
| 서명 | APK Signature Scheme v2 검증 통과, `CN=Chatoon, O=Chatoon, C=KR` (인증서 SHA-256 `6cb09f00…037832c`) |
| release 여부 | 설치 뒤 `pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]` (DEBUGGABLE 없음) |
| 빌드 기준 | 앱 코드 3fb34195 (그 뒤로 `app/` 변경 없음) |

## 방법과 결과

1. debug 앱을 지우고 위 APK 설치, 속도·알림 권한을 앱 실행 전에 줌.
2. `am start -W`: 첫 실행 TotalTime 1,598ms.
3. 소개 화면 → 「시작하기」 → 「동의하고 시작하기」 (`screens/01-intro.jpg`).
4. `../2026-11-13-chatoon-release-4/scripts/release_drive4.py`와 같은 주행(비, 창문 70초, 공조 22→26°, 열선 2단, 급정거, R·D·R → P).
   → 제1화 「비 오는데 창문」, 칩 다섯 개(와이퍼 2분, 창문 내림 1분, 공조 22°→26°, 열선 2단, 급정거 1번), 급정거 질문과 보기 네 개 (`screens/02-episode-question.jpg`).
5. 보기 「앞차가 갑자기」 → 제목 「앞차가 갑자기 선 날」로 다시 그림, 답은 근거 줄 「주인의 답(앞차가 갑자기)」에만 (`screens/03-after-answer.jpg`).
6. `logcat -b crash`에 앱 비정상 종료 0건.

## 참고

- 머리 줄 「1분 운전 · 12.0km」는 확인 스크립트가 주행거리를 한꺼번에 늘린 탓이다(4차와 같음).
- 시작할 때 콘솔 기어가 Drive로 남아 있어 앱이 「주행 중에는 사용할 수 없습니다」로 가려졌다(Pleos 동작). P로 바꾸자 소개 화면이 나왔다.
- 같은 시간대(21:01~21:03)에 다른 세션이 같은 에뮬레이터로 주행했지만, 제출 APK 설치·동의는 그 뒤이고 화의 칩이 이 주행 값과 모두 일치해 섞이지 않았다.
- 끝난 뒤 debug(3fb34195)와 commuter 시연 데이터로 되돌림.

## 2차: 첫 주행 문장을 고친 새 APK (같은 날 21시 40분)

재점검에서 찾은 것: 처음 깐 앱의 첫 주행이 「별일 없는 날」이 되면서 「어제와 비슷한 시간에 나가, 비슷한 시간만큼 달렸다」(근거 「출발 시각과 주행 시간이 평소 범위」, 실제로는 계산하지 않는 고정 문장)와 「주차는 세 번. 평소와 비슷했다」가 나왔다. 또 README의 `python3 tools/vc.py scenario parking`이 release에서 「동의 안 됨」으로 멈췄다. 사용자 결정으로 앱을 고치고(a4ccd8b8) 도구도 고쳤다(eff104a5).

| 항목 | 값 |
|---|---|
| APK | `apk/공릉동아이들_차툰_release.apk`(11/14 팀명 통일로 `apk/HumanFirst_차툰_release.apk`로 이름만 바꿈, 내용·sha256 같음), sha256 `a4188ec9f8548395635aabfb73d493213dea307fe809563ef15404d6f576605c`, 16,123,996바이트 |
| 서명·release | `CN=Chatoon`, DEBUGGABLE 없음 |
| 빌드 기준 | a4ccd8b8, 단위 테스트 188개 통과, lintDebug·lintRelease 통과 |

README 순서 그대로 확인했다.

1. 설치 → 실행(TotalTime 1,145ms) → 소개 「시작하기」 → 「동의하고 시작하기」 → 알림 허용.
2. `python3 tools/vc.py scenario parking` → 「주행함 … 앱의 '이번 화'에서 확인」. R 기어 뒤 Pleos 후방 카메라 화면이 남아 차툰을 다시 열었다(README에 적음).
   → 제1화 「처음 함께 달린 날」: 2컷 「기록을 시작하고 첫 주행이었다.」(근거 「주행 기록 1번」), 3컷 「주차는 세 번. 뒤로 넣었다.」(평소와 견주지 않음), 4컷 「오늘부터 내가 본 것을 그려 둔다. 잘 부탁한다.」 (`screens/04-new-first-episode.jpg`)
3. 속도 권한 → 앱 다시 시작 → `../2026-11-13-chatoon-release-4/scripts/release_drive4.py` → 제2화 「비 오는데 창문」, 근거 「1초에 시속 7.5km 이상 줄어든 순간 1번」, 급정거 질문 (`05`).
4. 「누가 끼어들어서」 → 「누가 끼어든 날」, 「잘 섰다. 그러라고 브레이크가 있다.」, 말풍선 「잘했어요」(`06`). 비정상 종료 0건.
5. 동의 화면 문장이 모두 「~합니다」로 끝남(debug 같은 코드, `07`).
