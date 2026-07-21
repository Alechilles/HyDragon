package com.alechilles.hydragon.persistence;

import java.util.Objects;

/** Frozen evidence for the exact source stack reserved by a consumable saga. */
public record SourceItemEvidence(
        String itemId,
        String holderEvidenceId,
        String containerPath,
        int inventorySlot,
        long inventoryRevision,
        String itemFingerprint,
        int stackQuantityAtPrepare) {
    public SourceItemEvidence {
        itemId = requiredText(itemId, "itemId");
        holderEvidenceId = requiredText(holderEvidenceId, "holderEvidenceId");
        containerPath = requiredText(containerPath, "containerPath");
        itemFingerprint = requiredText(itemFingerprint, "itemFingerprint");
        if (inventorySlot < 0) {
            throw new IllegalArgumentException("inventorySlot must not be negative");
        }
        if (inventoryRevision < 0) {
            throw new IllegalArgumentException("inventoryRevision must not be negative");
        }
        if (stackQuantityAtPrepare <= 0) {
            throw new IllegalArgumentException("stackQuantityAtPrepare must be positive");
        }
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
