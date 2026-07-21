package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
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

class MiniwyvernAttunementServiceTest {
    @TempDir Path temp;

    @Test
    void committedRetryNeverMutatesOrConsumesTwice() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        MemoryProfileData data = new MemoryProfileData();
        CountingProjection projection = new CountingProjection();
        StateStoreOperationJournal journal = journal("once.properties");
        MiniwyvernAttunementService service = service(owner, profile, data, journal, projection,
                fullCapabilities());
        String operationId = "hydragon:attune:" + owner + ":fire";

        FakeReservation first = new FakeReservation(operationId, "Draconic_Essence_Fire");
        GameplayResult applied = service.attune(owner, "fire", first).toCompletableFuture().join();
        FakeReservation retry = new FakeReservation(operationId, "Draconic_Essence_Fire");
        GameplayResult replay = service.attune(owner, "fire", retry).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, applied.status());
        assertEquals(GameplayResult.Status.ALREADY_APPLIED, replay.status());
        assertEquals(1, data.commits);
        assertEquals(1, first.consumeCalls);
        assertEquals(0, retry.consumeCalls);
        assertEquals(1, retry.releaseCalls);
        assertEquals(1, projection.calls);
        assertEquals(OperationJournal.Phase.COMMITTED,
                journal.find(operationId).orElseThrow().phase());
    }

    @Test
    void committedAuthorityIsRecoveredBeforeFirstConsumption() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        MemoryProfileData data = new MemoryProfileData();
        CountingProjection unavailable = new CountingProjection();
        unavailable.decision = MiniwyvernAttunementService.ProfileProjection.Decision.UNAVAILABLE;
        StateStoreOperationJournal journal = journal("recovery.properties");
        String operationId = "hydragon:attune:" + owner + ":ice";
        FakeReservation first = new FakeReservation(operationId, "Draconic_Essence_Ice");

        GameplayResult pending = service(owner, profile, data, journal, unavailable, fullCapabilities())
                .attune(owner, "ice", first).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED, pending.status());
        assertEquals(1, data.commits);
        assertEquals(0, first.consumeCalls);
        assertEquals(OperationJournal.Phase.PREPARED,
                journal.find(operationId).orElseThrow().phase());

        CountingProjection recovered = new CountingProjection();
        FakeReservation retry = new FakeReservation(operationId, "Draconic_Essence_Ice");
        GameplayResult result = service(owner, profile, data, journal, recovered, fullCapabilities())
                .attune(owner, "ice", retry).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(1, data.commits);
        assertEquals(1, retry.consumeCalls);
        assertEquals(OperationJournal.Phase.COMMITTED,
                journal.find(operationId).orElseThrow().phase());
    }

    @Test
    void lostCasResponseUsesDurableOperationProofWithoutSecondMutation() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        MemoryProfileData data = new MemoryProfileData();
        data.failNextResponseAfterCommit = true;
        FakeReservation essence = new FakeReservation(
                "hydragon:attune:" + owner + ":wind", "Draconic_Essence_Wind");

        GameplayResult result = service(owner, profile, data, journal("lost.properties"),
                new CountingProjection(), fullCapabilities())
                .attune(owner, "wind", essence).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(1, data.commits);
        assertEquals(1, essence.consumeCalls);
    }

    @Test
    void staleRevisionIsTerminallyDeniedAndEssenceIsReleased() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        MemoryProfileData data = new MemoryProfileData();
        data.writeExisting(profile.toString(), payload("fire", "older"));
        data.mutateBeforeNextCompare = payload("water", "concurrent");
        StateStoreOperationJournal journal = journal("stale.properties");
        FakeReservation essence = new FakeReservation(
                "hydragon:attune:" + owner + ":nature", "Draconic_Essence_Nature");

        GameplayResult result = service(owner, profile, data, journal,
                new CountingProjection(), fullCapabilities())
                .attune(owner, "nature", essence).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.DENIED, result.status());
        assertEquals(0, essence.consumeCalls);
        assertEquals(1, essence.releaseCalls);
        // PREPARED is retained because the journal's refund phases are reserved for material that
        // was actually consumed. The durable Tamework denial closes every retry without another CAS.
        assertEquals(OperationJournal.Phase.PREPARED,
                journal.find(essence.operationId()).orElseThrow().phase());
        FakeReservation replay = new FakeReservation(
                essence.operationId(), "Draconic_Essence_Nature");
        GameplayResult replayResult = service(owner, profile, data, journal,
                new CountingProjection(), fullCapabilities())
                .attune(owner, "nature", replay).toCompletableFuture().join();
        assertEquals(GameplayResult.Status.DENIED, replayResult.status());
        assertEquals(1, data.compareCalls);
        assertEquals(0, replay.consumeCalls);
    }

    @Test
    void missingTransactionCapabilityFailsClosedBeforeJournalOrCas() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        MemoryProfileData data = new MemoryProfileData();
        StateStoreOperationJournal journal = journal("capability.properties");
        FakeReservation essence = new FakeReservation(
                "hydragon:attune:" + owner + ":void", "Draconic_Essence_Void");
        EnumSet<TameworkApiCapability> capabilities = fullCapabilities();
        capabilities.remove(TameworkApiCapability.PROFILE_DATA_TRANSACTIONS);

        GameplayResult result = service(owner, profile, data, journal,
                new CountingProjection(), capabilities)
                .attune(owner, "void", essence).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.UNAVAILABLE, result.status());
        assertEquals(0, data.compareCalls);
        assertEquals(0, essence.consumeCalls);
        assertEquals(1, essence.releaseCalls);
        assertTrue(journal.find(essence.operationId()).isEmpty());
    }

    private StateStoreOperationJournal journal(String name) throws Exception {
        return new StateStoreOperationJournal(new HyDragonStateStore(temp.resolve(name)), () -> 10L);
    }

    private static MiniwyvernAttunementService service(
            UUID owner,
            UUID profile,
            ProfileDataApi data,
            OperationJournal journal,
            MiniwyvernAttunementService.ProfileProjection projection,
            EnumSet<TameworkApiCapability> capabilities) {
        SoulBondLedger ledger = new SoulBondLedger() {
            public Reservation reserve(UUID playerUuid, String operationId) { return Reservation.CONFLICT; }
            public Reservation complete(UUID playerUuid, String operationId, UUID profileId, long claimedAt) {
                return Reservation.CONFLICT;
            }
            public Reservation reconcile(UUID playerUuid, String operationId, Optional<UUID> profileId) {
                return Reservation.CONFLICT;
            }
            public Optional<Claim> find(UUID playerUuid) {
                return playerUuid.equals(owner)
                        ? Optional.of(new Claim("soul-bond", Optional.of(profile), Claim.State.CLAIMED))
                        : Optional.empty();
            }
        };
        return new MiniwyvernAttunementService(
                new TameworkGameplayAdapter(api(capabilities, data)), ledger, journal, projection);
    }

    private static EnumSet<TameworkApiCapability> fullCapabilities() {
        return EnumSet.of(
                TameworkApiCapability.PROFILE_DATA,
                TameworkApiCapability.PROFILE_DATA_TRANSACTIONS);
    }

    private static TameworkApi api(
            EnumSet<TameworkApiCapability> capabilities,
            ProfileDataApi data) {
        return new TameworkApi() {
            public String getApiVersion() { return "0.9.0"; }
            public EnumSet<TameworkApiCapability> getCapabilities() { return capabilities.clone(); }
            public NpcProfilesApi profiles() { return null; }
            public CommandLinksApi commandLinks() { return null; }
            public ProgressionApi progression() { return null; }
            public PolicyApi policies() { return null; }
            public InteractionExtensionApi interactionExtensions() { return null; }
            public TraitEffectApi traitEffects() { return null; }
            public ProfileDataApi profileData() { return data; }
            public TameworkEventsApi events() { return null; }
            public TameworkConfigReadApi configs() { return null; }
            public DiagnosticsApi diagnostics() { return null; }
        };
    }

    private static String payload(String archetype, String operationId) {
        return "{\"schemaVersion\":1,\"archetypeId\":\"" + archetype
                + "\",\"attunementOperationId\":\"" + operationId + "\"}";
    }

    private static final class CountingProjection
            implements MiniwyvernAttunementService.ProfileProjection {
        private int calls;
        private Decision decision = Decision.APPLIED;

        public Decision synchronize(UUID profileId, String archetypeId, String operationId) {
            calls++;
            return decision;
        }
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

        public String operationId() { return operationId; }
        public SourceEvidence sourceEvidence() {
            return new SourceEvidence(itemId, "player", "hotbar", 0, 1L,
                    "fingerprint:" + operationId, 1);
        }
        public int quantity() { return 1; }
        public CompletionStage<Disposition> consume() {
            consumeCalls++;
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
        public CompletionStage<Disposition> release() {
            releaseCalls++;
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
    }

    private static final class MemoryProfileData implements ProfileDataApi {
        private final Map<String, ProfileDataEntryView> values = new LinkedHashMap<>();
        private final Map<String, ProfileDataOperationView> operations = new LinkedHashMap<>();
        private boolean failNextResponseAfterCommit;
        private String mutateBeforeNextCompare;
        private int compareCalls;
        private int commits;

        void writeExisting(String profileId, String payload) {
            long revision = Optional.ofNullable(values.get(profileId))
                    .map(ProfileDataEntryView::revision).orElse(0L) + 1L;
            values.put(profileId, new ProfileDataEntryView(profileId,
                    MiniwyvernAttunementService.NAMESPACE, MiniwyvernAttunementService.KEY,
                    revision, payload, revision));
        }

        public Optional<String> get(String profileId, String namespace, String key) {
            return getVersioned(profileId, namespace, key).map(ProfileDataEntryView::jsonPayload);
        }
        public Map<String, String> list(String profileId, String namespace) { return Map.of(); }
        public boolean put(String profileId, String namespace, String key, String jsonPayload) {
            throw new AssertionError("unfenced put must not be used");
        }
        public boolean delete(String profileId, String namespace, String key) { return false; }
        public Optional<ProfileDataEntryView> getVersioned(String profileId, String namespace, String key) {
            return Optional.ofNullable(values.get(profileId));
        }
        public CompletionStage<Optional<ProfileDataOperationView>> findOperation(
                String namespace, String idempotencyKey) {
            return CompletableFuture.completedFuture(Optional.ofNullable(operations.get(idempotencyKey)));
        }
        public CompletionStage<ProfileDataCompareAndSetResult> compareAndSet(
                ProfileDataCompareAndSetRequest request) {
            compareCalls++;
            ProfileDataOperationView prior = operations.get(request.idempotencyKey());
            if (prior != null) return CompletableFuture.completedFuture(result(prior));
            if (mutateBeforeNextCompare != null) {
                String payload = mutateBeforeNextCompare;
                mutateBeforeNextCompare = null;
                writeExisting(request.profileId(), payload);
            }
            long current = Optional.ofNullable(values.get(request.profileId()))
                    .map(ProfileDataEntryView::revision).orElse(0L);
            if (current != request.expectedRevision()) {
                ProfileDataOperationView denied = operation(
                        request, ProfileDataOperationStatus.TERMINAL_DENIED, -1L);
                operations.put(request.idempotencyKey(), denied);
                return CompletableFuture.completedFuture(new ProfileDataCompareAndSetResult(
                        ProfileDataCompareAndSetResult.Status.TERMINAL_DENIED,
                        "revision-conflict", denied, null));
            }
            long resulting = current + 1L;
            ProfileDataEntryView entry = new ProfileDataEntryView(
                    request.profileId(), request.namespace(), request.key(), resulting,
                    request.jsonPayload(), resulting);
            values.put(request.profileId(), entry);
            ProfileDataOperationView committed = operation(
                    request, ProfileDataOperationStatus.COMMITTED, resulting);
            operations.put(request.idempotencyKey(), committed);
            commits++;
            if (failNextResponseAfterCommit) {
                failNextResponseAfterCommit = false;
                return CompletableFuture.failedFuture(new IllegalStateException("lost response"));
            }
            return CompletableFuture.completedFuture(new ProfileDataCompareAndSetResult(
                    ProfileDataCompareAndSetResult.Status.COMMITTED,
                    "committed", committed, entry));
        }

        private ProfileDataCompareAndSetResult result(ProfileDataOperationView operation) {
            if (operation.status() == ProfileDataOperationStatus.COMMITTED) {
                return new ProfileDataCompareAndSetResult(
                        ProfileDataCompareAndSetResult.Status.COMMITTED,
                        "replayed", operation, values.get(operation.profileId()));
            }
            return new ProfileDataCompareAndSetResult(
                    ProfileDataCompareAndSetResult.Status.TERMINAL_DENIED,
                    operation.reason(), operation, null);
        }

        private static ProfileDataOperationView operation(
                ProfileDataCompareAndSetRequest request,
                ProfileDataOperationStatus status,
                long resultingRevision) {
            return new ProfileDataOperationView(
                    UUID.randomUUID(), request.namespace(), request.idempotencyKey(),
                    request.profileId(), request.key(), request.expectedRevision(), resultingRevision,
                    "fingerprint", status, status.name().toLowerCase(), 1L);
        }
    }
}
