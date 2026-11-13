package kr.chatoon.app.domain.cartoon

/**
 * 차가 묻고 주인이 답한 말을 주행별로 모아 둔다. 열쇠는 주행의 출발 시각이다.
 *
 * 항목이 있으면 이미 물은 주행이다. 주인이 건너뛰었으면 두 답이 모두 빈 글로 남는다(다시 묻지 않는다).
 * 주인의 말은 고치지 않고 [Seen.trimAnswer]로 앞뒤 빈칸과 길이만 맞춘다.
 */
data class AnswerBook(val byTrip: Map<Long, TripSeen.Answers> = emptyMap()) {

    fun answersFor(tripStartMillis: Long): TripSeen.Answers = byTrip[tripStartMillis] ?: TripSeen.Answers()

    fun asked(tripStartMillis: Long): Boolean = tripStartMillis in byTrip

    /** 답을 적는다. [jolt]가 참이면 놀란 순간에 대한 답, 아니면 어디 가는 길이었는지에 대한 답이다. */
    fun answer(tripStartMillis: Long, jolt: Boolean, said: String): AnswerBook {
        val clean = Seen.trimAnswer(said.replace(LINE_BREAKS, " "))
        val before = answersFor(tripStartMillis)
        val after = if (jolt) before.copy(joltWhy = clean) else before.copy(why = clean)
        return trimmed(byTrip + (tripStartMillis to after))
    }

    /** 답하지 않고 넘긴다. 이 주행은 다시 묻지 않는다. */
    fun skip(tripStartMillis: Long): AnswerBook =
        if (asked(tripStartMillis)) this else trimmed(byTrip + (tripStartMillis to TripSeen.Answers()))

    private fun trimmed(map: Map<Long, TripSeen.Answers>): AnswerBook =
        AnswerBook(if (map.size <= MAX_ENTRIES) map else map.entries.sortedByDescending { it.key }.take(MAX_ENTRIES).associate { it.key to it.value })

    companion object {
        /** 화면에 그리는 화(최근 30화)보다 넉넉하게. 오래된 것부터 버린다. */
        const val MAX_ENTRIES = 400
        private val LINE_BREAKS = Regex("[\\t\\r\\n]+")
    }
}

/** 한 줄에 한 주행: `출발시각\t어디가는길\t놀란이유`. 답에 탭·줄바꿈은 들어오지 않는다([AnswerBook.answer]). */
object AnswerBookCodec {
    private const val HEADER = "answers v1"

    fun encode(book: AnswerBook): String = buildString {
        append(HEADER)
        for ((start, a) in book.byTrip.entries.sortedBy { it.key }) {
            append('\n').append(start).append('\t').append(a.why).append('\t').append(a.joltWhy)
        }
    }

    /** 읽지 못하는 줄은 버린다. 머리말이 다르면 빈 책이다. */
    fun decode(text: String): AnswerBook {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != HEADER) return AnswerBook()
        val map = HashMap<Long, TripSeen.Answers>()
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            if (parts.size != 3) continue
            val start = parts[0].toLongOrNull() ?: continue
            map[start] = TripSeen.Answers(Seen.trimAnswer(parts[1]), Seen.trimAnswer(parts[2]))
        }
        return AnswerBook(map)
    }
}

/**
 * 주행이 [windowMillis] 안에 끝났는가.
 * 주행 기록의 시각은 경과 시간으로 흐르는 기록 시계([kr.chatoon.app.domain.drive.RecordingClock])라 벽시계보다 조금 앞설 수 있다.
 * 그래서 끝난 시각이 "지금"보다 허용 오차만큼 뒤여도 방금 끝난 것으로 본다(그러지 않으면 다음 재계산까지 질문·목소리가 빠진다).
 */
fun endedRecently(endedAtMillis: Long, nowMillis: Long, windowMillis: Long): Boolean =
    nowMillis - endedAtMillis in -kr.chatoon.app.domain.drive.RecordingClock.DEFAULT_TOLERANCE_MILLIS..windowMillis

/** 차의 질문 하나와 답하기 쉬운 보기. */
data class Question(val tripStartMillis: Long, val lines: List<String>, val jolt: Boolean, val suggestions: List<String>) {
    companion object {
        /** 이 화에서 차가 물을 질문. 묻지 않는 화이거나 이미 물었으면 null. */
        fun of(tripStartMillis: Long, seen: Seen, book: AnswerBook): Question? {
            if (book.asked(tripStartMillis)) return null
            val lines = CartoonWriter.questionFor(seen) ?: return null
            val suggestions = when (seen.jolt) {
                Jolt.BRAKE -> listOf("앞차가 갑자기", "누가 끼어들어서", "신호가 바뀌어서", "한눈팔다가")
                Jolt.ACCEL -> listOf("급한 일이 있어서", "앞차가 너무 느려서", "합류하느라", "그냥 기분이")
                Jolt.NONE -> listOf("출근", "장 보러", "병원", "친구 만나러", "그냥 바람 쐬러")
            }
            return Question(tripStartMillis, lines, seen.jolt != Jolt.NONE, suggestions)
        }
    }
}
