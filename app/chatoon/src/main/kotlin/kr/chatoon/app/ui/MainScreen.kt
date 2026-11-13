package kr.chatoon.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kr.chatoon.app.ui.cartoon.AboutTab
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.chatoon.app.R
import kr.chatoon.app.data.consent.ConsentStatus
import kr.chatoon.app.ui.cartoon.SeriesTab
import kr.chatoon.app.ui.cartoon.DictationControl
import kr.chatoon.app.ui.cartoon.VoiceControl
import kr.chatoon.app.ui.cartoon.LightControl
import kr.chatoon.app.ui.cartoon.LightDirector
import kr.chatoon.app.domain.cartoon.Question
import kr.chatoon.app.domain.vehicle.DriveContext
import kr.chatoon.app.ui.cartoon.TodayTab
import kr.chatoon.app.ui.theme.LocalReduceMotion
import kr.chatoon.app.ui.theme.PleosMotion
import kr.chatoon.app.ui.theme.pleosTween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 탭: 한 번에 한 가지만 보여 준다(운전석에서 팔을 뻗어 보는 화면). */
enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    TODAY(R.string.tab_today, R.drawable.ic_nav_today),
    SERIES(R.string.tab_series, R.drawable.ic_nav_series),
    SETTINGS(R.string.tab_settings, R.drawable.ic_nav_settings),
    ABOUT(R.string.tab_about, R.drawable.ic_nav_about),
    ;

}

@Composable
fun MainScreen(viewModel: MainViewModel, onDecline: () -> Unit, onEnableSpeed: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    when (val consent = state.consent) {
        // 저장된 동의를 읽는 짧은 순간에는 빈 배경만 둔다(동의 화면이 깜빡 보이지 않게).
        ConsentStatus.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        is ConsentStatus.Required -> ConsentScreen(consent.termsUpdated || consent.unreadable, viewModel::grantConsent, onDecline, viewModel::withdrawConsent)
        is ConsentStatus.Granted -> MainContent(
            state = state,
            acceptedAtMillis = consent.acceptedAtMillis,
            notice = notice,
            onNoticeShown = viewModel::noticeShown,
            onWithdraw = viewModel::withdrawConsent,
            onEnableSpeed = onEnableSpeed,
            onAnswer = viewModel::answer,
            onSkipQuestion = viewModel::skipQuestion,
            dictation = DictationControl(
                state = viewModel.dictationState.collectAsStateWithLifecycle().value,
                start = viewModel::startDictation,
                stop = viewModel::stopDictation,
                taken = viewModel::dictationTaken,
            ),
            carPaint = viewModel.carPaint.collectAsStateWithLifecycle().value,
            onCarPaint = viewModel::setCarPaint,
            light = LightControl(
                on = viewModel.moodLightOn.collectAsStateWithLifecycle().value,
                set = viewModel::setMoodLight,
                show = viewModel::showLight,
                restore = viewModel::restoreLight,
            ),
            voice = VoiceControl(
                speaking = viewModel.speaking.collectAsStateWithLifecycle().value,
                speak = viewModel::speak,
                stop = viewModel::stopSpeaking,
                speakIfNew = viewModel::speakIfNew,
            ),
        )
    }
}

