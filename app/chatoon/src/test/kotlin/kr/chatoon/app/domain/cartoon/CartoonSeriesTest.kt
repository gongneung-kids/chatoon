package kr.chatoon.app.domain.cartoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CartoonSeriesTest {

    private fun quiet(day: String, hour: Int, time: TimeOfDay = TimeOfDay.DAY, rain: Rain = Rain.NONE) =
        Seen(day = day, time = time, hour = hour, rain = rain, parkShifts = 1, usualParkShifts = 1)

    private fun brake(day: String, hour: Int, time: TimeOfDay, rain: Rain = Rain.NONE) =
        Seen(day = day, time = time, hour = hour, rain = rain, parkShifts = 1, usualParkShifts = 1, jolt = Jolt.BRAKE)

    @Test
    fun `제목이 다르면 그대로 둔다`() {
        val titles = CartoonWriter.series(listOf(quiet("월요일", 8), brake("월요일", 22, TimeOfDay.NIGHT))).map { it.title }
        assertEquals(listOf("별일 없는 날", "급하게 선 날"), titles)
    }

    @Test
    fun `바로 앞 화와 제목이 같으면 날씨나 요일과 때를 부제로 붙인다`() {
        val titles = CartoonWriter.series(
            listOf(brake("수요일", 14, TimeOfDay.DAY), brake("목요일", 22, TimeOfDay.NIGHT, Rain.RAIN), quiet("금요일", 8), quiet("금요일", 18, TimeOfDay.NIGHT)),
        ).map { it.title }
        assertEquals(listOf("급하게 선 날", "급하게 선 날 · 비 오는 밤", "별일 없는 날", "별일 없는 날 · 금요일 밤"), titles)
    }

    @Test
    fun `세 번 이어져도 이웃한 두 화의 제목은 늘 다르다`() {
        val seens = listOf(quiet("월요일", 8), quiet("월요일", 9), quiet("월요일", 10), quiet("월요일", 11))
        val titles = CartoonWriter.series(seens).map { it.title }
        for (i in 1 until titles.size) assertNotEquals(titles[i - 1], titles[i])
    }

    @Test
    fun `내용은 한 화씩 쓴 것과 같다`() {
        val seens = listOf(brake("수요일", 14, TimeOfDay.DAY), brake("목요일", 22, TimeOfDay.NIGHT))
        val series = CartoonWriter.series(seens)
        for ((s, e) in seens.zip(series)) assertEquals(CartoonWriter.write(s).cuts, e.cuts)
    }
}
