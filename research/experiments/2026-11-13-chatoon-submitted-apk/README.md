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
