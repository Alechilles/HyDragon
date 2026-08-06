# Patchwork spawn-patch compatibility matrix

| HyDragon target | Patchwork range | Integration | Validation | Failure behavior |
| --- | --- | --- | --- | --- |
| Hytale `0.5.7.x` | `>=1.1.0 <2.0.0` | Required additive Nordic Drake spawn patch | Repository JSON/package validation; live Patchwork status/self-test before release | HyDragon fails manifest dependency resolution if Patchwork is missing or incompatible |

The guarded Nordic Drake patch appends one entry to the existing Zone 3 forest predator pool. Hydra's two uniquely named standalone world-spawn assets own its independent lunar tuning, and Rock Drakes' three standalone cave beacons under `Server/NPC/Spawn/Beacons/HyDragon` coexist with, but do not alter, vanilla beacons. These paths remain compatible with other mods that also add entries, provided those mods do not replace the same base-game spawn asset wholesale.
