# Patchwork spawn-patch compatibility matrix

| HyDragon target | Patchwork range | Integration | Validation | Failure behavior |
| --- | --- | --- | --- | --- |
| Hytale `0.5.7.x` | `>=1.1.0 <2.0.0` | Required additive spawn patches | Repository JSON/package validation; live Patchwork status/self-test before release | HyDragon fails manifest dependency resolution if Patchwork is missing or incompatible |

The patches append guarded NPC entries to existing spawn pools. They remain compatible with other mods that also add entries, provided those mods do not replace the same base-game spawn asset wholesale.
