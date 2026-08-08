package com.jpark.alarmcard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpark.alarmcard.data.crawler.BusStationSearchResult
import com.jpark.alarmcard.data.crawler.FxQuote
import com.jpark.alarmcard.data.crawler.StockSearchResult
import com.jpark.alarmcard.domain.model.StockMarket
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카드 추가") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("주식") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("버스") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("환율") })
            }
            when (tab) {
                0 -> AddStockTab(vm, onBack)
                1 -> AddBusTab(vm, onBack)
                2 -> AddFxTab(vm, onBack)
            }
        }
    }
}

/* ---------- 주식 (URL / 코드 입력 방식) ---------- */
@Composable
private fun AddStockTab(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(12.dp)) {
        Text(
            "네이버 금융 종목 URL 또는 6자리 종목코드를 입력하세요.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            "예) https://finance.naver.com/item/main.naver?code=005930\n" +
                "또는 코드만: 005930",
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(6.dp))
        SearchBar(q, "네이버 금융 URL 또는 종목코드") { q = it }
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    errorMsg = null
                    results = runCatching { vm.searchStocks(q) }.getOrDefault(emptyList())
                    if (results.isEmpty()) {
                        errorMsg = "URL 또는 코드를 인식하지 못했거나, 정보를 가져오지 못했습니다."
                    }
                    loading = false
                }
            },
            enabled = q.isNotBlank() && !loading,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(if (loading) "확인 중..." else "종목 확인")
        }

        errorMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.symbol + it.market }) { r ->
                val priceLine = r.price?.let { p ->
                    val chg = r.change?.let { c -> " (${if (c >= 0) "+" else ""}${c.toLong()})" } ?: ""
                    "현재가 ${"%,d".format(p.toLong())}$chg"
                } ?: "가격 정보 없음"
                ResultRow(
                    title = r.name,
                    subtitle = "${r.symbol}  ·  ${if (r.market == StockMarket.DOMESTIC) "국내" else "해외"}\n$priceLine",
                    onClick = {
                        vm.addStock(r); onBack()
                    }
                )
            }
        }
    }
}

/* ---------- 버스 (URL / ID 입력 전용) ---------- */
@Composable
private fun AddBusTab(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var selectedStation by remember { mutableStateOf<BusStationSearchResult?>(null) }
    var routeChoices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val checkedRoutes = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(12.dp)) {
        Text(
            "네이버 지도 정류장 URL 또는 정류장 ID(숫자)를 입력하세요.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            "예) https://map.naver.com/p/search/판교역/bus-station/194374?...\n" +
                "또는 정류장 ID만: 194374",
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(6.dp))
        SearchBar(input, "네이버 지도 URL 또는 정류장 ID") { input = it }
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    errorMsg = null
                    val list = runCatching { vm.searchStations(input) }.getOrDefault(emptyList())
                    if (list.isEmpty()) {
                        errorMsg = "URL 또는 ID를 인식하지 못했습니다. 네이버 지도의 정류장 페이지 URL을 붙여넣어 주세요."
                        selectedStation = null
                        routeChoices = emptyList()
                    } else {
                        val st = list.first()
                        selectedStation = st
                        val detail = runCatching {
                            vm.previewStationArrivals(st.stationId, st.cityCode)
                        }.getOrNull()
                        routeChoices = detail?.arrivals?.map { it.routeId to it.routeNo } ?: emptyList()
                        if (routeChoices.isEmpty()) {
                            errorMsg = "도착정보를 가져오지 못했습니다. ID가 맞는지 확인해 주세요."
                        }
                    }
                    loading = false
                }
            },
            enabled = input.isNotBlank() && !loading,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(if (loading) "확인 중..." else "정류장 확인")
        }

        errorMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))

        selectedStation?.let { st ->
            Text("정류장: ${st.stationName}", fontWeight = FontWeight.Bold)
            Text("(ID: ${st.stationId})", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    vm.addBus(st, checkedRoutes.toList())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(
                    if (checkedRoutes.isEmpty()) "이 정류장 카드 추가 (전체 노선)"
                    else "이 정류장 카드 추가 (${checkedRoutes.size}개 노선)"
                )
            }
            Spacer(Modifier.height(8.dp))

            if (routeChoices.isNotEmpty()) {
                Text(
                    "표시할 노선을 선택하세요 (선택 안 하면 전체 표시)",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(routeChoices, key = { it.first }) { (id, no) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checkedRoutes.contains(id),
                                onCheckedChange = {
                                    if (it) checkedRoutes.add(id) else checkedRoutes.remove(id)
                                }
                            )
                            Text(no)
                        }
                    }
                }
            }
        }
    }
}

/* ---------- 환율 ---------- */
@Composable
private fun AddFxTab(vm: MainViewModel, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<FxQuote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        items = runCatching { vm.listFxPresets() }.getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.padding(12.dp)) {
        Text("환율 프리셋", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (loading) Text("불러오는 중...", color = Color.Gray)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.code }) { q ->
                ResultRow(
                    title = q.name,
                    subtitle = q.code,
                    onClick = { vm.addFx(q); onBack() }
                )
            }
        }
    }
}

/* ---------- shared UI ---------- */
@Composable
private fun SearchBar(value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
    )
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
