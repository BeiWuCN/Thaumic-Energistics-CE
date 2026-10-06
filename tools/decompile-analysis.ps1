$ErrorActionPreference = 'Stop'
Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue

$java = 'D:\Java\Java21\bin\java.exe'
$vine = 'C:\Users\Admin\.gradle\caches\modules-2\files-2.1\org.vineflower\vineflower\1.11.2\f02c1f554d5bd9cc0fd74d4977efba364d85e739\vineflower-1.11.2.jar'
$ae2 = 'C:\Users\Admin\.gradle\caches\modules-2\files-2.1\maven.modrinth\ae2\19.2.17\49c18d6a4af487957d7e5a6ad5dcbf71090b8e14\ae2-19.2.17.jar'
$analysis = 'F:\Deepseek Harness\TECE 2.0\build\analysis'

Write-Output "=== JEI jars found ==="
Get-ChildItem 'C:\Users\Admin\.gradle\caches\modules-2\files-2.1\maven.modrinth\jei' -Recurse -Filter *.jar |
    ForEach-Object { Write-Output $_.FullName }

Write-Output ""
Write-Output "=== AE2 entries matching jei / CraftingTermMenu / AEBaseMenu ==="
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($ae2)
foreach ($e in $zip.Entries) {
    $n = $e.FullName
    if ($n -like '*jei*' -or $n -like '*Jei*' -or $n -like '*JEI*' -or $n -like '*CraftingTermMenu*' -or $n -like '*AEBaseMenu*' -or $n -like '*FillCraftingGrid*' -or $n -like '*SlotSemantic*' -or $n -like '*CraftingTermSlot*' -or $n -like '*ICraftingGridMenu*') {
        Write-Output $n
    }
}
$zip.Dispose()
