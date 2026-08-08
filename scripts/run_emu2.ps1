# 이미 SDK와 시스템 이미지가 설치된 상태에서 AVD 생성부터 이어서 진행.
param(
  [string]$SdkRoot = "$env:USERPROFILE\AndroidSDK",
  [string]$AvdName = "alarmcard_avd",
  [string]$SysImage = "system-images;android-34;google_apis;x86_64",
  [string]$ApkPath = "c:\Users\user\Desktop\alarmcard\dist\alarmcard-debug-apk_20260808_143301\app-debug.apk",
  [string]$AppId  = "com.jpark.alarmcard.debug",
  [string]$LauncherActivity = "com.jpark.alarmcard.ui.MainActivity",
  [string]$LogFile = "c:\Users\user\Desktop\alarmcard\scripts\run_emu2.log"
)
$ErrorActionPreference = "Continue"
if (Test-Path $LogFile) { Remove-Item $LogFile -Force }
function Log($m) { $t = Get-Date -Format 'HH:mm:ss'; $line = "[$t] $m"; Add-Content -Path $LogFile -Value $line; Write-Host $line }

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$env:JAVA_HOME\bin;$SdkRoot\platform-tools;$SdkRoot\emulator;$SdkRoot\cmdline-tools\latest\bin;" + $env:Path

$avdmanager = Join-Path $SdkRoot "cmdline-tools\latest\bin\avdmanager.bat"
$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$emulator = Join-Path $SdkRoot "emulator\emulator.exe"

Log "avdmanager: $avdmanager (exists=$(Test-Path $avdmanager))"
Log "adb: $adb (exists=$(Test-Path $adb))"
Log "emulator: $emulator (exists=$(Test-Path $emulator))"

# --- Create AVD via stdin "no" ---
Log "Creating AVD $AvdName..."
"no`n" | & $avdmanager create avd -n $AvdName -k $SysImage --device "pixel_5" --force 2>&1 | Tee-Object -FilePath $LogFile -Append | Out-Null
Log "avdmanager exit=$LASTEXITCODE"

# --- Verify ---
$list = & $avdmanager list avd 2>&1
Log ("avdmanager list avd (top):`n" + ($list -join "`r`n"))

# --- Launch emulator in a new process ---
$existing = & $adb devices 2>$null
if ($existing -match "emulator-\d+\s+device") {
  Log "Emulator already running."
} else {
  Log "Starting emulator (new window)..."
  Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName, "-no-snapshot-save") | Out-Null
}

Log "adb start-server"
& $adb start-server | Out-Null
Log "Waiting for device (with timeout)..."
# adb wait-for-device with our own polling to avoid blocking forever
$deadline = (Get-Date).AddMinutes(10)
$connected = $false
while ((Get-Date) -lt $deadline) {
  $d = & $adb devices 2>$null
  if ($d -match "emulator-\d+\s+device") { $connected = $true; break }
  Start-Sleep -Seconds 3
}
if (-not $connected) { Log "[FATAL] emulator did not connect via adb in 10 min"; exit 2 }
Log "Device connected"

Log "Waiting for boot_completed..."
$booted = $false
for ($i = 0; $i -lt 300; $i++) {
  $p = & $adb shell getprop sys.boot_completed 2>$null
  if ($p -match "1") { $booted = $true; break }
  Start-Sleep -Seconds 2
}
if (-not $booted) { Log "[WARN] boot_completed not detected, continuing anyway" }
Log "Boot completed."

if (-not (Test-Path $ApkPath)) { Log "[FATAL] APK not found: $ApkPath"; exit 3 }
& $adb uninstall $AppId 2>$null | Out-Null
Log "Installing APK..."
& $adb install -r $ApkPath 2>&1 | Tee-Object -FilePath $LogFile -Append
if ($LASTEXITCODE -ne 0) { Log "[FATAL] install failed"; exit 4 }
Log "Launching app..."
& $adb shell am start -n "$AppId/$LauncherActivity" 2>&1 | Tee-Object -FilePath $LogFile -Append
Log "DONE"
