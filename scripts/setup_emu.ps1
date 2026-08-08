# AlarmCard - 안드로이드 에뮬레이터 셋업 & 실행 (JDK는 미리 설치돼 있어야 함)
# curl.exe로 큰 파일 안정 다운로드. sdkmanager로 SDK 설치. AVD 생성 및 부팅. APK 설치.

param(
  [string]$SdkRoot = "$env:USERPROFILE\AndroidSDK",
  [string]$AvdName = "alarmcard_avd",
  [string]$ApiLevel = "34",
  [string]$SysImage = "system-images;android-34;google_apis;x86_64",
  [string]$ApkPath = "c:\Users\user\Desktop\alarmcard\dist\alarmcard-debug-apk_20260808_143301\app-debug.apk",
  [string]$AppId  = "com.jpark.alarmcard.debug",
  [string]$LauncherActivity = "com.jpark.alarmcard.ui.MainActivity",
  [string]$LogFile = "c:\Users\user\Desktop\alarmcard\scripts\setup_emu.log"
)
$ErrorActionPreference = "Continue"
if (Test-Path $LogFile) { Remove-Item $LogFile -Force }
function Log($m) { $t = Get-Date -Format 'HH:mm:ss'; $line = "[$t] $m"; Add-Content -Path $LogFile -Value $line; Write-Host $line }
function Die($m) { Log "[FATAL] $m"; exit 1 }

# --- JDK detect ---
Log "Detecting JDK..."
$candidates = @()
Get-ChildItem -Path "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "jdk-17*" } | ForEach-Object { $candidates += $_.FullName }
Get-ChildItem -Path "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "jdk-17*" -or $_.Name -like "*jdk*17*" } | ForEach-Object { $candidates += $_.FullName }
if ($candidates.Count -eq 0) { Die "JDK 17 이 감지되지 않음. 먼저 scripts\install_jdk.ps1 로 설치하세요." }
$jdkDir = $candidates[0]
$env:JAVA_HOME = $jdkDir
$env:Path = "$jdkDir\bin;" + $env:Path
Log "JAVA_HOME=$env:JAVA_HOME"

# --- Download cmdline-tools with curl.exe ---
$cmdlineDir = Join-Path $SdkRoot "cmdline-tools\latest"
$sdkmanager = Join-Path $cmdlineDir "bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) {
  Log "Downloading Android cmdline-tools (curl, resumable)..."
  New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
  $zip = Join-Path $env:TEMP "cmdline-tools.zip"
  # 기존 0바이트 파일 등 정리
  if (Test-Path $zip) { Remove-Item $zip -Force }
  $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
  # curl로 재시도 5회, 이어받기 지원
  & curl.exe -L --retry 5 --retry-delay 5 --retry-connrefused -o "$zip" "$url"
  if ($LASTEXITCODE -ne 0) { Die "cmdline-tools download failed (curl exit=$LASTEXITCODE)" }
  $size = (Get-Item $zip).Length
  Log "Downloaded cmdline-tools.zip ($size bytes)"
  if ($size -lt 10MB) { Die "cmdline-tools.zip too small ($size). Something went wrong." }

  $stage = Join-Path $env:TEMP "cmdline-tools-stage"
  if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
  Expand-Archive -Path $zip -DestinationPath $stage -Force
  New-Item -ItemType Directory -Force -Path (Join-Path $SdkRoot "cmdline-tools") | Out-Null
  if (Test-Path $cmdlineDir) { Remove-Item $cmdlineDir -Recurse -Force }
  Move-Item -Path (Join-Path $stage "cmdline-tools") -Destination $cmdlineDir -Force
  Log "cmdline-tools installed at $cmdlineDir"
}

$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$SdkRoot\platform-tools;$SdkRoot\emulator;$cmdlineDir\bin;" + $env:Path

