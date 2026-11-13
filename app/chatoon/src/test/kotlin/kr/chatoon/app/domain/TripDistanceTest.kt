package kr.chatoon.app.domain

import kr.chatoon.app.domain.drive.RecorderSnapshot
import kr.chatoon.app.domain.drive.Trip
import kr.chatoon.app.domain.drive.TripRecorder
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDistanceTest {

    private val trips = mutableListOf<Trip>()
    private val amended = mutableListOf<Trip>()
    private var recorder = TripRecorder(onTrip = { trips += it }, onTripAmended = { amended += it })
    private var state = VehicleState(gear = Gear.PARK, odometerKm = 1_000.0)

    private fun at(seconds: Long, change: (VehicleState) -> VehicleState = { it }) {
        state = change(state)
        recorder.onState(state, seconds * 1_000)
    }

    @Test fun `distance is the odometer difference between leaving and parking`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(300) { it.copy(odometerKm = 1_004.0) }
        at(600) { it.copy(odometerKm = 1_012.5) }
        at(610) { it.copy(gear = Gear.PARK) }
        assertEquals(12.5, trips.single().distanceKm!!, 1e-9)
    }

    @Test fun `odometer arriving together with the gear change is used`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(600) { it.copy(gear = Gear.PARK, odometerKm = 1_007.0) }
        assertEquals(7.0, trips.single().distanceKm!!, 1e-9)
    }

    @Test fun `unknown odometer at start gives no distance`() {
        state = VehicleState(gear = Gear.PARK)
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(300) { it.copy(odometerKm = 2_000.0) }
        at(600) { it.copy(gear = Gear.PARK, odometerKm = 2_005.0) }
        assertNull(trips.single().distanceKm)
    }

    @Test fun `odometer going backwards or jumping is ignored`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(600) { it.copy(gear = Gear.PARK, odometerKm = 990.0) }
        at(700) { it.copy(gear = Gear.DRIVE) }
        at(800) { it.copy(gear = Gear.PARK, odometerKm = 990.0 + Trip.MAX_PLAUSIBLE_TRIP_KM + 1) }
        assertNull(trips[0].distanceKm)
        assertNull(trips[1].distanceKm)
    }

    @Test fun `briefly unknown odometer keeps the last known value`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(300) { it.copy(odometerKm = null) }
        at(600) { it.copy(odometerKm = 1_003.0) }
        at(610) { it.copy(gear = Gear.PARK, odometerKm = null) }
        assertEquals(3.0, trips.single().distanceKm!!, 1e-9)
    }

    @Test fun `odometer arriving late while parked is added to the trip that just ended`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(600) { it.copy(gear = Gear.PARK) } // 마지막 주행거리 갱신보다 P가 먼저 도착
        at(602) { it.copy(odometerKm = 1_005.2) } // 2초 늦게 도착(에뮬레이터에서 실제로 관찰)
        at(900) { it.copy(gear = Gear.DRIVE) }
        at(1500) { it.copy(gear = Gear.PARK, odometerKm = 1_008.0) }

        assertEquals(0.0, trips[0].distanceKm!!, 1e-9) // 처음 낼 때는 아직 모름
        assertEquals(listOf(5.2), amended.map { Math.round(it.distanceKm!! * 10) / 10.0 }) // 주차 중 고침
        assertEquals(trips[0].startedAtMillis, amended.single().startedAtMillis)
        assertEquals("next trip starts from the corrected value", 2.8, trips[1].distanceKm!!, 1e-9)
    }

    @Test fun `odometer change after leaving again is not added to the previous trip`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(600) { it.copy(gear = Gear.PARK, odometerKm = 1_005.0) }
        at(900) { it.copy(gear = Gear.DRIVE) }
        at(905) { it.copy(odometerKm = 1_006.0) }
        assertTrue(amended.isEmpty())
    }

    @Test fun `implausible or decreasing late odometer is ignored`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        at(600) { it.copy(gear = Gear.PARK, odometerKm = 1_005.0) }
        at(601) { it.copy(odometerKm = 1_004.0) } // 줄어듦
        at(602) { it.copy(odometerKm = 1_000.0 + Trip.MAX_PLAUSIBLE_TRIP_KM + 1) } // 튐
        assertTrue(amended.isEmpty())
    }

    @Test fun `distance survives a restart`() {
        at(0)
        at(10) { it.copy(gear = Gear.DRIVE) }
        val line = RecorderSnapshot.encode(recorder.snapshot()!!)
        recorder = TripRecorder.restore(RecorderSnapshot.decode(line)!!, { trips += it })
        at(40) { it.copy(odometerKm = 1_002.0) }
        at(100) { it.copy(gear = Gear.PARK) }
        assertEquals(2.0, trips.single().distanceKm!!, 1e-9)
    }
}
