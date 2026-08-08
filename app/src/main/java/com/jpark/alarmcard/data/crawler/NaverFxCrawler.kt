package com.jpark.alarmcard.data.crawler

import com.jpark.alarmcard.data.remote.NextData
import com.jpark.alarmcard.data.remote.arrayOrEmpty
import com.jpark.alarmcard.data.remote.doubleOrNull
import com.jpark.alarmcard.data.remote.httpGetString
import com.jpark.alarmcard.data.remote.strOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class FxQuote(
    val code: String,   // ex) FX_USDKRW
    val name: String,
    val rate: Double?,
    val change: Double?,
    val changeRate: Double?
)

/**
 * 네이버 증권 환율 페이지 크롤러.
 * 예: https://m.stock.naver.com/marketindex/exchange/FX_USDKRW
 * 지원 프리셋(추가는 자유): FX_USDKRW, FX_JPYKRW, FX_EURKRW, FX_CNYKRW, FX_GBPKRW ...
 */
@Singleton
class NaverFxCrawler @Inject constructor() {

    suspend fun fetchQuote(code: String): FxQuote {
        val url = "https://m.stock.naver.com/marketindex/exchange/$code"
        val html = httpGetString(url, referer = "https://m.stock.naver.com/")
        return parseNextData(html, code)
            ?: parseCssFallback(html, code)
            ?: error("Failed to parse fx for $code")
    }

    /** 지원 환율 리스트를 페이지에서 긁어와서 반환. (카드 추가 화면에서 사용) */
    suspend fun listAvailable(): List<FxQuote> {
        val url = "https://m.stock.naver.com/marketindex/home/exchangeRate/exchange"
        val html = runCatching {
            httpGetString(url, referer = "https://m.stock.naver.com/")
        }.getOrElse { return DEFAULT_PRESETS }

        val root = NextData.extract(html) ?: return DEFAULT_PRESETS
        val list = mutableListOf<FxQuote>()
        collectFxItems(root, list)
        return list.ifEmpty { DEFAULT_PRESETS }
    }

    private fun parseNextData(html: String, code: String): FxQuote? {
        val root = NextData.extract(html) ?: return null
        val fxObj = findFxObject(root, code) ?: return null
        val rate = fxObj.doubleOrNull("closePrice")
            ?: fxObj.doubleOrNull("nowPrice")
            ?: fxObj.doubleOrNull("currentPrice")
            ?: fxObj.doubleOrNull("basePrice")
        val change = fxObj.doubleOrNull("compareToPreviousClosePrice")
            ?: fxObj.doubleOrNull("compareToPreviousPrice")
        val rateChg = fxObj.doubleOrNull("fluctuationsRatio")
            ?: fxObj.doubleOrNull("changeRate")
        val name = fxObj.strOrNull("stockName")
            ?: fxObj.strOrNull("name")
            ?: code
        if (rate == null) return null
        return FxQuote(code, name, rate, change, rateChg)
    }

    private fun findFxObject(root: JsonElement, code: String): JsonObject? {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        var fallback: JsonObject? = null
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur is JsonObject) {
                val ks = cur.keys
                val hasPrice = ks.contains("closePrice") || ks.contains("nowPrice") ||
                    ks.contains("currentPrice") || ks.contains("basePrice")
                if (hasPrice) {
                    val objCode = cur.strOrNull("reutersCode") ?: cur.strOrNull("symbolCode")
                    if (objCode == code) return cur
                    if (fallback == null) fallback = cur
                }
                cur.values.forEach { stack.addLast(it) }
            } else if (cur is kotlinx.serialization.json.JsonArray) {
                cur.forEach { stack.addLast(it) }
            }
        }
        return fallback
    }

    private fun collectFxItems(root: JsonElement, out: MutableList<FxQuote>) {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur is JsonObject) {
                val code = cur.strOrNull("reutersCode") ?: cur.strOrNull("symbolCode")
                val rate = cur.doubleOrNull("closePrice") ?: cur.doubleOrNull("basePrice")
                if (code != null && code.startsWith("FX_") && rate != null) {
                    val name = cur.strOrNull("stockName") ?: code
                    val chg = cur.doubleOrNull("compareToPreviousClosePrice")
                    val chgR = cur.doubleOrNull("fluctuationsRatio")
                    if (out.none { it.code == code }) {
                        out += FxQuote(code, name, rate, chg, chgR)
                    }
                }
                cur.values.forEach { stack.addLast(it) }
            } else if (cur is kotlinx.serialization.json.JsonArray) {
                cur.forEach { stack.addLast(it) }
            }
        }
    }

    private fun parseCssFallback(html: String, code: String): FxQuote? {
        val doc = Jsoup.parse(html)
        val priceText = doc.select("strong[class*=price], [class*=GraphMain_price]")
            .firstOrNull()?.text()
        val rate = priceText?.replace(",", "")?.toDoubleOrNull() ?: return null
        val name = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: code
        Timber.d("Fx CSS fallback used for $code")
        return FxQuote(code, name, rate, null, null)
    }

    companion object {
        val DEFAULT_PRESETS = listOf(
            FxQuote("FX_USDKRW", "미국 (USD/KRW)", null, null, null),
            FxQuote("FX_JPYKRW", "일본 (JPY/KRW)", null, null, null),
            FxQuote("FX_EURKRW", "유럽 (EUR/KRW)", null, null, null),
            FxQuote("FX_CNYKRW", "중국 (CNY/KRW)", null, null, null),
            FxQuote("FX_GBPKRW", "영국 (GBP/KRW)", null, null, null)
        )
    }
}
