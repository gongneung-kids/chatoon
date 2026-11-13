package kr.chatoon.app.domain.cartoon

import kr.chatoon.app.domain.analysis.SunCalculator
import kr.chatoon.app.domain.drive.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 평소와 다른 일이 없는 날에도 차가 아는 사실. 모두 지난 주행 기록에서만 센다(위치·경로를 쓰지 않는다).
 *
 * 매일 같은 시간에 출퇴근만 하는 사람은 거의 모든 화가 「별일 없는 날」이 된다(기획서 ※6, `CartoonRepetitionTest`).
 * 그런 날에도 차만 아는 숫자(몇 번째 주행, 며칠 만의 비, 며칠 쉬고 나왔는지, 며칠째 같은 시간인지, 함께 달린 거리)로
 * 화를 다르게 쓴다. 고비가 되는 값일 때만 채우고, 아니면 null이다. 모두 null이면 예전과 같은 「별일 없는 날」이다.
 *
 * @param tripNumber 기록이 남아 있는 주행 가운데 이번이 몇 번째인지. 고비(10, 50, 100, 그 뒤 100마다)일 때만.
 * @param kmMilestone 함께 달린 거리(주행마다 누적 주행거리 차이의 합)가 이번 주행에서 넘은 고비(500km마다).
 * @param dryDays 이번 주행에 비가 왔고, 앞선 비 온 주행이 이만큼 전이었다(3일 이상일 때만).
 * @param restDays 앞선 주행이 끝나고 이만큼 지나서 나왔다(2일 이상일 때만).
 * @param sameSlotDays 오늘까지 이만큼의 날 동안 날마다 같은 시간대(앞뒤 한 시간)에 나왔다(5, 10, 20, 30일 때만).
 * @param oneShotStreak 이번까지 이만큼 이어서 주차가 한 번에 들어갔다(3, 5, 10, 20일 때만).
 */
data class Routine(
    val tripNumber: Int? = null,
    val kmMilestone: Int? = null,
    val dryDays: Int? = null,
    val restDays: Int? = null,
    val sameSlotDays: Int? = null,
    val oneShotStreak: Int? = null,
) {
    companion object {
        val NONE = Routine()

        private const val RAIN_SHARE = TripSeen.RAIN_SHARE
        private const val KM_STEP = 500
        private val SLOT_MARKS = setOf(5, 10, 20, 30)
        private val ONE_SHOT_MARKS = setOf(3, 5, 10, 20)

        /**
         * @param before 이번 주행보다 먼저 끝난 주행들(순서는 상관없다).
         */
        fun of(trip: Trip, before: List<Trip>, zone: ZoneId = SunCalculator.SEOUL_ZONE): Routine {
            fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            val today = date(trip.startedAtMillis)
            val sorted = before.sortedBy { it.startedAtMillis }

            val n = sorted.size + 1
            val tripNumber = n.takeIf { it == 10 || it == 50 || (it >= 100 && it % 100 == 0) }

            val kmBefore = sorted.sumOf { it.distanceKm ?: 0.0 }
            val kmMilestone = trip.distanceKm?.let { km ->
                val a = (kmBefore / KM_STEP).toInt()
                val b = ((kmBefore + km) / KM_STEP).toInt()
                if (b > a && b > 0) b * KM_STEP else null
            }

            val rainy = (trip.rainShare ?: 0.0) >= RAIN_SHARE
            val dryDays = if (!rainy) null else sorted.lastOrNull { (it.rainShare ?: 0.0) >= RAIN_SHARE }
                ?.let { ChronoUnit.DAYS.between(date(it.startedAtMillis), today).toInt() }
                ?.takeIf { it >= 3 }

            val restDays = sorted.maxByOrNull { it.endedAtMillis }
                ?.let { ChronoUnit.DAYS.between(date(it.endedAtMillis), today).toInt() }
                ?.takeIf { it >= 2 }

            val hour = Instant.ofEpochMilli(trip.startedAtMillis).atZone(zone).hour
            val slotDays = sorted.filter {
                val h = Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).hour
                val d = abs(h - hour)
                minOf(d, 24 - d) <= 1
            }.map { date(it.startedAtMillis) }.toSet()
            var streak = 1
            while (today.minusDays(streak.toLong()) in slotDays) streak++
            val sameSlotDays = streak.takeIf { it in SLOT_MARKS }

            val oneShotStreak = if (trip.parkingGearShifts > 1) null else {
                (1 + sorted.asReversed().takeWhile { it.parkingGearShifts <= 1 }.size).takeIf { it in ONE_SHOT_MARKS }
            }

            return Routine(tripNumber, kmMilestone, dryDays, restDays, sameSlotDays, oneShotStreak)
        }
    }
}
