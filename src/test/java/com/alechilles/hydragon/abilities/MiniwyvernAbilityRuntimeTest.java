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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
        assertEquals("wild", fixture.authority.extensionDocument().abilityState().formId());
    }

    @Test
    void wrongFamilyNeverActivatesMiniwyvernAbilities() throws Exception {
        Fixture fixture = fixture("wrong-family.properties",
                TameworkGameplayAdapter.FULL_DRAGON_FAMILY);

        fixture.runtime.start();

        assertEquals(0, fixture.runtime.tickSome(8));
        assertEquals(0, fixture.worlds.dispatches);
    }

    @Test
    void everyConfiguredMiniwyvernRoleActivatesTheLiveRuntime() throws Exception {
        for (String roleId : TameworkGameplayAdapter.MINIWYVERN_ROLE_IDS) {
            Fixture fixture = fixture("role-" + roleId + ".properties",
                    TameworkGameplayAdapter.MINIWYVERN_FAMILY, roleId);
            fixture.runtime.start();
            assertEquals(1, fixture.runtime.tickSome(8), roleId);
            assertEquals(formId(roleId), fixture.states.state.formId(), roleId);
            fixture.runtime.close();
        }
    }

    /** Regression: malformed bonded extension evidence must revoke a cached same-lease binding. */
    @Test
    void invalidExtensionRefreshDetachesUnchangedActiveLease() throws Exception {
        Fixture fixture = fixture("invalid-extension.properties",
                TameworkGameplayAdapter.MINIWYVERN_FAMILY);
        fixture.runtime.start();
        fixture.authority.extensionMode = ExtensionMode.INVALID;

        int ticked = fixture.runtime.tickSome(8);

        assertEquals(0, ticked);
        assertEquals(1, fixture.worlds.dispatches,
                "the cached binding must be deactivated instead of ticked");
        assertEquals(0, fixture.runtime.tickSome(8));
        assertEquals(1, fixture.worlds.dispatches,
                "an invalid extension must not leave a binding to tick again");
    }

    /** Regression: unavailable bonded extension evidence must fail closed for a cached binding. */
    @Test
    void unavailableExtensionRefreshDetachesUnchangedActiveLease() throws Exception {
        Fixture fixture = fixture("unavailable-extension.properties",
                TameworkGameplayAdapter.MINIWYVERN_FAMILY);
        fixture.runtime.start();
        fixture.authority.extensionMode = ExtensionMode.UNAVAILABLE;

        int ticked = fixture.runtime.tickSome(8);

        assertEquals(0, ticked);
        assertEquals(1, fixture.worlds.dispatches,
                "the cached binding must be deactivated instead of ticked");
        assertEquals(0, fixture.runtime.tickSome(8));
        assertEquals(1, fixture.worlds.dispatches,
                "an unavailable extension must not leave a binding to tick again");
    }

    /** Regression: an older malformed response cannot revoke a newer value-equal binding. */
    @Test
    void olderInvalidRefreshCannotDetachNewerValueEqualBinding() throws Exception {
        assertOlderFailureCannotDetachNewerBinding(
                "superseded-invalid-extension.properties", ExtensionMode.INVALID);
    }

    /** Regression: an older unavailable response cannot revoke a newer value-equal binding. */
    @Test
    void olderUnavailableRefreshCannotDetachNewerValueEqualBinding() throws Exception {
        assertOlderFailureCannotDetachNewerBinding(
                "superseded-unavailable-extension.properties", ExtensionMode.UNAVAILABLE);
    }

    private void assertOlderFailureCannotDetachNewerBinding(
            String fileName,
            ExtensionMode failureMode) throws Exception {
        Fixture fixture = fixture(fileName, TameworkGameplayAdapter.MINIWYVERN_FAMILY);
        fixture.runtime.start();
        CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>> older =
                fixture.authority.deferNextExtension();

        assertEquals(1, fixture.runtime.tickSome(8),
                "the existing binding remains active while the older refresh is pending");
        assertEquals(1, fixture.runtime.tickSome(8),
                "a newer valid refresh must install and tick its binding");
        assertTrue(older.complete(fixture.authority.extensionResult(failureMode)));
        fixture.authority.deferNextExtension();

        assertEquals(1, fixture.runtime.tickSome(8),
                "the superseded failure must not detach the newer binding");
    }

    private Fixture fixture(String fileName, String familyId) throws Exception {
        return fixture(fileName, familyId, "Tamed_Wyvern_Mini_Fire");
    }

    private Fixture fixture(String fileName, String familyId, String roleId) throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve(fileName));
        store.beginSoulBond(owner, "claim");
        store.completeSoulBond(owner, "claim", profile, 10L);
        BondedAuthority authority = new BondedAuthority(owner, profile, npc, familyId, roleId);
        RecordingWorldDispatcher worlds = new RecordingWorldDispatcher(owner, npc, roleId);
        MemoryAbilityStates states = new MemoryAbilityStates();
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);
        Map<String, com.alechilles.hydragon.config.MiniwyvernArchetypeConfig> archetypes = Map.of(
                formId(roleId), roleConfig(roleId));
        MiniwyvernAbilityRuntime runtime = new MiniwyvernAbilityRuntime(
                api(authority), store,
                () -> new HyDragonConfigRepository.Snapshot(
                        Map.of(), archetypes, Map.of(), List.of()),
                MiniwyvernAbilityRuntimeTest::availableGate,
                worlds,
                service,
                Clock.fixed(Instant.ofEpochMilli(100L), ZoneOffset.UTC));
        return new Fixture(owner, profile, authority, worlds, states, runtime);
    }

    private static String formId(String roleId) {
        return roleId.substring("Tamed_Wyvern_Mini_".length()).toLowerCase(Locale.ROOT);
    }

    private static com.alechilles.hydragon.config.MiniwyvernArchetypeConfig roleConfig(
            String roleId) throws Exception {
        String formId = formId(roleId);
        com.alechilles.hydragon.config.MiniwyvernArchetypeConfig config = construct(
                com.alechilles.hydragon.config.MiniwyvernArchetypeConfig.class);
        set(config, "id", formId);
        set(config, "roleId", roleId);
        set(config, "particleAndSoundIds", new String[0]);
        set(config, "passiveEffects", new String[0]);
        set(config, "passiveModifiers", Map.of());
        set(config, "fallbackBehavior", "BASIC_BITE");
        if ("nature".equals(formId)) {
            set(config, "passiveModifiers", Map.of(
                    "RegenerationTickSeconds", 2.0D,
                    "MaximumHealFractionPerTick", 0.01D));
        } else if ("lightning".equals(formId)) {
            set(config, "passiveModifiers", Map.of(
                    "MovementSpeedMultiplier", 1.15D,
                    "ActionSpeedMultiplier", 1.10D));
            set(config, "passiveModifierEffects", Map.of(
                    "MovementSpeedMultiplier", "test-lightning-boon"));
        }
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
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
            MemoryAbilityStates states,
            MiniwyvernAbilityRuntime runtime) {
    }

    private static final class BondedAuthority {
        private final UUID owner;
        private final UUID profileId;
        private final UUID npc;
        private final String familyId;
        private final String roleId;
        private final String extensionPayload;
        private BondedCompanionProfileView profile;
        private ExtensionMode extensionMode = ExtensionMode.VALID;
        private final java.util.ArrayDeque<CompletableFuture<
                BondedCompanionResult<BondedCompanionExtensionData>>> extensionResponses =
                new java.util.ArrayDeque<>();
        private Consumer<BondedCompanionChangedEvent> listener;
        private int listCalls;
        private int extensionCalls;

        private BondedAuthority(
                UUID owner,
                UUID profileId,
                UUID npc,
                String familyId,
                String roleId) {
            this.owner = owner;
            this.profileId = profileId;
            this.npc = npc;
            this.familyId = familyId;
            this.roleId = roleId;
            extensionPayload = CODEC.encode(
                    BondedMiniwyvernExtensionDocument.wild("miniwyvern", 0L));
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
            CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>> queued =
                    extensionResponses.pollFirst();
            return queued != null
                    ? queued
                    : CompletableFuture.completedFuture(extensionResult(extensionMode));
        }

        private CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>>
                deferNextExtension() {
            CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>> response =
                    new CompletableFuture<>();
            extensionResponses.addLast(response);
            return response;
        }

        private BondedCompanionResult<BondedCompanionExtensionData> extensionResult(
                ExtensionMode mode) {
            return switch (mode) {
                case VALID -> success(extensionData(extensionPayload));
                case INVALID -> success(extensionData("{}"));
                case UNAVAILABLE -> new BondedCompanionResult<>(
                        BondedCompanionResultCode.UNAVAILABLE, null,
                        "bonded extension authority unavailable");
            };
        }

        private BondedCompanionExtensionData extensionData(String payload) {
            return new BondedCompanionExtensionData(
                    new BondedCompanionExtensionDataKey(
                            owner, profileId.toString(),
                            BondedMiniwyvernExtensionDocument.NAMESPACE),
                    payload, 0L, 10L);
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
                    roleId,
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

    private enum ExtensionMode { VALID, INVALID, UNAVAILABLE }

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
        private final String liveRoleId;
        private int dispatches;

        private RecordingWorldDispatcher(UUID owner, UUID npc, String liveRoleId) {
            this.owner = owner;
            this.npc = npc;
            this.liveRoleId = liveRoleId;
        }

        public void dispatch(
                UUID ownerUuid,
                UUID npcUuid,
                Consumer<MiniwyvernAbilityWorld> callback) {
            assertEquals(owner, ownerUuid);
            assertEquals(npc, npcUuid);
            dispatches++;
            callback.accept(new EmptyWorld(owner, npc, liveRoleId));
        }
    }

    private record EmptyWorld(UUID ownerUuid, UUID npcUuid, String liveRoleId)
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
        public Optional<String> companionRoleId() { return Optional.of(liveRoleId); }
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
