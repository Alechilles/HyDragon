package com.alechilles.hydragon.persistence;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Durable queue boundary for at-least-once full-dragon profile projection. */
public interface PendingProfileProjectionStore {
    Map<UUID, PendingProfileProjectionRecord> pendingProfileProjections();

    MutationOutcome putPendingProfileProjection(PendingProfileProjectionRecord record) throws IOException;

    MutationOutcome removePendingProfileProjection(UUID operationId) throws IOException;
}
