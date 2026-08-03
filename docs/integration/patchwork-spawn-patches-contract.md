# HyDragon - Patchwork spawn-patch contract

## Purpose

HyDragon adds dragon roles to existing base-game spawn pools without replacing the pools. Patchwork is the required integration layer for those changes.

## Version and load contract

- HyDragon requires `Alechilles:Patchwork >=1.1.0 <2.0.0`.
- Patchwork must load before HyDragon asset patches are applied.
- If Patchwork is unavailable or incompatible, the HyDragon manifest dependency rejects startup. HyDragon must not silently load with its dragon spawns absent.

## Exposed integration surface

The only surface is `Server/Patchwork/Patches/HyDragon/*.json`.

- Each patch targets an existing base-game `WorldNPCSpawn` or `BeaconNPCSpawn` asset.
- Each operation uses `Insert` at `/NPCs` with an `Existing` role-ID guard.
- Patches append roles only; they do not replace arrays, remove entries, or modify top-level pool conditions owned by the target asset.

## Ownership

HyDragon owns its patch files, dragon roles, and relative weights. Patchwork owns patch application. Base-game and third-party mods retain ownership of their spawn assets and entries.

## Validation and rollback

Validate JSON syntax, patch targets, and packaged asset presence before release. Validate a live server with Patchwork installed through its status/self-test commands. Removing the HyDragon JAR cleanly removes its additions; no target spawn asset is modified in place.
