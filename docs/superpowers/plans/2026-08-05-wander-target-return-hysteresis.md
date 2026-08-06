# Wander-Target Return Hysteresis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every autonomous `WANDER_TARGET` flight return toward its current target after crossing the maximum wander radius and resume wandering only after reaching the minimum radius.

**Architecture:** `BodyMotionTameworkFlyingOrbit` owns a boolean return latch and updates it from horizontal target distance plus `WanderRadiusRange`. While latched, existing target-directed approach steering replaces random-waypoint steering at the configured `RelativeSpeed`; altitude correction and obstacle avoidance then process that translation normally.

**Tech Stack:** Java 25, Hytale server/NPC API 0.5.7, JOML, JUnit 5, Gradle.

## Global Constraints

- Apply to every `WANDER_TARGET` consumer, including follow and combat flight.
- Follow targets the owner/player; combat targets the current combat target.
- Use horizontal distance. Enter only beyond `WanderRadiusRange[1]`; remain latched until at or inside `WanderRadiusRange[0]`.
- Use the existing `RelativeSpeed`; add no speed setting.
- Preserve target-relative altitude correction and autonomous obstacle avoidance.
- Do not change target slots, combat states, engagement logic, teleport thresholds, builder IDs, other flight modes, rider behavior, or HyDragon NPC assets.
- Implement on the existing Tamework branch `feat/autonomous-flight-obstacle-avoidance`.

---

### Task 1: Add Wander-Target Return State and Steering

**Files:**
- Modify: `src/main/java/com/alechilles/alecstamework/npc/movement/BodyMotionTameworkFlyingOrbit.java`
- Test: `src/test/java/com/alechilles/alecstamework/npc/movement/BodyMotionTameworkFlyingOrbitTest.java`

**Interfaces:**
- Consumes: `wanderRadiusRange`, `relativeSpeed`, `desiredAltitudeRange`, `resolveApproachTranslation(...)`, and the existing final obstacle-avoidance pass.
- Produces: `static boolean updateWanderReturnState(boolean returning, double horizontalDistanceSquared, double minimumRadius, double maximumRadius)` and one per-instance `returningToWanderTarget` latch.

- [ ] **Step 1: Write failing transition tests**

Add to `BodyMotionTameworkFlyingOrbitTest`:

```java
@Test
void wanderReturnStartsOnlyBeyondMaximumRadius() {
    assertFalse(BodyMotionTameworkFlyingOrbit.updateWanderReturnState(false, 18.0 * 18.0, 12.0, 18.0));
    assertTrue(BodyMotionTameworkFlyingOrbit.updateWanderReturnState(false, 18.01 * 18.01, 12.0, 18.0));
}

@Test
void wanderReturnStaysLatchedUntilMinimumRadius() {
    assertTrue(BodyMotionTameworkFlyingOrbit.updateWanderReturnState(true, 15.0 * 15.0, 12.0, 18.0));
    assertFalse(BodyMotionTameworkFlyingOrbit.updateWanderReturnState(true, 12.0 * 12.0, 12.0, 18.0));
    assertFalse(BodyMotionTameworkFlyingOrbit.updateWanderReturnState(true, 11.99 * 11.99, 12.0, 18.0));
}

@Test
void equalWanderRadiiUseOneStableBoundary() {
    assertFalse(BodyMotionTameworkFlyingOrbit.updateWanderReturnState(false, 12.0 * 12.0, 12.0, 12.0));
    assertTrue(BodyMotionTameworkFlyingOrbit.updateWanderReturnState(false, 12.01 * 12.01, 12.0, 12.0));
    assertFalse(BodyMotionTameworkFlyingOrbit.updateWanderReturnState(true, 12.0 * 12.0, 12.0, 12.0));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
bash gradlew test --tests com.alechilles.alecstamework.npc.movement.BodyMotionTameworkFlyingOrbitTest
```

Expected: compilation fails because `updateWanderReturnState(...)` does not exist.

- [ ] **Step 3: Implement the transition helper and latch**

Add:

```java
private boolean returningToWanderTarget;

static boolean updateWanderReturnState(boolean returning,
                                       double horizontalDistanceSquared,
                                       double minimumRadius,
                                       double maximumRadius) {
    double boundary = returning ? minimumRadius : maximumRadius;
    return horizontalDistanceSquared > boundary * boundary;
}
```

Reset the latch in `activate(...)` and both early-exit paths that reset obstacle avoidance.

- [ ] **Step 4: Route `WANDER_TARGET` through the latch**

At the start of the wandering branch, measure current horizontal target distance and call the helper. When latched:

```java
hasWanderDestination = false;
resolveApproachTranslation(
        selfPosition.x(), selfPosition.z(),
        targetPosition.x(), targetPosition.z(),
        wanderRadiusRange[0], wanderRadiusRange[0],
        relativeSpeed, translation);
```

When not latched, retain `updateWanderDestination(...)` and `resolveWaypointTranslation(...)`. Keeping `hasWanderDestination` false during return forces a fresh target-relative waypoint after reaching the minimum radius.

- [ ] **Step 5: Preserve altitude correction and avoidance**

Change the altitude condition to:

```java
if ((!wandering || returningToWanderTarget) && !passingThrough) {
    altitudeCorrection = resolveTargetRelativeAltitudeCorrection(...);
    translation.y = altitudeCorrection;
}
```

Do not add another avoidance path. The existing final `obstacleAvoidance.adjust(...)` call processes the completed return translation.

- [ ] **Step 6: Run focused tests and verify GREEN**

```bash
bash gradlew test \
  --tests com.alechilles.alecstamework.npc.movement.BodyMotionTameworkFlyingOrbitTest \
  --tests com.alechilles.alecstamework.npc.movement.FlyingObstacleAvoidanceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Validate engine references**

Run Hytale Workshop `validate_hytale_code_refs` against the full changed `BodyMotionTameworkFlyingOrbit.java` with version `0.5.7`.

Expected: zero missing or deprecated references and resolved version `0.5.7`.

- [ ] **Step 8: Run safety checks and the full suite**

```bash
rg "PlayerRef\.getComponent\(Player|getComponent\(Player\.getComponentType\(\)\)|Universe\.get\(\).*getPlayers" -n src/main/java
bash gradlew test
git diff --check
```

Expected: no new player-thread access, `BUILD SUCCESSFUL`, and no whitespace errors. Restore build-generated `manifest.json` whitespace churn without committing it.

- [ ] **Step 9: Commit**

```bash
git add \
  src/main/java/com/alechilles/alecstamework/npc/movement/BodyMotionTameworkFlyingOrbit.java \
  src/test/java/com/alechilles/alecstamework/npc/movement/BodyMotionTameworkFlyingOrbitTest.java
git commit -m "Feat: return distant flying wanderers to target"
```

- [ ] **Step 10: Review the final branch**

Verify `git status --short --branch`, `git diff --check main...HEAD`, and the branch commit list. Confirm no HyDragon role or combat assets changed.
