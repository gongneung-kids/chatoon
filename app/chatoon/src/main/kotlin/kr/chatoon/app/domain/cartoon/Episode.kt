package kr.chatoon.app.domain.cartoon

/** 하루 중 언제 나갔는가. 해 질 녘과 밤은 기기 안 일몰 계산으로 가른다. */
enum class TimeOfDay { DAY, DUSK, NIGHT }

/** 와이퍼로 본 비. 와이퍼가 꺼져 있었으면 NONE, 켜져 있었으면 RAIN, 가장 빠른 단계였으면 HEAVY. */
enum class Rain { NONE, RAIN, HEAVY }

/** 차가 놀란 순간. 급하게 섰거나(BRAKE) 가속 페달을 세게 밟았다(ACCEL). */
enum class Jolt { NONE, BRAKE, ACCEL }

/** 차의 표정. 전조등이 눈, 그릴이 입이다. */
enum class Mood { CALM, HAPPY, PROUD, SULK, WARY, SHOCK, SLEEPY, SWEAT }

/** 컷에 덧붙는 효과 글자와 기호. */
enum class Fx { NONE, QUESTION, EXCLAIM, ZZZ, SWEAT, SHIVER, NOTE }

/** 마지막 컷(도착)의 배경 장소. 주인이 말한 행선지에서만 정한다. 모르면 null(일반 도로). */
enum class Place { HOME, OFFICE, HOSPITAL, MART, CAFE, NATURE }

/** 운전석에 앉은 사람. 낯선 사람이면 모자를 쓴 진한 실루엣으로 그린다. */
enum class Driver { OWNER, STRANGER }

/**
 * 컷 한 장의 그림. 그림은 미리 그려 둔 파일이 아니라 조각(배경, 차의 표정, 소품)을 조립해 그린다.
 * 배경(시간, 비)은 화 전체가 같으므로 [Episode]에 둔다.
 */
sealed interface Panel {
    /** 그날의 무대. 정면에서 본 차와 하늘. */
    data class Stage(val mood: Mood = Mood.CALM) : Panel

    /** 정면에서 본 차. */
    data class Front(val mood: Mood, val driver: Driver = Driver.OWNER, val fx: Fx = Fx.NONE) : Panel

    /** 와이퍼가 움직이는 앞유리를 가까이서. */
    data object Wiper : Panel

    /**
     * 위에서 본 주차. 화살표 수가 기어를 바꾼 횟수다.
     * [reverse]: 후면 주차(차 앞이 통로 쪽)면 true, 전면 주차(차 앞이 벽 쪽)면 false, 모르면 null.
     */
    data class Park(val shifts: Int, val reverse: Boolean? = null) : Panel

    /** 옆에서 본 차. 오른쪽으로 달린다. */
    data class Side(
        val mood: Mood,
        val windowsDown: Boolean = false,
        val guests: Int = 0,
        val wind: Boolean = false,
        val fx: Fx = Fx.NONE,
    ) : Panel

    /** 공조 설정 온도. 낮으면 눈꽃, 높으면 열기를 그린다. [from]이 있으면 달리다 그 온도에서 바꾼 것이라 "22°→26°"로 적는다. */
    data class Temp(val degrees: Int, val mood: Mood, val from: Int? = null) : Panel

    /** 운전석 시트 열선(엉뜨). 단계만큼 열기가 올라오는 시트를 그린다. [vent]면 통풍시트: 시트에서 시원한 바람이 나온다. */
    data class Seat(val level: Int, val mood: Mood, val vent: Boolean = false) : Panel

    /** 차가 묻고(흰 말풍선) 주인이 답한다(노란 말풍선). 주인의 말은 고치지 않고 옮긴다. */
    data class Ask(val question: List<String>, val answer: String) : Panel

    /** 급하게 선 순간. 차가 놀란 얼굴로만 그린다. */
    data object Brake : Panel

    /** 가속 페달을 세게 밟은 순간. 이것도 차가 놀란 얼굴로만 그린다. */
    data object Accel : Panel

    /** 마지막 컷. 차의 얼굴을 가까이서. */
    data class End(val mood: Mood, val driver: Driver = Driver.OWNER, val fx: Fx = Fx.NONE) : Panel
}

/**
 * 컷 한 장.
 *
 * @param caption 차가 쓴 글(일기체).
 * @param proof 이 컷이 어떤 신호에서 나왔는지. null이면 사실이 아니라 차의 생각을 그린 컷이다.
 */
data class Cut(
    val panel: Panel,
    val caption: String,
    val proof: String? = null,
    /** 그림 속 말풍선에 차가 하는 짧은 말(주인의 말이 아니다). 그림에 말하는 차가 없는 컷은 null. */
    val say: String? = null,
)

/**
 * 만화 한 화.
 *
 * @param title 화 제목. 주인이 어디 가는 길이었는지 말했으면 그 말이 제목이 된다.
 * @param line 세우는 순간 차가 하는 말(소리로도 읽는다).
 * @param cuts 네 컷.
 * @param now 세우는 순간 먼저 보여 주는 한 컷.
 * @param ask 이 화에서 차가 한 질문(한 줄 또는 두 줄). 묻지 않았으면 null.
 * @param said 그 질문에 주인이 한 말. 답하지 않았으면 null.
 */
data class Episode(
    val title: String,
    val line: String,
    val cuts: List<Cut>,
    val now: Cut,
    val ask: List<String>?,
    val said: String?,
    val time: TimeOfDay,
    val rain: Rain,
    /** 도착 장소(그림 배경). 주인이 말한 행선지에서만 정하고, 모르면 null. */
    val place: Place? = null,
)
