package kr.chatoon.app.data.cartoon

import android.util.Log
import kr.chatoon.app.data.storage.CipherUnavailableException
import kr.chatoon.app.data.storage.DurableFile
import kr.chatoon.app.data.storage.TextCipher
import kr.chatoon.app.domain.cartoon.AnswerBook
import kr.chatoon.app.domain.cartoon.AnswerBookCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * 주인이 차의 질문에 답한 말을 파일 하나에 저장한다. 형식은 [AnswerBookCodec].
 *
 * 주인의 말은 어디에 갔는지 같은 사생활이 담길 수 있으므로 주행 기록과 같은 키로 암호화한다.
 * 동의한 동안에만 쓸 수 있고([setEnabled]), 동의를 철회하면 기록과 함께 지운다([eraseAll]).
 * 키를 쓸 수 없어 읽지 못하면 그 실행 동안 파일을 덮어쓰지 않는다. 모든 메서드는 파일 I/O를 하므로 메인 스레드 밖에서 부른다.
 */
class AnswerStore(private val file: File, private val cipher: TextCipher) {

    private var enabled = false
    private var locked = false

    private val _book = MutableStateFlow(AnswerBook())
    val book: StateFlow<AnswerBook> = _book.asStateFlow()

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    @Synchronized
    fun load() {
        locked = false
        _book.value = try {
            if (!file.exists()) {
                AnswerBook()
            } else {
                // 줄마다 따로 암호화돼 있다(암호화는 한 줄씩만 한다). 읽지 못하는 줄은 버린다.
                val lines = file.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                    when (val opened = cipher.open(line.trim())) {
                        is TextCipher.Opened.Sealed -> opened.text
                        is TextCipher.Opened.Plain -> opened.text
                        TextCipher.Opened.Corrupt -> null.also { Log.w(TAG, "Discarding an unreadable answer line") }
                    }
                }
                AnswerBookCodec.decode(lines.joinToString("\n"))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read answers", e)
            AnswerBook()
        } catch (e: CipherUnavailableException) {
            Log.e(TAG, "Encryption key unavailable; leaving answers untouched this run", e)
            locked = true
            AnswerBook()
        }
    }

    @Synchronized
    fun answer(tripStartMillis: Long, jolt: Boolean, said: String) = save(_book.value.answer(tripStartMillis, jolt, said))

    @Synchronized
    fun skip(tripStartMillis: Long) = save(_book.value.skip(tripStartMillis))

    @Synchronized
    fun eraseAll() {
        enabled = false
        locked = false
        for (f in listOf(file, DurableFile.tmpOf(file))) {
            if (f.exists() && !f.delete()) Log.e(TAG, "Failed to delete ${f.name}")
        }
        _book.value = AnswerBook()
    }

    private fun save(book: AnswerBook) {
        if (!enabled || locked) return
        // 저장에 실패해도 이번 실행 동안은 답한 대로 보이게 먼저 바꾼다.
        _book.value = book
        try {
            DurableFile.replace(file, AnswerBookCodec.encode(book).lines().joinToString("") { cipher.seal(it) + "\n" })
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save answers", e)
        } catch (e: CipherUnavailableException) {
            Log.e(TAG, "Encryption key unavailable; answers not saved", e)
        }
    }

    private companion object {
        const val TAG = "Chatoon/Answers"
    }
}
