package kr.chatoon.app.domain

import kr.chatoon.app.domain.drive.RecordingClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingClockTest {

    private val hour = 3_600_000L
    private var wall = 1_793_500_000_000L
    private var elapsed = 10_000L
    private fun clock() = RecordingClock({ wall }, { elapsed })
    private fun advance(ms: Long) {
        wall += ms
        elapsed += ms
    }

    @Test fun `follows the wall clock while nothing jumps`() {
        val c = clock()
        val start = c.now(canResync = true)
        advance(30 * 60_000)
        assertEquals(start + 30 * 60_000, c.now(canResync = false))
    }

    @Test fun `clock going back keeps time moving so trips do not collapse onto one instant`() {
        val c = clock()
        val t1 = c.now(canResync = true)
        wall -= 9 * hour // 시간 동기화로 9시간 뒤로
        advance(20 * 60_000)
        val t2 = c.now(canResync = true)
        advance(20 * 60_000)
        val t3 = c.now(canResync = true)
        assertEquals("keeps real elapsed time", t1 + 20 * 60_000, t2)
        assertEquals(t2 + 20 * 60_000, t3)
    }

    @Test fun `forward jump during a trip does not stretch the trip`() {
        wall = 1_577_836_800_000L // 2020년으로 잘못 잡힌 채 출발
        val c = clock()
        val start = c.now(canResync = true)
        wall = 1_793_500_000_000L // 주행 중 시간 동기화
        advance(25 * 60_000)
        val end = c.now(canResync = false)
        assertEquals(25 * 60_000, end - start)
    }

    @Test fun `forward jump is followed once parked`() {
        wall = 1_577_836_800_000L
        val c = clock()
        c.now(canResync = true)
        wall = 1_793_500_000_000L
        advance(60_000)
        assertEquals(wall, c.now(canResync = true))
    }

    @Test fun `never goes backwards even when resync would`() {
        val c = clock()
        var previous = c.now(canResync = true)
        val jumps = listOf(-9 * hour, 2 * hour, -30 * 60_000L, 5 * hour, -48 * hour)
        for (jump in jumps) {
            wall += jump
            repeat(5) { i ->
                advance(7 * 60_000)
                val t = c.now(canResync = i % 2 == 0)
                assertTrue("$t < $previous", t >= previous)
                previous = t
            }
        }
    }

    @Test fun `small drift within tolerance is ignored`() {
        val c = clock()
        val start = c.now(canResync = true)
        wall += 30_000 // NTP 미세 조정
        advance(60_000)
        assertEquals(start + 60_000, c.now(canResync = true))
    }
}
