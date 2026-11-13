package kr.chatoon.app.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kr.chatoon.app.R
import kr.chatoon.app.ui.MainActivity

/**
 * 화면을 닫아도 운전 기록이 이어지도록 앱 프로세스를 포그라운드로 유지한다.
 *
 * 기록 자체는 [kr.chatoon.app.AppContainer]가 한다. 이 서비스는 프로세스 우선순위와 상태 알림만 맡는다.
 * 동의한 동안에만 실행되고, 철회하면 멈춘다. 이벤트를 기다리기만 하므로 주행 중 CPU를 거의 쓰지 않는다.
 */
class RecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: IllegalStateException) {
            // 시스템이 START_STICKY로 백그라운드에서 다시 만들 때 Android 12+가 포그라운드 전환을 막을 수 있다
            // (ForegroundServiceStartNotAllowedException). 앱 전체가 죽지 않게 서비스만 멈추고,
            // 화면이 다시 보일 때 AppContainer.ensureRecordingService가 다시 띄운다.
            Log.w(TAG, "Could not enter foreground; stopping until the app is opened", e)
            stopSelf()
        }
    }

    // 프로세스가 정리되면 시스템이 다시 띄운다. 재시작 때 받을 인텐트는 필요 없다.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(getString(R.string.recording_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "Chatoon/Service"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1

        /**
         * 서비스를 시작한다. 이미 실행 중이면 아무 일도 없다.
         *
         * Android 12부터는 앱이 백그라운드일 때 포그라운드 서비스를 시작할 수 없다(부팅 직후 등 예외 제외).
         * 막히면 기록은 앱 프로세스가 살아 있는 동안만 이어지고, 화면이 다시 보일 때(MainActivity.onStart) 다시 시도한다.
         */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RecordingService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException은 IllegalStateException의 하위 타입이다.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Log.w(TAG, "Foreground service start not allowed now; will retry when the app is opened")
                } else {
                    Log.e(TAG, "Failed to start recording service", e)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
