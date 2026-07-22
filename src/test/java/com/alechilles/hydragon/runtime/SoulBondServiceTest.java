package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileKind;
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

class SoulBondServiceTest {
    @TempDir Path temp;

    @Test
    void consumesEggBeforeAtomicProvisionAndLinkThenStartsTimedSummon() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        List<String> order = new ArrayList<>();
        LinkAuthority authority = new LinkAuthority(owner, profile, order);
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("success.properties"));
        SoulBondService service = service(store, authority, 10L);
        FakeReservation egg = new FakeReservation("hydragon:soul-bond:" + owner, order);

        GameplayResult result = service.claim(owner, "default", egg).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(List.of("consume", "provision-link", "timed-summon"), order);
        assertEquals(TameworkGameplayAdapter.DRAGON_HORN_COMMAND_FAMILY,
                authority.lastRequest.commandFamilyId());
        assertEquals(TameworkGameplayAdapter.DRAGON_HORN_COMMAND_CONFIG,
                authority.lastRequest.requiredCommandConfigId());
        assertEquals(TameworkGameplayAdapter.DRAGON_HORN_ITEM_ID,
                authority.lastRequest.accessItemId());
        assertEquals(TameworkGameplayAdapter.MINIWYVERN_POPULATION_GROUP,
                authority.lastRequest.groupId());
        assertTrue(authority.lastRequest.requestInitialProjection());
        assertEquals(SoulBondState.CLAIMED,
                store.snapshot().playerSoulBond(owner).orElseThrow().state());
        assertEquals(profile,
                store.snapshot().playerSoulBond(owner).orElseThrow().profileId().orElseThrow());
        assertEquals(ProfileKind.SOULBOUND_MINIWYVERN,
                store.snapshot().profileExtension(profile).orElseThrow().kind());
        assertEquals(OperationJournal.Phase.COMMITTED,
                journal(store, 10L).find(egg.operationId()).orElseThrow().phase());
    }

    @Test
    void retryAfterCommitCannotConsumeOrProvisionAgain() throws Exception {
        UUID owner = UUID.randomUUID();
        List<String> order = new ArrayList<>();
        LinkAuthority authority = new LinkAuthority(owner, UUID.randomUUID(), order);
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("retry.properties"));
        SoulBondService service = service(store, authority, 20L);
        String operationId = "hydragon:soul-bond:" + UUID.randomUUID();
        FakeReservation first = new FakeReservation(operationId, order);
        FakeReservation retry = new FakeReservation(operationId, order);

        assertEquals(GameplayResult.Status.APPLIED,
                service.claim(owner, "default", first).toCompletableFuture().join().status());
        assertEquals(GameplayResult.Status.DENIED,
                service.claim(owner, "default", retry).toCompletableFuture().join().status());

        assertEquals(1, first.consumeCalls);
        assertEquals(0, retry.consumeCalls);
        assertEquals(1, retry.releaseCalls);
        assertEquals(1, authority.calls);
    }

    @Test
    void transientLinkFailureReplaysSameOperationWithoutSecondEggSpend() throws Exception {
        UUID owner = UUID.randomUUID();
        List<String> order = new ArrayList<>();
        LinkAuthority authority = new LinkAuthority(owner, UUID.randomUUID(), order);
        authority.unavailableCalls = 1;
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("transient.properties"));
        StateStoreOperationJournal journal = journal(store, 30L);
        SoulBondService service = service(store, authority, 30L);
        String operationId = "hydragon:soul-bond:" + UUID.randomUUID();
        FakeReservation first = new FakeReservation(operationId, order);
        FakeReservation retry = new FakeReservation(operationId, order);

        assertEquals(GameplayResult.Status.RETRYABLE,
                service.claim(owner, "default", first).toCompletableFuture().join().status());
        assertEquals(OperationJournal.Phase.MATERIAL_CONSUMED,
                journal.find(operationId).orElseThrow().phase());
        assertEquals(GameplayResult.Status.APPLIED,
                service.claim(owner, "default", retry).toCompletableFuture().join().status());

        assertEquals(1, first.consumeCalls);
        assertEquals(0, retry.consumeCalls);
        assertEquals(List.of(operationId, operationId), authority.keys);
        assertEquals(OperationJournal.Phase.COMMITTED,
                journal.find(operationId).orElseThrow().phase());
    }

    @Test
    void terminalDenialAfterSpendReleasesEntitlementAndCreatesOneEggRefund() throws Exception {
        UUID owner = UUID.randomUUID();
        List<String> order = new ArrayList<>();
        LinkAuthority authority = new LinkAuthority(owner, UUID.randomUUID(), order);
        authority.deny = true;
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("denied.properties"));
        StateStoreOperationJournal journal = journal(store, 40L);
        FakeReservation egg = new FakeReservation("hydragon:soul-bond:" + UUID.randomUUID(), order);

        GameplayResult result = service(store, authority, 40L)
                .claim(owner, "default", egg).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED, result.status());
        assertEquals(1, egg.consumeCalls);
        assertEquals(SoulBondState.UNCLAIMED,
                store.snapshot().playerSoulBond(owner).orElseThrow().state());
        assertEquals(OperationJournal.Phase.REFUND_DUE,
                journal.find(egg.operationId()).orElseThrow().phase());
        List<ConsumableRefundClaimService.Claim> claims =
                new ConsumableRefundClaimService(journal).claims(owner);
        assertEquals(1, claims.size());
        assertEquals(SoulBondService.WYVERN_EGG_ITEM_ID, claims.getFirst().itemId());
        assertEquals(1, claims.getFirst().quantity());
    }

    @Test
    void initialSummonFailureStillCommitsVisibleDormantHornMembership() throws Exception {
        UUID owner = UUID.randomUUID();
        List<String> order = new ArrayList<>();
        LinkAuthority authority = new LinkAuthority(owner, UUID.randomUUID(), order);
        authority.initialProjectionSuccessful = false;
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("dormant.properties"));
        FakeReservation egg = new FakeReservation("hydragon:soul-bond:" + UUID.randomUUID(), order);

        GameplayResult result = service(store, authority, 50L)
                .claim(owner, "default", egg).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertTrue(result.reason().contains("stored in the Dragon Horn"));
        assertEquals(SoulBondState.CLAIMED,
                store.snapshot().playerSoulBond(owner).orElseThrow().state());
        assertEquals(OperationJournal.Phase.COMMITTED,
                journal(store, 50L).find(egg.operationId()).orElseThrow().phase());
    }

    private static SoulBondService service(
            HyDragonStateStore store,
            CompanionProvisioningApi provisioning,
            long now) {
        return new SoulBondService(
                new TameworkGameplayAdapter(api(provisioning)),
                new StateStoreSoulBondLedger(store),
                journal(store, now),
                () -> now);
    }

    private static StateStoreOperationJournal journal(HyDragonStateStore store, long now) {
        return new StateStoreOperationJournal(store, () -> now);
    }

    private static TameworkApi api(CompanionProvisioningApi provisioning) {
        return new TameworkApi() {
            public String getApiVersion() { return "0.9.0"; }
            public EnumSet<TameworkApiCapability> getCapabilities() {
                return EnumSet.of(
                        TameworkApiCapability.PROFILES,
                        TameworkApiCapability.POLICY,
                        TameworkApiCapability.PERSISTENCE_RESILIENCE,
                        TameworkApiCapability.POPULATION_GROUPS,
                        TameworkApiCapability.COMPANION_PROVISIONING,
                        TameworkApiCapability.COMMAND_FAMILY_ROSTERS,
                        TameworkApiCapability.COMMAND_TIMED_SUMMONING,
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

    private static final class LinkAuthority implements CompanionProvisioningApi {
        private final UUID owner;
        private final UUID profile;
        private final UUID authorityOperation = UUID.randomUUID();
        private final List<String> order;
        private final List<String> keys = new ArrayList<>();
        private int calls;
        private int unavailableCalls;
        private boolean deny;
        private boolean initialProjectionSuccessful = true;
        private CompanionProvisioningLinkRequest lastRequest;

        private LinkAuthority(UUID owner, UUID profile, List<String> order) {
            this.owner = owner;
            this.profile = profile;
            this.order = order;
        }

        public Optional<ProvisionedCompanionView> getByProfileId(String profileId) {
            return Optional.empty();
        }
        public Optional<ProvisionedCompanionView> getByOrigin(String namespace, String key) {
            return Optional.empty();
        }
        public CompletionStage<CompanionProvisioningResult> provision(
                CompanionProvisioningRequest request) {
            throw new AssertionError("HyDragon must use atomic provisionAndLink");
        }
        public CompletionStage<CompanionProvisioningResult> transition(
                ProvisionedCompanionTransitionRequest request) {
            throw new AssertionError("Egg projection must use timed summoning");
        }
        public CompletionStage<Optional<CompanionProvisioningOperationView>> findOperation(
                String namespace, String key) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        public CompletionStage<CompanionProvisioningLinkResult> provisionAndLink(
                CompanionProvisioningLinkRequest request) {
            calls++;
            lastRequest = request;
            keys.add(request.provisioning().idempotencyKey());
            order.add("provision-link");
            if (unavailableCalls-- > 0) {
                return CompletableFuture.completedFuture(new CompanionProvisioningLinkResult(
                        CompanionProvisioningLinkResult.Status.UNAVAILABLE,
                        "transient",
                        CompanionProvisioningResult.unavailable("transient"),
                        null,
                        null,
                        null));
            }
            CompanionProvisioningResult provisioning = new CompanionProvisioningResult(
                    deny ? CompanionProvisioningResult.Status.DENIED
                            : CompanionProvisioningResult.Status.PROVISIONED_DORMANT,
                    deny ? "denied" : "provisioned",
                    request.provisioning().callerNamespace(),
                    request.provisioning().idempotencyKey(),
                    authorityOperation,
                    deny ? null : profile.toString(),
                    owner,
                    request.provisioning().roleId(),
                    deny ? null : PopulationCompanionLifecycle.PROVISIONED_DORMANT,
                    CompanionProvisioningProjectionStatus.NOT_REQUESTED,
                    "not requested",
                    null,
                    deny ? CompanionProvisioningResult.UNKNOWN_PROFILE_REVISION : 0L);
            if (deny) {
                return CompletableFuture.completedFuture(new CompanionProvisioningLinkResult(
                        CompanionProvisioningLinkResult.Status.DENIED,
                        "denied",
                        provisioning,
                        null,
                        null,
                        null));
            }
            CommandFamilyRosterMembershipView membership = new CommandFamilyRosterMembershipView(
                    owner,
                    request.commandFamilyId(),
                    profile.toString(),
                    request.provisioning().roleId(),
                    0L,
                    CommandFamilyRosterMemberState.ROSTER_STORED,
                    request.groupId(),
                    request.activeForBulkCommands(),
                    null,
                    1L);
            if (request.requestInitialProjection()) order.add("timed-summon");
            CommandTimedSummoningResult initialProjection = new CommandTimedSummoningResult(
                    initialProjectionSuccessful ? CommandTimedSummoningResult.Status.SUCCESS
                            : CommandTimedSummoningResult.Status.UNAVAILABLE,
                    initialProjectionSuccessful ? "summoned" : "unavailable",
                    null);
            return CompletableFuture.completedFuture(new CompanionProvisioningLinkResult(
                    CompanionProvisioningLinkResult.Status.COMMITTED,
                    "committed",
                    provisioning,
                    new CommandFamilyRosterView(
                            owner, request.commandFamilyId(), 1L, List.of(membership), 1L),
                    membership,
                    initialProjection));
        }
    }

    private static final class FakeReservation implements ConsumableReservation {
        private final String operationId;
        private final List<String> order;
        private int consumeCalls;
        private int releaseCalls;

        private FakeReservation(String operationId, List<String> order) {
            this.operationId = operationId;
            this.order = order;
        }
        public String operationId() { return operationId; }
        public SourceEvidence sourceEvidence() {
            return new SourceEvidence(
                    SoulBondService.WYVERN_EGG_ITEM_ID,
                    "player", "hotbar", 0, 1L, "fingerprint", 1);
        }
        public int quantity() { return 1; }
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
