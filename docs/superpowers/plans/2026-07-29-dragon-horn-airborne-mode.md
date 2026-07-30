# Dragon Horn Airborne Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Dragon Horn distinct Defend and Attack Target commands, replace its two home commands with a transient Flight/Ground toggle, and make every bonded Miniwyvern and full HyDragon preserve its current order and target while switching movement style.

**Architecture:** The Horn owns an explicit replacement `CommandList`. Its toggle emits Tamework 3.0.0's existing one-shot `TriggerHook`; a shared HyDragon NPC instruction consumes that hook and flips the vanilla `AirborneMode` role flag. The same instruction performs only native takeoff/landing controller transitions. The Miniwyvern and full-dragon tamed templates select grounded or aerial Follow, Defend, Hold, and Idle behavior from that flag, so no command toggle changes `State`, `MasterTarget`, or `LockedTarget`.

**Tech Stack:** Hytale 0.5.7 NPC JSON assets, unreleased Tamework 3.0.0, Java 25, Maven/JUnit 5, Hytale NPC Asset Tools, Hytale language catalogs.

**Design source:** [Dragon Horn Locomotion Design](../specs/2026-07-29-dragon-horn-locomotion-design.md)

## Global constraints

- Keep Tamework at unreleased version `3.0.0`. Do not add a Tamework Java action, sensor, component, flag store, or version bump: `TriggerHook` and `TameworkHook` are already present and are the required generic bridge.
- Use the vanilla role flag name exactly `AirborneMode`. It is compiled by native `Flag` sensors and `SetFlag` actions; do not persist it in bonded-companion data or a Tamework component.
- Reset `AirborneMode` to `false` once for every newly spawned NPC role. Dismissal and re-summoning must therefore return to grounded mode.
- The toggle path may only flip `AirborneMode` and drive native takeoff/landing. It must never contain `State`, `SetTarget`, `ReleaseTarget`, `ClearTarget`, or master-target actions.
- Explicitly replace the inherited Horn `CommandList`; inheritance replaces arrays rather than appending to them. The final wheel contains exactly eight commands: `Follow`, `Hold`, `Recall`, `MoveToPing`, `Defend`, `AttackTarget`, `Idle`, and `ToggleAirborneMode`.
- `Defend` clears `LockedTarget`, assigns the owner to `MasterTarget`, and enters `Defend`. `AttackTarget` assigns the crosshair target to `LockedTarget` and enters the same `Defend` state. Do not merge their semantics.
- Mode switching must leave the outer order unchanged. In particular, `Defend` remains `Defend`, retains `LockedTarget`, and continues to drive the Miniwyvern talent projectile contract.
- Use asset wiring wherever Hytale and current Tamework assets can express the behavior. Preserve Tamework's existing generic hook transport; do not introduce species checks into it.
- Load and follow `hytale-npc-asset-tools` before changing NPC JSON. Validate against the locked HyDragon `release-0.5.7` project profile and use generated/inspected asset fields rather than guessed ones.
- Preserve unrelated working-tree changes. Commit each completed implementation task in HyDragon; do not publish or install a build without a separate request.

## Files and ownership map

| Repository | Area | Responsibility |
| --- | --- | --- |
| Tamework | Existing `TriggerHook` / `TameworkHook` support | Verification-only dependency; no source changes planned. |
| HyDragon | `Server/Tamework/Items/Commands/HyDragonDragonHorn.json` | Exact Dragon Horn radial-menu command contract. |
| HyDragon | `Server/NPC/Roles/Creature/HyDragon/Components/` | Shared hook consumer plus native, state-preserving takeoff/landing transition. |
| HyDragon | `Template_Wyvern_Mini_Flying_Tamed.json` | Miniwyvern grounded/airborne behavior and talent-combat preservation. |
| HyDragon | `Template_HyDragon_Dragon_Tamed.json` | Nordic Drake grounded/airborne behavior; Hydra and Rock Drake roles remain ground-only. |
| HyDragon | `Server/Languages/*/server.lang`, `src/test/java/...` | Command text, static asset contracts, and regression coverage. |

---

## Task 1: Establish the hook dependency and a failing Horn contract

**Repository:** HyDragon. Tamework source is inspected and tested only.

**Files:**

- Create `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java`.
- Read, but do not modify, `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwCommandItemConfig.java`.
- Read, but do not modify, `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/items/CommandStepExecutionService.java`.
- Read, but do not modify, `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/npc/sensors/SensorTameworkHook.java`.

