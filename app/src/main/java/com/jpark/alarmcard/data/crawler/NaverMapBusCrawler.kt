package com.jpark.alarmcard.data.crawler

import com.jpark.alarmcard.data.remote.NextData
import com.jpark.alarmcard.data.remote.arrayOrEmpty
import com.jpark.alarmcard.data.remote.doubleOrNull
import com.jpark.alarmcard.data.remote.httpGetString
import com.jpark.alarmcard.data.remote.intOrNull
import com.jpark.alarmcard.data.remote.strOrNull
import com.jpark.alarmcard.data.remote.boolOrNull
import com.jpark.alarmcard.domain.model.BusArrival
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

data class BusStationSearchResult(
    val stationId: String,
    val stationName: String,
    val cityCode: String? = null,
    val displayCode: String? = null
)

data class BusStationDetail(
    val stationId: String,
    val stationName: String,
    val cityCode: String?,
    val arrivals: List<BusArrival>
)

/**
 * 네이버 지도 대중교통(버스) 실시간 도착정보 크롤러.
 *
 * ★ 사용하는 실제 엔드포인트 (2026-08 관측):
 *   GET https://map.naver.com/p/api/pubtrans/realtime/bus/arrivals/multi?stations={stationId}
 *   → 200 JSON. 그 정류장에 서는 모든 노선의 실시간 도착 정보를 반환.
 *
 * 응답 예:
 *   [{
 *     "stopId": 194374,
 *     "stopDisplayName": "판교역동편",
 *     "id": 20025664,            ← routeId
 *     "name": "6012",             ← routeNo
 *     "type": {"id":4,"name":"직행좌석", ...},
 *     "direction": "낙성대을...로데카슨...",
 *     "arrival": {
 *       "status": "RUNNING",
 *       "buses": [{
 *         "plateNo": "경기76자281",
 *         "lowFloor": false,
 *         "remainingTime": 1844,  ← 초 단위
 *         "remainingStop": 13,
 *         "remainingSeat": 41,
 *         "congestion": {"code":"RELAXED"}
 *       }],
 *       "referenceTime": "2026-08-08T15:24:29+09:00"
 *     }
 *   }, ...]
 *
 * 정류장 검색 API 는 캡차 벽 때문에 프로그램적 크롤링이 불안정하므로,
 * 사용자는 브라우저의 네이버 지도 URL(정류장 페이지)을 앱에 붙여넣는 방식으로 stationId 를 등록한다:
 *   https://map.naver.com/p/search/{keyword}/bus-station/{stationId}?...
 *   → parseStationUrl() 이 stationId 와 stationName 을 뽑아냄.
 */
@Singleton
class NaverMapBusCrawler @Inject constructor() {

    /**
     * 사용자가 붙여넣은 네이버 지도 URL 에서 stationId + name 을 파싱.
     * 지원 형태:
     *  - https://map.naver.com/p/search/{query}/bus-station/{stationId}?...
     *  - https://map.naver.com/p/search/{query}/bus-route/{routeId}?bsl={routeId},{stationId},{seq}...
     *  - https://map.naver.com/p/bus/bus-station/{name}/bus-route/{routeId}?bsl=...
     *  - https://m.map.naver.com/bus/station?stationID={stationId}&busID={routeId}
     *  - stationId 숫자만 (예: 194374)
     */
    fun parseStationUrl(input: String): BusStationSearchResult? {
        val s = input.trim()
        if (s.matches(Regex("^\\d{3,10}$"))) {
            return BusStationSearchResult(stationId = s, stationName = "정류장 $s")
        }

        // Mobile web: m.map.naver.com/bus/station?stationID=194374&busID=20025664
        val mobileStationRx = Regex("m\\.map\\.naver\\.com/bus/station")
        if (mobileStationRx.find(s) != null) {
            val stationIdRx = Regex("[?&]stationID=(\\d+)")
            val stationIdMatch = stationIdRx.find(s)
            if (stationIdMatch != null) {
                val stationId = stationIdMatch.groupValues[1]
                return BusStationSearchResult(stationId = stationId, stationName = "정류장 $stationId")
            }
        }

        // bus-station/{id} (desktop web)
        val busStationRx = Regex("/bus-station/(\\d+)")
        val bsMatch = busStationRx.find(s)
        val nameFromPath: String? = Regex("/p/search/([^/?]+)").find(s)?.groupValues?.getOrNull(1)?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull()
        }
        if (bsMatch != null) {
            val id = bsMatch.groupValues[1]
            return BusStationSearchResult(stationId = id, stationName = nameFromPath ?: "정류장 $id")
        }

