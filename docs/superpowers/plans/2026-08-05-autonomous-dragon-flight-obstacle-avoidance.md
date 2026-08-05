# Autonomous Dragon Flight Obstacle Avoidance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add collider-aware, performance-bounded obstacle avoidance to autonomous `TameworkFlyingOrbit` movement without altering rider-controlled flight.

**Architecture:** A package-private `FlyingObstacleAvoidance` helper owns lookahead calculation, a six-probe decision budget, the fixed climb/left/right/diagonal fan, candidate scoring, cadence, and side hysteresis. `BodyMotionTameworkFlyingOrbit` computes its existing 3D steering first, then delegates only autonomous movement to the helper through a reusable engine-probe adapter backed by Hytale 0.5.7 `MotionControllerFly.probeMove`. The existing builder ID and modes remain stable; a default-on `AvoidObstacles` field provides an opt-out without HyDragon asset edits.

**Tech Stack:** Java 25, JOML `Vector3d`, Hytale 0.5.7 NPC movement API, Gradle, JUnit 5.

## Global Constraints

- Scope is autonomous wild and tamed NPC flight that uses `TameworkFlyingOrbit`.
- Rider-controlled flight must receive zero avoidance probes and no steering changes.
- Skip avoidance when either `TameworkRideMountComponent` or native `NPCMountComponent` is present.
- Preserve the public builder ID `TameworkFlyingOrbit` and all existing mode names.
- Use `MotionControllerFly.probeMove` with reusable `ProbeMoveData`; do not raycast or manually scan blocks.
- Clear flight is limited to one probe per 0.10 seconds per moving NPC.
- A blocked decision is limited to one primary and five alternate probes.
- A full fan is held for 0.35 seconds to prevent left/right oscillation.
- A single steering update may perform no more than six total probes, including wander preflight.
- Reuse vectors, probe data, candidate storage, and the probe adapter; allocate nothing in the per-tick avoidance path.
- Keep HyDragon's existing blocked recovery as the final fallback.
- Do not modify HyDragon NPC JSON, mounted controllers, mounted input, avatar flight, or mounted collision recovery.

---

### Task 1: Bounded obstacle planner

**Files:**
- Create: `src/main/java/com/alechilles/alecstamework/npc/movement/FlyingObstacleAvoidance.java`
- Create: `src/test/java/com/alechilles/alecstamework/npc/movement/FlyingObstacleAvoidanceTest.java`

**Interfaces:**
- Consumes: JOML vectors and a package-private `Probe` functional interface returning the traversable distance for a requested probe vector.
- Produces: `beginUpdate(double dt)`, `adjust(Vector3d desired, Vector3d horizontalReference, double maximumSpeed, double turnRadius, Probe probe, Vector3d output)`, `probeWaypoint(Vector3d route, double maximumSpeed, double turnRadius, Probe probe)`, `reset()`, and package-private probe-count access for tests.

- [ ] **Step 1: Write failing lookahead and clear-route tests**

```java
@Test
void lookaheadClampsSlowAndFastFlight() {
    assertEquals(4.0, FlyingObstacleAvoidance.lookaheadDistance(0.1, 1.0, 0.0), EPSILON);
    assertEquals(12.0, FlyingObstacleAvoidance.lookaheadDistance(2.0, 20.0, 8.0), EPSILON);
}

@Test
void clearRouteUsesOneProbeAndPreservesDesiredTranslation() {
    FlyingObstacleAvoidance avoidance = new FlyingObstacleAvoidance();
    avoidance.beginUpdate(0.1);
    Vector3d result = avoidance.adjust(
            new Vector3d(0.5, 0.0, 0.0), new Vector3d(1.0, 0.0, 0.0),
            12.0, 0.0, direction -> direction.length(), new Vector3d());
    assertEquals(new Vector3d(0.5, 0.0, 0.0), result);
    assertEquals(1, avoidance.getProbesThisUpdate());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `bash gradlew test --tests com.alechilles.alecstamework.npc.movement.FlyingObstacleAvoidanceTest`

Expected: compilation failure because `FlyingObstacleAvoidance` does not exist.

- [ ] **Step 3: Implement lookahead, update budget, primary probing, and reusable storage**

```java
final class FlyingObstacleAvoidance {
    static final double PROBE_INTERVAL_SECONDS = 0.10;
    static final double HOLD_SECONDS = 0.35;
    static final int MAX_PROBES_PER_UPDATE = 6;
    private static final double MIN_LOOKAHEAD = 4.0;
    private static final double MAX_LOOKAHEAD = 12.0;
    private static final double LOOKAHEAD_SECONDS = 0.75;

