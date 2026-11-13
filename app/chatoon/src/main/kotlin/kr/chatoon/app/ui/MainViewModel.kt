package kr.chatoon.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.chatoon.app.AppContainer
import kr.chatoon.app.data.consent.ConsentStatus
import kr.chatoon.app.data.drive.DriveLog
import kr.chatoon.app.domain.drive.Trip
import kr.chatoon.app.domain.cartoon.CartoonWriter
import kr.chatoon.app.domain.cartoon.Episode
import kr.chatoon.app.domain.cartoon.TripSeen
import kr.chatoon.app.domain.cartoon.AnswerBook
import kr.chatoon.app.domain.cartoon.Question
import kr.chatoon.app.domain.cartoon.LightScene
import kr.chatoon.app.domain.cartoon.endedRecently
import kr.chatoon.app.data.speech.Dictation
import kr.chatoon.app.domain.vehicle.VehicleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val consent: ConsentStatus = ConsentStatus.Loading,
    val vehicle: VehicleState = VehicleState(),
    /** 저장된 전체 주행 수(설정 화면). */
    val totalTrips: Int = 0,
    /** 저장된 기록을 읽지 못했거나(저장 장치 오류) 암호 키를 쓸 수 없어 기록을 멈춘 실행. 화면이 '기록 없음'처럼 보이지 않게 말한다. */
    val logNotice: LogNotice? = null,
    /** 급가속·급감속을 세는 데 필요한 차량 속도 권한 상태. */
    val speedAccess: SpeedAccess = SpeedAccess.OFF,
    /** 차가 그린 화. 최근 것이 앞이다. 주행이 없으면 빈 목록. */
    val episodes: List<EpisodeEntry> = emptyList(),
    /** 가장 최근 주행에 대해 차가 아직 묻지 않은 질문. 화면은 주차 중일 때만 보여 준다. */
    val question: Question? = null,
    /** 이번 달에 운전한 시간과 그게 몇 화로 남았는지(지난 화 맨 위). 이번 달 주행이 없으면 null. */
    val month: MonthSummary? = null,
)

/** 한 달 동안 운전한 시간과 화 수. "운전한 시간이 그냥 사라지지 않고 만화로 남았다"를 숫자로 보여 준다. */
data class MonthSummary(val month: Int, val trips: Int, val drivingMillis: Long)

/**
 * 차가 그린 한 화.
 *
 * @param number 회차(첫 주행이 제1화).
 * @param endedAtMillis 그 주행을 마친 시각.
 */
data class EpisodeEntry(val number: Int, val endedAtMillis: Long, val episode: Episode, val trip: Trip? = null)

/** 차량 속도 권한. [BLOCKED]: 거절 뒤 다시 물을 수 없어 차량 설정에서만 바꿀 수 있다. */
enum class SpeedAccess { GRANTED, OFF, BLOCKED }

/** 주차 뒤 이 시간 안에 앱을 열면 방금 주행을 맨 위에 보여 준다. */
const val RECAP_WINDOW_MILLIS = 3 * 60 * 60 * 1000L

private data class Analysis(
    val totalTrips: Int,
    val logNotice: LogNotice?,
    val episodes: List<EpisodeEntry>,
    val question: Question?,
    val month: MonthSummary?,
)

