package kr.chatoon.app.ui.cartoon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kr.chatoon.app.domain.cartoon.Cut
import kr.chatoon.app.domain.cartoon.Driver
import kr.chatoon.app.domain.cartoon.Fx
import kr.chatoon.app.domain.cartoon.Mood
import kr.chatoon.app.domain.cartoon.Panel
import kr.chatoon.app.domain.cartoon.Place
import kr.chatoon.app.domain.cartoon.Rain
import kr.chatoon.app.domain.cartoon.TimeOfDay
import kr.chatoon.app.domain.cartoon.splitForBalloon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 만화 컷의 먹선·종이 색과 별색 노랑. 그림은 한 장의 일러스트라 밝은·어두운 화면 테마와 상관없이 같은 색으로 그린다
 * (어두운 테마에서 먹선을 밝게 바꾸면 눈동자와 테두리가 색칠한 차 위에서 사라진다).
 */
@Immutable
data class CartoonColors(
    val ink: Color,
    val paper: Color,
    val tone: Color,
    val tone2: Color,
    val glass: Color,
    val night: Color,
    val nightInk: Color,
    val spot: Color,
    val onSpot: Color,
)

private val LightCartoon = CartoonColors(
    ink = Color(0xFF1F1B17), paper = Color(0xFFFFFFFF), tone = Color(0xFFDDDFE3), tone2 = Color(0xFF9AA0A6),
    glass = Color(0xFFCFE7F2), night = Color(0xFF141A33), nightInk = Color(0xFFF2F3F5),
    spot = Color(0xFFFFD11A), onSpot = Color(0xFF1F1B17),
)

@Composable
fun cartoonColors(): CartoonColors = LightCartoon

/**
 * 컷 한 장을 2D로 그린다(2026-11-12 사용자 결정: 3D 그림을 버리고 2D로 전면 개발).
 *
 * 하늘(시간·비) → 무대(컷의 역할: 출발 = 집 앞 골목, 달리는 장면 = 도로, 주차 = 주차장, 마지막 컷 = 도착지) → 땅 → 차 → 비 → 효과 글자
 * 순서로 쌓는다. 차는 주인이 고른 색([LocalCarPaint])으로 칠하고, 유리 너머 운전자가 보이게 그린다. 좌표는 320×240 기준이다.
 */
@Composable
fun PanelView(cut: Cut, time: TimeOfDay, rain: Rain, modifier: Modifier = Modifier, place: Place? = null, endStyle: EndStyle = EndStyle.CLOSE) {
    val colors = cartoonColors()
    val measurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.bodyLarge.fontFamily
    val paint = LocalCarPaint.current
    Canvas(
        modifier
            .aspectRatio(W / H)
            .background(colors.paper)
            .border(3.dp, colors.ink)
            .clipToBounds()
            .semantics { contentDescription = cut.caption },
    ) {
        val unit = size.width / W
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            PanelPainter(this, colors, measurer, family, CarLook.of(paint)).draw(cut.panel, time, rain, place, cut.say, endStyle)
        }
    }
}

/** 차의 생각 컷의 구도. */
enum class EndStyle { SIDE, MEDIUM, CLOSE }

/**
 * [cuts]의 [index]번째 컷이 차의 생각(End)일 때의 구도. 마지막 컷은 얼굴을 가까이, 가운데 컷은 옆모습으로 하되
 * 바로 앞 컷이 옆에서 본 그림이면 겹치지 않게 중간 크기 정면으로 한다.
 */
fun endStyleAt(cuts: List<Cut>, index: Int): EndStyle = when {
    index >= cuts.lastIndex -> EndStyle.CLOSE
    cuts.getOrNull(index - 1)?.panel.let { it is Panel.Side || it == Panel.Accel } -> EndStyle.MEDIUM
    else -> EndStyle.SIDE
}

private const val W = 320f
private const val H = 240f

/** 고른 차 색에서 만든 차체의 밝은 면·그늘·깊은 그늘. */
private class CarLook(val body: Color, val light: Color, val shade: Color, val deep: Color) {
    companion object {
        fun of(paint: Color) = CarLook(
            body = paint,
            light = lerp(paint, Color.White, 0.35f),
            shade = lerp(paint, Color.Black, 0.22f),
            deep = lerp(paint, Color.Black, 0.42f),
        )
    }
}

/** 컷의 무대. */
private enum class Backdrop { ALLEY, STREET, HOME, OFFICE, HOSPITAL, MART, CAFE, NATURE }

private fun backdropOf(place: Place?): Backdrop = when (place) {
    Place.HOME -> Backdrop.HOME
    Place.OFFICE -> Backdrop.OFFICE
    Place.HOSPITAL -> Backdrop.HOSPITAL
    Place.MART -> Backdrop.MART
    Place.CAFE -> Backdrop.CAFE
    Place.NATURE -> Backdrop.NATURE
    null -> Backdrop.STREET
}

private object Pal {
    val skin = Color(0xFFF2C9A0)
    val hair = Color(0xFF3A2A22)
    val shirt = Color(0xFF5B7FA6)
    val strangerShirt = Color(0xFF3A3A40)
    val cap = Color(0xFF26262B)
    val seat = Color(0xFF7C818A)
    val tire = Color(0xFF26231F)
    val rim = Color(0xFFBFC4CC)
    val eye = Color(0xFFFFFFFF)
    val iris = Color(0xFF2A2622)
    val amber = Color(0xFFFFB020)
    val tail = Color(0xFFD7263D)
    val plate = Color(0xFFF7F7F2)
    val shadow = Color(0x40000000)
    val glow = Color(0x40FFE58A)
    val beam = Color(0x33FFF1B0)
    val cheek = Color(0x55FF8A7A)

    val dayTop = Color(0xFF7CC4F5); val dayBottom = Color(0xFFE3F4FF)
    val rainTop = Color(0xFF7E8C9A); val rainBottom = Color(0xFFC9D1D8)
    val duskTop = Color(0xFFF2765A); val duskBottom = Color(0xFFFFD49A)
    val nightTop = Color(0xFF0D1330); val nightBottom = Color(0xFF2B3A68)

    val road = Color(0xFF5E636B); val roadWet = Color(0xFF4A5059); val roadNight = Color(0xFF262B36)
    val walk = Color(0xFFC9C3B8); val walkNight = Color(0xFF4A4D58)
    val lane = Color(0xFFF4F1E8)
    val litWindow = Color(0xFFFFD86B)
    val tree = Color(0xFF4E9A5B); val treeDark = Color(0xFF2F6B3D); val trunk = Color(0xFF7A5536)
}

