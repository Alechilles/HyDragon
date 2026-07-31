# MiniWyvern Stage-One Combat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the complete pre-1.1 MiniWyvern combat system: a reliable default swoop with a six-node melee route, a balanced status-driven projectile attack with a six-node ranged route, and deterministic arbitration between aiming, volleys, swoops, commands, and recovery.

**Architecture:** Keep combat behavior asset-owned. Replace the weighted aerial dive with an internal four-phase swoop state machine, migrate all MiniWyvern shots to modern `ProjectileConfig` hit/miss chains, and resolve the twelve shipped combat talents through explicit highest-milestone branches. Java remains limited to Toxic/Void damage modifiers because their strongest-wins semantics cannot be represented reliably by status JSON alone.

**Tech Stack:** Hytale 0.5.7 NPC/interaction/projectile/entity-effect JSON, Tamework 3.x talent sensors and config assets, Java 25 damage systems, Gson/JUnit 5 contracts, HytaleNpcAssetTools exact `release-0.5.7` profile, Maven Wrapper.

## Global Constraints

- Do not implement the separate `2026-07-30-tamework-any-of-talent-prerequisites-implementation-plan.md` from `C:/Users/22ale/AppData/Roaming/Hytale/My Mod Docs/Planned Features` as part of stage one. It is the documented Tamework prerequisite for the deferred 1.1-era merge/special route; this plan uses only the existing `RequiresTalentIds` contract.
- The production MiniWyvern tree contains exactly six projectile nodes and six melee nodes. `DraconicAssault`, `AssaultUtility`, `AssaultMastery`, and `DraconicApex` are absent from production assets for the foreseeable future; they are not disabled or represented by empty effects.
- `ProjectileForce` and `ProjectileImpact` are removed without aliases or migration. The tree has not been released.
- Preserve the Bond and Vigor branches and every owner-aura value. A representation change to Void exposure may retain its exact 12%/6-second Bond behavior but may not change its observable potency or duration.
- Preserve unrelated user-owned changes. At planning time these include modified `Fly.blockyanim`, `Walk.blockyanim`, `Wyvern_Mini.blockymodel`, `Server/Models/HyDragon/Wyvern_Mini/Wyvern_Mini.json`, and untracked `Bite.blockyanim`/`Shoot.blockyanim`. Never stage or overwrite them unless the user separately transfers ownership.
- Do not modify grounded MiniWyvern bite behavior, Nordic Drakes, Rock Drakes, Hydras, full dragons, hostile MiniWyverns, global vanilla projectile assets, or core projectile physics.
- Swoop damage is exactly 16 / 20 / 24 / 28 physical with no random modifier, knockback, force, impact, launch, stun, or invulnerability.
- Elemental projectile single-shot benchmarks are 8 / 12 / 16; selectable single shots use 8 and 12. Wild benchmarks are 10 / 15 / 20; selectable single shots use 10 and 15. Pattern replaces the would-be Apex single shot with elemental 10+10 or Wild 12+12.
- Only the first pattern shot is status-enabled. The second fires exactly 0.30 seconds later and never applies a status, even when the first misses.
- Base / Intermediate / Apex modern-config ballistics are normalized to `{LaunchForce, TerminalVelocityAir, Gravity, SpawnTimeout}` values `{28,32,6,4}`, `{34,40,4,5}`, and `{40,48,3,6}`. Damage exists only in the hit interaction; no legacy projectile damage/profile participates. Pattern uses Intermediate; Mastery uses Apex.
- Hytale 0.5.7 core physics ignores the creator but cannot make other allies pass-through. An allied collision consumes the projectile harmlessly because cancelled `DamageEntity` takes its `Failed` path and never runs the status-bearing `Next` chain.
- Lock all authored assets to `.hytale-npc-assets.json` profile `release-0.5.7`, expected source commit `dd07e6a837aaf6378e82ff81d6f520f913624c08`; use ignored `.asset-tools/reports` for generated evidence.
- Use Git Bash, create an isolated worktree for execution, commit after every task, stage only files listed by that task, and leave no Maven, asset-tool, or Hytale server process running.

---

### Task 1: Replace the unreleased Combat talent graph

**Files:**
- Modify: `Server/Tamework/Talents/HyDragonMiniwyvern.json`
- Modify: `Server/Languages/en-US/server.lang`
- Modify: `Server/Languages/de-DE/server.lang`
- Modify: `Server/Languages/es-ES/server.lang`
- Modify: `Server/Languages/fr-FR/server.lang`
- Modify: `Server/Languages/pt-BR/server.lang`
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java`

**Interfaces:**
- Consumes: existing Tamework `RequiresTalentIds` all-of semantics and `TameworkHasTalent` sensors.
- Produces: the twelve shipped marker effect keys used by later asset tasks.

- [ ] **Step 1: Rewrite the failing graph contract**

Replace the old Combat expectations with this exact map:

```java
expected.put("DraconicProjectile", talent(combat, 1, 3, 1));
expected.put("ProjectileRange", talent(combat, 2, 5, 1, "DraconicProjectile"));
expected.put("ProjectileCadence", talent(combat, 2, 5, 1, "DraconicProjectile"));
expected.put("ProjectileGuidance", talent(combat, 3, 9, 2, "ProjectileRange"));
expected.put("ProjectilePattern", talent(combat, 3, 11, 2, "ProjectileCadence"));
expected.put("ProjectileMastery", talent(combat, 4, 14, 3,
        "ProjectileGuidance", "ProjectilePattern"));
