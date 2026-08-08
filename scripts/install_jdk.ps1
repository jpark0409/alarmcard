# winget으로 Temurin JDK 17 설치. --source winget 지정으로 msstore 상관없이 진행.
$ErrorActionPreference = "Continue"
& winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements --source winget --silent
$exit = $LASTEXITCODE
Write-Host "winget exit=$exit"
# 설치 확인
$found = @()
Get-ChildItem -Path "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $found += $_.FullName }
if ($found.Count -eq 0) { Write-Host "no jdk installed"; exit 1 } else { Write-Host ("JDK: " + $found[0]); exit 0 }
