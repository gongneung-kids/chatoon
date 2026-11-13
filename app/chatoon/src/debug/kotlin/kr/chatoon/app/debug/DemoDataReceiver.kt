package kr.chatoon.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.chatoon.app.ChatoonApplication
import kotlin.concurrent.thread

/**
 * 시연용 주행 기록을 넣는다(debug 빌드 전용, adb 셸만 호출 가능).
 *
 * ```
 * adb shell am broadcast --user 10 -a kr.chatoon.app.DEMO_DATA \
 *     -n kr.chatoon.app/.debug.DemoDataReceiver --es scenario commuter
 * ```
 * scenario: commuter(평일 출퇴근 12주) | clear(모두 지움)
 */
class DemoDataReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_SCENARIO)?.lowercase()
        val container = (context.applicationContext as ChatoonApplication).container
        val pending = goAsync()
        thread(name = "chatoon-demo") {
            try {
                val now = container.clock()
                // 실패 이유를 나눠 남긴다(알 수 없는 시나리오를 "동의 필요"로 적으면 원인을 헷갈림).
                when (name) {
                    "clear" -> container.driveLog.replaceAll(emptyList()).also {
                        Log.i(TAG, if (it) "Cleared all trips" else NOT_SAVED)
                    }
                    "commuter" -> {
                        val trips = DemoHistory.generate(now)
                        container.driveLog.replaceAll(trips).also {
                            if (it) {
                                // 몇 주치 기록인데 설정 화면이 "기록 동의: 오늘부터"라고 하면 동의 전 기록처럼 보인다. 첫 주행 하루 전으로 맞춘다.
                                trips.minOfOrNull { trip -> trip.startedAtMillis }?.let { first -> container.consent.grant(first - DAY_MILLIS) }
                                // 설정의 '누적 주행거리'가 기록과 어긋나지 않게, 도구가 콘솔 값을 이 값으로 맞춘다.
                                val odometer = DemoHistory.ODOMETER_START_KM + trips.sumOf { trip -> trip.distanceKm ?: 0.0 }
                                Log.i(TAG, "Loaded COMMUTER: ${trips.size} trips over ${DemoHistory.spanDays(trips, now)} days, odometer ${"%.1f".format(java.util.Locale.ROOT, odometer)} km")
                            } else {
                                Log.w(TAG, NOT_SAVED)
                            }
                        }
                    }
                    else -> Log.w(TAG, "Unknown scenario: $name (commuter|clear)")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Chatoon/Demo"
        const val EXTRA_SCENARIO = "scenario"
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        /** 저장소가 쓰기를 거부한 경우: 동의 전(기록 꺼짐)이거나 저장 실패. */
        const val NOT_SAVED = "Demo data not saved (consent required or storage failed)"
    }
}
