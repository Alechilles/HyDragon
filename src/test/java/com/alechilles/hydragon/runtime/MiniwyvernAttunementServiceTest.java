package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedCompanionApi;
import com.alechilles.alecstamework.api.BondedCompanionAvailability;
import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import com.alechilles.hydragon.bonded.BondedExtensionJsonValue;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionGateway;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Crash and concurrency coverage for bonded Miniwyvern attunement. */
final class MiniwyvernAttunementServiceTest {
    private static final BondedMiniwyvernExtensionCodec CODEC =
            new BondedMiniwyvernExtensionCodec();

    @TempDir
    Path temp;

    @Test
    void committedRetryNeverMutatesOrConsumesTwice() throws Exception {
        Fixture fixture = fixture("once.properties");
        String operationId = "hydragon:attune:" + fixture.owner + ":fire";

        FakeReservation first = new FakeReservation(
                operationId, "Draconic_Essence_Fire");
        GameplayResult applied = fixture.service.attune(
                fixture.owner, "fire", first).toCompletableFuture().join();
        FakeReservation retry = new FakeReservation(
                operationId, "Draconic_Essence_Fire");
        GameplayResult replay = fixture.service.attune(
                fixture.owner, "fire", retry).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, applied.status());
        assertEquals(GameplayResult.Status.ALREADY_APPLIED, replay.status());
        assertEquals(1, fixture.gateway.commits);
        assertEquals(1, first.consumeCalls);
        assertEquals(0, retry.consumeCalls);
        assertEquals(1, retry.releaseCalls);
        assertEquals(OperationJournal.Phase.COMMITTED,
                fixture.journal.find(operationId).orElseThrow().phase());
    }

    @Test
    void lostCasResponseIsRecoveredFromAttunementEvidenceBeforeConsumption()
            throws Exception {
        Fixture fixture = fixture("lost.properties");
        fixture.gateway.failNextResponseAfterCommit = true;
        FakeReservation essence = new FakeReservation(
                "hydragon:attune:" + fixture.owner + ":wind",
                "Draconic_Essence_Wind");

        GameplayResult result = fixture.service.attune(
                fixture.owner, "wind", essence).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(1, fixture.gateway.commits);
        assertEquals(1, essence.consumeCalls);
        assertTrue(fixture.gateway.document().hasAttunementEvidence(
                essence.operationId(), "wind"));
    }

    @Test
    void concurrentAbilityWriteRebasesAttunementWithoutLosingOtherData()
            throws Exception {
        Fixture fixture = fixture("rebase.properties");
        BondedMiniwyvernExtensionDocument concurrent = fixture.gateway.document()
                .withProgression(BondedExtensionJsonValue.parse("{\"level\":4}"));
        fixture.gateway.mutateBeforeNextCompare = concurrent;
        FakeReservation essence = new FakeReservation(
                "hydragon:attune:" + fixture.owner + ":nature",
                "Draconic_Essence_Nature");

        GameplayResult result = fixture.service.attune(
                fixture.owner, "nature", essence).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(1, essence.consumeCalls);
        assertEquals("nature", fixture.gateway.document().archetypeId());
        assertEquals(BondedExtensionJsonValue.parse("{\"level\":4}"),
                fixture.gateway.document().progression());
        assertTrue(fixture.gateway.compareCalls >= 2);
    }

    @Test
    void alreadyAttunedByDifferentOperationDeniesWithoutConsumption()
            throws Exception {
        Fixture fixture = fixture("already.properties");
        fixture.gateway.externalWrite(
                fixture.gateway.document().attune("ice", "attune-older"));
        FakeReservation essence = new FakeReservation(
                "hydragon:attune:" + fixture.owner + ":ice",
                "Draconic_Essence_Ice");

        GameplayResult result = fixture.service.attune(
                fixture.owner, "ice", essence).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.DENIED, result.status());
        assertEquals(0, essence.consumeCalls);
        assertEquals(1, essence.releaseCalls);
        assertTrue(fixture.journal.find(essence.operationId()).isEmpty());
    }

    @Test
    void missingOrMalformedExtensionFailsClosedBeforeJournalOrConsumption()
            throws Exception {
        Fixture missing = fixture("missing.properties");
        missing.gateway.rawPayload = null;
        missing.gateway.revision = -1L;
        FakeReservation first = new FakeReservation(
                "hydragon:attune:" + missing.owner + ":water",
                "Draconic_Essence_Water");
        GameplayResult missingResult = missing.service.attune(
                missing.owner, "water", first).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED,
                missingResult.status());
        assertEquals(0, first.consumeCalls);
        assertEquals(1, first.releaseCalls);
        assertTrue(missing.journal.find(first.operationId()).isEmpty());

        Fixture malformed = fixture("malformed.properties");
        malformed.gateway.rawPayload = "{}";
        FakeReservation second = new FakeReservation(
                "hydragon:attune:" + malformed.owner + ":void",
                "Draconic_Essence_Void");
        GameplayResult malformedResult = malformed.service.attune(
                malformed.owner, "void", second).toCompletableFuture().join();
        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED,
                malformedResult.status());
        assertEquals(0, second.consumeCalls);
        assertEquals(1, second.releaseCalls);
    }

    @Test
    void missingBondedCapabilityFailsClosedBeforeJournalOrCas() throws Exception {
        Fixture fixture = fixture(
                "capability.properties", EnumSet.noneOf(TameworkApiCapability.class));
        FakeReservation essence = new FakeReservation(
                "hydragon:attune:" + fixture.owner + ":void",
                "Draconic_Essence_Void");

        GameplayResult result = fixture.service.attune(
                fixture.owner, "void", essence).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.UNAVAILABLE, result.status());
        assertEquals(0, fixture.gateway.compareCalls);
        assertEquals(0, essence.consumeCalls);
        assertEquals(1, essence.releaseCalls);
        assertTrue(fixture.journal.find(essence.operationId()).isEmpty());
    }

    private Fixture fixture(String journalName) throws Exception {
        return fixture(journalName,
                EnumSet.of(TameworkApiCapability.BONDED_COMPANIONS));
    }

    private Fixture fixture(
            String journalName,
            EnumSet<TameworkApiCapability> capabilities) throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        MemoryGateway gateway = new MemoryGateway(owner, profile.toString());
        gateway.install(BondedMiniwyvernExtensionDocument.neutral(
                "hydragon:miniwyvern", 10L), 0L);
        StateStoreOperationJournal journal = new StateStoreOperationJournal(
                new HyDragonStateStore(temp.resolve(journalName)), () -> 10L);
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(
                api(capabilities));
        SoulBondLedger ledger = claimedLedger(owner, profile);
        MiniwyvernAttunementService service = new MiniwyvernAttunementService(
                adapter,
                new BondedMiniwyvernExtensionStore(gateway, CODEC),
                ledger,
                journal);
        return new Fixture(owner, gateway, journal, service);
    }

    private static SoulBondLedger claimedLedger(UUID owner, UUID profile) {
        return new SoulBondLedger() {
            public Reservation reserve(UUID playerUuid, String operationId) {
                return Reservation.CONFLICT;
            }

            public Reservation complete(
                    UUID playerUuid, String operationId, UUID profileId, long claimedAt) {
                return Reservation.CONFLICT;
            }

            public Reservation reconcile(
                    UUID playerUuid, String operationId, Optional<UUID> profileId) {
                return Reservation.CONFLICT;
            }

            public Optional<Claim> find(UUID playerUuid) {
                return playerUuid.equals(owner)
                        ? Optional.of(new Claim(
                        "soul-bond", Optional.of(profile), Claim.State.CLAIMED))
                        : Optional.empty();
            }
        };
    }

    private static TameworkApi api(EnumSet<TameworkApiCapability> capabilities) {
        BondedCompanionApi bonded = proxy(BondedCompanionApi.class,
                (method, arguments) -> {
                    if (method.equals("availability")) {
                        return BondedCompanionAvailability.availableNow();
                    }
                    throw new AssertionError("unexpected bonded call " + method);
                });
        return proxy(TameworkApi.class, (method, arguments) -> switch (method) {
            case "getApiVersion" -> "3.0.0";
            case "getCapabilities" -> capabilities.clone();
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
            MemoryGateway gateway,
            StateStoreOperationJournal journal,
            MiniwyvernAttunementService service) {
    }

    private static final class FakeReservation implements ConsumableReservation {
        private final String operationId;
        private final String itemId;
        private int consumeCalls;
        private int releaseCalls;

        private FakeReservation(String operationId, String itemId) {
            this.operationId = operationId;
            this.itemId = itemId;
        }

        public String operationId() {
            return operationId;
        }

        public SourceEvidence sourceEvidence() {
            return new SourceEvidence(itemId, "player", "hotbar", 0, 1L,
                    "fingerprint:" + operationId, 1);
        }

        public int quantity() {
            return 1;
        }

        public CompletionStage<Disposition> consume() {
            consumeCalls++;
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }

        public CompletionStage<Disposition> release() {
            releaseCalls++;
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
    }

    private static final class MemoryGateway implements BondedMiniwyvernExtensionGateway {
        private final UUID owner;
        private final String profile;
        private final Map<String, Operation> operations = new LinkedHashMap<>();
        private String rawPayload;
        private long revision = -1L;
        private boolean failNextResponseAfterCommit;
        private BondedMiniwyvernExtensionDocument mutateBeforeNextCompare;
        private int compareCalls;
        private int commits;

        private MemoryGateway(UUID owner, String profile) {
            this.owner = owner;
            this.profile = profile;
        }

        private void install(BondedMiniwyvernExtensionDocument document, long revision) {
            rawPayload = CODEC.encode(document);
            this.revision = revision;
        }

        private BondedMiniwyvernExtensionDocument document() {
            return CODEC.decode(rawPayload);
        }

        private void externalWrite(BondedMiniwyvernExtensionDocument document) {
            install(document, revision + 1L);
        }

        @Override
        public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                getMiniwyvernExtension(UUID ownerUuid, String profileId) {
            if (rawPayload == null) {
                return CompletableFuture.completedFuture(new BondedCompanionResult<>(
                        BondedCompanionResultCode.NOT_FOUND, null, "missing"));
            }
            return CompletableFuture.completedFuture(success(rawPayload, revision));
        }

        @Override
        public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                compareAndSetMiniwyvernExtension(
                        UUID ownerUuid, String profileId, String idempotencyKey,
                        String jsonPayload, long expectedRevision) {
            compareCalls++;
            Operation prior = operations.get(idempotencyKey);
            if (prior != null) {
                boolean exact = prior.payload.equals(jsonPayload)
                        && prior.expectedRevision == expectedRevision;
                return CompletableFuture.completedFuture(exact && prior.resultingRevision >= 0L
                        ? success(prior.payload, prior.resultingRevision)
                        : conflict());
            }
            if (mutateBeforeNextCompare != null) {
                BondedMiniwyvernExtensionDocument concurrent = mutateBeforeNextCompare;
                mutateBeforeNextCompare = null;
                externalWrite(concurrent);
            }
            if (revision != expectedRevision) {
                operations.put(idempotencyKey,
                        new Operation(jsonPayload, expectedRevision, -1L));
                return CompletableFuture.completedFuture(conflict());
            }
            revision++;
            rawPayload = jsonPayload;
            commits++;
            operations.put(idempotencyKey,
                    new Operation(jsonPayload, expectedRevision, revision));
            if (failNextResponseAfterCommit) {
                failNextResponseAfterCommit = false;
                return CompletableFuture.failedFuture(
                        new IllegalStateException("lost response"));
            }
            return CompletableFuture.completedFuture(success(rawPayload, revision));
        }

        private BondedCompanionResult<BondedCompanionExtensionData> success(
                String payload,
                long revision) {
            return new BondedCompanionResult<>(
                    BondedCompanionResultCode.SUCCESS,
                    new BondedCompanionExtensionData(
                            new BondedCompanionExtensionDataKey(
                                    owner, profile,
                                    BondedMiniwyvernExtensionDocument.NAMESPACE),
                            payload, revision, 10L),
                    null);
        }

        private static BondedCompanionResult<BondedCompanionExtensionData> conflict() {
            return new BondedCompanionResult<>(
                    BondedCompanionResultCode.REVISION_CONFLICT, null, "conflict");
        }

        private record Operation(String payload, long expectedRevision, long resultingRevision) {
        }
    }
}
