package kr.chatoon.app.domain.cartoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시안에 없는 경우(요일·시각·평소 값이 다를 때)와, 어떤 재료로 쓰든 지켜야 하는 것. */
class CartoonWriterTest {

    private val quiet = Seen(day = "목요일", time = TimeOfDay.DAY, hour = 14, parkShifts = 5, usualParkShifts = 5)

    @Test
    fun `평소와 같은 날은 별일 없는 날로 쓰고 묻지 않는다`() {
        val episode = CartoonWriter.write(quiet)
        assertEquals("별일 없는 날", episode.title)
        assertEquals("목요일 낮. 비는 오지 않았다.", episode.cuts[0].caption)
        assertNull(CartoonWriter.questionFor(quiet))
    }

    @Test
    fun `평소 주차 횟수에 맞춰 늘었는지 본다`() {
        val better = CartoonWriter.write(quiet.copy(parkShifts = 4, usualParkShifts = 7))
        assertEquals("주차가 늘었다", better.title)
        assertEquals("주차 네 번 만에. 지난주보다 세 번 줄었다.", better.line)
        assertEquals("지난주는 평균 일곱 번이었다.", better.cuts[2].caption)
        assertEquals("지난주 주차 평균 7번", better.cuts[2].proof)
        assertEquals("늘고 있다. 말은 안 했다.", better.cuts[3].caption)

        // 평소에도 한두 번에 넣는 사람에게 "한 번에"는 새 일이 아니다.
        assertEquals("별일 없는 날", CartoonWriter.write(quiet.copy(parkShifts = 1, usualParkShifts = 2)).title)
    }

    @Test
    fun `기어를 한 번도 바꾸지 않고 세운 날도 한 번에로 쓴다`() {
        val episode = CartoonWriter.write(quiet.copy(parkShifts = 0, usualParkShifts = 4))
        assertEquals("한 번에", episode.title)
        assertEquals("주차 직전 2분, 기어를 바꾼 횟수 0번", episode.cuts[1].proof)
        assertEquals(Panel.Park(1), episode.cuts[1].panel)
    }

    @Test
    fun `밤 시각은 실제 시각으로 쓴다`() {
        val late = quiet.copy(time = TimeOfDay.NIGHT, hour = 2, lateUnusual = true)
        val episode = CartoonWriter.write(late)
        assertEquals("새벽 두 시", episode.title)
        assertEquals("목요일 새벽 2시. 해가 진 뒤였다.", episode.cuts[0].caption)

        // 밤이어도 평소 이 시간에 다니던 사람이면 사건이 아니다.
        assertEquals("별일 없는 날", CartoonWriter.write(late.copy(lateUnusual = false)).title)
    }

    @Test
    fun `바람이 최대가 아니면 최대라고 쓰지 않는다`() {
        val episode = CartoonWriter.write(quiet.copy(setTemp = 17, fanAtMax = false))
        assertEquals("에어컨 17도", episode.title)
        assertEquals("에어컨을 17도에 맞췄다.", episode.cuts[1].caption)
        assertEquals("공조 설정 온도", episode.cuts[1].proof)
    }

    @Test
    fun `놀란 순간이 여러 번이면 그 수대로 쓴다`() {
        val episode = CartoonWriter.write(quiet.copy(jolt = Jolt.BRAKE, joltCount = 2))
        assertEquals("오는 길에 두 번 급하게 섰다.", episode.cuts[1].caption)
        assertEquals("1초에 시속 7.5km 이상 줄어든 순간 2번", episode.cuts[1].proof)
    }

    @Test
    fun `놀란 순간이 있으면 그것을 묻고 없으면 평소와 다른 날에만 어디 가는 길이었는지 묻는다`() {
        assertEquals(listOf("아까 급하게 서셨죠.", "괜찮으세요?"), CartoonWriter.questionFor(quiet.copy(jolt = Jolt.BRAKE)))
        assertEquals(listOf("아까 가속 페달을 세게 밟으셨죠.", "급한 일 있었나요?"), CartoonWriter.questionFor(quiet.copy(jolt = Jolt.ACCEL)))
        assertEquals(ASK_WHERE, CartoonWriter.questionFor(quiet.copy(windowsAllDown = true)))
        assertNull(CartoonWriter.questionFor(quiet))
    }

