package kr.chatoon.app.debug

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/**
 * 카툰이 쓰려는 신호와 기능이 에뮬레이터에서 실제로 되는지 확인하는 탐침(debug 빌드 전용, adb 셸만 호출 가능).
 * 값은 logcat에만 남기고 저장하지 않는다. 결과를 보고 되는 것만 앱 기능으로 올린다.
 *
 * adb shell am broadcast --user 10 -a kr.chatoon.app.VEHICLE_PROBE -n kr.chatoon.app/.debug.VehicleProbeReceiver --es cmd read
 *   --es cmd ambient [--ei bright 5] [--ei color 3] [--es switch ON|OFF]
 *   --es cmd tts [--es text "말할 문장"] [--es mode ON_DEVICE|HYBRID|SERVER]
 *   --es cmd stt [--es mode ON_DEVICE] [--es pcm files/probe.pcm]   (pcm: 16kHz 16bit mono, 없으면 마이크)
 *   --es cmd sttstop
 * adb logcat -s Chatoon/Probe
 */
class VehicleProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.getStringExtra("cmd")) {
            "read" -> read(app)
            "ambient" -> ambient(app, intent.getIntExtra("bright", -1), intent.getIntExtra("color", -1), intent.getStringExtra("switch"))
            "tts" -> speak(app, intent.getStringExtra("text") ?: "주차 세 번 만에. 지난주보다 두 번 줄었다.", intent.getStringExtra("mode") ?: "ON_DEVICE")
            "stt" -> listen(app, intent.getStringExtra("mode") ?: "ON_DEVICE", intent.getStringExtra("pcm"))
            "sttstop" -> {
                stt?.let { runCatching { it.stop() } }
                log("stt stop requested")
            }
            else -> Log.w(TAG, "cmd: read|ambient|tts|stt|sttstop")
        }
    }

    // ---------------------------------------------------------------- 차량 신호 읽기

    private fun read(app: Context) {
        val v = vehicle(app)
        log("---- read start ----")

        val window = v.getWindow()
        window.checkWindowPositionCapabilities({ _, map ->
            log("window caps ${map.map { "${it.key.values.toList()}=${it.value.minValue}..${it.value.maxValue}" }}")
            for (area in map.keys) {
                window.getCurrentWindowPosition(area, { log("window ${area.values.toList()} = $it") }, fail("window ${area.values.toList()}"))
            }
        }, fail("window caps"))

        val hvac = v.getHVAC()
        hvac.checkHvacTemperatureCapabilities({ _, map ->
            log("hvac temp caps ${map.map { "${it.key.values.toList()}=${it.value.minValue}..${it.value.maxValue}" }}")
            for (area in map.keys) {
                hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { log("hvac temp ${area.values.toList()} = $it") }, fail("hvac temp"))
                hvac.getHvacPowerState(area, { log("hvac power ${area.values.toList()} = $it") }, fail("hvac power"))
            }
        }, fail("hvac temp caps"))
        hvac.checkHvacFanSpeedCapabilities({ _, map ->
            log("hvac fan caps ${map.map { "${it.key.values.toList()}=${it.value.minValue}..${it.value.maxValue}" }}")
            for (area in map.keys) {
                hvac.getHvacFanSpeed(area, { log("hvac fan ${area.values.toList()} = $it") }, fail("hvac fan"))
            }
        }, fail("hvac fan caps"))

        val seat = v.getSeat()
        seat.checkSeatOccupancyCapabilities({ _, list ->
            log("seat occupancy caps ${list.map { it.values.toList() }}")
            for (area in list) {
                seat.getCurrentSeatOccupancy(area, { log("seat occupancy ${area.values.toList()} = $it") }, fail("seat occupancy ${area.values.toList()}"))
            }
        }, fail("seat occupancy caps"))
        seat.checkSeatForeAftPositionCapabilities({ _, map ->
            log("seat foreaft caps ${map.map { "${it.key.values.toList()}=${it.value.minValue}..${it.value.maxValue}" }}")
            for (area in map.keys) {
                seat.getCurrentSeatForeAftPosition(area, { log("seat foreaft ${area.values.toList()} = $it") }, fail("seat foreaft"))
            }
        }, fail("seat foreaft caps"))
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        seat.getCurrentSeatBackrestAnglePosition(driver, { log("seat backrest driver = $it") }, fail("seat backrest driver"))
        seat.getCurrentSeatBeltBuckledState(driver, { log("seat belt driver = $it") }, fail("seat belt driver"))
        seat.getCurrentSeatBeltBuckledState(SeatArea(SeatValue.ROW_2_LEFT), { log("seat belt row2 left = $it") }, fail("seat belt row2 left"))
        seat.getHvacSeatTemperature(driver, { log("seat heat driver = $it") }, fail("seat heat driver"))

        val light = v.getLight()
        light.checkAmbientLightBrightnessCapabilities({ _, map ->
            log("ambient bright caps ${map.map { "${it.key.values.toList()}=${it.value.minValue}..${it.value.maxValue}" }}")
            for (area in map.keys) {
                light.getAmbientLightBrightness(area, { log("ambient bright ${area.values.toList()} = $it") }, fail("ambient bright"))
            }
        }, fail("ambient bright caps"))
        light.checkAmbientLightBrightnessColorCapabilities({ _, list ->
            log("ambient color caps ${list.map { it.values.toList() }}")
            for (area in list) {
                light.getAmbientLightBrightnessColor(area, { log("ambient color ${area.values.toList()} = $it") }, fail("ambient color"))
            }
        }, fail("ambient color caps"))
        light.checkAmbientLightSwitchCapabilities({ _, map ->
            log("ambient switch caps ${map.map { "${it.key.values.toList()}=${it.value}" }}")
            for (area in map.keys) {
                light.getAmbientLightSwitch(area, { log("ambient switch ${area.values.toList()} = $it") }, fail("ambient switch"))
            }
        }, fail("ambient switch caps"))

        v.getOdometer().getRangeRemainingDistance({ log("range remaining = $it") }, fail("range remaining"))
        v.getDrivingMode().getCruiseControlState({ log("cruise = $it") }, fail("cruise"))
        v.getDrivingMode().getCurrentDrivingModeState({ log("driving mode = $it") }, fail("driving mode"))
    }

    // ---------------------------------------------------------------- 실내 조명 제어

    private fun ambient(app: Context, bright: Int, color: Int, switch: String?) {
        val light = vehicle(app).getLight()
        light.checkAmbientLightBrightnessCapabilities({ _, map ->
            val areas = map.keys.ifEmpty { setOf(SeatArea(SeatValue.ROW_1_LEFT)) }
            log("ambient control areas ${areas.map { it.values.toList() }}")
            for (area in areas) {
                if (switch != null) {
                    light.setAmbientLightSwitch(area, LightSwitch.valueOf(switch), { _, v -> log("ambient switch set -> $v") }, fail("ambient switch set"))
                }
                if (bright >= 0) {
                    light.setAmbientLightBrightness(area, bright, { _, v -> log("ambient bright set -> $v") }, fail("ambient bright set"))
                }
                if (color >= 0) {
                    light.setAmbientLightBrightnessColor(area, color, { _, v -> log("ambient color set -> $v") }, fail("ambient color set"))
                }
            }
        }, fail("ambient control caps"))
    }

    // ---------------------------------------------------------------- 차의 목소리

    private fun speak(app: Context, text: String, modeName: String) {
        tts?.let { runCatching { it.release() } }
        val created = TextToSpeech(app, TtsMode.valueOf(modeName))
        tts = created
        log("tts init request mode=$modeName")
        created.initialize(object : TtsInit {
            override fun onSuccess() {
                log("tts init ok")
                created.addEventListener(object : EventListener {
                    private var rmsSeen = false
                    override fun onReady() = log("tts onReady")
                    override fun onStart() = log("tts onStart")
                    override fun onDone() = log("tts onDone")
                    override fun onStop() = log("tts onStop")
                    override fun onError(errorType: TtsError) = log("tts onError $errorType")
                    override fun onUpdatedRms(rms: Double) {
                        if (!rmsSeen) {
                            rmsSeen = true
                            log("tts first rms $rms")
                        }
                    }
                })
                created.setLocale(Locale.KOREA)
                created.speak(text, true)
                log("tts speak requested (${text.length} chars)")
            }

            override fun onFailure(errorType: TtsError) = log("tts init FAIL $errorType")
        })
    }

    // ---------------------------------------------------------------- 받아쓰기

    private fun listen(app: Context, modeName: String, pcm: String?) {
        stt?.let { runCatching { it.release() } }
        val created = SpeechToText(app, SttMode.valueOf(modeName))
        stt = created
        log("stt init request mode=$modeName pcm=$pcm")
        created.initialize(object : SttInit {
            override fun onSuccess() {
                log("stt init ok")
                created.addListener(object : ResultListener {
                    private var rmsSeen = false
                    override fun onReady() = log("stt onReady")
                    override fun onStartedRecognition() = log("stt onStartedRecognition")
                    override fun onEndedRecognition() = log("stt onEndedRecognition")
                    override fun onUpdated(stt: String, completed: Boolean) = log("stt onUpdated completed=$completed text=[$stt]")
                    override fun onUpdatedEpdData(on: Long, off: Long) = log("stt epd on=$on off=$off")
                    override fun onError(errorType: SttError) = log("stt onError $errorType")
                    override fun onUpdatedRms(rms: Double) {
                        if (!rmsSeen) {
                            rmsSeen = true
                            log("stt first rms $rms")
                        }
                    }
                })
                created.setLocale(Locale.KOREA)
                if (pcm == null) {
                    created.request()
                    log("stt request (mic)")
                } else {
                    sendPcm(app, created, pcm)
                }
            }

            override fun onFailure(errorType: SttError) = log("stt init FAIL $errorType")
        })
    }

    /** 마이크 없이 받아쓰기를 확인한다. 16kHz 16bit mono PCM을 100ms(3,200바이트)씩 보낸다(문서 예시와 같은 방식). */
    private fun sendPcm(app: Context, stt: SpeechToText, relativePath: String) {
        val file = File(app.filesDir.parentFile, relativePath)
        if (!file.exists()) {
            log("stt pcm not found: ${file.path}")
            return
        }
        thread(name = "probe-pcm") {
            val bytes = file.readBytes()
            log("stt sending pcm ${bytes.size} bytes")
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + PCM_CHUNK, bytes.size)
                stt.sendAudio(bytes.copyOfRange(offset, end))
                offset = end
                Thread.sleep(100)
            }
            stt.completeAudioSend()
            log("stt pcm send complete")
        }
    }

    private fun fail(name: String): (Exception) -> Unit = { e -> Log.w(TAG, "$name FAIL ${e.javaClass.simpleName}: ${e.message}") }

    private companion object {
        const val TAG = "Chatoon/Probe"
        const val PCM_CHUNK = 3_200
        var vehicle: Vehicle? = null
        var tts: TextToSpeech? = null
        var stt: SpeechToText? = null

        fun vehicle(app: Context): Vehicle = vehicle ?: Vehicle(app).also {
            it.initialize()
            vehicle = it
        }

        fun log(message: String) {
            Log.i(TAG, message)
        }
    }
}
