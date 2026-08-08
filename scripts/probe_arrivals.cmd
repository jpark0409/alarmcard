@echo off
setlocal
set UA=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
set REF=https://map.naver.com/p/search/panyo/bus-route/20025664?bsl=20025664,194374,1

echo === arrivals: stations=194374 ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0a1.txt" -w "a1 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/pubtrans/realtime/bus/arrivals/multi?stations=194374" > "%~dp0a_meta.txt" 2>&1

echo === arrivals: stations=194374 with routeIds ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0a2.txt" -w "a2 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/pubtrans/realtime/bus/arrivals/multi?stations=194374&routeIds=20025664" >> "%~dp0a_meta.txt" 2>&1

echo === arrivals: stations=194374:20025664:1 (composite) ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0a3.txt" -w "a3 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/pubtrans/realtime/bus/arrivals/multi?stations=194374:20025664:1" >> "%~dp0a_meta.txt" 2>&1

echo === arrivals: stations=20025664,194374,1 (bsl-style) ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0a4.txt" -w "a4 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/pubtrans/realtime/bus/arrivals/multi?stations=20025664,194374,1" >> "%~dp0a_meta.txt" 2>&1

echo === arrivals: stations=194374,20025664,1 ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0a5.txt" -w "a5 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/pubtrans/realtime/bus/arrivals/multi?stations=194374,20025664,1" >> "%~dp0a_meta.txt" 2>&1

echo === station page probe ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0st1.txt" -w "st1 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/pubtrans/bus/stations/194374" >> "%~dp0a_meta.txt" 2>&1

echo === search API allSearch with searchCoord (panyo area) ===
curl.exe -sL -A "%UA%" -H "Referer: https://map.naver.com/" -H "Accept: application/json" -o "%~dp0s1.txt" -w "s1 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%%ED%%8C%%90%%EA%%B5%%90%%EC%%97%%AD%%EB%%8F%%99%%ED%%8E%%B8&type=bus_station&searchCoord=127.111,37.394" >> "%~dp0a_meta.txt" 2>&1

echo DONE
