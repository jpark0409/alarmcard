package com.jpark.alarmcard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
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
fun AddCardScreen(vm: MainViewModel, onBack: () -> Unit) {
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

/* ---------- 주식 ---------- */
@Composable
private fun AddStockTab(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.padding(12.dp)) {
        SearchBar(q, "종목명 또는 티커 (예: 삼성전자, AAPL)") {
            q = it
        }
        Button(onClick = {
            scope.launch {
                loading = true
                results = runCatching { vm.searchStocks(q) }.getOrDefault(emptyList())
                loading = false
            }
        }, enabled = q.isNotBlank() && !loading, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (loading) "검색 중..." else "검색")
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.symbol + it.market }) { r ->
                ResultRow(
                    title = r.name,
                    subtitle = "${r.symbol}  ·  ${if (r.market == StockMarket.DOMESTIC) "국내" else "해외"}",
                    onClick = {
                        vm.addStock(r); onBack()
                    }
                )
            }
        }
    }
}

/* ---------- 버스 ---------- */
@Composable
private fun AddBusTab(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<BusStationSearchResult>>(emptyList()) }
    var selectedStation by remember { mutableStateOf<BusStationSearchResult?>(null) }
    var routeChoices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // routeId to routeNo
    val checkedRoutes = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.padding(12.dp)) {
        SearchBar(q, "정류장명 (예: 강남역, 시청)") { q = it }
        Button(onClick = {
            scope.launch {
                loading = true
                stations = runCatching { vm.searchStations(q) }.getOrDefault(emptyList())
                selectedStation = null
                routeChoices = emptyList()
                checkedRoutes.clear()
                loading = false
            }
        }, enabled = q.isNotBlank() && !loading, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (loading) "검색 중..." else "정류장 검색")
        }
        Spacer(Modifier.height(10.dp))

        if (selectedStation == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(stations, key = { it.stationId }) { st ->
                    ResultRow(
                        title = st.stationName,
                        subtitle = listOfNotNull(st.displayCode, st.cityCode).joinToString(" · "),
                        onClick = {
                            selectedStation = st
                            scope.launch {
                                loading = true
                                val detail = runCatching {
                                    vm.previewStationArrivals(st.stationId, st.cityCode)
                                }.getOrNull()
                                routeChoices = detail?.arrivals?.map { it.routeId to it.routeNo } ?: emptyList()
                                loading = false
                            }
                        }
                    )
                }
            }
        } else {
            Text("정류장: ${selectedStation!!.stationName}", fontWeight = FontWeight.Bold)
            Text("표시할 노선을 선택하세요 (선택 안 하면 전체 표시)", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            Button(onClick = {
                vm.addBus(selectedStation!!, checkedRoutes.toList())
                onBack()
            }) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("이 정류장 카드 추가")
            }
        }
    }
}

/* ---------- 환율 ---------- */
@Composable
private fun AddFxTab(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
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
