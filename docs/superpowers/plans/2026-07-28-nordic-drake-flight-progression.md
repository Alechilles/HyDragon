# Nordic Drake Flight Progression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reusable Tamework Avatar Flight XP and talent effects, then give HyDragon's Nordic Drake a level-30 flight/combat/resilience progression tree.

**Architecture:** Tamework adds a nested flight XP source and a pure per-session time accountant. The Avatar Flight movement system resolves the parked NPC source, uses it both for XP awards and an immutable effective-flight tuning, and leaves all species-specific values in HyDragon JSON assets. HyDragon adds two role-scoped configs, five-locale text, and resource-level contract tests after Tamework publishes the local `3.0.0` JAR.

**Tech Stack:** Java 25, Maven/Surefire, Tamework `Tw*Config` assets, Hytale ECS, JSON assets, JUnit 5.

## Global Constraints

- Tamework remains generic: no HyDragon or Nordic Drake identifier in its Java, default assets, docs, or API identifiers.
- The public enum value is exactly `CompanionXpSource.AVATAR_FLIGHT`.
- Flight XP is server-time at the existing relative fast-flight threshold, never distance, client input, player-reported speed, or idle time.
- XP always accrues to the valid parked source companion in the same world, not to the transformed player.
- Missing source/session/config/talent data is a safe no-award or neutral `1.0` multiplier; it must never throw from a ticking system.
- New nested `TwLevelingConfig.XpSources.Flight` fields follow the established explicit-key inheritance contract.
- Nordic Drake uses level 30, `BaseXp: 155`, `GrowthFactor: 1.09`, `0.15` XP per qualified second, 10-second awards, and a 9-XP/minute ceiling.
- Nordic Drake combat XP uses the established Beast values: `DamageDealtXpPerPoint: 0.35`, `DamageTakenXpPerPoint: 0.12`, and `MinimumDamageEvent: 2.0`.
- Do not add breeding, needs, happiness, harvesting, or feed XP to Nordic Drake.
- Use Git Bash, keep JDK release 25, and stop any manual live-test server after validation.

---

## File Structure

| Repository | Path | Responsibility |
| --- | --- | --- |
| Tamework | `src/main/java/com/alechilles/alecstamework/config/assets/TwLevelingConfig.java` | Parse, inherit, and expose `XpSources.Flight`. |
| Tamework | `src/main/java/com/alechilles/alecstamework/api/CompanionXpSource.java` | Publish the `AVATAR_FLIGHT` event-source bucket. |
| Tamework | `src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightExperienceService.java` | Pure, bounded qualified-time and rate-cap accounting. |
| Tamework | `src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightProgressionTuning.java` | Resolve and clamp source-companion flight multipliers. |
| Tamework | `src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightComponent.java` | Persist only live-session XP accounting state. |
| Tamework | `src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightMovementSystem.java` | Resolve source NPC, call the XP accountant, and pass effective tuning to flight logic. |
| Tamework | `src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightController.java` | Consume effective boost impulse, sink, and climb lift. |
| Tamework | `src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightVigourService.java` | Consume effective Vigour capacity, recharge rate, and boost cost. |
| Tamework | `docs/Avatar-Flight.md` and progression wiki pages | Document configuration, event semantics, and effect keys. |
| HyDragon | `Server/Tamework/Leveling/HyDragonNordicDrake.json` | Nordic Drake curve, sources, stat growth, and points. |
| HyDragon | `Server/Tamework/Talents/HyDragonNordicDrake.json` | Nordic Drake's three-branch talent tree. |
| HyDragon | `Server/Languages/*/server.lang` | Five-locale talent and branch text. |
| HyDragon | `src/test/java/com/alechilles/hydragon/config/NordicDrakeProgressionAssetTest.java` | Static asset, prerequisite, effect-key, and locale contract. |

### Task 1: Add Flight XP Config And Public Source

**Files:**
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/config/assets/TwLevelingConfig.java`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/api/CompanionXpSource.java`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/config/assets/TwConfigInheritanceContractTest.java`
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/api/internal/TameworkEventBusTest.java`

