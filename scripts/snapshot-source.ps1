param(
    [switch]$Force,
    # Version note from the command line. Non-ASCII here arrives corrupted
    # (powershell.exe passes argv through the ANSI code page), so prefer -NoteFile.
    [string]$Note = '',
    # UTF-8 file holding the note (e.g. tmp\snapshot-note.txt). Preferred.
    [string]$NoteFile = ''
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

# A snapshot must be built from the tree it claims. When the version was bumped
# in app/build.gradle.kts but assembleRelease has not run since, the APK in
# app/build/outputs is still the previous build and the snapshot would be a
# mislabelled copy (this happened on 2026-11-06 with a v3.8.17 stub).
$builtApk = Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
if (Test-Path $builtApk) {
    $apkTime = (Get-Item $builtApk).LastWriteTimeUtc
    $newer = Get-ChildItem -Path (Join-Path $root 'app/src'), (Join-Path $root 'app/build.gradle.kts') -Recurse -File |
        Where-Object { $_.LastWriteTimeUtc -gt $apkTime }
    if ($newer) {
        throw ("APK is older than the source: {0} file(s) changed after it was built " +
            "(first: {1}). Run :app:assembleRelease before snapshotting." -f $newer.Count, (($newer | Select-Object -First 1).FullName))
    }
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
if ($NoteFile) {
    if (-not (Test-Path $NoteFile)) { throw "NoteFile not found: $NoteFile" }
    $Note = [System.IO.File]::ReadAllText((Resolve-Path $NoteFile), [System.Text.UTF8Encoding]::new($false)).Trim()
}
if ($Note) { $meta.note = $Note }
$json = $meta | ConvertTo-Json
# No BOM: ConvertFrom-Json rejects it, and a BOM inside Chinese quotes made the
# note unreadable in 3.8.9 / 3.8.10 (2026-11-06 fixed). Never use Set-Content
# without -Encoding UTF8 for this file; on powershell.exe 5.1 it writes the
# local code page instead.
[System.IO.File]::WriteAllText(
    (Join-Path $dest 'SNAPSHOT.json'),
    $json,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output "Snapshot: $dest"
Write-Output "Version: $versionName / $versionCode"
if ($sha) { Write-Output "APK SHA-256: $sha" }
