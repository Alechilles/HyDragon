# Bonded Companion Flight Toggle Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an active-card button that shows and toggles the authoritative grounded/airborne state for explicitly configured bonded companions, while Hydras, Rock Drakes, stored companions, dead companions, and stale projections never receive the control.

**Architecture:** Alec's Tamework owns a default-disabled per-role `Command.FlightToggle` capability, reads its named NPC flag from the live role sensor scope, projects that transient value into the bonded-card snapshot, and routes clicks through the existing hook component boundary. HyDragon opts MiniWyverns and Nordic Drakes into `AirborneMode` plus `HyDragon.Command.ToggleAirborneMode`; its existing transition instructions remain the only locomotion implementation.

**Tech Stack:** Java 25, Hytale 0.5.7 server API, Tamework JSON asset codecs and bonded-companion panel, Hytale custom UI, JUnit 5, Maven Wrapper, Python asset validation, Codex image generation, transparent PNG UI assets, Git Bash.

## Global Constraints

- Work in both source repositories, never in the unpacked runtime mod copy:
  - HyDragon: `C:/Users/22ale/.codex/worktrees/313c/HyDragon`
  - Tamework: `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework`
- Keep HyDragon on `feat/bonded-flight-toggle-panel`. Before Tamework edits, create `feat/bonded-flight-toggle-panel` from its clean `main` at `70d81163`.
- Preserve the user's unrelated dirty MiniWyvern model/animation files and stage only files named by each task.
- Use Git Bash for every repository command. Do not leave Maven, image-generation helper, Hytale server, or temporary process running.
- Capability is role-scoped because `HyDragonFullDragons.json` currently mixes the flying Nordic Drake with ground-only Hydras and Rock Drakes. Do not add a roster-wide flight boolean.
- The exact reusable configuration contract is:

```json
"FlightToggle": {
  "Enabled": true,
  "StateFlag": "AirborneMode",
  "HookId": "HyDragon.Command.ToggleAirborneMode"
}
```

- `Enabled` defaults to `false`; `StateFlag` and `HookId` default to blank. A toggle is configured only when all three values are valid after inheritance.
- Read `AirborneMode` through `role.getEntitySupport().getSensorScope().getBooleanSupplier(stateFlag)`. Do not infer capability or current mode from species names, role-name allowlists, `MotionController.getType()`, `inAir()`, or `onGround()`.
- A click may only write a `TameworkHookComponent` for the current live NPC. It must not directly set a role flag, controller, target, order, or durable profile field.
- The card icon represents the current flag value: standing means grounded; flying means airborne. Its tooltip describes the action that will result: `Switch to flight` or `Switch to ground`.
- The control is visible only when the row is `ACTIVE`, its configured live projection resolves in the event world, and the named state flag is readable. No optimistic icon change is allowed.
- Preserve the existing one-second lightweight card refresh and rebind the flight event on every refresh payload.
- Do not persist a next-summon mode. Dismissal, death, and resummon continue to use HyDragon's existing grounded default.
- Hydras and all three Rock Drake tiers must remain without `FlightToggle` and without any new flying state or controller behavior.

---

### Task 1: Add the inherited per-role flight-toggle configuration contract to Tamework

**Repository:** `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework`

**Files:**
- Create: `src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionFlightToggleSettings.java`
- Modify: `src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionCommandSettings.java`
- Modify: `src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionCommandSettingsCodec.java`
- Modify: `src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionCommandInheritance.java`
- Modify: `src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionConfig.java`
- Create: `src/test/java/com/alechilles/alecstamework/config/assets/TwCompanionFlightToggleSettingsTest.java`
- Modify: `src/test/java/com/alechilles/alecstamework/config/assets/TwCompanionConfigInheritanceTest.java`

**Interfaces:**
- Consumes: `Command.FlightToggle.Enabled`, `StateFlag`, and `HookId` from role-scoped companion assets.
- Produces: an immutable-by-copy effective capability available from `TwCompanionConfig.EffectiveSettings#getFlightToggle()`.

- [ ] **Step 1: Create the Tamework feature branch and verify edit custody**

```bash
cd /c/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework
git status --short --branch
git switch -c feat/bonded-flight-toggle-panel
```

Expected: the repository is clean before the switch and the new branch starts at `70d81163`. If the branch already exists at that commit, switch to it instead of creating another branch.

- [ ] **Step 2: Write failing default, completeness, copy, and inheritance tests**

In `TwCompanionFlightToggleSettingsTest`, cover this exact contract:

```java
@Test
void defaultsAreDisabledAndIncomplete() {
    TwCompanionFlightToggleSettings settings =
            new TwCompanionFlightToggleSettings();
    assertFalse(settings.isEnabled());
    assertFalse(settings.isConfigured());
    assertEquals("", settings.getStateFlag());
    assertEquals("", settings.getHookId());
}

@Test
void enabledCapabilityRequiresBothStateFlagAndHook() {
    TwCompanionFlightToggleSettings settings = configured(
            true, "AirborneMode", "HyDragon.Command.ToggleAirborneMode");
    assertTrue(settings.isConfigured());
    assertFalse(configured(true, "", "HyDragon.Command.ToggleAirborneMode")
            .isConfigured());
    assertFalse(configured(true, "AirborneMode", "").isConfigured());
}
```

Add inheritance tests to `TwCompanionConfigInheritanceTest` for:

1. an omitted `FlightToggle` copying all parent values;
2. `FlightToggle.StateFlag` overriding only the parent state flag while inheriting `Enabled` and `HookId`;
3. `FlightToggle.Enabled: false` explicitly disabling an inherited capability; and
4. `EffectiveSettings.from(scoped, global).getFlightToggle()` preserving the resolved role-scoped values.

Use explicit-key sets matching the existing nested inheritance convention:

```java
Map.of("Command", Set.of(
        "FlightToggle", "FlightToggle.StateFlag"))
```

- [ ] **Step 3: Run the focused tests and verify the intended red state**

```bash
./mvnw -Dtest=TwCompanionFlightToggleSettingsTest,TwCompanionConfigInheritanceTest test
```

Expected: test compilation fails because `TwCompanionFlightToggleSettings` and `EffectiveSettings#getFlightToggle()` do not exist.

- [ ] **Step 4: Implement the settings value object and codec**

Implement `TwCompanionFlightToggleSettings` with normalized, trimmed strings and copy semantics:

```java
public final class TwCompanionFlightToggleSettings {
    private boolean enabled;
    private String stateFlag = "";
    private String hookId = "";

    public boolean isEnabled() { return enabled; }
    public String getStateFlag() { return stateFlag; }
    public String getHookId() { return hookId; }

    public boolean isConfigured() {
        return enabled && !stateFlag.isBlank() && !hookId.isBlank();
    }

    TwCompanionFlightToggleSettings copy() {
        TwCompanionFlightToggleSettings copy =
                new TwCompanionFlightToggleSettings();
        copy.enabled = enabled;
        copy.stateFlag = stateFlag;
        copy.hookId = hookId;
        return copy;
    }
}
```

Keep setters package-private for the codec and normalize null to `""`. Add a `FLIGHT_TOGGLE_CODEC` to `TwCompanionCommandSettingsCodec` with the exact JSON keys `Enabled`, `StateFlag`, and `HookId`, then append it to the command codec under `FlightToggle`.

- [ ] **Step 5: Wire copy, nested inheritance, and effective settings**

Add a non-null `flightToggle` field to `TwCompanionCommandSettings`, a public getter that returns a copy, a package-private mutable accessor for inheritance, and copy it in `copy()`.

Extend `TwCompanionCommandInheritance.inheritMissing(...)` with `inheritFlightToggle(...)`:

```java
if (!explicit.contains("FlightToggle")) {
    current.flightToggle = parent.getFlightToggle().copy();
    return;
}
if (!explicit.contains("FlightToggle.Enabled")) {
    current.flightToggle.setEnabled(parent.getFlightToggle().isEnabled());
}
if (!explicit.contains("FlightToggle.StateFlag")) {
    current.flightToggle.setStateFlag(parent.getFlightToggle().getStateFlag());
}
if (!explicit.contains("FlightToggle.HookId")) {
    current.flightToggle.setHookId(parent.getFlightToggle().getHookId());
}
```

Carry a copied `TwCompanionFlightToggleSettings` through both `EffectiveSettings.from(...)` and `fromGlobal(...)`; the global/default path must produce the default-disabled object. Expose it with:

```java
@Nonnull
public TwCompanionFlightToggleSettings getFlightToggle() {
    return flightToggle.copy();
}
```

- [ ] **Step 6: Run focused tests and commit the configuration boundary**

```bash
./mvnw -Dtest=TwCompanionFlightToggleSettingsTest,TwCompanionConfigInheritanceTest test
git diff --check
git add -- src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionFlightToggleSettings.java src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionCommandSettings.java src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionCommandSettingsCodec.java src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionCommandInheritance.java src/main/java/com/alechilles/alecstamework/config/assets/TwCompanionConfig.java src/test/java/com/alechilles/alecstamework/config/assets/TwCompanionFlightToggleSettingsTest.java src/test/java/com/alechilles/alecstamework/config/assets/TwCompanionConfigInheritanceTest.java
git commit -m "Feature: add companion flight toggle capability"
```

Expected: focused tests pass and only the seven listed files are staged.

---

### Task 2: Reuse the authoritative named flag and hook boundaries

**Repository:** `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework`

**Files:**
- Create: `src/main/java/com/alechilles/alecstamework/items/BondedCompanionFlightModeReader.java`
- Create: `src/main/java/com/alechilles/alecstamework/items/CommandNpcHookDispatchService.java`
- Modify: `src/main/java/com/alechilles/alecstamework/items/CommandStepExecutionService.java`
- Create: `src/test/java/com/alechilles/alecstamework/items/BondedCompanionFlightModeReaderTest.java`
- Create: `src/test/java/com/alechilles/alecstamework/items/CommandNpcHookDispatchServiceTest.java`
- Modify: `src/test/java/com/alechilles/alecstamework/items/CommandGenericTargetAuthorityTest.java`

**Interfaces:**
- Consumes: a configured `StateFlag`, a live `Role`, and hook metadata.
- Produces: `Optional<Boolean>` for the exact named role flag and one shared hook-dispatch implementation used by commands and the panel.

- [ ] **Step 1: Write failing flag-reader tests**

Test the package-private supplier boundary so it does not require a live server store:

