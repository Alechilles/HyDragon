# Miniwyvern Talent Progression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every bonded Miniwyvern one persistent, freely resettable level-30 talent tree whose shared flags are interpreted by each current form through asset wiring, with combat and capped active-summon XP.

**Architecture:** Tamework owns the reusable engine primitives: a self-targeting purchased-talent sensor and a generic `SUMMONED` XP source. HyDragon owns the Miniwyvern leveling/talent data and each form's asset interpretation of those flags. NPC role instructions gate root interactions and entity effects with `TameworkHasTalent`; once every equivalent path is asset-driven, the existing HyDragon Miniwyvern Java ability runtime is removed.

**Tech Stack:** Java 25, Maven/JUnit 5, Tamework 3.1.0, Hytale 0.5.7, Hytale NPC assets and entity effects, JSON assets, Python asset validator.

**Design source:** [Miniwyvern Talent Progression Design](../specs/2026-07-29-miniwyvern-talent-progression-design.md)

## Global constraints

- Keep one `TwLevelingConfig` and one `TwTalentConfig` for all seven Miniwyvern roles; never create form-specific XP, point, or purchased-talent state.
- Put all gameplay that Hytale assets can express in role instructions, root interactions, projectiles, and entity effects. Java is limited to generic Tamework primitives.
- `TameworkHasTalent` evaluates the NPC itself, fails closed for absent/malformed state, and reads only `TameworkTalentsComponent`.
- Every upgraded instruction must exclude stronger variants with native `Not` sensors so variants do not stack.
- The Wild form's projectile remains raw damage only. It may gain damage, range, cadence, and additional-projectile upgrades, but never elemental statuses.
- Award talent points once per level after level 1: level 30 grants 29 points. The authored tree has 30 nodes costing 51 points, so specialization remains necessary.
- `SUMMONED` XP requires a live bonded-companion projection, grants no catch-up after inactive time, and retains its wall-clock hourly cap across dismiss/re-summon within the same hour.
- Start Tamework work from the clean `main` integration worktree (`C:/Users/22ale/AppData/Roaming/Hytale/Modding/.codex-worktrees/tamework-main-integration`), not the dirty `feat/capture-alert` worktree. Avatar Flight XP is already merged into `main` and is the reference cadence implementation.
- Before editing NPC JSON, load and follow the `hytale-npc-asset-tools` skill, lock the Hytale 0.5.7 profile, use generated author options rather than remembered field names, and validate every changed role plus generated dependency.
- Preserve unrelated worktree changes. Commit each completed task in its own repository; do not publish or deploy without separate authorization.

## Files and ownership map

| Repository | Area | Responsibility |
| --- | --- | --- |
| Tamework | `npc/sensors`, `npc/progression`, `config`, `Tamework.java` | Generic talent sensor and active-summon XP machinery. |
| HyDragon | `Server/Tamework/Leveling`, `Server/Tamework/Talents`, `Server/Languages` | Miniwyvern progression contract, localization, and balance. |
| HyDragon | `Server/NPC/Roles/.../Wyvern_Mini`, `Server/Item`, `Server/Entity/Effects` | Per-form asset interpretation of shared flags. |
| HyDragon | `src/main/java/.../abilities`, plugin registration | Delete legacy Miniwyvern ability authority after asset parity. |
| HyDragon | `src/test`, `scripts/validate_assets.py` | Contract, packaging, and asset validation. |

---

## Task 1: Add Tamework's self-targeting talent sensor

**Repository:** Tamework clean-main worktree.

**Files:**

- Create `src/main/java/com/alechilles/alecstamework/npc/sensors/builders/BuilderSensorTameworkHasTalent.java`.
- Create `src/main/java/com/alechilles/alecstamework/npc/sensors/SensorTameworkHasTalent.java`.
- Modify `src/main/java/com/alechilles/alecstamework/npc/TameworkNpcBuilderRegistrar.java`.
- Create `src/test/java/com/alechilles/alecstamework/npc/sensors/SensorTameworkHasTalentTest.java`.
- Modify `docs/Actions-Sensors-Components.md`.

