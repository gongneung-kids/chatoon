package kr.chatoon.app.domain.cartoon

/**
 * 한 화를 만드는 데 쓰는 재료: 차가 본 것과 주인이 한 말.
 *
 * 차가 본 것은 차량 신호에서만 온다. 위치·경로·카메라·늘 켜 둔 마이크는 쓰지 않는다.
 * 주인의 말은 차가 세운 뒤에 물었을 때 주인이 답한 것이고, 고치지 않고 그대로 둔다.
 *
 * @param day 요일("화요일").
 * @param time 낮, 해 질 녘, 밤.
 * @param hour 출발 시각의 시(0~23). 밤일 때만 문장에 쓴다.
 * @param rain 와이퍼로 본 비.
 * @param parkShifts 주차 직전 2분 동안 기어를 바꾼 횟수.
 * @param usualParkShifts 평소의 그 횟수(지난주 평균을 반올림).
 * @param windowsAllDown 달리는 동안 창문 네 곳을 전부 내렸는가.
 * @param setTemp 공조 설정 온도(도). 모르면 null.
 * @param fanAtMax 그때 풍량이 가장 센 단계였는가.
 * @param guests 뒷자리 손님 수(좌석 점유). 에뮬레이터에서는 값이 따라오지 않아 0으로 둔다(실차 확인 필요).
 * @param strangerSeat 운전석 위치가 평소와 달랐는가. 에뮬레이터에서는 확인할 수 없어 false로 둔다(실차 확인 필요).
 * @param lateUnusual 밤에 나갔고, 평소 이 시간대에는 나가지 않았는가.
 * @param jolt 차가 놀란 순간.
 * @param joltCount 그런 순간이 몇 번 있었는가.
 * @param why "어디 가시는 길이었나요?"에 주인이 한 말. 답하지 않았으면 빈 글.
 * @param joltWhy 놀란 순간에 대해 물었을 때 주인이 한 말. 답하지 않았으면 빈 글.
 */
data class Seen(
    val day: String,
    val time: TimeOfDay,
    val hour: Int,
    val rain: Rain = Rain.NONE,
    val parkShifts: Int,
    val usualParkShifts: Int,
    val windowsAllDown: Boolean = false,
    val setTemp: Int? = null,
    val fanAtMax: Boolean = true,
    val guests: Int = 0,
    val strangerSeat: Boolean = false,
    val lateUnusual: Boolean = false,
    val jolt: Jolt = Jolt.NONE,
    val joltCount: Int = 1,
    val why: String = "",
    val joltWhy: String = "",
    /** 후면 주차(true)인지 전면 주차(false)인지. P단 직전 마지막 기어로 안다. 모르면 null. */
    val parkedInReverse: Boolean? = null,
    /** 평소와 달리 켠 운전석 열선 단계(지난 7일 동안 켠 적이 없을 때만). 그렇지 않으면 0. */
    val seatHeat: Int = 0,
    /** 열선을 출발 때는 꺼 두었다가 달리는 중에 켰는가(그러면 "달리다 켠 날"로 쓴다). */
    val seatHeatMidDrive: Boolean = false,
    /** 평소와 달리 켠 운전석 통풍시트 단계(달리다 켰거나 지난 7일 동안 켠 적이 없을 때만). 그렇지 않으면 0. */
    val seatVent: Int = 0,
    val seatVentMidDrive: Boolean = false,
    /** 달리는 중에 공조 설정 온도를 2도 이상 바꿨으면 출발 때 온도(바꾼 뒤 온도는 [setTemp]). 아니면 null. */
    val tempFrom: Int? = null,
    /** 같은 상황에서 어떤 표현을 쓸지 고르는 번호. 주행마다 정해져(출발 시각) 같은 화는 늘 같은 문장이다. 0이면 기본 문장. */
    val variant: Int = 0,
    /** 평소와 다른 일이 없는 날에 쓰는, 지난 기록에서 센 사실([Routine]). 기본은 없음(예전과 같은 「별일 없는 날」). */
    val routine: Routine = Routine.NONE,
    /** 기록을 시작하고 첫 주행인가(지난 기록이 하나도 없음). 그러면 어제·평소와 견주는 말을 쓰지 않는다. */
    val firstTrip: Boolean = false,
    /** 어제 비슷한 시각(앞뒤 한 시간)에 나가 비슷한 시간(0.7~1.3배)만큼 달린 주행이 있었는가. 있어야 「어제와 비슷한」이라고 쓴다. */
    val likeYesterday: Boolean = true,
    /** 지난 7일 안에 주차 기록이 있는가. 있어야 「평소와 비슷했다」라고 쓴다. */
    val parkUsualKnown: Boolean = true,
    /** 이 주행의 운전 시간(분). 모르면 null. */
    val driveMinutes: Int? = null,
) {
    init {
        require(hour in 0..23) { "hour must be 0..23" }
        require(parkShifts >= 0 && usualParkShifts >= 0) { "park shifts must not be negative" }
        require(guests >= 0) { "guests must not be negative" }
        require(seatHeat >= 0 && seatVent >= 0) { "seat levels must not be negative" }
        require(tempFrom == null || setTemp != null) { "tempFrom needs setTemp" }
        require(joltCount >= 1) { "joltCount must be at least 1" }
    }

    companion object {
        /** 주인의 말은 이 길이까지만 받는다(말풍선 두 줄, 한 줄 8자). */
        const val MAX_ANSWER_LENGTH = 16

        /** 주인의 말을 다듬지 않되, 앞뒤 빈칸을 떼고 길이만 맞춘다. */
        fun trimAnswer(raw: String): String = raw.trim().take(MAX_ANSWER_LENGTH).trim()
    }
}
