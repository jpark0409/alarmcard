@echo off
setlocal
set UA=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
set REF=https://map.naver.com/
set COOKIE_JAR=%~dp0cookies.txt
if exist "%COOKIE_JAR%" del "%COOKIE_JAR%"

set Q=%%ED%%8C%%90%%EA%%B5%%90%%EC%%97%%AD%%EB%%8F%%99%%ED%%8E%%B8

echo === 1) prime session ===
curl.exe -sL -A "%UA%" -c "%COOKIE_JAR%" -o "%~dp0home.html" -w "home HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/" > "%~dp0s2_meta.txt" 2>&1

echo === 2) load search page (SPA) with same cookies ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -b "%COOKIE_JAR%" -c "%COOKIE_JAR%" -o "%~dp0sp.html" -w "sp HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/search/%Q%" >> "%~dp0s2_meta.txt" 2>&1

echo === 3) call allSearch with cookies (bus_station type) ===
curl.exe -sL -A "%UA%" -H "Referer: https://map.naver.com/p/search/%Q%" -H "Accept: application/json" -b "%COOKIE_JAR%" -c "%COOKIE_JAR%" -o "%~dp0as1.txt" -w "as1 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&type=all&searchCoord=127.111,37.394" >> "%~dp0s2_meta.txt" 2>&1

echo === 4) call allSearch type=bus_station ===
curl.exe -sL -A "%UA%" -H "Referer: https://map.naver.com/p/search/%Q%" -H "Accept: application/json" -b "%COOKIE_JAR%" -c "%COOKIE_JAR%" -o "%~dp0as2.txt" -w "as2 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&type=bus_station&searchCoord=127.111,37.394" >> "%~dp0s2_meta.txt" 2>&1

echo === 5) also try type=bus_station without searchCoord ===
curl.exe -sL -A "%UA%" -H "Referer: https://map.naver.com/p/search/%Q%" -H "Accept: application/json" -b "%COOKIE_JAR%" -c "%COOKIE_JAR%" -o "%~dp0as3.txt" -w "as3 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&type=bus_station" >> "%~dp0s2_meta.txt" 2>&1

echo DONE
