package kr.chatoon.app.ui.cartoon

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 주인이 고를 수 있는 차 색. 만화 속 차체를 이 색으로 칠한다(그늘·반사광은 이 색에서 만든다).
 * 차의 실제 색을 알려 주는 차량 신호는 없어서 설정에서 고른다.
 */
enum class CarPaint(val color: Color) {
    RED(Color(0xFFE5533A)),
    ORANGE(Color(0xFFF28C28)),
    YELLOW(Color(0xFFF5C518)),
    GREEN(Color(0xFF3FA66B)),
    SKY(Color(0xFF4DA3E8)),
    BLUE(Color(0xFF2F5FD0)),
    PURPLE(Color(0xFF7A5AD6)),
    PINK(Color(0xFFF07FA8)),
    WHITE(Color(0xFFF4F4F1)),
    SILVER(Color(0xFFB9BEC6)),
    CHARCOAL(Color(0xFF4A4E57)),
    ;

    companion object {
        val DEFAULT = RED

        /** 저장해 둔 이름으로 찾는다. 모르는 이름이면 기본 색. */
        fun of(name: String?): CarPaint = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** 지금 그릴 차 색. 화면 맨 위에서 설정값으로 채운다. */
val LocalCarPaint = compositionLocalOf { CarPaint.DEFAULT.color }