**Interfaces:**
- Produces `TwLevelingConfig.FlightXpSourceSettings getFlight()` with `isEnabled()`, `getXpPerQualifiedSecond()`, `getAwardIntervalSeconds()`, and `getMaxXpPerMinute()`.
- Produces the enum member `CompanionXpSource.AVATAR_FLIGHT` for Task 3.
- `FlightXpSourceSettings` defaults to disabled and zero award values, so old configs remain inert.

- [ ] **Step 1: Write config inheritance and event tests**

Add a `Flight` parent and child section to `levelingSectionsNestedMergeAndEffectsReplacementWork`. The child explicitly sets only `Flight.XpPerQualifiedSecond`; assert inherited `Enabled`, `AwardIntervalSeconds`, and `MaxXpPerMinute` values. Add one `TameworkEventBusTest` event constructed with `CompanionXpSource.AVATAR_FLIGHT` and assert the listener receives that value.

```java
assertTrue(child.getXpSources().getFlight().isEnabled());
assertEquals(0.15d, child.getXpSources().getFlight().getXpPerQualifiedSecond(), EPSILON);
assertEquals(10.0d, child.getXpSources().getFlight().getAwardIntervalSeconds(), EPSILON);
assertEquals(9.0d, child.getXpSources().getFlight().getMaxXpPerMinute(), EPSILON);
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run from `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework`:

```bash
./mvnw test -Dtest=TwConfigInheritanceContractTest,TameworkEventBusTest
```

Expected: compilation failure because `getFlight()` and `AVATAR_FLIGHT` do not exist.

- [ ] **Step 3: Implement the nested config and enum member**

Add a dedicated codec and settings type rather than reusing `SimpleXpSourceSettings`:

```java
public static final class FlightXpSourceSettings {
    private boolean enabled;
    private double xpPerQualifiedSecond;
    private double awardIntervalSeconds = 10.0;
    private double maxXpPerMinute;
    // public validated getters
}
```

Register it under `XpSources.Flight`; add explicit nested inheritance for `Flight`, `Flight.Enabled`, `Flight.XpPerQualifiedSecond`, `Flight.AwardIntervalSeconds`, and `Flight.MaxXpPerMinute`. Document object and nested-field inheritance in every codec section. Add `AVATAR_FLIGHT` without renaming or reordering existing enum names.

- [ ] **Step 4: Run focused tests and static compile verification**

```bash
./mvnw test -Dtest=TwConfigInheritanceContractTest,TameworkEventBusTest
```

Expected: PASS.

- [ ] **Step 5: Commit the Tamework config/API unit**

```bash
git add src/main/java/com/alechilles/alecstamework/config/assets/TwLevelingConfig.java src/main/java/com/alechilles/alecstamework/api/CompanionXpSource.java src/test/java/com/alechilles/alecstamework/config/assets/TwConfigInheritanceContractTest.java src/test/java/com/alechilles/alecstamework/api/internal/TameworkEventBusTest.java
git commit -m "feat: add avatar flight XP config"
```

### Task 2: Implement Pure Flight XP Accounting

**Files:**
- Create: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightExperienceService.java`
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightExperienceServiceTest.java`

**Interfaces:**
- Consumes `TwLevelingConfig.FlightXpSourceSettings` from Task 1.
- Produces `AvatarFlightExperienceService.State(double qualifiedSeconds, double windowAwardedXp, long windowStartedAtMs, long lastSampleAtMs)`.
- Produces `Result(State state, double awardedXp)` from `tick(State state, FlightXpSourceSettings settings, boolean qualifies, long nowMs)` and `State reset(long nowMs)`.
- Task 3 stores this state on `AvatarFlightComponent` and calls `CompanionLevelingService.awardXp(..., AVATAR_FLIGHT, result.awardedXp())` only for positive awards.

- [ ] **Step 1: Write the failing accounting tests**

Cover first sample, 10 qualified seconds producing `1.5` XP, non-qualifying samples pausing accumulation, disabled/zero settings producing zero, a 60-second cap at `9.0`, and a 30-second clock jump contributing no more than the documented per-tick maximum.

```java
AvatarFlightExperienceService.Result result = service.tick(
        new State(9.8, 0.0, 1_000L, 10_800L), settings(0.15, 10.0, 9.0), true, 11_000L);
