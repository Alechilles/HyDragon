package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityRuntime;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityService;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityState;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityStateRepository;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityWorld;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.runtime.ConsumableReservation;
import com.alechilles.hydragon.runtime.GameplayResult;
import com.alechilles.hydragon.runtime.MiniwyvernAttunementService;
import com.alechilles.hydragon.runtime.StateStoreMiniwyvernProfileProjection;
import com.alechilles.hydragon.runtime.StateStoreOperationJournal;
import com.alechilles.hydragon.runtime.StateStoreSoulBondLedger;
import com.alechilles.hydragon.runtime.TameworkGameplayAdapter;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end acceptance coverage for HYD-SOUL-006 across HyDragon's production boundaries. */
class MiniwyvernLifecycleContinuityTest {
    private static final UUID OWNER = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE = UUID.fromString("81000000-0000-0000-0000-000000000002");
    private static final UUID FIRST_NPC = UUID.fromString("81000000-0000-0000-0000-000000000003");
    private static final UUID REVIVED_NPC = UUID.fromString("81000000-0000-0000-0000-000000000004");

    @TempDir Path temporaryDirectory;

    @Test
    void sameCanonicalProfileSurvivesAttunementUnloadRestartDeathAndRevive() throws Exception {
        Path statePath = temporaryDirectory.resolve("hydragon-state.properties");
        HyDragonStateStore initialStore = claimedStore(statePath);
        CanonicalTameworkAuthority authority = new CanonicalTameworkAuthority();
        MemoryEvents events = new MemoryEvents();
        TameworkApi api = api(authority, events);
        MiniwyvernAttunementService attunements = new MiniwyvernAttunementService(
                new TameworkGameplayAdapter(api),
                new StateStoreSoulBondLedger(initialStore),
                new StateStoreOperationJournal(initialStore, () -> 2_000L),
                new StateStoreMiniwyvernProfileProjection(initialStore));

        GameplayResult attuned = attunements.attune(
                OWNER, "fire", new EssenceReservation("attune:fire:one"))
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, attuned.status());
        assertEquals(PROFILE, initialStore.snapshot().playerSoulBond(OWNER).orElseThrow()
                .profileId().orElseThrow());
        assertEquals("fire", initialStore.snapshot().profileExtension(PROFILE).orElseThrow()
                .archetypeId().orElseThrow());
        authority.assertIdentityState("Ember", 0.42D, 7, 350.0D);

        HyDragonStateStore restartedStore = new HyDragonStateStore(statePath);
        assertEquals(PROFILE, restartedStore.snapshot().playerSoulBond(OWNER).orElseThrow()
                .profileId().orElseThrow());
        assertEquals("fire", restartedStore.snapshot().profileExtension(PROFILE).orElseThrow()
                .archetypeId().orElseThrow());

        AtomicInteger firstRuntimeDispatches = new AtomicInteger();
        AtomicInteger cleanupCalls = new AtomicInteger();
        MemoryAbilityStates firstRuntimeStates = new MemoryAbilityStates();
        firstRuntimeStates.save(PROFILE.toString(), trackedAbilityState());
        MiniwyvernAbilityRuntime firstRuntime = runtime(
                api, restartedStore, firstRuntimeStates, firstRuntimeDispatches, cleanupCalls,
                Clock.fixed(Instant.ofEpochMilli(3_000L), ZoneOffset.UTC));
        firstRuntime.start();
        assertEquals(TameworkGameplayAdapter.CALLER_NAMESPACE, MiniwyvernAbilityRuntime.CALLER_NAMESPACE);

        NpcProfileView loaded = authority.profileView();
        authority.currentNpcUuid = null;
        events.emit(new NpcProfileChangedEvent(
                PROFILE.toString(), EnumSet.of(ProfileChangeType.CURRENT_NPC_UUID),
                loaded, authority.profileView(), 3_100L));
        assertEquals(1, firstRuntimeDispatches.get(), "unload must reconcile the previous projection");
        assertEquals(1, cleanupCalls.get(), "unload must remove tracked source-keyed effects");
        assertTrue(firstRuntimeStates.states.get(PROFILE.toString()).appliedSourceKeys().isEmpty());

