package com.alechilles.hydragon.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable HyDragon metadata associated with a Tamework companion profile. */
public record ProfileExtensionRecord(
        int schemaVersion,
        UUID profileId,
        ProfileKind kind,
        String speciesId,
        Optional<String> lastOperationId) {
    public static final int SCHEMA_VERSION = 1;

    public ProfileExtensionRecord {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported profile extension schema " + schemaVersion);
        }
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(kind, "kind");
        speciesId = requiredText(speciesId, "speciesId");
        lastOperationId = normalizedText(lastOperationId, "lastOperationId");
    }

    public static ProfileExtensionRecord fullDragon(
            UUID profileId,
            String speciesId,
            Optional<String> lastOperationId) {
        return new ProfileExtensionRecord(
                SCHEMA_VERSION,
                profileId,
                ProfileKind.FULL_DRAGON,
                speciesId, lastOperationId);
    }

    public static ProfileExtensionRecord soulboundMiniwyvern(
            UUID profileId,
            String speciesId,
            Optional<String> lastOperationId) {
        return new ProfileExtensionRecord(
                SCHEMA_VERSION,
                profileId,
                ProfileKind.SOULBOUND_MINIWYVERN,
                speciesId, lastOperationId);
    }

    private static String requiredText(String value, String field) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return text;
    }

    private static Optional<String> normalizedText(Optional<String> value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isEmpty()) {
            return value;
        }
        return Optional.of(requiredText(value.orElseThrow(), field));
    }
}
