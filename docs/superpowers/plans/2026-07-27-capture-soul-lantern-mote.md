# Capture Soul Lantern Mote Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the dragon-capture homing mote's directional-ribbon particles with the exact Soul Lantern particle presentation.

**Architecture:** The existing `HyDragon_DragonStone_Capture_Mote` model remains the moving entity and keeps its `HyDragon_DragonStone_CaptureMote` attachment. That system changes from three custom directional spawners to two HyDragon-named copies of Tamework's Soul Lantern `Fireflies` and `Fireflies_Start` spawners.

**Tech Stack:** Hytale JSON particle assets, Maven/JUnit, `scripts/validate_assets.py`.

## Global Constraints

- Preserve the existing capture model attachment, homing path, capture timings, and beam fallback.
- Copy every Soul Lantern particle value verbatim; only the source asset IDs become HyDragon-prefixed IDs.
- Keep HyDragon runtime assets self-contained; no `Tamework_Soul_Lantern` runtime reference.
- Remove the unused third capture-mote spawner and update the focused contract test.
- Deploy only with the local `install-plugin` Maven profile after verification passes.

---

### Task 1: Replace the capture mote particle system

**Files:**
- Modify: `Server/Particles/HyDragon/DragonStone/HyDragon_DragonStone_CaptureMote.particlesystem`
- Modify: `Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Particle.particlespawner`
- Modify: `Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Trail.particlespawner`
- Delete: `Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Sparks.particlespawner`
- Modify: `src/test/java/com/alechilles/hydragon/integration/CaptureEnergyTetherAssetContractTest.java`

**Interfaces:**
- Consumes: `HyDragon_DragonStone_Capture_Mote` model attachment with `SystemId: HyDragon_DragonStone_CaptureMote`.
- Produces: Two-spawner `HyDragon_DragonStone_CaptureMote` system that is identical in behavior to Tamework's `Tamework_Soul_Lantern`.

- [ ] **Step 1: Update the focused contract test before asset edits**

Replace the directional-ribbon assertions with assertions that the particle system contains exactly `HyDragon_DragonStone_CaptureMote_Particle` and `HyDragon_DragonStone_CaptureMote_Trail`; both spawners use `Particles/Textures/Basic/Glow.png`; both use `Particles/Textures/UVMotion/FlowMap4.png`; both have the Soul Lantern attractor values; and the Spark spawner file is absent.

- [ ] **Step 2: Run the focused test to verify the old particle implementation fails the new contract**

Run: `./mvnw -Dtest=CaptureEnergyTetherAssetContractTest test`

Expected: FAIL because the current mote system still has a third spark spawner and directional textures.

- [ ] **Step 3: Copy the Soul Lantern system structure and source values into HyDragon assets**

Set the particle system's spawners, in order, to `HyDragon_DragonStone_CaptureMote_Particle` and `HyDragon_DragonStone_CaptureMote_Trail`, with the second spawner retaining `PositionOffset: { "Y": 0 }`. Replace the first HyDragon spawner body with Tamework `Tamework_Soul_Lantern_Fireflies`, replacing only its asset ID; replace the second body with Tamework `Tamework_Soul_Lantern_Fireflies_Start`, replacing only its asset ID; remove the Spark spawner file.

- [ ] **Step 4: Run focused validation and the focused test**

Run: `python scripts/validate_assets.py && ./mvnw -Dtest=CaptureEnergyTetherAssetContractTest test`

Expected: asset validation succeeds and the focused test passes.

- [ ] **Step 5: Commit the isolated feature change**

Run: `git add Server/Particles/HyDragon/DragonStone src/test/java/com/alechilles/hydragon/integration/CaptureEnergyTetherAssetContractTest.java && git commit -m "Feat: use soul lantern capture mote"`

### Task 2: Verify and locally deploy the changed artifact

**Files:**
- Test: Packaged `HyDragon` jar produced by Maven.

**Interfaces:**
- Consumes: Task 1 commit and local Maven install profile.
- Produces: Identical installed artifacts in the release server-mod and UserData mod locations.

- [ ] **Step 1: Run the full verification suite**

Run: `./mvnw verify`

Expected: Maven verification succeeds, including asset validation and all tests.

- [ ] **Step 2: Build and install locally**

Run: `./mvnw package -Pinstall-plugin`

Expected: Maven copies the built HyDragon jar to both local Hytale mod locations.

- [ ] **Step 3: Verify the installed artifact content and hashes**

Run: `sha256sum target/*.jar "/c/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods/HyDragon v0.2.1.jar" "/c/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v0.2.1.jar"`

Expected: all three hashes match; `jar tf target/*.jar | rg "HyDragon_DragonStone_CaptureMote_(Particle|Trail)\\.particlespawner"` lists the two Soul Lantern spawners and does not list `CaptureMote_Sparks`.
