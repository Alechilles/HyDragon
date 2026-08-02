# Toxic Hydra Aerial Combat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give wild and bonded Toxic Hydras Nordic Drake-style aerial combat using toxic projectiles, bounded poison-spit flybys, player flight abilities, and a usable mount prompt.

**Architecture:** Keep the Nordic Drake flight-state machinery but give Toxic Hydra dedicated roles and a dedicated bonded-combat component, so no Nordic fire/flame interaction can enter a Toxic Hydra path. The wild Toxic role derives from the dragon flight template and selects a Toxic CAE; the bonded Toxic role derives from the dragon tamed template, opts into its own locked-target component, and receives two toxic AvatarFlight abilities. Existing 30-second poison clouds and hazards are reused by every new projectile launch.

**Tech Stack:** Hytale JSON assets, Tamework role/interaction patches, Java 25, JUnit 5, Gson, `scripts/validate_assets.py`.

## Global Constraints

- Preserve the existing Ice Hydra, Nordic Drake, rain barrage, and vanilla poison behavior unchanged.
- A wild aerial volley is the existing fixed three-shot toxic ball choreography; the flyby is one new single projectile, never the 20-shot rain barrage.
- All new Toxic Hydra projectile hazards use `Poison_T1`, a 30.0 second duration, 1.0 second ticks, 5.0 damage per tick, and exclude their source.
- Tamed combat consumes the outer Defend `LockedTarget`; it must retain hard-leash, owner, friendly-target, target-loss, airborne-mode, and motion-controller exits.
- Toxic paths must contain no `NordicDrake` fireball, flame-breath, or flame effect IDs.
- The existing static profile file is not a valid `hytale-assets` project profile, so use repository contract tests and the asset validator; record that limitation in the implementation summary.

---

## File Structure

- Create: `Server/NPC/Balancing/CAE_Hydra_Toxic_Aerial.json` — wild aerial action set with toxic direct volley and poison-spit flyby state transitions.
- Create: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Aerial_Spit.json` — one wild CAE-targeted toxic projectile launch.
- Create: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Aerial_Spit_Tamed.json` — one `LockedTarget`-targeted toxic projectile launch for bonded combat.
- Create: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Avatar_Ball.json` and `Hydra_Toxic_Avatar_Spit.json` — player-safe look-targeted toxic mounted abilities.
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Aerial_Spit.json`, `Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed.json`, `Root_NPC_Hydra_Toxic_Aerial_Spit_Tamed.json`, `Root_NPC_Hydra_Toxic_Avatar_Ball.json`, and `Root_NPC_Hydra_Toxic_Avatar_Spit.json` — bounded AI and player entry points.
- Create: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_ToxicHydra_Tamed_Combat.json` — Toxic Hydra’s isolated locked-target ground/air cycle.
- Modify: `Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json` — move the wild toxic role to the flight-capable template and provide Hydra ground plus toxic aerial parameters.
- Modify: `Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json` — move the bonded toxic role to the tamed flight template and provide Hydra ground plus toxic aerial parameters.
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json` — add a disabled Toxic-Hydra opt-in and its first-priority Defend branch, preserving the Nordic branch and generic fallbacks.
- Modify: `Server/Tamework/AvatarFlight/HyDragonToxicHydra.json` — expose toxic volley and poison-spit mounted abilities.
- Modify: `Server/Tamework/Interactions/HyDragonIntBeast.json` — include `Tamed_Hydra_Toxic` so its existing Mount interaction is offered.
- Modify: `src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java` — replace obsolete inheritance/deferred-ability assumptions with contracts for the new wild, bonded, mounted, and mount-prompt routes.
- Modify: `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java` — preserve the Nordic-only opt-in assertion while allowing a second, Toxic-only branch in the shared tamed template.

