package kr.chatoon.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.chatoon.app.R

/** 차량 속도 권한을 켜는 안내. 다시 묻지 않음 상태면 차량 설정 안내만. */
@Composable
internal fun SpeedAccessCard(speedAccess: SpeedAccess, onEnableSpeed: () -> Unit, @StringRes offHint: Int) {
    var explaining by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), padding = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                stringResource(if (speedAccess == SpeedAccess.BLOCKED) R.string.speed_blocked else offHint),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (speedAccess == SpeedAccess.OFF) OutlineButton(stringResource(R.string.speed_enable), { explaining = true })
        }
    }
    if (explaining) {
        PleosPopup(
            title = stringResource(R.string.speed_dialog_title),
            confirmLabel = stringResource(R.string.speed_dialog_continue),
            onConfirm = {
                explaining = false
                onEnableSpeed()
            },
            onDismiss = { explaining = false },
        ) {
            Text(stringResource(R.string.speed_dialog_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
