package kr.chatoon.app.domain.cartoon

/**
 * 차가 본 것([Seen])으로 만화 한 화([Episode])를 쓴다.
 *
 * 지키는 원칙
 * - 있었던 일만 그린다. 사실을 그린 컷에는 근거 신호([Cut.proof])가 붙고, 차의 감상을 그린 컷은 근거가 없다(차의 생각).
 * - 차의 생각은 기분과 짐작만 쓰고, 일어난 일처럼 쓰지 않는다.
 * - 평소와 다른 일에 점수를 주고, 점수가 높은 두 가지로 한 화를 만든다.
 * - 급하게 서거나 가속 페달을 세게 밟은 순간은 차가 놀라고 걱정하는 장면으로만 쓴다. 점수·순위·꾸짖는 말이 없다.
 * - 생성형 AI와 서버를 쓰지 않는다. 대사 틀은 사람이 쓴 것이고 여기 있는 것이 전부다.
 *
 * 규칙과 문장은 상세안 시안(docs/cartoon)의 스크립트와 같아야 한다. `CartoonGoldenTest`가 둘을 대조한다.
 */
object CartoonWriter {

    fun write(seen: Seen): Episode {
        val found = find(seen)
        val stage = stageCut(seen)

        // 주인의 답은 이야기의 재료(이유)로만 쓴다. 대사나 제목에 그대로 옮기지 않는다(2026-11-12 사용자 지적).
        // 답에서 상황·이유를 알아내 차가 자기 말로 옮기고, 실제로 한 말은 근거 줄에만 출처로 남긴다.

        // 놀란 순간이 있었고 주인이 이유를 말했으면, 그 순간이 그 화의 중심이 된다.
        if (seen.jolt != Jolt.NONE && seen.joltWhy.isNotEmpty()) {
            val kind = if (seen.jolt == Jolt.BRAKE) Moment.BRAKE else Moment.ACCEL
            val reason = joltReasonOf(seen.joltWhy)
            val situation = seen.why.takeIf { it.isNotEmpty() }?.let(::situationOf)
            val opening = if (situation == null) stage else stage.copy(caption = stage.caption + " " + situation.told.pick(seen), proof = stage.proof + " · " + answerProof(seen.why))
            return episode(
                seen,
                title = situation?.title ?: reason.title ?: kind.title(seen),
                line = kind.line(seen),
                now = kind.cut(seen),
                ask = askAbout(seen.jolt),
                said = seen.joltWhy,
                cuts = listOf(
                    opening,
                    kind.cut(seen),
                    Cut(Panel.Front(reason.mood, fx = if (reason.mood == Mood.SWEAT) Fx.SWEAT else Fx.QUESTION), reason.told.pick(seen), answerProof(seen.joltWhy), say = REASON_SAY[reason.key]),
                    Cut(Panel.End(reason.mood, fx = if (reason.mood == Mood.SWEAT) Fx.SWEAT else Fx.NONE), reason.close.pick(seen), say = REASON_END_SAY[reason.key]),
                ),
            )
        }

        // 어디 가는 길이었는지 말했으면, 그 상황이 제목이 되고 차의 반응이 상황을 따라간다.
        if (seen.why.isNotEmpty()) {
            val situation = situationOf(seen.why)
            val top = found.firstOrNull()?.moment
            val tie = top?.tie(seen).orEmpty()
            return episode(
                seen,
                title = situation.title ?: top?.title(seen) ?: "어디 다녀온 날",
                line = situation.told.pick(seen) + " " + (top?.line(seen) ?: "오늘도 평소대로였다."),
                now = top?.cut(seen) ?: QUIET,
                ask = ASK_WHERE,
                said = seen.why,
                cuts = listOf(
                    stage,
                    Cut(Panel.Front(situation.mood, fx = situation.fx), situation.told.pick(seen), answerProof(seen.why), say = SITUATION_SAY[situation.key]),
                    top?.cut(seen) ?: QUIET,
                    Cut(Panel.End(situation.mood), situation.open.pick(seen) + (if (tie.isEmpty()) "" else " $tie") + " " + situation.close.pick(seen), say = SITUATION_END_SAY[situation.key]),
                ),
            )
        }

        // 평소와 다른 일이 없어도, 지난 기록에서 센 사실이 고비면 그 사실로 한 화를 쓴다(소재 반복 줄이기, 기획서 ※6).
        if (found.isEmpty()) routineEpisode(seen)?.let { return it }

        if (found.isEmpty()) {
            return episode(
                seen,
                title = "별일 없는 날",
                line = "오늘도 평소대로. 별일 없었다.",
                now = QUIET,
                cuts = listOf(
                    stage,
                    QUIET,
                    Cut(
                        Panel.Park(seen.parkShifts.coerceAtLeast(1), seen.parkedInReverse),
                        (if (seen.parkShifts <= 1) "주차는 한 번에 들어갔다. 평소와 비슷했다." else "주차는 ${count(seen.parkShifts)} 번. 평소와 비슷했다.") + parkDirection(seen),
                        parkProof(seen),
                    ),
                    Cut(Panel.End(Mood.HAPPY), QUIET_ENDINGS.pick(seen)),
                ),
            )
        }

        val first = found[0].moment
        val cuts = if (found.size == 1) {
            listOf(stage, first.cut(seen), first.mid(seen), first.end(seen))
        } else {
            // 두 사건은 일어난 순서(출발, 달리는 중, 주차)대로 놓고, 마지막 컷은 점수가 높은 쪽의 맺음말로 쓴다.
            val pair = found.take(2).sortedBy { it.order }
            val end = first.end(seen)
            val closing = first.both(seen) ?: (first.mid(seen).caption + " " + end.caption)
            listOf(stage, pair[0].moment.cut(seen), pair[1].moment.cut(seen), end.copy(caption = closing))
        }
        return episode(seen, title = first.title(seen), line = first.line(seen), now = first.cut(seen), cuts = cuts)
    }

