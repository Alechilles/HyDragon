# Miniwyvern Essence Bond Auras Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make each non-Wild Miniwyvern Essence Bond tree modify only its summoned owner aura, with form-specific wards, enemy-effect upgrades, and Void's cooldown-limited siphon.

**Architecture:** Preserve the current root auras and lifecycle. Add validated form-local upgrade data to Miniwyvern archetype assets, derive one immutable live Aura from purchased talents, and consume that Aura in the existing owner-hit and player-effect systems. Use authored EntityEffects for wards and only small Java damage systems for dynamic marked-target, outgoing-damage, conditional-Ward, and Void-cooldown behavior.

**Tech Stack:** Java 21, Hytale Server ECS and EntityEffects, Tamework public talent state API, JSON asset codecs, JUnit 5, Maven.

## Global Constraints

- Preserve Fire, Ice, Lightning, Nature, Toxic, and Void root aura effect IDs and baseline values.
- Wild has no Essence Bond behavior. Earth is specified, but no Earth role, archetype, or tree is added here.
- No Essence Bond node grants generic Miniwyvern combat, health, or movement statistics.
- Tier 5 requires both tier-4 endpoints; tier 6 requires tier 5.
- Every tooltip is form-specific, includes exact values, and never says `Not implemented`.
- Fire and Toxic never heal. Void heals 0.5% maximum health at most once per 3 seconds, increased to 1% by capstone on that same cooldown.
- Use Git Bash and leave no stale Java/Maven processes.

---

### Task 1: Add validated upgrade data and derive live Aura state

**Files:**
- Modify: `src/main/java/com/alechilles/hydragon/config/MiniwyvernArchetypeConfig.java`
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraRegistry.java`
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityService.java`
- Test: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityServiceTest.java`
- Test: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraRegistryTest.java`

**Interfaces:**
- Consumes: `MiniwyvernAbilityWorld.hasPurchasedTalent(String)` and `OwnerAttackAura`.
- Produces: `MiniwyvernArchetypeConfig.getEssenceBondAura()` and an expanded immutable `MiniwyvernOwnerAuraRegistry.Aura`.

- [ ] **Step 1: Write failing derivation tests**

Add reflection-built Fire and Void configs with upgrade records. Assert root values remain when no upgrades are purchased and capstones derive the approved values:

```java
assertEquals(6.0D, aura.durationSeconds());
assertEquals(0.10D, aura.ownerDamageToAffectedFraction());
assertEquals(0.01D, aura.siphonMaximumHealthFraction());
assertEquals(3_000L, aura.siphonCooldownMs());
```

Also assert duplicate upgrade talent IDs, unknown semantics, non-finite values, and invalid fractions fail `validate()`.

- [ ] **Step 2: Confirm the tests fail**

Run `./mvnw -Dtest=MiniwyvernAbilityServiceTest,MiniwyvernOwnerAuraRegistryTest test`.

Expected: compilation fails because the upgrade data and Aura accessors do not exist.

- [ ] **Step 3: Implement data, validation, and folding**

Add `EssenceBondAura` and an ordered `Upgrade[]` to `MiniwyvernArchetypeConfig`. An upgrade has `TalentId`, optional target effect ID/duration, target outgoing-damage reduction, target damage-taken increase, owner damage-to-affected fraction, Ward effect ID, conditional Ward reduction, speed-burst multiplier/duration, and siphon fraction/cooldown. Validate nonblank unique IDs, finite values, fractions in `[0,1)`, and positive duration/cooldown fields.

Extend `Aura` with:

```java
double targetDamageTakenFraction,
double ownerDamageToAffectedFraction,
String wardEffectId,
double conditionalWardDamageReductionFraction,
double siphonMaximumHealthFraction,
long siphonCooldownMs
```

Fold purchased upgrades in asset order over the root state in `synchronizeOwnerAttackAura`; retain current clear behavior when the root talent is absent or derived state is invalid.

- [ ] **Step 4: Confirm the tests pass**

Run `./mvnw -Dtest=MiniwyvernAbilityServiceTest,MiniwyvernOwnerAuraRegistryTest test`.

Expected: PASS for root-only, capstone, invalid-config, and clear cases.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alechilles/hydragon/config/MiniwyvernArchetypeConfig.java src/main/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraRegistry.java src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityService.java src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityServiceTest.java src/test/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraRegistryTest.java
git commit -m "Feat: derive Miniwyvern Essence Bond auras"
```

### Task 2: Apply target upgrades and marked-target damage bonuses

**Files:**
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraEffectSystem.java`
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernToxicWeaknessDamageSystem.java`
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernVoidExposureDamageSystem.java`
- Create: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAuraMarkedTargetDamageSystem.java`
- Modify: `src/main/java/com/alechilles/hydragon/HyDragonPlugin.java`
- Test: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernToxicWeaknessDamageSystemTest.java`
- Test: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernVoidExposureDamageSystemTest.java`
- Test: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAuraMarkedTargetDamageSystemTest.java`

