package kr.chatoon.app.domain.drive

import kotlin.math.abs

/**
 * 주행 기록에 쓰는 시각. 차의 시스템 시계(벽시계)는 시간 동기화나 RTC 오류로 앞뒤로 크게 바뀔 수 있다.
 *
 * - 같은 실행 안에서는 [elapsed](부팅 후 경과 시간, 되돌아가지 않음)로 흐른다. 시계가 뒤로 가도 시각이 한 값에 묶이지 않아,
 *   그동안의 주행이 같은 출발 시각으로 겹쳐 버려지는 일이 없다.
 * - 벽시계와 [toleranceMillis]보다 크게 어긋나면 **주행 중이 아닐 때만**([now]의 `canResync`) 벽시계로 다시 맞춘다.
 *   주행 도중 시계가 몇 년씩 뛰어도 그 주행의 길이는 실제 경과 시간 그대로다.
 * - 다시 맞춘 결과가 이미 낸 시각보다 이르면 맞추지 않는다(기록 순서 보장). 그 경우 벽시계가 따라잡을 때까지 경과 시간으로 흐른다.
 *
 * 한 코루틴에서만 쓴다.
 */
class RecordingClock(
    private val wall: () -> Long,
    private val elapsed: () -> Long,
    private val toleranceMillis: Long = DEFAULT_TOLERANCE_MILLIS,
) {
    private var anchorWall = wall()
    private var anchorElapsed = elapsed()
    private var last = Long.MIN_VALUE

    fun now(canResync: Boolean): Long {
        var t = anchorWall + (elapsed() - anchorElapsed)
        if (canResync) {
            val w = wall()
            if (abs(w - t) > toleranceMillis && w >= last) {
                anchorWall = w
                anchorElapsed = elapsed()
                t = w
            }
        }
        t = maxOf(t, last)
        last = t
        return t
    }

    companion object {
        const val DEFAULT_TOLERANCE_MILLIS = 60_000L
    }
}