- [ ] Write focused failing tests that create an NPC with a `TameworkTalentsComponent` and prove exact purchased IDs match, another NPC's IDs never match, and missing component/blank `TalentId` returns false or is rejected during config parsing.
- [ ] Implement the config-bearing builder with `BUILDER_ID = "TameworkHasTalent"`, a required non-empty `TalentId` `StringHolder`, and a `getTalentId(BuilderSupport)` accessor. Follow `BuilderSensorTameworkEffectActive`'s parser/validator pattern.
- [ ] Implement `SensorTameworkHasTalent` as a `TameworkSensorBase`. In `matches(Ref<EntityStore> ref, Role role, double dt, Store<EntityStore> store)`, fetch the component from `ref`, return false if unavailable, and call `hasPurchasedTalent(talentId)`. Do not resolve an owner, target, player, or world profile.
- [ ] Register the builder alongside the existing Tamework sensor builders and document the canonical asset form:

  ```json
  { "Type": "TameworkHasTalent", "TalentId": "DraconicProjectile" }
  ```

- [ ] Run `./mvnw test -Dtest=SensorTameworkHasTalentTest` and then `./mvnw test` from the Tamework worktree.
- [ ] Commit the isolated change as `Feat: add talent sensor`.

## Task 2: Add the generic `SUMMONED` leveling source and config contract

**Repository:** Tamework clean-main worktree, after Task 1.

**Files:**

- Modify `src/main/java/com/alechilles/alecstamework/npc/progression/CompanionXpSource.java`.
- Modify `src/main/java/com/alechilles/alecstamework/config/TwLevelingConfig.java`.
- Modify `src/main/java/com/alechilles/alecstamework/npc/progression/TameworkLevelingComponent.java`.
- Modify the existing leveling-config and component codec tests under `src/test/java/com/alechilles/alecstamework`.

- [ ] Add `SUMMONED` without changing the names or semantics of existing enum values, especially `AVATAR_FLIGHT`.
- [ ] Add `SummonedXpSourceSettings` to `TwLevelingConfig.XpSourcesSettings` with exactly `Enabled`, `XpPerActiveSecond`, `AwardIntervalSeconds`, and `MaxXpPerHour`. Give it safe disabled defaults, validate positive cadence/rate/cap when enabled, expose a getter, and wire it through all inheritance/copy paths just as the existing Flight settings are wired.
- [ ] Extend the persistent `TameworkLevelingComponent` codec, clone/copy logic, and constructors with the summon cadence state: accumulated active seconds, awarded XP in the current window, window start time, and last sample time. Default old profiles safely to an empty state.
- [ ] Add tests before implementation that deserialize an inherited config, confirm child overrides do not lose `Summoned`, round-trip component cadence state, and verify `SUMMONED` reaches the normal XP event/audit path.
- [ ] Run the focused config/component/event tests and `./mvnw test`.
- [ ] Commit as `Feat: configure summoned companion XP`.

## Task 3: Implement deterministic active-summon XP in Tamework

**Repository:** Tamework clean-main worktree, after Task 2.

**Files:**

- Create `src/main/java/com/alechilles/alecstamework/npc/progression/SummonedCompanionExperienceService.java`.
- Create `src/main/java/com/alechilles/alecstamework/npc/systems/SummonedCompanionExperienceSystem.java`.
- Modify `src/main/java/com/alechilles/alecstamework/Tamework.java`.
- Create `src/test/java/com/alechilles/alecstamework/npc/progression/SummonedCompanionExperienceServiceTest.java`.
- Create or extend the system wiring test under `src/test/java/com/alechilles/alecstamework/npc/systems`.

- [ ] Write service tests modeled on `AvatarFlightExperienceServiceTest`: first sample awards nothing, qualified active time awards only on whole `AwardIntervalSeconds` boundaries, a large tick is clamped to the existing 0.25-second maximum, disabled config awards nothing, and no tick can exceed `MaxXpPerHour`.
- [ ] Define the service boundary explicitly:

  ```java
  Result advance(State state, long nowMs, double dt,
                 TwLevelingConfig.SummonedXpSourceSettings settings,
                 boolean active);
  ```

  `Result` contains replacement cadence state and the XP to award. On an inactive/gapped sample, discard partial interval progress immediately, retain the current wall-clock cap until its hour expires, and award zero catch-up XP. On hour expiry, reset only the hourly award counter/window, not earned companion XP.