    /**
     * 여러 화를 일어난 순서(오래된 것부터)대로 쓴다. 내용은 [write]와 같다.
     * 바로 앞 화와 제목이 같으면 그날의 날씨·때를 부제로 붙여(「급하게 선 날 · 비 오는 밤」) 지난 화 목록에 같은 제목이 줄지어 서지 않게 한다.
     * 한 화만 볼 때의 제목(시안과 대조하는 [write])은 바꾸지 않는다.
     */
    fun series(seens: List<Seen>): List<Episode> {
        var previousBase: String? = null
        var previousTitle: String? = null
        return seens.map { seen ->
            val episode = write(seen)
            val base = episode.title
            val title = if (base != previousBase && base != previousTitle) base else {
                listOf("$base · ${weatherOrDay(seen)}", "$base · ${seen.day} ${clockWord(seen.hour)}")
                    .firstOrNull { it != previousTitle } ?: "또, $base"
            }
            previousBase = base
            previousTitle = title
            episode.copy(title = title)
        }
    }

    /** 부제: 비가 왔으면 "비 오는 밤", 아니면 "목요일 밤". */
    private fun weatherOrDay(s: Seen): String {
        val time = when (s.time) {
            TimeOfDay.DAY -> "낮"
            TimeOfDay.DUSK -> "해 질 녘"
            TimeOfDay.NIGHT -> if (s.hour in 0..5) "새벽" else "밤"
        }
        return if (s.rain != Rain.NONE) "비 오는 $time" else "${s.day} $time"
    }

    /** "아침 8시", "오후 3시", "밤 10시", "새벽 2시". */
    private fun clockWord(hour: Int): String = when (hour) {
        0 -> "밤 12시"
        in 1..5 -> "새벽 ${hour}시"
        in 6..11 -> "아침 ${hour}시"
        12 -> "낮 12시"
        in 13..17 -> "오후 ${hour - 12}시"
        in 18..19 -> "저녁 ${hour - 12}시"
        else -> "밤 ${hour - 12}시"
    }

    /**
     * 세운 뒤에 차가 물을 질문. 한 번 세울 때 하나만 묻는다.
     * 놀란 순간이 있었으면 그것을 묻고, 없으면 평소와 다른 주행이었을 때만 어디 가는 길이었는지 묻는다.
     * 평소와 같은 주행이면 묻지 않는다(null).
     */
    fun questionFor(seen: Seen): List<String>? = askAbout(seen.jolt) ?: if (find(seen).isNotEmpty()) ASK_WHERE else null

    // ---------------------------------------------------------------- 평소와 다른 일 찾기

    private class Found(val moment: Moment, val weight: Double, val order: Double)

    /** 평소와 다른 일에 점수를 준다. 점수가 같으면 여기 적힌 순서가 먼저다. */
    private fun find(s: Seen): List<Found> = buildList {
        if (s.strangerSeat) add(Found(Moment.STRANGER, 9.0, 1.0))
        when {
            s.parkShifts <= 1 && s.usualParkShifts >= 3 -> add(Found(Moment.PARK_ONE, 8.0, 3.0))
            s.parkShifts > 1 && s.parkShifts <= s.usualParkShifts - 2 -> add(Found(Moment.PARK_BETTER, 5.0 + s.usualParkShifts - s.parkShifts, 3.0))
            s.parkShifts >= s.usualParkShifts + 2 -> add(Found(Moment.PARK_WORSE, 4.2 + s.parkShifts - s.usualParkShifts, 3.0))
        }
        if (s.windowsAllDown) add(if (s.rain != Rain.NONE) Found(Moment.WINDOWS_RAIN, 8.5, 2.0) else Found(Moment.WINDOWS, 6.0, 2.0))
        // 달리다 온도를 바꿨으면 "바꾼 일"이 사건이다(설정 온도가 높다·낮다는 같은 이야기라 겹쳐 쓰지 않는다).
        val changed = s.tempFrom
        if (changed != null && s.setTemp != null) {
            val delta = s.setTemp - changed
            add(Found(if (delta > 0) Moment.HEAT_UP else Moment.COOL_DOWN, 5.0 + kotlin.math.abs(delta) * 0.3, 2.0))
        }
        if (changed == null) s.setTemp?.let { t ->
            if (t <= COLD_AT) add(Found(Moment.COLD, 4.0 + COLD_AT - t, 2.0)) else if (t >= HOT_AT) add(Found(Moment.HOT, 4.0 + t - HOT_AT, 2.0))
        }
        if (s.guests > 0) add(Found(Moment.GUESTS, 3.5 + s.guests, 1.5))
        if (s.seatHeat > 0) add(Found(Moment.SEAT_HEAT, 4.5 + s.seatHeat * 0.5 + (if (s.seatHeatMidDrive) 0.5 else 0.0), if (s.seatHeatMidDrive) 2.0 else 1.2))
        if (s.seatVent > 0) add(Found(Moment.SEAT_VENT, 4.5 + s.seatVent * 0.5 + (if (s.seatVentMidDrive) 0.5 else 0.0), if (s.seatVentMidDrive) 2.0 else 1.2))
        if (s.rain == Rain.HEAVY) add(Found(Moment.WIPER_FAST, 4.5, 2.0))
        if (s.time == TimeOfDay.NIGHT && s.lateUnusual) add(Found(Moment.LATE, 5.5, 1.0))
        if (s.jolt == Jolt.BRAKE) add(Found(Moment.BRAKE, 7.5, 2.0))
        if (s.jolt == Jolt.ACCEL) add(Found(Moment.ACCEL, 7.0, 2.0))
    }.sortedByDescending { it.weight }

