$out = @()
$w = Get-Command winget -ErrorAction SilentlyContinue
if ($w) { $out += "winget: $($w.Source)" } else { $out += "winget: NOT FOUND" }
$c = Get-Command choco -ErrorAction SilentlyContinue
if ($c) { $out += "choco: $($c.Source)" } else { $out += "choco: NOT FOUND" }
$j = Get-Command java -ErrorAction SilentlyContinue
if ($j) { $out += "java: $($j.Source)" } else { $out += "java: NOT FOUND" }
$out += "OS: " + [Environment]::OSVersion.VersionString
$out += "Arch: $env:PROCESSOR_ARCHITECTURE"
# Hyper-V / WHPX 확인
try {
  $info = & systeminfo.exe 2>$null | Select-String -Pattern "Hyper-V|VT-x|Virtualization|가상화" -SimpleMatch:$false
  if ($info) { foreach ($l in $info) { $out += "sysinfo: $($l.ToString().Trim())" } }
} catch {}
$out | Out-File -Encoding utf8 -Force "c:\Users\user\Desktop\alarmcard\scripts\env.txt"
