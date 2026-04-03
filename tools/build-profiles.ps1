param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("modern_1_21", "legacy_1_7_10", "legacy_1_8_to_1_12", "mid_1_13_to_1_16", "modern_1_17_to_1_20")]
    [string]$Profile = "modern_1_21",

    [Parameter(Mandatory = $false)]
    [switch]$PrepareOnly
)

$ErrorActionPreference = "Stop"

function Get-JavaMajor {
    param([Parameter(Mandatory = $true)][string]$JavaExePath)

    $cmdLine = "`"$JavaExePath`" -version 2>&1"
    $v = & cmd.exe /d /c $cmdLine | Select-Object -First 1
    if ($v -match '"1\.(\d+)') {
        return [int]$Matches[1]
    }
    if ($v -match '"(\d+)') {
        return [int]$Matches[1]
    }
    return $null
}

function Resolve-JavaHome {
    param([Parameter(Mandatory = $true)][int]$RequiredMajor)

    $candidates = New-Object System.Collections.Generic.List[string]
    $roots = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Zulu",
        "C:\Program Files (x86)\Java"
    )

    foreach ($root in $roots) {
        if (-not (Test-Path $root)) {
            continue
        }
        Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $javaExe = Join-Path $_.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                try {
                    $major = Get-JavaMajor -JavaExePath $javaExe
                    if ($major -eq $RequiredMajor) {
                        $candidates.Add($_.FullName)
                    }
                } catch {
                    # Ignore invalid entries.
                }
            }
        }
    }

    $fromJavaHome = $env:JAVA_HOME
    if ($fromJavaHome) {
        $javaExe = Join-Path $fromJavaHome "bin\java.exe"
        if (Test-Path $javaExe) {
            try {
                $major = Get-JavaMajor -JavaExePath $javaExe
                if ($major -eq $RequiredMajor) {
                    $candidates.Add($fromJavaHome)
                }
            } catch {
                # Ignore invalid JAVA_HOME.
            }
        }
    }

    $unique = @($candidates | Sort-Object -Descending -Unique)
    if ($unique.Count -gt 0) {
        return $unique[0]
    }
    return $null
}

function Read-RequiredJavaFromMatrix {
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

    $javaVersion = $null
    for ($i = $profileStart + 1; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match "^\s{2}[A-Za-z0-9_]+:\s*$") {
            break
        }
        if ($line -match "^\s{4}java:\s*(\d+)\s*$") {
            $javaVersion = [int]$Matches[1]
            continue
        }
    }

    if (-not $javaVersion) {
        throw "profile '$ProfileName' has no java version in $MatrixPath"
    }
    return $javaVersion
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
        [Parameter(Mandatory = $true)][string]$DistributionUrl
    )

    $wrapperPath = Join-Path $RepoRoot "gradle\wrapper\gradle-wrapper.properties"
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
    Write-Host "Set Gradle wrapper distribution for '$Profile' to $DistributionUrl"
}

function Read-AllJavaVersionsFromMatrix {
    param([Parameter(Mandatory = $true)][string]$MatrixPath)

    if (-not (Test-Path $MatrixPath)) {
        throw "version matrix not found: $MatrixPath"
    }

    $versions = @()
    Get-Content $MatrixPath | ForEach-Object {
        if ($_ -match "^\s{4}java:\s*(\d+)\s*$") {
            $versions += [int]$Matches[1]
        }
    }
    return @($versions | Sort-Object -Unique)
}

function Install-JdkWithWinget {
    param([Parameter(Mandatory = $true)][int]$Major)

    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget) {
        Write-Host "winget is not installed. Install JDK $Major manually and rerun." -ForegroundColor Red
        return $false
    }

    $packageId = switch ($Major) {
        8  { "EclipseAdoptium.Temurin.8.JDK" }
        17 { "EclipseAdoptium.Temurin.17.JDK" }
        21 { "EclipseAdoptium.Temurin.21.JDK" }
        default { $null }
    }

    if (-not $packageId) {
        Write-Host "No automatic installer mapping configured for JDK $Major." -ForegroundColor Red
        return $false
    }

    Write-Host "Installing JDK $Major via winget package '$packageId'..."
    & winget install --id $packageId --accept-package-agreements --accept-source-agreements --silent
    if ($LASTEXITCODE -ne 0) {
        Write-Host "JDK $Major installation failed (exit code $LASTEXITCODE)." -ForegroundColor Red
        return $false
    }
    return $true
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$matrixPath = Join-Path $repoRoot "versions\version-matrix.yml"
$gradlew = Join-Path $repoRoot "gradlew.bat"

if (-not (Test-Path $gradlew)) {
    Write-Host "gradlew.bat not found in repo root: $repoRoot" -ForegroundColor Red
    exit 1
}

$profileModules = @{
    modern_1_21 = @("plugin-paper", "plugin-sponge", "mod-fabric-1_21", "mod-forge-1_21", "mod-neoforge-1_21")
    legacy_1_7_10 = @("legacy-forge-1_7_10", "legacy-plugin-1_7_10")
    legacy_1_8_to_1_12 = @("legacy-forge-1_12_2", "legacy-plugin-1_8_8")
    mid_1_13_to_1_16 = @("plugin-paper-1_16", "mod-fabric-1_16", "mod-forge-1_16")
    modern_1_17_to_1_20 = @("plugin-paper-1_20", "mod-fabric-1_20", "mod-forge-1_20")
}

$modules = $profileModules[$Profile]
if (-not $modules) {
    Write-Host "Unknown profile: $Profile" -ForegroundColor Red
    exit 1
}

$requiredJava = [int](Read-RequiredJavaFromMatrix -MatrixPath $matrixPath -ProfileName $Profile)
$requiredGradleDistribution = Read-GradleDistributionUrlFromMatrix -MatrixPath $matrixPath -ProfileName $Profile
$allMatrixJavas = @(Read-AllJavaVersionsFromMatrix -MatrixPath $matrixPath)

$installedVersions = @()
$missingVersions = @()
foreach ($v in $allMatrixJavas) {
    $foundJavaHome = Resolve-JavaHome -RequiredMajor $v
    if ($foundJavaHome) {
        $installedVersions += "JDK $v ($foundJavaHome)"
    } else {
        $missingVersions += $v
    }
}

Write-Host "Java check from version matrix:"
if ($installedVersions.Count -gt 0) {
    Write-Host "Installed:"
    $installedVersions | ForEach-Object { Write-Host " - $_" }
} else {
    Write-Host "Installed: none detected"
}
if ($missingVersions.Count -gt 0) {
    Write-Host "Missing: $($missingVersions -join ', ')" -ForegroundColor Yellow
} else {
    Write-Host "Missing: none"
}

if (-not $PrepareOnly) {
    $missingModules = @()
    foreach ($m in $modules) {
        $moduleDir = Join-Path $repoRoot $m
        if (-not (Test-Path $moduleDir)) {
            $missingModules += $m
        }
    }
    if ($missingModules.Count -gt 0) {
        Write-Host "Profile '$Profile' cannot build; missing module directories:" -ForegroundColor Red
        $missingModules | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
        exit 1
    }
}

$javaHome = Resolve-JavaHome -RequiredMajor $requiredJava
if (-not $javaHome) {
    Write-Host "Missing required Java $requiredJava for profile '$Profile'." -ForegroundColor Red
    $answer = ""
    while ($answer -notin @("yes", "no", "y", "n")) {
        $raw = Read-Host "Install missing JDK $requiredJava now? (yes/no)"
        if ($null -eq $raw) {
            Write-Host "No input received. Build stopped." -ForegroundColor Red
            exit 1
        }
        $answer = $raw.ToLowerInvariant().Trim()
    }

    if ($answer -in @("no", "n")) {
        Write-Host "Build stopped. Install JDK $requiredJava and run again." -ForegroundColor Red
        exit 1
    }

    $installed = Install-JdkWithWinget -Major $requiredJava
    if (-not $installed) {
        Write-Host "Build stopped because required JDK $requiredJava is still missing." -ForegroundColor Red
        exit 1
    }

    $javaHome = Resolve-JavaHome -RequiredMajor $requiredJava
    if (-not $javaHome) {
        Write-Host "JDK $requiredJava install finished but Java was not detected yet." -ForegroundColor Red
        Write-Host "Close and reopen terminal, then run build again." -ForegroundColor Yellow
        exit 1
    }
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
Set-GradleWrapperDistributionUrl -RepoRoot $repoRoot -DistributionUrl $requiredGradleDistribution

if ($PrepareOnly) {
    Write-Host "Prepared JAVA_HOME for profile '$Profile': $javaHome"
    exit 0
}

$tasks = @(":core:build") + ($modules | ForEach-Object { ":$($_):build" })
Write-Host "Building profile: $Profile"
Write-Host "Using JAVA_HOME: $javaHome"
Write-Host "Tasks: $($tasks -join ' ')"

Push-Location $repoRoot
try {
    & $gradlew @tasks
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
