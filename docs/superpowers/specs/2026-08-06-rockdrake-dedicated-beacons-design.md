# Rockdrake Dedicated Beacons Design

## Goal

Make all three Rockdrake tiers discoverable in their intended volcanic caves
without changing vanilla beacon weights, vanilla beacon population caps, or
vanilla NPC selection behavior.

Rockdrakes should be rare, solitary encounters. At most one Rockdrake from a
given HyDragon beacon may be active in an area, while vanilla beacons continue
to operate exactly as authored by Hytale.

## Architecture

Replace the nine Patchwork operations that insert Rockdrakes into vanilla cave
beacons with three HyDragon-owned `BeaconNPCSpawn` assets:

- `HyDragon_RockDrakeT1_Volcanic_Caves` targets the Zone 1 T1, T2, and T3
  volcanic-cave environments and spawns `RockDrakeT1`.
- `HyDragon_RockDrakeT2_Volcanic_Caves` targets the Zone 2 T1, T2, and T3
  volcanic-cave environments and spawns `RockDrakeT2`.
- `HyDragon_RockDrakeT3_Volcanic_Caves` targets the Zone 3 T1, T2, and T3
  volcanic-cave environments and spawns `RockDrakeT3`.

The assets belong under `Server/NPC/Spawn/Beacons/HyDragon/`. Each asset is
registered independently for all three matching environment IDs. Hytale's
local spawn controller may therefore run the HyDragon beacon alongside vanilla
beacons, but it deduplicates additional beacons of the same HyDragon asset in
the active area.

This adds a deliberately bounded Rockdrake population rather than consuming or
diluting a vanilla beacon slot.

## Beacon Configuration

All three beacon assets use the same encounter pacing and spatial rules:

- `MinDistanceFromPlayer`: `15`
- `TargetDistanceFromPlayer`: `25`
- `SpawnRadius`: `50`
- `BeaconRadius`: `70`
- `YRange`: `[-24, 24]`
- `MaxSpawnedNPCs`: `1`
- `ConcurrentSpawnsRange`: `[1, 1]`
- `InitialSpawnDelayRange`: `PT10M` to `PT30M`
- `SpawnAfterGameTimeRange`: `PT45M` to `PT90M`
- `BeaconVacantDespawnGameTime`: `PT15M`
- `NPCIdleDespawnTime`: `60`
- `LightRanges.Light`: `[0, 7]`

Each beacon has exactly one NPC entry at weight `100`. The T1 and T2 entries
retain `SpawnBlockSet: Volcanic`, matching their vanilla volcanic-cave pools.
The T3 entry does not set a spawn block set because the Zone 3 volcanic-cave
terrain and its vanilla aquatic pool do not use that restriction; the volcanic
environment IDs remain the location boundary.

The wider `YRange` lets a beacon created near a flying player discover cave
floors above or below the default five-block band. Normal light, distance,
movement-mode, breathing, block, collision, and model-fit validation still
apply.

## Asset Wiring

Remove these Patchwork assets:

- the three `RockDrakeT1_Zone1_Cave_Volcanic_*_Aggro` patches;
- the three `RockDrakeT2_Zone2_Cave_Volcanic_*_Goblin` patches;
- the three `RockDrakeT3_Zone3_Cave_Volcanic_*_Aggro` patches.

Update each Rockdrake `DragonSpecies` asset so `OrdinarySpawnAssetIds` contains
its single HyDragon beacon ID instead of three Patchwork patch IDs. No vanilla
spawn JSON is replaced or modified.

Any validator logic that specifically expects the removed patch filenames or
targets must be deleted or updated. Do not replace it with source-text,
file-presence, asset-count, or exact-tuning assertions. The production JSON
parser, normal build, Patchwork diagnostics, and live spawn workflow provide
the relevant validation.

## Runtime Behavior

When a player enters a matching volcanic-cave environment, Hytale considers
both the vanilla beacon configurations and the corresponding HyDragon beacon.
The HyDragon beacon waits 10 to 30 in-game minutes before its first attempt. If
it finds a valid position, it spawns one Rockdrake and cannot spawn another
while that beacon tracks the existing one.

Each successful spawn schedules the next attempt 45 to 90 in-game minutes
later. The one-NPC cap prevents another spawn while the beacon still tracks its
Rockdrake. If players leave the beacon area, the normal vacancy timeout removes
the inactive beacon and allows the local spawn controller to recreate one when
the area becomes active again.

Because the Rockdrake is the only role in its beacon, a model-fit or movement
validation failure affects only that HyDragon encounter. It cannot silently
fall through to a vanilla mob and make a separate spawn source look healthy.

## Failure Handling and Diagnostics

- A beacon that finds no cave floor within `YRange` emits the standard Hytale
  position-selection warning.
- A Rockdrake that fits nowhere in the candidate area becomes unspawnable for
  that HyDragon beacon without changing any vanilla beacon.
- Invalid role, environment, or model references must be surfaced by the normal
  asset validation and server asset-load diagnostics rather than being hidden
  by fallback NPC entries.
- Patchwork's generated targets must no longer contain Rockdrake insertions
  after the obsolete patches are removed.

## Verification

Implementation verification will include:

1. Run the repository's normal asset validation and complete Gradle build.
2. Confirm all three new beacon assets load without NPC or spawn-configuration
   validation failures.
3. Confirm Patchwork no longer generates Rockdrake entries in vanilla cave
   beacon targets.
4. In each zone, enter a matching volcanic cave and manually add/trigger the
   corresponding HyDragon beacon at cave-floor height.
5. Confirm T1, T2, and T3 each produce their intended Rockdrake when a valid
   location exists.
6. Confirm repeated triggers do not exceed one Rockdrake for the beacon and
   that vanilla beacon populations continue independently.
7. Exercise at least one cave while flying more than five blocks above its
   floor to confirm the widened vertical search finds candidate terrain.
8. Update the changelog to describe the dedicated, non-invasive Rockdrake cave
   encounters.

No new unit test is warranted for shipped JSON presence, exact values, patch
absence, or asset inventory. Runtime behavior and the project's normal asset
workflow are the observable checks for this configuration change.

## Scope Boundaries

- No Rockdrake model, hitbox, scale, AI, combat, drops, or taming changes.
- No vanilla beacon fields, weights, cooldowns, or population caps change.
- No custom spawn-marker or ordinary world-spawn system is introduced.
- No per-tier tuning divergence beyond role, environment IDs, and the T1/T2
  volcanic block-set restriction.
- Multiplayer may sustain separate encounters in sufficiently distant active
  cave areas; the one-Rockdrake cap is per HyDragon beacon area, not global.
