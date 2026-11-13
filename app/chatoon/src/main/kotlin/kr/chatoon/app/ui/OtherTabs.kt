package kr.chatoon.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import kr.chatoon.app.ui.cartoon.CarPaint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.chatoon.app.R
import kr.chatoon.app.ui.theme.DisplayFont
import kr.chatoon.app.domain.analysis.SunCalculator
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun Header(text: String) {
    Text(
        text,
        Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 12.dp).asHeading(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------- 설정

@Composable
fun SettingsTab(
    vehicle: VehicleState,
    acceptedAtMillis: Long,
    totalTrips: Int,
    onWithdraw: () -> Unit,
    speedAccess: SpeedAccess,
    onEnableSpeed: () -> Unit,
    moodLightOn: Boolean,
    setMoodLight: (Boolean) -> Unit,
    carPaint: String?,
    setCarPaint: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        Header(stringResource(R.string.settings_privacy_title))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_consent_since, formatDate(acceptedAtMillis)), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.settings_records, totalTrips), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 나이·생활 변화 설정은 개발 초기 화면에만 있던 것이라 차툰 설정에서는 뺐다.
        Header(stringResource(R.string.settings_speed_title))
        if (speedAccess == SpeedAccess.GRANTED) {
            Card(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_speed_on), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            SpeedAccessCard(speedAccess, onEnableSpeed, R.string.cartoon_speed_off)
        }
        Header(stringResource(R.string.settings_paint_title))
        CarPaintPicker(CarPaint.of(carPaint), setCarPaint)
        Header(stringResource(R.string.settings_light_title))
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    stringResource(if (moodLightOn) R.string.settings_light_on_body else R.string.settings_light_off_body),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlineButton(stringResource(if (moodLightOn) R.string.cartoon_light_off else R.string.cartoon_light_on), { setMoodLight(!moodLightOn) })
            }
        }
        Header(stringResource(R.string.settings_vehicle_title))
        Card(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_vehicle_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(stringResource(R.string.label_gear), gearLabel(vehicle.gear))
                StatusLine(stringResource(R.string.label_wiper), onOff(vehicle.frontWiperOn))
                StatusLine(stringResource(R.string.label_odometer), vehicle.odometerKm?.let { stringResource(R.string.distance_km, String.format(Locale.KOREA, "%.1f", it)) } ?: stringResource(R.string.unknown))
                StatusLine(
                    stringResource(R.string.label_windows),
                    when (vehicle.windowsOpen) {
                        true -> stringResource(R.string.windows_open)
                        false -> stringResource(R.string.windows_closed)
                        null -> stringResource(R.string.unknown)
                    },
                )
                StatusLine(
                    stringResource(R.string.label_climate),
                    vehicle.cabinSetTempC?.let {
                        val degrees = String.format(Locale.KOREA, "%.1f", it).removeSuffix(".0")
                        stringResource(if (vehicle.fanAtMax == true) R.string.climate_value_fan_max else R.string.climate_value, degrees)
                    } ?: stringResource(R.string.unknown),
                )
                StatusLine(
                    stringResource(R.string.label_seat_heat),
                    vehicle.seatHeatLevel?.let { if (it > 0) stringResource(R.string.seat_heat_level, it) else stringResource(R.string.off) } ?: stringResource(R.string.unknown),
                )
            }
        }
        Header(stringResource(R.string.settings_delete_title))
        Card(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_delete_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WithdrawButton(onWithdraw)
        }
        Header(stringResource(R.string.settings_about_title))
        AboutCard()
    }
}

private val AcceptedFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA)

internal fun formatDate(millis: Long): String = AcceptedFormat.format(Instant.ofEpochMilli(millis).atZone(SunCalculator.SEOUL_ZONE))

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 상태 값은 먹색 굵은 글씨(차툰 강조색은 빨강 계열이라 상태 값에 쓰면 경고처럼 읽힌다)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 동의 철회. 되돌릴 수 없으므로 한 번 더 묻는다. 화면을 돌려도 확인 창이 유지되게 rememberSaveable을 쓴다. */
@Composable
private fun WithdrawButton(onWithdraw: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    OutlineButton(
        stringResource(R.string.withdraw_button),
        onClick = { confirming = true },
        modifier = Modifier.padding(top = 20.dp),
        color = MaterialTheme.colorScheme.error,
    )
    if (confirming) {
        PleosPopup(
            title = stringResource(R.string.withdraw_dialog_title),
            confirmLabel = stringResource(R.string.withdraw_confirm),
            onConfirm = {
                confirming = false
                onWithdraw()
            },
            onDismiss = { confirming = false },
            destructive = true,
        ) {
            Text(stringResource(R.string.withdraw_dialog_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun gearLabel(gear: Gear?): String = when (gear) {
    null -> stringResource(R.string.unknown)
    Gear.PARK -> stringResource(R.string.parked)
    Gear.REVERSE -> "R"
    Gear.NEUTRAL -> "N"
    Gear.DRIVE -> stringResource(R.string.driving)
}

@Composable
private fun onOff(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.on)
    false -> stringResource(R.string.off)
    null -> stringResource(R.string.unknown)
}

/** 내 차 색: 견본(64dp)을 눌러 고른다. 고른 색은 굵은 테두리와 이름으로 알린다. 그림의 모든 컷이 이 색으로 칠해진다. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CarPaintPicker(selected: CarPaint, onPick: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), padding = 24.dp) {
        Text(
            stringResource(R.string.settings_paint_body, stringResource(paintName(selected))),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (paint in CarPaint.entries) {
                val picked = paint == selected
                val name = stringResource(paintName(paint))
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(64.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .border(if (picked) 5.dp else 2.dp, if (picked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                        .padding(if (picked) 8.dp else 4.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(paint.color)
                        .selectable(selected = picked, role = Role.RadioButton) { onPick(paint.name) }
                        .semantics { contentDescription = name },
                )
            }
        }
    }
}

private fun paintName(paint: CarPaint): Int = when (paint) {
    CarPaint.RED -> R.string.paint_red
    CarPaint.ORANGE -> R.string.paint_orange
    CarPaint.YELLOW -> R.string.paint_yellow
    CarPaint.GREEN -> R.string.paint_green
    CarPaint.SKY -> R.string.paint_sky
    CarPaint.BLUE -> R.string.paint_blue
    CarPaint.PURPLE -> R.string.paint_purple
    CarPaint.PINK -> R.string.paint_pink
    CarPaint.WHITE -> R.string.paint_white
    CarPaint.SILVER -> R.string.paint_silver
    CarPaint.CHARCOAL -> R.string.paint_charcoal
}