assertEquals(1.5, result.awardedXp(), EPSILON);
assertEquals(0.0, result.state().qualifiedSeconds(), EPSILON);
```

- [ ] **Step 2: Run the focused test and verify failure**

```bash
./mvnw test -Dtest=AvatarFlightExperienceServiceTest
```

Expected: test compilation failure because `AvatarFlightExperienceService` is absent.

- [ ] **Step 3: Implement bounded, deterministic accounting**

Use a `MAX_TICK_SECONDS = 0.25d` constant. A first sample initializes `lastSampleAtMs` without credit. A non-qualifying sample updates `lastSampleAtMs` and retains the partial qualified interval, matching the approved paused-time behavior. On a qualifying sample, clamp elapsed time, accumulate it, pay only full intervals, and limit every award to the remaining current-minute allowance. When a new minute window starts, reset only `windowAwardedXp`; do not erase qualified partial time.

```java
double elapsedSeconds = Math.min(MAX_TICK_SECONDS, Math.max(0.0d, nowMs - state.lastSampleAtMs()) / 1000.0d);
double availableXp = Math.max(0.0d, settings.getMaxXpPerMinute() - windowAwardedXp);
double award = Math.min(fullIntervals * settings.getAwardIntervalSeconds() * settings.getXpPerQualifiedSecond(), availableXp);
```

- [ ] **Step 4: Run focused accounting tests**

```bash
./mvnw test -Dtest=AvatarFlightExperienceServiceTest
```

Expected: PASS, including exact batch and cap assertions.

- [ ] **Step 5: Commit the pure accounting unit**

```bash
git add src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightExperienceService.java src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightExperienceServiceTest.java
git commit -m "feat: meter avatar flight experience"
```

### Task 3: Wire Session-Safe Flight XP Into Avatar Flight

**Files:**
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightComponent.java`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightMovementSystem.java`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/Tamework.java`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightComponentTest.java`
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightMovementSystemTest.java`
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/architecture/CompanionXpEventWiringTest.java`

**Interfaces:**
- Consumes `AvatarFlightExperienceService` from Task 2 and `CompanionXpSource.AVATAR_FLIGHT` from Task 1.
- Produces `AvatarFlightMovementSystem` behavior that awards only the valid `AvatarFlightMountSessionComponent.SourceNpcUuid` source using `CompanionLevelingService.awardXp`.
- Adds `get/setFlightXpQualifiedSeconds`, `get/setFlightXpWindowAwardedXp`, `get/setFlightXpWindowStartedAtMs`, and `get/setFlightXpLastSampleAtMs` to `AvatarFlightComponent` plus clone/codec coverage.

- [ ] **Step 1: Write failing source-resolution and component-state tests**

Add component codec/clone assertions for the four tracker fields. Add a movement-system seam test that provides a valid session/source and verifies the award uses `AVATAR_FLIGHT`; add cases for missing session, wrong world, missing source ref, non-fast output, and teardown reset. Update the architecture test to require the concrete source call.

```java
assertTrue(content.contains(
        "CompanionXpSource.AVATAR_FLIGHT"));
assertTrue(content.contains(
        "AvatarFlightExperienceService"));
