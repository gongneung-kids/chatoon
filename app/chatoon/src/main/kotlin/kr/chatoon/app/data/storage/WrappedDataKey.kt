package kr.chatoon.app.data.storage

import android.util.Log
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 기록을 암호화하는 데이터 키. 키 자체는 [wrappingKey](Android Keystore 키)로 감싸서 [file]에 둔다(봉투 암호화).
 *
 * Keystore 연산은 한 번에 수 ms가 걸려, 기록 수천 줄을 줄마다 Keystore로 풀면 앱 시작이 수 초 늦어진다(에뮬레이터 실측 3~4 ms/줄).
 * 그래서 Keystore는 시작할 때 데이터 키를 푸는 데 한 번만 쓰고, 줄 암호화는 메모리의 데이터 키로 한다(Jetpack Security와 같은 구조).
 *
 * - 키 파일이 없거나, 깨졌거나, 지금 Keystore 키로 풀리지 않으면 새 키를 만든다. 단 이 키로 암호화한 기록이 남아 있으면
 *   ([hasSealedData]) 만들지 않고 [CipherUnavailableException]을 던진다. 새 키로 다시 쓰는 순간 남은 기록을 영영 열 수 없게 되기 때문이다
 *   (지시 A6). 저장소는 그 실행 동안 파일을 건드리지 않고, 운전자가 기록을 모두 지우면 그다음부터 새 키를 만든다.
 * - Keystore를 잠시 쓸 수 없으면 [CipherUnavailableException]을 던지고 키 파일은 건드리지 않는다.
 * - 새 키를 파일에 저장하지 못하면 그 키로 암호화하지 않는다(다음 실행에서 열 수 없는 기록이 생기므로).
 */
class WrappedDataKey(
    private val file: File,
    private val wrappingKey: () -> SecretKey,
    private val random: SecureRandom = SecureRandom(),
    /** 이 데이터 키로 암호화한 기록이 저장소에 남아 있는가. 키를 새로 만들어도 되는지 판단할 때만 부른다. */
    private val hasSealedData: () -> Boolean = { false },
) {
    @Volatile
    private var cached: SecretKey? = null

    @Synchronized
    fun get(): SecretKey {
        cached?.let { return it }
        return try {
            (unwrapExisting() ?: createIfNothingSealed()).also { cached = it }
        } catch (e: CipherUnavailableException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw CipherUnavailableException(e)
        } catch (e: IOException) {
            throw CipherUnavailableException(e)
        } catch (e: RuntimeException) { // Keystore 서비스 오류(ProviderException 등)
            throw CipherUnavailableException(e)
        }
    }

    /** 저장된 키. 파일이 없거나, 형식이 깨졌거나, 감싼 키가 지금 Keystore 키로 풀리지 않으면 null. */
    private fun unwrapExisting(): SecretKey? {
        if (!file.exists()) return null
        val line = file.readText().trim()
        val bytes = line.takeIf { it.startsWith(PREFIX) }?.let {
            try {
                Base64.getDecoder().decode(it.substring(PREFIX.length))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        if (bytes == null || bytes.size != IV_BYTES + KEY_BYTES + TAG_BITS / 8) {
            Log.w(TAG, "Record key file is unreadable")
            return null
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return try {
            SecretKeySpec(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), "AES")
        } catch (_: AEADBadTagException) {
            Log.w(TAG, "Record key no longer matches the Keystore key")
            null
        }
    }

    private fun createIfNothingSealed(): SecretKey {
        if (hasSealedData()) throw CipherUnavailableException(IllegalStateException("record key is missing or damaged but sealed records remain"))
        return create()
    }

    private fun create(): SecretKey {
        val raw = ByteArray(KEY_BYTES).also(random::nextBytes)
        // 키만 주고 초기화하면 제공자가 무작위 IV를 만든다(Android Keystore는 직접 고른 IV를 거부한다).
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        check(iv != null && iv.size == IV_BYTES) { "unexpected GCM IV" }
        val wrapped = iv + cipher.doFinal(raw)
        DurableFile.replace(file, PREFIX + Base64.getEncoder().encodeToString(wrapped) + "\n")
        return SecretKeySpec(raw, "AES")
    }

    private companion object {
        const val TAG = "Chatoon/RecordKey"
        const val PREFIX = "K1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
