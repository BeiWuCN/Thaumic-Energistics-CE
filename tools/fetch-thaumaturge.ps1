# Builds Thaumaturge from source into libs\, which is the one dependency this project cannot resolve
# from maven.
#
# Thaumaturge is All Rights Reserved. Its LICENSE forbids publishing the mod or any binary built from
# it - section 3.1 names "GitHub Releases on a fork" outright - and it publishes no maven artifact,
# so no build of it can be downloaded, committed, or handed to anyone else. What section 2.4 does
# allow is building it for your own use, which is exactly and only what this script does.
#
# Datagen runs first, and it is not optional. Thaumaturge generates almost all of its content - the
# aspects, the research categories, recipes, advancements, loot tables and the biomes - and none of
# that is committed to its repository: src\generated\resources holds 14 files and src\main\resources
# holds 202, against the ~1780 the mod actually needs. `jar` alone therefore produces a jar whose
# every datapack registry loads empty, and the game dies at world load with
#     Unbound values in registry ... thaumaturge:aspect
# upstream knows this - build.gradle registers a `generateData` task - but nothing depends on it, so
# it has to be run explicitly. The check at the end refuses to install a jar without the data.
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

# Gradle refuses to configure a project whose directory name starts with a dot, and it says so with
# "The project name '.thaumaturge-src' must not start or end with a '.'". Catch it here, before the
# clone, because the failure at that point is far harder to read.
$leaf = Split-Path -Leaf $src
if ($leaf.StartsWith('.')) {
    throw "fetch-thaumaturge: THAUMATURGE_SRC must not be a dot-directory (got $src); Gradle cannot configure a project named '$leaf'"
}

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

$wrapper = Join-Path $src 'gradlew.bat'
if (-not (Test-Path $wrapper)) { $wrapper = Join-Path $src 'gradlew' }
$gradleArgs = @()
if ($env:CI) { $gradleArgs += '--no-daemon' }

Push-Location $src
try {
    Write-Host "fetch-thaumaturge: generating Thaumaturge's data (this is what makes the jar usable)"
    & $wrapper @gradleArgs runData -PdatagenPass=true
    if ($LASTEXITCODE -ne 0) { throw "fetch-thaumaturge: Thaumaturge datagen failed ($LASTEXITCODE)" }

    Write-Host 'fetch-thaumaturge: building Thaumaturge (several minutes the first time)'
    & $wrapper @gradleArgs jar
    if ($LASTEXITCODE -ne 0) { throw "fetch-thaumaturge: the Thaumaturge build failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

$built = @(Get-ChildItem -Path (Join-Path $src 'build\libs') -Filter 'thaumaturge-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' })
if ($built.Count -eq 0) {
    throw "fetch-thaumaturge: the build left no jar in $(Join-Path $src 'build\libs')"
}
$jar = $built[0].FullName

Add-Type -AssemblyName System.IO.Compression.FileSystem
$aspects = 0
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    $aspects = @($zip.Entries | Where-Object { $_.FullName -match '^data/thaumaturge/thaumaturge/aspect/.+\.json$' }).Count
} finally {
    $zip.Dispose()
}
if ($aspects -lt 37) {
    throw "fetch-thaumaturge: $jar carries only $aspects aspect files, expected 37. Datagen did not run, and this jar would crash the game."
}
Write-Host "fetch-thaumaturge: the jar carries $aspects aspects and the rest of the generated data"

New-Item -ItemType Directory -Force -Path $libs | Out-Null
Copy-Item -LiteralPath $jar -Destination $libs -Force
Write-Host "fetch-thaumaturge: $($built[0].Name) -> $libs"
Write-Host 'fetch-thaumaturge: do not commit this jar or pass it on; the licence forbids both.'
