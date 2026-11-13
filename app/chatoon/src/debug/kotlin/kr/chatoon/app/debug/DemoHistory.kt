package kr.chatoon.app.debug

import kr.chatoon.app.domain.analysis.SunCalculator
import kr.chatoon.app.domain.analysis.SunCalculator.Companion.DAY_MILLIS
import kr.chatoon.app.domain.drive.Trip
import kotlin.random.Random

/**
 * 시연용 12주 주행 기록. 몇 주치 연재(지난 화)를 공모전 기간에 실제로 모을 수 없어서 만든다(debug 전용).
 *
 * 평일 출퇴근과 격주 토요일 나들이. 차툰이 그리는 순간이 가끔 섞인다: 비(와이퍼), 창문을 내리고 달림, 달리다 히터·에어컨 온도를
 * 바꿈, 달리다 엉뜨·통풍시트를 켬, 급하게 섬, 주차를 몇 번 고쳐 넣음, 늦은 밤 귀가. 시드를 고정해 항상 같은 기록이 나온다.
 */
object DemoHistory {

    private const val WEEKS = 12
    private const val MINUTE = 60_000L

    /** 시연 기록이 시작될 때 차의 누적 주행거리(km). 기록 거리를 더한 값이 시연 뒤 콘솔 주행거리가 된다. */
    const val ODOMETER_START_KM = 23_450.0

    fun generate(nowMillis: Long, seed: Int = 42): List<Trip> {
        val random = Random(seed)
        val sun = SunCalculator()
        val trips = mutableListOf<Trip>()
        val firstDay = sun.localDate(nowMillis).minusDays(WEEKS * 7L - 1)
        for (dayIndex in 0 until WEEKS * 7) {
            val date = firstDay.plusDays(dayIndex.toLong())
            val dow = date.dayOfWeek.value // 1=월 … 7=일
            // (출발 분, 운전 분)
            val plan = mutableListOf<Pair<Int, Long>>()
            if (dow <= 5) {
                plan += 7 * 60 + 40 + random.nextInt(0, 30) to random.nextLong(40, 60)
                val back = if ((dow == 2 || dow == 4) && random.nextInt(3) == 0) 22 * 60 + random.nextInt(0, 90) else 18 * 60 + 20 + random.nextInt(0, 50)
                plan += back to random.nextLong(40, 60)
            } else if (dow == 6 && (dayIndex / 7) % 2 == 0) {
                plan += 10 * 60 + random.nextInt(0, 60) to random.nextLong(60, 100)
                plan += 16 * 60 + random.nextInt(0, 60) to random.nextLong(60, 100)
            }
            for ((startMinute, minutes) in plan) {
                val start = date.atStartOfDay(SunCalculator.SEOUL_ZONE).toInstant().toEpochMilli() + startMinute * MINUTE
                val end = start + minutes * MINUTE
                if (end > nowMillis) continue
                trips += trip(random, start, end, minutes)
            }
        }
        // 앱을 열면 가장 최근 화가 먼저 보인다. 시연 첫 화면이 밋밋하지 않게 마지막 주행은 여러 일이 겹친 날로 둔다:
        // 비가 와 와이퍼를 켜고, 창문을 내린 채 달리다, 추워 히터 온도를 올리고, 한 번 급하게 선 저녁.
        val sorted = trips.sortedBy { it.startedAtMillis }.toMutableList()
        sorted.lastOrNull()?.let { last ->
            sorted[sorted.lastIndex] = last.copy(
                rainShare = 0.7,
                windowsOpenMillis = minOf(last.durationMillis, 12 * MINUTE),
                setTempStartC = 22,
                setTempC = 26,
                seatHeatStart = 0,
                seatHeatLevel = 2,
                hardDecelerations = 1,
                hardAccelerations = 0,
            )
        }
        return sorted
    }

    /** 한 번의 주행. 대부분은 평범하고, 가끔 차툰이 그리는 순간이 하나씩 섞인다. */
    private fun trip(random: Random, start: Long, end: Long, minutes: Long): Trip {
        val rainy = random.nextInt(7) == 0
        val baseTemp = 22
        var setStart = baseTemp
        var setEnd = baseTemp
        var heatStart = 0
        var heat = 0
        var ventStart = 0
        var vent = 0
        var windowsOpen = 0L
        when (random.nextInt(14)) {
            0 -> setEnd = baseTemp + 4 // 달리다 히터 온도를 올림
            1 -> { setStart = 25; setEnd = 19 } // 달리다 에어컨 온도를 내림
            2 -> heat = 2 // 달리다 엉뜨를 켬
            3 -> vent = 2 // 달리다 통풍시트를 켬
            4 -> if (!rainy) windowsOpen = minOf(minutes, 20L) * MINUTE // 창문을 내리고 달림
            5 -> { heatStart = 1; heat = 1 } // 출발 때부터 켜 둔 열선
        }
        return Trip(
            startedAtMillis = start,
            endedAtMillis = end,
            parkingGearShifts = if (random.nextInt(9) == 0) random.nextInt(3, 6) else random.nextInt(0, 2),
            rainShare = if (rainy) random.nextDouble(0.4, 1.0) else 0.0,
            distanceKm = Math.round(minutes * random.nextDouble(0.5, 0.9) * 10) / 10.0,
            hardAccelerations = if (random.nextInt(12) == 0) 1 else 0,
            hardDecelerations = if (random.nextInt(8) == 0) 1 else 0,
            parkedInReverse = random.nextInt(3) != 0,
            windowsOpenMillis = windowsOpen,
            setTempC = setEnd,
            fanAtMax = false,
            seatHeatLevel = heat,
            setTempStartC = setStart,
            seatHeatStart = heatStart,
            seatVentLevel = vent,
            seatVentStart = ventStart,
        )
    }

    /** 첫 기록이 [nowMillis] 기준 며칠 전인지(로그용). */
    fun spanDays(trips: List<Trip>, nowMillis: Long): Long = (nowMillis - (trips.minOfOrNull { it.startedAtMillis } ?: nowMillis)) / DAY_MILLIS
}
