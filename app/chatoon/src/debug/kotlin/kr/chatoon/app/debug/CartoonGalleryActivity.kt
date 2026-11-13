package kr.chatoon.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.chatoon.app.domain.cartoon.CartoonWriter
import kr.chatoon.app.domain.cartoon.Jolt
import kr.chatoon.app.domain.cartoon.Rain
import kr.chatoon.app.domain.cartoon.Routine
import kr.chatoon.app.domain.cartoon.Seen
import kr.chatoon.app.domain.cartoon.TimeOfDay
import kr.chatoon.app.ui.cartoon.CarPaint
import kr.chatoon.app.ui.cartoon.EpisodeStrip
import kr.chatoon.app.ui.cartoon.LocalCarPaint
import kr.chatoon.app.ui.theme.ChatoonTheme

/**
 * 그림을 눈으로 확인하는 화면(debug 빌드 전용). 여러 경우의 화를 차례로 그린다.
 *
 * adb shell am start --user 10 -n kr.chatoon.app/.debug.CartoonGalleryActivity [--ei start 0] [--ei count 2] [--es paint BLUE]
 */
class CartoonGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = intent.getIntExtra("start", 0).coerceIn(0, CASES.lastIndex)
        val count = intent.getIntExtra("count", 2).coerceAtLeast(1)
        val paint = CarPaint.of(intent.getStringExtra("paint"))
        setContent {
            ChatoonTheme {
              CompositionLocalProvider(LocalCarPaint provides paint.color) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    for ((name, seen) in CASES.drop(start).take(count)) {
                        val episode = CartoonWriter.write(seen)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("$name — ${episode.title}", style = MaterialTheme.typography.titleMedium)
                            Text(episode.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            EpisodeStrip(episode)
                        }
                    }
                }
              }
            }
        }
    }

    private companion object {
        val base = Seen(day = "화요일", time = TimeOfDay.DAY, hour = 14, parkShifts = 5, usualParkShifts = 5)
        val CASES = listOf(
            "0 면접+비+주차3" to base.copy(time = TimeOfDay.DUSK, hour = 18, rain = Rain.HEAVY, parkShifts = 3, why = "면접 보러"),
            "1 급하게 섬+답" to base.copy(jolt = Jolt.BRAKE, joltWhy = "한눈팔다가 사고 날 뻔"),
            "2 밤+창문" to base.copy(time = TimeOfDay.NIGHT, hour = 23, lateUnusual = true, windowsAllDown = true),
            "3 에어컨17+주차7" to base.copy(setTemp = 17, parkShifts = 7),
            "4 세게 밟음+앞차+해질녘" to base.copy(time = TimeOfDay.DUSK, hour = 18, jolt = Jolt.ACCEL, joltWhy = "앞차가 너무 느려서"),
            "5 별일 없는 날" to base,
            "6 비+창문" to base.copy(rain = Rain.RAIN, windowsAllDown = true),
            "7 히터27+한 번에+밤" to base.copy(time = TimeOfDay.NIGHT, hour = 21, setTemp = 27, parkShifts = 1),
            "8 손님2+낯선 운전자(실차용)" to base.copy(guests = 2, strangerSeat = true),
            "9 밤+비+급하게 섬(답 없음)" to base.copy(time = TimeOfDay.NIGHT, hour = 0, lateUnusual = true, rain = Rain.HEAVY, jolt = Jolt.BRAKE),
            "10 병원+후면주차(R)" to base.copy(why = "병원", parkShifts = 1, usualParkShifts = 4, parkedInReverse = true),
            "11 마트+전면주차(D)+해질녘" to base.copy(time = TimeOfDay.DUSK, hour = 18, why = "장 보러", parkShifts = 1, usualParkShifts = 4, parkedInReverse = false),
            "12 카페+비" to base.copy(rain = Rain.RAIN, why = "점심 먹으러"),
            "13 회사+밤" to base.copy(time = TimeOfDay.NIGHT, hour = 22, why = "야근"),
            "14 가족(집)" to base.copy(why = "엄마 보러", variant = 1),
            "15 여행(자연)+세게 밟음" to base.copy(why = "바다 놀러", jolt = Jolt.ACCEL, joltWhy = "늦을까 봐", variant = 2),
            "16 열선2단+밤" to base.copy(time = TimeOfDay.NIGHT, hour = 20, seatHeat = 2),
            "17 고비: 10번째 주행" to base.copy(routine = Routine(tripNumber = 10)),
            "18 고비: 함께 500km+해질녘" to base.copy(time = TimeOfDay.DUSK, hour = 18, routine = Routine(kmMilestone = 500)),
            "19 고비: 나흘 만의 비" to base.copy(rain = Rain.RAIN, routine = Routine(dryDays = 4)),
            "20 고비: 사흘 만에" to base.copy(routine = Routine(restDays = 3), variant = 1),
            "21 고비: 닷새째 같은 시간+밤" to base.copy(time = TimeOfDay.NIGHT, hour = 21, routine = Routine(sameSlotDays = 5)),
            "22 고비: 세 번 연속 한 번에" to base.copy(parkShifts = 1, usualParkShifts = 1, routine = Routine(oneShotStreak = 3), parkedInReverse = true),
            "23 달리다 히터 22→26+밤" to base.copy(time = TimeOfDay.NIGHT, hour = 21, setTemp = 26, tempFrom = 22),
            "24 달리다 에어컨 24→18" to base.copy(setTemp = 18, tempFrom = 24, fanAtMax = false),
            "25 달리다 엉뜨+해질녘" to base.copy(time = TimeOfDay.DUSK, hour = 18, seatHeat = 3, seatHeatMidDrive = true),
            "26 달리다 통풍시트" to base.copy(seatVent = 2, seatVentMidDrive = true),
        )
    }
}
