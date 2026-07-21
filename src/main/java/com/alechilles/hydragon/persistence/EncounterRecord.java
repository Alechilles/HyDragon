package com.alechilles.hydragon.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable persisted state for one plugin-managed dragon encounter. */
public record EncounterRecord(
        int schemaVersion,
        String encounterId,
        String definitionId,
        String worldName,
        String regionKey,
        String phase,
        Optional<UUID> targetNpcUuid,
        Set<UUID> eligiblePlayerUuids,
        long createdAtEpochMillis,
        long phaseStartedAtEpochMillis,
        long updatedAtEpochMillis,
        long cooldownUntilEpochMillis) {
    public static final int SCHEMA_VERSION = 1;

    public EncounterRecord {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported encounter schema " + schemaVersion);
        }
        encounterId = requiredText(encounterId, "encounterId");
        definitionId = requiredText(definitionId, "definitionId");
        worldName = requiredText(worldName, "worldName");
        regionKey = requiredText(regionKey, "regionKey");
        phase = requiredText(phase, "phase");
        targetNpcUuid = Objects.requireNonNull(targetNpcUuid, "targetNpcUuid");
        Objects.requireNonNull(eligiblePlayerUuids, "eligiblePlayerUuids");
        if (eligiblePlayerUuids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("eligiblePlayerUuids cannot contain null");
        }
        eligiblePlayerUuids = Set.copyOf(eligiblePlayerUuids);
        requireNonNegative(createdAtEpochMillis, "createdAtEpochMillis");
        requireNonNegative(phaseStartedAtEpochMillis, "phaseStartedAtEpochMillis");
        requireNonNegative(updatedAtEpochMillis, "updatedAtEpochMillis");
        requireNonNegative(cooldownUntilEpochMillis, "cooldownUntilEpochMillis");
        if (phaseStartedAtEpochMillis < createdAtEpochMillis) {
            throw new IllegalArgumentException("phaseStartedAtEpochMillis cannot precede creation");
        }
        if (updatedAtEpochMillis < phaseStartedAtEpochMillis) {
            throw new IllegalArgumentException("updatedAtEpochMillis cannot precede the current phase");
        }
    }

    private static String requiredText(String value, String field) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return text;
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
