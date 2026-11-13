package kr.chatoon.app.ui.cartoon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.chatoon.app.R
import kr.chatoon.app.domain.cartoon.Question
import kr.chatoon.app.data.speech.Dictation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kr.chatoon.app.domain.cartoon.Seen
import kr.chatoon.app.domain.cartoon.TimeOfDay
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import kr.chatoon.app.ui.EpisodeEntry
import androidx.compose.ui.graphics.graphicsLayer
import kr.chatoon.app.ui.MonthSummary
import kr.chatoon.app.ui.SpeedAccess
import kr.chatoon.app.ui.SpeedAccessCard
import kr.chatoon.app.ui.Card
import kr.chatoon.app.ui.OutlineButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kr.chatoon.app.domain.cartoon.CartoonWriter
import kr.chatoon.app.domain.cartoon.Jolt
import kr.chatoon.app.domain.cartoon.Rain
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
private val DATE = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

private fun dateOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZONE).format(DATE)

/**
 * 이번 화: 가장 최근 주행을 차가 그린 네 컷.
 * [question]이 있으면(주차 중일 때만 넘어온다) 맨 위에서 차가 묻는다. 답하면 그 화가 다시 그려진다.
 */
@Composable
fun TodayTab(entries: List<EpisodeEntry>, question: Question?, onAnswer: (Question, String) -> Unit, onSkip: (Question) -> Unit, dictation: DictationControl, speedAccess: SpeedAccess, onEnableSpeed: () -> Unit, voice: VoiceControl?, light: LightControl?) {
    // 질문 카드가 있으면 그 높이만큼 그림을 줄여, 질문과 네 컷이 한 화면에 함께 들어오게 한다(2026-11-12 사용자 기준: 2×2를 스크롤 없이).
    var questionHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Column(Modifier.padding(horizontal = 28.dp, vertical = TODAY_PADDING), verticalArrangement = Arrangement.spacedBy(TODAY_GAP)) {
        if (question != null) {
            QuestionCard(question, onAnswer, onSkip, dictation, Modifier.onSizeChanged { questionHeight = with(density) { it.height.toDp() } })
        }
        val asking = if (question != null) questionHeight + TODAY_GAP else 0.dp
        val latest = entries.firstOrNull()
        if (latest == null) {
            // 내 만화가 아직 없다는 것을 먼저 알리고, 그 아래에 쓰는 법과 예시를 둔다(예시를 내 기록으로 오해하지 않게).
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFD11A)).padding(horizontal = 28.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(stringResource(R.string.cartoon_empty_title), color = Color(0xFF231F1A), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.cartoon_empty_body), color = Color(0xFF231F1A), style = MaterialTheme.typography.bodyMedium)
            }
            CartoonIntro()
            // 첫 주행 전에 켜 두어야 첫 화부터 놀란 순간을 그릴 수 있다.
            if (speedAccess != SpeedAccess.GRANTED) SpeedAccessCard(speedAccess, onEnableSpeed, R.string.cartoon_speed_off)
            return@Column
        }
        // 주차 중에 새 화를 처음 보면 차가 한마디를 소리로 한 번 읽는다(voice는 주차 중에만 넘어온다).
        if (voice != null) LaunchedEffect(latest.endedAtMillis) { voice.speakIfNew(latest) }
        // 이전 화·다음 화로 넘겨 보기(연재처럼). 새 화가 오면 다시 최신 화로 돌아온다.
        var back by rememberSaveable(latest.number) { mutableStateOf(0) }
        val shown = entries.getOrNull(back) ?: latest
        // 한 화면에 네 컷이 다 들어오게: 창 높이에서 위아래 여백과 두 줄짜리 머리글을 뺀 만큼을 그림에 준다.
        val windowHeight = LocalConfiguration.current.screenHeightDp.dp
        TodayHeader(
            shown,
            voice,
            onOlder = { back += 1 }.takeIf { back < entries.lastIndex },
            onNewer = { back -= 1 }.takeIf { back > 0 },
        )
        EpisodeStrip(shown.episode, maxHeight = windowHeight - TODAY_PADDING * 2 - TODAY_HEADER - TODAY_GAP - asking)
        // 속도 권한이 없으면 급하게 선 순간을 그릴 수 없다. 만화 아래에 조용히 알린다.
        if (speedAccess != SpeedAccess.GRANTED) SpeedAccessCard(speedAccess, onEnableSpeed, R.string.cartoon_speed_off)
        // 조명 연출을 아직 켜지 않았으면, 주차 중에 한 번 권한다(설정에서도 켜고 끌 수 있다).
        if (light != null && !light.on) LightOfferCard(light)
    }
}

/** 차툰 소개 탭: 언제든 이 앱이 무엇을 하는지 다시 볼 수 있다. */
@Composable
fun AboutTab() {
    Column(Modifier.padding(28.dp)) { CartoonIntro() }
}

