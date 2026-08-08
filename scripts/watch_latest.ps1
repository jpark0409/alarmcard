param(
  [string]$Repo = "jpark0409/alarmcard",
  [string]$Sha = "e788a1a",
  [int]$TimeoutMinutes = 40,
  [int]$IntervalSec = 20,
  [string]$LogFile = "c:\Users\user\Desktop\alarmcard\scripts\watch.log"
)
$ErrorActionPreference = "Continue"
if (Test-Path $LogFile) { Remove-Item $LogFile -Force }
function Log($m) { Add-Content -Path $LogFile -Value $m; Write-Host $m }

# 새로 트리거된 run을 head_sha 로 찾음. 없으면 latest 1개 사용.
Log "Looking for a run matching sha $Sha ..."
$runId = 0
for ($t = 0; $t -lt 12; $t++) {
  try {
    $runs = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs?per_page=10"
    $match = $runs.workflow_runs | Where-Object { $_.head_sha -like "$Sha*" } | Select-Object -First 1
    if ($match) { $runId = $match.id; break }
  } catch { Log ("List runs error: " + $_.Exception.Message) }
  Start-Sleep -Seconds 5
}
if ($runId -eq 0) {
  $runs = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs?per_page=1"
  $runId = $runs.workflow_runs[0].id
  Log "Fallback to latest run: $runId"
} else {
  Log "Found run: $runId"
}

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$run = $null
while ((Get-Date) -lt $deadline) {
  try {
    $run = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$runId"
    Log ("[{0}] status={1} conclusion={2}" -f (Get-Date -Format 'HH:mm:ss'), $run.status, $run.conclusion)
  } catch { Log ("poll err: " + $_.Exception.Message) }
  if ($run -and $run.status -eq 'completed') { break }
  Start-Sleep -Seconds $IntervalSec
}

if (-not $run -or $run.status -ne 'completed') { Log "Timeout"; exit 2 }
if ($run.conclusion -ne 'success') {
  Log "Build failed: $($run.conclusion). Fetching job/step summary..."
  $jobs = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$runId/jobs"
  foreach ($j in $jobs.jobs) {
    Log ("-- job: {0}  conclusion={1}" -f $j.name, $j.conclusion)
    foreach ($s in $j.steps) { Log ("   step: {0}  conclusion={1}" -f $s.name, $s.conclusion) }
  }
  exit 3
}

# Download artifact
$arts = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$runId/artifacts"
Log ("artifacts: " + $arts.total_count)
$art = $arts.artifacts | Where-Object { $_.name -like "*apk*" } | Select-Object -First 1
if (-not $art) { $art = $arts.artifacts[0] }
Log ("Chosen: id={0} name={1} size={2}" -f $art.id, $art.name, $art.size_in_bytes)

$parts = $Repo.Split('/')
$dlUrl = "https://nightly.link/$($parts[0])/$($parts[1])/actions/artifacts/$($art.id).zip"
$outDir = "c:\Users\user\Desktop\alarmcard\dist"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$zip = Join-Path $outDir "$($art.name).zip"
Log ("Downloading: " + $dlUrl)
Invoke-WebRequest -Uri $dlUrl -OutFile $zip -UseBasicParsing
Log ("Downloaded " + (Get-Item $zip).Length + " bytes")

$extract = Join-Path $outDir $art.name
if (Test-Path $extract) { Remove-Item $extract -Recurse -Force }
Expand-Archive -Path $zip -DestinationPath $extract -Force
Get-ChildItem -Recurse -Path $extract -Filter *.apk | ForEach-Object {
  Log ("APK: {0}  ({1} MB)" -f $_.FullName, [math]::Round($_.Length/1MB,2))
}
Log "DONE"
