package kr.chatoon.app.domain.drive

import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState

/**
 * 시간순으로 들어오는 차량 상태를 보고 한 번의 주행([Trip])을 만든다. 만화 한 화의 재료가 된다.
 *
 * - 기어가 P에서 벗어나면 주행 시작, 다시 P가 되면 주행 끝.
 * - 와이퍼·창문 시간은 상태가 바뀐 시점 사이의 시간으로 계산한다(폴링 없이 이벤트만 사용).
 * - 앱이 주행 도중에 켜졌다면(처음 본 상태가 이미 주행 중) 그 주행은 기록하지 않는다. 시작을 모르면 비율이 틀리기 때문이다.
 * - 기어 신호가 잠시 "모름(null)"이 되어도 마지막으로 알았던 기어를 기준으로 판단한다.
 * - 앱 프로세스가 도중에 종료되면 [snapshot]으로 저장해 둔 상태를 [restore]로 이어 받는다.
 *   끊긴 시간이 [MAX_RESUME_GAP_MILLIS] 이하면 이어 붙이고, 끊긴 동안의 와이퍼·창문은 "모름"으로 둔다.
 *   그보다 길거나 시계가 뒤로 갔으면 시동이 꺼졌던 것으로 보고, 진행 중이던 주행은 마지막으로 확인한 시각에서 끝난 것으로 기록한다
 *   (실제보다 몇 초 짧을 수 있지만 주행 하나를 통째로 잃는 것보다 낫다).
 * - 주행거리 신호는 기어 신호보다 몇 초 늦게 올 수 있다(에뮬레이터에서 약 2초 확인). P단에서는 차가 움직일 수 없으므로,
 *   주차 중에 늘어난 주행거리는 방금 끝난 주행의 것이다. 그런 갱신이 오면 [onTripAmended]로 거리를 고친 주행을 낸다.
 *
 * 한 스레드에서만 호출한다고 가정한다.
 */
