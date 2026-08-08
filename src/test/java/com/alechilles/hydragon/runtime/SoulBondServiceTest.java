package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.alechilles.alecstamework.api.BondedCompanionApi;
import com.alechilles.alecstamework.api.BondedCompanionAvailability;
import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataUpdate;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionProvisionRequest;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import com.alechilles.alecstamework.api.BondedCompanionStateView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.SoulBondState;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Crash, replay, and authority-boundary coverage for bonded Soul Bond claims. */
final class SoulBondServiceTest {
    private static final BondedMiniwyvernExtensionCodec CODEC =
            new BondedMiniwyvernExtensionCodec();

    @TempDir
    Path temp;

    @Test
    void consumesEggThenProvisionsStoredMiniwyvernAndInitializesExtension()
            throws Exception {
        Fixture fixture = fixture("success.properties");
        FakeReservation egg = fixture.egg("claim");

        GameplayResult result = fixture.service.claim(
                fixture.owner, "default", egg).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(List.of("consume", "provision", "extension"), fixture.order);
        BondedCompanionProvisionRequest request = fixture.authority.lastRequest;
        assertEquals(TameworkGameplayAdapter.DRAGON_HORN_ROSTER, request.rosterId());
        assertEquals(TameworkGameplayAdapter.MINIWYVERN_FAMILY, request.familyId());
        assertEquals(TameworkGameplayAdapter.WILD_MINIWYVERN_ROLE, request.roleId());
        assertNull(request.displayName(),
                "newly bonded Miniwyverns must remain unnamed");
        assertEquals(BondedCompanionStateView.STORED,
                fixture.authority.profile().state());
        assertNull(fixture.authority.profile().activeLease());
        assertEquals("miniwyvern", fixture.authority.extension().speciesId());
        assertEquals("wild", fixture.authority.extension().abilityState().formId());
        assertEquals(SoulBondState.CLAIMED,
                fixture.store.snapshot().playerSoulBond(fixture.owner)
                        .orElseThrow().state());
        assertFalse(fixture.store.snapshot().profileExtension(
                fixture.profile).isPresent(),
                "bonded Miniwyvern data must not be duplicated in local profile state");
        assertEquals(OperationJournal.Phase.COMMITTED,
                fixture.journal.find(egg.operationId()).orElseThrow().phase());
    }

    @Test
    void committedRetryCannotConsumeProvisionOrInitializeTwice() throws Exception {
        Fixture fixture = fixture("retry.properties");
        FakeReservation first = fixture.egg("same-operation");
        FakeReservation retry = fixture.egg("same-operation");

        assertEquals(GameplayResult.Status.APPLIED, fixture.service.claim(
                fixture.owner, "default", first).toCompletableFuture().join().status());
        assertEquals(GameplayResult.Status.DENIED, fixture.service.claim(
                fixture.owner, "default", retry).toCompletableFuture().join().status());

        assertEquals(1, first.consumeCalls);
        assertEquals(0, retry.consumeCalls);
        assertEquals(1, retry.releaseCalls);
        assertEquals(1, fixture.authority.provisionCalls);
        assertEquals(1, fixture.authority.extensionCommits);
    }

    @Test
    void transientProvisionReplaysExactRequestWithoutSecondEggSpend()
            throws Exception {
        Fixture fixture = fixture("transient.properties");
        fixture.authority.unavailableProvisionCalls = 1;
        FakeReservation first = fixture.egg("transient");
        FakeReservation retry = fixture.egg("transient");

        assertEquals(GameplayResult.Status.RETRYABLE, fixture.service.claim(
                fixture.owner, "default", first).toCompletableFuture().join().status());
        assertEquals(GameplayResult.Status.APPLIED, fixture.service.claim(
                fixture.owner, "default", retry).toCompletableFuture().join().status());

        assertEquals(1, first.consumeCalls);
        assertEquals(0, retry.consumeCalls);
        assertEquals(2, fixture.authority.provisionCalls);
        assertEquals(List.of(first.operationId(), first.operationId()),
                fixture.authority.provisionKeys);
    }

    @Test
    void lostExtensionResponseIsRecoveredFromDurableBondedEvidence()
            throws Exception {
        Fixture fixture = fixture("extension-lost.properties");
        fixture.authority.failExtensionResponseAfterCommit = true;
        FakeReservation egg = fixture.egg("extension-lost");

        GameplayResult result = fixture.service.claim(
                fixture.owner, "default", egg).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(1, fixture.authority.extensionCommits);
        assertEquals(OperationJournal.Phase.COMMITTED,
                fixture.journal.find(egg.operationId()).orElseThrow().phase());
    }