    // ---------------------------------------------------------------- 평범한 날의 고비(Routine)

    /**
     * 평소와 다른 일이 없는 날, 지난 기록에서 센 고비 하나로 쓰는 화. 고비가 없으면 null(「별일 없는 날」).
     * 컷: 무대 → 고비(사실, 근거 있음) → 주차 → 차의 생각.
     * 여럿이 겹치면 드문 것부터: 몇 번째 주행 > 함께 달린 거리 > 며칠 만의 비 > 연속 한 번에 주차 > 며칠 쉬고 나옴 > 며칠째 같은 시간.
     * 주차 고비가 이미 주차 컷이면 셋째 컷은 주차 대신 차의 얼굴로 둔다.
     */
    private fun routineEpisode(s: Seen): Episode? {
        val r = s.routine
        val (title, fact, ends) = when {
            r.tripNumber != null -> Triple(
                "${r.tripNumber}번째 주행",
                Cut(Panel.Front(Mood.PROUD, fx = Fx.NOTE), "기록을 시작하고 ${r.tripNumber}번째 주행이었다.", "주행 기록 ${r.tripNumber}번", say = "${r.tripNumber}번째!"),
                listOf("${r.tripNumber}번 나갔다가 ${r.tripNumber}번 돌아왔다. 그게 제일 좋다.", "숫자를 세는 건 내 일이다. 주인은 몰라도 된다.", "벌써 ${r.tripNumber}번이다. 하나도 잊지 않았다."),
            )
            r.kmMilestone != null -> Triple(
                "함께 ${km(r.kmMilestone)}",
                Cut(Panel.Side(Mood.PROUD), "기록을 시작하고 함께 달린 거리가 ${km(r.kmMilestone)}를 넘었다.", "주행마다 누적 주행거리 차이의 합", say = "${km(r.kmMilestone)}!"),
                listOf("타이어가 조금 닳았다. 훈장이다.", "그만큼 같이 다녔다. 나는 다 기억한다.", "다음 ${km(r.kmMilestone + 500)}까지도 같이 가자."),
            )
            r.dryDays != null -> Triple(
                "${days(r.dryDays)} 만의 비",
                Cut(Panel.Front(Mood.HAPPY, fx = Fx.NOTE), "${days(r.dryDays)} 만에 와이퍼를 켰다.", "와이퍼 · 지난번 비 온 주행 ${r.dryDays}일 전", say = "반가운 비"),
                listOf("먼지를 씻었다. 개운하다.", "와이퍼가 오랜만에 일했다. 녹슬지 않았다.", "빗소리가 지붕을 두드렸다. 나쁘지 않았다."),
            )
            r.oneShotStreak != null -> Triple(
                "${count(r.oneShotStreak)} 번 연속 한 번에",
                Cut(Panel.Park(1, s.parkedInReverse), "주차가 ${count(r.oneShotStreak)} 번 연속 한 번에 들어갔다." + parkDirection(s), "주차 직전 2분 기어 변경 1번 이하 · 최근 ${r.oneShotStreak}번 연속", say = "또 쏙!"),
                listOf("이제 주차는 주인 몸이 기억한다.", "나는 가만히 있었다. 주인이 다 했다.", "한 번에, 또 한 번에. 나는 박수를 칠 손이 없다."),
            )
            r.restDays != null -> Triple(
                "${days(r.restDays)} 만에",
                Cut(Panel.Front(Mood.HAPPY, fx = Fx.EXCLAIM), "${days(r.restDays)} 만에 시동이 걸렸다.", "앞선 주행이 끝난 날 · ${r.restDays}일 전", say = "오랜만이에요"),
                listOf("주차장에서 기다렸다. 기다리는 것도 내 일이다.", "오랜만에 바퀴가 굴렀다. 몸이 풀린다.", "쉬는 동안 먼지가 조금 앉았다. 금방 털렸다."),
            )
            r.sameSlotDays != null -> Triple(
                "${days(r.sameSlotDays)}째 같은 시간",
                Cut(Panel.Front(Mood.CALM), "${days(r.sameSlotDays)}째, 같은 시간에 나왔다.", "출발 시각 · 지난 ${r.sameSlotDays - 1}일 같은 시간대 주행", say = "시간 됐다"),
                listOf("시계를 안 봐도 안다. 이제 나갈 시간이다.", "주인은 꼬박꼬박 나온다. 나도 꼬박꼬박 기다린다.", "같은 시간, 같은 출발. 나는 이런 날이 좋다."),
            )
            else -> return null
        }
        val park = if (fact.panel is Panel.Park) {
            Cut(Panel.End(Mood.PROUD, fx = Fx.NOTE), "한 번도 다시 넣지 않았다.", parkProof(s))
        } else Cut(
            Panel.Park(s.parkShifts.coerceAtLeast(1), s.parkedInReverse),
            (if (s.parkShifts <= 1) "주차는 한 번에 들어갔다." else "주차는 ${count(s.parkShifts)} 번.") + parkDirection(s),
            parkProof(s),
        )
        return episode(
            s,
            title = title,
            line = fact.caption + " 별일은 없었다.",
            now = fact,
            cuts = listOf(stageCut(s), fact, park, Cut(Panel.End(Mood.HAPPY), ends.pick(s))),
        )
    }

    /** "1,000km". */
    private fun km(n: Int): String = "%,dkm".format(n)

    /** 날수를 세는 말("사흘", "닷새", "일주일"). 열흘을 넘으면 "12일". */
    private fun days(n: Int): String = when (n) {
        1 -> "하루"; 2 -> "이틀"; 3 -> "사흘"; 4 -> "나흘"; 5 -> "닷새"; 6 -> "엿새"; 7 -> "일주일"; 8 -> "여드레"; 9 -> "아흐레"; 10 -> "열흘"
        else -> "${n}일"
    }

