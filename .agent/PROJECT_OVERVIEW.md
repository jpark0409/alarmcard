# AlarmCard 프로젝트 전체 분석 문서

## 프로젝트 개요

**프로젝트명**: AlarmCard  
**설명**: 카드 형식으로 주식 시세, 버스 도착정보, 환율을 한 화면에 모아 보는 안드로이드 앱  
**기술 스택**: Kotlin 2.0, Jetpack Compose, Hilt, Room, OkHttp, Jsoup, Coroutines

---

## 1. 핵심 목표 & 기능

### 주요 기능
1. **주식 카드**: 국내(KOSPI/KOSDAQ) + 해외(US: AAPL.O 형식) 종목 시세 조회
2. **버스 카드**: 서울/수도권 정류장 도착 정보 조회 및 알림 기능
3. **환율 카드**: 주요 통화쌍(USD/KRW, JPY/KRW, EUR/KRW 등) 실시간 환율
4. **카드 관리**: 카드 추가/삭제/재정렬, 병렬 새로고침, 자동 갱신(화면 활성화 시)
5. **버스 알림**: 도착 N분 전 알림 설정 (WorkManager 기반)

### 데이터 소스
- **주식/환율**: 네이버 증권 모바일 페이지 크롤링 (`__NEXT_DATA__` JSON 파싱 우선, CSS 셀렉터 Fallback)
- **버스**: 네이버 지도 대중교통 XHR 엔드포인트

---

## 2. 프로젝트 구조 (명칭 체계)

```
app/src/main/java/com/jpark/alarmcard/
├── AlarmCardApp.kt                    # @HiltAndroidApp 진입점
├── domain/
│   └── model/
│       └── Card.kt                    # sealed interface: StockCard, BusCard, FxCard
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt             # Room Database
│   │   ├── CardDao.kt                 # Room DAO (CRUD)
│   │   └── CardEntity.kt              # Room Entity + toDomain()/toEntity() 확장
│   ├── remote/
│   │   ├── Http.kt                    # HTTP 유틸리티
│   │   ├── HttpClient.kt              # OkHttp 클라이언트 설정
│   │   ├── JsonExt.kt                 # 직렬화 확장 함수
│   │   └── NextData.kt                # __NEXT_DATA__ JSON 파싱
│   ├── crawler/
│   │   ├── NaverStockCrawler.kt        # 주식 시세 크롤러
│   │   ├── NaverFxCrawler.kt           # 환율 크롤러
│   │   └── NaverMapBusCrawler.kt       # 버스 도착정보 크롤러
│   └── CardRepository.kt              # 데이터 레이어 Facade (병렬 새로고침, 카드 CRUD)
├── di/
│   └── AppModule.kt                   # Hilt @Provides / Singleton 주입
├── notify/
│   ├── BusAlarmWorker.kt              # WorkManager 기반 버스 알림 Worker
│   └── NotificationHelper.kt          # 채널 설정 등 알림 유틸
└── ui/
    ├── MainActivity.kt                # Activity 진입점 + Lifecycle 콜백
    ├── MainViewModel.kt               # ViewModel (상태 관리 + UI 이벤트)
    ├── HomeScreen.kt                  # 카드 목록 화면
    ├── AddCardScreen.kt               # 카드 추가 화면 (주식/버스/환율 검색)
    └── AppNav.kt                      # Navigation 라우팅
```

---

## 3. 데이터 흐름 (플로우)

### 3.1 앱 시작 플로우

```
AlarmCardApp.onCreate()
  ↓
Hilt DI 초기화 (@HiltAndroidApp)
  ↓
MainActivity 실행
  ↓
MainViewModel 초기화 (StateFlow<List<Card>> 구독)
  ↓
CardRepository.observeCards() → Room DAO → StateFlow 방출
  ↓
HomeScreen 렌더링 (현재 카드 목록)
```

### 3.2 카드 새로고침 플로우

```
사용자 액션 (버튼 클릭 또는 화면 재진입)
  ↓
MainViewModel.refresh() / onScreenResumed()
  ↓
CardRepository.refreshAll() (또는 refreshOne)
  ↓
coroutineScope { cards.map { async { refreshCard(card) } } } (병렬 실행)
  ↓
각 카드 타입별 크롤러 호출:
  - StockCard → NaverStockCrawler.fetchQuote()
  - FxCard → NaverFxCrawler.fetchQuote()
  - BusCard → NaverMapBusCrawler.fetchArrivals()
  ↓
성공: CardEntity 업데이트 (updatedAt, price/rate/arrivals, lastError=null)
실패: CardEntity 업데이트 (lastError=에러메시지)
  ↓
CardDao.upsert() → Room DB 저장
  ↓
StateFlow 구독자들에게 변경 알림 (HomeScreen 자동 업데이트)
```

