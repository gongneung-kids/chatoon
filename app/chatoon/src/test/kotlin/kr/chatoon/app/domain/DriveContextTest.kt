package kr.chatoon.app.domain

import kr.chatoon.app.domain.vehicle.DriveContext
import kr.chatoon.app.domain.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Test

/** 기어(모름 포함 5가지) × 운전 제한(켜짐·꺼짐·모름 3가지) 전체 조합. */
class DriveContextTest {

    @Test fun `every combination of gear and restriction`() {
        val gears: List<Gear?> = Gear.entries + listOf(null)
        val restrictions = listOf(true, false, null)
        for (gear in gears) for (restricted in restrictions) {
            val expected = when {
                restricted == true -> DriveContext.DRIVING // 플랫폼이 제한하면 무조건 주행
                gear == null -> DriveContext.UNKNOWN
                gear == Gear.PARK -> DriveContext.PARKED
                else -> DriveContext.DRIVING // 신호 대기(D, 제한 풀림)도 정차로 보지 않는다
            }
            assertEquals("$gear $restricted", expected, DriveContext.of(gear, restricted))
        }
    }
}
