package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.CompanionProvisioningResult;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;

/** Crash-recoverable once-per-player Soul Bond provisioning saga. */
public final class SoulBondService {
    private final TameworkGameplayAdapter tamework;
    private final SoulBondLedger ledger;
    private final OperationJournal journal;
    private final LongSupplier clock;

    public SoulBondService(TameworkGameplayAdapter tamework,
                           SoulBondLedger ledger,
                           OperationJournal journal,
                           LongSupplier clock) {
        this.tamework = Objects.requireNonNull(tamework, "tamework");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<GameplayResult> claim(UUID playerUuid,
                                                  String ownershipWorldName,
                                                  ConsumableReservation item) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        ownershipWorldName = requiredText(ownershipWorldName, "ownershipWorldName");
        Objects.requireNonNull(item, "item");
        TameworkGameplayAdapter.Readiness readiness = tamework.soulBondReadiness();
        if (!readiness.ready()) return released(item, GameplayResult.unavailable(readiness.reason()));
        if (!journal.available()) {
            return released(item, GameplayResult.unavailable("HyDragon durable operation journal is unavailable"));
        }

        String operationId = item.operationId();
        Optional<OperationJournal.Entry> prior = journal.find(operationId);
        if (prior.isPresent()) {
            OperationJournal.Entry entry = prior.orElseThrow();
            if (entry.kind() != OperationJournal.Kind.SOUL_BOND
                    || !entry.descriptor().ownerUuid().equals(playerUuid)
                    || !entry.descriptor().source().itemFingerprint()
                    .equals(item.sourceEvidence().itemFingerprint())) {
                return released(item, GameplayResult.reconciliation(
                        "Soul Bond operation identity conflicts with durable evidence"));
            }
            if (entry.phase() == OperationJournal.Phase.COMMITTED) {
                return released(item, GameplayResult.denied("Soul Bond entitlement already consumed"));
            }
            if (entry.phase() == OperationJournal.Phase.MATERIAL_CONSUMED) {
                OperationJournal.Decision closed = journal.transition(
                        operationId, OperationJournal.Phase.MATERIAL_CONSUMED,
                        OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
                return released(item,
                        closed == OperationJournal.Decision.APPLIED
                                || closed == OperationJournal.Decision.ALREADY_APPLIED
                                ? new GameplayResult(GameplayResult.Status.ALREADY_APPLIED, "Soul Bond claimed")
                                : GameplayResult.reconciliation("Soul Bond journal closure is pending"));
            }
            if (entry.phase() != OperationJournal.Phase.PREPARED) {
                return released(item, GameplayResult.reconciliation(
                        "Soul Bond operation requires operator reconciliation"));
            }
        }

        SoulBondLedger.Reservation reservation = ledger.reserve(playerUuid, operationId);
        if (reservation == SoulBondLedger.Reservation.CONFLICT) {
            return released(item, GameplayResult.denied("Soul Bond already claimed or reserved"));
        }
        if (reservation == SoulBondLedger.Reservation.QUARANTINED) {
            return released(item, new GameplayResult(GameplayResult.Status.QUARANTINED,
                    "Soul Bond entitlement is quarantined"));
        }
        if (reservation == SoulBondLedger.Reservation.UNAVAILABLE) {
            return released(item, GameplayResult.unavailable("Soul Bond entitlement store is unavailable"));
        }

        OperationJournal.Decision begun = prior.isPresent()
                ? OperationJournal.Decision.ALREADY_APPLIED
                : journal.begin(new OperationJournal.Descriptor(
                operationId,
                operationId,
                OperationJournal.Kind.SOUL_BOND,
                playerUuid,
                "soul_bond",
                item.sourceEvidence(),
                item.quantity(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                java.util.OptionalLong.empty(),
                java.util.OptionalLong.empty()));
        if (begun == OperationJournal.Decision.CONFLICT || begun == OperationJournal.Decision.QUARANTINED) {
            ledger.reconcile(playerUuid, operationId, Optional.empty());
            return released(item, GameplayResult.reconciliation("Soul Bond journal conflict requires reconciliation"));
        }
        if (begun == OperationJournal.Decision.UNAVAILABLE) {
            ledger.reconcile(playerUuid, operationId, Optional.empty());
            return released(item, GameplayResult.reconciliation("Soul Bond journal write failed"));
        }

        final String world = ownershipWorldName;
        return tamework.provisionDormantMiniwyvern(playerUuid, operationId, world)
                .handle((result, failure) -> failure == null ? result : null)
                .thenCompose(result -> result == null
                        ? keepPending(playerUuid, operationId, Optional.empty(), item,
                        "Tamework provisioning result is unknown")
                        : afterProvision(playerUuid, operationId, item, result));
    }

    private CompletionStage<GameplayResult> afterProvision(UUID playerUuid,
                                                            String operationId,
                                                            ConsumableReservation item,
                                                            CompanionProvisioningResult result) {
        if (!result.accepted() || result.profileId() == null) {
            return released(item, result.status() == CompanionProvisioningResult.Status.DENIED
                    ? GameplayResult.denied(result.reason())
                    : GameplayResult.retryable(result.reason()));
        }
        UUID profileId;
        try {
            profileId = UUID.fromString(result.profileId());
        } catch (IllegalArgumentException invalidProfileId) {
            return keepPending(playerUuid, operationId, Optional.empty(), item,
                    "Tamework returned a non-UUID profile identity");
        }

        SoulBondLedger.Reservation linked = ledger.complete(
                playerUuid, operationId, profileId, Math.max(0L, clock.getAsLong()));
        if (linked != SoulBondLedger.Reservation.APPLIED
                && linked != SoulBondLedger.Reservation.ALREADY_APPLIED) {
            return keepPending(playerUuid, operationId, Optional.of(profileId), item,
                    "Miniwyvern exists but HyDragon could not durably link it");
        }
        return item.consume().thenApply(consumed -> {
            if (consumed != ConsumableReservation.Disposition.APPLIED
                    && consumed != ConsumableReservation.Disposition.ALREADY_APPLIED) {
                ledger.reconcile(playerUuid, operationId, Optional.of(profileId));
                return GameplayResult.reconciliation("Miniwyvern linked; Soul Bond consumption requires reconciliation");
            }
            OperationJournal.Decision material = journal.transition(
                    operationId, OperationJournal.Phase.PREPARED,
                    OperationJournal.Phase.MATERIAL_CONSUMED,
                    new OperationJournal.Update(
                            Optional.of(result.operationId().toString()),
                            Optional.of(profileId.toString()),
                            java.util.OptionalLong.empty(),
                            Optional.empty()));
            if (material == OperationJournal.Decision.CONFLICT || material == OperationJournal.Decision.UNAVAILABLE) {
                ledger.reconcile(playerUuid, operationId, Optional.of(profileId));
                return GameplayResult.reconciliation("Soul Bond consumed; journal closure requires reconciliation");
            }
            OperationJournal.Decision closed = journal.transition(
                    operationId, OperationJournal.Phase.MATERIAL_CONSUMED,
                    OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
            return closed == OperationJournal.Decision.APPLIED || closed == OperationJournal.Decision.ALREADY_APPLIED
                    ? GameplayResult.applied("Soul Bond claimed")
                    : GameplayResult.reconciliation("Soul Bond succeeded; final journal closure is pending");
        });
    }

    private CompletionStage<GameplayResult> keepPending(UUID playerUuid,
                                                         String operationId,
                                                         Optional<UUID> profileId,
                                                         ConsumableReservation item,
                                                         String reason) {
        ledger.reconcile(playerUuid, operationId, profileId);
        return released(item, GameplayResult.reconciliation(reason));
    }

    private static CompletionStage<GameplayResult> released(ConsumableReservation item, GameplayResult result) {
        return item.release().handle((ignored, failure) -> result);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

}
