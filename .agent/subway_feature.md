# Subway Card Feature Implementation

## Summary
Added "Subway" card type with real-time arrival information from Kakao Map, bypassing bot detection using WebView.

## Changes
- **Domain/Data Model**: Added SubwayCard and SubwayArrival models. Updated CardEntity and converters.
- **Crawler**: Implemented KakaoMapSubwayCrawler using a WebView to load place.map.kakao.com and extract real-time data via JavaScript injection (window.INITS.subway).
- **Repository**: Updated CardRepository to support adding, refreshing, and alarm logic for subway cards.
- **UI**: 
  - Added "Subway" tab in AddCardScreen.
  - Added subway card support in HomeScreen (Card item UI, alarm settings, auto-enable settings).
- **Notifications**: Added SubwayAlarmWorker for periodic arrival checks and NotificationHelper updates for subway arrival notifications.

## Verification
- Verified ID-based searching (e.g., SES1857, SES3406).
- Verified parsing logic for common Kakao Map subway response format.
- Successfully built project locally using ./gradlew assembleDebug.
