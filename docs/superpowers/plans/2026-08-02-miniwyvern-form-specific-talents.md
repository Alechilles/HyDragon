# Miniwyvern Form-Specific Talent Trees Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every elemental Miniwyvern a truthful role-specific talent tree, leave Wild Bond-free, and safely refund incompatible allocations on role/tree changes.

**Architecture:** Tamework owns a generic reconciliation operation that validates a persisted allocation against the tree selected by the current progression role. HyDragon supplies seven role-scoped talent assets, retains stable combat gate IDs and gives them form-specific copy, and uses existing owner-passive gating plus companion effects for Bond. The Vigor branch uses Tamework's already-consumed health, damage-taken, and movement-speed multipliers.

**Tech Stack:** Java 25, Tamework 3.x source, Hytale JSON assets, JUnit 5, Maven.

## Global Constraints

- Preserve Miniwyvern level, XP, earned point capacity, bonded identity, and role-swap lifecycle state; only an incompatible talent allocation resets.
- An enabled current role with no resolved talent config must preserve an existing allocation rather than clear it.
- Bond and Vigor talent IDs are form-prefixed; stable Combat IDs preserve existing attack gates. Wild has no Bond nodes.
- Preserve the existing projectile and swoop mechanics; change only their form-specific identifiers and player-facing copy.
- Use only the proven Vigor effects `MaxHealthMultiplier`, `DamageTakenMultiplier`, and `MoveSpeedMultiplier`.
- Bond introduces no kill triggers, shields, chains, thresholds, lifesteal, or new projectile behavior.
- Use Git Bash; do not modify or stage unrelated working-tree changes.

---

### Task 1: Reconcile persisted talent allocations in Tamework

**Files:**
- Modify: `../alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwTalentConfig.java`
- Modify: `../alecstamework/src/main/java/com/alechilles/alecstamework/npc/components/TameworkTalentsComponent.java`
- Modify: `../alecstamework/src/main/java/com/alechilles/alecstamework/npc/progression/CompanionTalentService.java`
- Create: `../alecstamework/src/test/java/com/alechilles/alecstamework/npc/progression/CompanionTalentAllocationReconciliationTest.java`

**Interfaces:**
- Consumes: `TwTalentConfig.resolveForRole(String)` and `TameworkTalentsComponent` persisted fields.
- Produces: `CompanionTalentService.reconcileTalentsForRole(Ref<EntityStore>, Store<EntityStore>, String)` returning `TalentReconciliationResult` with `retained`, `reset`, and `noConfig` outcomes.

- [ ] **Step 1: Write failing reconciliation tests**

```java
assertTrue(CompanionTalentService.validateAllocation(config, component).compatible());
assertFalse(CompanionTalentService.validateAllocation(config, staleComponent).compatible());
assertEquals(0, reconciled.component().getSpentPoints());
assertArrayEquals(new String[0], reconciled.component().getPurchasedTalentIds());
assertEquals(config.getId(), reconciled.component().getConfigId());
```

Cover: matching config/revision with a valid prerequisite chain; mismatched config; mismatched allocation revision; removed ID; missing prerequisite; and incorrect recorded point cost.

- [ ] **Step 2: Run the focused test to verify failure**

Run: `cd ../alecstamework && ./gradlew test --tests com.alechilles.alecstamework.npc.progression.CompanionTalentAllocationReconciliationTest`

Expected: compilation failure because the reconciliation API and allocation revision fields do not exist.

- [ ] **Step 3: Persist allocation revisions**

Add `AllocationRevision` to `TwTalentConfig`, defaulting to `1`, and add the matching integer field to `TameworkTalentsComponent`. Include both in their codecs and clone/constructor paths. Make `TwTalentConfig` validate that the revision is positive.

```java
public int getAllocationRevision() { return Math.max(1, allocationRevision); }
public void setAllocationRevision(int value) { allocationRevision = Math.max(1, value); }
```

- [ ] **Step 4: Implement the pure allocation validator and reconciler**

`validateAllocation` must compare config ID and allocation revision, verify every ID through `config.findTalent`, recursively validate prerequisites against the purchased set, and compare `spentPoints` to the sum of `getPointCost()`. `reconcileTalentsForRole` resolves by the supplied role, returns `noConfig` without mutation when no enabled config resolves, and otherwise writes either the unchanged allocation or a cloned component with current identity/revision, zero spent points, and no purchased IDs.

- [ ] **Step 5: Run focused tests and commit Tamework task**

Run: `cd ../alecstamework && ./gradlew test --tests com.alechilles.alecstamework.npc.progression.CompanionTalentAllocationReconciliationTest`

Expected: PASS.