    // ---------------------------------------------------------------- 무대

    private fun stageCut(s: Seen): Cut {
        val whenText = when (s.time) {
            TimeOfDay.NIGHT -> "${s.day} ${nightClock(s.hour)}."
            TimeOfDay.DUSK -> "${s.day}, 해 질 녘."
            TimeOfDay.DAY -> "${s.day} 낮."
        }
        // 날씨를 넘겨짚지 않는다. 와이퍼와 일몰 계산으로 아는 것만 쓴다.
        val sky = when {
            s.rain == Rain.HEAVY -> "비가 쏟아졌다."
            s.rain == Rain.RAIN -> "비가 왔다."
            s.time == TimeOfDay.NIGHT -> "해가 진 뒤였다."
            s.time == TimeOfDay.DUSK -> "해가 지고 있었다."
            else -> "비는 오지 않았다."
        }
        val proof = "출발 시각" + (if (s.time != TimeOfDay.DAY) " · 일몰 계산" else "") + (if (s.rain != Rain.NONE) " · 와이퍼" else "")
        return Cut(Panel.Stage(), "$whenText $sky", proof)
    }

    /** 별일 없는 날의 맺음말. 첫 문장이 시안의 문장이다. */
    private val QUIET_ENDINGS = listOf("별일 없는 날이었다. 나는 그런 날이 좋다.", "오늘은 조용했다. 조용한 날도 일기에 적는다.", "아무 일 없이 다녀왔다. 그거면 충분하다.")

    /** 같은 상황의 여러 표현 중 이 주행의 것을 고른다. variant 0은 첫 문장(기본). */
    private fun <T> List<T>.pick(s: Seen): T = this[Math.floorMod(s.variant, size)]

    private val QUIET = Cut(Panel.Front(Mood.CALM), "어제와 비슷한 시간에 나가, 비슷한 시간만큼 달렸다.", "출발 시각과 주행 시간이 평소 범위")

    private fun episode(
        seen: Seen,
        title: String,
        line: String,
        now: Cut,
        cuts: List<Cut>,
        ask: List<String>? = null,
        said: String? = null,
    ) = Episode(
        title, line, cuts.map { it.withSay(seen) }, now.withSay(seen), ask, said, seen.time, seen.rain,
        seen.why.takeIf { it.isNotEmpty() }?.let { situationOf(it).place },
    )

    /** 말풍선에 넣을 차의 짧은 말. 이미 정한 말(이유·행선지 컷)은 그대로 둔다. */
    private fun Cut.withSay(s: Seen): Cut = if (say != null) this else copy(say = sayFor(panel, s))

    private fun sayFor(panel: Panel, s: Seen): String? = when (panel) {
        is Panel.Stage -> when {
            s.rain != Rain.NONE -> "비 온다…"
            s.time == TimeOfDay.NIGHT -> "늦은 출발이네"
            s.time == TimeOfDay.DUSK -> "노을 예쁘다"
            else -> "출발!"
        }
        is Panel.Front -> when (panel.fx) {
            Fx.QUESTION -> "응?"
            Fx.EXCLAIM -> "어!"
            Fx.SWEAT -> "휴…"
            Fx.NOTE -> "룰루~"
            Fx.ZZZ -> "졸려…"
            Fx.SHIVER -> "으슬으슬"
            Fx.NONE -> if (panel.mood == Mood.CALM) listOf("평소대로~", "오늘도 무사히", "조용한 길").pick(s) else END_SAY[panel.mood]
        }
        is Panel.Park -> when {
            panel.shifts <= 1 -> "쏙!"
            panel.shifts >= 4 -> "어디 보자…"
            else -> "다 왔다"
        }
        is Panel.Side -> when {
            panel.windowsDown -> "시원해~"
            panel.guests > 0 -> "북적북적"
            panel.mood == Mood.SHOCK -> "어?"
            else -> "부릉부릉"
        }
        is Panel.Temp -> when {
            panel.from != null && panel.degrees > panel.from -> "따뜻해진다"
            panel.from != null -> "시원해진다"
            panel.degrees <= COLD_AT -> "추워…"
            else -> "따뜻해"
        }
        is Panel.Seat -> if (panel.vent) "솔솔~" else "따끈따끈"
        Panel.Brake -> "으악!"
        Panel.Accel -> "으앗!"
        is Panel.End -> when {
            panel.driver == Driver.STRANGER -> "누구세요?"
            panel.fx == Fx.SHIVER -> "으슬으슬" // 떨고 있는 그림에 "휴…"는 어울리지 않는다
            else -> END_SAY[panel.mood]
        }
        Panel.Wiper, is Panel.Ask -> null
    }

    private val END_SAY = mapOf(
        Mood.HAPPY to "좋은 하루!", Mood.PROUD to "잘했어!", Mood.SULK to "흥.", Mood.SWEAT to "휴…",
        Mood.CALM to "오늘도 끝", Mood.SHOCK to "깜짝이야", Mood.WARY to "누구지?", Mood.SLEEPY to "졸려…",
    )

    /** 행선지별 차의 한마디. */
    private val SITUATION_SAY = mapOf(
        "interview" to "화이팅!", "hospital" to "조심조심", "date" to "두근두근", "pickup" to "어서 타요",
        "family" to "보고 싶었죠?", "work" to "오늘도 힘내요", "shopping" to "장 보러 가자", "workout" to "가 볼까!",
        "food" to "배고프다", "trip" to "신난다!", "just" to "바람 좋다", "unknown" to "그렇구나",
    )

