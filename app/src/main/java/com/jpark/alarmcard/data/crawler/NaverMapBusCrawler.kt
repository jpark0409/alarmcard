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
 */
@Singleton
class NaverMapBusCrawler @Inject constructor() {

    fun parseStationUrl(input: String): BusStationSearchResult? {
        val s = input.trim()
        if (s.matches(Regex("^\\d{3,10}$"))) {
            return BusStationSearchResult(stationId = s, stationName = "정류장 $s")
        }

        val mobileStationRx = Regex("m\\.map\\.naver\\.com/bus/station")
        if (mobileStationRx.find(s) != null) {
            val stationIdRx = Regex("[?&]stationID=(\\d+)")
            val stationIdMatch = stationIdRx.find(s)
            if (stationIdMatch != null) {
                val stationId = stationIdMatch.groupValues[1]
                return BusStationSearchResult(stationId = stationId, stationName = "정류장 $stationId")
            }
        }

        val busStationRx = Regex("/bus-station/(\\d+)")
        val bsMatch = busStationRx.find(s)
        val nameFromPath: String? = Regex("/p/search/([^/?]+)").find(s)?.groupValues?.getOrNull(1)?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull()
        }
        if (bsMatch != null) {
            val id = bsMatch.groupValues[1]
            return BusStationSearchResult(stationId = id, stationName = nameFromPath ?: "정류장 $id")
        }

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
                listOf("result", "data", "items", "arrivals").flatMap { k ->
                    (root[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
                }
            }
            else -> emptyList()
        }

        if (items.isEmpty()) {
            return BusStationDetail(stationId, "정류장 $stationId", cityCode, emptyList())
        }

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
                lowFloor1 = lowFloor1,
                plateNo1 = buses.getOrNull(0)?.strOrNull("plateNo")
            )
        }

        return BusStationDetail(
            stationId = stationId,
            stationName = stationName,
            cityCode = cityCode,
            arrivals = arrivals
        )
    }

    suspend fun searchStation(keyword: String): List<BusStationSearchResult> {
        parseStationUrl(keyword)?.let { return listOf(it) }
        return emptyList()
    }
}
