Write-Host "=== download.log ==="
if (Test-Path "c:\Users\user\Desktop\alarmcard\scripts\download.log") {
  Get-Content "c:\Users\user\Desktop\alarmcard\scripts\download.log"
} else { Write-Host "no log" }
Write-Host ""
Write-Host "=== dist directory ==="
if (Test-Path "c:\Users\user\Desktop\alarmcard\dist") {
  Get-ChildItem -Recurse "c:\Users\user\Desktop\alarmcard\dist" | ForEach-Object {
    "{0}  {1} bytes" -f $_.FullName, $_.Length
  }
} else {
  Write-Host "no dist dir yet"
}
