package kr.chatoon.app.data.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 기록 데이터 키([WrappedDataKey])를 감싸는 키. Android Keystore 안에서 만들고 꺼내지 않는다
 * (Pleos 앱 보안 가이드: 키는 Keystore로 보호, 하드코딩 금지). 앱을 지우면 함께 지워져, 남은 파일이 있어도 열 수 없다.
 */
object AndroidKeystoreKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "chatoon.records.v1"

    /** 키 저장소 조회는 IPC라서 한 번 얻은 키 핸들을 잡아 둔다(키 자료는 Keystore 밖으로 나오지 않는다). */
    @Volatile
    private var cached: SecretKey? = null

    @Synchronized
    fun getOrCreate(): SecretKey {
        cached?.let { return it }
        return loadOrGenerate().also { cached = it }
    }

    private fun loadOrGenerate(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
