package kr.chatoon.app.data.consent

import kr.chatoon.app.data.storage.DurableFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

sealed interface ConsentStatus {
    /** 저장된 동의를 아직 읽지 못했다. 이 동안에는 동의 화면도, 기록도 띄우지 않는다. */
    data object Loading : ConsentStatus

    /**
     * 동의가 필요하다. [termsUpdated]면 예전에 동의했지만 안내 내용이 바뀐 경우다.
     * [unreadable]이면 동의 파일이 있는데 읽지 못했거나 깨졌다. 파일이 없는 경우(철회가 끊긴 경우)와 달리 기록을 지우지 않는다.
     */
    data class Required(val termsUpdated: Boolean, val unreadable: Boolean = false) : ConsentStatus

    data class Granted(val acceptedAtMillis: Long) : ConsentStatus
}

/**
 * 운전 기록 동의를 파일 하나에 저장한다. 내용: `버전<TAB>동의시각`.
 *
 * 동의 안내([CURRENT_VERSION])가 바뀌면 예전 동의는 효력이 없고 다시 동의를 받는다.
 * 모든 메서드는 파일 I/O를 하므로 메인 스레드 밖에서 부른다.
 */
class ConsentStore(private val file: File, private val clock: () -> Long) {

    private val _status = MutableStateFlow<ConsentStatus>(ConsentStatus.Loading)
    val status: StateFlow<ConsentStatus> = _status.asStateFlow()

    @Synchronized
    fun load() {
        _status.value = when (val saved = read()) {
            Saved.Missing -> ConsentStatus.Required(termsUpdated = false)
            Saved.Unreadable -> ConsentStatus.Required(termsUpdated = false, unreadable = true)
            is Saved.Ok -> if (saved.version == CURRENT_VERSION) {
                ConsentStatus.Granted(saved.acceptedAtMillis)
            } else {
                ConsentStatus.Required(termsUpdated = true)
            }
        }
    }

    /** [atMillis]: 동의 시각. 앱에서는 늘 지금이고, 시연 데이터(debug)만 첫 기록 앞으로 맞출 때 지정한다. */
    @Synchronized
    fun grant(atMillis: Long = clock()): Boolean {
        val now = atMillis
        try {
            DurableFile.replace(file, "$CURRENT_VERSION\t$now\n")
        } catch (e: IOException) {
            // 저장하지 못한 동의로 기록을 모으면, 다음 실행에 동의 파일이 없어 그 기록이 '철회가 끊긴 기록'으로 지워진다.
            // 그래서 동의한 것으로 보지 않고 동의 화면에 남겨 둔다(다시 누르면 다시 저장을 시도한다).
            Log.e(TAG, "Failed to save consent; not recording", e)
            return false
        }
        _status.value = ConsentStatus.Granted(now)
        return true
    }

    @Synchronized
    fun revoke() {
        if (file.exists() && !file.delete()) Log.e(TAG, "Failed to delete consent file")
        _status.value = ConsentStatus.Required(termsUpdated = false)
    }

    private sealed interface Saved {
        data object Missing : Saved
        data object Unreadable : Saved
        data class Ok(val version: Int, val acceptedAtMillis: Long) : Saved
    }

    /** 저장된 동의. 파일이 없으면 [Saved.Missing], 있는데 읽지 못하거나 깨졌으면 [Saved.Unreadable](기록을 지우지 않는 쪽). */
    private fun read(): Saved {
        if (!file.exists()) return Saved.Missing
        return try {
            val parts = file.readText().trim().split('\t')
            if (parts.size != 2) return Saved.Unreadable
            val version = parts[0].toIntOrNull() ?: return Saved.Unreadable
            val acceptedAt = parts[1].toLongOrNull() ?: return Saved.Unreadable
            Saved.Ok(version, acceptedAt)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read consent", e)
            Saved.Unreadable
        }
    }

    companion object {
        /**
         * 동의 화면에서 안내하는 수집 항목·보관 방식·전달·차량 제어가 바뀌면 올린다(수집만 줄어들 때는 올리지 않아도 된다).
         */
        const val CURRENT_VERSION = 10 // 10: 주인의 답을 대사로 옮기지 않고 근거 줄에만 남긴다는 설명을 바로잡음. 9: 문 잠금·방향지시등 수집을 빼고 통풍 단계를 더함(2026-11-13). 8: 창문·공조 설정 온도와 풍량·운전석 열선(2026-11-13). 7: 실내 조명 연출(켠 사람만, 주차 중에만). 6: 6: 차툰(주인의 답 저장, 말로 답하기 마이크, 나이 조건 없이 속도로 횟수 세기)
        private const val TAG = "Chatoon/Consent"
    }
}
