package com.jpark.alarmcard.data.remote

import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 공용 OkHttp 클라이언트.
 * - 모바일 크롬 UA / 필요 시 Referer 설정
 * - 간단한 in-memory CookieJar (네이버 세션 쿠키 유지)
 * - 5초 timeout, 2회 재시도(호출부에서 구현)
 */
object HttpClient {

    private val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            return cookieStore.entries
                .filter { host.endsWith(it.key) }
                .flatMap { it.value }
                .filter { !it.hasExpired() }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = url.topPrivateDomainOrHost()
            cookieStore.compute(key) { _, existing ->
                val list = existing ?: mutableListOf()
                // 같은 이름 쿠키 교체
                cookies.forEach { c ->
                    list.removeAll { it.name == c.name }
                    list.add(c)
                }
                list
            }
        }

        private fun Cookie.hasExpired(): Boolean = expiresAt < System.currentTimeMillis()
        private fun HttpUrl.topPrivateDomainOrHost(): String = topPrivateDomain() ?: host
    }

    private val defaultHeaders = Interceptor { chain ->
        val req: Request = chain.request().newBuilder()
            .apply {
                if (chain.request().header("User-Agent") == null) header("User-Agent", UA)
                header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
            }
            .build()
        chain.proceed(req)
    }

    val client: OkHttpClient by lazy {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor(defaultHeaders)
            .addInterceptor(log)
            .retryOnConnectionFailure(true)
            .build()
    }
}
