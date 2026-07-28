package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedCompanionApi;
import com.alechilles.alecstamework.api.BondedCompanionAvailability;
import com.alechilles.alecstamework.api.BondedCompanionCaptureEvidenceView;
import com.alechilles.alecstamework.api.BondedCompanionChangedEvent;
import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataUpdate;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Contract tests proving HyDragon's gameplay adapter uses only the bonded API. */
final class TameworkGameplayAdapterBondedTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Test
    void readinessRequiresBothCapabilityAndBondedAuthorityAvailability() {
        TameworkGameplayAdapter missingCapability = new TameworkGameplayAdapter(
                api(EnumSet.noneOf(TameworkApiCapability.class), bondedAvailable()));
        TameworkGameplayAdapter unavailableAuthority = new TameworkGameplayAdapter(
                api(EnumSet.of(TameworkApiCapability.BONDED_COMPANIONS),
                        bonded(BondedCompanionAvailability.unavailable("database-offline"))));
        TameworkGameplayAdapter ready = new TameworkGameplayAdapter(
                api(EnumSet.of(TameworkApiCapability.BONDED_COMPANIONS), bondedAvailable()));

        assertFalse(missingCapability.soulBondReadiness().ready());
        assertFalse(unavailableAuthority.soulBondReadiness().ready());
        assertEquals("database-offline", unavailableAuthority.soulBondReadiness().reason());
        assertTrue(ready.soulBondReadiness().ready());
        assertTrue(ready.abilityStateReadiness().ready());
    }

    @Test
    void rosterExtensionAndCaptureOperationsAreBoundToHyDragonAuthority() {
        RecordingBondedApi bonded = new RecordingBondedApi();
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(
                api(EnumSet.of(TameworkApiCapability.BONDED_COMPANIONS), bonded.proxy()));

        adapter.listDragonHorn(OWNER).toCompletableFuture().join();
        adapter.getMiniwyvernExtension(OWNER, "profile-mini").toCompletableFuture().join();
        adapter.compareAndSetMiniwyvernExtension(
                OWNER, "profile-mini", "attune-7", "{\"archetypeId\":\"ice\"}", -1L)
                .toCompletableFuture().join();
        adapter.findDragonCapture(OWNER, SOURCE).toCompletableFuture().join();
        AutoCloseable subscription = adapter.subscribeBondedChanges(event -> { });

        assertEquals(TameworkGameplayAdapter.DRAGON_HORN_ROSTER, bonded.listRoster);
        assertEquals(new BondedCompanionExtensionDataKey(
                        OWNER, "profile-mini", TameworkGameplayAdapter.EXTENSION_NAMESPACE),
                bonded.extensionKey);
        assertEquals(TameworkGameplayAdapter.CALLER_NAMESPACE, bonded.extensionUpdate.callerNamespace());
        assertEquals("attune-7", bonded.extensionUpdate.idempotencyKey());
        assertEquals(BondedCompanionExtensionDataUpdate.MISSING_REVISION,
                bonded.extensionUpdate.expectedRevision());
        assertEquals(TameworkGameplayAdapter.DRAGON_HORN_ROSTER, bonded.captureRoster);
        assertEquals(SOURCE, bonded.captureSource);
        assertSame(bonded.subscription, subscription);
    }

    @Test
    void adapterDoesNotExposeLegacyGenericPersistenceEntrypoints() {
        assertFalse(Arrays.stream(TameworkGameplayAdapter.class.getDeclaredMethods())
                .map(method -> method.getName())
                .anyMatch(name -> name.equals("findVersionedProfileData")
                        || name.equals("compareAndSetProfileData")
                        || name.equals("findProfileDataOperation")
                        || name.equals("provisionAndLinkMiniwyvern")
                        || name.equals("findMiniwyvern")));
    }

    @Test
    void miniwyvernRolesAreRestrictedToTheSevenRoleFamily() {
        assertEquals(Set.of(
                        "Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature",
                        "Tamed_Wyvern_Mini_Toxic", "Tamed_Wyvern_Mini_Fire",
                        "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning",
                        "Tamed_Wyvern_Mini_Ice"),
                TameworkGameplayAdapter.MINIWYVERN_ROLE_IDS);
        assertEquals("Tamed_Wyvern_Mini_Wild", TameworkGameplayAdapter.WILD_MINIWYVERN_ROLE);
    }

    private static BondedCompanionApi bondedAvailable() {
        return bonded(BondedCompanionAvailability.availableNow());
    }

    private static BondedCompanionApi bonded(BondedCompanionAvailability availability) {
        return proxy(BondedCompanionApi.class, (method, arguments) -> {
            if (method.equals("availability")) return availability;
            if (method.equals("subscribe")) return (AutoCloseable) () -> { };
            return CompletableFuture.completedFuture(BondedCompanionResult.unavailable("unused"));
        });
    }

    private static TameworkApi api(
            EnumSet<TameworkApiCapability> capabilities,
            BondedCompanionApi bonded) {
        return proxy(TameworkApi.class, (method, arguments) -> switch (method) {
            case "getApiVersion" -> "3.0.0";
            case "getCapabilities" -> EnumSet.copyOf(capabilities);
            case "bondedCompanions" -> bonded;
            default -> throw new AssertionError("legacy Tamework API accessed: " + method);
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

    private static final class RecordingBondedApi {
        private final AutoCloseable subscription = () -> { };
        private String listRoster;
        private BondedCompanionExtensionDataKey extensionKey;
        private BondedCompanionExtensionDataUpdate extensionUpdate;
        private String captureRoster;
        private UUID captureSource;

        private BondedCompanionApi proxy() {
            return TameworkGameplayAdapterBondedTest.proxy(
                    BondedCompanionApi.class, this::invoke);
        }

        private Object invoke(String method, Object[] arguments) {
            return switch (method) {
                case "availability" -> BondedCompanionAvailability.availableNow();
                case "list" -> list(arguments);
                case "getExtensionData" -> extension(arguments);
                case "compareAndSetExtensionData" -> update(arguments);
                case "findCapture" -> capture(arguments);
                case "subscribe" -> subscribe(arguments);
                default -> CompletableFuture.completedFuture(
                        BondedCompanionResult.unavailable("unused"));
            };
        }

        private CompletableFuture<BondedCompanionResult<List<BondedCompanionProfileView>>> list(
                Object[] arguments) {
            listRoster = (String) arguments[1];
            return CompletableFuture.completedFuture(success(List.of()));
        }

        private CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>> extension(
                Object[] arguments) {
            extensionKey = (BondedCompanionExtensionDataKey) arguments[0];
            return CompletableFuture.completedFuture(
                    new BondedCompanionResult<>(BondedCompanionResultCode.NOT_FOUND, null, "missing"));
        }

        private CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>> update(
                Object[] arguments) {
            extensionUpdate = (BondedCompanionExtensionDataUpdate) arguments[0];
            return CompletableFuture.completedFuture(
                    new BondedCompanionResult<>(BondedCompanionResultCode.REVISION_CONFLICT, null, "conflict"));
        }

        private CompletableFuture<BondedCompanionResult<BondedCompanionCaptureEvidenceView>> capture(
                Object[] arguments) {
            captureRoster = (String) arguments[1];
            captureSource = (UUID) arguments[2];
            return CompletableFuture.completedFuture(
                    new BondedCompanionResult<>(BondedCompanionResultCode.NOT_FOUND, null, "missing"));
        }

        private AutoCloseable subscribe(Object[] arguments) {
            @SuppressWarnings("unchecked")
            Consumer<BondedCompanionChangedEvent> ignored =
                    (Consumer<BondedCompanionChangedEvent>) arguments[0];
            return subscription;
        }

        private static <T> BondedCompanionResult<T> success(T value) {
            return new BondedCompanionResult<>(BondedCompanionResultCode.SUCCESS, value, null);
        }
    }
}
