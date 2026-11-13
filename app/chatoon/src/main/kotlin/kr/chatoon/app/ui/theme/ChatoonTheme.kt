package kr.chatoon.app.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.io.File

/*
 * 차툰 테마: 만화 종이. 주간은 따뜻한 미색 종이와 먹색 글씨, 야간은 따뜻한 먹색 바탕과 종이색 글씨.
 * 강조색은 주인공 차의 색(산호빨강) 하나. 만화 속 별색 노랑(CartoonColors.spot)은 그림 안에서만 쓴다.
 * 레이아웃은 Pleos 기본 앱과 같은 언어(왼쪽 메뉴, 선택은 먹색 바)를 그대로 둔다.
 * 모든 글자색은 바탕·타일 대비 4.5:1 이상, 테두리는 3:1 이상을 WCAG 식으로 계산해 확인했다(2026-11-12).
 */

private val Light = lightColorScheme(
    primary = Color(0xFFB23A25), // 종이 5.8:1, 타일 5.1:1, 흰 글자 6.0:1
    onPrimary = Color.White,
    background = Color(0xFFFFFBF2),
    onBackground = Color(0xFF231F1A), // 15.9:1
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF231F1A),
    surfaceVariant = Color(0xFFF5ECDA), // 타일
    onSurfaceVariant = Color(0xFF6A5F52), // 종이 6.0:1, 타일 5.3:1
    outline = Color(0xFFE8DCC4), // 구분선
    inverseSurface = Color(0xFF231F1A), // 선택된 메뉴·주 버튼
    inverseOnSurface = Color(0xFFFFFBF2),
    error = Color(0xFFB3261E),
    // Material 기본 보라 기운이 섞이지 않게 표면 색을 모두 지정한다.
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFBF2),
    surfaceContainerLow = Color(0xFFFFFBF2),
    surfaceContainer = Color(0xFFFFFBF2),
    surfaceContainerHigh = Color(0xFFFFFBF2),
    surfaceContainerHighest = Color(0xFFF5ECDA),
    secondaryContainer = Color(0xFFF5ECDA),
    onSecondaryContainer = Color(0xFF231F1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A6B), // 바탕 8.1:1, 타일 6.9:1
    onPrimary = Color(0xFF231F1A), // 7.1:1
    background = Color(0xFF14120F),
    onBackground = Color(0xFFF6F0E5), // 16.5:1
    surface = Color(0xFF14120F),
    onSurface = Color(0xFFF6F0E5),
    surfaceVariant = Color(0xFF26221C),
    onSurfaceVariant = Color(0xFFB3A898), // 바탕 8.0:1, 타일 6.8:1
    outline = Color(0xFF3A342B),
    inverseSurface = Color(0xFFF6F0E5),
    inverseOnSurface = Color(0xFF231F1A),
    error = Color(0xFFFF8A80),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF14120F),
    surfaceContainerLow = Color(0xFF1B1814),
    surfaceContainer = Color(0xFF26221C),
    surfaceContainerHigh = Color(0xFF26221C),
    surfaceContainerHighest = Color(0xFF302B24),
    secondaryContainer = Color(0xFF302B24),
    onSecondaryContainer = Color(0xFFF6F0E5),
)

/** 판정 상태·그래프 색. 경고색(빨강·주황)은 쓰지 않는다: 달라짐 = 파랑(colorScheme.primary), 좋아짐 = 초록. */
@Immutable
data class AppColors(
    val improved: Color,
    val neutral: Color,
    /** 그래프 데이터(최근 주·실제 비율). */
    val series: Color,
    /** 그래프 기준·예상(참고). */
    val reference: Color,
    val grid: Color,
    /** 그래프 점 바깥 테두리(카드 배경색과 같게). */
    val plotSurface: Color,
    /** 외곽선 버튼 테두리. 버튼이 놓이는 모든 바탕(바탕·타일·창)과 3:1 이상(WCAG 1.4.11). */
    val controlBorder: Color,
)

private val LightAppColors = AppColors(
    improved = Color(0xFF2E6B34), // 6.2:1
    neutral = Color(0xFF6A5F52),
    series = Color(0xFFB23A25),
    reference = Color(0xFF938776),
    grid = Color(0xFFE8DCC4),
    plotSurface = Color(0xFFFFFBF2),
    controlBorder = Color(0xFF938776), // 종이 3.4:1, 타일 3.0:1
)

