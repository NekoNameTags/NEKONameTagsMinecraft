# NEKONameTags Minecraft

Multi-loader NekoNameTags foundation for:
- Forge mods
- Fabric mods
- Bukkit/Paper plugins

This repository is structured so one shared core can be reused across loader-specific modules.

## Why this layout

A single jar cannot realistically support every Minecraft version from `1.7.10` to `1.21.x` because:
- mappings and APIs changed heavily across versions
- Java runtime requirements differ by version band
- build tooling differs for legacy and modern loaders

This repo uses a practical strategy:
- `core`: loader-agnostic logic (API fetch, models, tag text/color helpers)
- `plugin-paper`: Bukkit/Paper implementation
- `mod-fabric-1_21`: Fabric 1.21.x implementation scaffold
- `mod-forge-1_21`: Forge 1.21.x implementation scaffold
- `versions/`: version profile metadata and orchestration notes

## Quick start

1. Install JDK 21 for modern builds.
2. Configure API URL in `gradle.properties` (`nekonametags_api_url`).
3. Build target modules:
   - `./gradlew :plugin-paper:build`
   - `./gradlew :mod-fabric-1_21:build`
   - `./gradlew :mod-forge-1_21:build`

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

## Next implementation steps

1. Add per-version adapter modules using `versions/version-matrix.yml`.
2. Add CI matrix to compile each module with its required JDK.
3. Add runtime render logic per loader (client render hooks for mod loaders, scoreboard/team tags for server plugins).
