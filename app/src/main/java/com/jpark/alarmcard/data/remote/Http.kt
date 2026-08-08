package com.jpark.alarmcard.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

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
