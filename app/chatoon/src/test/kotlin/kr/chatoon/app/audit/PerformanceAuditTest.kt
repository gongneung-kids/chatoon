package kr.chatoon.app.audit

import kr.chatoon.app.data.TestCiphers
import kr.chatoon.app.data.drive.DriveLogStore
import kr.chatoon.app.domain.analysis.SunCalculator.Companion.DAY_MILLIS
import kr.chatoon.app.domain.cartoon.AnswerBook
import kr.chatoon.app.domain.cartoon.CartoonWriter
import kr.chatoon.app.domain.cartoon.Question
import kr.chatoon.app.domain.cartoon.TripSeen
import kr.chatoon.app.domain.analysis.SunCalculator
import kr.chatoon.app.domain.drive.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * 점검 4: 성능. 보관 기간을 꽉 채운 최악의 기록(400일 × 하루 6회 = 2,400회, 모든 항목 값 있음)으로
 * 화면이 다시 계산할 때마다 도는 전체 경로와 저장소 시작·다시 쓰기를 잰다.
 *
 * JVM(개발 PC)에서 잰 값이라 차량 기기는 이보다 느리다. 기기에서 이전에 잰 분석 시간(0.09~0.27초, docs/HANDOFF)과
 * 함께 보기 위해 숫자를 출력하고, 기준은 넉넉하게(PC 값의 수 배가 기기에서도 화면을 막지 않을 만큼) 둔다.
 */
class PerformanceAuditTest {

    @get:Rule val tmp = TemporaryFolder()

    private val zone = SunCalculator.SEOUL_ZONE

    private fun fullYear(): Pair<List<Trip>, Long> {
        val random = Random(17)
        val day0 = LocalDate.of(2025, 9, 1)
        val trips = mutableListOf<Trip>()
        for (d in 0 until 400) {
            val base = day0.plusDays(d.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            var t = base + random.nextLong(6, 9) * 3_600_000
            repeat(6) {
                val minutes = random.nextLong(8, 70)
                val e = t + minutes * 60_000
                trips += Trip(
                    t, e, random.nextInt(0, 4), random.nextDouble(),
                    distanceKm = minutes * 0.6, hardAccelerations = random.nextInt(0, 3), hardDecelerations = random.nextInt(0, 3),
                    windowsOpenMillis = 0, setTempC = 22, fanAtMax = false, seatHeatLevel = random.nextInt(0, 3),
                    setTempStartC = 22, seatHeatStart = 0, seatVentLevel = 0, seatVentStart = 0,
                )
                t = e + random.nextLong(5, 180) * 60_000
            }
        }
        val now = trips.last().endedAtMillis + 3_600_000
        return trips to now
    }

    private fun medianMillis(times: Int = 7, block: () -> Unit): Double {
        block() // 예열
        val samples = List(times) {
            val t0 = System.nanoTime()
            block()
            (System.nanoTime() - t0) / 1_000_000.0
        }
        return samples.sorted()[times / 2]
    }

    @Test fun `drawing the latest thirty episodes from thirteen full months stays fast`() {
        val (trips, _) = fullYear()
        assertEquals(2_400, trips.size)
        val convert = TripSeen()
        val book = AnswerBook()
        // MainViewModel.analyze와 같은 경로: 시간순 정렬 → 최근 30화 그리기 → 마지막 주행의 질문
        val whole = medianMillis {
            val ordered = trips.sortedBy { it.startedAtMillis }
            ordered.takeLast(30).forEach { CartoonWriter.write(convert.of(it, ordered, book.answersFor(it.startedAtMillis))) }
            Question.of(ordered.last().startedAtMillis, convert.of(ordered.last(), ordered), book)
        }
        println("PERF-AUDIT 2400 trips (ms, median of 7): episodes=$whole")
        assertTrue("episodes $whole ms", whole < 200)
    }

    @Test fun `loading and rewriting thirteen full months of encrypted records stays within a second`() {
        val (trips, now) = fullYear()
        val file = File(tmp.root, "drive-log.tsv")
        val key = TestCiphers.newKey()
        fun store() = DriveLogStore(file, clock = { now }, cipher = TestCiphers.aes(key))
        store().apply { load(); setRecording(true); replaceAll(trips) }
        assertTrue(file.length() > 0)

        val load = medianMillis(5) { store().load() }
        val writer = store().apply { load(); setRecording(true) }
        // 주차 직후 늦게 온 주행거리로 고칠 때(amend)는 파일 전체를 다시 쓴다. 한 주행에 1~3번 일어날 수 있다.
        var k = 0
        val amend = medianMillis(5) { val t = trips.last(); writer.amend(t.copy(distanceKm = (t.distanceKm ?: 0.0) + 0.1 * ++k)) }
        val append = medianMillis(5) { val s = now + 10_000_000L * ++k; writer.append(Trip(s, s + 60_000, 0, null)) }
        println("PERF-AUDIT store ${trips.size} trips, file ${file.length() / 1024} KB (ms, median): load=$load amend(rewrite)=$amend append=$append")
        assertTrue("load $load ms", load < 1_000)
        assertTrue("amend $amend ms", amend < 1_000)
        assertTrue("append $append ms", append < 50)
        assertEquals("reloaded", trips.size + 6, store().apply { load() }.log.value.trips.size)
    }
}
