# Rockdrake Dedicated Beacons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to execute this plan.

**Goal:** Give every Rock Drake tier a rare, solitary HyDragon-owned volcanic-cave beacon without changing vanilla beacon configuration or population behavior.

**Architecture:** Replace the nine Patchwork insertions into vanilla cave beacons with three standalone `BeaconNPCSpawn` assets, one for each Rock Drake tier and its zone's three volcanic-cave environment tiers. Point each species record at its new beacon, extend the existing production asset validator to discover local beacon routes, and retain Patchwork only for the Nordic Drake's additive world-spawn entry.

**Tech Stack:** Hytale `BeaconNPCSpawn` JSON assets, HyDragon `DragonSpecies` JSON, Python asset validation, Gradle 8/Java 25 build, Patchwork/Tamework integration documentation.

## Global Constraints

- Use Git Bash for every command.
- Do not modify any vanilla Hytale spawn asset.
- Do not change Rock Drake models, roles, AI, combat, drops, capture, or mounting.
- Keep the three beacons identical except for asset ID, environment IDs, NPC role, and the intentional absence of `SpawnBlockSet` on T3.
- Keep the Nordic Drake Patchwork patch and its verified base target unchanged.
- Do not add tests that assert shipped JSON text, exact tuning, file presence, asset counts, or deleted patch names. The existing production JSON validator, complete build, asset-load diagnostics, and live spawn behavior are the appropriate checks.
- Commit implementation and documentation separately so each change remains reviewable.

---

### Task 1: Replace Rock Drake Patchwork routes with dedicated beacon assets

**Files:**

- Create: `src/main/resources/Server/NPC/Spawn/Beacons/HyDragon/HyDragon_RockDrakeT1_Volcanic_Caves.json`
- Create: `src/main/resources/Server/NPC/Spawn/Beacons/HyDragon/HyDragon_RockDrakeT2_Volcanic_Caves.json`
- Create: `src/main/resources/Server/NPC/Spawn/Beacons/HyDragon/HyDragon_RockDrakeT3_Volcanic_Caves.json`
- Modify: `src/main/resources/Server/HyDragon/DragonSpecies/RockDrakeT1.json:14-20`
- Modify: `src/main/resources/Server/HyDragon/DragonSpecies/RockDrakeT2.json:14-20`
- Modify: `src/main/resources/Server/HyDragon/DragonSpecies/RockDrakeT3.json:14-20`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT1_Zone1_Cave_Volcanic_T1_Aggro.json`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT1_Zone1_Cave_Volcanic_T2_Aggro.json`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT1_Zone1_Cave_Volcanic_T3_Aggro.json`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT2_Zone2_Cave_Volcanic_T1_Goblin.json`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT2_Zone2_Cave_Volcanic_T2_Goblin.json`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT2_Zone2_Cave_Volcanic_T3_Goblin.json`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT3_Zone3_Cave_Volcanic_T1_Aggro.json`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT3_Zone3_Cave_Volcanic_T2_Aggro.json`
- Delete: `src/main/resources/Server/Patchwork/Patches/HyDragon/RockDrakeT3_Zone3_Cave_Volcanic_T3_Aggro.json`

- [ ] **Step 1: Create the T1 beacon**

Create `HyDragon_RockDrakeT1_Volcanic_Caves.json` with this complete asset:

```json
{
  "Environments": [
    "Env_Zone1_Caves_Volcanic_T1",
    "Env_Zone1_Caves_Volcanic_T2",
    "Env_Zone1_Caves_Volcanic_T3"
  ],
  "MinDistanceFromPlayer": 15,
  "MaxSpawnedNPCs": 1,
  "ConcurrentSpawnsRange": [ 1, 1 ],
  "SpawnAfterGameTimeRange": [ "PT45M", "PT90M" ],
  "InitialSpawnDelayRange": [ "PT10M", "PT30M" ],
  "NPCIdleDespawnTime": 60,
  "BeaconVacantDespawnGameTime": "PT15M",
  "BeaconRadius": 70,
  "SpawnRadius": 50,
  "TargetDistanceFromPlayer": 25,
  "YRange": [ -24, 24 ],
  "NPCs": [
    { "Weight": 100, "SpawnBlockSet": "Volcanic", "Id": "RockDrakeT1" }
  ],
  "LightRanges": {
    "Light": [ 0, 7 ]
  }
}
```

