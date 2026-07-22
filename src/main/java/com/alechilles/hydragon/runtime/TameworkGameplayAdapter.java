package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.CompanionProvisioningDisposition;
import com.alechilles.alecstamework.api.CompanionProvisioningLinkRequest;
import com.alechilles.alecstamework.api.CompanionProvisioningLinkResult;
import com.alechilles.alecstamework.api.CompanionProvisioningRequest;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetRequest;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetResult;
import com.alechilles.alecstamework.api.ProfileDataEntryView;
import com.alechilles.alecstamework.api.ProfileDataOperationView;
import com.alechilles.alecstamework.api.ProvisionedCompanionView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import com.alechilles.hydragon.integration.HyDragonFeature;
import java.util.Set;
import java.util.TreeSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/** Public-API-only adapter for HyDragon's mutation-authoritative Tamework operations. */
public final class TameworkGameplayAdapter {
    public static final String CALLER_NAMESPACE = "Alechilles:HyDragon";
    public static final String SOULBOUND_MINIWYVERN_ROLE = "Tamed_Wyvern_Mini";
    public static final String DRAGON_HORN_COMMAND_FAMILY = "hydragon:dragon_horn";
    public static final String DRAGON_HORN_COMMAND_CONFIG = "HyDragonDragonHorn";
    public static final String DRAGON_HORN_ITEM_ID = "HyDragon_Dragon_Horn";
    public static final String MINIWYVERN_POPULATION_GROUP = "hydragon:soulbound_mini";

    private final TameworkApi api;

    public TameworkGameplayAdapter(@Nonnull TameworkApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public Readiness soulBondReadiness() {
        return readiness(HyDragonFeature.SOUL_BOND_CLAIM);
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

    public CompletionStage<CompanionProvisioningLinkResult> provisionAndLinkMiniwyvern(
            UUID playerUuid,
            String operationId,
            String ownershipWorldName) {
        return api.companionProvisioning().provisionAndLink(new CompanionProvisioningLinkRequest(
                new CompanionProvisioningRequest(
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
                        CompanionProvisioningRequest.CURRENT_POLICY_REVISION),
                DRAGON_HORN_COMMAND_FAMILY,
                DRAGON_HORN_COMMAND_CONFIG,
                DRAGON_HORN_ITEM_ID,
                MINIWYVERN_POPULATION_GROUP,
                true,
                true));
    }

    public Optional<ProvisionedCompanionView> findMiniwyvern(String profileId) {
        return api.companionProvisioning().getByProfileId(profileId);
    }

    private Readiness readiness(HyDragonFeature feature) {
        Set<String> capabilities = new TreeSet<>();
        try {
            for (TameworkApiCapability capability : api.getCapabilities()) {
                if (capability != null) capabilities.add(capability.name());
            }
        } catch (RuntimeException failure) {
            return new Readiness(false, "Tamework capability refresh failed");
        }
        Set<String> missing = new TreeSet<>(feature.requiredCapabilities());
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