expected.put("SwoopFerocity", talent(combat, 1, 3, 1));
expected.put("SwoopCadence", talent(combat, 2, 5, 1, "SwoopFerocity"));
expected.put("SwoopPrecision", talent(combat, 2, 5, 1, "SwoopFerocity"));
expected.put("RelentlessSwoop", talent(combat, 3, 9, 2, "SwoopCadence"));
expected.put("RendingDive", talent(combat, 3, 11, 2, "SwoopPrecision"));
expected.put("SwoopMastery", talent(combat, 4, 14, 3,
        "RelentlessSwoop", "RendingDive"));
```

Assert Combat costs 20, all branches cost 52 total, every effect key equals its talent ID with multiplier `1.0`, and none of these IDs exists:

```java
Set.of("ProjectileForce", "ProjectileImpact", "DraconicAssault",
        "AssaultUtility", "AssaultMastery", "DraconicApex")
```

Update the locale-key count from 63 to 65 and add an assertion that no shipped talent uses `RequiresAnyTalentIds` in this stage.

- [ ] **Step 2: Run the graph tests and verify the red state**

```bash
./mvnw -Dtest=MiniwyvernTalentProgressionAssetTest,MiniwyvernTalentAssetWiringTest test
```

Expected: failures enumerate the old Force/Impact/special nodes and missing projectile/melee mastery nodes.

- [ ] **Step 3: Replace only the Combat entries in the asset**

Author the twelve nodes with the exact IDs, tiers, levels, costs, prerequisites, and marker effects from Step 1. Keep the existing branch localization key and leave Bond/Vigor JSON byte-for-byte unchanged except for formatting required at Combat boundaries.

The `ProjectileMastery` and `SwoopMastery` definitions use ordinary all-of arrays:

```json
"RequiresTalentIds": ["ProjectileGuidance", "ProjectilePattern"]
```

```json
"RequiresTalentIds": ["RelentlessSwoop", "RendingDive"]
```

Do not add a merge node or `RequiresAnyTalentIds` to the HyDragon config.

- [ ] **Step 4: Add localized player-facing names and descriptions**

Add name/description pairs for `ProjectileMastery`, `SwoopFerocity`, `SwoopCadence`, `SwoopPrecision`, `RelentlessSwoop`, `RendingDive`, and `SwoopMastery` to all five locale files. English descriptions must state the observable upgrade, including 20/24/28 swoop damage and cooldown bands where applicable. Remove shipped Force/Impact/special-node strings so 1.0 language files describe only purchasable nodes.

- [ ] **Step 5: Run focused tests and commit**

```bash
./mvnw -Dtest=MiniwyvernTalentProgressionAssetTest,MiniwyvernTalentAssetWiringTest test
git diff --check
git add -- Server/Tamework/Talents/HyDragonMiniwyvern.json \
  Server/Languages/{en-US,de-DE,es-ES,fr-FR,pt-BR}/server.lang \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java
git commit -m "Feat: add MiniWyvern projectile and swoop routes"
```

---

### Task 2: Implement the reliable swoop state machine and damage profiles

**Files:**
- Create: `src/test/java/com/alechilles/hydragon/config/MiniwyvernSwoopAssetContractTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Wyvern_Mini_Swoop_Bite.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Wyvern_Mini_Swoop_Bite_Ferocity.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Wyvern_Mini_Swoop_Bite_Rending.json`
- Create: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Wyvern_Mini_Swoop_Bite_Mastery.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite_Ferocity.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite_Rending.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite_Mastery.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite_Damage.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite_Damage_Ferocity.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite_Damage_Rending.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite_Damage_Mastery.json`

**Interfaces:**
- Consumes: six melee marker talents from Task 1, existing `LockedTarget`, owner/leash sensors, Fly controller, and projectile aim flag.
- Produces: `Miniwyvern_Swoop_Pending`, `Miniwyvern_Swooping`, `Miniwyvern_Swoop_Strike_Committed`, four damage roots, and `.Combat/.Swoop/.Recovery` phases.

- [ ] **Step 1: Add a red structural contract for the complete cycle**

The new test must parse the component and assert exact names/ranges:

```java
assertTimerRange(component, "Miniwyvern_Swoop_Cooldown", "SwoopMastery", 18, 24);
assertTimerRange(component, "Miniwyvern_Swoop_Cooldown", "RelentlessSwoop", 20, 26);
assertTimerRange(component, "Miniwyvern_Swoop_Cooldown", "SwoopCadence", 22, 30);
assertTimerRange(component, "Miniwyvern_Swoop_Cooldown", null, 25, 35);
assertTimerRange(component, "Miniwyvern_Swoop_Approach", null, 6, 6);
assertOverrideAltitude(component, ".Swoop", 0, 2);
assertTrue(hasFlag(component, "Miniwyvern_Swoop_Pending"));
assertTrue(hasFlag(component, "Miniwyvern_Swooping"));
assertTrue(hasFlag(component, "Miniwyvern_Swoop_Strike_Committed"));
assertFalse(component.toString().contains("\"Type\":\"Random\""));
```

Assert the attack gate sets the strike latch before its single `Attack`, transitions to `.Recovery` afterward, and every cancellation path clears all three flags and stops approach/cooldown timers. Assert `SwoopPrecision` selects relative speed `0.70`; all other profiles use `0.55`.

