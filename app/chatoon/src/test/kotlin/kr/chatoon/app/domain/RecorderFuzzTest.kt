package kr.chatoon.app.domain

import kr.chatoon.app.domain.drive.DriveLogCodec
import kr.chatoon.app.domain.drive.RecorderSnapshot
import kr.chatoon.app.domain.drive.Trip
import kr.chatoon.app.domain.drive.TripRecorder
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 무작위 차량 신호 수천 개로 기록기·저장 형식을 흔든다. 시드를 고정해 실패하면 그대로 재현된다.
 *
 * 확인하는 것
 * 1. 어떤 신호 순서(모름 포함, 주행거리 역행 포함)에도 예외 없이 동작한다.
 * 2. 나온 기록은 항상 정상 범위다(시간 순서, 비율 0~1, 거리 범위).
 * 3. 프로세스가 아무 때나 죽었다 10분 안에 살아나도 주행의 시작·끝·주차 기어 반복·거리는 똑같다.
 * 4. 모든 기록은 저장 형식으로 쓰고 읽어도 같다.
 */
class RecorderFuzzTest {

    private data class Event(val state: VehicleState, val atMillis: Long)

    private fun randomEvents(random: Random, count: Int): List<Event> {
        var t = 1_790_000_000_000L
        var state = VehicleState()
        var odometer = random.nextDouble(0.0, 200_000.0)
        return List(count) {
            t += when (random.nextInt(10)) {
                0 -> 0L // 같은 시각에 여러 변화
                in 1..6 -> random.nextLong(1_000, 120_000)
                in 7..8 -> random.nextLong(120_000, 9 * 60_000)
                else -> random.nextLong(9 * 60_000, 3_600_000)
            }
            state = when (random.nextInt(8)) {
                0, 1 -> state.copy(gear = if (random.nextInt(8) == 0) null else Gear.entries.random(random))
                2 -> state.copy(frontWiperOn = listOf(true, false, null).random(random))
                3 -> state.copy(windowsOpen = listOf(true, false, null).random(random))
                4 -> state.copy(
                    cabinSetTempC = if (random.nextInt(5) == 0) null else random.nextInt(32, 61) / 2f,
                    seatHeatLevel = if (random.nextInt(5) == 0) null else random.nextInt(-3, 4),
                    fanAtMax = listOf(true, false, null).random(random),
                )
                5 -> {
                    odometer += if (random.nextInt(20) == 0) -random.nextDouble(0.0, 50.0) else random.nextDouble(0.0, 5.0)
                    state.copy(odometerKm = if (random.nextInt(10) == 0) null else odometer)
                }
                6 -> {
                    val a = state.hardAccelerations ?: 0
                    state.copy(hardAccelerations = if (random.nextInt(10) == 0) null else a + random.nextInt(0, 2), hardDecelerations = if (random.nextInt(10) == 0) null else (state.hardDecelerations ?: 0) + random.nextInt(0, 2))
                }
                else -> state.copy(uxRestricted = listOf(true, false, null)[random.nextInt(3)])
            }
            Event(state, t)
        }
    }

    private fun run(events: List<Event>, crashBefore: Set<Int> = emptySet()): List<Trip> {
        val trips = mutableListOf<Trip>()
        var recorder = TripRecorder({ trips += it })
        events.forEachIndexed { i, e ->
            if (i in crashBefore) {
                val snapshot = recorder.snapshot()
                recorder = if (snapshot == null) {
                    TripRecorder({ trips += it })
                } else {
                    TripRecorder.restore(RecorderSnapshot.decode(RecorderSnapshot.encode(snapshot))!!, { trips += it })
                }
            }
            recorder.onState(e.state, e.atMillis)
        }
        return trips
    }

    @Test fun `random signals never break the recorder and produce valid records`() {
        repeat(200) { seed ->
            val random = Random(seed)
            val events = randomEvents(random, 600)
            val trips = run(events, crashBefore = (1 until events.size).filter { random.nextInt(25) == 0 }.toSet())

            for ((a, b) in trips.zipWithNext()) assertTrue("seed $seed: trips in order", a.endedAtMillis <= b.startedAtMillis)
            for (trip in trips) {
                trip.rainShare?.let { assertTrue("seed $seed", it in 0.0..1.0) }
                trip.distanceKm?.let { assertTrue("seed $seed", it in 0.0..Trip.MAX_PLAUSIBLE_TRIP_KM) }
                assertEquals("seed $seed: codec round trip", trip, DriveLogCodec.decode(DriveLogCodec.encode(trip)))
            }
        }
    }

    @Test fun `short crashes never change trips`() {
        repeat(200) { seed ->
            val random = Random(seed)
            // 끊김이 10분을 넘지 않게: 이 범위에서는 복원이 항상 이어 붙여야 한다.
            val events = randomEvents(random, 500).let { evs ->
                var t = evs.first().atMillis
                evs.map { e -> t += (e.atMillis - t).coerceIn(0, 9 * 60_000); e.copy(atMillis = t) }
            }
            val crashes = (1 until events.size).filter { random.nextInt(15) == 0 }.toSet()
            fun Trip.core() = listOf(startedAtMillis, endedAtMillis, parkingGearShifts, distanceKm, parkedInReverse, setTempStartC, seatHeatStart)
            assertEquals("seed $seed: trips", run(events).map { it.core() }, run(events, crashes).map { it.core() })
        }
    }

    @Test fun `random text never crashes the decoders`() {
        val random = Random(1)
        val alphabet = "CR1\t-.,0123456789abcxyzNaN\n DRIVEPARK"
        val valid = listOf(
            DriveLogCodec.encode(Trip(1, 2, 0, null, 1.5, setTempC = 22, seatVentLevel = 1)),
            RecorderSnapshot.encode(RecorderSnapshot(VehicleState(Gear.DRIVE), 5, Gear.DRIVE, 1.0, 1, 1.0, 0, 0, emptyList())),
        )
        repeat(20_000) {
            val text = if (random.nextBoolean()) {
                String(CharArray(random.nextInt(0, 80)) { alphabet[random.nextInt(alphabet.length)] })
            } else {
                // 정상 줄을 한 글자 바꾸거나 자르기
                val base = valid.random(random).toCharArray()
                if (base.isNotEmpty()) base[random.nextInt(base.size)] = alphabet[random.nextInt(alphabet.length)]
                String(base).take(random.nextInt(0, base.size + 1))
            }
            DriveLogCodec.decode(text) // 예외 없이 결과 또는 null
            RecorderSnapshot.decode(text)
        }
    }
}
