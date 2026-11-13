package kr.chatoon.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 앱 안 오픈소스 고지가 배포 의무를 지키는지 검사한다.
 * - 라이선스 전문은 Apache 공식 원문(https://www.apache.org/licenses/LICENSE-2.0.txt)과 바이트까지 같아야 한다.
 * - 고지 목록과 README의 출처 표가 어긋나지 않아야 한다.
 */
class OpenSourceNoticeTest {

    private val moduleDir = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "src/main/assets").isDirectory }
    private val repoDir = moduleDir.parentFile.parentFile

    @Test fun `bundled license is the official Apache 2_0 text`() {
        val bytes = File(moduleDir, "src/main/assets/$ApacheLicenseAsset").readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30", sha256)
    }

    @Test fun `every component has a name and a source url`() {
        assertTrue(OpenSourceComponents.isNotEmpty())
        OpenSourceComponents.forEach {
            assertTrue(it.name, it.name.isNotBlank())
            assertTrue(it.url, it.url.matches(Regex("https?://\\S+")))
        }
        assertEquals(OpenSourceComponents.size, OpenSourceComponents.map { it.name }.toSet().size)
    }

    @Test fun `readme names every bundled component`() {
        val readme = File(repoDir, "README.md").readText()
        listOf("Jetpack Compose", "Kotlin", "kotlinx.coroutines", "kotlinx.serialization", "Gson", "Dagger", "javax.inject", "JSR-305", "JetBrains annotations", "JSpecify", "Guava listenablefuture")
            .forEach { assertTrue("README에 $it 출처가 없음", readme.contains(it)) }
    }
}
