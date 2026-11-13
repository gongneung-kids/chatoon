# 개발 환경 정리

확인일: **2026-10-29**. 출처: [공식 개발 환경 안내](https://appdevchallenge.auto-hmg.io/guide/development). 지원 범위와 버전은 **Pleos 공식 문서가 기준**입니다.

## 용어

| 이름 | 설명 |
| --- | --- |
| Pleos | 현대자동차그룹의 소프트웨어 중심 모빌리티 브랜드 |
| Pleos Connect | 차량용 인포테인먼트 플랫폼 (앱이 돌아가는 곳) |
| Pleos Playground | Pleos 개발자 포털. 플랫폼 소개와 개발 문서 |
| Pleos Connect SDK | Pleos Connect 환경에서 차량용 앱을 만들 때 쓰는 개발 도구 |
| Pleos App Market | 수상작 탑재 준비를 지원하는 앱 마켓 (입점 보장 아님) |

## 요구사항

- Android **API 28 이상**
- Gradle **8.0 이상**
- **Java 또는 Kotlin**
- 문서와 대응하는 SDK 버전은 [개발 환경 설정 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 있습니다.
- 에뮬레이터로 구동 검증과 보완을 할 수 있습니다. 2차 PT에서는 **에뮬레이터 구동 화면 또는 기기 시연**이 필수입니다.

## 쓸 수 있는 SDK

| 용도 | SDK |
| --- | --- |
| 차량 상태 | Vehicle SDK |
| 위치 정보 | FusedLocation SDK |
| 길 안내 연동 | NaviHelper SDK |
| 음성 입출력 | TextToSpeech SDK · SpeechToText SDK |

각 SDK의 제공 상태와 사용 조건은 [Pleos Connect SDK 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)에서 확인합니다. 심사 항목 **기술 활용성**은 이 SDK·API를 앱의 핵심 기능에 얼마나 적절히 썼는지 봅니다.

## 공식 예시 시나리오 (주제 제한 아님)

| 예시 앱 | 쓰는 SDK | 내용 |
| --- | --- | --- |
| 음성 메모 앱 | SpeechToText | 운전 중 음성을 받아 메모로 남김 |
| 안전운전 점수 앱 | Vehicle | 차량 상태 데이터로 주행 습관을 점수화 |
| 배달 기사용 앱 | NaviHelper | 경로 안내와 연동해 배달 동선 지원 |

## 공식 문서

- [Pleos Connect 개발 환경 설정 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) — 개발 시작 방법, 에뮬레이터 사용법
- [Pleos Playground](https://pleos.ai/playground) — 개발 문서·도구·가이드 포털
- [Pleos Connect SDK 공식 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)
- [Pleos Connect SDK API 참조](https://document.pleos.ai/api-reference/connect-sdk-pleos)
- [Vehicle SDK 튜토리얼](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk)

## 이 저장소에서의 위치

- 안드로이드 프로젝트는 `app/`에 둡니다.
- 제출용 release APK는 `apk/팀명_앱이름_release.apk`로 둡니다. 용량 초과 시 GitHub Releases를 씁니다.
- 서명 키(`*.jks`, `*.keystore`)와 `local.properties`는 올리지 않습니다.
