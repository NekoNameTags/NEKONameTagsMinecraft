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

function Test-UsableMatrixValue {
    param([Parameter(Mandatory = $false)]$Value)

    if ($null -eq $Value) {
        return $false
    }
    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $false
    }
    $normalized = $text.Trim().ToUpperInvariant()
    if ($normalized -in @("UNSUPPORTED", "N/A", "NA", "NONE", "NULL")) {
        return $false
    }
    return $true
}

function Read-GradleDistributionUrlFromMatrix {
    param(
        [Parameter(Mandatory = $true)][string]$MatrixPath,
        [Parameter(Mandatory = $true)][string]$ProfileName
    )

    if (-not (Test-Path $MatrixPath)) {
        throw "version matrix not found: $MatrixPath"
    }

    $lines = Get-Content $MatrixPath
    $profileStart = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\s{2}$([regex]::Escape($ProfileName)):\s*$") {
            $profileStart = $i
            break
        }
    }
    if ($profileStart -lt 0) {
        throw "profile '$ProfileName' not found in $MatrixPath"
    }

    $gradleUrl = $null
    for ($i = $profileStart + 1; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match "^\s{2}[A-Za-z0-9_]+:\s*$") {
            break
        }
        if ($line -match '^\s{4}gradle_distribution_url:\s*"?(.*?)"?\s*$') {
            $gradleUrl = [string]$Matches[1]
            continue
        }
    }

    if (-not $gradleUrl -or [string]::IsNullOrWhiteSpace($gradleUrl)) {
        throw "profile '$ProfileName' has no gradle_distribution_url in $MatrixPath"
    }
    return $gradleUrl
}

function Set-GradleWrapperDistributionUrl {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$DistributionUrl,
        [Parameter(Mandatory = $true)][string]$ProfileName
    )

    $wrapperPath = Join-Path $RepoRoot "gradle/wrapper/gradle-wrapper.properties"
    if (-not (Test-Path $wrapperPath)) {
        throw "gradle wrapper properties not found: $wrapperPath"
    }

    $raw = Get-Content $wrapperPath -Raw
    $escaped = [regex]::Escape($DistributionUrl)
    if ($raw -match "(?m)^distributionUrl=$escaped\s*$") {
        return
    }

    if ($raw -match "(?m)^distributionUrl=.*$") {
        $updated = [regex]::Replace($raw, "(?m)^distributionUrl=.*$", "distributionUrl=$DistributionUrl")
    } else {
        $updated = $raw.TrimEnd("`r", "`n") + "`r`ndistributionUrl=$DistributionUrl`r`n"
    }

    Set-Content -Path $wrapperPath -Value $updated -Encoding ASCII
    Write-Host "Set Gradle wrapper distribution for '$ProfileName' to $DistributionUrl"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$matrixPath = Join-Path $repoRoot $MatrixFile
