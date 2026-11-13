package kr.chatoon.app.domain

import kr.chatoon.app.domain.drive.Trip
import kr.chatoon.app.domain.drive.TripRecorder
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {

    private val trips = mutableListOf<Trip>()
    private val amended = mutableListOf<Trip>()
    private val recorder = TripRecorder(onTrip = { trips += it }, onTripAmended = { amended += it })
    private var state = VehicleState(gear = Gear.PARK, frontWiperOn = false)

    private fun at(seconds: Long, change: (VehicleState) -> VehicleState = { it }) {
        state = change(state)
        recorder.onState(state, seconds * 1_000)
    }

    @Test fun `records a trip from leaving park to parking again`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(610) { it.copy(gear = Gear.PARK) }

        val trip = trips.single()
        assertEquals(10_000L, trip.startedAtMillis)
        assertEquals(600_000L, trip.durationMillis)
        assertEquals(0, trip.parkingGearShifts)
    }

    @Test fun `records windows open time and cabin settings while driving`() {
        at(0) { it.copy(windowsOpen = false, cabinSetTempC = 22f, fanAtMax = false, seatHeatLevel = 0) }
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(70) { it.copy(windowsOpen = true) }
        at(160) { it.copy(windowsOpen = false, cabinSetTempC = 17.4f, fanAtMax = true) }
        at(200) { it.copy(seatHeatLevel = 2) }
        at(220) { it.copy(seatHeatLevel = 1) }
        at(300) { it.copy(gear = Gear.PARK, cabinSetTempC = 26f, seatHeatLevel = 3) }

        val trip = trips.single()
        assertEquals(90_000L, trip.windowsOpenMillis)
        assertEquals(17, trip.setTempC) // 주차하는 순간 바꾼 값은 세지 않는다
        assertEquals(true, trip.fanAtMax)
        assertEquals(2, trip.seatHeatLevel) // 달리는 동안 가장 높은 단계
    }

    @Test fun `remembers cabin values at the start to tell what changed while driving`() {
        at(0) { it.copy(cabinSetTempC = 22f, seatHeatLevel = 0) }
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(100) { it.copy(cabinSetTempC = 26f, seatHeatLevel = 2) }
        at(150) { it.copy(seatHeatLevel = -3) } // 통풍으로 바꿈
        at(200) { it.copy(gear = Gear.PARK) }

        val trip = trips.single()
        assertEquals(22, trip.setTempStartC)
        assertEquals(26, trip.setTempC)
        assertEquals(0, trip.seatHeatStart)
        assertEquals(2, trip.seatHeatLevel)
        assertEquals(0, trip.seatVentStart)
        assertEquals(3, trip.seatVentLevel)
    }

    @Test fun `cabin values stay unknown when never received`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(100) { it.copy(seatHeatLevel = -2) } // 통풍은 열선 0으로 센다
        at(200) { it.copy(gear = Gear.PARK) }

        val trip = trips.single()
        assertNull(trip.windowsOpenMillis)
        assertNull(trip.setTempC)
        assertNull(trip.fanAtMax)
        assertEquals(0, trip.seatHeatLevel)
    }

    @Test fun `counts gear back and forth only near the end of the trip`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(20) { it.copy(gear = Gear.REVERSE) } // 출발 직후 후진: 주차 구간 밖
        at(30) { it.copy(gear = Gear.DRIVE) }
        at(600) { it.copy(gear = Gear.REVERSE) }
        at(610) { it.copy(gear = Gear.DRIVE) }
        at(620) { it.copy(gear = Gear.REVERSE) }
        at(640) { it.copy(gear = Gear.PARK) }

        assertEquals(3, trips.single().parkingGearShifts)
    }

    @Test fun `rain share is time weighted`() {
        at(0)
        at(0) { it.copy(gear = Gear.DRIVE) }
        at(300) { it.copy(frontWiperOn = true) }
        at(400) { it.copy(gear = Gear.PARK) }

        val trip = trips.single()
        assertEquals(0.25, trip.rainShare!!, 1e-9)
    }

    @Test fun `unknown wiper state gives null share`() {
        state = state.copy(frontWiperOn = null)
        at(0)
        at(0) { it.copy(gear = Gear.DRIVE) }
        at(100) { it.copy(gear = Gear.PARK) }

        assertNull(trips.single().rainShare)
    }

    @Test fun `trip already running when app starts is not recorded`() {
        state = state.copy(gear = Gear.DRIVE)
        at(0)
        at(100) { it.copy(gear = Gear.PARK) }

        assertTrue(trips.isEmpty())
    }

    @Test fun `gear briefly unknown does not lose the next trip`() {
        state = state.copy(gear = Gear.DRIVE)
        at(0)
        at(10) { it.copy(gear = Gear.PARK) }
        at(20) { it.copy(gear = null) } // 차량 서비스 재연결 등으로 잠시 모름
        at(30) { it.copy(gear = Gear.DRIVE) }
        at(90) { it.copy(gear = Gear.PARK) }

        assertEquals(30_000L, trips.single().startedAtMillis)
        assertEquals(90_000L, trips.single().endedAtMillis)
    }

    @Test fun `first known gear arriving later as park is treated as unknown start`() {
        state = state.copy(gear = null)
        at(0)
        at(5) { it.copy(gear = Gear.PARK) }
        at(500) { it.copy(gear = Gear.DRIVE) }
        at(900) { it.copy(gear = Gear.PARK) }

        // 처음 안 기어가 늦게 와도 그 뒤의 출발·주차는 그대로 한 주행이다.
        assertEquals(500_000L, trips.single().startedAtMillis)
    }

    @Test fun `old gear changes in a long trip are not counted or kept`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        repeat(500) { i -> at(20L + i * 2) { it.copy(gear = if (i % 2 == 0) Gear.REVERSE else Gear.DRIVE) } }
        at(5_000) { it.copy(gear = Gear.DRIVE) }
        at(5_010) { it.copy(gear = Gear.PARK) }

        assertEquals(0, trips.single().parkingGearShifts)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of order states are rejected`() {
        at(10)
        at(5)
    }

    // ---------------------------------------------------------------- 급가속·급감속(명세 §15)

    @Test fun `harsh events during the trip are counted, not those while parked`() {
        state = state.copy(hardAccelerations = 0, hardDecelerations = 0)
        at(0) { it.copy(hardAccelerations = 1) } // 주차 중(앱 켜기 전 값과 상관없이) 늘어난 것은 세지 않는다
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(100) { it.copy(hardAccelerations = 2) }
        at(200) { it.copy(hardDecelerations = 3) } // 한 번에 여러 번 늘 수도 있다
        at(300) { it.copy(gear = Gear.PARK, hardDecelerations = 4) } // 세우는 순간 함께 바뀐 것도 이 주행
        at(400) { it.copy(hardAccelerations = 9) } // 주차 뒤는 다음 주행 몫이 아니다
        val trip = trips.single()
        assertEquals(1, trip.hardAccelerations)
        assertEquals(4, trip.hardDecelerations)
    }

    @Test fun `no speed signal means unknown, not zero`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(300) { it.copy(gear = Gear.PARK) }
        assertNull(trips.single().hardAccelerations)
        assertNull(trips.single().hardDecelerations)
    }

    @Test fun `signal lost or arriving mid trip makes the trip unknown`() {
        state = state.copy(hardAccelerations = 0, hardDecelerations = 0)
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(100) { it.copy(hardAccelerations = null, hardDecelerations = null) } // 신호 끊김
        at(200) { it.copy(hardAccelerations = 0, hardDecelerations = 0) }
        at(300) { it.copy(gear = Gear.PARK) }
        assertNull(trips.single().hardAccelerations)

        // 다음 주행은 처음부터 끝까지 알았으므로 0이라고 말할 수 있다.
        at(400) { it.copy(gear = Gear.DRIVE) }
        at(700) { it.copy(gear = Gear.PARK) }
        assertEquals(0, trips.last().hardAccelerations)
        assertEquals(0, trips.last().hardDecelerations)
    }

    @Test fun `a restart mid trip makes the trip unknown`() {
        state = state.copy(hardAccelerations = 5, hardDecelerations = 5)
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(20) { it.copy(hardAccelerations = 6) }
        val restored = TripRecorder.restore(recorder.snapshot()!!, { trips += it })
        // 다시 켜진 앱의 누적 횟수는 0부터 다시 센다.
        restored.onState(state.copy(hardAccelerations = 0, hardDecelerations = 0), 60_000)
        restored.onState(state.copy(hardAccelerations = 1, hardDecelerations = 0), 120_000)
        restored.onState(state.copy(gear = Gear.PARK, hardAccelerations = 1, hardDecelerations = 0), 180_000)
        assertNull(trips.single().hardAccelerations)
    }

    // ---------------------------------------------------------------- 회전·방향지시등(명세 §16)

    @Test fun `주차 직전 마지막 기어로 후면·전면 주차를 기록한다`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(100) { it.copy(gear = Gear.REVERSE) }
        at(110) { it.copy(gear = Gear.PARK) }
        assertEquals(true, trips.last().parkedInReverse)
        at(200) { it.copy(gear = Gear.DRIVE) }
        at(300) { it.copy(gear = Gear.PARK) }
        assertEquals(false, trips.last().parkedInReverse)
        at(400) { it.copy(gear = Gear.DRIVE) }
        at(500) { it.copy(gear = Gear.NEUTRAL) }
        at(510) { it.copy(gear = Gear.PARK) }
        assertNull(trips.last().parkedInReverse)
    }
}
