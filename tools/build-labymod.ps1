param(
    [Parameter(Mandatory = $false)]
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$projectRoot = Join-Path $repoRoot "mod-labymod"

if (-not (Test-Path $projectRoot)) {
    Write-Host "Missing mod-labymod project at: $projectRoot" -ForegroundColor Red
    exit 1
}

Push-Location $projectRoot
try {
    if ([string]::IsNullOrWhiteSpace($Version)) {
        & .\gradlew.bat --no-daemon :core:build
    } else {
        $env:VERSION = $Version
        & .\gradlew.bat --no-daemon :core:build
    }

    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $libs = Join-Path $projectRoot "core/build/libs"
    if (Test-Path $libs) {
        Get-ChildItem $libs -Filter *.jar | Select-Object Name, Length, LastWriteTime
    }
}
finally {
    Pop-Location
}
