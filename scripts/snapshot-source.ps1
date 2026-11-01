param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$appGradle = Join-Path $root 'app/build.gradle.kts'
$text = Get-Content $appGradle -Raw -Encoding UTF8
$versionName = [regex]::Match($text, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$versionCode = [regex]::Match($text, 'versionCode\s*=\s*(\d+)').Groups[1].Value
if (-not $versionName -or -not $versionCode) { throw 'Cannot read version from app/build.gradle.kts' }

$releasesRoot = Join-Path (Split-Path -Parent $root) 'releases'
$dest = Join-Path $releasesRoot "v$versionName"
if ((Test-Path $dest) -and -not $Force) {
    throw "Snapshot already exists: $dest (use -Force only after checking)"
}
New-Item -ItemType Directory -Force $dest | Out-Null
$srcDest = Join-Path $dest 'src'
if (Test-Path $srcDest) { Remove-Item $srcDest -Recurse -Force }
New-Item -ItemType Directory -Force (Join-Path $srcDest 'app') | Out-Null

Copy-Item (Join-Path $root 'app/src') (Join-Path $srcDest 'app/src') -Recurse
Copy-Item (Join-Path $root 'app/libs') (Join-Path $srcDest 'app/libs') -Recurse -ErrorAction SilentlyContinue
Copy-Item (Join-Path $root 'app/build.gradle.kts') (Join-Path $srcDest 'app/build.gradle.kts')
Copy-Item (Join-Path $root 'build.gradle.kts') (Join-Path $srcDest 'build.gradle.kts')
Copy-Item (Join-Path $root 'settings.gradle.kts') (Join-Path $srcDest 'settings.gradle.kts') -ErrorAction SilentlyContinue
Copy-Item (Join-Path $root 'gradle.properties') (Join-Path $srcDest 'gradle.properties') -ErrorAction SilentlyContinue
Copy-Item (Join-Path $root 'gradle') (Join-Path $srcDest 'gradle') -Recurse -ErrorAction SilentlyContinue

$apk = Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
if (Test-Path $apk) {
    $apkDest = Join-Path $dest "ziyu-v$versionName.apk"
    Copy-Item $apk $apkDest
    $sha = (Get-FileHash $apkDest -Algorithm SHA256).Hash
} else {
    $sha = $null
}

$meta = [ordered]@{
    versionName = $versionName
    versionCode = [int]$versionCode
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    source = 'app/src + build files'
    apkSha256 = $sha
}
$json = $meta | ConvertTo-Json
[System.IO.File]::WriteAllText(
    (Join-Path $dest 'SNAPSHOT.json'),
    $json,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output "Snapshot: $dest"
Write-Output "Version: $versionName / $versionCode"
if ($sha) { Write-Output "APK SHA-256: $sha" }