        events.emit(new ProvisionedCompanionDeathRecordedEvent(
                UUID.fromString("81000000-0000-0000-0000-000000000007"),
                "hydragon",
                "soul-bond:owner",
                PROFILE.toString(), OWNER, TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                FIRST_NPC, 7L, 8L, false, 3_150L, 3_150L));
        assertEquals(1, firstRuntimeDispatches.get(), "a foreign caller namespace must be ignored");

        events.emit(new ProvisionedCompanionDeathRecordedEvent(
                UUID.fromString("81000000-0000-0000-0000-000000000005"),
                TameworkGameplayAdapter.CALLER_NAMESPACE,
                "soul-bond:owner",
                PROFILE.toString(), OWNER, TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                FIRST_NPC, 8L, 9L, false, 3_200L, 3_200L));
        assertEquals(2, firstRuntimeDispatches.get(), "death must clean the same profile's effects");
        firstRuntime.close();

        HyDragonStateStore secondRestart = new HyDragonStateStore(statePath);
        authority.currentNpcUuid = REVIVED_NPC;
        AtomicInteger restartedDispatches = new AtomicInteger();
        MiniwyvernAbilityRuntime restartedRuntime = runtime(
                api, secondRestart, new MemoryAbilityStates(), restartedDispatches, new AtomicInteger(),
                Clock.fixed(Instant.ofEpochMilli(4_000L), ZoneOffset.UTC));
        restartedRuntime.start();
        events.emit(new ProvisionedCompanionRevivedEvent(
                UUID.fromString("81000000-0000-0000-0000-000000000006"),
                TameworkGameplayAdapter.CALLER_NAMESPACE,
                "soul-bond:owner",
                PROFILE.toString(), OWNER, TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                REVIVED_NPC, PopulationCompanionLifecycle.ACTIVE,
                9L, 10L, true, 4_000L, 4_000L));

