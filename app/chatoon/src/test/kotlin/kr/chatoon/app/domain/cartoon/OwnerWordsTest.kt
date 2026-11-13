package kr.chatoon.app.domain.cartoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerWordsTest {

    @Test
    fun `정해 둔 낱말이 들어 있으면 그 상황으로 가른다`() {
        assertEquals("interview", situationOf("면접 보러").key)
        assertEquals("date", situationOf("소개팅").key)
        assertEquals("work", situationOf("회사 야근").key)
        assertEquals("shopping", situationOf("코스트코").key)
        assertEquals("just", situationOf("그냥 답답해서").key)
    }

    @Test
    fun `두 상황에 걸치면 사전에서 먼저 나오는 쪽으로 가른다`() {
        // "엄마 병원"은 가족이기도 하지만, 걱정하는 말로 받는 쪽이 맞다.
        assertEquals("hospital", situationOf("엄마 병원").key)
    }

    @Test
    fun `사전에 없는 말은 뜻을 넘겨짚지 않는다`() {
        assertEquals("unknown", situationOf("달 보러 감").key)
        // 누구를 데리러 간 것인지, 본인이 간 것인지 알 수 없는 말은 가르지 않는다.
        assertEquals("unknown", situationOf("학교").key)
        assertEquals("unknown", situationOf("공항").key)
        assertEquals("unknown", joltReasonOf("모르겠어요").key)
    }

    @Test
    fun `놀란 이유는 구체적인 원인을 먼저 받는다`() {
        assertEquals("lapse", joltReasonOf("한눈팔다가 사고 날 뻔").key)
        assertEquals("near", joltReasonOf("사고 날 뻔").key)
        assertEquals("frontstop", joltReasonOf("앞차가 갑자기 서서").key)
        assertEquals("frontstop", joltReasonOf("앞차가 갑자기").key)
        assertEquals("cutin", joltReasonOf("누가 끼어들어서").key)
        assertEquals("sudden", joltReasonOf("고양이가 튀어나와서").key)
        assertEquals("slowcar", joltReasonOf("앞차가 너무 느려서").key)
        assertEquals("signal", joltReasonOf("노란불이라서").key)
        assertEquals("hurry", joltReasonOf("늦어서").key)
    }

    @Test
    fun `주인의 말을 옮길 때 받침에 맞춰 잇는다`() {
        assertEquals("“면접 보러”라고", quoted("면접 보러"))
        assertEquals("“한눈팔다가 사고 날 뻔”이라고", quoted("한눈팔다가 사고 날 뻔"))
        assertEquals("“OK”라고", quoted("OK"))
    }

    @Test
    fun `말풍선에는 한 줄 여덟 자까지로 나눠 넣는다`() {
        assertEquals(listOf("면접 보러"), splitForBalloon("면접 보러"))
        assertEquals(listOf("한눈팔다가", "사고 날 뻔"), splitForBalloon("한눈팔다가 사고 날 뻔"))
        assertEquals(listOf("앞차가 너무", "느려서"), splitForBalloon("앞차가 너무 느려서"))
        assertEquals(listOf("고양이가", "튀어나와서"), splitForBalloon("고양이가 튀어나와서"))
        assertEquals(listOf("갑자기", "끼어들어서"), splitForBalloon("갑자기 끼어들어서"))
        assertEquals(listOf("12345678", "90123456"), splitForBalloon("1234567890123456"))
    }

    @Test
    fun `받을 수 있는 가장 긴 말도 두 줄에 들어간다`() {
        for (said in listOf("가나다라마바사아자차카타파하가나", "가 나 다 라 마 바 사 아", "가나다라마바사아 자차카타파하가", "가나다라마 바사아자차카타파하")) {
            val lines = splitForBalloon(Seen.trimAnswer(said))
            assertTrue("두 줄을 넘는다: $lines", lines.size <= 2)
            assertTrue("한 줄이 여덟 자를 넘는다: $lines", lines.all { it.length <= 8 })
        }
    }

    @Test
    fun `주인의 말은 앞뒤 빈칸만 떼고 길이만 맞춘다`() {
        assertEquals("면접 보러", Seen.trimAnswer("  면접 보러  "))
        assertEquals(16, Seen.trimAnswer("가".repeat(40)).length)
        assertEquals("", Seen.trimAnswer("   "))
    }
}
