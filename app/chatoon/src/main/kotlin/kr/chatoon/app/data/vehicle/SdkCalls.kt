package kr.chatoon.app.data.vehicle

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** SDK 응답을 기다리는 최대 시간. Pleos 심사 기준(버튼 반응 2초)을 넘지 않게 잡는다. */
internal const val SDK_CALL_TIMEOUT_MILLIS = 2_000L

/**
 * `onComplete`/`onFailed` 콜백 방식의 Pleos SDK 호출을 일시 중단 함수로 바꾼다.
 *
 * SDK가 콜백을 두 번 부르거나 취소 뒤에 부르더라도 한 번만 반영한다.
 * 정해진 시간 안에 응답이 없으면 [kotlinx.coroutines.TimeoutCancellationException]을 던진다.
 */
internal suspend fun <T> awaitSdk(
    call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
): T = withTimeout(SDK_CALL_TIMEOUT_MILLIS) {
    suspendCancellableCoroutine { cont ->
        call(
            { value -> if (cont.isActive) cont.resume(value) },
            { error -> if (cont.isActive) cont.resumeWithException(error) },
        )
    }
}
