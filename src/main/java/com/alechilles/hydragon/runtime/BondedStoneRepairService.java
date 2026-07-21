package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.BondedVesselDurableOperationStatus;
import com.alechilles.alecstamework.api.BondedVesselOperationResult;
import com.alechilles.alecstamework.api.BondedVesselOperationView;
import com.alechilles.alecstamework.api.BondedVesselTransitionRequest;
import com.alechilles.alecstamework.api.BondedVesselTransition;
import com.alechilles.alecstamework.api.BondedVesselTransitionContext;
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
            case MATERIAL_CONSUMED -> recover(entry);
            case REFUND_DUE -> release(essence, GameplayResult.reconciliation(
                    "repair was denied after consumption; durable essence recovery is pending"));
        };
    }

    /** Restarts a consumed repair using only durable journal evidence. */
    public CompletionStage<GameplayResult> recover(OperationJournal.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.kind() != OperationJournal.Kind.BONDED_STONE_REPAIR) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    GameplayResult.denied("not a bonded-stone repair operation"));
        }
        if (entry.phase() == OperationJournal.Phase.COMMITTED) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new GameplayResult(GameplayResult.Status.ALREADY_APPLIED, "bonded stone already repaired"));
        }
        if (entry.phase() == OperationJournal.Phase.REFUND_DUE
                || entry.phase() == OperationJournal.Phase.REFUNDED) {
            return java.util.concurrent.CompletableFuture.completedFuture(GameplayResult.reconciliation(
                    "repair terminally denied; durable essence recovery is pending"));
        }
        if (entry.phase() != OperationJournal.Phase.MATERIAL_CONSUMED) {
            return java.util.concurrent.CompletableFuture.completedFuture(GameplayResult.reconciliation(
                    "repair is not in a consumed recovery phase"));
        }
        BondedVesselTransitionRequest request = recoveryRequest(entry);
        if (request == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(GameplayResult.reconciliation(
                    "repair journal evidence is incomplete"));
        }
        return tamework.resumeRepair(request).thenCompose(result -> {
            if (result.status() == BondedVesselOperationResult.Status.COMMITTED) {
                return closeRecovered(entry.operationId());
            }
            BondedVesselTransitionToken token = result.token();
            if ((result.status() == BondedVesselOperationResult.Status.RESERVED
                    || result.status() == BondedVesselOperationResult.Status.APPLYING
                    || result.status() == BondedVesselOperationResult.Status.APPLIED)
                    && token != null) {
                BondedVesselOperationResult claimed = tamework.claimRepair(token);
                if (claimed.status() == BondedVesselOperationResult.Status.APPLYING
                        || claimed.status() == BondedVesselOperationResult.Status.APPLIED) {
                    return tamework.commitRepair(token).thenCompose(committed ->
                            committed.status() == BondedVesselOperationResult.Status.COMMITTED
                                    ? closeRecovered(entry.operationId())
                                    : recoverAfterMaterial(entry.operationId()));
                }
            }
            return recoverAfterMaterial(entry.operationId());
        });
    }

    private CompletionStage<GameplayResult> afterPrepared(BondedVesselTransitionRequest request,
                                                           ConsumableReservation.SourceEvidence damagedStone,
                                                           ConsumableReservation essence,
                                                           BondedVesselOperationResult prepared) {
        if (prepared.status() == BondedVesselOperationResult.Status.COMMITTED) {
            // A replay may observe closure after a prior material commit; never consume speculatively here.
            return recoverAfterMaterial(essence.operationId());
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
                return recoverAfterMaterial(essence.operationId());
            }
            return tamework.commitRepair(token).thenCompose(result -> afterCommit(request, essence, result));
        });
    }

    private CompletionStage<GameplayResult> afterCommit(BondedVesselTransitionRequest request,
                                                         ConsumableReservation essence,
                                                         BondedVesselOperationResult result) {
        if (result.status() == BondedVesselOperationResult.Status.COMMITTED) {
            OperationJournal.Decision closed = journal.transition(
                    essence.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                    OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
            return java.util.concurrent.CompletableFuture.completedFuture(
                    closed == OperationJournal.Decision.APPLIED || closed == OperationJournal.Decision.ALREADY_APPLIED
                            ? GameplayResult.applied("bonded stone repaired")
                            : GameplayResult.reconciliation("repair applied; local journal closure is pending"));
        }
        if (result.status() == BondedVesselOperationResult.Status.APPLIED) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    GameplayResult.reconciliation("repair applied; Tamework source closure is still pending"));
        }
        return recoverAfterMaterial(essence.operationId());
    }

    private CompletionStage<GameplayResult> recoverAfterMaterial(String operationId) {
        return tamework.findRepair(operationId).thenCompose(found -> {
            if (found.isEmpty()) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.reconciliation("repair authority is indeterminate; no refund permitted"));
            }
            BondedVesselOperationView view = found.orElseThrow();
            if (view.status() == BondedVesselDurableOperationStatus.COMMITTED) {
                journal.transition(operationId, OperationJournal.Phase.MATERIAL_CONSUMED,
                        OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.applied("bonded stone repaired"));
            }
            if (view.status() == BondedVesselDurableOperationStatus.APPLIED) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.reconciliation("repair applied; Tamework source closure is still pending"));
            }
            if (view.status() != BondedVesselDurableOperationStatus.TERMINAL_DENIED) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        GameplayResult.reconciliation("repair remains " + view.status() + "; no refund permitted"));
            }
            journal.transition(operationId, OperationJournal.Phase.MATERIAL_CONSUMED,
                    OperationJournal.Phase.REFUND_DUE, OperationJournal.Update.EMPTY);
            return java.util.concurrent.CompletableFuture.completedFuture(
                    GameplayResult.reconciliation(
                            "repair denied after consumption; durable Revitalizing Essence recovery is pending"));
        });
    }

    private CompletionStage<GameplayResult> closeRecovered(String operationId) {
        OperationJournal.Decision closed = journal.transition(
                operationId, OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
        return java.util.concurrent.CompletableFuture.completedFuture(
                closed == OperationJournal.Decision.APPLIED
                        || closed == OperationJournal.Decision.ALREADY_APPLIED
                        ? GameplayResult.applied("bonded stone repaired")
                        : GameplayResult.reconciliation("repair applied; local journal closure is pending"));
    }

    private static BondedVesselTransitionRequest recoveryRequest(OperationJournal.Entry entry) {
        OperationJournal.Descriptor descriptor = entry.descriptor();
        if (descriptor.authoritySource().isEmpty()
                || descriptor.bindingId().isEmpty()
                || descriptor.bindingGeneration().isEmpty()
                || descriptor.profileRevision().isEmpty()) {
            return null;
        }
        ConsumableReservation.SourceEvidence source = descriptor.authoritySource().orElseThrow();
        try {
            return new BondedVesselTransitionRequest(
                    TameworkGameplayAdapter.CALLER_NAMESPACE,
                    entry.operationId(),
                    descriptor.ownerUuid(),
                    descriptor.bindingId().orElseThrow(),
                    descriptor.bindingGeneration().getAsLong(),
                    descriptor.profileRevision().getAsLong(),
                    BondedVesselTransition.REPAIR_DEAD_TO_STORED,
                    new BondedVesselTransitionContext(
                            source.itemId(), source.holderEvidenceId(), source.containerPath(),
                            source.inventorySlot(), source.inventoryRevision(), source.itemFingerprint(),
                            null, null));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static CompletionStage<GameplayResult> release(ConsumableReservation essence, GameplayResult result) {
        return essence.release().handle((ignored, failure) -> result);
    }
}
