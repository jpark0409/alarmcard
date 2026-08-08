param([int]$Seconds = 240)
Start-Sleep -Seconds $Seconds
Get-Content 'c:\Users\user\Desktop\alarmcard\scripts\watch.log' | Out-File -Encoding utf8 -Force 'c:\Users\user\Desktop\alarmcard\scripts\snap.txt'
