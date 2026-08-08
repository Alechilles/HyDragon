package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.BondedCompanionApi;
import com.alechilles.alecstamework.api.BondedCompanionAvailability;
import com.alechilles.alecstamework.api.BondedCompanionCaptureEvidenceView;
import com.alechilles.alecstamework.api.BondedCompanionChangedEvent;
import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataUpdate;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionProvisionRequest;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionGateway;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/** Public-API-only adapter for HyDragon's mutation-authoritative Tamework operations. */
public final class TameworkGameplayAdapter implements BondedMiniwyvernExtensionGateway {
    public static final String CALLER_NAMESPACE = "Alechilles:HyDragon";
    public static final String EXTENSION_NAMESPACE = CALLER_NAMESPACE;
    public static final String WILD_MINIWYVERN_ROLE = "Tamed_Wyvern_Mini_Wild";
    /** The role family is authoritative; extension data must never select a live form. */
    public static final Set<String> MINIWYVERN_ROLE_IDS = Set.of(
            WILD_MINIWYVERN_ROLE,
            "Tamed_Wyvern_Mini_Nature",
            "Tamed_Wyvern_Mini_Toxic",
            "Tamed_Wyvern_Mini_Fire",
            "Tamed_Wyvern_Mini_Void",
            "Tamed_Wyvern_Mini_Lightning",
            "Tamed_Wyvern_Mini_Ice");
    public static final String DRAGON_HORN_ROSTER = "hydragon:dragon_horn";
    public static final String FULL_DRAGON_FAMILY = "hydragon:full_dragons";
    public static final String MINIWYVERN_FAMILY = "hydragon:soulbound_mini";

    private final TameworkApi api;

    public TameworkGameplayAdapter(@Nonnull TameworkApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public Readiness soulBondReadiness() {
        return readiness(HyDragonFeature.SOUL_BOND_CLAIM);
    }

    public Readiness abilityStateReadiness() {
        return readiness(HyDragonFeature.MINIWYVERN_ABILITIES);
    }

    public CompletionStage<BondedCompanionResult<List<BondedCompanionProfileView>>>
            listDragonHorn(UUID ownerUuid) {
        return bonded().list(
                Objects.requireNonNull(ownerUuid, "ownerUuid"),
                DRAGON_HORN_ROSTER);
    }

    /** Provisions the one-lifetime Miniwyvern directly into the shared Horn roster. */
    public CompletionStage<BondedCompanionResult<BondedCompanionProfileView>>
            provisionMiniwyvern(UUID ownerUuid, String operationId) {
        return bonded().provision(new BondedCompanionProvisionRequest(
                CALLER_NAMESPACE,
                Objects.requireNonNull(operationId, "operationId"),
                Objects.requireNonNull(ownerUuid, "ownerUuid"),
                DRAGON_HORN_ROSTER,
                WILD_MINIWYVERN_ROLE,
                null,
                "Miniwyvern",
                null,
                Map.of("source", "soul-bond"),
                MINIWYVERN_FAMILY));
    }

    @Override
    public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
            getMiniwyvernExtension(UUID ownerUuid, String profileId) {
        return bonded().getExtensionData(extensionKey(ownerUuid, profileId));
    }

    @Override
    public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
            compareAndSetMiniwyvernExtension(
                    UUID ownerUuid,
                    String profileId,
                    String idempotencyKey,
                    String jsonPayload,
                    long expectedRevision) {
        return bonded().compareAndSetExtensionData(
                new BondedCompanionExtensionDataUpdate(
                        CALLER_NAMESPACE,
                        idempotencyKey,
                        extensionKey(ownerUuid, profileId),
                        jsonPayload,
                        expectedRevision));
    }

    public CompletionStage<BondedCompanionResult<BondedCompanionCaptureEvidenceView>>
            findDragonCapture(UUID ownerUuid, UUID sourceNpcUuid) {
        return bonded().findCapture(
                Objects.requireNonNull(ownerUuid, "ownerUuid"),
                DRAGON_HORN_ROSTER,
                Objects.requireNonNull(sourceNpcUuid, "sourceNpcUuid"));
    }

    public AutoCloseable subscribeBondedChanges(
            Consumer<BondedCompanionChangedEvent> listener) {
        return bonded().subscribe(Objects.requireNonNull(listener, "listener"));
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
        if (feature.requiredCapabilities().contains(
                TameworkApiCapability.BONDED_COMPANIONS.name())) {
            try {
                BondedCompanionAvailability availability = bonded().availability();
                if (!availability.available()) {
                    return new Readiness(false, availability.reason());
                }
            } catch (RuntimeException | LinkageError failure) {
                return new Readiness(false, "Tamework bonded companion availability failed");
            }
        }
        return new Readiness(true, "ready");
    }

    private BondedCompanionExtensionDataKey extensionKey(
            UUID ownerUuid,
            String profileId) {
        return new BondedCompanionExtensionDataKey(
                Objects.requireNonNull(ownerUuid, "ownerUuid"),
                Objects.requireNonNull(profileId, "profileId"),
                EXTENSION_NAMESPACE);
    }

    private BondedCompanionApi bonded() {
        return Objects.requireNonNull(
                api.bondedCompanions(), "Tamework bondedCompanions API");
    }

    public record Readiness(boolean ready, String reason) {
        public Readiness {
            reason = Objects.requireNonNull(reason, "reason").trim();
            if (reason.isEmpty()) throw new IllegalArgumentException("reason is required");
        }
    }
}
