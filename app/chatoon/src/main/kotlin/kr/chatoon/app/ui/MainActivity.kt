package kr.chatoon.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.chatoon.app.ui.cartoon.CarPaint
import kr.chatoon.app.ui.cartoon.LocalCarPaint
import androidx.activity.OnBackPressedCallback
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kr.chatoon.app.ChatoonApplication
import kr.chatoon.app.data.vehicle.CarSpeedSource
import kr.chatoon.app.data.consent.ConsentStatus
import kr.chatoon.app.ui.theme.ChatoonTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as ChatoonApplication).container

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(container) }

    // 변화 알림용 권한(Android 13+). 동의 뒤 한 번 묻는다. 거절해도 앱은 그대로 동작하고, 알림만 뜨지 않는다.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // 차량 속도 권한(급가속·급감속, 명세 §15). 차량 OS가 이 권한을 "위치" 묶음으로 보여 주므로 자동으로 묻지 않고,
    // 운전자가 화면에서 설명을 읽고 "켜기"를 누를 때만 묻는다.
    private val speedPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // 거절했는데 다시 물을 수 없으면(다시 묻지 않음) 화면이 차량 설정 안내로 바뀐다.
        val blocked = !granted && !shouldShowRequestPermissionRationale(CarSpeedSource.PERMISSION)
        viewModel.setSpeedAccess(if (granted) SpeedAccess.GRANTED else if (blocked) SpeedAccess.BLOCKED else SpeedAccess.OFF)
        container.vehicle.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면이 다시 만들어진 경우(프로세스 복원·설정 변경)에도 감시한다. 이미 동의한 상태면 묻지 않는다.
        requestNotificationPermissionAfterConsent()
        // 최상위 화면에서 Back(CCP Back 하드키 포함)은 앱을 끝내지 않고 숨긴다(Pleos CCP 개발 가이드, 미구현 시 심사 반려 대상).
        // 확인 창은 각자 Back으로 닫히고, 탭은 모두 같은 단계의 최상위 화면이다.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        setContent {
            ChatoonTheme {
                // 주인이 고른 차 색으로 모든 컷의 차를 칠한다(설정 '내 차 색').
                val paint by viewModel.carPaint.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalCarPaint provides CarPaint.of(paint).color) {
                    MainScreen(viewModel, onDecline = ::finish, onEnableSpeed = { speedPermission.launch(CarSpeedSource.PERMISSION) })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 앱이 백그라운드에서 깨어나 기록 서비스를 못 띄웠다면, 화면이 보이는 지금 다시 띄운다.
        container.ensureRecordingService()
        // 차량 설정에서 속도 권한을 바꾸고 돌아왔을 수 있다.
        val granted = checkSelfPermission(CarSpeedSource.PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.setSpeedAccess(SpeedAccess.GRANTED) else viewModel.speedAccessLost()
        container.vehicle.refreshPermissions()
    }

    /**
     * 운전자가 이 화면에서 방금 "동의하고 시작하기"를 눌렀을 때만 한 번 묻는다. 동의서에 알림을 쓰는 이유를 먼저 적어 두어
     * 시스템 창이 맥락 없이 뜨지 않게 한다. 이미 동의한 상태로 앱을 열 때는 묻지 않는다(거절했는데 열 때마다 묻지 않게).
     * 거절해도 앱은 그대로 동작하고 알림만 뜨지 않는다.
     */
    private fun requestNotificationPermissionAfterConsent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        lifecycleScope.launch {
            var sawRequired = false
            container.consent.status.first {
                if (it is ConsentStatus.Required) sawRequired = true
                sawRequired && it is ConsentStatus.Granted
            }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

}
