package kr.chatoon.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import kr.chatoon.app.domain.vehicle.HarshEventDetector
import android.content.Context
import android.util.Log
import kr.chatoon.app.domain.vehicle.VehicleGateway
import kr.chatoon.app.domain.vehicle.VehicleState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pleos Vehicle SDK와 Android 차량 API로 만화에 쓰는 차량 상태(기어, 속도로 센 급가속·급감속, 와이퍼, 주행거리, 창문, 공조, 시트 열선·통풍)를
 * 읽는다. 제어 API는 쓰지 않는다(실내 조명 연출은 [MoodLight]가 따로 맡는다).
 *
 * - 연결 대기와 재연결 뒤 리스너 재등록은 SDK가 처리한다.
 * - 기능마다 지원 여부를 먼저 묻고, 지원하는 것만 구독한다. 한 기능의 실패가 다른 기능을 막지 않는다.
 * - 폴링하지 않는다. 값이 바뀔 때 오는 알림만 쓴다.
 */
class PleosVehicleGateway(
    context: Context,
    private val scope: CoroutineScope,
) : VehicleGateway {

    private val vehicle = Vehicle(context.applicationContext)
    private val gearSource = CarGearSource(context.applicationContext)
    private val speedSource = CarSpeedSource(context.applicationContext)
    private val uxRestrictions = UxRestrictionSource(context.applicationContext)

    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)

    private var setupJob: Job? = null
    private val unregisterActions = CopyOnWriteArrayList<() -> Unit>()


    override fun start() {
        if (!started.compareAndSet(false, true)) return
        vehicle.initialize()
        uxRestrictions.start { restricted -> _state.update { it.copy(uxRestricted = restricted) } }
        gearSource.start { gear ->
            // 차량 설정에서 속도 권한을 허용했어도 Pleos는 앱 화면을 내리지 않아 onStart가 안 불릴 수 있다.
            // 기어가 바뀔 때(출발 등) 다시 시도해, 늦어도 다음 주행부터는 센다. 권한이 없으면 확인만 하고 끝난다.
            if (!speedSource.running) startSpeed()
            _state.update { it.copy(gear = gear) }
        }
        startSpeed()
        setupJob = scope.launch {
            isolated("wiper") { setupWiper() }
            isolated("odometer") { setupOdometer() }
            isolated("windows") { setupWindows() }
            isolated("cabinTemp") { setupCabinTemp() }
            isolated("fan") { setupFan() }
            isolated("seatHeat") { setupSeatHeat() }
        }
    }

    override fun refreshPermissions() {
        if (started.get() && !speedSource.running) startSpeed()
    }

    // ---------------------------------------------------------------- 급가속·급감속(차가 놀란 순간)
    //
    // 속도는 별도 스레드에서 오므로 [motionLock]으로 감지기를 묶는다. 상태에는 누적 횟수만 올리고 속도 값은 내보내지 않는다.

    private val motionLock = Any()
    private var harsh = HarshEventDetector()
    private var speedFlowing = false

    /** 속도로 횟수를 세도 되는가(기록 동의를 받은 동안만). */
    @Volatile
    private var motionAllowed = false

    override fun setMotionCounting(enabled: Boolean) {
        if (motionAllowed == enabled) return
        motionAllowed = enabled
        if (enabled) {
            if (started.get()) startSpeed()
            return
        }
        speedSource.stop()
        synchronized(motionLock) { speedFlowing = false }
        publishMotion() // 모름(null)으로: 기록기는 그 주행의 값을 남기지 않는다
    }

    /** 속도 권한이 있으면 구독을 시작한다. 시작할 때마다 감지기를 새로 만든다(누적 횟수는 0부터; 기록기는 줄어든 값을 "모름"으로 본다). */
    private fun startSpeed() {
        if (!motionAllowed) return
        synchronized(motionLock) {
            harsh = HarshEventDetector()
            speedFlowing = false
        }
        speedSource.start(onSample = ::onSpeedSample, onLost = ::onSpeedLost)
    }

    private fun onSpeedSample(metersPerSecond: Float, elapsedNanos: Long) {
        val changed = synchronized(motionLock) {
            // 세기를 끈 직후에 이미 오고 있던 샘플이 "속도 앎"으로 되돌려 놓지 않게 한다(setMotionCounting이 같은 잠금 안에서 끈다).
            if (!motionAllowed) return
            val counted = harsh.onSpeed(metersPerSecond, elapsedNanos)
            val firstSample = !speedFlowing
            speedFlowing = true
            counted || firstSample
        }
        if (changed) publishMotion()
    }

    private fun onSpeedLost() {
        synchronized(motionLock) { speedFlowing = false }
        publishMotion()
    }

    /** 누적 횟수를 상태에 올린다. 속도를 모르면 null(모름). */
    private fun publishMotion() {
        // 값 읽기를 update 안에서 한다. 밖에서 읽고 쓰면 다른 스레드가 올린 더 새 값을 옛 값으로 덮어쓸 수 있다(update는 경쟁 시 다시 계산).
        _state.update {
            val (accel, decel) = synchronized(motionLock) {
                if (speedFlowing) harsh.accelerations to harsh.decelerations else null to null
            }
            it.copy(hardAccelerations = accel, hardDecelerations = decel)
        }
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        setupJob?.cancel()
        setupJob = null
        unregisterActions.forEach { unregister ->
            try {
                unregister()
            } catch (e: Exception) {
                Log.w(TAG, "Unregister failed", e)
            }
        }
        unregisterActions.clear()
        gearSource.stop()
        speedSource.stop()
        synchronized(motionLock) { speedFlowing = false }
        uxRestrictions.stop()
        vehicle.release()
        windowOpen.clear()
        windowAreas.clear()
        _state.value = VehicleState()
    }

    // ---------------------------------------------------------------- 와이퍼

    private suspend fun setupWiper() {
        val wiper = vehicle.getWiper()
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) = updateWiper(state)
            override fun onFailed(e: Exception) {
                    Log.w(TAG, "Wiper listener failed", e)
                }
        }
        wiper.registerWiperState(area, listener)
        track { wiper.unregisterWiperState(area, listener) }
        updateWiper(awaitSdk { ok, fail -> wiper.getCurrentWiperState(area, ok, fail) })
    }

    private fun updateWiper(state: WiperState?) {
        val on = when (state) {
            WiperState.ON -> true
            WiperState.OFF -> false
            // OTHER(정비 위치 등)·SERVICE·모름은 비가 오는지 판단할 수 없다.
            else -> null
        }
        _state.update { it.copy(frontWiperOn = on) }
    }


    // ---------------------------------------------------------------- 누적 주행거리

    private suspend fun setupOdometer() {
        val odometer = vehicle.getOdometer()
        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) = updateOdometer(value)
            override fun onFailed(e: Exception) {
                Log.w(TAG, "Odometer listener failed", e)
            }
        }
        odometer.registerDistanceDriven(listener)
        track { odometer.unregisterDistanceDriven(listener) }
        updateOdometer(awaitSdk { ok, fail -> odometer.getCurrentDistanceDriven(ok, fail) })
    }

    private fun updateOdometer(km: Float?) {
        // 음수·NaN 같은 비정상 값은 "모름"으로 둔다. 기록기는 모르는 값으로 거리를 계산하지 않는다.
        val value = km?.takeIf { it.isFinite() && it >= 0f }?.toDouble()
        _state.update { it.copy(odometerKm = value) }
    }

    // ---------------------------------------------------------------- 창문·공조·열선(차툰 사건의 재료, 읽기만)
    //
    // 만화에는 "창문을 전부 내리고 달렸다", "에어컨 17도, 바람은 최대", "열선을 켰다"처럼 상태만 쓴다.
    // 값이 바뀔 때 오는 알림만 쓰고 폴링하지 않는다. 제어 함수(set…)는 부르지 않는다.

    // 창문은 영역(창문 하나 또는 여러 개를 묶은 것)마다 "절반 넘게 열렸는가"를 둔다.
    private val windowOpen = ConcurrentHashMap<Set<WindowValue>, Boolean>()
    private val windowAreas = ConcurrentHashMap.newKeySet<Set<WindowValue>>()

    private suspend fun setupWindows() {
        val window = vehicle.getWindow()
        val caps: Map<WindowArea, AreaBounds<Int>> = awaitSdk { ok, fail ->
            window.checkWindowPositionCapabilities(onComplete = { _, map -> ok(map) }, onFailed = fail)
        }
        for ((area, bounds) in caps) {
            // 앞뒤 유리와 선루프는 빼고 옆 창문만 본다.
            val sides = area.values.filter { it in SIDE_WINDOWS }.toSet()
            if (sides.isEmpty()) continue
            windowAreas += sides
            val listener = object : WindowPositionListener {
                override fun onWindowPositionUpdated(value: Int?) = updateWindow(sides, value, bounds)
                override fun onFailed(e: Exception) {
                    Log.w(TAG, "Window listener failed", e)
                }
            }
            window.registerWindowPosition(area, listener)
            track { window.unregisterWindowPosition(area, listener) }
            updateWindow(sides, awaitSdk { ok, fail -> window.getCurrentWindowPosition(area, ok, fail) }, bounds)
        }
    }

    private fun updateWindow(area: Set<WindowValue>, position: Int?, bounds: AreaBounds<Int>) {
        val min = bounds.minValue
        val max = bounds.maxValue
        if (position == null || min == null || max == null || max <= min) windowOpen.remove(area)
        // 0이 닫힘, 최댓값이 끝까지 내림(안드로이드 차량 표준 WINDOW_POS, 에뮬레이터에서 콘솔 100 → 앱 10 확인).
        else windowOpen[area] = (position - min) * 2 > (max - min)
        val known = windowAreas.mapNotNull { windowOpen[it] }
        val all = when {
            known.any { !it } -> false
            known.size == windowAreas.size && known.isNotEmpty() -> true
            else -> null
        }
        _state.update { it.copy(windowsOpen = all) }
    }

    private suspend fun setupCabinTemp() {
        val hvac = vehicle.getHVAC()
        val areas: Set<SeatArea> = awaitSdk { ok, fail ->
            hvac.checkHvacTemperatureCapabilities(onComplete = { _, map: Map<SeatArea, AreaBounds<Float>> -> ok(map.keys) }, onFailed = fail)
        }
        val area = driverArea(areas) ?: return
        val listener = object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(value: Float?) = updateCabinTemp(value)
            override fun onFailed(e: Exception) {
                Log.w(TAG, "Cabin temperature listener failed", e)
            }
        }
        hvac.registerHvacTemperature(area, listener)
        track { hvac.unregisterHvacTemperature(area, listener) }
        updateCabinTemp(awaitSdk { ok, fail -> hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail) })
    }

    private fun updateCabinTemp(celsius: Float?) {
        // 사람이 맞출 수 없는 값(NaN, 0도 아래, 40도 위)은 모름으로 둔다.
        val value = celsius?.takeIf { it.isFinite() && it in 1f..40f }
        _state.update { it.copy(cabinSetTempC = value) }
    }

    private suspend fun setupFan() {
        val hvac = vehicle.getHVAC()
        val caps: Map<SeatArea, AreaBounds<Int>> = awaitSdk { ok, fail ->
            hvac.checkHvacFanSpeedCapabilities(onComplete = { _, map -> ok(map) }, onFailed = fail)
        }
        val area = driverArea(caps.keys) ?: return
        val max = caps.getValue(area).maxValue ?: return
        val listener = object : HvacFanSpeedListener {
            override fun onHvacFanSpeedUpdated(value: Int?) = updateFan(value, max)
            override fun onFailed(e: Exception) {
                Log.w(TAG, "Fan listener failed", e)
            }
        }
        hvac.registerHvacFanSpeed(area, listener)
        track { hvac.unregisterHvacFanSpeed(area, listener) }
        updateFan(awaitSdk { ok, fail -> hvac.getHvacFanSpeed(area, ok, fail) }, max)
    }

    private fun updateFan(speed: Int?, max: Int) {
        _state.update { it.copy(fanAtMax = speed?.let { s -> s >= max }) }
    }

    private suspend fun setupSeatHeat() {
        val seat = vehicle.getSeat()
        val caps: Map<SeatArea, AreaBounds<Int>> = awaitSdk { ok, fail ->
            seat.checkSeatTemperatureCapabilities(onComplete = { _, map -> ok(map) }, onFailed = fail)
        }
        val area = driverArea(caps.keys) ?: return
        val listener = object : HvacSeatTemperatureListener {
            override fun onHvacSeatTemperatureUpdated(value: Int?) = updateSeatHeat(value)
            override fun onFailed(e: Exception) {
                Log.w(TAG, "Seat heat listener failed", e)
            }
        }
        seat.registerHvacSeatTemperature(area, listener)
        track { seat.unregisterHvacSeatTemperature(area, listener) }
        updateSeatHeat(awaitSdk { ok, fail -> seat.getHvacSeatTemperature(area, ok, fail) })
    }

    private fun updateSeatHeat(level: Int?) {
        _state.update { it.copy(seatHeatLevel = level) }
    }

    /** 운전석(1열 왼쪽)이 든 영역. 없으면 null(그 기능은 쓰지 않는다). */
    private fun driverArea(areas: Collection<SeatArea>): SeatArea? = areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values }

    // ---------------------------------------------------------------- 공통

    /**
     * 리스너 해제 동작을 기록한다. 그 사이 [stop]이 불렸다면 남기지 않고 바로 해제한다.
     * 준비 작업과 [stop]이 다른 스레드에서 겹쳐도 리스너가 남지 않게 하기 위해서다.
     */
    private fun track(unregister: () -> Unit) {
        unregisterActions += unregister
        if (!started.get() && unregisterActions.remove(unregister)) unregister()
    }

    /** 기능 하나를 준비하다 실패해도 로그만 남기고 다음 기능으로 넘어간다. */
    private suspend fun isolated(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Setup $name timed out; feature disabled")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Setup $name failed; feature disabled", e)
        }
    }

    private companion object {
        const val TAG = "Chatoon/Vehicle"
        val SIDE_WINDOWS = setOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
    }
}
