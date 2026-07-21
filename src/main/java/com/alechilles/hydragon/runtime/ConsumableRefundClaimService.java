package com.alechilles.hydragon.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Owner-scoped, receipt-idempotent delivery of consumables proven safe to refund. */
public final class ConsumableRefundClaimService {
    private final OperationJournal journal;

    public ConsumableRefundClaimService(OperationJournal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public List<Claim> claims(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return journal.entries().stream()
                .filter(entry -> entry.phase() == OperationJournal.Phase.REFUND_DUE)
                .filter(entry -> entry.descriptor().ownerUuid().equals(ownerUuid))
                .map(ConsumableRefundClaimService::claim)
                .sorted(java.util.Comparator.comparing(Claim::operationId))
                .toList();
    }

    public CompletionStage<GameplayResult> claim(
            UUID ownerUuid, String operationId, RefundDelivery delivery) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        String normalizedOperationId = required(operationId, "operationId");
        Objects.requireNonNull(delivery, "delivery");
        if (!journal.available()) {
            return completed(GameplayResult.unavailable("HyDragon refund journal is unavailable"));
        }
        Optional<OperationJournal.Entry> found = journal.find(normalizedOperationId);
        if (found.isEmpty()) return completed(GameplayResult.denied("refund claim was not found"));
        OperationJournal.Entry entry = found.orElseThrow();
        if (entry.kind() != OperationJournal.Kind.BONDED_STONE_REPAIR
                || !entry.descriptor().ownerUuid().equals(ownerUuid)) {
            return completed(GameplayResult.denied("refund claim does not belong to this player"));
        }
        if (entry.phase() == OperationJournal.Phase.REFUNDED) {
            return completed(new GameplayResult(
                    GameplayResult.Status.ALREADY_APPLIED, "Revitalizing Essence already recovered"));
        }
        if (entry.phase() != OperationJournal.Phase.REFUND_DUE) {
            return completed(GameplayResult.denied("repair is not eligible for a refund claim"));
        }

        Claim claim = claim(entry);
        final CompletionStage<RefundDelivery.Decision> attempted;
        try {
            attempted = delivery.deliver(claim);
        } catch (RuntimeException failure) {
            return completed(GameplayResult.retryable("refund inventory delivery is unavailable"));
        }
        if (attempted == null) {
            return completed(GameplayResult.retryable("refund inventory delivery is unavailable"));
        }
        return attempted.handle((decision, failure) -> failure == null ? decision : null)
                .thenApply(decision -> {
                    if (decision != RefundDelivery.Decision.APPLIED
                            && decision != RefundDelivery.Decision.ALREADY_APPLIED) {
                        return decision == RefundDelivery.Decision.NO_SPACE
                                ? GameplayResult.denied("make inventory space before claiming the refund")
                                : GameplayResult.retryable("refund delivery remains pending");
                    }
                    OperationJournal.Decision closed = journal.transition(
                            normalizedOperationId, OperationJournal.Phase.REFUND_DUE,
                            OperationJournal.Phase.REFUNDED, OperationJournal.Update.EMPTY);
                    return closed == OperationJournal.Decision.APPLIED
                            || closed == OperationJournal.Decision.ALREADY_APPLIED
                            ? GameplayResult.applied("Revitalizing Essence recovered")
                            : GameplayResult.reconciliation(
                            "refund was delivered; durable claim closure remains pending");
                });
    }

    private static Claim claim(OperationJournal.Entry entry) {
        OperationJournal.Descriptor descriptor = entry.descriptor();
        return new Claim(entry.operationId(), descriptor.ownerUuid(),
                descriptor.source().itemId(), descriptor.materialQuantity());
    }

    private static CompletionStage<GameplayResult> completed(GameplayResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    public record Claim(String operationId, UUID ownerUuid, String itemId, int quantity) {
        public Claim {
            operationId = required(operationId, "operationId");
            ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
            itemId = required(itemId, "itemId");
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        }
    }

    @FunctionalInterface
    public interface RefundDelivery {
        CompletionStage<Decision> deliver(Claim claim);

        enum Decision { APPLIED, ALREADY_APPLIED, NO_SPACE, UNAVAILABLE, AMBIGUOUS }
    }
}