```java
@Test
void readsConfiguredNamedFlagWithoutControllerInference() {
    TwCompanionFlightToggleSettings settings = configuredFlightToggle();
    Optional<Boolean> result = BondedCompanionFlightModeReader.read(
            settings, name -> "AirborneMode".equals(name)
                    ? () -> true : null);
    assertEquals(Optional.of(true), result);
}

@Test
void missingOrThrowingSupplierIsUnavailable() {
    assertTrue(BondedCompanionFlightModeReader.read(
            configuredFlightToggle(), ignored -> null).isEmpty());
    assertTrue(BondedCompanionFlightModeReader.read(
            configuredFlightToggle(), ignored -> () -> {
                throw new IllegalStateException("stale role scope");
            }).isEmpty());
}
```

Also assert a disabled/incomplete capability returns empty without invoking the supplier lookup.

- [ ] **Step 2: Write failing shared-hook factory tests**

Move the component construction contract into a testable method on `CommandNpcHookDispatchService`. Assert exact preservation of hook ID, player UUID/name, item ID, timestamp, one-shot flag, and optional target position. Add invalid-input tests for blank hook IDs and invalid NPC references.

- [ ] **Step 3: Run the tests and verify the intended red state**

```bash
./mvnw -Dtest=BondedCompanionFlightModeReaderTest,CommandNpcHookDispatchServiceTest,CommandGenericTargetAuthorityTest test
```

Expected: compilation fails because both new services are absent.

- [ ] **Step 4: Implement the exact named-flag reader**

Production reading must follow this path and return empty for every missing/stale layer:

```java
Role role = npc == null ? null : npc.getRole();
EntitySupport entity = role == null ? null : role.getEntitySupport();
StdScope scope = entity == null ? null : entity.getSensorScope();
BooleanSupplier supplier = scope == null
        ? null : scope.getBooleanSupplier(settings.getStateFlag());
return supplier == null ? Optional.empty()
        : Optional.of(supplier.getAsBoolean());
```

Catch `RuntimeException | LinkageError` at the live boundary (which includes `IllegalStateException`) and return empty. Do not consult `Role#flags` reflectively and do not inspect motion controllers.

- [ ] **Step 5: Extract shared hook dispatch without changing command semantics**

Implement this service boundary:

```java
boolean dispatch(
        @Nonnull String hookId,
        @Nonnull Player player,
        @Nullable String itemId,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Vector3d targetPosition)
```

It must create the same `TameworkHookComponent` currently built by `CommandStepExecutionService#applyHook`, including `System.currentTimeMillis()` and `oneShot = true`, then `putComponent` on the supplied live NPC reference.

Inject a default `CommandNpcHookDispatchService` into `CommandStepExecutionService`, retain its current three-argument constructor for existing callers, and delegate both ordinary `TriggerHook` and move-position hook variants through the new service. Do not change step ordering, failure policies, or target-position behavior.

- [ ] **Step 6: Run focused tests, inspect the extraction, and commit**

```bash
./mvnw -Dtest=BondedCompanionFlightModeReaderTest,CommandNpcHookDispatchServiceTest,CommandGenericTargetAuthorityTest test
git diff --check
git diff -- src/main/java/com/alechilles/alecstamework/items/CommandStepExecutionService.java
git add -- src/main/java/com/alechilles/alecstamework/items/BondedCompanionFlightModeReader.java src/main/java/com/alechilles/alecstamework/items/CommandNpcHookDispatchService.java src/main/java/com/alechilles/alecstamework/items/CommandStepExecutionService.java src/test/java/com/alechilles/alecstamework/items/BondedCompanionFlightModeReaderTest.java src/test/java/com/alechilles/alecstamework/items/CommandNpcHookDispatchServiceTest.java src/test/java/com/alechilles/alecstamework/items/CommandGenericTargetAuthorityTest.java
git commit -m "Refactor: share companion hook dispatch"
```

Expected: the old command tests stay green and the diff removes the duplicate component constructor from `CommandStepExecutionService`.

---

### Task 3: Project live flight capability and state into bonded-card snapshots

**Repository:** `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework`

**Files:**
- Modify: `src/main/java/com/alechilles/alecstamework/api/BondedCompanionPresentationAttributes.java`
- Modify: `src/main/java/com/alechilles/alecstamework/items/BondedCompanionPanelLiveProfileOverlay.java`
- Modify: `src/main/java/com/alechilles/alecstamework/items/BondedCompanionPanelEntrySourceService.java`
- Modify: `src/main/java/com/alechilles/alecstamework/items/BondedCompanionPanelFeaturePresentationSource.java`
- Modify: `src/main/java/com/alechilles/alecstamework/ui/BondedCompanionCardDynamicState.java`
- Modify: `src/test/java/com/alechilles/alecstamework/items/BondedCompanionPanelLiveProfileOverlayTest.java`
- Modify: `src/test/java/com/alechilles/alecstamework/items/BondedCompanionPanelFeaturePresentationSourceTest.java`
- Modify: `src/test/java/com/alechilles/alecstamework/ui/BondedCompanionCardDynamicStateTest.java`

**Interfaces:**
- Consumes: role-scoped effective settings and the current active projection.
- Produces: transient `bonded.flightToggle.available` and `bonded.flightToggle.airborne` card attributes only when the configured flag is readable.

- [ ] **Step 1: Add failing overlay and snapshot tests**

Add constants:

```java
public static final String LIVE_NPC_UUID = "bonded.liveNpcUuid";
public static final String FLIGHT_TOGGLE_AVAILABLE =
        "bonded.flightToggle.available";
public static final String FLIGHT_TOGGLE_AIRBORNE =
        "bonded.flightToggle.airborne";
```

Test all of these cases:

