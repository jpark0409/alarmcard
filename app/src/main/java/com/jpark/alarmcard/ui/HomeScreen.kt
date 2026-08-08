package com.jpark.alarmcard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpark.alarmcard.domain.model.BusCard
import com.jpark.alarmcard.domain.model.Card as DomainCard
import com.jpark.alarmcard.domain.model.FxCard
import com.jpark.alarmcard.domain.model.StockCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onAdd: () -> Unit
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val refreshing by vm.isRefreshing.collectAsStateWithLifecycle()

    // 화면이 active(RESUMED) 될 때 자동 새로고침
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onScreenResumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AlarmCard") },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, contentDescription = "추가")
                    }
                    IconButton(onClick = { vm.refresh() }, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                        }
                    }
                }
            )
        }
    ) { inner ->
        if (cards.isEmpty()) {
            EmptyState(inner)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cards, key = { it.id }) { c ->
                    CardItem(c, onDelete = { vm.deleteCard(c.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(pv: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(pv),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "우측 상단 + 로 카드를 추가하세요\n주식 / 버스 / 환율",
            color = Color.Gray
        )
    }
}

@Composable
fun CardItem(card: DomainCard, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TypeBadge(card)
                Spacer(Modifier.size(8.dp))
                Text(cardTitle(card), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제")
                }
            }
            Spacer(Modifier.height(6.dp))
            when (card) {
                is StockCard -> StockBody(card)
                is FxCard -> FxBody(card)
                is BusCard -> BusBody(card)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = footerText(card),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun TypeBadge(c: DomainCard) {
    val (label, color) = when (c) {
        is StockCard -> "주식" to Color(0xFF0F62FE)
        is FxCard -> "환율" to Color(0xFF10893E)
        is BusCard -> "버스" to Color(0xFFEB6100)
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun StockBody(c: StockCard) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = c.price?.let { fmtPrice(it, c.currency) } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(10.dp))
        val chgColor = c.change?.let { if (it >= 0) Color(0xFFD32F2F) else Color(0xFF1976D2) } ?: Color.Gray
        Text(
            text = buildString {
                if (c.change != null) append((if (c.change >= 0) "▲" else "▼") + " " + fmtPrice(kotlin.math.abs(c.change), c.currency))
                if (c.changeRate != null) append("  " + String.format("%.2f%%", c.changeRate))
                if (c.change == null && c.changeRate == null) append("변동 정보 없음")
            },
            color = chgColor,
            fontSize = 13.sp
        )
    }
    Text(c.symbol, fontSize = 12.sp, color = Color.Gray)
}

@Composable
private fun FxBody(c: FxCard) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = c.rate?.let { String.format("%,.2f", it) } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(10.dp))
        val chgColor = c.change?.let { if (it >= 0) Color(0xFFD32F2F) else Color(0xFF1976D2) } ?: Color.Gray
        Text(
            text = buildString {
                if (c.change != null) append((if (c.change >= 0) "▲" else "▼") + " " + String.format("%.2f", kotlin.math.abs(c.change)))
                if (c.changeRate != null) append("  " + String.format("%.2f%%", c.changeRate))
            },
            color = chgColor,
            fontSize = 13.sp
        )
    }
    Text("${c.base} → ${c.quote}", fontSize = 12.sp, color = Color.Gray)
}

@Composable
private fun BusBody(c: BusCard) {
    if (c.arrivals.isEmpty()) {
        Text("도착 정보 없음", color = Color.Gray)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        c.arrivals.take(6).forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.routeNo, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                Text(fmtEta(a.eta1Sec, a.remainStops1), fontSize = 13.sp)
                if (a.eta2Sec != null) {
                    Text(
                        "  |  ${fmtEta(a.eta2Sec, a.remainStops2)}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

/* ---- helpers ---- */

private fun fmtEta(sec: Int?, stops: Int?): String {
    if (sec == null) return "정보없음"
    val m = sec / 60
    val s = sec % 60
    val stopPart = stops?.let { "  ($it 정거장)" } ?: ""
    return if (m > 0) "${m}분 ${s}초$stopPart" else "${s}초$stopPart"
}

private fun fmtPrice(v: Double, currency: String?): String =
    when (currency) {
        "KRW", null -> "%,.0f원".format(v)
        "USD" -> "$%,.2f".format(v)
        else -> "%,.2f %s".format(v, currency)
    }

private fun cardTitle(c: DomainCard): String = when (c) {
    is StockCard -> c.name.ifBlank { c.symbol }
    is FxCard -> c.code.removePrefix("FX_")
    is BusCard -> c.stationName.ifBlank { c.stationId }
}

private fun footerText(c: DomainCard): String {
    val time = if (c.updatedAt == 0L) "아직 갱신 전" else "갱신 " + SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(c.updatedAt))
    return c.lastError?.let { "⚠ $it · $time" } ?: time
}

