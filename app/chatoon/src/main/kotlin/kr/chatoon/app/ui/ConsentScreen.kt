package kr.chatoon.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.chatoon.app.R
import kr.chatoon.app.ui.cartoon.CartoonIntro


/**
 * 첫 실행 동의 화면. 동의 버튼을 누르기 전에는 아무것도 기록하지 않는다.
 * "나중에"를 누르면 앱을 닫을 뿐 아무것도 저장하지 않는다.
 * 안내가 바뀌어 다시 묻는 경우([termsUpdated])에는 예전 동의로 모은 기록이 남아 있으므로, 동의하지 않을 사람도
 * 여기서 바로 지울 수 있게 한다([onEraseOld]).
 */
@Composable
fun ConsentScreen(termsUpdated: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, onEraseOld: () -> Unit) {
    // 처음 여는 사람에게는 동의서보다 먼저 이 앱이 무엇인지 보여 준다. 안내가 바뀌어 다시 묻는 경우는 이미 아는 사람이라 바로 동의서.
    var introDone by rememberSaveable { mutableStateOf(termsUpdated) }
    if (!introDone) {
        IntroScreen(onStart = { introDone = true })
        return
    }
    var confirmingErase by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 1100.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.consent_title), Modifier.asHeading(), style = MaterialTheme.typography.titleLarge)
            if (termsUpdated) {
                Text(
                    stringResource(R.string.consent_terms_updated),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(stringResource(R.string.consent_intro), style = MaterialTheme.typography.bodyLarge)
            Item(stringResource(R.string.consent_collect_title), stringResource(R.string.consent_collect))
            Item(stringResource(R.string.consent_not_collect_title), stringResource(R.string.consent_not_collect))
            Item(stringResource(R.string.consent_storage_title), stringResource(R.string.consent_storage))
            Item(stringResource(R.string.consent_voice_title), stringResource(R.string.consent_voice))
            Item(stringResource(R.string.consent_light_title), stringResource(R.string.consent_light))
            Item(stringResource(R.string.consent_refuse_title), stringResource(R.string.consent_refuse))
            Item(stringResource(R.string.consent_withdraw_title), stringResource(R.string.consent_withdraw))
            // 의료 진단이 아니라는 고지는 동의 전에 꼭 읽혀야 한다. 가장 작은 회색 글로 두지 않는다(총괄 검수 S7).
            Text(
                stringResource(R.string.consent_not_medical),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlineButton(stringResource(R.string.consent_decline), onDecline, Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.consent_accept), onAccept, Modifier.weight(2f))
            }
            if (termsUpdated) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        stringResource(R.string.consent_erase_old_hint),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlineButton(stringResource(R.string.consent_erase_old), onClick = { confirmingErase = true }, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmingErase) {
        PleosPopup(
            title = stringResource(R.string.withdraw_dialog_title),
            confirmLabel = stringResource(R.string.withdraw_confirm),
            onConfirm = {
                confirmingErase = false
                onEraseOld()
            },
            onDismiss = { confirmingErase = false },
            destructive = true,
        ) {
            Text(stringResource(R.string.withdraw_dialog_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 처음 실행 소개: 앱 이름과 한 줄 소개, 쓰는 법 세 단계, 예시 네 컷. "시작하기"를 누르면 동의서로 간다(아직 아무것도 기록하지 않음). */
@Composable
private fun IntroScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 1400.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            BrandHeader(big = true)
            CartoonIntro()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryButton(stringResource(R.string.intro_start), onStart, Modifier.widthIn(min = 360.dp))
            }
        }
    }
}

@Composable
private fun Item(title: String, body: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            title,
            Modifier.widthIn(min = 200.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(body, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}
