package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.BondedVesselDurableOperationStatus;
import com.alechilles.alecstamework.api.BondedVesselOperationResult;
import com.alechilles.alecstamework.api.BondedVesselOperationView;
import com.alechilles.alecstamework.api.BondedVesselTransitionRequest;
import com.alechilles.alecstamework.api.BondedVesselTransitionToken;
import com.alechilles.alecstamework.api.BondedVesselView;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

/** Durable Revitalizing Essence saga around Tamework's DEAD-to-STORED vessel authority. */
public final class BondedStoneRepairService {
    private final TameworkGameplayAdapter tamework;
    private final OperationJournal journal;

    public BondedStoneRepairService(TameworkGameplayAdapter tamework, OperationJournal journal) {
        this.tamework = Objects.requireNonNull(tamework, "tamework");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public CompletionStage<GameplayResult> repair(BondedVesselTransitionRequest request,
                                                   ConsumableReservation.SourceEvidence damagedStone,
                                                   ConsumableReservation essence) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(damagedStone, "damagedStone");
        Objects.requireNonNull(essence, "essence");
        if (!request.idempotencyKey().equals(essence.operationId())) {
            return release(essence, GameplayResult.denied("repair operation and essence receipt do not match"));
        }
        TameworkGameplayAdapter.Readiness readiness = tamework.repairReadiness();
        if (!readiness.ready()) return release(essence, GameplayResult.unavailable(readiness.reason()));
        if (!journal.available()) {
            return release(essence, GameplayResult.unavailable("HyDragon repair journal is unavailable"));
        }

        Optional<OperationJournal.Entry> existing = journal.find(essence.operationId());
        if (existing.isPresent()) {
            return resume(existing.orElseThrow(), request, damagedStone, essence);
        }
        return tamework.prepareRepair(request)
                .thenCompose(result -> afterPrepared(request, damagedStone, essence, result));
    }

    private CompletionStage<GameplayResult> resume(OperationJournal.Entry entry,
                                                    BondedVesselTransitionRequest request,
                                                    ConsumableReservation.SourceEvidence damagedStone,
                                                    ConsumableReservation essence) {
        return switch (entry.phase()) {
            case COMMITTED -> release(essence,
                    new GameplayResult(GameplayResult.Status.ALREADY_APPLIED, "bonded stone already repaired"));
            case REFUNDED -> release(essence, GameplayResult.denied("repair was terminally denied and refunded"));
            case QUARANTINED -> release(essence,
                    new GameplayResult(GameplayResult.Status.QUARANTINED, "repair operation is quarantined"));
            case PREPARED -> tamework.resumeRepair(request)
                    .thenCompose(result -> afterPrepared(request, damagedStone, essence, result));
            case MATERIAL_CONSUMED, REFUND_DUE -> recoverAfterMaterial(request, essence);
        };
    }