@Composable
private fun MainContent(
    state: MainUiState,
    acceptedAtMillis: Long,
    notice: MainViewModel.Notice?,
    onNoticeShown: () -> Unit,
    onWithdraw: () -> Unit,
    onEnableSpeed: () -> Unit,
    onAnswer: (Question, String) -> Unit,
    onSkipQuestion: (Question) -> Unit,
    dictation: DictationControl,
    voice: VoiceControl,
    light: LightControl,
    carPaint: String?,
    onCarPaint: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // 지난 화에서 마지막으로 펼친 화(회차). 조명 연출이 그 화의 색을 따른다.
    var openedEpisode by rememberSaveable { mutableStateOf<Int?>(null) }
    val parked = state.vehicle.driveContext == DriveContext.PARKED
    val lightEntry = when (tab) {
        Tab.TODAY -> state.episodes.firstOrNull()
        Tab.SERIES -> state.episodes.firstOrNull { it.number == openedEpisode }
        Tab.SETTINGS, Tab.ABOUT -> null
    }
    LightDirector(lightEntry.takeIf { light.on && parked }, light)
    // 탭마다 스크롤 위치를 따로 둔다(한 개를 같이 쓰면 다른 탭으로 옮겨도 앞 탭의 스크롤 위치가 따라온다).
    val scrolls: Map<Tab, ScrollState> = Tab.entries.associateWith { rememberScrollState() }
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    // 앞 화면이 100ms에 먼저 사라진 뒤 새 화면이 150ms에 나타난다(두 화면 글자가 겹쳐 보이는 순간이 없게). 전체 250ms.
    val tabExit = pleosTween<Float>(PleosMotion.SHORT1)
    val tabEnter = pleosTween<Float>(PleosMotion.SHORT2, delayMillis = PleosMotion.SHORT1)
    // 이미 보고 있는 탭을 다시 누르면 맨 위로 돌아간다(차량 제어 앱 메뉴와 같은 동작).
    val select: (Tab) -> Unit = { t ->
        if (t == tab) {
            scope.launch { if (reduceMotion) scrolls.getValue(t).scrollTo(0) else scrolls.getValue(t).animateScrollTo(0) }
        } else {
            tab = t
        }
    }
    // Pleos 창은 가로(landscape)와 세로(portrait, 화면 1:1 분할) 모두 될 수 있다. 좁으면 위쪽 세그먼트 메뉴로 바꾼다.
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= WideLayoutMinWidth
        val body: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                // 같은 단계 화면끼리는 자리 이동 없이 흐려졌다 나타나게 바꾼다(Google Design for Driving: 같은 단계 화면 전환은 crossfade).
                // 움직임 줄이기 설정이면 바로 바뀐다.
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { (fadeIn(tabEnter) togetherWith fadeOut(tabExit)).using(null) },
                    label = "tab",
                ) { shown ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrolls.getValue(shown)),
                    ) {
                        when (shown) {
                            Tab.TODAY -> TodayTab(state.episodes, state.question.takeIf { state.vehicle.driveContext == DriveContext.PARKED }, onAnswer, onSkipQuestion, dictation, state.speedAccess, onEnableSpeed, voice.takeIf { state.vehicle.driveContext == DriveContext.PARKED }, light.takeIf { parked })
                            Tab.SERIES -> SeriesTab(state.episodes, state.month, onToggle = { entry, open -> openedEpisode = if (open) entry.number else openedEpisode.takeIf { it != entry.number } })
                            Tab.ABOUT -> AboutTab()
                            Tab.SETTINGS -> SettingsTab(state.vehicle, acceptedAtMillis, state.totalTrips, onWithdraw, state.speedAccess, onEnableSpeed, light.on, light.set, carPaint, onCarPaint)
                        }
                    }
                }
                NoticeToast(notice, onNoticeShown, Modifier.align(Alignment.BottomCenter))
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                SideMenu(tab, onSelect = select)
                Box(Modifier.fillMaxHeight().width(TileGap).background(MaterialTheme.colorScheme.outline))
                Box(Modifier.weight(1f).fillMaxHeight()) { body() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopSegmentedMenu(tab, onSelect = select)
                Box(Modifier.weight(1f).fillMaxWidth()) { body() }
            }
        }
    }
}

/** 이 너비 이상이면 왼쪽 메뉴 목록, 아니면 위쪽 세그먼트 메뉴. */
private val WideLayoutMinWidth = 1000.dp

/** Pleos 차량 제어 앱과 같은 왼쪽 메뉴 목록: 아이콘 + 글, 선택하면 검정(어두운 테마는 흰) 바. */
@Composable
private fun SideMenu(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxHeight().width(280.dp).background(MaterialTheme.colorScheme.surface)) {
        BrandHeader(Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 28.dp, bottom = 24.dp))
        Tab.entries.filter { it != Tab.ABOUT }.forEach { t -> MenuItem(t, t == selected, onSelect, Modifier.fillMaxWidth().height(96.dp)) }
        Spacer(Modifier.weight(1f))
        // 소개는 자주 쓰는 탭이 아니라서 아래에 따로 둔다.
        MenuItem(Tab.ABOUT, selected == Tab.ABOUT, onSelect, Modifier.fillMaxWidth().height(96.dp))
    }
}

