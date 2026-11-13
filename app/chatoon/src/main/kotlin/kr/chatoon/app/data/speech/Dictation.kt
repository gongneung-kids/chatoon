package kr.chatoon.app.data.speech

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 말로 답하기. Pleos SpeechToText를 기기 안(ON_DEVICE) 모드로만 쓴다: 목소리가 차 밖으로 나가지 않는다.
 * 마이크는 주인이 버튼을 눌렀을 때만, 한 문장을 받아쓰는 동안만 켠다(늘 켜 둔 마이크는 쓰지 않는다).
 *
 * [testAudio]가 있으면(debug 빌드에서만 넘긴다) 마이크 대신 그 파일(16kHz 16bit mono PCM)을 보낸다. 에뮬레이터 확인용.
 */
class Dictation(private val context: Context, private val testAudio: File? = null) {

    sealed interface State {
        data object Idle : State
        data object Starting : State
        /** 듣는 중. [heard]는 지금까지 받아쓴 글. */
        data class Listening(val heard: String) : State
        data class Done(val text: String) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var stt: SpeechToText? = null

    @Synchronized
    fun start() {
        if (_state.value is State.Starting || _state.value is State.Listening) return
        _state.value = State.Starting
        val engine = stt ?: try {
            SpeechToText(context, Mode.ON_DEVICE).also { stt = it }
        } catch (e: Exception) {
            Log.w(TAG, "SpeechToText unavailable", e)
            _state.value = State.Failed
            return
        }
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                engine.addListener(listener)
                engine.setLocale(Locale.KOREA)
                val audio = testAudio?.takeIf { it.canRead() }
                if (audio == null) engine.request() else feed(engine, audio)
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "SpeechToText init failed: $errorType")
                _state.value = State.Failed
            }
        })
    }

    /** 듣기를 멈춘다. 그때까지 받아쓴 글이 있으면 그것으로 끝낸다. */
    @Synchronized
    fun stop() {
        runCatching { stt?.stop() }
        val now = _state.value
        _state.value = if (now is State.Listening && now.heard.isNotBlank()) State.Done(now.heard.trim()) else State.Idle
    }

    /** 결과를 화면이 가져간 뒤 처음 상태로. */
    fun reset() {
        if (_state.value is State.Done || _state.value is State.Failed) _state.value = State.Idle
    }

    @Synchronized
    fun release() {
        stt?.let { engine ->
            runCatching { engine.removeListener(listener) }
            runCatching { engine.release() }
        }
        stt = null
        _state.value = State.Idle
    }

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() {
            if (_state.value is State.Starting) _state.value = State.Listening("")
        }
        override fun onEndedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            if (_state.value is State.Idle) return // 이미 멈췄다
            _state.value = if (completed) State.Done(stt.trim()) else State.Listening(stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) {
            if (_state.value is State.Starting) _state.value = State.Listening("")
        }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "SpeechToText error: $errorType")
            val now = _state.value
            _state.value = if (now is State.Listening && now.heard.isNotBlank()) State.Done(now.heard.trim()) else State.Failed
        }
    }

    /** 문서 예시와 같이 100ms(3,200바이트)씩 보낸다. */
    private fun feed(engine: SpeechToText, audio: File) {
        _state.value = State.Listening("")
        thread(name = "dictation-test-audio", isDaemon = true) {
            val bytes = audio.readBytes()
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + CHUNK, bytes.size)
                engine.sendAudio(bytes.copyOfRange(offset, end))
                offset = end
                Thread.sleep(100)
            }
            engine.completeAudioSend()
        }
    }

    private companion object {
        const val TAG = "Chatoon/Dictation"
        const val CHUNK = 3_200
    }
}
