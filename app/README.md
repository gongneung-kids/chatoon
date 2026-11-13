# app

"차툰" 안드로이드 프로젝트입니다. Pleos Connect 에뮬레이터에서 실행합니다. 에뮬레이터 설치는 [Pleos 플랫폼 정리](../docs/pleos-platform.md)를 따릅니다. 앱이 무엇을 하는지는 [기획서](../contests/hmg-vehicle-app/기획서.md)에 있습니다.

## 빌드와 실행

필요: JDK 17 이상(CI는 21), Android SDK Platform 37. 아래는 macOS 예시이며, Windows·Linux는 `sdk.dir`과 `JAVA_HOME`만 그 환경의 경로로 바꾸면 됩니다.

```bash
cd app
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # 처음 한 번
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :chatoon:assembleDebug
adb install -r chatoon/build/outputs/apk/debug/chatoon-debug.apk
adb shell am start --user 10 -n kr.chatoon.app/.ui.MainActivity   # 운전자 사용자(10)
```

Android Studio에서 `app/` 폴더를 열어도 됩니다.

## 구성

| 항목 | 값 |
| --- | --- |
| 모듈 | `chatoon` (패키지 `kr.chatoon.app`) |
| 빌드 | Gradle 9.8.2, Android Gradle Plugin 9.5.0, compileSdk 37, minSdk 28 |
| SDK | `ai.pleos.playground:Vehicle:2.3.2`, `TextToSpeech:3.3.1`, `SpeechToText:3.3.1` (공개 Maven `nexus-playground.pleos.ai`, 계정 불필요) |

## 구조

```text
chatoon/src/main/kotlin/kr/chatoon/app/
├── domain/            # 안드로이드·SDK에 의존하지 않는 순수 Kotlin (단위 테스트 대상)
│   ├── vehicle/       # VehicleState, VehicleGateway(읽기 통로), DriveContext(정차/주행 판단),
│   │                  # HarshEventDetector(속도 변화로 급가속·급감속 횟수만 셈)
│   ├── drive/         # TripRecorder(차량 상태 → 주행 기록), Trip, 기록 형식(DriveLogCodec)
│   ├── cartoon/       # 만화를 쓰는 규칙: TripSeen(기록 → 한 화의 재료), Routine(평범한 날의 고비),
│   │                  # CartoonWriter(네 컷과 대사), OwnerWords(주인의 답에서 상황 읽기), AnswerBook, LightScene(조명 색)
│   └── analysis/      # SunCalculator(기기 안 일출·일몰 계산)
├── data/
│   ├── vehicle/       # PleosVehicleGateway(Pleos Vehicle SDK), CarGearSource·CarSpeedSource(표준 기어·속도),
│   │                  # UxRestrictionSource(운전 제한), MoodLight(실내 조명 연출, 바꾸기 전 값으로 되돌림)
│   ├── speech/        # CarVoice(TextToSpeech, 차의 한마디), Dictation(SpeechToText, 말로 답하기)
│   ├── drive/         # 주행 기록 파일(앱 전용 저장소·암호화, 차 밖으로 보내지 않음)
│   ├── cartoon/       # 주인의 답 저장(암호화)
│   ├── consent/       # 동의 버전·시각. 철회하면 기록과 답을 즉시 삭제
│   └── storage/       # 안전한 파일 쓰기(DurableFile), AES-256-GCM 암호화(데이터 키는 Android Keystore 키로 감쌈)
├── service/           # 화면을 닫아도 기록하는 포그라운드 서비스, 시동 시 재시작
├── ui/                # Jetpack Compose 화면(이번 화·지난 화·설정, 동의 화면)
│   └── cartoon/       # 만화 그림: Compose Canvas로 그 자리에서 그림(그림 파일 없음)
└── ChatoonApplication  # 객체 조립. 차량 연결과 기록은 앱 프로세스에 묶여 화면을 닫아도 동작
```

차량 제어는 실내 조명 하나뿐이고, 설정에서 켠 사람에게만, 세워 둔 동안에만 씁니다. 창문·공조·열선은 읽기만 합니다. 위치·경로는 수집하지 않고, 속도 값도 저장하지 않습니다(급하게 선·세게 밟은 횟수만, 운전자가 속도 권한을 켤 때).

## 검사

```bash
./gradlew :chatoon:testDebugUnitTest :chatoon:lintDebug :chatoon:assembleDebug
```

GitHub Actions가 `app/` 변경마다 같은 검사와 release(R8) 빌드를 돌립니다. R8 문제는 빌드가 아니라 실행 중에 드러날 수 있으니, release는 에뮬레이터 실주행으로도 확인합니다.

## 에뮬레이터 시연·검증 (Virtual Console)

`adb forward tcp:8088 tcp:8088` 뒤 [`tools/vc.py`](../tools/vc.py)를 씁니다.

```bash
python3 tools/vc.py status              # 콘솔·앱 상태
python3 tools/vc.py scenario parking    # 주차를 고쳐 넣는 주행 한 번(그 밖의 시나리오: scenario all)
python3 tools/vc.py set wiper Low       # 값 하나 바꾸고 적용 확인(gear·wiper·windows·temp·fan·seatheat 등)
python3 tools/vc.py measure 60          # 주행 중 CPU·메모리 측정
```

## 제출용 release APK

서명 키와 비밀번호는 저장소 밖 `~/.gradle/gradle.properties`에 둡니다(`chatoon.storeFile`, `chatoon.storePassword`, `chatoon.keyAlias`, `chatoon.keyPassword`). 키가 없으면 서명 없이 만들어집니다.

```bash
./gradlew :chatoon:submissionApk -Pchatoon.teamName=<팀명>
```

결과는 `../apk/<팀명>_차툰_release.apk`(공모전 규정 파일 이름)입니다. release는 R8로 줄이고 진단 로그를 지웁니다.