### 3.3 카드 추가 플로우

**주식 카드 추가**:
```
AddCardScreen (검색 UI)
  ↓
MainViewModel.searchStocks(q) → NaverStockCrawler.search()
  ↓
사용자 선택 (StockSearchResult)
  ↓
MainViewModel.addStock(sel)
  ↓
CardRepository.addStock()
  ├─ 새 UUID 생성 + order 계산
  ├─ CardDao.upsert()
  └─ refreshOne() → 최초 시세 가져오기
  ↓
HomeScreen 에 카드 추가됨
```

**버스 카드 추가**:
```
AddCardScreen (버스 검색 UI)
  ↓
MainViewModel.searchStations(q) → NaverMapBusCrawler.searchStation()
  ↓
사용자 정류장 선택 (BusStationSearchResult)
  ↓
MainViewModel.previewStationArrivals() → 해당 정류장 노선 미리보기
  ↓
사용자 필터링할 노선 체크
  ↓
MainViewModel.addBus(st, routeIds)
  ↓
CardRepository.addBus()
  ├─ 새 UUID 생성 + order 계산
  ├─ filterRouteIds 저장
  ├─ CardDao.upsert()
  └─ refreshOne() → 최초 도착정보 가져오기
  ↓
HomeScreen 에 카드 추가됨
```

**환율 카드 추가**:
```
AddCardScreen (환율 프리셋 UI)
  ↓
MainViewModel.listFxPresets() → NaverFxCrawler.listAvailable()
  ↓
사용자 통화쌍 선택 (FxQuote)
  ↓
MainViewModel.addFx(q)
  ↓
CardRepository.addFx()
  ├─ FX_USDKRW 형식 코드 파싱 (USD, KRW)
  ├─ CardDao.upsert()
  └─ refreshOne() → 최초 환율 가져오기
  ↓
HomeScreen 에 카드 추가됨
```

### 3.4 버스 알림 플로우

```
사용자 버스 카드에서 "알림 설정" 활성화
  ↓
MainViewModel.setBusAlarm(cardId, enabled=true, minutesBefore=3)
  ↓
CardRepository.setBusAlarm()
  ├─ CardEntity 업데이트 (alarmEnabled=true, alarmMinutesBefore=3)
  ├─ CardDao.upsert()
  └─ rescheduleBusAlarmWorker() 호출

rescheduleBusAlarmWorker()
  ↓
repo.hasActiveBusAlarm() 확인
  ↓
활성 알람 있음 → BusAlarmWorker.scheduleNext(ctx, delaySec=5)
활성 알람 없음 → BusAlarmWorker.cancel(ctx)

BusAlarmWorker 실행 (주기적)
  ↓
CardRepository.refreshBusAlarmsAndSelectFireable()
  ├─ 모든 활성 버스카드 새로고침
  ├─ eta1Sec <= alarmMinutesBefore*60 인 노선 검색
  ├─ 중복 방지 (alarmLastFiredAt < 5분 이내 스킵)
  └─ 발송 대상 리스트 반환

리스트 순회하며 NotificationHelper.showBusAlarm() 발송
  ↓
사용자 알림 수신
```

---

## 4. 핵심 클래스/인터페이스 설명

### 4.1 Domain Layer

#### Card.kt (sealed interface)
```
Card (sealed interface)
├── StockCard: symbol, market, name, price, change, changeRate, currency
├── BusCard: stationId, stationName, filterRouteIds, arrivals[], alarmEnabled, alarmMinutesBefore
└── FxCard: code, base, quote, rate, change, changeRate
```

**공통 속성**:
- `id`: UUID (로컬 유일성)
- `order`: 정렬 순서
- `updatedAt`: 마지막 갱신 시각 (epoch ms, 0=미갱신)
- `lastError`: 마지막 에러 메시지

---

### 4.2 Data Layer

#### CardRepository (Singleton)
**책임**: 데이터 접근 통합 (DAO + Crawlers)

**핵심 메서드**:
- `observeCards()`: StateFlow<List<Card>> 반환 (DAO 구독)
- `refreshAll()`: 모든 카드 병렬 갱신
- `refreshOne(id)`: 단일 카드 갱신
- `addStock/addBus/addFx()`: 카드 추가 + 초기 갱신
- `setBusAlarm()`: 버스 알림 설정
- `refreshBusAlarmsAndSelectFireable()`: 활성 버스카드 갱신 + 알림 대상 필터링

---

#### Crawlers (NaverStockCrawler, NaverFxCrawler, NaverMapBusCrawler)