    @FunctionalInterface
    interface Probe {
        double probe(@Nonnull Vector3d direction);
    }

    static double lookaheadDistance(double desiredMagnitude, double maximumSpeed, double turnRadius) {
        double distance = Math.max(0.0, turnRadius)
                + Math.max(0.0, maximumSpeed) * Math.min(2.0, Math.max(0.0, desiredMagnitude))
                * LOOKAHEAD_SECONDS;
        return Math.max(MIN_LOOKAHEAD, Math.min(MAX_LOOKAHEAD, distance));
    }
}
```

`adjust` normalizes the desired vector into a reusable probe vector, multiplies it by the calculated lookahead, invokes the probe only when the 0.10-second cadence is due, and returns the untouched desired vector when the reported distance completes the corridor.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `bash gradlew test --tests com.alechilles.alecstamework.npc.movement.FlyingObstacleAvoidanceTest`

Expected: both tests pass.

- [ ] **Step 5: Add failing fan, budget, vertical, partial-clearance, and hysteresis tests**

Add separate tests whose literal probe responses prove:

```java
assertTrue(result.y > 0.0);                 // climb wins
assertTrue(result.z < 0.0);                 // left wins
assertTrue(result.z > 0.0);                 // right wins
assertTrue(result.x > 0.0 && result.y > 0); // diagonal wins
assertTrue(result.y >= 0.0);                // no alternate descends
assertTrue(result.length() < desired.length()); // partial clearance slows
assertEquals(0.0, result.lengthSquared(), EPSILON); // below 25% stops
assertTrue(avoidance.getProbesThisUpdate() <= 6);
```

Use a counting `Probe` that returns distances from a literal `double[]`, then call `beginUpdate(0.05)` during the hold and assert that no second full fan is requested and the selected side remains stable.

- [ ] **Step 6: Run the expanded test and verify RED**

Run: `bash gradlew test --tests com.alechilles.alecstamework.npc.movement.FlyingObstacleAvoidanceTest`

Expected: fan-selection and hold assertions fail because only primary probing exists.

- [ ] **Step 7: Implement the fixed fan and deterministic scoring**

Use five preallocated candidate vectors and side metadata `{0, -1, 1, -1, 1}`. Build candidates at climb 35 degrees, left/right 45 degrees, and climb-left/right 25/45 degrees. Clamp alternate pitch to level or upward. Compare candidates lexicographically by full clearance, clearance fraction, alignment with the original direction, then remembered side. Store the selected normalized direction, speed scale, side, 0.35-second hold, and 0.35-second fan cooldown.

When the best clearance fraction is below `0.25`, return zero translation. Otherwise scale the original desired magnitude by the selected clearance fraction. During the hold, validate only the cached route on cadence; do not rebuild the fan before the fan cooldown expires.

- [ ] **Step 8: Run the expanded test and verify GREEN**

Run: `bash gradlew test --tests com.alechilles.alecstamework.npc.movement.FlyingObstacleAvoidanceTest`

Expected: all helper tests pass with no more than six probes per update.

- [ ] **Step 9: Add failing waypoint-budget tests**

```java
@Test
void waypointProbeSharesTheSixProbeUpdateBudget() {
    FlyingObstacleAvoidance avoidance = new FlyingObstacleAvoidance();
    avoidance.beginUpdate(0.1);
    for (int i = 0; i < 3; i++) {
        avoidance.probeWaypoint(new Vector3d(12.0, 0.0, 0.0), 12.0, 0.0, d -> 0.0);
    }
    avoidance.adjust(new Vector3d(1.0, 0.0, 0.0), new Vector3d(1.0, 0.0, 0.0),
            12.0, 0.0, d -> 0.0, new Vector3d());
    assertTrue(avoidance.getProbesThisUpdate() <= 6);
}
```

- [ ] **Step 10: Run the waypoint test and verify RED**

Run: `bash gradlew test --tests com.alechilles.alecstamework.npc.movement.FlyingObstacleAvoidanceTest`

Expected: compilation failure because `probeWaypoint` is missing.

- [ ] **Step 11: Implement waypoint corridor probing with the shared budget**

`probeWaypoint` uses the same lookahead formula, increments the same per-update counter, returns a clearance fraction in `[0, 1]`, and returns `0` without invoking the probe after the six-probe budget is exhausted.

- [ ] **Step 12: Run the helper test and commit**

Run: `bash gradlew test --tests com.alechilles.alecstamework.npc.movement.FlyingObstacleAvoidanceTest`

Expected: all tests pass.

```bash
git add src/main/java/com/alechilles/alecstamework/npc/movement/FlyingObstacleAvoidance.java \
  src/test/java/com/alechilles/alecstamework/npc/movement/FlyingObstacleAvoidanceTest.java
