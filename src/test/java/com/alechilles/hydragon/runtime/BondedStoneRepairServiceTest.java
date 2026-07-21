package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.alechilles.alecstamework.api.*;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BondedStoneRepairServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void restartAfterMaterialConsumptionResumesFromDurableEvidenceAndCommits() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID binding = UUID.randomUUID();
        String operationId = "hydragon:repair:restart";
        ConsumableReservation.SourceEvidence stone = stoneEvidence();
        consumedJournal(operationId, owner, binding, stone);
        StateStoreOperationJournal journal = new StateStoreOperationJournal(
                new HyDragonStateStore(journalFile(operationId)), () -> 101L);
        RecordingBondedVessels vessels = RecordingBondedVessels.committing(binding);
        BondedStoneRepairService service = service(vessels, journal);

        GameplayResult result = service.recover(journal.find(operationId).orElseThrow())
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(OperationJournal.Phase.COMMITTED, journal.find(operationId).orElseThrow().phase());
        assertEquals(1, vessels.resumeCalls);
        assertEquals(1, vessels.claimCalls);
        assertEquals(1, vessels.commitCalls);
        assertNotNull(vessels.resumedRequest);
        assertEquals(operationId, vessels.resumedRequest.idempotencyKey());
        assertEquals(owner, vessels.resumedRequest.actorUuid());
        assertEquals(binding, vessels.resumedRequest.bindingId());
        assertEquals(3L, vessels.resumedRequest.expectedGeneration());
        assertEquals(7L, vessels.resumedRequest.expectedProfileRevision());
        assertEquals(BondedVesselTransition.REPAIR_DEAD_TO_STORED, vessels.resumedRequest.transition());
        assertEquals(stone.itemId(), vessels.resumedRequest.context().sourceItemId());
        assertEquals(stone.holderEvidenceId(), vessels.resumedRequest.context().sourceHolderEvidenceId());
        assertEquals(stone.containerPath(), vessels.resumedRequest.context().sourceContainerPath());
        assertEquals(stone.inventorySlot(), vessels.resumedRequest.context().sourceInventorySlot());
        assertEquals(stone.inventoryRevision(), vessels.resumedRequest.context().sourceInventoryRevision());
        assertEquals(stone.itemFingerprint(), vessels.resumedRequest.context().sourceItemFingerprint());
    }

    @Test
    void restartClosesAnAlreadyAppliedAuthorityOperationWithItsFreshRecoveryToken() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID binding = UUID.randomUUID();
        String operationId = "hydragon:repair:applied-restart";
        ConsumableReservation.SourceEvidence stone = stoneEvidence();
        consumedJournal(operationId, owner, binding, stone);
        StateStoreOperationJournal journal = new StateStoreOperationJournal(
                new HyDragonStateStore(journalFile(operationId)), () -> 101L);
        RecordingBondedVessels vessels = RecordingBondedVessels.committing(
                binding, BondedVesselOperationResult.Status.APPLIED);

        GameplayResult result = service(vessels, journal)
                .recover(journal.find(operationId).orElseThrow())
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(OperationJournal.Phase.COMMITTED, journal.find(operationId).orElseThrow().phase());
        assertEquals(1, vessels.claimCalls);
        assertEquals(1, vessels.commitCalls);
    }

    @Test
    void terminalDenialAfterConsumptionCreatesDurableRefundDueWithoutReleasingReceipt() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID binding = UUID.randomUUID();
        String operationId = "hydragon:repair:terminal-denial";
        ConsumableReservation.SourceEvidence stone = stoneEvidence();
        StateStoreOperationJournal journal = consumedJournal(operationId, owner, binding, stone);
        RecordingBondedVessels vessels = RecordingBondedVessels.terminallyDenied(binding, operationId);
        BondedStoneRepairService service = service(vessels, journal);
        CountingReservation essence = new CountingReservation(operationId);

        GameplayResult result = service.repair(
                repairRequest(operationId, owner, binding, stone), stone, essence)
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED, result.status());
        assertEquals(OperationJournal.Phase.REFUND_DUE, journal.find(operationId).orElseThrow().phase());
        assertEquals(0, essence.consumeCalls);
        assertEquals(0, essence.releaseCalls);
        assertEquals(1, vessels.resumeCalls);
        assertEquals(0, vessels.claimCalls);
        assertEquals(0, vessels.commitCalls);
        assertEquals(0, vessels.cancelCalls);

        GameplayResult replay = service.repair(
                repairRequest(operationId, owner, binding, stone), stone, essence)
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED, replay.status());
        assertEquals(OperationJournal.Phase.REFUND_DUE, journal.find(operationId).orElseThrow().phase());
        assertEquals(0, essence.consumeCalls);
        assertEquals(1, essence.releaseCalls);
        assertEquals(1, vessels.resumeCalls);
    }

    private BondedStoneRepairService service(BondedVesselsApi vessels, OperationJournal journal) {
        TameworkApi api = new TameworkApi() {
            public String getApiVersion() { return "0.9.0"; }
            public EnumSet<TameworkApiCapability> getCapabilities() { return repairCapabilities(); }
            public NpcProfilesApi profiles() { return null; }
            public CommandLinksApi commandLinks() { return null; }
            public ProgressionApi progression() { return null; }
            public PolicyApi policies() { return null; }
            public InteractionExtensionApi interactionExtensions() { return null; }
            public TraitEffectApi traitEffects() { return null; }
            public ProfileDataApi profileData() { return null; }
            public TameworkEventsApi events() { return null; }
            public TameworkConfigReadApi configs() { return null; }
            public DiagnosticsApi diagnostics() { return null; }
            public BondedVesselsApi bondedVessels() { return vessels; }
        };
        return new BondedStoneRepairService(new TameworkGameplayAdapter(api), journal);
    }

    private StateStoreOperationJournal consumedJournal(
            String operationId,
            UUID owner,
            UUID binding,
            ConsumableReservation.SourceEvidence stone) throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(journalFile(operationId));
        StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 100L);
        CountingReservation essence = new CountingReservation(operationId);
        assertEquals(OperationJournal.Decision.APPLIED, journal.begin(new OperationJournal.Descriptor(
                operationId,
                operationId,
                OperationJournal.Kind.BONDED_STONE_REPAIR,
                owner,
                "repair_dead_to_stored",
                essence.sourceEvidence(),
                essence.quantity(),
                Optional.of(stone),
                Optional.of(UUID.randomUUID().toString()),
                Optional.of("profile-1"),
                Optional.of(binding),
                OptionalLong.of(3L),
                OptionalLong.of(7L))));
        assertEquals(OperationJournal.Decision.APPLIED, journal.transition(
                operationId,
                OperationJournal.Phase.PREPARED,
                OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Update.EMPTY));
        return journal;
    }

    private Path journalFile(String operationId) {
        return temporaryDirectory.resolve(
                operationId.substring(operationId.lastIndexOf(':') + 1) + ".properties");
    }

    private static BondedVesselTransitionRequest repairRequest(
            String operationId,
            UUID owner,
            UUID binding,
            ConsumableReservation.SourceEvidence stone) {
        return new BondedVesselTransitionRequest(
                TameworkGameplayAdapter.CALLER_NAMESPACE,
                operationId,
                owner,
                binding,
                3L,
                7L,
                BondedVesselTransition.REPAIR_DEAD_TO_STORED,
                new BondedVesselTransitionContext(
                        stone.itemId(), stone.holderEvidenceId(), stone.containerPath(), stone.inventorySlot(),
                        stone.inventoryRevision(), stone.itemFingerprint(), null, null));
    }

    private static ConsumableReservation.SourceEvidence stoneEvidence() {
        return new ConsumableReservation.SourceEvidence(
                "Draconic_Stone_Damaged", "player:owner", "hotbar", 2, 19L,
                "sha256:damaged-stone", 1);
    }

    private static EnumSet<TameworkApiCapability> repairCapabilities() {
        return EnumSet.of(
                TameworkApiCapability.PROFILES,
                TameworkApiCapability.POLICY,
                TameworkApiCapability.PERSISTENCE_RESILIENCE,
                TameworkApiCapability.POPULATION_GROUPS,
                TameworkApiCapability.BONDED_VESSELS);
    }

    private static final class CountingReservation implements ConsumableReservation {
        private final String operationId;
        private int consumeCalls;
        private int releaseCalls;

        private CountingReservation(String operationId) {
            this.operationId = operationId;
        }

        public String operationId() { return operationId; }

        public SourceEvidence sourceEvidence() {
            return new SourceEvidence(
                    "Revitalizing_Essence", "player:owner", "hotbar", 1, 18L,
                    "sha256:revitalizing-essence", 2);
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

    private static final class RecordingBondedVessels implements BondedVesselsApi {
        private final UUID binding;
        private final boolean commits;
        private final String operationId;
        private final BondedVesselOperationResult.Status resumeStatus;
        private int resumeCalls;
        private int claimCalls;
        private int commitCalls;
        private int cancelCalls;
        private BondedVesselTransitionRequest resumedRequest;
        private BondedVesselTransitionToken token;

        private RecordingBondedVessels(
                UUID binding,
                boolean commits,
                String operationId,
                BondedVesselOperationResult.Status resumeStatus) {
            this.binding = binding;
            this.commits = commits;
            this.operationId = operationId;
            this.resumeStatus = resumeStatus;
        }

        static RecordingBondedVessels committing(UUID binding) {
            return committing(binding, BondedVesselOperationResult.Status.RESERVED);
        }

        static RecordingBondedVessels committing(
                UUID binding, BondedVesselOperationResult.Status resumeStatus) {
            return new RecordingBondedVessels(binding, true, null, resumeStatus);
        }

        static RecordingBondedVessels terminallyDenied(UUID binding, String operationId) {
            return new RecordingBondedVessels(binding, false, operationId, null);
        }

        public Optional<BondedVesselView> getByBindingId(UUID bindingId) { return Optional.empty(); }
        public Optional<BondedVesselView> getByProfileId(String profileId) { return Optional.empty(); }
        public BondedVesselReadinessView readiness() { return BondedVesselReadinessView.unavailable(); }
        public BondedVesselProjectionValidationView validateProjection(BondedVesselProjectionValidationRequest request) {
            return BondedVesselProjectionValidationView.unavailable(request.bindingId());
        }
        public CompletionStage<BondedVesselOperationResult> prepareTransition(BondedVesselTransitionRequest request) {
            return CompletableFuture.completedFuture(BondedVesselOperationResult.unavailable("unused"));
        }
        public CompletionStage<BondedVesselOperationResult> resumeTransition(BondedVesselTransitionRequest request) {
            resumeCalls++;
            resumedRequest = request;
            if (!commits) {
                return CompletableFuture.completedFuture(new BondedVesselOperationResult(
                        BondedVesselOperationResult.Status.DENIED, "terminal denial", null, null,
                        binding, "profile-1", BondedVesselOperationResult.UNKNOWN,
                        BondedVesselOperationResult.UNKNOWN, null, null, null, null));
            }
            token = token(binding);
            return CompletableFuture.completedFuture(openResult(resumeStatus, token));
        }
        public BondedVesselOperationResult claimForApply(BondedVesselTransitionToken token) {
            claimCalls++;
            return openResult(BondedVesselOperationResult.Status.APPLYING, token);
        }
        public CompletionStage<BondedVesselOperationResult> commit(BondedVesselTransitionToken token) {
            commitCalls++;
            return CompletableFuture.completedFuture(new BondedVesselOperationResult(
                    BondedVesselOperationResult.Status.COMMITTED, "committed", token.operationId(), null,
                    binding, "profile-1", 4L, 7L, null, null, null, null));
        }
        public CompletionStage<BondedVesselOperationResult> cancel(BondedVesselTransitionToken token) {
            cancelCalls++;
            return CompletableFuture.completedFuture(BondedVesselOperationResult.unavailable("unused"));
        }
        public CompletionStage<Optional<BondedVesselOperationView>> findOperation(
                String callerNamespace, String idempotencyKey) {
            if (commits) return CompletableFuture.completedFuture(Optional.empty());
            return CompletableFuture.completedFuture(Optional.of(new BondedVesselOperationView(
                    UUID.randomUUID(), callerNamespace, operationId,
                    BondedVesselDurableOperationStatus.TERMINAL_DENIED, "terminal denial", binding,
                    "profile-1", BondedVesselTransition.REPAIR_DEAD_TO_STORED,
                    3L, 4L, 7L, null, true, 100L)));
        }

        private BondedVesselOperationResult openResult(
                BondedVesselOperationResult.Status status, BondedVesselTransitionToken token) {
            return new BondedVesselOperationResult(
                    status, status.name().toLowerCase(), token.operationId(), token, binding, "profile-1",
                    4L, 7L, null, BondedVesselState.STORED,
                    "Draconic_Stone", "sha256:repaired-stone");
        }

        private static BondedVesselTransitionToken token(UUID binding) {
            return new BondedVesselTransitionToken(
                    UUID.randomUUID(), UUID.randomUUID(), binding,
                    BondedVesselTransition.REPAIR_DEAD_TO_STORED,
                    BondedVesselState.DEAD, BondedVesselState.STORED,
                    "sha256:damaged-stone", "Draconic_Stone", "sha256:repaired-stone",
                    null, 3L, 4L, 7L, Long.MAX_VALUE);
        }
    }
}
