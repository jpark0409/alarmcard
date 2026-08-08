@echo off
setlocal
set UA=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
set REF=https://map.naver.com/

REM 판교역 좌표 근처
set Q=%%ED%%8C%%90%%EA%%B5%%90%%EC%%97%%AD%%EB%%8F%%99%%ED%%8E%%B8

echo === allSearch with searchCoord ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0sr1.txt" -w "sr1 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&type=bus_station&searchCoord=127.111,37.394" > "%~dp0sr_meta.txt" 2>&1

echo === allSearch type=all ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0sr2.txt" -w "sr2 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&type=all&searchCoord=127.111,37.394" >> "%~dp0sr_meta.txt" 2>&1

echo === allSearch coord=127.111;37.394 semicolon ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0sr3.txt" -w "sr3 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&type=bus_station&searchCoord=127.111;37.394" >> "%~dp0sr_meta.txt" 2>&1

echo === bus stations search direct ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept: application/json" -o "%~dp0sr4.txt" -w "sr4 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&type=bus_station" >> "%~dp0sr_meta.txt" 2>&1

echo === without searchCoord but different lang ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -H "Accept-Language: ko" -o "%~dp0sr5.txt" -w "sr5 HTTP=%%{http_code}  size=%%{size_download}\n" "https://map.naver.com/p/api/search/allSearch?query=%Q%&types=bus_station&searchCoord=127.111,37.394" >> "%~dp0sr_meta.txt" 2>&1

echo DONE