- [ ] **Step 2: Create the T2 beacon**

Create `HyDragon_RockDrakeT2_Volcanic_Caves.json` with the same pacing, radii, cap, distance, light, and Y-range fields. Use these environment and NPC values:

```json
"Environments": [
  "Env_Zone2_Caves_Volcanic_T1",
  "Env_Zone2_Caves_Volcanic_T2",
  "Env_Zone2_Caves_Volcanic_T3"
],
"NPCs": [
  { "Weight": 100, "SpawnBlockSet": "Volcanic", "Id": "RockDrakeT2" }
]
```

- [ ] **Step 3: Create the T3 beacon**

Create `HyDragon_RockDrakeT3_Volcanic_Caves.json` with the same pacing, radii, cap, distance, light, and Y-range fields. Use these environment and NPC values, deliberately omitting `SpawnBlockSet`:

```json
"Environments": [
  "Env_Zone3_Caves_Volcanic_T1",
  "Env_Zone3_Caves_Volcanic_T2",
  "Env_Zone3_Caves_Volcanic_T3"
],
"NPCs": [
  { "Weight": 100, "Id": "RockDrakeT3" }
]
```

- [ ] **Step 4: Rewire each species to its single owned beacon**

Replace each three-entry `OrdinarySpawnAssetIds` array with the corresponding single route:

```json
"OrdinarySpawnAssetIds": [ "HyDragon_RockDrakeT1_Volcanic_Caves" ]
```

```json
"OrdinarySpawnAssetIds": [ "HyDragon_RockDrakeT2_Volcanic_Caves" ]
```

```json
"OrdinarySpawnAssetIds": [ "HyDragon_RockDrakeT3_Volcanic_Caves" ]
```

- [ ] **Step 5: Remove all nine obsolete Rock Drake patch files**

Delete only the nine files listed in this task. Confirm the Nordic Drake patch remains:

Run: `find src/main/resources/Server/Patchwork/Patches/HyDragon -maxdepth 1 -type f -name '*.json' -printf '%f\n' | sort`

Expected: no `RockDrake*.json` files, and `Spawns_Zone3_Forests_Predator.json` is still present.

- [ ] **Step 6: Demonstrate the validator does not yet understand the new production route**

Run: `python scripts/validate_assets.py`

Expected: failure for unresolved `HyDragon_RockDrakeT*_Volcanic_Caves` ordinary spawn routes. This is the production validation path that Task 2 will update; do not add a source-shape test for it.

---

### Task 2: Teach production validation about HyDragon-owned beacon routes

**Files:**

- Modify: `scripts/validate_assets.py:83-108`
- Modify: `scripts/validate_assets.py:807-846`
- Modify: `scripts/validate_assets.py:925-1023`
- Modify: `scripts/validate_assets.py:1727-1728`

- [ ] **Step 1: Remove obsolete Rock Drake targets from the Workshop manifest**

Reduce `WORKSHOP_057_PATCH_TARGETS` to the still-shipped Nordic Drake target:

```python
WORKSHOP_057_PATCH_TARGETS = {
    "Server/NPC/Spawn/World/Zone3/Spawns_Zone3_Forests_Predator.json": (
        "Env_Zone3_Forests", {"DayTimeRange"}),
}
```

Keep `HYDRA_INDEPENDENT_WORLD_SPAWN_IDS` unchanged because that allowlist applies only to standalone world-spawn assets.

- [ ] **Step 2: Remove the patch-only species identity validator**

Delete `validate_spawn_patch_role_identity`. Its assumption that every ordinary route must resolve to a world-spawn asset or Patchwork insertion is no longer valid. Do not replace it with filename, inventory, or exact-configuration assertions.

Remove its call from `main`; `validate_static_spawn_contracts` remains responsible for resolving species routes and validating the production spawn objects and their NPC role references.

- [ ] **Step 3: Validate local beacon assets through the existing spawn parser**

In `validate_static_spawn_contracts`, keep the world-spawn loop and Hydra allowlist, then add the dedicated beacon tree to the same route set:

