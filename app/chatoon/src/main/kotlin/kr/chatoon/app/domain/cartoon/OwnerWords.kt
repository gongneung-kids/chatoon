package kr.chatoon.app.domain.cartoon

/**
 * 주인의 한마디를 상황으로 가르는 낱말 사전.
 *
 * 생성형 AI를 쓰지 않는다. 정해 둔 낱말이 들어 있는지만 보고, 위에서부터 먼저 맞는 것을 쓴다.
 * 사전에 없는 말은 뜻을 넘겨짚지 않고 [UNKNOWN_SITUATION]으로 받아 그대로 옮기기만 한다.
 * 차의 반응(open, close)은 기분과 짐작만 쓰고, 일어난 일처럼 쓰지 않는다.
 */
internal data class Situation(
    val key: String,
    val words: List<String>,
    /** 화 제목. 주인의 말을 그대로 쓰지 않고 상황으로 붙인다. 모르는 말이면 null(사건 제목을 쓴다). */
    val title: String?,
    /** 차가 주인의 답을 자기 말로 옮긴 문장. 주인의 말을 그대로 따오지 않는다. */
    val told: List<String>,
    val open: List<String>,
    val close: List<String>,
    val mood: Mood,
    val fx: Fx = Fx.NONE,
    /** 도착 장소(그림 배경). 모르면 null. */
    val place: Place? = null,
)

internal val SITUATIONS = listOf(
    Situation("interview", listOf("면접", "시험", "발표", "오디션", "수능"), "떨리는 날", listOf("중요한 자리에 가는 길이었다."), listOf("떨렸겠다."), listOf("잘 됐을 거다. 나는 안다.", "주인은 잘 해낼 거다. 내가 데려다줬으니까."), Mood.PROUD, place = Place.OFFICE),
    Situation("hospital", listOf("병원", "약국", "치과", "검진", "진료", "문병"), "병원 가는 길", listOf("병원에 가는 길이었다."), listOf("걱정이 많았겠다."), listOf("아프지 않았으면 좋겠다.", "얼른 나았으면 좋겠다. 나는 기다릴 수 있다."), Mood.CALM, place = Place.HOSPITAL),
    Situation("date", listOf("데이트", "소개팅", "여자친구", "남자친구", "여친", "남친", "애인"), "설레는 길", listOf("누군가를 만나러 가는 길이었다."), listOf("그래서 그랬구나."), listOf("오늘은 나도 좀 깨끗해 보이고 싶었다.", "좋은 사람을 만나러 가는 길이었겠지. 나도 괜히 들떴다."), Mood.HAPPY, Fx.NOTE, place = Place.CAFE),
    Situation("pickup", listOf("픽업", "마중", "데리러", "태우러", "데려다"), "데리러 가는 길", listOf("누군가를 데리러 가는 길이었다."), listOf("기다리는 사람이 있었다."), listOf("돌아올 때는 혼자가 아니었겠다. 그런 길이 좋다.", "뒷자리가 따뜻해졌다. 그런 길이 좋다."), Mood.HAPPY),
    Situation("family", listOf("엄마", "아빠", "부모님", "본가", "할머니", "할아버지", "가족", "어머니", "아버지", "동생"), "가족에게 가는 길", listOf("가족을 보러 가는 길이었다."), listOf("가족 일이었다."), listOf("이런 날은 나도 더 조심해서 달리고 싶다.", "가는 길이 조금 짧게 느껴졌다."), Mood.CALM, place = Place.HOME),
    Situation("work", listOf("출근", "퇴근", "회사", "야근", "회의", "출장", "미팅", "알바", "근무"), "일하러 가는 길", listOf("일하러 가는 길이었다."), listOf("오늘도 수고했다."), listOf("주인이 일하는 동안 나는 기다렸다. 그게 내 일이다.", "오늘도 같이 출근했다. 우리는 좋은 동료다."), Mood.PROUD, place = Place.OFFICE),
    Situation("shopping", listOf("마트", "장보", "장 보", "쇼핑", "코스트코", "이마트", "백화점"), "장 보러 간 날", listOf("뭔가를 사러 가는 길이었다."), listOf("장을 봤다."), listOf("뭘 샀는지는 못 봤다. 트렁크는 내 등 뒤에 있다.", "돌아오는 길은 조금 무거웠다. 좋은 무게였다."), Mood.CALM, place = Place.MART),
    Situation("workout", listOf("운동", "헬스", "수영", "골프", "축구", "등산", "필라테스", "요가", "러닝", "테니스"), "운동 가는 길", listOf("운동하러 가는 길이었다."), listOf("몸을 쓰고 왔다."), listOf("땀을 많이 흘렸겠다. 창문 좀 열어 줬으면.", "돌아오는 길은 개운했을 거다. 나도 좀 씻고 싶다."), Mood.SULK),
    Situation("food", listOf("밥", "점심", "저녁", "맛집", "카페", "커피", "치킨", "회식"), "먹으러 간 날", listOf("뭔가 먹으러 가는 길이었다."), listOf("든든했겠다."), listOf("맛있었겠다. 부럽다. 나는 못 먹는다.", "주인이 배부르면 나도 기분이 좋다."), Mood.SULK, place = Place.CAFE),
    Situation("trip", listOf("여행", "바다", "캠핑", "놀러", "휴가", "드라이브", "나들이"), "놀러 가는 길", listOf("놀러 가는 길이었다."), listOf("좋은 날이었다."), listOf("나는 이런 날을 기다린다.", "이런 날은 기름이 아깝지 않다."), Mood.HAPPY, Fx.NOTE, place = Place.NATURE),
    Situation("just", listOf("그냥", "답답", "바람", "심심"), "그냥 달린 날", listOf("그냥 바람 쐬러 나온 길이었다."), listOf("이유는 없었다."), listOf("나는 그런 날이 제일 좋다.", "이유 없는 드라이브가 제일 좋다."), Mood.HAPPY, Fx.NOTE),
)