- [ ] Run Tamework's existing focused hook coverage first: `./mvnw -Dtest=TameworkHookComponentTest test` from `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework`. Confirm its current 3.0.0 implementation accepts `TriggerHook`, stamps a per-recipient request, and `TameworkHook` consumes that request once. If the installed 3.0.0 jar lacks this already-tested support, stop before editing HyDragon and rebuild the current Tamework source at the same version; do not design a replacement API.
- [ ] Write a JSON-parsing contract test before changing assets. It must load `HyDragonDragonHorn.json` and initially fail because the file inherits the parent array. Assert that its explicit `CommandList` has the exact eight IDs and order from the global constraints, that `SetHome` and `ReturnHome` are absent, and that `Follow` is the only default command.
- [ ] In the same failing test, assert the target semantics precisely: `Defend` clears `LockedTarget`, sets `MasterTarget` from `OwnerPlayer`, then sets state `Defend`; `AttackTarget` sets `LockedTarget` from `CrosshairTarget` with `AbortCommandForNpc`, then sets state `Defend` and does not overwrite `MasterTarget`.
- [ ] Assert that `ToggleAirborneMode` has one and only one step: `{ "Type": "TriggerHook", "HookId": "HyDragon.Command.ToggleAirborneMode" }`. The test must reject an inline custom flag payload or any state/target mutation in that command.
- [ ] Add catalog assertions for all five shipped locales (`en-US`, `de-DE`, `es-ES`, `fr-FR`, `pt-BR`) so the command names and HUD feedback keys exist in every catalog and all catalogs have the same relevant key set.
- [ ] Run `./mvnw -Dtest=DragonHornLocomotionAssetContractTest test` and record the expected initial failure before Task 2 changes any production asset.

## Task 2: Replace the Horn wheel and localize its new commands

**Repository:** HyDragon, after Task 1's failing test exists.

**Files:**

- Modify `Server/Tamework/Items/Commands/HyDragonDragonHorn.json`.
- Modify `Server/Languages/en-US/server.lang`.
- Modify `Server/Languages/de-DE/server.lang`.
- Modify `Server/Languages/es-ES/server.lang`.
- Modify `Server/Languages/fr-FR/server.lang`.
- Modify `Server/Languages/pt-BR/server.lang`.

- [ ] Retain the existing item binding, linked roster, ownership/tamed requirements, radius, and all existing role allowlists. Add a full local `CommandList`; do not rely on the inherited `TwCommandExample` array after this task.
- [ ] Copy the existing parent definitions for `Follow`, `Hold`, `Recall`, `MoveToPing`, `AttackTarget`, and `Idle` without changing their established feedback or step behavior, except for the explicit Attack Target contract required by Task 1. Do not carry `SetHome` or `ReturnHome` forward.
- [ ] Add `Defend` between `MoveToPing` and `AttackTarget`, with the distinct owner-defense sequence from Task 1 and its own Defend feedback particle/sound. Add `ToggleAirborneMode` after `Idle`, using only the canonical hook ID and toggle-specific feedback.
- [ ] Add localized text for Defend and Toggle Flight/Ground Mode (name plus HUD feedback) in all five catalogs. Use a stable HyDragon namespace such as `hydragon.commands.defend.*` and `hydragon.commands.toggleAirborneMode.*`; no command may display a raw localization key.
- [ ] Run `./mvnw -Dtest=DragonHornLocomotionAssetContractTest test`; it must pass before proceeding. Inspect the command wheel in the generated/effective asset view to confirm there are eight, not inherited-plus-local, entries.
- [ ] Commit as `Feat: configure dragon horn combat wheel`.

## Task 3: Add the shared native AirborneMode transition instruction

**Repository:** HyDragon. Load `hytale-npc-asset-tools` before editing NPC JSON.

**Files:**

- Create `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Airborne_Mode_Transition.json`.
- Modify `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json`.
- Modify `Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json`.
- Extend `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java`.

