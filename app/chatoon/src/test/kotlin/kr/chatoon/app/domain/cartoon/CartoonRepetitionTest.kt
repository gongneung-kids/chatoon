package kr.chatoon.app.domain.cartoon

import kr.chatoon.app.domain.drive.Trip
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * 소재 반복률 측정(기획서 ※6).
 *
 * 가장 단조로운 운전자를 흉내 낸다: 평일 아침 출근·저녁 퇴근만 하고 주말에는 타지 않는다.
 * 같은 길, 같은 시간대(앞뒤 20분 흔들림), 주차는 대개 한두 번, 가끔 비. 묻는 말에는 답하지 않는다(가장 나쁜 경우).
 * 이 기록으로 만든 화가 얼마나 같은지 센다. 결과는 표준 출력으로 남기고, 아래 기준은 측정값이 크게 나빠지는 것만 막는다.
 *
 * "같은 화"는 제목과, 첫 컷(요일·시각·날씨)을 뺀 세 컷의 글이 글자까지 같은 화다(그림의 하늘·비는 따로 보지 않는다).
 */
class CartoonRepetitionTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val convert = TripSeen()

    private data class Driver(val name: String, val joltEvery: Int?, val badParkEvery: Int?)

    private data class Result(
        val episodes: Int,
        val distinct: Int,
        val sameAsPrevious: Int,
        val quiet: Int,
        val titles: Map<String, Int>,
        val longestSameTitleRun: Int,
    )

    /** 2026-11-02(월)부터 [weeks]주 동안의 평일 출퇴근 기록. 같은 씨앗이면 늘 같은 기록이다. */
    private fun commute(weeks: Int, seed: Int, driver: Driver): List<Trip> {
        val r = Random(seed)
        val trips = mutableListOf<Trip>()
        var n = 0
        var day = LocalDate.of(2026, 11, 2)
        repeat(weeks * 7) {
            if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) {
                val rainy = r.nextDouble() < RAIN_DAY_SHARE
                for ((h, m) in listOf(8 to 0, 18 to 30)) {
                    n++
                    val start = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli() + r.nextLong(-20, 21) * 60_000 + r.nextLong(0, 60) * 1000
                    val shifts = when {
                        driver.badParkEvery != null && n % driver.badParkEvery == 0 -> 5
                        else -> if (r.nextDouble() < 0.7) 1 else 2
                    }
                    val jolt = driver.joltEvery != null && n % driver.joltEvery == 0
                    trips += Trip(
                        start, start + 35 * 60_000, shifts, if (rainy) 0.6 else 0.0,
                        distanceKm = 14.0, hardAccelerations = 0, hardDecelerations = if (jolt) 1 else 0,
                        parkedInReverse = h == 18,
                    )
                }
            }
            day = day.plusDays(1)
        }
        return trips
    }

    /** [routine]이 false면 지난 기록에서 센 고비(Routine)를 쓰지 않은 예전 방식으로 쓴다. */
    private fun measure(trips: List<Trip>, routine: Boolean): Result {
        val eps = trips.map { t ->
            val before = trips.filter { it.endedAtMillis <= t.startedAtMillis && it !== t }
            val seen = convert.of(t, trips)
            CartoonWriter.write(seen.copy(routine = if (routine) Routine.of(t, before) else Routine.NONE))
        }
        // 첫 컷(요일·시각·날씨)은 날마다 달라지므로 빼고, 제목과 나머지 세 컷의 글이 같으면 같은 화로 본다.
        val keys = eps.map { e -> e.title + "|" + e.cuts.drop(1).joinToString("|") { it.caption } }
        var run = 1
        var longest = 1
        for (i in 1 until eps.size) {
            run = if (eps[i].title == eps[i - 1].title) run + 1 else 1
            longest = maxOf(longest, run)
        }
        return Result(
            episodes = eps.size,
            distinct = keys.toSet().size,
            sameAsPrevious = (1 until keys.size).count { keys[it] == keys[it - 1] },
            quiet = eps.count { it.title == "별일 없는 날" },
            titles = eps.groupingBy { it.title }.eachCount().toList().sortedByDescending { it.second }.toMap(),
            longestSameTitleRun = longest,
        )
    }

    private fun report(driver: Driver, r: Result) {
        fun pct(a: Int, b: Int) = "%.1f%%".format(100.0 * a / b)
        println("[반복률] ${driver.name} — 4주 ${r.episodes}화, 제목 ${r.titles.size}종")
        println("  서로 다른 화 ${r.distinct}개(${pct(r.distinct, r.episodes)}), 바로 앞 화와 글까지 같은 화 ${r.sameAsPrevious}번(${pct(r.sameAsPrevious, r.episodes - 1)})")
        println("  별일 없는 날 ${r.quiet}화(${pct(r.quiet, r.episodes)}), 같은 제목이 가장 길게 이어진 횟수 ${r.longestSameTitleRun}")
        println("  제목별: " + r.titles.entries.joinToString(", ") { "${it.key} ${it.value}" })
    }

    @Test
    fun `출퇴근만 하는 운전자의 4주 반복률을 재고, 지난 기록의 고비로 별일 없는 날을 줄인다`() {
        val drivers = listOf(
            Driver("출퇴근만, 급정거·주차 실수 없음(가장 단조로움)", joltEvery = null, badParkEvery = null),
            Driver("출퇴근만, 열 번에 한 번 급정거, 일곱 번에 한 번 주차 다섯 번", joltEvery = 10, badParkEvery = 7),
        )
        for (d in drivers) for (routine in listOf(false, true)) {
            println(if (routine) "== 지난 기록의 고비를 쓸 때(지금)" else "== 고비를 쓰지 않을 때(예전)")
            // 씨앗 다섯 개의 평균을 본다.
            val results = (1..5).map { measure(commute(WEEKS, it, d), routine) }
            results.first().let { report(d, it) }
            val same = results.sumOf { it.sameAsPrevious }.toDouble() / results.sumOf { it.episodes - 1 }
            val distinct = results.sumOf { it.distinct }.toDouble() / results.sumOf { it.episodes }
            val quiet = results.sumOf { it.quiet }.toDouble() / results.sumOf { it.episodes }
            println("  씨앗 5개 평균: 별일 없는 날 ${"%.1f%%".format(quiet * 100)}, 바로 앞 화와 같은 비율 ${"%.1f%%".format(same * 100)}, 서로 다른 화 비율 ${"%.1f%%".format(distinct * 100)}")
            if (routine) assertTrue("고비를 써도 별일 없는 날이 너무 많다: $quiet", quiet < 0.8)
        }
    }

    private companion object {
        const val WEEKS = 4
        /** 서울 연간 강수일수(일 강수량 0.1mm 이상)가 대략 110일 안팎이라 3할로 둔다. 대략값이며 측정의 가정이다. */
        const val RAIN_DAY_SHARE = 0.3
    }
}