class TripRecorder(
    private val onTrip: (Trip) -> Unit,
    private val onTripAmended: (Trip) -> Unit = {},
) {
    private var resumePending = false
    private var skipNextAccumulate = false

    private var lastState: VehicleState? = null
    private var lastAt: Long = 0
    private var lastKnownGear: Gear? = null
    private var lastKnownOdometer: Double? = null

    // 주행 중 누적값
    private var tripStartedAt: Long? = null
    private var tripStartOdometer: Double? = null
    private var knownWiperMillis = 0L
    private var wiperOnMillis = 0L
    private val gearChanges = ArrayDeque<Long>()

    // 방금 끝난 주행(주차 중 늦게 온 주행거리로 고칠 수 있음). 다음 출발이나 복원 때 비운다.
    private var lastFinishedTrip: Trip? = null
    private var lastFinishedTripStartOdometer: Double? = null

    // 급가속·급감속: 차량 상태의 누적 횟수가 늘어난 만큼 더한다.
    private var tripHardAccelerations = 0
    private var tripHardDecelerations = 0
    private var tripHarshKnown = false
    /** 복원 직후: 누적 횟수는 앱이 켜질 때 0부터 다시 세므로 이전 값과 빼지 않는다. */
    private var harshCounterRestarted = false

    // 창문·공조·열선·통풍: 창문은 와이퍼처럼 시간을 더하고, 공조는 출발 때와 마지막 값, 열선·통풍은 출발 때와 가장 높은 단계를 둔다.
    private var cabin = Cabin()

    fun onState(state: VehicleState, atMillis: Long) {
        if (resumePending) resume(atMillis)
        val previous = lastState
        if (previous != null) {
            require(atMillis >= lastAt) { "states must arrive in time order" }
            if (skipNextAccumulate) skipNextAccumulate = false else accumulate(previous, atMillis - lastAt)
            accumulateHarsh(previous, state)
            // 기어와 주행거리가 한 번에 바뀌어 들어오면 바뀐 주행거리를 기준으로 출발·도착을 잡는다.
            state.odometerKm?.let { lastKnownOdometer = it }
            handleGear(lastKnownGear, state, atMillis)
            noteCabin(state)
            amendLastTripDistance()
        } else {
            state.odometerKm?.let { lastKnownOdometer = it }
        }
        if (state.gear != null) lastKnownGear = state.gear
        lastState = state
        lastAt = atMillis
    }

    /** 지금까지의 진행 상태. 아직 상태를 하나도 받지 않았으면 null. */
    fun snapshot(): RecorderSnapshot? {
        val state = lastState ?: return null
        return RecorderSnapshot(
            lastState = state,
            lastAtMillis = lastAt,
            lastKnownGear = lastKnownGear,
            lastKnownOdometerKm = lastKnownOdometer,
            tripStartedAtMillis = tripStartedAt,
            tripStartOdometerKm = tripStartOdometer,
            knownWiperMillis = knownWiperMillis,
            wiperOnMillis = wiperOnMillis,
            gearChangesAtMillis = gearChanges.toList(),
            tripHardAccelerations = tripHardAccelerations,
            tripHardDecelerations = tripHardDecelerations,
            tripHarshKnown = tripHarshKnown,
            cabin = cabin,
        )
    }

    /** 주차 중(주행 없음)에 주행거리가 늘었으면 방금 끝난 주행의 거리를 고친다. */
    private fun amendLastTripDistance() {
        val trip = lastFinishedTrip ?: return
        if (tripStartedAt != null) return
        val start = lastFinishedTripStartOdometer ?: return
        val end = lastKnownOdometer ?: return
        val distance = (end - start).takeIf { it in 0.0..Trip.MAX_PLAUSIBLE_TRIP_KM } ?: return
        if (distance <= (trip.distanceKm ?: -1.0)) return
        val amended = trip.copy(distanceKm = distance)
        lastFinishedTrip = amended
        onTripAmended(amended)
    }

    /** 복원 뒤 첫 상태에서 한 번만 부른다. 끊긴 구간을 이어 붙일지 버릴지 정한다. */
    private fun resume(atMillis: Long) {
        resumePending = false
        val gap = atMillis - lastAt
        if (gap < 0 || gap > MAX_RESUME_GAP_MILLIS) {
            val last = lastState
            if (tripStartedAt != null && last != null && lastAt > tripStartedAt!!) finishTrip(lastAt)
            reset()
            return
        }
        // 끊긴 동안의 와이퍼·창문은 모르므로 시간 계산에서 뺀다. 주행 시간 자체는 시작~끝으로 그대로 센다.
        skipNextAccumulate = true
        // 끊긴 동안의 급가속·급감속은 셀 수 없다. 0이라고 남기면 틀린 말이 되므로 이 주행의 횟수는 모름으로 둔다.
        tripHarshKnown = false
        harshCounterRestarted = true
    }

    private fun reset() {
        lastState = null
        lastAt = 0
        lastKnownGear = null
        lastKnownOdometer = null
        tripStartedAt = null
        tripStartOdometer = null
        lastFinishedTrip = null
        lastFinishedTripStartOdometer = null
        gearChanges.clear()
    }

    private fun accumulateHarsh(previous: VehicleState, state: VehicleState) {
        val restarted = harshCounterRestarted
        harshCounterRestarted = false
        if (tripStartedAt == null) return
        val accel = state.hardAccelerations
        val decel = state.hardDecelerations
        val prevAccel = previous.hardAccelerations
        val prevDecel = previous.hardDecelerations
        // 지금 값이나 이전 값을 모르거나(신호가 이제 막 들어옴) 앱이 다시 켜졌으면 그 사이를 셀 수 없다.
        if (accel == null || decel == null || restarted || prevAccel == null || prevDecel == null || accel < prevAccel || decel < prevDecel) {
            tripHarshKnown = false
            return
        }
        tripHardAccelerations += (accel - prevAccel).toInt()
        tripHardDecelerations += (decel - prevDecel).toInt()
    }

    private fun accumulate(previous: VehicleState, elapsed: Long) {
        if (tripStartedAt == null || elapsed <= 0) return
        previous.frontWiperOn?.let { on ->
            knownWiperMillis += elapsed
            if (on) wiperOnMillis += elapsed
        }
        previous.windowsOpen?.let { open -> cabin = cabin.withWindow(open, elapsed) }
    }

    /** 주행 중에 본 공조·열선 값. 출발하는 순간의 상태부터 센다(기어 처리 뒤에 부른다). 주차하는 순간의 상태는 세지 않는다. */
    private fun noteCabin(state: VehicleState) {
        if (tripStartedAt == null) return
        cabin = cabin.noted(state)
    }

    private fun handleGear(before: Gear?, state: VehicleState, at: Long) {
        val now = state.gear
        if (now == before || now == null) return
        if (before == Gear.PARK) {
            beginTrip(at, harshKnown = state.hardAccelerations != null && state.hardDecelerations != null)
            return
        }
        if (now == Gear.PARK) {
            finishTrip(at, lastGear = before)
            return
        }
        if (tripStartedAt != null) {
            gearChanges.addLast(at)
            // 주차 판단에 쓰는 최근 구간만 남겨 긴 주행에서도 메모리가 늘지 않게 한다.
            while (gearChanges.isNotEmpty() && at - gearChanges.first() > Trip.PARKING_WINDOW_MILLIS) {
                gearChanges.removeFirst()
            }
        }
    }

    private fun beginTrip(at: Long, harshKnown: Boolean) {
        lastFinishedTrip = null
        lastFinishedTripStartOdometer = null
        tripStartedAt = at
        tripStartOdometer = lastKnownOdometer
        knownWiperMillis = 0; wiperOnMillis = 0
        gearChanges.clear()
        tripHardAccelerations = 0
        tripHardDecelerations = 0
        tripHarshKnown = harshKnown
        cabin = Cabin()
    }

    private fun finishTrip(at: Long, lastGear: Gear? = null) {
        val start = tripStartedAt ?: return
        val shifts = gearChanges.count { at - it <= Trip.PARKING_WINDOW_MILLIS }
        val trip = Trip(
            startedAtMillis = start,
            endedAtMillis = at,
            parkingGearShifts = shifts,
            rainShare = share(wiperOnMillis, knownWiperMillis),
            distanceKm = tripDistance(),
            hardAccelerations = tripHardAccelerations.takeIf { tripHarshKnown },
            hardDecelerations = tripHardDecelerations.takeIf { tripHarshKnown },
            parkedInReverse = when (lastGear) {
                Gear.REVERSE -> true
                Gear.DRIVE -> false
                else -> null
            },
            windowsOpenMillis = cabin.windowsOpenMillis.takeIf { cabin.knownWindowMillis > 0 }?.coerceAtMost(at - start),
            setTempC = cabin.setTempC?.let { Math.round(it) },
            fanAtMax = cabin.fanAtMax,
            seatHeatLevel = cabin.seatHeat,
            setTempStartC = cabin.setTempStartC?.let { Math.round(it) },
            seatHeatStart = cabin.seatHeatStart,
            seatVentLevel = cabin.seatVent,
            seatVentStart = cabin.seatVentStart,
        )
        onTrip(trip)
        lastFinishedTrip = trip
        lastFinishedTripStartOdometer = tripStartOdometer
        tripStartedAt = null
    }

    /** 시작·끝 주행거리를 모두 알고, 값이 그럴듯할 때만 거리로 인정한다. */
    private fun tripDistance(): Double? {
        val start = tripStartOdometer ?: return null
        val end = lastKnownOdometer ?: return null
        return (end - start).takeIf { it in 0.0..Trip.MAX_PLAUSIBLE_TRIP_KM }
    }

    private fun share(on: Long, known: Long): Double? = if (known == 0L) null else on.toDouble() / known

    /**
     * 한 주행 동안의 실내(창문·공조·열선·통풍) 기록. 값이 바뀔 때마다 새로 만들어 기록기 안에서만 쓴다.
     *
     * @param setTempStartC 출발 때 공조 설정 온도. [setTempC]는 주차 전 마지막 값.
     * @param seatHeat 달리는 동안 가장 높은 열선 단계. [seatHeatStart]는 출발 때 단계(꺼짐 0).
     * @param seatVent 달리는 동안 가장 높은 통풍 단계. [seatVentStart]는 출발 때 단계. 차량은 통풍을 음수 단계로 알려 준다.
     */
    data class Cabin(
        val knownWindowMillis: Long = 0,
        val windowsOpenMillis: Long = 0,
        val setTempStartC: Float? = null,
        val setTempC: Float? = null,
        val fanAtMax: Boolean? = null,
        val seatHeatStart: Int? = null,
        val seatHeat: Int? = null,
        val seatVentStart: Int? = null,
        val seatVent: Int? = null,
    ) {
        fun withWindow(open: Boolean, elapsed: Long) =
            copy(knownWindowMillis = knownWindowMillis + elapsed, windowsOpenMillis = windowsOpenMillis + if (open) elapsed else 0)

        fun noted(state: VehicleState): Cabin {
            var c = this
            state.cabinSetTempC?.let { c = c.copy(setTempStartC = c.setTempStartC ?: it, setTempC = it) }
            state.fanAtMax?.let { c = c.copy(fanAtMax = it) }
            state.seatHeatLevel?.let { level ->
                val heat = level.coerceAtLeast(0)
                val vent = (-level).coerceAtLeast(0)
                c = c.copy(
                    seatHeatStart = c.seatHeatStart ?: heat,
                    seatHeat = maxOf(c.seatHeat ?: 0, heat),
                    seatVentStart = c.seatVentStart ?: vent,
                    seatVent = maxOf(c.seatVent ?: 0, vent),
                )
            }
            return c
        }
    }

    companion object {
        /**
         * 프로세스가 죽었다 다시 뜨는 데는 보통 몇 초면 된다. 이보다 오래 끊겼다면 시동이 꺼졌다 켜졌을 가능성이 크고,
         * 그 사이 무슨 일이 있었는지 모르므로 이어 붙이지 않는다.
         */
        const val MAX_RESUME_GAP_MILLIS: Long = 10L * 60 * 1000

        /** 방금 끝난 주행은 체크포인트에 넣지 않으므로, 복원 뒤에는 늦은 주행거리로 고치지 않는다(거리가 조금 짧을 뿐). */
        fun restore(snapshot: RecorderSnapshot, onTrip: (Trip) -> Unit, onTripAmended: (Trip) -> Unit = {}): TripRecorder =
            TripRecorder(onTrip, onTripAmended).apply {
                lastState = snapshot.lastState
                lastAt = snapshot.lastAtMillis
                lastKnownGear = snapshot.lastKnownGear
                lastKnownOdometer = snapshot.lastKnownOdometerKm
                tripStartedAt = snapshot.tripStartedAtMillis
                tripStartOdometer = snapshot.tripStartOdometerKm
                knownWiperMillis = snapshot.knownWiperMillis
                wiperOnMillis = snapshot.wiperOnMillis
                gearChanges.addAll(snapshot.gearChangesAtMillis)
                tripHardAccelerations = snapshot.tripHardAccelerations
                tripHardDecelerations = snapshot.tripHardDecelerations
                tripHarshKnown = snapshot.tripHarshKnown
                cabin = snapshot.cabin
                resumePending = true
            }
    }
}