internal val UNKNOWN_SITUATION =
    Situation("unknown", emptyList(), null, listOf("어디 가는 길이었는지 주인이 알려 줬다."), listOf("그런 날도 있다."), listOf("무슨 뜻인지는 다 모르지만 적어 둔다. 나는 차니까.", "다 알아듣지는 못했지만 알려 줘서 좋았다."), Mood.CALM)

internal fun situationOf(why: String): Situation =
    SITUATIONS.firstOrNull { s -> s.words.any { it in why } } ?: UNKNOWN_SITUATION

/**
 * 급하게 서거나 가속 페달을 세게 밟은 이유를 가르는 낱말 사전.
 * 차는 꾸짖지 않는다. 반응은 걱정과 바람만 쓴다.
 */
internal data class JoltReason(
    val key: String,
    val words: List<String>,
    /** 화 제목. 모르는 말이면 null(사건 제목을 쓴다). */
    val title: String?,
    /** 차가 주인의 답을 자기 말로 옮긴 문장("~다고 한다"). 주인의 말을 그대로 따오지 않는다. */
    val told: List<String>,
    val close: List<String>,
    val mood: Mood,
)

internal val JOLT_REASONS = listOf(
    JoltReason("lapse", listOf("한눈", "전방주시", "폰", "딴생각", "졸다", "졸려", "졸음", "멍때", "멍 때"), "한눈판 순간", listOf("잠깐 앞을 놓쳤다고 한다."), listOf("나는 심장이 없는데도 멎는 줄 알았다. 다음에는 같이 앞을 봤으면 좋겠다.", "깜짝 놀랐다. 그래도 아무 일 없었다. 나는 계속 앞을 보고 있을게.", "둘 다 놀랐다. 오늘은 그걸로 됐다."), Mood.SWEAT),
    JoltReason("near", listOf("사고", "날 뻔", "날뻔", "박을 뻔", "죽을 뻔", "큰일"), "아찔했던 순간", listOf("큰일 날 뻔했다고 한다."), listOf("아무 일 없어서 다행이다. 오늘 일은 여기 적어 둔다.", "무사해서 다행이다. 나도 한숨 돌렸다.", "아찔했다. 오늘은 푹 쉬었으면 좋겠다."), Mood.SWEAT),
    // 보기 「앞차가 갑자기」·「누가 끼어들어서」가 '앞에 뭔가 불쑥'으로 뭉뚱그려지지 않게, 더 구체적인 이유를 먼저 본다.
    JoltReason("frontstop", listOf("앞차가 갑자기", "앞차가 급", "앞차 급", "앞차가 섰", "앞차가 서"), "앞차가 갑자기 선 날", listOf("앞차가 갑자기 섰다고 한다."), listOf("앞차를 따라 나도 꽉 섰다. 잘 멈췄다.", "앞차 사정은 모르지만, 우리는 잘 섰다.", "다음엔 앞차와 거리를 조금 더 두자. 나도 덜 놀란다."), Mood.PROUD),
    JoltReason("cutin", listOf("끼어들", "칼치기", "끼어드"), "누가 끼어든 날", listOf("누가 앞에 끼어들었다고 한다."), listOf("잘 섰다. 그러라고 브레이크가 있다.", "끼어든 차는 바빴나 보다. 우리는 잘 섰다.", "나도 깜짝 놀랐다. 그래도 잘 멈췄다."), Mood.PROUD),
    JoltReason("sudden", listOf("갑자기", "튀어나", "고양이", "강아지", "사람", "어린이", "자전거", "킥보드", "오토바이"), "앞에 불쑥", listOf("앞에 뭔가 갑자기 나타났다고 한다."), listOf("잘 섰다. 그러라고 브레이크가 있다.", "잘 멈췄다. 우리 둘 다 잘했다.", "갑자기 나타나면 나도 놀란다. 그래도 잘 섰다."), Mood.PROUD),
    JoltReason("signal", listOf("신호", "노란불", "빨간불", "황색"), "바뀐 신호", listOf("신호가 바뀌었다고 한다."), listOf("서는 게 맞다. 나는 서는 것도 잘한다.", "신호를 지켰다. 나는 그런 주인이 좋다.", "노란불엔 서는 거다. 잘했다."), Mood.PROUD),
    JoltReason("hurry", listOf("늦", "지각", "급해", "급한", "바빠", "바쁜"), "급한 길", listOf("급한 일이 있었다고 한다."), listOf("5분 일찍 나오면 나도 덜 놀란다.", "바쁜 날이었나 보다. 천천히 가도 늦지 않았을 거다.", "급할수록 나는 더 조심하고 싶다."), Mood.SULK),
    JoltReason("slowcar", listOf("앞차", "느리", "느려", "답답", "추월"), "느린 앞차", listOf("앞차가 느렸다고 한다. 주인은 답답했나 보다."), listOf("나는 천천히 가는 것도 좋은데.", "앞차도 사정이 있었겠지. 나는 기다리는 것도 잘한다.", "조금 답답했겠다. 나는 느긋한 편이다."), Mood.SULK),
)

