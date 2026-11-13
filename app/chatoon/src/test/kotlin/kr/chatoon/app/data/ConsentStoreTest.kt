package kr.chatoon.app.data

import kr.chatoon.app.data.consent.ConsentStatus
import kr.chatoon.app.data.consent.ConsentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConsentStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "consent.txt")
    private fun store(now: Long = 1_000L) = ConsentStore(file, clock = { now })

    @Test fun `first run asks for consent`() {
        val store = store()
        assertEquals(ConsentStatus.Loading, store.status.value)
        store.load()
        assertEquals(ConsentStatus.Required(termsUpdated = false), store.status.value)
    }

    @Test fun `granted consent survives restart`() {
        store(now = 42L).grant()
        val reloaded = store().apply { load() }
        assertEquals(ConsentStatus.Granted(42L), reloaded.status.value)
    }

    @Test fun `consent to older terms must be renewed`() {
        file.writeText("${ConsentStore.CURRENT_VERSION - 1}\t42\n")
        val store = store().apply { load() }
        assertEquals(ConsentStatus.Required(termsUpdated = true), store.status.value)
    }

    @Test fun `a corrupted or unreadable file asks again but is not the same as no file`() {
        // ee 지적 3: 깨진 파일을 "동의 없음(파일 없음)"과 같게 보면, 시작할 때 '철회가 끊긴 것'으로 여겨 기록을 지운다.
        for (content in listOf("", "abc", "1", "1\tnot-a-time", "x\t42")) {
            file.writeText(content)
            val store = store().apply { load() }
            assertEquals(content, ConsentStatus.Required(termsUpdated = false, unreadable = true), store.status.value)
        }
        file.writeText("${ConsentStore.CURRENT_VERSION}\t42\n")
        assertTrue(file.setReadable(false))
        assertEquals(ConsentStatus.Required(termsUpdated = false, unreadable = true), store().apply { load() }.status.value)
        assertTrue(file.setReadable(true))
    }

    @Test fun `consent that could not be saved is not treated as given`() {
        // 저장에 실패한 동의로 기록을 모으면 다음 실행에 동의 파일이 없어 그 기록이 지워진다. 처음부터 기록하지 않는다.
        val readOnly = File(tmp.root, "read-only").apply { mkdirs() }
        assertTrue(readOnly.setWritable(false)) // 쓸 수 없는 폴더 → 저장 실패(저장 장치 오류 흉내)
        val store = ConsentStore(File(readOnly, "consent.txt"), clock = { 1_000L }).apply { load() }
        assertFalse(store.grant()) // 화면이 실패를 알릴 수 있게 결과를 돌려준다(ee 검수 A3①)
        assertTrue(store.status.value is ConsentStatus.Required)
        assertTrue(readOnly.setWritable(true))
    }

    @Test fun `revoke deletes the file`() {
        val store = store().apply { grant() }
        store.revoke()
        assertFalse(file.exists())
        assertEquals(ConsentStatus.Required(termsUpdated = false), store.status.value)
        assertEquals(ConsentStatus.Required(termsUpdated = false), store().apply { load() }.status.value)
    }
}
