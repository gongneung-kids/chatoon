package kr.chatoon.app.debug

import kr.chatoon.app.domain.cartoon.CartoonWriter
import kr.chatoon.app.domain.cartoon.TripSeen
import kr.chatoon.app.domain.drive.DriveLogCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시연 데이터가 저장 형식을 지키고, 지난 화가 한 가지 제목으로만 채워지지 않는지 본다. */
class DemoHistoryTest {

    private val now = 1_794_500_000_000L // 2026-11-12 무렵

    @Test fun `demo trips are valid, ordered and stable`() {
        val trips = DemoHistory.generate(now)
        assertTrue(trips.size in 100..200)
        assertEquals(trips, DemoHistory.generate(now)) // 시드 고정
        for ((a, b) in trips.zipWithNext()) assertTrue(a.endedAtMillis <= b.startedAtMillis)
        for (t in trips) {
            assertTrue(t.endedAtMillis <= now)
            assertEquals(t, DriveLogCodec.decode(DriveLogCodec.encode(t)))
        }
    }

    @Test fun `demo history shows varied moments, not one title`() {
        val trips = DemoHistory.generate(now)
        val seen = TripSeen()
        val titles = trips.takeLast(30).map { CartoonWriter.write(seen.of(it, trips)).title }
        assertTrue("titles: $titles", titles.toSet().size >= 6)
    }
}