git commit -m "Feat: add bounded flying obstacle planner"
```

### Task 2: Wire autonomous `TameworkFlyingOrbit`

**Files:**
- Modify: `src/main/java/com/alechilles/alecstamework/npc/movement/BuilderBodyMotionTameworkFlyingOrbit.java`
- Modify: `src/main/java/com/alechilles/alecstamework/npc/movement/BodyMotionTameworkFlyingOrbit.java`
- Modify: `src/test/java/com/alechilles/alecstamework/npc/movement/BodyMotionTameworkFlyingOrbitTest.java`

**Interfaces:**
- Consumes: `FlyingObstacleAvoidance` from Task 1 and Hytale 0.5.7 `MotionControllerBase.probeMove(Ref<EntityStore>, Vector3dc, Vector3dc, ProbeMoveData, ComponentAccessor<EntityStore>)`.
- Produces: default-on builder field `AvoidObstacles`, autonomous runtime probing, explicit mounted guards, and bounded wander-destination preflight.

- [ ] **Step 1: Write failing mounted-guard and waypoint-selection tests**

Add tests that instantiate real component objects and exercise package-private predicates:

```java
@Test
void anyMountedComponentDisablesAvoidance() {
    assertTrue(BodyMotionTameworkFlyingOrbit.isRiderControlled(
            new TameworkRideMountComponent(), null));
    assertTrue(BodyMotionTameworkFlyingOrbit.isRiderControlled(
            null, new NPCMountComponent()));
    assertFalse(BodyMotionTameworkFlyingOrbit.isRiderControlled(null, null));
}
```

Add a deterministic selector test for
`selectWanderCandidate(double[] clearanceFractions, int candidateCount)`.
With literal fractions `{0.0, 1.0, 0.0}` and `candidateCount=2`, assert that it
returns index `1`; with `{0.4, 0.7, 0.6}` and `candidateCount=3`, assert that it
returns index `1`. This method makes the random runtime loop's accept-first-clear
and best-partial rules independently testable.

- [ ] **Step 2: Run the motion test and verify RED**

Run: `bash gradlew test --tests com.alechilles.alecstamework.npc.movement.BodyMotionTameworkFlyingOrbitTest`

Expected: compilation failure because the mounted predicate and deterministic waypoint selector do not exist.

- [ ] **Step 3: Add the default-on builder field and runtime state**

In `BuilderBodyMotionTameworkFlyingOrbit`:

```java
private final BooleanHolder avoidObstacles = new BooleanHolder();

getBoolean(data, "AvoidObstacles", avoidObstacles, true,
        BuilderDescriptorState.WorkInProgress,
        "Whether autonomous flight probes blocks and steers around obstructions.", null);

