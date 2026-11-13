package kr.chatoon.app.audit

import kr.chatoon.app.domain.drive.DriveLogCodec
import kr.chatoon.app.domain.drive.RecorderSnapshot
import kr.chatoon.app.domain.drive.RecordingClock
import kr.chatoon.app.domain.drive.Trip
import kr.chatoon.app.domain.drive.TripRecorder
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 점검 3: 기록기 견고성. 기존 RecorderFuzzTest가 다루지 않는 것을 흔든다.
 * - 급가속·급감속·회전 누적 횟수(모름·0부터 다시 세기 포함)
 * - 10시간 넘는 주행, 주행 중 기어 R·N·D 반복
 * - 프로세스가 아무 때나 죽었다 살아날 때 횟수가 "같거나 모름"이고 틀린 숫자가 되지 않는지
 * - 벽시계가 몇 년씩 앞뒤로 뛰어도 기록 시각이 거꾸로 가지 않는지(RecordingClock)
 * 시드를 고정해 실패하면 그대로 재현된다.
 */
class RecorderRobustnessAuditTest {

    private data class Event(val state: VehicleState, val atMillis: Long)

    /**
     * 실제 게이트웨이처럼 회전 수와 깜빡이 켠 회전 수는 함께 늘고(켠 수 ≤ 회전 수), 누적 횟수는 앱이 켜진 뒤 0부터 센다.
     * [restartAt]에 해당하는 사건에서는 프로세스가 다시 시작된 것처럼 누적 횟수가 0으로 돌아간다.
     */
    private fun events(random: Random, count: Int, restartAt: Set<Int> = emptySet()): List<Event> {
        var t = 1_790_000_000_000L
        var state = VehicleState(gear = Gear.PARK, odometerKm = 10_000.0)
        var odometer = 10_000.0
        var accel = 0L
        var decel = 0L
        var turns = 0L
        var signaled = 0L
        var speedKnown = true
        return List(count) { i ->
            if (i in restartAt) {
                // 새 프로세스: 누적 횟수는 0부터 다시 세고, 첫 상태에서는 아직 모른다(게이트웨이가 처음 올리기 전).
                accel = 0; decel = 0; turns = 0; signaled = 0
                state = state.copy(hardAccelerations = null, hardDecelerations = null)
            }
            t += when (random.nextInt(20)) {
                0 -> 0L
                in 1..12 -> random.nextLong(500, 90_000)
                in 13..17 -> random.nextLong(90_000, 20 * 60_000)
                18 -> random.nextLong(2 * 3_600_000, 6 * 3_600_000) // 긴 주행·긴 주차
                else -> random.nextLong(10 * 3_600_000, 14 * 3_600_000) // 10시간 넘는 주행
            }
            val driving = state.gear != null && state.gear != Gear.PARK
            state = when (random.nextInt(9)) {
                0 -> state.copy(gear = Gear.entries.random(random))
                1 -> state.copy(gear = if (driving) listOf(Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE).random(random) else state.gear) // 기어 반복
                2 -> state.copy(frontWiperOn = listOf(true, false, null).random(random))
                3 -> state.copy(windowsOpen = listOf(true, false, null).random(random), seatHeatLevel = random.nextInt(-3, 4))
                4 -> {
                    if (driving) odometer += random.nextDouble(0.0, 30.0)
                    state.copy(odometerKm = odometer)
                }
                5, 6 -> {
                    if (driving) {
                        accel += random.nextInt(0, 3); decel += random.nextInt(0, 3)
                        val k = random.nextInt(0, 3); turns += k; signaled += random.nextInt(0, k + 1)
                    }
                    if (random.nextInt(15) == 0) speedKnown = !speedKnown
                    state.copy(
                        hardAccelerations = accel.takeIf { speedKnown },
                        hardDecelerations = decel.takeIf { speedKnown },
                    )
                }
                7 -> state.copy(frontWiperOn = listOf(true, false, null).random(random))
                else -> state.copy(uxRestricted = listOf(true, false, null).random(random))
            }
            Event(state, t)
        }
    }

    private class Output {
        val trips = mutableListOf<Trip>()
        val amended = mutableListOf<Trip>()
    }

    private fun run(events: List<Event>, crashBefore: Set<Int> = emptySet()): Output {
        val out = Output()
        fun fresh() = TripRecorder({ out.trips += it }, { out.amended += it })
        var recorder = fresh()
        events.forEachIndexed { i, e ->
            if (i in crashBefore) {
                val snapshot = recorder.snapshot()
                recorder = snapshot?.let {
                    val decoded = RecorderSnapshot.decode(RecorderSnapshot.encode(it))
                    assertTrue("checkpoint must decode: ${'$'}it", decoded != null)
                    TripRecorder.restore(decoded!!, { t -> out.trips += t }, { t -> out.amended += t })
                } ?: fresh()
            }
            recorder.onState(e.state, e.atMillis)
        }
        return out
    }

