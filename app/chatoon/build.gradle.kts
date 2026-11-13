plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.chatoon.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "kr.chatoon.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // release 서명: 키와 비밀번호는 저장소 밖 개인 설정(~/.gradle/gradle.properties)에 둔다. 없으면 서명 없이 만든다.
    val signing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { providers.gradleProperty("chatoon.$it").orNull }
    signingConfigs {
        if (signing.values.all { it != null }) {
            create("release") {
                storeFile = file(signing.getValue("storeFile")!!)
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // 점검용(제출하지 않음): release와 같은 R8·리소스 축소에 debug의 시연 입구(adb 셸 전용)만 더한다.
        // release에는 시연 데이터가 없어 "제안" 단계 화면(내비·휴게소 버튼)을 R8이 적용된 상태로 누를 수 없었기 때문이다.
        create("releaseCheck") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
    }
    sourceSets.getByName("releaseCheck") {
        kotlin.directories += "src/debug/kotlin"
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }

    // 안드로이드 표준 차량 API(CarPropertyManager): 기어 상태
    useLibrary("android.car")

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // 감사 테스트(SurfaceLabelAuditTest 등)는 문자열 자원 파일을 실행 중에 직접 읽는다. Gradle이 이를 입력으로 모르면
            // 문구만 바뀐 빌드에서 테스트를 건너뛰고(UP-TO-DATE) 지난 결과를 쓴다 — 11-04 로컬 '통과', CI 실패의 원인.
            it.inputs.dir("src/main/res/values").withPropertyName("auditedStrings").withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.org.json)
}

// 제출용 APK: apk/<팀명>_차툰_release.apk (공모전 규정 파일 이름). 팀명은 -Pchatoon.teamName=... 또는 gradle.properties.
val teamName = providers.gradleProperty("chatoon.teamName").orElse("팀명")
tasks.register<Copy>("submissionApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/chatoon-release.apk"))
    into(rootProject.layout.projectDirectory.dir("../apk"))
    rename { "${teamName.get()}_차툰_release.apk" }
}