**공통 패턴**:
1. OkHttp로 네이버 페이지 요청
2. `__NEXT_DATA__` JSON에서 필요 데이터 추출 (우선)
3. 실패 시 Jsoup CSS 셀렉터로 Fallback
4. Quote/StationSearchResult 객체 반환

**NaverStockCrawler**:
- `search(q)`: 종목 검색 → List<StockSearchResult>
- `fetchQuote(symbol, market)`: 시세 조회 → StockQuote (price, change, changeRate)

**NaverMapBusCrawler**:
- `searchStation(q)`: 정류장 검색 → List<BusStationSearchResult>
- `fetchArrivals(stationId, cityCode)`: 도착정보 → BusDetail (arrivals[])
  - arrivals: 해당 정류장 모든 노선의 ETA 정보

**NaverFxCrawler**:
- `listAvailable()`: 환율 프리셋 → List<FxQuote>
- `fetchQuote(code)`: 환율 조회 → FxQuote (rate, change, changeRate)

---

### 4.3 Local Layer (Room)

#### CardEntity
- Room @Entity
- `type` 필드로 카드 타입 구분 (TYPE_STOCK, TYPE_BUS, TYPE_FX)
- 모든 카드 공통/개별 필드를 하나의 테이블에 저장
- `toDomain()`/`toEntity()` 확장 함수로 변환

#### CardDao
- `observeAll()`: Flow<List<CardEntity>> (구독 가능)
- `getAll()`, `getById(id)`: 동기 조회
- `upsert()`, `update()`, `deleteById()`

#### AppDatabase
- Room @Database
- Hilt @Provides로 Singleton 주입

---

### 4.4 UI Layer

#### MainViewModel (AndroidViewModel)
**상태**:
- `cards`: StateFlow<List<Card>> (DAO 구독)
- `isRefreshing`: StateFlow<Boolean> (새로고침 중 여부)
- `lastAutoRefresh`: Long (자동 갱신 최소 간격 관리)

**이벤트 메서드**:
- `refresh()`: 명시적 새로고침 (버튼)
- `onScreenResumed()`: 화면 재진입 시 자동 갱신 (15초 미만 중복 무시)
- `deleteCard(id)`: 카드 삭제 + Worker 재스케줄
- `reorder(ids)`: 카드 순서 변경
- `setBusAlarm()`: 버스 알림 설정 + Worker 스케줄
- 검색 헬퍼: `searchStocks()`, `searchStations()`, `listFxPresets()`

#### HomeScreen
- 카드 목록 렌더링 (StockCard/BusCard/FxCard 각각 Composable)
- 드래그&드롭 재정렬 (reorderable 라이브러리)
- 삭제 버튼 / 새로고침 버튼

#### AddCardScreen
- TabRow로 3가지 탭 (주식 / 버스 / 환율)
- 각 탭에서 검색 + 미리보기 + 추가
- 버스 탭에서는 노선 필터링 UI 포함

#### MainActivity
- `onResume()` → `viewModel.onScreenResumed()` (자동 갱신 트리거)
- Compose 기반 UI 렌더링

#### AppNav
- Jetpack Navigation Compose 라우팅
- Route: "home" (홈) / "add" (카드 추가)

---

### 4.5 알림 & Worker

#### BusAlarmWorker (WorkManager)
- 주기적으로 실행 (초기 지연 후 자동 재스케줄)
- `refreshBusAlarmsAndSelectFireable()` 호출
- 반환된 카드들에 대해 `NotificationHelper.showBusAlarm()` 발송
- 각 실행 후 `scheduleNext()` 호출해 자동 연쇄 스케줄

#### NotificationHelper
- `ensureChannel()`: NotificationChannel 생성 (앱 시작 시)
- `showBusAlarm()`: 알림 발송

#### AlarmCardApp
- @HiltAndroidApp 진입점
- WorkManager HiltWorkerFactory 설정
- Timber 디버그 로깅 초기화
- NotificationChannel 생성

---

## 5. 명칭 규칙 (Naming Conventions)

### 파일명
- **Crawler 클래스**: `Naver{Service}Crawler.kt` (e.g., NaverStockCrawler)
- **Model**: `{EntityName}.kt` (e.g., Card.kt, StockCard data class)
- **Repository**: `CardRepository.kt` (데이터 레이어 통합)
- **DAO**: `{Entity}Dao.kt`
- **Database**: `AppDatabase.kt`
- **ViewModel**: `{Screen}ViewModel.kt` (e.g., MainViewModel)
- **Screen**: `{ScreenName}Screen.kt` (e.g., HomeScreen, AddCardScreen)
- **Activity**: `Main{Purpose}Activity.kt` (e.g., MainActivity)
- **Worker**: `{Purpose}Worker.kt` (e.g., BusAlarmWorker)
- **Utility**: `{Functionality}Helper.kt` 또는 `{Functionality}Ext.kt`

