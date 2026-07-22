package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.SoulBondState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoulBondTransientRecoveryTest {
    @TempDir Path temp;

    @Test
    void unavailableAndQuarantinedResponsesRetryTheOriginalStableOperation() throws Exception {
        for (CompanionProvisioningResult.Status status : List.of(
                CompanionProvisioningResult.Status.UNAVAILABLE,
                CompanionProvisioningResult.Status.QUARANTINED)) {
            UUID owner = UUID.randomUUID();
            UUID profile = UUID.randomUUID();
            String originalOperation = "hydragon:soul-bond:" + UUID.randomUUID();
            ScriptedProvisioning authority = new ScriptedProvisioning(owner, profile);
            authority.firstResult = nonterminalResult(status);
            HyDragonStateStore store = new HyDragonStateStore(temp.resolve(status.name() + ".properties"));
            StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 10L);
            SoulBondService service = service(store, journal, authority);
            FakeReservation first = new FakeReservation(originalOperation);

            GameplayResult ambiguous = service.claim(owner, "default", first).toCompletableFuture().join();

            assertEquals(GameplayResult.Status.RETRYABLE, ambiguous.status());
            assertEquals(1, first.releaseCalls);
            assertEquals(OperationJournal.Phase.PREPARED,
                    journal.find(originalOperation).orElseThrow().phase());

            FakeReservation retry = new FakeReservation("hydragon:soul-bond:" + UUID.randomUUID());
            GameplayResult recovered = service.claim(owner, "default", retry).toCompletableFuture().join();

            assertEquals(GameplayResult.Status.APPLIED, recovered.status());
            assertEquals(List.of(originalOperation, originalOperation), authority.provisionKeys);
            assertEquals(0, first.consumeCalls);
            assertEquals(1, retry.consumeCalls);
            assertEquals(OperationJournal.Phase.COMMITTED,
                    journal.find(originalOperation).orElseThrow().phase());
            assertTrue(journal.find(retry.operationId()).isEmpty());
        }
    }

    @Test
    void nullAndMalformedResultsUseCanonicalOperationEvidenceBeforeConsumption() throws Exception {
        for (boolean nullResult : List.of(true, false)) {
            UUID owner = UUID.randomUUID();
            UUID profile = UUID.randomUUID();
            String operationId = "hydragon:soul-bond:" + UUID.randomUUID();
            ScriptedProvisioning authority = new ScriptedProvisioning(owner, profile);
            authority.returnNullFirst = nullResult;
            if (!nullResult) authority.firstResult = authority.success(operationId, UUID.randomUUID());
            authority.operationView = Optional.of(authority.committedView(operationId));
            HyDragonStateStore store = new HyDragonStateStore(temp.resolve("canonical-" + nullResult + ".properties"));
            StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 20L);
            FakeReservation item = new FakeReservation(operationId);

            GameplayResult result = service(store, journal, authority)
                    .claim(owner, "default", item).toCompletableFuture().join();

            assertEquals(GameplayResult.Status.APPLIED, result.status());
            assertEquals(1, item.consumeCalls);
            assertEquals(0, item.releaseCalls);
            assertEquals(profile, store.snapshot().playerSoulBond(owner).orElseThrow()
                    .profileId().orElseThrow());
            assertEquals(OperationJournal.Phase.COMMITTED,
                    journal.find(operationId).orElseThrow().phase());
        }
    }

    @Test
    void restartLinksPreparedAuthorityEvidenceAndLaterRetryConsumesOnce() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        String operationId = "hydragon:soul-bond:" + UUID.randomUUID();
        Path statePath = temp.resolve("restart-prepared.properties");
        ScriptedProvisioning unavailable = new ScriptedProvisioning(owner, profile);
        unavailable.firstResult = CompanionProvisioningResult.unavailable("transient");
        HyDragonStateStore initial = new HyDragonStateStore(statePath);
        StateStoreOperationJournal initialJournal = new StateStoreOperationJournal(initial, () -> 30L);
        FakeReservation first = new FakeReservation(operationId);
        service(initial, initialJournal, unavailable).claim(owner, "default", first)
                .toCompletableFuture().join();

        HyDragonStateStore restarted = new HyDragonStateStore(statePath);
        StateStoreOperationJournal restartedJournal = new StateStoreOperationJournal(restarted, () -> 31L);
        ScriptedProvisioning recoveredAuthority = new ScriptedProvisioning(owner, profile);
        recoveredAuthority.operationView = Optional.of(recoveredAuthority.committedView(operationId));
        SoulBondService restartedService = service(restarted, restartedJournal, recoveredAuthority);

        GameplayResult startupRecovery = restartedService.recover(
                restartedJournal.find(operationId).orElseThrow()).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED, startupRecovery.status());
        assertEquals(SoulBondState.CLAIMED,
                restarted.snapshot().playerSoulBond(owner).orElseThrow().state());
        assertEquals(OperationJournal.Phase.PREPARED,
                restartedJournal.find(operationId).orElseThrow().phase());
        assertEquals(0, recoveredAuthority.provisionKeys.size());

        FakeReservation retry = new FakeReservation("hydragon:soul-bond:" + UUID.randomUUID());
        GameplayResult completed = restartedService.claim(owner, "default", retry)
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, completed.status());
        assertEquals(1, retry.consumeCalls);
        assertEquals(0, recoveredAuthority.provisionKeys.size());
        assertEquals(OperationJournal.Phase.COMMITTED,
                restartedJournal.find(operationId).orElseThrow().phase());
    }

    @Test
    void durableQuarantineEndsPreparedLoopWithoutConsuming() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        String operationId = "hydragon:soul-bond:" + UUID.randomUUID();
        ScriptedProvisioning authority = new ScriptedProvisioning(owner, profile);
        authority.firstResult = nonterminalResult(CompanionProvisioningResult.Status.QUARANTINED);
        authority.operationView = Optional.of(authority.quarantinedView(operationId));
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("durable-quarantine.properties"));
        StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 40L);
        FakeReservation item = new FakeReservation(operationId);

        GameplayResult result = service(store, journal, authority)
                .claim(owner, "default", item).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.QUARANTINED, result.status());
        assertEquals(0, item.consumeCalls);
        assertEquals(1, item.releaseCalls);
        assertEquals(OperationJournal.Phase.QUARANTINED,
                journal.find(operationId).orElseThrow().phase());
    }

    private SoulBondService service(HyDragonStateStore store,
                                    StateStoreOperationJournal journal,
                                    CompanionProvisioningApi authority) {
        return new SoulBondService(new TameworkGameplayAdapter(api(authority)),
                new StateStoreSoulBondLedger(store), journal, () -> 50L);
    }

    private static CompanionProvisioningResult nonterminalResult(CompanionProvisioningResult.Status status) {
        return status == CompanionProvisioningResult.Status.UNAVAILABLE
                ? CompanionProvisioningResult.unavailable("transient")
                : new CompanionProvisioningResult(status, "quarantined", null, null, null, null, null,
                null, null, CompanionProvisioningProjectionStatus.UNAVAILABLE, "quarantined", null,
                CompanionProvisioningResult.UNKNOWN_PROFILE_REVISION);
    }

    private static TameworkApi api(CompanionProvisioningApi provisioning) {
        return new TameworkApi() {
            public String getApiVersion() { return "0.9.0"; }
            public EnumSet<TameworkApiCapability> getCapabilities() {
                return EnumSet.of(TameworkApiCapability.PROFILES, TameworkApiCapability.POLICY,
                        TameworkApiCapability.PERSISTENCE_RESILIENCE,
                        TameworkApiCapability.POPULATION_GROUPS,
                        TameworkApiCapability.COMPANION_PROVISIONING,
                        TameworkApiCapability.INTERACTION_EXTENSIONS);
            }
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
            public CompanionProvisioningApi companionProvisioning() { return provisioning; }
        };
    }

    private static final class ScriptedProvisioning implements CompanionProvisioningApi {
        private final UUID owner;
        private final UUID profile;
        private final UUID authorityOperation = UUID.randomUUID();
        private final List<String> provisionKeys = new ArrayList<>();
        private CompanionProvisioningResult firstResult;
        private boolean returnNullFirst;
        private Optional<CompanionProvisioningOperationView> operationView = Optional.empty();

        private ScriptedProvisioning(UUID owner, UUID profile) {
            this.owner = owner;
            this.profile = profile;
        }

        private CompanionProvisioningResult success(String key, UUID resultOwner) {
            return new CompanionProvisioningResult(CompanionProvisioningResult.Status.PROVISIONED_DORMANT,
                    "provisioned", TameworkGameplayAdapter.CALLER_NAMESPACE, key, authorityOperation,
                    profile.toString(), resultOwner, TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    PopulationCompanionLifecycle.PROVISIONED_DORMANT,
                    CompanionProvisioningProjectionStatus.NOT_REQUESTED, "not requested", null, 0L);
        }

        private CompanionProvisioningOperationView committedView(String key) {
            return new CompanionProvisioningOperationView(authorityOperation,
                    TameworkGameplayAdapter.CALLER_NAMESPACE, key, null,
                    CompanionProvisioningOperationStatus.DORMANT_COMMITTED, "committed", profile.toString(),
                    owner, TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    PopulationCompanionLifecycle.PROVISIONED_DORMANT,
                    CompanionProvisioningProjectionStatus.NOT_REQUESTED, 0L, true, 1L);
        }

        private CompanionProvisioningOperationView quarantinedView(String key) {
            return new CompanionProvisioningOperationView(authorityOperation,
                    TameworkGameplayAdapter.CALLER_NAMESPACE, key, null,
                    CompanionProvisioningOperationStatus.QUARANTINED, "quarantined", null,
                    owner, TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE, null,
                    CompanionProvisioningProjectionStatus.UNAVAILABLE, -1L, true, 1L);
        }

        public Optional<ProvisionedCompanionView> getByProfileId(String profileId) { return Optional.empty(); }
        public Optional<ProvisionedCompanionView> getByOrigin(String namespace, String key) { return Optional.empty(); }
        public CompletionStage<CompanionProvisioningResult> provision(CompanionProvisioningRequest request) {
            provisionKeys.add(request.idempotencyKey());
            if (provisionKeys.size() == 1 && returnNullFirst) {
                return CompletableFuture.completedFuture(null);
            }
            if (provisionKeys.size() == 1 && firstResult != null) {
                return CompletableFuture.completedFuture(firstResult);
            }
            return CompletableFuture.completedFuture(success(request.idempotencyKey(), owner));
        }
        public CompletionStage<CompanionProvisioningResult> transition(ProvisionedCompanionTransitionRequest request) {
            return CompletableFuture.completedFuture(new CompanionProvisioningResult(
                    CompanionProvisioningResult.Status.TRANSITIONED, "active", request.callerNamespace(),
                    request.idempotencyKey(), UUID.randomUUID(), profile.toString(), owner,
                    TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE, PopulationCompanionLifecycle.ACTIVE,
                    CompanionProvisioningProjectionStatus.ACTIVE, "active", null, 1L));
        }
        public CompletionStage<Optional<CompanionProvisioningOperationView>> findOperation(
                String namespace, String key) {
            return CompletableFuture.completedFuture(operationView);
        }
    }

    private static final class FakeReservation implements ConsumableReservation {
        private final String operationId;
        private int consumeCalls;
        private int releaseCalls;

        private FakeReservation(String operationId) { this.operationId = operationId; }
        public String operationId() { return operationId; }
        public SourceEvidence sourceEvidence() {
            return new SourceEvidence("Draconic_Soul_Bond", "player", "hotbar", 0, 1L,
                    "fingerprint", 1);
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
}
