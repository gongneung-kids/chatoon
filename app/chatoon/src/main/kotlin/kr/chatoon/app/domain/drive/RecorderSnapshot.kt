package kr.chatoon.app.domain.drive

import kr.chatoon.app.domain.vehicle.Gear
import kr.chatoon.app.domain.vehicle.VehicleState

/**
 * [TripRecorder]의 진행 중 상태. 앱 프로세스가 주행 도중 종료돼도 다시 켜졌을 때 이어서 기록하려고 저장한다.
 *
 * 필드는 기록기 내부 상태를 그대로 옮긴 것이다. 의미는 [TripRecorder]를 본다.
 */
data class RecorderSnapshot(
    val lastState: VehicleState,
    val lastAtMillis: Long,
    val lastKnownGear: Gear?,
    val lastKnownOdometerKm: Double?,
    val tripStartedAtMillis: Long?,
    val tripStartOdometerKm: Double?,
    val knownWiperMillis: Long,
    val wiperOnMillis: Long,
    val gearChangesAtMillis: List<Long>,
    val tripHardAccelerations: Int = 0,
    val tripHardDecelerations: Int = 0,
    /** 지금 주행에서 속도 신호를 줄곧 알았는가. 하나라도 몰랐으면 횟수를 남기지 않는다. */
    val tripHarshKnown: Boolean = false,
    val cabin: TripRecorder.Cabin = TripRecorder.Cabin(),
) {
    companion object {
        // 형식이 바뀌면 올린다. 예전 형식 체크포인트는 읽지 않는다(진행 중이던 주행 하나만 잃는다).
        private const val TAG = "R1"
        private const val NULL = "-"
        private const val FIELD_COUNT = 31

        /** 탭으로 구분한 한 줄. 기어 변경 시각은 쉼표로 묶는다. */
        fun encode(s: RecorderSnapshot): String = listOf(
            TAG,
            s.lastAtMillis.toString(),
            s.lastKnownGear.orNull(),
            s.lastState.gear.orNull(),
            s.lastState.frontWiperOn.orNull(),
            s.lastState.odometerKm.orNull(),
            s.lastState.hardAccelerations.orNull(),
            s.lastState.hardDecelerations.orNull(),
            s.lastState.windowsOpen.orNull(),
            s.lastState.cabinSetTempC.orNull(),
            s.lastState.fanAtMax.orNull(),
            s.lastState.seatHeatLevel.orNull(),
            s.lastKnownOdometerKm.orNull(),
            s.tripStartedAtMillis.orNull(),
            s.tripStartOdometerKm.orNull(),
            s.knownWiperMillis.toString(),
            s.wiperOnMillis.toString(),
            s.gearChangesAtMillis.joinToString(","),
            s.tripHardAccelerations.toString(),
            s.tripHardDecelerations.toString(),
            s.tripHarshKnown.toFlag(),
            s.cabin.knownWindowMillis.toString(),
            s.cabin.windowsOpenMillis.toString(),
            s.cabin.setTempStartC.orNull(),
            s.cabin.setTempC.orNull(),
            s.cabin.fanAtMax.orNull(),
            s.cabin.seatHeatStart.orNull(),
            s.cabin.seatHeat.orNull(),
            s.cabin.seatVentStart.orNull(),
            s.cabin.seatVent.orNull(),
            "", // 끝 표시(형식이 잘린 줄을 가려낸다)
        ).joinToString("\t")

        /** 형식이 맞지 않거나 값이 앞뒤가 안 맞으면 null. 깨진 체크포인트로 이어 붙이느니 버리는 편이 안전하다. */
        fun decode(line: String): RecorderSnapshot? {
            val f = line.trimEnd('\n', '\r').split('\t')
            if (f.size != FIELD_COUNT || f[0] != TAG) return null
            return try {
                RecorderSnapshot(
                    lastAtMillis = f[1].toLong(),
                    lastKnownGear = f[2].toGearOrNull(),
                    lastState = VehicleState(
                        gear = f[3].toGearOrNull(),
                        frontWiperOn = f[4].toBoolOrNull(),
                        odometerKm = f[5].toDoubleOrNullField(),
                        hardAccelerations = f[6].toLongOrNullField(),
                        hardDecelerations = f[7].toLongOrNullField(),
                        windowsOpen = f[8].toBoolOrNull(),
                        cabinSetTempC = f[9].toFloatOrNullField(),
                        fanAtMax = f[10].toBoolOrNull(),
                        seatHeatLevel = f[11].toIntOrNullField(),
                    ),
                    lastKnownOdometerKm = f[12].toDoubleOrNullField(),
                    tripStartedAtMillis = f[13].toLongOrNullField(),
                    tripStartOdometerKm = f[14].toDoubleOrNullField(),
                    knownWiperMillis = f[15].toLong(),
                    wiperOnMillis = f[16].toLong(),
                    gearChangesAtMillis = f[17].split(',').filter { it.isNotEmpty() }.map { it.toLong() },
                    tripHardAccelerations = f[18].toInt(),
                    tripHardDecelerations = f[19].toInt(),
                    tripHarshKnown = f[20].fromFlag(),
                    cabin = TripRecorder.Cabin(
                        knownWindowMillis = f[21].toLong(),
                        windowsOpenMillis = f[22].toLong(),
                        setTempStartC = f[23].toFloatOrNullField(),
                        setTempC = f[24].toFloatOrNullField(),
                        fanAtMax = f[25].toBoolOrNull(),
                        seatHeatStart = f[26].toIntOrNullField(),
                        seatHeat = f[27].toIntOrNullField(),
                        seatVentStart = f[28].toIntOrNullField(),
                        seatVent = f[29].toIntOrNullField(),
                    ),
                ).takeIf { it.isConsistent() }
            } catch (_: IllegalArgumentException) {
                null // NumberFormatException 포함
            }
        }

        private fun RecorderSnapshot.isConsistent(): Boolean =
            tripHardAccelerations >= 0 && tripHardDecelerations >= 0 &&
                wiperOnMillis in 0..knownWiperMillis &&
                cabin.windowsOpenMillis in 0..cabin.knownWindowMillis &&
                (tripStartedAtMillis == null || tripStartedAtMillis <= lastAtMillis)

        private fun Any?.orNull(): String = when (this) {
            null -> NULL
            is Boolean -> toFlag()
            else -> toString()
        }

        private fun Boolean.toFlag() = if (this) "1" else "0"

        private fun String.fromFlag(): Boolean = when (this) {
            "1" -> true
            "0" -> false
            else -> throw IllegalArgumentException("bad flag: $this")
        }

        private fun String.toBoolOrNull(): Boolean? = if (this == NULL) null else fromFlag()

        private fun String.toLongOrNullField(): Long? = if (this == NULL) null else toLong()

        private fun String.toIntOrNullField(): Int? = if (this == NULL) null else toInt()

        private fun String.toFloatOrNullField(): Float? =
            if (this == NULL) null else toFloat().also { require(it.isFinite()) { "not finite: $this" } }

        private fun String.toDoubleOrNullField(): Double? =
            if (this == NULL) null else toDouble().also { require(it.isFinite()) { "not finite: $this" } }

        private fun String.toGearOrNull(): Gear? = if (this == NULL) null else Gear.valueOf(this)
    }
}
