# Nordic Drake Vanilla Melee Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restore the tamed Nordic Drake's full chained melee repertoire by making its normal chained attack the guaranteed ground-combat fallback, while preserving bite and flame breath as cooldown-driven specials and removing the redundant Java chaining-data provisioner.

**Architecture:** Keep target ownership, defend behavior, movement, head aim, ground/flying selection, and every aerial state unchanged. Within `.GroundCombat`, evaluate the existing weighted/direct special branches before one final vanilla-style `ActionAttack` branch. Let the engine's role activation supply `ChainingInteraction.Data`, and use `AttackPauseRange` on the basic attack instead of a custom timer.

**Tech Stack:** Hytale 0.5.7 NPC role JSON, Java 25, Maven Wrapper, JUnit 5, Gson, HytaleNpcAssetTools.

## Global Constraints

- Work only in `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/nordic-drake-vanilla-fallback` until the reviewed commit is ready to merge.
- Preserve all unrelated worktrees and user changes.
- Do not change attack interaction assets, aerial combat, target selection, chase/positioning distances, or mounted abilities.
- Do not install over a running Hytale client/server or an active Maven build.
- Use exact-profile asset validation for the changed NPC component before merge.

---

### Task 1: Replace the incorrect combat contract with the vanilla fallback contract

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/build/PluginLifecycleContractTest.java`
- Delete after the RED run: `src/test/java/com/alechilles/hydragon/combat/NordicDrakeChainingDataSystemTest.java`

**Step 1: Write the failing asset contract**

Update the grounded-combat assertions to require:

- `GroundBasicAttackPauseRange: [1.5, 2.5]` and no `GroundBasicCooldownRange`.
- Ordered ground branches: tracking, chase, positioning, weighted specials, direct bite, direct breath, chained basic fallback.
- The basic fallback has `Continue: true`, `ActionsBlocking: true`, the existing grounded target/range/LOS/head-aim context, and actions exactly `Timeout`, `Attack`, `Timeout`.
- The basic `Attack` uses `GroundBasicAttack`, `[0.1, 0.2]` aiming time, and `AttackPauseRange.Compute = GroundBasicAttackPauseRange`.
- The basic fallback has no `NordicDrake_Ground_Basic` timer sensors/actions and no active-special flag gates.
- Bite and breath keep their existing timer contracts and weighted/direct availability behavior.

Add a lifecycle/source contract asserting that `HyDragonPlugin.java` does not reference `NordicDrakeChainingDataSystem` and that the production system source file does not exist.

**Step 2: Run the focused tests and verify RED**

Run:

```bash
./mvnw -Dtest=NordicDrakeTamedCombatAssetTest,PluginLifecycleContractTest test
```

Expected: failures identify the old parameter name, old basic-first ordering/timer behavior, and the still-present Java hook. Do not proceed if the failures are unrelated.

### Task 2: Implement the asset-only melee fallback and remove the Java hook

**Files:**
- Modify: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json`
- Modify: `src/main/java/com/alechilles/hydragon/HyDragonPlugin.java`
- Delete: `src/main/java/com/alechilles/hydragon/combat/NordicDrakeChainingDataSystem.java`

**Step 1: Remove the redundant runtime provisioner**

Delete the provisioner class and its test, then remove its import and `registerSystem` call from `HyDragonPlugin`. No replacement Java code is added because Hytale's role activation already ensures the chaining-data component for NPCs.

**Step 2: Change the basic attack parameter**

Rename `GroundBasicCooldownRange` to `GroundBasicAttackPauseRange`, retaining `[1.5, 2.5]`.

**Step 3: Reorder and simplify `.GroundCombat`**

Move the weighted and direct bite/breath branches ahead of the basic branch. Make the final basic branch a vanilla-style blocking fallback with `Continue: true`, preserved grounded context and head aim, and these actions:

```json
[
  { "Type": "Timeout", "Delay": [0.3, 0.3] },
  {
    "Type": "Attack",
    "Attack": { "Compute": "GroundBasicAttack" },
    "AimingTimeRange": [0.1, 0.2],
    "AttackPauseRange": { "Compute": "GroundBasicAttackPauseRange" }
  },
  { "Type": "Timeout", "Delay": [0.4, 0.4] }
]
```

Remove only the basic branch's custom cooldown timer sensor/actions and its two active-special flag gates. Leave special timers and active flags intact.

**Step 4: Run the focused tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=NordicDrakeTamedCombatAssetTest,PluginLifecycleContractTest test
```

Expected: all focused tests pass.

**Step 5: Commit the implementation**

```bash
git add Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json \
  src/main/java/com/alechilles/hydragon/HyDragonPlugin.java \
  src/main/java/com/alechilles/hydragon/combat/NordicDrakeChainingDataSystem.java \
  src/test/java/com/alechilles/hydragon/combat/NordicDrakeChainingDataSystemTest.java \
  src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java \
  src/test/java/com/alechilles/hydragon/build/PluginLifecycleContractTest.java
git commit -m "Fix: restore vanilla Nordic Drake melee fallback"
```

### Task 3: Validate, review, merge, and install the exact result

**Files:**
- Inspect: all files changed by Task 2
- Validate: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json`

**Step 1: Run exact-profile asset validation**

Use `.hytale-npc-assets.json` and HytaleNpcAssetTools to validate the candidate component at `affected` scope. Save the report under `.asset-tools/reports/` and confirm no errors involving branch ordering, sensors, actions, parameter bindings, or the referenced chained attack.

**Step 2: Run full verification**

Run:

```bash
./mvnw test
./mvnw package
git diff --check
git status --short
```

Expected: tests and packaging pass, no whitespace errors, and only intentionally generated ignored reports remain.

**Step 3: Obtain independent review**

Request read-only review of the implementation diff with particular attention to Hytale instruction ordering, `Continue` semantics, special availability, the basic fallback's `AttackPauseRange`, and complete removal of the Java hook. Resolve any material finding and rerun verification.

**Step 4: Merge and verify the exact merged commit**

Merge `fix/nordic-drake-vanilla-fallback` into the main branch without rewriting history. Build/test the exact merged commit in a clean detached worktree before installation.

**Step 5: Install only when Hytale and Maven are stopped**

Check for Hytale client/server and Maven processes. If none are running, execute:

```bash
./mvnw -Pinstall-plugin package
```

from the clean detached merged commit. Verify the installed JAR hashes against the packaged artifact. If Hytale or Maven is running, stop before installation and ask the user to close it; never terminate the user's game automatically.
