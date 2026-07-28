# Miniwyvern Role-Swap Attunement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Miniwyvern’s custom, extension-backed attunement transaction with owner-only Tamework NPC interactions that consume eight held essences and role-swap the live companion. Make the swapped NPC role the sole authority for its form, appearance, combat kit, and owner aura.

**Architecture:** Eight dedicated NPC roles represent the eight forms. Each role points to a role-specific Tamework interaction config containing the seven other legal transformations, so the current form is never selectable or charged. A backward-compatible Tamework `SetRole.ChangeAppearance` option makes the swap update the live model. HyDragon’s ability runtime resolves the active form from the live NPC role on the world thread; its bonded extension remains only a cooldown/effect-cleanup store. A small owner-aura registry bridges active Fire/Ice/Void/Toxic form state to a post-filter damage system, while Lightning, Nature, and Wind use continuous owner effects/modifiers.

**Tech Stack:** Java 25, Hytale 0.5.6 server APIs, Tamework 3.0.x, Tamework `TwInteractionConfig`, Hytale NPC roles/effects/projectiles, Maven/JUnit 5, Python asset validator.

## Global Constraints

- Form authority is the Miniwyvern’s **live Tamework/Hytale NPC role**. Do not write a form/archetype/attunement value to HyDragon state, a bonded extension, an item interaction, or a second service.
- The only mutation path for a transformation is the Miniwyvern’s NPC-side `TwInteractionConfig`; essence item JSON must not retain a secondary `HyDragonMiniwyvernAttune` interaction.
- A transform requires the player to own the active Miniwyvern and hold **one stack of exactly eight** of the matching essence. Tamework removes that held stack only after its requirements match.
- The current form must have no interaction entry targeting itself. This prevents a role no-op from still consuming eight essences, since Tamework applies `SetRole` before `RemoveItemsHand`.
- `SetRole` must opt into `ChangeAppearance: true` for these configs. The new field defaults to `false` for every existing Tamework config.
- Aura presence is global to the owner while the companion is active/summoned; no distance/range test is permitted. Storing, death, despawn, an invalid projection, feature shutdown, or role swap removes the owner’s continuous aura immediately. Enemy debuffs already applied by a prior Fire/Ice/Void/Toxic hit keep their authored short duration.
- No save migration or compatibility aliases are required: this feature has not shipped. Rename `neutral` to `wild`; retain Nature as the support form and add Toxic as a separate form. Add `Draconic_Essence_Toxic` as Toxic’s matching transform cost; it need not receive a beta drop source in this change.
- Preserve unrelated dirty worktree changes. Do not modify the unrelated Tamework source changes already present in its checkout.

## Agreed Form Matrix

| Form / role suffix | Cost (held stack) | Companion combat | Owner aura while summoned |
| --- | --- | --- | --- |
| Wild | 8 `Draconic_Essence` | Existing bite | None |
| Nature | 8 `Draconic_Essence_Nature` | Existing bite only | Healing aura |
| Toxic | 8 `Draconic_Essence_Toxic` | Venom projectile | Owner hits reduce enemy attack damage |
| Fire | 8 `Draconic_Essence_Fire` | Fire projectile | Owner hits burn enemies |
| Void | 8 `Draconic_Essence_Void` | Bite plus void projectile | Owner hits reduce enemy defense |
| Lightning | 8 `Draconic_Essence_Lightning` | Lightning strike/projectile | Movement-speed aura |
| Ice | 8 `Draconic_Essence_Ice` | Ice projectile | Owner hits slow enemies |
| Wind | 8 `Draconic_Essence_Wind` | Wind projectile | Jump-height aura |

All role IDs use the prefix `Tamed_Wyvern_Mini_` (for example, `Tamed_Wyvern_Mini_Fire`).

### Beta starting values

Retain the existing authored values unless a task explicitly replaces the mechanic: Lightning speed is 1.15x; Nature heals 1% maximum health every two seconds; Fire burn is 2 damage/sec for four seconds; Ice slow is 0.5x for four seconds; Void exposure is 12% for six seconds; Wind jump is 1.15x `jumpForce`. Toxic Weakness starts at **12% reduced attack damage for six seconds**, matching Void Exposure’s beta magnitude and duration; it is asset/config-defined so beta balance changes stay data-only.

