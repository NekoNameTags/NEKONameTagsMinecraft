# NEKONameTags Minecraft

Multi-loader NekoNameTags foundation for:
- Forge mods
- NeoForge mods
- Fabric mods
- Bukkit/Paper plugins
- Sponge plugins

This repository is structured so one shared core can be reused across loader-specific modules.

Top-level layout:
- `core/`: shared mod + plugin logic
- `ui/core/`: shared UI addon logic
- `mods/`: client mod loaders grouped by loader, then version
- `ui/`: client UI addons grouped by loader, then version
- `plugins/`: server plugins grouped by platform, then version

## Why this layout

A single jar cannot realistically support every Minecraft version from `1.7.10` to `1.21.x` because:
- mappings and APIs changed heavily across versions
- Java runtime requirements differ by version band
- build tooling differs for legacy and modern loaders

This repo uses a practical strategy:
- `core`: loader-agnostic logic (API fetch, models, tag text/color helpers)
- `ui/core`: shared UI addon logic (WEB settings, Minecraft tag management state)
- `mods/fabric/<mc-version>`: Fabric mod modules
- `mods/forge/<mc-version>`: Forge mod modules
- `mods/neoforge/<mc-version>`: NeoForge mod modules
- `mods/labymod`: standalone LabyMod addon project
- `ui/fabric/<mc-version>`: Fabric UI addon modules
- `ui/forge/<mc-version>`: Forge UI addon modules
- `ui/neoforge/<mc-version>`: NeoForge UI addon modules
- `plugins/paper/<mc-version>`: Bukkit/Paper plugin modules
- `plugins/sponge/<mc-version>`: Sponge plugin modules
- `versions/`: version profile metadata and orchestration notes

## Quick start

1. Install JDK 21 for modern builds.
2. Set version in one place: `gradle.properties` -> `mod_version=...`
3. Configure API URL in `gradle.properties` (`nekonametags_api_url`).
4. Build target modules:
   - `./gradlew :paper:1_21_11:build`
   - `./gradlew :sponge:1_21_11:build`
   - `./gradlew :fabric:1_21_11:build`
   - `./gradlew :forge:1_21_11:build`
   - `./gradlew :neoforge:1_21_11:build`
   - `./gradlew :fabric-ui:1_21_11:build`
   - `./gradlew :forge-ui:1_21_11:build`
   - `./gradlew :neoforge-ui:1_21_11:build`

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

### UI module routing

UI addons follow the same versioned layout pattern as the client mods:
- `ui/fabric/<mc-version>`
- `ui/forge/<mc-version>`
- `ui/neoforge/<mc-version>`

If a version folder exists, Gradle can include and build it the same way as the main mod loaders.

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

## LabyMod addon

`mod-labymod` is a standalone Gradle project for a LabyMod addon.

Configured Minecraft versions:
- `1.8.9`, `1.12.2`, `1.16.5`
- `1.17.1`, `1.18.2`, `1.19.4`
- `1.20.1`, `1.20.6`
- `1.21`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`

Build command:
- `tools\build-labymod.bat`

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