    @Test
    fun `어떤 재료로 쓰든 네 컷이고 주인의 말은 대사가 아니라 근거로만 남는다`() {
        val whys = listOf("", "면접 보러", "달 보러 감", "ㅋㅋ", "A", "1234567890123456")
        val joltWhys = listOf("", "한눈팔다가 사고 날 뻔", "몰라", "앞차!")
        var written = 0
        for (time in TimeOfDay.entries) for (rain in Rain.entries) for (park in 0..9) for (usual in 0..8)
            for (jolt in Jolt.entries) for (why in whys) for (joltWhy in joltWhys) {
                val seen = Seen(
                    day = "토요일", time = time, hour = if (time == TimeOfDay.NIGHT) 1 else 13, rain = rain,
                    parkShifts = park, usualParkShifts = usual, windowsAllDown = park % 2 == 0,
                    setTemp = 15 + (park + usual) % 16, fanAtMax = usual % 2 == 0, lateUnusual = usual % 3 == 0,
                    jolt = jolt, joltCount = 1 + park % 3, why = why, joltWhy = if (jolt == Jolt.NONE) "" else joltWhy,
                )
                val episode = CartoonWriter.write(seen)
                written++
                assertEquals("네 컷이어야 한다: $seen", 4, episode.cuts.size)
                assertTrue("첫 컷은 그날의 무대다: $seen", episode.cuts[0].panel is Panel.Stage)
                assertTrue("마지막 컷은 차의 얼굴이다: $seen", episode.cuts[3].panel is Panel.End)
                assertNull("마지막 컷은 차의 생각이라 근거가 없다: $seen", episode.cuts[3].proof)
                for (cut in episode.cuts + episode.now) {
                    assertTrue("빈 글이 있다: $seen", cut.caption.isNotBlank())
                    assertFalse("모르는 값을 글에 썼다: ${cut.caption}", "null" in cut.caption || "null" in cut.proof.orEmpty())
                }
                // 주인의 답은 대사·제목·일기에 옮기지 않고, 근거 줄에 출처로만 남는다(2026-11-12 사용자 결정).
                val said = listOf(seen.why, seen.joltWhy).filter { it.isNotEmpty() && (it == seen.why || seen.jolt != Jolt.NONE) }
                assertTrue("말풍선 컷은 나오지 않는다: $seen", episode.cuts.none { it.panel is Panel.Ask })
                for (words in said) {
                    assertTrue("제목에 주인의 말을 그대로 쓰지 않는다: $seen", episode.title != words)
                    for (cut in episode.cuts + episode.now) assertFalse("글에 주인의 말을 그대로 쓰지 않는다: ${cut.caption}", "“$words”" in cut.caption)
                    assertTrue("근거 줄에 출처로 남는다: $seen", episode.cuts.any { "주인의 답($words)" in it.proof.orEmpty() })
                }
                if (seen.jolt != Jolt.NONE && seen.joltWhy.isNotEmpty()) {
                    assertEquals(seen.joltWhy, episode.said)
                } else if (seen.why.isNotEmpty()) {
                    assertEquals(seen.why, episode.said)
                } else {
                    assertNull(episode.said)
                    assertFalse(episode.cuts.any { it.panel is Panel.Ask })
                }
            }
        assertTrue(written > 50_000)
    }

    @Test
    fun `차는 점수를 매기지 않고 꾸짖지 않는다`() {
        // 운전 점수 앱의 말과 꾸짖는 말. 주인의 말을 옮긴 부분은 빼고 본다.
        val banned = listOf("점수", "등급", "순위", "위험", "감점", "경고", "조심하세요", "안 됩니다", "하지 마", "잘못", "나쁜", "불량", "위반")
        for (jolt in Jolt.entries) for (rain in Rain.entries) for (park in listOf(1, 3, 5, 8)) for (windows in listOf(false, true))
            for (temp in listOf(17, 22, 27)) for (why in listOf("", "출근")) for (joltWhy in listOf("", "늦어서")) {
                val seen = Seen(
                    day = "월요일", time = TimeOfDay.NIGHT, hour = 23, rain = rain, parkShifts = park, usualParkShifts = 5,
                    windowsAllDown = windows, setTemp = temp, lateUnusual = true, jolt = jolt,
                    why = why, joltWhy = if (jolt == Jolt.NONE) "" else joltWhy,
                )
                val episode = CartoonWriter.write(seen)
                val text = (episode.cuts + episode.now).joinToString(" ") { it.caption } + " " + episode.line + " " + episode.title
                for (word in banned) assertFalse("쓰지 않기로 한 말 '$word': $text", word in text)
            }
    }

