package kr.chatoon.app.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class SunCalculatorTest {

    private val sun = SunCalculator()
    private val zone: ZoneId = SunCalculator.SEOUL_ZONE

    private fun localTime(millis: Long): LocalTime = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    private fun assertNear(expected: String, actualMillis: Long) {
        val diffMinutes = abs(LocalTime.parse(expected).toSecondOfDay() - localTime(actualMillis).toSecondOfDay()) / 60
        assertTrue("expected ~$expected but was ${localTime(actualMillis)}", diffMinutes <= 3)
    }

    /** 기준: 한국천문연구원 서울 일출·일몰 (2026년). 3분 이내로 맞아야 한다. */
    @Test fun `matches published Seoul sunrise and sunset`() {
        sun.daylight(LocalDate.of(2026, 6, 21)).let { assertNear("05:11", it.sunriseMillis); assertNear("19:57", it.sunsetMillis) }
        sun.daylight(LocalDate.of(2026, 12, 21)).let { assertNear("07:43", it.sunriseMillis); assertNear("17:17", it.sunsetMillis) }
        sun.daylight(LocalDate.of(2026, 3, 20)).let { assertNear("06:35", it.sunriseMillis); assertNear("18:44", it.sunsetMillis) }
        sun.daylight(LocalDate.of(2026, 9, 23)).let { assertNear("06:20", it.sunriseMillis); assertNear("18:28", it.sunsetMillis) }
    }

    @Test fun `dark time of trips`() {
        val day = LocalDate.of(2026, 12, 21)
        fun at(date: LocalDate, h: Int, m: Int = 0) = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val minute = 60_000L

        assertEquals("daytime", 0L, sun.darkMillis(at(day, 12), at(day, 13)))
        assertEquals("night", 60 * minute, sun.darkMillis(at(day, 21), at(day, 22)))
        assertEquals("across midnight", 120 * minute, sun.darkMillis(at(day, 23), at(day.plusDays(1), 1)))

        val sunset = sun.daylight(day).sunsetMillis
        assertEquals("across sunset", at(day, 18) - sunset, sun.darkMillis(at(day, 17), at(day, 18)))
        val sunrise = sun.daylight(day).sunriseMillis
        assertEquals("across sunrise", sunrise - at(day, 7), sun.darkMillis(at(day, 7), at(day, 8)))

        assertEquals("empty", 0L, sun.darkMillis(at(day, 21), at(day, 21)))
        assertEquals("all night", sun.daylight(day.plusDays(1)).sunriseMillis - sunset, sun.darkMillis(at(day, 12), at(day.plusDays(1), 12)))
    }

    /** 빠른 날짜 변환(오프셋 구간 기억)이 java.time 변환과 항상 같은지. 시간대 전환 경계 전후와 무작위 시각을 섞는다. */
    @Test fun `epoch day matches java time for any instant`() {
        val zones = listOf(zone, ZoneId.of("Europe/Berlin"), ZoneId.of("America/New_York"), ZoneId.of("UTC"))
        val random = java.util.Random(20261101)
        for (z in zones) {
            val calc = SunCalculator(zone = z)
            val samples = mutableListOf<Long>()
            // 1900~2100년 무작위 시각. 순서를 섞어 기억해 둔 구간 밖으로 자주 나가게 한다.
            repeat(20_000) { samples += -2_208_988_800_000L + (random.nextDouble() * 6_311_433_600_000L).toLong() }
            var t = java.time.Instant.parse("1900-01-01T00:00:00Z")
            while (true) {
                val tr = z.rules.nextTransition(t) ?: break
                if (tr.instant.isAfter(java.time.Instant.parse("2100-01-01T00:00:00Z"))) break
                val at = tr.instant.toEpochMilli()
                samples += listOf(at - 1, at, at + 1, at - DAY, at + DAY)
                t = tr.instant
            }
            samples.shuffle(random)
            for (millis in samples) {
                val expected = java.time.Instant.ofEpochMilli(millis).atZone(z).toLocalDate()
                assertEquals("$z $millis", expected.toEpochDay(), calc.epochDay(millis))
                assertEquals("$z $millis", expected, calc.localDate(millis))
            }
        }
    }

    /** 어두운 시간 계산이 LocalDate로 하루씩 넘기던 예전 방식과 똑같은지. */
    @Test fun `dark millis matches the date by date reference`() {
        val random = java.util.Random(7)
        val reference = SunCalculator()
        fun referenceDark(start: Long, end: Long): Long {
            if (end <= start) return 0
            var date = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate().minusDays(1)
            val lastDate = java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
            var total = 0L
            while (!date.isAfter(lastDate)) {
                val from = reference.daylight(date).sunsetMillis
                val to = reference.daylight(date.plusDays(1)).sunriseMillis
                total += (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)
                date = date.plusDays(1)
            }
            return total
        }
        repeat(20_000) {
            val start = 1_700_000_000_000L + (random.nextDouble() * 200 * DAY).toLong()
            val end = start + (random.nextDouble() * 2 * DAY).toLong() - DAY / 10
            assertEquals(referenceDark(start, end), sun.darkMillis(start, end))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `polar latitudes are rejected`() {
        SunCalculator(latitude = 70.0)
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
