$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$destination = Join-Path $projectRoot 'libs/compat'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'compat-mods.lock.json') -Raw | ConvertFrom-Json
$headers = @{ 'User-Agent' = 'TECE-1.21.1-compatibility-tests/1.0' }
foreach ($entry in $manifest) {
    $target = Join-Path $destination $entry.file
    if ((Test-Path -LiteralPath $target) -and
        (Get-FileHash -LiteralPath $target -Algorithm SHA512).Hash.ToLowerInvariant() -eq $entry.sha512) {
        Write-Output "Verified: $($entry.file)"
        continue
    }
    $temporary = "$target.download"
    Invoke-WebRequest -Uri $entry.url -Headers $headers -OutFile $temporary
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash.ToLowerInvariant() -ne $entry.sha512) {
        throw "SHA-512 mismatch: $($entry.file)"
    }
    Move-Item -LiteralPath $temporary -Destination $target -Force
    Write-Output "Installed: $($entry.file)"
}