### Task 1: Establish the Toxic Hydra aerial contracts

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java`

**Interfaces:**
- Consumes: existing JSON helpers in both tests.
- Produces: failing specifications for `CAE_Hydra_Toxic_Aerial`, the five Toxic roots, the Toxic bonded component, AvatarFlight abilities, and the mount role registration.

- [ ] **Step 1: Write the failing wild and mounted-route tests**

  Add a `toxicAerialCombatUsesToxicRoutesAndNeverTheRainBarrage()` test that asserts:

  ```java
  JsonObject wild = toxicRole("Hydra_Toxic");
  JsonObject modify = wild.getAsJsonObject("Modify");
  assertEquals("Template_HyDragon_Dragon", wild.get("Reference").getAsString());
  assertEquals("CAE_Hydra_Toxic_Aerial", modify.get("_CombatConfig").getAsString());
  assertTrue(modify.get("UseHealthPhaseFlight").getAsBoolean());
  assertEquals("Root_NPC_Hydra_Ice_Ball", modify.get("AirRangedAttack").getAsString());
  assertEquals("Root_NPC_Hydra_Toxic_Aerial_Spit", modify.get("AirBreathAttack").getAsString());
  ```

  Assert `CAE_Hydra_Toxic_Aerial` has `AirRanged` basic attacks rooted at `Root_NPC_Hydra_Ice_Ball`, an `AirFireballVolley` state action, and an `AirBreathRun` state action. Assert every JSON asset reachable by the two actions lacks `Root_NPC_Hydra_RainShoot` and every string in the Toxic aerial role/config/root/interaction files lacks `NordicDrake` and `Flame`.

  Replace the existing deferred-ability assertion with exact mounted abilities:

  ```java
  JsonObject abilities = flight.getAsJsonObject("CombatAbilities");
  assertEquals("Root_NPC_Hydra_Toxic_Avatar_Ball",
      abilities.getAsJsonObject("Ability2").get("Interaction").getAsString());
  assertEquals("Root_NPC_Hydra_Toxic_Avatar_Spit",
      abilities.getAsJsonObject("Ability3").get("Interaction").getAsString());
  ```

  Verify `HyDragonIntBeast.json` lists exactly `Tamed_Hydra` and `Tamed_Hydra_Toxic`, and retains its enabled `Mount` interaction.

- [ ] **Step 2: Write the failing bonded-combat and shared-template tests**

  Add a `tamedToxicHydraConsumesLockedTargetForToxicAerialCombat()` test that verifies the tamed role references `Template_HyDragon_Dragon_Tamed`, has `UseToxicHydraTamedCombat: true`, and its component defaults bind:

  ```java
  assertParameter(parameters, "AirFireballAttack", "Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed");
  assertParameter(parameters, "AirBreathAttack", "Root_NPC_Hydra_Toxic_Aerial_Spit_Tamed");
  assertParameter(parameters, "AirVolleyAttack2", "Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed");
  assertParameter(parameters, "AirVolleyAttack3", "Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed");
  assertParameter(parameters, "AirVolleyAttack4", "Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed");
  ```

  Reuse the Nordic test’s safety assertions against the Toxic component after changing its timer/flag prefixes to `ToxicHydra_`. Verify every entity-target sensor uses `LockedTarget`, and that the five states use `WANDER_TARGET`, `FACE_TARGET`, `APPROACH`, `PASS_THROUGH_TARGET`, and `WANDER_TARGET` respectively.

  In `NordicDrakeTamedCombatAssetTest`, change the Defend child-count expectation from three to four and assert the first two exclusive branches are Nordic then Toxic. Continue asserting only `Tamed_NordicDrake` sets `UseNordicDrakeTamedCombat`, and add the analogous assertion that only `Tamed_Hydra_Toxic` sets `UseToxicHydraTamedCombat`.

- [ ] **Step 3: Run the focused contract test and confirm red**

  Run the isolated test because the normal Maven test-compile currently fails on unrelated stale Tamework patch classes:

  ```bash
  mkdir -p target/isolated-test-classes
  javac --release 25 -cp "C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar;C:/Users/22ale/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar;C:/Users/22ale/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar" -d target/isolated-test-classes src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java
  java -jar "C:/Users/22ale/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar" execute --class-path "target/isolated-test-classes;C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar;C:/Users/22ale/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar" --select-class com.alechilles.hydragon.config.ToxicHydraVariantAssetTest --disable-banner --disable-ansi-colors --details summary
  ```

  Expected: FAIL because the aerial role, component, roots, abilities, and mount-role registration do not exist.

- [ ] **Step 4: Commit the red tests**

  ```bash
  git add src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java
  git commit -m "Test: specify toxic hydra aerial combat"
  ```

### Task 2: Add wild Toxic Hydra aerial combat and bounded attacks

**Files:**
- Create: `Server/NPC/Balancing/CAE_Hydra_Toxic_Aerial.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Aerial_Spit.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Aerial_Spit.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json`

**Interfaces:**
- Consumes: `Template_HyDragon_Dragon` air states and `Hydra_Toxic_Ball` projectile.
- Produces: wild `AirRanged` uses the existing three-shot `Root_NPC_Hydra_Ice_Ball`; `AirBreathPass` uses exactly one new toxic spit root.

- [ ] **Step 1: Convert the Toxic wild role to the flight-capable template**

  Change the role reference and explicitly carry forward all current Hydra gameplay fields that were inherited from `Hydra.json`: appearance, empty weapons/offhand, flock, attitude, detection, drops, movement, combat distances, leash values, health, taming fields, memory fields, and the existing poison melee/ball/rain interaction variables. Add the flight configuration below while retaining the existing ground settings:

  ```json
  "_CombatConfig": "CAE_Hydra_Toxic_Aerial",
  "UseCombatActionEvaluator": true,
  "UseHealthPhaseFlight": true,
  "FlightSpeed": 9,
  "AirRangedAttack": "Root_NPC_Hydra_Ice_Ball",
  "AirBreathAttack": "Root_NPC_Hydra_Toxic_Aerial_Spit",
  "AirRangedAttackDistance": 28,
  "AirRangedAttackMinimumDistance": 6,
  "AirBreathAttackDistance": 13,
  "AirCombatBehaviorDistance": 24,
  "AirCombatAltitudeRange": [8, 16],
  "AirRangedAttackPauseRange": [1, 3],
  "AirBreathAttackAltitudeRange": [2, 4],
  "AirBreathAttackRelativeSpeed": 1.15,
  "AirBreathIngressTimeout": [6, 7],
  "AirBreathPassDistance": 18,
  "AirBreathPassStopDistance": 2,
  "AirBreathPassDuration": [1.85, 2],
  "AirCombatRecoveryDuration": [3, 4]
  ```

- [ ] **Step 2: Create the one-shot wild flyby interaction and root**

  Create `Hydra_Toxic_Aerial_Spit.json` as a single `TameworkLaunchProjectile` using `Hydra_Toxic_Ball`, direct trajectory, `CAETargetSlot`, `Poison_T1` impact radius 3.0, and the existing direct-cloud hazard values:

  ```json
  "LingeringHazard": {
    "Radius": 3.0,
    "DurationSeconds": 30.0,
    "TickIntervalSeconds": 1.0,
    "DamagePerTick": 5.0,
    "ExcludeSource": true,
    "EffectId": "Poison_T1",
    "SourceTypeId": "hydragon.toxic_hydra_hazard"
  }
  ```

  Preserve the toxic ball’s poison particle/sound presentation. Root it as one ranged interaction with `RequireNewClick: false`; do not reference `Root_NPC_Hydra_RainShoot` or `Hydra_Rain_Toxic_Launch`.

- [ ] **Step 3: Create the Toxic aerial CAE**

  Copy `CAE_NordicDrake.json` as the state-machine shape, retain `SelectTarget`, `AirBreathRun`, and `AirFireballVolley`, and rename their descriptions to Toxic Hydra terms. Use this exact air action set:

  ```json
  "AirRanged": {
    "BasicAttacks": {
      "Attacks": ["Root_NPC_Hydra_Ice_Ball"],
      "Randomise": false,
      "MaxRange": 28,
      "Timeout": 2.5,
      "CooldownRange": [1, 3]
    },
    "Actions": ["SelectTarget", "AirBreathRun", "AirFireballVolley"]
  }
  ```

  Copy the Toxic Hydra’s ground `Default` action set and ground actions from `CAE_Hydra_Toxic.json` unchanged, including each poison interaction variable. Set the state actions to `Combat/AirBreathIngress` and `Combat/AirVolley`; use cooldown conditions `[15, 30]` for the flyby and `[12, 20]` for the volley. The flyby’s higher weight remains 6 and the volley remains 3.

- [ ] **Step 4: Run the focused contract test and asset validator**

  Run the isolated JUnit command from Task 1 and:

  ```bash
  python scripts/validate_assets.py
  ```

  Expected: Toxic aerial and no-rain contracts pass; asset validation reports all assets valid.

- [ ] **Step 5: Commit the wild feature**

  ```bash
  git add Server/NPC/Balancing/CAE_Hydra_Toxic_Aerial.json Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Aerial_Spit.json Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Aerial_Spit.json
  git commit -m "Feat: add toxic hydra aerial attacks"
  ```

### Task 3: Add bonded Toxic Hydra combat, mounted abilities, and mount registration

**Files:**
- Create: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_ToxicHydra_Tamed_Combat.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Aerial_Spit_Tamed.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Avatar_Ball.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Avatar_Spit.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Aerial_Spit_Tamed.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Avatar_Ball.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Avatar_Spit.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json`
- Modify: `Server/Tamework/AvatarFlight/HyDragonToxicHydra.json`
- Modify: `Server/Tamework/Interactions/HyDragonIntBeast.json`

