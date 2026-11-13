package kr.chatoon.app.domain

import kr.chatoon.app.domain.drive.DriveLogCodec
import kr.chatoon.app.domain.drive.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveLogCodecTest {

    private val full = Trip(
        startedAtMillis = 1_000, endedAtMillis = 121_000, parkingGearShifts = 3, rainShare = 0.25, distanceKm = 12.4,
        hardAccelerations = 1, hardDecelerations = 2, parkedInReverse = true, windowsOpenMillis = 90_000,
        setTempC = 26, fanAtMax = true, seatHeatLevel = 2, setTempStartC = 22, seatHeatStart = 0, seatVentLevel = 1, seatVentStart = 0,
    )

    @Test fun `every field round trips`() {
        val line = DriveLogCodec.encode(full)
        assertEquals("C1", line.substringBefore('\t'))
        assertEquals(full, DriveLogCodec.decode(line))
    }

    @Test fun `unknown values round trip as unknown`() {
        val bare = Trip(1_000, 2_000, 0, null)
        assertEquals(bare, DriveLogCodec.decode(DriveLogCodec.encode(bare)))
        for (dir in listOf(true, false, null)) {
            val t = bare.copy(parkedInReverse = dir, fanAtMax = dir)
            assertEquals(t, DriveLogCodec.decode(DriveLogCodec.encode(t)))
        }
    }

    @Test fun `broken lines are skipped`() {
        val good = DriveLogCodec.encode(full)
        val broken = listOf(
            "",
            "garbage",
            good.replaceFirst("C1", "C9"), // 모르는 형식
            good.substringBeforeLast('\t'), // 칸 누락
            good.replace("\t0.25\t", "\t1.5\t"), // 비율 범위 밖
            good.replace("\t90000\t", "\t999999\t"), // 창문을 연 시간이 주행보다 김
            good.replace("\tR\t", "\tX\t"), // 모르는 주차 방향
            good.replace("\t26\t1\t", "\t26\tX\t"), // 깨진 풍량 칸
            good.replace("\t121000\t", "\t500\t"), // 끝이 시작보다 이름
        )
        for (line in broken) assertNull(line, DriveLogCodec.decode(line))
    }
}
