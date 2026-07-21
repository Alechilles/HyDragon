package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TameworkGameplayAdapterTest {
    @Test
    void dormantActivationUsesThePublicProvisioningTransition() {
        AtomicReference<ProvisionedCompanionTransitionRequest> captured = new AtomicReference<>();
        CompanionProvisioningApi provisioning = new CompanionProvisioningApi() {
            public Optional<ProvisionedCompanionView> getByProfileId(String profileId) {
                return Optional.empty();
            }
            public Optional<ProvisionedCompanionView> getByOrigin(String namespace, String key) {
                return Optional.empty();
            }
            public CompletionStage<CompanionProvisioningResult> provision(CompanionProvisioningRequest request) {
                return CompletableFuture.completedFuture(CompanionProvisioningResult.unavailable("unused"));
            }
            public CompletionStage<CompanionProvisioningResult> transition(
                    ProvisionedCompanionTransitionRequest request) {
                captured.set(request);
                return CompletableFuture.completedFuture(CompanionProvisioningResult.unavailable("captured"));
            }
            public CompletionStage<Optional<CompanionProvisioningOperationView>> findOperation(
                    String namespace, String key) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
        UUID player = UUID.randomUUID();
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(api(
                EnumSet.of(TameworkApiCapability.COMPANION_PROVISIONING), provisioning));

        adapter.activateDormantMiniwyvern(
                player, "activate:1", "profile-1", 4L, "default", 12, -7);

        ProvisionedCompanionTransitionRequest request = captured.get();
        assertTrue(request != null);
        assertTrue(request.actorUuid().equals(player));
        assertTrue(request.transition() == ProvisionedCompanionTransition.ACTIVATE);
        assertTrue(request.destination().worldName().equals("default"));
        assertTrue(request.destination().chunkX() == 12 && request.destination().chunkZ() == -7);
    }

    @Test
    void attunementRequiresBothProfileDataCapabilities() {
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(api(
                EnumSet.of(TameworkApiCapability.PROFILE_DATA)));

        TameworkGameplayAdapter.Readiness readiness = adapter.attunementReadiness();

        assertFalse(readiness.ready());
        assertTrue(readiness.reason().contains("PROFILE_DATA_TRANSACTIONS"));

        TameworkGameplayAdapter ready = new TameworkGameplayAdapter(api(EnumSet.of(
                TameworkApiCapability.PROFILE_DATA,
                TameworkApiCapability.PROFILE_DATA_TRANSACTIONS)));
        assertTrue(ready.attunementReadiness().ready());
        assertTrue(ready.abilityStateReadiness().ready());
    }

    @Test
    void onlyTerminalDeniedProvesRepairRefundIsSafe() {
        BondedVesselOperationView terminal = operation(BondedVesselDurableOperationStatus.TERMINAL_DENIED);
        BondedVesselOperationView applied = operation(BondedVesselDurableOperationStatus.APPLIED);

        assertTrue(TameworkGameplayAdapter.provesTerminalPreApplyDenial(Optional.of(terminal)));
        assertFalse(TameworkGameplayAdapter.provesTerminalPreApplyDenial(Optional.of(applied)));
        assertFalse(TameworkGameplayAdapter.provesTerminalPreApplyDenial(Optional.empty()));
    }

    private static BondedVesselOperationView operation(BondedVesselDurableOperationStatus status) {
        return new BondedVesselOperationView(
                UUID.randomUUID(), "Alechilles:HyDragon", "repair-key", status, "reason",
                UUID.randomUUID(), UUID.randomUUID().toString(), BondedVesselTransition.REPAIR_DEAD_TO_STORED,
                1L, 2L, 4L, null, false, 10L);
    }

    private static TameworkApi api(EnumSet<TameworkApiCapability> capabilities) {
        return api(capabilities, null);
    }

    private static TameworkApi api(
            EnumSet<TameworkApiCapability> capabilities,
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
}
