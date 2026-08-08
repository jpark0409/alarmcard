package com.jpark.alarmcard.data.crawler

import com.jpark.alarmcard.data.remote.NextData
import com.jpark.alarmcard.data.remote.httpGetString
import com.jpark.alarmcard.domain.model.StockMarket
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class StockQuote(
    val symbol: String,
    val name: String,
    val price: Double?,
    val change: Double?,
    val changeRate: Double?,
    val currency: String?
)

data class StockSearchResult(
    val symbol: String,   // 국내 6자리 코드 또는 AAPL.O
    val name: String,
    val market: StockMarket,
    val currency: String? = null
)

/**
 * 네이버 증권 모바일 페이지 크롤러.
 * - 국내: https://m.stock.naver.com/domestic/stock/{code}/total
 * - 해외(미국): https://m.stock.naver.com/worldstock/stock/{symbol}/total (예: AAPL.O)
 *
 * 파싱 전략:
 *  1) __NEXT_DATA__ JSON에서 stockInfo 계열 필드 탐색
 *  2) 실패 시 헤더의 CSS 셀렉터에서 가격 텍스트 fallback
 */
@Singleton
class NaverStockCrawler @Inject constructor() {

    suspend fun fetchQuote(symbol: String, market: StockMarket): StockQuote {
        val url = buildUrl(symbol, market)
        val html = httpGetString(url, referer = "https://m.stock.naver.com/")
        return parseNextData(html, symbol)
            ?: parseCssFallback(html, symbol)
            ?: error("Failed to parse quote for $symbol")
    }

    /** 종목 검색: 이름/티커로 검색해서 상위 후보 반환 */
    suspend fun search(keyword: String): List<StockSearchResult> {
        // 네이버 증권 검색 JSON 엔드포인트 (모바일)
        val url = "https://m.stock.naver.com/api/search/searchListPage?keyword=" +
            java.net.URLEncoder.encode(keyword, "UTF-8")
        val body = httpGetString(url, referer = "https://m.stock.naver.com/")
        return runCatching {
            val root = NextData.parseObject(body).jsonObject
            val list = mutableListOf<StockSearchResult>()

            // 국내 주식
            (root["stocks"] ?: root["domesticStocks"])?.let { arr ->
                arr.jsonArrayOrEmpty().forEach { item ->
                    val obj = item.jsonObject
                    val code = obj.strOrNull("itemCode") ?: obj.strOrNull("code") ?: return@forEach
                    val name = obj.strOrNull("stockName") ?: obj.strOrNull("name") ?: code
                    list += StockSearchResult(code, name, StockMarket.DOMESTIC, "KRW")
                }
            }
            // 해외 주식
            (root["worldStocks"] ?: root["overseasStocks"])?.let { arr ->
                arr.jsonArrayOrEmpty().forEach { item ->
                    val obj = item.jsonObject
                    val code = obj.strOrNull("reutersCode") ?: obj.strOrNull("symbolCode")
                        ?: obj.strOrNull("itemCode") ?: return@forEach
                    val name = obj.strOrNull("stockName") ?: obj.strOrNull("name") ?: code
                    val curr = obj.strOrNull("currency") ?: "USD"
                    list += StockSearchResult(code, name, StockMarket.US, curr)
                }
            }
            list
        }.onFailure { Timber.w(it, "stock search parse failed") }.getOrDefault(emptyList())
    }

    private fun buildUrl(symbol: String, market: StockMarket): String = when (market) {
        StockMarket.DOMESTIC -> "https://m.stock.naver.com/domestic/stock/$symbol/total"
        StockMarket.US -> "https://m.stock.naver.com/worldstock/stock/$symbol/total"
    }

    /** __NEXT_DATA__ JSON에서 값 찾기 (구조가 자주 변하므로 재귀 탐색). */
    private fun parseNextData(html: String, symbol: String): StockQuote? {
        val root = NextData.extract(html) ?: return null

        // 재귀적으로 관심 필드가 있는 오브젝트 탐색
        val stockObj = findStockObject(root) ?: return null

        val price = stockObj.doubleOrNull("closePrice")
            ?: stockObj.doubleOrNull("nowPrice")
            ?: stockObj.doubleOrNull("currentPrice")
            ?: stockObj.doubleOrNull("price")

        val change = stockObj.doubleOrNull("compareToPreviousClosePrice")
            ?: stockObj.doubleOrNull("compareToPreviousPrice")
            ?: stockObj.doubleOrNull("change")

        val rate = stockObj.doubleOrNull("fluctuationsRatio")
            ?: stockObj.doubleOrNull("changeRate")

        val name = stockObj.strOrNull("stockName")
            ?: stockObj.strOrNull("name")
            ?: symbol
        val currency = stockObj.strOrNull("currency")

        if (price == null) return null
        return StockQuote(symbol, name, price, change, rate, currency)
    }

    /** JSON 트리에서 stock quote 관련 오브젝트를 찾음. */
    private fun findStockObject(root: JsonElement): JsonObject? {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur is JsonObject) {
                val keys = cur.keys
                val looksLikeStock = keys.contains("closePrice") ||
                    keys.contains("nowPrice") ||
                    keys.contains("currentPrice") ||
                    (keys.contains("stockName") && (keys.contains("compareToPreviousClosePrice") || keys.contains("fluctuationsRatio")))
                if (looksLikeStock) return cur
                cur.values.forEach { stack.addLast(it) }
            } else if (cur is kotlinx.serialization.json.JsonArray) {
                cur.forEach { stack.addLast(it) }
            }
        }
        return null
    }

    /** __NEXT_DATA__ 실패 시 최소한의 CSS 셀렉터 fallback. */
    private fun parseCssFallback(html: String, symbol: String): StockQuote? {
        val doc = Jsoup.parse(html)
        val priceText = doc.select("strong[class*=price]").firstOrNull()?.text()
            ?: doc.select("[class*=GraphMain_price]").firstOrNull()?.text()
        val price = priceText?.replace(",", "")?.toDoubleOrNull() ?: return null
        val name = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.title().substringBefore('|').trim()
            ?: symbol
        return StockQuote(symbol, name, price, null, null, null)
    }
}

/* ---------- 작은 JSON 헬퍼들 ---------- */

private fun JsonObject.strOrNull(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.doubleOrNull(key: String): Double? =
    this[key]?.let { runCatching { it.jsonPrimitive.content.replace(",", "").toDouble() }.getOrNull() }

private fun JsonElement.jsonArrayOrEmpty(): kotlinx.serialization.json.JsonArray =
    (this as? kotlinx.serialization.json.JsonArray) ?: kotlinx.serialization.json.JsonArray(emptyList())
