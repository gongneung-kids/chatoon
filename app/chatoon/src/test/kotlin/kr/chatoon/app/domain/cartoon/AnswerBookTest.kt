package kr.chatoon.app.domain.cartoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerBookTest {

    private val quiet = Seen(day = "목요일", time = TimeOfDay.DAY, hour = 14, parkShifts = 5, usualParkShifts = 5)
    private val unusual = quiet.copy(parkShifts = 1, usualParkShifts = 5)
    private val brake = quiet.copy(jolt = Jolt.BRAKE)

    @Test
    fun `답을 적고 다시 읽는다`() {
        val book = AnswerBook().answer(100L, jolt = false, said = "  면접 보러\n").answer(200L, jolt = true, said = "앞차가\t갑자기")
        val back = AnswerBookCodec.decode(AnswerBookCodec.encode(book))
        assertEquals(book, back)
        assertEquals("면접 보러", back.answersFor(100L).why)
        assertEquals("앞차가 갑자기", back.answersFor(200L).joltWhy)
    }

    @Test
    fun `건너뛰면 물은 것으로 남고 답은 비어 있다`() {
        val book = AnswerBook().skip(100L)
        assertTrue(book.asked(100L))
        assertEquals(TripSeen.Answers(), book.answersFor(100L))
        // 이미 답한 주행을 건너뛰어도 답은 지워지지 않는다.
        assertEquals("출근", AnswerBook().answer(1L, false, "출근").skip(1L).answersFor(1L).why)
    }

    @Test
    fun `긴 답은 말풍선 길이로 자른다`() {
        assertEquals(Seen.MAX_ANSWER_LENGTH, AnswerBook().answer(1L, false, "가".repeat(40)).answersFor(1L).why.length)
    }

    @Test
    fun `읽지 못하는 글은 빈 책이거나 그 줄만 버린다`() {
        assertEquals(AnswerBook(), AnswerBookCodec.decode("모르는 형식"))
        assertEquals(setOf(5L), AnswerBookCodec.decode("answers v1\nx\ta\tb\n5\t출근\t\n7\t한 칸뿐").byTrip.keys)
    }

    @Test
    fun `오래된 답부터 버린다`() {
        var book = AnswerBook()
        for (i in 0L until AnswerBook.MAX_ENTRIES + 5L) book = book.skip(i)
        assertEquals(AnswerBook.MAX_ENTRIES, book.byTrip.size)
        assertFalse(book.asked(0L))
        assertTrue(book.asked(AnswerBook.MAX_ENTRIES + 4L))
    }

    @Test
    fun `평소와 같은 날은 묻지 않고, 다른 날과 놀란 날은 묻는다`() {
        assertNull(Question.of(1L, quiet, AnswerBook()))
        val where = Question.of(1L, unusual, AnswerBook())
        assertNotNull(where)
        assertFalse(where!!.jolt)
        assertEquals(ASK_WHERE, where.lines)
        val jolt = Question.of(1L, brake, AnswerBook())!!
        assertTrue(jolt.jolt)
        assertNull(Question.of(1L, brake, AnswerBook().skip(1L)))
    }

    @Test
    fun `보기는 모두 말풍선에 들어가는 길이다`() {
        for (seen in listOf(unusual, brake, quiet.copy(jolt = Jolt.ACCEL))) {
            for (s in Question.of(1L, seen, AnswerBook())!!.suggestions) assertTrue(s, s.length <= Seen.MAX_ANSWER_LENGTH)
        }
    }

    @Test
    fun `답하면 그 이유로 화가 다시 그려지고 말은 근거로 남는다`() {
        val book = AnswerBook().answer(1L, jolt = true, said = "앞차가 갑자기")
        val episode = CartoonWriter.write(brake.copy(joltWhy = book.answersFor(1L).joltWhy))
        assertEquals("앞차가 갑자기 선 날", episode.title)
        assertTrue(episode.cuts.any { it.proof == "주인의 답(앞차가 갑자기)" })
    }

    @Test
    fun `기록 시계가 조금 앞서 끝난 시각이 지금보다 뒤여도 방금 끝난 주행이다`() {
        val hour = 60 * 60 * 1000L
        assertTrue(endedRecently(endedAtMillis = 10_000L, nowMillis = 9_000L, windowMillis = 3 * hour))
        assertTrue(endedRecently(endedAtMillis = 0L, nowMillis = 3 * hour, windowMillis = 3 * hour))
        assertFalse(endedRecently(endedAtMillis = 0L, nowMillis = 3 * hour + 1, windowMillis = 3 * hour))
        // 허용 오차보다 더 미래면 시계가 이상한 것이라 묻지 않는다.
        assertFalse(endedRecently(endedAtMillis = 10 * hour, nowMillis = 0L, windowMillis = 3 * hour))
    }
}
