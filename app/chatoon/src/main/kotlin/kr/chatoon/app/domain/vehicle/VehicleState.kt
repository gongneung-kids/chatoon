package kr.chatoon.app.domain.vehicle

/**
 * 차툰이 읽는 차량 상태 스냅샷. 차가 본 순간을 만화로 그리는 데 쓰는 것만 둔다. 이 앱은 차량을 읽기만 한다
 * (바꾸는 것은 주인이 켠 실내 조명 연출 하나이고, 그것은 이 상태에 넣지 않는다).
 *
 * 값이 null이면 차량에서 아직 받지 못했거나 이 차가 지원하지 않는 항목이다.
 * 기록기는 null을 "모름"으로 다루고, 모르는 값으로는 판단하지 않는다.
 */
data class VehicleState(
    val gear: Gear? = null,
    val frontWiperOn: Boolean? = null,
    /** 누적 주행거리(km). */
    val odometerKm: Double? = null,
    /** 플랫폼 운전 제한(CarUxRestrictions.isRequiresDistractionOptimization). 모르면 null. */
    val uxRestricted: Boolean? = null,
    /**
     * 앱이 켜진 뒤 센 급가속·급감속 누적 횟수([HarshEventDetector]). 속도 신호를 받을 수 없으면(권한 없음 등) null.
     * 속도 값 자체는 상태에 넣지 않는다 — 횟수만 기록에 남긴다.
     */
    val hardAccelerations: Long? = null,
    val hardDecelerations: Long? = null,
    /** 옆 창문이 전부 절반 넘게 내려가 있으면 true, 하나라도 덜 열렸으면 false. 한 곳이라도 모르면 null. */
    val windowsOpen: Boolean? = null,
    /** 운전석 쪽 공조 설정 온도(°C). 실내 온도가 아니라 맞춰 둔 온도다. */
    val cabinSetTempC: Float? = null,
    /** 공조 풍량이 가장 센 단계인가. */
    val fanAtMax: Boolean? = null,
    /** 운전석 시트 열선 단계. 0은 꺼짐, 음수는 통풍. */
    val seatHeatLevel: Int? = null,
) {
    val driveContext: DriveContext get() = DriveContext.of(gear, uxRestricted)
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE }
