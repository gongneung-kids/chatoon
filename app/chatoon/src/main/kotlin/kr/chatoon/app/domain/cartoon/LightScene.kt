package kr.chatoon.app.domain.cartoon

/**
 * 한 화를 볼 때 실내 조명(앰비언트 라이트)을 맞출 장면. 화 하나에 색 하나.
 *
 * 화의 시간·날씨·놀란 순간에서만 정한다(새로 넘겨짚지 않는다). 밝기는 0~100(%).
 *
 * @param color 차분한 색(ARGB, 알파는 무시).
 * @param flash 처음 잠깐 켤 색. 놀란 순간이 있는 화만 있다. [FLASH_MILLIS] 뒤 [color]로 바뀐다.
 */
data class LightScene(val color: Int, val brightness: Int, val flash: Int? = null) {

    companion object {
        const val FLASH_MILLIS = 1_500L
        const val FLASH_BRIGHTNESS = 80

        val NIGHT = LightScene(0xFF2B3A8C.toInt(), 35)
        val DUSK = LightScene(0xFFFF8A3D.toInt(), 60)
        val RAIN = LightScene(0xFF00A3A3.toInt(), 50)
        val DAY = LightScene(0xFFFFE2B8.toInt(), 70)
        const val JOLT_FLASH = 0xFFFF6A00.toInt()

        fun of(episode: Episode): LightScene {
            val base = when {
                episode.time == TimeOfDay.NIGHT -> NIGHT
                episode.rain != Rain.NONE -> RAIN
                episode.time == TimeOfDay.DUSK -> DUSK
                else -> DAY
            }
            val jolted = episode.cuts.any { it.panel == Panel.Brake || it.panel == Panel.Accel }
            return if (jolted) base.copy(flash = JOLT_FLASH) else base
        }
    }
}
