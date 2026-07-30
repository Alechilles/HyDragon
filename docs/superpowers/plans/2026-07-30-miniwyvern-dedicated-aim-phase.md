# MiniWyvern Dedicated Projectile Aim Phase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the tamed MiniWyvern a real airborne Hold hover and a timer-driven projectile phase that stops, faces the locked target, fires, and resumes its existing aerial combat movement.

**Architecture:** Keep the native Fly controller, but allow it to decelerate to zero. Add a shared flag plus aim/cooldown timers ahead of the ordinary state-motion selector so Hytale's first-match-wins resolver grants the line-up phase temporary body/head ownership; retain the 11 talent branches as action dispatchers after the state selector.

**Tech Stack:** Hytale 0.5.7 NPC JSON, Tamework NPC builders, Gson/JUnit 5 asset contracts, HytaleNpcAssetTools exact release profile, Maven Wrapper, Git worktrees.

## Global Constraints

- Apply only to `Template_Wyvern_Mini_Flying_Tamed` and its seven inheriting tamed MiniWyvern variants.
- Preserve all 11 projectile interaction selections and cooldown ranges: `[5,7]`, `[4,6]`, or `[3,5]` according to the mapping below.
- Preserve the visible 0.4–0.7-second line-up, zero diagnostic spread, target deflection, orbit tuning, rare dive/bite routine, command names, and talent gates.
- Do not change wild MiniWyverns, Nordic Drakes, Rockdrakes, Hydras, or any other species.
- Keep `MinAirSpeed: 0`, `Deceleration: 12`, `BodyMotion: MatchLook`, and `HeadMotion: Aim` with `Spread: 0`, `HitProbability: 1`, and `Deflection: true` exact.
- Preserve user changes and unrelated dirty files; stage only files named by each task.
- Use Git Bash and leave no Hytale server, Maven, asset-tool, or temporary-worktree process running.

---

### Task 1: Encode the dedicated phase as a failing asset contract

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java`
- Test: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java`

**Interfaces:**
- Consumes: the root `Instructions` array and `MotionControllerList` in `Template_Wyvern_Mini_Flying_Tamed.json`.
- Produces: regression contracts for controller braking, first-match ordering, scheduler/recovery actions, priority aim motion, and per-talent cooldown dispatch.

- [ ] **Step 1: Replace the obsolete per-variant motion assertion with controller and priority-phase contracts**

Add constants:

```java
private static final String AIM_FLAG = "Miniwyvern_Projectile_Aiming";
private static final String AIM_TIMER = "Miniwyvern_Projectile_Aim";
private static final String COOLDOWN_TIMER = "Miniwyvern_Projectile_Cooldown";
```

Replace `everyProjectileVariantPausesMovementWhileAimingAtItsTarget` with these contracts:

```java
@Test
void nativeFlightCanBrakeToAStationaryHover() throws IOException {
    JsonObject template = load(TEMPLATE);
    JsonObject fly = template.getAsJsonArray("MotionControllerList").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .filter(controller -> "Fly".equals(string(controller, "Type")))
            .findFirst().orElseThrow();

    assertEquals(0.0, fly.get("MinAirSpeed").getAsDouble());
    assertEquals(12.0, fly.get("Deceleration").getAsDouble());
}

@Test
void dedicatedAimPhaseOwnsMotionBeforeOrdinaryDefendMovement() throws IOException {
    JsonArray instructions = load(TEMPLATE).getAsJsonArray("Instructions");
    JsonObject aim = instructionUsingFlagAndMotion(instructions, AIM_FLAG, "MatchLook");
    JsonObject defendDispatch = defendDispatch(instructions);

    assertTrue(topLevelIndex(instructions, aim) < topLevelIndex(instructions, defendDispatch));
    assertEquals(JsonParser.parseString("{\"Type\":\"MatchLook\"}"), aim.get("BodyMotion"));
    assertEquals(JsonParser.parseString(
            "{\"Type\":\"Aim\",\"Spread\":0,\"HitProbability\":1,\"Deflection\":true}"),
            aim.get("HeadMotion"));
}
```

Implement the named helpers with recursive traversal of JSON objects/arrays, using the existing `string`, `hasTalentGate`, and `containsDefendStateInstruction` patterns:

```java
private static JsonObject defendDispatch(JsonArray instructions) {
    return instructions.asList().stream()
            .filter(JsonElement::isJsonObject)
            .map(JsonElement::getAsJsonObject)
            .filter(instruction -> instruction.has("Instructions")
                    && containsDefendStateInstruction(instruction.get("Instructions")))
            .findFirst().orElseThrow(() -> new AssertionError("missing Defend dispatch"));
}

private static int topLevelIndex(JsonArray instructions, JsonObject expected) {
    for (int index = 0; index < instructions.size(); index++) {
        if (instructions.get(index) == expected) return index;
    }
    throw new AssertionError("instruction is not top-level");
}
```

`instructionUsingFlagAndMotion` must return the top-level instruction containing positive `Flag` sensor `AIM_FLAG` and exact body-motion type `MatchLook`; it must not accept a nested later match.

- [ ] **Step 2: Add scheduler and cancellation contracts**

Add a test that locates top-level instructions by their actions and asserts exact semantics:

```java
@Test
void projectileAimSchedulerAndRecoveryAreExplicit() throws IOException {
    JsonArray instructions = load(TEMPLATE).getAsJsonArray("Instructions");
    JsonObject scheduler = instructionWithAction(instructions, "TimerStart", AIM_TIMER);
    JsonObject recovery = instructionWithAction(instructions, "TimerStop", AIM_TIMER);
    JsonObject defendDispatch = defendDispatch(instructions);

    assertTrue(topLevelIndex(instructions, recovery) < topLevelIndex(instructions, scheduler));
    assertTrue(topLevelIndex(instructions, scheduler) < topLevelIndex(instructions, defendDispatch));

    JsonObject start = action(scheduler, "TimerStart", AIM_TIMER);
    assertEquals(JsonParser.parseString("[0.4,0.7]"), start.get("StartValueRange"));
    assertEquals(JsonParser.parseString("[0.4,0.7]"), start.get("RestartValueRange"));
    assertTrue(hasAction(scheduler, "TimerRestart", AIM_TIMER));
    assertTrue(hasSetFlag(scheduler, AIM_FLAG, true));

    assertTrue(hasSetFlag(recovery, AIM_FLAG, false));
    assertTrue(hasAction(recovery, "TimerStop", AIM_TIMER));
    assertTrue(hasAction(recovery, "ResetInstructions", null));
}
```

`instructionWithAction`, `action`, `hasAction`, and `hasSetFlag` must inspect only the selected top-level instruction's descendants and match timer `Name` exactly when the supplied timer name is non-null.

- [ ] **Step 3: Change cadence and final-shot assertions to the explicit cooldown sequence**

Keep the existing 11-entry cadence map, but replace reads of `AttackPauseRange` with `TimerStart` for `COOLDOWN_TIMER`. For every talent instruction, assert:

```java
JsonObject instruction = instructionForTalent(instructions, talentId);
JsonObject attack = projectileAttack(instruction);
JsonObject cooldown = action(instruction, "TimerStart", COOLDOWN_TIMER);
JsonArray actions = instruction.getAsJsonArray("Actions");

assertEquals(JsonParser.parseString("[0.1,0.2]"), attack.get("AimingTimeRange"), talentId);
assertEquals(JsonParser.parseString("[0,0]"), attack.get("AttackPauseRange"), talentId);
assertEquals(expected.get(talentId), cooldown.get("StartValueRange"), talentId);
assertEquals(expected.get(talentId), cooldown.get("RestartValueRange"), talentId);
assertTrue(hasAction(instruction, "TimerRestart", COOLDOWN_TIMER), talentId);
assertTrue(hasStoppedTimerSensor(instruction, AIM_TIMER), talentId);
assertTrue(hasPositiveFlagSensor(instruction, AIM_FLAG), talentId);
assertEquals("SetFlag", string(actions.get(actions.size() - 1).getAsJsonObject(), "Type"), talentId);
assertTrue(hasSetFlag(actions.get(actions.size() - 1), AIM_FLAG, false), talentId);
assertFalse(instruction.has("BodyMotion"), talentId + " motion belongs to the shared priority phase");
assertFalse(instruction.has("HeadMotion"), talentId + " motion belongs to the shared priority phase");
```

