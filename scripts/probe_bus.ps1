# 실제로 어떤 네이버 버스/정류장 검색 URL이 살아있는지 로컬 PC에서 프로브
$ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
$hdr = @{ "User-Agent"=$ua; "Referer"="https://map.naver.com/"; "Accept"="application/json, text/plain, */*"; "Accept-Language"="ko-KR,ko;q=0.9" }
$q = [uri]::EscapeDataString("강남역")
$urls = @()
$urls += "https://map.naver.com/p/search/$q"
$urls += "https://map.naver.com/p/api/search/allSearch?query=$q&type=all"
$urls += "https://map.naver.com/p/api/search/instantSearch?query=$q"
$urls += "https://map.naver.com/p/api/site/summary/business?query=$q"
$urls += "https://map.naver.com/v5/api/search?query=$q&type=SITE_1"
$urls += "https://map.naver.com/api/site/search?query=$q&type=bus_station"
$urls += "https://map.naver.com/api/place/searchByCoords?query=$q"
$urls += "https://map.naver.com/api/search/site?query=$q&type=bus_station"
$urls += "https://pubtrans.map.naver.com/api/search/station?query=$q"
$urls += "https://map-pubtrans.map.naver.com/api/search/station?query=$q"
$urls += "https://map.naver.com/p/api/pubtrans/search/station?query=$q"
# 서울 TOPIS 모바일
$urls += "https://m.bus.go.kr/mBus/bus/getStationByName.bms?searchType=1&strSrch=$q&pageNo=1"
$urls += "https://m.bus.go.kr/mBus/bus/getStationByName.bms?strSrch=$q"
# 지도 정류장 검색 결과 페이지 (HTML)
$urls += "https://map.naver.com/p/search/$q?c=15.00,0,0,0,dh&searchType=bus_station"

$out = @()
foreach ($u in $urls) {
  try {
    $r = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $u -Headers $hdr -MaximumRedirection 3 -TimeoutSec 8
    $ct = $r.Headers["Content-Type"]
    $len = $r.RawContentLength
    $line = "OK  " + $r.StatusCode + " (" + $ct + ", " + $len + "B) " + $u
    $out += $line
    if ($r.Content) {
      $s = $r.Content
      if ($s.Length -gt 300) { $s = $s.Substring(0, 300) }
      $out += "     sample: " + ($s -replace "`r?`n", " ")
    }
  } catch {
    $st = ""
    try { $st = $_.Exception.Response.StatusCode.value__ } catch {}
    $out += "ERR $st $($_.Exception.Message) - $u"
  }
}
$out -join "`r`n" | Set-Content -Path "c:\Users\user\Desktop\alarmcard\scripts\probe.txt" -Encoding utf8
