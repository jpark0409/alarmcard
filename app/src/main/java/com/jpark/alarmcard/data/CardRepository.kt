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

    suspend fun getCardById(id: String) = dao.getById(id)

    /** 버스카드 알람 설정 갱신 */
    suspend fun setBusAlarm(id: String, enabled: Boolean, minutesBefore: Int) {
        val entity = dao.getById(id) ?: return
        if (entity.type != com.jpark.alarmcard.data.local.CardEntity.TYPE_BUS) return
        dao.upsert(
            entity.copy(
                alarmEnabled = enabled,
                alarmMinutesBefore = minutesBefore,
                // 활성화 시 이전 발송 이력 리셋
                alarmLastFiredAt = if (enabled) 0L else entity.alarmLastFiredAt
            )
        )
    }

    /** 알람이 켜져있는 버스카드가 하나라도 있는지 (Worker 스케줄러가 사용) */
    suspend fun hasActiveBusAlarm(): Boolean =
        dao.getAll().any {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_BUS && it.alarmEnabled
        }

    /** 주식카드 알람 설정 갱신 */
    suspend fun setStockAlarm(id: String, enabled: Boolean, price: Double?, rate: Double?) {
        val entity = dao.getById(id) ?: return
        if (entity.type != com.jpark.alarmcard.data.local.CardEntity.TYPE_STOCK) return
        dao.upsert(
            entity.copy(
                alarmEnabled = enabled,
                alarmPriceThreshold = price,
                alarmRateThreshold = rate,
                alarmLastFiredAt = if (enabled) 0L else entity.alarmLastFiredAt
            )
        )
    }

    suspend fun hasActiveStockAlarm(): Boolean =
        dao.getAll().any {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_STOCK && it.alarmEnabled
        }

    suspend fun refreshStockAlarmsAndSelectFireable(): List<StockCard> {
        val entities = dao.getAll().filter {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_STOCK && it.alarmEnabled
        }
        // 새로고침
        entities.forEach { runCatching { refreshCard(it.toDomain()) } }

        val nowMs = System.currentTimeMillis()
        val fire = mutableListOf<StockCard>()
        for (e in dao.getAll()) {
            if (e.type != com.jpark.alarmcard.data.local.CardEntity.TYPE_STOCK || !e.alarmEnabled) continue
            val sc = e.toDomain() as StockCard
            val currentPrice = sc.price ?: continue

            var isHit = false
            // 1) 가격 임계점 (절대값 도달)
            sc.alarmPriceThreshold?.let { threshold ->
                // 특정 가격 도달 시 (보통 상하방 구분 없이 근접/통과 시 알려줌)
                // 여기서는 간단히 마지막 발송 시점 대비 현재 상태만 체크
                isHit = true 
            }
            // 2) 퍼센티지 임계점 (변동률 절대값)
            sc.alarmRateThreshold?.let { threshold ->
                if (kotlin.math.abs(sc.changeRate ?: 0.0) >= threshold) {
                    isHit = true
                }
            }

            if (isHit) {
                // 중복 방지: 1시간(또는 30분) 이내 동일 종목 알림 방지
                if (nowMs - sc.alarmLastFiredAt > 30 * 60_000L) {
                    dao.upsert(e.copy(alarmLastFiredAt = nowMs))
                    fire += sc
                }
            }
        }
        return fire
    }

    /**
>>>>>>> +++++++ REPLACE

     * 활성 버스카드들을 새로고침한 뒤, 알림을 발송해야 할 카드 목록을 반환.
     * (실제 발송은 상위 레이어에서 수행)
     */
    suspend fun refreshBusAlarmsAndSelectFireable(): List<com.jpark.alarmcard.domain.model.BusCard> {
        val entities = dao.getAll().filter {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_BUS && it.alarmEnabled
        }
        // 새로고침
        entities.forEach { runCatching { refreshCard(it.toDomain()) } }
        // 최신 상태로 다시 읽어 임계치 만족 여부 검사
        val nowMs = System.currentTimeMillis()
        val fire = mutableListOf<com.jpark.alarmcard.domain.model.BusCard>()
        for (e in dao.getAll()) {
            if (e.type != com.jpark.alarmcard.data.local.CardEntity.TYPE_BUS || !e.alarmEnabled) continue
            val bc = e.toDomain() as com.jpark.alarmcard.domain.model.BusCard
            // 필터된 노선 중에 eta1Sec <= alarmMinutesBefore*60 인 것이 있는지
            val threshold = bc.alarmMinutesBefore * 60
            val hit = bc.arrivals.firstOrNull { a -> a.eta1Sec != null && a.eta1Sec in 0..threshold }
            if (hit != null) {
                // 중복 방지: 마지막 알림이 최근 5분 이내면 스킵
                if (nowMs - bc.alarmLastFiredAt > 5 * 60_000L) {
                    // 발송 기록
                    dao.upsert(e.copy(alarmLastFiredAt = nowMs))
                    fire += bc
                }
            }
        }
        return fire
    }

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

    suspend fun exportToJson(): String {
        val entities = dao.getAll()
        val json = kotlinx.serialization.json.Json { prettyPrint = true }
        return json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.jpark.alarmcard.data.local.CardEntity.serializer()), entities)
    }

    suspend fun importFromJson(jsonStr: String) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val entities = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.jpark.alarmcard.data.local.CardEntity.serializer()), jsonStr)
        dao.upsertAll(entities)
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
