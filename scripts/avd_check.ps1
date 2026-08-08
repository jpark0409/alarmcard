$sdk = "$env:USERPROFILE\AndroidSDK"
$avdmanager = Join-Path $sdk "cmdline-tools\latest\bin\avdmanager.bat"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

Write-Host "=== avdmanager list avd ==="
& $avdmanager list avd

Write-Host ""
Write-Host "=== Files ==="
Get-ChildItem -Path "$env:USERPROFILE\.android\avd" -ErrorAction SilentlyContinue
