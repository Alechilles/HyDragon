package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedCompanionApi;
import com.alechilles.alecstamework.api.BondedCompanionAvailability;
import com.alechilles.alecstamework.api.BondedCompanionChangedEvent;
import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionLeaseView;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import com.alechilles.alecstamework.api.BondedCompanionStateView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
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

/** Bonded lifecycle coverage for the live Miniwyvern ability runtime. */
final class MiniwyvernAbilityRuntimeTest {
    private static final BondedMiniwyvernExtensionCodec CODEC =
            new BondedMiniwyvernExtensionCodec();

    @TempDir
    Path temp;

    @Test
    void activeBondedLeaseTicksWithoutAccessingGenericProfileApis() throws Exception {
        Fixture fixture = fixture("active.properties", TameworkGameplayAdapter.MINIWYVERN_FAMILY);

        fixture.runtime.start();
        int ticked = fixture.runtime.tickSome(8);

        assertEquals(1, ticked);
        assertEquals(1, fixture.worlds.dispatches);
        assertTrue(fixture.authority.listCalls >= 1);
        assertTrue(fixture.authority.extensionCalls >= 1);
    }

    @Test
    void storedEventDetachesProjectionAndRetainsBondedExtension() throws Exception {
        Fixture fixture = fixture("stored.properties", TameworkGameplayAdapter.MINIWYVERN_FAMILY);
        fixture.runtime.start();
        fixture.runtime.tickSome(8);
        fixture.authority.profile = fixture.authority.storedProfile();

        fixture.authority.emit(new BondedCompanionChangedEvent(
                fixture.profile.toString(), fixture.owner,
                TameworkGameplayAdapter.DRAGON_HORN_ROSTER,
                BondedCompanionStateView.ACTIVE,
                BondedCompanionStateView.STORED,
                2L,
                "stored"));

        assertEquals(0, fixture.runtime.tickSome(8));
        assertEquals(2, fixture.worlds.dispatches,
                "one active tick and one source cleanup dispatch are expected");
        assertEquals("fire", fixture.authority.extensionDocument().archetypeId());
    }

    @Test
    void wrongFamilyNeverActivatesMiniwyvernAbilities() throws Exception {
        Fixture fixture = fixture("wrong-family.properties",
                TameworkGameplayAdapter.FULL_DRAGON_FAMILY);

        fixture.runtime.start();

        assertEquals(0, fixture.runtime.tickSome(8));
        assertEquals(0, fixture.worlds.dispatches);
    }