/** 지난 화: 회차 목록. 누르면 그 자리에서 네 컷이 펼쳐진다. */
@Composable
fun SeriesTab(entries: List<EpisodeEntry>, month: MonthSummary? = null, onToggle: (EpisodeEntry, Boolean) -> Unit = { _, _ -> }) {
    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (entries.isEmpty()) {
            EmptyEpisode()
            return@Column
        }
        // 이번 달에 운전한 시간이 몇 화로 남았는지. 그냥 이동한 시간으로 사라질 뻔한 시간을 숫자로 보여 준다.
        if (month != null) MonthCard(month)
        // 주(월~일)마다 묶고, 머리에 그 주에 운전한 시간과 화 수.
        for ((week, inWeek) in entries.groupBy { weekOf(it.endedAtMillis) }) {
            val driven = inWeek.sumOf { it.trip?.durationMillis ?: 0L }
            Text(
                stringResource(R.string.series_week, weekLabel(week), drivingTime(driven), inWeek.size),
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            for (entry in inWeek) SeriesRow(entry, onToggle)
        }
    }
}

@Composable
private fun MonthCard(month: MonthSummary) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFD11A)).padding(horizontal = 28.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.series_month, month.month, drivingTime(month.drivingMillis), month.trips),
            color = Color(0xFF231F1A),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 그 주의 월요일(서울 날짜). */
private fun weekOf(millis: Long): java.time.LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

private fun weekLabel(monday: java.time.LocalDate): String {
    val sunday = monday.plusDays(6)
    return if (monday.month == sunday.month) "${monday.monthValue}월 ${monday.dayOfMonth}일~${sunday.dayOfMonth}일"
    else "${monday.monthValue}월 ${monday.dayOfMonth}일~${sunday.monthValue}월 ${sunday.dayOfMonth}일"
}

