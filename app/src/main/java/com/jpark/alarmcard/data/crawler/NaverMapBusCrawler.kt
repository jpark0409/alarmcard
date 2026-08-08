package com.jpark.alarmcard.data.crawler

import com.jpark.alarmcard.data.remote.NextData
import com.jpark.alarmcard.data.remote.boolOrNull
import com.jpark.alarmcard.data.remote.doubleOrNull
import com.jpark.alarmcard.data.remote.httpGetString
import com.jpark.alarmcard.data.remote.intOrNull
import com.jpark.alarmcard.data.remote.strOrNull
import com.jpark.alarmcard.domain.model.BusArrival
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class BusStationSearchResult(
    val stationId: String,
    val stationName: String,
    val cityCode: String? = null,
    val displayCode: String? = null // 정류장 표시번호 (ARS 등)
)

data class BusStationDetail(
    val stationId: String,
    val stationName: String,
    val cityCode: String?,
    val arrivals: List<BusArrival>
)

/**
 * 네이버 지도의 대중교통 정보를 크롤링.
 * 지도 팀 내부 엔드포인트는 스키마가 자주 변하므로:
 *   1) 여러 후보 URL을 순차 시도
 *   2) 응답 JSON에서 재귀 스캔으로 "arrivalInfo" 같은 필드를 찾아 파싱
 *
 * 지역: 서울/수도권(경기·인천)에 최적화. 그 외 지역도 대개 동작.
 */
@Singleton
class NaverMapBusCrawler @Inject constructor() {

    /** 정류장 이름/번호로 검색 */
    suspend fun searchStation(keyword: String): List<BusStationSearchResult> {
        val q = URLEncoder.encode(keyword, "UTF-8")
        val urls = listOf(
            "https://map.naver.com/p/api/search/allSearch?query=$q&type=bus_station",
            "https://map.naver.com/p/api/search/busStation?query=$q",
            "https://pubtrans.map.naver.com/api/station/search?query=$q&lang=ko"
        )
        for (url in urls) {
            val body = runCatching {
                httpGetString(
                    url,
                    referer = "https://map.naver.com/",
                    extraHeaders = mapOf("Accept" to "application/json, text/plain, */*")
                )
            }.getOrNull() ?: continue
            val root = runCatching { NextData.parseObject(body) }.getOrNull() ?: continue
            val results = mutableListOf<BusStationSearchResult>()
            collectStationSearch(root, results)
            if (results.isNotEmpty()) return results.distinctBy { it.stationId }
        }
        return emptyList()
    }

    /** 정류장 도착정보 조회 */
    suspend fun fetchArrivals(stationId: String, cityCode: String? = null): BusStationDetail {
        val urls = buildList {
            add("https://pubtrans.map.naver.com/api/station/${URLEncoder.encode(stationId, "UTF-8")}?lang=ko")
            if (cityCode != null) {
                add("https://pubtrans.map.naver.com/api/station/${URLEncoder.encode(stationId, "UTF-8")}?cityCode=$cityCode&lang=ko")
            }
            add("https://map.naver.com/p/api/bus/station/${URLEncoder.encode(stationId, "UTF-8")}")
        }
        var lastErr: Throwable? = null
        for (url in urls) {
            try {
                val body = httpGetString(
                    url,
                    referer = "https://map.naver.com/",
                    extraHeaders = mapOf("Accept" to "application/json, text/plain, */*")
                )
                val root = NextData.parseObject(body)
                val name = findStationName(root) ?: stationId
                val arrivals = mutableListOf<BusArrival>()
                collectArrivals(root, arrivals)
                if (arrivals.isNotEmpty() || name != stationId) {
                    return BusStationDetail(stationId, name, cityCode, arrivals)
                }
            } catch (t: Throwable) {
                lastErr = t
                Timber.d(t, "bus fetchArrivals attempt failed: $url")
            }
        }
        throw lastErr ?: IllegalStateException("No arrivals for $stationId")
    }

