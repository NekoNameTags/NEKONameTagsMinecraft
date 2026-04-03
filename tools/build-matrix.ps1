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

function Test-LegacyForgeModuleCompatible {
    param(
        [Parameter(Mandatory = $true)][string]$ProfileName,
        [Parameter(Mandatory = $true)][string]$LoaderName,
        [Parameter(Mandatory = $true)][string]$ModuleDir
    )

    if ($LoaderName -ne "forge") {
        return $true
    }
    if ($ProfileName -ne "legacy_1_8_to_1_12") {
        return $true
    }

    $buildKts = Join-Path $ModuleDir "build.gradle.kts"
    $buildGroovy = Join-Path $ModuleDir "build.gradle"
    if (Test-Path $buildGroovy) {
        return $true
    }
    if (Test-Path $buildKts) {
        $raw = Get-Content $buildKts -Raw
        if ($raw -match 'id\("net\.minecraftforge\.gradle"\)\s+version\s+"6\.') {
            return $false
        }
        return $true
    }

    return $false
}

function Get-RootModVersion {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $gradlePropsPath = Join-Path $RepoRoot "gradle.properties"
    if (-not (Test-Path $gradlePropsPath)) {
        return "0.1.10"
    }

    $line = Get-Content $gradlePropsPath | Where-Object { $_ -match '^mod_version=' } | Select-Object -First 1
    if (-not $line) {
        return "0.1.10"
    }
    return $line.Split('=')[1].Trim()
}

