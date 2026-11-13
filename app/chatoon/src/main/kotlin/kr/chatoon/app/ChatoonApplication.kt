package kr.chatoon.app

import android.app.Application
import android.os.SystemClock
import kr.chatoon.app.data.consent.ConsentStatus
import kr.chatoon.app.data.consent.ConsentStore
import kr.chatoon.app.data.cartoon.AnswerStore
import kr.chatoon.app.data.speech.Dictation
import kr.chatoon.app.data.speech.CarVoice
import kr.chatoon.app.data.vehicle.MoodLight
import kr.chatoon.app.data.drive.DriveLogStore
import kr.chatoon.app.data.storage.AesGcmTextCipher
import kr.chatoon.app.data.storage.AndroidKeystoreKey
import kr.chatoon.app.data.storage.WrappedDataKey
import kr.chatoon.app.data.vehicle.PleosVehicleGateway
import kr.chatoon.app.domain.drive.RecordingClock
import kr.chatoon.app.domain.drive.TripRecorder
import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.service.RecordingService
import kr.chatoon.app.domain.vehicle.VehicleGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * 앱 전체에서 하나만 있어야 하는 객체를 만들고 연결한다.
 *
 * 차량 연결과 주행 기록은 화면이 아니라 앱 프로세스에 묶어 둔다. 그래야 화면을 닫아도 기록이 이어진다.
 */
class ChatoonApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { it.start() }
    }
}

