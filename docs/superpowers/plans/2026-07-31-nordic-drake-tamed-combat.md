# Tamed Nordic Drake Wild-Style Combat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `Tamed_NordicDrake` defend its owner or attack the player's commanded target with the Nordic Drake's grounded and aerial attack suite, selected exclusively by `AirborneMode`.

**Architecture:** `Template_HyDragon_Dragon_Tamed` gains a disabled-by-default Nordic combat opt-in ahead of its existing generic Defend branches. `Tamed_NordicDrake` alone enables a new HyDragon instruction component; that component keeps every sensor and attack on `LockedTarget`, implements the grounded melee/bite/flame-breath and aerial fireball/volley/breath/recovery cycles directly, and yields to the existing Miniwyvern-style controller transition while `AirborneMode` and the active controller disagree.

**Tech Stack:** Hytale 0.5.7 NPC JSON assets, Tamework 3.0.0 asset components and filters, Java 25, Maven, JUnit 5, Gson, Python asset validation, Hytale NPC Asset Tools.

**Design source:** [Tamed Nordic Drake Wild-Style Combat Design](../specs/2026-07-31-nordic-drake-tamed-combat-design.md)

## Global Constraints

- Preserve the command contract: Defend acquires recent threats to `MasterTarget`; Attack Target preserves the player's valid `LockedTarget`.
- Every combat `Target` sensor must name `TargetSlot: LockedTarget`. The new component may release an invalid target but may never set, replace, or clear it through an implicit hostile query.
- The component must contain no `CombatActionEvaluator`, `CombatAbility`, `SelectBasicAttackTarget`, `HasHostileTargetMemory`, `AddToHostileTargetMemory`, or target sensor without an explicit slot.
- `AirborneMode=false` plus `Walk` is the only grounded-combat context. `AirborneMode=true` plus `Fly` is the only aerial-combat context.
- Flag/controller disagreement is a transition interval. The existing `Component_HyDragon_Instruction_Airborne_Mode_Transition` owns takeoff and landing without changing `Defend`, `MasterTarget`, or `LockedTarget`.
- The tamed component must contain no `Stat` health filter and no reference to `AirPhaseHealthRange`, `GroundPhaseHealthRange`, or `UseHealthPhaseFlight`.
- Preserve the wild attack IDs exactly: `Root_NPC_NordicDrake_Attack`, `Root_NPC_NordicDrake_Bite`, `Root_NPC_NordicDrake_Flame_Breath`, `Root_NPC_NordicDrake_Fire_Ball`, `Root_NPC_NordicDrake_Fire_Ball_Volley_2`, `Root_NPC_NordicDrake_Fire_Ball_Volley_3`, `Root_NPC_NordicDrake_Fire_Ball_Volley_4`, and `Root_NPC_NordicDrake_Flying_Flame_Breath`.
- Keep `Template_HyDragon_Dragon_Tamed` safe for every descendant. The Nordic feature flag defaults to `false`, and only `Tamed_NordicDrake` sets it to `true`.
- Do not change wild `NordicDrake`, `CAE_NordicDrake`, Dragon Horn commands, Miniwyvern combat, Hydra roles, Rock Drake roles, mounting, avatar flight, progression, breeding, health, or damage calculators.
- Do not add a Java NPC builder or change Tamework source. The Tamework source checkout is dirty with unrelated user work and is verification-only for this feature.
- Validate against HyDragon's locked `release-0.5.7` profile: identity `8f917b67e62db471460d6e72d9c512e290c313611493f67a483b28d818ebc65d`, source commit `dd07e6a837aaf6378e82ff81d6f520f913624c08`, semantic pack `3b9c4eb8596cf221ea6b0578b8cb7f9c4859e316607326a1ae0531c162a9d64c`, and no knowledge pack.
- Preserve unrelated workspace changes, make one scoped commit per completed implementation task, and do not install, publish, deploy, or start a game/server process.

## Files and Ownership Map

| File | Responsibility |
| --- | --- |
| `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java` | Behavior-facing JSON contract and mutation protection. |
| `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json` | Nordic-only target safety, grounded combat, aerial combat, timers, and recovery. |
| `Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json` | Disabled-by-default feature parameter and priority handoff from Defend to the Nordic component. |
| `Server/NPC/Roles/Creature/HyDragon/NordicDrake/Tamed_NordicDrake.json` | Sole role opt-in. Existing role statistics and companion features remain intact. |
| `.asset-tools/reports/nordic-tamed-combat-*.json` | Ignored exact-profile inspection, validation, and verification evidence. |