Retain the interaction mapping and positive/excluded talent-gate tests unchanged.

- [ ] **Step 4: Run the focused contract and verify the intended red state**

Run:

```bash
./mvnw -Dtest=MiniwyvernTalentAssetWiringTest test
```

Expected: failures report `MinAirSpeed` as `3`, missing `Deceleration`, missing scheduler/recovery instructions, missing `MatchLook`, and the old attack-owned cooldown fields. Existing unrelated test methods must remain green.

---

### Task 2: Implement native hover and the timer-driven firing phase

**Files:**
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json`
- Test: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java`

**Interfaces:**
- Consumes: `AIM_FLAG`, `AIM_TIMER`, and `COOLDOWN_TIMER` contract names from Task 1.
- Produces: one controller configuration, three priority instructions, and 11 mutually exclusive projectile action sequences.

- [ ] **Step 1: Make the native Fly controller hover-capable**

Change the Fly entry only:

```json
"MinAirSpeed": 0,
"Acceleration": 6,
"Deceleration": 12,
```

Do not change the Walk controller or the hostile MiniWyvern template.

- [ ] **Step 2: Insert recovery, scheduler, and priority motion before the owner-leash/state dispatch instructions**

Insert these three top-level instructions immediately after `Component_HyDragon_Instruction_Airborne_Mode_Transition`, in this order:

```json
{
  "$Comment": "Cancel a stale projectile line-up before ordinary state motion is selected.",
  "Continue": true,
  "Sensor": { "Type": "And", "Sensors": [
    { "Type": "Flag", "Name": "Miniwyvern_Projectile_Aiming" },
    { "Type": "Or", "Sensors": [
      { "Type": "Not", "Sensor": { "Type": "State", "State": "Defend" } },
      { "Type": "Not", "Sensor": { "Type": "Target", "TargetSlot": "LockedTarget" } }
    ] }
  ] },
  "Actions": [
    { "Type": "SetFlag", "Name": "Miniwyvern_Projectile_Aiming", "SetTo": false },
    { "Type": "TimerStop", "Name": "Miniwyvern_Projectile_Aim" },
    { "Type": "ResetInstructions" }
  ]
},
{
  "$Comment": "Start one visible line-up when the projectile cadence is ready.",
  "Continue": true,
  "Sensor": { "Type": "And", "Sensors": [
    { "Type": "State", "State": "Defend" },
    { "Type": "Target", "TargetSlot": "LockedTarget" },
    { "Type": "TameworkHasTalent", "TalentId": "DraconicProjectile" },
    { "Type": "Flag", "Name": "Miniwyvern_Projectile_Aiming", "Set": false },
    { "Type": "Timer", "Name": "Miniwyvern_Projectile_Cooldown", "State": "Stopped" }
  ] },
  "Actions": [
    { "Type": "TimerStart", "Name": "Miniwyvern_Projectile_Aim", "StartValueRange": [0.4, 0.7], "RestartValueRange": [0.4, 0.7] },
    { "Type": "TimerRestart", "Name": "Miniwyvern_Projectile_Aim" },
    { "Type": "SetFlag", "Name": "Miniwyvern_Projectile_Aiming", "SetTo": true }
  ]
},
{
  "$Comment": "First-match ownership: stop and turn with the aimed head until the shot sequence completes.",
  "Continue": true,
  "Sensor": { "Type": "And", "Sensors": [
    { "Type": "State", "State": "Defend" },
    { "Type": "Target", "TargetSlot": "LockedTarget" },
    { "Type": "Flag", "Name": "Miniwyvern_Projectile_Aiming" }
  ] },
  "BodyMotion": { "Type": "MatchLook" },
  "HeadMotion": { "Type": "Aim", "Spread": 0, "HitProbability": 1, "Deflection": true }
}
```

The scheduler actions deliberately set the flag last. The recovery actions deliberately reset blocking instruction state after clearing/stopping the phase.

- [ ] **Step 3: Convert all 11 talent instructions into post-line-up fire/cooldown sequences**

For each existing talent sensor, preserve every `State`, `Target`, positive talent, and higher-talent exclusion. Add these two sensors to its existing `And.Sensors`:

```json
{ "Type": "Flag", "Name": "Miniwyvern_Projectile_Aiming" },
{ "Type": "Timer", "Name": "Miniwyvern_Projectile_Aim", "State": "Stopped" }
```

Remove `BodyMotion` and `HeadMotion` from every talent instruction. Retain `ActionsBlocking: true`, and replace its action list with this exact ordering:

```json
"Actions": [
  { "Type": "Attack", "Attack": { "Compute": "TalentProjectileBase" }, "AimingTimeRange": [0.1, 0.2], "AttackPauseRange": [0, 0] },
  { "Type": "TimerStart", "Name": "Miniwyvern_Projectile_Cooldown", "StartValueRange": [5, 7], "RestartValueRange": [5, 7] },
  { "Type": "TimerRestart", "Name": "Miniwyvern_Projectile_Cooldown" },
  { "Type": "SetFlag", "Name": "Miniwyvern_Projectile_Aiming", "SetTo": false }
]
```

Use this complete mapping for the computed projectile and cooldown range:

| Positive talent | Attack compute | Cooldown |
| --- | --- | --- |
| `DraconicProjectile` | `TalentProjectileBase` | `[5,7]` |
| `ProjectileRange` | `TalentProjectileIntermediate` | `[5,7]` |
| `ProjectileCadence` | `TalentProjectileIntermediate` | `[4,6]` |
| `ProjectileForce` | `TalentProjectileIntermediate` | `[5,7]` |
| `ProjectileGuidance` | `TalentProjectileIntermediate` | `[4,6]` |
| `ProjectileImpact` | `TalentProjectileApex` | `[5,7]` |
| `ProjectilePattern` | `TalentProjectileApex` | `[4,6]` |
| `DraconicAssault` | `TalentProjectileApex` | `[3,5]` |
| `AssaultUtility` | `TalentProjectileApex` | `[3,5]` |
| `AssaultMastery` | `TalentProjectileApex` | `[3,5]` |
| `DraconicApex` | `TalentProjectileApex` | `[3,5]` |

The final `SetFlag` must remain last because blocking action lists advance one action per tick; clearing the flag earlier would prevent the cooldown actions from executing.

- [ ] **Step 4: Run focused contracts and inspect the exact asset diff**

Run:

```bash
./mvnw -Dtest=MiniwyvernTalentAssetWiringTest,DragonHornLocomotionAssetContractTest test
git diff --check
git diff --stat
git diff -- Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java
```

Expected: all focused tests pass; only the tamed MiniWyvern template and its contract test differ. Confirm the Hold branches still use `BodyMotion: Nothing`, the aerial Defend component reference/tuning is unchanged, and no other species file appears.

- [ ] **Step 5: Commit the behavior and regression contract**

```bash
git add -- Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java
git commit -m "Fix: add MiniWyvern projectile aim phase"
```

---

### Task 3: Validate the exact candidate and complete repository verification

**Files:**
- Read: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json`
- Read: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java`
- Generate (ignored): `.asset-tools/reports/miniwyvern-dedicated-aim.patch.json`
- Generate (ignored): `.asset-tools/reports/miniwyvern-dedicated-aim-baseline-context.json`
- Generate (ignored): `.asset-tools/reports/miniwyvern-dedicated-aim.candidate.json`
- Generate (ignored): `.asset-tools/reports/miniwyvern-dedicated-aim-validation.json`
- Generate (ignored): `.asset-tools/reports/miniwyvern-dedicated-aim-verification-plan.json`
- Generate (ignored): `.asset-tools/reports/miniwyvern-dedicated-aim-verification-result.json`

**Interfaces:**
- Consumes: the committed Task 2 candidate and its parent revision as the exact baseline.
- Produces: exact-profile affected-scope validation, static verification evidence, and a green packaged build.

- [ ] **Step 1: Create an external detached baseline and stage its exact-profile evidence**

Use an external temporary root so HytaleNpcAssetTools does not scan a nested `.worktrees` copy as duplicate assets. Capture the Windows-form main path before changing directories:

```bash
main_root=$(pwd -W)
baseline_root=$(mktemp -d /tmp/hydragon-dedicated-aim-baseline.XXXXXX)
baseline_wt="$baseline_root/worktree"
git worktree add --detach "$baseline_wt" HEAD^
```