/** 앱 정체성: 아이콘, 이름, 한 줄 소개. 앱을 처음 보는 사람도 무엇을 하는 앱인지 알 수 있게 메뉴 맨 위에 둔다. */
@Composable
fun BrandHeader(modifier: Modifier = Modifier, big: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 24.dp else 14.dp)) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            null,
            Modifier.size(if (big) 96.dp else 60.dp).clip(RoundedCornerShape(if (big) 24.dp else 16.dp)).background(Color(0xFFFFD11A)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 테마에는 headline 크기가 없어(기본값이 차량 화면에 작다) 제목 크기를 여기서 정한다. 한 줄 소개도 24sp 아래로 두지 않는다.
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = if (big) 52.sp else 36.sp),
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.app_tagline),
                style = if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Pleos Segmented menu(Box, Fitted): 좁은 창에서 쓰는 위쪽 탭. */
@Composable
private fun TopSegmentedMenu(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(TileGap, MaterialTheme.colorScheme.outline),
    ) {
        Tab.entries.forEach { t -> MenuItem(t, t == selected, onSelect, Modifier.weight(1f).height(80.dp), centered = true) }
    }
}

@Composable
private fun MenuItem(tab: Tab, selected: Boolean, onSelect: (Tab) -> Unit, modifier: Modifier, centered: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    // 선택 바는 150ms 동안 색만 바뀐다(바가 미끄러져 움직이면 시선을 끈다). 선택 안 된 항목은 누르는 동안 타일색으로(Pleos Pressed).
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.inverseSurface
            pressed -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        pleosTween(PleosMotion.SHORT2),
        label = "menuBackground",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        pleosTween(PleosMotion.SHORT2),
        label = "menuContent",
    )
    Row(
        modifier
            .background(background)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = withHaptic { onSelect(tab) })
            .pressShrink(interaction)
            // 위쪽 세그먼트 메뉴(좁은 창, Pleos 1:1 분할이면 약 840dp → 한 칸 약 200dp)는 여백을 줄여 아이콘 + 네 글자가 한 줄에 들어가게 한다.
            .padding(horizontal = if (centered) 12.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(20.dp),
    ) {
        Icon(painterResource(tab.icon), null, Modifier.size(36.dp), tint = content)
        Text(stringResource(tab.label), style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1, softWrap = false)
    }
}

/**
 * Pleos Toast: 좌우·아래 24dp, 최대 폭 792dp. 5초 뒤 사라진다.
 * 아래 가장자리에서 짧게 올라오며 나타나고(250ms) 더 짧게(150ms) 사라진다(Google Design for Driving: 막지 않는 짧은 알림은
 * 화면 가장자리에서 밀어 올린다). 화면 낭독기는 나타날 때 한 번 읽는다.
 */
@Composable
private fun NoticeToast(notice: MainViewModel.Notice?, onShown: () -> Unit, modifier: Modifier) {
    // 사라지는 동안에도 글이 남아 있게 마지막 알림을 기억한다(화면을 다시 그리게 하는 상태가 아니라 단순 보관).
    val last = remember { arrayOfNulls<MainViewModel.Notice>(1) }
    if (notice != null) last[0] = notice
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        delay(5_000)
        onShown()
    }
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = fadeIn(pleosTween(PleosMotion.MEDIUM1)) + slideInVertically(pleosTween(PleosMotion.MEDIUM1)) { it / 2 },
        exit = fadeOut(pleosTween(PleosMotion.SHORT2)) + slideOutVertically(pleosTween(PleosMotion.SHORT2)) { it / 2 },
    ) {
        val shown = last[0] ?: return@AnimatedVisibility
        Text(
            stringResource(
                when (shown) {
                    MainViewModel.Notice.CONSENT_NOT_SAVED -> R.string.consent_not_saved
                },
            ),
            Modifier
                .padding(24.dp)
                .widthIn(max = 792.dp)
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(8.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}
