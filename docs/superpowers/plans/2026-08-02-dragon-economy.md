# Dragon Economy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved HyDragon crafting, drops, capture, attunement, and revival economy.

**Architecture:** HyDragon owns item recipes, drop tables, role mappings, and contract tests. Alec's Tamework receives three generic mechanics: an active-effect capture bonus, a tranquilized-sleep health cap, and role-specific bonded revive prices; its quoted and escrowed recipe remains the only payment authority.

**Tech Stack:** Java 25, Maven/JUnit 5, Hytale JSON assets, Alec's Tamework 3.x.

## Global Constraints

- Use Hytale `0.5.6` material and crafting contracts.
- Keep capture rolls authoritative in Tamework and consume a Stone only on a resolved attempt.
- Keep every recipe asset-driven except the required generic Tamework runtime mechanics.
- Test every new Tamework behavior before its implementation and run HyDragon's Maven verification after asset changes.

---

### Task 1: Add Tamework capture and revival primitives

**Files:**
- Modify: `alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwCapturePolicyConfig.java`
- Modify: `alecstamework/src/main/java/com/alechilles/alecstamework/items/capturepolicy/SpawnerCaptureChanceService.java`
- Modify: `alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwBondedCompanionRosterConfig.java`
- Modify: `alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwBondedCompanionRosterCodecs.java`
- Modify: `alecstamework/src/main/java/com/alechilles/alecstamework/config/bonded/BondedCompanionRosterRegistry.java`
- Modify: `alecstamework/src/main/java/com/alechilles/alecstamework/companion/bonded/BondedCompanionPolicyResolver.java`
- Modify: `alecstamework/src/main/java/com/alechilles/alecstamework/companion/bonded/BondedCompanionTransitionService.java`
- Test: `alecstamework/src/test/java/com/alechilles/alecstamework/items/capturepolicy/SpawnerCaptureChanceServiceTest.java`
- Test: `alecstamework/src/test/java/com/alechilles/alecstamework/companion/bonded/BondedCompanionTransitionServiceTest.java`

- [ ] Write failing tests for a final-roll +25-point active-effect bonus and for a dead profile using its role-specific revive recipe.
- [ ] Run the two focused tests and confirm the new assertions fail.
- [ ] Add optional `ActiveEffectBonus` capture-policy data (`EffectId`, `FlatChanceBonus`) and calculate it after the ordinary clamped chance, using live completion evidence.
- [ ] Add `RevivePriceByRole` to roster configuration; resolve the profile role before quote, escrow, and transition recipe comparison, with `RevivePrice` as the fallback.
- [ ] Run the focused tests and commit the Tamework change.

### Task 2: Suppress recovery during tranquilized sleep

**Files:**
- Modify: `alecstamework/src/main/java/com/alechilles/alecstamework/npc/systems/CompanionTranquilizerPeakSystem.java` or a focused new NPC system beside it
- Test: `alecstamework/src/test/java/com/alechilles/alecstamework/npc/systems/TranquilizedSleepHealthCapSystemTest.java`

- [ ] Write a failing test proving a sleeping tranquilized NPC cannot regain health above its health at sleep entry, while later damage remains retained.
- [ ] Run the test and confirm it fails because no cap system exists.
- [ ] Implement a component-backed health cap that is active only for `Sleep.Tranquilized`, lowers after damage, and is removed on wake/effect removal.
- [ ] Run the focused test and commit the Tamework change.

### Task 3: Apply HyDragon assets and recipes

**Files:**
- Modify: `Server/Item/Items/Bench/Draconic_Altar.json`
- Modify: `Server/Item/Items/Tool/HyDragon_Dragon_Horn.json`
- Modify: `Server/Item/Items/Ingredient/Draconic_Stone*.json`
- Modify: `Server/Item/Items/Ingredient/Revitalizing_Essence.json`
- Modify: `Server/Item/Items/Ingredient/Wyvern_Egg.json`
- Modify: `Server/Item/Items/Ingredient/Draconic_Essence*.json`
- Create: `Server/Item/ResourceTypes/HyDragon_DraconicEssences.json`
- Create: conversion-recipe assets under `Server/Item/Items/Ingredient/`
- Modify: `Server/Drops/HyDragon/*.json`
- Modify: `Server/Tamework/Items/Spawners/HyDragonDraconicStone.json`
- Modify: `Server/Tamework/CapturePolicies/HyDragon*.json`
- Modify: `Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json`
- Modify: `Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json`
- Test: `src/test/java/com/alechilles/hydragon/integration/DraconicCaptureAssetContractTest.java`
- Test: `src/test/java/com/alechilles/hydragon/integration/DragonRosterAssetContractTest.java`
- Test: `src/test/java/com/alechilles/hydragon/integration/DraconicAltarAssetContractTest.java`

- [ ] Extend the asset-contract tests with the exact recipes, resource type membership, no required capture effect, capture values, role revive prices, and revised drop quantities.
- [ ] Run the focused tests and confirm the new assertions fail.
- [ ] Update the assets to satisfy the specification, including conversion recipes and all role mappings.
- [ ] Run the focused tests and commit the HyDragon change.

### Task 4: Update documentation and verify packages

**Files:**
- Modify: `docs/specs/capture-summoning-maintenance.md`
- Modify: `docs/specs/soul-bond-miniwyvern.md`
- Modify: `docs/specs/dragon-content-encounters.md`

- [ ] Replace superseded balance statements with links to `dragon-economy.md`.
- [ ] Run `mvn verify` in HyDragon and the focused Tamework test suite.
- [ ] Inspect `git diff --check` and commit the documentation/verification result.

## Self-review

- Specification coverage: Tasks 1–2 cover every required Tamework runtime extension; Task 3 covers every economy asset; Task 4 removes contradictory documentation and runs package verification.
- Placeholder scan: no deferred implementation steps or unnamed files remain.
- Type consistency: `ActiveEffectBonus` is configured in Task 1 and consumed by Task 3; `RevivePriceByRole` is configured in Task 1 and populated by Task 3.

