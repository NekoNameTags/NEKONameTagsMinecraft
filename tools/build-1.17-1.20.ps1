param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("all", "paper", "sponge", "fabric", "forge", "neoforge", "bukkit")]
    [string]$Loader = "all",

    [Parameter(Mandatory = $false)]
    [string]$MinecraftVersion = ""
)

$script = Join-Path $PSScriptRoot "build-matrix.ps1"
& $script -Profile modern_1_17_to_1_20 -MatrixFile "versions/minecraft-1.17-1.20-builds.json" -Loader $Loader -MinecraftVersion $MinecraftVersion
exit $LASTEXITCODE