```

- [ ] **Step 2: Run focused tests and verify failure**

```bash
./mvnw test -Dtest=AvatarFlightComponentTest,AvatarFlightMovementSystemTest,CompanionXpEventWiringTest
```

Expected: missing tracker accessors and missing flight-XP wiring assertions.

- [ ] **Step 3: Persist tracker state and integrate it after controller output**

Extend `AvatarFlightComponent.CODEC`, fields, getters/setters, and `clone()` with the Task 2 state. Inject `AvatarFlightMountSessionComponent` access into `AvatarFlightMovementSystem` and register the required component type from `Tamework`. After controller output is known, resolve the source ref from `session.getSourceNpcUuid()` only when the session world matches the active world and the source ref is valid. Call the service with this predicate:

```java
boolean qualifies = output.applyVelocity() && output.fastFlight() && sourceRef != null && sourceRef.isValid();
```

When `result.awardedXp() > 0.0d`, invoke:

```java
CompanionLevelingService.awardXp(
        sourceRef, store, commandBuffer, null,
        CompanionXpSource.AVATAR_FLIGHT, result.awardedXp());
```

Write the returned state to the flight component every tick. Call `reset(now)` on every normal/exceptional Avatar Flight deactivation path before removing the component.

- [ ] **Step 4: Run focused session-safety tests**

```bash
./mvnw test -Dtest=AvatarFlightComponentTest,AvatarFlightMovementSystemTest,CompanionXpEventWiringTest
```

Expected: PASS; no case awards player XP or awards an invalid source.

- [ ] **Step 5: Commit the runtime XP integration**

```bash
git add src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightComponent.java src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightMovementSystem.java src/main/java/com/alechilles/alecstamework/Tamework.java src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightComponentTest.java src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightMovementSystemTest.java src/test/java/com/alechilles/alecstamework/architecture/CompanionXpEventWiringTest.java
git commit -m "feat: award XP for fast avatar flight"
```

### Task 4: Apply Source-Companion Flight Talents

**Files:**
- Create: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightProgressionTuning.java`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightController.java`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightVigourService.java`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightMovementSystem.java`
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightProgressionTuningTest.java`
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightControllerTest.java`
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightVigourServiceTest.java`

**Interfaces:**
- Produces immutable `AvatarFlightProgressionTuning` with neutral `1.0` values and source-aware `resolve(Ref<EntityStore> sourceRef, Store<EntityStore> store)`.
- `AvatarFlightController.update` gains an `AvatarFlightProgressionTuning` argument before `dt`.
- `AvatarFlightVigourService.recharge`, `canSpend`, and `spend` gain an `AvatarFlightProgressionTuning` argument after `TwAvatarFlightConfig`.
- Supports exactly the six effect keys named in the approved design.

- [ ] **Step 1: Write failing neutral, clamp, and modifier-effect tests**

Write direct record tests for neutral fallback and each bound. Add controller tests proving a `1.15` boost-impulse multiplier changes only boost impulse, a `0.86` sink multiplier reduces unpowered sink, and a `1.12` lift multiplier changes pitch-up lift. Add Vigour tests proving capacity, recharge rate, and `0.88` boost-cost semantics; retain one regression test that all `1.0` values match prior controller output exactly.

```java
AvatarFlightProgressionTuning tuning = new AvatarFlightProgressionTuning(
        1.15, 1.15, 0.88, 1.15, 0.86, 1.12);
assertEquals(1.15, tuning.forwardBoostImpulseMultiplier(), EPSILON);
assertEquals(0.86, tuning.glideSinkMultiplier(), EPSILON);
```

- [ ] **Step 2: Run the focused tests and verify failure**

```bash
./mvnw test -Dtest=AvatarFlightProgressionTuningTest,AvatarFlightControllerTest,AvatarFlightVigourServiceTest
```

Expected: compilation failure because the tuning record and expanded method signatures do not exist.

- [ ] **Step 3: Implement clamped source-aware tuning**

Use `CompanionProgressionModifierService.resolveMultiplier(sourceRef, store, effectKey, 1.0d)` for each key. Clamp resulting values in one record constructor so malformed downstream values cannot make flight unstable:

```java
capacity = clamp(capacity, 1.0, 1.35);
rechargeRate = clamp(rechargeRate, 1.0, 1.35);
boostCost = clamp(boostCost, 0.70, 1.0);
boostImpulse = clamp(boostImpulse, 1.0, 1.25);
glideSink = clamp(glideSink, 0.70, 1.0);
climbLift = clamp(climbLift, 1.0, 1.25);
```

