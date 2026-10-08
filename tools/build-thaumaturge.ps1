# Builds the latest Thaumaturge (branch main) into libs\, replacing the previous self-built jar.
#
# Thaumaturge is the one dependency this project cannot resolve from maven: it is All Rights Reserved
# and publishes no artifact, so the jar is built here, kept out of version control, and never handed
# to anyone else. See libs/README.md.
#
# Datagen is not optional. Thaumaturge generates nearly all of its content - aspects, research
# categories, recipes, advancements, loot tables, biomes - and none of it is committed, so `jar`
# alone produces a jar whose datapack registries load empty and the game dies at world load with
# "Unbound values in registry ... thaumaturge:aspect". `runData` has to run first, explicitly.
#
# The checkout is a git tree, not a copy: the pinned commit is checked out and then every patch in
# tools\patches is applied on top of it. A patch that no longer applies aborts the run, because a
# silently unpatched jar and a patched one look identical from the outside.
#
#     powershell -NoProfile -ExecutionPolicy Bypass -File Tools\build-thaumaturge.ps1
#
# Environment: JAVA_HOME must point at a JDK 25 (the 26.1.2 line).

[CmdletBinding()]
param(
    [string]$Commit = 'de63133232483fce39a8e9fc1499f8f4e7e82379',
    [switch]$SkipClone,
    # A 1.0.x jar is wanted even when a patch has been overtaken by upstream. The skip is loud and
    # per-patch: a silently unpatched jar and a patched one look identical from the outside, which
    # is exactly the failure this script exists to prevent. Only the default (all-or-nothing) run
    # gives a jar that may be shipped.
    [switch]$SkipPatches,
    # Upstream's wrapper points at gradle-9.7.0, and that is the only distribution this build runs on:
    # its own settings plugin, dev.prism 0.6.4, declares org.gradle.plugin.api-version 9.7.0, so every
    # earlier Gradle rejects the build in variant matching before a single task is configured. 9.7 is
    # cached locally now. Set this to another distribution only to test an upstream change; the
    # pristine wrapper properties are kept beside the patched ones whenever it is overridden.
    [string]$WrapperDistribution = '',
    [switch]$SkipWrapperOverride,
    # The global gradle.properties pins the local VPN proxy, which refuses connections whenever the
    # VPN is down. System properties win over those systemProp entries, so an empty value here
    # forces a direct connection for this build only.
    [switch]$DirectConnection
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$src = Join-Path $root 'build\thaumaturge-src'
$libs = Join-Path $root 'libs'
$patchDir = Join-Path $PSScriptRoot 'patches'
$repo = 'https://github.com/Leclowndu93150/Thaumaturge.git'

Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'D:\Java\Java25'
}
Write-Host "build-thaumaturge: JAVA_HOME=$env:JAVA_HOME"

if (-not $SkipClone) {
    if (Test-Path -LiteralPath $src) { Remove-Item -LiteralPath $src -Recurse -Force }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $src) | Out-Null
    & git -c http.proxy= -c https.proxy= init --quiet $src
    if ($LASTEXITCODE -ne 0) { throw "git init failed ($LASTEXITCODE)" }
    & git -C $src remote add origin $repo
    & git -C $src config http.proxy ''
    & git -C $src config https.proxy ''
    & git -C $src fetch --quiet --depth 1 --filter=blob:none origin $Commit
    if ($LASTEXITCODE -ne 0) { throw "git fetch $Commit failed ($LASTEXITCODE)" }
    & git -C $src checkout --quiet --force FETCH_HEAD
    if ($LASTEXITCODE -ne 0) { throw "git checkout $Commit failed ($LASTEXITCODE)" }
}

$head = (& git -C $src rev-parse HEAD).Trim()
if ($head -ne $Commit) { throw "checkout is at $head, expected $Commit" }
Write-Host "build-thaumaturge: source pin $head"

$patches = @(Get-ChildItem -Path $patchDir -Filter '*.patch' -File -ErrorAction SilentlyContinue | Sort-Object Name)
foreach ($patch in $patches) {
    & git -C $src apply --check $patch.FullName
    if ($LASTEXITCODE -ne 0) {
        if ($SkipPatches) {
            Write-Warning "build-thaumaturge: SKIPPING $($patch.Name) - it does not apply to $Commit. This jar is not the shipped configuration."
            continue
        }
        throw "build-thaumaturge: $($patch.Name) does not apply to $Commit; the patch and the pin have drifted apart"
    }
    & git -C $src apply $patch.FullName
    if ($LASTEXITCODE -ne 0) { throw "build-thaumaturge: applying $($patch.Name) failed ($LASTEXITCODE)" }
    Write-Host "build-thaumaturge: applied $($patch.Name)"
}