## File / Responsibility Map

| Area | Files | Responsibility |
| --- | --- | --- |
| Tamework compatibility | `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwInteractionConfig.java`, `TwInteractionConfigCodecs.java`, `npc/actions/TameworkInteractEffects.java`, `npc/actions/InteractionStateEffects.java`, `docs/Interactions.md`, matching interaction tests | Add and document opt-in `ChangeAppearance` for role effects without changing existing behavior. |
| NPC form assets | `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/*.json`, `Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json`, `Server/Tamework/Companion/HyDragonMiniwyvern.json` | Define all legal roles and allow them in the one Miniwyvern companion family. |
| NPC interaction assets | `Server/Tamework/Interactions/HyDragonIntWyvernMini_*.json` | Owner-only held-stack transforms, preserving Feed and ModeCycle. |
| Form data / presentation | `Server/HyDragon/MiniwyvernArchetypes/*.json`, `Server/Models/HyDragon/Wyvern_Mini/*`, `Common/NPC/HyDragon/Wyvern_Mini/Model/*`, `Server/Entity/Effects/Status/HyDragon_Miniwyvern_*.json`, new Miniwyvern projectile assets | Bind form data to roles and define combat/aura visuals. |
| Attunement removal | `HyDragonPlugin.java`, `runtime/*Attunement*`, `runtime/HyDragonGameplayRuntime.java`, `interactions/HyDragonInteractionRuntime.java`, `interactions/HyDragonMiniwyvernAttuneInteraction.java`, `persistence/*`, relevant item JSON | Remove the second transformation authority and its transaction/recovery plumbing. |
| Role-driven abilities / auras | `abilities/MiniwyvernAbilityRuntime.java`, `MiniwyvernAbilityService.java`, `MiniwyvernAbilityState.java`, `MiniwyvernAbilityWorld.java`, `HytaleMiniwyvernAbilityWorldDispatcher.java`, new owner-aura classes | Resolve forms from live roles, manage continuous auras, and apply owner-hit debuffs. |
| Tests / verification | existing Miniwyvern, runtime, config, interaction, integration, and plugin lifecycle tests; new aura tests | Prove no double authority, exact costs, role persistence, and every aura lifecycle. |

---

### Task 1: Add the Tamework visual role-swap option first

**Files:**
- Modify `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwInteractionConfig.java`
- Modify `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwInteractionConfigCodecs.java`
- Modify `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/npc/actions/TameworkInteractEffects.java`
- Modify `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/npc/actions/InteractionStateEffects.java`
- Modify `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/docs/Interactions.md`
- Modify/add focused tests under `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/npc/actions/`

- [ ] Write a failing codec test that decodes `"SetRole": { "Role": "Tamed_Wyvern_Mini_Fire", "ChangeAppearance": true }`, asserts the flag is true, and asserts a legacy role effect with no field resolves to false.
- [ ] Add nullable/boxed `changeAppearance` plus a `getChangeAppearance()` accessor to `TwInteractionConfig.SetRoleEffect`; encode/decode it as `ChangeAppearance`, defaulting absent/null to `false`.
- [ ] Thread this flag through `TameworkInteractEffects.applySetRole` into `InteractionStateEffects.applySetRole`, replacing the hard-coded `false` passed to `RoleChangeSystem.requestRoleChange`.
- [ ] Add a focused behavior test around the effect call that proves `false` remains the legacy default and `true` is forwarded for a visual-changing role swap. Do not change effect execution order or introduce a new transaction layer.
- [ ] Document `SetRole.ChangeAppearance`, including its default and the fact that role configs must exclude self-targeting costed entries.
- [ ] Run from the Tamework checkout: `mvn -q -Dtest=InteractionParsingTest,InteractionBehaviorTest test`.
- [ ] Commit in the Tamework repository: `Feat: support visual interaction role swaps`.

### Task 2: Replace the old form authority and attunement code with role-only state

