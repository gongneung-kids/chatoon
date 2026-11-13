package kr.chatoon.app.domain

import kr.chatoon.app.domain.drive.RecorderSnapshot
import kr.chatoon.app.domain.drive.TripRecorder
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecorderSnapshotTest {

    private val full = RecorderSnapshot(
        lastState = VehicleState(
            Gear.DRIVE, frontWiperOn = true, odometerKm = 12_345.6, hardAccelerations = 3, hardDecelerations = 1,
            windowsOpen = true, cabinSetTempC = 17.5f, fanAtMax = false, seatHeatLevel = -1,
        ),
        lastAtMillis = 5_000,
        lastKnownGear = Gear.DRIVE,
        lastKnownOdometerKm = 12_345.6,
        tripStartedAtMillis = 1_000,
        tripStartOdometerKm = 12_300.0,
        knownWiperMillis = 4_000,
        wiperOnMillis = 4_000,
        gearChangesAtMillis = listOf(2_000, 3_000),
        tripHardAccelerations = 1,
        tripHardDecelerations = 0,
        tripHarshKnown = true,
        cabin = TripRecorder.Cabin(
            knownWindowMillis = 3_000, windowsOpenMillis = 2_000, setTempStartC = 22f, setTempC = 17.5f, fanAtMax = true,
            seatHeatStart = 0, seatHeat = 2, seatVentStart = 0, seatVent = 1,
        ),
    )

    @Test fun `round trips every field`() {
        assertEquals(full, RecorderSnapshot.decode(RecorderSnapshot.encode(full)))
        val empty = full.copy(
            lastState = VehicleState(),
            lastKnownGear = null,
            lastKnownOdometerKm = null,
            tripStartedAtMillis = null,
            tripStartOdometerKm = null,
            gearChangesAtMillis = emptyList(),
            cabin = TripRecorder.Cabin(),
        )
        assertEquals(empty, RecorderSnapshot.decode(RecorderSnapshot.encode(empty) + "\n"))
    }

    @Test fun `broken lines are rejected`() {
        val good = RecorderSnapshot.encode(full)
        val broken = listOf(
            "",
            "garbage",
            good.replaceFirst("R1", "R9"), // 모르는 형식 버전
            good.replace("12345.6", "NaN"), // 숫자가 아닌 거리
            good.substringBeforeLast('\t'), // 필드 누락
            good.replace("DRIVE", "FLY"), // 없는 기어
            good.replace("\t5000\t", "\tabc\t"), // 숫자가 아님
        )
        for (line in broken) assertNull(line, RecorderSnapshot.decode(line))
    }

    @Test fun `inconsistent values are rejected`() {
        val bad = listOf(
            full.copy(wiperOnMillis = 9_999), // 켜진 시간이 아는 시간보다 김
            full.copy(tripStartedAtMillis = 9_999), // 마지막 상태보다 늦게 시작
            full.copy(tripHardDecelerations = -1), // 음수 횟수
            full.copy(cabin = full.cabin.copy(windowsOpenMillis = 9_999)), // 창문을 연 시간이 아는 시간보다 김
        )
        for (s in bad) assertNull(s.toString(), RecorderSnapshot.decode(RecorderSnapshot.encode(s)))
    }
}