- [ ] Implement the ticking system using the live entity's `TameworkProjectionIdentityComponent.isBondedCompanion()` and leveling component. It must ignore ordinary NPCs, players, dead/invalid projections, and non-bonded projections. Award through `CompanionLevelingService.awardXp(..., CompanionXpSource.SUMMONED, amount)` so events, caps, and persistence follow the standard route.
- [ ] Register the system near the existing companion combat/flight progression systems in `Tamework.java` with the required component types. Avoid a static timer map: role swaps and server lifecycle must use the component state.
- [ ] Add a system-level test proving a non-bonded or dead projection cannot earn `SUMMONED` XP and a bonded live projection can.
- [ ] Run `./mvnw test -Dtest=SummonedCompanionExperienceServiceTest` followed by `./mvnw test` and `./mvnw verify`.
- [ ] Commit as `Feat: award XP to active companions`.

## Task 4: Release the Tamework API change locally for HyDragon integration

**Repository:** Tamework clean-main worktree, after Tasks 1-3.

- [ ] Bump Tamework's Maven/artifact version from `3.0.0` to `3.1.0`, update any version assertions and release notes required by that repository, and build the mod jar into the normal local mod location.
- [ ] Verify the resulting jar exposes `TameworkHasTalent`, `SUMMONED`, the `Summoned` config block, and does not regress Avatar Flight XP with `./mvnw verify`.
- [ ] Commit the version/release metadata as `Release: prepare Tamework 3.1.0`.
- [ ] Keep this local unless the user separately authorizes publication. HyDragon's integration work uses this exact local artifact and changes `<tamework.version>` to `3.1.0` only after the jar exists.

## Task 5: Author the complete shared Miniwyvern progression data

**Repository:** HyDragon.

**Files:**

- Create `Server/Tamework/Leveling/HyDragonMiniwyvern.json`.
- Create `Server/Tamework/Talents/HyDragonMiniwyvern.json`.
- Modify `Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json`.
- Modify `Server/Tamework/Companion/HyDragonMiniwyvern.json`.
- Modify `pom.xml` (`tamework.version` to `3.1.0`).
- Create `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentProgressionAssetTest.java`.
- Modify the relevant `Server/Languages/*.lang` files: `en-US`, `de-DE`, `es-ES`, `fr-FR`, and `pt-BR`.

- [ ] Write the progression-asset test first, following `NordicDrakeProgressionAssetTest`. Assert enabled level cap 30, one point after level 1, all seven Miniwyvern role IDs resolve to the same leveling and talent IDs, all 30 node IDs are unique, prerequisites exist, and node cost/level totals are 51 points across Bond 15, Combat 19, and Vigor 17.
- [ ] Author the level config with existing final-damage dealt and taken sources enabled, player and same-owner exclusions preserved, and taken XP lower than dealt XP. Add `Summoned` with a conservative nonzero trickle, whole-second cadence, and an explicit hourly cap; keep these balance values isolated to this file.
- [ ] Author exactly these generic, non-form-prefixed talent IDs and graph:

  | Branch | IDs in prerequisite order |
  | --- | --- |
  | Bond | `EssenceBond`, `EssenceFocus`, `EssenceAttunement`, `EssenceAmplification`, `EssenceResonance`, `EssenceEfficiency`, `EssenceHarmony`, `EssenceMastery`, `EssenceAscendance` |
  | Combat | `DraconicProjectile`, `ProjectileRange`, `ProjectileCadence`, `ProjectileForce`, `ProjectileGuidance`, `ProjectileImpact`, `ProjectilePattern`, `DraconicAssault`, `AssaultUtility`, `AssaultMastery`, `DraconicApex` |
  | Vigor | `VitalScales`, `HardenedScales`, `ElderScales`, `ScaleGuard`, `ScaleBulwark`, `RapidRecovery`, `SurvivalInstinct`, `LastingScales`, `WyrmFortitude`, `VigorAscendance` |

  Use the design's tier/min-level/cost tables. Apply the first three Vigor effects as `MaxHealthMultiplier` values `1.05`, `1.05`, and `1.08`; each later node's effect is only a generic flag consumed by assets. Use existing linked-panel icons and localized display/description keys for every node.
