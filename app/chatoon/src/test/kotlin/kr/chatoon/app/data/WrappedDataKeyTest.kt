package kr.chatoon.app.data

import kr.chatoon.app.data.storage.AesGcmTextCipher
import kr.chatoon.app.data.storage.CipherUnavailableException
import kr.chatoon.app.data.storage.TextCipher
import kr.chatoon.app.data.storage.WrappedDataKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WrappedDataKeyTest {

    @get:Rule val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "records.key")
    private val wrapping = TestCiphers.newKey()
    private fun key(wrap: javax.crypto.SecretKey = wrapping) = WrappedDataKey(file, { wrap })

    @Test fun `key is created once and reused after restart`() {
        val first = key().get()
        assertTrue(file.readText().startsWith("K1:"))
        val again = key().get()
        assertArrayEquals(first.encoded, again.encoded)
        // 암호문이 다음 실행에서도 열린다.
        val sealed = AesGcmTextCipher({ first }).seal("T2\t1")
        assertEquals(TextCipher.Opened.Sealed("T2\t1"), AesGcmTextCipher({ again }).open(sealed))
    }

    @Test fun `stored file does not contain the raw key`() {
        val raw = key().get().encoded
        val stored = java.util.Base64.getDecoder().decode(file.readText().trim().removePrefix("K1:"))
        assertFalse(stored.toList().windowed(raw.size).any { it == raw.toList() })
    }

    @Test fun `a different keystore key means a new data key`() {
        val first = key().get()
        val second = key(TestCiphers.newKey()).get()
        assertNotEquals(first.encoded.toList(), second.encoded.toList())
        // 새 키가 저장되어 다음 실행에서 그대로 쓰인다.
        assertNotEquals(first.encoded.toList(), key().get().encoded.toList())
    }

    @Test fun `corrupt key file is replaced`() {
        file.writeText("K1:%%%\n")
        key().get()
        assertTrue(file.readText().length > 20)
        file.writeText("garbage\n")
        val k = key().get()
        assertArrayEquals(k.encoded, key().get().encoded)
    }

    @Test fun `a damaged or missing key is never replaced while sealed records remain`() {
        val sealed = { true }
        fun guarded(wrap: javax.crypto.SecretKey = wrapping) = WrappedDataKey(file, { wrap }, hasSealedData = sealed)
        // 깨진 키 파일: 새 키로 덮어쓰면 남은 기록을 영영 못 연다 → 거부하고 파일은 그대로
        file.writeText("K1:%%%\n")
        assertThrows(CipherUnavailableException::class.java) { guarded().get() }
        assertEquals("K1:%%%\n", file.readText())
        // 키 파일이 아예 없음
        file.delete()
        assertThrows(CipherUnavailableException::class.java) { guarded().get() }
        assertFalse(file.exists())
        // Keystore 키가 바뀌어 풀리지 않음
        key().get()
        val before = file.readText()
        assertThrows(CipherUnavailableException::class.java) { guarded(TestCiphers.newKey()).get() }
        assertEquals(before, file.readText())
        // 원래 Keystore 키가 돌아오면 그대로 열린다
        assertArrayEquals(key().get().encoded, guarded().get().encoded)
    }

    @Test fun `after the records are erased a new key may be created`() {
        file.writeText("garbage\n")
        var sealedLeft = true
        val guarded = WrappedDataKey(file, { wrapping }, hasSealedData = { sealedLeft })
        assertThrows(CipherUnavailableException::class.java) { guarded.get() }
        sealedLeft = false // 운전자가 '기록 끄고 모두 삭제'
        guarded.get()
        assertTrue(file.readText().startsWith("K1:"))
    }

    @Test fun `unavailable keystore leaves the key file untouched`() {
        key().get()
        val before = file.readBytes()
        val broken = WrappedDataKey(file, { throw IllegalStateException("keystore down") })
        assertThrows(CipherUnavailableException::class.java) { broken.get() }
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test fun `a key that cannot be saved is never used`() {
        val blocker = File(tmp.root, "not-a-dir").apply { writeText("x") }
        val k = WrappedDataKey(File(blocker, "records.key"), { wrapping })
        assertThrows(CipherUnavailableException::class.java) { k.get() }
    }
}
