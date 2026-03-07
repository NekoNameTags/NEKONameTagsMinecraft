# Legacy Render Split

The project now keeps legacy version folders separate from modern modules.

## Folders
- `legacy-forge-1_7_10`
- `legacy-forge-1_8_9`

## Why
- Prevent old API/mapping requirements from breaking modern builds.
- Allow per-version render path selection (armor-stand vs manual label fallback).

## Next implementation step
- Add each legacy module to Gradle only when its toolchain and mappings are configured.