**Files:**
- Modify `src/main/java/com/alechilles/hydragon/HyDragonPlugin.java`
- Modify `src/main/java/com/alechilles/hydragon/runtime/HyDragonGameplayRuntime.java`
- Modify `src/main/java/com/alechilles/hydragon/runtime/TameworkGameplayAdapter.java`
- Delete `src/main/java/com/alechilles/hydragon/runtime/MiniwyvernAttunementService.java`
- Delete `src/main/java/com/alechilles/hydragon/runtime/StateStoreMiniwyvernProfileProjection.java`
- Delete `src/main/java/com/alechilles/hydragon/interactions/HyDragonMiniwyvernAttuneInteraction.java`
- Modify/delete the corresponding attunement interaction, runtime, recovery, projection, lifecycle, and codec tests.
- Modify `src/main/java/com/alechilles/hydragon/persistence/ProfileExtensionRecord.java`, `HyDragonStateStore.java`, `HyDragonStateSnapshot.java`, `ReconciliationInventory.java`, and their tests.
- Modify `src/main/java/com/alechilles/hydragon/bonded/BondedMiniwyvernExtensionDocument.java`, `BondedMiniwyvernExtensionCodec.java`, and their tests.

- [ ] Write failing tests proving a newly claimed Miniwyvern gets the `Tamed_Wyvern_Mini_Wild` role and that neither the local profile record nor the bonded extension exposes an `archetypeId`, an attunement revision, or a last-attunement operation.
- [ ] Change `TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE` into `WILD_MINIWYVERN_ROLE` and introduce one immutable `MINIWYVERN_ROLE_IDS` set containing all eight roles. Provision the Wild role only.
- [ ] Remove the `MINIWYVERN_ATTUNEMENT` feature, the `ATTUNE` interaction action/handler, the custom interaction codec registration, attunement transaction kind/journal recovery branch, profile projection, and refund path. `HyDragonGameplayRuntime` now owns only Soul Bond gameplay.
- [ ] Make the local Miniwyvern profile metadata form-free. Keep only the metadata still needed for Soul Bond identity; leave full-dragon projection behavior intact. Since no release exists, update the local record schema/read-write implementation directly rather than adding an old-form migration.
- [ ] Reduce `BondedMiniwyvernExtensionDocument` to companion identity, durable ability scheduler state, progression, and unknown-field preservation. Rename the scheduler’s stored `archetypeId` to `formId` (or equivalent) and document it as cleanup/cooldown state, never form authority.
- [ ] Replace `neutral` defaults with `wild`; remove `attune`, attunement evidence checks, and cross-field checks that assert the extension chooses the live form.
- [ ] Remove `HyDragonMiniwyvernAttune` from every `Draconic_Essence*.json` item interaction surface; essences become ordinary held items again.
- [ ] Update plugin lifecycle, state-store, Soul Bond, interaction codec, recovery, and integration tests to assert absence of the removed path rather than its former durability behavior.
- [ ] Run: `mvn -q -Dtest=SoulBondServiceTest,HyDragonStateStoreTest,PluginLifecycleContractTest,HyDragonInteractionCodecTest test`.
- [ ] Commit: `Refactor: remove miniwyvern attunement authority`.

### Task 3: Create the eight form roles and role-specific Tamework interactions

**Files:**
- Delete `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini.json`
- Add `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_{Wild,Nature,Toxic,Fire,Void,Lightning,Ice,Wind}.json`
- Replace `Server/Tamework/Interactions/HyDragonIntWyvernMini.json` with `Server/Tamework/Interactions/HyDragonIntWyvernMini_{Wild,Nature,Toxic,Fire,Void,Lightning,Ice,Wind}.json`
- Add `Server/Item/Items/Ingredient/Draconic_Essence_Toxic.json`, its icon/texture, and localized display strings; do not add it to beta drops.
- Modify `Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json`
- Modify `Server/Tamework/Companion/HyDragonMiniwyvern.json`
- Modify/add `src/test/java/com/alechilles/hydragon/config/BundledConfigAssetContractTest.java` or a dedicated `MiniwyvernRoleAndInteractionContractTest`.

