package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.BondedVesselHeldItemLocatorRequest;
import com.alechilles.alecstamework.api.BondedVesselHeldItemLocatorResult;
import com.alechilles.alecstamework.api.BondedVesselHeldItemProjectionStatus;
import com.alechilles.alecstamework.api.BondedVesselSourceItemEvidence;
import com.alechilles.alecstamework.api.BondedVesselState;
import com.alechilles.alecstamework.api.BondedVesselTransition;
import com.alechilles.alecstamework.api.BondedVesselTransitionContext;
import com.alechilles.alecstamework.api.BondedVesselTransitionRequest;
import com.alechilles.alecstamework.api.BondedVesselView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.interactions.HyDragonInteractionRuntime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Resolves a held damaged stone entirely through Tamework's public mutation authority. */
public final class TameworkBondedRepairRequestResolver
        implements HyDragonGameplayRuntime.BondedRepairRequestResolver {
    private final TameworkApi api;

    public TameworkBondedRepairRequestResolver(TameworkApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public CompletionStage<HyDragonGameplayRuntime.Resolution> resolve(
            UUID playerUuid,
            String worldName,
            String operationId,
            HyDragonInteractionRuntime.HeldItemLocator locator) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        required(worldName, "worldName");
        operationId = required(operationId, "operationId");
        Objects.requireNonNull(locator, "locator");
        BondedVesselHeldItemLocatorRequest lookup = new BondedVesselHeldItemLocatorRequest(
                playerUuid,
                locator.holderEvidenceId(),
                locator.containerPath(),
                locator.inventorySlot(),
                locator.expectedItemId(),
                BondedVesselState.DEAD);
        final CompletionStage<BondedVesselHeldItemLocatorResult> stage;
        try {
            stage = api.bondedVessels().resolveHeldItemLocator(lookup);
        } catch (RuntimeException | LinkageError failure) {
            return completed("bonded vessel locator dispatch failed");
        }
        if (stage == null) return completed("bonded vessel locator did not start");
        final String stableOperationId = operationId;
        return stage.handle((result, failure) -> failure == null ? result : null)
                .thenApply(result -> toResolution(playerUuid, stableOperationId, result));
    }

    private static HyDragonGameplayRuntime.Resolution toResolution(
            UUID playerUuid,
            String operationId,
            BondedVesselHeldItemLocatorResult result) {
        if (result == null) return unresolved("bonded vessel locator result is unavailable");
        if (result.status() != BondedVesselHeldItemProjectionStatus.VALID || !result.authoritative()
                || result.resolvedSourceEvidence().isEmpty() || result.resolvedVessel().isEmpty()) {
            return unresolved(result.reason());
        }
        BondedVesselSourceItemEvidence source = result.resolvedSourceEvidence().orElseThrow();
        BondedVesselView vessel = result.resolvedVessel().orElseThrow();
        if (!playerUuid.equals(vessel.ownerUuid()) || vessel.state() != BondedVesselState.DEAD) {
            return unresolved("bonded vessel owner or damaged state changed");
        }
        BondedVesselTransitionContext context = new BondedVesselTransitionContext(
                source.itemId(),
                source.holderEvidenceId(),
                source.containerPath(),
                source.inventorySlot(),
                source.inventoryRevision(),
                source.itemFingerprint(),
                null,
                null);
        BondedVesselTransitionRequest request = new BondedVesselTransitionRequest(
                TameworkGameplayAdapter.CALLER_NAMESPACE,
                operationId,
                playerUuid,
                vessel.bindingId(),
                vessel.generation(),
                vessel.profileRevision(),
                BondedVesselTransition.REPAIR_DEAD_TO_STORED,
                context);
        ConsumableReservation.SourceEvidence damagedStone = new ConsumableReservation.SourceEvidence(
                source.itemId(), source.holderEvidenceId(), source.containerPath(), source.inventorySlot(),
                source.inventoryRevision(), source.itemFingerprint(), 1);
        return new HyDragonGameplayRuntime.Resolution(
                Optional.of(request), Optional.of(damagedStone), "ready");
    }

    private static CompletionStage<HyDragonGameplayRuntime.Resolution> completed(String reason) {
        return CompletableFuture.completedFuture(unresolved(reason));
    }

    private static HyDragonGameplayRuntime.Resolution unresolved(String reason) {
        return new HyDragonGameplayRuntime.Resolution(Optional.empty(), Optional.empty(), required(reason, "reason"));
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