function Invoke-LegacyStandaloneForgeBuild {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$ModuleDir,
        [Parameter(Mandatory = $true)][string]$MinecraftVersion,
        [Parameter(Mandatory = $true)][string[]]$Props,
        [Parameter(Mandatory = $true)][bool]$IsWindowsHost
    )

    $modVersion = Get-RootModVersion -RepoRoot $RepoRoot
    $args = @(
        "--no-daemon"
        "-c", "settings.gradle"
        "-b", "build.gradle"
        "build"
        "-Pmod_version=$modVersion"
        "-Pminecraft_version=$MinecraftVersion"
    ) + $Props

    Push-Location $ModuleDir
    try {
        if ($IsWindowsHost) {
            & ..\..\..\gradlew.bat @args
        } else {
            & ../../../gradlew @args
        }
    }
    finally {
        Pop-Location
    }
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

    function Get-ModuleNameForLoaderVersion {
        param(
            [Parameter(Mandatory = $true)][string]$LoaderName,
            [Parameter(Mandatory = $true)][string]$McVersion
        )

        $effectiveLoader = if ($LoaderName -eq "bukkit") { "paper" } else { $LoaderName }
        if ($effectiveLoader -notin @("paper", "sponge", "fabric", "forge", "neoforge")) {
            return ""
        }
        if ([string]::IsNullOrWhiteSpace($McVersion)) {
            return ""
        }
        return "$effectiveLoader/$McVersion"
    }

    function Get-ModuleDirectoryForLoaderVersion {
        param(
            [Parameter(Mandatory = $true)][string]$LoaderName,
            [Parameter(Mandatory = $true)][string]$McVersion
        )

        $effectiveLoader = if ($LoaderName -eq "bukkit") { "paper" } else { $LoaderName }
        $loaderBaseDir = switch ($effectiveLoader) {
            "paper" { "plugins/paper" }
            "sponge" { "plugins/sponge" }
            "fabric" { "mods/fabric" }
            "forge" { "mods/forge" }
            "neoforge" { "mods/neoforge" }
            default { "" }
        }

        if ([string]::IsNullOrWhiteSpace($loaderBaseDir) -or [string]::IsNullOrWhiteSpace($McVersion)) {
            return ""
        }

        return Join-Path (Join-Path $repoRoot $loaderBaseDir) $McVersion
    }

    $requiredFieldsByLoader = @{
        paper = @("paper_api_version")
        bukkit = @("paper_api_version")
        sponge = @("sponge_api_version")
        fabric = @("fabric_loader_version", "fabric_api_version", "yarn_mappings")
        forge = @("forge_version")
        neoforge = @("neoforge_version")
    }

    $failedBuilds = @()
    $attemptedBuilds = 0

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
            $moduleName = Get-ModuleNameForLoaderVersion -LoaderName $l -McVersion $mc
            $moduleDir = Get-ModuleDirectoryForLoaderVersion -LoaderName $l -McVersion $mc
            if (-not $moduleName -or [string]::IsNullOrWhiteSpace([string]$moduleName)) {
                continue
            }
            if (-not (Test-Path $moduleDir)) {
                Write-Host "Skipping loader '$l' for ${mc}: module missing ($moduleName)" -ForegroundColor Yellow
                continue
            }
            if (-not (Test-LegacyForgeModuleCompatible -ProfileName $Profile -LoaderName $l -ModuleDir $moduleDir)) {
        Write-Host "Skipping loader '$l' for ${mc}: profile '$Profile' is incompatible with modern ForgeGradle scaffold in $moduleName" -ForegroundColor Yellow
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
                moduleDir = $moduleDir
            }
        }

        if ($buildTargets.Count -eq 0) {
            Write-Host "Skipping ${mc}: no buildable loaders for current repo/matrix." -ForegroundColor Yellow
            continue
        }

        foreach ($target in $buildTargets) {
            $attemptedBuilds++
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

            $moduleParts = $target.module -split '/'
            if ($moduleParts.Count -eq 2) {
                $loaderPart = $moduleParts[0]
                $versionPart = ($moduleParts[1] -replace '\.', '_')
                $moduleTask = ":${loaderPart}:${versionPart}:build"
            } else {
                $moduleTask = ":$($target.module):build"
            }
            Write-Host "Building Minecraft $mc ($($target.loader)) with task: $moduleTask"

            $useLegacyStandaloneForge = (
                $Profile -eq "legacy_1_8_to_1_12" -and
                $target.loader -eq "forge" -and
                (Test-Path (Join-Path $target.moduleDir "build.gradle"))
            )

            if ($useLegacyStandaloneForge) {
                Invoke-LegacyStandaloneForgeBuild -RepoRoot $repoRoot -ModuleDir $target.moduleDir -MinecraftVersion $mc -Props $props -IsWindowsHost $isWindowsHost
            } elseif ($isWindowsHost) {
                & .\gradlew.bat --no-daemon :core:build $moduleTask "-Pnnt_target_loader=$($target.loader)" "-Pnnt_minecraft_version=$mc" @props
            } else {
                & ./gradlew --no-daemon :core:build $moduleTask "-Pnnt_target_loader=$($target.loader)" "-Pnnt_minecraft_version=$mc" @props
            }
            if ($LASTEXITCODE -ne 0 -and $target.loader -eq "neoforge") {
                Write-Host "NeoForge build failed, cleaning NeoForm caches and retrying once..." -ForegroundColor Yellow
                $neoTmp = Join-Path $repoRoot "$($target.module)/build/tmp/createMinecraftArtifacts"
                if (Test-Path $neoTmp) {
                    Remove-Item -Recurse -Force $neoTmp -ErrorAction SilentlyContinue
                }
                $userHomePath = [System.Environment]::GetFolderPath("UserProfile")
                if (-not $userHomePath -or [string]::IsNullOrWhiteSpace($userHomePath)) {
                    $userHomePath = $env:HOME
                }
                if ($userHomePath -and -not [string]::IsNullOrWhiteSpace($userHomePath)) {
                    $neoCache = Join-Path $userHomePath ".gradle/caches/neoformruntime"
                    if (Test-Path $neoCache) {
                        Remove-Item -Recurse -Force $neoCache -ErrorAction SilentlyContinue
                    }
                }

                if ($isWindowsHost) {
                    & .\gradlew.bat --no-daemon :core:build $moduleTask "-Pnnt_target_loader=$($target.loader)" "-Pnnt_minecraft_version=$mc" @props
                } else {
                    & ./gradlew --no-daemon :core:build $moduleTask "-Pnnt_target_loader=$($target.loader)" "-Pnnt_minecraft_version=$mc" @props
                }
            }
            if ($LASTEXITCODE -ne 0) {
                Write-Host "Build failed for $mc [$($target.loader)] (continuing)." -ForegroundColor Red
                $failedBuilds += "${mc}:$($target.loader)"
                continue
            }

            $moduleLibs = Join-Path $target.moduleDir "build/libs"
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
    if ($count -eq 0 -and $attemptedBuilds -eq 0) {
        Write-Host "No buildable loaders/modules found for this profile in the current repository. Skipping without error." -ForegroundColor Yellow
        exit 0
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
