param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("all", "paper", "fabric", "forge", "bukkit")]
    [string]$Loader = "all",

    [Parameter(Mandatory = $false)]
    [string]$MinecraftVersion = ""
)

$script = Join-Path $PSScriptRoot "build-matrix.ps1"
& $script -Profile mid_1_13_to_1_16 -MatrixFile "versions/minecraft-1.13-1.16-builds.json" -Loader $Loader -MinecraftVersion $MinecraftVersion
exit $LASTEXITCODE