- [ ] Ensure the companion roster/config defines all Wild, Fire, Ice, Lightning, Nature, Toxic, and Void tamed role IDs against the shared config IDs and preserves normal Tamework reset behavior with no cost/cooldown override.
- [ ] Add all English localization; copy faithful translated text into the other four shipped locales, preserving every key so the talent panel cannot show raw IDs.
- [ ] Run `./mvnw test -Dtest=MiniwyvernTalentProgressionAssetTest`, `python scripts/validate_assets.py`, and `./mvnw test`.
- [ ] Commit as `Feat: add miniwyvern talent progression`.

## Task 6: Replace Miniwyvern Java gameplay with talent-gated assets

**Repository:** HyDragon. Load `hytale-npc-asset-tools` before beginning this task.

**Files:**

- Modify `Server/NPC/Templates/Creature/HyDragon/Template_Wyvern_Mini_Flying_Tamed.json`.
- Modify all seven `Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_*.json` files.
- Create/update the Miniwyvern root-interaction assets under `Server/Item/RootInteractions/NPCs/HyDragon/Wyvern_Mini/`.
- Create/update projectile and interaction assets under `Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/`.
- Create/update talent-tier entity effects under `Server/Entity/Effects/Status/`.
- Modify `scripts/validate_assets.py` and add/extend focused asset contract tests under `src/test/java/com/alechilles/hydragon`.

- [ ] With the locked 0.5.7 asset profile, inspect the current bite/root-interaction contract and generate author options for instructions, sensor combinators, projectile launch, target selection, entity effect application/removal, cadence, and multi-projectile behavior. Record the selected supported fields in the task commit description or nearby asset comments where the format permits.
- [ ] Build a common role-instruction shape for each capability: `And(TameworkHasTalent(flag), normal combat/owner condition, Not(higher-tier flag))` selects exactly one base/upgraded variant; the selected instruction calls a root interaction or effect asset that completely performs the behavior. Never retain an instruction that only checks a talent but has no action path.
- [ ] Wire the three branches for every form. Bond gates its owner passive and upgrades; Combat gates projectile, range, cadence, force, pattern, advanced attack, and capstone; Vigor's health multipliers come from the talent config while its non-health survival behavior is gated in assets. Existing effect IDs may be retained only when their full behavior is expressed in assets.
- [ ] Author Wild first as the reference implementation: `EssenceBond` and upgrades grant stamina regeneration/max stamina; `DraconicProjectile` launches raw damage with no effect payload; upgrades only alter raw projectile damage, range, cadence, or count. Add tests/validator assertions that no Wild projectile path applies Fire, Ice, Lightning, Nature, Toxic, or Void status effects.
- [ ] Apply the same shared flags to Fire, Ice, Lightning, Nature, Toxic, and Void with their own asset-level interpretation and presentation. Reuse only generic flag IDs; no form-specific talent ID may appear in the tree.
- [ ] For every replaceable passive/attack, remove or neutralize the prior Java-owned execution path before enabling its asset counterpart. Test base, intermediate, and highest-tier ownership to show exactly one intended variant fires.
- [ ] Validate each changed role and every generated dependency through the NPC asset tool, then run `python scripts/validate_assets.py` and `./mvnw test`.
- [ ] Commit as `Feat: wire miniwyvern talents through assets`.

## Task 7: Remove the superseded HyDragon Miniwyvern ability runtime

**Repository:** HyDragon, only after Task 6's full asset parity test passes.

**Files:**

