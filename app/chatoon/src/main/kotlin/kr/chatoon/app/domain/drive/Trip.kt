package kr.chatoon.app.domain.drive

/**
 * P단에서 출발해 다시 P단에 세울 때까지 한 번의 주행 기록. 만화 한 화의 재료다.
 *
 * 차가 본 순간만 남긴다: 와이퍼, 주차, 급하게 선 순간, 창문, 공조, 시트 열선·통풍, 거리. 위치·속도 값·경로는 남기지 않는다.
 * 값을 모르면 null이고, 모르는 값으로는 만화를 그리지 않는다.
 */
data class Trip(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    /** 주차하기 직전 [Trip.PARKING_WINDOW_MILLIS] 안에 R·D·N 사이를 오간 횟수. 주차가 어려울수록 커진다. */
    val parkingGearShifts: Int,
    /** 주행 시간 중 와이퍼가 켜져 있던 비율(0.0~1.0). 모르면 null. */
    val rainShare: Double?,
    /** 출발~주차 사이 누적 주행거리 차이(km). 주행거리계 값을 모르면 null. */
    val distanceKm: Double? = null,
    /** 주행 중 급가속·급감속 횟수. 주행 내내 속도 신호를 알았을 때만 값이 있고, 아니면 null(모름). */
    val hardAccelerations: Int? = null,
    val hardDecelerations: Int? = null,
    /** P단에 넣기 바로 전 기어가 R이었으면 true(후면 주차), D였으면 false(전면 주차). N이었거나 모르면 null. */
    val parkedInReverse: Boolean? = null,
    /** 달리는 동안 옆 창문을 전부 절반 넘게 내리고 있던 시간(ms). 창문 위치를 한 번도 몰랐으면 null. */
    val windowsOpenMillis: Long? = null,
    /** 주차하기 전 마지막으로 본 공조 설정 온도(°C, 반올림)와 그때 풍량이 최대였는지. 모르면 null. */
    val setTempC: Int? = null,
    val fanAtMax: Boolean? = null,
    /** 달리는 동안 운전석 시트 열선의 가장 높은 단계(0은 안 켬). 모르면 null. */
    val seatHeatLevel: Int? = null,
    /** 출발할 때의 공조 설정 온도(°C, 반올림). 달리다 온도를 바꿨는지 알려고 둔다. 모르면 null. */
    val setTempStartC: Int? = null,
    /** 출발할 때의 운전석 열선 단계(0은 꺼짐). 달리다 켰는지 알려고 둔다. 모르면 null. */
    val seatHeatStart: Int? = null,
    /** 달리는 동안 운전석 통풍시트의 가장 높은 단계(0은 안 켬)와 출발할 때의 단계. 모르면 null. */
    val seatVentLevel: Int? = null,
    val seatVentStart: Int? = null,
) {
    val durationMillis: Long get() = endedAtMillis - startedAtMillis

    init {
        require(endedAtMillis >= startedAtMillis) { "trip ends before it starts" }
        require(parkingGearShifts >= 0) { "parkingGearShifts must not be negative" }
        require(distanceKm == null || (distanceKm.isFinite() && distanceKm >= 0)) { "distanceKm must be a non-negative number" }
        require((hardAccelerations ?: 0) >= 0 && (hardDecelerations ?: 0) >= 0) { "harsh event counts must not be negative" }
        require((windowsOpenMillis ?: 0) in 0..(endedAtMillis - startedAtMillis)) { "windows open time must be within the trip" }
        require((seatHeatLevel ?: 0) >= 0 && (seatHeatStart ?: 0) >= 0) { "seat heat level must not be negative" }
        require((seatVentLevel ?: 0) >= 0 && (seatVentStart ?: 0) >= 0) { "seat vent level must not be negative" }
    }

    companion object {
        const val PARKING_WINDOW_MILLIS: Long = 120_000

        /** 한 번 주행으로 이보다 길게 달렸다면 주행거리계 값이 튄 것으로 본다(서울~부산 왕복 약 800km). */
        const val MAX_PLAUSIBLE_TRIP_KM: Double = 2_000.0
    }
}
