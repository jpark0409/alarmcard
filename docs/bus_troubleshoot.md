# 버스 정류장 검색이 안 되는 원인 분석

## 결론
**앱과 에뮬레이터 네트워크는 정상이며**, 실제로 크롤러가 호출하는 네이버 지도 URL들이 사용자가 원하는 정류장/도착정보를 반환하지 않는 것이 원인입니다.

## 증거
### 1. 에뮬레이터 네트워크는 정상
`adb shell ping 8.8.8.8` → 정상 왕복 300ms. 앱의 OkHttp 로그에도 `map.naver.com` 은 HTTP 응답을 받고 있음(404/400).

### 2. 앱 로그 (`adb logcat`)
```
--> GET https://map.naver.com/p/api/search/allSearch?query=6012&type=bus_station
<-- 400 (254-byte body)
--> GET https://map.naver.com/p/api/search/busStation?query=6012
<-- 404 (100-byte body)
--> GET https://pubtrans.map.naver.com/api/station/search?query=6012&lang=ko
<-- UnknownHostException: Unable to resolve host "pubtrans.map.naver.com"
```

### 3. PC에서 curl로 프로브한 결과
- `https://map.naver.com/p/search/강남역` = **200 OK, 15KB HTML** (하지만 SPA 껍데기로 JS 하이드레이션이 없으면 결과 안 보임)
- `https://map.naver.com/p/api/search/allSearch?query=강남역&type=bus_station`
  = **400 Bad Request**, 응답 본문: `{"message":"querystring must have required property 'searchCoord'", ...}`
  → 이 API는 **실존하지만 `searchCoord` 필수**. 좌표 없이 못 부름.
- `https://map.naver.com/v5/api/search?query=강남역&type=SITE_1` = 200 OK (HTML)
- `https://pubtrans.map.naver.com/*` = **DNS 실패**. 해당 호스트가 존재하지 않음. (내가 짐작으로 넣은 호스트였음)
- `https://m.bus.go.kr/mBus/bus/getStationByName.bms?...` = **연결 실패 (HTTP 000)**. 서버가 응답 안 함.

## 왜 이렇게 됐나
네이버 지도의 대중교통(버스) 실시간 정보는:
1. **웹뷰/앱 SDK 내부의 XHR + JS 브릿지** 조합으로 렌더됨
2. `map.naver.com/p/*` 경로들은 대부분 **CSR SPA 껍데기** (JS가 데이터 XHR을 이후에 호출)
3. 그 내부 XHR은 필수 매개변수(좌표, 세션 토큰, `Referer` 헤더 등)를 요구하며 호스트도 지속적으로 바뀜
4. 특히 정류장/버스 실시간 도착 API는 **모바일 앱 API로 이동/은닉**되어 웹에서 공개적으로 접근 가능한 안정 엔드포인트가 없음

즉 초기 구현에서 넣은 URL들은 몇 년 전 유효했거나 다른 지도 앱에서 관찰된 것으로, **현재 시점에서는 실제로 작동하지 않습니다**.

## 대안

### 선택 A (권장) — 서울시 TOPIS 오픈API로 전환
- 데이터 서울 (`data.seoul.go.kr`) 에서 무료 인증키 발급
- 정류장 검색: `http://ws.bus.go.kr/api/rest/stationinfo/getStationByName?serviceKey={KEY}&stSrch={이름}`
- 실시간 도착정보: `http://ws.bus.go.kr/api/rest/stationinfo/getStationByUid?serviceKey={KEY}&arsId={ARS}`
- 사용자는 앱 최초 실행 시 자신의 인증키를 한 번만 입력.
- 서울시 + 광역버스(경기 유입버스 상당수 포함) 커버.

### 선택 B — 경기도 버스 API
- 경기도 데이터드림 인증키.
- 경기도 정류장/노선 커버.

### 선택 C — 네이버 지도 크롤러 유지
- searchCoord 파라미터로 좌표 넣어 정류장 후보는 뽑을 수 있으나, **버스 도착 예정시간은 사실상 불가**. 카드의 핵심 기능이 무의미해짐.