Commit: `git add src/main/java/com/alechilles/alecstamework/config/assets/TwTalentConfig.java src/main/java/com/alechilles/alecstamework/npc/components/TameworkTalentsComponent.java src/main/java/com/alechilles/alecstamework/npc/progression/CompanionTalentService.java src/test/java/com/alechilles/alecstamework/npc/progression/CompanionTalentAllocationReconciliationTest.java && git commit -m "Feat: reconcile incompatible talent allocations"`

### Task 2: Run reconciliation at initialization, reset, and role changes

**Files:**
- Modify: `../alecstamework/src/main/java/com/alechilles/alecstamework/npc/progression/CompanionTalentService.java`
- Modify: `../alecstamework/src/main/java/com/alechilles/alecstamework/npc/actions/TameworkInteractEffects.java`
- Modify: `../alecstamework/src/test/java/com/alechilles/alecstamework/npc/progression/CompanionTalentAllocationReconciliationTest.java`
- Modify: `../alecstamework/src/test/java/com/alechilles/alecstamework/npc/progression/CompanionTalentServiceResetTest.java`

**Interfaces:**
- Consumes: `reconcileTalentsForRole` from Task 1 and `roleChangeEffects.applySetRole` success result.
- Produces: current-role components that are reconciled before purchase, display, modifiers, or role-gated combat can consume them.

- [ ] **Step 1: Extend the failing tests for lifecycle calls**

```java
assertEquals(TalentReconciliationResult.Status.RESET,
        CompanionTalentService.ensureTalentComponent(npcRef, store, "Role_B").status());
assertEquals(TalentReconciliationResult.Status.NO_CONFIG,
        CompanionTalentService.reconcileTalentsForRole(npcRef, store, "Temporary_Mount").status());
```

Verify that a no-config temporary role preserves the old IDs and spend, while an actual configured role swap clears them.

- [ ] **Step 2: Run focused tests to verify failure**

Run: `cd ../alecstamework && ./gradlew test --tests com.alechilles.alecstamework.npc.progression.CompanionTalentAllocationReconciliationTest --tests com.alechilles.alecstamework.npc.progression.CompanionTalentServiceResetTest`

Expected: FAIL because initialization only rewrites `ConfigId` and role change does not reconcile.

- [ ] **Step 3: Reconcile before all talent consumers observe the component**

Make `ensureTalentComponent` delegate to reconciliation with the resolved role. Make `resetTalents` set both current config ID and current allocation revision. After `applySetRole` returns true, resolve the destination role and call reconciliation before returning from `TameworkInteractEffects.applySetRole`.

```java
boolean changed = roleChangeEffects.applySetRole(roleId, effect.getChangeAppearance(), npcRef, role, store);
if (changed) CompanionTalentService.reconcileTalentsForRole(npcRef, store, roleId);
return changed;
```

- [ ] **Step 4: Reapply modifiers after a reset result**

When reconciliation changes an allocation, call `CompanionStatModifierService.applyTraitModifiers(npcRef, store)` so max health and other purchased modifiers are removed immediately.

- [ ] **Step 5: Run focused tests and commit Tamework task**

Run: `cd ../alecstamework && ./gradlew test --tests com.alechilles.alecstamework.npc.progression.CompanionTalentAllocationReconciliationTest --tests com.alechilles.alecstamework.npc.progression.CompanionTalentServiceResetTest`

Expected: PASS.

Commit: `git add src/main/java/com/alechilles/alecstamework/npc/progression/CompanionTalentService.java src/main/java/com/alechilles/alecstamework/npc/actions/TameworkInteractEffects.java src/test/java/com/alechilles/alecstamework/npc/progression/CompanionTalentAllocationReconciliationTest.java src/test/java/com/alechilles/alecstamework/npc/progression/CompanionTalentServiceResetTest.java && git commit -m "Feat: reconcile talents on role changes"`

### Task 3: Split Miniwyvern talent assets and make Vigor real

