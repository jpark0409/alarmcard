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

    suspend fun fetchQuote(symbol: String, market: StockMarket): StockQuote {
        val url = "https://query1.finance.yahoo.com/v1/finance/quote?symbols=$symbol"
        val response = httpGetString(url)
        
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val result = root["quoteResponse"]?.jsonObject?.get("result")?.jsonArray?.get(0)?.jsonObject
                ?: error("No result found for symbol: $symbol")

            val name = result["longName"]?.jsonPrimitive?.content 
                ?: result["shortName"]?.jsonPrimitive?.content 
                ?: symbol
            val price = result["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull
            val change = result["regularMarketChange"]?.jsonPrimitive?.doubleOrNull
            val changeRate = result["regularMarketChangePercent"]?.jsonPrimitive?.doubleOrNull
            val currency = result["currency"]?.jsonPrimitive?.content

            StockQuote(symbol, name, price, change, changeRate, currency)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch quote for $symbol")
            throw e
        }
    }

    suspend fun search(keyword: String): List<StockSearchResult> {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return emptyList()

        val url = "https://query1.finance.yahoo.com/v1/finance/search?q=${trimmed}"
        val response = httpGetString(url)

        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val quotes = root["quotes"]?.jsonArray ?: return emptyList()

            quotes.mapNotNull { element ->
                val obj = element.jsonObject
                val symbol = obj["symbol"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["longname"]?.jsonPrimitive?.content 
                    ?: obj["shortname"]?.jsonPrimitive?.content 
                    ?: symbol
                val exchange = obj["exchange"]?.jsonPrimitive?.content
                
                // 야후 파이낸스 exchange 코드를 보고 국내/해외 구분 (간단하게)
                val market = if (exchange == "KSC" || exchange == "KOE" || symbol.endsWith(".KS") || symbol.endsWith(".KQ")) {
                    StockMarket.DOMESTIC
                } else {
                    StockMarket.US
                }

                StockSearchResult(
                    symbol = symbol,
                    name = name,
                    market = market,
                    exchange = exchange
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to search stocks for $keyword")
            emptyList()
        }
    }
}