1. active + configured + readable false produces `available=true`, `airborne=false`;
2. active + configured + readable true produces `available=true`, `airborne=true`;
3. stored, dead, disabled, incomplete, missing live UUID, wrong-world, missing role, and unreadable flag produce neither flight attribute;
4. applying the transient overlay does not change profile revision, lease, state, role ID, or durable API data; and
5. a change only to either flight attribute is classified by `BondedCompanionCardDynamicState.changedOnlyByLiveFields(...)` as a lightweight dynamic update.

- [ ] **Step 2: Run focused tests and verify the intended red state**

```bash
./mvnw -Dtest=BondedCompanionPanelLiveProfileOverlayTest,BondedCompanionPanelFeaturePresentationSourceTest,BondedCompanionCardDynamicStateTest test
```

Expected: failures report missing constants/overlay behavior and the flight fields are not yet treated as live.

- [ ] **Step 3: Add a transient flight overlay**

Add `withFlightMode(profile, Optional<Boolean> airborne)` to `BondedCompanionPanelLiveProfileOverlay`. When present, copy the presentation map and set both attributes with lowercase boolean strings. When empty, remove both fields from a previously overlaid copy so a stale/missing projection cannot keep the button visible.

Never write these keys through the bonded API or persistence layer.

- [ ] **Step 4: Enrich active snapshots from the exact live role**

In `BondedCompanionPanelEntrySourceService#withLivePresentation(...)`, after name and health:

1. require `profile.state() == ACTIVE` and a non-null active lease;
2. resolve the live ref from the player's current world and require the ref belongs to the supplied store;
3. read `NPCEntity` and resolve its role ID with `CompanionRoleIdResolver`;
4. require the live role ID to match `profile.roleId()`;
5. resolve `TwCompanionConfig.resolveEffectiveForRole(roleId).getFlightToggle()`;
6. require `settings.isConfigured()` before attempting the flag lookup; and
7. pass the reader's `Optional<Boolean>` to `withFlightMode`, allowing an empty result to remove both transient keys.

Replace the presentation source's literal `"bonded.liveNpcUuid"` with `BondedCompanionPresentationAttributes.LIVE_NPC_UUID`.

- [ ] **Step 5: Mark only the flight values as dynamic fields**

Remove `FLIGHT_TOGGLE_AVAILABLE` and `FLIGHT_TOGGLE_AIRBORNE` in `BondedCompanionCardDynamicState#attributesWithoutLiveFields`. Keep structural status, role, and lifecycle comparisons unchanged.

- [ ] **Step 6: Run focused tests and commit the projection layer**

```bash
./mvnw -Dtest=BondedCompanionPanelLiveProfileOverlayTest,BondedCompanionPanelFeaturePresentationSourceTest,BondedCompanionCardDynamicStateTest test
git diff --check
git add -- src/main/java/com/alechilles/alecstamework/api/BondedCompanionPresentationAttributes.java src/main/java/com/alechilles/alecstamework/items/BondedCompanionPanelLiveProfileOverlay.java src/main/java/com/alechilles/alecstamework/items/BondedCompanionPanelEntrySourceService.java src/main/java/com/alechilles/alecstamework/items/BondedCompanionPanelFeaturePresentationSource.java src/main/java/com/alechilles/alecstamework/ui/BondedCompanionCardDynamicState.java src/test/java/com/alechilles/alecstamework/items/BondedCompanionPanelLiveProfileOverlayTest.java src/test/java/com/alechilles/alecstamework/items/BondedCompanionPanelFeaturePresentationSourceTest.java src/test/java/com/alechilles/alecstamework/ui/BondedCompanionCardDynamicStateTest.java
git commit -m "Feature: project bonded companion flight mode"
```

Expected: tests pass without adding any durable-profile field.

---

### Task 4: Route a profile-scoped panel click to the configured live hook

**Repository:** `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework`

**Files:**
- Create: `src/main/java/com/alechilles/alecstamework/items/BondedCompanionFlightToggleActionService.java`
- Modify: `src/main/java/com/alechilles/alecstamework/items/CommandItemFeatureHandler.java`
- Modify: `src/main/java/com/alechilles/alecstamework/items/CommandSelectionPageService.java`
- Modify: `src/main/java/com/alechilles/alecstamework/ui/CommandSelectionPageEventBinder.java`
- Modify: `src/main/java/com/alechilles/alecstamework/ui/TameworkCommandSelectionPage.java`
- Modify: `src/main/java/com/alechilles/alecstamework/ui/LinkedNpcPanelCardBindingFactory.java`
- Modify: `src/main/java/com/alechilles/alecstamework/ui/LinkedNpcPanelCardBinder.java`
- Create: `src/test/java/com/alechilles/alecstamework/items/BondedCompanionFlightToggleActionServiceTest.java`
- Modify: `src/test/java/com/alechilles/alecstamework/items/BondedCompanionCommandPageRoutingIntegrationTest.java`
- Modify: `src/test/java/com/alechilles/alecstamework/ui/TameworkCommandSelectionPageNavigationTest.java`

**Interfaces:**
- Consumes: commands shaped like `__bonded_flight_toggle__:550e8400-e29b-41d4-a716-446655440000`, current event player/store, the latest row snapshot, and current role-scoped settings.
- Produces: one configured hook on the exact active projection followed by an ordinary snapshot refresh.

- [ ] **Step 1: Write failing authorization and no-op tests**

The action-service tests must prove that it rejects:

