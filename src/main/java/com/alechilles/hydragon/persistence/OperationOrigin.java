package com.alechilles.hydragon.persistence;

import java.util.Objects;

/** Stable cross-plugin operation identity; correlation IDs are diagnostic only. */
public record OperationOrigin(String callerNamespace, String idempotencyKey) {
    public OperationOrigin {
        callerNamespace = requiredText(callerNamespace, "callerNamespace");
        idempotencyKey = requiredText(idempotencyKey, "idempotencyKey");
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
