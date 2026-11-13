package kr.chatoon.app.data.drive

import android.os.SystemClock
import android.util.Log
import kr.chatoon.app.data.storage.CipherUnavailableException
import kr.chatoon.app.data.storage.DurableFile
import kr.chatoon.app.data.storage.TextCipher
import kr.chatoon.app.domain.drive.DriveLogCodec
import kr.chatoon.app.domain.drive.RecorderSnapshot
import kr.chatoon.app.domain.drive.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException

data class DriveLog(
    val trips: List<Trip> = emptyList(),
    /** 저장된 기록 파일을 읽지 못한 실행(저장 장치 오류). 화면이 "기록이 없다"가 아니라 "읽지 못했다"를 말하게 한다. */
    val unreadable: Boolean = false,
    /** 암호 키를 쓸 수 없어 이 실행 동안 기록을 읽지도 쓰지도 않는다(파일은 그대로 둠). 화면이 "기록 0회"처럼 보이지 않게 알린다. */
    val paused: Boolean = false,
)

/**
 * 주행 기록을 앱 전용 저장소의 파일 하나에 한 줄씩 덧붙여 저장한다.
 *
 * - 기기 밖으로 보내지 않는다(개인정보 최소화). 위치·속도·경로는 처음부터 기록하지 않는다.
 * - 오래된 기록은 [retentionMillis]가 지나면 다음 실행 때 지운다.
 * - 동의한 동안에만 쓴다([setRecording]). 동의를 철회하면 [eraseAll]로 파일과 메모리 기록을 모두 지운다.
 * - 진행 중인 주행 상태(체크포인트)도 같은 폴더에 둔다. 프로세스가 죽었다 살아나도 이어서 기록하기 위해서다.
 * - 기록기 코루틴과 화면(철회)이 동시에 부를 수 있어 모든 메서드를 한 잠금으로 묶는다.
 *   그래서 철회 직후에 기록기가 한 줄을 더 쓰는 일이 없다.
 * - [cipher]로 암호화한다(Pleos 앱 보안 가이드). 새 기록은 한 줄씩 덧붙이고, 다시 쓸 때는 [BLOCK_RECORDS]개씩 묶어 한 줄로 암호화한다.
 *   암호화 전에 저장된 평문 줄은 읽은 뒤 암호문으로 다시 쓴다.
 *   키 저장소를 쓸 수 없으면 그 실행 동안 파일을 건드리지 않는다([locked]) — 덮어쓰면 기록이 복구할 수 없게 사라진다.
 * - 풀리지 않거나 형식이 맞지 않는 줄은 버리지 않고 격리 파일([quarantineFile])로 옮긴다(지시 A6). 한 줄에 기록이 최대
 *   [BLOCK_RECORDS]개 들어 있어, 원인을 고친 뒤 되살릴 수 있게 남긴다. 격리 파일도 암호문만 담고, 마지막으로 넣은 뒤
 *   보관 기간이 지나면 지우며, [eraseAll]이 함께 지운다.
 */
