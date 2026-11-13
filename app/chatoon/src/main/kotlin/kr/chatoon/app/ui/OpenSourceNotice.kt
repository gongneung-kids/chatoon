package kr.chatoon.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.chatoon.app.R

/**
 * release APK에 들어가는 오픈소스와 라이선스. Apache License 2.0 4조 a항(받는 사람에게 라이선스 사본 제공)을 지킨다.
 * 목록은 releaseRuntimeClasspath의 Maven POM에서 확인했다(README "오픈소스 라이선스와 출처"와 같은 내용).
 * NOTICE 파일이 있는 의존성은 없다(2026-11-01 확인).
 */
internal data class OpenSourceComponent(val name: String, val url: String)

internal val OpenSourceComponents = listOf(
    OpenSourceComponent("Jetpack Compose, AndroidX", "https://developer.android.com/jetpack/androidx"),
    OpenSourceComponent("Kotlin 표준 라이브러리", "https://github.com/JetBrains/kotlin"),
    OpenSourceComponent("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceComponent("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization"),
    OpenSourceComponent("Gson", "https://github.com/google/gson"),
    OpenSourceComponent("Dagger, Hilt", "https://github.com/google/dagger"),
    OpenSourceComponent("Guava listenablefuture", "https://github.com/google/guava"),
    OpenSourceComponent("javax.inject", "http://code.google.com/p/atinject/"),
    OpenSourceComponent("JSR-305 annotations", "http://findbugs.sourceforge.net/"),
    OpenSourceComponent("JetBrains annotations", "https://github.com/JetBrains/java-annotations"),
    OpenSourceComponent("JSpecify", "https://github.com/jspecify/jspecify"),
)

internal const val ApacheLicenseAsset = "licenses/apache-2.0.txt"

/** 설정 탭의 "앱 정보": 버전과 오픈소스 라이선스 보기. */
@Composable
internal fun AboutCard() {
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    var showing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyLarge)
        OutlineButton(stringResource(R.string.oss_button), onClick = { showing = true }, modifier = Modifier.padding(top = 20.dp))
    }
    if (showing) OpenSourceDialog(onClose = { showing = false })
}

@Composable
private fun OpenSourceDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var licenseText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        licenseText = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(ApacheLicenseAsset).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .widthIn(max = 900.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.oss_title), Modifier.asHeading(), style = MaterialTheme.typography.titleLarge)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.oss_intro), style = MaterialTheme.typography.bodyLarge)
                OpenSourceComponents.forEach { component ->
                    Column {
                        Text(component.name, style = MaterialTheme.typography.bodyLarge)
                        Text(component.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.oss_pleos_sdk), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    licenseText ?: "",
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(20.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
            }
            PrimaryButton(stringResource(R.string.close), onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
