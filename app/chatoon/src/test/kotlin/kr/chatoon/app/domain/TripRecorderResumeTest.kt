package kr.chatoon.app.domain

import kr.chatoon.app.domain.drive.RecorderSnapshot
import kr.chatoon.app.domain.drive.Trip
import kr.chatoon.app.domain.drive.TripRecorder
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 프로세스가 죽었다 살아난 상황: 첫 기록기에서 snapshot을 뜨고, 새 기록기로 restore해 이어 간다. */
class TripRecorderResumeTest {

    private val trips = mutableListOf<Trip>()
    private var recorder = newRecorder()
    private var state = VehicleState(gear = Gear.PARK, frontWiperOn = false)

    private fun newRecorder() = TripRecorder(onTrip = { trips += it })

    private fun at(seconds: Long, change: (VehicleState) -> VehicleState = { it }) {
        state = change(state)
        recorder.onState(state, seconds * 1_000)
    }

    /** 저장 → 문자열로 직렬화 → 다시 읽기 → 새 기록기로 복원. 실제 앱과 같은 경로를 거친다. */
    private fun crashAndRestore() {
        val line = RecorderSnapshot.encode(recorder.snapshot()!!)
        recorder = TripRecorder.restore(RecorderSnapshot.decode(line)!!, { trips += it })
    }

    @Test fun `no snapshot before the first state`() {
        assertNull(newRecorder().snapshot())
    }

    @Test fun `trip continues across a short restart`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(100) { it.copy(frontWiperOn = true) }
        crashAndRestore()
        at(130) // 30초 뒤 다시 연결: 그 사이 와이퍼 상태는 모름
        at(330) { it.copy(gear = Gear.PARK) }

        val trip = trips.single()
        assertEquals(10_000L, trip.startedAtMillis)
        assertEquals(320_000L, trip.durationMillis)
        // 아는 구간: 10~100 꺼짐(90초), 130~330 켜짐(200초). 끊긴 100~130은 뺀다.
        assertEquals(200.0 / 290.0, trip.rainShare!!, 1e-9)
    }

    @Test fun `trip cut off by turning the car off ends at the last observed moment`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(400) { it.copy(frontWiperOn = true) } // 마지막으로 본 순간
        crashAndRestore()
        val nextMorning = 400 + TripRecorder.MAX_RESUME_GAP_MILLIS / 1000 + 36_000
        at(nextMorning) { it.copy(gear = Gear.PARK, frontWiperOn = false) }
        at(nextMorning + 10) { it.copy(gear = Gear.DRIVE) }
        at(nextMorning + 100) { it.copy(gear = Gear.PARK) }

        assertEquals(2, trips.size)
        assertEquals(10_000L, trips[0].startedAtMillis)
        assertEquals(400_000L, trips[0].endedAtMillis) // 끊긴 시간은 주행으로 치지 않는다
        assertEquals(0.0, trips[0].rainShare!!, 1e-9)
        assertEquals((nextMorning + 10) * 1000, trips[1].startedAtMillis) // 다음 날 주행은 새로 시작
    }

    @Test fun `trip with nothing observed after leaving is not recorded on a long gap`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        crashAndRestore()
        at(10 + TripRecorder.MAX_RESUME_GAP_MILLIS / 1000 + 1)
        at(2000) { it.copy(gear = Gear.PARK) }
        assertTrue(trips.isEmpty())
    }

    @Test fun `clock going backwards drops the resumed state instead of crashing`() {
        at(1000)
        at(1010) { it.copy(gear = Gear.DRIVE) }
        crashAndRestore()
        at(500) // 시계가 뒤로 감
        at(600) { it.copy(gear = Gear.PARK) }
        assertTrue(trips.isEmpty())
    }

    @Test fun `gear changes before restart still count for parking`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(500) { it.copy(gear = Gear.REVERSE) }
        at(510) { it.copy(gear = Gear.DRIVE) }
        crashAndRestore()
        at(520) { it.copy(gear = Gear.REVERSE) }
        at(530) { it.copy(gear = Gear.PARK) }
        assertEquals(3, trips.single().parkingGearShifts)
    }

}