Parse the effective component instruction array and assert: the enter-combat
initialization starts the selected swoop cooldown when it is not already
running; cooldown expiry sets `Miniwyvern_Swoop_Pending` at a lower array index
than every projectile scheduler; all projectile readiness logic lives in this
same component (none remains as a top-level template scheduler); entering
`.Recovery` starts a `[2,4]` recovery timer without clearing
`Miniwyvern_Swooping`; and only recovery completion clears the swooping/strike
latches and returns to `.Combat`.

Parse all four damage assets and assert exact physical base damage 16/20/24/28, `RandomPercentageModifier: 0`, and absence of `Knockback`.

- [ ] **Step 2: Run the focused test and verify it fails**

```bash
./mvnw -Dtest=MiniwyvernSwoopAssetContractTest,DragonHornLocomotionAssetContractTest test
```

Expected: missing dedicated assets/flags/timers and the existing weighted Random dive is reported.

- [ ] **Step 3: Author four isolated swoop attacks**

Each root has `Tags.Attack = ["Melee"]` and points to its matching animation/selector interaction. Copy the ordinary bite selector geometry and animation ID, but replace its damage interaction with the matching swoop damage asset. Each damage asset follows this shape with only `BaseDamage.Physical` changing:

```json
{
  "Parent": "DamageEntityParent",
  "DamageCalculator": {
    "BaseDamage": { "Physical": 16 },
    "RandomPercentageModifier": 0
  },
  "DamageEffects": {
    "WorldSoundEventId": "SFX_Unarmed_Impact",
    "LocalSoundEventId": "SFX_Unarmed_Impact",
    "WorldParticles": [{ "SystemId": "Impact_Sword_Basic" }]
  }
}
```

Do not edit `Wyvern_Mini_Bite*` or the user-owned Bite animation file.

- [ ] **Step 4: Replace the weighted dive with the timer phases**

Add component parameters for all four roots and these exact values:

```json
"SwoopAttack": { "Value": "Root_NPC_Wyvern_Mini_Swoop_Bite" },
"SwoopAttackFerocity": { "Value": "Root_NPC_Wyvern_Mini_Swoop_Bite_Ferocity" },
"SwoopAttackRending": { "Value": "Root_NPC_Wyvern_Mini_Swoop_Bite_Rending" },
"SwoopAttackMastery": { "Value": "Root_NPC_Wyvern_Mini_Swoop_Bite_Mastery" },
"SwoopAltitudeRange": { "Value": [0, 2] },
"SwoopApproachTimeout": { "Value": [6, 6] }
```

Remove `LoiterWeight`, `DiveWeight`, and the Random dive instruction. Preserve the existing irregular `TameworkFlyingOrbit` as `.Combat` body motion. Move the template's projectile aim/cooldown/readiness schedulers into this same component, after the swoop cooldown-expiry instruction, so a shared-ready tick always sets pending before any shot can claim it. Pending waits while aiming/volley-active, swoop claim sets `Miniwyvern_Swooping`, and `.Swoop` refreshes:

```json
"Actions": [{
  "Type": "OverrideAltitude",
  "DesiredAltitudeRange": { "Compute": "SwoopAltitudeRange" }
}],
"BodyMotion": {
  "Type": "Seek", "RelativeSpeed": 0.55,
  "SlowDownDistance": 4, "StopDistance": 2.2
}
```

Use highest-owned selection order `SwoopMastery`, `RendingDive`, `SwoopFerocity`, default for attack roots and `SwoopMastery`, `RelentlessSwoop`, `SwoopCadence`, default for cooldowns. On first entry to valid `.Combat`, start the selected cooldown if it is neither running nor expired. Strike completion or six-second timeout restarts that cooldown immediately, enters `.Recovery`, starts a `[2,4]` recovery timer, and leaves `Miniwyvern_Swooping` true. Recovery completion alone clears `Miniwyvern_Swooping` and `Miniwyvern_Swoop_Strike_Committed` and returns to `.Combat`; invalid combat stops all timers and clears every latch.

- [ ] **Step 5: Wire only the tamed aerial template**

Pass the four Swoop attack roots, the existing projectile parameters, and existing backoff ranges into the component reference. Remove the migrated projectile schedulers from the template. Add top-level cancellation for leaving outer `Defend`, losing `LockedTarget`, disabling `AirborneMode`, or leaving `Fly`; clear all swoop flags/timers and reset internal instructions. Leave ground-mode `Attack = Root_NPC_Wyvern_Mini_Bite` unchanged.

- [ ] **Step 6: Run tests, inspect isolation, and commit**

```bash
./mvnw -Dtest=MiniwyvernSwoopAssetContractTest,DragonHornLocomotionAssetContractTest,MiniwyvernTalentAssetWiringTest test
git diff --check
git diff --name-only | rg -v "Wyvern_Mini|MiniwyvernSwoopAssetContractTest|DragonHornLocomotionAssetContractTest" && exit 1 || true
git add -- Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json \
  Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json \
  Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Wyvern_Mini_Swoop_Bite*.json \
  Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Swoop_Bite*.json \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernSwoopAssetContractTest.java \
  src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java
git commit -m "Feat: add reliable MiniWyvern swoop cycle"
```

---

### Task 3: Implement projectile status assets and strongest-wins modifiers