$wrapper = Join-Path $src 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper)) { $wrapper = Join-Path $src 'gradlew' }

if (-not $SkipWrapperOverride -and $WrapperDistribution) {
    $propsPath = Join-Path $src 'gradle\wrapper\gradle-wrapper.properties'
    $backupPath = "$propsPath.pristine"
    if (-not (Test-Path -LiteralPath $backupPath)) { Copy-Item -LiteralPath $propsPath -Destination $backupPath }
    $props = [System.IO.File]::ReadAllText($propsPath, (New-Object System.Text.UTF8Encoding($false)))
    $patched = [regex]::Replace($props, '(?m)^distributionUrl=.*$', "distributionUrl=$WrapperDistribution")
    if ($patched -ne $props) {
        [System.IO.File]::WriteAllText($propsPath, $patched, (New-Object System.Text.UTF8Encoding($false)))
        Write-Host "build-thaumaturge: wrapper distribution overridden -> $WrapperDistribution"
    } else {
        Write-Host 'build-thaumaturge: wrapper distribution left as upstream pinned it'
    }
}

# --no-build-cache because upstream turns the Gradle build cache on; a cached run would write this
# project's jar into the shared build cache, and that cache is persisted for CI where a Thaumaturge
# binary has no business being. Dependency downloads are still cached - they are public artifacts.
# There is one build here and it is a long one, so it reports line by line into build\, where it can be
# followed while it runs. Plain console rather than --info, which buries the task list in dependency
# noise; there is no --progress switch on Gradle 9.4, it was added later. Gradle 9.4 does parse an
# upstream 9.7 Kotlin DSL project; the distribution is only substituted because 9.7 cannot be
# downloaded on this machine.
$gradleArgs = @('--no-build-cache', '--console=plain')
if ($DirectConnection) {
    $gradleArgs += @('-Dhttp.proxyHost=', '-Dhttps.proxyHost=', '-Dhttp.proxyPort=', '-Dhttps.proxyPort=')
    Write-Host 'build-thaumaturge: forcing a direct connection for this run'
}

Push-Location $src
try {
    # 1.0.2 split datagen into the two moddev runs the platform provides; there is no plain runData
    # any more, and the -PdatagenPass switch the old script passed is gone from upstream. Both halves
    # are generated because the jar carries whatever they write.
    Write-Host 'build-thaumaturge: generating Thaumaturge data (this is what makes the jar usable)'
    & $wrapper @gradleArgs runServerData
    if ($LASTEXITCODE -ne 0) { throw "Thaumaturge server datagen failed ($LASTEXITCODE)" }

    & $wrapper @gradleArgs runClientData
    if ($LASTEXITCODE -ne 0) { throw "Thaumaturge client datagen failed ($LASTEXITCODE)" }

    Write-Host 'build-thaumaturge: building the jar'
    & $wrapper @gradleArgs jar
    if ($LASTEXITCODE -ne 0) { throw "Thaumaturge build failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

$built = @(Get-ChildItem -Path (Join-Path $src 'build\libs') -Filter 'thaumaturge-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
    Sort-Object LastWriteTime -Descending)
if ($built.Count -eq 0) { throw "the build left no jar in $src\build\libs" }
$jar = $built[0]

Add-Type -AssemblyName System.IO.Compression.FileSystem
$aspects = 0
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    $aspects = @($zip.Entries | Where-Object { $_.FullName -match '^data/thaumaturge/thaumaturge/aspect/.+\.json$' }).Count
} finally {
    $zip.Dispose()
}
if ($aspects -lt 37) {
    throw "$($jar.FullName) carries only $aspects aspect files, expected 37: datagen did not run and this jar would crash the game"
}
Write-Host "build-thaumaturge: $($jar.Name) carries $aspects aspects and the rest of the generated data"

New-Item -ItemType Directory -Force -Path $libs | Out-Null
# A jar parked under libs\backup\ is a deliberate rollback point for a version migration, so it is
# left alone: only the jars at the top of libs\ are the ones this script owns.
$old = @(Get-ChildItem -Path $libs -Filter 'thaumaturge-*.jar' -File -ErrorAction SilentlyContinue)
foreach ($o in $old) { Remove-Item -LiteralPath $o.FullName -Force }
Copy-Item -LiteralPath $jar.FullName -Destination $libs -Force
Write-Host "build-thaumaturge: installed -> $libs\$($jar.Name)"
Write-Host 'build-thaumaturge: do not commit this jar or pass it on; the licence forbids both.'
