package kr.chatoon.app.audit

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 화면 안내 문구가 실제 동작과 맞는가. 화면은 Compose라 그리지 않고, 화면이 쓰는 문자열 자원(strings*.xml)을 직접 읽어 대조한다.
 */
class SurfaceLabelAuditTest {

    private val strings: Map<String, String> by lazy {
        File("src/main/res/values").listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!
            .flatMap { Regex("<string name=\"([a-z_0-9]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(it.readText()).toList() }
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
    }

    private fun s(key: String) = strings[key] ?: error("문자열 없음: $key")

    @Test fun `S2 - speed permission explanations name what it counts and nothing it no longer collects`() {
        // 차툰은 속도 권한으로 급가속·급감속만 센다. 제한속도·방향지시등·문 잠금은 수집하지 않는다(2026-11-13).
        val texts = listOf("speed_dialog_body", "consent_collect")
        val problems = texts.mapNotNull { key ->
            val t = s(key)
            val stale = listOf("제한속도", "방향지시등", "문 잠금").filter { t.contains(it) }
            when {
                !t.contains("급가속") -> "$key: 급가속 없음"
                stale.isNotEmpty() -> "$key: 수집하지 않는 ${stale.joinToString()}을(를) 적음"
                else -> null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun `user-facing app texts no longer name the old driving-age product`() {
        // 차툰으로 바꾼 뒤 사용자에게 보이는 글에 예전 앱 이름이 남지 않게 한다.
        val visible = listOf("app_name", "consent_title", "consent_intro", "consent_not_medical", "recording_title", "settings_version", "oss_intro", "speed_blocked", "speed_dialog_body", "settings_vehicle_hint")
        val left = visible.filter { s(it).contains("운전나이") }
        assertTrue(left.toString(), left.isEmpty())
    }
}
