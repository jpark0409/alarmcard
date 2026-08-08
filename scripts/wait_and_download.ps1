# Poll GitHub Actions run until completion, then download artifact.
param(
  [string]$Repo = "jpark0409/alarmcard",
  [Int64]$RunId = 0,
  [int]$TimeoutMinutes = 40
)

$ErrorActionPreference = "Stop"

if ($RunId -eq 0) {
  Write-Host "Fetching latest run..."
  $runs = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs?per_page=1"
  $RunId = $runs.workflow_runs[0].id
}
Write-Host "Watching run: $RunId"

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$run = $null
while ((Get-Date) -lt $deadline) {
  $run = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$RunId"
  $ts = Get-Date -Format 'HH:mm:ss'
  Write-Host "[$ts] status=$($run.status) conclusion=$($run.conclusion)"
  if ($run.status -eq 'completed') { break }
  Start-Sleep -Seconds 20
}

if ($run.status -ne 'completed') {
  Write-Host "Timed out after $TimeoutMinutes minutes."
  exit 2
}

if ($run.conclusion -ne 'success') {
  Write-Host "Run finished with conclusion: $($run.conclusion). Fetching failed job logs summary..."
  $jobs = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$RunId/jobs"
  foreach ($j in $jobs.jobs) {
    Write-Host "-- job: $($j.name)  conclusion=$($j.conclusion)"
    foreach ($s in $j.steps) {
      Write-Host ("   step: {0}  conclusion={1}" -f $s.name, $s.conclusion)
    }
  }
  exit 3
}

Write-Host "Run succeeded. Listing artifacts..."
$arts = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$RunId/artifacts"
if ($arts.total_count -eq 0) {
  Write-Host "No artifacts."
  exit 4
}
$art = $arts.artifacts | Where-Object { $_.name -like "*apk*" } | Select-Object -First 1
if (-not $art) { $art = $arts.artifacts[0] }
Write-Host "Artifact: $($art.name) ($($art.size_in_bytes) bytes)"

# Anonymous download for public repos: use the archive_download_url via redirect
# Without a token, GitHub returns 401. We instead use the public "nightly" pattern:
# https://nightly.link/{owner}/{repo}/actions/artifacts/{artifact_id}.zip
$parts = $Repo.Split('/')
$owner = $parts[0]; $repoName = $parts[1]
$dlUrl = "https://nightly.link/$owner/$repoName/actions/artifacts/$($art.id).zip"
$outDir = Join-Path (Get-Location) "dist"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$zipPath = Join-Path $outDir "$($art.name).zip"
Write-Host "Downloading via nightly.link: $dlUrl"
Invoke-WebRequest -Uri $dlUrl -OutFile $zipPath -UseBasicParsing
Write-Host "Downloaded: $zipPath"

# Extract
$extractDir = Join-Path $outDir $art.name
if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
Expand-Archive -Path $zipPath -DestinationPath $extractDir -Force
Write-Host "Extracted to: $extractDir"
Get-ChildItem -Path $extractDir -Recurse -Filter *.apk | ForEach-Object {
  Write-Host "APK: $($_.FullName)  ($([math]::Round($_.Length/1MB,2)) MB)"
}
