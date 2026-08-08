$adb = "$env:USERPROFILE\AndroidSDK\platform-tools\adb.exe"

Write-Host "=== adb devices ==="
& $adb devices

Write-Host ""
Write-Host "=== emulator: ping ==="
& $adb shell "ping -c 2 8.8.8.8" 2>&1

Write-Host ""
Write-Host "=== emulator: curl m.stock.naver.com  ==="
& $adb shell "curl -sI https://m.stock.naver.com/ -o /dev/null -w '%{http_code}\n'" 2>&1

Write-Host ""
Write-Host "=== emulator: curl pubtrans.map.naver.com ==="
& $adb shell "curl -sI https://pubtrans.map.naver.com/ -o /dev/null -w '%{http_code}\n'" 2>&1

Write-Host ""
Write-Host "=== emulator: curl map.naver.com  ==="
& $adb shell "curl -sI https://map.naver.com/ -o /dev/null -w '%{http_code}\n'" 2>&1

Write-Host ""
Write-Host "=== logcat: AlarmCard (Timber) - last 200 lines ==="
& $adb logcat -d -t 400 | Select-String -Pattern "AlarmCard|Timber|NaverMap|NaverStock|NaverFx|com.jpark.alarmcard|okhttp|OkHttp|Retrofit"