**Interfaces:**
- Consumes: `Template_HyDragon_Dragon_Tamed` state names and the 30-second `Hydra_Toxic_Ball` cloud mechanics.
- Produces: an exclusive Toxic Defend takeover that uses `LockedTarget`, a normal mount prompt, and player-safe toxic flight actions.

- [ ] **Step 1: Create the isolated Toxic bonded combat component**

  Copy the Nordic component’s safety shell and five state cycle, replacing all timer and flag names with the `ToxicHydra_` prefix. Keep the first four exits in this order: hard leash/player absence, owner target, friendly target, and lost target. Keep the cancellation condition over `.AirRanged`, `.AirVolley`, `.AirBreathIngress`, `.AirBreathPass`, and `.AirRecovery`.

  Set the attack parameters to these Toxic roots:

  ```json
  "GroundBasicAttack": { "Value": "Root_NPC_Hydra_Attack" },
  "GroundBiteAttack": { "Value": "Root_NPC_Hydra_Bite" },
  "GroundBreathAttack": { "Value": "Root_NPC_Hydra_Ice_Ball" },
  "AirFireballAttack": { "Value": "Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed" },
  "AirVolleyAttack2": { "Value": "Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed" },
  "AirVolleyAttack3": { "Value": "Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed" },
  "AirVolleyAttack4": { "Value": "Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed" },
  "AirBreathAttack": { "Value": "Root_NPC_Hydra_Toxic_Aerial_Spit_Tamed" }
  ```

  Keep Nordic’s movement distances, five orbit modes, cooldown ranges, and bounded-state transitions. Do not add target selection, target mutation, health checks, or combat evaluator operations to this component.

