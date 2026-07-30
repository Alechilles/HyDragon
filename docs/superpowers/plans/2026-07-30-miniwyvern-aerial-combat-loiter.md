# Miniwyvern Aerial Combat Loiter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make airborne Miniwyverns loiter slowly and irregularly 8–14 blocks horizontally around and 5–9 blocks above hostile targets, fire projectiles at deliberate 3–7 second progression-dependent intervals, and perform only occasional bite dives.

**Architecture:** Keep projectile execution and movement in Hytale/Tamework NPC assets. Slow projectile variants directly in the Miniwyvern template, and replace only the airborne `Defend` reference with a focused HyDragon instruction component because Tamework's generic `Defend` macro always chases to bite range. The new component preserves Tamework's target lifecycle but uses its existing `TameworkFlyingOrbit` `WanderTarget` motion for target-relative waypoints; the grounded branch continues referencing the generic Tamework macro unchanged.

**Tech Stack:** Hytale 0.5.7 NPC role/component JSON, Tamework 3.0.0 instruction components and talent sensor, Java 25, Gson, JUnit 5, Maven, HyDragon's Python asset validator, HytaleNpcAssetTools exact release profile.

## Global Constraints

- Work only in `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes` on `feat/dragon-horn-flight-modes`.
- Use the exact `.hytale-npc-assets.json` profile: game `0.5.7`, channel `release`, identity `0caa7f5e27a3925cca89e5f858c49e14ed54d067874c7d49fa0e227462e63e65`.
- Do not add Java world-tick movement or projectile scheduling.
- Do not modify Rockdrake, Hydra, Nordic Drake, or full-dragon flight behavior.
- Grounded Miniwyvern `Defend` behavior remains byte-for-byte equivalent in values: `CombatBehaviorDistance=8`, `CombatBackOffDistanceRange=[2.5,4]`, strafe/direct/always-moving weights `4/8/8`, and `ChaseRelativeSpeed=0.9`.
- Airborne loiter distance is `[8,14]` horizontally from the target; loiter altitude is `[5,9]` relative to the target; ordinary loiter speed is `0.28`.
- Projectile cadence bands are base/range/force/impact `[5,7]`, cadence/guidance/pattern `[4,6]`, and assault/utility/mastery/apex `[3,5]`. No minimum may be below 3 seconds.
- Preserve every existing positive and excluded `TameworkHasTalent` gate so exactly one projectile variant remains eligible.
- Use test-driven development: write each focused test, observe the expected failure, then edit production assets.
- Keep generated HytaleNpcAssetTools reports under ignored `.asset-tools/reports/`; never commit them.
- Commit after each independently green task. Do not publish. Install locally only after full verification passes.

## File and Responsibility Map

| File | Responsibility |
| --- | --- |
| `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json` | Select grounded versus airborne combat components and retain mutually exclusive projectile actions. |
| `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json` | Own airborne target lifecycle, ranged loiter motion, rare bite dives, post-bite backoff, and loss-of-target cleanup. |
| `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java` | Enforce the projectile cadence map and retain talent exclusivity. |
| `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java` | Enforce grounded behavior preservation and the airborne component's movement contract. |
| `.asset-tools/reports/miniwyvern-aerial-combat-*.json` | Ignored exact-profile inspection, candidate validation, and static verification evidence. |

---

