package kr.chatoon.app.data.storage

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 저장하는 글 한 줄을 암호화한다(Pleos 앱 보안 가이드: 기기에 저장하는 민감정보는 앱 수준에서 암호화, 필수 기준).
 *
 * 한 줄씩 따로 봉인하므로 주행 기록처럼 "한 줄씩 덧붙이는" 파일에도 그대로 쓸 수 있다. 줄 형식: `E1:` + Base64(IV 12바이트 + 암호문 + 인증 태그).
 */
interface TextCipher {

    /** [plain](줄바꿈 없는 한 줄)을 봉인한 한 줄. 키를 쓸 수 없으면 [CipherUnavailableException]. */
    fun seal(plain: String): String

    /**
     * 봉인된 한 줄을 연다.
     * - 봉인 형식이 아니면(암호화 전에 저장한 줄) [Opened.Plain]으로 그대로 돌려준다 — 다시 쓸 때 봉인한다.
     * - 형식은 맞는데 열 수 없으면(깨짐·변조) [Opened.Corrupt].
     * - 키를 쓸 수 없으면 [CipherUnavailableException]. 이때 호출하는 쪽은 파일을 덮어쓰면 안 된다(복구할 수 없게 지워질 수 있음).
     */
    fun open(line: String): Opened

    sealed interface Opened {
        data class Sealed(val text: String) : Opened
        data class Plain(val text: String) : Opened
        data object Corrupt : Opened
    }
}

/** 키 저장소를 지금 쓸 수 없다(잠김·일시 오류 등). 데이터가 깨진 것과 구분한다. */
class CipherUnavailableException(cause: Throwable) : RuntimeException(cause)

/**
 * AES-256-GCM(Pleos 가이드 권장: 검증된 인증 암호 모드, IV 재사용 금지, 안전한 난수, 키는 Keystore로 보호).
 * 키는 [key]가 줄 때마다 받아 쓴다. 앱에서는 Keystore 키로 감싼 데이터 키([WrappedDataKey])를, 단위 테스트에서는 메모리 키를 넣는다.
 */
class AesGcmTextCipher(
    private val key: () -> SecretKey,
    private val random: SecureRandom = SecureRandom(),
) : TextCipher {

    /**
     * `Cipher.getInstance`는 제공자를 찾느라 느려(에뮬레이터에서 기록 3천 줄을 읽을 때 1초 이상 차이) 한 개를 만들어 다시 쓴다.
     * Cipher는 여러 스레드가 함께 쓸 수 없으므로 seal/open을 잠금으로 묶는다(주행 기록·설정 저장이 같은 객체를 쓴다).
     */
    private var reusable: Cipher? = null

    private fun cipher(): Cipher = reusable ?: Cipher.getInstance(TRANSFORMATION).also { reusable = it }

    @Synchronized
    override fun seal(plain: String): String {
        require('\n' !in plain) { "one line only" }
        val secret = keyOrThrow()
        try {
            // 줄마다 안전한 난수로 새 IV(96비트)를 만든다. 같은 키로 IV가 겹칠 확률은 2^-96 수준이다.
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val cipher = cipher()
            cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, iv))
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return PREFIX + Base64.getEncoder().encodeToString(iv + body)
        } catch (e: GeneralSecurityException) {
            throw CipherUnavailableException(e)
        }
    }

    @Synchronized
    override fun open(line: String): TextCipher.Opened {
        if (!line.startsWith(PREFIX)) return TextCipher.Opened.Plain(line)
        val bytes = try {
            Base64.getDecoder().decode(line.substring(PREFIX.length))
        } catch (_: IllegalArgumentException) {
            return TextCipher.Opened.Corrupt
        }
        if (bytes.size < IV_BYTES + TAG_BITS / 8) return TextCipher.Opened.Corrupt
        val secret = keyOrThrow()
        return try {
            val cipher = cipher()
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            val plain = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
            TextCipher.Opened.Sealed(String(plain, Charsets.UTF_8))
        } catch (_: AEADBadTagException) {
            TextCipher.Opened.Corrupt
        } catch (e: GeneralSecurityException) {
            throw CipherUnavailableException(e)
        }
    }

    private fun keyOrThrow(): SecretKey = try {
        key()
    } catch (e: CipherUnavailableException) {
        throw e
    } catch (e: Exception) {
        throw CipherUnavailableException(e)
    }

    companion object {
        const val PREFIX = "E1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
