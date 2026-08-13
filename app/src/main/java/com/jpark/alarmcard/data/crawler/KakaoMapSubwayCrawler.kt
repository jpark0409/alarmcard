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
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        val jsInterface = object {
            @JavascriptInterface
            fun onDataReceived(data: String) {
                try {
                    Timber.d("Raw Crawler Result: $data")
                    val detail = parseParsedJson(data, stationId)
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
                // DOM 요소가 렌더링될 때까지 주기적으로 체크하는 스크립트 실행
                view?.evaluateJavascript(
                    """
                    (function() {
                        var maxRetries = 20;
                        var retries = 0;
                        
                        var interval = setInterval(function() {
                            retries++;
                            
                            // 역 이름 추출
                            var titleEl = document.querySelector('.tit_location') || document.querySelector('.heading_title');
                            var stationName = titleEl ? titleEl.innerText.trim() : '지하철역';
                            
                            // 도착 정보 리스트 추출 (카카오맵 지하철 DOM 요소 탐색)
                            var arrivalItems = document.querySelectorAll('.list_arrival li, .info_arrival, .item_subway');
                            var arrivals = [];

                            if (arrivalItems.length > 0 || retries >= maxRetries) {
                                clearInterval(interval);

                                arrivalItems.forEach(function(item) {
                                    var line = item.querySelector('.txt_line, .badge_line')?.innerText.trim() || '';
                                    var dest = item.querySelector('.txt_destination, .txt_dest')?.innerText.trim() || '';
                                    var status = item.querySelector('.txt_time, .state_arrival')?.innerText.trim() || '';

                                    if (line || dest || status) {
                                        arrivals.push({
                                            lineName: line,
                                            destination: dest,
                                            status: status
                                        });
                                    }
                                });

                                AndroidBridge.onDataReceived(JSON.stringify({
                                    stationName: stationName,
                                    arrivals: arrivals
                                }));
                            }
                        }, 500);
                    })()
                    """.trimIndent(), null
                )
            }
        }

        webView.loadUrl("https://place.map.kakao.com/$stationId")

        val result = withTimeoutOrNull(12000) {
            deferred.await()
        }

        webView.destroy()

        result ?: SubwayStationDetail(stationId, "Timeout", emptyList())
    }

    private fun parseParsedJson(body: String, stationId: String): SubwayStationDetail {
        val root = json.parseToJsonElement(body).jsonObject
        val stationName = root["stationName"]?.jsonPrimitive?.content ?: "지하철역"
        val arrivalsArray = root["arrivals"]?.jsonArray ?: JsonArray(emptyList<Nothing>())

        val arrivalsList = arrivalsArray.map { element ->
            val obj = element.jsonObject
            val lineName = obj["lineName"]?.jsonPrimitive?.content ?: ""
            val destination = obj["destination"]?.jsonPrimitive?.content ?: ""
            val status = obj["status"]?.jsonPrimitive?.content ?: ""

            // "3분 후", "약 5분" 등의 문자열에서 숫자(초)만 추출 시도
            val etaSec = Regex("(\\d+)분").find(status)?.groupValues?.get(1)?.toIntOrNull()?.times(60)

            SubwayArrival(
                lineId = lineName,
                lineName = lineName,
                destination = destination,
                eta1Sec = etaSec,
                eta2Sec = null,
                status1 = status
            )
        }

        return SubwayStationDetail(
            stationId = stationId,
            stationName = stationName,
            arrivals = arrivalsList
        )
    }
}