enum class LogNotice { UNREADABLE, PAUSED }

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val clock = container.clock
    private val tripSeen = TripSeen()

    /** 기록·설정이 바뀔 때마다, 그리고 날짜가 넘어가도록 1시간마다 다시 계산한다. 계산은 메인 스레드 밖에서 한다. */
    private val analysis: Flow<Analysis> =
        combine(container.driveLog.log, container.answers.book, ticker(RECOMPUTE_INTERVAL_MILLIS)) { log, answers, _ -> analyze(log, answers) }
            .flowOn(Dispatchers.Default)

    private val _speedAccess = MutableStateFlow(SpeedAccess.OFF)

    val uiState: StateFlow<MainUiState> =
        combine(container.consent.status, container.vehicle.state, analysis, _speedAccess) { consent, vehicle, a, speed ->
            MainUiState(consent, vehicle, a.totalTrips, a.logNotice, speed, a.episodes, a.question, a.month)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MainUiState(container.consent.status.value, container.vehicle.state.value),
        )

    /** 한 번 보여 주고 지우는 안내(동의를 저장하지 못함). */
    enum class Notice { CONSENT_NOT_SAVED }

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    fun noticeShown() {
        _notice.value = null
    }

    fun setSpeedAccess(access: SpeedAccess) {
        _speedAccess.value = access
    }

    /** 권한이 없다. 이미 "다시 묻지 않음"으로 알고 있으면 그대로 두고(설정 안내 유지), 아니면 켜기 버튼을 보인다. */
    fun speedAccessLost() {
        if (_speedAccess.value == SpeedAccess.GRANTED) _speedAccess.value = SpeedAccess.OFF
    }

    /** 동의 저장에 실패하면 아무 일도 없는 것처럼 보이지 않게 알린다(ee 검수 A3①). */
    fun grantConsent() {
        viewModelScope.launch {
            if (!container.grantConsent()) _notice.value = Notice.CONSENT_NOT_SAVED
        }
    }

    fun withdrawConsent() = container.withdrawConsent()

    private fun analyze(log: DriveLog, answers: AnswerBook): Analysis {
        val now = clock()
        return Analysis(
            log.trips.size,
            when {
                log.paused -> LogNotice.PAUSED
                log.unreadable -> LogNotice.UNREADABLE
                else -> null
            },
            episodes(log.trips, answers),
            question(log.trips, answers, now),
            month(log.trips, now),
        )
    }

    /** 이번 달(서울 시각)에 끝난 주행들의 운전 시간 합. */
    private fun month(trips: List<Trip>, now: Long): MonthSummary? {
        val today = java.time.Instant.ofEpochMilli(now).atZone(kr.chatoon.app.domain.analysis.SunCalculator.SEOUL_ZONE)
        val from = today.toLocalDate().withDayOfMonth(1).atStartOfDay(kr.chatoon.app.domain.analysis.SunCalculator.SEOUL_ZONE).toInstant().toEpochMilli()
        val inMonth = trips.filter { it.endedAtMillis >= from }
        if (inMonth.isEmpty()) return null
        return MonthSummary(today.monthValue, inMonth.size, inMonth.sumOf { it.durationMillis })
    }

    /** 가장 최근 주행이 [RECAP_WINDOW_MILLIS] 안에 끝났고, 차가 물을 것이 있으면 그 질문. 오래된 주행은 묻지 않는다. */
    private fun question(trips: List<Trip>, answers: AnswerBook, now: Long): Question? {
        val last = trips.maxByOrNull { it.startedAtMillis } ?: return null
        if (!endedRecently(last.endedAtMillis, now, RECAP_WINDOW_MILLIS)) return null
        return Question.of(last.startedAtMillis, tripSeen.of(last, trips), answers)
    }

    /** 차의 질문에 답한다. 답하면 그 화가 다시 그려진다. */
    fun answer(question: Question, said: String) {
        if (said.isBlank()) return
        container.answer(question.tripStartMillis, question.jolt, said)
    }

    fun skipQuestion(question: Question) = container.skipQuestion(question.tripStartMillis)

    /** 주인이 고른 차 색 이름(CarPaint.name). 고르지 않았으면 null(기본 색). */
    val carPaint: StateFlow<String?> get() = container.carPaint

    fun setCarPaint(name: String) = container.setCarPaint(name)

    /** 실내 조명 연출을 켰는가. */
    val moodLightOn: StateFlow<Boolean> get() = container.moodLightOn

    fun setMoodLight(on: Boolean) = container.setMoodLight(on)

    /** 이 화의 장면으로 실내 조명을 바꾼다(화면이 주차 중·켜짐을 확인하고 부른다). */
    fun showLight(entry: EpisodeEntry) = container.moodLight.show(LightScene.of(entry.episode))

    /** 바꾸기 전 조명으로 되돌린다. 바꾼 적이 없으면 아무것도 안 한다. */
    fun restoreLight() = container.moodLight.restore()

    /** 차가 지금 말하는 중인가. */
    val speaking: StateFlow<Boolean> get() = container.carVoice.speaking

    /** 차의 한마디를 소리로 읽는다(듣기 버튼). */
    fun speak(entry: EpisodeEntry) {
        container.spokenEpisodes.add(entry.endedAtMillis)
        container.carVoice.speak(entry.episode.line)
    }

    fun stopSpeaking() = container.carVoice.stop()

    /** 주차 뒤 처음 보는 새 화면 한 번 읽는다. 끝난 지 [RECAP_WINDOW_MILLIS]가 지난 화는 읽지 않는다. */
    fun speakIfNew(entry: EpisodeEntry) {
        if (!endedRecently(entry.endedAtMillis, clock(), RECAP_WINDOW_MILLIS)) return
        if (!container.spokenEpisodes.add(entry.endedAtMillis)) return
        container.carVoice.speak(entry.episode.line)
    }

    /** 말로 답하기. 처음 누를 때 만든다. */
    private val dictation by lazy { container.newDictation() }
    private val _dictationState = MutableStateFlow<Dictation.State>(Dictation.State.Idle)
    val dictationState: StateFlow<Dictation.State> = _dictationState.asStateFlow()
    private var dictationWatch: kotlinx.coroutines.Job? = null

    fun startDictation() {
        if (dictationWatch == null) dictationWatch = viewModelScope.launch { dictation.state.collect { _dictationState.value = it } }
        dictation.start()
    }

    fun stopDictation() = dictation.stop()

    /** 받아쓴 글을 화면이 입력란에 옮긴 뒤 부른다. */
    fun dictationTaken() = dictation.reset()

    override fun onCleared() {
        if (dictationWatch != null) dictation.release()
    }

    /**
     * 최근 [MAX_EPISODES]번의 주행을 한 화씩 그린다. 회차는 저장된 주행 전체에서 센다.
     * 이웃한 두 화의 제목이 같지 않게 연재 순서대로 쓴다([CartoonWriter.series]). 맨 앞 하나 더는 비교용으로만 쓴다.
     */
    private fun episodes(trips: List<Trip>, answers: AnswerBook): List<EpisodeEntry> {
        val ordered = trips.sortedBy { it.startedAtMillis }
        val picked = ordered.indices.toList().takeLast(MAX_EPISODES + 1)
        val written = CartoonWriter.series(picked.map { tripSeen.of(ordered[it], ordered, answers.answersFor(ordered[it].startedAtMillis)) })
        return picked.zip(written).takeLast(MAX_EPISODES).reversed().map { (i, episode) ->
            EpisodeEntry(i + 1, ordered[i].endedAtMillis, episode, ordered[i])
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_EPISODES = 30
        private const val RECOMPUTE_INTERVAL_MILLIS = 60 * 60 * 1000L

        private fun ticker(periodMillis: Long): Flow<Unit> = flow {
            while (true) {
                emit(Unit)
                delay(periodMillis)
            }
        }

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(container) }
        }
    }
}
