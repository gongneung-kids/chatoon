package kr.chatoon.app.domain.cartoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightSceneTest {

    private val quiet = Seen(day = "목요일", time = TimeOfDay.DAY, hour = 14, parkShifts = 5, usualParkShifts = 5)

    private fun scene(seen: Seen) = LightScene.of(CartoonWriter.write(seen))

    @Test
    fun `시간과 날씨로 색을 고른다`() {
        assertEquals(LightScene.DAY, scene(quiet))
        assertEquals(LightScene.DUSK, scene(quiet.copy(time = TimeOfDay.DUSK, hour = 17)))
        assertEquals(LightScene.RAIN, scene(quiet.copy(rain = Rain.RAIN)))
        // 밤은 비보다 먼저다(밤에 밝은 청록은 눈부시다).
        assertEquals(LightScene.NIGHT, scene(quiet.copy(time = TimeOfDay.NIGHT, hour = 23, rain = Rain.HEAVY)))
    }

    @Test
    fun `놀란 순간이 있는 화만 처음에 잠깐 번쩍인다`() {
        assertNull(scene(quiet).flash)
        assertEquals(LightScene.JOLT_FLASH, scene(quiet.copy(jolt = Jolt.BRAKE)).flash)
        assertEquals(LightScene.JOLT_FLASH, scene(quiet.copy(jolt = Jolt.ACCEL, joltWhy = "앞차가 느려서")).flash)
    }

    @Test
    fun `밤은 어둡게, 어떤 장면도 밝기 범위 안이다`() {
        assertTrue(LightScene.NIGHT.brightness < LightScene.DAY.brightness)
        for (s in listOf(LightScene.NIGHT, LightScene.DUSK, LightScene.RAIN, LightScene.DAY)) assertTrue(s.brightness in 0..100)
        assertTrue(LightScene.FLASH_BRIGHTNESS in 0..100)
    }
}
