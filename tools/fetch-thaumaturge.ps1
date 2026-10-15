# Install the user-supplied 1.0.2 release jar; no source checkout is modified.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$SourceJar)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceJar).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($source)
try {
    $entry = $archive.GetEntry('META-INF/neoforge.mods.toml')
    if ($null -eq $entry) { throw 'Not a NeoForge mod jar.' }
    $reader = [System.IO.StreamReader]::new($entry.Open())
    try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($metadata -notmatch 'modId\s*=\s*"thaumaturge"' -or $metadata -notmatch 'version\s*=\s*"1\.0\.2"') {
        throw 'Expected the Thaumaturge 1.0.2 release jar.'
    }
} finally { $archive.Dispose() }
$target = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../libs/thaumaturge-1.21.1-NeoForge-1.0.2.jar'))
if ($source -ne $target) { Copy-Item -LiteralPath $source -Destination $target }
Write-Host "Installed $target"
