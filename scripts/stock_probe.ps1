$src = "c:\Users\user\Desktop\alarmcard\scripts\stock_005930.html"
$dst = "c:\Users\user\Desktop\alarmcard\scripts\stock_005930_utf8.html"
$bytes = [System.IO.File]::ReadAllBytes($src)
$enc = [System.Text.Encoding]::GetEncoding(51949)
$text = $enc.GetString($bytes)
[System.IO.File]::WriteAllText($dst, $text, [System.Text.Encoding]::UTF8)
Write-Host ("converted len=" + $text.Length)

$patterns = @('no_today','no_up','no_down','no_exday','_nowVal','GetCommonContext','wrap_company','tah p11','ico ','blind','og:title','og:description')
foreach ($p in $patterns) {
  $mm = [regex]::Matches($text, [regex]::Escape($p))
  Write-Host ("pattern '" + $p + "' hits: " + $mm.Count)
}

$m = [regex]::Match($text, 'no_today')
if ($m.Success) {
  $start = [math]::Max(0, $m.Index - 50)
  $len = [math]::Min(1500, $text.Length - $start)
  Write-Host "--- no_today context ---"
  Write-Host $text.Substring($start, $len)
}

$m2 = [regex]::Match($text, 'no_exday')
if ($m2.Success) {
  $start = [math]::Max(0, $m2.Index - 50)
  $len = [math]::Min(1500, $text.Length - $start)
  Write-Host "--- no_exday context ---"
  Write-Host $text.Substring($start, $len)
}

$m3 = [regex]::Match($text, '<title>([^<]+)</title>')
if ($m3.Success) { Write-Host ("title: " + $m3.Groups[1].Value) }

$m4 = [regex]::Match($text, 'og:title" content="([^"]+)"')
if ($m4.Success) { Write-Host ("og:title: " + $m4.Groups[1].Value) }

$m5 = [regex]::Match($text, 'og:description" content="([^"]+)"')
if ($m5.Success) { Write-Host ("og:description: " + $m5.Groups[1].Value) }

$m6 = [regex]::Match($text, 'wrap_company')
if ($m6.Success) {
  $start = [math]::Max(0, $m6.Index)
  $len = [math]::Min(800, $text.Length - $start)
  Write-Host "--- wrap_company context ---"
  Write-Host $text.Substring($start, $len)
}
