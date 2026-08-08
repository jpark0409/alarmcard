param([int]$Minutes = 20)
$log = "c:\Users\user\Desktop\alarmcard\scripts\deploy_new.log"
$deadline = (Get-Date).AddMinutes($Minutes)
while ((Get-Date) -lt $deadline) {
  $tail = Get-Content $log -Tail 5 -ErrorAction SilentlyContinue
  if ($tail -match 'DONE|build not success|no apk|no run found|Timed out') { break }
  Start-Sleep -Seconds 20
}
Get-Content $log -Tail 80
