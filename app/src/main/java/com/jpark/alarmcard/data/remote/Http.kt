package com.jpark.alarmcard.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset

/** OkHttp 동기 호출을 코루틴 friendly 하게 감쌈. Referer 지정 가능. */
suspend fun httpGetString(
    url: String,
    referer: String? = null,
    extraHeaders: Map<String, String> = emptyMap()
): String = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(url).get()
    referer?.let { builder.header("Referer", it) }
    extraHeaders.forEach { (k, v) -> builder.header(k, v) }
    HttpClient.client.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
        resp.body?.string().orEmpty()
    }
}

/**
 * 지정한 charset으로 응답 본문을 디코딩하여 반환.
 * 네이버 finance.naver.com 같이 EUC-KR로 서빙되는 페이지에 사용.
 */
suspend fun httpGetStringWithCharset(
    url: String,
    charset: Charset,
    referer: String? = null,
    extraHeaders: Map<String, String> = emptyMap()
): String = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(url).get()
    referer?.let { builder.header("Referer", it) }
    extraHeaders.forEach { (k, v) -> builder.header(k, v) }
    HttpClient.client.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
        val bytes = resp.body?.bytes() ?: ByteArray(0)
        String(bytes, charset)
    }
}


