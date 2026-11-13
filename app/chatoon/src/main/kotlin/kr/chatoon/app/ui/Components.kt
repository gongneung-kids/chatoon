package kr.chatoon.app.ui

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.chatoon.app.R
import kr.chatoon.app.ui.theme.LocalAppColors
import kr.chatoon.app.ui.theme.PleosMotion
import kr.chatoon.app.ui.theme.StatusColors
import kr.chatoon.app.ui.theme.pleosTween

/*
 * Pleos 디자인 언어를 따르는 공통 부품.
 * - 타일: 평평한 사각 영역(둥근 카드·그림자 없음), 타일끼리는 얇은 간격으로 구분
 * - 누르면 95%로 줄어들고 배경색이 한 단계 바뀐다(Pleos Interaction: Button interaction).
 *   움직임은 Pleos 모션 토큰(theme/Motion.kt)만 쓴다.
 * - 상태는 색만으로 전하지 않는다: 아이콘 + 글
 */

/** 기본 타일. Pleos 차량 제어 앱처럼 평평한 사각 영역. */
@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 28.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(padding),
        content = content,
    )
}

/** 화면 낭독기(TalkBack)가 제목만 골라 건너뛸 수 있게 표시한다(G7). */
fun Modifier.asHeading(): Modifier = semantics { heading() }

/** 타일 하나(이름 + 값)를 낭독기가 한 번에 읽게 묶는다(G7). 안에 버튼이 있는 타일에는 쓰지 않는다. */
fun Modifier.readAsOne(): Modifier = semantics(mergeDescendants = true) {}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.asHeading(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(16.dp))
}

/**
 * Pleos 버튼 상호작용: 누르는 동안 가운데를 기준으로 95%로 줄어든다(Pleos Interaction 그림: Scale 95%, Origin Center/Middle).
 * 0.25초 안에 반응해야 하므로(Pleos Introduction: 즉시성) 가장 짧은 토큰(100ms)을 쓴다.
 */
@Composable
fun Modifier.pressShrink(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, pleosTween(PleosMotion.SHORT1), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 누르는 동안 [normal] 대신 [pressed] 색(Pleos: Pressed = scale down + background color by Mode). */
@Composable
internal fun pressedColor(interaction: MutableInteractionSource, normal: Color, pressed: Color): Color {
    val isPressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(if (isPressed) pressed else normal, pleosTween(PleosMotion.SHORT1), label = "pressedColor")
    return color
}

/**
 * Pleos Basic Button(주 버튼): 검정(어두운 테마는 흰) 바탕 + 흰 글씨, 높이 72dp 이상.
 * [loading]이면 진행 표시를 보이고 누를 수 없다(Pleos: Loading 상태).
 */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val content = MaterialTheme.colorScheme.inverseOnSurface
    // 누르면 바탕이 글자색 쪽으로 16% 옅어진다(흰 테마: 검정→진회색, 블랙 테마: 흰→연회색). 글자 대비는 10:1 이상 유지.
    val container = pressedColor(interaction, MaterialTheme.colorScheme.inverseSurface, lerp(MaterialTheme.colorScheme.inverseSurface, content, 0.16f))
    Box(
        modifier
            .pressShrink(interaction)
            .heightIn(min = 72.dp)
            .background(container, RoundedCornerShape(8.dp))
            .clickable(interaction, indication = null, enabled = !loading, role = Role.Button, onClick = withHaptic(onClick))
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            // Pleos Loading: 옅은 원 트랙 위로 호가 돈다.
            CircularProgressIndicator(Modifier.size(32.dp), color = content, strokeWidth = 3.dp, trackColor = content.copy(alpha = 0.24f))
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium, color = content)
        }
    }
}

/**
 * 누를 때 진동 피드백을 준다(Pleos 디자인 가이드: "터치 시 진동 피드백(Haptic Feedback)이나 음성 피드백을 제공").
 * 앱의 모든 버튼과 탭이 이것을 거친다.
 */
@Composable
fun withHaptic(onClick: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onClick()
    }
}

/** 보조 버튼: 테두리만(위험한 동작은 오류색 글씨). */
@Composable
fun OutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val interaction = remember { MutableInteractionSource() }
    // 누르는 동안 글자색을 8% 깐 바탕(타일·흰 바탕 어디서나 한 단계 어둡게/밝게).
    val pressedFill = pressedColor(interaction, Color.Transparent, color.copy(alpha = 0.08f))
    Box(
        modifier
            .pressShrink(interaction)
            .heightIn(min = 72.dp)
            .background(pressedFill, RoundedCornerShape(8.dp))
            .border(2.dp, LocalAppColors.current.controlBorder, RoundedCornerShape(8.dp))
            .clickable(interaction, indication = null, role = Role.Button, onClick = withHaptic(onClick))
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** 상태 표시: 배경 없이 아이콘 + 글(Pleos 차량 제어 앱의 "열림/잠김" 표시처럼). */
@Composable
fun StatusChip(@DrawableRes icon: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** 타일 사이 간격(Pleos 타일 그리드의 얇은 구분선 역할). */
val TileGap = 2.dp

/**
 * Pleos System popup 규격의 팝업: 제목 + 설명(본문) + 가로 정렬 버튼(취소·확인).
 * Material 기본 대화상자 대신 앱 디자인(흑백, 큰 버튼)을 쓴다. [destructive]면 확인 버튼을 오류색으로.
 */
@Composable
fun PleosPopup(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
    body: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)) // 블랙 테마에서 뒤 화면과 구분되게 한 단계 밝게
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(title, Modifier.asHeading(), style = MaterialTheme.typography.titleLarge)
            body()
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlineButton(dismissLabel, onDismiss, Modifier.weight(1f))
                if (destructive) {
                    OutlineButton(confirmLabel, onConfirm, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                } else {
                    PrimaryButton(confirmLabel, onConfirm, Modifier.weight(1f))
                }
            }
        }
    }
}
