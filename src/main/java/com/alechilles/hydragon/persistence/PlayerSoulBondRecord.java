package com.alechilles.hydragon.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Immutable persisted Soul Bond entitlement record keyed by a stable player UUID. */
public record PlayerSoulBondRecord(
        int schemaVersion,
        UUID playerUuid,
        SoulBondState state,
        Optional<String> operationId,
        Optional<UUID> profileId,
        OptionalLong claimedAtEpochMillis) {
    public static final int SCHEMA_VERSION = 1;

    public PlayerSoulBondRecord {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported player Soul Bond schema " + schemaVersion);
        }
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(state, "state");
        operationId = normalizedText(operationId, "operationId");
        profileId = Objects.requireNonNull(profileId, "profileId");
        claimedAtEpochMillis = Objects.requireNonNull(claimedAtEpochMillis, "claimedAtEpochMillis");
        if (claimedAtEpochMillis.isPresent() && claimedAtEpochMillis.getAsLong() < 0) {
            throw new IllegalArgumentException("claimedAtEpochMillis must not be negative");
        }

        switch (state) {
            case UNCLAIMED -> {
                if (operationId.isPresent() || profileId.isPresent() || claimedAtEpochMillis.isPresent()) {
                    throw new IllegalArgumentException("UNCLAIMED records cannot carry claim data");
                }
            }
            case PENDING -> require(operationId.isPresent(), "PENDING records require an operationId");
            case CLAIMED -> {
                require(operationId.isPresent(), "CLAIMED records require an operationId");
                require(profileId.isPresent(), "CLAIMED records require a profileId");
                require(claimedAtEpochMillis.isPresent(), "CLAIMED records require claimedAtEpochMillis");
            }
            case NEEDS_RECONCILIATION ->
                    require(operationId.isPresent(), "NEEDS_RECONCILIATION records require an operationId");
        }
    }

    public static PlayerSoulBondRecord unclaimed(UUID playerUuid) {
        return new PlayerSoulBondRecord(
                SCHEMA_VERSION,
                playerUuid,
                SoulBondState.UNCLAIMED,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty());
    }

    public static PlayerSoulBondRecord pending(UUID playerUuid, String operationId) {
        return new PlayerSoulBondRecord(
                SCHEMA_VERSION,
                playerUuid,
                SoulBondState.PENDING,
                Optional.of(operationId),
                Optional.empty(),
                OptionalLong.empty());
    }

    public static PlayerSoulBondRecord claimed(
            UUID playerUuid,
            String operationId,
            UUID profileId,
            long claimedAtEpochMillis) {
        return new PlayerSoulBondRecord(
                SCHEMA_VERSION,
                playerUuid,
                SoulBondState.CLAIMED,
                Optional.of(operationId),
                Optional.of(profileId),
                OptionalLong.of(claimedAtEpochMillis));
    }

    public static PlayerSoulBondRecord needsReconciliation(
            UUID playerUuid,
            String operationId,
            Optional<UUID> profileId,
            OptionalLong claimedAtEpochMillis) {
        return new PlayerSoulBondRecord(
                SCHEMA_VERSION,
                playerUuid,
                SoulBondState.NEEDS_RECONCILIATION,
                Optional.of(operationId),
                profileId,
                claimedAtEpochMillis);
    }

    private static Optional<String> normalizedText(Optional<String> value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isEmpty()) {
            return value;
        }
        String text = value.orElseThrow().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return Optional.of(text);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
