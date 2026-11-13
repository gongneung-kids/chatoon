package kr.chatoon.app.domain.drive

/**
 * 주행 기록을 한 줄 문자열로 바꾸고 되돌린다. 파일에 한 줄씩 덧붙여 저장하기 위한 형식이다.
 *
 * 형식(탭 구분, 첫 칸은 형식 이름 `C1`):
 * `C1  시작  끝  주차기어반복  빗길비율  거리km  급가속  급감속  주차방향(R/D)  창문연시간ms  설정온도  풍량최대(1/0)
 *  열선최대  출발때설정온도  출발때열선  통풍최대  출발때통풍`
 *
 * 모르는 값은 빈 칸이다. 읽을 수 없는 줄은 null을 돌려주고 건너뛴다(파일 일부가 깨져도 나머지를 살린다).
 * 형식이 바뀌면 이름을 올리고(C2), 예전 이름도 계속 읽는다.
 */
object DriveLogCodec {

    private const val TAG = "C1"
    private const val CELLS = 17

    fun encode(trip: Trip): String = listOf(
        TAG,
        trip.startedAtMillis.toString(),
        trip.endedAtMillis.toString(),
        trip.parkingGearShifts.toString(),
        trip.rainShare.cell(),
        trip.distanceKm.cell(),
        trip.hardAccelerations.cell(),
        trip.hardDecelerations.cell(),
        when (trip.parkedInReverse) {
            true -> "R"
            false -> "D"
            null -> ""
        },
        trip.windowsOpenMillis.cell(),
        trip.setTempC.cell(),
        when (trip.fanAtMax) {
            true -> "1"
            false -> "0"
            null -> ""
        },
        trip.seatHeatLevel.cell(),
        trip.setTempStartC.cell(),
        trip.seatHeatStart.cell(),
        trip.seatVentLevel.cell(),
        trip.seatVentStart.cell(),
    ).joinToString("\t")

    fun decode(line: String): Trip? {
        val c = line.split('\t')
        if (c.size != CELLS || c[0] != TAG) return null
        return try {
            Trip(
                startedAtMillis = c[1].toLong(),
                endedAtMillis = c[2].toLong(),
                parkingGearShifts = c[3].toInt(),
                rainShare = c[4].orNull()?.toDouble()?.also { require(it in 0.0..1.0) { "ratio out of range: $it" } },
                distanceKm = c[5].orNull()?.toDouble(),
                hardAccelerations = c[6].orNull()?.toInt(),
                hardDecelerations = c[7].orNull()?.toInt(),
                parkedInReverse = when (c[8]) {
                    "" -> null
                    "R" -> true
                    "D" -> false
                    else -> throw IllegalArgumentException("unknown park direction")
                },
                windowsOpenMillis = c[9].orNull()?.toLong(),
                setTempC = c[10].orNull()?.toInt(),
                fanAtMax = when (c[11]) {
                    "" -> null
                    "1" -> true
                    "0" -> false
                    else -> throw IllegalArgumentException("bad fan flag")
                },
                seatHeatLevel = c[12].orNull()?.toInt(),
                setTempStartC = c[13].orNull()?.toInt(),
                seatHeatStart = c[14].orNull()?.toInt(),
                seatVentLevel = c[15].orNull()?.toInt(),
                seatVentStart = c[16].orNull()?.toInt(),
            )
        } catch (e: IllegalArgumentException) {
            // NumberFormatException과 Trip 검증 실패 모두 여기로 온다.
            null
        }
    }

    private fun Any?.cell(): String = this?.toString().orEmpty()

    private fun String.orNull(): String? = takeIf { it.isNotEmpty() }
}
