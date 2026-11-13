package kr.chatoon.app.domain.vehicle

/**
 * 속도 샘플로 급가속·급감속을 센다. 속도 값은 이 안에서만 쓰고 밖으로 내보내지 않는다 — 횟수만 남는다.
 *
 * 기준(학술 논문 두 편이 한국교통안전공단 운행기록(eTAS) 집계 정의로 인용한 값, 사업용 차량):
 * 공단의 현행 「위험운전행동 기준」 표는 택시·버스·화물차별·속도 구간별 값이고 승용차용 단일 값은 없다(2026-11-03 원문 확인,
 * research/sources/기획서_근거_확인_2026-11-03.md). 그래서 화면에는 "공단 기준"이 아니라 "사업용 차량 운행기록 연구에서 쓰는 기준"이라고 쓴다.
 * - 급가속: 1초 동안 [ACCEL_KMH_PER_S]km/h 이상 빨라짐. [ACCEL_MAX_KMH_PER_S]를 넘는 값은 센서 튐으로 보고 버린다.
 * - 급감속: 1초 동안 [DECEL_KMH_PER_S]km/h 이상 느려짐. [DECEL_MAX_KMH_PER_S]를 넘는 값은 버린다.
 *
 * 한 번의 급제동이 여러 샘플에 걸쳐 기준을 넘어도 1번으로 센다: 변화율이 기준의 절반 아래로 돌아와야 다음을 셀 수 있다.
 * 시각은 부팅 뒤 경과 시간(나노초)을 쓴다 — 시계를 바꿔도 변화율이 틀리지 않는다.
 */
class HarshEventDetector {

    var accelerations: Long = 0
        private set
    var decelerations: Long = 0
        private set

    private val samples = ArrayDeque<Pair<Long, Double>>() // (경과 나노초, km/h)
    private var inAcceleration = false
    private var inDeceleration = false

    /** 샘플을 넣는다. 횟수가 늘었으면 true. */
    fun onSpeed(speedMetersPerSecond: Float, elapsedNanos: Long): Boolean {
        if (!speedMetersPerSecond.isFinite() || speedMetersPerSecond < 0f) return false
        val kmh = speedMetersPerSecond * 3.6
        val last = samples.lastOrNull()
        if (last != null && elapsedNanos <= last.first) return false // 순서가 뒤집힌 샘플은 버린다
        if (last != null && elapsedNanos - last.first > MAX_GAP_NANOS) {
            // 샘플이 오래 끊겼다(신호 끊김·절전). 끊기기 전 값과 비교하면 변화율이 틀린다.
            samples.clear()
            inAcceleration = false
            inDeceleration = false
        }
        samples.addLast(elapsedNanos to kmh)
        while (samples.size > 1 && elapsedNanos - samples[1].first >= WINDOW_NANOS) samples.removeFirst()
        val reference = samples.first()
        val span = elapsedNanos - reference.first
        if (span < WINDOW_NANOS) return false // 아직 1초치가 없다
        val rate = (kmh - reference.second) / (span / 1e9) // km/h per second
        var counted = false
        if (rate >= ACCEL_KMH_PER_S && rate <= ACCEL_MAX_KMH_PER_S) {
            if (!inAcceleration) {
                accelerations++
                counted = true
            }
            inAcceleration = true
        } else if (rate < ACCEL_KMH_PER_S / 2) {
            inAcceleration = false
        }
        if (-rate >= DECEL_KMH_PER_S && -rate <= DECEL_MAX_KMH_PER_S) {
            if (!inDeceleration) {
                decelerations++
                counted = true
            }
            inDeceleration = true
        } else if (-rate < DECEL_KMH_PER_S / 2) {
            inDeceleration = false
        }
        return counted
    }

    companion object {
        const val ACCEL_KMH_PER_S = 11.0
        const val ACCEL_MAX_KMH_PER_S = 25.0
        const val DECEL_KMH_PER_S = 7.5
        const val DECEL_MAX_KMH_PER_S = 40.0
        private const val WINDOW_NANOS = 1_000_000_000L
        private const val MAX_GAP_NANOS = 2_000_000_000L
    }
}