```python
local_spawn_ids: set[str] = set()
for path in sorted(world_root.rglob("*.json")):
    local_spawn_ids.add(path.stem)
    validate_spawn_shape(
        parsed.get(path), "WorldNPCSpawn", path.relative_to(ROOT).as_posix(), known_assets, errors
    )
    if path.stem not in HYDRA_INDEPENDENT_WORLD_SPAWN_IDS:
        fail(errors, f"{path.relative_to(ROOT)} is not an approved independent Hydra spawn asset")

beacon_root = RESOURCE_ROOT / "Server/NPC/Spawn/Beacons/HyDragon"
for path in sorted(beacon_root.rglob("*.json")):
    local_spawn_ids.add(path.stem)
    validate_spawn_shape(
        parsed.get(path), "BeaconNPCSpawn", path.relative_to(ROOT).as_posix(), known_assets, errors
    )
```

Leave `available_routes = local_spawn_ids | target_stems | patch_ids` in place so the new beacon filenames resolve naturally while the Nordic target and patch identifiers continue to resolve.

- [ ] **Step 4: Run production asset validation**

Run: `python scripts/validate_assets.py`

Expected: exit code `0` and the validator's success summary. The run must validate the three beacon objects as `BeaconNPCSpawn`, resolve all environment and NPC role IDs, and resolve all three species routes.

- [ ] **Step 5: Run the focused Gradle verification path**

Run: `./gradlew.bat validateHyDragonAssets test`

Expected: `BUILD SUCCESSFUL` with the asset validator and ordinary unit tests passing.

- [ ] **Step 6: Review and commit the implementation**

Run:

```bash
git diff --check
git diff -- src/main/resources/Server/NPC/Spawn src/main/resources/Server/HyDragon/DragonSpecies src/main/resources/Server/Patchwork/Patches/HyDragon scripts/validate_assets.py
git status --short
```

Confirm the diff contains exactly three new beacon assets, three species route changes, nine patch deletions, and the validator generalization. Then commit:

```bash
git add scripts/validate_assets.py src/main/resources/Server/NPC/Spawn/Beacons/HyDragon src/main/resources/Server/HyDragon/DragonSpecies/RockDrakeT1.json src/main/resources/Server/HyDragon/DragonSpecies/RockDrakeT2.json src/main/resources/Server/HyDragon/DragonSpecies/RockDrakeT3.json src/main/resources/Server/Patchwork/Patches/HyDragon
git commit -m "Feat: add dedicated Rock Drake cave beacons"
```

---

### Task 3: Align durable spawn contracts and player-facing notes

**Files:**

- Modify: `CHANGELOG.md:28-35`
- Modify: `docs/integration/patchwork-spawn-patches-contract.md`
- Modify: `docs/integration/patchwork-spawn-patches-compatibility-matrix.md`
- Modify: `docs/specs/dragon-content-encounters.md:8-21,41-46,180-193,205-223,225-248`

- [ ] **Step 1: Document the player-facing behavior change**

Under `CHANGELOG.md`'s `Changed` section, add:

```markdown
- Moved Rock Drakes from vanilla cave spawn pools to rare, solitary HyDragon beacons so their encounters no longer consume or alter vanilla beacon population slots.
```

- [ ] **Step 2: Narrow the Patchwork integration contract to its surviving purpose**

Update `patchwork-spawn-patches-contract.md` so it states:

- Patchwork appends only the Nordic Drake to the base Zone 3 forest predator pool.
- Hydra owns two standalone `WorldNPCSpawn` assets.
- Rock Drakes own three standalone `BeaconNPCSpawn` assets under `Server/NPC/Spawn/Beacons/HyDragon`.
- None of those five standalone assets mutates a base-game spawn asset.
- Removing the HyDragon JAR removes both its additive Nordic entry and standalone spawn assets without modifying vanilla files in place.

Retain the existing version/load contract and guarded `Insert` rules for the Nordic patch.

- [ ] **Step 3: Update the compatibility matrix**

Keep the Hytale/Patchwork compatibility row, but describe the integration as a required additive Nordic Drake spawn patch. Rewrite the explanatory paragraph to distinguish:

- the guarded Nordic Drake append;
- Hydra's two standalone world-spawn assets; and
- Rock Drakes' three standalone cave beacons that coexist with, but do not alter, vanilla beacons.

- [ ] **Step 4: Supersede the obsolete content-spec requirements**

Update `dragon-content-encounters.md` consistently:

- State that the Nordic Drake uses Patchwork, Hydra uses dedicated world spawns, and Rock Drakes use dedicated beacons.
- Rewrite **HYD-CONT-012** to require additive Patchwork only when HyDragon modifies an existing spawn pool; require uniquely named HyDragon assets for independent spawn conditions, and prohibit replacing or mutating vanilla assets.
- Change the ordinary spawn table to Zone 1/2/3 volcanic caves with dedicated T1/T2/T3 `BeaconNPCSpawn` assets.
- State that all three Rock Drake beacons share a one-NPC cap, rare cooldowns, light range, and `YRange: [-24, 24]`, while Patchwork operations apply only to the Nordic Drake.
- Update acceptance criteria, implemented sequence item 5, and dependency-map D4 so none claims Rock Drake spawning is Patchwork-owned.

- [ ] **Step 5: Check for stale claims and validate the edited documentation**

Run:

```bash
rg -n "Nordic and Rock Drake|guarded Nordic and Rock Drake|every non-Hydra|Rock Drake spawn data is represented by additive Patchwork|Preserve current patch" CHANGELOG.md docs
git diff --check
```

Expected: no obsolete Patchwork ownership claim remains and no whitespace errors are reported. Mentions in the committed historical design/plan documents are allowed when they describe the removed implementation or migration itself.

- [ ] **Step 6: Commit the documentation**

Run:

```bash
git add CHANGELOG.md docs/integration/patchwork-spawn-patches-contract.md docs/integration/patchwork-spawn-patches-compatibility-matrix.md docs/specs/dragon-content-encounters.md
git commit -m "Docs: document dedicated Rock Drake beacons"
```

---

### Task 4: Complete automated and live-ready verification

**Files:**

- Verify only; no new test or source file is expected.

- [ ] **Step 1: Run the complete clean build**

Run: `./gradlew.bat clean build packagingTest`

Expected: `BUILD SUCCESSFUL`. This executes production asset validation through `check`, Java tests, packaging, and packaged integration tests.

- [ ] **Step 2: Inspect the packaged asset surface**

Run:

```bash
for archive in build/libs/HyDragon-*.jar; do
  jar tf "$archive"
done | rg "Server/NPC/Spawn/(Beacons/HyDragon|World)|Server/Patchwork/Patches/HyDragon"
```

Expected: the package contains all three `HyDragon_RockDrakeT*_Volcanic_Caves.json` assets, both Hydra world-spawn assets, and the Nordic patch; it contains no `Server/Patchwork/Patches/HyDragon/RockDrake*.json` entries.

- [ ] **Step 3: Perform final repository review**

Run:

```bash
git status --short
git log -3 --oneline
git diff --check HEAD~2..HEAD
```

Expected: the worktree is clean, the implementation and documentation commits are present, and the committed diff has no whitespace errors.

- [ ] **Step 4: Hand off the live-server acceptance checklist**

On a test server with the built HyDragon JAR and required Tamework/Patchwork versions:

1. Confirm all three new beacon assets load without NPC/spawn-configuration errors.
2. Run Patchwork status/self-test diagnostics and confirm generated vanilla cave targets have no Rock Drake insertions.
3. Enter a Zone 1, Zone 2, and Zone 3 volcanic cave and manually add or trigger the corresponding HyDragon beacon at cave-floor height.
4. Confirm T1, T2, and T3 each spawn their intended Rock Drake when a valid location exists.
5. Re-trigger each active beacon and confirm it never tracks more than one Rock Drake.
6. Confirm vanilla beacon populations continue spawning independently in the same cave area.
7. Exercise one cave while flying more than five blocks above the floor and confirm the widened `YRange` still finds valid terrain.

Record any Hytale position-selection warning or asset-load error verbatim before changing tuning; those diagnostics distinguish missing terrain/model fit from beacon registration.