- non-active rows;
- rows lacking `FLIGHT_TOGGLE_AVAILABLE=true`;
- malformed or missing `LIVE_NPC_UUID`;
- an event player/store that no longer owns the page authority;
- a missing, invalid, or wrong-store live ref;
- a live role ID different from the profile role ID;
- current settings that are disabled/incomplete even if the old snapshot said available; and
- a current named flag that is no longer readable.

The success test must prove it dispatches exactly the re-resolved `HookId` to the one live ref and does not set state, controller, target, or presentation attributes directly.

- [ ] **Step 2: Add failing page-routing tests**

Add the exact prefix to `CommandSelectionPageEventBinder`:

```java
static final String BONDED_FLIGHT_TOGGLE_COMMAND_PREFIX =
        "__bonded_flight_toggle__:";
```

In the integration test, send a command for one presentation UUID and assert only that row's callback receives the UUID plus the event's current `playerRef` and `store`. Add stale-row and non-bonded-page cases that result in no hook callback but still complete a safe refresh.

In `TameworkCommandSelectionPageNavigationTest`, assert the new action does not begin page navigation or close the command page.

- [ ] **Step 3: Run focused tests and verify the intended red state**

```bash
./mvnw -Dtest=BondedCompanionFlightToggleActionServiceTest,BondedCompanionCommandPageRoutingIntegrationTest,TameworkCommandSelectionPageNavigationTest test
```

Expected: compilation or assertions fail because the action service, prefix, callback, and route are absent.

- [ ] **Step 4: Implement authoritative action validation and hook dispatch**

Expose this package-private action boundary:

```java
boolean toggle(
        @Nonnull UUID ownerUuid,
        @Nonnull Ref<EntityStore> eventPlayerRef,
        @Nonnull Store<EntityStore> eventStore,
        @Nullable String itemId,
        @Nonnull BondedCompanionPanelPresentation row)
```

Resolve the current player through `BondedCompanionPanelActionRouter.resolvePlayerFromEvent(...)`, then repeat every live projection, role ID, effective-settings, and named-flag check from the snapshot path before dispatching the configured hook through `CommandNpcHookDispatchService`. Reading the current flag is an availability check; do not choose a different hook based on its value because HyDragon's existing transition owns the toggle.

- [ ] **Step 5: Wire the event through the page without speculative state**

Add a `LinkedNpcPanelFeatureAction` flight callback to `TameworkCommandSelectionPage` and `CommandSelectionPageService`. The service callback must resolve the row from `CommandPanelSnapshotState`, revalidate `BondedLifecycleAuthority` against the event player, and call `BondedCompanionFlightToggleActionService` only for bonded-roster pages.

Handle the prefix before generic linked-NPC mutations:

```java
if (commandId.startsWith(BONDED_FLIGHT_TOGGLE_COMMAND_PREFIX)) {
    UUID cardUuid = CommandUiIdParser.parseNpcUuid(
            commandId, BONDED_FLIGHT_TOGGLE_COMMAND_PREFIX);
    CommandPanelFeaturePresentation feature =
            featureController.presentation(cardUuid);
    if (cardUuid != null && feature != null && feature.bonded() != null
            && flightToggleAvailable(feature.bonded())) {
        flightToggleCallback.accept(cardUuid, ref, store);
    }
    refreshLinkedNpcEntries();
    sendCardRefreshUpdate();
    return;
}
```

Add the prefix to `LinkedNpcPanelCardBinder.CardBindingConfig` and its factory. Retain overloads/default no-op callbacks used by existing unit tests so unrelated constructor call sites do not gain null behavior.

- [ ] **Step 6: Run focused tests and commit the action route**

```bash
./mvnw -Dtest=BondedCompanionFlightToggleActionServiceTest,BondedCompanionCommandPageRoutingIntegrationTest,TameworkCommandSelectionPageNavigationTest test
git diff --check
git add -- src/main/java/com/alechilles/alecstamework/items/BondedCompanionFlightToggleActionService.java src/main/java/com/alechilles/alecstamework/items/CommandItemFeatureHandler.java src/main/java/com/alechilles/alecstamework/items/CommandSelectionPageService.java src/main/java/com/alechilles/alecstamework/ui/CommandSelectionPageEventBinder.java src/main/java/com/alechilles/alecstamework/ui/TameworkCommandSelectionPage.java src/main/java/com/alechilles/alecstamework/ui/LinkedNpcPanelCardBindingFactory.java src/main/java/com/alechilles/alecstamework/ui/LinkedNpcPanelCardBinder.java src/test/java/com/alechilles/alecstamework/items/BondedCompanionFlightToggleActionServiceTest.java src/test/java/com/alechilles/alecstamework/items/BondedCompanionCommandPageRoutingIntegrationTest.java src/test/java/com/alechilles/alecstamework/ui/TameworkCommandSelectionPageNavigationTest.java
git commit -m "Feature: route bonded flight toggle actions"
```

---

### Task 5: Generate the two icons and bind the active-card control

**Repository:** `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework`

