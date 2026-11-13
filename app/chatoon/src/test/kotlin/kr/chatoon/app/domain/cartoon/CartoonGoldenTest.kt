package kr.chatoon.app.domain.cartoon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱이 쓰는 만화가 상세안 시안(docs/cartoon/카툰_상세안.html)과 같은지 대조한다.
 *
 * 대조용 값은 `node tools/cartoon_golden.mjs`가 시안의 스크립트를 그대로 돌려 만든다
 * (src/test/resources/cartoon/golden.jsonl). 시안의 규칙이나 문장을 바꾸면 다시 만들고 앱 코드도 같이 고친다.
 * 시안은 요일을 화요일, 밤 시각을 11시, 평소 주차를 다섯 번으로 고정해 두었으므로 여기서도 그 값으로 맞춘다.
 */
class CartoonGoldenTest {

    @Test
    fun `앱이 쓰는 만화는 시안과 같다`() {
        val lines = javaClass.getResourceAsStream("/cartoon/golden.jsonl")!!
            .bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        assertTrue("대조용 값이 너무 적다: ${lines.size}건", lines.size >= 900)

        val different = mutableListOf<String>()
        var compared = 0
        for (line in lines) {
            val case = JSONObject(line)
            val input = case.getJSONObject("in")
            // 2026-11-12 사용자 결정으로 시안과 일부러 달라진 두 곳:
            // ① 주인의 답은 대사·제목에 옮기지 않는다(답이 있는 화는 새 규칙 테스트 CartoonWriterTest가 본다).
            // ② 급가속은 무엇을 밟았는지 드러나게 "가속 페달을"을 넣는다. 나머지는 시안과 그대로 같아야 한다.
            if (input.getString("why").isNotEmpty() || input.optString("why2").isNotEmpty()) continue
            compared++
            val expected = case.getString("out")
                .replace(Regex("(\\S+) 번 세게 밟았다"), "가속 페달을 $1 번 세게 밟았다")
                .replace("세게 밟은 날", "가속 페달을 세게 밟은 날")
                .replace("아까 세게 밟으셨죠.", "아까 가속 페달을 세게 밟으셨죠.")
            val actual = text(CartoonWriter.write(seenOf(input)))
            if (actual != expected) different += "입력 $input\n--- 시안\n$expected\n--- 앱\n$actual"
        }
        assertTrue("대조한 화가 너무 적다: ${compared}건", compared >= 290)
        assertEquals("시안과 다른 화가 ${different.size}건 있다. 첫 건:\n${different.firstOrNull()}", 0, different.size)
    }

    private fun seenOf(input: JSONObject) = Seen(
        day = "화요일",
        time = when (input.getString("time")) {
            "night" -> TimeOfDay.NIGHT
            "dusk" -> TimeOfDay.DUSK
            else -> TimeOfDay.DAY
        },
        hour = 23,
        rain = when (input.getInt("wiper")) {
            2 -> Rain.HEAVY
            1 -> Rain.RAIN
            else -> Rain.NONE
        },
        parkShifts = input.getInt("park"),
        usualParkShifts = 5,
        windowsAllDown = input.getBoolean("windows"),
        setTemp = input.getInt("temp"),
        fanAtMax = true,
        guests = input.getInt("guests"),
        strangerSeat = input.getBoolean("stranger"),
        lateUnusual = true,
        jolt = when (input.getString("jolt")) {
            "brake" -> Jolt.BRAKE
            "accel" -> Jolt.ACCEL
            else -> Jolt.NONE
        },
        why = input.getString("why"),
        joltWhy = input.getString("why2"),
    )

    private fun text(episode: Episode): String = (
        listOf(
            episode.title,
            episode.line,
            episode.ask?.joinToString("/") ?: "-",
            episode.said ?: "-",
            cutLine(episode.now),
        ) + episode.cuts.map(::cutLine)
        ).joinToString("\n")

    /** 컷 하나를 한 줄로. 순서는 tools/cartoon_golden.mjs의 cutLine과 같다. */
    private fun cutLine(cut: Cut): String {
        var kind = ""
        var mood = ""
        var driver = ""
        var fx = ""
        var shifts = ""
        var degrees = ""
        var windows = ""
        var guests = ""
        var wind = ""
        var question = ""
        var answer = ""
        when (val p = cut.panel) {
            is Panel.Stage -> { kind = "stage"; mood = name(p.mood) }
            is Panel.Front -> { kind = "front"; mood = name(p.mood); driver = name(p.driver); fx = name(p.fx) }
            Panel.Wiper -> kind = "wiper"
            is Panel.Park -> { kind = "park"; shifts = p.shifts.toString() }
            is Panel.Side -> {
                kind = "side"; mood = name(p.mood); fx = name(p.fx)
                windows = if (p.windowsDown) "down" else ""
                guests = if (p.guests > 0) p.guests.toString() else ""
                wind = if (p.wind) "wind" else ""
            }
            is Panel.Temp -> { kind = "temp"; mood = name(p.mood); degrees = p.degrees.toString() }
            // 열선 컷은 앱에서 더한 것이라 시안에는 없다(골든 사례에는 나오지 않는다).
            is Panel.Seat -> { kind = "seat"; mood = name(p.mood); degrees = p.level.toString() }
            is Panel.Ask -> { kind = "ask"; question = p.question.joinToString("/"); answer = p.answer }
            Panel.Brake -> kind = "brake"
            Panel.Accel -> kind = "accel"
            is Panel.End -> { kind = "end"; mood = name(p.mood); driver = name(p.driver); fx = name(p.fx) }
        }
        return listOf(kind, mood, driver, fx, shifts, degrees, windows, guests, wind, question, answer, cut.caption, cut.proof ?: "")
            .joinToString("|")
    }

    private fun name(mood: Mood) = mood.name.lowercase()
    private fun name(driver: Driver) = if (driver == Driver.STRANGER) "stranger" else ""
    private fun name(fx: Fx) = when (fx) {
        Fx.NONE -> ""
        Fx.QUESTION -> "q"
        Fx.EXCLAIM -> "ex"
        Fx.ZZZ -> "z"
        Fx.SWEAT -> "sweat"
        Fx.SHIVER -> "shiver"
        Fx.NOTE -> "note"
    }
}