        // bsl={routeId},{stationId},{seq} 로부터 stationId 추출 (desktop web)
        val bslRx = Regex("[?&]bsl=([^&]+)")
        val bslMatch = bslRx.find(s)
        if (bslMatch != null) {
            val parts = bslMatch.groupValues[1].split(",")
            if (parts.size >= 2) {
                val stationId = parts[1]
                if (stationId.matches(Regex("^\\d+$"))) {
                    return BusStationSearchResult(stationId = stationId, stationName = nameFromPath ?: "정류장 $stationId")
                }
            }
        }

        return null
    }

    /**
     * 실시간 도착정보 조회.
     * @param stationId 예: "194374"
     */
    suspend fun fetchArrivals(stationId: String, cityCode: String? = null): BusStationDetail {
        val url = "https://map.naver.com/p/api/pubtrans/realtime/bus/arrivals/multi?stations=$stationId"
        val body = httpGetString(
            url,
            referer = "https://map.naver.com/p/search/-/bus-station/$stationId",
            extraHeaders = mapOf("Accept" to "application/json")
        )
        return parseArrivalsResponse(body, stationId, cityCode)
    }

    private fun parseArrivalsResponse(body: String, stationId: String, cityCode: String?): BusStationDetail {
        val root = try {
            NextData.parseObject(body)
        } catch (t: Throwable) {
            Timber.w(t, "arrivals JSON parse failed")
            return BusStationDetail(stationId, "정류장 $stationId", cityCode, emptyList())
        }

        val items: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                // wrapped forms {"result":[...]} or {"data":[...]}
                listOf("result", "data", "items", "arrivals").flatMap { k ->
                    (root[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
                }
            }
            else -> emptyList()
        }

        if (items.isEmpty()) {
            return BusStationDetail(stationId, "정류장 $stationId", cityCode, emptyList())
        }

        // stationName 결정 (첫 항목 stopDisplayName)
        val stationName = items.firstOrNull()?.strOrNull("stopDisplayName") ?: "정류장 $stationId"

        val arrivals = items.mapNotNull { itm ->
            val routeIdVal = itm["id"]?.let { runCatching { it.toString().trim('"') }.getOrNull() }
                ?: itm.strOrNull("routeId")
                ?: return@mapNotNull null
            val routeNo = itm.strOrNull("name")
                ?: itm.strOrNull("shortName")
                ?: return@mapNotNull null

            val arrival = itm["arrival"] as? JsonObject
            val buses = (arrival?.get("buses") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

            val eta1 = buses.getOrNull(0)?.intOrNull("remainingTime")
            val eta2 = buses.getOrNull(1)?.intOrNull("remainingTime")
            val remainStops1 = buses.getOrNull(0)?.intOrNull("remainingStop")
            val remainStops2 = buses.getOrNull(1)?.intOrNull("remainingStop")
            val lowFloor1 = buses.getOrNull(0)?.boolOrNull("lowFloor") ?: false

            BusArrival(
                routeId = routeIdVal,
                routeNo = routeNo,
                eta1Sec = eta1,
                eta2Sec = eta2,
                remainStops1 = remainStops1,
                remainStops2 = remainStops2,
                lowFloor1 = lowFloor1
            )
        }

        return BusStationDetail(
            stationId = stationId,
            stationName = stationName,
            cityCode = cityCode,
            arrivals = arrivals
        )
    }

    /**
     * 정류장 검색은 네이버 캡차 벽으로 프로그램적 접근이 불안정.
     * 대신 사용자가 붙여넣는 URL 또는 stationId 를 parseStationUrl 로 처리해 대체함.
     * 이 메서드는 시도만 하고 결과가 없으면 빈 리스트를 반환한다 (fail-soft).
     */
    suspend fun searchStation(keyword: String): List<BusStationSearchResult> {
        // 사용자가 URL/숫자를 붙여넣었다면 즉시 파싱해서 반환
        parseStationUrl(keyword)?.let { return listOf(it) }
        // 그 외 텍스트 검색은 지원하지 않음
        return emptyList()
    }
}