Thread the record through controller and Vigour code. Apply capacity to every charge clamp, recharge rate by dividing seconds-per-charge, boost cost only to forward boosts, impulse only when boost is applied, sink only to unpowered glide, and lift only to pitch-up lift. Resolve `neutral()` when no valid source session exists.

- [ ] **Step 4: Run focused modifier and regression tests**

```bash
./mvnw test -Dtest=AvatarFlightProgressionTuningTest,AvatarFlightControllerTest,AvatarFlightVigourServiceTest,AvatarFlightMovementSystemTest
```

Expected: PASS; neutral tuning preserves all existing behavior.

- [ ] **Step 5: Commit the flight-talent runtime unit**

```bash
git add src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightProgressionTuning.java src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightController.java src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightVigourService.java src/main/java/com/alechilles/alecstamework/avatarflight/AvatarFlightMovementSystem.java src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightProgressionTuningTest.java src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightControllerTest.java src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightVigourServiceTest.java src/test/java/com/alechilles/alecstamework/avatarflight/AvatarFlightMovementSystemTest.java
git commit -m "feat: apply companion talents to avatar flight"
```

### Task 5: Document And Verify The Tamework Public Contract

**Files:**
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/docs/Avatar-Flight.md`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/wiki/Modder-Documentation/System-Integration/Progression-Systems-Guide.md`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/wiki/Modder-Documentation/Config-Reference/TwTalentConfig-Reference.md`
- Modify: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/debug/CompanionXpEventDebugLogServiceTest.java`
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/src/test/java/com/alechilles/alecstamework/architecture/CompanionXpEventWiringTest.java`

**Interfaces:**
- Documents exact `Flight` JSON field names, qualification rules, and all six effect-key multiplier directions.
- Documents `AVATAR_FLIGHT` as a normal companion XP event source.

- [ ] **Step 1: Write failing public-contract tests**

Extend the XP debug-log test with an `AVATAR_FLIGHT` event and assert its source label is retained. Extend the architecture test to require both the source-session lookup and the six literal effect-key constants to live in `AvatarFlightProgressionTuning`.

- [ ] **Step 2: Run the focused tests and verify failure**

```bash
./mvnw test -Dtest=CompanionXpEventDebugLogServiceTest,CompanionXpEventWiringTest
```

Expected: source-label or architecture assertion failure until the public documentation/constants are complete.

- [ ] **Step 3: Document field semantics and operational limits**

Add a `Flight XP` section to `docs/Avatar-Flight.md` with this representative config:

```json
"Flight": {
  "Enabled": true,
  "XpPerQualifiedSecond": 0.15,
  "AwardIntervalSeconds": 10.0,
  "MaxXpPerMinute": 9.0
}
```

State that qualification is output fast-flight time, not distance. In the progression guide, list `AVATAR_FLIGHT`, its source-NPC target, batching, and cap. In the talent reference, list the six exact keys, their neutral values, directions, and runtime clamps.

- [ ] **Step 4: Run the full Tamework test suite and package the JAR**

```bash
./mvnw test
./mvnw package -DskipTests
```

Expected: both commands PASS and produce `target/Alec's Tamework! v3.0.0.jar` for HyDragon's system-scoped dependency.

- [ ] **Step 5: Commit the documentation and compatibility unit**

```bash
git add docs/Avatar-Flight.md wiki/Modder-Documentation/System-Integration/Progression-Systems-Guide.md wiki/Modder-Documentation/Config-Reference/TwTalentConfig-Reference.md src/test/java/com/alechilles/alecstamework/debug/CompanionXpEventDebugLogServiceTest.java src/test/java/com/alechilles/alecstamework/architecture/CompanionXpEventWiringTest.java
git commit -m "Docs: describe avatar flight progression"
```

### Task 6: Add Nordic Drake Progression Assets And Localization

