package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import com.alechilles.hydragon.interactions.HyDragonInteractionRuntime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class TameworkBondedRepairRequestResolverTest {
    @Test
    void buildsRepairRequestOnlyFromAuthoritativeDeadLocatorResult() {
        UUID owner = UUID.randomUUID();
        UUID binding = UUID.randomUUID();
        BondedVesselSourceItemEvidence source = new BondedVesselSourceItemEvidence(
                "Draconic_Stone:Damaged", "player:" + owner, "hotbar", 2, 9L, "fingerprint");
        BondedVesselView vessel = new BondedVesselView(
                binding, UUID.randomUUID().toString(), owner, "HyDragonDraconicStone",
                BondedVesselState.DEAD, 4L, 7L, null,
                BondedVesselProjectionStatus.PRESENT, null, 10L);
        BondedVesselsApi vessels = vessels(request -> CompletableFuture.completedFuture(
                new BondedVesselHeldItemLocatorResult(
                        BondedVesselHeldItemProjectionStatus.VALID, "valid", request,
                        source, vessel, true)));
        TameworkBondedRepairRequestResolver resolver = new TameworkBondedRepairRequestResolver(api(vessels));

        HyDragonGameplayRuntime.Resolution resolution = resolver.resolve(
                owner, "default", "repair-one",
                new HyDragonInteractionRuntime.HeldItemLocator(
                        "player:" + owner, "hotbar", 2, "Draconic_Stone:Damaged"))
                .toCompletableFuture().join();

        assertTrue(resolution.request().isPresent());
        BondedVesselTransitionRequest request = resolution.request().orElseThrow();
        assertEquals(binding, request.bindingId());
        assertEquals(4L, request.expectedGeneration());
        assertEquals(7L, request.expectedProfileRevision());
        assertEquals(BondedVesselTransition.REPAIR_DEAD_TO_STORED, request.transition());
        assertEquals("fingerprint", resolution.damagedStone().orElseThrow().itemFingerprint());
    }

    @Test
    void deniedLocatorNeverCreatesMutationAuthority() {
        UUID owner = UUID.randomUUID();
        BondedVesselsApi vessels = vessels(request -> CompletableFuture.completedFuture(
                new BondedVesselHeldItemLocatorResult(
                        BondedVesselHeldItemProjectionStatus.STATE_MISMATCH, "not-dead", request,
                        null, null, false)));

        HyDragonGameplayRuntime.Resolution resolution = new TameworkBondedRepairRequestResolver(api(vessels))
                .resolve(owner, "default", "repair-two",
                        new HyDragonInteractionRuntime.HeldItemLocator(
                                "player:" + owner, "hotbar", 1, "Draconic_Stone:Damaged"))
                .toCompletableFuture().join();

        assertTrue(resolution.request().isEmpty());
        assertEquals("not-dead", resolution.reason());
    }

    private static BondedVesselsApi vessels(
            java.util.function.Function<BondedVesselHeldItemLocatorRequest,
                    CompletionStage<BondedVesselHeldItemLocatorResult>> lookup) {
        return new BondedVesselsApi() {
            public Optional<BondedVesselView> getByBindingId(UUID bindingId) { return Optional.empty(); }
            public Optional<BondedVesselView> getByProfileId(String profileId) { return Optional.empty(); }
            public BondedVesselReadinessView readiness() { return BondedVesselReadinessView.unavailable(); }
            public BondedVesselProjectionValidationView validateProjection(
                    BondedVesselProjectionValidationRequest request) {
                return BondedVesselProjectionValidationView.unavailable(request.bindingId());
            }
            public CompletionStage<BondedVesselHeldItemLocatorResult> resolveHeldItemLocator(
                    BondedVesselHeldItemLocatorRequest request) { return lookup.apply(request); }
            public CompletionStage<BondedVesselOperationResult> prepareTransition(
                    BondedVesselTransitionRequest request) { return CompletableFuture.completedFuture(null); }
            public CompletionStage<BondedVesselOperationResult> resumeTransition(
                    BondedVesselTransitionRequest request) { return CompletableFuture.completedFuture(null); }
            public BondedVesselOperationResult claimForApply(BondedVesselTransitionToken token) { return null; }
            public CompletionStage<BondedVesselOperationResult> commit(BondedVesselTransitionToken token) {
                return CompletableFuture.completedFuture(null);
            }
            public CompletionStage<BondedVesselOperationResult> cancel(BondedVesselTransitionToken token) {
                return CompletableFuture.completedFuture(null);
            }
            public CompletionStage<Optional<BondedVesselOperationView>> findOperation(
                    String callerNamespace, String idempotencyKey) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
    }

    private static TameworkApi api(BondedVesselsApi vessels) {
        return new TameworkApi() {
            public String getApiVersion() { return "0.9.0"; }
            public EnumSet<TameworkApiCapability> getCapabilities() { return EnumSet.noneOf(TameworkApiCapability.class); }
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
    }
}
