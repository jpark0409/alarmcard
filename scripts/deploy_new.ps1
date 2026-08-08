# 1. 최신 커밋에 대한 CI run 찾기
# 2. 성공까지 폴링
# 3. 새 APK 다운로드
# 4. 에뮬레이터에 재설치 및 실행
param(
  [string]$Repo = "jpark0409/alarmcard",
  [string]$Sha = "700beaf",
  [string]$AppId = "com.jpark.alarmcard.debug",
  [string]$LauncherActivity = "com.jpark.alarmcard.ui.MainActivity",
  [string]$LogFile = "c:\Users\user\Desktop\alarmcard\scripts\deploy_new.log"
)
$ErrorActionPreference = "Continue"
if (Test-Path $LogFile) { Remove-Item $LogFile -Force }
function Log($m) { Add-Content -Path $LogFile -Value ("[" + (Get-Date -Format 'HH:mm:ss') + "] " + $m); Write-Host $m }

Log "Looking for run with head_sha $Sha ..."
$runId = 0
for ($t = 0; $t -lt 12; $t++) {
  try {
    $runs = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs?per_page=10"
    $match = $runs.workflow_runs | Where-Object { $_.head_sha -like "$Sha*" } | Select-Object -First 1
    if ($match) { $runId = $match.id; break }
  } catch { Log ("err: " + $_.Exception.Message) }
  Start-Sleep -Seconds 5
}
if ($runId -eq 0) { Log "no run found"; exit 2 }
Log "runId=$runId"

# 폴링
$deadline = (Get-Date).AddMinutes(30)
$run = $null
while ((Get-Date) -lt $deadline) {
  try {
    $run = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$runId"
    Log ("status=" + $run.status + " conclusion=" + $run.conclusion)
  } catch { Log ("poll err: " + $_.Exception.Message) }
  if ($run -and $run.status -eq 'completed') { break }
  Start-Sleep -Seconds 20
}
if (-not $run -or $run.status -ne 'completed' -or $run.conclusion -ne 'success') {
  Log "build not success"; exit 3
}

# Artifact 다운로드
$arts = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$runId/artifacts"
$art = $arts.artifacts | Where-Object { $_.name -like "*apk*" } | Select-Object -First 1
if (-not $art) { $art = $arts.artifacts[0] }
$parts = $Repo.Split('/')
$dlUrl = "https://nightly.link/$($parts[0])/$($parts[1])/actions/artifacts/$($art.id).zip"

$outDir = "c:\Users\user\Desktop\alarmcard\dist"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$zip = Join-Path $outDir ("$($art.name)_$stamp.zip")
Log "downloading: $dlUrl"
Invoke-WebRequest -Uri $dlUrl -OutFile $zip -UseBasicParsing
Log "zip: $((Get-Item $zip).Length) bytes"
$extract = Join-Path $outDir ("$($art.name)_$stamp")
if (Test-Path $extract) { Remove-Item $extract -Recurse -Force }
Expand-Archive -Path $zip -DestinationPath $extract -Force

$apk = Get-ChildItem -Recurse -Path $extract -Filter *.apk | Select-Object -First 1
if (-not $apk) { Log "no apk"; exit 4 }
Log "APK: $($apk.FullName) ($([math]::Round($apk.Length/1MB,2)) MB)"

# 에뮬레이터에 재설치
$adb = "$env:USERPROFILE\AndroidSDK\platform-tools\adb.exe"
& $adb devices | Out-Null
Log "adb uninstall $AppId ..."
& $adb uninstall $AppId 2>&1 | Out-Null
Log "adb install -r ..."
& $adb install -r $apk.FullName 2>&1 | ForEach-Object { Log $_ }
Log "adb am start ..."
& $adb shell am start -n "$AppId/$LauncherActivity" 2>&1 | ForEach-Object { Log $_ }
Log "DONE"
