package kr.chatoon.app.data

import kr.chatoon.app.data.drive.DriveLogStore
import kr.chatoon.app.domain.drive.DriveLogCodec
import kr.chatoon.app.data.storage.AesGcmTextCipher
import kr.chatoon.app.data.storage.TextCipher
import kr.chatoon.app.domain.drive.RecorderSnapshot
import kr.chatoon.app.domain.drive.Trip
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DriveLogStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val day = 24L * 60 * 60 * 1000
    private val file get() = File(tmp.root, "drive-log.tsv")
    private var now = 500 * day
    private val key = TestCiphers.newKey()

    private fun store(cipher: TextCipher = TestCiphers.aes(key)) =
        DriveLogStore(file, clock = { now }, cipher = cipher, retentionMillis = 100 * day)
    private fun trip(end: Long) = Trip(end - 60_000, end, 1, 0.0)

    @Test fun `nothing is written before recording is enabled`() {
        val store = store().apply { load() }
        store.append(trip(now))
        assertTrue(store.log.value.trips.isEmpty())
        assertFalse(file.exists())
    }

    @Test fun `a read failure never overwrites the file, only appends`() {
        // ee 지적 2: 읽다 IOException이 나면 일부·빈 기록을 정상처럼 두고, 이후 다시 쓰기에서 13개월 기록이 사라질 수 있었다.
        val first = store().apply { load(); setRecording(true) }
        val kept = listOf(trip(now - 30 * day), trip(now - 20 * day), trip(now - 10 * day))
        kept.forEach(first::append)
        assertTrue(file.setReadable(false)) // 읽기 실패(저장 장치 오류) 흉내
        val failed = store().apply { load(); setRecording(true) }
        assertTrue("일부만 읽은 기록으로 계산하지 않는다", failed.log.value.trips.isEmpty())
        assertFalse(failed.replaceAll(emptyList())) // 전체 교체 막음
        failed.amend(kept[0].copy(parkingGearShifts = 9))       // 고치기 막음(다시 쓰기)
        now += 200 * day
        failed.purgeExpired()                                     // 만료 삭제 막음
        now -= 200 * day
        val added = trip(now - 1 * day)
        failed.append(added)                                      // 덧붙이기는 기존 내용을 지우지 않으니 허용
        assertTrue(failed.log.value.unreadable)                   // 화면이 '읽지 못함'을 말할 수 있게
        // 늦게 온 주행거리 보정(ee 결정 A2②): 다시 쓸 수 없으니 고친 줄을 덧붙이고, 읽을 때 뒤의 줄이 이긴다
        val amendedAdded = added.copy(distanceKm = 12.5)
        failed.amend(amendedAdded)
        assertTrue(file.setReadable(true))
        val reloaded = store().apply { load() }.log.value.trips
        assertEquals(kept + amendedAdded, reloaded)               // 같은 출발 시각은 한 번만, 거리는 고친 값
    }

    @Test fun `expired records are purged even while consent is not given`() {
        // ee 검수 A3②: 동의 파일을 읽지 못해 다시 동의를 기다리는 동안(기록 꺼짐)에도 13개월 보관 약속은 지킨다
        val writer = store().apply { load(); setRecording(true) }
        writer.append(trip(now - 90 * day))
        val waiting = store().apply { load() } // setRecording(false) 상태
        now += 30 * day
        assertTrue(waiting.purgeExpired())
        assertTrue(store().apply { load() }.log.value.trips.isEmpty())
    }

    @Test fun `expired records are purged while the app keeps running`() {
        val store = store().apply { load(); setRecording(true) }
        store.append(trip(now - 90 * day)) // 보관 기간(이 테스트에서는 100일) 안
        store.append(trip(now - 10 * day))
        assertFalse("nothing expired yet at load time", store.purgeExpired())
        now += 30 * day // 앱이 꺼지지 않은 채 한 달이 지남
        assertTrue(store.purgeExpired())
        assertEquals(listOf(trip(now - 40 * day)), store.log.value.trips)
        assertEquals("file follows memory", listOf(trip(now - 40 * day)), store().apply { load() }.log.value.trips)
        assertFalse("second purge has nothing to do", store.purgeExpired())
    }

    @Test fun `a failed append is recovered by rewriting the whole file on the next write`() {
        val store = store().apply { load(); setRecording(true) }
        store.append(trip(now - 3 * day))
        // 파일 자리에 폴더가 생겨 덧붙이기가 실패하는 상황(디스크 오류 흉내)
        file.delete()
        file.mkdir()
        store.append(trip(now - 2 * day))
        assertEquals("kept in memory", 2, store.log.value.trips.size)
        file.delete()
        store.append(trip(now - 1 * day))
        val reloaded = store().apply { load() }.log.value.trips
        assertEquals("all three on disk, none half-written", listOf(trip(now - 3 * day), trip(now - 2 * day), trip(now - day)), reloaded)
        assertTrue(file.readText().endsWith("\n"))
    }

    @Test fun `records survive restart`() {
        store().apply { load(); setRecording(true); append(trip(now)) }
        val reloaded = store().apply { load() }
        assertEquals(listOf(trip(now)), reloaded.log.value.trips)
    }

    @Test fun `eraseAll removes everything and stops recording`() {
        val store = store().apply { load(); setRecording(true); append(trip(now)) }
        store.eraseAll()
        assertFalse(file.exists())
        assertTrue(store.log.value.trips.isEmpty())
        store.append(trip(now)) // 철회 뒤 기록기가 늦게 부르더라도 남지 않아야 한다
        assertFalse(file.exists())
        assertTrue(store().apply { load() }.log.value.trips.isEmpty())
    }

    @Test fun `old and broken lines are dropped on load`() {
        store().apply { load(); setRecording(true); append(trip(now - 200 * day)); append(trip(now)) }
        file.appendText("garbage line\n")
        val reloaded = store().apply { load() }
        assertEquals(listOf(trip(now)), reloaded.log.value.trips)
        assertEquals(1, file.readLines().count { it.isNotBlank() })
    }

    private val quarantine get() = File(tmp.root, "drive-log.tsv.quarantine")

    @Test fun `broken lines are moved to quarantine, not deleted`() {
        store().apply { load(); setRecording(true); append(trip(now - day)); append(trip(now)) }
        val sealedTrip = file.readLines().first()
        val damaged = sealedTrip.dropLast(4) + "AAAA" // 인증 실패하는 암호문
        file.appendText(damaged + "\n")
        val reloaded = store().apply { load() }
        assertEquals(listOf(trip(now - day), trip(now)), reloaded.log.value.trips)
        assertEquals(listOf(damaged), quarantine.readLines())
        assertTrue(file.readLines().none { it == damaged })
        // 풀렸지만 형식이 맞지 않는 기록도 평문이 아니라 다시 봉인해 옮긴다
        file.appendText(TestCiphers.aes(key).seal("T9\tfuture") + "\n")
        store().apply { load() }
        assertEquals(2, quarantine.readLines().size)
        assertTrue(quarantine.readLines().all { it.startsWith(AesGcmTextCipher.PREFIX) })
    }

    @Test fun `quarantine is erased with the log and expires with the retention period`() {
        store().apply { load(); setRecording(true); append(trip(now)) }
        file.appendText("${AesGcmTextCipher.PREFIX}broken\n")
        store().apply { load() }
        assertTrue(quarantine.exists())
        quarantine.setLastModified(now - 101 * day) // 테스트 저장소의 보관 기간은 100일
        store().apply { load() }
        assertFalse(quarantine.exists())
        file.appendText("${AesGcmTextCipher.PREFIX}broken\n")
        store().apply { load(); eraseAll() }
        assertFalse(quarantine.exists())
    }

    @Test fun `when the key cannot be used the log says recording is paused`() {
        store().apply { load(); setRecording(true); append(trip(now)) }
        val paused = store(TestCiphers.unavailable).apply { load() }
        assertTrue(paused.log.value.paused)
        assertTrue(paused.log.value.trips.isEmpty())
    }

    private val snapshot = RecorderSnapshot(
        lastState = VehicleState(gear = Gear.DRIVE),
        lastAtMillis = 10,
        lastKnownGear = Gear.DRIVE,
        lastKnownOdometerKm = 1234.5,
        tripStartedAtMillis = 5,
        tripStartOdometerKm = 1230.0,
        knownWiperMillis = 0,
        wiperOnMillis = 0,
        gearChangesAtMillis = emptyList(),
    )

    @Test fun `checkpoint survives restart and is erased with the log`() {
        val store = store().apply { load() }
        store.saveCheckpoint(snapshot)
        assertNull("not saved before consent", store().loadCheckpoint())
        store.setRecording(true)
        store.saveCheckpoint(snapshot)
        assertEquals(snapshot, store().loadCheckpoint())
        store.eraseAll()
        assertNull(store().loadCheckpoint())
        assertTrue(tmp.root.listFiles()!!.isEmpty())
    }

    @Test fun `a half written last line does not swallow the next record`() {
        store().apply { load(); setRecording(true); append(trip(now)) }
        file.appendText("T2\t123") // 덧붙이다 죽음: 줄바꿈 없음
        val store = store().apply { load(); setRecording(true) }
        store.append(trip(now + 100_000))
        val reloaded = store().apply { load() }
        assertEquals(listOf(trip(now), trip(now + 100_000)), reloaded.log.value.trips)
    }

    @Test fun `corrupted checkpoint is ignored`() {
        File(tmp.root, "drive-log.tsv.checkpoint").writeText("S1\tbroken\n")
        assertNull(store().loadCheckpoint())
    }

    @Test fun `amend replaces the stored trip and survives restart`() {
        val store = store().apply { load(); setRecording(true) }
        store.append(trip(now))
        store.append(trip(now + 100_000))
        val corrected = trip(now).copy(distanceKm = 5.2)
        store.amend(corrected)
        store.amend(trip(now + 999_000_000)) // 없는 주행은 무시
        assertEquals(listOf(corrected, trip(now + 100_000)), store.log.value.trips)
        assertEquals(listOf(corrected, trip(now + 100_000)), store().apply { load() }.log.value.trips)
    }

    @Test fun `amend does nothing before consent`() {
        val store = store().apply { load(); setRecording(true); append(trip(now)); setRecording(false) }
        store.amend(trip(now).copy(distanceKm = 9.0))
        assertEquals(listOf(trip(now)), store.log.value.trips)
    }

    @Test fun `same trip appended twice after a crash is kept once`() {
        val store = store().apply { load(); setRecording(true) }
        store.append(trip(now))
        store.append(trip(now).copy(endedAtMillis = now + 30_000)) // 복원된 기록기가 같은 주행을 다시 끝냄
        assertEquals(1, store.log.value.trips.size)
        assertEquals(1, file.readLines().count { it.isNotBlank() })
    }

    @Test fun `records are stored encrypted, never as plain text`() {
        val store = store().apply { load(); setRecording(true) }
        store.append(trip(now - 3_600_000))
        store.append(trip(now))
        store.saveCheckpoint(snapshot.copy(lastAtMillis = now))
        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.startsWith(AesGcmTextCipher.PREFIX) })
        assertFalse(file.readText().contains('\t')) // 평문 칸 구분(탭)이 없어야 한다. Base64에는 탭이 없다.
        val checkpoint = File(tmp.root, "drive-log.tsv.checkpoint").readText()
        assertTrue(checkpoint.startsWith(AesGcmTextCipher.PREFIX))

        val reopened = store().apply { load() }
        assertEquals(store.log.value, reopened.log.value)
        assertEquals(now, reopened.loadCheckpoint()?.lastAtMillis)
    }

    @Test fun `plain records from before encryption are kept and re-encrypted`() {
        val old = trip(now)
        file.writeText(DriveLogCodec.encode(old) + "\n")
        val store = store().apply { load() }
        assertEquals(listOf(old), store.log.value.trips)
        assertTrue(file.readLines().filter { it.isNotBlank() }.all { it.startsWith(AesGcmTextCipher.PREFIX) })
        assertEquals(listOf(old), store().apply { load() }.log.value.trips)
    }

    @Test fun `a tampered line is dropped, the rest survive`() {
        val store = store().apply { load(); setRecording(true) }
        store.append(trip(now - 1000))
        store.append(trip(now))
        val lines = file.readLines().filter { it.isNotBlank() }.toMutableList()
        // 암호문 바이트 하나를 직접 뒤집는다(Base64 글자를 바꾸면 패딩 비트만 바뀌어 변조가 안 될 수 있다).
        val bytes = java.util.Base64.getDecoder().decode(lines[0].removePrefix(AesGcmTextCipher.PREFIX))
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        lines[0] = AesGcmTextCipher.PREFIX + java.util.Base64.getEncoder().encodeToString(bytes)
        file.writeText(lines.joinToString("\n", postfix = "\n"))
        assertEquals(listOf(trip(now)), store().apply { load() }.log.value.trips)
    }

    @Test fun `records sealed with another key cannot be read`() {
        store().apply { load(); setRecording(true) }.append(trip(now))
        val other = store(TestCiphers.aes()).apply { load() }
        assertTrue(other.log.value.trips.isEmpty())
    }

    @Test fun `when the key store is unavailable the file is left untouched`() {
        store().apply { load(); setRecording(true) }.append(trip(now))
        val before = file.readBytes()
        val locked = store(TestCiphers.unavailable).apply { load(); setRecording(true) }
        assertTrue(locked.log.value.trips.isEmpty())
        locked.append(trip(now + 1000))
        locked.saveCheckpoint(snapshot.copy(lastAtMillis = now))
        locked.purgeExpired()
        assertTrue(before.contentEquals(file.readBytes()))
        assertFalse(File(tmp.root, "drive-log.tsv.checkpoint").exists())
        // 다음 실행에서 키를 다시 쓸 수 있으면 기록이 그대로 있다.
        assertEquals(listOf(trip(now)), store().apply { load() }.log.value.trips)
    }

    @Test fun `erasing works even when the key store is unavailable`() {
        store().apply { load(); setRecording(true) }.append(trip(now))
        store(TestCiphers.unavailable).apply { load(); eraseAll() }
        assertFalse(file.exists())
    }

    @Test fun `rewrites pack records into a few encrypted lines`() {
        val trips = (1..600).map { trip(now - it * 60_000L) }.sortedBy { it.endedAtMillis }
        val store = store().apply { load(); setRecording(true) }
        assertTrue(store.replaceAll(trips))
        assertEquals(3, file.readLines().count { it.isNotBlank() }) // 256 + 256 + 88
        assertEquals(trips, store().apply { load() }.log.value.trips)
    }

    @Test fun `many appended lines are repacked on the next start`() {
        val store = store().apply { load(); setRecording(true) }
        val trips = (1..40).map { trip(now - (41 - it) * 60_000L) }
        trips.forEach(store::append)
        assertEquals(40, file.readLines().count { it.isNotBlank() })
        val reopened = store().apply { load() }
        assertEquals(1, file.readLines().count { it.isNotBlank() })
        assertEquals(trips, reopened.log.value.trips)
    }

    @Test fun `a few appended lines are left as they are`() {
        val store = store().apply { load(); setRecording(true) }
        repeat(5) { store.append(trip(now - (5 - it) * 60_000L)) }
        val before = file.readBytes()
        store().apply { load() }
        assertTrue(before.contentEquals(file.readBytes()))
    }
}
