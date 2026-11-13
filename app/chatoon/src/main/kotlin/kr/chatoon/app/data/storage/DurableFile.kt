package kr.chatoon.app.data.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * 갑자기 전원이 끊겨도(시동을 끄는 순간 등) 파일이 비거나 반쯤 남지 않게 쓴다.
 *
 * - [replace]: 임시 파일에 다 쓰고 디스크에 동기화(fsync)한 뒤 이름을 바꾼다. 이름 바꾸기는 같은 폴더 안에서 원자적이라
 *   읽는 쪽은 예전 내용이나 새 내용 둘 중 하나만 본다. 폴더도 동기화해 이름 바꾸기 자체가 디스크에 남게 한다.
 * - [append]: 덧붙이고 동기화한다.
 *
 * 실패하면 IOException을 던진다. 호출하는 쪽이 로그와 복구를 맡는다.
 */
internal object DurableFile {

    fun tmpOf(target: File): File = File(target.parentFile, target.name + ".tmp")

    @Throws(IOException::class)
    fun replace(target: File, content: String) {
        target.parentFile?.mkdirs()
        val tmp = tmpOf(target)
        FileOutputStream(tmp).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) throw IOException("rename failed: ${tmp.name} -> ${target.name}")
        syncDirectory(target.parentFile)
    }

    @Throws(IOException::class)
    fun append(target: File, content: String) {
        target.parentFile?.mkdirs()
        FileOutputStream(target, true).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
    }

    /** 폴더 항목(이름 바꾸기)을 디스크에 남긴다. 지원하지 않는 파일 시스템이면 조용히 넘어간다(파일 내용은 이미 동기화됨). */
    private fun syncDirectory(dir: File?) {
        if (dir == null) return
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
        } catch (_: UnsupportedOperationException) {
        }
    }
}
