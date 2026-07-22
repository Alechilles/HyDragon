package com.alechilles.hydragon.runtime;

import java.util.Optional;
import java.util.UUID;

/** Durable once-per-player entitlement boundary. */
public interface SoulBondLedger {
    Reservation reserve(UUID playerUuid, String operationId);

    Reservation complete(UUID playerUuid, String operationId, UUID profileId, long claimedAtEpochMillis);

    /**
     * Atomically closes a pre-profile terminal denial and returns the entitlement to its unclaimed state.
     * Implementations must not use this path once a canonical profile identity has been observed.
     */
    default Reservation compensateDenied(UUID playerUuid,
                                          String operationId,
                                          Optional<String> authorityOperationId,
                                          long compensatedAtEpochMillis) {
        return Reservation.UNAVAILABLE;
    }

    Reservation reconcile(UUID playerUuid, String operationId, Optional<UUID> profileId);

    Optional<Claim> find(UUID playerUuid);

    enum Reservation { APPLIED, ALREADY_APPLIED, CONFLICT, QUARANTINED, UNAVAILABLE }

    record Claim(String operationId, Optional<UUID> profileId, State state) {
        public enum State { PENDING, CLAIMED, NEEDS_RECONCILIATION }
    }
}
