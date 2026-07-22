package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileKind;
import com.alechilles.hydragon.persistence.SoulBondState;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
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
    void restartAfterMaterialConsumedActivatesWithoutProvisioningOrSecondConsumption() throws Exception {
        UUID owner = UUID.randomUUID();
        CountingProvisioning provisioning = new CountingProvisioning(owner, UUID.randomUUID(), UUID.randomUUID());
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("restart.properties"));
        StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 5L);
        String operationId = "hydragon:soul-bond:" + owner;
        FakeReservation item = new FakeReservation(operationId);
        assertEquals(OperationJournal.Decision.APPLIED, journal.begin(new OperationJournal.Descriptor(
                operationId, operationId, OperationJournal.Kind.SOUL_BOND, owner, soulBondIntent("default", 0, 0),
                item.sourceEvidence(), 1, Optional.empty(), Optional.of(UUID.randomUUID().toString()),
                Optional.of(UUID.randomUUID().toString()), Optional.empty(), java.util.OptionalLong.empty(),
                java.util.OptionalLong.of(0L))));
        assertEquals(OperationJournal.Decision.APPLIED, journal.transition(
                operationId, OperationJournal.Phase.PREPARED, OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Update.EMPTY));
        SoulBondService service = new SoulBondService(
                new TameworkGameplayAdapter(api(fullSoulCapabilities(), provisioning)),
                new StateStoreSoulBondLedger(store), journal, () -> 5L);

        GameplayResult result = service.claim(owner, "default", item).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(0, provisioning.calls);
        assertEquals(0, item.consumeCalls);
        assertEquals(OperationJournal.Phase.COMMITTED, journal.find(operationId).orElseThrow().phase());
    }

    @Test
    void terminalDenialAtomicallyCancelsOperationAndRestoresEntitlementAcrossRestart() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID authorityOperation = UUID.randomUUID();
        String deniedOperation = "hydragon:soul-bond:" + UUID.randomUUID();
        Path stateFile = temp.resolve("denied.properties");
        HyDragonStateStore store = new HyDragonStateStore(stateFile);
        StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 17L);
        DeniedProvisioning deniedProvisioning = new DeniedProvisioning(authorityOperation);
        SoulBondService service = new SoulBondService(
                new TameworkGameplayAdapter(api(fullSoulCapabilities(), deniedProvisioning)),
                new StateStoreSoulBondLedger(store), journal, () -> 17L);
        FakeReservation deniedItem = new FakeReservation(deniedOperation);

        GameplayResult denied = service.claim(owner, "default", deniedItem).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.DENIED, denied.status());
        assertEquals(0, deniedItem.consumeCalls);
        assertEquals(1, deniedItem.releaseCalls);
        assertEquals(1, deniedProvisioning.calls);
        assertEquals(SoulBondState.UNCLAIMED,
                store.snapshot().playerSoulBond(owner).orElseThrow().state());
        assertTrue(store.snapshot().profileExtensions().isEmpty());
        assertEquals(OperationJournal.Phase.CANCELED,
                journal.find(deniedOperation).orElseThrow().phase());
        assertEquals(Optional.of(authorityOperation.toString()),
                journal.find(deniedOperation).orElseThrow().descriptor().authorityOperationId());

        HyDragonStateStore restarted = new HyDragonStateStore(stateFile);
        StateStoreSoulBondLedger restartedLedger = new StateStoreSoulBondLedger(restarted);
        assertEquals(SoulBondLedger.Reservation.ALREADY_APPLIED, restartedLedger.compensateDenied(
                owner, deniedOperation, Optional.of(authorityOperation.toString()), 99L));
        assertEquals(SoulBondState.UNCLAIMED,
                restarted.snapshot().playerSoulBond(owner).orElseThrow().state());
        assertEquals(OperationJournal.Phase.CANCELED,
                new StateStoreOperationJournal(restarted, () -> 99L)
                        .find(deniedOperation).orElseThrow().phase());

        CountingProvisioning allowed = new CountingProvisioning(owner, UUID.randomUUID(), UUID.randomUUID());
        SoulBondService restartedService = new SoulBondService(
                new TameworkGameplayAdapter(api(fullSoulCapabilities(), allowed)),
                restartedLedger, new StateStoreOperationJournal(restarted, () -> 100L), () -> 100L);
        FakeReservation nextItem = new FakeReservation("hydragon:soul-bond:" + UUID.randomUUID());

        GameplayResult applied = restartedService.claim(owner, "default", nextItem)
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, applied.status());
        assertEquals(1, nextItem.consumeCalls);
        assertEquals(1, allowed.calls);
        assertEquals(SoulBondState.CLAIMED,
                restarted.snapshot().playerSoulBond(owner).orElseThrow().state());
    }

    @Test
    void simultaneousIdenticalClaimsShareOneProvisionAndOneConsumption() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        String operationId = "hydragon:soul-bond:" + UUID.randomUUID();
        DeferredProvisioning provisioning = new DeferredProvisioning(owner, profile);
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("concurrent.properties"));
        SoulBondService service = new SoulBondService(
                new TameworkGameplayAdapter(api(fullSoulCapabilities(), provisioning)),
                new StateStoreSoulBondLedger(store),
                new StateStoreOperationJournal(store, () -> 21L),
                () -> 21L);
        FakeReservation firstItem = new FakeReservation(operationId);
        FakeReservation duplicateItem = new FakeReservation(operationId);

        CompletionStage<GameplayResult> first = service.claim(owner, "default", firstItem);
        CompletionStage<GameplayResult> duplicate = service.claim(owner, "default", duplicateItem);
        assertEquals(1, provisioning.provisionCalls.get());

        provisioning.completeProvision();
        assertEquals(GameplayResult.Status.APPLIED, first.toCompletableFuture().join().status());
        assertEquals(GameplayResult.Status.APPLIED, duplicate.toCompletableFuture().join().status());
        assertEquals(1, provisioning.provisionCalls.get());
        assertEquals(1, provisioning.activationCalls.get());
        assertEquals(1, firstItem.consumeCalls);
        assertEquals(0, duplicateItem.consumeCalls);
        assertEquals(0, duplicateItem.releaseCalls);
        assertEquals(OperationJournal.Phase.COMMITTED,
                new StateStoreOperationJournal(store, () -> 21L)
                        .find(operationId).orElseThrow().phase());
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

    private static String soulBondIntent(String worldName, int chunkX, int chunkZ) {
        return "soul_bond:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                worldName.getBytes(StandardCharsets.UTF_8)) + ':' + chunkX + ':' + chunkZ;
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
            return CompletableFuture.completedFuture(new CompanionProvisioningResult(
                    CompanionProvisioningResult.Status.TRANSITIONED,
                    "active",
                    request.callerNamespace(),
                    request.idempotencyKey(),
                    UUID.randomUUID(),
                    profile.toString(),
                    owner,
                    TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    PopulationCompanionLifecycle.ACTIVE,
                    CompanionProvisioningProjectionStatus.ACTIVE,
                    "active",
                    null,
                    1L));
        }
        public CompletionStage<Optional<CompanionProvisioningOperationView>> findOperation(
                String callerNamespace, String idempotencyKey) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static final class DeniedProvisioning implements CompanionProvisioningApi {
        private final UUID operation;
        private int calls;

        private DeniedProvisioning(UUID operation) {
            this.operation = operation;
        }

        public Optional<ProvisionedCompanionView> getByProfileId(String profileId) { return Optional.empty(); }
        public Optional<ProvisionedCompanionView> getByOrigin(String callerNamespace, String idempotencyKey) {
            return Optional.empty();
        }
        public CompletionStage<CompanionProvisioningResult> provision(CompanionProvisioningRequest request) {
            calls++;
            return CompletableFuture.completedFuture(new CompanionProvisioningResult(
                    CompanionProvisioningResult.Status.DENIED,
                    "population denied",
                    request.callerNamespace(),
                    request.idempotencyKey(),
                    operation,
                    null,
                    request.ownerUuid(),
                    request.roleId(),
                    null,
                    CompanionProvisioningProjectionStatus.NOT_REQUESTED,
                    "not requested",
                    null,
                    CompanionProvisioningResult.UNKNOWN_PROFILE_REVISION));
        }
        public CompletionStage<CompanionProvisioningResult> transition(ProvisionedCompanionTransitionRequest request) {
            throw new AssertionError("denied provisioning must not activate");
        }
        public CompletionStage<Optional<CompanionProvisioningOperationView>> findOperation(
                String callerNamespace, String idempotencyKey) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static final class DeferredProvisioning implements CompanionProvisioningApi {
        private final UUID owner;
        private final UUID profile;
        private final UUID operation = UUID.randomUUID();
        private final CompletableFuture<CompanionProvisioningResult> provision = new CompletableFuture<>();
        private final AtomicInteger provisionCalls = new AtomicInteger();
        private final AtomicInteger activationCalls = new AtomicInteger();
        private CompanionProvisioningRequest request;

        private DeferredProvisioning(UUID owner, UUID profile) {
            this.owner = owner;
            this.profile = profile;
        }

        private void completeProvision() {
            CompanionProvisioningRequest current = request;
            provision.complete(new CompanionProvisioningResult(
                    CompanionProvisioningResult.Status.PROVISIONED_DORMANT,
                    "provisioned",
                    current.callerNamespace(),
                    current.idempotencyKey(),
                    operation,
                    profile.toString(),
                    owner,
                    current.roleId(),
                    PopulationCompanionLifecycle.PROVISIONED_DORMANT,
                    CompanionProvisioningProjectionStatus.NOT_REQUESTED,
                    "not requested",
                    null,
                    0L));
        }

        public Optional<ProvisionedCompanionView> getByProfileId(String profileId) { return Optional.empty(); }
        public Optional<ProvisionedCompanionView> getByOrigin(String callerNamespace, String idempotencyKey) {
            return Optional.empty();
        }
        public CompletionStage<CompanionProvisioningResult> provision(CompanionProvisioningRequest request) {
            this.request = request;
            provisionCalls.incrementAndGet();
            return provision;
        }
        public CompletionStage<CompanionProvisioningResult> transition(ProvisionedCompanionTransitionRequest request) {
            activationCalls.incrementAndGet();
            return CompletableFuture.completedFuture(new CompanionProvisioningResult(
                    CompanionProvisioningResult.Status.TRANSITIONED,
                    "active",
                    request.callerNamespace(),
                    request.idempotencyKey(),
                    UUID.randomUUID(),
                    profile.toString(),
                    owner,
                    TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    PopulationCompanionLifecycle.ACTIVE,
                    CompanionProvisioningProjectionStatus.ACTIVE,
                    "active",
                    null,
                    1L));
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
