package com.jpark.alarmcard.data.crawler

import com.jpark.alarmcard.data.remote.NextData
import com.jpark.alarmcard.data.remote.httpGetString
import com.jpark.alarmcard.data.remote.httpGetStringWithCharset
import com.jpark.alarmcard.domain.model.StockMarket
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import timber.log.Timber
import java.nio.charset.Charset
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
    val currency: String? = null,
    val price: Double? = null,
    val change: Double? = null,
    val changeRate: Double? = null
)

/**
 * 네이버 증권 크롤러.
 *
 * 사용자 입력은 다음 두 형태를 지원합니다:
 *   1) 네이버 금융 종목 URL          : https://finance.naver.com/item/main.naver?code=005930
 *   2) 6자리 종목코드 (국내)          : 005930
 *
 * 위의 URL/코드로 EUC-KR로 서빙되는 finance.naver.com 페이지를 파싱해 종목명/현재가/전일대비를 추출합니다.
 */
@Singleton
class NaverStockCrawler @Inject constructor() {

    /**
     * 카드 리프레시 용. StockCard 의 symbol(6자리 코드) + market 만 있으면
     * finance.naver.com 페이지를 다시 파싱해 최신 값을 반환.
     */
    suspend fun fetchQuote(symbol: String, market: StockMarket): StockQuote {
        // 국내는 finance.naver.com HTML 파싱. 해외는 fallback 으로 m.stock.naver.com 사용.
        return when (market) {
            StockMarket.DOMESTIC -> fetchDomesticQuote(symbol)
            StockMarket.US -> fetchOverseasQuote(symbol)
        }
    }

    /**
     * 사용자 입력을 파싱해서 하나의 후보(검색결과)로 반환.
     * URL 이면 code 추출, 6자리 숫자면 그대로 국내 코드로 취급.
     */
    suspend fun search(keyword: String): List<StockSearchResult> {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1) 국내: URL 또는 6자리 코드
        val code = extractDomesticCode(trimmed)
        if (code != null) {
            return runCatching {
                val q = fetchDomesticQuote(code)
                listOf(
                    StockSearchResult(
                        symbol = code,
                        name = q.name,
                        market = StockMarket.DOMESTIC,
                        currency = "KRW",
                        price = q.price,
                        change = q.change,
                        changeRate = q.changeRate
                    )
                )
            }.onFailure { Timber.w(it, "stock preview failed for domestic $code") }
                .getOrDefault(emptyList())
        }

        // 2) 해외: URL 또는 AAPL.O 같은 심볼(대문자.접미)
        val overseas = extractOverseasSymbol(trimmed)
        if (overseas != null) {
            return runCatching {
                val q = fetchOverseasQuote(overseas)
                listOf(
                    StockSearchResult(
                        symbol = overseas,
                        name = q.name,
                        market = StockMarket.US,
                        currency = q.currency ?: "USD",
                        price = q.price,
                        change = q.change,
                        changeRate = q.changeRate
                    )
                )
            }.onFailure { Timber.w(it, "stock preview failed for overseas $overseas") }
                .getOrDefault(emptyList())
        }

        return emptyList()
    }

    /* ---------------- 국내 (UTF-8 HTML) ---------------- */

    private suspend fun fetchDomesticQuote(code: String): StockQuote {
        val url = "https://finance.naver.com/item/main.naver?code=$code"
        // finance.naver.com 은 현재 <meta charset="utf-8"> 로 서빙되고 있음.
        // OkHttp 기본 (UTF-8) 로 파싱한 뒤, 만약 문자가 깨진 것으로 보이면 EUC-KR fallback.
        val html = httpGetString(
            url = url,
            referer = "https://finance.naver.com/",
            extraHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AlarmCard/1.0"
            )
        )
        val doc = Jsoup.parse(html)


        // 종목명: <div class="wrap_company"><h2><a>삼성전자</a></h2>
        val name = doc.selectFirst(".wrap_company h2 a")?.text()?.trim()
            ?.ifBlank { null }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" - ")?.trim()
            ?: code

        // 현재가: <p class="no_today"> ... <span class="blind">231,000</span>
        val priceText = doc.selectFirst("p.no_today .blind")?.text()
        val price = priceText?.replace(",", "")?.trim()?.toDoubleOrNull()

        // 전일대비 부호 (no_up / no_down / no_upordown)
        val exdayEm = doc.selectFirst("p.no_exday em")
        val sign = when {
            exdayEm?.hasClass("no_down") == true -> -1.0
            exdayEm?.hasClass("no_up") == true -> 1.0
            else -> 0.0
        }
        // no_exday 안의 blind 는 [금액, 등락률] 두 개
        val exBlinds = doc.select("p.no_exday .blind").map { it.text().trim() }
        val change = exBlinds.getOrNull(0)?.replace(",", "")?.toDoubleOrNull()?.let { it * sign }
        val rate = exBlinds.getOrNull(1)?.replace(",", "")?.replace("%", "")?.toDoubleOrNull()
            ?.let { it * sign }

        return StockQuote(
            symbol = code,
            name = name,
            price = price,
            change = change,
            changeRate = rate,
            currency = "KRW"
        )
    }

    /* ---------------- 해외 (기존 __NEXT_DATA__ 방식 유지) ---------------- */

    private suspend fun fetchOverseasQuote(symbol: String): StockQuote {
        val url = "https://m.stock.naver.com/worldstock/stock/$symbol/total"
        val html = httpGetString(url, referer = "https://m.stock.naver.com/")
        return parseNextData(html, symbol)
            ?: parseCssFallback(html, symbol)
            ?: error("Failed to parse overseas quote for $symbol")
    }

    private fun parseNextData(html: String, symbol: String): StockQuote? {
        val root = NextData.extract(html) ?: return null
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
                    (keys.contains("stockName") &&
                        (keys.contains("compareToPreviousClosePrice") ||
                            keys.contains("fluctuationsRatio")))
                if (looksLikeStock) return cur
                cur.values.forEach { stack.addLast(it) }
            } else if (cur is kotlinx.serialization.json.JsonArray) {
                cur.forEach { stack.addLast(it) }
            }
        }
        return null
    }

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

    /* ---------------- 입력 파싱 유틸 ---------------- */

    private fun extractDomesticCode(input: String): String? {
        // 순수 6자리 숫자
        val digits = input.filter { it.isDigit() }
        if (input.matches(Regex("^\\d{6}$"))) return input
        // finance.naver.com/... code=005930
        val m = Regex("[?&]code=([0-9]{6})").find(input)
        if (m != null) return m.groupValues[1]
        // /item/(?:main|sise|coinfo)\.naver 뒤 code=
        if (input.contains("finance.naver.com") && digits.length >= 6) {
            val d6 = Regex("([0-9]{6})").find(input)
            if (d6 != null) return d6.groupValues[1]
        }
        return null
    }

    private fun extractOverseasSymbol(input: String): String? {
        // m.stock.naver.com/worldstock/stock/AAPL.O/total
        val m = Regex("worldstock/stock/([A-Za-z0-9.\\-]+)").find(input)
        if (m != null) return m.groupValues[1].uppercase()
        // 심볼 형태 (AAPL, AAPL.O, TSM.N 등)
        if (input.matches(Regex("^[A-Za-z]{1,6}(\\.[A-Za-z])?$"))) return input.uppercase()
        return null
    }
}

/* ---------- 작은 JSON 헬퍼들 ---------- */

private fun JsonObject.strOrNull(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.doubleOrNull(key: String): Double? =
    this[key]?.let { runCatching { it.jsonPrimitive.content.replace(",", "").toDouble() }.getOrNull() }
