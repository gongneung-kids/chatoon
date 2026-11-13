package kr.chatoon.app.domain.cartoon

import kr.chatoon.app.domain.drive.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TripSeenTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val convert = TripSeen()

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun trip(start: Long, shifts: Int = 3, rain: Double? = 0.0, hard: Int? = 0, fast: Int? = 0) =
        Trip(start, start + 30 * 60_000, shifts, rain, hardAccelerations = fast, hardDecelerations = hard)

    @Test
    fun `요일과 시각, 낮과 밤을 고른다`() {
        // 2026-11-10은 화요일. 서울 11월 일몰은 17시 20분 무렵.
        val noon = convert.of(trip(at(2026, 11, 10, 14)), emptyList())
        assertEquals("화요일", noon.day)
        assertEquals(14, noon.hour)
        assertEquals(TimeOfDay.DAY, noon.time)
        assertEquals(TimeOfDay.DUSK, convert.timeOf(at(2026, 11, 10, 17, 10)))
        assertEquals(TimeOfDay.NIGHT, convert.timeOf(at(2026, 11, 10, 23)))
        assertEquals(TimeOfDay.NIGHT, convert.timeOf(at(2026, 11, 10, 5)))
    }

    @Test
    fun `와이퍼가 켜진 비율로 비를 보고, 모르면 비가 없다고 쓴다`() {
        assertEquals(Rain.RAIN, convert.of(trip(at(2026, 11, 10, 14), rain = 0.6), emptyList()).rain)
        assertEquals(Rain.NONE, convert.of(trip(at(2026, 11, 10, 14), rain = 0.05), emptyList()).rain)
        assertEquals(Rain.NONE, convert.of(trip(at(2026, 11, 10, 14), rain = null), emptyList()).rain)
    }

    @Test
    fun `평소 주차 횟수는 지난 7일 평균이고, 지난 기록이 없으면 이번과 같다`() {
        val now = trip(at(2026, 11, 10, 14), shifts = 1)
        assertEquals(1, convert.of(now, listOf(now)).usualParkShifts)
        val week = listOf(trip(at(2026, 11, 8, 9), shifts = 5), trip(at(2026, 11, 9, 9), shifts = 6), trip(at(2026, 10, 20, 9), shifts = 20))
        assertEquals(6, convert.of(now, week + now).usualParkShifts) // 5.5 → 6, 3주 전 기록은 빼고
    }

    @Test
    fun `급감속이 있으면 급하게 선 화, 없고 급가속만 있으면 세게 밟은 화`() {
        val brake = convert.of(trip(at(2026, 11, 10, 14), hard = 2, fast = 1), emptyList(), TripSeen.Answers(joltWhy = "앞차가 갑자기"))
        assertEquals(Jolt.BRAKE, brake.jolt)
        assertEquals(2, brake.joltCount)
        assertEquals("앞차가 갑자기", brake.joltWhy)
        assertEquals(Jolt.ACCEL, convert.of(trip(at(2026, 11, 10, 14), fast = 1), emptyList()).jolt)
        val none = convert.of(trip(at(2026, 11, 10, 14), hard = null, fast = null), emptyList(), TripSeen.Answers(joltWhy = "답"))
        assertEquals(Jolt.NONE, none.jolt)
        assertEquals("", none.joltWhy)
    }

    @Test
    fun `밤 외출이 평소와 다른지는 지난 8주 같은 시간대로 본다`() {
        val late = trip(at(2026, 11, 10, 23))
        val mornings = (1..6).map { trip(at(2026, 11, it, 8)) }
        assertTrue(convert.of(late, mornings).lateUnusual)
        assertFalse(convert.of(late, mornings + trip(at(2026, 11, 7, 22))).lateUnusual)
        // 기록이 적으면 판단하지 않는다.
        assertFalse(convert.of(late, mornings.take(2)).lateUnusual)
    }

    @Test
    fun `만든 재료로 언제나 네 컷이 나온다`() {
        val history = (1..9).map { trip(at(2026, 11, it, 8 + it), shifts = it % 7, rain = it / 10.0, hard = it % 3, fast = it % 2) }
        for (t in history) assertEquals(4, CartoonWriter.write(convert.of(t, history)).cuts.size)
    }

    @Test
    fun `창문을 1분 넘게 내리고 달렸으면 창문 사건, 공조 값은 그대로 넘긴다`() {
        val base = trip(at(2026, 11, 10, 14))
        assertTrue(convert.of(base.copy(windowsOpenMillis = 90_000), emptyList()).windowsAllDown)
        assertFalse(convert.of(base.copy(windowsOpenMillis = 20_000), emptyList()).windowsAllDown)
        assertFalse(convert.of(base, emptyList()).windowsAllDown)
        val cold = convert.of(base.copy(setTempC = 17, fanAtMax = true), emptyList())
        assertEquals(17, cold.setTemp)
        assertTrue(cold.fanAtMax)
        assertFalse(convert.of(base.copy(setTempC = 17, fanAtMax = null), emptyList()).fanAtMax)
        assertEquals("에어컨 최대", CartoonWriter.write(cold).title)
    }

    @Test
    fun `열선은 지난 7일 동안 켠 적이 없을 때만 사건이 된다`() {
        val today = trip(at(2026, 11, 10, 8)).copy(seatHeatLevel = 2)
        assertEquals(2, convert.of(today, emptyList()).seatHeat)
        val threeDaysAgo = trip(at(2026, 11, 7, 8)).copy(seatHeatLevel = 1)
        assertEquals(0, convert.of(today, listOf(threeDaysAgo, today)).seatHeat)
        val tenDaysAgo = trip(at(2026, 10, 31, 8)).copy(seatHeatLevel = 3)
        assertEquals(2, convert.of(today, listOf(tenDaysAgo, today)).seatHeat)
        assertEquals(0, convert.of(today.copy(seatHeatLevel = 0), emptyList()).seatHeat)

        val episode = CartoonWriter.write(convert.of(today, emptyList()))
        assertEquals("열선을 켠 날", episode.title)
        assertTrue(episode.cuts.any { it.panel is Panel.Seat && it.proof == "운전석 시트 열선 단계" })
    }

    @Test
    fun `달리다 공조 온도를 2도 넘게 바꾸면 올린 날·내린 날이 되고, 설정 온도 사건과 겹치지 않는다`() {
        val base = trip(at(2026, 11, 10, 8))
        val up = convert.of(base.copy(setTempStartC = 22, setTempC = 26), emptyList())
        assertEquals(22, up.tempFrom)
        val upEpisode = CartoonWriter.write(up)
        assertEquals("달리다 히터 온도를 올린 날", upEpisode.title)
        assertTrue(upEpisode.cuts.any { (it.panel as? Panel.Temp)?.from == 22 })
        assertFalse(upEpisode.cuts.any { it.caption.startsWith("히터를 26도까지") }) // HOT과 겹쳐 쓰지 않음
        assertEquals("달리다 에어컨 온도를 내린 날", CartoonWriter.write(convert.of(base.copy(setTempStartC = 24, setTempC = 18), emptyList())).title)
        assertEquals(null, convert.of(base.copy(setTempStartC = 22, setTempC = 23), emptyList()).tempFrom) // 1도는 만지작거림
    }

    @Test
    fun `달리다 켠 엉뜨와 통풍시트는 지난주에 켰어도 사건이 된다`() {
        val today = trip(at(2026, 11, 10, 8))
        val yesterday = trip(at(2026, 11, 9, 8)).copy(seatHeatLevel = 2, seatVentLevel = 1)
        val heat = convert.of(today.copy(seatHeatStart = 0, seatHeatLevel = 3), listOf(yesterday, today))
        assertEquals(3, heat.seatHeat)
        assertTrue(heat.seatHeatMidDrive)
        assertEquals("달리다 엉뜨를 켠 날", CartoonWriter.write(heat).title)
        // 출발 때부터 켜 두었고 어제도 켰으면 평소 일
        assertEquals(0, convert.of(today.copy(seatHeatStart = 3, seatHeatLevel = 3), listOf(yesterday, today)).seatHeat)

        val vent = convert.of(today.copy(seatVentStart = 0, seatVentLevel = 2), listOf(yesterday, today))
        assertEquals(2, vent.seatVent)
        val ventEpisode = CartoonWriter.write(vent)
        assertEquals("달리다 통풍시트를 켠 날", ventEpisode.title)
        assertTrue(ventEpisode.cuts.any { (it.panel as? Panel.Seat)?.vent == true && it.proof == "운전석 시트 통풍 단계 · 출발 때 꺼짐" })
        // 지난 7일 안 켜다가 켠 통풍(출발 때부터)
        assertEquals("통풍시트를 켠 날", CartoonWriter.write(convert.of(today.copy(seatVentStart = 1, seatVentLevel = 1), emptyList())).title)
    }
}
