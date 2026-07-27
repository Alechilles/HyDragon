package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedCompanionApi;
import com.alechilles.alecstamework.api.BondedCompanionAvailability;
import com.alechilles.alecstamework.api.BondedCompanionChangedEvent;
import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataUpdate;
import com.alechilles.alecstamework.api.BondedCompanionLeaseView;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import com.alechilles.alecstamework.api.BondedCompanionStateView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityRuntime;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityService;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityState;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityStateRepository;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityWorld;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityWorldDispatcher;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.runtime.ConsumableReservation;
import com.alechilles.hydragon.runtime.GameplayResult;
import com.alechilles.hydragon.runtime.MiniwyvernAttunementService;
import com.alechilles.hydragon.runtime.StateStoreOperationJournal;
import com.alechilles.hydragon.runtime.StateStoreSoulBondLedger;
import com.alechilles.hydragon.runtime.TameworkGameplayAdapter;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end continuity for the bonded Miniwyvern extension and active lease lifecycle. */
class MiniwyvernLifecycleContinuityTest {
    private static final UUID OWNER = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE = UUID.fromString("81000000-0000-0000-0000-000000000002");
    private static final UUID FIRST_NPC = UUID.fromString("81000000-0000-0000-0000-000000000003");
    private static final UUID REVIVED_NPC = UUID.fromString("81000000-0000-0000-0000-000000000004");

    @TempDir
    Path temporaryDirectory;

    @Test
    void bondedExtensionSurvivesAttunementStoreRestartAndReviveLease() throws Exception {
        Path statePath = temporaryDirectory.resolve("hydragon-state.properties");
        HyDragonStateStore firstStore = claimedStore(statePath);
        BondedAuthority authority = new BondedAuthority();
        TameworkApi api = api(authority);
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(api);
        BondedMiniwyvernExtensionStore extensions = new BondedMiniwyvernExtensionStore(
                adapter, new BondedMiniwyvernExtensionCodec());
        MiniwyvernAttunementService attunements = new MiniwyvernAttunementService(
                adapter, extensions, new StateStoreSoulBondLedger(firstStore),
                new StateStoreOperationJournal(firstStore, () -> 2_000L));

        GameplayResult attuned = attunements.attune(
                OWNER, "fire", new EssenceReservation("attune:fire:one"))
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, attuned.status());
        assertEquals(PROFILE, firstStore.snapshot().playerSoulBond(OWNER).orElseThrow()
                .profileId().orElseThrow());
        assertEquals("fire", authority.extension().archetypeId());

        authority.activate(FIRST_NPC);
        RecordingDispatcher firstDispatcher = new RecordingDispatcher();
        MiniwyvernAbilityRuntime firstRuntime = runtime(api, firstStore, firstDispatcher);
        firstRuntime.start();
        assertEquals(1, firstRuntime.tickSome(8));
        assertEquals(List.of(FIRST_NPC), firstDispatcher.npcs);

        authority.store();
        authority.emit(BondedCompanionStateView.ACTIVE, BondedCompanionStateView.STORED);
        assertEquals(0, firstRuntime.tickSome(8));
        assertEquals("fire", authority.extension().archetypeId(),
                "storing only detaches the lease; it must not discard bonded extension data");
        firstRuntime.close();

        HyDragonStateStore restartedStore = new HyDragonStateStore(statePath);
        assertEquals(PROFILE, restartedStore.snapshot().playerSoulBond(OWNER).orElseThrow()
                .profileId().orElseThrow());
        authority.activate(REVIVED_NPC);
        RecordingDispatcher revivedDispatcher = new RecordingDispatcher();
        MiniwyvernAbilityRuntime revivedRuntime = runtime(api, restartedStore, revivedDispatcher);
        revivedRuntime.start();