        assertEquals(1, restartedDispatches.get(), "revive must reactivate by canonical profile ID");
        assertEquals(PROFILE.toString(), authority.lastProfileLookup);
        assertNotEquals(FIRST_NPC, authority.currentNpcUuid);
        assertEquals(REVIVED_NPC, authority.currentNpcUuid);
        assertEquals("fire", secondRestart.snapshot().profileExtension(PROFILE).orElseThrow()
                .archetypeId().orElseThrow());
        authority.assertIdentityState("Ember", 0.42D, 7, 350.0D);
        restartedRuntime.close();
    }

    private HyDragonStateStore claimedStore(Path statePath) throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(statePath);
        assertEquals(com.alechilles.hydragon.persistence.MutationOutcome.APPLIED,
                store.beginSoulBond(OWNER, "soul-bond:owner"));
        assertEquals(com.alechilles.hydragon.persistence.MutationOutcome.APPLIED,
                store.completeSoulBondWithMiniwyvernProfile(
                        OWNER, "soul-bond:owner", PROFILE, 1_000L));
        return store;
    }

    private static MiniwyvernAbilityRuntime runtime(
            TameworkApi api,
            HyDragonStateStore store,
            MemoryAbilityStates states,
            AtomicInteger dispatches,
            AtomicInteger cleanupCalls,
            Clock clock) {
        return new MiniwyvernAbilityRuntime(
                api,
                store,
                () -> new HyDragonConfigRepository.Snapshot(
                        Map.of(), Map.of(), Map.of(), Map.of(), java.util.List.of()),
                () -> new FeatureGate(
                        HyDragonFeature.MINIWYVERN_ABILITIES,
                        true,
                        Set.of(), Set.of(), java.util.List.of()),
                (owner, npc, callback) -> {
                    dispatches.incrementAndGet();
                    callback.accept(world(owner, npc, cleanupCalls));
                },
                new MiniwyvernAbilityService(states),
                clock);
    }

    private static MiniwyvernAbilityWorld world(UUID owner, UUID npc, AtomicInteger cleanupCalls) {
        return (MiniwyvernAbilityWorld) Proxy.newProxyInstance(
                MiniwyvernAbilityWorld.class.getClassLoader(),
                new Class<?>[] {MiniwyvernAbilityWorld.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isWorldThread" -> true;
                    case "worldName" -> "world";
                    case "owner" -> Optional.of(new MiniwyvernAbilityWorld.Target(
                            owner, null, "world", 0.0D, true));
                    case "companion" -> Optional.of(new MiniwyvernAbilityWorld.Target(
                            npc, owner, "world", 0.0D, true));
                    case "nearbyTargets" -> java.util.List.of();
                    case "health" -> new MiniwyvernAbilityWorld.Health(42.0D, 100.0D);
                    case "removeOwnerModifiers" -> {
                        cleanupCalls.incrementAndGet();
                        yield true;
                    }
                    default -> method.getReturnType() == boolean.class;
                });
    }

    private static MiniwyvernAbilityState trackedAbilityState() {
        String source = "hydragon:mini:" + PROFILE + ":fire:passive";
        return new MiniwyvernAbilityState(
                MiniwyvernAbilityState.SCHEMA_VERSION,
                "fire",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(source),
                Map.of(source, OWNER),
                Map.of(source, 10_000L),
                3_000L);
    }

    private static TameworkApi api(CanonicalTameworkAuthority authority, MemoryEvents events) {
        NpcProfilesApi profiles = (NpcProfilesApi) Proxy.newProxyInstance(
                NpcProfilesApi.class.getClassLoader(), new Class<?>[] {NpcProfilesApi.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getByProfileId" -> {
                        authority.lastProfileLookup = (String) arguments[0];
                        yield PROFILE.toString().equals(arguments[0])
                                ? Optional.of(authority.profileView()) : Optional.empty();
                    }
                    case "getByNpcUuid", "getActiveSnapshot" -> Optional.empty();
                    case "resolveProfileId" -> Optional.of(PROFILE.toString());
                    case "listActiveSnapshotTypes" -> Set.of();
                    default -> null;
                });
        PolicyApi policies = (PolicyApi) Proxy.newProxyInstance(
                PolicyApi.class.getClassLoader(), new Class<?>[] {PolicyApi.class},
                (proxy, method, arguments) -> method.getName().equals("isOwner") ? true : null);
        return new TameworkApi() {
            @Override public String getApiVersion() { return "0.9.0"; }
            @Override public EnumSet<TameworkApiCapability> getCapabilities() {
                return EnumSet.of(
                        TameworkApiCapability.PROFILE_DATA,
                        TameworkApiCapability.PROFILE_DATA_TRANSACTIONS,
                        TameworkApiCapability.PROFILES,
                        TameworkApiCapability.POLICY,
                        TameworkApiCapability.EVENTS);
            }
            @Override public NpcProfilesApi profiles() { return profiles; }
            @Override public CommandLinksApi commandLinks() { return null; }
            @Override public ProgressionApi progression() { return null; }
            @Override public PolicyApi policies() { return policies; }
            @Override public InteractionExtensionApi interactionExtensions() { return null; }
            @Override public TraitEffectApi traitEffects() { return null; }
            @Override public ProfileDataApi profileData() { return authority; }
            @Override public TameworkEventsApi events() { return events; }
            @Override public TameworkConfigReadApi configs() { return null; }
            @Override public DiagnosticsApi diagnostics() { return null; }
        };
    }

    private static final class CanonicalTameworkAuthority implements ProfileDataApi {
        private final String customName = "Ember";
        private final double healthRatio = 0.42D;
        private final int level = 7;
        private final double totalXp = 350.0D;
        private final Map<String, ProfileDataEntryView> values = new LinkedHashMap<>();
        private final Map<String, ProfileDataOperationView> operations = new LinkedHashMap<>();
        private UUID currentNpcUuid = FIRST_NPC;
        private String lastProfileLookup;

        private NpcProfileView profileView() {
            return new NpcProfileView(
                    PROFILE.toString(), currentNpcUuid, OWNER, "owner",
                    TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    "Miniwyvern", customName, true, null, null,
                    Set.of(), Set.of("HEALTH", "LEVELING"), 1L);
        }

        private void assertIdentityState(String name, double health, int expectedLevel, double xp) {
            assertEquals(PROFILE.toString(), profileView().profileId());
            assertEquals(name, profileView().customName());
            assertEquals(health, healthRatio);
            assertEquals(expectedLevel, level);
            assertEquals(xp, totalXp);
        }

        @Override public Optional<String> get(String profileId, String namespace, String key) {
            return getVersioned(profileId, namespace, key).map(ProfileDataEntryView::jsonPayload);
        }
        @Override public Map<String, String> list(String profileId, String namespace) { return Map.of(); }
        @Override public boolean put(String profileId, String namespace, String key, String jsonPayload) {
            throw new AssertionError("attunement must use fenced profile-data CAS");
        }
        @Override public boolean delete(String profileId, String namespace, String key) { return false; }
        @Override public Optional<ProfileDataEntryView> getVersioned(
                String profileId, String namespace, String key) {
            return Optional.ofNullable(values.get(profileId));
        }
        @Override public CompletionStage<Optional<ProfileDataOperationView>> findOperation(
                String namespace, String idempotencyKey) {
            return CompletableFuture.completedFuture(Optional.ofNullable(operations.get(idempotencyKey)));
        }
        @Override public CompletionStage<ProfileDataCompareAndSetResult> compareAndSet(
                ProfileDataCompareAndSetRequest request) {
            long currentRevision = Optional.ofNullable(values.get(request.profileId()))
                    .map(ProfileDataEntryView::revision).orElse(0L);
            if (currentRevision != request.expectedRevision()) {
                return CompletableFuture.completedFuture(new ProfileDataCompareAndSetResult(
                        ProfileDataCompareAndSetResult.Status.TERMINAL_DENIED,
                        "revision-conflict", null, null));
            }
            long revision = currentRevision + 1L;
            ProfileDataEntryView entry = new ProfileDataEntryView(
                    request.profileId(), request.namespace(), request.key(), revision,
                    request.jsonPayload(), revision);
            ProfileDataOperationView operation = new ProfileDataOperationView(
                    UUID.nameUUIDFromBytes(request.idempotencyKey().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    request.namespace(), request.idempotencyKey(), request.profileId(), request.key(),
                    request.expectedRevision(), revision, "attunement-payload",
                    ProfileDataOperationStatus.COMMITTED, "committed", revision);
            values.put(request.profileId(), entry);
            operations.put(request.idempotencyKey(), operation);
            return CompletableFuture.completedFuture(new ProfileDataCompareAndSetResult(
                    ProfileDataCompareAndSetResult.Status.COMMITTED,
                    "committed", operation, entry));
        }
    }

    private static final class EssenceReservation implements ConsumableReservation {
        private final String operationId;
        private EssenceReservation(String operationId) { this.operationId = operationId; }
        @Override public String operationId() { return operationId; }
        @Override public SourceEvidence sourceEvidence() {
            return new SourceEvidence(
                    "Draconic_Essence_Fire", "player", "hotbar", 0,
                    1L, "fire-essence", 1);
        }
        @Override public int quantity() { return 1; }
        @Override public CompletionStage<Disposition> consume() {
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
        @Override public CompletionStage<Disposition> release() {
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
    }

    private static final class MemoryAbilityStates implements MiniwyvernAbilityStateRepository {
        private final Map<String, MiniwyvernAbilityState> states = new LinkedHashMap<>();
        @Override public LoadResult load(String profileId) {
            MiniwyvernAbilityState state = states.get(profileId);
            return state == null ? LoadResult.missing() : LoadResult.loaded(state);
        }
        @Override public boolean save(String profileId, MiniwyvernAbilityState state) {
            states.put(profileId, state);
            return true;
        }
    }

    private static final class MemoryEvents implements TameworkEventsApi {
        private final Map<Class<?>, Consumer<?>> listeners = new LinkedHashMap<>();
        @Override public <E extends TameworkEvent> AutoCloseable subscribe(
                Class<E> type, Consumer<E> listener) {
            listeners.put(type, listener);
            return () -> listeners.remove(type, listener);
        }
        @SuppressWarnings("unchecked")
        private <E extends TameworkEvent> void emit(E event) {
            Consumer<E> listener = (Consumer<E>) listeners.get(event.getClass());
            assertTrue(listener != null, "expected lifecycle subscription for " + event.getClass().getSimpleName());
            listener.accept(event);
        }
    }
}