    private CompletionStage<GameplayResult> afterPrepared(BondedVesselTransitionRequest request,
                                                           ConsumableReservation.SourceEvidence damagedStone,
                                                           ConsumableReservation essence,
                                                           BondedVesselOperationResult prepared) {
        if (prepared.status() == BondedVesselOperationResult.Status.COMMITTED) {
            // A replay may observe closure after a prior material commit; never consume speculatively here.
            return recoverAfterMaterial(request, essence);
        }
        BondedVesselTransitionToken token = prepared.token();
        if (prepared.status() != BondedVesselOperationResult.Status.RESERVED || token == null) {
            return release(essence, prepared.status() == BondedVesselOperationResult.Status.DENIED
                    ? GameplayResult.denied(prepared.reason())
                    : GameplayResult.retryable(prepared.reason()));
        }

        if (journal.find(essence.operationId()).isEmpty()) {
            Optional<BondedVesselView> vessel = tamework.findVessel(request.bindingId());
            if (vessel.isEmpty() || !vessel.orElseThrow().ownerUuid().equals(request.actorUuid())) {
                return release(essence, GameplayResult.denied("bonded vessel identity or ownership is unavailable"));
            }
            BondedVesselView view = vessel.orElseThrow();
            OperationJournal.Decision begun = journal.begin(new OperationJournal.Descriptor(
                    essence.operationId(),
                    essence.operationId(),
                    OperationJournal.Kind.BONDED_STONE_REPAIR,
                    request.actorUuid(),
                    "repair_dead_to_stored",
                    essence.sourceEvidence(),
                    essence.quantity(),
                    Optional.of(damagedStone),
                    Optional.of(token.operationId().toString()),
                    Optional.of(view.profileId()),
                    Optional.of(request.bindingId()),
                    OptionalLong.of(request.expectedGeneration()),
                    OptionalLong.of(request.expectedProfileRevision())));
            if (begun != OperationJournal.Decision.APPLIED
                    && begun != OperationJournal.Decision.ALREADY_APPLIED) {
                return release(essence,
                        GameplayResult.reconciliation("repair prepared but local journal could not reserve it"));
            }
        }

        return essence.consume().thenCompose(consumed -> {
            if (consumed != ConsumableReservation.Disposition.APPLIED
                    && consumed != ConsumableReservation.Disposition.ALREADY_APPLIED) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.reconciliation("repair prepared but essence consumption is unresolved"));
            }
            OperationJournal.Decision material = journal.transition(
                    essence.operationId(), OperationJournal.Phase.PREPARED,
                    OperationJournal.Phase.MATERIAL_CONSUMED, OperationJournal.Update.EMPTY);
            if (material != OperationJournal.Decision.APPLIED
                    && material != OperationJournal.Decision.ALREADY_APPLIED) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.reconciliation("essence consumed but repair journal did not advance"));
            }
            BondedVesselOperationResult claim = tamework.claimRepair(token);
            if (claim.status() != BondedVesselOperationResult.Status.APPLYING
                    && claim.status() != BondedVesselOperationResult.Status.APPLIED) {
                return recoverAfterMaterial(request, essence);
            }
            return tamework.commitRepair(token).thenCompose(result -> afterCommit(request, essence, result));
        });
    }

    private CompletionStage<GameplayResult> afterCommit(BondedVesselTransitionRequest request,
                                                         ConsumableReservation essence,
                                                         BondedVesselOperationResult result) {
        if (result.status() == BondedVesselOperationResult.Status.COMMITTED
                || result.status() == BondedVesselOperationResult.Status.APPLIED) {
            OperationJournal.Decision closed = journal.transition(
                    essence.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                    OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
            return java.util.concurrent.CompletableFuture.completedFuture(
                    closed == OperationJournal.Decision.APPLIED || closed == OperationJournal.Decision.ALREADY_APPLIED
                            ? GameplayResult.applied("bonded stone repaired")
                            : GameplayResult.reconciliation("repair applied; local journal closure is pending"));
        }
        return recoverAfterMaterial(request, essence);
    }

    private CompletionStage<GameplayResult> recoverAfterMaterial(BondedVesselTransitionRequest request,
                                                                  ConsumableReservation essence) {
        return tamework.findRepair(essence.operationId()).thenCompose(found -> {
            if (found.isEmpty()) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.reconciliation("repair authority is indeterminate; no refund permitted"));
            }
            BondedVesselOperationView view = found.orElseThrow();
            if (view.status() == BondedVesselDurableOperationStatus.COMMITTED
                    || view.status() == BondedVesselDurableOperationStatus.APPLIED) {
                journal.transition(essence.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                        OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.applied("bonded stone repaired"));
            }
            if (view.status() != BondedVesselDurableOperationStatus.TERMINAL_DENIED) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.reconciliation("repair remains " + view.status() + "; no refund permitted"));
            }
            journal.transition(essence.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                    OperationJournal.Phase.REFUND_DUE, OperationJournal.Update.EMPTY);
            return essence.release().thenApply(refunded -> {
                if (refunded == ConsumableReservation.Disposition.APPLIED
                        || refunded == ConsumableReservation.Disposition.ALREADY_APPLIED) {
                    journal.transition(essence.operationId(), OperationJournal.Phase.REFUND_DUE,
                            OperationJournal.Phase.REFUNDED, OperationJournal.Update.EMPTY);
                    return GameplayResult.denied("repair denied; Revitalizing Essence restored");
                }
                return GameplayResult.reconciliation("repair denied; essence recovery claim is required");
            });
        });
    }

    private static CompletionStage<GameplayResult> release(ConsumableReservation essence, GameplayResult result) {
        return essence.release().handle((ignored, failure) -> result);
    }
}
