package com.jpark.alarmcard.data.crawler

import com.jpark.alarmcard.data.remote.httpGetString
import com.jpark.alarmcard.domain.model.StockMarket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    val symbol: String,
    val name: String,
    val market: StockMarket,
    val currency: String? = null,
    val price: Double? = null,
    val change: Double? = null,
    val changeRate: Double? = null,
    val exchange: String? = null
)

/**
 * Yahoo Finance API Crawler.
 */
@Singleton
class YahooFinanceCrawler @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "application/json"
    )

    suspend fun fetchQuote(symbol: String, market: StockMarket): StockQuote {
        // v1/quote is 404, use v8/chart which provides similar info in 'meta'
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=1d"
        val response = httpGetString(url, extraHeaders = commonHeaders)
        
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val result = root["chart"]?.jsonObject?.get("result")?.jsonArray?.get(0)?.jsonObject
                ?: error("No result found for symbol: $symbol")
            val meta = result["meta"]?.jsonObject ?: error("No meta found for $symbol")

            val name = meta["longName"]?.jsonPrimitive?.content 
                ?: meta["shortName"]?.jsonPrimitive?.content 
                ?: symbol
            val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull
            val prevClose = meta["chartPreviousClose"]?.jsonPrimitive?.doubleOrNull 
                ?: meta["previousClose"]?.jsonPrimitive?.doubleOrNull

            val change = if (price != null && prevClose != null) price - prevClose else null
            val changeRate = if (change != null && prevClose != null && prevClose != 0.0) (change / prevClose) * 100.0 else null
            val currency = meta["currency"]?.jsonPrimitive?.content

            StockQuote(symbol, name, price, change, changeRate, currency)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch quote for $symbol")
            throw e
        }
    }

    suspend fun search(keyword: String): List<StockSearchResult> {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return emptyList()

        // Yahoo search API often fails (400) with non-ASCII characters like Korean.
        // We need to encode the query properly.
        val encodedQuery = java.net.URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://query1.finance.yahoo.com/v1/finance/search?q=$encodedQuery&quotesCount=10&newsCount=0"
        
        return try {
            val response = httpGetString(url, extraHeaders = commonHeaders)
            val root = json.parseToJsonElement(response).jsonObject
            val quotes = root["quotes"]?.jsonArray ?: return emptyList()

            quotes.mapNotNull { element ->
                val obj = element.jsonObject
                val symbol = obj["symbol"]?.jsonPrimitive?.content ?: return@mapNotNull null
                
                val exchange = obj["exchange"]?.jsonPrimitive?.content
                val isKorean = exchange == "KSC" || exchange == "KOE" || symbol.endsWith(".KS") || symbol.endsWith(".KQ")
                
                val name = if (isKorean && !trimmed.all { it.code < 128 }) {
                    // If searching with Korean and result is a Korean stock, 
                    // prefer using the search keyword as name if the result name is just English.
                    trimmed
                } else {
                    obj["longname"]?.jsonPrimitive?.content 
                        ?: obj["shortname"]?.jsonPrimitive?.content 
                        ?: symbol
                }
                
                val market = if (isKorean) StockMarket.DOMESTIC else StockMarket.US

                StockSearchResult(
                    symbol = symbol,
                    name = name,
                    market = market
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to search stocks for $keyword")
            
            // Fallback: If search fails (likely due to Korean query), 
            // and the keyword looks like a symbol (e.g. 005930.KS), try to return it directly.
            if (trimmed.contains(".") || trimmed.all { it.isDigit() }) {
                val symbol = if (trimmed.all { it.isDigit() }) "$trimmed.KS" else trimmed
                return listOf(
                    StockSearchResult(
                        symbol = symbol,
                        name = trimmed,
                        market = if (symbol.endsWith(".KS") || symbol.endsWith(".KQ")) StockMarket.DOMESTIC else StockMarket.US
                    )
                )
            }
            emptyList()
        }
    }
}
