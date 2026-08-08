param(
  [Int64]$RunId = 31240740764,
  [string]$Repo = "jpark0409/alarmcard",
  [string]$LogFile = "c:\Users\user\Desktop\alarmcard\scripts\download.log"
)
$ErrorActionPreference = "Continue"
if (Test-Path $LogFile) { Remove-Item $LogFile -Force }
function Log($m) { Add-Content -Path $LogFile -Value $m }

Log "Listing artifacts..."
$arts = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$RunId/artifacts"
Log ("total_count=" + $arts.total_count)
foreach ($a in $arts.artifacts) { Log (" - id={0} name={1} size={2}" -f $a.id, $a.name, $a.size_in_bytes) }
if ($arts.total_count -eq 0) { Log "No artifacts"; exit 4 }

$art = $arts.artifacts | Where-Object { $_.name -like "*apk*" } | Select-Object -First 1
if (-not $art) { $art = $arts.artifacts[0] }
Log ("Chosen: id=" + $art.id + " name=" + $art.name)

$parts = $Repo.Split('/')
$owner = $parts[0]; $repoName = $parts[1]
$dlUrl = "https://nightly.link/$owner/$repoName/actions/artifacts/$($art.id).zip"

$outDir = "c:\Users\user\Desktop\alarmcard\dist"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$zipPath = Join-Path $outDir "$($art.name).zip"
Log ("Downloading: " + $dlUrl)
try {
  Invoke-WebRequest -Uri $dlUrl -OutFile $zipPath -UseBasicParsing
  Log ("Downloaded: " + $zipPath + " (" + (Get-Item $zipPath).Length + " bytes)")
} catch {
  Log ("Download failed: " + $_.Exception.Message)
  exit 5
}

$extractDir = Join-Path $outDir $art.name
if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
Expand-Archive -Path $zipPath -DestinationPath $extractDir -Force
Log ("Extracted to: " + $extractDir)
Get-ChildItem -Path $extractDir -Recurse -Filter *.apk | ForEach-Object {
  Log ("APK: {0}  ({1} MB)" -f $_.FullName, [math]::Round($_.Length/1MB,2))
}
Log "DONE"
