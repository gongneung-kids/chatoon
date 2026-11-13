package kr.chatoon.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kr.chatoon.app.domain.cartoon.LightScene
import kotlin.coroutines.resume

/**
 * 화를 볼 때 실내 조명(앰비언트 라이트)을 그 화의 장면([LightScene])으로 바꾸고, 다 보면 바꾸기 전 값으로 되돌린다.
 *
 * - 처음 바꿀 때 스위치·밝기·색을 읽어 두고, [restore]에서 그대로 되돌린다. 여러 화를 옮겨 봐도 처음 값만 기억한다.
 * - 부르는 쪽(화면)이 주차 중이고 주인이 켜 두었을 때만 [show]를 부른다. 화면을 벗어나거나 D단이 되면 [restore]를 부른다.
 * - 차량 서비스가 답하지 않아도 화면이 멈추지 않게 호출마다 [CALL_TIMEOUT_MILLIS]까지만 기다린다. 실패는 기록만 하고 넘어간다.
 */
class MoodLight(
    context: Context,
    private val scope: CoroutineScope,
    /** 바꾸기 전 값을 앱 밖(파일)에 적어 둔다. 바꾼 채로 프로세스가 죽어도 다음 시작 때 되돌릴 수 있게([recover]). null이면 지운다. */
    private val remember: (String?) -> Unit = {},
) {

    private val vehicle by lazy { Vehicle(context.applicationContext).also { it.initialize() } }
    private val light get() = vehicle.getLight()

    private class Saved(val area: SeatArea, val switch: LightSwitch?, val brightness: Int?, val color: Int?)

    private val lock = Mutex()
    private var saved: List<Saved>? = null
    private var job: Job? = null

    /**
     * 차량 서비스 연결을 미리 연다. SDK는 연결 완료를 알려 주지 않고, 연결 전 호출은 답이 늦어
     * 처음 화를 볼 때 조명이 몇 초 늦게 바뀐다(2026-11-12 에뮬레이터 약 8초). 조명 연출을 켠 경우 앱 시작 때 부른다.
     */
    fun warmUp() {
        vehicle
    }

    @Synchronized
    fun show(scene: LightScene) {
        job?.cancel()
        job = scope.launch {
            lock.withLock {
                val areas = saved?.map { it.area } ?: areas().also { found ->
                    saved = found.map { read(it) }.also { remember(encode(it)) }
                }
                if (scene.flash != null) {
                    apply(areas, scene.flash, LightScene.FLASH_BRIGHTNESS)
                    delay(LightScene.FLASH_MILLIS)
                }
                apply(areas, scene.color, scene.brightness)
            }
        }
    }

    @Synchronized
    fun restore() {
        job?.cancel()
        job = scope.launch {
            lock.withLock {
                val before = saved ?: return@withLock
                for (s in before) {
                    s.color?.let { call("restore color") { ok, fail -> light.setAmbientLightBrightnessColor(s.area, it, { _, _ -> ok() }, fail) } }
                    s.brightness?.let { call("restore brightness") { ok, fail -> light.setAmbientLightBrightness(s.area, it, { _, _ -> ok() }, fail) } }
                    s.switch?.let { call("restore switch") { ok, fail -> light.setAmbientLightSwitch(s.area, it, { _, _ -> ok() }, fail) } }
                }
                saved = null
                remember(null)
            }
        }
    }

    /** 지난 실행이 조명을 바꾼 채 끝났다면(적어 둔 값이 남아 있으면) 그 값으로 되돌린다. 앱이 시작할 때 부른다. */
    @Synchronized
    fun recover(remembered: String?) {
        val before = remembered?.let(::decode)?.takeIf { it.isNotEmpty() } ?: return
        Log.i(TAG, "Restoring ambient light left changed by a previous run")
        saved = before
        restore()
    }

    // 한 줄에 한 자리: 자리이름들(쉼표)|스위치|밝기|색. 모르는 값은 "-".
    private fun encode(list: List<Saved>): String = list.joinToString("\n") { s ->
        listOf(s.area.values.joinToString(",") { it.name }, s.switch?.name ?: "-", s.brightness?.toString() ?: "-", s.color?.toString() ?: "-").joinToString("|")
    }

    private fun decode(text: String): List<Saved> = text.lines().mapNotNull { line ->
        val p = line.split('|')
        if (p.size != 4) return@mapNotNull null
        val seats = runCatching { p[0].split(',').map { SeatValue.valueOf(it) } }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Saved(
            SeatArea(*seats.toTypedArray()),
            p[1].takeIf { it != "-" }?.let { runCatching { LightSwitch.valueOf(it) }.getOrNull() },
            p[2].toIntOrNull(),
            p[3].toIntOrNull(),
        )
    }

    private suspend fun apply(areas: List<SeatArea>, color: Int, brightness: Int) {
        for (area in areas) {
            call("switch") { ok, fail -> light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> ok() }, fail) }
            call("brightness") { ok, fail -> light.setAmbientLightBrightness(area, brightness, { _, _ -> ok() }, fail) }
            // SDK는 RGB 값을 받는다(탐침: #2979ff → 2718207). 알파는 뺀다.
            call("color") { ok, fail -> light.setAmbientLightBrightnessColor(area, color and 0xFFFFFF, { _, _ -> ok() }, fail) }
        }
    }

    /** 조명을 바꿀 수 있는 자리. 모르면 운전석 한 곳. */
    private suspend fun areas(): List<SeatArea> {
        val found = withTimeoutOrNull(CALL_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { cont ->
                light.checkAmbientLightBrightnessCapabilities(
                    { _, map -> if (cont.isActive) cont.resume(map.keys.toList()) },
                    { e -> Log.w(TAG, "caps failed: ${e.message}"); if (cont.isActive) cont.resume(emptyList<SeatArea>()) },
                )
            }
        }
        return found?.takeIf { it.isNotEmpty() } ?: listOf(SeatArea(SeatValue.ROW_1_LEFT))
    }

    private suspend fun read(area: SeatArea): Saved = Saved(
        area,
        get("read switch") { ok, fail -> light.getAmbientLightSwitch(area, ok, fail) },
        get("read brightness") { ok, fail -> light.getAmbientLightBrightness(area, ok, fail) },
        get("read color") { ok, fail -> light.getAmbientLightBrightnessColor(area, ok, fail) },
    )

    private suspend fun <T> get(what: String, request: ((T) -> Unit, (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(CALL_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<T?> { cont ->
                try {
                    request({ v -> if (cont.isActive) cont.resume(v) }, { e -> Log.w(TAG, "$what failed: ${e.message}"); if (cont.isActive) cont.resume(null) })
                } catch (e: Exception) {
                    Log.w(TAG, "$what threw", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    private suspend fun call(what: String, request: (() -> Unit, (Exception) -> Unit) -> Unit) {
        get<Unit>(what) { ok, fail -> request({ ok(Unit) }, fail) }
    }

    private companion object {
        const val TAG = "Chatoon/MoodLight"
        const val CALL_TIMEOUT_MILLIS = 2_000L
    }
}
