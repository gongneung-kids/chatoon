package kr.chatoon.app.domain.analysis

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * 일출·일몰 시각과 "주행 중 어두웠던 시간"을 기기 안에서 계산한다(인터넷·위치 권한 불필요).
 *
 * 천문 일출 방정식(Sunrise equation)을 쓴다. 태양 중심이 지평선 아래 0.833°(대기 굴절·태양 반지름 보정)일 때를
 * 일출·일몰로 본다. 한국 안에서는 실제와 1~2분 이내로 맞는다(테스트 참고).
 *
 * 같은 날짜를 여러 번 묻는 일이 많아 날짜별 결과를 캐시한다. 한 인스턴스는 한 스레드에서만 쓴다.
 *
 * 분석 한 번에 수만 번 불리므로(기준 주행 × 기간의 날짜) 날짜는 LocalDate 대신 epoch day(Long)로 다룬다.
 * 시각 → 현지 날짜 변환은 시간대 규칙을 그대로 따르되, 전환과 전환 사이 구간의 오프셋을 기억해 java.time 변환을
 * 반복하지 않는다. 결과는 `Instant.atZone(zone).toLocalDate()`와 항상 같다(SunCalculatorTest).
 */
class SunCalculator(
    private val latitude: Double = SEOUL_LATITUDE,
    private val longitude: Double = SEOUL_LONGITUDE,
    private val zone: ZoneId = SEOUL_ZONE,
) {
    init {
        // 극지방(백야·극야)은 다루지 않는다. 한국에서 쓰는 앱이다.
        require(latitude in -60.0..60.0) { "latitude out of supported range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }

    /** 일출과 일몰(epoch millis). */
    data class Daylight(val sunriseMillis: Long, val sunsetMillis: Long)

    // 날짜별 결과 캐시. 처음 물은 날짜 앞뒤 CACHE_SPAN일은 배열로(분석이 대부분 이 안에서 끝난다), 그 밖은 맵으로 둔다.
    private var cacheBase = Long.MIN_VALUE
    private val cached = arrayOfNulls<Daylight>(CACHE_SPAN * 2)
    private val overflow = HashMap<Long, Daylight>()
    private val rules = zone.rules

    // 오프셋이 같은 구간 [offsetFrom, offsetUntil)과 그 오프셋. 처음에는 빈 구간이다.
    private var offsetFrom = 0L
    private var offsetUntil = 0L
    private var offsetMillis = 0L

    fun daylight(date: LocalDate): Daylight = daylight(date.toEpochDay())

    private fun daylight(epochDay: Long): Daylight {
        if (cacheBase == Long.MIN_VALUE) cacheBase = epochDay - CACHE_SPAN
        val index = epochDay - cacheBase
        if (index !in cached.indices) return overflow.getOrPut(epochDay) { compute(epochDay) }
        return cached[index.toInt()] ?: compute(epochDay).also { cached[index.toInt()] = it }
    }

    /** [startMillis, endMillis) 중 해가 진 뒤였던(일몰~다음 날 일출) 시간. */
    fun darkMillis(startMillis: Long, endMillis: Long): Long {
        if (endMillis <= startMillis) return 0
        val lastDay = epochDay(endMillis)
        var total = 0L
        var day = epochDay(startMillis) - 1
        var darkFrom = daylight(day).sunsetMillis
        while (day <= lastDay) {
            val next = daylight(day + 1)
            total += overlap(startMillis, endMillis, darkFrom, next.sunriseMillis)
            darkFrom = next.sunsetMillis
            day++
        }
        return total
    }

    fun localDate(millis: Long): LocalDate = LocalDate.ofEpochDay(epochDay(millis))

    /** [millis]가 속한 현지 날짜의 epoch day. */
    fun epochDay(millis: Long): Long = Math.floorDiv(millis + offsetAt(millis), DAY_MILLIS)

    private fun offsetAt(millis: Long): Long {
        if (millis < offsetFrom || millis >= offsetUntil) {
            val instant = Instant.ofEpochMilli(millis)
            offsetMillis = rules.getOffset(instant).totalSeconds * 1000L
            // previousTransition은 주어진 시각보다 앞선 전환을, nextTransition은 뒤의 전환을 준다(전환 시각 자체는 제외).
            // 그래서 [millis 이하의 마지막 전환, millis 뒤의 첫 전환) 동안 오프셋이 같다.
            offsetFrom = rules.previousTransition(instant.plusMillis(1))?.instant?.toEpochMilli() ?: Long.MIN_VALUE
            offsetUntil = rules.nextTransition(instant)?.instant?.toEpochMilli() ?: Long.MAX_VALUE
        }
        return offsetMillis
    }

    private fun compute(epochDay: Long): Daylight {
        // 해당 날짜 12:00 UTC의 J2000 기준 일수. 경도를 빼면 그 지역의 태양 남중에 가까워진다.
        val n = epochDay - EPOCH_DAY_OF_J2000
        val jStar = n - longitude / 360.0
        val m = normalizeDegrees(357.5291 + 0.98560028 * jStar)
        val mRad = Math.toRadians(m)
        val center = 1.9148 * sin(mRad) + 0.0200 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
        val lambdaRad = Math.toRadians(normalizeDegrees(m + center + 180.0 + 102.9372))
        val transit = J2000 + jStar + 0.0053 * sin(mRad) - 0.0069 * sin(2 * lambdaRad)
        val sinDeclination = sin(lambdaRad) * sin(Math.toRadians(OBLIQUITY))
        val cosDeclination = cos(asin(sinDeclination))
        val latRad = Math.toRadians(latitude)
        val cosHourAngle = (sin(Math.toRadians(SUN_ALTITUDE)) - sin(latRad) * sinDeclination) / (cos(latRad) * cosDeclination)
        val hourAngle = Math.toDegrees(acos(cosHourAngle.coerceIn(-1.0, 1.0)))
        return Daylight(
            sunriseMillis = julianToMillis(transit - hourAngle / 360.0),
            sunsetMillis = julianToMillis(transit + hourAngle / 360.0),
        )
    }

    companion object {
        const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000L
        const val SEOUL_LATITUDE = 37.5665
        const val SEOUL_LONGITUDE = 126.9780
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        /** 배열 캐시가 덮는 날 수의 절반. 보관 기간(13개월)에 계산용 이동을 더해도 넉넉하다. */
        private const val CACHE_SPAN = 512

        private const val J2000 = 2451545.0
        private const val UNIX_EPOCH_JULIAN = 2440587.5
        private const val EPOCH_DAY_OF_J2000 = 10957L // 2000-01-01
        private const val OBLIQUITY = 23.4397
        private const val SUN_ALTITUDE = -0.833

        private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

        private fun julianToMillis(julian: Double): Long = ((julian - UNIX_EPOCH_JULIAN) * DAY_MILLIS).roundToLong()

        private fun overlap(a: Long, b: Long, c: Long, d: Long): Long = (minOf(b, d) - maxOf(a, c)).coerceAtLeast(0)
    }
}