**Files:**
- Create: `src/main/resources/Common/UI/Custom/Tamework/LinkedPanelIcons/FlightMode_Grounded.png`
- Create: `src/main/resources/Common/UI/Custom/Tamework/LinkedPanelIcons/FlightMode_Airborne.png`
- Modify: `src/main/resources/Common/UI/Custom/TameworkBondedCompanionPanelCard.ui`
- Modify: `src/main/java/com/alechilles/alecstamework/ui/BondedCompanionCardPresenter.java`
- Modify: `src/main/resources/Server/Languages/en-US/server.lang`
- Modify: `src/main/resources/Server/Languages/de-DE/server.lang`
- Modify: `src/main/resources/Server/Languages/es-ES/server.lang`
- Modify: `src/main/resources/Server/Languages/fr-CA/server.lang`
- Modify: `src/main/resources/Server/Languages/fr-FR/server.lang`
- Modify: `src/main/resources/Server/Languages/pt-BR/server.lang`
- Modify: `src/test/java/com/alechilles/alecstamework/ui/BondedCompanionCardPresenterTest.java`
- Modify: `src/test/java/com/alechilles/alecstamework/ui/TameworkCommandSelectionPageNavigationTest.java`

**Interfaces:**
- Consumes: the two live presentation attributes and the flight event prefix.
- Produces: one 24-pixel active-card button with mutually exclusive 32x32 source icons, state-accurate visibility, action tooltips, and refresh-safe bindings.

- [ ] **Step 1: Write failing presenter, layout, asset, and refresh tests**

Add tests for:

1. active + available + airborne false: button and grounded icon visible, airborne icon hidden, tooltip resolves `switchToFlight`;
2. active + available + airborne true: button and airborne icon visible, grounded icon hidden, tooltip resolves `switchToGround`;
3. stored/dead/disabled/unreadable: button and both icons hidden;
4. `refreshDynamicState(...)` updates icon visibility and tooltip without appending/recreating the card;
5. `bindEventBindings(...)` emits `__bonded_flight_toggle__:<cardUuid>` on every lightweight refresh;
6. the UI contains all three selectors and references both final texture paths; and
7. both PNG files exist, are exactly 32x32 RGBA images, contain transparent pixels, and have non-empty visible bounds.

Run:

```bash
./mvnw -Dtest=BondedCompanionCardPresenterTest,TameworkCommandSelectionPageNavigationTest test
```

Expected: failures report missing UI selectors, textures, localized keys, visual state, and event binding.

- [ ] **Step 2: Generate and inspect a matched icon pair using the image-generation skill**

Read and follow the `imagegen` skill before creating assets. Generate a matched pair from this art direction:

```text
Two matching compact game UI silhouette icons of the same small dragon-bird companion, strong readable shape, pale icy blue-white with subtle navy edge shading, no text, no border, transparent background. Grounded icon: creature standing calmly in side profile with folded wings and feet planted. Airborne icon: same creature in side profile with wings spread, feet lifted, and body angled slightly upward. Designed to remain legible at 24 pixels and match a dark navy fantasy companion panel.
```

Keep generation intermediates outside `src/main/resources`. Produce the two final files at exactly 32x32 RGBA with transparent padding and centered, visually equal-scale silhouettes. Inspect both final files before continuing; reject unreadable anatomy, opaque square backgrounds, text, borders, or inconsistent creature designs.

- [ ] **Step 3: Add the compact card controls at the approved location**

In `TameworkBondedCompanionPanelCard.ui`:

- shrink `#BondedName`'s right edge from `Right: 108` to `Right: 142` so it cannot run under the new control;
- add `#BondedFlightModeGroundedIcon` and `#BondedFlightModeAirborneIcon` at `Anchor: (Top: 5, Right: 108, Width: 24, Height: 24)` using their fixed textures;
- add transparent `TextButton #BondedFlightToggleButton` over the same anchor using `@BondedTransparentButton`, `@BondedCardTextTooltipStyle`, and no visible text; and
- default all three new controls to `Visible: false`.

The fixed, overlapping icon groups avoid runtime texture-string replacement and missing-texture artifacts.

- [ ] **Step 4: Bind live state, tooltip, and events**

Add focused helpers to `BondedCompanionCardPresenter`:

```java
private static boolean flightToggleVisible(BondedCompanionPanelPresentation row) {
    return row.status().state() == BondedCompanionStateView.ACTIVE
            && Boolean.parseBoolean(row.attributes().get(
                    BondedCompanionPresentationAttributes
                            .FLIGHT_TOGGLE_AVAILABLE));
}
```

`bindFlightToggle(...)` must set all three visibility properties, select the icon from `FLIGHT_TOGGLE_AIRBORNE`, and set only one of these localized tooltips:

- `tamework.ui.linkedPanel.bonded.flight.switchToFlight`
- `tamework.ui.linkedPanel.bonded.flight.switchToGround`

Call it from both full `bind(...)` and `refreshDynamicState(...)`. Add `bindFlightToggleEvents(...)` to both full binding and `bindEventBindings(...)`; only bind the activating event when the current row is eligible.

- [ ] **Step 5: Add all six localized tooltips**

Use these values:

| Locale | `switchToFlight` | `switchToGround` |
| --- | --- | --- |
| en-US | `Switch to flight` | `Switch to ground` |
| de-DE | `In den Flugmodus wechseln` | `In den Bodenmodus wechseln` |
| es-ES | `Cambiar a modo de vuelo` | `Cambiar a modo terrestre` |
| fr-CA | `Passer en mode vol` | `Passer en mode terrestre` |
| fr-FR | `Passer en mode vol` | `Passer en mode terrestre` |
| pt-BR | `Mudar para modo de voo` | `Mudar para modo terrestre` |

- [ ] **Step 6: Run UI tests, inspect the icons, and commit**

```bash
./mvnw -Dtest=BondedCompanionCardPresenterTest,TameworkCommandSelectionPageNavigationTest test
git diff --check
git status --short
```

