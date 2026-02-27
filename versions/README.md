# Version Strategy

To support `1.7.10` through `1.21.x`, keep separate modules per loader/version band.

## Rule set

- Shared protocol/data logic stays in `core` where possible.
- Loader bindings stay in versioned modules.
- Each band uses its required Java level and build plugins.
- Avoid cross-compiling one module against all versions.

## Suggested rollout order

1. `modern_1_21` (already scaffolded in this repo)
2. `modern_1_17_to_1_20`
3. `mid_1_13_to_1_16`
4. `legacy_1_8_to_1_12`
5. `legacy_1_7_10`

