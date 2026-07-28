# Capture Energy Tether Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make dragon capture read as smooth magical energy flowing from the dragon to the player instead of a cloud of discrete motes.

**Architecture:** The existing target-to-player capture beam remains the continuous core tether. The homing-mote VFX becomes a small number of long-lived, elongated `Glow_Direction2` ribbon traces, with a sparse endpoint sparkle accent. The Tamework capture interaction and all gameplay IDs remain unchanged.

**Tech Stack:** Hytale particle-system JSON, particle-spawner JSON, Maven/JUnit asset-contract tests, HyDragon asset validator.

## Global Constraints

- Keep `HyDragon_DragonStone_CaptureBeam`, `HyDragon_DragonStone_CaptureMote`, and the existing capture interaction IDs unchanged.
- Keep `BeamFromTarget: true`, the 3-second channel, capture chance, target validation, roster behavior, and item IDs unchanged.
- Use only existing shipped particle textures: `Particles/Textures/Basic/Glow_Direction2.png` and `Particles/Textures/Basic/Spark_Direction.png`.
- Preserve the `HyDragon_DragonStone_Capture` bottom-up dissolve VFX as the completion effect.

---

### Task 1: Lock the tether asset contract

**Files:**
- Create: `src/test/java/com/alechilles/hydragon/integration/CaptureEnergyTetherAssetContractTest.java`
- Test: `src/test/java/com/alechilles/hydragon/integration/CaptureEnergyTetherAssetContractTest.java`

**Interfaces:**
- Consumes: Capture item JSON and the three spawner JSON files under `Server/Particles/HyDragon/DragonStone/Spawners/`.
- Produces: A regression gate proving the capture remains target-to-player and uses directional ribbon assets rather than circular mote sprites.

- [ ] **Step 1: Write the failing test**

```java
@Test
void capturePresentationUsesAContinuousTetherAndDirectionalRibbons() throws Exception {
    String item = read("Server/Item/Items/Ingredient/Draconic_Stone.json");
    String mote = read("Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Particle.particlespawner");
    String trail = read("Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Trail.particlespawner");
    String sparks = read("Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Sparks.particlespawner");
    assertTrue(item.contains("\"BeamFromTarget\": true"));
    assertTrue(item.contains("\"HomingProjectileSpawnIntervalSeconds\": 0.2"));
    assertTrue(item.contains("\"HomingProjectileMaxConcurrent\": 6"));
    assertTrue(mote.contains("Particles/Textures/Basic/Glow_Direction2.png"));
    assertTrue(trail.contains("Particles/Textures/Basic/Glow_Direction2.png"));
    assertFalse(mote.contains("Particles/Textures/Circles/Circle_Glow.png"));
    assertTrue(sparks.contains("\"Min\": 3.0"));
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=CaptureEnergyTetherAssetContractTest test`

Expected: FAIL because the current capture configuration emits dense circular motes at `0.08` seconds with up to `20` concurrent projectiles.

- [ ] **Step 3: Commit the regression test**

```bash
git add src/test/java/com/alechilles/hydragon/integration/CaptureEnergyTetherAssetContractTest.java
git commit -m "Test: lock capture energy tether presentation"
```

### Task 2: Retune the capture presentation as a smooth tether

**Files:**
- Modify: `Server/Item/Items/Ingredient/Draconic_Stone.json`
- Modify: `Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Particle.particlespawner`
- Modify: `Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Trail.particlespawner`
- Modify: `Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Sparks.particlespawner`
- Test: `src/test/java/com/alechilles/hydragon/integration/CaptureEnergyTetherAssetContractTest.java`

**Interfaces:**
- Consumes: The capture interaction's existing beam and homing-projectile references.
- Produces: A lower-density stream of overlapping directional ribbons travelling from dragon to player.

- [ ] **Step 1: Apply the capture-flow limits**

```json
"HomingProjectileSpawnIntervalSeconds": 0.2,
"HomingProjectileMaxConcurrent": 6
```

Keep `BeamFromTarget`, homing speed, turn rate, arrival radius, and channel duration unchanged.

- [ ] **Step 2: Convert the primary mote into a ribbon**

Set the primary mote texture to `Glow_Direction2.png`, use `BlendAdd`, set its lifetime to `0.30`–`0.42` seconds, spawn at `12` particles/second, and animate it as a narrow, long cyan ribbon:

```json
"Scale": {
  "X": { "Min": 0.07, "Max": 0.11 },
  "Y": { "Min": 0.55, "Max": 0.85 }
}
```

- [ ] **Step 3: Convert the trail into the continuous overlap layer**

Keep `Glow_Direction2.png`, set its lifetime to `0.38`–`0.52` seconds, spawn at `18` particles/second, and animate it from bright cyan (`#CFF8FF`) to deep blue (`#2F69FF`) while fading to zero. Use the same narrow/long scale profile as the primary mote, at a slightly larger scale.

- [ ] **Step 4: Reduce the sparkle accent**

Keep `Spark_Direction.png`, set its lifetime to `0.14`–`0.22` seconds, spawn at `3` particles/second, and reduce concurrent particles to `2`. Preserve its endpoint-local offset and fade it fully by frame `100`.

- [ ] **Step 5: Run the focused regression test**

Run: `./mvnw -q -Dtest=CaptureEnergyTetherAssetContractTest test`

Expected: PASS.

- [ ] **Step 6: Commit the presentation change**

```bash
git add Server/Item/Items/Ingredient/Draconic_Stone.json \
  Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Particle.particlespawner \
  Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Trail.particlespawner \
  Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Sparks.particlespawner
git commit -m "Feat: smooth dragon capture energy tether"
```

### Task 3: Verify the complete packaged effect

**Files:**
- Verify: `Server/Particles/HyDragon/DragonStone/HyDragon_DragonStone_CaptureBeam.particlesystem`
- Verify: `Server/Particles/HyDragon/DragonStone/HyDragon_DragonStone_CaptureMote.particlesystem`
- Verify: `Server/Entity/ModelVFX/HyDragon_DragonStone_Capture.json`

**Interfaces:**
- Consumes: The retuned spawners and unchanged capture-system/model-VFX references.
- Produces: A package that contains all particle assets and retains the dissolve completion VFX.

- [ ] **Step 1: Validate asset references and JSON**

Run: `python scripts/validate_assets.py`

Expected: `HyDragon asset validation passed` with no missing particle-system or spawner reference.

- [ ] **Step 2: Run the full package verification**

Run: `./mvnw verify`

Expected: `BUILD SUCCESS`, the focused tether test passes, and packaged-jar integration tests pass.

- [ ] **Step 3: Inspect the package contents**

Run: `unzip -l "target/HyDragon v0.2.1.jar" "Server/Particles/HyDragon/DragonStone/*"`

Expected: The capture beam system, capture mote system, all three retuned mote spawners, and the capture model-VFX asset are present.

## Plan Self-Review

- **Spec coverage:** Tasks 1 and 2 implement the continuous core plus flowing ribbon and restrained endpoint-accent requirements; Task 3 verifies unchanged IDs, references, packaging, and the dissolve endpoint.
- **Placeholder scan:** No unresolved placeholders or deferred implementation steps remain.
- **Consistency:** The regression test asserts the exact interaction values and directional texture choices introduced by Task 2.