Visually inspect both final PNGs and confirm the button is left of the lifecycle label and unlink X without overlap at the card's 118-pixel height.

```bash
git add -- src/main/resources/Common/UI/Custom/Tamework/LinkedPanelIcons/FlightMode_Grounded.png src/main/resources/Common/UI/Custom/Tamework/LinkedPanelIcons/FlightMode_Airborne.png src/main/resources/Common/UI/Custom/TameworkBondedCompanionPanelCard.ui src/main/java/com/alechilles/alecstamework/ui/BondedCompanionCardPresenter.java src/main/resources/Server/Languages/en-US/server.lang src/main/resources/Server/Languages/de-DE/server.lang src/main/resources/Server/Languages/es-ES/server.lang src/main/resources/Server/Languages/fr-CA/server.lang src/main/resources/Server/Languages/fr-FR/server.lang src/main/resources/Server/Languages/pt-BR/server.lang src/test/java/com/alechilles/alecstamework/ui/BondedCompanionCardPresenterTest.java src/test/java/com/alechilles/alecstamework/ui/TameworkCommandSelectionPageNavigationTest.java
git commit -m "Feature: add bonded flight mode button"
```

---

### Task 6: Opt only MiniWyverns and Nordic Drakes into the capability

**Repository:** `C:/Users/22ale/.codex/worktrees/313c/HyDragon`

**Files:**
- Modify: `Server/Tamework/Companion/HyDragonMiniwyvern.json`
- Modify: `Server/Tamework/Companion/HyDragonFullDragons.json`
- Create: `Server/Tamework/Companion/HyDragonNordicDrake.json`
- Modify: `scripts/validate_assets.py`
- Modify: `src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/integration/DragonRosterAssetContractTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/integration/PackagedHyDragonRosterIT.java`

**Interfaces:**
- Consumes: Tamework's `Command.FlightToggle` contract.
- Produces: explicit opt-in for seven MiniWyvern roles and one Nordic Drake role, with explicit negative coverage for Hydra and all Rock Drake tiers.

- [ ] **Step 1: Write failing asset contracts before editing JSON**

Add tests that parse the companion JSON and assert:

- `HyDragonMiniwyvern.json` contains exactly the seven existing MiniWyvern roles and the exact enabled flight-toggle object;
- `HyDragonNordicDrake.json` exists, contains only `Tamed_NordicDrake`, retains the full-dragon movement/placement distances, and contains the exact enabled flight-toggle object;
- `HyDragonFullDragons.json` contains exactly `Tamed_Hydra`, `Tamed_RockDrakeT1`, `Tamed_RockDrakeT2`, and `Tamed_RockDrakeT3`, with no `FlightToggle`, `AirborneMode`, or flight hook string; and
- the existing roster policy still contains all five full-dragon roles and is not split, so ownership limits and revive economics remain unchanged.

Update packaged-jar assertions to require all three companion files and the same positive/negative contract.

- [ ] **Step 2: Run the focused tests and verify the intended red state**

```bash
./mvnw -Dtest=DragonHornLocomotionAssetContractTest,DragonRosterAssetContractTest,PackagedHyDragonRosterIT test
```

Expected: failures report the missing Nordic companion file, Nordic still present in the mixed ground config, and absent flight-toggle objects.

- [ ] **Step 3: Split the mixed role-scoped companion asset**

Keep `HyDragonFullDragons.json`'s existing parent, priority, and command distances, but change `RoleIds` to only:

```json
[
  "Tamed_Hydra",
  "Tamed_RockDrakeT1",
  "Tamed_RockDrakeT2",
  "Tamed_RockDrakeT3"
]
```

Create `HyDragonNordicDrake.json` with:

```json
{
  "Parent": "TwCompanionDefault",
  "General": {
    "Enabled": true,
    "Priority": 100
  },
  "RoleIds": ["Tamed_NordicDrake"],
  "Command": {
    "ReturnHomeTeleportDistance": 128.0,
    "ReturnHomePathDistanceBeforeTeleport": 32.0,
    "RecallSafeSpawnDistance": 24.0,
    "RecallForceRelocateDistance": 96.0,
    "PlacementMinRelativeY": -4.0,
    "PlacementMaxRelativeY": 8.0,
    "FlightToggle": {
      "Enabled": true,
      "StateFlag": "AirborneMode",
      "HookId": "HyDragon.Command.ToggleAirborneMode"
    }
  }
}
```

Add the identical `FlightToggle` block inside `HyDragonMiniwyvern.json`'s existing `Command` object. Do not change the horn command, roster policies, role templates, locomotion transition component, or any Hydra/Rock Drake file.

- [ ] **Step 4: Update Python validation for the three companion assets**

Add `HyDragonNordicDrake.json` to required/parsed/package path lists. Update the expected role partitions and extend validation to require the exact three-key object only for Nordic/Mini, while rejecting `FlightToggle`, `AirborneMode`, and `HyDragon.Command.ToggleAirborneMode` from the ground-only companion asset.

Keep the lifecycle ownership assertion: none of the three companion assets may add `Travel`, `Summon`, or `Revive`.

- [ ] **Step 5: Run HyDragon validation and commit only intended files**

```bash
python scripts/validate_assets.py
./mvnw -Dtest=DragonHornLocomotionAssetContractTest,DragonRosterAssetContractTest,PackagedHyDragonRosterIT test
git diff --check
git status --short
```

Confirm the user's three pre-existing MiniWyvern model/animation modifications remain unstaged.