- Modify `src/main/java/com/alechilles/hydragon/HyDragonPlugin.java`.
- Modify `src/main/java/com/alechilles/hydragon/HyDragonAbilityRegistrationFacade.java`.
- Delete `src/main/java/com/alechilles/hydragon/abilities/MiniwyvernAbilityRuntime.java` and `MiniwyvernAbilityService.java`.
- Delete their Miniwyvern-only state, repository, world/dispatcher, archetype-config, owner-aura, toxic-weakness, and void-effect-lifetime collaborators only when `rg` proves no other feature imports them.
- Delete/update the matching tests under `src/test/java/com/alechilles/hydragon/abilities` and `bonded`.
- Modify `scripts/validate_assets.py` to remove the legacy Java-archetype expectation and replace it with the asset-first contract.

- [ ] Run `rg -n "MiniwyvernAbility|MiniwyvernOwnerAura|MiniwyvernToxicWeakness|MiniwyvernVoidEffectLifetime|MiniwyvernArchetype" src/main src/test scripts` and classify each remaining reference as deletion, migration, or an unrelated retained effect asset. Do not remove generic bonded-companion persistence used elsewhere.
- [ ] Remove scheduler/system registration from the plugin facade only after the role instruction/root interaction paths handle all former attacks, passives, owner effects, and cleanup. There must be no duplicate damage, effect duration, or cooldown source.
- [ ] Replace deleted Java-behavior tests with contract tests that inspect the asset wiring: all seven roles use the shared flags; every `TameworkHasTalent` instruction resolves to an existing action/effect; upgraded flags exclude lower variants; Wild remains raw-only.
- [ ] Run `./mvnw test`, `./mvnw verify`, and `python scripts/validate_assets.py`; then inspect `git diff --check` and `git status --short` to confirm no unrelated files were changed.
- [ ] Commit as `Refactor: retire miniwyvern ability runtime`.

## Task 8: End-to-end verification and integration handoff

**Repositories:** Tamework 3.1.0 worktree and HyDragon.

- [ ] Build/install the Tamework 3.1.0 jar, then run HyDragon `./mvnw clean verify` against that exact jar. Confirm the Maven property resolves `Alec's Tamework! v3.1.0.jar` rather than the old 3.0.0 artifact.
- [ ] Run the complete Tamework `./mvnw verify` suite and the complete HyDragon `./mvnw verify` suite, including packaged-roster tests. Extend `PackagedHyDragonRosterIT` if the artifact assertion needs to prove the new Miniwyvern leveling/talent assets are packaged.
- [ ] Perform the in-game smoke sequence with one bonded Miniwyvern: level to node eligibility, buy a flag, switch through all seven forms, dismiss/resummon, die/revive, relog, use free reset, and confirm the purchased IDs/points and current form behavior match expectations at each step.
- [ ] Specifically measure: dealt versus taken combat XP; no XP from player/same-owner damage; summon XP only while a live projection exists; no partial-interval catch-up; hourly cap survives dismiss/re-summon; cap refreshes after its wall-clock hour; and a reset immediately removes talent-gated behavior.
- [ ] Validate every modified NPC asset against Hytale 0.5.7 plus the Tamework 3.1.0 runtime profile and retain the validator output with the implementation handoff.
- [ ] Commit any verification-only test/packaging fixes as `Test: cover miniwyvern talent integration`.

## Final acceptance checklist

- [ ] The Miniwyvern has one persistent level-30 tree across all forms and exactly 29 earnable points.
- [ ] The 30-node, 51-point tree requires specialization and resets freely.
- [ ] The generic self-NPC `TameworkHasTalent` sensor is documented, tested, and used by all talent-gated Miniwyvern behavior.
- [ ] Every power expressible through assets is asset-owned; HyDragon's former Miniwyvern scheduler has no gameplay authority.
- [ ] Wild has stamina passives and raw projectile progression only; elemental forms interpret the same flags differently.
- [ ] Combat dealt/taken and capped active-summon XP are working, deterministic, and lifecycle-safe.
- [ ] Both repositories build, tests pass, assets validate, and the artifact contains the new progression files.
