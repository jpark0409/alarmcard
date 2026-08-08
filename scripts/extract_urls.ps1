$dir = "c:\Users\user\Desktop\alarmcard\scripts"
$files = @("index.js","chunk.js")
$patterns = @(
  '"(https?://[^"]*(pubtrans|bus|route|station|search)[^"]*)"',
  "'(https?://[^']*(pubtrans|bus|route|station|search)[^']*)'",
  '"(/api/[^"]*(bus|route|station|search|pubtrans)[^"]*)"',
  "'(/api/[^']*(bus|route|station|search|pubtrans)[^']*)'"
)
$hits = New-Object System.Collections.Generic.HashSet[string]
foreach ($f in $files) {
  $p = Join-Path $dir $f
  if (-not (Test-Path $p)) { continue }
  $text = [System.IO.File]::ReadAllText($p)
  foreach ($pat in $patterns) {
    $rx = [regex]::new($pat)
    foreach ($m in $rx.Matches($text)) {
      $u = $m.Groups[1].Value
      [void]$hits.Add($u)
    }
  }
}
$hits | Sort-Object | Out-File -Encoding utf8 -Force (Join-Path $dir "urls.txt")
Write-Host ("count=" + $hits.Count)
