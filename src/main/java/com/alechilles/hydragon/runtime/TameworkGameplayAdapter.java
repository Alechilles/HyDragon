package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.BondedVesselDurableOperationStatus;
import com.alechilles.alecstamework.api.BondedVesselOperationResult;
import com.alechilles.alecstamework.api.BondedVesselOperationView;
import com.alechilles.alecstamework.api.BondedVesselTransitionRequest;
import com.alechilles.alecstamework.api.BondedVesselTransitionToken;
import com.alechilles.alecstamework.api.BondedVesselView;
import com.alechilles.alecstamework.api.CompanionProvisioningDisposition;
import com.alechilles.alecstamework.api.CompanionProvisioningOperationView;
import com.alechilles.alecstamework.api.CompanionProvisioningRequest;
import com.alechilles.alecstamework.api.CompanionProvisioningResult;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetRequest;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetResult;
import com.alechilles.alecstamework.api.ProfileDataEntryView;
import com.alechilles.alecstamework.api.ProfileDataOperationView;
import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.alecstamework.api.ProvisionedCompanionTransition;
import com.alechilles.alecstamework.api.ProvisionedCompanionTransitionRequest;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import com.alechilles.hydragon.integration.HyDragonFeature;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/** Public-API-only adapter for HyDragon's mutation-authoritative Tamework operations. */
public final class TameworkGameplayAdapter {
    public static final String CALLER_NAMESPACE = "Alechilles:HyDragon";
    public static final String SOULBOUND_MINIWYVERN_ROLE = "Tamed_Wyvern_Mini";

    private final TameworkApi api;
    private final EnumSet<TameworkApiCapability> capabilities;

    public TameworkGameplayAdapter(@Nonnull TameworkApi api) {
        this.api = Objects.requireNonNull(api, "api");
        this.capabilities = api.getCapabilities().clone();
    }

    public Readiness soulBondReadiness() {
        return readiness(HyDragonFeature.SOUL_BOND_CLAIM);
    }

    public Readiness repairReadiness() {
        return readiness(HyDragonFeature.BONDED_STONE_REPAIR);
    }

    public Readiness attunementReadiness() {
        return readiness(HyDragonFeature.MINIWYVERN_ATTUNEMENT);
    }

    public Readiness abilityStateReadiness() {
        return readiness(HyDragonFeature.MINIWYVERN_ATTUNEMENT);
    }

    public Optional<ProfileDataEntryView> findVersionedProfileData(
            String profileId,
            String namespace,
            String key) {
        return api.profileData().getVersioned(profileId, namespace, key);
    }

    public CompletionStage<ProfileDataCompareAndSetResult> compareAndSetProfileData(
            ProfileDataCompareAndSetRequest request) {
        return api.profileData().compareAndSet(request);
    }

    public CompletionStage<Optional<ProfileDataOperationView>> findProfileDataOperation(
            String namespace,
            String operationId) {
        return api.profileData().findOperation(namespace, operationId);
    }

    public CompletionStage<CompanionProvisioningResult> provisionDormantMiniwyvern(
            UUID playerUuid,
            String operationId,
            String ownershipWorldName) {
        return api.companionProvisioning().provision(new CompanionProvisioningRequest(
                CALLER_NAMESPACE,
                operationId,
                null,
                playerUuid,
                SOULBOUND_MINIWYVERN_ROLE,
                CompanionProvisioningDisposition.PROVISIONED_DORMANT,
                ownershipWorldName,
                null,
                null,
                null,
                CompanionProvisioningRequest.CURRENT_POLICY_REVISION
        ));
    }

    public CompletionStage<Optional<CompanionProvisioningOperationView>> findMiniwyvernProvisioning(
            String operationId) {
        return api.companionProvisioning().findOperation(CALLER_NAMESPACE, operationId);
    }

    public CompletionStage<CompanionProvisioningResult> activateDormantMiniwyvern(
            UUID playerUuid,
            String operationId,
            String profileId,
            long expectedProfileRevision,
            String ownershipWorldName,
            int chunkX,
            int chunkZ) {
        return api.companionProvisioning().transition(new ProvisionedCompanionTransitionRequest(
                CALLER_NAMESPACE,
                operationId,
                playerUuid,
                profileId,
                expectedProfileRevision,
                ProvisionedCompanionTransition.ACTIVATE,
                ownershipWorldName,
                new PopulationAdmissionLocation(ownershipWorldName, chunkX, chunkZ)
        ));
    }

    public CompletionStage<BondedVesselOperationResult> prepareRepair(BondedVesselTransitionRequest request) {
        return api.bondedVessels().prepareTransition(request);
    }

    public Optional<BondedVesselView> findVessel(UUID bindingId) {
        return api.bondedVessels().getByBindingId(bindingId);
    }

    public CompletionStage<BondedVesselOperationResult> resumeRepair(BondedVesselTransitionRequest request) {
        return api.bondedVessels().resumeTransition(request);
    }

    public BondedVesselOperationResult claimRepair(BondedVesselTransitionToken token) {
        return api.bondedVessels().claimForApply(token);
    }

    public CompletionStage<BondedVesselOperationResult> commitRepair(BondedVesselTransitionToken token) {
        return api.bondedVessels().commit(token);
    }

    public CompletionStage<Optional<BondedVesselOperationView>> findRepair(String operationId) {
        return api.bondedVessels().findOperation(CALLER_NAMESPACE, operationId);
    }

    public static boolean provesTerminalPreApplyDenial(Optional<BondedVesselOperationView> operation) {
        return operation.isPresent()
                && operation.orElseThrow().status() == BondedVesselDurableOperationStatus.TERMINAL_DENIED;
    }

    private Readiness readiness(HyDragonFeature feature) {
        EnumSet<TameworkApiCapability> required = EnumSet.noneOf(TameworkApiCapability.class);
        for (String capability : feature.requiredCapabilities()) {
            required.add(TameworkApiCapability.valueOf(capability));
        }
        EnumSet<TameworkApiCapability> missing = required.clone();
        missing.removeAll(capabilities);
        if (!missing.isEmpty()) return new Readiness(false, "missing Tamework capabilities " + missing);
        return new Readiness(true, "ready");
    }

    public record Readiness(boolean ready, String reason) {
        public Readiness {
            reason = Objects.requireNonNull(reason, "reason").trim();
            if (reason.isEmpty()) throw new IllegalArgumentException("reason is required");
        }
    }
}
