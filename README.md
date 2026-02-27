# NEKONameTags Minecraft

Multi-loader NekoNameTags foundation for:
- Forge mods
- NeoForge mods
- Fabric mods
- Bukkit/Paper plugins
- Sponge plugins

This repository is structured so one shared core can be reused across loader-specific modules.

## Why this layout

A single jar cannot realistically support every Minecraft version from `1.7.10` to `1.21.x` because:
- mappings and APIs changed heavily across versions
- Java runtime requirements differ by version band
- build tooling differs for legacy and modern loaders

This repo uses a practical strategy:
- `core`: loader-agnostic logic (API fetch, models, tag text/color helpers)
- `plugin-paper`: Bukkit/Paper implementation
- `plugin-sponge`: Sponge implementation scaffold
- `mod-fabric-1_21`: Fabric 1.21.x implementation scaffold
- `mod-fabric-1_21_11`: Fabric 1.21.10-1.21.11 implementation scaffold
- `mod-forge-1_21`: Forge 1.21.x implementation scaffold
- `mod-neoforge-1_21`: NeoForge 1.21.x implementation scaffold
- `versions/`: version profile metadata and orchestration notes

## Quick start

1. Install JDK 21 for modern builds.
2. Set version in one place: `gradle.properties` -> `mod_version=...`
3. Configure API URL in `gradle.properties` (`nekonametags_api_url`).
4. Build target modules:
   - `./gradlew :plugin-paper:build`
   - `./gradlew :plugin-sponge:build`
   - `./gradlew :mod-fabric-1_21:build`
   - `./gradlew :mod-forge-1_21:build`
   - `./gradlew :mod-neoforge-1_21:build`

## GitHub release automation

- GitHub Actions builds all modules on push/PR.
- Pushing a tag like `v0.1.0` publishes a public GitHub Release with built jars.
- Version comes from `mod_version` in `gradle.properties`; update that single value.

## Minecraft 1.21.1 -> 1.21.11 matrix

- Version list file: `versions/minecraft-1.21x-builds.json`
- Build locally (all configured entries): `tools\build-1.21x.bat -Loader all`
- Build one version: `tools\build-1.21x.bat -Loader all -MinecraftVersion 1.21.1`
- Workflow: `.github/workflows/build-1.21x-matrix.yml` (manual dispatch, choose one OS: Windows or Linux)

Note: fill loader coordinates in `versions/minecraft-1.21x-builds.json` for each MC patch (Forge/NeoForge/Fabric/Paper entries). Empty fields are reported and skipped.

### Fabric module routing (1.21.x)

- `1.21.1` to `1.21.9` -> `mod-fabric-1_21`
- `1.21.10` and `1.21.11` -> `mod-fabric-1_21_11`

This split is intentional for API/mapping compatibility.

Release filename format stays stable and version-correct:
- `NekoNameTags-Fabric-<mod_version>-mc<mc_version>.jar`
- Examples:
  - `NekoNameTags-Fabric-0.1.7-mc1.21.10.jar`
  - `NekoNameTags-Fabric-0.1.7-mc1.21.11.jar`

### Fabric versions (from matrix)

- `1.21.1`: loader `0.16.10`, api `0.115.1+1.21.1`, yarn `1.21.1+build.3`
- `1.21.2`: loader `0.18.4`, api `0.106.1+1.21.2`, yarn `1.21.2+build.1`
- `1.21.3`: loader `0.18.4`, api `0.114.1+1.21.3`, yarn `1.21.3+build.2`
- `1.21.4`: loader `0.18.4`, api `0.119.4+1.21.4`, yarn `1.21.4+build.8`
- `1.21.5`: loader `0.18.4`, api `0.128.2+1.21.5`, yarn `1.21.5+build.1`
- `1.21.6`: loader `0.18.4`, api `0.128.2+1.21.6`, yarn `1.21.6+build.1`
- `1.21.7`: loader `0.18.4`, api `0.129.0+1.21.7`, yarn `1.21.7+build.8`
- `1.21.8`: loader `0.18.4`, api `0.136.1+1.21.8`, yarn `1.21.8+build.1`
- `1.21.9`: loader `0.18.4`, api `0.134.1+1.21.9`, yarn `1.21.9+build.1`
- `1.21.10`: loader `0.18.4`, api `0.138.4+1.21.10`, yarn `1.21.10+build.3`
- `1.21.11`: loader `0.18.4`, api `0.141.3+1.21.11`, yarn `1.21.11+build.4`

## Other version bands

- `versions/minecraft-1.7.10-builds.json` + workflow `build-1.7.10-matrix.yml`
- `versions/minecraft-1.8-1.12-builds.json` + workflow `build-1.8-1.12-matrix.yml`
- `versions/minecraft-1.13-1.16-builds.json` + workflow `build-1.13-1.16-matrix.yml`
- `versions/minecraft-1.17-1.20-builds.json` + workflow `build-1.17-1.20-matrix.yml`

Local helpers:
- `tools\build-1.7.10.bat`
- `tools\build-1.8-1.12.bat`
- `tools\build-1.13-1.16.bat`
- `tools\build-1.17-1.20.bat`

## Legacy support plan (`1.7.10` and older bands)

Use dedicated legacy modules per version band (do not mix with modern 1.21 modules):
- `legacy-forge-1_7_10`
- `legacy-forge-1_12_2`
- `legacy-plugin-1_8_8`

These can still depend on protocol-compatible pieces from `core` if you keep Java compatibility constraints in mind.

## Data model compatibility with CVR project

The shared model mirrors your CVR format:
- `UserId`
- `NamePlatesText[]`
- `BigPlatesText[]`
- `Color[]`
- `isLive`
- `platform`

For Minecraft API usage, `UserId` may be either:
- player UUID (recommended), e.g. `6db9eac6-df98-4ca0-90e7-d7537bef69a9`
- player name, e.g. `squeek1984`

Effect markers supported in shared parsing:
- `#rainbow#`
- `#animationtag#`

## Visibility behavior

- Rich CVR-style effects require client mod rendering (Fabric/Forge client).
- Server plugin can show fallback plain text prefixes for all players.
- Players without the mod will not see animated/rainbow overlays.

## Next implementation steps

1. Add per-version adapter modules using `versions/version-matrix.yml`.
2. Add CI matrix to compile each module with its required JDK.
3. Add runtime render logic per loader (client render hooks for mod loaders, scoreboard/team tags for server plugins).
