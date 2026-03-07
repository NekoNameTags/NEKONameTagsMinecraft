param()
$ErrorActionPreference = 'Stop'
$root='d:\DEV\NekoSuneVRAPPS\Programs\NekoNameTags\Mod&Addons\MINECRAFT'
$templates = @{
  paper = Join-Path $root 'plugin-paper'
  sponge = Join-Path $root 'plugin-sponge'
  forge = Join-Path $root 'mod-forge-1_21'
  neoforge = Join-Path $root 'mod-neoforge-1_21'
  fabricDefault = Join-Path $root 'mod-fabric-1_21'
  fabric2111 = Join-Path $root 'mod-fabric-1_21_11'
}

function Sync-Module([string]$templateDir, [string]$targetDir) {
  Copy-Item -Force (Join-Path $templateDir 'build.gradle.kts') (Join-Path $targetDir 'build.gradle.kts')
  $srcTemplate = Join-Path $templateDir 'src'
  $srcTarget = Join-Path $targetDir 'src'
  New-Item -ItemType Directory -Force -Path $srcTarget | Out-Null
  Copy-Item -Recurse -Force (Join-Path $srcTemplate '*') $srcTarget
}

$counts = [ordered]@{ paper = 0; sponge = 0; forge = 0; neoforge = 0; fabric = 0 }

Get-ChildItem -Path (Join-Path $root 'paper') -Directory | ForEach-Object { Sync-Module $templates.paper $_.FullName; $counts.paper++ }
Get-ChildItem -Path (Join-Path $root 'sponge') -Directory | ForEach-Object { Sync-Module $templates.sponge $_.FullName; $counts.sponge++ }
Get-ChildItem -Path (Join-Path $root 'forge') -Directory | ForEach-Object { Sync-Module $templates.forge $_.FullName; $counts.forge++ }
Get-ChildItem -Path (Join-Path $root 'neoforge') -Directory | ForEach-Object { Sync-Module $templates.neoforge $_.FullName; $counts.neoforge++ }
Get-ChildItem -Path (Join-Path $root 'fabric') -Directory | ForEach-Object {
  if ($_.Name -in @('1.21.10','1.21.11')) { Sync-Module $templates.fabric2111 $_.FullName } else { Sync-Module $templates.fabricDefault $_.FullName }
  $counts.fabric++
}

$counts.GetEnumerator() | ForEach-Object { "{0}={1}" -f $_.Key, $_.Value }
