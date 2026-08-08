$rows = Get-CimInstance Win32_Process |
    Where-Object { $_.Name -match 'powershell|winget|adb|emulator|sdkmanager|avdmanager|java' } |
    Select-Object ProcessId, Name, @{n='CmdLine'; e={$_.CommandLine}}
$rows | Format-Table -AutoSize -Wrap | Out-String -Width 400 | Out-File -Encoding utf8 -Force "c:\Users\user\Desktop\alarmcard\scripts\procs.txt"