**Files:**
- Delete: `Server/Tamework/Talents/HyDragonMiniwyvern.json`
- Create: `Server/Tamework/Talents/HyDragonMiniwyvernWild.json`
- Create: `Server/Tamework/Talents/HyDragonMiniwyvernFire.json`
- Create: `Server/Tamework/Talents/HyDragonMiniwyvernIce.json`
- Create: `Server/Tamework/Talents/HyDragonMiniwyvernLightning.json`
- Create: `Server/Tamework/Talents/HyDragonMiniwyvernNature.json`
- Create: `Server/Tamework/Talents/HyDragonMiniwyvernToxic.json`
- Create: `Server/Tamework/Talents/HyDragonMiniwyvernVoid.json`
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java`

**Interfaces:**
- Consumes: the current 31-node topology and Task 1's `AllocationRevision` field.
- Produces: exactly one role-scoped config per Miniwyvern role, each declaring `AllocationRevision: 1`.

- [ ] **Step 1: Replace the asset test with per-role expectations**

Assert seven assets, one matching `RoleIds` entry each, unique IDs across all assets, six elemental configs with 31 nodes/52 points, and Wild with 22 nodes/37 points. Assert every Vigor node has at least one of `MaxHealthMultiplier`, `DamageTakenMultiplier`, or `MoveSpeedMultiplier`.

- [ ] **Step 2: Run the focused test to verify failure**

Run: `./gradlew test --tests com.alechilles.hydragon.config.MiniwyvernTalentProgressionAssetTest`

Expected: FAIL because the repository still contains one shared role list and placeholder Vigor effects.

- [ ] **Step 3: Author seven role-scoped configs**

Use form-prefixed IDs, for example `Miniwyvern_Fire_EmberBond`, `Miniwyvern_Ice_RimeBond`, and `Miniwyvern_Wild_HunterScales`. Preserve all Combat tiers/costs/minimum levels and reword them to state their exact existing behavior. Exclude all Bond nodes from Wild.

For every Vigor tree, preserve the first health chain and make the remaining seven nodes use only these effects:

```json
{ "EffectKey": "DamageTakenMultiplier", "Multiplier": 1.0416666666666667 }
{ "EffectKey": "MoveSpeedMultiplier", "Multiplier": 1.05 }
{ "EffectKey": "MaxHealthMultiplier", "Multiplier": 1.06 }
```

Use the first value to state “take 4% less damage”; combine capstone health and damage reduction in one node. Do not describe recovery or shielding unless the configured effect grants it.

- [ ] **Step 4: Run focused asset tests**

Run: `./gradlew test --tests com.alechilles.hydragon.config.MiniwyvernTalentProgressionAssetTest`

Expected: PASS.

- [ ] **Step 5: Commit HyDragon asset task**

Commit: `git add Server/Tamework/Talents src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java && git commit -m "Feat: split Miniwyvern talent trees by form"`

### Task 4: Re-key form-specific combat gates and localization

**Files:**
- Modify: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json`
- Modify: `Server/Languages/en-US/server.lang`
- Modify: `Server/Languages/de-DE/server.lang`
- Modify: `Server/Languages/es-ES/server.lang`
- Modify: `Server/Languages/fr-FR/server.lang`
- Modify: `Server/Languages/pt-BR/server.lang`
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java`

**Interfaces:**
- Consumes: form-prefixed Combat IDs from Task 3.
- Produces: the existing `TameworkHasTalent` branches selecting form-compatible root interactions and no locale line claiming a purchased Miniwyvern talent does nothing.

- [ ] **Step 1: Add failing static asset assertions**

Assert that each sensor path tests the ID appropriate to the role that supplies its configured projectile root interaction, and assert none of the Miniwyvern talent descriptions contains “Not implemented yet” or its translated equivalent.

- [ ] **Step 2: Run the focused test to verify failure**

Run: `./gradlew test --tests com.alechilles.hydragon.config.MiniwyvernTalentProgressionAssetTest`

Expected: FAIL because the flight instruction still refers to shared combat IDs and locales retain placeholder descriptions.

- [ ] **Step 3: Re-key the asset gates and write exact copy**

Branch the existing sensor selection by current form/role before checking its form-prefixed Combat IDs, preserving the same root interaction values and ordering. Write form-specific names and descriptions that report only existing projectile/swoop parameters and configured status effects. Translate every replacement key or use the established English fallback mechanism; do not leave false placeholder copy.

- [ ] **Step 4: Run focused test and locale scan**

Run: `./gradlew test --tests com.alechilles.hydragon.config.MiniwyvernTalentProgressionAssetTest && rg -n "Not implemented yet|no gameplay effect|Ainda não implementado|Pas encore implémenté|Noch nicht implementiert|Aún no implementado" Server/Languages`

Expected: test PASS and no Miniwyvern talent placeholder matches.

- [ ] **Step 5: Commit HyDragon content task**

Commit: `git add Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json Server/Languages src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java && git commit -m "Feat: localize form specific Miniwyvern talents"`

### Task 5: Make elemental Bond tiers data-driven

**Files:**
- Modify: `src/main/java/com/alechilles/hydragon/config/MiniwyvernArchetypeConfig.java`
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityService.java`
- Modify: `Server/HyDragon/MiniwyvernArchetypes/Fire.json`
- Modify: `Server/HyDragon/MiniwyvernArchetypes/Ice.json`
- Modify: `Server/HyDragon/MiniwyvernArchetypes/Lightning.json`
- Modify: `Server/HyDragon/MiniwyvernArchetypes/Nature.json`
- Modify: `Server/HyDragon/MiniwyvernArchetypes/Toxic.json`
- Modify: `Server/HyDragon/MiniwyvernArchetypes/Void.json`
- Modify: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityServiceTest.java`

**Interfaces:**
- Consumes: elemental form-specific Bond IDs from Task 3.
- Produces: `MiniwyvernArchetypeConfig.BondTier[] getBondTiers()` ordered by rank, each with required talent ID, owner effects/aura values, and optional existing companion progression effects.

- [ ] **Step 1: Write failing tier-resolution tests**

```java
assertEquals("Miniwyvern_Fire_EmberMastery",
        config.resolveHighestBondTier(world::hasPurchasedTalent).requiredTalentId());
