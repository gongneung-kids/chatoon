package kr.chatoon.app.domain.cartoon

import kr.chatoon.app.domain.analysis.SunCalculator
import kr.chatoon.app.domain.drive.Trip
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 저장된 주행 기록([Trip])에서 한 화의 재료([Seen])를 만든다.
 *
 * 주행 기록에 있는 것만 쓴다: 출발 시각, 와이퍼, 주차 직전 기어, 급가속·급감속, 창문, 공조 설정 온도(출발 때와 주차 전)·풍량,
 * 운전석 열선·통풍(출발 때와 가장 높은 단계).
 * 손님(좌석 점유)과 운전석 위치는 에뮬레이터에서 값이 따라오지 않아 기록하지 않으므로 비워 둔다(그런 컷은 나오지 않는다).
 * 주인의 말(why, joltWhy)은 차가 물어서 받은 답이 있을 때 [answers]로 넘긴다.
 */
class TripSeen(
    private val sun: SunCalculator = SunCalculator(),
    private val zone: ZoneId = SunCalculator.SEOUL_ZONE,
) {
    /** 주인이 답한 말. 없으면 빈 글. */
    data class Answers(val why: String = "", val joltWhy: String = "")

    /**
     * @param trip 그릴 주행.
     * @param history 모든 주행(그릴 주행이 들어 있어도 된다). 평소 값을 구하는 데 쓴다.
     */
    fun of(trip: Trip, history: List<Trip>, answers: Answers = Answers()): Seen {
        val start = Instant.ofEpochMilli(trip.startedAtMillis).atZone(zone)
        val before = history.filter { it.endedAtMillis <= trip.startedAtMillis && it !== trip }
        val time = timeOf(trip.startedAtMillis)
        val hard = trip.hardDecelerations ?: 0
        val fast = trip.hardAccelerations ?: 0
        val jolt = when {
            hard > 0 -> Jolt.BRAKE
            fast > 0 -> Jolt.ACCEL
            else -> Jolt.NONE
        }
        return Seen(
            day = DAY_NAMES.getValue(start.dayOfWeek),
            time = time,
            hour = start.hour,
            rain = if ((trip.rainShare ?: 0.0) >= RAIN_SHARE) Rain.RAIN else Rain.NONE,
            parkShifts = trip.parkingGearShifts,
            usualParkShifts = usualParkShifts(trip, before),
            lateUnusual = time == TimeOfDay.NIGHT && lateUnusual(start.hour, trip, before),
            jolt = jolt,
            joltCount = (if (jolt == Jolt.BRAKE) hard else fast).coerceAtLeast(1),
            why = Seen.trimAnswer(answers.why),
            joltWhy = if (jolt == Jolt.NONE) "" else Seen.trimAnswer(answers.joltWhy),
            parkedInReverse = trip.parkedInReverse,
            windowsAllDown = (trip.windowsOpenMillis ?: 0) >= WINDOWS_OPEN_MILLIS,
            setTemp = trip.setTempC,
            fanAtMax = trip.fanAtMax == true,
            seatHeat = seatEvent(trip.seatHeatLevel, trip.seatHeatStart, trip, before) { it.seatHeatLevel },
            seatHeatMidDrive = midDrive(trip.seatHeatLevel, trip.seatHeatStart),
            seatVent = seatEvent(trip.seatVentLevel, trip.seatVentStart, trip, before) { it.seatVentLevel },
            seatVentMidDrive = midDrive(trip.seatVentLevel, trip.seatVentStart),
            tempFrom = tempFrom(trip),
            variant = Math.floorMod(trip.startedAtMillis / 1000, 9973L).toInt(),
            routine = Routine.of(trip, before, zone),
        )
    }

    /** 해가 지기 전 [DUSK_MILLIS]부터 해가 진 뒤 [DUSK_MILLIS]까지는 해 질 녘, 그 밖의 어두운 때는 밤. */
    fun timeOf(millis: Long): TimeOfDay {
        val light = sun.daylight(sun.localDate(millis))
        return when {
            millis < light.sunriseMillis -> TimeOfDay.NIGHT
            millis < light.sunsetMillis - DUSK_MILLIS -> TimeOfDay.DAY
            millis < light.sunsetMillis + DUSK_MILLIS -> TimeOfDay.DUSK
            else -> TimeOfDay.NIGHT
        }
    }

    /** 지난 7일 주차 횟수 평균(반올림). 지난 기록이 없으면 이번 값과 같게 둔다(비교할 평소가 없다). */
    private fun usualParkShifts(trip: Trip, before: List<Trip>): Int {
        val week = before.filter { trip.startedAtMillis - it.startedAtMillis <= WEEK_MILLIS }
        if (week.isEmpty()) return trip.parkingGearShifts
        return week.map { it.parkingGearShifts }.average().roundToInt()
    }

    /**
     * 시트 열선·통풍을 켠 것이 사건인가. 달리는 중에 켰거나(출발 때는 꺼져 있었음), 지난 7일 동안 켠 적이 없으면 그 단계,
     * 아니면 0(매일 켜는 것은 평소 일이라 사건이 아니다).
     */
    private fun seatEvent(level: Int?, start: Int?, trip: Trip, before: List<Trip>, levelOf: (Trip) -> Int?): Int {
        val on = level ?: return 0
        if (on <= 0) return 0
        if (midDrive(level, start)) return on
        val usedLately = before.any { trip.startedAtMillis - it.startedAtMillis <= WEEK_MILLIS && (levelOf(it) ?: 0) > 0 }
        return if (usedLately) 0 else on
    }

    private fun midDrive(level: Int?, start: Int?): Boolean = start == 0 && (level ?: 0) > 0

    /** 달리는 중에 설정 온도를 [TEMP_CHANGE]도 넘게 바꿨으면 출발 때 온도. */
    private fun tempFrom(trip: Trip): Int? {
        val from = trip.setTempStartC ?: return null
        val to = trip.setTempC ?: return null
        return from.takeIf { abs(to - it) >= TEMP_CHANGE }
    }

    /** 지난 8주 동안 같은 시간대(앞뒤 한 시간)에 나간 적이 없으면 평소와 다른 밤이다. 지난 기록이 너무 적으면 판단하지 않는다. */
    private fun lateUnusual(hour: Int, trip: Trip, before: List<Trip>): Boolean {
        val recent = before.filter { trip.startedAtMillis - it.startedAtMillis <= LATE_LOOKBACK_MILLIS }
        if (recent.size < MIN_TRIPS_FOR_USUAL) return false
        return recent.none {
            val h = Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).hour
            val d = abs(h - hour)
            minOf(d, 24 - d) <= 1
        }
    }

    companion object {
        /** 주행 시간 중 와이퍼가 이 비율 이상 켜져 있었으면 비 온 날로 본다. */
        const val RAIN_SHARE = 0.2
        /** 달리는 동안 창문을 전부 이만큼 넘게 내리고 있었으면 "창문을 내리고 달린 날"이다(톨게이트에서 잠깐 연 것은 세지 않는다). */
        const val WINDOWS_OPEN_MILLIS = 60_000L
        /** 달리다 설정 온도를 이만큼(도) 넘게 바꿔야 "온도를 올린/내린 날"이다(0.5도씩 만지작거린 것은 세지 않는다). */
        const val TEMP_CHANGE = 2
        const val DUSK_MILLIS = 40 * 60 * 1000L
        private const val DAY = 24 * 60 * 60 * 1000L
        const val WEEK_MILLIS = 7 * DAY
        /** 시안의 "지난 8주 같은 시간대 주행 0번"과 같다. */
        const val LATE_LOOKBACK_MILLIS = 56 * DAY
        const val MIN_TRIPS_FOR_USUAL = 5

        private val DAY_NAMES = mapOf(
            DayOfWeek.MONDAY to "월요일", DayOfWeek.TUESDAY to "화요일", DayOfWeek.WEDNESDAY to "수요일",
            DayOfWeek.THURSDAY to "목요일", DayOfWeek.FRIDAY to "금요일", DayOfWeek.SATURDAY to "토요일", DayOfWeek.SUNDAY to "일요일",
        )
    }
}