boolean isAvoidObstacles(BuilderSupport support) {
    return avoidObstacles.get(support.getExecutionContext());
}
```

In `BodyMotionTameworkFlyingOrbit`, store the resolved boolean, one `FlyingObstacleAvoidance`, one `ProbeMoveData`, one reusable engine-probe adapter, and reusable waypoint/fan vectors. `activate` calls `avoidance.reset()`.

- [ ] **Step 4: Implement and test the explicit rider predicate**

Retrieve `TameworkRideMountComponent.getComponentType()` defensively because it can be null, retrieve `NPCMountComponent.getComponentType()`, and pass the component instances to:

```java
static boolean isRiderControlled(@Nullable TameworkRideMountComponent tameworkRide,
                                 @Nullable NPCMountComponent nativeMount) {
    return tameworkRide != null || nativeMount != null;
}
```

If mounted, bypass `FlyingObstacleAvoidance` entirely and preserve the original steering.

- [ ] **Step 5: Wire the reusable engine probe after altitude correction**

Call `avoidance.beginUpdate(dt)` once per steering update. After the existing mode translation and target-relative altitude correction are complete, bind the current `Ref`, transform position, fly controller, and component accessor to a probe adapter created once with the body motion. Its `probe(Vector3d direction)` implementation calls:

```java
fly.probeMove(ref, selfPosition, direction, obstacleProbeData, componentAccessor)
```

Pass the target-facing vector as the horizontal fallback reference. Replace `translation` with `avoidance.adjust(...)` only when enabled, autonomous, and non-zero. Existing yaw/pitch calculation then consumes the adjusted translation.

- [ ] **Step 6: Implement three-candidate wander preflight**

Refactor random destination generation into
`generateWanderCandidate(Vector3d target, Vector3d output)`. Keep three
preallocated candidate vectors and one three-element clearance array. When a
retarget is required, generate at most three candidates, use
`avoidance.probeWaypoint(candidate - selfPosition, ...)`, and stop probing after
the first fully clear corridor. Pass the populated clearance prefix to
`selectWanderCandidate(double[], int)`; select the first clear index or the
greatest partial-clearance index. Reuse the candidate vectors, clearance array,
and route vector; do not allocate in the loop.

- [ ] **Step 7: Run focused motion and helper tests**

Run:

```bash
bash gradlew test \
  --tests com.alechilles.alecstamework.npc.movement.BodyMotionTameworkFlyingOrbitTest \
  --tests com.alechilles.alecstamework.npc.movement.FlyingObstacleAvoidanceTest
```

Expected: all focused tests pass.

- [ ] **Step 8: Commit runtime integration**

```bash
git add src/main/java/com/alechilles/alecstamework/npc/movement/BuilderBodyMotionTameworkFlyingOrbit.java \
  src/main/java/com/alechilles/alecstamework/npc/movement/BodyMotionTameworkFlyingOrbit.java \
  src/test/java/com/alechilles/alecstamework/npc/movement/BodyMotionTameworkFlyingOrbitTest.java
git commit -m "Feat: avoid obstacles during autonomous flight"
```

### Task 3: Engine contract and regression verification

**Files:**
- Verify only: all Task 1 and Task 2 files.

**Interfaces:**
- Consumes: completed Tamework implementation and Hytale Workshop 0.5.7 corpus.
- Produces: compile/test evidence, engine-reference validation, thread-safety scan, and an independent review result.

- [ ] **Step 1: Validate Hytale engine references**

Run Workshop `validate_hytale_code_refs` against the full contents of `FlyingObstacleAvoidance.java` and `BodyMotionTameworkFlyingOrbit.java` at version `0.5.7`. Confirm `MotionControllerFly`, `ProbeMoveData`, `probeMove`, `getMaximumSpeed`, and `getCurrentTurnRadius` are found and non-deprecated.

- [ ] **Step 2: Run the full Tamework tests**

Run: `bash gradlew test`

Expected: Gradle exits 0 with all tests passing.

- [ ] **Step 3: Run static safety checks**

```bash
git diff --check HEAD~2..HEAD
rg "PlayerRef\.getComponent\(Player|getComponent\(Player\.getComponentType\(\)\)|Universe\.get\(\).*getPlayers" -n src/main/java
```

Expected: no whitespace errors; the player-access scan introduces no new matches in the changed movement files.

- [ ] **Step 4: Confirm change scope**

```bash
git status --short
git diff --stat main...HEAD
git diff --name-only main...HEAD
```

Expected: only the two movement production files, new helper, and the two focused test files are changed in Tamework. HyDragon JSON and every mounted controller/input file remain unchanged.

- [ ] **Step 5: Request independent runtime review**

Ask a read-only reviewer to inspect collision semantics, mounted exclusion, probe budgets, allocations, hysteresis, and failure behavior. Resolve any material finding with a new failing test before changing production code.

- [ ] **Step 6: Commit review-driven fixes only if needed**

```bash
git add src/main/java/com/alechilles/alecstamework/npc/movement \
  src/test/java/com/alechilles/alecstamework/npc/movement
git commit -m "Fix: harden flying obstacle avoidance"
```

Skip this commit when review produces no code changes.

## Live Performance Follow-up

The code-level performance limits are automated. The approved design's 20-dragon, 60-second open-air and tree/wall profiling runs require a live server and are not launched implicitly. After the built Tamework artifact is staged for manual testing, compare baseline and changed p95 tick time, with a target of no more than five percent open-air regression.