# --- Pre-accept licenses ---
Log "Pre-accepting licenses..."
$licDir = Join-Path $SdkRoot "licenses"
New-Item -ItemType Directory -Force -Path $licDir | Out-Null
$licMap = @{}
$licMap["android-sdk-license"] = "24333f8a63b6825ea9c5514f83c2829b004d1fee`r`n8933bad161af4178b1185d1a37fbf41ea5269c55"
$licMap["android-googletv-license"] = "601085b94cd77f0b54ff86406957099ebe79c4d6"
$licMap["android-sdk-arm-dbt-license"] = "859f317696f67ef3d7f30a50a5560e7834b43903"
$licMap["android-sdk-preview-license"] = "84831b9409646a918e30573bab4c9c91346d8abd"
$licMap["google-gdk-license"] = "33b6a2b64607f11b759f320ef9dff4ae5c47d97a"
$licMap["intel-android-extra-license"] = "d975f751698a77b662f1254ddbeed3901e976f5a"
$licMap["mips-android-sysimage-license"] = "e9acab5b5fbb560a72cfaecce8946896ff6aab9d"
foreach ($k in $licMap.Keys) {
  $f = Join-Path $licDir $k
  $licMap[$k] | Set-Content -Path $f -Encoding ASCII
}

# --- SDK packages ---
Log "Installing SDK packages (may take a while, several GB)..."
$pkgs = @("platform-tools", "emulator", "platforms;android-$ApiLevel", "$SysImage")
$smArgs = @("--sdk_root=$SdkRoot") + $pkgs
& $sdkmanager @smArgs 2>&1 | Tee-Object -FilePath $LogFile -Append | Out-Null
if ($LASTEXITCODE -ne 0) { Log "sdkmanager exit=$LASTEXITCODE" }

# --- Create AVD ---
$avdmanager = Join-Path $cmdlineDir "bin\avdmanager.bat"
$listOut = & $avdmanager list avd 2>$null
$exists = $false
if ($listOut) {
  foreach ($line in $listOut) {
    if ($line -match "^\s*Name:\s+$AvdName\s*$") { $exists = $true; break }
  }
}
if ($exists) {
  Log "AVD exists: $AvdName"
} else {
  Log "Creating AVD $AvdName..."
  "no`n" | & $avdmanager create avd -n $AvdName -k $SysImage --device "pixel_5" --force 2>&1 | Tee-Object -FilePath $LogFile -Append | Out-Null
}

# --- Launch emulator ---
$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$emulator = Join-Path $SdkRoot "emulator\emulator.exe"
if (-not (Test-Path $emulator)) { Die "emulator.exe not found at $emulator. Check SDK install." }
if (-not (Test-Path $adb)) { Die "adb.exe not found. Check SDK install." }

$booted = $false
$devs = & $adb devices 2>$null
if ($devs -match "emulator-\d+\s+device") { $booted = $true }
if (-not $booted) {
  Log "Booting emulator..."
  Start-Process -FilePath $emulator -ArgumentList @(
    "-avd", $AvdName,
    "-no-snapshot-save",
    "-netdelay", "none",
    "-netspeed", "full"
  ) | Out-Null
}

Log "Waiting for device..."
& $adb start-server | Out-Null
# wait-for-device 대신 우리 손으로 폴링 (block 방지)
$dl = (Get-Date).AddMinutes(10)
$conn = $false
while ((Get-Date) -lt $dl) {
  $d = & $adb devices 2>$null
  if ($d -match "emulator-\d+\s+device") { $conn = $true; break }
  Start-Sleep -Seconds 3
}
if (-not $conn) { Die "Emulator did not connect via adb in 10 minutes" }
Log "Waiting for boot completion (up to 8 min)..."
for ($i = 0; $i -lt 240; $i++) {
  $p = & $adb shell getprop sys.boot_completed 2>$null
  if ($p -match "1") { break }
  Start-Sleep -Seconds 2
}
Log "Device ready"

# --- Install APK ---
if (-not (Test-Path $ApkPath)) { Die "APK not found: $ApkPath" }
Log "Uninstalling previous app (if any)..."
& $adb uninstall $AppId 2>$null | Out-Null
Log "Installing APK..."
& $adb install -r $ApkPath 2>&1 | Tee-Object -FilePath $LogFile -Append
if ($LASTEXITCODE -ne 0) { Die "APK install failed" }

Log "Launching app..."
& $adb shell am start -n "$AppId/$LauncherActivity" 2>&1 | Tee-Object -FilePath $LogFile -Append
Log "DONE. AlarmCard is running on emulator."