    /** 행선지가 있는 화의 마지막 컷 한마디. */
    private val SITUATION_END_SAY = mapOf(
        "interview" to "잘될 거예요", "hospital" to "얼른 나아요", "date" to "좋았겠다", "pickup" to "어서 와요",
        "family" to "다녀왔어요", "work" to "수고했어요", "shopping" to "다 샀어요?", "workout" to "개운하다!",
        "food" to "맛있었어요?", "trip" to "또 가요!", "just" to "좋았어", "unknown" to "그렇구나",
    )

    /** 놀란 이유가 있는 화의 마지막 컷 한마디(꾸짖지 않는다). */
    private val REASON_END_SAY = mapOf(
        "lapse" to "같이 조심해요", "near" to "다행이다", "frontstop" to "거리 두자", "cutin" to "잘했어요", "sudden" to "잘했어요", "signal" to "잘 섰어요",
        "hurry" to "늦어도 괜찮아요", "slowcar" to "천천히 가요", "unknown" to "그래요",
    )

    /** 놀란 이유별 차의 한마디(꾸짖지 않는다). */
    private val REASON_SAY = mapOf(
        "lapse" to "깜짝이야", "near" to "휴…", "frontstop" to "꽉 섰다!", "cutin" to "어이쿠!", "sudden" to "잘 섰어!", "signal" to "멈춤!",
        "hurry" to "천천히요~", "slowcar" to "느긋하게~", "unknown" to "그랬구나",
    )

    // ---------------------------------------------------------------- 사건: 컷과 대사 틀

    private enum class Moment {
        STRANGER, PARK_ONE, PARK_BETTER, PARK_WORSE, WINDOWS, WINDOWS_RAIN, COLD, HOT, HEAT_UP, COOL_DOWN, SEAT_HEAT, SEAT_VENT, GUESTS, WIPER_FAST, LATE, BRAKE, ACCEL;

        fun title(s: Seen): String = when (this) {
            STRANGER -> "낯선 사람"
            PARK_ONE -> "한 번에"
            PARK_BETTER -> "주차가 늘었다"
            PARK_WORSE -> "앞으로, 뒤로"
            WINDOWS -> "창문을 전부 내린 날"
            WINDOWS_RAIN -> "비 오는데 창문"
            COLD -> if (s.fanAtMax) "에어컨 최대" else "에어컨 ${s.setTemp}도"
            HOT -> "히터를 올린 날"
            HEAT_UP -> "달리다 히터 온도를 올린 날"
            COOL_DOWN -> "달리다 에어컨 온도를 내린 날"
            SEAT_HEAT -> if (s.seatHeatMidDrive) "달리다 엉뜨를 켠 날" else "열선을 켠 날"
            SEAT_VENT -> if (s.seatVentMidDrive) "달리다 통풍시트를 켠 날" else "통풍시트를 켠 날"
            GUESTS -> "손님"
            WIPER_FAST -> "비 오는 날"
            LATE -> lateTitle(s.hour)
            BRAKE -> "급하게 선 날"
            ACCEL -> "가속 페달을 세게 밟은 날"
        }

        /** 세우는 순간 차가 하는 말. */
        fun line(s: Seen): String = when (this) {
            STRANGER -> "운전석이 한 뼘 뒤로 밀렸다. 누가 탔다."
            PARK_ONE -> "주차, 한 번에 들어갔다. 나만 봤다."
            PARK_BETTER -> "주차 ${count(s.parkShifts)} 번 만에. 지난주보다 ${count(s.usualParkShifts - s.parkShifts)} 번 줄었다."
            PARK_WORSE -> "주차 ${count(s.parkShifts)} 번 만에. 괜찮다, 나는 시간이 많다."
            WINDOWS -> "창문을 전부 내리고 달렸다. 바람이 좋았다."
            WINDOWS_RAIN -> "비가 오는데 창문을 내렸다. 시트가 젖었다."
            COLD -> "에어컨 ${s.setTemp}도. 나는 좀 추웠다."
            HOT -> "히터 ${s.setTemp}도. 손이 차가웠나 보다."
            HEAT_UP -> "달리다가 온도를 ${s.tempFrom}도에서 ${s.setTemp}도로 올렸다. 추웠나 보다."
            COOL_DOWN -> "달리다가 온도를 ${s.tempFrom}도에서 ${s.setTemp}도로 내렸다. 더웠나 보다."
            SEAT_HEAT -> if (s.seatHeatMidDrive) "달리다가 운전석 열선을 ${s.seatHeat}단으로 켰다. 추웠나 보다." else "운전석 열선을 ${s.seatHeat}단으로 켰다. 일주일 만이다."
            SEAT_VENT -> if (s.seatVentMidDrive) "달리다가 운전석 통풍시트를 ${s.seatVent}단으로 켰다. 더웠나 보다." else "운전석 통풍시트를 ${s.seatVent}단으로 켰다. 일주일 만이다."
            GUESTS -> "뒷자리에 ${count(s.guests)} 명. 오랜만에 시끄러웠다."
            WIPER_FAST -> "와이퍼를 가장 빠르게. 열심히 닦았다."
            LATE -> "평소라면 주차장에서 자고 있을 시간이다."
            BRAKE -> "${count(s.joltCount)} 번 급하게 섰다. 나는 놀랐다."
            ACCEL -> "가속 페달을 ${count(s.joltCount)} 번 세게 밟았다. 나는 놀랐다."
        }