**Files:**
- Create: `Server/Entity/Effects/Status/HyDragon_Miniwyvern_Lightning_Shock.json`
- Create: `Server/Entity/Effects/Status/HyDragon_Miniwyvern_Nature_Root.json`
- Create: `Server/Entity/Effects/Status/HyDragon_Miniwyvern_Toxic_Projectile_Weakness.json`
- Create: `Server/Entity/Effects/Status/HyDragon_Miniwyvern_Void_Projectile_Exposure.json`
- Modify: `Server/Entity/Effects/Status/HyDragon_Miniwyvern_Void_Exposure.json`
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernToxicWeaknessDamageSystem.java`
- Create: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernVoidExposureDamageSystem.java`
- Modify: `src/main/java/com/alechilles/hydragon/HyDragonPlugin.java`
- Modify: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernToxicWeaknessDamageSystemTest.java`
- Create: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernVoidExposureDamageSystemTest.java`
- Create: `src/test/java/com/alechilles/hydragon/config/MiniwyvernProjectileStatusAssetTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/build/PluginLifecycleContractTest.java`

**Interfaces:**
- Consumes: existing Fire Burn, Ice Slow, Toxic Bond registry, and Void Bond effect.
- Produces: projectile status IDs and exact 0.90/1.10 damage modifiers for Task 4 hit chains.

- [ ] **Step 1: Add red data and modifier tests**

Assert exact effect fields:

```java
assertDuration("HyDragon_Miniwyvern_Lightning_Shock", 0.5);
assertDisabledAbilities("HyDragon_Miniwyvern_Lightning_Shock",
        "Primary", "Secondary", "Ability1", "Ability2", "Ability3");
assertNoMovementDisable("HyDragon_Miniwyvern_Lightning_Shock");
assertDuration("HyDragon_Miniwyvern_Nature_Root", 0.6);
assertHorizontalMovementDisabledOnly("HyDragon_Miniwyvern_Nature_Root");
assertDuration("HyDragon_Miniwyvern_Toxic_Projectile_Weakness", 5.0);
assertDuration("HyDragon_Miniwyvern_Void_Projectile_Exposure", 5.0);
```

Add pure modifier tests:

```java
assertEquals(90.0F, reducedAmount(100.0F, false, true));
assertEquals(88.0F, reducedAmount(100.0F, true, true));
assertEquals(110.0F, increasedAmount(100.0F, false, true));
assertEquals(112.0F, increasedAmount(100.0F, true, true));
assertEquals(100.0F, reducedAmount(100.0F, false, false));
assertEquals(100.0F, increasedAmount(100.0F, false, false));
```

The booleans represent active Bond and projectile effects. Add finite/cancelled/self/environment exclusions at the system-decision level.

- [ ] **Step 2: Run focused tests and verify the red state**

```bash
./mvnw -Dtest=MiniwyvernProjectileStatusAssetTest,MiniwyvernToxicWeaknessDamageSystemTest,MiniwyvernVoidExposureDamageSystemTest,PluginLifecycleContractTest test
```

Expected: missing status assets/system and old single-fraction Toxic helper are reported.

- [ ] **Step 3: Author the four dedicated effects**

Lightning uses ability-only disable and no movement effect:

```json
{
  "ApplicationEffects": {
    "AbilityEffects": {
      "Disabled": ["Primary", "Secondary", "Ability1", "Ability2", "Ability3"]
    },
    "Particles": [{ "SystemId": "Lightning" }]
  },
  "OverlapBehavior": "Overwrite",
  "RemovalBehavior": "Duration",
  "Infinite": false,
  "Debuff": true,
  "Duration": 0.5
}
```

Nature disables only horizontal inputs and leaves jump/crouch/abilities available:

```json
{
  "ApplicationEffects": {
    "MovementEffects": {
      "DisableForward": true,
      "DisableBackward": true,
      "DisableLeft": true,
      "DisableRight": true
    },
    "Particles": [{ "SystemId": "NatureBeam" }]
  },
  "OverlapBehavior": "Overwrite",
  "RemovalBehavior": "Duration",
  "Infinite": false,
  "Debuff": true,
  "Duration": 0.6
}
```

Toxic and Void projectile effects are overwrite marker/VFX effects with durations 5.0 and no built-in damage modifier. Convert the Bond Void effect from built-in `DamageResistance` to the same marker/VFX representation while retaining duration 6.0; Task 3's Java system becomes the single modifier authority and preserves 12% Bond behavior.

- [ ] **Step 4: Extend Toxic and add Void filtering**

Toxic resolves the largest active reduction:

```java
static float reducedAmount(float amount, boolean bondActive, boolean projectileActive) {
    double fraction = bondActive ? 0.12D : projectileActive ? 0.10D : 0.0D;
    return (float) (amount * (1.0D - fraction));
}
```

Keep the registry+effect proof for Bond; additionally inspect the source entity's `EffectControllerComponent` for `HyDragon_Miniwyvern_Toxic_Projectile_Weakness`. Apply only to positive finite entity-caused damage and exclude self-damage.

`MiniwyvernVoidExposureDamageSystem` runs in `DamageModule.get().getFilterDamageGroup()`, inspects the target chunk's controller for Bond/projectile Void effects, selects 12% over 10%, and applies:

```java
static float increasedAmount(float amount, boolean bondActive, boolean projectileActive) {
    double fraction = bondActive ? 0.12D : projectileActive ? 0.10D : 0.0D;
    return (float) (amount * (1.0D + fraction));
}
```

