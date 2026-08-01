# Tamed Nordic Drake Combat Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the grounded tamed Nordic Drake hold a 3.5-5 block combat band, continuously aim at its locked target, use its normal swipe/stomp chain as the basic cadence, and play the mounted fire cues during autonomous fireball and flame-breath abilities.

**Architecture:** Keep the existing manual `LockedTarget` combat component so player commands and owner-defense targeting remain authoritative. Add a continuing grounded head-motion lane, move the existing basic attack branch ahead of special selection, and place audio inside the shared NPC interaction chains so single fireballs, volleys, grounded breath, and flying breath inherit correctly timed cues.

**Tech Stack:** Hytale 0.5.7 NPC and item-interaction JSON assets, Tamework 3.0.0 instruction builders, Java 25, Maven, JUnit 5, Gson, Python asset validation, HytaleNpcAssetTools, Git Bash.

**Design source:** [Tamed Nordic Drake Combat Polish Design](../specs/2026-07-31-nordic-drake-tamed-combat-polish-design.md)

## Global Constraints

- Grounded `DesiredAttackDistanceRange` must be exactly `[3.5, 5]`; the basic melee maximum remains `5.25`.
- Grounded target tracking must consume only `LockedTarget` and require `.GroundCombat`, `AirborneMode=false`, and the `Walk` motion controller.
- Preserve `Root_NPC_NordicDrake_Attack` and its existing left swipe, right swipe, and stomp chain as the regular basic attack.
- Bite and ground flame breath retain their existing roots, ranges, weights, blocking behavior, and `[10, 20]` cooldowns.
- Do not add `CombatActionEvaluator`, `CombatAbility`, hostile-memory selection, health-phase switching, or any action that sets or replaces `LockedTarget`.
- Reuse `SFX_HyDragon_NordicDrake_Avatar_Fireball_Roar`, `SFX_HyDragon_NordicDrake_Avatar_Fireball_Launch`, and `SFX_HyDragon_NordicDrake_Avatar_Flame_Breath_Roar`; do not duplicate or rename sound assets.
- Do not change mounted ability behavior, wild Nordic Drake behavior, damage values, Dragon Horn behavior, other species, progression, breeding, or target-safety exits.
- Add no new permanent test cases. Only maintain existing assertions that hard-code the old grounded spacing and branch order.
- Validate against the repository's locked Hytale `release-0.5.7` profile, identity `8f917b67e62db471460d6e72d9c512e290c313611493f67a483b28d818ebc65d`.
- Preserve unrelated workspace changes, use Git Bash, leave no Maven/server/helper processes or temporary worktrees, and commit each bounded implementation task.
- Install locally from one exact committed revision into both configured Hytale mod directories. Do not create a public release or marketplace deployment; HyDragon is absent from the local publisher catalog.

## Files and Ownership Map

| File | Responsibility |
| --- | --- |
| `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json` | Grounded spacing, continuous head tracking, and basic-versus-special attack priority. |
| `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java` | Existing JSON contract whose hard-coded spacing and branch-order expectations must follow the approved graph. |
| `Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Fire_Ball.json` | Autonomous fireball charge and launch audio timing; inherited by volley roots. |
| `Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Flame_Breath.json` | Grounded autonomous flame-breath roar timing. |
| `Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Flying_Flame_Breath.json` | Flying autonomous flame-breath roar timing. |
| `.asset-tools/reports/nordic-tamed-combat-polish-*.json` | Ignored exact-profile inspection and affected-scope validation evidence. |
| `target/HyDragon v1.0.0.jar` | Verified artifact built from the final exact commit and copied by the existing install profile. |

---

### Task 1: Restore Grounded Spacing, Head Tracking, and Basic Attack Cadence

**Files:**

