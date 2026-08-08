@echo off
setlocal
set UA=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
set REF=https://map.naver.com/p/bus/bus-route/6012/bus-route/20025664

echo === arrivals multi (GET, no params) ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0arr1.txt" -w "arr1 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/api/pubtrans/realtime/bus/arrivals/multi" > "%~dp0arr_meta.txt" 2>&1

echo === arrivals multi with routeIds ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0arr2.txt" -w "arr2 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/api/pubtrans/realtime/bus/arrivals/multi?routeIds=20025664" >> "%~dp0arr_meta.txt" 2>&1

echo === routes types 6012 ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0rt1.txt" -w "rt1 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/api/pubtrans/bus/routes/types?routeIds=20025664" >> "%~dp0arr_meta.txt" 2>&1

echo === same but through /p/api/ (pcweb prefix) ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0arr3.txt" -w "arr3 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/pubtrans/realtime/bus/arrivals/multi?routeIds=20025664" >> "%~dp0arr_meta.txt" 2>&1
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0rt2.txt" -w "rt2 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/pubtrans/bus/routes/types?routeIds=20025664" >> "%~dp0arr_meta.txt" 2>&1

echo DONE