- [ ] Write a failing asset-contract test that asserts exactly the eight form role IDs are in the roster and companion config, each role has its matching `InteractionConfigId`, and every config contains seven—not eight—transform entries.
- [ ] Create each role as a `Template_Wyvern_Mini_Flying_Tamed` variant. Preserve the companion/command/follow/defend values from the old role; set the form’s model appearance and its exact interaction config ID.
- [ ] Create all eight interaction configs. Each one preserves the current Feed and ModeCycle entries and contains seven `Type: "Custom"` transform entries before them. Each transform requires `IsTamed`, `PlayerIsOwner`, and `ItemsInHand` with the exact item and `Quantity: 8`; its effects are `SetRole` with `ChangeAppearance: true` followed by `RemoveItemsHand` for the same item/quantity.

  ```json
  {
    "Type": "Custom",
    "Requires": {
      "All": {
        "IsTamed": true,
        "PlayerIsOwner": true,
        "ItemsInHand": [{ "Items": ["Draconic_Essence_Fire"], "Quantity": 8 }]
      }
    },
    "Effects": {
      "SetRole": { "Role": "Tamed_Wyvern_Mini_Fire", "ChangeAppearance": true },
      "RemoveItemsHand": { "Items": ["Draconic_Essence_Fire"], "Quantity": 8 }
    }
  }
  ```

- [ ] Use this destination-cost mapping in every config: Wild→`Draconic_Essence`, Nature→`Draconic_Essence_Nature`, Toxic→`Draconic_Essence_Toxic`, Fire→`Draconic_Essence_Fire`, Void→`Draconic_Essence_Void`, Lightning→`Draconic_Essence_Lightning`, Ice→`Draconic_Essence_Ice`, Wind→`Draconic_Essence_Wind`.
- [ ] Leave existing water essence, models, drops, and unrelated water gameplay untouched; Water is simply not a selectable Miniwyvern form in this release.
- [ ] Ensure no config offers its own destination role. Verify an owner holding fewer than eight, a non-owner holding eight, and an owner holding an unrelated eight-stack cannot match any transform entry.
- [ ] Expand the bonded roster/companion role allowlists to all eight roles while keeping one `hydragon:soulbound_mini` family and the existing single-profile Horn behavior.
- [ ] Run: `python scripts/validate_assets.py` and `mvn -q -Dtest=BundledConfigAssetContractTest test`.
- [ ] Commit: `Feat: add miniwyvern role swap interactions`.

### Task 4: Make form assets role-bound and finish the requested combat matrix

**Files:**
- Rename `Server/HyDragon/MiniwyvernArchetypes/Neutral.json` to `Wild.json`; retain `Nature.json` as the support form; add `Toxic.json`.
- Keep the existing Nature model/texture identifiers. The user will supply dedicated Toxic texture art; wire its model/role references when it is available, without using the retired Water Miniwyvern assets as a substitute. This final asset-wiring substep is intentionally deferred, but must complete before asset validation/release.
- Modify `src/main/java/com/alechilles/hydragon/config/MiniwyvernArchetypeConfig.java`
- Modify `src/main/java/com/alechilles/hydragon/config/HyDragonConfigRepository.java`
- Modify `Server/Entity/Effects/Status/HyDragon_Miniwyvern_{Lightning_Boon,Nature_Regeneration,Wind_Boon}.json`; add `HyDragon_Miniwyvern_Toxic_Weakness.json`; retain/reuse Fire, Ice, and Void combat effects.
- Add/modify Miniwyvern projectile assets under `Server/Projectiles/HyDragon/Miniwyvern/` only where a verified base-game projectile is not suitable.
- Modify config asset and ability-service tests.

