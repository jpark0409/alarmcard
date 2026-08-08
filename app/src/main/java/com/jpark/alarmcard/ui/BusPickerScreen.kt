package com.jpark.alarmcard.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jpark.alarmcard.data.crawler.BusStationSearchResult
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * 버스 정류장/노선을 네이버 지도 웹뷰에서 직접 선택하는 화면.
 * 흐름:
 *  1) 상단 검색바에 텍스트 입력 → 웹뷰가 https://map.naver.com/p/search/{query} 로 이동.
 *  2) 사용자는 웹뷰에서 브라우저처럼 정류장/노선을 선택.
 *  3) URL 이 `/bus-route/{routeId}?bsl={routeId},{stationId},...` 형태가 되면
 *     자동으로 detect 해 상단에 "이 노선을 카드로 추가" 버튼이 활성화됨.
 *  4) 확인 시 실시간 도착정보 API를 통해 최종 카드 저장.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BusPickerScreen(
    vm: MainViewModel,
    onDone: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var detected by remember { mutableStateOf<DetectedRoute?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 웹뷰 인스턴스 (Compose 밖에서 유지)
    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정류장·노선 선택") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            // 검색어 입력
            Row(Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("정류장명 (예: 판교역동편)") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val q = query.trim()
                        if (q.isNotEmpty()) {
                            val url = "https://map.naver.com/p/search/" +
                                URLEncoder.encode(q, "UTF-8")
                            webViewRef[0]?.loadUrl(url)
                        }
                    },
                    enabled = query.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("검색") }
            }

            // 상단 안내 / 노선 detect 시 카드 추가 버튼
            detected?.let { d ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "선택 감지: 노선 ${d.routeId} · 정류장 ${d.stationId}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                status = "실시간 도착정보 조회 중..."
                                val detail = runCatching {
                                    vm.previewStationArrivals(d.stationId, null)
                                }.getOrNull()
                                if (detail == null || detail.arrivals.isEmpty()) {
                                    status = "도착정보를 얻지 못했습니다. 다른 정류장을 선택해 보세요."
                                } else {
                                    // 그 노선만 필터해서 카드로 추가
                                    vm.addBus(
                                        BusStationSearchResult(
                                            stationId = d.stationId,
                                            stationName = detail.stationName
                                        ),
                                        listOf(d.routeId)
                                    )
                                    onDone()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("이 노선을 카드로 추가")
                    }
                }
            }
            status?.let { Text(it, color = Color(0xFFD32F2F), modifier = Modifier.padding(12.dp)) }

            // 웹뷰 본체
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val wv = WebView(ctx)
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.databaseEnabled = true
                    wv.settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            currentUrl = url
                            detected = url?.let { detectRouteFromUrl(it) }
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            currentUrl = url
                            detected = url?.let { detectRouteFromUrl(it) }
                        }
                    }
                    webViewRef[0] = wv
                    wv.loadUrl("https://map.naver.com/")
                    wv
                }
            )
        }
    }
}

private data class DetectedRoute(val routeId: String, val stationId: String)

private fun detectRouteFromUrl(url: String): DetectedRoute? {
    // https://map.naver.com/p/search/{q}/bus-route/{routeId}?bsl={routeId},{stationId},{seq}
    val bslRx = Regex("[?&]bsl=([^&]+)")
    val bslMatch = bslRx.find(url) ?: return null
    val parts = bslMatch.groupValues[1].split(",")
    if (parts.size < 2) return null
    val routeId = parts[0]
    val stationId = parts[1]
    if (!routeId.matches(Regex("^\\d+$")) || !stationId.matches(Regex("^\\d+$"))) return null
    return DetectedRoute(routeId = routeId, stationId = stationId)
}
