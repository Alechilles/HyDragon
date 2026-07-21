package com.alechilles.hydragon.persistence;

import java.util.Objects;
import java.util.UUID;

/** Minimal durable evidence needed to replay a captured full-dragon profile projection. */
public record PendingProfileProjectionRecord(
        int schemaVersion,
        UUID operationId,
        String profileId,
        String roleId,
        long recordedAtEpochMillis) {
    public static final int SCHEMA_VERSION = 1;

    public PendingProfileProjectionRecord {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported pending profile projection schema " + schemaVersion);
        }
        operationId = Objects.requireNonNull(operationId, "operationId");
        profileId = requiredText(profileId, "profileId");
        roleId = requiredText(roleId, "roleId");
        if (recordedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("recordedAtEpochMillis must not be negative");
        }
    }

    public static PendingProfileProjectionRecord captured(
            UUID operationId,
            String profileId,
            String roleId,
            long recordedAtEpochMillis) {
        return new PendingProfileProjectionRecord(
                SCHEMA_VERSION, operationId, profileId, roleId, recordedAtEpochMillis);
    }

    /** Timestamp does not participate in the idempotency identity of capture evidence. */
    public boolean matchesEvidence(PendingProfileProjectionRecord other) {
        return other != null
                && operationId.equals(other.operationId)
                && profileId.equals(other.profileId)
                && roleId.equals(other.roleId);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
