package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.BondedVesselTransitionRequest;
import com.alechilles.hydragon.interactions.HyDragonInteractionRuntime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Production composition of interaction commands and domain sagas. */
public final class HyDragonGameplayRuntime implements HyDragonInteractionRuntime.Handler {
    private final SoulBondService soulBonds;
    private final MiniwyvernAttunementService attunements;
    private final BondedStoneRepairService repairs;
    private final BondedRepairRequestResolver repairRequests;

    public HyDragonGameplayRuntime(SoulBondService soulBonds,
                                   MiniwyvernAttunementService attunements,
                                   BondedStoneRepairService repairs,
                                   BondedRepairRequestResolver repairRequests) {
        this.soulBonds = Objects.requireNonNull(soulBonds, "soulBonds");
        this.attunements = Objects.requireNonNull(attunements, "attunements");
        this.repairs = Objects.requireNonNull(repairs, "repairs");
        this.repairRequests = Objects.requireNonNull(repairRequests, "repairRequests");
    }

    @Override
    public CompletionStage<GameplayResult> soulBond(
            UUID playerUuid, String worldName, ConsumableReservation reservation) {
        return soulBonds.claim(playerUuid, worldName, reservation);
    }

    @Override
    public CompletionStage<GameplayResult> attune(
            UUID playerUuid, String archetypeId, ConsumableReservation reservation) {
        return attunements.attune(playerUuid, archetypeId, reservation);
    }

    @Override
    public CompletionStage<GameplayResult> repair(
            UUID playerUuid, String worldName, ConsumableReservation reservation) {
        Resolution resolution = repairRequests.resolve(playerUuid, worldName, reservation.operationId());
        if (resolution.request().isEmpty() || resolution.damagedStone().isEmpty()) {
            return reservation.release().handle((ignored, failure) -> GameplayResult.unavailable(resolution.reason()));
        }
        return repairs.repair(
                resolution.request().orElseThrow(), resolution.damagedStone().orElseThrow(), reservation);
    }

    /**
     * API 0.9 has no public source-item-to-binding resolver. A production implementation must be supplied
     * when Tamework exposes resolveTransitionSource(holder/container/slot/fingerprint).
     */
    public interface BondedRepairRequestResolver {
        Resolution resolve(UUID playerUuid, String worldName, String operationId);

        static BondedRepairRequestResolver unavailable() {
            return (playerUuid, worldName, operationId) -> new Resolution(
                    Optional.empty(), Optional.empty(),
                    "Tamework BondedVesselsApi requires resolveTransitionSource("
                            + "actorUuid, holderEvidenceId, containerPath, slot, itemFingerprint)");
        }
    }

    public record Resolution(Optional<BondedVesselTransitionRequest> request,
                             Optional<ConsumableReservation.SourceEvidence> damagedStone,
                             String reason) {
        public Resolution {
            request = Objects.requireNonNull(request, "request");
            damagedStone = Objects.requireNonNull(damagedStone, "damagedStone");
            reason = Objects.requireNonNull(reason, "reason").trim();
            if (reason.isEmpty()) throw new IllegalArgumentException("reason is required");
        }
    }
}
