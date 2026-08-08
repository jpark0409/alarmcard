@echo off
setlocal
set UA=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
set REF=https://map.naver.com/p/bus/bus-route/6012/bus-route/20025664

echo === Download main JS bundle ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0index.js" "https://maps-service.pstatic.net/pcweb_navermap_v5/260806-7d4fd09/index-7d4fd09.js"
echo === Download preload chunk ===
curl.exe -sL -A "%UA%" -H "Referer: %REF%" -o "%~dp0chunk.js" "https://maps-service.pstatic.net/pcweb_navermap_v5/260806-7d4fd09/js/2083918811.BjvK1eA-.js"

echo === grep API endpoints ===
findstr /R /C:"/api/" "%~dp0index.js" > "%~dp0api_hits.txt" 2>&1
findstr /R /C:"pubtrans" "%~dp0index.js" > "%~dp0pubtrans_hits.txt" 2>&1
findstr /R /C:"bus-route" "%~dp0index.js" > "%~dp0busroute_hits.txt" 2>&1
findstr /R /C:"/api/" "%~dp0chunk.js" >> "%~dp0api_hits.txt" 2>&1
findstr /R /C:"pubtrans" "%~dp0chunk.js" >> "%~dp0pubtrans_hits.txt" 2>&1
findstr /R /C:"bus-route" "%~dp0chunk.js" >> "%~dp0busroute_hits.txt" 2>&1

echo DONE
