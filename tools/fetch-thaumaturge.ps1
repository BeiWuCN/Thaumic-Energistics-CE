# Builds Thaumaturge from source into libs\, which is the one dependency this project cannot resolve
# from maven.
#
# Thaumaturge is All Rights Reserved. Its LICENSE forbids publishing the mod or any binary built from
# it - section 3.1 names "GitHub Releases on a fork" outright - and it publishes no maven artifact,
# so no build of it can be downloaded, committed, or handed to anyone else. What section 2.4 does
# allow is building it for your own use, which is exactly and only what this script does.
#
# Idempotent: stops if libs\ already holds a Thaumaturge jar. Pass -Force to rebuild.
#
#     powershell -ExecutionPolicy Bypass -File tools\fetch-thaumaturge.ps1 [-Force]
#
# Environment:
#     THAUMATURGE_SRC   where the checkout lives (default <root>\build\thaumaturge-src)
#     JAVA_HOME         must point at a JDK 21

[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$root  = Split-Path -Parent $PSScriptRoot
$libs  = Join-Path $root 'libs'
$props = Join-Path $root 'gradle.properties'

$line = Select-String -Path $props -Pattern '^thaumaturge_commit=(.+)$' -Encoding UTF8 | Select-Object -First 1
if (-not $line) {
    throw "fetch-thaumaturge: no thaumaturge_commit line in $props"
}
$commit = $line.Matches[0].Groups[1].Value.Trim()
if (-not $commit) {
    throw "fetch-thaumaturge: thaumaturge_commit is empty in $props"
}

$existing = @(Get-ChildItem -Path $libs -Filter 'thaumaturge-*.jar' -File -ErrorAction SilentlyContinue)
if (-not $Force -and $existing.Count -gt 0) {
    Write-Host "fetch-thaumaturge: already have $($existing[0].FullName)"
    Write-Host "fetch-thaumaturge: nothing to do (pass -Force to rebuild)"
    exit 0
}

$src = if ($env:THAUMATURGE_SRC) { $env:THAUMATURGE_SRC } else { Join-Path $root 'build\thaumaturge-src' }
$repo = 'https://github.com/Leclowndu93150/Thaumaturge.git'

if (-not (Test-Path (Join-Path $src '.git'))) {
    Write-Host "fetch-thaumaturge: cloning $repo"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $src) | Out-Null
    & git clone --quiet $repo $src
    if ($LASTEXITCODE -ne 0) { throw "fetch-thaumaturge: git clone failed ($LASTEXITCODE)" }
}

Write-Host "fetch-thaumaturge: checking out $commit"
& git -C $src fetch --quiet origin
if ($LASTEXITCODE -ne 0) { throw "fetch-thaumaturge: git fetch failed ($LASTEXITCODE)" }
& git -C $src checkout --quiet --force $commit
if ($LASTEXITCODE -ne 0) { throw "fetch-thaumaturge: git checkout $commit failed ($LASTEXITCODE)" }

Write-Host 'fetch-thaumaturge: building Thaumaturge (several minutes the first time)'
$wrapper = Join-Path $src 'gradlew.bat'
if (-not (Test-Path $wrapper)) { $wrapper = Join-Path $src 'gradlew' }
Push-Location $src
try {
    if ($env:CI) { & $wrapper --no-daemon jar } else { & $wrapper jar }
    if ($LASTEXITCODE -ne 0) { throw "fetch-thaumaturge: the Thaumaturge build failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

$built = @(Get-ChildItem -Path (Join-Path $src 'build\libs') -Filter 'thaumaturge-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' })
if ($built.Count -eq 0) {
    throw "fetch-thaumaturge: the build left no jar in $(Join-Path $src 'build\libs')"
}

New-Item -ItemType Directory -Force -Path $libs | Out-Null
Copy-Item -LiteralPath $built[0].FullName -Destination $libs -Force
Write-Host "fetch-thaumaturge: $($built[0].Name) -> $libs"
Write-Host 'fetch-thaumaturge: do not commit this jar or pass it on; the licence forbids both.'
