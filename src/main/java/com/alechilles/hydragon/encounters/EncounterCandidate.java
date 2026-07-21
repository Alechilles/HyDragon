package com.alechilles.hydragon.encounters;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable world-thread snapshot used for side-effect-free encounter admission. */
public record EncounterCandidate(
        UUID playerUuid,
        String worldName,
        String regionKey,
        String environmentId,
        double x,
        double y,
        double z,
        Set<String> activeWeatherIds,
        Set<String> accessibleItemIds) {
    public EncounterCandidate {
        Objects.requireNonNull(playerUuid, "playerUuid");
        worldName = requiredText(worldName, "worldName");
        regionKey = requiredText(regionKey, "regionKey");
        environmentId = requiredText(environmentId, "environmentId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("candidate coordinates must be finite");
        }
        activeWeatherIds = copyStrings(activeWeatherIds, "activeWeatherIds");
        accessibleItemIds = copyStrings(accessibleItemIds, "accessibleItemIds");
    }

    private static Set<String> copyStrings(Set<String> values, String field) {
        Objects.requireNonNull(values, field);
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + " contains a blank value");
        }
        return Set.copyOf(values);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
