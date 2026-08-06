# HyDragon - Patchwork spawn-patch contract

## Purpose

HyDragon uses Patchwork only to append Nordic Drake to the base Zone 3 forest predator pool. Hydra owns two uniquely named standalone `WorldNPCSpawn` assets for independent lunar tuning, and Rock Drakes own three standalone `BeaconNPCSpawn` assets under `Server/NPC/Spawn/Beacons/HyDragon` for their volcanic cave encounters. None of these five standalone assets mutates a base-game spawn asset.

## Version and load contract

- HyDragon uses the Patchwork runtime shaded into its required Tamework version.
- Patchwork must load before HyDragon asset patches are applied.
- If Patchwork is unavailable or incompatible, the HyDragon manifest dependency rejects startup. HyDragon must not silently load with its dragon spawns absent.

## Exposed integration surface

The Patchwork surface is `Server/Patchwork/Patches/HyDragon/NordicDrake_Zone3_Forests_Predator.json`.

- The Nordic patch targets the existing Zone 3 forest predator `WorldNPCSpawn` asset.
- Its operation uses `Insert` at `/NPCs` with an `Existing` `NordicDrake` role-ID guard.
- The patch appends only Nordic Drake; it does not replace arrays, remove entries, or modify top-level pool conditions owned by the target asset.

The two Hydra assets under `Server/NPC/Spawn/World` and the three Rock Drake assets under `Server/NPC/Spawn/Beacons/HyDragon` are not patches. They must remain uniquely named and do not mutate a base-game spawn asset.

## Ownership

HyDragon owns the Nordic patch, its dragon roles, relative weights, two Hydra world-spawn assets, and three Rock Drake beacon assets. Patchwork owns patch application. Base-game and third-party mods retain ownership of their spawn assets and entries.

## Validation and rollback

Validate JSON syntax, the guarded Nordic patch target, and packaged standalone asset presence before release. Validate a live server with Patchwork installed through its status/self-test commands. Removing the HyDragon JAR removes the additive Nordic entry and all five standalone spawn assets without modifying vanilla files in place.