private val DarkAppColors = AppColors(
    improved = Color(0xFF8BCB8E), // 9.8:1
    neutral = Color(0xFFB3A898),
    series = Color(0xFFFF8A6B),
    reference = Color(0xFFB3A898),
    grid = Color(0xFF3A342B),
    plotSurface = Color(0xFF14120F),
    controlBorder = Color(0xFF7C7266), // 바탕 4.0:1, 타일 3.4:1
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** 판정 상태색(현재 테마). */
object StatusColors {
    val Improved: Color @Composable get() = LocalAppColors.current.improved
    val Neutral: Color @Composable get() = LocalAppColors.current.neutral
}

/**
 * Pleos 시스템 글꼴 Asta Sans(기기 /system/fonts에 있음). 없는 기기에서는 기본 글꼴을 쓴다.
 * 글꼴 파일은 앱에 넣지 않는다(시스템 것을 그대로 써서 기본 UI와 같아 보이게).
 */
private val AppFont: FontFamily = run {
    val dir = File("/system/fonts")
    val faces = listOf(
        "AstaSansOTF-Regular.otf" to FontWeight.Normal,
        "AstaSansOTF-Medium.otf" to FontWeight.Medium,
        "AstaSansOTF-Bold.otf" to FontWeight.Bold,
    ).map { (name, weight) -> File(dir, name) to weight }
    if (faces.all { it.first.canRead() }) FontFamily(faces.map { (file, weight) -> Font(file, weight) }) else FontFamily.Default
}

/**
 * 한국어를 어절 단위로 줄바꿈한다("저장해 / 요."처럼 글자 하나가 다음 줄로 떨어지지 않게).
 * Android 13 이상에서 동작하고, 그보다 낮으면 기본 줄바꿈을 쓴다.
 */
private val KoreanLineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

// 차량 화면은 팔 길이만큼 떨어져서 본다. 보조 설명까지 24sp 이상으로 둔다:
// - Google Android for Cars 앱 품질 지침 UX-3 "Font sizes should be at least 24sp"(Video·Games·Browsers 범주, 참고치로 씀)
// - Google Design for Driving Typography "The minimum type size for Android Automotive OS is 24dp"
// - Pleos 최소값은 수집본 docs__connect__guide__docs-design__foundations__typography.md "최소 폰트 크기는 12sp 이상을 권장"(이보다 넉넉히 넘김)
// 위계는 크기만이 아니라 색(보조 = 회색)·굵기로도 나눈다(2026-11-04 총괄 검수 R4: 보조 21sp·라벨 20sp가 참고치 미달이었다).
private val CarTypography = Typography(
    titleLarge = TextStyle(fontFamily = AppFont, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineBreak = KoreanLineBreak),
    titleMedium = TextStyle(fontFamily = AppFont, fontSize = 28.sp, fontWeight = FontWeight.Medium, lineBreak = KoreanLineBreak),
    bodyLarge = TextStyle(fontFamily = AppFont, fontSize = 26.sp, lineBreak = KoreanLineBreak),
    bodyMedium = TextStyle(fontFamily = AppFont, fontSize = 24.sp, lineBreak = KoreanLineBreak),
    labelLarge = TextStyle(fontFamily = AppFont, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineBreak = KoreanLineBreak),
)

/** 숫자 강조용(게이지·타일 큰 값). */
val DisplayFont: FontFamily get() = AppFont

@Composable
fun ChatoonTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, typography = CarTypography) {
        // Surface 없이 배경을 직접 칠하므로 기본 글자색을 배경에 맞게 정해 둔다.
        CompositionLocalProvider(
            LocalContentColor provides scheme.onBackground,
            LocalAppColors provides if (dark) DarkAppColors else LightAppColors,
            LocalReduceMotion provides rememberReduceMotion(),
            // 스크롤 끝에서 화면이 늘어나는 효과(Android 12+ stretch overscroll)를 끈다. 차 화면에서 거슬리고
            // 그림이 일그러져 보인다(2026-11-12 사용자 요청). 끝에 닿으면 그냥 멈춘다.
            LocalOverscrollFactory provides null,
            content = content,
        )
    }
}