### Task 1: Slow and randomize every projectile talent variant

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json:533-613`

**Interfaces:**
- Consumes: existing `instructionForTalent(JsonArray, String)` and positive/excluded talent-gate traversal in `MiniwyvernTalentAssetWiringTest`.
- Produces: one randomized `AttackPauseRange` per combat talent, with the exact map below and unchanged mutual-exclusion sensors.

- [ ] **Step 1: Add the exact cadence contract test**

Add this test and helper to `MiniwyvernTalentAssetWiringTest`:

```java
@Test
void projectileCadenceUsesDeliberateRandomizedProgressionBands() throws IOException {
    JsonArray instructions = load(TEMPLATE).getAsJsonArray("Instructions");
    Map<String, JsonElement> expected = Map.ofEntries(
            Map.entry("DraconicProjectile", JsonParser.parseString("[5,7]")),
            Map.entry("ProjectileRange", JsonParser.parseString("[5,7]")),
            Map.entry("ProjectileCadence", JsonParser.parseString("[4,6]")),
            Map.entry("ProjectileForce", JsonParser.parseString("[5,7]")),
            Map.entry("ProjectileGuidance", JsonParser.parseString("[4,6]")),
            Map.entry("ProjectileImpact", JsonParser.parseString("[5,7]")),
            Map.entry("ProjectilePattern", JsonParser.parseString("[4,6]")),
            Map.entry("DraconicAssault", JsonParser.parseString("[3,5]")),
            Map.entry("AssaultUtility", JsonParser.parseString("[3,5]")),
            Map.entry("AssaultMastery", JsonParser.parseString("[3,5]")),
            Map.entry("DraconicApex", JsonParser.parseString("[3,5]")));

    for (String talentId : COMBAT_TALENTS) {
        JsonObject attack = projectileAttack(instructionForTalent(instructions, talentId));
        JsonArray pause = attack.getAsJsonArray("AttackPauseRange");
        assertEquals(expected.get(talentId), pause, talentId);
        assertTrue(pause.get(0).getAsDouble() >= 3.0, talentId + " fires too quickly");
        assertTrue(pause.get(0).getAsDouble() < pause.get(1).getAsDouble(),
                talentId + " cadence must be randomized");
    }
}

