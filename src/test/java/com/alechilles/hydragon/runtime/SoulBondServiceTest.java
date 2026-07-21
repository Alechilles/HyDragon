package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileKind;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoulBondServiceTest {
    @TempDir Path temp;

    @Test
    void retryAfterCommitDoesNotCreateOrConsumeAgain() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID authorityOperation = UUID.randomUUID();
        CountingProvisioning provisioning = new CountingProvisioning(owner, profile, authorityOperation);
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(api(fullSoulCapabilities(), provisioning));
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("state.properties"));
        SoulBondService service = new SoulBondService(
                adapter,
                new StateStoreSoulBondLedger(store),
                new StateStoreOperationJournal(store, () -> 1234L),
                () -> 1234L);
        String operationId = "hydragon:soul-bond:" + owner;

        FakeReservation first = new FakeReservation(operationId);
        GameplayResult applied = service.claim(owner, "default", first).toCompletableFuture().join();
        FakeReservation retry = new FakeReservation(operationId);
        GameplayResult denied = service.claim(owner, "default", retry).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, applied.status());
        assertEquals(GameplayResult.Status.DENIED, denied.status());
        assertEquals(1, provisioning.calls);
        assertEquals(1, first.consumeCalls);
        assertEquals(0, retry.consumeCalls);
        assertEquals(1, retry.releaseCalls);
        assertEquals(profile, store.snapshot().playerSoulBond(owner).orElseThrow().profileId().orElseThrow());
        var extension = store.snapshot().profileExtension(profile).orElseThrow();
        assertEquals(ProfileKind.SOULBOUND_MINIWYVERN, extension.kind());
        assertEquals(Optional.of("neutral"), extension.archetypeId());
        assertEquals(Optional.of(operationId), extension.lastOperationId());
        assertEquals(OperationJournal.Phase.COMMITTED,
                new StateStoreOperationJournal(store, () -> 1234L).find(operationId).orElseThrow().phase());
    }

    @Test
    void missingProvisioningCapabilityReleasesInputWithoutMutation() throws Exception {
        UUID owner = UUID.randomUUID();
        CountingProvisioning provisioning = new CountingProvisioning(owner, UUID.randomUUID(), UUID.randomUUID());
        EnumSet<TameworkApiCapability> capabilities = fullSoulCapabilities();
        capabilities.remove(TameworkApiCapability.COMPANION_PROVISIONING);
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("missing.properties"));
        SoulBondService service = new SoulBondService(
                new TameworkGameplayAdapter(api(capabilities, provisioning)),
                new StateStoreSoulBondLedger(store),
                new StateStoreOperationJournal(store, () -> 1L),
                () -> 1L);
        FakeReservation item = new FakeReservation("hydragon:soul-bond:" + owner);

        GameplayResult result = service.claim(owner, "default", item).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.UNAVAILABLE, result.status());
        assertEquals(0, item.consumeCalls);
        assertEquals(1, item.releaseCalls);
        assertTrue(store.snapshot().playerSoulBonds().isEmpty());
    }

    @Test
    void restartAfterMaterialConsumedClosesWithoutProvisioningOrSecondConsumption() throws Exception {
        UUID owner = UUID.randomUUID();
        CountingProvisioning provisioning = new CountingProvisioning(owner, UUID.randomUUID(), UUID.randomUUID());
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("restart.properties"));
        StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 5L);
        String operationId = "hydragon:soul-bond:" + owner;
        FakeReservation item = new FakeReservation(operationId);
        assertEquals(OperationJournal.Decision.APPLIED, journal.begin(new OperationJournal.Descriptor(
                operationId, operationId, OperationJournal.Kind.SOUL_BOND, owner, "soul_bond",
                item.sourceEvidence(), 1, Optional.empty(), Optional.of(UUID.randomUUID().toString()),
                Optional.of(UUID.randomUUID().toString()), Optional.empty(), java.util.OptionalLong.empty(),
                java.util.OptionalLong.empty())));
        assertEquals(OperationJournal.Decision.APPLIED, journal.transition(
                operationId, OperationJournal.Phase.PREPARED, OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Update.EMPTY));
        SoulBondService service = new SoulBondService(
                new TameworkGameplayAdapter(api(fullSoulCapabilities(), provisioning)),
                new StateStoreSoulBondLedger(store), journal, () -> 5L);

        GameplayResult result = service.claim(owner, "default", item).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.ALREADY_APPLIED, result.status());
        assertEquals(0, provisioning.calls);
        assertEquals(0, item.consumeCalls);
        assertEquals(OperationJournal.Phase.COMMITTED, journal.find(operationId).orElseThrow().phase());
    }

    private static EnumSet<TameworkApiCapability> fullSoulCapabilities() {
        return EnumSet.of(
                TameworkApiCapability.PROFILES,
                TameworkApiCapability.POLICY,
                TameworkApiCapability.PERSISTENCE_RESILIENCE,
                TameworkApiCapability.POPULATION_GROUPS,
                TameworkApiCapability.COMPANION_PROVISIONING,
                TameworkApiCapability.INTERACTION_EXTENSIONS);
    }

    private static TameworkApi api(EnumSet<TameworkApiCapability> capabilities,
                                   CompanionProvisioningApi provisioning) {
        return new TameworkApi() {
            public String getApiVersion() { return "0.9.0"; }
            public EnumSet<TameworkApiCapability> getCapabilities() { return capabilities.clone(); }
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

    private static final class CountingProvisioning implements CompanionProvisioningApi {
        private final UUID owner;
        private final UUID profile;
        private final UUID operation;
        private int calls;

        private CountingProvisioning(UUID owner, UUID profile, UUID operation) {
            this.owner = owner;
            this.profile = profile;
            this.operation = operation;
        }

        public Optional<ProvisionedCompanionView> getByProfileId(String profileId) { return Optional.empty(); }
        public Optional<ProvisionedCompanionView> getByOrigin(String callerNamespace, String idempotencyKey) {
            return Optional.empty();
        }
        public CompletionStage<CompanionProvisioningResult> provision(CompanionProvisioningRequest request) {
            calls++;
            return CompletableFuture.completedFuture(new CompanionProvisioningResult(
                    CompanionProvisioningResult.Status.PROVISIONED_DORMANT,
                    "provisioned",
                    request.callerNamespace(),
                    request.idempotencyKey(),
                    operation,
                    profile.toString(),
                    owner,
                    TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    PopulationCompanionLifecycle.PROVISIONED_DORMANT,
                    CompanionProvisioningProjectionStatus.NOT_REQUESTED,
                    "not-requested",
                    null,
                    0L));
        }
        public CompletionStage<CompanionProvisioningResult> transition(ProvisionedCompanionTransitionRequest request) {
            return CompletableFuture.completedFuture(CompanionProvisioningResult.unavailable("unused"));
        }
        public CompletionStage<Optional<CompanionProvisioningOperationView>> findOperation(
                String callerNamespace, String idempotencyKey) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static final class FakeReservation implements ConsumableReservation {
        private final String operationId;
        private int consumeCalls;
        private int releaseCalls;

        private FakeReservation(String operationId) { this.operationId = operationId; }
        public String operationId() { return operationId; }
        public SourceEvidence sourceEvidence() {
            return new SourceEvidence("Draconic_Soul_Bond", "player", "hotbar", 0, 1L, "fingerprint", 1);
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