- Modify: `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java:258-280,636-716`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json:13-28,145-325`

**Interfaces:**

- Consumes: `.GroundCombat`, `LockedTarget`, `AirborneMode`, `Walk`, `CombatRelativeTurnSpeed`, the existing three grounded attack roots, and their timers.
- Produces: a seven-branch grounded instruction order: continuous target tracking, chase, distance maintenance, basic melee, weighted special selection, direct bite, direct flame breath.

- [ ] **Step 1: Maintain the existing grounded test for the approved graph**

  Do not add a test method. Update `assertGroundedParameters` and the existing `assertGroundedMovement`/`assertGroundedAttackChoice` helpers so their old literal range and six-child order describe the approved seven-child order:

  ```java
  assertParameter(parameters, "DesiredAttackDistanceRange", List.of(3.5, 5));

  assertEquals(7, children.size(), "ground combat must retain its seven ordered behavior branches");
  JsonObject tracking = children.get(0);
  assertTrue(tracking.has("Continue") && tracking.get("Continue").getAsBoolean());
  assertGroundedContext(tracking.getAsJsonObject("Sensor"));
  assertEquals("Aim", string(tracking.getAsJsonObject("HeadMotion"), "Type"));
  assertTrue(isChaseBranch(children.get(1)));
  assertTrue(isPositioningBranch(children.get(2)));
  assertTrue(containsAttack(children.get(3), "GroundBasicAttack"));
  assertEquals("Random", string(children.get(4), "Type"));
  assertTrue(containsAttack(children.get(5), "GroundBiteAttack"));
  assertTrue(containsAttack(children.get(6), "GroundBreathAttack"));
  ```

  Update the direct-special helper calls to inspect children `5` and `6`. Keep all existing root, cooldown, weight, timer, `LockedTarget`, flag, controller, and forbidden-CAE assertions. Add only this maintenance assertion to the already located `MaintainDistance` object:

  ```java
  assertEquals(0.2, maintainMotion.get("MoveThreshold").getAsDouble());
  ```

- [ ] **Step 2: Run the maintained test and verify the expected failure**

  Run:

  ```bash
  ./mvnw -Dtest=NordicDrakeTamedCombatAssetTest test
  ```

  Expected: FAIL because the production component still declares `[0.5, 5]` and six grounded children. Compilation or JSON parsing errors are not the expected failure.

- [ ] **Step 3: Change grounded spacing and add continuous target tracking**

  Change only the component-local spacing parameter:

  ```json
  "DesiredAttackDistanceRange": { "Value": [3.5, 5] }
  ```

  Insert this as the first direct instruction inside `.GroundCombat`:

  ```json
  {
    "Continue": true,
    "Sensor": {
      "Type": "And",
      "Sensors": [
        { "Type": "State", "State": ".GroundCombat" },
        { "Type": "Target", "TargetSlot": "LockedTarget" },
        { "Type": "Flag", "Name": "AirborneMode", "Set": false },
        { "Type": "MotionController", "MotionController": "Walk" }
      ]
    },
    "HeadMotion": {
      "Type": "Aim",
      "Spread": 0,
      "HitProbability": 1,
      "Deflection": true,
      "RelativeTurnSpeed": { "Compute": "CombatRelativeTurnSpeed" }
    }
  }
  ```

  The branch must remain actionless and continuing: it owns head steering while later siblings independently own body motion and attacks. Add `"MoveThreshold": 0.2` to the existing grounded `MaintainDistance` body motion.

- [ ] **Step 4: Make normal melee the baseline before special selection**

  Move the existing `GroundBasicAttack` instruction, unchanged, so it is immediately after `MaintainDistance` and before the `Random` bite/breath selector. Do not copy it or leave a second basic branch at the end.

  Preserve its sensor gates, `HeadMotion`, attack action, `[0.1, 0.2]` aiming time, and `NordicDrake_Ground_Basic` timer actions. Preserve the special selector and direct bite/breath branches after it. With no `Continue` on the basic branch, a ready basic attack wins; while its `[1.5, 2.5]` timer runs, selection falls through to eligible specials.

- [ ] **Step 5: Run focused verification and inspect the diff**

  ```bash
  ./mvnw -Dtest=NordicDrakeTamedCombatAssetTest,DragonHornLocomotionAssetContractTest test
  git diff --check
  git diff -- Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java
  ```

  Expected: both tests PASS; the diff shows one range change, one tracking branch, one movement threshold, one moved basic branch, and only maintenance edits inside the existing test.

- [ ] **Step 6: Commit grounded combat polish**

  ```bash
  git add -- Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java
  git commit -m "Fix: restore grounded Nordic Drake combat cadence"
  ```

---

### Task 2: Add Autonomous Fireball and Flame-Breath Audio

**Files:**

- Modify: `Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Fire_Ball.json:4-39`
- Modify: `Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Flame_Breath.json:14-43`
- Modify: `Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Flying_Flame_Breath.json:14-43`
- Verify only: `src/test/java/com/alechilles/hydragon/config/NordicDrakeInteractionAssetTest.java`

**Interfaces:**

- Consumes: the three existing mounted sound-event IDs and normal NPC interaction timing.
- Produces: charge/launch cues on every normal fireball invocation and a charge-start roar on both autonomous breath variants.

- [ ] **Step 1: Add the two fireball cues at their mounted-equivalent moments**

  Add the roar to the existing normal fireball `ChargeShoot` effects without changing its `0.8` runtime:

  ```json
  "Effects": {
    "ItemPlayerAnimationsId": "NordicDrake_Default",
    "ItemAnimationId": "ChargeShoot",
    "WorldSoundEventId": "SFX_HyDragon_NordicDrake_Avatar_Fireball_Roar"
  }
  ```

  Immediately after the `TameworkLaunchProjectile` step and before `FinishShoot`, add:

  ```json
  {
    "Type": "Simple",
    "Effects": {
      "WorldSoundEventId": "SFX_HyDragon_NordicDrake_Avatar_Fireball_Launch"
    },
    "RunTime": 0
  }
  ```

  Do not change projectile ID, target slot, offsets, trajectory, damage effect, animations, or timing. The `_Volley_2`, `_Volley_3`, and `_Volley_4` roots already repeat `NordicDrake_Fire_Ball`, so do not edit the volley roots.

- [ ] **Step 2: Add the breath roar to grounded and flying NPC interactions**

  In both `NordicDrake_Flame_Breath.json` and `NordicDrake_Flying_Flame_Breath.json`, add this field to the existing `ChargeShoot` effects that also own the jaw `Flamethrower` particle:

  ```json
  "WorldSoundEventId": "SFX_HyDragon_NordicDrake_Avatar_Flame_Breath_Roar"
  ```

  Preserve particle offsets/scales, `FlamethrowerSource`, selectors, damage pulses, animation clearing, and all runtimes.

- [ ] **Step 3: Run existing validation without adding test cases**

  ```bash
  rg -n "WorldSoundEventId" Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Fire_Ball.json Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Flame_Breath.json Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Flying_Flame_Breath.json
  ./mvnw -Dtest=NordicDrakeInteractionAssetTest test
  python scripts/validate_assets.py
  git diff --check
  ```

  Expected: the fireball has exactly two matching sound fields, each breath file has exactly one roar field, the existing test passes, and asset validation reports success. Do not modify `NordicDrakeInteractionAssetTest`.

- [ ] **Step 4: Commit autonomous ability audio**

  ```bash
  git add -- Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Fire_Ball.json Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Flame_Breath.json Server/Item/Interactions/NPCs/HyDragon/NordicDrake/NordicDrake_Flying_Flame_Breath.json
  git commit -m "Feat: add Nordic Drake autonomous ability audio"
  ```

---

### Task 3: Review, Validate, Build, and Install the Exact Commit

**Files:**

- Verify: all files changed in Tasks 1-2
- Generate ignored evidence: `.asset-tools/reports/nordic-tamed-combat-polish-*.json`
- Generate in a detached worktree: `target/HyDragon v1.0.0.jar`
- Install: `C:/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar`
- Install: `C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods/HyDragon v1.0.0.jar`

**Interfaces:**

- Consumes: both committed implementation tasks and HyDragon's locked release profile/build configuration.
- Produces: independent review evidence, passing focused/full validation, and byte-identical local/client-server JARs built from one clean commit.

- [ ] **Step 1: Run exact-profile affected-scope validation**

  From `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HytaleNpcAssetTools`, run:

  ```bash
  hytale-assets profile check --project-profile /c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.hytale-npc-assets.json --json
  hytale-assets author inspect --project-profile /c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.hytale-npc-assets.json --asset Component_HyDragon_Instruction_NordicDrake_Tamed_Combat --view both --provenance compact --references both --include-advisories actionable --format json --out /c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.asset-tools/reports/nordic-tamed-combat-polish-component.json
  hytale-assets author check --project-profile /c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.hytale-npc-assets.json --changed --base 337c7aa --scope affected --format json --pretty --out /c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.asset-tools/reports/nordic-tamed-combat-polish-check.json
  ```

  Require profile status `ready`, version `0.5.7`, channel `release`, and identity `8f917b67e62db471460d6e72d9c512e290c313611493f67a483b28d818ebc65d`. Review added, removed, and unchanged findings. Record known descriptor/component-classification tool limitations separately; they do not override successful repository validation, but any new invalid field, missing reference, or affected consumer is a blocker.

- [ ] **Step 2: Run full repository verification and package inspection**

  From HyDragon:

  ```bash
  ./mvnw -Dtest=NordicDrakeTamedCombatAssetTest,NordicDrakeInteractionAssetTest,DragonHornLocomotionAssetContractTest test
  python scripts/validate_assets.py
  ./mvnw verify
  jar tf "target/HyDragon v1.0.0.jar" | grep -E "Component_HyDragon_Instruction_NordicDrake_Tamed_Combat|NordicDrake_(Fire_Ball|Flame_Breath|Flying_Flame_Breath)\.json"
  git diff --check
  git status --short --branch
  ```

  Expected: focused tests, all existing tests, asset validation, and packaging pass; the JAR lists the combat component and all three modified interactions; the implementation branch is clean.

- [ ] **Step 3: Request independent read-only review**

  Use `superpowers:requesting-code-review` with a `reviewer` agent. Ask it to compare the two task commits against the approved design and verify: basic-before-special scheduling, continuous `LockedTarget` head steering, 3.5-5 spacing compatibility with the 5.25 basic range, no target-authority regression, sound placement/timing, volley inheritance, and absence of unrelated changes.

  Resolve every confirmed issue in the main agent, commit only the necessary corrections, and rerun Steps 1-2. Do not install until review and verification are green.

- [ ] **Step 4: Confirm there is no running Hytale server or Maven build**

  ```bash
  jps -lv | grep -Ei "HytaleServer|org.codehaus.plexus.classworlds.launcher.Launcher" && exit 1 || true
  ```

  Expected: no matches. Do not start a game or server process as part of installation.

- [ ] **Step 5: Build and install from one exact committed revision**

  Run from the main HyDragon repository. The guarded temporary worktree ensures uncommitted files cannot enter the installed JAR and is removed on success or failure:

  ```bash
  repo_path=/c/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon
  install_commit=$(git -C "$repo_path" rev-parse HEAD^{commit})
  install_dir=$(mktemp -d -p /c/Users/22ale/AppData/Local/Temp hydragon-install.XXXXXX)
  case "$install_dir" in
    /c/Users/22ale/AppData/Local/Temp/hydragon-install.*) ;;
    *) exit 1 ;;
  esac
  cleanup_install_worktree() {
    git -C "$repo_path" worktree remove --force "$install_dir" >/dev/null 2>&1 || true
  }
  trap cleanup_install_worktree EXIT
  git -C "$repo_path" worktree add --detach "$install_dir" "$install_commit"
  (
    cd "$install_dir"
    ./mvnw -Pinstall-plugin package
  )
  sha256sum "$install_dir/target/HyDragon v1.0.0.jar" \
    "/c/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar" \
    "/c/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods/HyDragon v1.0.0.jar"
  cleanup_install_worktree
  trap - EXIT
  ```

  Expected: all three SHA-256 values are identical. Record `install_commit` and the digest. The Maven install profile copies only to the two configured local Hytale mod directories; do not invoke the public release publisher.

- [ ] **Step 6: Confirm cleanup and hand off the live acceptance check**

  ```bash
  git worktree list
  jps -lv | grep -Ei "HytaleServer|org.codehaus.plexus.classworlds.launcher.Launcher" && exit 1 || true
  git status --short --branch
  ```

  Confirm no `hydragon-install.*` worktree or server/Maven process remains. Report the exact commit, artifact digest, validation results, and installed paths. Ask the user to verify in game that grounded combat holds roughly 3.5-5 blocks, visibly tracks the target with its head, regularly uses swipe/stomp between specials, and plays roar/launch cues for ground breath, flying breath, single fireballs, and volleys.

## Final Acceptance Checklist

- [ ] Grounded `DesiredAttackDistanceRange` is `[3.5, 5]` with `MoveThreshold=0.2`.
- [ ] A continuing grounded `Aim` branch tracks only `LockedTarget` while the walk controller is active.
- [ ] The normal swipe/stomp root precedes special selection and retains its original cooldown/range.
- [ ] Bite and breath retain existing balance and run while the basic timer is unavailable.
- [ ] Autonomous fireball charge and launch cues are synchronized and inherited by volleys.
- [ ] Grounded and flying autonomous flame breath play the mounted roar cue at `ChargeShoot`.
- [ ] No new test case, target selector, health phase, damage change, or unrelated role change is introduced.
- [ ] Exact-profile validation, existing focused tests, the full suite, packaging, and independent review pass.
- [ ] Both installed JARs are byte-identical to the exact committed build artifact.
- [ ] Temporary worktrees and build/server processes are absent after installation.
