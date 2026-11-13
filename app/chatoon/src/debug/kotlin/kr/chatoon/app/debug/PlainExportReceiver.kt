package kr.chatoon.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.chatoon.app.ChatoonApplication
import kr.chatoon.app.data.storage.TextCipher
import java.io.File
import kotlin.concurrent.thread

/**
 * 암호화된 기록 파일을 풀어서 캐시 폴더에 내보낸다(debug 빌드 전용, adb 셸만 호출 가능). 검증 도구(tools/vc.py)가 쓴다.
 *
 * ```
 * adb shell am broadcast --user 10 -a kr.chatoon.app.EXPORT_PLAIN \
 *     -n kr.chatoon.app/.debug.PlainExportReceiver --es name drive-log.tsv
 * adb shell run-as kr.chatoon.app --user 10 cat cache/plain/drive-log.tsv
 * ```
 * 읽은 쪽이 바로 지운다. 파일이 없으면 내보낸 파일도 지운다(없음을 알리기 위해).
 */
class PlainExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME)
        if (name !in EXPORTABLE) {
            Log.w(TAG, "Not exportable: $name")
            resultCode = 2
            return
        }
        val container = (context.applicationContext as ChatoonApplication).container
        val pending = goAsync()
        thread(name = "chatoon-export") {
            try {
                val source = File(context.filesDir, name!!)
                val target = File(context.cacheDir, "plain/$name").apply { parentFile?.mkdirs() }
                if (!source.exists()) {
                    target.delete()
                    pending.resultCode = 1
                    return@thread
                }
                val plain = source.readLines().filter { it.isNotBlank() }.joinToString("") { line ->
                    when (val opened = container.cipher.open(line.trim())) {
                        // 주행 기록은 여러 기록을 한 줄에 묶어 암호화한다(레코드 구분자 U+001E). 한 줄에 하나씩 풀어 쓴다.
                        is TextCipher.Opened.Sealed -> opened.text.replace('\u001E', '\n') + "\n"
                        is TextCipher.Opened.Plain -> opened.text + "\n"
                        TextCipher.Opened.Corrupt -> "#CORRUPT\n"
                    }
                }
                target.writeText(plain)
                pending.resultCode = 0
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                pending.resultCode = 3
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Chatoon/Export"
        const val EXTRA_NAME = "name"
        val EXPORTABLE = setOf("drive-log.tsv", "drive-log.tsv.checkpoint", "profile.txt")
    }
}