```bash
git add -- Server/Tamework/Companion/HyDragonMiniwyvern.json Server/Tamework/Companion/HyDragonFullDragons.json Server/Tamework/Companion/HyDragonNordicDrake.json scripts/validate_assets.py src/test/java/com/alechilles/hydragon/config/DragonHornLocomotionAssetContractTest.java src/test/java/com/alechilles/hydragon/integration/DragonRosterAssetContractTest.java src/test/java/com/alechilles/hydragon/integration/PackagedHyDragonRosterIT.java
git commit -m "Feature: configure dragon flight toggle capability"
```

---

### Task 7: Review, verify, package, install, and perform the in-game acceptance check

**Repositories:** Both repositories listed under Global Constraints.

**Files:**
- Read: all files changed by Tasks 1-6
- Generate: `target/Alec's Tamework! v3.0.0.jar`
- Generate: `target/HyDragon v1.0.0.jar`
- Install: `C:/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/Alec's Tamework! v3.0.0.jar`
- Install: `C:/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar`
- Install: matching server copies under `C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods`

**Interfaces:**
- Consumes: committed Tamework and HyDragon candidates.
- Produces: reviewed green builds, byte-identical installed artifacts, and an in-game result covering eligible and ground-only cards.

- [ ] **Step 1: Run the complete Tamework verification suite**

```bash
cd /c/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework
./mvnw verify
git diff --check
git status --short --branch
```

Expected: the full suite passes and the feature branch is clean.

- [ ] **Step 2: Run the complete HyDragon verification suite without staging user files**

```bash
cd /c/Users/22ale/.codex/worktrees/313c/HyDragon
python scripts/validate_assets.py
./mvnw verify
git diff --check
git status --short --branch
```

Expected: validation and Maven pass; only the user's three unrelated MiniWyvern model/animation files remain dirty.

- [ ] **Step 3: Audit packaged assets and forbidden ground-only capability strings**

```bash
cd /c/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework
jar tf "target/Alec's Tamework! v3.0.0.jar" | grep -E "TameworkBondedCompanionPanelCard.ui|FlightMode_(Grounded|Airborne).png"
cd /c/Users/22ale/.codex/worktrees/313c/HyDragon
jar tf "target/HyDragon v1.0.0.jar" | grep -E "HyDragon(Miniwyvern|NordicDrake|FullDragons).json"
unzip -p "target/HyDragon v1.0.0.jar" Server/Tamework/Companion/HyDragonFullDragons.json | grep -E "FlightToggle|AirborneMode|ToggleAirborneMode" && exit 1 || true
```

Expected: Tamework lists the UI and both icons; HyDragon lists all three companion assets; the ground-only grep finds nothing.

- [ ] **Step 4: Request independent review before installation**

Use `superpowers:requesting-code-review` and request review of:

- live-world/thread authority and stale-ref rejection;
- no optimistic UI state;
- exact named-flag reading rather than controller/species inference;
- shared hook dispatch preserving command behavior;
- default-disabled and partial inheritance semantics;
- event rebinding on lightweight refreshes; and
- the explicit Hydra/Rock Drake negative contract.

Address every confirmed issue, rerun the affected focused tests, and rerun both full verification commands before continuing.

- [ ] **Step 5: Install exact committed artifacts without starting a server**

Read and follow the `alec-mod-publish` skill for the local build/install operation. Confirm no Hytale server Java process is running, then install Tamework first and HyDragon second:

```bash
cd /c/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework
./mvnw -Pinstall-plugin package
cd /c/Users/22ale/.codex/worktrees/313c/HyDragon
./mvnw -Pinstall-plugin package
```

Verify both UserData copies are byte-identical to their build artifacts:

```bash
sha256sum "/c/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/target/Alec's Tamework! v3.0.0.jar" "/c/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/Alec's Tamework! v3.0.0.jar"
sha256sum "/c/Users/22ale/.codex/worktrees/313c/HyDragon/target/HyDragon v1.0.0.jar" "/c/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar"
```

Expected: each source/installed pair has the same SHA-256 digest. Do not edit the unpacked `UserData/Mods/alecstamework` directory.

- [ ] **Step 6: Run the in-game acceptance matrix**

With the newly installed jars:

1. Open the Dragon Horn panel with a stored MiniWyvern and confirm no flight button appears.
2. Summon the MiniWyvern; confirm the standing icon appears once the active projection resolves.
3. Click it; confirm the companion keeps its current order/target, transitions to flight, and the icon changes to flying only after the live `AirborneMode` refresh.
4. Click again; confirm the companion lands through the existing transition and the icon changes to standing only after the flag changes.
5. Repeat the grounded/airborne cycle with an active Nordic Drake.
6. Open cards for an active Hydra and each Rock Drake tier; confirm no flight button is rendered.
7. Dismiss or kill an eligible companion while the panel is open; confirm the next refresh removes the button without an error or stale click effect.
8. Confirm the normal summon, dismiss, revive, talent, unlink, and command controls still work and card refreshes do not lose the flight button's click binding.

- [ ] **Step 7: Record final commits and repository state**

```bash
cd /c/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework
git log -5 --oneline
git status --short --branch
cd /c/Users/22ale/.codex/worktrees/313c/HyDragon
git log -5 --oneline
git status --short --branch
```

Report both exact commit hashes, both artifact SHA-256 digests, full-suite results, and the eight acceptance results. Do not claim completion if either repository has uncommitted feature files, an installed digest differs, or Hydra/Rock Drake shows the control.