    @Test
    void terminalProvisionDenialCreatesOneEggRecoveryClaim() throws Exception {
        Fixture fixture = fixture("denied.properties");
        fixture.authority.provisionCode = BondedCompanionResultCode.POLICY_DENIED;
        FakeReservation egg = fixture.egg("denied");

        GameplayResult result = fixture.service.claim(
                fixture.owner, "default", egg).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED, result.status());
        assertEquals(1, egg.consumeCalls);
        assertEquals(SoulBondState.UNCLAIMED,
                fixture.store.snapshot().playerSoulBond(fixture.owner)
                        .orElseThrow().state());
        assertEquals(OperationJournal.Phase.REFUND_DUE,
                fixture.journal.find(egg.operationId()).orElseThrow().phase());
        List<ConsumableRefundClaimService.Claim> claims =
                new ConsumableRefundClaimService(fixture.journal)
                        .claims(fixture.owner);
        assertEquals(1, claims.size());
        assertEquals(SoulBondService.WYVERN_EGG_ITEM_ID,
                claims.getFirst().itemId());
    }

    @Test
    void noncanonicalBondedProfileQuarantinesConsumedClaim() throws Exception {
        for (EvidenceMismatch mismatch : EvidenceMismatch.values()) {
            if (mismatch == EvidenceMismatch.NONE) continue;
            Fixture fixture = fixture("mismatch-" + mismatch + ".properties");
            fixture.authority.evidenceMismatch = mismatch;
            FakeReservation egg = fixture.egg("mismatch-" + mismatch);

            GameplayResult result = fixture.service.claim(
                    fixture.owner, "default", egg).toCompletableFuture().join();

            assertEquals(GameplayResult.Status.QUARANTINED,
                    result.status(), mismatch.name());
            assertEquals(OperationJournal.Phase.QUARANTINED,
                    fixture.journal.find(egg.operationId()).orElseThrow().phase(),
                    mismatch.name());
            assertEquals(0, fixture.authority.extensionCommits, mismatch.name());
        }
    }

    private Fixture fixture(String fileName) throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        List<String> order = new ArrayList<>();
        BondedAuthority authority = new BondedAuthority(owner, profile, order);
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(api(authority));
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve(fileName));
        StateStoreOperationJournal journal = new StateStoreOperationJournal(
                store, () -> 10L);
        SoulBondService service = new SoulBondService(
                adapter,
                new BondedMiniwyvernExtensionStore(adapter, CODEC),
                new StateStoreSoulBondLedger(store),
                journal,
                () -> 10L);
        return new Fixture(owner, profile, order, authority, store, journal, service);
    }

    private static TameworkApi api(BondedAuthority authority) {
        BondedCompanionApi bonded = proxy(BondedCompanionApi.class, authority::invoke);
        return proxy(TameworkApi.class, (method, arguments) -> switch (method) {
            case "getApiVersion" -> "3.0.0";
            case "getCapabilities" -> EnumSet.of(TameworkApiCapability.BONDED_COMPANIONS);
            case "bondedCompanions" -> bonded;
            default -> throw new AssertionError("legacy API accessed: " + method);
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
            List<String> order,
            BondedAuthority authority,
            HyDragonStateStore store,
            StateStoreOperationJournal journal,
            SoulBondService service) {
        private FakeReservation egg(String suffix) {
            return new FakeReservation(
                    "hydragon:soul-bond:" + owner + ':' + suffix, order);
        }
    }

    private static final class BondedAuthority {
        private final UUID owner;
        private final UUID profile;
        private final List<String> order;
        private final List<String> provisionKeys = new ArrayList<>();
        private BondedCompanionProvisionRequest lastRequest;
        private BondedCompanionResultCode provisionCode = BondedCompanionResultCode.SUCCESS;
        private EvidenceMismatch evidenceMismatch = EvidenceMismatch.NONE;
        private int unavailableProvisionCalls;
        private int provisionCalls;
        private String extensionPayload;
        private long extensionRevision = -1L;
        private int extensionCommits;
        private boolean failExtensionResponseAfterCommit;

        private BondedAuthority(UUID owner, UUID profile, List<String> order) {
            this.owner = owner;
            this.profile = profile;
            this.order = order;
        }

        private Object invoke(String method, Object[] arguments) {
            return switch (method) {
                case "availability" -> BondedCompanionAvailability.availableNow();
                case "provision" -> provision((BondedCompanionProvisionRequest) arguments[0]);
                case "getExtensionData" -> getExtension();
                case "compareAndSetExtensionData" -> compareAndSet(
                        (BondedCompanionExtensionDataUpdate) arguments[0]);
                case "subscribe" -> (AutoCloseable) () -> { };
                default -> throw new AssertionError("unexpected bonded call " + method);
            };
        }

        private CompletionStage<BondedCompanionResult<BondedCompanionProfileView>>
                provision(BondedCompanionProvisionRequest request) {
            provisionCalls++;
            lastRequest = request;
            provisionKeys.add(request.idempotencyKey());
            order.add("provision");
            if (unavailableProvisionCalls-- > 0) {
                return CompletableFuture.completedFuture(failure(
                        BondedCompanionResultCode.UNAVAILABLE, "transient"));
            }
            if (provisionCode != BondedCompanionResultCode.SUCCESS) {
                return CompletableFuture.completedFuture(failure(
                        provisionCode, "denied"));
            }
            return CompletableFuture.completedFuture(success(profile()));
        }

        private CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                getExtension() {
            return CompletableFuture.completedFuture(extensionPayload == null
                    ? failure(BondedCompanionResultCode.NOT_FOUND, "missing")
                    : success(extensionView()));
        }

        private CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                compareAndSet(BondedCompanionExtensionDataUpdate update) {
            order.add("extension");
            if (extensionRevision != update.expectedRevision()) {
                return CompletableFuture.completedFuture(failure(
                        BondedCompanionResultCode.REVISION_CONFLICT, "conflict"));
            }
            extensionRevision++;
            extensionPayload = update.jsonPayload();
            extensionCommits++;
            if (failExtensionResponseAfterCommit) {
                failExtensionResponseAfterCommit = false;
                return CompletableFuture.failedFuture(
                        new IllegalStateException("lost extension response"));
            }
            return CompletableFuture.completedFuture(success(extensionView()));
        }

        private BondedCompanionProfileView profile() {
            UUID evidenceOwner = evidenceMismatch == EvidenceMismatch.OWNER
                    ? UUID.randomUUID() : owner;
            String roster = evidenceMismatch == EvidenceMismatch.ROSTER
                    ? "other:roster" : TameworkGameplayAdapter.DRAGON_HORN_ROSTER;
            String family = evidenceMismatch == EvidenceMismatch.FAMILY
                    ? "other:family" : TameworkGameplayAdapter.MINIWYVERN_FAMILY;
            String role = evidenceMismatch == EvidenceMismatch.ROLE
                    ? "Other_Role" : TameworkGameplayAdapter.WILD_MINIWYVERN_ROLE;
            BondedCompanionStateView state = evidenceMismatch == EvidenceMismatch.STATE
                    ? BondedCompanionStateView.DEAD : BondedCompanionStateView.STORED;
            return new BondedCompanionProfileView(
                    profile.toString(), evidenceOwner, roster, family, role,
                    "Bonded Miniwyvern", "Miniwyvern", null, 0L, state,
                    state == BondedCompanionStateView.STORED,
                    false, state == BondedCompanionStateView.DEAD,
                    Map.of("source", "soul-bond"), null, 0L, null);
        }

        private BondedCompanionExtensionData extensionView() {
            return new BondedCompanionExtensionData(
                    new BondedCompanionExtensionDataKey(
                            owner, profile.toString(),
                            BondedMiniwyvernExtensionDocument.NAMESPACE),
                    extensionPayload, extensionRevision, 10L);
        }

        private BondedMiniwyvernExtensionDocument extension() {
            return CODEC.decode(extensionPayload);
        }

        private static <T> BondedCompanionResult<T> success(T value) {
            return new BondedCompanionResult<>(
                    BondedCompanionResultCode.SUCCESS, value, null);
        }

        private static <T> BondedCompanionResult<T> failure(
                BondedCompanionResultCode code,
                String reason) {
            return new BondedCompanionResult<>(code, null, reason);
        }
    }

    private enum EvidenceMismatch { NONE, OWNER, ROSTER, FAMILY, ROLE, STATE }

    private static final class FakeReservation implements ConsumableReservation {
        private final String operationId;
        private final List<String> order;
        private int consumeCalls;
        private int releaseCalls;

        private FakeReservation(String operationId, List<String> order) {
            this.operationId = operationId;
            this.order = order;
        }

        public String operationId() {
            return operationId;
        }

        public SourceEvidence sourceEvidence() {
            return new SourceEvidence(
                    SoulBondService.WYVERN_EGG_ITEM_ID,
                    "player", "hotbar", 0, 1L, "fingerprint:" + operationId, 1);
        }

        public int quantity() {
            return 1;
        }

        public CompletionStage<Disposition> consume() {
            consumeCalls++;
            order.add("consume");
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }

        public CompletionStage<Disposition> release() {
            releaseCalls++;
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
    }
}