@Composable
private fun SeriesRow(entry: EpisodeEntry, onToggle: (EpisodeEntry, Boolean) -> Unit) {
    var open by rememberSaveable(entry.number) { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.cartoon_fold else R.string.cartoon_unfold)) {
                open = !open
                onToggle(entry, open)
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // 그 화의 핵심 컷(세우는 순간 보여 주는 컷)을 작은 표지로.
            PanelView(entry.episode.now, entry.episode.time, entry.episode.rain, Modifier.width(200.dp), place = entry.episode.place)
            Column(Modifier.weight(1f).heightIn(min = 64.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EpisodeHeader(entry, big = false)
            }
            // 「펼치기」 글자 대신 화살표(카드 전체를 누르면 펼쳐진다). 화면 낭독기는 카드의 누르기 설명을 읽는다.
            androidx.compose.material3.Icon(
                painterResource(R.drawable.ic_chevron_down),
                null,
                Modifier.size(40.dp).graphicsLayer { rotationZ = if (open) 180f else 0f },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(open) { EpisodeStrip(entry.episode) }
    }
}

/** 회차 표시, 제목, 날짜, 차가 한 말. */
@Composable
private fun EpisodeHeader(entry: EpisodeEntry, big: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (big) 10.dp else 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.cartoon_episode_number, entry.number),
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                dateOf(entry.endedAtMillis) + " · " + stringResource(timeLabel(entry.episode.time)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.trip?.let { FactChip(drivingTime(it.durationMillis) + " 운전") }
        }
        Text(
            entry.episode.title,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (big) Text(entry.episode.line.let { if (it.startsWith("“")) it else "“$it”" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun timeLabel(time: TimeOfDay): Int = when (time) {
    TimeOfDay.DAY -> R.string.cartoon_time_day
    TimeOfDay.DUSK -> R.string.cartoon_time_dusk
    TimeOfDay.NIGHT -> R.string.cartoon_time_night
}

/**
 * 차툰 소개: 왜 쓰는지 한 줄, 쓰는 법 세 단계, 예시 네 컷을 한 화면에 보여 준다.
 * 처음 실행(동의 전), 아직 그린 만화가 없을 때(이번 화), '차툰 소개' 탭에서 같은 내용을 쓴다.
 * 예시는 규칙 엔진이 정해진 재료로 그린 것이고, "예시"라고 밝힌다(주인의 기록이 아니다).
 */
@Composable
fun CartoonIntro() {
    val sample = remember { CartoonWriter.write(WELCOME_SAMPLE) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 세 단계 카드는 높이를 맞춘다(좁은 창에서 글 줄 수가 달라도 들쭉날쭉하지 않게).
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WelcomeStep(1, R.string.welcome_step1_title, R.string.welcome_step1_body, Modifier.weight(1f).fillMaxHeight())
            WelcomeStep(2, R.string.welcome_step2_title, R.string.welcome_step2_body, Modifier.weight(1f).fillMaxHeight())
            WelcomeStep(3, R.string.welcome_step3_title, R.string.welcome_step3_body, Modifier.weight(1f).fillMaxHeight())
        }
        Text(stringResource(R.string.welcome_sample_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        // 예시는 한 줄에 네 컷으로 작게(한 화면에 안내와 함께 들어오게).
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 16.dp
            val cell = (maxWidth - gap * 3) / 4
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                sample.cuts.forEachIndexed { i, cut ->
                    // 근거 줄이 이 앱의 핵심이라 말줄임 없이 다 보이게 한다(칸이 좁아 두 줄이 될 수 있다).
                    CutCard(cut, i + 1, sample.time, sample.rain, Modifier.width(cell), place = sample.place, endStyle = endStyleAt(sample.cuts, i))
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(number: Int, title: Int, body: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                number.toString(),
                Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 12.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 첫 안내의 예시 재료: 비 오는 해 질 녘, 창문을 내리고 달리다 한 번 급하게 선 날(차툰이 남기는 순간들이 한 화에). */
private val WELCOME_SAMPLE = Seen(
    day = "금요일", time = TimeOfDay.DUSK, hour = 18, rain = Rain.RAIN, parkShifts = 1, usualParkShifts = 1,
    windowsAllDown = true, jolt = Jolt.BRAKE,
)

@Composable
private fun EmptyEpisode() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cartoon_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.cartoon_empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 차의 질문. 보기를 누르거나 직접 적어 답한다. 주차 중에만 보이므로 글자를 쳐도 된다.
 * 누를 곳은 모두 64dp 이상(차량 화면 터치 크기).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(question: Question, onAnswer: (Question, String) -> Unit, onSkip: (Question) -> Unit, dictation: DictationControl, modifier: Modifier = Modifier) {
    var typed by rememberSaveable(question.tripStartMillis) { mutableStateOf("") }
    // 받아쓴 글은 바로 저장하지 않고 입력란에 옮긴다(잘못 들었으면 고치거나 다시 말할 수 있게).
    val state = dictation.state
    LaunchedEffect(state) {
        if (state is Dictation.State.Done) {
            if (state.text.isNotBlank()) typed = Seen.trimAnswer(state.text)
            dictation.taken()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (typed.isNotBlank()) {
            keyboard?.hide()
            onAnswer(question, typed)
        }
    }
    // 세 줄로 줄였다: 차의 질문 / 보기 / 말로 답하기·직접 적기·답하기·넘기기. 그래야 아래 네 컷이 너무 작아지지 않는다.
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFD11A)))
            Text(stringResource(R.string.cartoon_ask_from_car), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(question.lines.joinToString(" "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (choice in question.suggestions) {
                Text(
                    choice,
                    Modifier
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFFFFD11A))
                        .clickable(role = Role.Button) { onAnswer(question, choice) }
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    color = Color(0xFF231F1A),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DictationButton(state, dictation)
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(Seen.MAX_ANSWER_LENGTH) },
                modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(stringResource(R.string.cartoon_ask_placeholder), style = MaterialTheme.typography.bodyLarge) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Text(
                stringResource(R.string.cartoon_ask_send),
                Modifier
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (typed.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.inverseSurface)
                    .clickable(enabled = typed.isNotBlank(), role = Role.Button) { submit() }
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                color = if (typed.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(R.string.cartoon_ask_skip),
                Modifier
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { onSkip(question) }
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        DictationNote(state)
    }
}

/** 말로 답하기를 화면에 넘기는 묶음. */
class DictationControl(val state: Dictation.State, val start: () -> Unit, val stop: () -> Unit, val taken: () -> Unit)

/** "말로 답하기" 버튼. 듣는 중에는 "그만"으로 바뀐다. */
@Composable
private fun DictationButton(state: Dictation.State, dictation: DictationControl) {
    val listening = state is Dictation.State.Starting || state is Dictation.State.Listening
    Text(
        stringResource(if (listening) R.string.cartoon_dictation_stop else R.string.cartoon_dictation_start),
        Modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inverseSurface)
            .clickable(role = Role.Button) { if (listening) dictation.stop() else dictation.start() }
            .padding(horizontal = 32.dp, vertical = 20.dp),
        color = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inverseOnSurface,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** 받아쓰는 동안의 안내(준비 중, 듣는 중, 들은 글, 실패). 평소에는 자리를 차지하지 않는다. */
@Composable
private fun DictationNote(state: Dictation.State) {
    val note = when (state) {
        Dictation.State.Starting -> stringResource(R.string.cartoon_dictation_starting)
        is Dictation.State.Listening -> state.heard.ifBlank { stringResource(R.string.cartoon_dictation_listening) }
        Dictation.State.Failed -> stringResource(R.string.cartoon_dictation_failed)
        else -> null
    }
    if (note != null) {
        Text(
            note,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 차의 목소리를 화면에 넘기는 묶음. 주차 중이 아니면 화면에 넘기지 않는다(null). */
class VoiceControl(val speaking: Boolean, val speak: (EpisodeEntry) -> Unit, val stop: () -> Unit, val speakIfNew: (EpisodeEntry) -> Unit)

/** "차의 한마디 듣기" / 말하는 중이면 "멈추기". */
@Composable
private fun ListenButton(entry: EpisodeEntry, voice: VoiceControl) {
    Text(
        stringResource(if (voice.speaking) R.string.cartoon_voice_stop else R.string.cartoon_voice_listen),
        Modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(if (voice.speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button) { if (voice.speaking) voice.stop() else voice.speak(entry) }
            .padding(horizontal = 28.dp, vertical = 16.dp),
        color = if (voice.speaking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** 이전 화·다음 화 화살표. 누를 곳은 64dp(차량 화면 터치 크기). 넘길 화가 없으면 흐리게 두고 누를 수 없다. */
@Composable
private fun PageArrow(label: Int, rotation: Float, onClick: (() -> Unit)?) {
    val description = stringResource(label)
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = onClick != null, role = Role.Button, onClickLabel = description) { onClick?.invoke() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            painterResource(R.drawable.ic_chevron_down),
            null,
            Modifier.size(32.dp).graphicsLayer { rotationZ = rotation },
            tint = if (onClick != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

/** 실내 조명 연출을 화면에 넘기는 묶음. */
class LightControl(val on: Boolean, val set: (Boolean) -> Unit, val show: (EpisodeEntry) -> Unit, val restore: () -> Unit)

/**
 * 보고 있는 화의 장면으로 실내 조명을 맞춘다. [entry]가 null이면(다른 탭, D단, 꺼짐) 바꾸기 전 조명으로 되돌린다.
 * 앱이 뒤로 가면(onStop) 되돌리고, 다시 앞으로 오면 다시 맞춘다. 이 화면이 사라질 때(동의 철회 등)도 되돌린다.
 */
@Composable
fun LightDirector(entry: EpisodeEntry?, light: LightControl) {
    // 화면이 안 보이면 Compose는 다시 그리지 않으므로, 앱이 뒤로 갈 때 되돌리는 일은 생명주기 이벤트에서 바로 한다
    // (2026-11-12 에뮬레이터: 다른 앱이 앞에 와도 재구성이 멈춰 조명이 그대로 남던 것).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(entry)
    LaunchedEffect(entry?.endedAtMillis) {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@LaunchedEffect
        val shown = entry
        if (shown != null) light.show(shown) else light.restore()
    }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> light.restore()
                androidx.lifecycle.Lifecycle.Event.ON_START -> current?.let(light.show)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            light.restore()
        }
    }
}

@Composable
private fun LightOfferCard(light: LightControl) {
    Card(Modifier.fillMaxWidth(), padding = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                stringResource(R.string.cartoon_light_offer),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlineButton(stringResource(R.string.cartoon_light_on), { light.set(true) })
        }
    }
}

private val TODAY_PADDING = 20.dp
private val TODAY_GAP = 16.dp
/** 이번 화 머리글 두 줄(회차·제목·듣기 버튼 / 날짜·차의 한마디)의 높이. */
private val TODAY_HEADER = 112.dp

/** 이번 화 머리글: 한 줄에 회차·제목·듣기 버튼, 다음 줄에 날짜와 차의 한마디. 그림이 크게 들어갈 자리를 남긴다. */
@Composable
private fun TodayHeader(entry: EpisodeEntry, voice: VoiceControl?, onOlder: (() -> Unit)? = null, onNewer: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().heightIn(max = TODAY_HEADER), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 누를 곳(듣기·이전 화·다음 화) 사이는 24dp 넘게 띄운다(Android for Cars 품질 지침 UX-2 참고치).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                stringResource(R.string.cartoon_episode_number, entry.number),
                Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 12.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(entry.episode.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (voice != null) ListenButton(entry, voice)
            PageArrow(R.string.cartoon_older, rotation = 90f, onClick = onOlder)
            PageArrow(R.string.cartoon_newer, rotation = -90f, onClick = onNewer)
        }
        // 이 화가 된 운전 시간과 그때 차가 본 것. 사라질 뻔한 운전 시간이 숫자로 남는다(만든 이유). 넘치면 뒤쪽 칩을 놓지 않는다.
        OneLine(Modifier.fillMaxWidth()) {
            Text(
                dateOf(entry.endedAtMillis) + " · " + stringResource(timeLabel(entry.episode.time)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            entry.trip?.let { trip ->
                FactChip(driveLine(trip), strong = true)
                for (fact in seenFacts(trip)) FactChip(fact)
            }
        }
    }
}
