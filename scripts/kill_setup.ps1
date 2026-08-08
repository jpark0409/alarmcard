Get-Process -Name adb -ErrorAction SilentlyContinue | Stop-Process -Force
Get-CimInstance Win32_Process | Where-Object {
    $_.CommandLine -like "*setup_emu.ps1*" -or $_.CommandLine -like "*avdmanager*"
} | ForEach-Object {
    Write-Host "Killing PID $($_.ProcessId) $($_.Name)"
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}
Write-Host "Done"