$releaseOut = Join-Path $repoRoot "build/matrix-release"
$profileMatrixPath = Join-Path $repoRoot "versions/version-matrix.yml"

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

    $requiredGradleDistribution = Read-GradleDistributionUrlFromMatrix -MatrixPath $profileMatrixPath -ProfileName $Profile
    Set-GradleWrapperDistributionUrl -RepoRoot $repoRoot -DistributionUrl $requiredGradleDistribution -ProfileName $Profile

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

    $failedBuilds = @()

    if (-not $isWindowsHost) {
        & chmod +x ./gradlew
    }

    foreach ($entry in $versions) {
        $mc = $entry.mc
        $candidateLoaders = @()
        if ($Loader -eq "all") {
            $candidateLoaders = @("paper", "sponge", "fabric", "forge", "neoforge")
        } else {
            $candidateLoaders = @($Loader)
        }

        $buildTargets = @()
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
                if (-not (Test-UsableMatrixValue -Value $value)) {
                    $missingFields += "$l.$field"
                }
            }
            if ($missingFields.Count -gt 0) {
                Write-Host "Skipping loader '$l' for ${mc}: missing matrix values $($missingFields -join ', ')" -ForegroundColor Yellow
                continue
            }

            $buildTargets += [pscustomobject]@{
                loader = $l
                module = $moduleName
            }
        }

        if ($buildTargets.Count -eq 0) {
            Write-Host "Skipping ${mc}: no buildable loaders for current repo/matrix." -ForegroundColor Yellow
            continue
        }

        foreach ($target in $buildTargets) {
            $props = @()
            if (Test-UsableMatrixValue -Value $mc) {
                $props += "-Pminecraft_version=$mc"
            }
            if (Test-UsableMatrixValue -Value $entry.paper_api_version) {
                $props += "-Ppaper_api_version=$($entry.paper_api_version)"
            }
            if (Test-UsableMatrixValue -Value $entry.sponge_api_version) {
                $props += "-Psponge_api_version=$($entry.sponge_api_version)"
            }
            if (Test-UsableMatrixValue -Value $entry.fabric_loader_version) {
                $props += "-Pfabric_loader_version=$($entry.fabric_loader_version)"
            }
            if (Test-UsableMatrixValue -Value $entry.fabric_api_version) {
                $props += "-Pfabric_api_version=$($entry.fabric_api_version)"
            }
            if (Test-UsableMatrixValue -Value $entry.yarn_mappings) {
                $props += "-Pyarn_mappings=$($entry.yarn_mappings)"
            }
            if (Test-UsableMatrixValue -Value $entry.forge_version) {
                $props += "-Pforge_version=$($entry.forge_version)"
            }
            if (Test-UsableMatrixValue -Value $entry.neoforge_version) {
                $props += "-Pneoforge_version=$($entry.neoforge_version)"
            }

            switch ($target.loader) {
                "paper" {
                }
                "bukkit" {
                }
                "sponge" {
                }
                "fabric" {
                }
                "forge" {
                }
                "neoforge" {
                }
            }

            $moduleTask = ":$($target.module):build"
            Write-Host "Building Minecraft $mc ($($target.loader)) with task: $moduleTask"

            if ($isWindowsHost) {
                & .\gradlew.bat --no-daemon :core:build $moduleTask @props
            } else {
                & ./gradlew --no-daemon :core:build $moduleTask @props
            }
            if ($LASTEXITCODE -ne 0) {
                Write-Host "Build failed for $mc [$($target.loader)] (continuing)." -ForegroundColor Red
                $failedBuilds += "${mc}:$($target.loader)"
                continue
            }

            $moduleLibs = Join-Path $repoRoot "$($target.module)/build/libs"
            $jarCandidates = Get-ChildItem -Path $moduleLibs -Filter *.jar -ErrorAction SilentlyContinue |
                Where-Object {
                    $_.Name -notlike "*-sources.jar" -and
                    $_.Name -notlike "*-javadoc.jar"
                }
            $preferred = @($jarCandidates | Where-Object { $_.Name -like "NekoNameTags-*.jar" })
            if ($preferred.Count -gt 0) {
                $jarFiles = $preferred
            } else {
                $jarFiles = $jarCandidates
            }
            foreach ($jar in $jarFiles) {
                $base = [System.IO.Path]::GetFileNameWithoutExtension($jar.Name)
                $ext = [System.IO.Path]::GetExtension($jar.Name)
                if ($base -notmatch "(^|[-_])mc$([regex]::Escape($mc))($|[-_])") {
                    $base = "$base-mc$mc"
                }
                if ($base -notmatch "(^|[-_])$([regex]::Escape($target.loader))($|[-_])") {
                    $base = "$base-$($target.loader)"
                }
                $outName = "$base$ext"
                Copy-Item $jar.FullName -Destination (Join-Path $releaseOut $outName) -Force
            }
        }
    }

    $count = @(Get-ChildItem $releaseOut -Filter *.jar -ErrorAction SilentlyContinue).Count
    Write-Host "Release files prepared: $count ($releaseOut)"
    if ($failedBuilds.Count -gt 0) {
        Write-Host "Failed builds: $($failedBuilds -join ', ')" -ForegroundColor Yellow
    }
    if ($count -eq 0) {
        Write-Host "No successful builds produced jars." -ForegroundColor Red
        exit 1
    }
    Write-Host "Done."
    exit 0
}
finally {
    Pop-Location
}