internal val UNKNOWN_JOLT_REASON = JoltReason("unknown", emptyList(), null, listOf("왜 그랬는지 주인이 말해 줬다."), listOf("놀랐지만 괜찮다. 적어 둔다.", "이유는 주인만 안다. 나는 적어만 둔다."), Mood.CALM)

internal fun joltReasonOf(why: String): JoltReason =
    JOLT_REASONS.firstOrNull { r -> r.words.any { it in why } } ?: UNKNOWN_JOLT_REASON

/** 차의 질문. 한 번 세울 때 하나만 묻는다. */
val ASK_WHERE: List<String> = listOf("어디 가시는 길이었나요?")

fun askAbout(jolt: Jolt): List<String>? = when (jolt) {
    Jolt.BRAKE -> listOf("아까 급하게 서셨죠.", "괜찮으세요?")
    Jolt.ACCEL -> listOf("아까 가속 페달을 세게 밟으셨죠.", "급한 일 있었나요?")
    Jolt.NONE -> null
}

/** “…”라고 / “…”이라고. 마지막 글자의 받침으로 고른다. 한글이 아니면 "라고"로 쓴다. */
internal fun quoted(said: String): String {
    val last = said.last()
    val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
    return "“$said”" + if (hasFinal) "이라고" else "라고"
}

/** 말풍선에 넣으려고 한 줄 8자까지로 나눈다. 가운데에서 가장 가까운 빈칸에서 나누고, 빈칸이 마땅치 않으면 반으로 자른다. */
fun splitForBalloon(said: String): List<String> {
    if (said.length <= BALLOON_LINE) return listOf(said)
    val mid = (said.length + 1) / 2
    var best = -1
    var gap = Int.MAX_VALUE
    for (i in 1 until said.length - 1) {
        val distance = kotlin.math.abs(i - mid)
        if (said[i] == ' ' && distance < gap) {
            gap = distance
            best = i
        }
    }
    return if (best > 0 && best <= BALLOON_LINE && said.length - best - 1 <= BALLOON_LINE) {
        listOf(said.substring(0, best), said.substring(best + 1))
    } else {
        listOf(said.substring(0, mid), said.substring(mid))
    }
}

private const val BALLOON_LINE = 8
