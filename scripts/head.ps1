param([string]$path, [int]$len = 3000)
$t = Get-Content $path -Raw
$t.Substring(0, [Math]::Min($len, $t.Length))