- [ ] Write failing config tests for a one-to-one mapping of eight `Id` values to eight unique `RoleId` values, and reject duplicate/unknown role IDs or a roleless form config.
- [ ] Add required `RoleId` to the form config codec/validation and remove its `EssenceSemanticId`, `EssenceItemId`, and `AppearanceId` fields; transformation costs and visual roles now live in their owning asset systems, not in form data.
- [ ] Rename the default config identity to `wild`; retain `nature` as the support-form ID; add `toxic`. Keep the Nature and Toxic transformation item IDs only in the interaction assets. Do not rename the existing Nature presentation/model IDs.
- [ ] Configure combat as follows: Wild and Nature have no active ability beyond their existing bite; Toxic launches verified `Scarak_Seeker_Spitball`; Fire retains its fire projectile and burn; Void retains bite plus `Eye_Void_Blast`; Ice retains `Hydra_Ice_Ball`; Wind retains `Feran_Windwalker_Wind_Burst`; Lightning retains its existing lightning strike and emits its existing Lightning presentation. Use a custom projectile only if a live test proves the verified base projectile is incompatible with the Miniwyvern launcher.
- [ ] Retain Lightning’s speed effect. Keep Nature’s visible regeneration effect presentation-only while the existing capped scheduler healing delivers the actual 1%-per-two-seconds aura. Remove Wind’s speed modifier; Wind will carry only the supported jump modifier in Task 6.
- [ ] Add data-only `OwnerAttackAura` metadata to Fire, Ice, Void, and Toxic configs: Fire→Fire Burn/4 s, Ice→Ice Slow/4 s, Void→Void Exposure/6 s, Toxic→Toxic Weakness/6 s/12% attack damage reduction. These are not companion active abilities and are not persisted form state.
- [ ] Run: `mvn -q -Dtest=BundledConfigAssetContractTest,MiniwyvernAbilityServiceTest test`.
- [ ] Commit: `Feat: define miniwyvern role-bound combat forms`.

### Task 5: Resolve live roles in the ability runtime and remove model synchronization

**Files:**
- Modify `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityRuntime.java`
- Modify `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityService.java`
- Modify `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityState.java`
- Modify `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityWorld.java`
- Modify `src/main/java/com/alechilles/hydragon/abilities/HytaleMiniwyvernAbilityWorldDispatcher.java`
- Modify `src/main/java/com/alechilles/hydragon/abilities/TameworkMiniwyvernAbilityStateRepository.java`
- Modify `src/test/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityRuntimeTest.java`, `MiniwyvernAbilityServiceTest.java`, `TameworkMiniwyvernAbilityStateRepositoryTest.java`, and `src/test/java/com/alechilles/hydragon/integration/MiniwyvernLifecycleContinuityTest.java`.

- [ ] Write a failing runtime test that starts a Wild active lease, changes only the live companion role to Fire, and asserts the next world-thread tick uses Fire behavior without an extension/profile form write. Repeat through store/re-summon and assert the persisted Tamework role remains Fire.
- [ ] Replace the `ActiveBinding.archetypeId` with identity/lease data only. `activeMiniwyvern` must accept any role in `MINIWYVERN_ROLE_IDS`, rather than a single old role ID.
- [ ] Add `companionRoleId()` to the world boundary and implement it from the live `NPCEntity` role/name in `HytaleMiniwyvernAbilityWorldDispatcher.Port`. Resolve `MiniwyvernArchetypeConfig` by that exact live role inside `MiniwyvernAbilityService.tick`.
- [ ] Delete `synchronizeAppearance` from the service and world interface/dispatcher. The role swap with `ChangeAppearance: true` is the one visual authority.
- [ ] On a role-to-form change, clean source-keyed companion effects using the prior scheduler `formId`, reset cooldown/buildup state for the new form, and persist only that scheduler state. Never call a transform/attunement service or update a profile role from Java.
- [ ] Keep the Nature healing scheduler checks and cooldown keys, retain the healing cap and cleanup semantics, and add separate Toxic projectile cooldown state. Update config snapshot validation to index by role as well as ID.
- [ ] Confirm lifecycle cleanup clears continuous owner effects when the active lease goes stored/dead/unresolved and that a fresh lease re-resolves its live role.
- [ ] Run: `mvn -q -Dtest=MiniwyvernAbilityRuntimeTest,MiniwyvernAbilityServiceTest,TameworkMiniwyvernAbilityStateRepositoryTest,MiniwyvernLifecycleContinuityTest test`.
- [ ] Commit: `Refactor: drive miniwyvern abilities from roles`.