**Files:**
- Create: `Server/Tamework/Leveling/HyDragonNordicDrake.json`
- Create: `Server/Tamework/Talents/HyDragonNordicDrake.json`
- Modify: `Server/Languages/en-US/server.lang`
- Modify: `Server/Languages/de-DE/server.lang`
- Modify: `Server/Languages/es-ES/server.lang`
- Modify: `Server/Languages/fr-FR/server.lang`
- Modify: `Server/Languages/pt-BR/server.lang`
- Modify: `docs/Tamework-Avatar-Flight-Integration.md`
- Create: `src/test/java/com/alechilles/hydragon/config/NordicDrakeProgressionAssetTest.java`

**Interfaces:**
- Consumes Tamework `Flight` source fields and the six effect keys from Tasks 1-5.
- Produces role-scoped assets for exactly `Tamed_NordicDrake` and localization keys with the `hydragon.talents.nordic_drake.*` prefix.

- [ ] **Step 1: Write the failing Nordic asset contract test**

Load both JSON assets with Gson and assert the exact level curve, source enablement, stat growth, 29-point policy, all 18 talent IDs, costs, levels, prerequisites, effects, and absence of feed/harvest/breeding enablement. Read all five locale files and assert every English `hydragon.talents.nordic_drake.*` key exists in every catalog with no `{placeholder}` mismatch.

```java
assertEquals(List.of("Tamed_NordicDrake"), roleIds(leveling));
assertEquals(30, leveling.getAsJsonObject("Levels").get("MaxLevel").getAsInt());
assertEquals(18, talents.getAsJsonArray("Talents").size());
assertFalse(leveling.getAsJsonObject("XpSources").getAsJsonObject("Feed").get("Enabled").getAsBoolean());
```

- [ ] **Step 2: Run the focused test and verify failure**

Run from `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon` after Task 5 has built the Tamework JAR:

```bash
./mvnw test -Dtest=NordicDrakeProgressionAssetTest
```

Expected: missing-resource failure for both new JSON assets.

- [ ] **Step 3: Create the level config and talent tree**

Use this exact leveling shape and assert every numeric value in `NordicDrakeProgressionAssetTest`:

```json
{
  "Enabled": true,
  "Priority": 100,
  "RoleIds": ["Tamed_NordicDrake"],
  "Levels": {"MaxLevel": 30, "BaseXp": 155.0, "GrowthFactor": 1.09},
  "XpSources": {
    "Feed": {"Enabled": false},
    "Harvest": {"Enabled": false},
    "Breeding": {"Enabled": false},
    "Combat": {
      "Enabled": true,
      "DamageDealtXpPerPoint": 0.35,
      "DamageTakenXpPerPoint": 0.12,
      "MinimumDamageEvent": 2.0,
      "AwardVsPlayers": false,
      "AwardVsOwnedAllies": false
    },
    "Flight": {"Enabled": true, "XpPerQualifiedSecond": 0.15, "AwardIntervalSeconds": 10.0, "MaxXpPerMinute": 9.0}
  },
  "StatGrowth": {"Effects": [
    {"EffectKey": "MaxHealthMultiplier", "PerLevel": 0.004},
    {"EffectKey": "DamageDealtMultiplier", "PerLevel": 0.002}
  ]},
  "TalentPoints": {"PointsPerLevel": 1}
}
```

Create every approved node exactly as specified in the design: Aerial Mastery (`Northwind Resolve` through `Storm Sovereign`), War Drake (`Ember Discipline` through `Jarl's Bane`), and Wyrmguard (`Runestone Hide` through `Northern Bulwark`). Use raw `server.lang` keys for display names, branch labels, and descriptions, and include all five locales. Update the integration document to name the two configs and state that fast-flight progression is source-companion time based.

- [ ] **Step 4: Run resource, asset, and package tests**

```bash
./mvnw test -Dtest=NordicDrakeProgressionAssetTest,NordicAvatarFlightPatchContractTest,DragonRosterAssetContractTest
./mvnw package -DskipTests
```