- [ ] Extend the test first and run it to fail. Require both templates to reference the shared transition component; require the component to reset `AirborneMode` to `false` through a once-per-role native `SetFlag`; and require it to consume exactly `HyDragon.Command.ToggleAirborneMode` with `TameworkHook`.
- [ ] Make the hook branch mutually exclusive: when the native `Flag` says `AirborneMode` is set, clear it; when it says the flag is not set, set it. Keep the hook single-consumption behavior supplied by current Tamework and do not use a second persistent component or Java service.
- [ ] Author the reusable controller-transition branch using only native asset actions/sensors: `AirborneMode=true` plus the grounded `Walk` controller issues `TakeOff`; `AirborneMode=false` plus the native `Fly` controller finds a safe downward landing position and issues `Land`. Reuse the proven `SearchRay`/`AdjustPosition` and `Land` field shape from `Template_HyDragon_Dragon.json`, including search-ray reset after touchdown.
- [ ] Ensure that this component contains no `State`, `ParentState`, target mutation, combat-clear, owner mutation, or Tamework flying-mode action. It is a controller transition only, so landing from Defend stays in Defend and retains its target.
- [ ] Add the component as a global, continuing instruction in both tamed templates before their order-specific behavior. It must run for Follow, Hold, Defend, and Idle rather than requiring separate toggle states.
- [ ] Use the asset tool to generate/inspect the supported `Flag`, `SetFlag`, `TameworkHook`, `TakeOff`, `Land`, `SearchRay`, and `AdjustPosition` shapes; lint and resolve the new component plus both templates. Then run the focused contract test.
- [ ] Commit as `Feat: add airborne mode transition`.

## Task 4: Make Miniwyvern orders select grounded or aerial movement without losing combat

**Repository:** HyDragon, after Task 3.

**Files:**

- Modify `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json`.
- Extend `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java`.
- Extend `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java` only where needed to protect the existing Defend-plus-`LockedTarget` projectile gate.

- [ ] Write failing asset assertions for the Miniwyvern template: Follow, Defend, Hold, and Idle each have mutually exclusive native `AirborneMode` branches; each branch has the intended `Walk` or `Fly` controller behavior; and no legacy branch can unconditionally take off or land while the flag says otherwise.
- [ ] Replace the current automatic Idle `TakeOff`/`Land` timeout cycle with a grounded Walk wander branch and an aerial Fly wander/hover branch. The shared transition component, not Idle state changes, owns mode changes.
- [ ] Split Follow into a grounded `Component_Tamework_Instruction_Follow_Advanced` branch and the existing aerial `Component_Tamework_Instruction_Follow_Flying` branch. Preserve the current Miniwyvern flight altitude, teleport threshold, slow-down, stop-distance, and hover tuning in the airborne branch; give the grounded branch matching owner slot, leash, and sensible walk-distance tuning.
- [ ] Split Defend into grounded and aerial `Component_Tamework_Instruction_Defend` variants. Keep all existing attack timing, detection, leash, and bite values in both. Set `DefendFollowMacroElement` to `Component_Tamework_Instruction_Follow_Advanced` when grounded and to `Component_Tamework_Instruction_Follow_Flying` when airborne. Do not touch the existing talent projectile instructions gated by `State=Defend` and `LockedTarget`.
- [ ] Replace the old Hold-to-`HoldGrounded` handoff with state-preserving branches: ground Hold is stationary on Walk and airborne Hold hovers on Fly. Remove the `TameworkSetFlyingCompanionMode` actions and obsolete `TakeOff`, `Land`, and `HoldGrounded` state wiring if no other instruction references them, because those paths change the outer order during a mode switch.
- [ ] Make the contract test reject `TameworkSetFlyingCompanionMode`, `State`, `SetTarget`, `ReleaseTarget`, or `ClearTarget` in Miniwyvern mode-transition logic. Keep a positive assertion that every talent projectile instruction still requires both `Defend` and `LockedTarget`.
- [ ] Run `./mvnw -Dtest=DragonHornLocomotionAssetContractTest,MiniwyvernTalentAssetWiringTest test`, then resolve/lint the template and all seven `Tamed_Wyvern_Mini_*.json` variants with the release-0.5.7 profile.
- [ ] Commit as `Feat: add miniwyvern flight modes`.

## Task 5: Give Nordic Drakes state-preserving flight modes

**Repository:** HyDragon, after Task 3.

**Files:**

- Modify `Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json`.
- Extend `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java`.