        /** 그 일을 그린 컷(사실). */
        fun cut(s: Seen): Cut = when (this) {
            STRANGER -> Cut(Panel.Front(Mood.WARY, Driver.STRANGER, Fx.QUESTION), "운전석이 평소보다 한 뼘 뒤로 밀렸다. 등받이도 누웠다.", "운전석 앞뒤 위치 · 등받이 각도")
            PARK_ONE -> Cut(Panel.Park(1, s.parkedInReverse), "주차가 한 번에 들어갔다. 한 번에." + parkDirection(s), parkProof(s))
            PARK_BETTER, PARK_WORSE -> Cut(Panel.Park(s.parkShifts, s.parkedInReverse), "주차는 ${count(s.parkShifts)} 번 만에 들어갔다." + parkDirection(s), parkProof(s))
            WINDOWS -> Cut(Panel.Side(Mood.HAPPY, windowsDown = true, wind = true), "주인은 창문을 전부 내리고 달렸다.", "창문 네 곳의 위치")
            WINDOWS_RAIN -> Cut(Panel.Side(Mood.SHOCK, windowsDown = true, wind = true), "비가 오는데 창문을 전부 내렸다.", "와이퍼 켜짐 · 창문 네 곳의 위치")
            COLD -> if (s.fanAtMax) {
                Cut(Panel.Temp(s.setTemp!!, Mood.SHOCK), "에어컨은 ${s.setTemp}도, 바람은 최대.", "공조 설정 온도 · 풍량")
            } else {
                Cut(Panel.Temp(s.setTemp!!, Mood.SHOCK), "에어컨을 ${s.setTemp}도에 맞췄다.", "공조 설정 온도")
            }
            HOT -> Cut(Panel.Temp(s.setTemp!!, Mood.CALM), "히터를 ${s.setTemp}도까지 올렸다.", "공조 설정 온도")
            HEAT_UP -> Cut(Panel.Temp(s.setTemp!!, Mood.CALM, from = s.tempFrom), "달리는 중에 공조 온도를 ${s.tempFrom}도에서 ${s.setTemp}도로 올렸다.", "공조 설정 온도 출발 ${s.tempFrom}도 → 주차 전 ${s.setTemp}도")
            COOL_DOWN -> Cut(Panel.Temp(s.setTemp!!, Mood.CALM, from = s.tempFrom), "달리는 중에 공조 온도를 ${s.tempFrom}도에서 ${s.setTemp}도로 내렸다.", "공조 설정 온도 출발 ${s.tempFrom}도 → 주차 전 ${s.setTemp}도")
            SEAT_HEAT -> if (s.seatHeatMidDrive) {
                Cut(Panel.Seat(s.seatHeat, Mood.HAPPY), "달리던 중에 주인이 운전석 열선을 ${s.seatHeat}단으로 켰다.", "운전석 시트 열선 단계 · 출발 때 꺼짐")
            } else {
                Cut(Panel.Seat(s.seatHeat, Mood.HAPPY), "주인은 운전석 열선을 ${s.seatHeat}단으로 켰다.", "운전석 시트 열선 단계")
            }
            SEAT_VENT -> if (s.seatVentMidDrive) {
                Cut(Panel.Seat(s.seatVent, Mood.HAPPY, vent = true), "달리던 중에 주인이 운전석 통풍시트를 ${s.seatVent}단으로 켰다.", "운전석 시트 통풍 단계 · 출발 때 꺼짐")
            } else {
                Cut(Panel.Seat(s.seatVent, Mood.HAPPY, vent = true), "주인은 운전석 통풍시트를 ${s.seatVent}단으로 켰다.", "운전석 시트 통풍 단계")
            }
            GUESTS -> Cut(Panel.Side(Mood.HAPPY, guests = s.guests, fx = Fx.NOTE), "오늘은 뒷자리에 ${count(s.guests)} 명이 탔다.", "좌석 점유 · 안전벨트")
            WIPER_FAST -> Cut(Panel.Wiper, "와이퍼가 가장 빠르게 움직였다.", "와이퍼 빠르기 단계")
            LATE -> Cut(Panel.Front(Mood.SLEEPY, fx = Fx.ZZZ), "평소라면 주차장에서 자고 있을 시간이다.", "출발 시각 · 지난 8주 같은 시간대 주행 0번")
            BRAKE -> Cut(Panel.Brake, "오는 길에 ${count(s.joltCount)} 번 급하게 섰다.", "1초에 시속 7.5km 넘게 줄어든 순간 ${s.joltCount}번")
            ACCEL -> Cut(Panel.Accel, "오는 길에 가속 페달을 ${count(s.joltCount)} 번 세게 밟았다.", "1초에 시속 11km 넘게 늘어난 순간 ${s.joltCount}번")
        }

