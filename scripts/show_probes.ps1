$dir = "c:\Users\user\Desktop\alarmcard\scripts"
Get-ChildItem "$dir\probe*.txt" | ForEach-Object {
  Write-Host ("=== " + $_.Name + " (" + $_.Length + " bytes) ===")
  Get-Content $_.FullName -TotalCount 15 -ErrorAction SilentlyContinue
  Write-Host ""
}
foreach ($f in @("probe2.html","probe4.txt","probe5.txt")) {
  $p = Join-Path $dir $f
  if (Test-Path $p) {
    Write-Host ("=== " + $f + " (" + (Get-Item $p).Length + " bytes) ===")
    Get-Content $p -TotalCount 5 -ErrorAction SilentlyContinue
    Write-Host ""
  }
}
