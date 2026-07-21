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
        Optional<String> archetypeId,
        Optional<String> lastOperationId) {
    public static final int SCHEMA_VERSION = 1;

    public ProfileExtensionRecord {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported profile extension schema " + schemaVersion);
        }
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(kind, "kind");
        speciesId = requiredText(speciesId, "speciesId");
        archetypeId = normalizedText(archetypeId, "archetypeId");
        lastOperationId = normalizedText(lastOperationId, "lastOperationId");

        if (kind == ProfileKind.FULL_DRAGON && archetypeId.isPresent()) {
            throw new IllegalArgumentException("FULL_DRAGON profiles cannot carry a miniwyvern archetype");
        }
        if (kind == ProfileKind.SOULBOUND_MINIWYVERN && archetypeId.isEmpty()) {
            throw new IllegalArgumentException("SOULBOUND_MINIWYVERN profiles require an archetypeId");
        }
    }

    public static ProfileExtensionRecord fullDragon(
            UUID profileId,
            String speciesId,
            Optional<String> lastOperationId) {
        return new ProfileExtensionRecord(
                SCHEMA_VERSION,
                profileId,
                ProfileKind.FULL_DRAGON,
                speciesId,
                Optional.empty(),
                lastOperationId);
    }

    public static ProfileExtensionRecord soulboundMiniwyvern(
            UUID profileId,
            String speciesId,
            String archetypeId,
            Optional<String> lastOperationId) {
        return new ProfileExtensionRecord(
                SCHEMA_VERSION,
                profileId,
                ProfileKind.SOULBOUND_MINIWYVERN,
                speciesId,
                Optional.of(archetypeId),
                lastOperationId);
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