        /** 그 일만으로 한 화를 채울 때 셋째 컷. */
        fun mid(s: Seen): Cut = when (this) {
            STRANGER -> Cut(Panel.End(Mood.WARY, Driver.STRANGER, Fx.QUESTION), "…누구세요?")
            PARK_ONE -> Cut(Panel.End(Mood.SHOCK, fx = Fx.EXCLAIM), usualParkText(s), usualParkProof(s))
            PARK_BETTER -> Cut(Panel.End(Mood.CALM), usualParkText(s), usualParkProof(s))
            PARK_WORSE -> Cut(Panel.End(Mood.SWEAT, fx = Fx.SWEAT), "앞으로, 뒤로, 다시 앞으로.", "기어 R과 D를 오간 기록")
            WINDOWS -> Cut(Panel.End(Mood.HAPPY), "바람이 실내를 한 바퀴 돌고 나갔다.")
            WINDOWS_RAIN -> Cut(Panel.End(Mood.SHOCK, fx = Fx.QUESTION), "…비 오는데?")
            COLD -> Cut(Panel.End(Mood.CALM), "주인은 시원했을 거다.")
            HOT -> Cut(Panel.End(Mood.CALM), "주인 손이 차가웠나 보다.")
            HEAT_UP -> Cut(Panel.End(Mood.SWEAT, fx = Fx.SHIVER), "출발할 때는 몰랐는데, 꽤 추웠나 보다.")
            COOL_DOWN -> Cut(Panel.End(Mood.SWEAT, fx = Fx.SWEAT), "땀이 났을까. 나도 바람을 서둘렀다.")
            SEAT_HEAT -> if (s.seatHeatMidDrive) Cut(Panel.End(Mood.SWEAT, fx = Fx.SHIVER), "그새 몸이 식었나 보다.") else Cut(Panel.End(Mood.CALM), "지난 일주일 동안은 한 번도 켜지 않았다.", "지난 7일 열선 기록")
            SEAT_VENT -> if (s.seatVentMidDrive) Cut(Panel.End(Mood.SWEAT, fx = Fx.SWEAT), "등에 땀이 찼었나 보다.") else Cut(Panel.End(Mood.CALM), "지난 일주일 동안은 한 번도 켜지 않았다.", "지난 7일 통풍 기록")
            GUESTS -> Cut(Panel.End(Mood.PROUD), "안전벨트는 다 맸다. 내가 확인했다.", "뒷좌석 안전벨트")
            WIPER_FAST -> Cut(Panel.End(Mood.PROUD), "앞이 잘 보이게 열심히 닦았다.")
            LATE -> Cut(Panel.End(Mood.SLEEPY), "어디 가는 길인지 나는 묻지 않았다.")
            BRAKE -> Cut(Panel.End(Mood.SHOCK), "나는 놀랐다. 주인도 놀랐을 거다.")
            ACCEL -> Cut(Panel.End(Mood.SHOCK), "갑자기였다. 나는 준비가 안 돼 있었다.")
        }

        /** 맺는 컷(차의 생각). */
        fun end(s: Seen): Cut = when (this) {
            STRANGER -> Cut(Panel.End(Mood.SULK), listOf("주인이 돌아오면 전부 제자리로 돌려놓겠지. 그때까지는 삐져 있기로 했다.", "자리가 바뀌었다. 나는 원래 자리가 좋다.", "모르는 손길이었다. 주인이 빨리 왔으면 좋겠다.").pick(s))
            PARK_ONE -> Cut(Panel.End(Mood.HAPPY), listOf("아무도 못 봤다. 나만 봤다.", "한 번에 쏙. 오늘은 주인이 멋있었다.", "깔끔했다. 나도 줄 맞춰 서는 게 좋다.").pick(s))
            PARK_BETTER -> Cut(Panel.End(Mood.PROUD), listOf("늘고 있다. 말은 안 했다.", "조금씩 나아지고 있다. 나는 알아챘다.", "지난주보다 덜 헤맸다. 내가 기억한다.").pick(s))
            PARK_WORSE -> Cut(Panel.End(Mood.CALM), listOf("괜찮다. 나는 시간이 많다.", "오늘은 자리가 좁았나 보다. 들어가기만 하면 된다.", "몇 번 왔다 갔다 했다. 나는 어지럽지 않다.").pick(s))
            WINDOWS -> Cut(Panel.End(Mood.HAPPY), listOf("기분이 좋았을 거다. 나도 그랬다.", "바람 냄새가 좋았다. 나도 숨을 쉰 것 같았다.", "창밖 소리가 다 들렸다. 오랜만이다.").pick(s))
            WINDOWS_RAIN -> Cut(Panel.End(Mood.SULK), listOf("시트가 젖었다. 내 시트다.", "빗방울이 들어왔다. 다음엔 조금만 열어 줬으면.", "안이 축축해졌다. 그래도 바람은 좋았다.").pick(s))
            COLD -> Cut(Panel.End(Mood.SWEAT, fx = Fx.SHIVER), listOf("나는 좀 추웠다.", "내 안은 꽤 쌀쌀했다.", "주인은 시원했을 거다. 나는 떨었다.").pick(s))
            HOT -> Cut(Panel.End(Mood.HAPPY), listOf("나는 괜찮다. 원래 따뜻한 편이다.", "따뜻했다. 나도 몸이 풀렸다.", "히터 바람이 좋았다. 겨울이 오나 보다.").pick(s))
            HEAT_UP -> Cut(Panel.End(Mood.HAPPY), listOf("따뜻한 바람을 열심히 보냈다. 나는 그런 일이 좋다.", "금방 따뜻해졌다. 나도 몸이 풀렸다.", "바람이 따뜻해지자 차 안이 조용해졌다.").pick(s))
            COOL_DOWN -> Cut(Panel.End(Mood.HAPPY), listOf("시원한 바람을 열심히 보냈다.", "금방 시원해졌다. 나도 열이 식었다.", "차 안이 시원해졌다. 주인 얼굴도 펴졌을 거다.").pick(s))
            SEAT_HEAT -> Cut(Panel.End(Mood.HAPPY), listOf("날이 추워졌나 보다. 나는 데워 주는 게 좋다.", "등이 따뜻해지니 주인 어깨가 내려갔다.", "오늘은 내가 작은 난로였다.").pick(s))
            SEAT_VENT -> Cut(Panel.End(Mood.HAPPY), listOf("시트 사이로 바람을 보냈다. 나도 시원했다.", "등이 시원해졌을 거다. 허리도 좀 폈으면.", "오늘은 내가 부채였다.").pick(s))
            GUESTS -> Cut(Panel.End(Mood.HAPPY), listOf("오랜만에 시끄러웠다. 싫지 않았다.", "뒷자리가 꽉 찼다. 든든했다.", "웃음소리가 들렸다. 나도 기분이 좋았다.").pick(s))
            WIPER_FAST -> Cut(Panel.End(Mood.SULK), listOf("칭찬은 없었다.", "와이퍼가 쉬지 않았다. 팔이 아프다.", "비를 다 밀어냈다. 오늘은 내가 일등이다.").pick(s))
            LATE -> Cut(Panel.End(Mood.CALM), listOf("어디 갔는지는 말하지 않겠다. 나는 차니까.", "밤길은 조용했다. 나도 조용히 달렸다.", "늦은 시간이었다. 무사히 돌아와서 다행이다.").pick(s))
            BRAKE -> Cut(Panel.End(Mood.SWEAT, fx = Fx.SWEAT), listOf("아무 일 없었다. 그러면 됐다.", "멈췄다. 다치지 않았다. 그게 제일 중요하다.", "놀란 가슴은 금방 가라앉았다. 숨을 고르고 다시 달렸다.").pick(s))
            ACCEL -> Cut(Panel.End(Mood.CALM), listOf("급한 일이 있었나 보다. 나는 천천히 가는 것도 좋다.", "어디 그렇게 바빴을까. 나는 따라가느라 숨이 찼다.", "한 번 힘껏 달렸다. 다음엔 조금 살살 가 줬으면.").pick(s))
        }

