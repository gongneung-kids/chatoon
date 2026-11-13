package kr.chatoon.app.data.speech

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 차의 목소리. Pleos TextToSpeech를 기기 안(ON_DEVICE) 모드로만 쓴다(글이 차 밖으로 나가지 않는다).
 * 처음 말할 때 한 번 준비하고, 그 뒤로는 같은 엔진을 쓴다. 준비 중에 들어온 말은 준비가 끝나면 말한다.
 */
class CarVoice(private val context: Context) {

    private val _speaking = MutableStateFlow(false)
    /** 지금 말하는 중인가(화면의 듣기/멈추기 버튼). */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    @Synchronized
    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine != null && ready) {
            say(engine, text)
            return
        }
        pending = text
        if (engine != null) return // 준비 중
        val created = try {
            TextToSpeech(context, Mode.ON_DEVICE)
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech unavailable", e)
            pending = null
            return
        }
        tts = created
        created.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                synchronized(this@CarVoice) {
                    created.addEventListener(listener)
                    created.setLocale(Locale.KOREA)
                    ready = true
                    pending?.let { say(created, it) }
                    pending = null
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "TextToSpeech init failed: $errorType")
                synchronized(this@CarVoice) {
                    tts = null
                    pending = null
                }
            }
        })
    }

    @Synchronized
    fun stop() {
        pending = null
        runCatching { tts?.stop() }
        _speaking.value = false
    }

    @Synchronized
    fun release() {
        pending = null
        tts?.let { engine ->
            runCatching { engine.removeEventListener(listener) }
            runCatching { engine.release() }
        }
        tts = null
        ready = false
        _speaking.value = false
    }

    private fun say(engine: TextToSpeech, text: String) {
        _speaking.value = true
        // 두 번째 인자: 지금 말하던 것을 끊고 새로 말한다.
        engine.speak(text, true)
    }

    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() {
            _speaking.value = true
        }
        override fun onDone() {
            _speaking.value = false
        }
        override fun onStop() {
            _speaking.value = false
        }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "TextToSpeech error: $errorType")
            _speaking.value = false
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private companion object {
        const val TAG = "Chatoon/CarVoice"
    }
}