It excludes cancelled, nonfinite, nonpositive, self, block, healing, and unattributed environment events. Register it beside Toxic in `HyDragonPlugin.setup()` and update lifecycle tests.

- [ ] **Step 5: Run tests and commit**

```bash
./mvnw -Dtest=MiniwyvernProjectileStatusAssetTest,MiniwyvernToxicWeaknessDamageSystemTest,MiniwyvernVoidExposureDamageSystemTest,MiniwyvernOwnerAuraRegistryTest,PluginLifecycleContractTest test
git diff --check
git add -- Server/Entity/Effects/Status/HyDragon_Miniwyvern_{Lightning_Shock,Nature_Root,Toxic_Projectile_Weakness,Void_Projectile_Exposure,Void_Exposure}.json \
  src/main/java/com/alechilles/hydragon/abilities/MiniwyvernToxicWeaknessDamageSystem.java \
  src/main/java/com/alechilles/hydragon/abilities/MiniwyvernVoidExposureDamageSystem.java \
  src/main/java/com/alechilles/hydragon/HyDragonPlugin.java \
  src/test/java/com/alechilles/hydragon/abilities/MiniwyvernToxicWeaknessDamageSystemTest.java \
  src/test/java/com/alechilles/hydragon/abilities/MiniwyvernVoidExposureDamageSystemTest.java \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernProjectileStatusAssetTest.java \
  src/test/java/com/alechilles/hydragon/build/PluginLifecycleContractTest.java
git commit -m "Feat: add MiniWyvern projectile debuffs"
```

---

### Task 4: Replace inherited projectiles with modern form-owned configs

