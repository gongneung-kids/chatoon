package kr.chatoon.app.data

import kr.chatoon.app.data.storage.AesGcmTextCipher
import kr.chatoon.app.data.storage.CipherUnavailableException
import kr.chatoon.app.data.storage.TextCipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** 단위 테스트용 암호화. 실제 앱과 같은 AES-256-GCM을 쓰고, 키만 Android Keystore 대신 메모리에 둔다. */
object TestCiphers {
    fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    fun aes(key: SecretKey = newKey()): TextCipher = AesGcmTextCipher(key = { key })

    /** 키 저장소가 잠긴 상황: 무엇을 하든 [CipherUnavailableException]. */
    val unavailable: TextCipher = AesGcmTextCipher(key = { throw CipherUnavailableException(IllegalStateException("locked")) })
}
