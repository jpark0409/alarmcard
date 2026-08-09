package com.jpark.alarmcard.domain.model

/**
 * 카드 타입 공통 인터페이스.
 * id: 로컬 유일 식별자 (UUID)
 * order: 목록 정렬 순서 (오름차순)
 * updatedAt: 마지막 성공 갱신 시각(epoch millis, 0 = 미갱신)
 * lastError: 마지막 갱신 오류 메시지(null = 정상)
 */
sealed interface Card {
    val id: String
    val order: Int
    val updatedAt: Long
    val lastError: String?
}

/** 주식 시장 구분. 네이버 증권의 URL 규칙에 매핑됨. */
enum class StockMarket { DOMESTIC, US }

data class StockCard(
    override val id: String,
    override val order: Int,
    override val updatedAt: Long,
    override val lastError: String?,
    val symbol: String,          // 국내: 6자리 코드 / 해외: AAPL.O 같은 심볼
    val market: StockMarket,
    val name: String,
    val price: Double? = null,
    val change: Double? = null,
    val changeRate: Double? = null,
    val currency: String? = null, // KRW / USD 등
    val alarmEnabled: Boolean = false,
    val alarmPriceThreshold: Double? = null,
    val alarmRateThreshold: Double? = null,
    val alarmLastFiredAt: Long = 0L
) : Card

data class BusArrival(
    val routeId: String,
    val routeNo: String,
    val eta1Sec: Int?,   // 첫 번째 도착까지 남은 초
    val eta2Sec: Int?,   // 두 번째 도착까지 남은 초
    val remainStops1: Int? = null,
    val remainStops2: Int? = null,
    val lowFloor1: Boolean = false
)

data class BusCard(
    override val id: String,
    override val order: Int,
    override val updatedAt: Long,
    override val lastError: String?,
    val stationId: String,
    val stationName: String,
    val cityCode: String? = null, // 서울/경기 구분용 (선택)
    /** 사용자가 선택한 노선 필터. 비어있으면 전체 노선 표시 */
    val filterRouteIds: List<String> = emptyList(),
    val arrivals: List<BusArrival> = emptyList(),
    /** n분 전 도착 알림 활성화 여부 */
    val alarmEnabled: Boolean = false,
    /** 몇 분 전에 알림을 울릴지 (예: 3 → 3분 이내 도착 시) */
    val alarmMinutesBefore: Int = 3,
    /** 중복 알림 방지용 마지막 발송 시각 */
    val alarmLastFiredAt: Long = 0L
) : Card

data class FxCard(
    override val id: String,
    override val order: Int,
    override val updatedAt: Long,
    override val lastError: String?,
    val code: String,       // FX_USDKRW 형태
    val base: String,       // USD
    val quote: String,      // KRW
    val rate: Double? = null,
    val change: Double? = null,
    val changeRate: Double? = null
) : Card