- [ ] Write failing assertions that the tamed full-dragon template exposes a native `Fly` motion controller in addition to its existing ride controllers, has a `FlightSpeed` parameter with the base-dragon default of `12`, references the shared transition component, and branches Follow, Defend, Hold, and Idle on `AirborneMode`.
- [ ] Copy the proven native `Fly` controller settings from `Template_HyDragon_Dragon.json` into the tamed template, parameterized by the new `FlightSpeed`. Retain `Walk`, `TameworkRideWalk`, `TameworkFly`, and `TameworkMountedGlide` unchanged so mounted behavior remains independent of the Horn mode flag.
- [ ] Preserve the existing grounded full-dragon Follow, Defend, Hold, and Idle blocks under `AirborneMode=false`. In particular, keep all current full-dragon combat, flock, sleep, and grounded follow behavior intact.
- [ ] Add aerial alternatives under `AirborneMode=true`: use `Component_Tamework_Instruction_Follow_Flying` for Follow; use the existing Defend configuration with its `DefendFollowMacroElement` switched to that flight follow component; use a Fly-controller hover for Hold without entering Sleep; and use a Fly-controller aerial wander/hover for Idle. Keep the `Ridden` state and mounted glide action unchanged.
- [ ] Do not turn a Horn toggle into `Ridden`, `Follow`, `Hold`, `Idle`, or any new flight-only parent state. The common transition component changes controller mode under the current order; the state branch changes only movement behavior.
- [ ] Resolve and lint the Nordic Drake template and `Tamed_NordicDrake`; assert `Tamed_Hydra` and all tamed Rock Drake tiers remain on their ground-only shared template. Run the focused contract test.
- [ ] Commit as `Feat: add dragon flight modes`.

## Task 6: Validate integration, package, and perform the in-game acceptance pass

**Repositories:** HyDragon, plus verification-only Tamework 3.0.0.

- [ ] Verify the pinned asset profile before final checks:

  ```bash
  python -m hytale_npc_assets.cli profile check --project-profile .hytale-npc-assets.json --json
  python -m hytale_npc_assets.cli lint --mod . --project-profile .hytale-npc-assets.json --resolved-validation --summary
  ```

  Resolve both changed templates and the Horn config with the same profile; treat unresolved component references, unsupported fields, or invalid role-flag use as blockers.
- [ ] Run HyDragon's focused tests, then its full verification suite:

  ```bash
  ./mvnw -Dtest=DragonHornLocomotionAssetContractTest,MiniwyvernTalentAssetWiringTest test
  python scripts/validate_assets.py
  ./mvnw verify
  ```

  Run `./mvnw verify` in the Tamework repository as a dependency regression check, with no source changes expected there.
- [ ] Package HyDragon with `./mvnw package` and inspect the artifact to ensure the Horn config, shared transition component, both templates, and all five language catalogs are present. Do not copy the jar into `UserData/Mods` unless installation is explicitly requested.
- [ ] In game, use the Dragon Horn with one Miniwyvern and one Nordic Drake. Confirm the wheel has exactly the intended eight entries, Defend independently protects the owner, and Attack Target locks and pursues the crosshair target. Confirm Hydra and Rock Drake remain ground-only.
- [ ] For Follow, Hold, Defend, and Idle, toggle Flight/Ground while the companion is already in that order. Verify: the state and target remain unchanged; grounded mode lands and uses walking behavior; airborne mode takes off and flies/hovers; and a Defending Miniwyvern still fires its talent-gated projectile when the talent is unlocked.
- [ ] Dismiss and re-summon each representative while it was last airborne. Confirm every new projection begins grounded. Also test the landing fallback over uneven terrain: it may continue seeking a safe landing position, but it must not discard its target or switch orders.
- [ ] Review `git diff --check` and `git status --short`. Commit any verification-only test or packaging fix separately as `Test: verify dragon horn flight modes`.

## Final acceptance checklist

- [ ] The Dragon Horn explicitly defines the eight-command wheel, with Defend and Attack Target distinct and neither home command present.
- [ ] Toggling uses the existing Tamework hook bridge and the vanilla `AirborneMode` flag only; no new Tamework Java API or persistent flag store exists.
- [ ] Every newly summoned Miniwyvern and Nordic Drake starts grounded; Hydra and Rock Drake remain ground-only.
- [ ] Follow, Hold, Defend, and Idle switch only their movement style. Toggling does not change state, clear targets, or overwrite the owner target.
- [ ] Miniwyvern projectile talents remain active only through Defend plus LockedTarget, in both grounded and aerial movement branches.
- [ ] Nordic Drake and all seven Miniwyvern forms inherit valid grounded and airborne behavior; Hydra and Rock Drake remain ground-only from their shared template.
- [ ] Assets validate against Hytale 0.5.7, test suites pass, language keys are present in all shipped locales, and the packaged artifact contains every modified asset.
