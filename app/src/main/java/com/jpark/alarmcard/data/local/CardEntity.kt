package com.jpark.alarmcard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jpark.alarmcard.domain.model.BusArrival
import com.jpark.alarmcard.domain.model.BusCard
import com.jpark.alarmcard.domain.model.Card
import com.jpark.alarmcard.domain.model.FxCard
import com.jpark.alarmcard.domain.model.StockCard
import com.jpark.alarmcard.domain.model.StockMarket

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val type: String,            // "STOCK" | "BUS" | "FX"
    val orderIdx: Int,
    val updatedAt: Long,
    val lastError: String?,

    // Stock
    val symbol: String? = null,
    val market: String? = null,  // DOMESTIC/US
    val name: String? = null,
    val price: Double? = null,
    val change: Double? = null,
    val changeRate: Double? = null,
    val currency: String? = null,

    // Bus
    val stationId: String? = null,
    val stationName: String? = null,
    val cityCode: String? = null,
    val filterRouteIdsCsv: String? = null,
    val arrivalsJson: String? = null,

    // Fx
    val code: String? = null,
    val base: String? = null,
    val quote: String? = null,
    val rate: Double? = null
) {
    companion object {
        const val TYPE_STOCK = "STOCK"
        const val TYPE_BUS = "BUS"
        const val TYPE_FX = "FX"
    }
}

fun Card.toEntity(): CardEntity = when (this) {
    is StockCard -> CardEntity(
        id = id, type = CardEntity.TYPE_STOCK, orderIdx = order,
        updatedAt = updatedAt, lastError = lastError,
        symbol = symbol, market = market.name, name = name,
        price = price, change = change, changeRate = changeRate,
        currency = currency
    )
    is BusCard -> CardEntity(
        id = id, type = CardEntity.TYPE_BUS, orderIdx = order,
        updatedAt = updatedAt, lastError = lastError,
        stationId = stationId, stationName = stationName, cityCode = cityCode,
        filterRouteIdsCsv = filterRouteIds.joinToString(","),
        arrivalsJson = ArrivalCodec.encode(arrivals)
    )
    is FxCard -> CardEntity(
        id = id, type = CardEntity.TYPE_FX, orderIdx = order,
        updatedAt = updatedAt, lastError = lastError,
        code = code, base = base, quote = quote,
        rate = rate, change = change, changeRate = changeRate
    )
}

fun CardEntity.toDomain(): Card = when (type) {
    CardEntity.TYPE_STOCK -> StockCard(
        id = id, order = orderIdx, updatedAt = updatedAt, lastError = lastError,
        symbol = symbol.orEmpty(),
        market = StockMarket.valueOf(market ?: StockMarket.DOMESTIC.name),
        name = name.orEmpty(),
        price = price, change = change, changeRate = changeRate,
        currency = currency
    )
    CardEntity.TYPE_BUS -> BusCard(
        id = id, order = orderIdx, updatedAt = updatedAt, lastError = lastError,
        stationId = stationId.orEmpty(), stationName = stationName.orEmpty(),
        cityCode = cityCode,
        filterRouteIds = filterRouteIdsCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        arrivals = arrivalsJson?.let { ArrivalCodec.decode(it) } ?: emptyList()
    )
    CardEntity.TYPE_FX -> FxCard(
        id = id, order = orderIdx, updatedAt = updatedAt, lastError = lastError,
        code = code.orEmpty(), base = base.orEmpty(), quote = quote.orEmpty(),
        rate = rate, change = change, changeRate = changeRate
    )
    else -> error("Unknown card type: $type")
}

/** BusArrival 리스트 JSON 직렬화 helper (kotlinx.serialization 활용) */
object ArrivalCodec {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    private data class Dto(
        val routeId: String,
        val routeNo: String,
        val eta1Sec: Int? = null,
        val eta2Sec: Int? = null,
        val remainStops1: Int? = null,
        val remainStops2: Int? = null,
        val lowFloor1: Boolean = false
    )

    fun encode(list: List<BusArrival>): String {
        val dtos = list.map { Dto(it.routeId, it.routeNo, it.eta1Sec, it.eta2Sec, it.remainStops1, it.remainStops2, it.lowFloor1) }
        return json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Dto.serializer()), dtos)
    }

    fun decode(str: String): List<BusArrival> = try {
        val dtos = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Dto.serializer()), str)
        dtos.map { BusArrival(it.routeId, it.routeNo, it.eta1Sec, it.eta2Sec, it.remainStops1, it.remainStops2, it.lowFloor1) }
    } catch (t: Throwable) { emptyList() }
}