assertEquals(6.0, resolved.ownerAuraDurationSeconds());
assertNull(wildConfig.resolveHighestBondTier(world::hasPurchasedTalent));
```

Cover highest purchased tier wins, lower tier effects are removed/replaced, Nature selects its configured tick values, and Wild has no tier.

- [ ] **Step 2: Run the focused test to verify failure**

Run: `./gradlew test --tests com.alechilles.hydragon.abilities.MiniwyvernAbilityServiceTest`

Expected: compilation failure because Bond tier metadata and resolution do not exist.

- [ ] **Step 3: Add tier metadata and select one active tier**

Add a codec-backed `BondTiers` array to archetype config. Each tier names exactly one purchased talent prerequisite and declares the existing owner effect/aura values to apply. Validate tier IDs are nonblank, ranks are strictly increasing, and Wild has an empty tier list. Replace `requiredTalentPurchased` with a highest-tier selection; use that selection for owner aura application, passive effects, and Nature's tick values.

- [ ] **Step 4: Configure only existing elemental effects**

Fire tiers select the existing burn effect with increasing configured damage/duration assets; Ice tiers select slow strength/duration assets; Lightning tiers select speed effects; Nature tiers select regeneration values; Toxic and Void tiers select their existing weakening/exposure effects. Use the existing companion `MaxHealthMultiplier`, `DamageTakenMultiplier`, `DamageDealtMultiplier`, and `MoveSpeedMultiplier` only where the asset tree declares them. Do not add a new event listener or combat scheduler.

- [ ] **Step 5: Run focused tests and commit HyDragon runtime task**

Run: `./gradlew test --tests com.alechilles.hydragon.abilities.MiniwyvernAbilityServiceTest --tests com.alechilles.hydragon.config.MiniwyvernTalentProgressionAssetTest`

Expected: PASS.

Commit: `git add src/main/java/com/alechilles/hydragon/config/MiniwyvernArchetypeConfig.java src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityService.java Server/HyDragon/MiniwyvernArchetypes src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityServiceTest.java && git commit -m "Feat: apply Miniwyvern Bond talent tiers"`

### Task 6: Validate the two-mod integration

**Files:**
- Modify: `docs/testing/bonded-companion-integration-checklist.md`

**Interfaces:**
- Consumes: Tasks 1-5.
- Produces: a repeatable manual checklist for role swap refunds, compatible restoration, Wild behavior, and each elemental owner passive.

- [ ] **Step 1: Add acceptance steps to the checklist**

Include: purchase Fire talents, attune to Ice, confirm all points refund before Ice purchases; dismiss/store/relog with a valid Ice allocation, confirm it remains; temporarily enter a no-tree role, confirm no reset; confirm Wild presents no Bond branch; and verify one owner passive plus one Vigor modifier per elemental form.

- [ ] **Step 2: Run both project test suites**

Run: `cd ../alecstamework && ./gradlew test && cd ../HyDragon && ./gradlew test`

Expected: PASS.

- [ ] **Step 3: Validate changed NPC assets against the pinned profile**

Run: `cd ../HytaleNpcAssetTools && hytale-assets profile check --json`, then validate the changed Miniwyvern role/instruction candidates with `author validate --scope affected`.

Expected: profile identity is valid and affected asset validation has no high-severity failures.

- [ ] **Step 4: Commit checklist and report exact verification results**

Commit: `git add docs/testing/bonded-companion-integration-checklist.md && git commit -m "Docs: test form specific Miniwyvern talents"`

## Plan self-review

Spec coverage: Task 1 implements allocation identity/revision and compatibility validation; Task 2 binds it to role and lifecycle paths; Task 3 provides one tree per form and real Vigor; Task 4 rekeys existing combat and removes false copy; Task 5 implements bounded Bond tiers; Task 6 covers integration and profile validation.

Placeholder scan: this plan defines concrete APIs, files, commands, validation cases, and constraints for every task. It contains no deferred implementation markers.

Type consistency: the reconciliation API returns a typed result consumed only by Task 2. Bond tier metadata is confined to `MiniwyvernArchetypeConfig` and selected by `MiniwyvernAbilityService`; it does not change Tamework's public API.