/** 그림 조각을 그린다. 좌표는 모두 320×240 기준이다. */
private class PanelPainter(
    private val scope: DrawScope,
    private val c: CartoonColors,
    private val measurer: TextMeasurer,
    private val family: FontFamily?,
    private val car: CarLook,
) {
    /** 안쪽에서 크기를 바꿔 그릴 때도 선 굵기는 그대로 두려고 나눠 준다. */
    private var lineScale = 1f
    private var time = TimeOfDay.DAY
    private var wet = 0
    private val night get() = time == TimeOfDay.NIGHT
    private val line get() = if (night) c.nightInk else c.ink

    /** 말풍선 꼬리가 가리킬 차의 입 위치(그린 차가 정한다). */
    private var mouth: Offset? = null

    /**
     * [endStyle]: 차의 생각 컷(End)을 어떤 구도로 그릴지. 네 컷이 같은 정면 그림으로 겹치지 않게 자리에 따라 고른다([endStyleAt]).
     * 컷 크기는 출발(멀리, 작게) → 가운데(중간·옆모습) → 마지막(얼굴을 가까이) 순으로 달라진다.
     */
    fun draw(panel: Panel, time: TimeOfDay, rain: Rain, place: Place?, say: String?, endStyle: EndStyle = EndStyle.CLOSE) {
        this.time = time
        this.wet = rain.ordinal
        this.mouth = null
        when (panel) {
            is Panel.Stage -> {
                // 멀리서 본 출발: 차를 작게, 골목과 하늘을 넓게(만화의 첫 컷처럼 무대를 먼저 보여 준다).
                scene(Backdrop.ALLEY, sun = true)
                frontCar(panel.mood, Driver.OWNER, s = 0.46f, x = 86f, y = 112f)
                rainOver()
            }
            is Panel.Front -> {
                scene(Backdrop.STREET, sun = panel.fx == Fx.NONE)
                frontCar(panel.mood, panel.driver, s = 0.92f, x = 13f, y = 18f)
                rainOver()
                fx(panel.fx)
            }
            Panel.Wiper -> wiperCloseUp()
            is Panel.Park -> parking(panel.shifts, panel.reverse)
            is Panel.Side -> {
                scene(Backdrop.STREET, sun = panel.fx == Fx.NONE, side = true)
                sideCar(panel.mood, panel.windowsDown, panel.guests, panel.wind, accel = false)
                rainOver()
                fx(panel.fx)
            }
            is Panel.Temp -> {
                scene(Backdrop.STREET, sun = false)
                frontCar(panel.mood, Driver.OWNER, s = 0.78f, x = -12f, y = 50f)
                thermometer(panel.degrees, panel.from)
                rainOver()
            }
            is Panel.Seat -> {
                scene(Backdrop.STREET, sun = false)
                frontCar(panel.mood, Driver.OWNER, s = 0.78f, x = -12f, y = 50f)
                heatedSeat(panel.level, panel.vent)
                rainOver()
            }
            is Panel.Ask -> ask(panel)
            Panel.Brake -> {
                scene(Backdrop.STREET, sun = false)
                skidMarks()
                frontCar(Mood.SHOCK, Driver.OWNER, s = 1.0f, x = 0f, y = 14f, brake = true)
                smoke(70f, 222f); smoke(250f, 222f)
                rainOver()
                loud("끼익", 18f, 52f, 34f)
                loud("!", 286f, 62f, 50f)
            }
            Panel.Accel -> {
                scene(Backdrop.STREET, sun = false, side = true)
                speedLines()
                sideCar(Mood.SHOCK, windowsDown = false, guests = 0, wind = false, accel = true)
                rainOver()
                loud("부웅", 14f, 56f, 34f)
            }
            is Panel.End -> if (endStyle == EndStyle.SIDE) {
                // 가운데 컷의 차의 생각은 옆모습으로(정면 얼굴은 마지막 컷에 아껴 둔다).
                scene(Backdrop.STREET, sun = panel.fx == Fx.NONE, side = true)
                sideCar(panel.mood, windowsDown = false, guests = 0, wind = false, accel = false)
                rainOver()
                fx(panel.fx)
            } else if (endStyle == EndStyle.MEDIUM) {
                // 앞 컷이 이미 옆모습이면 중간 크기 정면으로.
                scene(Backdrop.STREET, sun = panel.fx == Fx.NONE)
                frontCar(panel.mood, panel.driver, s = 0.92f, x = 13f, y = 18f)
                rainOver()
                fx(panel.fx)
            } else {
                // 마지막 컷은 차 얼굴을 가까이(바퀴·거울은 칸 밖). 오른쪽 위에 간판이 있는 도착지(병원·마트·카페)는 해·달을 뺀다.
                val backdrop = backdropOf(place)
                scene(backdrop, sun = panel.fx == Fx.NONE && backdrop !in setOf(Backdrop.HOSPITAL, Backdrop.MART, Backdrop.CAFE))
                frontCar(panel.mood, panel.driver, s = 1.45f, x = -72f, y = -22f)
                rainOver()
                fx(panel.fx)
            }
        }
        // 효과 글자(끼익·부웅)가 있는 컷만 자리를 정하고, 나머지는 말하는 차 바로 위에 둔다.
        if (say != null) mouth?.let { balloon(say, it, left = when (panel) { Panel.Brake -> 132f; Panel.Accel -> 150f; else -> null }) }
    }

    // ================================================================ 무대: 하늘 → 배경 → 땅

    private fun scene(backdrop: Backdrop, sun: Boolean, side: Boolean = false) {
        sky(sun)
        when (backdrop) {
            Backdrop.ALLEY -> alley()
            Backdrop.STREET -> street(side)
            Backdrop.HOME -> apartments()
            Backdrop.OFFICE -> offices()
            Backdrop.HOSPITAL -> hospital()
            Backdrop.MART -> mart()
            Backdrop.CAFE -> cafe()
            Backdrop.NATURE -> nature()
        }
        if (side) flatRoad() else perspectiveRoad(narrow = backdrop == Backdrop.ALLEY)
    }

    private fun sky(sun: Boolean) {
        val (top, bottom) = when {
            night -> Pal.nightTop to Pal.nightBottom
            wet > 0 -> Pal.rainTop to Pal.rainBottom
            time == TimeOfDay.DUSK -> Pal.duskTop to Pal.duskBottom
            else -> Pal.dayTop to Pal.dayBottom
        }
        scope.drawRect(Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = 180f), Offset.Zero, Size(W, 182f))
        when {
            wet > 0 -> {
                cloud(40f, 34f, 1.1f, if (night) Color(0xFF3A4560) else Color(0xFFAEB7C0))
                cloud(200f, 22f, 1.3f, if (night) Color(0xFF333D57) else Color(0xFF9EA8B2))
                cloud(286f, 44f, 0.9f, if (night) Color(0xFF3A4560) else Color(0xFFAEB7C0))
            }
            night -> {
                for ((x, y, r) in STARS) circle(x, y, r, Color(0xFFF7F3D8))
                if (sun) {
                    circle(270f, 40f, 26f, Color(0x22FFF3B0))
                    circle(270f, 40f, 15f, Color(0xFFFFE8A0))
                    circle(277f, 35f, 13f, lerp(Pal.nightTop, Pal.nightBottom, 35f / 180f))
                }
            }
            time == TimeOfDay.DUSK -> {
                if (sun) {
                    circle(258f, 150f, 40f, Color(0x33FFFFFF))
                    circle(258f, 150f, 26f, Color(0xFFFFC14D))
                }
                cloud(60f, 40f, 0.9f, Color(0xFFFFB38F))
            }
            else -> {
                if (sun) {
                    circle(270f, 40f, 26f, Color(0x44FFF5B5))
                    circle(270f, 40f, 15f, c.spot, c.ink, thin = true)
                }
                cloud(52f, 40f, 1f, Color.White)
                cloud(190f, 28f, 0.75f, Color.White)
            }
        }
    }

    /** 시간에 맞춘 건물 색: 밤에는 어둡고 푸르게, 해 질 녘에는 주황빛이 돈다. */
    private fun tint(day: Color): Color = when {
        night -> lerp(day, Color(0xFF1B2342), 0.72f)
        time == TimeOfDay.DUSK -> lerp(day, Color(0xFFE88A5E), 0.22f)
        wet > 0 -> lerp(day, Color(0xFF8A96A3), 0.35f)
        else -> day
    }

    private fun windowColor(i: Int): Color = when {
        night -> if (i % 3 != 1) Pal.litWindow else Color(0xFF2A3350)
        else -> tint(Color(0xFFBFE0F2))
    }

    /** 창이 줄지어 있는 건물. */
    private fun building(x: Float, top: Float, w: Float, h: Float, color: Color, cols: Int, rows: Int, roof: Color? = null) {
        rrect(x, top, w, h, 2f, tint(color), c.ink, thin = true)
        if (roof != null) scope.drawRect(tint(roof), Offset(x, top), Size(w, 6f))
        val gx = w / (cols + 1)
        val gy = (h - 12f) / (rows + 0.5f)
        var n = 0
        for (r in 0 until rows) for (k in 0 until cols) {
            val wx = x + gx * (k + 1) - 4f
            val wy = top + 10f + gy * r
            scope.drawRect(windowColor(n++ + (x.toInt() / 7)), Offset(wx, wy), Size(8f, 9f))
        }
    }

    private fun tree(x: Float, base: Float, s: Float = 1f) {
        scope.drawRect(tint(Pal.trunk), Offset(x - 2.5f * s, base - 22f * s), Size(5f * s, 22f * s))
        circle(x, base - 30f * s, 14f * s, tint(Pal.treeDark))
        circle(x - 8f * s, base - 26f * s, 10f * s, tint(Pal.tree))
        circle(x + 7f * s, base - 34f * s, 10f * s, tint(Pal.tree))
    }

    private fun lamp(x: Float, base: Float) {
        scope.drawLine(tint(Color(0xFF5A5F68)), Offset(x, base), Offset(x, base - 70f), strokeWidth = 3f)
        scope.drawLine(tint(Color(0xFF5A5F68)), Offset(x, base - 70f), Offset(x + 12f, base - 74f), strokeWidth = 3f)
        if (night) {
            circle(x + 14f, base - 72f, 16f, Color(0x40FFE38A))
            circle(x + 14f, base - 72f, 4f, Color(0xFFFFE9A6))
        } else {
            circle(x + 14f, base - 72f, 3.5f, Color(0xFFEDEDED))
        }
    }

    /** 출발: 집 앞 골목(낮은 빌라, 전봇대와 전깃줄, 화분). */
    private fun alley() {
        scope.drawLine(tint(Color(0xFF555B66)), Offset(0f, 70f), Offset(320f, 58f), strokeWidth = 1.2f)
        scope.drawLine(tint(Color(0xFF555B66)), Offset(0f, 78f), Offset(320f, 68f), strokeWidth = 1.2f)
        // 왼쪽 빌라
        shape("M-4 178V96L30 72L64 96V178Z", tint(Color(0xFFE9D9C2)), thin = true)
        fill("M-8 98L30 68L68 98L64 101L30 75L-4 101Z", tint(Color(0xFFB4553F)))
        for ((wx, wy) in listOf(6f to 108f, 38f to 108f, 6f to 140f, 38f to 140f)) scope.drawRect(windowColor((wx + wy).toInt()), Offset(wx, wy), Size(16f, 14f))
        // 대문
        rrect(16f, 150f, 22f, 28f, 2f, tint(Color(0xFF7A5536)), c.ink, thin = true)
        circle(33f, 165f, 1.6f, c.spot)
        building(66f, 112f, 46f, 66f, Color(0xFFD9E2E8), 2, 3)
        // 오른쪽 빌라
        building(222f, 98f, 52f, 80f, Color(0xFFF1E6D2), 2, 4, roof = Color(0xFF8A6F55))
        shape("M272 178V104L300 84L328 104V178Z", tint(Color(0xFFDCCFE3)), thin = true)
        fill("M268 106L300 80L332 106L328 109L300 87L272 109Z", tint(Color(0xFF5C6B8A)))
        // 전봇대
        rrect(198f, 50f, 6f, 128f, 2f, tint(Color(0xFF8D8A84)), c.ink, thin = true)
        scope.drawLine(tint(Color(0xFF555B66)), Offset(150f, 62f), Offset(201f, 56f), strokeWidth = 1.2f)
        // 화분
        rrect(116f, 166f, 16f, 12f, 3f, tint(Color(0xFFB66A45)), c.ink, thin = true)
        circle(124f, 160f, 9f, tint(Pal.tree))
        if (night) {
            circle(201f, 60f, 18f, Color(0x33FFE38A))
            circle(201f, 60f, 4f, Color(0xFFFFE9A6))
        }
    }

    /** 달리는 장면: 먼 스카이라인, 상가 건물, 가로수와 가로등. */
    private fun street(side: Boolean) {
        val base = if (side) 176f else 178f
        fill("M0 ${base}V120H22V104H40V116H58V96H78V122H96V110H230V100H248V88H266V112H284V98H304V118H320V$base Z", tint(Color(0xFFB9D2E3)))
        building(-6f, base - 74f, 50f, 74f, Color(0xFFE8D3B8), 2, 4)
        building(46f, base - 92f, 40f, 92f, Color(0xFFC9DCE6), 2, 5)
        building(232f, base - 88f, 44f, 88f, Color(0xFFE4CFD9), 2, 5)
        building(278f, base - 70f, 48f, 70f, Color(0xFFD7E4C9), 2, 4)
        tree(100f, base, 0.9f)
        tree(220f, base, 0.9f)
        lamp(28f, base)
        lamp(284f, base)
    }

    /** 집: 아파트 단지. */
    private fun apartments() {
        building(-10f, 40f, 92f, 138f, Color(0xFFF0E7DA), 4, 8, roof = Color(0xFFB4553F))
        building(238f, 52f, 92f, 126f, Color(0xFFE6EEF2), 4, 7, roof = Color(0xFF5C6B8A))
        building(96f, 84f, 128f, 94f, Color(0xFFF4EDE2), 6, 5)
        tree(90f, 178f); tree(232f, 178f)
    }

    /** 회사: 유리로 된 높은 건물들. */
    private fun offices() {
        for ((x, w, top) in listOf(Triple(-8f, 70f, 20f), Triple(60f, 56f, 50f), Triple(210f, 60f, 36f), Triple(262f, 66f, 10f))) {
            scope.drawRect(Brush.verticalGradient(listOf(tint(Color(0xFF7FB2D8)), tint(Color(0xFF3F6E99))), startY = top, endY = 178f), Offset(x, top), Size(w, 178f - top))
            scope.drawRect(c.ink, Offset(x, top), Size(w, 178f - top), style = Stroke(1.5f / lineScale))
            var yy = top + 8f
            while (yy < 176f) {
                scope.drawLine(if (night) Color(0x88FFD86B) else Color(0x66FFFFFF), Offset(x + 3f, yy), Offset(x + w - 3f, yy), strokeWidth = 1.2f)
                yy += 9f
            }
        }
        building(116f, 96f, 88f, 82f, Color(0xFFD8DEE4), 4, 4)
        rrect(146f, 100f, 28f, 16f, 3f, tint(Color(0xFF2E3A4D)), c.ink, thin = true)
        rrect(153f, 104f, 14f, 9f, 2f, Color(0xFFF2C14E))
    }

    /** 병원: 흰 건물과 빨간 십자 간판. */
    private fun hospital() {
        building(-10f, 46f, 120f, 132f, Color(0xFFF7F7F5), 5, 7)
        building(210f, 60f, 120f, 118f, Color(0xFFEEF3F6), 5, 6)
        building(108f, 90f, 104f, 88f, Color(0xFFF7F7F5), 4, 4)
        rrect(270f, 30f, 34f, 34f, 6f, Color.White, c.ink, thin = true)
        fill("M282 35H292V42H299V52H292V59H282V52H275V42H282Z", Color(0xFFE23B3B))
    }

    /** 마트: 넓은 매장, 줄무늬 차양, 장바구니 간판. */
    private fun mart() {
        building(-10f, 70f, 340f, 108f, Color(0xFFF3EFE6), 10, 3)
        var x = -10f
        var i = 0
        while (x < 330f) {
            scope.drawRect(if (i % 2 == 0) tint(Color(0xFFE5533A)) else tint(Color.White), Offset(x, 70f), Size(20f, 16f))
            x += 20f; i++
        }
        scope.drawRect(c.ink, Offset(-10f, 70f), Size(340f, 16f), style = Stroke(1.5f / lineScale))
        for (sx in listOf(244f)) {
            rrect(sx, 26f, 60f, 34f, 6f, tint(Color(0xFF2F7D4E)), c.ink, thin = true)
            stroke("M${sx + 12} 36H${sx + 18}L${sx + 22} 50H${sx + 46}L${sx + 50} 40H${sx + 20}", Color.White, thin = true)
            circle(sx + 26f, 54f, 2.5f, Color.White); circle(sx + 42f, 54f, 2.5f, Color.White)
        }
    }

    /** 카페: 작은 가게, 차양, 커피잔 간판. */
    private fun cafe() {
        building(-10f, 80f, 120f, 98f, Color(0xFFEAD8C0), 3, 3, roof = Color(0xFF6B4A32))
        building(210f, 80f, 120f, 98f, Color(0xFFE2D3C6), 3, 3, roof = Color(0xFF6B4A32))
        building(110f, 96f, 100f, 82f, Color(0xFFF2E6D4), 3, 2)
        fill("M-14 80L-4 64H118L128 80Z", tint(Color(0xFF3F7D6B)))
        fill("M206 80L216 64H330L340 80Z", tint(Color(0xFF3F7D6B)))
        for (cx in listOf(280f)) {
            circle(cx, 36f, 18f, Color.White, c.ink, thin = true)
            rrect(cx - 9f, 30f, 14f, 13f, 3f, tint(Color(0xFF7A4B2E)))
            stroke("M${cx + 5} 33q5 0 5 4t-5 4", tint(Color(0xFF7A4B2E)), thin = true)
        }
    }

    /** 자연: 산과 나무(놀러 간 곳). */
    private fun nature() {
        fill("M-10 178L60 92L120 150L180 80L250 140L300 100L340 178Z", tint(Color(0xFF6E9E7A)))
        fill("M-10 178L40 130L90 170L150 120L210 168L270 128L340 178Z", tint(Color(0xFF4F8A5E)))
        fill("M180 80L196 100L188 98L180 108L172 98L164 100Z", tint(Color.White))
        for (x in listOf(16f, 52f, 252f, 296f)) tree(x, 178f, 1.1f)
    }

    /** 앞에서 본 도로: 멀어질수록 좁아지는 길, 양쪽 인도, 가운데 점선. 젖으면 반사가 비친다. */
    private fun perspectiveRoad(narrow: Boolean) {
        val road = when { night -> Pal.roadNight; wet > 0 -> Pal.roadWet; else -> Pal.road }
        val walk = if (night) Pal.walkNight else tint(Pal.walk)
        scope.drawRect(walk, Offset(0f, 176f), Size(W, H - 176f))
        val farL = if (narrow) 120f else 104f
        val farR = if (narrow) 200f else 216f
        fill("M$farL 176L$farR 176L${if (narrow) 300f else 340f} 240L${if (narrow) 20f else -20f} 240Z", road)
        stroke("M0 176H320", c.ink, thin = true)
        if (!narrow) {
            for (i in 0 until 4) {
                val t0 = 0.08f + i * 0.26f
                val t1 = t0 + 0.14f
                val y0 = 176f + t0 * 64f
                val y1 = 176f + t1 * 64f
                val w0 = 1.2f + t0 * 5f
                val w1 = 1.2f + t1 * 5f
                fill("M${160 - w0} ${y0}L${160 + w0} ${y0}L${160 + w1} ${y1}L${160 - w1} ${y1}Z", Pal.lane)
            }
        } else {
            oval(160f, 230f, 9f, 4f, lerp(road, Color.White, 0.12f))
        }
        if (wet > 0) {
            oval(60f, 222f, 24f, 4f, Color(0x55CFE0EE)); oval(250f, 214f, 18f, 3f, Color(0x55CFE0EE)); oval(130f, 236f, 14f, 3f, Color(0x44CFE0EE))
        }
    }

    /** 옆에서 본 도로: 인도, 연석, 차선. */
    private fun flatRoad() {
        val road = when { night -> Pal.roadNight; wet > 0 -> Pal.roadWet; else -> Pal.road }
        scope.drawRect(if (night) Pal.walkNight else tint(Pal.walk), Offset(0f, 176f), Size(W, 12f))
        scope.drawRect(road, Offset(0f, 188f), Size(W, H - 188f))
        stroke("M0 188H320", c.ink, thin = true)
        scope.drawLine(Pal.lane, Offset(-10f, 228f), Offset(330f, 228f), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 18f)))
        if (wet > 0) { oval(70f, 222f, 22f, 3f, Color(0x55CFE0EE)); oval(250f, 234f, 18f, 3f, Color(0x55CFE0EE)) }
    }

    private fun cloud(x: Float, y: Float, s: Float, color: Color) {
        circle(x, y, 12f * s, color)
        circle(x - 14f * s, y + 5f * s, 9f * s, color)
        circle(x + 14f * s, y + 4f * s, 10f * s, color)
        rrect(x - 22f * s, y + 2f * s, 46f * s, 12f * s, 6f * s, color)
    }

    // ================================================================ 앞에서 본 차(전조등이 눈, 그릴 위가 입)

    private fun frontCar(mood: Mood, driver: Driver, s: Float, x: Float, y: Float, brake: Boolean = false) = scaled(s, x, y) {
        val dip = if (brake) 7f else 0f
        // 말풍선 꼬리는 차의 지붕선(윤곽)에 닿는다.
        mouth = Offset(x + 150f * s, y + (57f + dip) * s)
        if (night) {
            // 전조등 불빛이 앞(보는 쪽)의 도로를 비춘다
            fill("M86 ${154 + dip}L20 260H170L124 ${154 + dip}Z", Pal.beam)
            fill("M196 ${154 + dip}L150 260H300L234 ${154 + dip}Z", Pal.beam)
        }
        oval(160f, 208f, 112f, 9f, Pal.shadow)
        rrect(70f, 172f, 36f, 36f, 9f, Pal.tire, c.ink, thin = true)
        rrect(214f, 172f, 36f, 36f, 9f, Pal.tire, c.ink, thin = true)
        shifted(0f, dip) {
            // 거울
            stroke("M54 118H60M260 118H266", c.ink)
            rrect(30f, 110f, 26f, 16f, 6f, car.body, c.ink)
            rrect(264f, 110f, 26f, 16f, 6f, car.body, c.ink)
            rrect(34f, 113f, 18f, 8f, 3f, Color(0xFFBFDDEB))
            rrect(268f, 113f, 18f, 8f, 3f, Color(0xFFBFDDEB))
            // 지붕과 앞유리
            shape("M90 112Q94 60 128 55H192Q226 60 230 112Z", car.body)
            stroke("M112 66Q128 59 152 59", car.light, thin = true)
            val glass = if (night) listOf(Color(0xFF34466A), Color(0xFF1E2A44)) else listOf(Color(0xFFD8EEF8), Color(0xFF94BCD3))
            fillBrush("M104 108Q108 70 132 66H188Q212 70 216 108Z", Brush.verticalGradient(glass, startY = 66f, endY = 108f))
            rrect(122f, 86f, 24f, 22f, 7f, Pal.seat)
            driverInside(driver, lean = brake)
            stroke("M120 102L134 76M130 104L140 86", Color(0x88FFFFFF), thin = true)
            outline("M104 108Q108 70 132 66H188Q212 70 216 108Z", thin = true)
            // 몸통
            rrect(52f, 104f, 216f, 88f, 30f, car.body, c.ink)
            fill("M56 160H264V164Q264 188 238 188H82Q56 188 56 164Z", car.shade)
            stroke("M80 112H140", car.light, thin = true)
            stroke("M110 122Q160 116 210 122", car.shade, thin = true)
            rrect(52f, 104f, 216f, 88f, 30f, Color.Transparent, c.ink)
            // 깜빡이
            rrect(58f, 150f, 12f, 8f, 3f, Pal.amber, c.ink, thin = true)
            rrect(250f, 150f, 12f, 8f, 3f, Pal.amber, c.ink, thin = true)
            // 눈(전조등)
            if (night && mood != Mood.HAPPY && mood != Mood.SLEEPY) { circle(104f, 140f, 25f, Pal.glow); circle(216f, 140f, 25f, Pal.glow) }
            eyes(mood)
            mouth(mood)
            if (mood == Mood.HAPPY || mood == Mood.PROUD) { oval(74f, 168f, 10f, 5f, Pal.cheek); oval(246f, 168f, 10f, 5f, Pal.cheek) }
            // 범퍼와 번호판
            rrect(60f, 182f, 200f, 14f, 7f, car.deep, c.ink, thin = true)
            rrect(138f, 178f, 44f, 14f, 2f, Pal.plate, c.ink, thin = true)
            stroke("M146 185H174", Color(0xFF9AA0A6), thin = true)
        }
    }

    /** 운전자(한국 차라 앞에서 보면 오른쪽 자리). 머리·어깨·핸들 잡은 두 손이 유리 너머로 보인다. */
    private fun driverInside(driver: Driver, lean: Boolean) {
        val dy = if (lean) 4f else 0f
        val shirt = if (driver == Driver.STRANGER) Pal.strangerShirt else Pal.shirt
        fill("M166 108Q168 ${96 + dy} 186 ${96 + dy}Q204 ${96 + dy} 206 108Z", shirt)
        circle(186f, 84f + dy, 10.5f, Pal.skin, c.ink, thin = true)
        if (driver == Driver.STRANGER) {
            fill("M174 ${82 + dy}Q176 ${70 + dy} 187 ${70 + dy}Q198 ${70 + dy} 199 ${82 + dy}Z", Pal.cap)
            stroke("M172 ${82 + dy}H204", Pal.cap)
        } else {
            fill("M175.5 ${83 + dy}Q176 ${72 + dy} 186 ${72 + dy}Q196.5 ${72 + dy} 196.5 ${83 + dy}Q192 ${77 + dy} 186 ${78 + dy}Q180 ${77 + dy} 175.5 ${83 + dy}Z", Pal.hair)
        }
        circle(182f, 86f + dy, 1.3f, c.ink); circle(190f, 86f + dy, 1.3f, c.ink)
        if (lean) oval(186f, 91f + dy, 2.2f, 2.6f, c.ink) else stroke("M183 ${90 + dy}Q186 ${92 + dy} 189 ${90 + dy}", c.ink, thin = true)
        // 핸들과 손
        oval(186f, 104f, 14f, 4.5f, Color.Transparent, c.ink, thin = true)
        circle(173f, 103f, 3.2f, Pal.skin, c.ink, thin = true)
        circle(199f, 103f, 3.2f, Pal.skin, c.ink, thin = true)
    }

    private fun eyes(mood: Mood) {
        val left = 104f
        val right = 216f
        val y = 140f
        val big = mood == Mood.SHOCK
        for (cx in listOf(left, right)) oval(cx, y, if (big) 22f else 20f, if (big) 21f else 18f, Pal.eye, c.ink)
        fun pupil(dx: Float, dy: Float, r: Float = 7.5f) {
            // 두 눈은 늘 같은 쪽을 본다(같은 dx, dy).
            for (cx in listOf(left, right)) {
                circle(cx + dx, y + dy, r, Pal.iris)
                if (r >= 5f) circle(cx + dx + r * 0.35f, y + dy - r * 0.4f, r * 0.33f, Pal.eye)
            }
        }
        fun lid(slant: Float = 0f) {
            // 눈꺼풀: 위쪽을 차체 그늘색으로 덮는다. 두 눈이 좌우 대칭이 되게 slant를 반대로 준다.
            shape("M${left - 20} ${y - 1 + slant}Q$left ${y - 24} ${left + 20} ${y - 1 - slant}Z", car.shade, thin = true)
            shape("M${right - 20} ${y - 1 - slant}Q$right ${y - 24} ${right + 20} ${y - 1 + slant}Z", car.shade, thin = true)
        }
        fun brows(inner: Float) {
            stroke("M${left - 16} ${y - 26}L${left + 12} ${y - 26 + inner}", c.ink)
            stroke("M${right + 16} ${y - 26}L${right - 12} ${y - 26 + inner}", c.ink)
        }
        when (mood) {
            Mood.HAPPY -> for (cx in listOf(left, right)) {
                oval(cx, y, 21f, 19f, car.body)
                stroke("M${cx - 11} ${y + 4}Q$cx ${y - 10} ${cx + 11} ${y + 4}", c.ink)
            }
            Mood.SLEEPY -> for (cx in listOf(left, right)) {
                oval(cx, y, 21f, 19f, car.body)
                stroke("M${cx - 11} ${y - 1}Q$cx ${y + 8} ${cx + 11} ${y - 1}", c.ink)
            }
            Mood.PROUD -> { pupil(0f, 4f); lid() }
            Mood.SULK -> { pupil(-5f, 5f); lid(slant = 4f) }
            Mood.WARY -> { pupil(6f, 3f, 6.5f); lid(slant = -3f); brows(-5f) }
            Mood.SHOCK -> { pupil(0f, 0f, 4.5f); brows(-6f) }
            Mood.SWEAT -> { pupil(0f, 2f); brows(5f) }
            Mood.CALM -> pupil(0f, 1f)
        }
    }

    private fun mouth(mood: Mood) {
        when (mood) {
            Mood.HAPPY -> fill("M140 160Q160 180 180 160Z", Pal.iris)
            Mood.PROUD -> stroke("M144 164Q162 172 178 158", c.ink)
            Mood.SULK -> stroke("M146 170Q160 160 174 170", c.ink)
            Mood.WARY -> stroke("M147 166H173", c.ink)
            Mood.SHOCK -> oval(160f, 166f, 8f, 9f, Pal.iris, c.ink)
            Mood.SLEEPY -> circle(160f, 166f, 4f, Pal.iris, c.ink, thin = true)
            Mood.SWEAT -> stroke("M145 166q5 -5 10 0t10 0t10 0", c.ink)
            Mood.CALM -> stroke("M149 163Q160 171 171 163", c.ink)
        }
    }

    // ================================================================ 옆에서 본 차(오른쪽으로 달린다)

    /**
     * 옆에서 본 차(오른쪽으로 달린다). 정면 차와 비율을 맞춘 짧은 해치백: 뒤 꽁무니가 짧고 실내가 가운데.
     * A필러 아래 사이드미러, 앞 창에 운전자, 뒤 창에 손님(머리카락·얼굴·어깨). 세게 밟으면 앞이 들리고 운전자가 등받이로 밀린다.
     */
    private fun sideCar(mood: Mood, windowsDown: Boolean, guests: Int, wind: Boolean, accel: Boolean) {
        if (wind) stroke("M10 118H40M4 140H34M14 162H38", line)
        if (accel) { smoke(70f, 202f); smoke(48f, 194f) }
        oval(176f, 210f, 104f, 8f, Pal.shadow)
        rotated(if (accel) -5f else 0f, 112f, 207f) {
            mouth = if (accel) Offset(204f, 92f) else Offset(196f, 100f)
            if (night) fill("M276 150L330 134V184L276 166Z", Pal.beam)
            val body = "M80 178Q76 152 92 144L116 139Q130 104 160 100H204Q226 102 244 132L262 139Q278 145 278 162V178Q278 188 268 188H90Q80 188 80 178Z"
            shape(body, car.body)
            fill("M83 168H275V178Q275 185 268 185H90Q83 185 83 178Z", car.shade)
            stroke("M98 146L118 141", car.light, thin = true)
            stroke("M150 104H200", car.light, thin = true)
            // 창
            val glass = if (night) Color(0xFF2A3A5A) else Color(0xFFBFDDEB)
            val window = if (windowsDown) car.deep else glass
            val rearWindow = "M126 136Q136 110 160 106H170V136Z"
            val frontWindow = "M178 106H202Q220 108 234 136H178Z"
            shape(rearWindow, window, thin = true)
            shape(frontWindow, window, thin = true)
            // 뒷좌석 손님
            if (guests >= 1) person(156f, 126f, 0.82f, Color(0xFF8E6BBF), Color(0xFF6B4A32))
            if (guests >= 2) person(140f, 128f, 0.74f, Color(0xFF5FA37A), Color(0xFF2E2620))
            if (guests >= 3) person(166f, 130f, 0.62f, Color(0xFFE59A3A), Color(0xFFB9853F))
            // 운전자(세게 밟으면 등받이로 밀린다)
            val lean = if (accel) -3f else 0f
            person(204f + lean, 124f, 1f, Pal.shirt, Pal.hair)
            if (windowsDown) stroke("M196 116q-9 -4 -16 0M195 121q-10 -1 -17 3", Pal.hair, thin = true)
            stroke("M220 128L228 121", c.ink, thin = true)
            // 창틀 위에 그어 손님·운전자가 창 안에 있게
            outline(rearWindow, thin = true); outline(frontWindow, thin = true)
            scope.drawRect(car.body, Offset(170.5f, 107f), Size(7f, 29f))
            stroke("M170 107V136M178 107V136", c.ink, thin = true)
            // 문과 손잡이
            stroke("M174 138V184", c.ink, thin = true)
            stroke("M184 148H196M140 148H152", c.ink, thin = true)
            // 사이드미러(A필러 아래)
            shape("M236 134L247 128Q254 127 254 133L252 139H238Z", car.body, thin = true)
            fill("M241 133L249 130Q251 130 251 133L250 136H242Z", Color(0xFFBFDDEB))
            // 꼬리등, 깜빡이
            rrect(80f, 148f, 8f, 14f, 3f, Pal.tail, c.ink, thin = true)
            rrect(262f, 162f, 10f, 6f, 2f, Pal.amber, c.ink, thin = true)
            // 바퀴
            for (wx in listOf(112f, 238f)) {
                circle(wx, 187f, 20f, Pal.tire, c.ink, thin = true)
                circle(wx, 187f, 10f, Pal.rim, c.ink, thin = true)
                for (k in 0 until 5) {
                    val a = k * 72.0 * PI / 180.0
                    scope.drawLine(Color(0xFF8C929B), Offset(wx, 187f), Offset(wx + (8 * cos(a)).toFloat(), 187f + (8 * sin(a)).toFloat()), strokeWidth = 1.6f)
                }
                circle(wx, 187f, 3f, Color(0xFF7D838C))
                if (accel) stroke("M${wx - 14} 177A17 17 0 0 0 ${wx - 14} 197", Color(0x99FFFFFF), thin = true)
            }
            // 눈(앞 전조등)과 입
            if (night) circle(266f, 150f, 16f, Pal.glow)
            oval(264f, 150f, 9f, 9f, Pal.eye, c.ink, thin = true)
            when (mood) {
                Mood.HAPPY -> { stroke("M259 153q5 -8 10 0", c.ink, thin = true); stroke("M265 173q6 4 10 -3", c.ink, thin = true) }
                Mood.SHOCK -> { circle(266f, 150f, 2.6f, Pal.iris); oval(272f, 173f, 3.2f, 3.8f, Pal.iris, c.ink, thin = true) }
                else -> { circle(266f, 151f, 3.8f, Pal.iris); circle(267.3f, 149.6f, 1.2f, Pal.eye); stroke("M268 173h8", c.ink, thin = true) }
            }
        }
    }

    /** 옆에서 본 사람(오른쪽을 본다): 어깨, 귀, 얼굴, 눈, 코, 머리카락. */
    private fun person(cx: Float, cy: Float, k: Float, shirt: Color, hair: Color) {
        fill("M${cx - 12 * k} ${cy + 12 * k}Q${cx - 11 * k} ${cy + 2 * k} $cx ${cy + 1 * k}Q${cx + 10 * k} ${cy + 2 * k} ${cx + 11 * k} ${cy + 12 * k}Z", shirt)
        circle(cx, cy - 9f * k, 7.5f * k, Pal.skin, c.ink, thin = true)
        fill("M${cx - 7.5f * k} ${cy - 9 * k}Q${cx - 7 * k} ${cy - 18 * k} ${cx + 1 * k} ${cy - 17.5f * k}Q${cx + 7 * k} ${cy - 17 * k} ${cx + 6.5f * k} ${cy - 12 * k}Q${cx + 1 * k} ${cy - 14 * k} ${cx - 3 * k} ${cy - 9 * k}Z", hair)
        circle(cx - 3f * k, cy - 8f * k, 1.6f * k, lerp(Pal.skin, Color.Black, 0.2f))
        circle(cx + 3.5f * k, cy - 10f * k, 1.1f * k, c.ink)
        stroke("M${cx + 7 * k} ${cy - 9 * k}l1.6 2.2l-1.8 0.4", c.ink, thin = true)
    }

    private fun speedLines() {
        for ((y, len) in listOf(108f to 40f, 128f to 26f, 150f to 34f, 170f to 22f)) {
            scope.drawLine(Color.White, Offset(4f, y), Offset(4f + len, y), strokeWidth = 5f, cap = StrokeCap.Round)
            scope.drawLine(c.ink, Offset(4f, y), Offset(4f + len, y), strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
    }

    // ================================================================ 급정거 연출

    private fun skidMarks() {
        val mark = Color(0xAA1A1A1A)
        fill("M78 214L72 240H96L94 214Z", mark)
        fill("M226 214L224 240H248L242 214Z", mark)
    }

    private fun smoke(x: Float, y: Float) {
        val puff = if (night) Color(0x88B8BEC8) else Color(0xCCEFEFEF)
        circle(x, y, 9f, puff); circle(x - 9f, y - 5f, 7f, puff); circle(x + 8f, y - 6f, 6f, puff)
    }

    // ================================================================ 와이퍼를 가까이서

    private fun wiperCloseUp() {
        sky(sun = false)
        val glass = if (night) listOf(Color(0xCC34466A), Color(0xCC1E2A44)) else listOf(Color(0xCCD8EEF8), Color(0xCC94BCD3))
        scope.drawRect(Brush.verticalGradient(glass, startY = 0f, endY = 200f), Offset.Zero, Size(W, 200f))
        // 운전자(유리 너머, 가까이)
        fill("M196 200Q200 150 236 148Q272 150 276 200Z", Pal.shirt)
        circle(236f, 118f, 26f, Pal.skin, c.ink, thin = true)
        fill("M210 118Q212 90 236 90Q262 90 262 118Q252 104 236 106Q220 104 210 118Z", Pal.hair)
        circle(227f, 122f, 2.5f, c.ink); circle(245f, 122f, 2.5f, c.ink)
        stroke("M230 134Q236 138 242 134", c.ink, thin = true)
        oval(236f, 178f, 40f, 12f, Color.Transparent, c.ink)
        if (wet > 0) for ((x, y, r) in DROPS) circle(x, y, r, Color(0x66FFFFFF), Color(0x55FFFFFF), thin = true)
        // 와이퍼가 닦고 지나간 자리와 날
        fill("M40 200A120 120 0 0 1 150 92L156 100A110 110 0 0 0 52 200Z", Color(0x33FFFFFF))
        stroke("M48 200L150 96", c.ink)
        stroke("M200 200L292 104", c.ink)
        rrect(-10f, 196f, 340f, 60f, 10f, Color(0xFF2B2A30), c.ink)
        loud("쓱", 30f, 44f, 30f)
        loud("쓱", 262f, 40f, 26f)
        rainOver()
    }

    // ================================================================ 공조 온도

    private fun thermometer(degrees: Int, from: Int? = null) {
        // 달리다 바꾼 온도면 올렸을 때 열기, 내렸을 때 눈꽃(설정이 몇 도인지보다 어느 쪽으로 바꿨는지가 이야기다).
        val cold = if (from != null) degrees < from else degrees <= 18
        val fluid = if (cold) Color(0xFF3D8BE0) else Color(0xFFE5533A)
        rrect(248f, 56f, 18f, 92f, 9f, Color.White, c.ink)
        circle(257f, 154f, 15f, fluid, c.ink)
        val level = ((degrees - 14).coerceIn(0, 16) / 16f) * 80f
        scope.drawRect(fluid, Offset(252f, 146f - level), Size(10f, level + 4f))
        if (from != null) loud("$from°→$degrees°", 176f, 46f, 30f) else loud("$degrees°", 228f, 46f, 34f)
        if (cold) {
            flake(222f, 110f, 9f); flake(292f, 128f, 8f); flake(286f, 82f, 7f)
        } else {
            stroke("M284 160q8 -10 0 -20t0 -20M298 160q8 -10 0 -20t0 -20", Color(0xFFE5533A))
        }
    }

    /** 옆에서 본 운전석 시트(왼쪽이 앞). 방석에 열선이 비치고, 단계 수만큼 열기가 올라온다. */
    private fun heatedSeat(level: Int, vent: Boolean = false) {
        val seat = Color(0xFF3A3F4A)
        // 통풍시트는 같은 시트에 파란 바람, 열선은 빨간 열기.
        val hot = if (vent) Color(0xFF3D8BE0) else Color(0xFFE5533A)
        rrect(256f, 158f, 12f, 16f, 2f, Color(0xFF6B6F78), c.ink, thin = true)
        shape("M274 144L288 72Q290 62 300 63L305 64Q312 67 310 78L299 146Z", seat)
        rrect(291f, 44f, 22f, 18f, 7f, seat, c.ink)
        shape("M222 144Q222 134 232 134H298Q306 134 306 144V152Q306 160 298 160H230Q222 160 222 152Z", seat)
        stroke("M232 147H294M290 140L301 86", hot, thin = true)
        val shown = level.coerceIn(1, 3)
        for (i in 0 until shown) {
            val x = 240f + i * 20f
            if (vent) {
                // 시트 위로 솟는 바람(물결 대신 위로 향한 짧은 화살표)
                stroke("M$x 128V100M${x - 6} 108L$x 99L${x + 6} 108", hot)
            } else {
                stroke("M$x 126q7 -8 0 -16t0 -16", hot)
            }
        }
        if (vent) {
            flake(222f, 92f, 7f)
            loud("통풍 ${level}단", 186f, 46f, 30f)
        } else {
            loud("${level}단", 220f, 46f, 34f)
        }
    }

    private fun flake(cx: Float, cy: Float, r: Float) {
        for (degrees in listOf(0.0, 60.0, 120.0)) {
            val rad = degrees * PI / 180.0
            val dx = (r * cos(rad)).toFloat()
            val dy = (r * sin(rad)).toFloat()
            scope.drawLine(Color.White, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = 4f, cap = StrokeCap.Round)
            scope.drawLine(Color(0xFF3D8BE0), Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }

    // ================================================================ 위에서 본 주차장

    /** [reverse]: 후면 주차면 차 앞(전조등)이 통로(아래) 쪽, 전면 주차·모름이면 벽(위) 쪽. 화살표 수가 기어를 바꾼 횟수다. */
    private fun parking(shifts: Int, reverse: Boolean?) {
        scope.drawRect(if (night) Color(0xFF353A45) else Color(0xFF6E737B), Offset.Zero, Size(W, H))
        // 벽과 화단
        scope.drawRect(if (night) Color(0xFF2A3B30) else Color(0xFF6FA36F), Offset.Zero, Size(W, 22f))
        for (x in listOf(20f, 70f, 120f, 170f, 220f, 270f)) circle(x, 12f, 9f, if (night) Color(0xFF34503E) else Pal.tree)
        scope.drawRect(if (night) Color(0xFF5A5E68) else Color(0xFFD8D4CC), Offset(0f, 22f), Size(W, 8f))
        stroke("M0 30H320", c.ink, thin = true)
        rrect(284f, 36f, 22f, 20f, 3f, c.spot, c.ink, thin = true)
        text("P", 295f, 52f, 15f, c.onSpot, center = true)
        for (x in listOf(64f, 128f, 192f, 256f)) scope.drawLine(Color(0xFFF4F1E8), Offset(x, 34f), Offset(x, 150f), strokeWidth = 4f, cap = StrokeCap.Round)
        for (x in listOf(78f, 142f, 206f)) rrect(x, 38f, 36f, 6f, 2f, Color(0xFFE8B44A))
        if (night) circle(160f, 90f, 70f, Color(0x22FFE38A))
        topCar(96f, 96f, Color(0xFF8EA6BF), frontUp = true)
        topCar(224f, 96f, Color(0xFFE7E3DA), frontUp = false)
        val tilt = if (shifts >= 6) -8f else if (shifts >= 4) -4f else 0f
        rotated(tilt, 160f, 96f) { topCar(160f, 96f, car.body, frontUp = reverse != true, ours = true) }
        mouth = Offset(176f, 58f)
        // 통로의 기어 화살표(R 위로, D 아래로)
        val shown = shifts.coerceIn(1, 9)
        for (i in 0 until shown) {
            val x = 160f + (i - (shown - 1) / 2f) * 26f
            val up = i % 2 == 0
            if (up) stroke("M$x 210V174M${x - 6} 183L$x 173L${x + 6} 183", Color.White) else stroke("M$x 174V210M${x - 6} 201L$x 211L${x + 6} 201", Color.White)
            text(if (up) "R" else "D", x, 233f, 17f, Color.White, center = true)
        }
    }

    /** 위에서 본 차. [frontUp]이면 앞(전조등)이 위쪽이다. */
    private fun topCar(cx: Float, cy: Float, color: Color, frontUp: Boolean, ours: Boolean = false) {
        val top = cy - 46f
        oval(cx + 3f, cy + 4f, 26f, 48f, Pal.shadow)
        val front = if (frontUp) top else top + 92f
        val dirM = if (frontUp) 1f else -1f
        // 사이드미러(앞유리 옆 양쪽)
        rrect(cx - 30f, front + dirM * 26f - 3f, 9f, 6f, 2f, color, c.ink, thin = true)
        rrect(cx + 21f, front + dirM * 26f - 3f, 9f, 6f, 2f, color, c.ink, thin = true)
        rrect(cx - 23f, top, 46f, 92f, 15f, color, c.ink)
        val rear = if (frontUp) top + 92f else top
        val dir = if (frontUp) 1f else -1f
        // 앞유리(넓게), 뒷유리(좁게), 지붕
        fill("M${cx - 17} ${front + dir * 20}Q$cx ${front + dir * 14} ${cx + 17} ${front + dir * 20}L${cx + 15} ${front + dir * 32}H${cx - 15}Z", Color(0xFFBFDDEB))
        fill("M${cx - 15} ${rear - dir * 18}H${cx + 15}L${cx + 16} ${rear - dir * 10}Q$cx ${rear - dir * 7} ${cx - 16} ${rear - dir * 10}Z", Color(0xFF9EC3D6))
        rrect(cx - 16f, cy - 13f, 32f, 28f, 8f, lerp(color, Color.White, 0.18f))
        // 전조등(앞), 꼬리등(뒤)
        circle(cx - 14f, front + dir * 5f, 3.6f, Color(0xFFFFF3B0), c.ink, thin = true)
        circle(cx + 14f, front + dir * 5f, 3.6f, Color(0xFFFFF3B0), c.ink, thin = true)
        rrect(cx - 19f, rear - dir * 5f - 2f, 8f, 4f, 1f, Pal.tail)
        rrect(cx + 11f, rear - dir * 5f - 2f, 8f, 4f, 1f, Pal.tail)
        if (night && ours) fill("M${cx - 18} ${front}L${cx - 30} ${front - dir * 40}H${cx + 30}L${cx + 18} ${front}Z", Pal.beam)
    }

    // ================================================================ 묻고 답하기(지금 만화에서는 쓰지 않는다)

    private fun ask(panel: Panel.Ask) {
        scene(Backdrop.STREET, sun = false)
        frontCar(Mood.CALM, Driver.OWNER, s = 0.62f, x = -8f, y = 86f)
        val question = panel.question
        val bottom = if (question.size > 1) 58 else 44
        shape("M22 12H246a12 12 0 0 1 12 12V${bottom}a12 12 0 0 1-12 12H92L74 98L72 ${bottom + 12}H22a12 12 0 0 1-12-12V24a12 12 0 0 1 12-12Z", c.paper)
        question.forEachIndexed { i, q -> text(q, 134f, 38f + i * 22f, 17f, c.ink, center = true) }
        shape("M176 78H296a12 12 0 0 1 12 12V126a12 12 0 0 1-12 12H176a12 12 0 0 1-12-12V122L136 132L164 106V90a12 12 0 0 1 12-12Z", c.spot)
        val lines = splitForBalloon(panel.answer)
        lines.forEachIndexed { i, l -> text(l, 236f, if (lines.size > 1) 104f + i * 22f else 114f, if (lines.size > 1) 15f else 20f, c.onSpot, center = true) }
    }

    // ================================================================ 비와 효과

    private fun rainOver() {
        if (wet == 0) return
        val color = if (night) Color(0xAACFE0FF) else Color(0xCCFFFFFF)
        val len = if (wet == 2) 18f else 13f
        val n = if (wet == 2) RAIN.size else RAIN.size / 2
        for (i in 0 until n) {
            val (x, y) = RAIN[i]
            scope.drawLine(Color(0x55000000), Offset(x + 0.8f, y + 0.8f), Offset(x - 4f + 0.8f, y + len + 0.8f), strokeWidth = 2f, cap = StrokeCap.Round)
            scope.drawLine(color, Offset(x, y), Offset(x - 4f, y + len), strokeWidth = 2f, cap = StrokeCap.Round)
        }
        if (wet == 2) for ((x, y) in SPLASH) stroke("M${x - 5} ${y}q2 -5 5 -5t5 5", color, thin = true)
    }

    private fun fx(fx: Fx) {
        when (fx) {
            Fx.NONE -> Unit
            Fx.QUESTION -> loud("?", 276f, 66f, 50f)
            Fx.EXCLAIM -> loud("!", 282f, 66f, 50f)
            Fx.ZZZ -> { loud("Z", 252f, 60f, 28f); loud("z", 280f, 38f, 20f) }
            Fx.SWEAT -> shape("M286 40q10 15 0 22q-10 -7 0 -22Z", Color(0xFFBFE4FF), thin = true)
            Fx.SHIVER -> { loud("덜", 26f, 50f, 24f); loud("덜", 272f, 54f, 24f) }
            Fx.NOTE -> {
                stroke("M262 70V46l16 -4v24", line)
                circle(257f, 71f, 5.5f, line)
                circle(273f, 66f, 5.5f, line)
            }
        }
    }

    /**
     * 차의 말풍선: 위쪽 [left]에서 시작하는 둥근 풍선과, 차의 입([target]) 쪽으로 뻗는 꼬리를 한 도형으로 합쳐 먹선을 두른다.
     * 꼬리는 입까지 다 가지 않고 가는 길의 절반쯤에서 끝난다(만화의 말꼬리).
     */
    private fun balloon(say: String, target: Offset, left: Float?) {
        val style = TextStyle(color = c.ink, fontSize = with(scope) { 17f.toSp() }, fontWeight = FontWeight.Bold, fontFamily = family)
        val layout = measurer.measure(say, style, softWrap = false, maxLines = 1)
        val w = layout.size.width + 26f
        val h = layout.size.height + 12f
        val x = (left ?: (target.x - w * 0.75f)).coerceIn(8f, W - w - 8f)
        val y = if (left != null) 10f else (target.y - h - 24f).coerceIn(6f, 64f)
        val baseX = (target.x).coerceIn(x + 16f, x + w - 16f)
        val bottom = y + h
        val dx = target.x - baseX
        val dy = target.y - bottom
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        // 꼬리 끝은 차의 윤곽에 닿고, 길수록 밑동을 넓혀 가시처럼 보이지 않게 한다.
        val tip = Offset(baseX + dx / len * (len - 1f), bottom + dy / len * (len - 1f))
        val half = (8f + len * 0.06f).coerceAtMost(14f)
        val shapePath = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + w, bottom, CornerRadius(h / 2f, h / 2f)))
        }
        val tail = Path().apply {
            moveTo(baseX - half, bottom - 2f)
            lineTo(tip.x, tip.y)
            lineTo(baseX + half, bottom - 2f)
            close()
        }
        val merged = Path.combine(androidx.compose.ui.graphics.PathOperation.Union, shapePath, tail)
        scope.drawPath(merged, Color.White, style = Fill)
        scope.drawPath(merged, c.ink, style = strokeStyle(thin = false))
        scope.drawText(layout, topLeft = Offset(x + (w - layout.size.width) / 2f, y + (h - layout.size.height) / 2f))
    }

    // ================================================================ 그리는 도구

    private inline fun scaled(s: Float, x: Float, y: Float, block: () -> Unit) {
        val before = lineScale
        lineScale = before * s
        scope.withTransform({
            translate(x, y)
            scale(s, s, pivot = Offset.Zero)
        }) { block() }
        lineScale = before
    }

    private inline fun shifted(dx: Float, dy: Float, block: () -> Unit) {
        scope.withTransform({ translate(dx, dy) }) { block() }
    }

    private inline fun rotated(degrees: Float, px: Float, py: Float, block: () -> Unit) {
        scope.withTransform({ rotate(degrees, pivot = Offset(px, py)) }) { block() }
    }

    private fun strokeStyle(thin: Boolean) = Stroke(
        width = (if (thin) 2f else 3f) / lineScale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )

    private fun stroke(d: String, color: Color, thin: Boolean = false) = scope.drawPath(pathOf(d), color, style = strokeStyle(thin))

    private fun fill(d: String, color: Color) = scope.drawPath(pathOf(d), color, style = Fill)

    private fun fillBrush(d: String, brush: Brush) = scope.drawPath(pathOf(d), brush, style = Fill)

    private fun outline(d: String, thin: Boolean = false) = scope.drawPath(pathOf(d), c.ink, style = strokeStyle(thin))

    /** 채우고 먹선으로 두른다. */
    private fun shape(d: String, fill: Color, thin: Boolean = false) {
        val path = pathOf(d)
        scope.drawPath(path, fill, style = Fill)
        scope.drawPath(path, c.ink, style = strokeStyle(thin))
    }

    private fun circle(cx: Float, cy: Float, r: Float, fill: Color, outline: Color? = null, thin: Boolean = false) {
        scope.drawCircle(fill, r, Offset(cx, cy))
        if (outline != null) scope.drawCircle(outline, r, Offset(cx, cy), style = strokeStyle(thin))
    }

    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float, fill: Color, outline: Color? = null, thin: Boolean = false) {
        val topLeft = Offset(cx - rx, cy - ry)
        val size = Size(rx * 2, ry * 2)
        scope.drawOval(fill, topLeft, size)
        if (outline != null) scope.drawOval(outline, topLeft, size, style = strokeStyle(thin))
    }

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color, outline: Color? = null, thin: Boolean = false) {
        val radius = CornerRadius(r, r)
        scope.drawRoundRect(fill, Offset(x, y), Size(w, h), radius)
        if (outline != null) scope.drawRoundRect(outline, Offset(x, y), Size(w, h), radius, style = strokeStyle(thin))
    }

    /** [baseline]은 글자의 밑선이다. */
    private fun text(value: String, x: Float, baseline: Float, size: Float, color: Color, center: Boolean = false) {
        val style = TextStyle(color = color, fontSize = with(scope) { size.toSp() }, fontWeight = FontWeight.Bold, fontFamily = family)
        val layout = measurer.measure(value, style, softWrap = false, maxLines = 1)
        val left = if (center) x - layout.size.width / 2f else x
        scope.drawText(layout, topLeft = Offset(left, baseline - layout.firstBaseline))
    }

    /** 만화 효과 글자: 흰 글자에 먹선 테두리(어떤 배경 위에서도 읽힌다). */
    private fun loud(value: String, x: Float, baseline: Float, size: Float, center: Boolean = false) {
        val o = size * 0.07f
        for ((dx, dy) in OUTLINE) text(value, x + dx * o, baseline + dy * o, size, c.ink, center)
        text(value, x, baseline, size, Color.White, center)
    }

    private fun pathOf(d: String): Path = PATHS.getOrPut(d) { PathParser().parsePathString(d).toPath() }

    private companion object {
        /** 같은 조각을 그릴 때마다 경로 글을 다시 읽지 않게 모아 둔다. 그리기는 화면 스레드에서만 한다. */
        val PATHS = HashMap<String, Path>()

        val OUTLINE = listOf(-1f to -1f, 0f to -1f, 1f to -1f, -1f to 0f, 1f to 0f, -1f to 1f, 0f to 1f, 1f to 1f)

        val STARS = listOf(Triple(30f, 30f, 1.6f), Triple(70f, 18f, 1.2f), Triple(118f, 40f, 1.4f), Triple(160f, 14f, 1.1f), Triple(204f, 34f, 1.6f), Triple(236f, 16f, 1.2f), Triple(92f, 62f, 1.0f), Triple(306f, 70f, 1.2f))

        /** 빗줄기 자리(고정이라 같은 컷은 늘 같은 모양). 앞 절반이 보통 비, 전부가 큰비. */
        val RAIN: List<Pair<Float, Float>> = List(56) { i -> ((i * 61 + 17) % 336 - 8).toFloat() to ((i * 47 + 11) % 236 - 6).toFloat() }

        val SPLASH = listOf(40f to 214f, 110f to 230f, 200f to 220f, 270f to 234f, 300f to 210f)

        val DROPS = listOf(Triple(30f, 40f, 4f), Triple(80f, 70f, 3f), Triple(120f, 30f, 5f), Triple(170f, 60f, 3.5f), Triple(290f, 50f, 4.5f), Triple(260f, 90f, 3f), Triple(60f, 130f, 4f), Triple(150f, 150f, 3f), Triple(300f, 160f, 3.5f))
    }
}
