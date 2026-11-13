package kr.chatoon.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kr.chatoon.app.ChatoonApplication
import kr.chatoon.app.data.consent.ConsentStatus
import kotlin.concurrent.thread

/**
 * 시동(부팅)이나 앱 업데이트 뒤 기록 서비스를 다시 띄운다. 이 두 경우는 백그라운드에서도 포그라운드 서비스 시작이 허용된다.
 * 동의하지 않았으면 아무것도 하지 않는다.
 */
class StartupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = (context.applicationContext as ChatoonApplication).container
        val pending = goAsync()
        // 동의 파일 읽기는 디스크 I/O라 메인 스레드에서 하지 않는다. 몇 ms면 끝나므로 goAsync 제한(10초)에 여유가 크다.
        thread(name = "chatoon-startup") {
            try {
                container.consent.load()
                if (container.consent.status.value is ConsentStatus.Granted) RecordingService.start(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