class AppContainer(private val application: Application) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val clock: () -> Long = System::currentTimeMillis
    val vehicle: VehicleGateway = PleosVehicleGateway(application, appScope)

    // 주행 기록과 주인의 답은 암호화해 저장한다(데이터 키는 Android Keystore 키로 감싸 둔다). 동의 시각과 조명 연출 설정은 민감정보가 아니라 평문이다.
    private val recordKey = WrappedDataKey(File(application.filesDir, "records.key"), AndroidKeystoreKey::getOrCreate) {
        // 키가 없거나 깨졌을 때만 부른다. 암호문 줄이 하나라도 남아 있으면 새 키를 만들지 않는다(A6, 평문 줄은 새 키로 다시 암호화해도 된다).
        listOf("drive-log.tsv", "drive-log.tsv.checkpoint", "profile.txt", "answers.txt").any { name ->
            val f = File(application.filesDir, name)
            f.exists() && f.useLines { lines -> lines.any { it.startsWith(AesGcmTextCipher.PREFIX) } }
        }
    }
    internal val cipher = AesGcmTextCipher(recordKey::get)
    val driveLog = DriveLogStore(File(application.filesDir, "drive-log.tsv"), clock, cipher)
    val consent = ConsentStore(File(application.filesDir, "consent.txt"), clock)
    /** 차의 질문에 주인이 답한 말(카툰). 주행 기록과 같은 키로 암호화한다. */
    val answers = AnswerStore(File(application.filesDir, "answers.txt"), cipher)

    /** 동의와 기록을 파일에서 다 읽었으면 완료된다. 음성 명령처럼 앱이 막 깨어난 직후의 요청이 기다린다. */
    private val ready = CompletableDeferred<Unit>()

    fun start() {
        // 차 상태는 화면에 보여 주기 위해 바로 읽는다. 기록은 동의한 뒤에만 한다.
        vehicle.start()
        // 지난 실행이 실내 조명을 바꾼 채 끝났으면(강제 종료·업데이트) 먼저 원래대로 돌린다.
        prefs.getString(KEY_LIGHT_BEFORE, null)?.let { moodLight.recover(it) }
        if (prefs.getBoolean(KEY_MOOD_LIGHT, false)) moodLight.warmUp()
        appScope.launch(Dispatchers.IO) {
            // 기록·설정을 다 읽은 뒤에 동의 상태를 내보낸다. 순서가 바뀌면 화면에 잠깐 "0일째·기록 0회"가 보인다.
            answers.load()
            driveLog.load()
            consent.load()
            // 동의 파일이 없는데 기록이 남아 있다면 철회가 중간에 끊긴 것이다(철회는 동의를 먼저 지운다). 마저 지운다.
            // 안내가 바뀌어 다시 동의를 기다리는 경우(termsUpdated)는 예전 동의로 모은 기록이므로 남겨 둔다.
            // 동의 파일을 읽지 못한 경우(unreadable)도 지우지 않는다(저장 장치 오류로 기록을 잃지 않게). 비교가 그 둘을 뺀다.
            if (consent.status.value == ConsentStatus.Required(termsUpdated = false)) eraseRecords()
            ready.complete(Unit)
            // 급가속·급감속을 센다(차가 놀란 순간을 그리는 재료). 속도 권한이 없으면 세지 않는다.
            vehicle.setMotionCounting(true)
            launch { purgeExpiredPeriodically() }
            consent.status
                .map { it is ConsentStatus.Granted }
                .distinctUntilChanged()
                .collectLatest { granted ->
                    driveLog.setRecording(granted)
                    answers.setEnabled(granted)
                    if (granted) RecordingService.start(application) else RecordingService.stop(application)
                    // 동의가 풀리면 collectLatest가 기록기를 취소한다. 다시 동의하면 새 기록기로 처음부터 센다.
                    if (granted) {
                        coroutineScope {
                            launch { recordDrives() }
                        }
                    }
                }
        }
    }

    /** 준비가 [timeoutMillis] 안에 끝나면 true. */
    suspend fun awaitReady(timeoutMillis: Long): Boolean = withTimeoutOrNull(timeoutMillis) { ready.await() } != null

    /** 앱 범위에서 백그라운드 작업을 돌린다(브로드캐스트 리시버용). */
    fun launchBackground(block: suspend () -> Unit) {
        appScope.launch(Dispatchers.IO) { block() }
    }

    fun answer(tripStartMillis: Long, jolt: Boolean, said: String) = launchBackground { answers.answer(tripStartMillis, jolt, said) }

    fun skipQuestion(tripStartMillis: Long) = launchBackground { answers.skip(tripStartMillis) }

    /** 실내 조명 연출. 주인이 켜 둔 경우에만, 주차 중에 화를 볼 때 그 화의 색으로 바꾸고 다 보면 되돌린다. */
    val moodLight: MoodLight by lazy {
        MoodLight(application, appScope) { remembered ->
            prefs.edit().apply { if (remembered == null) remove(KEY_LIGHT_BEFORE) else putString(KEY_LIGHT_BEFORE, remembered) }.commit()
        }
    }

    // 조명 연출을 켰는지(민감정보가 아닌 설정이라 평문 SharedPreferences). 기본은 꺼짐: 켜는 사람만 쓴다.
    private val prefs by lazy { application.getSharedPreferences("chatoon", android.content.Context.MODE_PRIVATE) }
    private val _moodLightOn by lazy { kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean(KEY_MOOD_LIGHT, false)) }
    val moodLightOn: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _moodLightOn

    // 주인이 고른 차 색(그림의 차체 색). CarPaint 이름으로 평문 저장(민감정보 아님).
    private val _carPaint by lazy { kotlinx.coroutines.flow.MutableStateFlow(prefs.getString(KEY_CAR_PAINT, null)) }
    val carPaint: kotlinx.coroutines.flow.StateFlow<String?> get() = _carPaint

    fun setCarPaint(name: String) {
        prefs.edit().putString(KEY_CAR_PAINT, name).apply()
        _carPaint.value = name
    }

    fun setMoodLight(on: Boolean) {
        prefs.edit().putBoolean(KEY_MOOD_LIGHT, on).apply()
        _moodLightOn.value = on
        if (on) moodLight.warmUp() else moodLight.restore()
    }

    /** 차의 목소리(세운 뒤 차의 한마디). 처음 말할 때 엔진을 준비한다. */
    val carVoice: CarVoice by lazy { CarVoice(application) }

    /** 이미 소리로 읽은 화(주행을 마친 시각). 같은 화를 앱을 열 때마다 다시 읽지 않게 이 실행 동안 기억한다. */
    val spokenEpisodes: MutableSet<Long> = java.util.Collections.synchronizedSet(HashSet())

    /** 말로 답하기. debug 빌드에서는 files/dictation-test.pcm이 있으면 마이크 대신 그 파일을 쓴다(에뮬레이터 확인용). */
    fun newDictation(): Dictation {
        val debuggable = application.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        return Dictation(application, testAudio = if (debuggable) File(application.filesDir, "dictation-test.pcm") else null)
    }

    /** 동의를 저장한다. 저장하지 못했으면 false(동의로 보지 않으므로 화면이 그 사실을 알려야 한다). */
    suspend fun grantConsent(): Boolean = withContext(Dispatchers.IO) { consent.grant() }

    /**
     * 동의 철회. 순서가 중요하다.
     * 1. 쓰기를 모두 막는다(기록기·알림·설정이 그 뒤에 한 줄도 더 쓰지 못하게).
     * 2. 동의 파일을 지운다. 여기서 프로세스가 죽어도 다음 실행 때 [start]가 남은 기록을 마저 지운다.
     * 3. 기록·알림 기억·설정을 지우고, 이미 떠 있는 변화 알림도 지운다.
     */
    fun withdrawConsent() {
        appScope.launch(Dispatchers.IO) {
            driveLog.setRecording(false)
            answers.setEnabled(false)
            consent.revoke()
            setMoodLight(false)
            eraseRecords()
        }
    }

    private fun eraseRecords() {
        driveLog.eraseAll()
        answers.eraseAll()
    }

    /**
     * 화면이 앞에 올 때 부른다. 동의했는데 기록 서비스가 떠 있지 않으면 다시 띄운다.
     * 앱이 백그라운드에서 깨어났을 때(음성 명령 등)는 Android 12+가 서비스 시작을 막으므로, 화면이 보일 때가 다시 시도할 기회다.
     */
    fun ensureRecordingService() {
        if (consent.status.value is ConsentStatus.Granted) RecordingService.start(application)
    }

    /** 13개월 보관 약속을 앱이 오래 켜져 있어도 지킨다(재부팅 없이 절전만 하는 차). */
    private suspend fun purgeExpiredPeriodically() {
        while (true) {
            delay(PURGE_INTERVAL_MILLIS)
            driveLog.purgeExpired()
        }
    }

    /** 차량 상태를 주행 기록기에 넘긴다. 한 코루틴에서만 실행되므로 기록 순서가 보장된다. */
    private suspend fun recordDrives() {
        // 프로세스가 주행 도중 죽었다 살아났다면 저장해 둔 진행 상태를 이어 받는다(이어 붙일지는 기록기가 판단).
        val saved = driveLog.loadCheckpoint()
        val recorder = saved?.let { TripRecorder.restore(it, driveLog::append, driveLog::amend) }
            ?: TripRecorder(onTrip = driveLog::append, onTripAmended = driveLog::amend)
        // 시계가 앞뒤로 바뀌어도 기록 순서와 주행 길이가 맞게 한다(RecordingClock). 주차 중(또는 기어 모름)에만 시계를 다시 맞춘다.
        // 실행 사이에 시계가 뒤로 간 경우는 기록기가 복원 단계에서 걸러 낸다.
        val recordingClock = RecordingClock(wall = clock, elapsed = SystemClock::elapsedRealtime)
        vehicle.state.collect { state ->
            val now = recordingClock.now(canResync = state.gear == null || state.gear == Gear.PARK)
            recorder.onState(state, now)
            recorder.snapshot()?.let(driveLog::saveCheckpoint)
        }
    }

    private companion object {
        const val PURGE_INTERVAL_MILLIS = 60 * 60 * 1000L
        const val KEY_MOOD_LIGHT = "mood_light"
        const val KEY_CAR_PAINT = "car_paint"
        const val KEY_LIGHT_BEFORE = "mood_light_before"
    }
}
