@echo off
setlocal enabledelayedexpansion
set UA=Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36
set REF=https://map.naver.com/
set Q=%%EA%%B0%%95%%EB%%82%%A8%%EC%%97%%AD

echo === 1. TOPIS mobile: getStationByName ===
curl.exe -sL -A "%UA%" -H "Referer: https://m.bus.go.kr/" -o - -w "\nHTTP=%%{http_code}  size=%%{size_download}\n" "https://m.bus.go.kr/mBus/bus/getStationByName.bms?searchType=1&strSrch=%Q%&pageNo=1" | more +0 > "%~dp0probe1.txt" 2>&1

echo === 2. map.naver.com p/search HTML ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0probe2.html" -w "HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/search/%Q%" > "%~dp0probe2.txt" 2>&1

echo === 3. Search hint API (mobile) ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0probe3.txt" -w "HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&type=bus_station" > "%~dp0probe3_meta.txt" 2>&1

echo === 4. Bus station specific ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0probe4.txt" -w "HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/v5/api/search?query=%Q%&type=SITE_1" > "%~dp0probe4_meta.txt" 2>&1

echo === 5. pubtrans.map.naver.com ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0probe5.txt" -w "HTTP=%%{http_code}  size=%%{size_download}\n" "https://pubtrans.map.naver.com/station/search/api?query=%Q%" > "%~dp0probe5_meta.txt" 2>&1

echo DONE