class DriveLogStore(
    private val file: File,
    private val clock: () -> Long,
    private val cipher: TextCipher,
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
) {
    private val _log = MutableStateFlow(DriveLog())
    val log: StateFlow<DriveLog> = _log.asStateFlow()

    private val checkpointFile = File(file.parentFile, file.name + ".checkpoint")
    private val quarantineFile = File(file.parentFile, file.name + ".quarantine")
    private var recording = false
    private var lastCheckpoint: String? = null

    /** 덧붙이기가 실패했다(디스크 가득 등). 반쯤 쓴 줄에 다음 줄이 이어 붙지 않게, 다음 쓰기는 파일 전체를 다시 쓴다. */
    private var appendFailed = false

    /** 격리 파일에 옮기지 못한 깨진 줄. 다시 쓸 때 지우지 않고 그대로 함께 쓴다. */
    private var keptBroken: List<String> = emptyList()

    /** 키 저장소를 쓸 수 없어 읽지 못했다. 이 실행 동안은 쓰지 않는다(기존 파일 보호). 지우기([eraseAll])는 한다. */
    private var locked = false

    /**
     * 파일을 읽다 오류가 났다(저장 장치 오류 등). 이 실행 동안은 파일을 다시 쓰지 않고(다시 쓰면 못 읽은 기록이 사라진다)
     * 새 기록을 덧붙이기만 한다. 일부만 읽은 기록으로 계산하면 빠진 주 때문에 가짜 변화가 나오므로 메모리 기록은 비워 둔다.
     */
    private var readFailed = false

    /** 동의 상태에 맞춰 기록을 켜고 끈다. 끄기만 해서는 지우지 않는다(예: 안내 변경으로 재동의 대기). */
    @Synchronized
    fun setRecording(enabled: Boolean) {
        recording = enabled
    }

    /** 기록을 멈추고 저장된 기록을 모두 지운다. 동의 철회 때 부른다. */
    @Synchronized
    fun eraseAll() {
        recording = false
        locked = false
        readFailed = false
        lastCheckpoint = null
        keptBroken = emptyList()
        val leftovers = listOf(file, DurableFile.tmpOf(file), checkpointFile, DurableFile.tmpOf(checkpointFile), quarantineFile)
        for (f in leftovers) {
            if (f.exists() && !f.delete()) Log.e(TAG, "Failed to delete ${f.name}")
        }
        _log.value = DriveLog()
    }

    /** 파일을 읽고 보관 기간이 지난 기록을 정리한다. 앱 시작 때 한 번 부른다. */
    @Synchronized
    fun load() {
        locked = false
        readFailed = false
        val startedAt = SystemClock.elapsedRealtime()
        val cutoff = clock() - retentionMillis
        val tripsByStart = LinkedHashMap<Long, Trip>()
        var dropped = 0
        var needsRewrite = false
        var lines = 0
        val broken = mutableListOf<String>()
        if (quarantineFile.exists() && quarantineFile.lastModified() < cutoff && !quarantineFile.delete()) Log.e(TAG, "Failed to delete expired quarantine")
        if (file.exists()) {
            try {
                // 덧붙이다 죽어 마지막 줄에 줄바꿈이 없으면, 다음 기록이 그 줄에 이어 붙어 둘 다 깨진다. 다시 써서 바로잡는다.
                needsRewrite = file.length() > 0 && !file.readText().endsWith("\n")
                file.forEachLine { raw ->
                    if (raw.isBlank()) return@forEachLine
                    lines++
                    val block = when (val opened = cipher.open(raw)) {
                        is TextCipher.Opened.Sealed -> opened.text
                        is TextCipher.Opened.Plain -> opened.text.also { needsRewrite = true } // 암호화 전 형식 → 다시 써서 암호화
                        TextCipher.Opened.Corrupt -> null
                    }
                    if (block == null) {
                        broken += raw // 이미 암호문(또는 알 수 없는 내용)이라 그대로 옮긴다
                        dropped++
                        return@forEachLine
                    }
                    for (record in block.split(RECORD_SEPARATOR)) {
                        val trip = DriveLogCodec.decode(record)
                        when {
                            trip == null -> {
                                broken += cipher.seal(record) // 풀린 평문은 다시 봉인해서만 옮긴다
                                dropped++
                            }
                            trip.endedAtMillis >= cutoff -> {
                                // 같은 출발 시각이면 뒤의 줄이 앞 줄을 대신한다(덧붙이기로 남긴 고치기)
                                if (tripsByStart.put(trip.startedAtMillis, trip) != null) needsRewrite = true
                            }
                            else -> dropped++
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read drive log; appending only this run", e)
                readFailed = true
                _log.value = DriveLog(unreadable = true)
                return
            } catch (e: CipherUnavailableException) {
                Log.e(TAG, "Encryption key unavailable; leaving the drive log untouched this run", e)
                locked = true
                _log.value = DriveLog(paused = true)
                return
            }
        }
        // 격리하지 못하면 깨진 줄을 원래 파일에 그대로 남긴다(다시 쓸 때마다 함께 쓴다).
        keptBroken = if (broken.isNotEmpty() && !quarantine(broken)) broken else emptyList()
        val trips = tripsByStart.values.sortedBy { it.startedAtMillis }
        _log.value = DriveLog(trips)
        Log.i(TAG, "Loaded ${trips.size} trips in ${SystemClock.elapsedRealtime() - startedAt} ms")
        // 덧붙인 한 줄짜리 기록이 쌓였으면 묶음으로 다시 쓴다(다음 시작 때 풀 줄 수를 줄인다).
        val packedLines = (trips.size + BLOCK_RECORDS - 1) / BLOCK_RECORDS
        if (dropped > 0 || needsRewrite || lines - packedLines >= REPACK_AFTER_LINES) rewrite()
    }

    /**
     * 보관 기간이 지난 기록을 지운다. 앱이 오래 켜져 있어도(재부팅 없이 절전만 하는 차) 약속한 13개월을 지키려고
     * 시작 때뿐 아니라 주기적으로 부른다. 지운 게 있으면 true.
     */
    @Synchronized
    fun purgeExpired(): Boolean {
        val cutoff = clock() - retentionMillis
        val current = _log.value
        val trips = current.trips.filter { it.endedAtMillis >= cutoff }
        if (trips.size == current.trips.size) return false
        _log.value = DriveLog(trips)
        rewrite()
        return true
    }

    @Synchronized
    fun append(trip: Trip) {
        if (!recording || locked) return
        // 기록을 쓴 직후 체크포인트를 갱신하기 전에 프로세스가 죽으면, 복원된 기록기가 같은 주행을 한 번 더 낸다.
        // 시작 시각이 같으면 같은 주행이므로 버린다.
        if (_log.value.trips.any { it.startedAtMillis == trip.startedAtMillis }) return
        _log.update { it.copy(trips = it.trips + trip) }
        write(DriveLogCodec.encode(trip))
    }


    /**
     * 이미 저장한 주행(같은 출발 시각)을 고친다. 주차 중 늦게 온 주행거리 반영용. 없으면 무시한다.
     */
    @Synchronized
    fun amend(trip: Trip) {
        if (!recording || locked) return
        val trips = _log.value.trips
        val index = trips.indexOfFirst { it.startedAtMillis == trip.startedAtMillis }
        if (index < 0 || trips[index] == trip) return
        _log.value = _log.value.copy(trips = trips.toMutableList().also { it[index] = trip })
        // 파일을 읽지 못한 실행에서는 다시 쓸 수 없으므로, 고친 주행을 한 줄 덧붙인다(읽을 때 뒤의 줄이 이긴다).
        // 늦게 온 주행거리가 저장 장치 오류 한 번으로 0으로 남지 않게 한다(ee 결정 A2②).
        if (readFailed) write(DriveLogCodec.encode(trip)) else rewrite()
    }

    /**
     * 기록 전체를 바꾼다(시연 데이터 넣기용). 기록이 꺼져 있으면(동의 전) 아무것도 하지 않고 false.
     * 파일을 통째로 다시 쓰므로 중간에 죽어도 예전 기록이나 새 기록 둘 중 하나만 남는다.
     */
    @Synchronized
    fun replaceAll(trips: List<Trip>): Boolean {
        if (!recording || locked || readFailed) return false
        _log.value = DriveLog(trips.sortedBy { it.startedAtMillis })
        rewrite()
        return true
    }

    /** 진행 상태를 저장한다. 바뀐 게 없으면 쓰지 않는다. 기록이 꺼져 있으면 무시한다. */
    @Synchronized
    fun saveCheckpoint(snapshot: RecorderSnapshot) {
        if (!recording || locked) return
        val line = RecorderSnapshot.encode(snapshot)
        if (line == lastCheckpoint) return
        if (replace(checkpointFile) { cipher.seal(line) + "\n" }) lastCheckpoint = line
    }

    /** 저장된 진행 상태. 없거나 깨졌으면 null. */
    @Synchronized
    fun loadCheckpoint(): RecorderSnapshot? {
        if (!checkpointFile.exists()) return null
        return try {
            val line = when (val opened = cipher.open(checkpointFile.readText().trim())) {
                is TextCipher.Opened.Sealed -> opened.text
                is TextCipher.Opened.Plain -> opened.text
                TextCipher.Opened.Corrupt -> null
            }
            line?.let(RecorderSnapshot::decode).also {
                if (it == null) Log.w(TAG, "Discarding unreadable checkpoint")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read checkpoint", e)
            null
        } catch (e: CipherUnavailableException) {
            Log.e(TAG, "Encryption key unavailable; not resuming the checkpoint", e)
            null
        }
    }

    private fun quarantine(lines: List<String>): Boolean {
        if (quarantineFile.length() >= QUARANTINE_MAX_BYTES) {
            Log.w(TAG, "Quarantine full; keeping ${lines.size} broken lines in place")
            return false
        }
        return try {
            DurableFile.append(quarantineFile, lines.joinToString("") { it + "\n" })
            Log.w(TAG, "Moved ${lines.size} broken lines to quarantine")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to quarantine broken lines", e)
            false
        }
    }

    /** 한 줄을 덧붙인다. 메모리 기록은 먼저 바꿔 둔다. 예전에 덧붙이기가 실패했으면 메모리 전체로 다시 쓴다. */
    private fun write(line: String) {
        if (appendFailed) {
            rewrite()
            return
        }
        try {
            DurableFile.append(file, cipher.seal(line) + "\n")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to append drive log", e)
            appendFailed = true
        } catch (e: CipherUnavailableException) {
            Log.e(TAG, "Encryption key unavailable; will rewrite later", e)
            appendFailed = true
        }
    }

    /** 메모리 기록 전체로 파일을 바꾼다. 성공하면 덧붙이기 실패 표시를 지운다. */
    private fun rewrite() {
        if (locked || readFailed) return
        val current = _log.value
        val records = current.trips.map(DriveLogCodec::encode)
        val content = {
            records.chunked(BLOCK_RECORDS).joinToString("") { cipher.seal(it.joinToString(RECORD_SEPARATOR)) + "\n" } +
                keptBroken.joinToString("") { it + "\n" }
        }
        if (replace(file, content)) appendFailed = false
    }

    /**
     * 쓰다가 죽거나 전원이 끊겨도 예전 내용이나 새 내용 둘 중 하나만 남게 한다([DurableFile]).
     * 내용은 암호화까지 끝낸 뒤에 쓰므로, 암호화가 실패하면 파일을 건드리지 않는다.
     */
    private fun replace(target: File, content: () -> String): Boolean = try {
        DurableFile.replace(target, content())
        true
    } catch (e: IOException) {
        Log.e(TAG, "Failed to write ${target.name}", e)
        false
    } catch (e: CipherUnavailableException) {
        Log.e(TAG, "Encryption key unavailable; ${target.name} left as is", e)
        false
    }

    private companion object {
        const val TAG = "Chatoon/DriveLog"
        const val DEFAULT_RETENTION_MILLIS: Long = 400L * 24 * 60 * 60 * 1000 // 약 13개월: 1년 전과 비교할 수 있게

        /**
         * 다시 쓸 때 기록을 이만큼씩 묶어 한 줄로 암호화한다. 복호화는 한 번에 0.3 ms 안팎이 들어(에뮬레이터 실측)
         * 기록마다 따로 풀면 13개월치(약 3천 개) 시작에 1초가 넘게 더 걸린다. 묶음이 너무 크면 한 줄이 깨질 때 잃는 양이 커진다.
         */
        const val BLOCK_RECORDS = 256

        /** 한 줄씩 덧붙인 기록이 이만큼 쌓이면 시작할 때 묶음으로 다시 쓴다. */
        const val REPACK_AFTER_LINES = 32

        /** 격리 파일 상한(1MB, 묶음 줄 약 수백 개). 넘으면 깨진 줄을 원래 파일에 그대로 둔다. */
        const val QUARANTINE_MAX_BYTES = 1L shl 20

        /** 묶음 안에서 기록을 나누는 문자(ASCII 레코드 구분자). 기록 형식(탭 구분 숫자·영문)에는 나오지 않는다. */
        const val RECORD_SEPARATOR = "\u001E"
    }
}