### Task 6: Implement continuous auras and owner-hit elemental effects

**Files:**
- Add `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraRegistry.java`
- Add `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraDamageSystem.java`
- Add/refactor a package-private owner movement modifier helper next to `HytaleMiniwyvernAbilityWorldDispatcher.java`
- Modify `MiniwyvernAbilityRuntime.java`, `MiniwyvernAbilityService.java`, `MiniwyvernAbilityWorld.java`, `HytaleMiniwyvernAbilityWorldDispatcher.java`, `HyDragonAbilityRegistrationFacade.java`, and `HyDragonPlugin.java`
- Add focused unit tests under `src/test/java/com/alechilles/hydragon/abilities/` and update plugin registration contract tests.

- [ ] Write failing tests covering: Lightning applies/removes 1.15x speed with lifecycle; Nature heals at the configured capped cadence only while active; Wind changes/restores jump force once rather than multiplying every tick; and Fire/Ice/Void/Toxic owner hits apply only their respective effect.
- [ ] Make `MiniwyvernOwnerAuraRegistry` a thread-safe in-memory registry keyed by owner UUID and active lease/profile. It records only the currently live form’s owner-hit effect metadata; the ability runtime updates it after live-role resolution and clears it on every deactivation/close. The registry is not persistence and is not form authority.
- [ ] Register `MiniwyvernOwnerAuraDamageSystem` during plugin setup in the Hytale `DamageModule` inspect-damage group, beside the existing encounter damage system. On a positive, non-cancelled damage event sourced by a player, look up that player’s active owner-hit aura, confirm the registered Miniwyvern considers the victim `Attitude.HOSTILE`, and apply the configured Fire/Ice/Void/Toxic effect with overwrite and the configured duration. Do not affect the owner, allies, unrelated NPCs, or non-player damage sources.
- [ ] Keep the companion’s own Fire/Ice/Void/Toxic projectile effects in `MiniwyvernAbilityService`; owner-hit effects must be driven only by the damage system so one owner melee/ranged hit gets the same behavior.
- [ ] Implement Wind through `MovementManager.getSettings().jumpForce`, not a fake speed effect. A source-keyed helper snapshots the base jump force when the first Wind source is applied, sets `base * 1.15`, sends `MovementManager.update`, and restores the snapshot when the final source is removed. It must mutate current settings only—never the default settings—and must compose safely without per-tick multiplication.
- [ ] Use the existing source-keyed owner effect path for Lightning with `HyDragon_Miniwyvern_Lightning_Boon`. Nature’s healing remains on its capped scheduler; no Water guard or other Water Miniwyvern aura is added.
- [ ] Register the shared aura registry before ability runtime startup, pass the same object into the dispatcher/runtime and damage system, and close/clear it during plugin shutdown before losing world access.
- [ ] Run: `mvn -q -Dtest=MiniwyvernOwnerAuraRegistryTest,MiniwyvernOwnerAuraDamageSystemTest,MiniwyvernAbilityServiceTest,PluginLifecycleContractTest test`.
- [ ] Run Hytale code-reference validation against the modified dispatcher and new damage system after writing them; resolve every `not_found` result before continuing.
- [ ] Commit: `Feat: add miniwyvern owner auras`.

### Task 7: Prove role-swap transaction behavior across both mods

**Files:**
- Modify/add Tamework interaction integration tests in `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/npc/actions/`
- Add/modify `src/test/java/com/alechilles/hydragon/integration/MiniwyvernRoleSwapContinuityTest.java`
- Modify `src/test/java/com/alechilles/hydragon/config/BundledConfigAssetContractTest.java`
- Modify `scripts/validate_assets.py` only if a deterministic new form/interaction invariant cannot be expressed in JUnit.