    private Fixture fixture(String fileName, String familyId) throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve(fileName));
        store.beginSoulBond(owner, "claim");
        store.completeSoulBond(owner, "claim", profile, 10L);
        BondedAuthority authority = new BondedAuthority(owner, profile, npc, familyId);
        RecordingWorldDispatcher worlds = new RecordingWorldDispatcher(owner, npc);
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(
                new MemoryAbilityStates());
        MiniwyvernAbilityRuntime runtime = new MiniwyvernAbilityRuntime(
                api(authority), store,
                () -> new HyDragonConfigRepository.Snapshot(
                        Map.of(), Map.of(), Map.of(), List.of()),
                MiniwyvernAbilityRuntimeTest::availableGate,
                worlds,
                service,
                Clock.fixed(Instant.ofEpochMilli(100L), ZoneOffset.UTC));
        return new Fixture(owner, profile, authority, worlds, runtime);
    }

    private static FeatureGate availableGate() {
        return new FeatureGate(
                HyDragonFeature.MINIWYVERN_ABILITIES,
                true,
                Set.of("BONDED_COMPANIONS"),
                Set.of(),
                List.of());
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

    private record Fixture(
            UUID owner,
            UUID profile,
            BondedAuthority authority,
            RecordingWorldDispatcher worlds,
            MiniwyvernAbilityRuntime runtime) {
    }

    private static final class BondedAuthority {
        private final UUID owner;
        private final UUID profileId;
        private final UUID npc;
        private final String familyId;
        private final String extensionPayload;
        private BondedCompanionProfileView profile;
        private Consumer<BondedCompanionChangedEvent> listener;
        private int listCalls;
        private int extensionCalls;

        private BondedAuthority(
                UUID owner,
                UUID profileId,
                UUID npc,
                String familyId) {
            this.owner = owner;
            this.profileId = profileId;
            this.npc = npc;
            this.familyId = familyId;
            extensionPayload = CODEC.encode(
                    BondedMiniwyvernExtensionDocument.neutral("miniwyvern", 0L)
                            .attune("fire", "test-attunement"));
            profile = activeProfile();
        }

        private Object invoke(String method, Object[] arguments) {
            return switch (method) {
                case "availability" -> BondedCompanionAvailability.availableNow();
                case "list" -> list();
                case "getExtensionData" -> extension();
                case "subscribe" -> subscribe(arguments[0]);
                default -> throw new AssertionError("unexpected bonded call " + method);
            };
        }

        private CompletableFuture<BondedCompanionResult<List<BondedCompanionProfileView>>>
                list() {
            listCalls++;
            return CompletableFuture.completedFuture(success(List.of(profile)));
        }

        private CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>>
                extension() {
            extensionCalls++;
            return CompletableFuture.completedFuture(success(
                    new BondedCompanionExtensionData(
                            new BondedCompanionExtensionDataKey(
                                    owner, profileId.toString(),
                                    BondedMiniwyvernExtensionDocument.NAMESPACE),
                            extensionPayload, 0L, 10L)));
        }

        @SuppressWarnings("unchecked")
        private AutoCloseable subscribe(Object value) {
            listener = (Consumer<BondedCompanionChangedEvent>) value;
            return () -> listener = null;
        }

        private void emit(BondedCompanionChangedEvent event) {
            listener.accept(event);
        }

        private BondedCompanionProfileView activeProfile() {
            return view(BondedCompanionStateView.ACTIVE,
                    new BondedCompanionLeaseView(
                            "lease", npc, "default", 10L, 0L), 1L);
        }

        private BondedCompanionProfileView storedProfile() {
            return view(BondedCompanionStateView.STORED, null, 2L);
        }

        private BondedCompanionProfileView view(
                BondedCompanionStateView state,
                BondedCompanionLeaseView lease,
                long revision) {
            return new BondedCompanionProfileView(
                    profileId.toString(), owner,
                    TameworkGameplayAdapter.DRAGON_HORN_ROSTER,
                    familyId,
                    TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    "Bonded Miniwyvern", "Miniwyvern", null,
                    revision, state,
                    state == BondedCompanionStateView.STORED,
                    state == BondedCompanionStateView.ACTIVE,
                    state == BondedCompanionStateView.DEAD,
                    Map.of(), lease, 0L, null);
        }

        private BondedMiniwyvernExtensionDocument extensionDocument() {
            return CODEC.decode(extensionPayload);
        }

        private static <T> BondedCompanionResult<T> success(T value) {
            return new BondedCompanionResult<>(
                    BondedCompanionResultCode.SUCCESS, value, null);
        }
    }

    private static final class MemoryAbilityStates
            implements MiniwyvernAbilityStateRepository {
        private MiniwyvernAbilityState state;

        public LoadResult load(UUID ownerUuid, String profileId) {
            return state == null ? LoadResult.missing() : LoadResult.loaded(state);
        }

        public boolean save(
                UUID ownerUuid,
                String profileId,
                MiniwyvernAbilityState replacement) {
            state = replacement;
            return true;
        }
    }

    private static final class RecordingWorldDispatcher
            implements MiniwyvernAbilityWorldDispatcher {
        private final UUID owner;
        private final UUID npc;
        private int dispatches;

        private RecordingWorldDispatcher(UUID owner, UUID npc) {
            this.owner = owner;
            this.npc = npc;
        }

        public void dispatch(
                UUID ownerUuid,
                UUID npcUuid,
                Consumer<MiniwyvernAbilityWorld> callback) {
            assertEquals(owner, ownerUuid);
            assertEquals(npc, npcUuid);
            dispatches++;
            callback.accept(new EmptyWorld(owner, npc));
        }
    }

    private record EmptyWorld(UUID ownerUuid, UUID npcUuid)
            implements MiniwyvernAbilityWorld {
        public boolean isWorldThread() { return true; }
        public String worldName() { return "default"; }
        public Optional<Target> owner() {
            return Optional.of(new Target(ownerUuid, ownerUuid, "default", 0D, true));
        }
        public Optional<Target> companion() {
            return Optional.of(new Target(npcUuid, ownerUuid, "default", 0D, true));
        }
        public Optional<Target> hostileTarget(double maximumRange) { return Optional.empty(); }
        public boolean synchronizeAppearance(UUID entityUuid, String appearanceId) { return true; }
        public Health health(UUID entityUuid) { return new Health(10D, 10D); }
        public boolean applyEffect(UUID entityUuid, String source, String effect, double duration) {
            return true;
        }
        public boolean removeEffect(UUID entityUuid, String source, String effect) { return true; }
        public boolean supportsOwnerModifiers(Map<String, Double> modifiers) { return true; }
        public boolean applyOwnerModifiers(
                UUID owner, String source, Map<String, Double> modifiers, double duration) {
            return true;
        }
        public boolean removeOwnerModifiers(UUID owner, String source) { return true; }
        public boolean launchProjectile(UUID source, UUID target, String projectile) { return true; }
        public boolean dealDamage(UUID source, UUID target, double amount) { return true; }
        public boolean heal(UUID entityUuid, double amount) { return true; }
        public boolean areAllies(UUID owner, UUID target) { return false; }
    }
}
