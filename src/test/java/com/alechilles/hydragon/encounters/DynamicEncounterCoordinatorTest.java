package com.alechilles.hydragon.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.NpcProfileView;
import com.alechilles.alecstamework.api.NpcProfilesApi;
import com.alechilles.alecstamework.api.PolicyApi;
import com.alechilles.alecstamework.api.PopulationGroupApi;
import com.alechilles.alecstamework.api.PopulationGroupCountsView;
import com.alechilles.alecstamework.api.PopulationGroupScope;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.persistence.EncounterDefinitionSnapshot;
import com.alechilles.hydragon.persistence.EncounterRecord;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicEncounterCoordinatorTest {
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACCESS_PROFILE = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ACCESS_NPC = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID TARGET = UUID.fromString("10000000-0000-0000-0000-000000000004");

    @TempDir Path temporaryDirectory;

    @Test
    void admissionIsIdempotentAndGroundingSurvivesReconciliation() throws Exception {
        AtomicBoolean captured = new AtomicBoolean(false);
        TameworkApi api = api(captured);
        HyDragonStateStore stateStore = stateStore();
        DragonEncounterConfig definition = encounterConfig();
        HyDragonConfigRepository.Snapshot configs = snapshot(definition, speciesConfig());
        DynamicEncounterCoordinator coordinator = new DynamicEncounterCoordinator(
                api, stateStore, new EncounterEligibilityService(api, stateStore));
        FakeWorld world = new FakeWorld();
        EncounterCandidate candidate = candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID));

        DynamicEncounterCoordinator.AdmissionResult admitted = coordinator.admit(
                definition, configs, candidate, world, true, 60_000L);
        assertTrue(admitted.admitted());
        assertEquals(TARGET, admitted.targetNpcUuid());
        assertEquals(1, world.spawnCalls);

        DynamicEncounterCoordinator.AdmissionResult replay = coordinator.admit(
                definition, configs, candidate, world, true, 60_000L);
        assertTrue(replay.admitted());
        assertEquals(admitted.encounterId(), replay.encounterId());
        assertEquals(1, world.spawnCalls);

        DynamicEncounterCoordinator.TransitionResult progress = coordinator.groundingHit(
                admitted.encounterId(), TARGET, "projectile:Lure+item:LureTool", 40.0D,
                world, 61_000L);
        assertEquals(EncounterPhase.GROUNDING, progress.phase());
        assertEquals(40.0D, progress.buildup());

        DynamicEncounterCoordinator.TransitionResult recovered = coordinator.reconcile(
                admitted.encounterId(), world, 62_000L);
        assertEquals(EncounterPhase.GROUNDING, recovered.phase());
        assertEquals(40.0D, recovered.buildup());

        DynamicEncounterCoordinator.TransitionResult grounded = coordinator.groundingHit(
                admitted.encounterId(), TARGET, "projectile:Stagger+item:StaggerTool", 60.0D,
                world, 63_000L);
        assertEquals(EncounterPhase.GROUNDED_CAPTURE_WINDOW, grounded.phase());
        assertTrue(coordinator.captureAllowed(TARGET));
        assertEquals(1, world.groundedCalls);
    }

    @Test
    void staggerCannotSkipLureAndCaptureWaitsForPhysicalGrounding() throws Exception {
        TameworkApi api = api(new AtomicBoolean(false));
        HyDragonStateStore stateStore = stateStore();
        DragonEncounterConfig definition = encounterConfig();
        DynamicEncounterCoordinator coordinator = new DynamicEncounterCoordinator(
                api, stateStore, new EncounterEligibilityService(api, stateStore));
        FakeWorld world = new FakeWorld();
        world.grounded = false;
        DynamicEncounterCoordinator.AdmissionResult admitted = coordinator.admit(
                definition, snapshot(definition, speciesConfig()),
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)),
                world, true, 60_000L);

        DynamicEncounterCoordinator.TransitionResult outOfOrder = coordinator.groundingHit(
                admitted.encounterId(), TARGET, "projectile:Stagger+item:StaggerTool", 100.0D,
                world, 61_000L);
        assertFalse(outOfOrder.transitioned());
        assertEquals("grounding-lure-required", outOfOrder.reason());

        DynamicEncounterCoordinator.TransitionResult lure = coordinator.groundingHit(
                admitted.encounterId(), TARGET, "projectile:Lure+item:LureTool", 100.0D,
                world, 62_000L);
        assertEquals(EncounterPhase.GROUNDING, lure.phase());
        assertFalse(coordinator.captureAllowed(TARGET));

        DynamicEncounterCoordinator.TransitionResult descent = coordinator.groundingHit(
                admitted.encounterId(), TARGET, "projectile:Stagger+item:StaggerTool", 1.0D,
                world, 63_000L);
        assertEquals("grounding-descent-active", descent.reason());
        assertEquals(EncounterPhase.GROUNDING, descent.phase());
        assertFalse(coordinator.captureAllowed(TARGET));

        world.grounded = true;
        DynamicEncounterCoordinator.TransitionResult landed = coordinator.tick(
                admitted.encounterId(), world, 64_000L);
        assertEquals("capture-window-open", landed.reason());
        assertEquals(EncounterPhase.GROUNDED_CAPTURE_WINDOW, landed.phase());
        assertTrue(coordinator.captureAllowed(TARGET));
    }

    @Test
    void missingOrNonTameworkTalismanFailsClosed() throws Exception {
        TameworkApi api = api(new AtomicBoolean(false));
        HyDragonStateStore stateStore = stateStore();
        DragonEncounterConfig definition = encounterConfig();
        EncounterEligibilityService eligibility = new EncounterEligibilityService(api, stateStore);
        HyDragonConfigRepository.Snapshot configs = snapshot(definition, speciesConfig());

        assertEquals("flightmasters-talisman-required",
                eligibility.evaluate(definition, configs, candidate(Set.of()), true).reason());

        set(definition.getPlayerEligibility(), "requiredItemId", "External_Flight_Item");
        assertEquals("unsupported-flight-item",
                eligibility.evaluate(definition, configs, candidate(Set.of("External_Flight_Item")), true).reason());
    }

    @Test
    void weatherAndAvatarFlightEligibilityFailClosedAndRecover() throws Exception {
        TameworkApi api = api(new AtomicBoolean(false));
        HyDragonStateStore store = stateStore();
        DragonEncounterConfig definition = encounterConfig();
        DragonSpeciesConfig species = speciesConfig();
        EncounterEligibilityService eligibility = new EncounterEligibilityService(api, store);

        EncounterCandidate clearWeather = new EncounterCandidate(
                PLAYER, "world", "region", "environment", 10.0D, 200.0D, 20.0D,
                Set.of("clear"), Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID));
        assertEquals("weather-gate",
                eligibility.evaluate(definition, snapshot(definition, species), clearWeather, true).reason());
        assertTrue(eligibility.evaluate(
                definition, snapshot(definition, species),
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)), true).allowed());

        set(species.getMount(), "mode", "GROUND");
        assertEquals("active-avatar-flight-dragon-required", eligibility.evaluate(
                definition, snapshot(definition, species),
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)), true).reason());

        HyDragonStateStore noDragonStore = new HyDragonStateStore(
                temporaryDirectory.resolve("no-flying-dragon.properties"));
        assertEquals("active-avatar-flight-dragon-required", new EncounterEligibilityService(api, noDragonStore)
                .evaluate(definition, snapshot(definition, speciesConfig()),
                        candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)), true)
                .reason());
    }

    @Test
    void concurrencyAndUnsafePlacementFailWithoutDuplicatingTargets() throws Exception {
        TameworkApi api = api(new AtomicBoolean(false));
        HyDragonStateStore store = stateStore();
        DragonEncounterConfig definition = encounterConfig();
        DynamicEncounterCoordinator coordinator = new DynamicEncounterCoordinator(
                api, store, new EncounterEligibilityService(api, store));
        FakeWorld world = new FakeWorld();
        HyDragonConfigRepository.Snapshot configs = snapshot(definition, speciesConfig());

        assertTrue(coordinator.admit(definition, configs,
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)),
                world, true, 60_000L).admitted());
        assertEquals("encounter-concurrency-limit", coordinator.admit(
                definition, configs,
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)),
                world, true, 120_000L).reason(), "per-region limit");

        set(definition.getAdmission(), "globalLimit", 1);
        EncounterCandidate otherRegion = new EncounterCandidate(
                PLAYER, "world", "other-region", "environment", 530.0D, 200.0D, 20.0D,
                Set.of("storm"), Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID));
        assertEquals("encounter-concurrency-limit", coordinator.admit(
                definition, configs, otherRegion, world, true, 120_000L).reason(), "global limit");

        HyDragonStateStore unsafeStore = new HyDragonStateStore(
                temporaryDirectory.resolve("unsafe-placement.properties"));
        unsafeStore.putProfileExtension(ProfileExtensionRecord.fullDragon(
                ACCESS_PROFILE, "hydragon:nordic", Optional.empty()));
        FakeWorld unsafeWorld = new FakeWorld();
        unsafeWorld.spawnAllowed = false;
        DynamicEncounterCoordinator unsafeCoordinator = new DynamicEncounterCoordinator(
                api, unsafeStore, new EncounterEligibilityService(api, unsafeStore));
        DynamicEncounterCoordinator.AdmissionResult unsafe = unsafeCoordinator.admit(
                definition, configs,
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)),
                unsafeWorld, true, 60_000L);
        assertFalse(unsafe.admitted());
        assertEquals("no-player-safe-placement", unsafe.reason());
        assertEquals(1, unsafeWorld.spawnCalls);
        assertEquals(EncounterPhase.COOLDOWN, EncounterCheckpoint.decode(
                unsafeStore.snapshot().encounters().values().stream().findFirst().orElseThrow().phase()).phase());
    }

    @Test
    void temporaryEligibilityLossUsesDurableGraceAndRestorationCancelsCleanup() throws Exception {
        TameworkApi api = api(new AtomicBoolean(false));
        Path statePath = temporaryDirectory.resolve("eligibility-grace.properties");
        HyDragonStateStore store = new HyDragonStateStore(statePath);
        store.putProfileExtension(ProfileExtensionRecord.fullDragon(
                ACCESS_PROFILE, "hydragon:nordic", Optional.empty()));
        DragonEncounterConfig definition = encounterConfig();
        HyDragonConfigRepository.Snapshot configs = snapshot(definition, speciesConfig());
        DynamicEncounterCoordinator coordinator = new DynamicEncounterCoordinator(
                api, store, new EncounterEligibilityService(api, store));
        FakeWorld world = new FakeWorld();
        DynamicEncounterCoordinator.AdmissionResult admitted = coordinator.admit(
                definition, configs,
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)),
                world, true, 60_000L);

        assertEquals("eligibility-grace-started", coordinator.recheckEligibility(
                admitted.encounterId(), definition, configs, java.util.List.of(), true,
                world, true, 61_000L).reason());
        HyDragonStateStore restarted = new HyDragonStateStore(statePath);
        EncounterCheckpoint persisted = EncounterCheckpoint.decode(
                restarted.snapshot().encounter(admitted.encounterId()).orElseThrow().phase());
        assertEquals(61_000L, persisted.eligibilityLostAtEpochMillis());

        DynamicEncounterCoordinator restartedCoordinator = new DynamicEncounterCoordinator(
                api, restarted, new EncounterEligibilityService(api, restarted));
        assertEquals("eligibility-grace-active", restartedCoordinator.recheckEligibility(
                admitted.encounterId(), definition, configs, java.util.List.of(), true,
                world, true, 61_999L).reason());
        assertEquals("eligibility-restored", restartedCoordinator.recheckEligibility(
                admitted.encounterId(), definition, configs,
                java.util.List.of(candidate(Set.of(
                        EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID))), true,
                world, true, 62_000L).reason());
        assertEquals(0, world.retireCalls);

        restartedCoordinator.recheckEligibility(
                admitted.encounterId(), definition, configs, java.util.List.of(), true,
                world, true, 63_000L);
        assertEquals("eligibility-grace-expired", restartedCoordinator.recheckEligibility(
                admitted.encounterId(), definition, configs, java.util.List.of(), true,
                world, true, 64_000L).reason());
        assertEquals(1, world.retireCalls);
        EncounterRecord cooldown = restarted.snapshot().encounter(admitted.encounterId()).orElseThrow();
        assertEquals(EncounterPhase.COOLDOWN, EncounterCheckpoint.decode(cooldown.phase()).phase());
        assertEquals(84_000L, cooldown.cooldownUntilEpochMillis());
    }

    @Test
    void committedCaptureAtTimeoutNeverRetiresTarget() throws Exception {
        AtomicBoolean captured = new AtomicBoolean(false);
        TameworkApi api = api(captured);
        HyDragonStateStore stateStore = stateStore();
        DragonEncounterConfig definition = encounterConfig();
        DynamicEncounterCoordinator coordinator = new DynamicEncounterCoordinator(
                api, stateStore, new EncounterEligibilityService(api, stateStore));
        FakeWorld world = new FakeWorld();
        DynamicEncounterCoordinator.AdmissionResult admitted = coordinator.admit(
                definition, snapshot(definition, speciesConfig()),
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)),
                world, true, 60_000L);
        assertNotNull(admitted.encounterId());

        captured.set(true);
        DynamicEncounterCoordinator.TransitionResult result = coordinator.tick(
                admitted.encounterId(), world, 71_000L);
        assertTrue(result.transitioned());
        assertEquals("captured-at-cleanup-boundary", result.reason());
        assertEquals(0, world.retireCalls);
        assertFalse(coordinator.captureAllowed(TARGET));
    }

    @Test
    void permanentRemovalCheckpointsCooldownBeforeRestart() throws Exception {
        Path statePath = temporaryDirectory.resolve("permanent-removal.properties");
        TameworkApi api = api(new AtomicBoolean(false));
        HyDragonStateStore store = new HyDragonStateStore(statePath);
        store.putProfileExtension(ProfileExtensionRecord.fullDragon(
                ACCESS_PROFILE, "hydragon:nordic", Optional.empty()));
        DragonEncounterConfig definition = encounterConfig();
        DynamicEncounterCoordinator coordinator = new DynamicEncounterCoordinator(
                api, store, new EncounterEligibilityService(api, store));
        DynamicEncounterCoordinator.AdmissionResult admitted = coordinator.admit(
                definition, snapshot(definition, speciesConfig()),
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)),
                new FakeWorld(), true, 60_000L);

        DynamicEncounterCoordinator.TransitionResult removed = coordinator.onTargetPermanentlyRemoved(
                admitted.encounterId(), TARGET, new FakeWorld(), 61_000L);

        assertEquals("target-permanently-removed", removed.reason());
        HyDragonStateStore restarted = new HyDragonStateStore(statePath);
        EncounterRecord cooldown = restarted.snapshot().encounter(admitted.encounterId()).orElseThrow();
        assertEquals(EncounterPhase.COOLDOWN, EncounterCheckpoint.decode(cooldown.phase()).phase());
        assertEquals(81_000L, cooldown.cooldownUntilEpochMillis());
    }

    @Test
    void removedDefinitionCannotStrandPersistedEncounterAcrossRestart() throws Exception {
        Path statePath = temporaryDirectory.resolve("removed-definition.properties");
        TameworkApi api = api(new AtomicBoolean(false));
        HyDragonStateStore initialStore = new HyDragonStateStore(statePath);
        initialStore.putProfileExtension(ProfileExtensionRecord.fullDragon(
                ACCESS_PROFILE, "hydragon:nordic", Optional.empty()));
        DragonEncounterConfig definition = encounterConfig();
        DynamicEncounterCoordinator initialCoordinator = new DynamicEncounterCoordinator(
                api, initialStore, new EncounterEligibilityService(api, initialStore));
        FakeWorld initialWorld = new FakeWorld();
        DynamicEncounterCoordinator.AdmissionResult admitted = initialCoordinator.admit(
                definition, snapshot(definition, speciesConfig()),
                candidate(Set.of(EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID)),
                initialWorld, true, 60_000L);
        assertTrue(admitted.admitted());

        HyDragonStateStore restartedStore = new HyDragonStateStore(statePath);
        EncounterRecord restartedRecord = restartedStore.snapshot().encounter(admitted.encounterId()).orElseThrow();
        assertEquals(100.0D, restartedRecord.definitionSnapshot().groundingThreshold());
        assertEquals(10_000L, restartedRecord.definitionSnapshot().encounterTimeoutMs());

        FakeWorld restartedWorld = new FakeWorld();
        AtomicInteger dispatches = new AtomicInteger();
        DynamicEncounterCoordinator restartedCoordinator = new DynamicEncounterCoordinator(
                api, restartedStore, new EncounterEligibilityService(api, restartedStore));
        HyDragonConfigRepository.Snapshot definitionRemoved = new HyDragonConfigRepository.Snapshot(
                Map.of(), Map.of(), Map.of(), Map.of(), java.util.List.of());
        DynamicEncounterRuntime runtime = new DynamicEncounterRuntime(
                api,
                restartedStore,
                () -> definitionRemoved,
                () -> null,
                (worldName, targetNpcUuid, callback) -> {
                    dispatches.incrementAndGet();
                    callback.accept(restartedWorld);
                },
                restartedCoordinator,
                Clock.fixed(Instant.ofEpochMilli(62_000L), ZoneOffset.UTC));

        runtime.reconcileAll();

        assertEquals(1, dispatches.get(), "removed definitions must not suppress restart reconciliation");
        EncounterRecord reattached = restartedStore.snapshot().encounter(admitted.encounterId()).orElseThrow();
        assertEquals(EncounterPhase.AERIAL, EncounterCheckpoint.decode(reattached.phase()).phase());
        assertEquals(Optional.of(TARGET), reattached.targetNpcUuid());

        restartedWorld.targetPresence = EncounterWorldGateway.TargetPresence.ABSENT;
        runtime.reconcileAll();

        EncounterRecord cooledDown = restartedStore.snapshot().encounter(admitted.encounterId()).orElseThrow();
        assertEquals(EncounterPhase.COOLDOWN, EncounterCheckpoint.decode(cooledDown.phase()).phase());
        assertTrue(cooledDown.targetNpcUuid().isEmpty());
        assertEquals(82_000L, cooledDown.cooldownUntilEpochMillis());
    }

    @Test
    void restartReconciliationPreservesEveryDurableActivePhase() throws Exception {
        TameworkApi api = api(new AtomicBoolean(false));
        DragonEncounterConfig definition = encounterConfig();
        for (EncounterPhase phase : java.util.List.of(
                EncounterPhase.ADMITTED,
                EncounterPhase.AERIAL,
                EncounterPhase.GROUNDING,
                EncounterPhase.GROUNDED_CAPTURE_WINDOW,
                EncounterPhase.RECONCILING)) {
            Path path = temporaryDirectory.resolve("restart-" + phase.name() + ".properties");
            HyDragonStateStore initial = new HyDragonStateStore(path);
            EncounterCheckpoint checkpoint = phase == EncounterPhase.GROUNDING
                    ? new EncounterCheckpoint(phase, 40.0D, 61_000L)
                    : new EncounterCheckpoint(phase, 0.0D, 61_000L);
            EncounterRecord persisted = new EncounterRecord(
                    EncounterRecord.SCHEMA_VERSION,
                    "encounter:restart:" + phase.name().toLowerCase(java.util.Locale.ROOT),
                    definition.getId(), "world", "region", checkpoint.encode(),
                    EncounterDefinitionSnapshot.capture(definition),
                    phase == EncounterPhase.ADMITTED ? Optional.empty() : Optional.of(TARGET),
                    Set.of(PLAYER), 60_000L, 60_500L, 61_000L,
                    phase == EncounterPhase.GROUNDED_CAPTURE_WINDOW ? 100_000L : 0L);
            initial.putEncounter(persisted);

            HyDragonStateStore restarted = new HyDragonStateStore(path);
            DynamicEncounterCoordinator coordinator = new DynamicEncounterCoordinator(
                    api, restarted, new EncounterEligibilityService(api, restarted));
            DynamicEncounterCoordinator.TransitionResult result = coordinator.reconcile(
                    persisted.encounterId(), new FakeWorld(), 62_000L);

            EncounterPhase expected = phase == EncounterPhase.ADMITTED || phase == EncounterPhase.RECONCILING
                    ? EncounterPhase.AERIAL : phase;
            assertEquals(expected, result.phase(), "restart phase " + phase);
            EncounterCheckpoint recovered = EncounterCheckpoint.decode(
                    restarted.snapshot().encounter(persisted.encounterId()).orElseThrow().phase());
            assertEquals(expected, recovered.phase(), "persisted restart phase " + phase);
            assertEquals(61_000L, recovered.eligibilityLostAtEpochMillis(),
                    "eligibility grace must survive restart phase " + phase);
            if (phase == EncounterPhase.GROUNDING) assertEquals(40.0D, recovered.groundingBuildup());
        }
    }

    private HyDragonStateStore stateStore() throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(temporaryDirectory.resolve("state.properties"));
        store.putProfileExtension(ProfileExtensionRecord.fullDragon(
                ACCESS_PROFILE, "hydragon:nordic", Optional.empty()));
        return store;
    }

    private static EncounterCandidate candidate(Set<String> items) {
        return new EncounterCandidate(
                PLAYER, "world", "region", "environment", 10.0D, 200.0D, 20.0D,
                Set.of("storm"), items);
    }

    private static TameworkApi api(AtomicBoolean captured) {
        NpcProfileView accessProfile = new NpcProfileView(
                ACCESS_PROFILE.toString(), ACCESS_NPC, PLAYER, "owner", "Tamed_Nordic", "Nordic", null,
                true, null, null, Set.of(), Set.of(), 1L);
        NpcProfilesApi profiles = proxy(NpcProfilesApi.class, (method, arguments) -> switch (method) {
            case "getByProfileId" -> Optional.of(accessProfile);
            case "resolveProfileId" -> captured.get() ? Optional.of("captured-profile") : Optional.empty();
            case "getByNpcUuid", "getActiveSnapshot" -> Optional.empty();
            case "listActiveSnapshotTypes" -> Set.of();
            default -> null;
        });
        PopulationGroupApi groups = proxy(PopulationGroupApi.class, (method, arguments) -> switch (method) {
            case "getCounts" -> Optional.of(new PopulationGroupCountsView(
                    PLAYER, "hydragon:full_dragons", PopulationGroupScope.PER_WORLD, "world",
                    1L, 0L, 1L, 0L, 1L, 1L, false, false, 1L));
            case "getDefinition" -> Optional.empty();
            case "resolveForRole" -> java.util.List.of();
            default -> null;
        });
        PolicyApi policies = proxy(PolicyApi.class, (method, arguments) -> switch (method) {
            case "populationGroups" -> groups;
            case "isOwner" -> true;
            default -> null;
        });
        return proxy(TameworkApi.class, (method, arguments) -> switch (method) {
            case "profiles" -> profiles;
            case "policies" -> policies;
            case "getApiVersion" -> "0.9";
            default -> null;
        });
    }

    private static DragonEncounterConfig encounterConfig() throws Exception {
        DragonEncounterConfig config = construct(DragonEncounterConfig.class);
        set(config, "id", "hydragon:test");
        set(config, "enabled", true);
        set(config, "targetSpeciesId", "hydragon:nordic");
        set(config.getRegionsAndAltitude(), "environmentIds", new String[] { "environment" });
        set(config.getRegionsAndAltitude(), "minY", 100.0D);
        set(config.getRegionsAndAltitude(), "maxY", 300.0D);
        set(config.getWeatherPredicate(), "mode", "AnyOf");
        set(config.getWeatherPredicate(), "weatherIds", new String[] { "storm" });
        set(config.getPlayerEligibility(), "activeCompanionGroup", "hydragon:full_dragons");
        set(config.getPlayerEligibility(), "requiredMountMode", "AVATAR_FLIGHT");
        set(config.getPlayerEligibility(), "requiredItemId",
                EncounterEligibilityService.FLIGHTMASTERS_TALISMAN_ITEM_ID);
        set(config.getAdmission(), "chance", 1.0D);
        set(config.getAdmission(), "evaluationCooldownSeconds", 60.0D);
        set(config.getAdmission(), "perRegionLimit", 1);
        set(config.getAdmission(), "globalLimit", 2);
        set(config, "phases", new String[] { "AERIAL", "GROUNDING", "GROUNDED_CAPTURE_WINDOW" });
        set(config.getGrounding(), "buildupSourceIds",
                new String[] {
                        "projectile:Lure+item:LureTool",
                        "projectile:Stagger+item:StaggerTool"
                });
        set(config.getGrounding(), "threshold", 100.0D);
        set(config.getGrounding(), "groundedState", "Combat.AirLand");
        set(config.getGrounding(), "groundedEffectId", "test-grounded");
        set(config.getGrounding(), "captureWindowSeconds", 45.0D);
        set(config.getCleanupAndCooldown(), "encounterTimeoutSeconds", 10.0D);
        set(config.getCleanupAndCooldown(), "retryCooldownSeconds", 20.0D);
        set(config.getCleanupAndCooldown(), "eligibilityGraceSeconds", 1.0D);
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static DragonSpeciesConfig speciesConfig() throws Exception {
        DragonSpeciesConfig species = construct(DragonSpeciesConfig.class);
        set(species, "id", "hydragon:nordic");
        set(species, "wildRoleIds", new String[] { "Nordic_Wild" });
        set(species, "tamedRoleIdByWildRole", Map.of("Nordic_Wild", "Nordic_Tamed"));
        set(species, "difficultyId", "T3");
        set(species, "dropListId", "Nordic_Drops");
        set(species.getMount(), "mode", "AVATAR_FLIGHT");
        set(species.getMount(), "avatarFlightConfigId", "NordicFlight");
        set(species.getPresentation(), "localizationPrefix", "npc.nordic");
        assertTrue(species.validate().isEmpty(), species.validate().toString());
        return species;
    }

    private static HyDragonConfigRepository.Snapshot snapshot(
            DragonEncounterConfig encounter, DragonSpeciesConfig species) {
        return new HyDragonConfigRepository.Snapshot(
                Map.of(species.getId(), species), Map.of(), Map.of(), Map.of(encounter.getId(), encounter), java.util.List.of());
    }

    private static <T> T construct(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (instance, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "Fake" + type.getSimpleName();
                            case "hashCode" -> System.identityHashCode(instance);
                            case "equals" -> instance == arguments[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method.getName(), arguments);
                });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws Throwable;
    }

    private static final class FakeWorld implements EncounterWorldGateway {
        int spawnCalls;
        int groundedCalls;
        int retireCalls;
        boolean grounded = true;
        boolean spawnAllowed = true;
        TargetPresence targetPresence = TargetPresence.PRESENT;

        @Override public boolean isWorldThread() { return true; }
        @Override public SpawnResult spawn(SpawnRequest request) {
            spawnCalls++;
            return spawnAllowed ? SpawnResult.success(TARGET)
                    : SpawnResult.failure("no-player-safe-placement");
        }
        @Override public TargetLookup findTarget(String encounterId, String worldName, UUID expectedTargetUuid) {
            return switch (targetPresence) {
                case PRESENT -> TargetLookup.present(TARGET);
                case ABSENT -> TargetLookup.absent();
                case UNKNOWN -> TargetLookup.unknown();
            };
        }
        @Override public boolean applyGroundedState(UUID targetNpcUuid, String groundedState, String effectId) {
            groundedCalls++;
            return true;
        }
        @Override public boolean isGrounded(UUID targetNpcUuid) { return grounded; }
        @Override public boolean retireTarget(UUID targetNpcUuid, String reason) {
            retireCalls++;
            return true;
        }
    }
}
