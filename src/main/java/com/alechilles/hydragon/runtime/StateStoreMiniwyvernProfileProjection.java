package com.alechilles.hydragon.runtime;

import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.MutationOutcome;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import com.alechilles.hydragon.persistence.ProfileKind;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Commits the Tamework-authoritative attunement into HyDragon's local runtime projection. */
public final class StateStoreMiniwyvernProfileProjection
        implements MiniwyvernAttunementService.ProfileProjection {
    private final HyDragonStateStore store;

    public StateStoreMiniwyvernProfileProjection(HyDragonStateStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Decision synchronize(UUID profileId, String archetypeId, String operationId) {
        Objects.requireNonNull(profileId, "profileId");
        archetypeId = required(archetypeId, "archetypeId");
        operationId = required(operationId, "operationId");
        ProfileExtensionRecord current = store.snapshot().profileExtension(profileId).orElse(null);
        if (current == null || current.kind() != ProfileKind.SOULBOUND_MINIWYVERN) {
            return Decision.CONFLICT;
        }
        ProfileExtensionRecord desired = ProfileExtensionRecord.soulboundMiniwyvern(
                profileId, current.speciesId(), archetypeId, Optional.of(operationId));
        try {
            return map(store.putProfileExtension(desired));
        } catch (IOException | RuntimeException failure) {
            return Decision.UNAVAILABLE;
        }
    }

    private static Decision map(MutationOutcome outcome) {
        return switch (outcome) {
            case APPLIED -> Decision.APPLIED;
            case ALREADY_APPLIED -> Decision.ALREADY_APPLIED;
            case CONFLICT -> Decision.CONFLICT;
            case QUARANTINED -> Decision.QUARANTINED;
            case STORE_READ_ONLY -> Decision.UNAVAILABLE;
        };
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
