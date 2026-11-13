package kr.chatoon.app.ui.cartoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kr.chatoon.app.domain.drive.Trip
import kotlin.math.roundToInt

/**
 * 이 화가 된 운전 시간을 사람이 읽는 말로("42분", "1시간 5분"). 차툰은 사라지는 운전 시간을 남기는 앱이라 이 숫자를 늘 보여 준다.
 */
fun drivingTime(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "${m}분"
        m == 0L -> "${h}시간"
        else -> "${h}시간 ${m}분"
    }
}

/** "42분 운전 · 18.3km". 거리를 모르거나 0.1km가 안 되면(주차장 안에서만 움직임 등) 시간만. */
fun driveLine(trip: Trip): String =
    drivingTime(trip.durationMillis) + " 운전" +
        (trip.distanceKm?.takeIf { it >= 0.1 }?.let { " · " + String.format(java.util.Locale.KOREA, "%.1fkm", it) } ?: "")

/**
 * 그 주행에서 차가 본 것(차량 신호)을 짧은 말로. 없는 신호는 빼고, 만화에 나오는 순서와 비슷하게 늘어놓는다.
 * 근거 줄보다 덜 기술적인 말로, 한눈에 "차가 무엇을 읽었는지" 보이게 한다.
 */
fun seenFacts(trip: Trip): List<String> = buildList {
    val minutes = { ms: Double -> (ms / 60_000).roundToInt() }
    trip.rainShare?.let { share -> minutes(share * trip.durationMillis).takeIf { it >= 1 }?.let { add("와이퍼 ${it}분") } }
    trip.windowsOpenMillis?.let { ms -> minutes(ms.toDouble()).takeIf { it >= 1 }?.let { add("창문 내림 ${it}분") } }
    val from = trip.setTempStartC
    val to = trip.setTempC
    when {
        from != null && to != null && from != to -> add("공조 $from°→$to°")
        to != null && (to <= 18 || to >= 26) -> add("공조 $to°")
    }
    trip.seatHeatLevel?.takeIf { it > 0 }?.let { add("열선 ${it}단") }
    trip.seatVentLevel?.takeIf { it > 0 }?.let { add("통풍 ${it}단") }
    trip.hardDecelerations?.takeIf { it > 0 }?.let { add("급정거 ${it}번") }
    trip.hardAccelerations?.takeIf { it > 0 }?.let { add("급가속 ${it}번") }
    val park = when (trip.parkedInReverse) {
        true -> "후면 주차"
        false -> "전면 주차"
        null -> null
    }
    when {
        park != null && trip.parkingGearShifts >= 2 -> add("$park · 기어 ${trip.parkingGearShifts}번")
        park != null -> add(park)
        trip.parkingGearShifts >= 2 -> add("주차 기어 ${trip.parkingGearShifts}번")
    }
}

/** 차가 본 것 하나. 작은 둥근 칸. */
@Composable
fun FactChip(text: String, strong: Boolean = false) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (strong) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        color = if (strong) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * 한 줄에 들어가는 만큼만 차례로 놓고, 넘치는 것은 놓지 않는다(반쯤 잘린 칸이 생기지 않게). 줄을 바꾸지 않아 머리글 높이가 늘 같다.
 */
@Composable
fun OneLine(modifier: Modifier = Modifier, gap: Int = 10, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.dp.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(loose) }
        var x = 0
        val shown = placeables.takeWhile { p ->
            val fits = x + p.width <= constraints.maxWidth
            if (fits) x += p.width + gapPx
            fits
        }
        val height = shown.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var px = 0
            for (p in shown) {
                p.placeRelative(px, (height - p.height) / 2)
                px += p.width + gapPx
            }
        }
    }
}