---

### Task 1: Establish Nordic-Only Defend Handoff and Target Safety

**Files:**

- Create: `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java`
- Create: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json:1-605,1403-1532`
- Modify: `Server/NPC/Roles/Creature/HyDragon/NordicDrake/Tamed_NordicDrake.json:1-140`

**Interfaces:**

- Consumes: the existing Defend state, `MasterTarget`, `LockedTarget`, `AirborneMode`, `Walk`, `Fly`, `TameworkIsOwner`, `TameworkAttitudeFromTargetSlot`, and `Component_Sensor_Lost_Target_Detection`.
- Produces: template boolean parameter `UseNordicDrakeTamedCombat` and component ID `Component_HyDragon_Instruction_NordicDrake_Tamed_Combat`.

- [ ] **Step 1: Write the failing opt-in and safety test**

  Create `NordicDrakeTamedCombatAssetTest` with Gson traversal helpers that read real repository assets. The first test must name the break it catches: enabling Nordic combat for another descendant, allowing generic Defend to win when a Nordic target is locked, or attacking the owner/friendly target.

  ```java
  @Test
  void nordicCombatIsAnExclusiveLockedTargetTakeoverWithBoundedSafety() throws IOException {
      JsonObject template = readJson(TEMPLATE);
      JsonObject nordic = readJson(TAMED_NORDIC);
      JsonObject component = readJson(COMPONENT);

      assertEquals(false, template.getAsJsonObject("Parameters")
              .getAsJsonObject("UseNordicDrakeTamedCombat").get("Value").getAsBoolean());
      assertTrue(nordic.getAsJsonObject("Modify")
              .get("UseNordicDrakeTamedCombat").getAsBoolean());
      assertNordicTakeoverPrecedesGenericDefend(defendInstruction(template));
      assertOnlyNordicRoleOptsIn();
      assertHardLeashRelease(component);
      assertOwnerAndFriendlyRelease(component);
      assertLostTargetRelease(component);
      assertNoImplicitTargetSelection(component);
  }
  ```

  Implement `readJson(Path)`, recursive `objects(JsonElement, Predicate<JsonObject>)`, `string(JsonObject,String)`, and the five assertion helpers in the test file. `assertNordicTakeoverPrecedesGenericDefend` must inspect direct children of the outer Defend instruction and prove that the enabled Nordic wrapper is the first locked-target branch and is non-continuing; the two existing Walk/Fly generic Defend fallbacks must remain after it. `assertOnlyNordicRoleOptsIn` must scan every role JSON below `Server/NPC/Roles/Creature/HyDragon` and allow the literal `true` only in `Tamed_NordicDrake.json`.

- [ ] **Step 2: Run the focused test and verify RED**

  Run:

  ```bash
  ./mvnw -Dtest=NordicDrakeTamedCombatAssetTest test
  ```

  Expected: FAIL because `UseNordicDrakeTamedCombat` and the component file do not exist. A JSON syntax error or test compilation error is not the expected failure; correct those and rerun until the assertion fails for the missing feature.

- [ ] **Step 3: Add the minimal role/template handoff**

  Add this template parameter with no behavior enabled by default:

  ```json
  "UseNordicDrakeTamedCombat": {
    "Value": false,
    "Description": "Whether Defend hands a valid LockedTarget to the Nordic Drake combat component."
  }
  ```

  Set only this additional field in `Tamed_NordicDrake.Modify`:

  ```json
  "UseNordicDrakeTamedCombat": true
  ```

  Insert the following wrapper as the first direct child of the existing outer Defend instruction, before the Walk and Fly generic branches. Do not add `Continue` to the wrapper.

  ```json
  {
    "Enabled": { "Compute": "UseNordicDrakeTamedCombat" },
    "Sensor": {
      "Type": "Target",
      "TargetSlot": "LockedTarget"
    },
    "Instructions": [
      {
        "Reference": "Component_HyDragon_Instruction_NordicDrake_Tamed_Combat",
        "Modify": {
          "HardLeashDistance": { "Compute": "HardLeashDistance" },
          "AlertedRange": { "Compute": "AlertedRange" },
          "ViewRange": { "Compute": "ViewRange" },
          "ViewSector": { "Compute": "ViewSector" },
          "HearingRange": { "Compute": "HearingRange" },
          "AbsoluteDetectionRange": { "Compute": "AbsoluteDetectionRange" }
        }
      }
    ]
  }
  ```

- [ ] **Step 4: Add the minimal safe component shell**

  Create a `Type: Component`, `Class: Instruction` asset with `DefaultState: .Default`. Declare the six parameters passed by the template plus `MasterTargetSlot` defaulting to `MasterTarget`. Its `Content` must be a continuing `Any` instruction whose ordered children are:

  1. hard-leash failure: `Not(Player Range=HardLeashDistance)` releases `LockedTarget` and calls `ResetInstructions`;
  2. owner target: `Target(TargetSlot=LockedTarget, Filters=[TameworkIsOwner])` releases and resets;
  3. friendly-to-owner target: `TameworkAttitudeFromTargetSlot` with `SourceTargetSlot=MasterTargetSlot`, `Attitudes=[Friendly]`, and `UseSelfWhenSourceMissing=false` releases and resets;
  4. lost-target detection: negated `Component_Sensor_Lost_Target_Detection` configured with the declared view/hearing ranges and `TargetSlot=LockedTarget` releases and resets;
  5. a final no-op `Any` child so the shell remains valid before Task 2 supplies combat.

  Every safety sensor that observes an entity target must explicitly name `LockedTarget` or computed `MasterTargetSlot`. No instruction may set a target.

- [ ] **Step 5: Run the focused test and verify GREEN**

  Run `./mvnw -Dtest=NordicDrakeTamedCombatAssetTest test` and expect PASS. Then run `./mvnw -Dtest=DragonHornLocomotionAssetContractTest test` and expect PASS, proving the new wrapper did not alter existing ground/fly Defend fallbacks.

- [ ] **Step 6: Commit the bounded handoff**

  ```bash
  git add src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json Server/NPC/Roles/Creature/HyDragon/NordicDrake/Tamed_NordicDrake.json
  git commit -m "Feat: route Nordic Drake defend combat"
  ```

---

### Task 2: Implement Grounded Melee, Bite, and Flame-Breath Combat

**Files:**

- Modify: `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json`

**Interfaces:**

- Consumes: the safe component shell from Task 1 and the existing `LockedTarget`, `AirborneMode`, and `Walk` context.
- Produces: local component state `.GroundCombat`; timers `NordicDrake_Ground_Bite`, `NordicDrake_Ground_Breath`, and `NordicDrake_Ground_Basic`; direct grounded attack and positioning behavior.

- [ ] **Step 1: Extend the test with a failing grounded-combat contract**

  Add a test that finds the direct branch requiring `AirborneMode=false`, `Walk`, `.GroundCombat`, and `LockedTarget`. Assert the component parameter values and direct actions from this literal table:

  | Behavior | Root interaction | Maximum range | Weight or fallback | Cooldown |
  | --- | --- | ---: | --- | --- |
  | Basic melee | `Root_NPC_NordicDrake_Attack` | `5.25` | fallback | `[1.5,2.5]` |
  | Bite | `Root_NPC_NordicDrake_Bite` | `4.5` | `3` | `[10,20]` |
  | Ground flame breath | `Root_NPC_NordicDrake_Flame_Breath` | `9` | `4` | `[10,20]` |

  The test must also assert that both special branches use `ActionsBlocking: true`, restart only their named timer after attacking, and that the basic melee branch cannot run while either selected special action is executing. Assert grounded movement uses `DesiredAttackDistanceRange=[0.5,5]`, `CombatBehaviorDistance=18`, `CombatBackwardsRelativeSpeed=0.4`, and explicit `LockedTarget` sensors.

- [ ] **Step 2: Run the grounded test and verify RED**

  Run `./mvnw -Dtest=NordicDrakeTamedCombatAssetTest test` and expect failure because `.GroundCombat` and the three attack roots are absent.

- [ ] **Step 3: Declare exact grounded parameters**

  Add component parameters with these names and values: `GroundBasicAttack`, `GroundBasicAttackDistance`, `GroundBasicCooldownRange`, `GroundBiteAttack`, `GroundBiteDistance`, `GroundBiteCooldownRange`, `GroundBiteWeight`, `GroundBreathAttack`, `GroundBreathDistance`, `GroundBreathCooldownRange`, `GroundBreathWeight`, `DesiredAttackDistanceRange`, `CombatBehaviorDistance`, `CombatMovingRelativeSpeed=0.6`, `CombatBackwardsRelativeSpeed`, and `CombatRelativeTurnSpeed=1.5`. Use the literal values in Step 1 and the global constraints.

- [ ] **Step 4: Implement grounded entry, movement, and attack choice**

  Before attack execution, add a mode-entry instruction that requires `LockedTarget`, `AirborneMode=false`, and `Walk`, then changes only the component-relative state to `.GroundCombat`. Inside `.GroundCombat`:

  - require the same four context sensors on every attack branch;
  - chase beyond `GroundBreathDistance` through a `LockedTarget`-guarded `Component_Tamework_Instruction_Intelligent_Chase`, modifying only its established `ViewRange`, `HearingRange`, `RelativeSpeed`, `SlowDownDistance`, and `StopDistance` parameters; do not invent a component parameter for the target slot;
  - within `CombatBehaviorDistance`, use `MaintainDistance` with `DesiredAttackDistanceRange`, forwards speed `CombatMovingRelativeSpeed`, and backwards speed `CombatBackwardsRelativeSpeed`;
  - when both special timers are stopped and both range/line-of-sight gates match, use one `Random` selector with bite weight `3` and breath weight `4`;
  - when only one special is eligible, execute that special directly;
  - use basic melee as the final in-range fallback with `AimingTimeRange=[0.1,0.2]`;
  - after each attack, `TimerStart` and `TimerRestart` only its own named timer using the cooldown table.

  Use `HeadMotion: Aim` with `Spread=0`, `HitProbability=1`, `Deflection=true`, and computed `CombatRelativeTurnSpeed`. Special and basic attacks use direct `Attack` actions, never `CombatAbility`.

- [ ] **Step 5: Run focused regressions and verify GREEN**

  ```bash
  ./mvnw -Dtest=NordicDrakeTamedCombatAssetTest,DragonHornLocomotionAssetContractTest test
  ```

  Expected: PASS. Mentally mutate each root ID, one cooldown, one weight, the false flag, the Walk controller, and one `TargetSlot`; at least one assertion must fail for each mutation.

- [ ] **Step 6: Commit grounded combat**

  ```bash
  git add src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json
  git commit -m "Feat: add grounded Nordic Drake combat"
  ```

---

### Task 3: Implement Flag-Driven Aerial Fireball, Volley, Breath, and Recovery

**Files:**

- Modify: `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json`

**Interfaces:**

- Consumes: the Task 1 safety shell, Task 2 grounded combat, `AirborneMode=true`, `Fly`, `LockedTarget`, and the existing `TameworkFlyingOrbit` builder.
- Produces: `.AirRanged`, `.AirVolley`, `.AirBreathIngress`, `.AirBreathPass`, and `.AirRecovery`; timers `NordicDrake_Air_Fireball`, `NordicDrake_Air_Volley`, and `NordicDrake_Air_Breath`.

- [ ] **Step 1: Extend the test with a failing aerial-cycle contract**

  Add one test that asserts all five aerial states exist and every state-owning branch directly requires `LockedTarget`, `AirborneMode=true`, and `Fly`. Assert the exact attack/timing table:

  | Behavior | Root interaction | Range | Weight or selection | Cooldown |
  | --- | --- | ---: | --- | --- |
  | Moving fireball | `Root_NPC_NordicDrake_Fire_Ball` | `28` | fallback | `[1,3]` |
  | Volley choice | volley roots `_2`, `_3`, `_4` | `28` | special weight `3`, equal internal weights | `[12,20]` |
  | Flying breath | `Root_NPC_NordicDrake_Flying_Flame_Breath` | ingress commit `13` | special weight `6` | `[15,30]` |

  Assert `TameworkFlyingOrbit` modes by state: `WANDER_TARGET` in ranged and recovery, `FACE_TARGET` in volley, `APPROACH` in breath ingress, and `PASS_THROUGH_TARGET` in breath pass. Assert the exact Nordic tuning: combat altitude `[8,16]`, climb `1`, sink `0.8`, ranged wander speed `0.9`, wander radius `[18,36]`, retarget `[4,8]`, stop distance `5`, breath altitude `[2,4]`, breath speed `1.15`, ingress timeout `[6,7]`, pass distance `18`, pass stop `2`, pass duration `[1.85,2]`, recovery duration `[3,4]`, recovery speed `1.1`, and recovery climb `1.5`.

  Assert the component contains zero health-stat filters and zero forbidden CAE/hostile-memory builders from the global constraints.

- [ ] **Step 2: Run the aerial test and verify RED**

  Run `./mvnw -Dtest=NordicDrakeTamedCombatAssetTest test` and expect failure because `.AirRanged` and the aerial attack roots are absent.

- [ ] **Step 3: Declare aerial parameters and cancellation cleanup**

  Add component parameters for every literal in Step 1 plus `AirRangedAttackViewSector=75`, `AirBreathAttackViewSector=60`, `AirCombatWanderMinMoveDistance=8`, and `AirCombatWanderTestsPerTick=8`. Add a high-priority continuing cancellation instruction: if any of `LockedTarget`, `AirborneMode`, or `Fly` is missing while an aerial relative state is active, stop all three aerial timers, change only the component-relative state to `.Default`, and call `ResetInstructions`. Do not release a valid target merely because the flag/controller is transitioning.

- [ ] **Step 4: Implement aerial entry and ranged loiter**

  When `LockedTarget`, `AirborneMode`, and `Fly` are all present, enter `.AirRanged`. In `.AirRanged`, keep `WANDER_TARGET` movement active with the exact geometry from Step 1. Execute a moving base fireball only through a direct `LockedTarget` range, line-of-sight, and view-sector sensor; aim the head without forcing body-facing motion; then restart `NordicDrake_Air_Fireball` with `[1,3]`.

  When both special timers are stopped, choose breath versus volley with a `Random` selector weighted `6` versus `3`. When only one special timer is stopped, enter its state directly. Starting a special state starts and restarts only that special's cooldown timer.

- [ ] **Step 5: Implement bounded volley, breath pass, and recovery**

  In `.AirVolley`, choose exactly one of the three volley roots with equal weights. Preserve the proven action pauses: two-shot waits `4`, three-shot waits `5.8`, four-shot waits `7.5`; all use `BallisticMode=Short`, aiming `[0.25,1]`, attack pause `[10,10]`, then enter `.AirRecovery`.

  In `.AirBreathIngress`, use `APPROACH` until `LockedTarget` is within `13`, in line of sight, and inside sector `60`; then enter `.AirBreathPass`. Timeout `[6,7]` routes to recovery. In `.AirBreathPass`, fire the flying breath once with aiming `[0,0]` and attack pause `[8,8]`, use `PASS_THROUGH_TARGET` for `[1.85,2]`, then enter recovery. In `.AirRecovery`, use the exact recovery wander geometry for `[3,4]`, then return to `.AirRanged`.

  Every state also needs an `Any` fallback that waits `[0.25,0.5]` and returns to `.AirRecovery` or `.AirRanged` as appropriate, preventing a stale phase when navigation or targeting becomes temporarily unavailable.

- [ ] **Step 6: Run focused and full asset regressions**

  ```bash
  ./mvnw -Dtest=NordicDrakeTamedCombatAssetTest,DragonHornLocomotionAssetContractTest test
  python scripts/validate_assets.py
  ```

  Expected: both commands PASS with no warnings or JSON parse errors. Mentally mutate one state name, orbit mode, root ID, timer range, flag gate, controller gate, health filter, and implicit Target sensor; the focused test must reject every mutation.

- [ ] **Step 7: Commit aerial combat**

  ```bash
  git add src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json
  git commit -m "Feat: add aerial Nordic Drake combat"
  ```

---

### Task 4: Exact-Profile Wiring Audit and Complete Verification

**Files:**

- Verify: all files changed in Tasks 1-3
- Generate ignored evidence: `.asset-tools/reports/nordic-tamed-combat-*.json`

**Interfaces:**

- Consumes: the complete Nordic component, template opt-in, role variant, locked release profile, and existing repository validators.
- Produces: affected-scope static validation evidence, generated verification results, Maven test/package evidence, and a bounded live-test checklist.

- [ ] **Step 1: Confirm the exact release profile is still ready**

  Run from `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HytaleNpcAssetTools`:

  ```bash
  hytale-assets profile check --project-profile /c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.hytale-npc-assets.json --json
  ```

  Require `status=ready`, game version `0.5.7`, channel `release`, and identity hash `8f917b67e62db471460d6e72d9c512e290c313611493f67a483b28d818ebc65d`. A changed identity, unavailable profile, or stale runtime input is a blocker rather than a role-wiring finding.

- [ ] **Step 2: Inspect declared/effective wiring and references**

  Run fresh `author inspect` calls for `Tamed_NordicDrake`, `Template_HyDragon_Dragon_Tamed`, and `Component_HyDragon_Instruction_NordicDrake_Tamed_Combat` with `--view both --provenance compact --references both --include-advisories actionable --format json`. Save each result beneath `.asset-tools/reports/`.

  Confirm these edges: role to template, template to Nordic component, component to all eight root interactions, component to Tamework filters/sensors/motion, relative state actions to declared state sensors, and reverse consumers of the shared template. Treat missing references, duplicate IDs, invalid computed parameters, a non-Nordic opt-in, or a field unavailable in release `0.5.7` as blockers.

- [ ] **Step 3: Run affected-scope author validation and generated verification**

  From a warm exact-profile session, run the final changed-source check against the amended-design checkpoint:

  ```bash
  hytale-assets author check --project-profile /c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.hytale-npc-assets.json --changed --base 8328a90 --scope affected --format json --pretty --out /c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.asset-tools/reports/nordic-tamed-combat-check.json
  ```

  `author check` must build the affected closure, validate the materialized changes, run its supported static verification, and emit the remaining live checklist. Record profile identity, session ID, snapshot ID, candidate/source hashes, added/removed/unchanged findings, and unsupported checks from that report.

  Required static claims: Nordic-only opt-in, valid component reference, explicit `LockedTarget` use, owner/friendly rejection, hard leash, target loss, grounded/aerial exclusivity, mode-transition preservation, no health phase, no CAE target selection, valid attack roots, and unaffected shared-template descendants. Mark real-time cadence, animation quality, hit registration, and navigation smoothness as live evidence gaps rather than static passes.

- [ ] **Step 4: Run complete repository verification**

  From HyDragon:

  ```bash
  ./mvnw -Dtest=NordicDrakeTamedCombatAssetTest,DragonHornLocomotionAssetContractTest test
  python scripts/validate_assets.py
  ./mvnw test
  ./mvnw package
  rg "PlayerRef\.getComponent\(Player|getComponent\(Player\.getComponentType\(\)\)|Universe\.get\(\).*getPlayers" -n src/main/java
  git diff --check
  git status --short
  ```

  Require all tests and packaging to pass. The thread-affinity grep must introduce no new match; no Java source is expected to change. Inspect the packaged jar and confirm it contains the component, shared template, tamed Nordic role, and focused test is excluded from runtime content.

- [ ] **Step 5: Perform independent review before completion**

  Request a read-only reviewer to compare the implementation against the amended design, this plan, the Hytale 0.5.7 CAE target evidence, and the exact-profile reports. Resolve every correctness or safety finding, rerun the affected focused tests, then rerun the complete verification commands from Step 4.

- [ ] **Step 6: Record live acceptance gaps without starting the game**

  Report that live testing remains required for: Defend selecting an owner attacker; Attack Target never switching away from the command target; basic melee, bite, and ground flame breath all occurring; fireball, bounded volley, ingress, breath pass, and recovery all occurring; toggling ground-to-air and air-to-ground mid-combat without losing state/target; owner/friendly rejection; hard leash; target death; and uneven-terrain landing. Do not start the game or a server without explicit authorization.

- [ ] **Step 7: Commit verification-only corrections if any exist**

  If review or validation required tracked changes, stage only those files and commit them as:

  ```bash
  git commit -m "Test: verify tamed Nordic Drake combat"
  ```

  If verification required no tracked changes, do not create an empty commit.

## Final Acceptance Checklist

- [ ] Defend acquires threats to the owner and Attack Target preserves the commanded `LockedTarget`.
- [ ] The Nordic component is the first valid-target Defend branch and generic Defend remains the no-target fallback.
- [ ] Only `Tamed_NordicDrake` enables the component.
- [ ] Ground mode exposes basic melee, bite, and ground flame breath with explicit locked-target gates.
- [ ] Air mode exposes moving fireballs, a bounded 2-4 shot volley, breath ingress/pass, and recovery with explicit locked-target gates.
- [ ] `AirborneMode`, not health, is the sole combat mode selector.
- [ ] The component contains no CAE or hostile-memory target selector and cannot replace `LockedTarget`.
- [ ] Owner/friendly rejection, hard leash, target loss, and controller-transition recovery are bounded.
- [ ] Hydra, Rock Drake, Miniwyvern, mounting, avatar flight, progression, breeding, wild combat, and Dragon Horn commands remain unchanged.
- [ ] Exact-profile affected-scope validation, focused tests, full tests, packaging, diff checks, and independent review pass; unavailable live claims are reported as gaps.