        /** 다른 사건과 한 화에 들어갈 때 맺음말. 따로 없으면(null) 셋째 컷의 글과 맺는 컷의 글을 잇는다. */
        fun both(s: Seen): String? = when (this) {
            STRANGER -> "…누구세요? 주인이 돌아올 때까지 삐져 있기로 했다."
            GUESTS -> "안전벨트는 다 맸다. 오랜만에 시끄러웠다. 싫지 않았다."
            LATE -> "어디 가는 길인지 묻지 않았다. 나는 차니까."
            else -> null
        }

        /** 주인의 한마디와 엮을 때 쓰는, 그 일을 한 문장으로 줄인 것. */
        fun tie(s: Seen): String = when (this) {
            STRANGER -> "운전석은 누가 바꿔 놓았다."
            PARK_ONE -> "주차는 한 번에 들어갔다."
            PARK_BETTER -> "주차는 지난주보다 ${count(s.usualParkShifts - s.parkShifts)} 번 줄었다."
            PARK_WORSE -> "주차는 ${count(s.parkShifts)} 번 걸렸다."
            WINDOWS -> "창문은 전부 내리고 달렸다."
            WINDOWS_RAIN -> "비가 오는데 창문을 내렸다."
            COLD -> "에어컨은 ${s.setTemp}도였다."
            HOT -> "히터는 ${s.setTemp}도였다."
            HEAT_UP -> "달리다 온도를 ${s.setTemp}도로 올렸다."
            COOL_DOWN -> "달리다 온도를 ${s.setTemp}도로 내렸다."
            SEAT_HEAT -> if (s.seatHeatMidDrive) "달리다 열선을 켰다." else "운전석 열선은 ${s.seatHeat}단이었다."
            SEAT_VENT -> if (s.seatVentMidDrive) "달리다 통풍시트를 켰다." else "운전석 통풍시트는 ${s.seatVent}단이었다."
            GUESTS -> "뒷자리에는 ${count(s.guests)} 명이 탔다."
            WIPER_FAST -> "비가 쏟아져서 와이퍼가 가장 빠르게 움직였다."
            LATE -> "평소라면 자고 있을 시간이었다."
            BRAKE -> "오는 길에 ${count(s.joltCount)} 번 급하게 섰다."
            ACCEL -> "오는 길에 가속 페달을 ${count(s.joltCount)} 번 세게 밟았다."
        }
    }

    // ---------------------------------------------------------------- 문장 조각

    private const val COLD_AT = 18
    private const val HOT_AT = 26

    private val COUNT = listOf("영", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두")

    /** 횟수·사람 수를 세는 말("세 번", "두 명"). 열둘을 넘으면 숫자로 쓴다. */
    private fun count(n: Int): String = COUNT.getOrNull(n) ?: n.toString()

    /** 주인의 답은 대사가 아니라 근거(출처)로만 적는다. */
    private fun answerProof(said: String) = "주인의 답($said)"

    /** 주차 방향을 알면 한 마디 붙인다. 모르면 붙이지 않는다(측면 주차는 가리지 않는다). */
    private fun parkDirection(s: Seen): String = when (s.parkedInReverse) {
        true -> " 뒤로 넣었다."
        false -> " 앞으로 들어갔다."
        null -> ""
    }

    private fun parkProof(s: Seen) = "주차 직전 2분, 기어를 바꾼 횟수 ${s.parkShifts}번" + when (s.parkedInReverse) {
        true -> " · 마지막 기어 R"
        false -> " · 마지막 기어 D"
        null -> ""
    }
    private fun usualParkText(s: Seen) = "지난주는 평균 ${count(s.usualParkShifts)} 번이었다."
    private fun usualParkProof(s: Seen) = "지난주 주차 평균 ${s.usualParkShifts}번"

    /** "밤 11시", "새벽 2시". 해 진 뒤이지만 이른 시각이면 "저녁 5시". */
    private fun nightClock(hour: Int): String = when (hour) {
        0 -> "밤 12시"
        in 1..5 -> "새벽 ${hour}시"
        in 6..11 -> "아침 ${hour}시"
        in 12..19 -> "저녁 ${if (hour == 12) 12 else hour - 12}시"
        else -> "밤 ${hour - 12}시"
    }

    /** 밤에 나간 화의 제목("밤 열한 시", "새벽 두 시"). */
    private fun lateTitle(hour: Int): String = when (hour) {
        0 -> "밤 열두 시"
        in 1..5 -> "새벽 ${count(hour)} 시"
        in 20..23 -> "밤 ${count(hour - 12)} 시"
        else -> "늦은 시간"
    }
}
