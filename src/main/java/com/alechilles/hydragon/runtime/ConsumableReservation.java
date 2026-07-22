package com.alechilles.hydragon.runtime;

import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/** Exact, receipt-backed reservation for one interaction input. */
public interface ConsumableReservation {
    @Nonnull String operationId();

    @Nonnull SourceEvidence sourceEvidence();

    int quantity();

    @Nonnull CompletionStage<Disposition> consume();

    @Nonnull CompletionStage<Disposition> release();

    /** Result is deliberately proof-oriented: UNKNOWN never authorizes a second mutation or refund. */
    enum Disposition {
        APPLIED,
        ALREADY_APPLIED,
        CONFLICT,
        UNAVAILABLE,
        UNKNOWN
    }

    record SourceEvidence(String itemId,
                          String holderEvidenceId,
                          String containerPath,
                          int inventorySlot,
                          long inventoryRevision,
                          String itemFingerprint,
                          int stackQuantityAtPrepare) {
        public SourceEvidence {
            itemId = requireText(itemId, "itemId");
            holderEvidenceId = requireText(holderEvidenceId, "holderEvidenceId");
            containerPath = requireText(containerPath, "containerPath");
            itemFingerprint = requireText(itemFingerprint, "itemFingerprint");
            if (inventorySlot < 0 || inventoryRevision < 0 || stackQuantityAtPrepare <= 0) {
                throw new IllegalArgumentException("invalid consumable source evidence");
            }
        }

        private static String requireText(String value, String field) {
            String normalized = java.util.Objects.requireNonNull(value, field).trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
            return normalized;
        }
    }
}
