param(
  [Int64]$RunId = 31241621187,
  [string]$Repo = "jpark0409/alarmcard"
)
$ErrorActionPreference = "Stop"
$log = "c:\Users\user\Desktop\alarmcard\scripts\download2.log"
if (Test-Path $log) { Remove-Item $log -Force }
function Log($m) { Add-Content -Path $log -Value $m }

$arts = Invoke-RestMethod "https://api.github.com/repos/$Repo/actions/runs/$RunId/artifacts"
Log ("total: " + $arts.total_count)
$art = $arts.artifacts | Where-Object { $_.name -like "*apk*" } | Select-Object -First 1
if (-not $art) { $art = $arts.artifacts[0] }
Log ("art: id={0} name={1} size={2}" -f $art.id, $art.name, $art.size_in_bytes)

$parts = $Repo.Split('/')
$dlUrl = "https://nightly.link/$($parts[0])/$($parts[1])/actions/artifacts/$($art.id).zip"
$outDir = "c:\Users\user\Desktop\alarmcard\dist"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$zip = Join-Path $outDir ("$($art.name)_$stamp.zip")
Log ("Downloading to " + $zip)
Invoke-WebRequest -Uri $dlUrl -OutFile $zip -UseBasicParsing
Log ("Downloaded: " + (Get-Item $zip).Length + " bytes")

$extract = Join-Path $outDir ("$($art.name)_$stamp")
if (Test-Path $extract) { Remove-Item $extract -Recurse -Force }
Expand-Archive -Path $zip -DestinationPath $extract -Force
Get-ChildItem -Recurse -Path $extract -Filter *.apk | ForEach-Object {
  Log ("APK: {0}  ({1} MB)" -f $_.FullName, [math]::Round($_.Length/1MB,2))
}
Log "DONE"