        assertEquals(1, revivedRuntime.tickSome(8));
        assertEquals(List.of(REVIVED_NPC), revivedDispatcher.npcs);
        assertEquals("fire", authority.extension().archetypeId());
        revivedRuntime.close();
    }

    private static HyDragonStateStore claimedStore(Path statePath) throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(statePath);
        store.beginSoulBond(OWNER, "soul-bond:owner");
        store.completeSoulBondWithMiniwyvernProfile(
                OWNER, "soul-bond:owner", PROFILE, 1_000L);
        return store;
    }

    private static MiniwyvernAbilityRuntime runtime(
            TameworkApi api,
            HyDragonStateStore store,
            RecordingDispatcher dispatcher) {
        return new MiniwyvernAbilityRuntime(
                api, store,
                () -> new HyDragonConfigRepository.Snapshot(
                        Map.of(), Map.of(), Map.of(), List.of()),
                () -> new FeatureGate(
                        HyDragonFeature.MINIWYVERN_ABILITIES, true,
                        Set.of("BONDED_COMPANIONS"), Set.of(), List.of()),
                dispatcher,
                new MiniwyvernAbilityService(new MemoryAbilityStates()),
                Clock.fixed(Instant.ofEpochMilli(3_000L), ZoneOffset.UTC));
    }

    private static TameworkApi api(BondedAuthority authority) {
        BondedCompanionApi bonded = proxy(BondedCompanionApi.class, authority::invoke);
        return proxy(TameworkApi.class, (method, arguments) -> switch (method) {
            case "getApiVersion" -> "3.0.0";
            case "getCapabilities" -> EnumSet.of(TameworkApiCapability.BONDED_COMPANIONS);
            case "bondedCompanions" -> bonded;
            default -> throw new AssertionError("generic API accessed: " + method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
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

    private static final class BondedAuthority {
        private static final BondedMiniwyvernExtensionCodec CODEC =
                new BondedMiniwyvernExtensionCodec();
        private String payload = CODEC.encode(
                BondedMiniwyvernExtensionDocument.neutral("miniwyvern", 1_000L));
        private long extensionRevision;
        private long profileRevision;
        private BondedCompanionProfileView profile = storedProfile();
        private Consumer<BondedCompanionChangedEvent> listener;

        private Object invoke(String method, Object[] arguments) {
            return switch (method) {
                case "availability" -> BondedCompanionAvailability.availableNow();
                case "list" -> CompletableFuture.completedFuture(success(List.of(profile)));
                case "getExtensionData" -> CompletableFuture.completedFuture(success(data()));
                case "compareAndSetExtensionData" -> update(arguments[0]);
                case "subscribe" -> subscribe(arguments[0]);
                default -> throw new AssertionError("unexpected bonded call " + method);
            };
        }

        private CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>> update(
                Object value) {
            BondedCompanionExtensionDataUpdate update = (BondedCompanionExtensionDataUpdate) value;
            assertEquals(extensionRevision, update.expectedRevision());
            payload = update.jsonPayload();
            extensionRevision++;
            return CompletableFuture.completedFuture(success(data()));
        }

        @SuppressWarnings("unchecked")
        private AutoCloseable subscribe(Object value) {
            listener = (Consumer<BondedCompanionChangedEvent>) value;
            return () -> listener = null;
        }

        private void activate(UUID npcUuid) {
            profileRevision++;
            profile = profile(BondedCompanionStateView.ACTIVE,
                    new BondedCompanionLeaseView(
                            "lease-" + npcUuid, npcUuid, "default", 1L, 0L));
        }

        private void store() {
            profileRevision++;
            profile = storedProfile();
        }

        private void emit(
                BondedCompanionStateView oldState,
                BondedCompanionStateView newState) {
            assertTrue(listener != null, "expected a bonded lifecycle subscription");
            listener.accept(new BondedCompanionChangedEvent(
                    PROFILE.toString(), OWNER, TameworkGameplayAdapter.DRAGON_HORN_ROSTER,
                    oldState, newState, profileRevision, "test"));
        }

        private BondedMiniwyvernExtensionDocument extension() {
            return CODEC.decode(payload);
        }

        private BondedCompanionExtensionData data() {
            return new BondedCompanionExtensionData(
                    new BondedCompanionExtensionDataKey(
                            OWNER, PROFILE.toString(), BondedMiniwyvernExtensionDocument.NAMESPACE),
                    payload, extensionRevision, 1_000L);
        }

        private BondedCompanionProfileView storedProfile() {
            return profile(BondedCompanionStateView.STORED, null);
        }

        private BondedCompanionProfileView profile(
                BondedCompanionStateView state,
                BondedCompanionLeaseView lease) {
            return new BondedCompanionProfileView(
                    PROFILE.toString(), OWNER, TameworkGameplayAdapter.DRAGON_HORN_ROSTER,
                    TameworkGameplayAdapter.MINIWYVERN_FAMILY,
                    TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    "Bonded Miniwyvern", "Miniwyvern", "Ember", profileRevision, state,
                    state == BondedCompanionStateView.STORED,
                    state == BondedCompanionStateView.ACTIVE,
                    state == BondedCompanionStateView.DEAD,
                    Map.of(), lease, 0L, null);
        }

        private static <T> BondedCompanionResult<T> success(T value) {
            return new BondedCompanionResult<>(BondedCompanionResultCode.SUCCESS, value, null);
        }
    }

    private static final class EssenceReservation implements ConsumableReservation {
        private final String operationId;

        private EssenceReservation(String operationId) {
            this.operationId = operationId;
        }

        public String operationId() { return operationId; }

        public SourceEvidence sourceEvidence() {
            return new SourceEvidence("Draconic_Essence_Fire", "player", "hotbar", 0,
                    1L, "fire-essence", 1);
        }

        public int quantity() { return 1; }

        public CompletableFuture<Disposition> consume() {
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }

        public CompletableFuture<Disposition> release() {
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
    }

    private static final class MemoryAbilityStates implements MiniwyvernAbilityStateRepository {
        public LoadResult load(UUID ownerUuid, String profileId) {
            return LoadResult.missing();
        }

        public boolean save(UUID ownerUuid, String profileId, MiniwyvernAbilityState state) {
            return true;
        }
    }

    private static final class RecordingDispatcher implements MiniwyvernAbilityWorldDispatcher {
        private final java.util.ArrayList<UUID> npcs = new java.util.ArrayList<>();

        public void dispatch(
                UUID ownerUuid,
                UUID npcUuid,
                Consumer<MiniwyvernAbilityWorld> callback) {
            npcs.add(npcUuid);
            callback.accept(proxy(MiniwyvernAbilityWorld.class, (method, arguments) -> switch (method) {
                case "isWorldThread" -> true;
                case "worldName" -> "default";
                case "owner" -> Optional.of(new MiniwyvernAbilityWorld.Target(
                        ownerUuid, ownerUuid, "default", 0D, true));
                case "companion" -> Optional.of(new MiniwyvernAbilityWorld.Target(
                        npcUuid, ownerUuid, "default", 0D, true));
                default -> methodReturn(method);
            }));
        }

        private static Object methodReturn(String method) {
            return switch (method) {
                case "nearbyTargets" -> List.of();
                case "hostileTarget" -> Optional.empty();
                case "health" -> new MiniwyvernAbilityWorld.Health(10D, 10D);
                default -> false;
            };
        }
    }
}
