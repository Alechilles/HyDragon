# HyDragon Player-Reported Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct Toxic Hydra combat/flight/VFX, Rock Drake sleep/mount/immunity behavior, and the downloaded dragon texture set.

**Architecture:** Make narrow data-driven changes following existing Nordic flight and Hytale 0.5.7 balance-asset patterns. Preserve unrelated species behavior through a defaulted sleep-only template parameter, and validate assets rather than adding source-shape tests.

**Tech Stack:** Hytale 0.5.7 NPC JSON, ModelVFX/entity effects, PNG textures, Gradle, `hytale-assets`.

## Global Constraints

- Work only in `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.worktrees/player-reported-dragon-issues`.
- Preserve the original checkout's uncommitted manifest and Rock Drake drop-table changes.
- Fire Miniwyvern Burn remains owner-only and requires `Miniwyvern_Fire_EmberBond`.
- `Wyvern Texture ByYasmim.png` is the Nature texture; do not use `WyverNature.png`.
- Do not add source-text, file-presence, or texture-inventory tests.

---

### Task 1: Correct Toxic Hydra flight and projectiles

**Files:**
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json`
- Modify: `src/main/resources/Server/NPC/Balancing/CAE_Hydra_Toxic_Aerial.json`

**Interfaces:**
- Consumes: existing `Root_NPC_Hydra_Toxic_Aerial_Spit` and Nordic Drake flight parameter names.
- Produces: wild Toxic Hydra aerial attacks that launch `Hydra_Toxic_Ball` and use stable movement tuning.

- [ ] Replace the four role-level ice roots with `Root_NPC_Hydra_Toxic_Aerial_Spit`.
- [ ] Add Nordic's ten explicit aerial movement/recovery overrides to `Hydra_Toxic.json`.
- [ ] Replace both CAE `Root_NPC_Hydra_Ice_Ball` entries with `Root_NPC_Hydra_Toxic_Aerial_Spit`.
- [ ] Validate `Hydra_Toxic` with the exact release-0.5.7 profile.
- [ ] Commit as `Fix: correct Toxic Hydra aerial combat`.

### Task 2: Correct Rock Drake sleep, mount speed, and immunity

**Files:**
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/Templates/Template_Hydra_Intelligent.json`
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/RockDrake/RockDrake.json`
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/RockDrake/RockDrakeT1.json`
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/RockDrake/RockDrakeT2.json`
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/RockDrake/RockDrakeT3.json`
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/RockDrake/Tamed_RockDrakeT1.json`
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/RockDrake/Tamed_RockDrakeT2.json`
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/RockDrake/Tamed_RockDrakeT3.json`
- Modify: `src/main/resources/Server/NPC/Balancing/CAE_RockDrake.json`

**Interfaces:**
- Consumes: `Component_Instruction_Wild_Sleep_State`, `Component_ActionList_Wake`, and Hytale `Immunity_Fire`.
- Produces: `SleepHearingRange` defaulting to `HearingRange`, Rock Drake override `2`, tamed speed `3.5`, and spawn-time fire/lava immunity.

- [ ] Add `SleepHearingRange` after `HearingRange` in the template parameters with computed default `{ "Compute": "HearingRange" }`.
- [ ] Use `SleepHearingRange * 2` for sleeping `Flock_Attack` beacon range and `SleepHearingRange` for the wild sleep component.
- [ ] Add `Combat` and `Search` to the existing Sleep exit transition's destinations.
- [ ] Set `SleepHearingRange` to `2` in all four wild Rock Drake roles.
- [ ] Set `MaxSpeed` to `3.5` only in all three tamed tier roles.
- [ ] Add top-level `"EntityEffect": "Immunity_Fire"` to `CAE_RockDrake` beside its type/target-memory fields.
- [ ] Validate all affected Rock Drake roles and reverse-reference consumers with release-0.5.7 affected scope.
- [ ] Commit as `Fix: stabilize Rock Drake sleep and movement`.

### Task 3: Shorten HyDragon's desummon animation

**Files:**
- Modify: `src/main/resources/Server/Entity/ModelVFX/HyDragon_Desummon.json`
- Modify: `src/main/resources/Server/Entity/Effects/Status/HyDragon_Dragon_Desummon.json`

**Interfaces:**
- Consumes: Tamework's five-second model-effect trigger from the companion plan.
- Produces: a five-second disappearance animation/effect ending at lease expiry.

- [ ] Change both `AnimationDuration` and status `Duration` from `30` to `5`.
- [ ] Run `python scripts/validate_assets.py` and confirm both JSON documents parse.
- [ ] Commit as `Fix: limit dragon desummon fade to five seconds`.

### Task 4: Import corrected textures under HyDragon names

**Files:**
- Create: `src/main/resources/Common/NPC/HyDragon/Hydra_Winged/Model/Toxic.png`
- Modify: `src/main/resources/Server/Models/HyDragon/Hydra_Winged/Hydra_Winged.json`
- Modify: `src/main/resources/Server/Models/HyDragon/Hydra_Winged/Hydra_Winged_AvatarFlight.json`
- Modify: five Nordic PNG files and seven Miniwyvern PNG files listed in the design.

**Interfaces:**
- Consumes: the three downloaded source artifacts and the approved filename map.
- Produces: only repo-convention texture paths referenced by existing model assets.

- [ ] Extract both archives to temporary directories outside the repository.
- [ ] Copy `Acid.png` to winged Hydra `Toxic.png` and update both model JSON texture references.
- [ ] Copy the five Nordic textures using unchanged destination basenames.
- [ ] Copy the seven Miniwyvern textures, mapping ByYasmim to Nature.
- [ ] Verify PNG signatures/dimensions, model texture references, and absence of archive source names in runtime JSON.
- [ ] Commit as `Art: update dragon and Miniwyvern textures`.

### Task 5: Integrated verification

**Files:**
- No production edits expected.

**Interfaces:**
- Consumes: Tasks 1-4 plus the committed Tamework timing change.
- Produces: static/build evidence and an explicit live-test gap list.

- [ ] Run `python scripts/validate_assets.py`.
- [ ] Run exact-profile affected-scope `hytale-assets author check --changed` or the supported equivalent for the committed candidate.
- [ ] Run `./gradlew test --no-daemon` and `./gradlew build --no-daemon`.
- [ ] Review `git diff origin/main...HEAD` and confirm the original checkout remains unchanged.
- [ ] Record live gaps for Hydra flight, Rock Drake wake/physics, mount feel, and Fire Ember Bond activation.
