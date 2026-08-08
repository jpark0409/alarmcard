@echo off
setlocal
set UA=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
set REF=https://map.naver.com/

echo === A) route page HTML ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0routepage.html" -w "HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/bus/bus-route/6012/bus-route/20025664?c=11.00,0,0,0,dh" > "%~dp0routepage_meta.txt" 2>&1

echo === B) potential route JSON APIs ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0api_b1.txt" -w "b1 HTTP=%%{http_code}  size=%%{size_download}\n"   "https://map.naver.com/p/api/bus/route/20025664" > "%~dp0api_b_meta.txt" 2>&1
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0api_b2.txt" -w "b2 HTTP=%%{http_code}  size=%%{size_download}\n"   "https://map.naver.com/p/api/pubtrans/bus/route/20025664" >> "%~dp0api_b_meta.txt" 2>&1
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0api_b3.txt" -w "b3 HTTP=%%{http_code}  size=%%{size_download}\n"   "https://map.naver.com/p/api/directions/bus-route/20025664" >> "%~dp0api_b_meta.txt" 2>&1
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0api_b4.txt" -w "b4 HTTP=%%{http_code}  size=%%{size_download}\n"   "https://map.naver.com/v5/api/bus/route/20025664" >> "%~dp0api_b_meta.txt" 2>&1
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0api_b5.txt" -w "b5 HTTP=%%{http_code}  size=%%{size_download}\n"   "https://map.naver.com/v5/api/pubtrans/bus/route/20025664" >> "%~dp0api_b_meta.txt" 2>&1

echo DONE
