package com.jpark.alarmcard.data

import com.jpark.alarmcard.data.crawler.NaverFxCrawler
import com.jpark.alarmcard.data.crawler.NaverMapBusCrawler
import com.jpark.alarmcard.data.crawler.NaverStockCrawler
import com.jpark.alarmcard.data.local.CardDao
import com.jpark.alarmcard.data.local.toDomain
import com.jpark.alarmcard.data.local.toEntity
import com.jpark.alarmcard.domain.model.BusCard
import com.jpark.alarmcard.domain.model.Card
import com.jpark.alarmcard.domain.model.FxCard
import com.jpark.alarmcard.domain.model.StockCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepository @Inject constructor(
    private val dao: CardDao,
    private val stockCrawler: NaverStockCrawler,
    private val fxCrawler: NaverFxCrawler,
    private val busCrawler: NaverMapBusCrawler
) {
    fun observeCards(): Flow<List<Card>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addStock(card: StockCard): String {
        val order = dao.maxOrder() + 1
        val id = card.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(card.copy(id = id, order = order).toEntity())
        // 최초 값 가져오기
        refreshOne(id)
        return id
    }

    suspend fun addBus(card: BusCard): String {
        val order = dao.maxOrder() + 1
        val id = card.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(card.copy(id = id, order = order).toEntity())
        refreshOne(id)
        return id
    }

    suspend fun addFx(card: FxCard): String {
        val order = dao.maxOrder() + 1
        val id = card.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(card.copy(id = id, order = order).toEntity())
        refreshOne(id)
        return id
    }

    suspend fun remove(id: String) = dao.deleteById(id)

    suspend fun reorder(ids: List<String>) {
        val entities = dao.getAll().associateBy { it.id }
        ids.forEachIndexed { idx, id ->
            entities[id]?.copy(orderIdx = idx)?.let { dao.update(it) }
        }
    }

    /** 모든 카드를 병렬로 새로고침. 개별 실패는 lastError에 저장. */
    suspend fun refreshAll() = coroutineScope {
        val cards = dao.getAll().map { it.toDomain() }
        cards.map { card ->
            async { runCatching { refreshCard(card) } }
        }.forEach { it.await() }
    }

    suspend fun refreshOne(id: String) {
        val entity = dao.getById(id) ?: return
        runCatching { refreshCard(entity.toDomain()) }
    }

    private suspend fun refreshCard(card: Card) {
        val now = System.currentTimeMillis()
        try {
            val updated: Card = when (card) {
                is StockCard -> {
                    val q = stockCrawler.fetchQuote(card.symbol, card.market)
                    card.copy(
                        name = q.name.ifBlank { card.name },
                        price = q.price,
                        change = q.change,
                        changeRate = q.changeRate,
                        currency = q.currency ?: card.currency,
                        updatedAt = now,
                        lastError = null
                    )
                }
                is FxCard -> {
                    val q = fxCrawler.fetchQuote(card.code)
                    card.copy(
                        rate = q.rate,
                        change = q.change,
                        changeRate = q.changeRate,
                        updatedAt = now,
                        lastError = null
                    )
                }
                is BusCard -> {
                    val detail = busCrawler.fetchArrivals(card.stationId, card.cityCode)
                    val filtered = if (card.filterRouteIds.isEmpty()) detail.arrivals
                    else detail.arrivals.filter { it.routeId in card.filterRouteIds }
                    card.copy(
                        stationName = detail.stationName.ifBlank { card.stationName },
                        arrivals = filtered,
                        updatedAt = now,
                        lastError = null
                    )
                }
            }
            dao.upsert(updated.toEntity())
        } catch (t: Throwable) {
            Timber.w(t, "refresh failed for ${card.id}")
            val failed: Card = when (card) {
                is StockCard -> card.copy(lastError = t.message ?: t::class.simpleName ?: "error")
                is FxCard -> card.copy(lastError = t.message ?: t::class.simpleName ?: "error")
                is BusCard -> card.copy(lastError = t.message ?: t::class.simpleName ?: "error")
            }
            dao.upsert(failed.toEntity())
        }
    }
}