    /* ---------- Parsers ---------- */

    private fun collectStationSearch(root: JsonElement, out: MutableList<BusStationSearchResult>) {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur is JsonObject) {
                val id = cur.strOrNull("stationId")
                    ?: cur.strOrNull("id")
                    ?: cur.strOrNull("bstopId")
                val name = cur.strOrNull("stationName")
                    ?: cur.strOrNull("name")
                    ?: cur.strOrNull("title")?.replace("<[^>]+>".toRegex(), "")
                val city = cur.strOrNull("cityCode") ?: cur.strOrNull("cityName")
                val display = cur.strOrNull("displayCode")
                    ?: cur.strOrNull("arsId")
                    ?: cur.strOrNull("stationNo")

                val looksLikeStation = id != null && name != null &&
                    (cur.keys.any { it.contains("station", ignoreCase = true) } ||
                        cur.keys.contains("arsId") || cur.keys.contains("bstopId"))

                if (looksLikeStation) {
                    out += BusStationSearchResult(id!!, name!!, city, display)
                }
                cur.values.forEach { stack.addLast(it) }
            } else if (cur is JsonArray) {
                cur.forEach { stack.addLast(it) }
            }
        }
    }

    private fun findStationName(root: JsonElement): String? {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur is JsonObject) {
                cur.strOrNull("stationName")?.let { return it }
                cur.strOrNull("name")?.takeIf { cur.keys.contains("stationId") }?.let { return it }
                cur.values.forEach { stack.addLast(it) }
            } else if (cur is JsonArray) cur.forEach { stack.addLast(it) }
        }
        return null
    }

    private fun collectArrivals(root: JsonElement, out: MutableList<BusArrival>) {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur is JsonObject) {
                // route level object: routeNo/routeName + arrivalInfo? or predictTime1?
                val routeNo = cur.strOrNull("routeName")
                    ?: cur.strOrNull("routeNo")
                    ?: cur.strOrNull("busNo")
                val routeId = cur.strOrNull("routeId") ?: cur.strOrNull("busRouteId")

                if (routeNo != null && routeId != null) {
                    val arrivalObj = (cur["arrivalInfo"] as? JsonObject) ?: cur
                    val eta1 = arrivalObj.intOrNull("predictTimeSec1")
                        ?: arrivalObj.intOrNull("remainSec1")
                        ?: arrivalObj.intOrNull("arrTime1")
                        ?: arrivalObj.intOrNull("firstArrivalTimeSec")
                    val eta2 = arrivalObj.intOrNull("predictTimeSec2")
                        ?: arrivalObj.intOrNull("remainSec2")
                        ?: arrivalObj.intOrNull("arrTime2")
                        ?: arrivalObj.intOrNull("secondArrivalTimeSec")
                    val remain1 = arrivalObj.intOrNull("remainStop1")
                        ?: arrivalObj.intOrNull("firstArrivalRemainStop")
                        ?: arrivalObj.intOrNull("stationCount1")
                    val remain2 = arrivalObj.intOrNull("remainStop2")
                        ?: arrivalObj.intOrNull("secondArrivalRemainStop")
                        ?: arrivalObj.intOrNull("stationCount2")
                    val lowFloor = arrivalObj.boolOrNull("lowFloor1")
                        ?: arrivalObj.boolOrNull("isLowFloor")
                        ?: false

                    // 도착정보가 없어도 노선은 표시할 수 있게 허용
                    if (out.none { it.routeId == routeId }) {
                        out += BusArrival(
                            routeId = routeId,
                            routeNo = routeNo,
                            eta1Sec = eta1,
                            eta2Sec = eta2,
                            remainStops1 = remain1,
                            remainStops2 = remain2,
                            lowFloor1 = lowFloor
                        )
                    }
                }

                cur.values.forEach { stack.addLast(it) }
            } else if (cur is JsonArray) cur.forEach { stack.addLast(it) }
        }
    }
}
