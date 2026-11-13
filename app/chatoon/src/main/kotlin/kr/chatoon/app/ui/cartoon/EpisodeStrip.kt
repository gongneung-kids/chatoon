package kr.chatoon.app.ui.cartoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chatoon.app.R
import kr.chatoon.app.domain.cartoon.Cut
import kr.chatoon.app.domain.cartoon.Episode
import kr.chatoon.app.domain.cartoon.Place
import kr.chatoon.app.domain.cartoon.Rain
import kr.chatoon.app.domain.cartoon.TimeOfDay

/**
 * 한 화의 네 컷을 2×2(4분면)로 크게 놓는다. 넓은 창에서 한 줄에 네 컷을 놓으면 그림이 작아 팔 길이에서 잘 안 보였다
 * (2026-11-12 사용자 요청). 만화책처럼 왼쪽 위 → 오른쪽 위 → 왼쪽 아래 → 오른쪽 아래 순서로 읽는다.
 * 다만 높이가 모자라(질문 카드가 떠 있음) 한 줄에 놓는 편이 컷이 더 크면 1×4로 놓는다. 어느 쪽이든 큰 쪽을 고른다.
 */
@Composable
fun EpisodeStrip(episode: Episode, modifier: Modifier = Modifier, maxHeight: Dp? = null) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val colGap = 24.dp
        val rowGap = 20.dp
        // 2×2: 칸 폭은 창 폭의 절반. 높이가 주어지면 두 줄(그림 + 글 칸)이 그 안에 들어가도록 줄인다(2026-11-12 사용자 요청: 한 화면에 한 번에).
        val twoByTwo = (maxWidth - colGap) / 2
        val cell2 = if (maxHeight == null) twoByTwo else minOf(twoByTwo, ((maxHeight - rowGap) / 2 - CAPTION_HEIGHT) * (4f / 3f))
        // 1×4: 질문 카드가 떠서 높이가 모자라면 2×2 칸이 좁아져 글이 잘린다. 그때 한 줄에 네 컷이 더 크면 한 줄로 놓는다.
        // 한 줄이면 아래에 자리가 남으므로 글 칸 높이를 고정하지 않고 글을 끝까지 보인다(맺음말이 잘리지 않게).
        val oneRow = (maxWidth - colGap * 3) / 4
        val cell4 = if (maxHeight == null) 0.dp else minOf(oneRow, (maxHeight - CAPTION_HEIGHT) * (4f / 3f))
        val perRow = if (cell4 > cell2) 4 else 2
        val cell = (if (perRow == 4) cell4 else cell2).coerceAtLeast(MIN_CELL)
        Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
            episode.cuts.chunked(perRow).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(colGap)) {
                    row.forEachIndexed { index, cut ->
                        val number = rowIndex * perRow + index + 1
                        CutCard(cut, number, episode.time, episode.rain, Modifier.width(cell), fixedCaption = maxHeight != null && perRow == 2, place = episode.place, endStyle = endStyleAt(episode.cuts, number - 1))
                    }
                }
            }
        }
    }
}

/**
 * 컷 아래 글 칸의 높이: 일기 세 줄 + 근거 두 줄과 사이 간격. 칸 높이를 고정해 네 컷의 줄이 맞는다.
 * 질문 카드가 떠서 컷이 좁아져도 맺음말(차의 생각)이 잘리지 않게 일기는 세 줄까지, 근거(이 앱의 신뢰 장치)는 두 줄까지 둔다.
 */
private val CAPTION_HEIGHT = 186.dp

/** 아주 작은 창에서도 이보다 작게는 그리지 않는다(그때는 스크롤). */
private val MIN_CELL = 280.dp

/** 컷 한 장과 그 아래의 글, 근거. */
@Composable
fun CutCard(cut: Cut, number: Int?, time: TimeOfDay, rain: Rain, modifier: Modifier = Modifier, fixedCaption: Boolean = false, place: Place? = null, endStyle: EndStyle = EndStyle.CLOSE) {
    val colors = cartoonColors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            PanelView(cut, time, rain, Modifier.fillMaxWidth(), place = place, endStyle = endStyle)
            if (number != null) {
                Text(
                    number.toString(),
                    // 컷 순서를 알려 주는 표시. 화면 읽기에는 글(캡션)이 순서대로 읽히므로 따로 읽지 않는다.
                    modifier = Modifier.background(colors.ink).padding(horizontal = 10.dp, vertical = 2.dp).clearAndSetSemantics { },
                    color = colors.paper,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // 고정 칸이면 일기는 세 줄, 근거는 두 줄까지(넘치면 말줄임)이고, 칸 높이를 고정해 남는 자리는 아래로 둔다.
        Column(if (fixedCaption) Modifier.height(CAPTION_HEIGHT - 8.dp) else Modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            cut.caption,
            Modifier.semantics { contentDescription = cut.caption },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = if (fixedCaption) 3 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            cut.proof?.let { stringResource(R.string.cartoon_proof, it) } ?: stringResource(R.string.cartoon_thought),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (fixedCaption) 2 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        }
    }
}
