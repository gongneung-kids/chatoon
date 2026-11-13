package kr.chatoon.app.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class HarshEventDetectorTest {

    private val step = 100_000_000L // 10Hz

    /** km/h 값 목록을 0.1초 간격으로 넣는다. */
    private fun HarshEventDetector.feed(kmh: List<Double>, startNanos: Long = 0L): Long {
        var t = startNanos
        for (v in kmh) {
            onSpeed((v / 3.6).toFloat(), t)
            t += step
        }
        return t
    }

    /** [from]에서 [to]까지 초당 [perSecond]km/h로 바꾼 뒤 1초 유지. */
    private fun ramp(from: Double, to: Double, perSecond: Double): List<Double> {
        val n = (kotlin.math.abs(to - from) / perSecond * 10).toInt()
        val dir = if (to > from) 1 else -1
        return List(10) { from } + List(n) { from + dir * perSecond * (it + 1) / 10 } + List(10) { to }
    }

    @Test fun `smooth driving counts nothing`() {
        val d = HarshEventDetector()
        d.feed(ramp(0.0, 60.0, 8.0) + ramp(60.0, 0.0, 6.0))
        assertEquals(0, d.accelerations)
        assertEquals(0, d.decelerations)
    }

    @Test fun `thresholds are inclusive and one event counts once`() {
        val d = HarshEventDetector()
        d.feed(ramp(0.0, 50.0, 12.0)) // 초당 12km/h로 4초 넘게 가속 → 1번
        assertEquals(1, d.accelerations)
        d.feed(ramp(50.0, 0.0, 8.0), 100 * step) // 초당 8km/h 감속 → 1번
        assertEquals(1, d.decelerations)
    }

    @Test fun `just under the threshold does not count`() {
        val d = HarshEventDetector()
        d.feed(ramp(0.0, 50.0, 10.5) + ramp(50.0, 0.0, 7.0))
        assertEquals(0, d.accelerations)
        assertEquals(0, d.decelerations)
    }

    @Test fun `two separate hard brakes count twice`() {
        val d = HarshEventDetector()
        d.feed(ramp(60.0, 40.0, 10.0) + List(20) { 40.0 } + ramp(40.0, 20.0, 10.0))
        assertEquals(2, d.decelerations)
    }

    @Test fun `sensor jumps beyond the plausible range are ignored`() {
        val d = HarshEventDetector()
        d.feed(List(10) { 30.0 } + List(20) { 90.0 } + List(20) { 0.0 }) // 0.1초 만에 60km/h 오르내림
        assertEquals(0, d.accelerations)
        assertEquals(0, d.decelerations)
    }

    @Test fun `a long gap in samples does not create an event`() {
        val d = HarshEventDetector()
        val t = d.feed(List(15) { 80.0 })
        d.feed(List(15) { 30.0 }, t + 5_000_000_000L) // 5초 끊긴 뒤 30km/h
        assertEquals(0, d.decelerations)
    }

    @Test fun `out of order and invalid samples are ignored`() {
        val d = HarshEventDetector()
        d.onSpeed(10f, 1_000_000_000L)
        assertEquals(false, d.onSpeed(0f, 500_000_000L))
        assertEquals(false, d.onSpeed(Float.NaN, 2_000_000_000L))
        assertEquals(false, d.onSpeed(-1f, 2_100_000_000L))
        assertEquals(0, d.decelerations)
    }
}