**Files:**
- Create: `src/test/java/com/alechilles/hydragon/config/MiniwyvernProjectileBalanceAssetTest.java`
- Modify: `scripts/validate_assets.py`
- Delete: obsolete `Server/Projectiles/HyDragon/Wyvern_Mini/*.json` legacy profiles after all references are removed
- Create: `Server/ProjectileConfigs/HyDragon/Wyvern_Mini/*.json`
- Replace: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_{Fire,Ice,Lightning,Nature,Toxic,Void,Wild}_Projectile*.json`
- Replace: `Server/Item/RootInteractions/NPCs/HyDragon/Wyvern_Mini/Root_NPC_Wyvern_Mini_{Fire,Ice,Lightning,Nature,Toxic,Void,Wild}_Projectile*.json`
- Create: `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/ProjectileHits/*.json`
- Create: `Server/Item/RootInteractions/NPCs/HyDragon/Wyvern_Mini/ProjectileHits/*.json`

**Interfaces:**
- Consumes: Fire/Ice existing effects and Task 3's Lightning/Nature/Toxic/Void status IDs.
- Produces: four launch roots per form: Base, Intermediate, Pattern, and Mastery.

- [ ] **Step 1: Add a red seven-form matrix contract**

Define this expected matrix in the test:

```java
Map<String, FormProfile> forms = Map.of(
    "Fire", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Fire_Burn"),
    "Ice", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Ice_Slow"),
    "Lightning", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Lightning_Shock"),
    "Nature", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Nature_Root"),
    "Toxic", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Toxic_Projectile_Weakness"),
    "Void", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Void_Projectile_Exposure"),
    "Wild", new FormProfile(10, 15, 12, null)
);
```

For each form, assert:

- every launch uses `Type: "Projectile"` or a `Type: "Serial"` containing two modern Projectile entries;
- no MiniWyvern interaction contains `LaunchProjectile` or `ProjectileId`;
- Base/Intermediate/Pattern/Mastery roots resolve;
- Pattern and Mastery contain exactly two launches with one `Simple.RunTime: 0.30` between them;
- first/echo configs share damage but only first hit chains contain an effect;
- physical damage is exact in hit interactions and no config/legacy projectile damage field exists;
- hit `DamageEntity.Next` contains the status/interrupt/presentation/despawn chain, while `Failed` and `Blocked` despawn without status;
- every config has a 4/5/6-second `ProjectileSpawn` timeout matching its profile, ProjectileMiss removes immediately, and a timeout that resumes after an earlier terminal removal can only perform an idempotent no-op without damage/status; and
- no splash, block damage, knockback, force, impact, or adult projectile parent appears.

- [ ] **Step 2: Run the balance contract and verify the red state**

```bash
./mvnw -Dtest=MiniwyvernProjectileBalanceAssetTest test
```

Expected: all 21 deprecated launch interactions and inherited/high-damage profiles fail.

- [ ] **Step 3: Create six self-contained modern ProjectileConfigs per form**

Use this exact naming formula for all seven forms:

```text
Projectile_Config_HyDragon_Miniwyvern_<Form>_Base
Projectile_Config_HyDragon_Miniwyvern_<Form>_Intermediate
Projectile_Config_HyDragon_Miniwyvern_<Form>_Pattern_First
Projectile_Config_HyDragon_Miniwyvern_<Form>_Pattern_Echo
Projectile_Config_HyDragon_Miniwyvern_<Form>_Mastery_First
Projectile_Config_HyDragon_Miniwyvern_<Form>_Mastery_Echo
```

Each config owns inline `Physics`, `Model`, `LaunchForce`, `SpawnOffset`, and
`Interactions`; it does not reference a `Server/Projectiles` asset. Use this
exact profile matrix:

| Profile | LaunchForce | TerminalVelocityAir | Gravity | ProjectileSpawn timeout |
| --- | ---: | ---: | ---: | ---: |
| Base | 28 | 32 | 6 | 4.0 seconds |
| Intermediate | 34 | 40 | 4 | 5.0 seconds |
| Apex | 40 | 48 | 3 | 6.0 seconds |

Base uses Base; Intermediate uses Intermediate; both Pattern configs use
Intermediate; both Mastery configs use Apex. Author physics as
`{"Type":"Standard","Gravity":N,"TerminalVelocityAir":N}` and preserve
only each form's resolved model/trail presentation through its referenced
`ModelAsset`. Use `"SpawnOffset":{"X":0,"Y":0,"Z":1}` instead of legacy
`DepthShot`. Do not inherit adult projectile damage or collision behavior.

Every config's `Interactions.ProjectileSpawn` uses its profile timeout:

```json
{
  "Cooldown": { "Cooldown": 0 },
  "Interactions": [
    { "Type": "Simple", "RunTime": 4.0 },
    { "Type": "RemoveEntity", "Entity": "User" }
  ]
}
```

Use 5.0 for Intermediate/Pattern and 6.0 for Mastery. Map `ProjectileHit` to
the exact form/profile hit root and `ProjectileMiss` to a harmless despawn
root. Do not claim that hit/miss rules interrupt `ProjectileSpawn`: Hytale 0.5.7
queues projectile-generated chains without applying interaction interruption
rules. Hit/miss still remove the proxy immediately; the short pending timeout
later reaches `RemoveEntity` against an already-removed proxy and is a guarded
no-op. Tests must prove that delayed completion cannot replay damage, status,
presentation, or removal side effects. Do not use an owner-scoped `Interrupt`,
which could cancel another projectile in the same volley. First configs use
status-enabled hit roots; echo configs use damage-only hit roots. Wild
first/echo may share the same damage-only hit root but retain both config IDs
so the serial matrix stays uniform.

- [ ] **Step 4: Create safe hit chains**

Each damage interaction uses inline `DamageEntity` with the exact base/intermediate/pattern amount. Put the status chain only under `Next`, because Hytale routes cancelled friendly damage through `Failed` and blocked damage through `Blocked`:

```json
{
  "Type": "DamageEntity",
  "DamageCalculator": {
    "BaseDamage": { "Physical": 10 },
    "RandomPercentageModifier": 0
  },
  "Next": {
    "Type": "Serial",
    "Interactions": [
      { "Type": "ApplyEffect", "Entity": "Target", "EffectId": "HyDragon_Miniwyvern_Fire_Burn" },
      { "Type": "RemoveEntity", "Entity": "User" }
    ]
  },
  "Failed": { "Type": "RemoveEntity", "Entity": "User" },
  "Blocked": { "Type": "RemoveEntity", "Entity": "User" }
}
```

Lightning's successful `Next` begins with:

```json
{ "Type": "Interrupt", "Entity": "Target", "ExcludedTag": "Uninterruptable" }
```

then applies Shock and despawns. Wild omits ApplyEffect. Every chain terminates exactly once.

- [ ] **Step 5: Replace launch/root assets with four roots per form**

Use suffixes `_Base`, `_Intermediate`, `_Pattern`, and `_Mastery`. Base/Intermediate child interactions are a single modern Projectile interaction. Pattern/Mastery child interactions use:

```json
{
  "Type": "Serial",
  "Interactions": [
    { "Type": "Projectile", "Config": "Projectile_Config_HyDragon_Miniwyvern_Fire_Pattern_First" },
    { "Type": "Simple", "RunTime": 0.30 },
    { "Type": "Projectile", "Config": "Projectile_Config_HyDragon_Miniwyvern_Fire_Pattern_Echo" }
  ]
}
```

Mastery substitutes the two Mastery config IDs. All roots retain `Tags.Attack = ["Ranged"]`.

- [ ] **Step 6: Extend static asset validation and commit**

Update `scripts/validate_assets.py` to enumerate all seven forms/four roots/six configs, reject deprecated launch types in the MiniWyvern namespace, validate damage/effect/despawn chains, and ensure no role points to the old Apex root naming.

```bash
./mvnw -Dtest=MiniwyvernProjectileBalanceAssetTest test
python scripts/validate_assets.py
git diff --check
git add -- Server/Projectiles/HyDragon/Wyvern_Mini \
  Server/ProjectileConfigs/HyDragon/Wyvern_Mini \
  Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini \
  Server/Item/RootInteractions/NPCs/HyDragon/Wyvern_Mini \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernProjectileBalanceAssetTest.java \
  scripts/validate_assets.py
git commit -m "Feat: rebalance MiniWyvern projectiles"
```

Before committing, inspect `git diff --cached --name-only` and unstage any user-owned animation/model path.

---

### Task 5: Resolve projectile milestones and arbitrate volleys against swoops

**Files:**
- Modify: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_Fire.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_Ice.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_Lightning.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_Nature.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_Toxic.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_Void.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_Wild.json`
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernSwoopAssetContractTest.java`

**Interfaces:**
- Consumes: four form roots from Task 4 and swoop flags/state from Task 2.
- Produces: exact talent composition, guidance aiming, volley latch, cancellation, and starvation-free swoop priority.

- [ ] **Step 1: Replace old mutually exclusive branch tests with the six milestones**

Set `COMBAT_TALENTS` to only:

```java
List.of("DraconicProjectile", "ProjectileRange", "ProjectileCadence",
        "ProjectileGuidance", "ProjectilePattern", "ProjectileMastery")
```

Add table-driven combination cases rather than treating ownership as one linear chain:

| Purchased set | Root | Aim | Cooldown | Volley |
| --- | --- | --- | --- | --- |
| DraconicProjectile | Base | [0.4,0.7] | [5,7] | false |
| +Range | Intermediate | [0.4,0.7] | [5,7] | false |
| +Cadence | Intermediate | [0.4,0.7] | [4,6] | false |
| +Range+Guidance | Intermediate | [0.55,0.85] | [5,7] | false |
| +Cadence+Pattern | Pattern | [0.4,0.7] | [4,6] | true |
| +Guidance+Pattern | Pattern | [0.55,0.85] | [4,6] | true |
| ProjectileMastery prerequisites + Mastery | Mastery | [0.55,0.85] | [3,5] | true |

Assert every scheduler and executable branch rejects `Miniwyvern_Swoop_Pending` and `Miniwyvern_Swooping`. Assert the volley latch is set before Pattern/Mastery Attack, remains set while Attack blocks through the serial second launch, and clears afterward.
Assert every projectile scheduler remains in the aerial component after the
swoop pending setter, and that the template contains no independent projectile
readiness scheduler.

- [ ] **Step 2: Run the focused contract and verify the red state**

```bash
./mvnw -Dtest=MiniwyvernTalentAssetWiringTest,MiniwyvernSwoopAssetContractTest,DragonHornLocomotionAssetContractTest test
```

Expected: old Force/Impact/special gates, single-shot Pattern, old Apex parameter, and missing arbitration are reported.

- [ ] **Step 3: Replace template parameters and seven role bindings**

Template parameters become:

```text
TalentProjectileBase
TalentProjectileIntermediate
TalentProjectilePattern
TalentProjectileMastery
```

Remove `TalentProjectileApex`. Each role binds the four exact form roots from Task 4. Do not add form-specific talent IDs.

- [ ] **Step 4: Collapse projectile execution to explicit profile branches**

Remove every stale Force/Impact/special branch. Keep every branch in the
aerial component's centralized readiness scope created by Task 2; do not
reintroduce a top-level template scheduler. Use highest-observable behavior
selection:

1. `ProjectileMastery` → Mastery root, guided aim, `[3,5]`.
2. `ProjectilePattern` → Pattern root, `[4,6]`, guided only when Guidance is also owned.
3. `ProjectileGuidance` → Intermediate root, guided aim, cadence `[4,6]` only if Cadence is also owned, otherwise `[5,7]`.
4. `ProjectileCadence` → Intermediate root, base aim, `[4,6]`.
5. `ProjectileRange` → Intermediate root, base aim, `[5,7]`.
6. `DraconicProjectile` → Base root, base aim, `[5,7]`.

Guidance scheduler starts `Miniwyvern_Projectile_Aim` with `[0.55,0.85]`; all other schedulers use `[0.4,0.7]`. Range never changes cooldown by itself.

- [ ] **Step 5: Add the volley lifecycle and swoop handoff**

Pattern/Mastery action order is exact:

```json
"Actions": [
  { "Type": "SetFlag", "Name": "Miniwyvern_Projectile_Volley_Active", "SetTo": true },
  { "Type": "Attack", "Attack": { "Compute": "TalentProjectilePattern" }, "AimingTimeRange": [0.1, 0.2], "AttackPauseRange": [0, 0] },
  { "Type": "TimerStart", "Name": "Miniwyvern_Projectile_Cooldown", "StartValueRange": [4, 6], "RestartValueRange": [4, 6] },
  { "Type": "TimerRestart", "Name": "Miniwyvern_Projectile_Cooldown" },
  { "Type": "SetFlag", "Name": "Miniwyvern_Projectile_Volley_Active", "SetTo": false },
  { "Type": "SetFlag", "Name": "Miniwyvern_Projectile_Aiming", "SetTo": false }
]
```

Mastery substitutes its root and `[3,5]`. Swoop pending may be set during the blocking Attack but cannot claim control until both flags clear. Command/target/airborne/controller cancellation clears aiming and volley flags, stops aim timer, and resets blocking instructions so an unfinished serial interaction does not launch the echo.

- [ ] **Step 6: Run combined contracts and commit**

```bash
./mvnw -Dtest=MiniwyvernTalentAssetWiringTest,MiniwyvernTalentProgressionAssetTest,MiniwyvernSwoopAssetContractTest,MiniwyvernProjectileBalanceAssetTest,DragonHornLocomotionAssetContractTest test
git diff --check
git add -- Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json \
  Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json \
  Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_*.json \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernSwoopAssetContractTest.java \
  src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java
git commit -m "Feat: arbitrate MiniWyvern ranged and swoop combat"
```

---

### Task 6: Exact-profile validation, full builds, installation, and runtime acceptance

**Files:**
- Read: every committed file from Tasks 1–5
- Generate (ignored): `.asset-tools/reports/miniwyvern-stage-one-*`
- Generate: HyDragon Maven artifacts
- Install: the verified HyDragon JAR through its `install-plugin` profile

**Interfaces:**
- Consumes: the currently packaged Tamework dependency and complete HyDragon candidate.
- Produces: exact-profile evidence, packaged/installable JARs, and an in-game acceptance record.

- [ ] **Step 1: Check the locked profile before graph work**

Create an ignored local overlay using the current release descriptors/schema paths, then run:

```bash
python -m hytale_npc_assets.cli profile check \
  --project-profile "$PWD/.hytale-npc-assets.json" \
  --project-profile-local "$PWD/.asset-tools/reports/miniwyvern-stage-one.local.json" \
  --mod "$PWD" --json
```

Expected: profile `release-0.5.7`, game `0.5.7`, channel `release`, source commit `dd07e6a837aaf6378e82ff81d6f520f913624c08`, and no identity/plugin mismatch.

- [ ] **Step 2: Validate the affected NPC graph and projectile/status candidates**

Use `author inspect` for `Template_Wyvern_Mini_Flying_Tamed` and `Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend`, then create a candidate envelope from the complete committed diff. Run:

```bash
python -m hytale_npc_assets.cli author validate \
  --project-profile "$PWD/.hytale-npc-assets.json" \
  --project-profile-local "$PWD/.asset-tools/reports/miniwyvern-stage-one.local.json" \
  --mod "$PWD" --patch "$PWD/.asset-tools/reports/miniwyvern-stage-one.candidate.json" \
  --scope affected --simulate \
  --out "$PWD/.asset-tools/reports/miniwyvern-stage-one-validation.json"
```

Expected: no blocker; all seven role inheritors and reverse-reference consumers are included. Review-required findings must be reconciled before continuing rather than waived generically.

- [ ] **Step 3: Generate and run static verification**

```bash
python -m hytale_npc_assets.cli author verify generate \
  --project-profile "$PWD/.hytale-npc-assets.json" \
  --project-profile-local "$PWD/.asset-tools/reports/miniwyvern-stage-one.local.json" \
  --mod "$PWD" --candidate "$PWD/.asset-tools/reports/miniwyvern-stage-one.candidate.json" \
  --out "$PWD/.asset-tools/reports/miniwyvern-stage-one-verification-plan.json"

python -m hytale_npc_assets.cli author verify run \
  --project-profile "$PWD/.hytale-npc-assets.json" \
  --project-profile-local "$PWD/.asset-tools/reports/miniwyvern-stage-one.local.json" \
  --mod "$PWD" --mode static \
  --verification "$PWD/.asset-tools/reports/miniwyvern-stage-one-verification-plan.json" \
  --out "$PWD/.asset-tools/reports/miniwyvern-stage-one-verification-result.json"
```

Expected: all supported static claims pass. Record flight geometry, effect refresh timing, friendly collision cancellation, and animation timing as runtime-only claims.

- [ ] **Step 4: Run full HyDragon verification**

From the HyDragon implementation worktree:

```bash
python scripts/validate_assets.py
./mvnw clean verify
```

Expected: asset validation, unit tests, packaged-JAR checks, and packaged Tamework integration all pass.

- [ ] **Step 5: Inspect and install the verified HyDragon build**

Do not return to the dirty main checkout for packaging. In the same verified
HyDragon implementation worktree used by Step 4, record `git rev-parse HEAD`
and run:

```bash
./mvnw package -DskipTests -Pinstall-plugin
```

Confirm the packaged JAR manifest and recorded hash correspond to that tested
commit. Confirm the destination JAR timestamp/hash changed in both the release
server mods directory and `UserData/Mods`. Inspect JAR contents for the new
HyDragon Void system, twelve-node talent asset, projectile configs, and swoop
component before launching the server. Leave the installed Tamework JAR
untouched because the any-of prerequisite work is deferred with the specials.

- [ ] **Step 6: Run the two-minute runtime matrix**

With one airborne MiniWyvern of each form, verify:

- Follow and Hold never initiate combat; explicit Attack Target participates through Defend;
- each form loiters irregularly at 8–14 blocks and projectiles connect at stationary targets 8, 14, and 20 blocks away;
- direct damage/status/cooldown values match each unlocked profile;
- Pattern/Mastery fire two shots 0.30 seconds apart, only the first applies status, and a pending swoop waits for shot two;
- creator/owner is ignored, other allied collision deals no damage/status and terminates the shot, and hostile collision executes once;
- Fire produces four uninterrupted ticks and refresh creates no bonus/concurrent schedule;
- Ice slows 20% for 4 seconds, Lightning interrupts and locks abilities for 0.5 seconds without stopping movement, Nature locks horizontal movement for 0.6 seconds, Toxic reduces outgoing damage 10% for 5 seconds, Void increases incoming damage 10% for 5 seconds, and Wild applies no status;
- Bond Toxic/Void at 12% wins over a simultaneous 10% projectile version without stacking;
- the default swoop attempts approximately three to four times in two minutes, descends with `[0,2]`, strikes at most once, and returns through 2–4-second recovery;
- swoop profiles deal 16/20/24/28 and use 25–35/22–30/20–26/18–24 cooldowns;
- obstructed approach cancels at six seconds; command, target, ground-mode, or controller changes clear all attack flags/timers; and
- no Nordic Drake, Rock Drake, Hydra, full dragon, grounded bite, owner aura, or non-MiniWyvern locomotion behavior changes.

- [ ] **Step 7: Record evidence and final repository state**

```bash
git show --check --stat HEAD
git status --short
```

Expected: only the pre-existing user-owned animation/model changes remain in the main workspace; the implementation worktree is clean. Record the exact HyDragon commit hash, profile identity, validation result path, static verification result path, Maven summaries, installed JAR hashes, and any runtime-only gaps.
