$src = "c:\Users\user\Desktop\alarmcard\scripts\stock_005930.html"
$bytes = [System.IO.File]::ReadAllBytes($src)
Write-Host ("bytes len=" + $bytes.Length)

# Head bytes
Write-Host ("head hex: " + (($bytes[0..80] | ForEach-Object { $_.ToString('x2') }) -join ' '))

# Try charset detection: locate <meta charset=... or content="text/html;charset=...
$ascii = [System.Text.Encoding]::ASCII.GetString($bytes)
$m = [regex]::Match($ascii, '(?i)charset\s*=\s*"?([\w\-]+)"?')
if ($m.Success) { Write-Host ("meta charset raw: " + $m.Groups[1].Value) }

# Try decode with UTF-8 vs EUC-KR
$utf = [System.Text.Encoding]::UTF8.GetString($bytes)
$euc = [System.Text.Encoding]::GetEncoding(51949).GetString($bytes)

# Search for hangul sample near wrap_company
$i = $ascii.IndexOf('wrap_company')
if ($i -gt 0) {
  $ctx_utf = $utf.Substring([math]::Max(0, $i - 10), [math]::Min(400, $utf.Length - $i))
  $ctx_euc = $euc.Substring([math]::Max(0, $i - 10), [math]::Min(400, $euc.Length - $i))
  Write-Host "--- decoded UTF8 near wrap_company ---"
  Write-Host $ctx_utf
  Write-Host "--- decoded EUC-KR near wrap_company ---"
  Write-Host $ctx_euc
}
