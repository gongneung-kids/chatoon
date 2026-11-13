package kr.chatoon.app.domain.vehicle

/**
 * 지금이 정차 중인지 주행 중인지(화면·알림을 보여도 되는지)의 판단.
 *
 * Pleos 주행 규제 개발 가이드는 CarUxRestrictions(운전 제한)로 판단하라고 요구한다. 다만 신호 대기(D단, 속도 0)에는
 * 운전 제한이 풀릴 수 있으므로, "정차"는 기어 P까지 함께 볼 때만 인정한다. 판단을 못 하면 [UNKNOWN].
 */
enum class DriveContext {
    PARKED, DRIVING, UNKNOWN;

    companion object {
        fun of(gear: Gear?, uxRestricted: Boolean?): DriveContext = when {
            uxRestricted == true -> DRIVING
            gear != null && gear != Gear.PARK -> DRIVING
            gear == Gear.PARK -> PARKED
            else -> UNKNOWN
        }
    }
}