    @Test
    fun `주인의 답은 이유로 읽어 차가 자기 말로 옮긴다`() {
        val slow = CartoonWriter.write(quiet.copy(jolt = Jolt.ACCEL, joltWhy = "앞차가 너무 느려서"))
        assertEquals("느린 앞차", slow.title)
        assertEquals("앞차가 느렸다고 한다. 주인은 답답했나 보다.", slow.cuts[2].caption)
        assertEquals("주인의 답(앞차가 너무 느려서)", slow.cuts[2].proof)
        assertEquals("오는 길에 가속 페달을 한 번 세게 밟았다.", slow.cuts[1].caption)
        assertEquals(Panel.Accel, slow.cuts[1].panel)

        val interview = CartoonWriter.write(quiet.copy(why = "면접 보러"))
        assertEquals("떨리는 날", interview.title)
        assertEquals("중요한 자리에 가는 길이었다.", interview.cuts[1].caption)

        // 사전에 없는 말은 넘겨짚지 않고, 사건 제목을 쓴다.
        val unknown = CartoonWriter.write(quiet.copy(jolt = Jolt.BRAKE, joltWhy = "몰라"))
        assertEquals("급하게 선 날", unknown.title)
        assertEquals("왜 그랬는지 주인이 말해 줬다.", unknown.cuts[2].caption)
    }

    @Test
    fun `주차 방향을 알면 주차 컷과 일기·근거에 쓰고, 모르면 쓰지 않는다`() {
        val back = CartoonWriter.write(quiet.copy(parkShifts = 1, usualParkShifts = 5, parkedInReverse = true))
        val cut = back.cuts.first { it.panel is Panel.Park }
        assertEquals(Panel.Park(1, reverse = true), cut.panel)
        assertTrue(cut.caption.endsWith("뒤로 넣었다."))
        assertTrue(cut.proof!!.endsWith("마지막 기어 R"))
        val front = CartoonWriter.write(quiet.copy(parkedInReverse = false)).cuts.first { it.panel is Panel.Park }
        assertTrue(front.caption.endsWith("앞으로 들어갔다."))
        val unknown = CartoonWriter.write(quiet).cuts.first { it.panel is Panel.Park }
        assertEquals(Panel.Park(5), unknown.panel)
        assertFalse(unknown.caption.contains("넣었다") || unknown.caption.contains("들어갔다."))
    }

    @Test
    fun `도착 장소는 주인이 말한 행선지에서만 정한다`() {
        assertEquals(Place.HOSPITAL, CartoonWriter.write(quiet.copy(why = "병원")).place)
        assertEquals(Place.MART, CartoonWriter.write(quiet.copy(why = "장 보러")).place)
        assertNull(CartoonWriter.write(quiet.copy(why = "몰라")).place)
        assertNull(CartoonWriter.write(quiet).place)
    }

    @Test
    fun `같은 상황도 주행마다 다른 표현을 쓰고, 같은 주행은 늘 같은 문장이다`() {
        val slow = quiet.copy(jolt = Jolt.ACCEL, joltWhy = "앞차가 너무 느려서")
        val closings = (0 until 6).map { CartoonWriter.write(slow.copy(variant = it)).cuts[3].caption }.toSet()
        assertTrue("표현이 한 가지뿐: $closings", closings.size >= 3)
        assertTrue("나는 천천히 가는 것도 좋은데." in closings)
        assertEquals(CartoonWriter.write(slow.copy(variant = 4)), CartoonWriter.write(slow.copy(variant = 4)))
        val quietEnds = (0 until 6).map { CartoonWriter.write(quiet.copy(variant = it)).cuts[3].caption }.toSet()
        assertTrue(quietEnds.size >= 3)
    }

    @Test
    fun `차가 보이는 컷에는 차의 짧은 말이 있고, 주인의 말을 옮기지 않는다`() {
        for (seen in listOf(quiet, quiet.copy(why = "병원"), quiet.copy(jolt = Jolt.ACCEL, joltWhy = "앞차가 너무 느려서"), quiet.copy(time = TimeOfDay.NIGHT, hour = 23, rain = Rain.HEAVY))) {
            val episode = CartoonWriter.write(seen)
            for (cut in episode.cuts) {
                if (cut.panel == Panel.Wiper) continue
                val say = cut.say
                assertTrue("말이 없다: ${cut.panel}", !say.isNullOrBlank())
                assertTrue("말풍선에 들어가는 길이여야 한다: $say", say!!.length <= 8)
                assertFalse("주인의 말을 옮기지 않는다: $say", seen.why.isNotEmpty() && say == seen.why || seen.joltWhy.isNotEmpty() && say == seen.joltWhy)
            }
        }
        assertEquals("얼른 나아요", CartoonWriter.write(quiet.copy(why = "병원")).cuts[3].say)
        assertEquals("으앗!", CartoonWriter.write(quiet.copy(jolt = Jolt.ACCEL)).cuts.first { it.panel == Panel.Accel }.say)
    }
}
