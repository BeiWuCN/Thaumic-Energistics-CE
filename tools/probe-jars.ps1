# Lists AE2 / Thaumaturge class names + javap signatures needed by the B-stage skeleton.
# Read-only: opens the jars, never writes into the trees.
$ErrorActionPreference = 'Stop'
$ae2 = 'C:\Users\Admin\.gradle\caches\modules-2\files-2.1\maven.modrinth\ae2\26.1.13-beta\e549080c2b6e1b44bee8fa8cf2b07f4e454d9e40\ae2-26.1.13-beta.jar'
$tha = 'F:\Deepseek Harness\TECE 26.1.2\libs\thaumaturge-26.1.2-NeoForge-BETA-0.2.1.jar'
$out = 'F:\Deepseek Harness\TECE 26.1.2\build\b-probe'
New-Item -ItemType Directory -Force -Path $out | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
function List-Jar($jar, $pattern, $label) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $hits = $zip.Entries | Where-Object { $_.FullName -match $pattern } | ForEach-Object { $_.FullName }
    } finally { $zip.Dispose() }
    "==== $label ($pattern) : $($hits.Count) hit(s) ====" | Out-File -Encoding utf8 "$out\list-$label.txt"
    $hits | Sort-Object | Out-File -Encoding utf8 -Append "$out\list-$label.txt"
}

List-Jar $ae2 'appeng/api/integrations/igtooltip/' 'ae2-igtooltip'
List-Jar $ae2 'appeng/api/parts/[A-Za-z]+\.class$' 'ae2-parts'
List-Jar $ae2 'appeng/api/networking/IGridTickable' 'ae2-tickable'
List-Jar $ae2 'appeng/blockentity/powered/AENetworkedPoweredBlockEntity' 'ae2-powered'
List-Jar $ae2 'appeng/api/networking/energy/' 'ae2-energy'
List-Jar $ae2 'appeng/api/config/Actionable' 'ae2-actionable'
List-Jar $tha 'api/aura/AuraHelper' 'tha-aura'
List-Jar $tha 'client/model/entity/BrainModel' 'tha-brain'
List-Jar $tha 'api/aspect/TCAspects' 'tha-aspects'

# Dump the .class entries of interest to disk so javap can read them.
function Extract($jar, $entry, $dest) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $e = $zip.Entries | Where-Object { $_.FullName -eq $entry } | Select-Object -First 1
        if ($null -eq $e) { Write-Host "MISSING $entry"; return }
        New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
    } finally { $zip.Dispose() }
}

$javap = 'D:\Java\Java25\bin\javap'
if (-not (Test-Path $javap)) { $javap = 'javap' }
Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue

$targets = @(
    @{ jar = $ae2; entry = 'appeng/api/integrations/igtooltip/PartTooltips.class'; name = 'PartTooltips' },
    @{ jar = $ae2; entry = 'appeng/api/integrations/igtooltip/TooltipBuilder.class'; name = 'TooltipBuilder' },
    @{ jar = $ae2; entry = 'appeng/api/integrations/igtooltip/TooltipContext.class'; name = 'TooltipContext' },
    @{ jar = $ae2; entry = 'appeng/api/integrations/igtooltip/providers/ServerDataProvider.class'; name = 'ServerDataProvider' },
    @{ jar = $ae2; entry = 'appeng/api/integrations/igtooltip/providers/BodyProvider.class'; name = 'BodyProvider' },
    @{ jar = $ae2; entry = 'appeng/api/parts/IPartItem.class'; name = 'IPartItem' },
    @{ jar = $ae2; entry = 'appeng/api/parts/PartHelper.class'; name = 'PartHelper' },
    @{ jar = $ae2; entry = 'appeng/api/parts/P2PTunnelPart.class'; name = 'P2PTunnelPart' },
    @{ jar = $ae2; entry = 'appeng/api/parts/PartModel.class'; name = 'PartModel' },
    @{ jar = $ae2; entry = 'appeng/api/parts/IPartModel.class'; name = 'IPartModel' },
    @{ jar = $ae2; entry = 'appeng/api/parts/IPart.class'; name = 'IPart' },
    @{ jar = $tha; entry = 'com/leclowndu93150/thaumaturge/api/aura/AuraHelper.class'; name = 'AuraHelper' },
    @{ jar = $tha; entry = 'com/leclowndu93150/thaumaturge/client/model/entity/BrainModel.class'; name = 'BrainModel' }
)
foreach ($t in $targets) {
    $dest = Join-Path $out ("cls\" + $t.name + ".class")
    Extract $t.jar $t.entry $dest
    if (Test-Path $dest) {
        "==== javap $($t.name) ====" | Out-File -Encoding utf8 "$out\javap-$($t.name).txt"
        & $javap -p -classpath (Join-Path $out 'cls') $t.name 2>&1 | Out-File -Encoding utf8 -Append "$out\javap-$($t.name).txt"
    }
}
Write-Host "probe done -> $out"
Get-ChildItem $out -Recurse | Select-Object -ExpandProperty FullName
