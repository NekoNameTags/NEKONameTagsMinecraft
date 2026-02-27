param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("modern_1_21", "legacy_1_7_10", "legacy_1_8_to_1_12", "mid_1_13_to_1_16", "modern_1_17_to_1_20")]
    [string]$Profile = "modern_1_21"
)

$ErrorActionPreference = "Stop"

$profileTasks = @{
    modern_1_21        = @(":core:build", ":plugin-paper:build", ":mod-fabric-1_21:build", ":mod-forge-1_21:build")
    legacy_1_7_10      = @(":legacy-forge-1_7_10:build", ":legacy-plugin-1_7_10:build")
    legacy_1_8_to_1_12 = @(":legacy-forge-1_12_2:build", ":legacy-plugin-1_8_8:build")
    mid_1_13_to_1_16   = @(":plugin-paper-1_16:build", ":mod-fabric-1_16:build", ":mod-forge-1_16:build")
    modern_1_17_to_1_20 = @(":plugin-paper-1_20:build", ":mod-fabric-1_20:build", ":mod-forge-1_20:build")
}

if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "gradlew.bat not found in repo root." -ForegroundColor Red
    exit 1
}

$tasks = $profileTasks[$Profile]
if (-not $tasks) {
    Write-Host "Unknown profile: $Profile" -ForegroundColor Red
    exit 1
}

Write-Host "Building profile: $Profile"
& .\gradlew.bat @tasks

