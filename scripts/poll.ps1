param(
  [Int64]$RunId = 31240740764,
  [string]$Repo = "jpark0409/alarmcard",
  [int]$TimeoutMinutes = 40,
  [int]$IntervalSec = 20,
  [string]$LogFile = "c:\Users\user\Desktop\alarmcard\scripts\poll.log"
)
$ErrorActionPreference = "Continue"
if (Test-Path $LogFile) { Remove-Item $LogFile -Force }

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$last = ""
while ((Get-Date) -lt $deadline) {
  try {
    $j = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$RunId"
    $line = "[{0}] status={1} conclusion={2}" -f (Get-Date -Format 'HH:mm:ss'), $j.status, $j.conclusion
  } catch {
    $line = "[{0}] ERR: {1}" -f (Get-Date -Format 'HH:mm:ss'), $_.Exception.Message
  }
  Add-Content -Path $LogFile -Value $line
  $last = $line
  if ($j -and $j.status -eq 'completed') { break }
  Start-Sleep -Seconds $IntervalSec
}
Add-Content -Path $LogFile -Value ("FINAL: " + $last)
