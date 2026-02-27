param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("all", "paper", "sponge", "fabric", "forge", "neoforge")]
    [string]$Loader = "all",

    [Parameter(Mandatory = $false)]
    [string]$MinecraftVersion = ""
)

$ErrorActionPreference = "Stop"
$isWindowsHost = ($env:OS -eq "Windows_NT")

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$matrixPath = Join-Path $repoRoot "versions\minecraft-1.21x-builds.json"

if (-not (Test-Path $matrixPath)) {
    Write-Host "Missing matrix file: $matrixPath" -ForegroundColor Red
    exit 1
}

Push-Location $repoRoot
try {
    if ($isWindowsHost) {
        # Reuse Java auto-select/install behavior from the profile builder on Windows.
        & (Join-Path $repoRoot "tools\build-profiles.ps1") -Profile modern_1_21 -PrepareOnly
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

    $moduleTasks = @()
    switch ($Loader) {
        "all" {
            $moduleTasks = @(":plugin-paper:build", ":plugin-sponge:build", ":mod-fabric-1_21:build", ":mod-forge-1_21:build", ":mod-neoforge-1_21:build")
        }
        "paper" { $moduleTasks = @(":plugin-paper:build") }
        "sponge" { $moduleTasks = @(":plugin-sponge:build") }
        "fabric" { $moduleTasks = @(":mod-fabric-1_21:build") }
        "forge" { $moduleTasks = @(":mod-forge-1_21:build") }
        "neoforge" { $moduleTasks = @(":mod-neoforge-1_21:build") }
    }

    $requiredFieldsByLoader = @{
        paper = @("paper_api_version")
        sponge = @("sponge_api_version")
        fabric = @("fabric_loader_version", "fabric_api_version", "yarn_mappings")
        forge = @("forge_version")
        neoforge = @("neoforge_version")
    }

    foreach ($entry in $versions) {
        $mc = $entry.mc
        $missing = @()

        $loadersToCheck = @()
        if ($Loader -eq "all") {
            $loadersToCheck = @("paper", "sponge", "fabric", "forge", "neoforge")
        } else {
            $loadersToCheck = @($Loader)
        }

        foreach ($l in $loadersToCheck) {
            foreach ($field in $requiredFieldsByLoader[$l]) {
                $value = $entry.$field
                if (-not $value -or [string]::IsNullOrWhiteSpace([string]$value)) {
                    $missing += "$l.$field"
                }
            }
        }

        if ($missing.Count -gt 0) {
            Write-Host "Skipping $mc due to missing matrix values: $($missing -join ', ')" -ForegroundColor Yellow
            continue
        }

        Write-Host "Building Minecraft $mc ($Loader)..."
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
    }

    Write-Host "Done."
    exit 0
}
finally {
    Pop-Location
}
