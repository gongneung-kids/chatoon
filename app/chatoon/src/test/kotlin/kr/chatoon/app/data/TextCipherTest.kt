package kr.chatoon.app.data

import kr.chatoon.app.data.storage.AesGcmTextCipher
import kr.chatoon.app.data.storage.CipherUnavailableException
import kr.chatoon.app.data.storage.TextCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCipherTest {

    private val cipher = TestCiphers.aes()

    @Test fun `round trip keeps Korean text and tabs`() {
        val plain = "T2\t1\t2\t운전\tTPMS,ABS"
        val sealed = cipher.seal(plain)
        assertTrue(sealed.startsWith(AesGcmTextCipher.PREFIX))
        assertTrue('\n' !in sealed)
        assertEquals(TextCipher.Opened.Sealed(plain), cipher.open(sealed))
    }

    @Test fun `empty line round trips`() {
        assertEquals(TextCipher.Opened.Sealed(""), cipher.open(cipher.seal("")))
    }

    @Test fun `same text seals differently each time (fresh IV)`() {
        val a = cipher.seal("same")
        val b = cipher.seal("same")
        assertNotEquals(a, b)
        assertNotEquals(a.substring(3, 19), b.substring(3, 19))
    }

    @Test fun `unprefixed line is plain`() {
        assertEquals(TextCipher.Opened.Plain("T2\t1"), cipher.open("T2\t1"))
    }

    @Test fun `broken base64, too short or tampered lines are corrupt`() {
        assertEquals(TextCipher.Opened.Corrupt, cipher.open("E1:%%%"))
        assertEquals(TextCipher.Opened.Corrupt, cipher.open("E1:AAAA"))
        val sealed = cipher.seal("hello")
        val bytes = java.util.Base64.getDecoder().decode(sealed.substring(3))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertEquals(TextCipher.Opened.Corrupt, cipher.open("E1:" + java.util.Base64.getEncoder().encodeToString(bytes)))
        assertEquals(TextCipher.Opened.Corrupt, TestCiphers.aes().open(sealed))
    }

    @Test fun `unavailable key throws instead of reporting corruption`() {
        val sealed = cipher.seal("hello")
        assertThrows(CipherUnavailableException::class.java) { TestCiphers.unavailable.open(sealed) }
        assertThrows(CipherUnavailableException::class.java) { TestCiphers.unavailable.seal("hello") }
    }

    @Test fun `multi line input is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { cipher.seal("a\nb") }
    }
}