### 클래스명
- Sealed Interface: `Card`
- Data Class: `{Domain}Card`, `{Domain}Quote`, `{Domain}SearchResult` (e.g., StockCard, BusArrival)
- Enum: `{EntityName}` (e.g., StockMarket)
- Singleton: `{Service}Repository`, `{Service}Crawler`, Database
- ViewModel: `{ScreenName}ViewModel`
- Composable Screen: `{ScreenName}Screen()`
- Worker: `{Functionality}Worker`

### 함수명
- 조회: `fetch{What}()`, `search{What}()`, `get{What}()`
- 갱신: `refresh{What}()`, `update{What}()`
- 변환: `to{TargetType}()`
- 설정: `set{Property}()`
- 콜백: `on{Event}()`

### 변수명
- StateFlow: `{propertyName}` (lowercase)
- Flow: `observe{EntityName}()` 반환
- 상태 필드: `_{fieldName}` (MutableStateFlow 비공개)
- 캐시/임시: `last{Info}`, `cached{Info}`
- 설정값: `{property}MinInterval`, `{property}Threshold`

---

## 6. 주요 기술 스택 & 라이브러리

| 분야 | 라이브러리 | 용도 |
|------|----------|------|
| 언어 | Kotlin 2.0 | 안드로이드 앱 |
| UI | Jetpack Compose + Material3 | 모던 UI 프레임워크 |
| 의존성 주입 | Hilt + KSP | DI 및 코드 생성 |
| 아키텍처 | MVVM (ViewModel + StateFlow) | 상태 관리 |
| 데이터베이스 | Room | 로컬 DB (카드/캐시) |
| 비동기 | Coroutines / Flow | 동시성 처리 |
| HTTP | OkHttp + Retrofit | 웹 요청 |
| 크롤링 | Jsoup + kotlinx.serialization | HTML/JSON 파싱 |
| 네비게이션 | Navigation Compose | 화면 라우팅 |
| 백그라운드 | WorkManager | 주기적 작업 (버스 알림) |
| 로깅 | Timber | 디버그 로깅 |
| 재정렬 UI | reorderable | 드래그&드롭 |

---

## 7. 에러 처리 & 복원력

### 크롤링 실패
- `try-catch` → `lastError` 필드에 메시지 저장
- UI에서 ⚠ 경고 아이콘 표시
- 앱은 크래시하지 않음 (graceful degradation)

### 네이버 페이지 변경 대응
- `__NEXT_DATA__` JSON 파싱 → CSS 셀렉터 Fallback
- 이중화된 크롤러 파이프라인

### 네트워크 실패
- OkHttp 재시도 정책
- Fallback URL 시도 (버스 도착정보 등)

### 버스 알림 중복 방지
- `alarmLastFiredAt` 필드 (마지막 발송 시각)
- 5분 이내 중복 발송 제외

---

## 8. 테스트 전략 (선택사항)

- 크롤러: 실제 네이버 페이지 mock/stub으로 테스트
- Repository: DAO + Crawler mock으로 테스트
- ViewModel: StateFlow 상태 변경 검증
- UI: Compose Preview + 수동 테스트

---

## 9. 배포 및 CI/CD

- **GitHub Actions**: main 브랜치 push 시 debug APK 자동 빌드
- **Release**: GitHub Release `latest` 태그에 APK 첨부
- **사용자 설치**: 폰 브라우저에서 직접 APK 다운로드 & 설치

---

## 10. 알려진 한계 & 주의사항

1. **네이버 페이지 의존성**: 페이지 스키마 변경 → 파서 업데이트 필요
2. **개인/학습용**: 공식 API 대신 크롤링 사용 (약관 확인 필수)
3. **지역 제한**: 서울/수도권 버스 정보 최적 지원
4. **실시간성**: 크롤링 기반이므로 공식 API 대비 지연 가능

---

## 11. 향후 개선 방향 (Roadmap)

1. 오프라인 모드 강화
2. 커스텀 알림 사운드/진동
3. 다국어 지원
4. 위젯 추가
5. 공식 API 전환 (가능한 범위)

---

이 문서는 AlarmCard 프로젝트의 전체 구조, 흐름, 기술 스택을 정리한 것입니다.  
향후 agent가 새로운 task를 수행할 때 이 문서를 참고하여 전체 프로젝트 분석을 최소화할 수 있습니다.