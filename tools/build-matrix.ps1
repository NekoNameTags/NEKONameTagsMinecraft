param(
    [Parameter(Mandatory = $true)]
    [string]$Profile,

    [Parameter(Mandatory = $true)]
    [string]$MatrixFile,

    [Parameter(Mandatory = $false)]
    [ValidateSet("all", "paper", "sponge", "fabric", "forge", "neoforge", "bukkit")]
    [string]$Loader = "all",

    [Parameter(Mandatory = $false)]
    [string]$MinecraftVersion = ""
)

$ErrorActionPreference = "Stop"
$isWindowsHost = ($env:OS -eq "Windows_NT")

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$matrixPath = Join-Path $repoRoot $MatrixFile
$releaseOut = Join-Path $repoRoot "build\matrix-release"

if (-not (Test-Path $matrixPath)) {
    Write-Host "Missing matrix file: $matrixPath" -ForegroundColor Red
    exit 1
}

Push-Location $repoRoot
try {
    if (Test-Path $releaseOut) {
        Remove-Item -Recurse -Force $releaseOut
    }
    New-Item -ItemType Directory -Force -Path $releaseOut | Out-Null

    if ($isWindowsHost) {
        & (Join-Path $repoRoot "tools\build-profiles.ps1") -Profile $Profile -PrepareOnly
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Failed to prepare Java/Gradle environment." -ForegroundColor Red
            exit $LASTEXITCODE
        }
    } else {
        try {
            & java -version *> $null
        } catch {
            Write-Host "Java is required but not found in PATH on this runner." -ForegroundColor Red
            exit 1
        }
    }

    $data = Get-Content $matrixPath -Raw | ConvertFrom-Json
    $versions = @($data.versions)
    if ($MinecraftVersion -ne "") {
        $versions = @($versions | Where-Object { $_.mc -eq $MinecraftVersion })
        if ($versions.Count -eq 0) {
            Write-Host "Minecraft version not found in matrix: $MinecraftVersion" -ForegroundColor Red
            exit 1
        }
    }

    $profileModuleByLoader = @{
        modern_1_21 = @{
            paper = "plugin-paper"
            bukkit = "plugin-paper"
            sponge = "plugin-sponge"
            fabric = "mod-fabric-1_21"
            forge = "mod-forge-1_21"
            neoforge = "mod-neoforge-1_21"
        }
        modern_1_17_to_1_20 = @{
            paper = "plugin-paper-1_20"
            bukkit = "plugin-paper-1_20"
            sponge = "plugin-sponge"
            fabric = "mod-fabric-1_20"
            forge = "mod-forge-1_20"
            neoforge = ""
        }
        mid_1_13_to_1_16 = @{
            paper = "plugin-paper-1_16"
            bukkit = "plugin-paper-1_16"
            sponge = ""
            fabric = "mod-fabric-1_16"
            forge = "mod-forge-1_16"
            neoforge = ""
        }
        legacy_1_8_to_1_12 = @{
            paper = "legacy-plugin-1_8_8"
            bukkit = "legacy-plugin-1_8_8"
            sponge = ""
            fabric = ""
            forge = "legacy-forge-1_12_2"
            neoforge = ""
        }
        legacy_1_7_10 = @{
            paper = "legacy-plugin-1_7_10"
            bukkit = "legacy-plugin-1_7_10"
            sponge = ""
            fabric = ""
            forge = "legacy-forge-1_7_10"
            neoforge = ""
        }
    }

    if (-not $profileModuleByLoader.ContainsKey($Profile)) {
        Write-Host "No module mapping for profile '$Profile'" -ForegroundColor Red
        exit 1
    }
    $moduleByLoader = $profileModuleByLoader[$Profile]

    $requiredFieldsByLoader = @{
        paper = @("paper_api_version")
        bukkit = @("paper_api_version")
        sponge = @("sponge_api_version")
        fabric = @("fabric_loader_version", "fabric_api_version", "yarn_mappings")
        forge = @("forge_version")
        neoforge = @("neoforge_version")
    }

    foreach ($entry in $versions) {
        $mc = $entry.mc
        $candidateLoaders = @()
        if ($Loader -eq "all") {
            $candidateLoaders = @("paper", "sponge", "fabric", "forge", "neoforge")
        } else {
            $candidateLoaders = @($Loader)
        }

        $moduleTasks = @()
        foreach ($l in $candidateLoaders) {
            $moduleName = $moduleByLoader[$l]
            if (-not $moduleName -or [string]::IsNullOrWhiteSpace([string]$moduleName)) {
                continue
            }
            $moduleDir = Join-Path $repoRoot $moduleName
            if (-not (Test-Path $moduleDir)) {
                Write-Host "Skipping loader '$l' for ${mc}: module missing ($moduleName)" -ForegroundColor Yellow
                continue
            }

            $missingFields = @()
            foreach ($field in $requiredFieldsByLoader[$l]) {
                $value = $entry.$field
                if (-not $value -or [string]::IsNullOrWhiteSpace([string]$value)) {
                    $missingFields += "$l.$field"
                }
            }
            if ($missingFields.Count -gt 0) {
                Write-Host "Skipping loader '$l' for ${mc}: missing matrix values $($missingFields -join ', ')" -ForegroundColor Yellow
                continue
            }

            $moduleTasks += ":$moduleName:build"
        }

        if ($moduleTasks.Count -eq 0) {
            Write-Host "Skipping ${mc}: no buildable loaders for current repo/matrix." -ForegroundColor Yellow
            continue
        }

        Write-Host "Building Minecraft $mc ($Loader) with tasks: $($moduleTasks -join ' ')"
        $props = @(
            "-Pminecraft_version=$mc",
            "-Ppaper_api_version=$($entry.paper_api_version)",
            "-Psponge_api_version=$($entry.sponge_api_version)",
            "-Pfabric_loader_version=$($entry.fabric_loader_version)",
            "-Pfabric_api_version=$($entry.fabric_api_version)",
            "-Pyarn_mappings=$($entry.yarn_mappings)",
            "-Pforge_version=$($entry.forge_version)",
            "-Pneoforge_version=$($entry.neoforge_version)"
        )

        if ($isWindowsHost) {
            & .\gradlew.bat --no-daemon :core:build @moduleTasks @props
        } else {
            & ./gradlew --no-daemon :core:build @moduleTasks @props
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Build failed for $mc" -ForegroundColor Red
            exit $LASTEXITCODE
        }

        $jarFiles = Get-ChildItem -Recurse -Path . -Filter *.jar |
            Where-Object {
                $_.FullName -like "*\build\libs\*" -and
                $_.Name -notlike "*-sources.jar" -and
                $_.Name -notlike "*-javadoc.jar"
            }
        foreach ($jar in $jarFiles) {
            $base = [System.IO.Path]::GetFileNameWithoutExtension($jar.Name)
            $ext = [System.IO.Path]::GetExtension($jar.Name)
            $outName = "$base-mc$mc$ext"
            Copy-Item $jar.FullName -Destination (Join-Path $releaseOut $outName) -Force
        }
    }

    $count = @(Get-ChildItem $releaseOut -Filter *.jar -ErrorAction SilentlyContinue).Count
    Write-Host "Release files prepared: $count ($releaseOut)"
    Write-Host "Done."
    exit 0
}
finally {
    Pop-Location
}
