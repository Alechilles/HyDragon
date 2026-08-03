# Patchwork spawn-patch compatibility matrix

| HyDragon target | Patchwork range | Integration | Validation | Failure behavior |
| --- | --- | --- | --- | --- |
| Hytale `0.5.7.x` | `>=1.1.0 <2.0.0` | Required additive spawn patches | Repository JSON/package validation; live Patchwork status/self-test before release | HyDragon fails manifest dependency resolution if Patchwork is missing or incompatible |

The patches append guarded Nordic and Rock Drake entries to existing spawn pools. Hydra's two uniquely named standalone world-spawn assets do not replace or mutate base-game files. These paths remain compatible with other mods that also add entries, provided those mods do not replace the same base-game spawn asset wholesale.
