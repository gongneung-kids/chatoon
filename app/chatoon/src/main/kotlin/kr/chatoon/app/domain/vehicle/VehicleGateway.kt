package kr.chatoon.app.domain.vehicle


import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 상태를 읽는 유일한 통로. 제어 기능은 두지 않는다(최소 권한).
 *
 * 도메인 계층은 이 인터페이스만 알고, Pleos SDK나 Android 차량 API는 구현체에만 있다.
 */
interface VehicleGateway {
    val state: StateFlow<VehicleState>

    /** 차량 서비스에 연결하고 상태 구독을 시작한다. 여러 번 불러도 한 번만 연결한다. */
    fun start()

    /** 구독을 해제하고 연결을 닫는다. */
    fun stop()

    /** 사용자가 권한을 바꿨을 수 있다(예: 속도 권한 허용). 아직 못 받는 신호의 구독을 다시 시도한다. */
    fun refreshPermissions() {}

    /** 차량 속도로 세는 값을 모을지. 끄면 속도 구독을 멈추고 그 값은 모름(null)이 된다. */
    fun setMotionCounting(enabled: Boolean) {}
}