**Interfaces:**
- Consumes: expanded `Aura` from Task 1 and active target EntityEffect IDs.
- Produces: exact live target outgoing-damage reductions, target damage-taken increases, and owner damage bonuses.

- [ ] **Step 1: Write failing multiplier tests**

Add root/capstone and projectile-fallback tests:

```java
assertEquals(80.0F, MiniwyvernToxicWeaknessDamageSystem.reducedAmount(100.0F, 0.20D));
assertEquals(122.0F, MiniwyvernVoidExposureDamageSystem.increasedAmount(100.0F, 0.22D));
assertEquals(110.0F, MiniwyvernAuraMarkedTargetDamageSystem.increasedOwnerDamage(100.0F, 0.10D));
```

The marked-target test must reject cancelled, healing, blocked, self, non-player, and nonpositive damage.

- [ ] **Step 2: Confirm the tests fail**

Run `./mvnw -Dtest=MiniwyvernToxicWeaknessDamageSystemTest,MiniwyvernVoidExposureDamageSystemTest,MiniwyvernAuraMarkedTargetDamageSystemTest test`.

Expected: FAIL because bond values are fixed at 12% and the marked-target system is absent.

- [ ] **Step 3: Implement live target interactions**

After a target effect is successfully applied, record the Aura's outgoing-damage reduction and expiry in the registry. Toxic reads that live value while preserving its projectile's fixed 10% fallback. Void reads the live damage-taken value while preserving its projectile's fixed 10% fallback.

Create `MiniwyvernAuraMarkedTargetDamageSystem` in Hytale's filter-damage group. It increases player-owner damage only when that owner has a live Aura with `ownerDamageToAffectedFraction > 0` and the target has that Aura's active status. Register it in `HyDragonPlugin.setup()` after Toxic and Void systems.

- [ ] **Step 4: Confirm the tests pass**

Run `./mvnw -Dtest=MiniwyvernToxicWeaknessDamageSystemTest,MiniwyvernVoidExposureDamageSystemTest,MiniwyvernAuraMarkedTargetDamageSystemTest test`.

Expected: PASS for root, intermediate, capstone, projectile fallback, and invalid-hit paths.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraEffectSystem.java src/main/java/com/alechilles/hydragon/abilities/MiniwyvernToxicWeaknessDamageSystem.java src/main/java/com/alechilles/hydragon/abilities/MiniwyvernVoidExposureDamageSystem.java src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAuraMarkedTargetDamageSystem.java src/main/java/com/alechilles/hydragon/HyDragonPlugin.java src/test/java/com/alechilles/hydragon/abilities/MiniwyvernToxicWeaknessDamageSystemTest.java src/test/java/com/alechilles/hydragon/abilities/MiniwyvernVoidExposureDamageSystemTest.java src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAuraMarkedTargetDamageSystemTest.java
git commit -m "Feat: apply Miniwyvern aura target upgrades"
```

### Task 3: Refresh player wards and enforce Void's siphon cooldown

**Files:**
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityWorld.java`
- Modify: `src/main/java/com/alechilles/hydragon/abilities/HytaleMiniwyvernAbilityWorldDispatcher.java`
- Modify: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityService.java`
- Create: `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAuraSiphonDamageSystem.java`
- Modify: `src/main/java/com/alechilles/hydragon/HyDragonPlugin.java`
- Test: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityServiceTest.java`
- Test: `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAuraSiphonDamageSystemTest.java`

**Interfaces:**
- Consumes: `Aura.wardEffectId()` and Aura siphon fields from Task 1.
- Produces: source-keyed owner EntityEffects, cleanup on aura end, and one server-side Void cooldown per owner.

- [ ] **Step 1: Write failing ward/siphon tests**

Extend the fake world to capture owner-effect source keys. Assert a tier change replaces the prior Ward and deactivation removes it. Use an injected clock for the siphon:

```java
assertTrue(system.trySiphon(OWNER, aura, 1_000L));
assertFalse(system.trySiphon(OWNER, aura, 3_999L));
assertTrue(system.trySiphon(OWNER, aura, 4_000L));
```

Assert Fire/Toxic have zero siphon fraction and Void capstone returns `0.01D`.

- [ ] **Step 2: Confirm the tests fail**

Run `./mvnw -Dtest=MiniwyvernAbilityServiceTest,MiniwyvernAuraSiphonDamageSystemTest test`.

Expected: FAIL because Ward refresh and siphon do not exist.

- [ ] **Step 3: Implement Ward lifecycle and siphon**

Add only the minimal source-keyed owner EntityEffect apply/remove capability to `MiniwyvernAbilityWorld` and dispatch it through the existing Hytale EntityEffect pattern. `MiniwyvernAbilityService` refreshes the derived Ward while the root bond is active and removes its source key on aura clear.

Create `MiniwyvernAuraSiphonDamageSystem` in the inspect-damage group. It requires a player source, matching active target status, positive Aura siphon fraction, and `ConcurrentHashMap<UUID, Long>` cooldown success before dispatching the existing percentage-maximum-health owner heal. Clear cooldown state with registry clear and plugin shutdown.

