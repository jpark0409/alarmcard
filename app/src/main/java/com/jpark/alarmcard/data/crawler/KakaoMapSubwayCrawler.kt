package com.jpark.alarmcard.data.crawler

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jpark.alarmcard.domain.model.SubwayArrival
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class SubwayStationSearchResult(
    val stationId: String,
    val stationName: String
)

data class SubwayStationDetail(
    val stationId: String,
    val stationName: String,
    val arrivals: List<SubwayArrival>
)

@Singleton
class KakaoMapSubwayCrawler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseStationUrl(input: String): SubwayStationSearchResult? {
        val s = input.trim()
        if (s.matches(Regex("^SES\\d+$"))) {
            return SubwayStationSearchResult(stationId = s, stationName = "지하철역 $s")
        }
        val idMatch = Regex("place\\.map\\.kakao\\.com/(SES\\d+)").find(s)
        if (idMatch != null) {
            val id = idMatch.groupValues[1]
            return SubwayStationSearchResult(stationId = id, stationName = "지하철역 $id")
        }
        return null
    }

    suspend fun fetchArrivals(stationId: String): SubwayStationDetail = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<SubwayStationDetail>()
        val webView = WebView(context)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 모바일 Chrome User-Agent로 변경하여 모바일 웹 모드로 실행
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        val jsInterface = object {
            @JavascriptInterface
            fun onDataReceived(data: String) {
                try {
                    val detail = parseArrivalsResponse(data, stationId)
                    deferred.complete(detail)
                } catch (e: Exception) {
                    Timber.e(e, "Subway parse error")
                    deferred.complete(SubwayStationDetail(stationId, "Error", emptyList()))
                }
            }
        }
        webView.addJavascriptInterface(jsInterface, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // WebView 내 세션을 이용하여 카카오 백엔드 JSON API 직접 fetch
                view?.evaluateJavascript(
                    """
                    (function() {
                        fetch('https://place.map.kakao.com/main/v/' + '$stationId')
                            .then(response => response.json())
                            .then(data => {
                                AndroidBridge.onDataReceived(JSON.stringify(data));
                            })
                            .catch(err => {
                                AndroidBridge.onDataReceived(JSON.stringify({}));
                            });
                    })()
                    """.trimIndent(), null
                )
            }
        }

        webView.loadUrl("https://place.map.kakao.com/$stationId")

        // 표준 타임아웃 처리 (10초)
        val result = withTimeoutOrNull(10000) {
            deferred.await()
        }

        // 메모리 누수 방지를 위해 WebView 정리
        webView.destroy()

        result ?: SubwayStationDetail(stationId, "Timeout", emptyList())
    }

    private fun parseArrivalsResponse(body: String, stationId: String): SubwayStationDetail {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (t: Throwable) {
            return SubwayStationDetail(stationId, "지하철역", emptyList())
        }

        // 역명 파싱 (basicInfo -> placenamefull 또는 title)
        val basicInfo = root["basicInfo"]?.jsonObject
        val stationName = basicInfo?.get("placenamefull")?.jsonPrimitive?.content
            ?: basicInfo?.get("title")?.jsonPrimitive?.content
            ?: root["stationName"]?.jsonPrimitive?.content
            ?: "지하철역"

        val arrivalsList = mutableListOf<SubwayArrival>()

        // 카카오 백엔드 API(main/v/) 구조 파싱
        // realtime 정보는 subwayInfo 또는 realtime / realtimeList 에 위치
        val subwayInfo = root["subwayInfo"]?.jsonObject
        val realtimeList = subwayInfo?.get("realtimeList")?.jsonArray
            ?: subwayInfo?.get("arrivals")?.jsonArray
            ?: root["realtime"]?.jsonObject?.get("arrivals")?.jsonArray
            ?: JsonArray(emptyList())

        realtimeList.forEach { element ->
            val obj = element.jsonObject
            val lineName = obj["lineName"]?.jsonPrimitive?.content 
                ?: obj["subwayLineName"]?.jsonPrimitive?.content 
                ?: ""
            val destination = obj["destination"]?.jsonPrimitive?.content 
                ?: obj["endStationName"]?.jsonPrimitive?.content 
                ?: ""
            val status = obj["status"]?.jsonPrimitive?.content 
                ?: obj["arvlMsg2"]?.jsonPrimitive?.content
                ?: obj["message"]?.jsonPrimitive?.content

            val etaSec = obj["arrivalTime"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: obj["barvlDt"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: obj["eta"]?.jsonPrimitive?.content?.toIntOrNull()

            arrivalsList.add(
                SubwayArrival(
                    lineId = obj["lineId"]?.jsonPrimitive?.content ?: lineName,
                    lineName = lineName,
                    destination = destination,
                    eta1Sec = etaSec,
                    eta2Sec = null,
                    status1 = status
                )
            )
        }

        return SubwayStationDetail(
            stationId = stationId,
            stationName = stationName,
            arrivals = arrivalsList
        )
    }
}