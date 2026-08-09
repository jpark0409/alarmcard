package com.jpark.alarmcard.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpark.alarmcard.domain.model.BusCard
import com.jpark.alarmcard.domain.model.Card as DomainCard
import com.jpark.alarmcard.domain.model.FxCard
import com.jpark.alarmcard.domain.model.StockCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onAdd: () -> Unit
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val refreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    var activeId by remember { mutableStateOf<String?>(null) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val reorderedIds = cards.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }.map { it.id }
        vm.reorder(reorderedIds)
    }

    val pullRefreshState = rememberPullToRefreshState()

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
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
            state = pullRefreshState
        ) {
            if (cards.isEmpty()) {
                EmptyState(inner)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    state = lazyListState
                ) {
                    items(cards, key = { it.id }) { c ->
                        ReorderableItem(reorderState, key = c.id) { _ ->
                            CardItem(
                                card = c,
                                onDelete = { vm.deleteCard(c.id) },
                                onSetBusAlarm = { enabled, minutes ->
                                    vm.setBusAlarm(c.id, enabled, minutes)
                                },
                                modifier = Modifier.longPressDraggableHandle(
                                    onDragStarted = { activeId = c.id },
                                    onDragStopped = { activeId = null }
                                ),
                                isActive = activeId == c.id
                            )
                        }
                    }
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
fun CardItem(
    card: DomainCard,
    onDelete: () -> Unit,
    onSetBusAlarm: (Boolean, Int) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    var showAlarmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(if (isActive) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 첫 번째 줄: 타입, 제목, 알람 아이콘, 삭제 버튼
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TypeBadge(card)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        cardTitle(card),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 버스카드에만 종 아이콘
                if (card is BusCard) {
                    IconButton(
                        onClick = {
                            if (card.alarmEnabled) {
                                onSetBusAlarm(false, card.alarmMinutesBefore)
                            } else {
                                showAlarmDialog = true
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (card.alarmEnabled) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = if (card.alarmEnabled) "알람 끄기" else "알람 설정",
                            tint = if (card.alarmEnabled) Color(0xFFF9A825) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "삭제",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 두 번째 줄: 본문 데이터
            when (card) {
                is StockCard -> StockBodyCompact(card)
                is FxCard -> FxBodyCompact(card)
                is BusCard -> BusBodyCompact(card)
            }

            // 세 번째 줄: 푸터 (시간 + 에러)
            Text(
                text = footerText(card),
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }
    }

    if (showAlarmDialog && card is BusCard) {
        BusAlarmSetupDialog(
            initialMinutes = card.alarmMinutesBefore,
            onDismiss = { showAlarmDialog = false },
            onConfirm = { minutes ->
                showAlarmDialog = false
                onSetBusAlarm(true, minutes)
            }
        )
    }
}

@Composable
private fun BusAlarmSetupDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("도착 알림 설정") },
        text = {
            Column {
                Text("몇 분 전에 알림을 받을까요? (1~30분)")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(2) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = text.toIntOrNull()?.coerceIn(1, 30) ?: initialMinutes
                onConfirm(n)
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
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
private fun StockBodyCompact(c: StockCard) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = c.price?.let { fmtPrice(it, c.currency) } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(Modifier.size(8.dp))
        val chgColor = c.change?.let { if (it >= 0) Color(0xFFD32F2F) else Color(0xFF1976D2) } ?: Color.Gray
        Text(
            text = buildString {
                if (c.change != null) append((if (c.change >= 0) "▲" else "▼") + " " + fmtPrice(kotlin.math.abs(c.change), c.currency))
                if (c.changeRate != null) append("  " + String.format("%.2f%%", c.changeRate))
                if (c.change == null && c.changeRate == null) append("변동")
            },
            color = chgColor,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.size(6.dp))
        Text(c.symbol, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
    }
}

@Composable
private fun FxBodyCompact(c: FxCard) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = c.rate?.let { String.format("%,.2f", it) } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(Modifier.size(8.dp))
        val chgColor = c.change?.let { if (it >= 0) Color(0xFFD32F2F) else Color(0xFF1976D2) } ?: Color.Gray
        Text(
            text = buildString {
                if (c.change != null) append((if (c.change >= 0) "▲" else "▼") + " " + String.format("%.2f", kotlin.math.abs(c.change)))
                if (c.changeRate != null) append("  " + String.format("%.2f%%", c.changeRate))
            },
            color = chgColor,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.size(6.dp))
        Text("${c.base}→${c.quote}", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
    }
}

@Composable
private fun BusBodyCompact(c: BusCard) {
    if (c.arrivals.isEmpty()) {
        Text("도착 정보 없음", color = Color.Gray, fontSize = 12.sp)
    } else {
        // 첫 번째 도착만 표시
        val a = c.arrivals.firstOrNull()
        if (a != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    a.routeNo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(50.dp)
                )
                Text(
                    fmtEta(a.eta1Sec, a.remainStops1),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (a.eta2Sec != null) {
                    Text(
                        " | ${fmtEta(a.eta2Sec, a.remainStops2)}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1
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
