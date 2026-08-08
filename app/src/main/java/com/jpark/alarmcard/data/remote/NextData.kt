package com.jpark.alarmcard.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup

/**
 * 네이버 모바일 페이지들은 Next.js 기반으로, HTML 안에
 * <script id="__NEXT_DATA__" type="application/json">...</script>
 * 형태로 JSON을 실어놓는다. 이 유틸이 그 JSON을 파싱해준다.
 * CSS 셀렉터 파싱보다 훨씬 안정적이므로 크롤러들은 이걸 1순위로 사용.
 */
object NextData {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** HTML에서 __NEXT_DATA__ JSON 오브젝트를 뽑아낸다. 없으면 null. */
    fun extract(html: String): JsonObject? {
        val doc = Jsoup.parse(html)
        val script = doc.selectFirst("script#__NEXT_DATA__") ?: return null
        val text = script.data().takeIf { it.isNotBlank() } ?: script.html()
        if (text.isBlank()) return null
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    fun parseObject(text: String): JsonElement = json.parseToJsonElement(text)

    val parser: Json get() = json
}
