param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("all", "forge", "bukkit")]
    [string]$Loader = "all",

    [Parameter(Mandatory = $false)]
    [string]$MinecraftVersion = ""
)

$script = Join-Path $PSScriptRoot "build-matrix.ps1"
& $script -Profile legacy_1_7_10 -MatrixFile "versions/minecraft-1.7.10-builds.json" -Loader $Loader -MinecraftVersion $MinecraftVersion
exit $LASTEXITCODE

