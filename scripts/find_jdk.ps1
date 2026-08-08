$paths = @()
Get-ChildItem -Path "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $paths += $_.FullName }
Get-ChildItem -Path "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $paths += $_.FullName }
Get-ChildItem -Path "C:\Program Files\Microsoft\jdk-*" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $paths += $_.FullName }
Get-ChildItem -Path "C:\Program Files (x86)\Java" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $paths += $_.FullName }
if ($paths.Count -eq 0) { "no jdk found" } else { $paths | ForEach-Object { $_ } }
