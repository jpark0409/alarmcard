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

/**
 * 카카오맵 지하철 실시간 도착정보 크롤러.
 * WebView를 활용하여 봇 감지를 우회함.
 */
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
        
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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
                // place.map.kakao.com의 지하철 정보 구조 시뮬레이션 추출
                // window.INITS.subway 가 주요 데이터 소스임
                view?.evaluateJavascript(
                    """
                    (function() {
                        var data = {};
                        if (window.INITS) {
                            if (window.INITS.subway) {
                                data = window.INITS.subway;
                            } else if (window.INITS.place && window.INITS.place.subway) {
                                data = window.INITS.place.subway;
                            }
                        }
                        AndroidBridge.onDataReceived(JSON.stringify(data));
                    })()
                    """.trimIndent(), null
                )
            }
        }

        webView.loadUrl("https://place.map.kakao.com/$stationId")
        
        // Timeout (15초)
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            while (!deferred.isCompleted && System.currentTimeMillis() - startTime < 15000) {
                kotlinx.coroutines.delay(500)
            }
            if (!deferred.isCompleted) {
                deferred.complete(SubwayStationDetail(stationId, "Timeout", emptyList()))
            }
        }

        deferred.await()
    }

    private fun parseArrivalsResponse(body: String, stationId: String): SubwayStationDetail {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (t: Throwable) {
            return SubwayStationDetail(stationId, "지하철역", emptyList())
        }

        val stationName = root["stationName"]?.jsonPrimitive?.content 
            ?: root["name"]?.jsonPrimitive?.content 
            ?: "지하철역"
            
        val arrivalsList = mutableListOf<SubwayArrival>()

        // Kakao Map의 실제 리스폰스 구조 (window.INITS.subway)
        // realtime: { arrivals: [ { lineName, status, arrivalTime, ... } ] }
        val realtime = root["realtime"]?.jsonObject
        val list = realtime?.get("arrivals")?.jsonArray ?: root["realtimeList"]?.jsonArray ?: JsonArray(emptyList())
        
        list.forEach { element ->
            val obj = element.jsonObject
            val lineName = obj["lineName"]?.jsonPrimitive?.content ?: ""
            val destination = obj["destination"]?.jsonPrimitive?.content ?: obj["endStationName"]?.jsonPrimitive?.content ?: ""
            val status = obj["status"]?.jsonPrimitive?.content ?: obj["message"]?.jsonPrimitive?.content
            
            // arrivalTime (초 단위 또는 HH:mm:ss) 파싱 로직
            val etaSec = obj["arrivalTime"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: obj["eta"]?.jsonPrimitive?.content?.toIntOrNull()

            arrivalsList.add(SubwayArrival(
                lineId = obj["lineId"]?.jsonPrimitive?.content ?: lineName,
                lineName = lineName,
                destination = destination,
                eta1Sec = etaSec,
                eta2Sec = null,
                status1 = status
            ))
        }

        return SubwayStationDetail(
            stationId = stationId,
            stationName = stationName,
            arrivals = arrivalsList
        )
    }
}