- [ ] **Step 4: Confirm the tests pass**

Run `./mvnw -Dtest=MiniwyvernAbilityServiceTest,MiniwyvernAuraSiphonDamageSystemTest test`.

Expected: PASS for Ward source ownership/cleanup and exact 3-second Void cadence.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityWorld.java src/main/java/com/alechilles/hydragon/abilities/HytaleMiniwyvernAbilityWorldDispatcher.java src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityService.java src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAuraSiphonDamageSystem.java src/main/java/com/alechilles/hydragon/HyDragonPlugin.java src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityServiceTest.java src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAuraSiphonDamageSystemTest.java
git commit -m "Feat: add Miniwyvern ward and Void siphon auras"
```

### Task 4: Author all six current aura trees and assets

**Files:**
- Modify: `Server/HyDragon/MiniwyvernArchetypes/{Fire,Ice,Lightning,Nature,Toxic,Void}.json`
- Create: `Server/Entity/Effects/Status/HyDragon_Miniwyvern_*_Ward.json` and distinct tiered status assets required by the authored data.
- Modify: `Server/Tamework/Talents/HyDragonMiniwyvern{Fire,Ice,Lightning,Nature,Toxic,Void}.json`
- Test: `src/test/java/com/alechilles/hydragon/config/BundledConfigAssetContractTest.java`
- Test: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java`

**Interfaces:**
- Consumes: codec and runtime behavior from Tasks 1-3.
- Produces: complete Form-specific JSON and exact player-facing talent copy.

- [ ] **Step 1: Write failing asset tests**

Require the six archetypes to retain root IDs/durations, contain eight valid upgrades, and reference existing effect assets. Require each tree to retain nine Essence Bond nodes and 52 total points, have no generic effect entries, and make capstone require both tier-4 endpoints:

```java
assertTrue(essenceBondEffects(talents).isEmpty(), form.name());
assertTrue(capstoneRequiresBothTierFourEndpoints(talents), form.name());
```

- [ ] **Step 2: Confirm the asset tests fail**

Run `./mvnw -Dtest=BundledConfigAssetContractTest,MiniwyvernTalentProgressionAssetTest test`.

Expected: FAIL because trees contain generic companion effects and assets have no upgrades.

- [ ] **Step 3: Author exact approved form data**

Populate Fire, Ice, Lightning, Nature, Toxic, and Void values from `docs/superpowers/specs/2026-08-03-miniwyvern-essence-bond-aura-design.md`. Author Wards using documented resistance categories/percentages and leave base root effects unchanged.

For every current form, remove all `Effects` from Essence Bond nodes, replace their names/descriptions with exact form-only aura copy, and impose:

```text
root -> pressure-1 -> pressure-2 -> pressure-3
root -> ward-1 -> ward-2 -> ward-3
pressure-3 + ward-3 -> convergence -> capstone
```

Do not change Combat or Vigor in this task.

- [ ] **Step 4: Confirm the asset tests pass**

Run `./mvnw -Dtest=BundledConfigAssetContractTest,MiniwyvernTalentProgressionAssetTest test`.

Expected: PASS with complete, form-specific, internally consistent Essence Bond data.

- [ ] **Step 5: Commit**

```bash
git add Server/HyDragon/MiniwyvernArchetypes Server/Entity/Effects/Status Server/Tamework/Talents/HyDragonMiniwyvernFire.json Server/Tamework/Talents/HyDragonMiniwyvernIce.json Server/Tamework/Talents/HyDragonMiniwyvernLightning.json Server/Tamework/Talents/HyDragonMiniwyvernNature.json Server/Tamework/Talents/HyDragonMiniwyvernToxic.json Server/Tamework/Talents/HyDragonMiniwyvernVoid.json src/test/java/com/alechilles/hydragon/config/BundledConfigAssetContractTest.java src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java
git commit -m "Feat: author Miniwyvern Essence Bond aura trees"
```

### Task 5: Verify and package

**Files:**
- Modify only a concrete defect exposed by verification in files already named above.

- [ ] **Step 1: Run the full suite**

Run `./mvnw test`.

Expected: PASS and no stale Java/Maven process remains.

- [ ] **Step 2: Build the jar**

Run `./mvnw package -DskipTests`.

Expected: PASS and creates `target/hydragon-*.jar`.

- [ ] **Step 3: Review final state**

Run:

```bash
git diff HEAD~4..HEAD --check
git status --short
```

Expected: no whitespace errors and only intended Essence Bond files changed/committed.

- [ ] **Step 4: Commit a narrow verification repair only if one was required**

If verification required a repair, stage only that repair and run `git commit -m "Fix: correct Essence Bond integration"`. If it did not, make no extra commit.

## Manual smoke checklist

1. Each current elemental form displays only its own concise, numeric aura copy.
2. The root aura appears only when the root bond is purchased.
3. Despawn, talent reset, and form change remove the old Ward and refresh only the current aura.
4. Fire burns without healing; Toxic weakens without healing; Void heals no more than once every 3 seconds.
5. Capstone is unavailable until both branch endpoints and convergence are purchased.
