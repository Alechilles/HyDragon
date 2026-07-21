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
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
                definition, world, 61_000L);
        assertEquals(EncounterPhase.GROUNDING, progress.phase());
        assertEquals(40.0D, progress.buildup());

        DynamicEncounterCoordinator.TransitionResult recovered = coordinator.reconcile(
                admitted.encounterId(), definition, world, 62_000L);
        assertEquals(EncounterPhase.GROUNDING, recovered.phase());
        assertEquals(40.0D, recovered.buildup());

        DynamicEncounterCoordinator.TransitionResult grounded = coordinator.groundingHit(
                admitted.encounterId(), TARGET, "projectile:Stagger+item:StaggerTool", 60.0D,
                definition, world, 63_000L);
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
                definition, world, 61_000L);
        assertFalse(outOfOrder.transitioned());
        assertEquals("grounding-lure-required", outOfOrder.reason());

        DynamicEncounterCoordinator.TransitionResult lure = coordinator.groundingHit(
                admitted.encounterId(), TARGET, "projectile:Lure+item:LureTool", 100.0D,
                definition, world, 62_000L);
        assertEquals(EncounterPhase.GROUNDING, lure.phase());
        assertFalse(coordinator.captureAllowed(TARGET));

        DynamicEncounterCoordinator.TransitionResult descent = coordinator.groundingHit(
                admitted.encounterId(), TARGET, "projectile:Stagger+item:StaggerTool", 1.0D,
                definition, world, 63_000L);
        assertEquals("grounding-descent-active", descent.reason());
        assertEquals(EncounterPhase.GROUNDING, descent.phase());
        assertFalse(coordinator.captureAllowed(TARGET));

        world.grounded = true;
        DynamicEncounterCoordinator.TransitionResult landed = coordinator.tick(
                admitted.encounterId(), definition, world, 64_000L);
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
                admitted.encounterId(), definition, world, 71_000L);
        assertTrue(result.transitioned());
        assertEquals("captured-at-cleanup-boundary", result.reason());
        assertEquals(0, world.retireCalls);
        assertFalse(coordinator.captureAllowed(TARGET));
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

        @Override public boolean isWorldThread() { return true; }
        @Override public SpawnResult spawn(SpawnRequest request) {
            spawnCalls++;
            return SpawnResult.success(TARGET);
        }
        @Override public TargetLookup findTarget(String encounterId, String worldName, UUID expectedTargetUuid) {
            return TargetLookup.present(TARGET);
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
