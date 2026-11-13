package kr.chatoon.app.data

import kr.chatoon.app.data.cartoon.AnswerStore
import kr.chatoon.app.data.storage.AesGcmTextCipher
import kr.chatoon.app.domain.cartoon.AnswerBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AnswerStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "answers.txt")
    private val key = TestCiphers.newKey()
    private fun store() = AnswerStore(file, TestCiphers.aes(key)).apply { setEnabled(true) }

    @Test fun `여러 주행의 답을 저장하고 다시 읽는다`() {
        val s = store()
        s.answer(100L, jolt = false, said = "면접 보러")
        s.answer(200L, jolt = true, said = "앞차가 갑자기")
        s.skip(300L)
        val again = AnswerStore(file, TestCiphers.aes(key)).apply { load() }
        assertEquals(s.book.value, again.book.value)
        assertEquals("면접 보러", again.book.value.answersFor(100L).why)
        assertTrue(again.book.value.asked(300L))
    }

    @Test fun `파일에는 주인의 말이 평문으로 남지 않는다`() {
        store().answer(1L, jolt = false, said = "병원")
        val text = file.readText()
        assertFalse(text.contains("병원"))
        assertTrue(text.lines().filter { it.isNotBlank() }.all { it.startsWith(AesGcmTextCipher.PREFIX) })
    }

    @Test fun `동의 전과 철회 뒤에는 쓰지 않는다`() {
        val s = AnswerStore(file, TestCiphers.aes(key))
        s.answer(1L, jolt = false, said = "출근")
        assertFalse(file.exists())
        val granted = store()
        granted.answer(1L, jolt = false, said = "출근")
        granted.eraseAll()
        granted.answer(2L, jolt = false, said = "퇴근")
        assertFalse(file.exists())
        assertEquals(AnswerBook(), granted.book.value)
    }

    @Test fun `키를 쓸 수 없으면 기존 파일을 덮어쓰지 않는다`() {
        store().answer(1L, jolt = false, said = "출근")
        val before = file.readText()
        val locked = AnswerStore(file, TestCiphers.unavailable).apply { setEnabled(true); load() }
        locked.answer(2L, jolt = false, said = "퇴근")
        assertEquals(before, file.readText())
    }
}
