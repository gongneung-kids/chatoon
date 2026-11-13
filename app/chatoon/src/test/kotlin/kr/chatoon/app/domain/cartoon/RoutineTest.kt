package kr.chatoon.app.domain.cartoon

import kr.chatoon.app.domain.drive.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RoutineTest {

    private val zone = ZoneId.of("Asia/Seoul")

    private fun at(d: Int, h: Int, mo: Int = 11) = LocalDateTime.of(2026, mo, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    /** 주차는 두 번(연속 한 번에 고비가 끼지 않게). */
    private fun trip(start: Long, rain: Double = 0.0, km: Double? = null) =
        Trip(start, start + 30 * 60_000, 2, rain, distanceKm = km)

    private fun seen(routine: Routine) = Seen(day = "수요일", time = TimeOfDay.DAY, hour = 9, parkShifts = 1, usualParkShifts = 1, routine = routine)

    @Test
    fun `고비가 아니면 아무것도 채우지 않는다`() {
        val before = listOf(trip(at(10, 9)), trip(at(10, 18)))
        assertEquals(Routine.NONE, Routine.of(trip(at(11, 9)), before))
    }

    @Test
    fun `열 번째 주행이면 몇 번째인지 센다`() {
        val before = (1..9).map { trip(at(1, 0) + it * 3_600_000L) }
        assertEquals(10, Routine.of(trip(at(5, 12)), before).tripNumber)
    }

    @Test
    fun `함께 달린 거리가 500km를 넘는 주행에서만 고비를 적는다`() {
        val before = listOf(trip(at(1, 9), km = 480.0))
        assertEquals(500, Routine.of(trip(at(2, 9), km = 30.0), before).kmMilestone)
        assertNull(Routine.of(trip(at(2, 9), km = 10.0), before).kmMilestone)
        assertNull("거리를 모르면 쓰지 않는다", Routine.of(trip(at(2, 9), km = null), before).kmMilestone)
    }

    @Test
    fun `며칠 만의 비는 앞선 비 온 주행과의 날수로 센다`() {
        val before = listOf(trip(at(1, 9), rain = 0.6), trip(at(3, 9)), trip(at(5, 9)))
        assertEquals(5, Routine.of(trip(at(6, 9), rain = 0.5), before).dryDays)
        assertNull("비가 안 왔으면 쓰지 않는다", Routine.of(trip(at(6, 9)), before).dryDays)
        assertNull("이틀 만이면 고비가 아니다", Routine.of(trip(at(3, 9), rain = 0.5), before.take(1)).dryDays)
    }

    @Test
    fun `금요일 저녁 뒤 월요일 아침은 사흘 만에 나온 것이다`() {
        // 2026-11-06 금요일, 11-09 월요일
        val r = Routine.of(trip(at(9, 8)), listOf(trip(at(6, 18))))
        assertEquals(3, r.restDays)
        assertNull("같은 날 다시 나오면 쓰지 않는다", Routine.of(trip(at(9, 18)), listOf(trip(at(9, 8)))).restDays)
    }

    @Test
    fun `닷새째 같은 시간대에 나오면 센다`() {
        val before = (9..12).map { trip(at(it, 8)) }
        assertEquals(5, Routine.of(trip(at(13, 9)), before).sameSlotDays)
        assertNull("넷째 날은 고비가 아니다", Routine.of(trip(at(12, 8)), before.take(3)).sameSlotDays)
        assertNull("하루 빠지면 이어지지 않는다", Routine.of(trip(at(14, 8)), before).sameSlotDays)
    }

    @Test
    fun `평소와 다른 일이 없는 날에 고비가 있으면 그 고비로 쓰고, 근거를 붙인다`() {
        val e = CartoonWriter.write(seen(Routine(dryDays = 5)))
        assertEquals("닷새 만의 비", e.title)
        assertEquals("닷새 만에 와이퍼를 켰다.", e.cuts[1].caption)
        assertTrue(e.cuts[1].proof!!.contains("와이퍼"))
        assertNull("마지막 컷은 차의 생각", e.cuts[3].proof)
        assertEquals(4, e.cuts.size)
    }

    @Test
    fun `고비가 여럿이면 드문 것부터 고른다`() {
        val e = CartoonWriter.write(seen(Routine(tripNumber = 100, restDays = 3, sameSlotDays = 5)))
        assertEquals("100번째 주행", e.title)
        assertEquals("함께 1,000km", CartoonWriter.write(seen(Routine(kmMilestone = 1000, restDays = 3))).title)
    }

    @Test
    fun `평소와 다른 일이 있으면 고비보다 그 일이 먼저다`() {
        val e = CartoonWriter.write(seen(Routine(tripNumber = 100)).copy(parkShifts = 1, usualParkShifts = 4))
        assertEquals("한 번에", e.title)
    }

    @Test
    fun `고비가 없으면 예전과 같은 별일 없는 날이다`() {
        assertEquals("별일 없는 날", CartoonWriter.write(seen(Routine.NONE)).title)
    }

    @Test
    fun `고비만 있는 날은 묻지 않는다`() {
        assertNull(CartoonWriter.questionFor(seen(Routine(tripNumber = 10))))
    }

    @Test
    fun `차의 말풍선은 여덟 자 이내다`() {
        val all = listOf(Routine(tripNumber = 100), Routine(kmMilestone = 1000), Routine(dryDays = 12), Routine(restDays = 3), Routine(sameSlotDays = 30))
        for (r in all) for (c in CartoonWriter.write(seen(r)).cuts) {
            c.say?.let { assertTrue("너무 긴 말풍선: $it", it.length <= 8) }
        }
    }
}