- [ ] **Step 2: Add the role/template opt-in without changing Nordic behavior**

  In `Template_HyDragon_Dragon_Tamed.json`, declare `UseToxicHydraTamedCombat` with default `false`. Insert a second exclusive Defend branch immediately after the existing Nordic branch and before generic fallbacks:

  ```json
  {
    "Enabled": { "Compute": "UseToxicHydraTamedCombat" },
    "Sensor": { "Type": "Target", "TargetSlot": "LockedTarget" },
    "Instructions": [{
      "Reference": "Component_HyDragon_Instruction_ToxicHydra_Tamed_Combat",
      "Modify": {
        "HardLeashDistance": { "Compute": "HardLeashDistance" },
        "AlertedRange": { "Compute": "AlertedRange" },
        "ViewRange": { "Compute": "ViewRange" },
        "ViewSector": { "Compute": "ViewSector" },
        "HearingRange": { "Compute": "HearingRange" },
        "AbsoluteDetectionRange": { "Compute": "AbsoluteDetectionRange" }
      }
    }]
  }
  ```

  Change `Tamed_Hydra_Toxic.json` to reference `Template_HyDragon_Dragon_Tamed`, explicitly copy its current `Tamed_Hydra` base gameplay/mount/taming values, retain all Toxic interaction variable overrides, and set `UseToxicHydraTamedCombat: true`, `MountMode: "TameworkAvatarFlight"`, and `AvatarFlightConfig: "HyDragonToxicHydra"`. Do not set `UseNordicDrakeTamedCombat`.