    @Test fun `counters long trips and gear repeats never break the recorder and records stay valid`() {
        repeat(300) { seed ->
            val random = Random(seed)
            val evs = events(random, 800)
            val out = run(evs, crashBefore = (1 until evs.size).filter { random.nextInt(40) == 0 }.toSet())
            for ((a, b) in out.trips.zipWithNext()) assertTrue("seed $seed: trips in order", a.endedAtMillis <= b.startedAtMillis)
            for (trip in out.trips + out.amended) {
                trip.hardAccelerations?.let { assertTrue("seed $seed", it >= 0) }
                assertEquals("seed $seed: harsh counts known together", trip.hardAccelerations == null, trip.hardDecelerations == null)
                assertEquals("seed $seed: codec", trip, DriveLogCodec.decode(DriveLogCodec.encode(trip)))
            }
            // 주행 중 기어 반복은 주차 직전 2분 안의 것만 센다.
            for (trip in out.trips) assertTrue("seed $seed: shifts", trip.parkingGearShifts in 0..10_000)
        }
    }

    @Test fun `a restart in the middle of a trip makes its counts unknown never wrong`() {
        repeat(300) { seed ->
            val random = Random(seed)
            // 끊김 10분 이내: 복원은 이어 붙인다. 죽은 시점에 누적 횟수가 0부터 다시 시작한다.
            val raw = events(random, 600)
            var t = raw.first().atMillis
            val compressed = raw.map { e -> t += (e.atMillis - t).coerceIn(0, 9 * 60_000); e.copy(atMillis = t) }
            val crashes = (1 until compressed.size).filter { random.nextInt(20) == 0 }.toSet()
            val withRestart = events(Random(seed), 600, restartAt = crashes).let { evs ->
                var u = evs.first().atMillis
                evs.map { e -> u += (e.atMillis - u).coerceIn(0, 9 * 60_000); e.copy(atMillis = u) }
            }
            val clean = run(compressed)
            val crashed = run(withRestart, crashes)
            assertEquals("seed $seed: same trips", clean.trips.map { it.startedAtMillis to it.endedAtMillis }, crashed.trips.map { it.startedAtMillis to it.endedAtMillis })
            for ((a, b) in clean.trips.zip(crashed.trips)) {
                if (b.hardAccelerations != null) assertEquals("seed $seed: accel", a.hardAccelerations, b.hardAccelerations)
            }
        }
    }

    @Test fun `a single drive of fourteen hours keeps exact length and valid shares`() {
        val out = Output()
        val recorder = TripRecorder({ out.trips += it })
        val t0 = 1_790_000_000_000L
        recorder.onState(VehicleState(gear = Gear.PARK, frontWiperOn = false, odometerKm = 0.0), t0)
        recorder.onState(VehicleState(gear = Gear.DRIVE, frontWiperOn = false, odometerKm = 0.0), t0 + 1_000)
        var t = t0 + 1_000
        repeat(14 * 60) { minute ->
            t += 60_000
            recorder.onState(VehicleState(gear = Gear.DRIVE, frontWiperOn = minute % 3 == 0, odometerKm = minute * 1.5), t)
        }
        recorder.onState(VehicleState(gear = Gear.PARK, frontWiperOn = false, odometerKm = 14 * 60 * 1.5), t + 1_000)
        val trip = out.trips.single()
        assertEquals(14 * 3_600_000L + 1_000, trip.durationMillis)
        assertTrue(trip.rainShare!! in 0.30..0.37)
        assertEquals(1_260.0, trip.distanceKm!!, 1e-9)
    }

    @Test fun `a trip longer than the plausible distance keeps its time but not a distance`() {
        val out = Output()
        val recorder = TripRecorder({ out.trips += it })
        val t0 = 1_790_000_000_000L
        recorder.onState(VehicleState(gear = Gear.PARK, odometerKm = 0.0), t0)
        recorder.onState(VehicleState(gear = Gear.DRIVE, odometerKm = 0.0), t0 + 1)
        recorder.onState(VehicleState(gear = Gear.DRIVE, odometerKm = 2_500.0), t0 + 20 * 3_600_000)
        recorder.onState(VehicleState(gear = Gear.PARK, odometerKm = 2_500.0), t0 + 20 * 3_600_000 + 1)
        val trip = out.trips.single()
        assertEquals(20 * 3_600_000L, trip.durationMillis)
        assertNull(trip.distanceKm)
    }

    @Test fun `recording clock never goes backwards while the wall clock jumps by years`() {
        repeat(200) { seed ->
            val random = Random(seed)
            var wall = 1_790_000_000_000L
            var elapsed = 0L
            val clock = RecordingClock(wall = { wall }, elapsed = { elapsed })
            var last = Long.MIN_VALUE
            var lastElapsed = elapsed
            var driving = false
            repeat(2_000) {
                val step = random.nextLong(0, 120_000)
                elapsed += step
                wall += step
                if (random.nextInt(50) == 0) wall += random.nextLong(-5L * 365 * 86_400_000, 5L * 365 * 86_400_000)
                if (random.nextInt(30) == 0) driving = !driving
                val now = clock.now(canResync = !driving)
                assertTrue("seed $seed: monotonic", now >= last)
                // 주행 중(다시 맞추지 않음)에는 경과 시간만큼만 흐른다.
                if (driving && last != Long.MIN_VALUE) assertTrue("seed $seed: drive length", now - last <= elapsed - lastElapsed)
                last = now
                lastElapsed = elapsed
            }
        }
    }

    // ------------------------------------------------------------------ 결함 D2(낮음)

}
