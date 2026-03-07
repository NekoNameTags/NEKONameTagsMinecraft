param(
    [string]$MinecraftVersion = ''
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')

$templateMap = @{
    paper = Join-Path $root 'template/plugin-paper'
    sponge = Join-Path $root 'template/plugin-sponge'
    forge = Join-Path $root 'template/mod-forge-1_21'
    neoforge = Join-Path $root 'template/mod-neoforge-1_21'
    fabric = Join-Path $root 'template/mod-fabric-1_21'
    fabric2111 = Join-Path $root 'template/mod-fabric-1_21_11'
}

foreach ($k in $templateMap.Keys) {
    if (-not (Test-Path $templateMap[$k])) {
        throw "Missing template for ${k}: $($templateMap[$k])"
    }
}

function Sync-FromTemplate([string]$templateDir, [string]$targetDir) {
    $templateBuild = Join-Path $templateDir 'build.gradle.kts'
    $templateSrc = Join-Path $templateDir 'src'
    if (-not (Test-Path $templateBuild)) { throw "Template missing build.gradle.kts: $templateDir" }
    if (-not (Test-Path $templateSrc)) { throw "Template missing src: $templateDir" }

    $targetSrc = Join-Path $targetDir 'src'
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

    Copy-Item -Force $templateBuild (Join-Path $targetDir 'build.gradle.kts')

    if (Test-Path $targetSrc) {
        Remove-Item -Recurse -Force $targetSrc
    }
    Copy-Item -Recurse -Force $templateSrc $targetSrc
}

$loaders = @('paper','sponge','forge','neoforge','fabric')
$summary = [ordered]@{}

foreach ($loader in $loaders) {
    $loaderDir = Join-Path $root $loader
    if (-not (Test-Path $loaderDir)) {
        $summary[$loader] = 0
        continue
    }

    $targets = Get-ChildItem -Path $loaderDir -Directory
    if ($MinecraftVersion -ne '') {
        $targets = @($targets | Where-Object { $_.Name -eq $MinecraftVersion })
    }

    $count = 0
    foreach ($target in $targets) {
        $templateDir = $templateMap[$loader]
        if ($loader -eq 'fabric' -and $target.Name -in @('1.21.10', '1.21.11')) {
            $templateDir = $templateMap.fabric2111
        }
        Sync-FromTemplate $templateDir $target.FullName
        $count++
    }
    $summary[$loader] = $count
}

$summary.GetEnumerator() | ForEach-Object { "{0}={1}" -f $_.Key, $_.Value }