- [ ] **Step 3: Add bounded tamed and player-safe Toxic roots**

  Create a tamed triple-shot root by copying `Hydra_Ice_Ball.json`’s eleven-step cadence but replacing each launch with a new launch interaction targeted at `LockedTarget`; root it as `Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed`. Create the tamed flyby root around a single equivalent `LockedTarget` launch. Both launches use `Hydra_Toxic_Ball` and the exact 30-second hazard object from Task 2.

  Create player versions that use `LookTargetDistance: 48.0`, no entity target slot, and the same projectile/hazard. The avatar ball remains one projectile (not the AI three-shot sequence) with a `ChargeShoot`/`FinishShoot` presentation; avatar spit is one immediate launch. Root both with `Tags.Attack: ["Ranged"]` and `RequireNewClick: false`.

- [ ] **Step 4: Wire mounted abilities and the mount interaction**

  Add the two ability slots to `HyDragonToxicHydra.json`:

  ```json
  "CombatAbilities": {
    "Ability2": { "Interaction": "Root_NPC_Hydra_Toxic_Avatar_Ball", "Glyph": "POISON" },
    "Ability3": { "Interaction": "Root_NPC_Hydra_Toxic_Avatar_Spit", "Glyph": "BREATH" }
  }
  ```

  Add `"Tamed_Hydra_Toxic"` to `HyDragonIntBeast.json`’s `RoleIds`; do not change the interaction’s mount, pet, or mode-cycle rules. This uses the existing enabled Mount interaction for the newly flight-capable bonded toxic variant.

- [ ] **Step 5: Run contracts, validation, and package build**

  Run the isolated Toxic test command from Task 1, then:

  ```bash
  python scripts/validate_assets.py
  ./mvnw -q -Dmaven.test.skip=true package
  ```

  Expected: focused tests pass, all assets validate, and the package builds. Also run the normal focused Maven test once and document the known unrelated test-compile failure if it still reports missing `com.alechilles.alecstamework.assets.patches` classes.

- [ ] **Step 6: Commit the bonded and mounted feature**

  ```bash
  git add Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_ToxicHydra_Tamed_Combat.json Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Aerial_Spit_Tamed.json Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Avatar_Ball.json Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Avatar_Spit.json Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Aerial_Ball_Tamed.json Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Aerial_Spit_Tamed.json Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Avatar_Ball.json Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_Toxic_Avatar_Spit.json Server/Tamework/AvatarFlight/HyDragonToxicHydra.json Server/Tamework/Interactions/HyDragonIntBeast.json src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java src/test/java/com/alechilles/hydragon/config/NordicDrakeTamedCombatAssetTest.java
  git commit -m "Feat: add toxic hydra aerial mount combat"
  ```

### Task 4: Review the completed asset boundary

**Files:**
- Review: all files changed in Tasks 1–3.

**Interfaces:**
- Consumes: completed contracts and generated package.
- Produces: a verification record that distinguishes passing local checks from the invalid profile and unrelated installed-Tamework test-compile limitation.

- [ ] **Step 1: Inspect the staged diff for boundary violations**

  Run:

  ```bash
  git diff --check HEAD~2..HEAD
  rg -n "NordicDrake|Flame|Root_NPC_Hydra_RainShoot" Server/NPC/Balancing/CAE_Hydra_Toxic_Aerial.json Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_ToxicHydra_Tamed_Combat.json Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Aerial_Spit.json Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Aerial_Spit_Tamed.json Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Avatar_Ball.json Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Avatar_Spit.json
  ```

  Expected: no matches from the `rg` command and no whitespace errors.

- [ ] **Step 2: Record verification results in the handoff**

  Report the passing isolated contract test, asset validator, and package build. Report the exact reason exact-profile validation cannot run (`runtime-profile.json` lacks required `formatVersion: 1`) and, if reproduced, the unrelated normal Maven test-compile failure from the installed Tamework JAR.

