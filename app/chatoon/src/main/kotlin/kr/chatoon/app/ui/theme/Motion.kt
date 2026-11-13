package kr.chatoon.app.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/*
 * Pleos 모션 토큰. 값은 Pleos 디자인 가이드 Foundations > Interaction(Tension) 그림의 원문 그대로다
 * (https://document.pleos.ai/docs/connect/guide/docs-design/foundations/interaction, 2026-11-03 확인).
 * - Easing: cubic-bezier(0.16, 1, 0.3, 1). 처음에 빨리 움직이고 끝에서 천천히 멈춘다.
 * - Duration: ivi_motion_duration_short1 100ms … long2 450ms
 *
 * 움직임은 상태를 알려 줄 때만 쓴다(Pleos Animation: "사용자에게 상태 설명을 위한 목적").
 * 반복·깜빡임·자동 스크롤은 쓰지 않는다(Pleos Introduction: 운전자의 시선을 끄는 애니메이션 금지).
 * 사라지는 움직임은 나타나는 움직임보다 짧게 한다(Google Design for Driving: Motion).
 */
object PleosMotion {
    val Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    const val SHORT1 = 100
    const val SHORT2 = 150
    const val MEDIUM1 = 250
    const val MEDIUM2 = 300
    const val LONG1 = 400
    const val LONG2 = 450
}

/**
 * 시스템 '애니메이션 삭제'(접근성) 또는 개발자 옵션 '애니메이터 길이 배율 = 0'이면 true.
 * 둘 다 Settings.Global.ANIMATOR_DURATION_SCALE을 0으로 만든다. 운전 중 바꿔도 바로 따르도록 설정 변경을 지켜본다.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(readReduceMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readReduceMotion(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduce
}

private fun readReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Pleos 이징·[durationMillis]의 tween. 움직임 줄이기 설정이면 바로 끝 상태로 간다. */
@Composable
fun <T> pleosTween(durationMillis: Int, delayMillis: Int = 0): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else tween(durationMillis, delayMillis, PleosMotion.Easing)
