# AlarmCard - PC에서 실행하기 (Google 공식 Android Emulator 자동 셋업)
#
# 최초 실행 시:
#   - JDK 17 확인 (없으면 안내 후 종료)
#   - Android SDK cmdline-tools 다운로드
#   - platform-tools / emulator / system-images;android-34;google_apis;x86_64 설치
#   - AVD "alarmcard_avd" 생성
#   - 에뮬레이터 부팅 → APK 설치 → 앱 실행
# 재실행:
#   - 이미 있는 SDK/AVD 재사용, 곧바로 부팅 및 앱 재설치
#
# 필요:
#   - Windows 10/11 64-bit
#   - Hyper-V 혹은 WHPX/HAXM (가상화 지원). BIOS에서 Intel VT-x/AMD-V 활성화되어 있어야 원활.
#   - 인터넷 (최초 2~4GB 다운로드)

param(
  [string]$SdkRoot = "$env:USERPROFILE\AndroidSDK",
  [string]$AvdName = "alarmcard_avd",
  [string]$ApiLevel = "34",
  [string]$SysImage = "system-images;android-34;google_apis;x86_64",
  [string]$Abi = "google_apis/x86_64",
  [string]$ApkPath = "c:\Users\user\Desktop\alarmcard\dist\alarmcard-debug-apk\app-debug.apk",
  [string]$AppId  = "com.jpark.alarmcard.debug",
  [string]$LauncherActivity = "com.jpark.alarmcard.ui.MainActivity"
)
$ErrorActionPreference = "Stop"

function Info($m) { Write-Host "[+] $m" -ForegroundColor Cyan }
function Warn($m) { Write-Host "[!] $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "[x] $m" -ForegroundColor Red; exit 1 }

# --- JDK 확인 ---
$javaOk = $false
try {
  $v = & java -version 2>&1
  if ($LASTEXITCODE -eq 0) { $javaOk = $true; Info "Java found: $($v -join ' ')" }
} catch {}
if (-not $javaOk) {
  # winget 자동 설치 시도
  Warn "JDK 미설치. winget으로 Temurin 17 자동 설치 시도합니다."
  try {
    winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements
  } catch {
    Die "JDK 17 설치 실패. 수동 설치 후 재실행: https://adoptium.net/temurin/releases/?version=17"
  }
  # 새로 설치된 java 경로 찾기
  $jdkDir = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "jdk-17*" } | Select-Object -First 1
  if ($jdkDir) {
    $env:JAVA_HOME = $jdkDir.FullName
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    Info "JAVA_HOME=$env:JAVA_HOME"
  } else {
    Die "Java 설치는 됐지만 경로를 못 찾음. 새 PowerShell 창을 열고 다시 실행하세요."
  }
}

# --- SDK cmdline-tools 다운로드 ---
$cmdlineDir = Join-Path $SdkRoot "cmdline-tools\latest"
$sdkmanager = Join-Path $cmdlineDir "bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) {
  Info "cmdline-tools 다운로드 중..."
  New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
  $zip = Join-Path $env:TEMP "cmdline-tools.zip"
  # 최신 안정 링크 (2024~)
  $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
  Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
  $stage = Join-Path $env:TEMP "cmdline-tools-stage"
  if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
  Expand-Archive -Path $zip -DestinationPath $stage -Force
  New-Item -ItemType Directory -Force -Path (Join-Path $SdkRoot "cmdline-tools") | Out-Null
  Move-Item -Path (Join-Path $stage "cmdline-tools") -Destination $cmdlineDir -Force
  Info "cmdline-tools 설치 완료: $cmdlineDir"
}

$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$SdkRoot\platform-tools;$SdkRoot\emulator;$cmdlineDir\bin;" + $env:Path

# --- 필수 패키지 설치 ---
Info "SDK 패키지 설치/업데이트 중 (라이선스 자동 수락)..."
"y`ny`ny`ny`ny`ny`n" | & $sdkmanager --licenses | Out-Null
& $sdkmanager "platform-tools" "emulator" "platforms;android-$ApiLevel" "$SysImage"
if ($LASTEXITCODE -ne 0) { Die "sdkmanager 설치 실패" }

# --- AVD 생성 ---
$avdmanager = Join-Path $cmdlineDir "bin\avdmanager.bat"
$existing = & $avdmanager list avd 2>$null | Select-String -Pattern "^\s+Name:\s+$AvdName$"
if (-not $existing) {
  Info "AVD 생성: $AvdName"
  "no`n" | & $avdmanager create avd -n $AvdName -k $SysImage --device "pixel_5" --force
} else {
  Info "기존 AVD 재사용: $AvdName"
}

# --- 에뮬레이터 부팅 (백그라운드) ---
$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$emulator = Join-Path $SdkRoot "emulator\emulator.exe"

$isBooted = $false
try {
  $devs = & $adb devices
  if ($devs -match "emulator-\d+\s+device") { $isBooted = $true }
} catch {}

if (-not $isBooted) {
  Info "에뮬레이터 부팅 시작 (별도 창)... 첫 부팅은 수 분 걸릴 수 있습니다."
  Start-Process -FilePath $emulator -ArgumentList @(
    "-avd", $AvdName,
    "-no-snapshot-save",
    "-netdelay", "none",
    "-netspeed", "full"
  ) -WindowStyle Minimized | Out-Null
}

Info "adb 서버 시작 + 디바이스 대기..."
& $adb start-server | Out-Null
& $adb wait-for-device
Info "부팅 완료 대기 (sys.boot_completed=1)..."
$maxWait = 300
for ($i = 0; $i -lt $maxWait; $i++) {
  $prop = & $adb shell getprop sys.boot_completed 2>$null
  if ($prop -match "1") { break }
  Start-Sleep -Seconds 2
}
Info "디바이스 준비 완료"

# --- APK 설치 및 실행 ---
if (-not (Test-Path $ApkPath)) {
  Die "APK 없음: $ApkPath  (먼저 scripts\download.ps1 로 다운로드하세요)"
}

Info "기존 앱 언인스톨(있으면)..."
& $adb uninstall $AppId 2>$null | Out-Null

Info "APK 설치: $ApkPath"
& $adb install -r $ApkPath
if ($LASTEXITCODE -ne 0) { Die "APK 설치 실패" }

Info "앱 실행 중..."
& $adb shell am start -n "$AppId/$LauncherActivity"

Info "완료. 에뮬레이터 창에서 AlarmCard 앱을 확인하세요."
Info "종료하려면 에뮬레이터 창을 닫거나: adb emu kill"