- [ ] Add an interaction-level Tamework test with a held eight-stack that proves the owner config requests the expected target role with appearance change and removes exactly eight items; verify fewer items/non-owner fail before either mutation.
- [ ] Add the complementary self-form test: the Wild config has no Wild transform entry, so an owner holding eight normal essences cannot trigger a transform and loses no items. Parameterize it across all eight source forms, including Toxic.
- [ ] Add a HyDragon continuity test that simulates Wild→Fire, stores the profile, restarts the local HyDragon runtime, and re-summons a Fire Miniwyvern. Assert the form comes from the Tamework role and the bonded extension contains ability state only.
- [ ] Add an asset test that parses every Miniwyvern interaction config and checks all 56 directed source→destination transforms, exact quantity 8, exact essence mapping, owner requirement, `ChangeAppearance: true`, and no self edge.

  ```java
  assertEquals(7, transformEntries.size(), sourceRole);
  assertFalse(destinations.contains(sourceRole), sourceRole + " must not offer a self-swap");
  assertEquals(expectedCost.get(destinationRole), entry.heldItemId());
  assertEquals(8, entry.heldQuantity());
  assertTrue(entry.playerIsOwner() && entry.changeAppearance());
  ```

- [ ] Run HyDragon focused tests: `mvn -q -Dtest=MiniwyvernRoleSwapContinuityTest,BundledConfigAssetContractTest,MiniwyvernAbilityRuntimeTest test`.
- [ ] Run all Tamework interaction tests affected by the new field and then package the tested Tamework version. Update HyDragon’s `tamework.version` and local JAR path only after the new JAR is available to the project’s system dependency.
- [ ] Commit Tamework: `Test: cover visual role swap costs`; commit HyDragon: `Test: cover miniwyvern role swap continuity`.

### Task 8: Perform release-grade verification and update implementation notes

**Files:**
- Modify `docs/superpowers/specs/2026-07-28-miniwyvern-role-swap-attunement-design.md` only to record the implementation refinement that configs are role-specific (seven entries each) and that form resolution is live-role-driven.
- Update any Miniwyvern-facing README/wiki content if it exists and currently describes custom essence-item attunement.

- [ ] Inspect the final diff and verify no `MiniwyvernAttunementService`, `HyDragonMiniwyvernAttune`, `MINIWYVERN_ATTUNEMENT`, `archetypeRevision`, `lastAttunementOperationId`, `neutral`, or form-authoritative extension fields remain outside historical changelog text.
- [ ] Run `python scripts/validate_assets.py`.
- [ ] Run `mvn -q clean verify` in HyDragon, including package/integration tests, and build/test the corresponding Tamework JAR once more from its checkout. Do not leave a server or helper process running.
- [ ] Confirm the packaged HyDragon JAR contains all eight roles, eight interactions, all form configs, the supplied Toxic model/texture, Toxic Weakness, and any needed projectile assets.
- [ ] In a disposable local test world, manually verify Wild→Fire, Fire→Toxic, Toxic→Wild; insufficient stack; non-owner attempt; store/re-summon persistence; Fire owner-hit burn; Ice owner-hit slow; Void owner-hit exposure; Toxic owner-hit weakness; Lightning speed; Wind jump; and Nature healing. Capture server logs for role-swap/model errors.
- [ ] Commit: `Docs: document role-driven miniwyvern forms`.

## Final Acceptance Checklist

- [ ] Every form change is initiated from the Miniwyvern NPC interaction while holding the exact eight-item stack.
- [ ] One role swap changes the persisted companion role, live appearance, combat behavior, and aura; no Java/item attunement path remains.
- [ ] The Miniwyvern can transform from any form to any other form except itself, and self-interaction never consumes essences.
- [ ] The Wild revert uses eight normal `Draconic_Essence`; Nature uses eight `Draconic_Essence_Nature`; Toxic uses eight `Draconic_Essence_Toxic`.
- [ ] The form survives store, restart, and re-summon through Tamework’s role persistence.
- [ ] Continuous auras exist only while the Miniwyvern is active, regardless of range.
- [ ] Wind changes jump height, Nature heals its owner, and Fire/Ice/Void/Toxic modify the owner’s attacks against hostile enemies.
- [ ] All targeted tests and both Maven verification suites pass with no stale processes.