Copy the ignored exact-profile evidence required by the baseline's portable `.hytale-npc-assets.json`:

```bash
mkdir -p "$baseline_wt/.asset-tools/reports"
cp "$main_root/.asset-tools/reports/schema-catalog.json" \
  "$baseline_wt/.asset-tools/reports/schema-catalog.json"
cp "$main_root/.asset-tools/reports/runtime-profile.json" \
  "$baseline_wt/.asset-tools/reports/runtime-profile.json"
```

- [ ] **Step 2: Check the exact 0.5.7 release profile and snapshot the baseline asset**

From `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HytaleNpcAssetTools`, run:

```bash
python -m hytale_npc_assets.cli profile check \
  --project-profile "$baseline_wt/.hytale-npc-assets.json" \
  --project-profile-local "$main_root/.asset-tools/reports/miniwyvern-pause-aim.local.json" \
  --mod "$baseline_wt" --json

python -m hytale_npc_assets.cli author inspect \
  --project-profile "$baseline_wt/.hytale-npc-assets.json" \
  --project-profile-local "$main_root/.asset-tools/reports/miniwyvern-pause-aim.local.json" \
  --mod "$baseline_wt" \
  --workspace-root "$baseline_wt" \
  --workspace-root "$main_root" \
  --asset Template_Wyvern_Mini_Flying_Tamed \
  --view declared --provenance compact --references outgoing \
  --out "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim-baseline-context.json"
```

Expected: the profile check reports exact release `0.5.7`, and the snapshot identifies the baseline source as `Template_Wyvern_Mini_Flying_Tamed`.

- [ ] **Step 3: Build and validate an exact candidate envelope**

Generate one RFC 6902 root replacement from the committed candidate, then wrap it with the exact profile and source hash returned by the baseline inspection. This generated report avoids brittle array-index patch construction:

```bash
python - \
  "$main_root/Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json" \
  "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim-baseline-context.json" \
  "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim.patch.json" \
  "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim.candidate.json" <<'PY'
import json
import sys
from pathlib import Path

source, context_path, patch_path, candidate_path = map(Path, sys.argv[1:])
document = json.loads(source.read_text(encoding="utf-8"))
context = json.loads(context_path.read_text(encoding="utf-8"))
operation = {"op": "replace", "path": "", "value": document}
patch_path.write_text(json.dumps([operation], indent=2) + "\n", encoding="utf-8")

snapshot = context["snapshot"]
envelope = {
    "formatVersion": 1,
    "profile": snapshot["profile"],
    "targets": [{
        "mode": "patch",
        "assetId": snapshot["asset"]["id"],
        "sourcePath": snapshot["asset"]["sourcePath"],
        "expectedSha256": snapshot["asset"]["sourceSha256"],
        "operations": [operation],
    }],
    "intent": "Give the tamed MiniWyvern a stationary aim phase and native airborne hover.",
}
candidate_path.write_text(json.dumps(envelope, indent=2) + "\n", encoding="utf-8")
PY
```

Validate the candidate from the same tool directory:

```bash
python -m hytale_npc_assets.cli author validate \
  --project-profile "$baseline_wt/.hytale-npc-assets.json" \
  --project-profile-local "$main_root/.asset-tools/reports/miniwyvern-pause-aim.local.json" \
  --mod "$baseline_wt" \
  --workspace-root "$baseline_wt" \
  --workspace-root "$main_root" \
  --patch "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim.candidate.json" \
  --scope affected --simulate \
  --out "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim-validation.json"
```

Accept exit `0` (`safe-static`) or exit `1` (`review-required`) with no blocker/regression diagnostic. Stop for exit `2`, `3`, or `4`.

- [ ] **Step 4: Generate and run the static verification plan**

Use the same exact candidate envelope:

```bash
python -m hytale_npc_assets.cli author verify generate \
  --project-profile "$baseline_wt/.hytale-npc-assets.json" \
  --project-profile-local "$main_root/.asset-tools/reports/miniwyvern-pause-aim.local.json" \
  --mod "$baseline_wt" \
  --workspace-root "$baseline_wt" \
  --workspace-root "$main_root" \
  --candidate "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim.candidate.json" \
  --out "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim-verification-plan.json"

python -m hytale_npc_assets.cli author verify run \
  --project-profile "$baseline_wt/.hytale-npc-assets.json" \
  --project-profile-local "$main_root/.asset-tools/reports/miniwyvern-pause-aim.local.json" \
  --mod "$baseline_wt" \
  --workspace-root "$baseline_wt" \
  --workspace-root "$main_root" \
  --verification "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim-verification-plan.json" \
  --mode static \
  --out "$main_root/.asset-tools/reports/miniwyvern-dedicated-aim-verification-result.json"
```

Record safe-static claims separately from the expected unsupported live-runtime claim. Do not report hover or facing as runtime-proven without the user's in-game acceptance test.

- [ ] **Step 5: Remove the validated baseline worktree safely**

Verify the resolved path remains under `/tmp/hydragon-dedicated-aim-baseline.*`, then:

```bash
git worktree remove "$baseline_wt"
git worktree prune
rmdir "$baseline_root"
```

- [ ] **Step 6: Run full verification on the committed feature revision**

```bash
git diff --check HEAD^
./mvnw clean verify
```

Expected: HyDragon asset validation passes for 451 JSON assets and 5 locales; all unit and packaged integration tests pass; Maven ends with `BUILD SUCCESS`.

---

### Task 4: Integrate, build the exact commit, and install both JARs

**Files:**
- Install: `C:/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar`
- Install: `C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods/HyDragon v1.0.0.jar`

**Interfaces:**
- Consumes: the fully verified Task 2 commit.
- Produces: clean `main`, identical installed artifacts, and a packaged-asset proof for all 11 variants.

- [ ] **Step 1: Reconcile the verified behavior commit onto local `main`**

If execution occurred directly on `main`, confirm the Task 2 commit is `HEAD` and continue. If execution used an isolated feature branch/worktree, use `superpowers:finishing-a-development-branch` and the user's already-selected local integration outcome to merge it into `main`. In either case, rerun `./mvnw verify` on the resulting `main` revision before removing the owned feature worktree/branch.

- [ ] **Step 2: Confirm Hytale and Maven are stopped**

```bash
jps -l | rg 'HytaleServer|MavenWrapperMain'
```

Expected: no matching JVM. Stop rather than overwriting a loaded plugin if Hytale is running.

- [ ] **Step 3: Build and install from an external detached worktree at the exact merged commit**

```bash
commit=$(git rev-parse 'HEAD^{commit}')
install_root=$(mktemp -d /tmp/hydragon-dedicated-aim-install.XXXXXX)
install_wt="$install_root/worktree"
git worktree add --detach "$install_wt" "$commit"
cd "$install_wt"
./mvnw package -DskipTests -Pinstall-plugin
```

Expected: one artifact is copied to the server mods directory and the UserData mods directory; Maven ends with `BUILD SUCCESS`.

- [ ] **Step 4: Verify both installed JARs and their packaged MiniWyvern contract**

Confirm `sha256sum` values match and `cmp -s` succeeds. Open each JAR with Python `zipfile`, load:

```text
Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json
```

Assert in both artifacts:

- Fly has `MinAirSpeed == 0` and `Deceleration == 12`.
- Recovery, scheduler, and priority aim precede the ordinary Defend dispatch.
- Priority motion is exact `MatchLook` plus deterministic `Aim`.
- There are exactly 11 talent projectile attacks.
- Every attack has `[0.1,0.2]` final allowance, `[0,0]` attack pause, an exact cooldown `TimerStart`/`TimerRestart`, and the final flag-clear action.
- Hold retains `Nothing` in both Walk and Fly branches.

- [ ] **Step 5: Remove the install worktree and perform final hygiene checks**

Verify the resolved install root remains under `/tmp/hydragon-dedicated-aim-install.*`, then:

```bash
git worktree remove "$install_wt"
git worktree prune
rmdir "$install_root"
git status --short --branch
jps -l | rg 'HytaleServer|MavenWrapperMain'
```

Expected: only the primary worktree remains, `main` is clean, no Hytale/Maven JVM remains, and both installed hashes are reported to the user with the exact source commit.
