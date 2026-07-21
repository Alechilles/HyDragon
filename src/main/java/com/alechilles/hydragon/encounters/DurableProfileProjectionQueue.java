package com.alechilles.hydragon.encounters;

import com.alechilles.alecstamework.api.CaptureAttemptOutcome;
import com.alechilles.alecstamework.api.CaptureAttemptResolvedEvent;
import com.alechilles.hydragon.persistence.MutationOutcome;
import com.alechilles.hydragon.persistence.PendingProfileProjectionRecord;
import com.alechilles.hydragon.persistence.PendingProfileProjectionStore;
import java.io.IOException;
import java.time.Clock;
import java.util.Comparator;
import java.util.Objects;

/** Persists capture evidence before projection and replays it in deterministic bounded batches. */
public final class DurableProfileProjectionQueue {
    private final PendingProfileProjectionStore store;
    private final FullDragonProfileProjection projection;
    private final Clock clock;

    public DurableProfileProjectionQueue(
            PendingProfileProjectionStore store,
            FullDragonProfileProjection projection,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Durably records a captured operation before applying its local projection. Non-capture outcomes are
     * terminal and never enter the queue.
     */
    public FullDragonProfileProjection.Result accept(CaptureAttemptResolvedEvent event) {
        if (event == null || event.outcome() != CaptureAttemptOutcome.CAPTURED) {
            return projection.project(event);
        }

        PendingProfileProjectionRecord record;
        try {
            record = PendingProfileProjectionRecord.captured(
                    event.operationId(), event.profileId(), event.roleId(), Math.max(0L, clock.millis()));
        } catch (RuntimeException invalidEvidence) {
            return FullDragonProfileProjection.Result.INVALID;
        }

        try {
            MutationOutcome queued = store.putPendingProfileProjection(record);
            if (queued == MutationOutcome.CONFLICT || queued == MutationOutcome.QUARANTINED) {
                return FullDragonProfileProjection.Result.CONFLICT;
            }
            if (queued == MutationOutcome.STORE_READ_ONLY) {
                return FullDragonProfileProjection.Result.UNAVAILABLE;
            }
        } catch (IOException | RuntimeException unavailable) {
            return FullDragonProfileProjection.Result.UNAVAILABLE;
        }
        return attempt(record);
    }

    /** Attempts at most {@code maximum} records in stable operation-id order. */
    public int retrySome(int maximum) {
        if (maximum <= 0) return 0;
        var pending = store.pendingProfileProjections().values().stream()
                .sorted(Comparator.comparing(record -> record.operationId().toString()))
                .limit(maximum)
                .toList();
        pending.forEach(this::attempt);
        return pending.size();
    }

    public int pendingCount() {
        return store.pendingProfileProjections().size();
    }

    private FullDragonProfileProjection.Result attempt(PendingProfileProjectionRecord record) {
        FullDragonProfileProjection.Result result = projection.project(record);
        if (result == FullDragonProfileProjection.Result.UNAVAILABLE) return result;
        try {
            store.removePendingProfileProjection(record.operationId());
        } catch (IOException | RuntimeException unavailable) {
            // Keep the durable record. A later replay is safe because projection is idempotent.
        }
        return result;
    }
}