Expected: PASS; the packaged JAR contains both `Server/Tamework` assets and all language entries.

- [ ] **Step 5: Commit the HyDragon asset unit**

```bash
git add Server/Tamework/Leveling/HyDragonNordicDrake.json Server/Tamework/Talents/HyDragonNordicDrake.json Server/Languages/en-US/server.lang Server/Languages/de-DE/server.lang Server/Languages/es-ES/server.lang Server/Languages/fr-FR/server.lang Server/Languages/pt-BR/server.lang docs/Tamework-Avatar-Flight-Integration.md src/test/java/com/alechilles/hydragon/config/NordicDrakeProgressionAssetTest.java
git commit -m "feat: add Nordic Drake flight talents"
```

### Task 7: Run Cross-Repository Packaging And Exact-Profile Validation

**Files:**
- No planned source edits; this task validates the outputs of Tasks 1-6.
- Test: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/target/Alec's Tamework! v3.0.0.jar`
- Test: `target/HyDragon v0.2.1.jar`

**Interfaces:**
- Consumes the Tamework JAR built in Task 5 and HyDragon assets from Task 6.
- Produces verified package evidence that HyDragon compiles against the new public API and includes both configs.

- [ ] **Step 1: Rebuild Tamework and run its complete tests**

```bash
cd "C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework"
./mvnw test
./mvnw package -DskipTests
```

Expected: PASS and a fresh `Alec's Tamework! v3.0.0.jar`.

- [ ] **Step 2: Run HyDragon validation and tests against the fresh Tamework JAR**

```bash
cd "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon"
./mvnw verify
```

Expected: PASS, including `scripts/validate_assets.py`, unit tests, integration tests, and packaging checks.

- [ ] **Step 3: Inspect packaged resources**

```bash
jar tf "target/HyDragon v0.2.1.jar" | rg "Server/Tamework/(Leveling|Talents)/HyDragonNordicDrake\.json|Server/Languages/(en-US|de-DE|es-ES|fr-FR|pt-BR)/server\.lang"
jar tf "../alecstamework/target/Alec's Tamework! v3.0.0.jar" | rg "AvatarFlightExperienceService|AvatarFlightProgressionTuning|TwLevelingConfig"
```

Expected: both Nordic JSON assets, all five language catalogs, and the three Tamework classes are listed.

- [ ] **Step 4: Run exact-profile affected-scope asset validation**

Lock the intended Hytale release profile and plugin set in HytaleNpcAssetTools. Inspect both Nordic config assets with declared/effective values and full provenance, then validate the affected scope including `Tamed_NordicDrake`, its Avatar Flight config, both progression assets, all localization references, and Tamework's `TwLevelingConfig` schema. Record the profile, knowledge-pack, snapshot, candidate, and actionable advisories in the implementation report.

Expected: no parse errors, broken references, or unsafe inherited defaults. Any advisory that changes an inherited value is reviewed explicitly rather than silenced by an unrelated override.

- [ ] **Step 5: Perform bounded live validation**

Start one local server with the rebuilt Tamework and HyDragon JARs. Capture a Nordic Drake, enter fast flight for at least 10 qualified seconds, verify one `AVATAR_FLIGHT` XP event on the source companion, purchase one Aerial Mastery node, then confirm the corresponding HUD/controller or Vigour behavior changes. Dismount, swim, hover, and cross-world recovery must produce no XP. Stop the server immediately after the evidence capture.

On a failed assertion, stop the server, retain the log window and exact reproduction state, then return to the task that owns the failing contract. Do not create a validation-only commit.

## Final Verification

- [ ] Tamework: `./mvnw test && ./mvnw package -DskipTests`
- [ ] HyDragon: `./mvnw verify`
- [ ] Packaged JARs contain all required classes and assets.
- [ ] Exact-profile validation reports no unresolved schema/reference failures.
- [ ] Live fast-flight XP, neutral fallback, talent effect, and no-XP invalid-state checks are recorded.