private static JsonObject projectileAttack(JsonElement value) {
    if (value.isJsonObject()) {
        JsonObject object = value.getAsJsonObject();
        if ("Attack".equals(string(object, "Type")) && object.has("AttackPauseRange")) return object;
        for (JsonElement child : object.asMap().values()) {
            try {
                return projectileAttack(child);
            } catch (IllegalArgumentException ignored) {
                // Search the next child.
            }
        }
    } else if (value.isJsonArray()) {
        for (JsonElement child : value.getAsJsonArray()) {
            try {
                return projectileAttack(child);
            } catch (IllegalArgumentException ignored) {
                // Search the next child.
            }
        }
    }
    throw new IllegalArgumentException("missing projectile Attack action");
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./mvnw -Dtest=MiniwyvernTalentAssetWiringTest#projectileCadenceUsesDeliberateRandomizedProgressionBands test
```

Expected: FAIL because `DraconicProjectile` currently has `[2.5,2.5]` instead of `[5,7]`. The failure must be an assertion failure, not JSON parsing or test setup failure.

- [ ] **Step 3: Replace only the eleven projectile pause arrays**

In `Template_Wyvern_Mini_Flying_Tamed.json`, keep every sensor, `Continue`, `ActionsBlocking`, and projectile root computation unchanged. Set the selected action's `AttackPauseRange` by positive talent gate:

```text
DraconicProjectile [5,7]    ProjectileRange [5,7]
ProjectileCadence [4,6]    ProjectileForce [5,7]
ProjectileGuidance [4,6]   ProjectileImpact [5,7]
ProjectilePattern [4,6]    DraconicAssault [3,5]
AssaultUtility [3,5]       AssaultMastery [3,5]
DraconicApex [3,5]
```

- [ ] **Step 4: Run focused cadence and exclusivity tests**

Run:

```bash
./mvnw -Dtest=MiniwyvernTalentAssetWiringTest test
```

Expected: PASS, including `templateContainsTalentGatedExecutableVariants`, which proves the cadence edit did not make talent variants stack.

- [ ] **Step 5: Commit the cadence change**

```bash
git add Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java
git commit -m "Fix: slow Miniwyvern projectile cadence"
```

---

### Task 2: Add a dedicated airborne loiter-and-dive component

**Files:**
- Create: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json:415-503`
- Modify: `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java:120-134,591-617`

**Interfaces:**
- Consumes: outer `Defend` state, `MasterTarget`, `LockedTarget`, existing owner/friendly rejection, `Component_Tamework_Sensor_Defend_Attacked_MasterTarget`, `Component_Tamework_Sensor_Defend_Hostile_To_MasterTarget`, `Component_Tamework_Instruction_Follow_Flying`, and the existing bite root interaction.
- Produces: `Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend`, an instruction component used only by the `AirborneMode=true` plus `MotionController=Fly` Miniwyvern branch.

- [ ] **Step 1: Change the locomotion contract to distinguish grounded and airborne combat**

In `miniwyvernSelectsLocomotionInsideEachCommandWithoutMutatingCommandStateOrTargets`, change the airborne reference assertion to:

```java
assertModeBranch(defend, true, "Fly", null,
        "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend");
assertGroundedDefendTuning(defend);
assertAerialDefendTuning(defend);
```

Keep the existing `assertDefendFollowMacro(defend, false, ...)` call for the grounded branch. Remove the airborne `assertDefendFollowMacro(defend, true, ...)` call because that helper deliberately searches for `Component_Tamework_Instruction_Defend`; the new `assertAerialDefendTuning` helper below takes over the airborne follow-component assertion through `DefendFollowMacroElement`.

Rename the existing `assertDefendTuning` to `assertGroundedDefendTuning`, make it select only the `AirborneMode=false`/`Walk` branch, and retain all its current exact assertions. Do not weaken the grounded macro or value checks merely to accommodate the new airborne component.

Add `assertAerialDefendTuning` with these assertions:

```java
private static void assertAerialDefendTuning(JsonObject defend) throws IOException {
    JsonObject branch = directModeBranches(defend).stream()
            .filter(candidate -> isModeControllerPair(candidate, true, "Fly"))
            .findFirst().orElseThrow();
    JsonObject reference = objects(branch, object ->
            "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend"
                    .equals(string(object, "Reference"))).stream().findFirst().orElseThrow();
    JsonObject modify = reference.getAsJsonObject("Modify");
    assertEquals(JsonParser.parseString("[8,14]"), modify.get("LoiterDistanceRange"));
    assertEquals(JsonParser.parseString("[5,9]"), modify.get("LoiterAltitudeRange"));
    assertEquals(JsonParser.parseString("0.28"), modify.get("LoiterRelativeSpeed"));
    assertEquals(JsonParser.parseString("[3,6]"), modify.get("LoiterRetargetTimeRange"));
    assertEquals(JsonParser.parseString("2.5"), modify.get("LoiterStopDistance"));
    assertEquals(JsonParser.parseString("9"), modify.get("LoiterWeight"));
    assertEquals(JsonParser.parseString("1"), modify.get("DiveWeight"));
    assertEquals(JsonParser.parseString("0.55"), modify.get("DiveRelativeSpeed"));
    assertEquals(JsonParser.parseString("[8,14]"), modify.get("CombatBackOffDistanceRange"));
    assertEquals(JsonParser.parseString("[2,4]"), modify.get("CombatBackOffDurationRange"));
    assertEquals(JsonParser.parseString("[8,12]"), modify.get("BitePauseRange"));
    assertEquals("Component_Tamework_Instruction_Follow_Flying",
            string(modify, "DefendFollowMacroElement"));

    JsonObject component = readJson("Server/NPC/Roles/Creature/HyDragon/Components/"
            + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json");
    assertEquals("Component", string(component, "Type"));
    assertEquals("Instruction", string(component, "Class"));
    assertTrue(anyObject(component, object -> "TameworkFlyingOrbit".equals(string(object, "Type"))
            && "WanderTarget".equals(string(object, "Mode"))
            && JsonParser.parseString("{\"Compute\":\"LoiterDistanceRange\"}")
                    .equals(object.get("WanderRadiusRange"))
            && JsonParser.parseString("{\"Compute\":\"LoiterAltitudeRange\"}")
                    .equals(object.get("DesiredAltitudeRange"))
            && JsonParser.parseString("{\"Compute\":\"LoiterRetargetTimeRange\"}")
                    .equals(object.get("WanderRetargetTimeRange"))
            && JsonParser.parseString("{\"Compute\":\"LoiterStopDistance\"}")
                    .equals(object.get("WanderStopDistance"))
            && JsonParser.parseString("{\"Compute\":\"LoiterRelativeSpeed\"}")
                    .equals(object.get("RelativeSpeed"))
            && JsonParser.parseString("0.45").equals(object.get("ClimbRelativeSpeed"))
            && JsonParser.parseString("0.35").equals(object.get("SinkRelativeSpeed"))));
    assertTrue(anyObject(component, object -> "Random".equals(string(object, "Type"))
            && JsonParser.parseString("[3,7]").equals(object.get("ExecuteFor"))));
    assertTrue(anyObject(component, object -> "Seek".equals(string(object, "Type"))
            && JsonParser.parseString("{\"Compute\":\"DiveRelativeSpeed\"}")
                    .equals(object.get("RelativeSpeed"))));
    assertTrue(anyObject(component, object -> "Attack".equals(string(object, "Type"))
            && JsonParser.parseString("{\"Compute\":\"BitePauseRange\"}")
                    .equals(object.get("AttackPauseRange"))));
}
```

- [ ] **Step 2: Run the focused locomotion test and verify RED**

Run:

```bash
./mvnw -Dtest=DragonHornLocomotionAssetContractTest#miniwyvernSelectsLocomotionInsideEachCommandWithoutMutatingCommandStateOrTargets test
```

Expected: FAIL because the airborne branch still references `Component_Tamework_Instruction_Defend` and the HyDragon aerial component does not exist.

- [ ] **Step 3: Author the aerial component from supported 0.5.7 primitives**

Create `Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json` as `Type=Component`, `Class=Instruction`, `DefaultState=.Default`. Define these exact parameters and defaults:

```json
{
  "HardLeashDistance": { "Value": 30 },
  "AlertedRange": { "Value": 28 },
  "ViewSector": { "Value": 180 },
  "HearingRange": { "Value": 18 },
  "AbsoluteDetectionRange": { "Value": 4 },
  "ViewRange": { "Value": 28 },
  "Attack": { "Value": "Root_NPC_Wyvern_Mini_Bite" },
  "AttackDistance": { "Value": 2.75 },
  "BitePauseRange": { "Value": [8, 12] },
  "LoiterDistanceRange": { "Value": [8, 14] },
  "LoiterAltitudeRange": { "Value": [5, 9] },
  "LoiterRelativeSpeed": { "Value": 0.28 },
  "LoiterRetargetTimeRange": { "Value": [3, 6] },
  "LoiterStopDistance": { "Value": 2.5 },
  "LoiterWeight": { "Value": 9 },
  "DiveWeight": { "Value": 1 },
  "DiveRelativeSpeed": { "Value": 0.55 },
  "CombatBackOffDistanceRange": { "Value": [8, 14] },
  "CombatBackOffDurationRange": { "Value": [2, 4] },
  "DefendFollowMacroElement": { "Value": "Component_Tamework_Instruction_Follow_Flying" }
}
```

The component content must implement these exact phases:

1. Preserve Tamework's hard-leash release and `.Default` transition.
2. Preserve Tamework's explicit owner-target rejection before any combat transition: if `LockedTarget` passes `TameworkIsOwner`, release it and return to `.Default`. Do not acquire or attack `MasterTarget`.
3. In `.Default`, reuse `Component_Tamework_Sensor_Defend_Attacked_MasterTarget` and `Component_Tamework_Sensor_Defend_Hostile_To_MasterTarget` with their existing owner/friendly exclusions to fill `LockedTarget`; enter `.Combat` when a valid locked target is present.
4. In `.Default` without a target, run the computed `DefendFollowMacroElement` through interfaces `Hytale.Instruction.Null` and `Tamework.Instruction.Follow`.
5. In `.Combat`, always watch `LockedTarget` and run a `Type=Random` instruction with `ExecuteFor=[3,7]`.
6. The loiter choice has `Weight={Compute:"LoiterWeight"}`, a `LockedTarget` sensor, and `BodyMotion.Type=TameworkFlyingOrbit`. Set `Mode="WanderTarget"`; bind `WanderRadiusRange`, `WanderRetargetTimeRange`, `WanderStopDistance`, `RelativeSpeed`, and `DesiredAltitudeRange` to the corresponding computed loiter parameters; set `ClimbRelativeSpeed=0.45` and `SinkRelativeSpeed=0.35`. These are target-relative waypoints, not flight-controller ground-clearance limits.
7. The dive choice has `Weight={Compute:"DiveWeight"}`, a `LockedTarget` sensor, and a nested `Instructions` array. Its continuing movement child uses `BodyMotion.Type=Seek`, `RelativeSpeed={Compute:"DiveRelativeSpeed"}`, `SlowDownDistance=4`, and `StopDistance=2.2` while not backing away.
8. Inside that same selected dive choice only, add a second child whose `LockedTarget` range/line-of-sight sensor at `AttackDistance` executes the computed bite with `BitePauseRange`, `TimerStart`, and `TimerRestart` for `Miniwyvern_Combat_Back_Off`. Add `Component_Instruction_Combat_Back_Off` as a third nested child with `TimerName="Miniwyvern_Combat_Back_Off"`, `CombatBackOffAfterAttack=true`, the computed `[8,14]` distance and `[2,4]` duration, `BlockAbility=""`, `BlockProbability=0`, `CombatStrafingDurationRange=[1.5,4]`, `CombatStrafingFrequencyRange=[1.25,3.5]`, `CombatBehaviorDistance=14`, and `CombatMovingRelativeSpeed={Compute:"LoiterRelativeSpeed"}`.
9. Reuse Tamework's lost-target detector to release `LockedTarget` and return to `.Default`.

Do not place the bite action outside the dive choice. That would make every loiter pass attempt a bite.

- [ ] **Step 4: Wire only the airborne branch**

In `Template_Wyvern_Mini_Flying_Tamed.json`:

- Leave the grounded branch's `Component_Tamework_Instruction_Defend` reference and values unchanged.
- Replace the flying branch's reference with `Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend`.
- Pass through the existing computed leash, detection, target, attack, and bite-distance values.
- Set the exact target-relative loiter, dive, and backoff values asserted in Step 1.
- Leave the `Fly` and `Walk` controller definitions unchanged; `[5,9]` belongs to `TameworkFlyingOrbit.DesiredAltitudeRange`, not `MinHeightOverGround`/`MaxHeightOverGround`.
- Keep `DefendFollowMacroElement="Component_Tamework_Instruction_Follow_Flying"`.

- [ ] **Step 5: Run focused movement and projectile contracts**

Run:

```bash
./mvnw -Dtest=DragonHornLocomotionAssetContractTest,MiniwyvernTalentAssetWiringTest test
```

Expected: PASS. The grounded exact-value assertions must pass without being weakened or removed.

- [ ] **Step 6: Run the direct asset validator**

```bash
python scripts/validate_assets.py
```

Expected: exit 0 with all JSON and locale checks passing.

- [ ] **Step 7: Commit the aerial movement component**

```bash
git add Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json \
  Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json \
  src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java
git commit -m "Fix: add Miniwyvern aerial combat loiter"
```

---

### Task 3: Validate the changed asset graph and install the verified jar

**Files:**
- Inspect: `.hytale-npc-assets.json`
- Generate ignored evidence: `.asset-tools/reports/miniwyvern-aerial-combat-*.json`
- Build: `target/HyDragon v1.0.0.jar`
- Install: `C:/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar`
- Install: `C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods/HyDragon v1.0.0.jar`

**Interfaces:**
- Consumes: committed cadence and aerial component assets from Tasks 1–2 plus installed Tamework 3.0.0.
- Produces: exact-profile static validation evidence and identical source/deployed HyDragon jar hashes.

- [ ] **Step 1: Recheck the exact project profile**

From `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HytaleNpcAssetTools`, run:

```bash
python -m hytale_npc_assets.cli profile check \
  --project-profile "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.hytale-npc-assets.json" \
  --json
```

Expected: `status=ready`, game `0.5.7`, channel `release`, and identity `0caa7f5e27a3925cca89e5f858c49e14ed54d067874c7d49fa0e227462e63e65`.

- [ ] **Step 2: Reopen the graph after source edits and inspect both changed assets**

From `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HytaleNpcAssetTools`, run:

```bash
python -m hytale_npc_assets.cli author inspect \
  --project-profile "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.hytale-npc-assets.json" \
  --asset Template_Wyvern_Mini_Flying_Tamed \
  --source-path Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json \
  --view both --provenance compact --references both \
  --include-advisories actionable --format json \
  --out "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.asset-tools/reports/miniwyvern-aerial-combat-template-inspect.json"

python -m hytale_npc_assets.cli author inspect \
  --project-profile "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.hytale-npc-assets.json" \
  --asset Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend \
  --source-path Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json \
  --view both --provenance compact --references both \
  --include-advisories actionable --format json \
  --out "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.asset-tools/reports/miniwyvern-aerial-combat-component-inspect.json"
```

Expected: fresh snapshots, no blocker diagnostics, the template resolves the new component, and the component's Tamework references resolve.

- [ ] **Step 3: Produce affected-scope candidate validation**

Create the ignored file `.asset-tools/reports/miniwyvern-aerial-combat-noop.patch.json` with `apply_patch`; its complete contents are:

```json
[]
```

Then validate the already-edited template and its affected reference graph without materializing another change:

```bash
python -m hytale_npc_assets.cli author validate \
  --project-profile "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.hytale-npc-assets.json" \
  --asset Template_Wyvern_Mini_Flying_Tamed \
  --source-path Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json \
  --patch "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.asset-tools/reports/miniwyvern-aerial-combat-noop.patch.json" \
  --scope affected --simulate --fail-on blocker --format json \
  --out "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.asset-tools/reports/miniwyvern-aerial-combat-validation.json"
```

Expected: candidate validation succeeds with no blocker and includes the changed template's affected graph. If this tool release rejects an empty RFC 6902 patch, generate the equivalent no-op `replace` operation for `/Type` from `"Template"` to `"Template"`; do not invent or apply a semantic asset change merely to produce a candidate report.

- [ ] **Step 4: Generate and run the static verification plan**

Use the candidate report produced in Step 3 as `--candidate`:

```bash
python -m hytale_npc_assets.cli author verify generate \
  --project-profile "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.hytale-npc-assets.json" \
  --candidate "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.asset-tools/reports/miniwyvern-aerial-combat-validation.json" \
  --behavior-goal "Airborne Miniwyvern loiters at range, rarely dives, and fires periodically" \
  --out "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.asset-tools/reports/miniwyvern-aerial-combat-verification-plan.json"

python -m hytale_npc_assets.cli author verify run \
  --project-profile "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.hytale-npc-assets.json" \
  --verification "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.asset-tools/reports/miniwyvern-aerial-combat-verification-plan.json" \
  --mode static \
  --out "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/dragon-horn-flight-modes/.asset-tools/reports/miniwyvern-aerial-combat-verification-result.json"
```

Expected: static checks pass. Record any live-runtime checks as unavailable rather than passing them; this profile has no live harness capability.

- [ ] **Step 5: Run complete HyDragon verification**

From the HyDragon worktree, run:

```bash
./mvnw clean verify
git diff --check
git status --short
```

Expected: asset validator, unit tests, integration tests, packaging checks, and `git diff --check` pass. `git status --short` must show no uncommitted source files; ignored `.asset-tools` reports are allowed.

- [ ] **Step 6: Install through the Maven profile**

Confirm no Hytale server process is running, then run:

```bash
./mvnw package -DskipTests -Pinstall-plugin
```

Expected: `BUILD SUCCESS` and the jar is copied to both release mod directories.

- [ ] **Step 7: Prove deployed artifacts are identical**

```bash
sha256sum \
  "target/HyDragon v1.0.0.jar" \
  "/c/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar" \
  "/c/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods/HyDragon v1.0.0.jar"
```

Expected: all three SHA-256 values are identical.

- [ ] **Step 8: In-game smoke test after restart**

With one projectile-talented Miniwyvern in airborne `Defend`:

1. Attack a stationary ground target for at least 60 seconds.
2. Confirm ordinary movement remains about 8–14 blocks horizontally away and 5–9 blocks above the target.
3. Confirm it changes direction and timing instead of drawing a continuous perfect circle.
4. Count projectile gaps: base 5–7 seconds, cadence branch 4–6 seconds, assault/apex branch 3–5 seconds.
5. Confirm close bite dives are occasional, not the default movement, and are followed by a retreat.
6. Switch to grounded mode and confirm the original close bite routine still works.
7. Issue Follow, Hold, Defend, Attack Target, and Toggle Airborne commands and confirm command state and target selection remain correct.

If movement geometry differs materially from the asset contract, capture the latest server log and one 60-second observation before changing any values; do not stack speculative tuning edits.
