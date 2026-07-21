package com.alechilles.hydragon.runtime;

import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.MutationOutcome;
import com.alechilles.hydragon.persistence.PlayerSoulBondRecord;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Production adapter from the runtime entitlement contract to HyDragon's durable state store. */
public final class StateStoreSoulBondLedger implements SoulBondLedger {
    private final HyDragonStateStore store;

    public StateStoreSoulBondLedger(HyDragonStateStore store) {
        this.store = java.util.Objects.requireNonNull(store, "store");
    }

    @Override
    public Reservation reserve(UUID playerUuid, String operationId) {
        try {
            return map(store.beginSoulBond(playerUuid, operationId));
        } catch (IOException exception) {
            return Reservation.UNAVAILABLE;
        }
    }

    @Override
    public Reservation complete(UUID playerUuid, String operationId, UUID profileId, long claimedAtEpochMillis) {
        try {
            return map(store.completeSoulBondWithMiniwyvernProfile(
                    playerUuid, operationId, profileId, claimedAtEpochMillis));
        } catch (IOException exception) {
            return Reservation.UNAVAILABLE;
        }
    }

    @Override
    public Reservation reconcile(UUID playerUuid, String operationId, Optional<UUID> profileId) {
        try {
            return map(store.markSoulBondNeedsReconciliation(
                    playerUuid, operationId, profileId, OptionalLong.empty()));
        } catch (IOException exception) {
            return Reservation.UNAVAILABLE;
        }
    }

    @Override
    public Optional<Claim> find(UUID playerUuid) {
        return store.snapshot().playerSoulBond(playerUuid).flatMap(StateStoreSoulBondLedger::toClaim);
    }

    private static Optional<Claim> toClaim(PlayerSoulBondRecord record) {
        if (record.state() == com.alechilles.hydragon.persistence.SoulBondState.UNCLAIMED) {
            return Optional.empty();
        }
        Claim.State state = switch (record.state()) {
            case PENDING -> Claim.State.PENDING;
            case CLAIMED -> Claim.State.CLAIMED;
            case NEEDS_RECONCILIATION -> Claim.State.NEEDS_RECONCILIATION;
            case UNCLAIMED -> throw new IllegalStateException("handled above");
        };
        return Optional.of(new Claim(record.operationId().orElseThrow(), record.profileId(), state));
    }

    private static Reservation map(MutationOutcome outcome) {
        return switch (outcome) {
            case APPLIED -> Reservation.APPLIED;
            case ALREADY_APPLIED -> Reservation.ALREADY_APPLIED;
            case CONFLICT -> Reservation.CONFLICT;
            case QUARANTINED -> Reservation.QUARANTINED;
            case STORE_READ_ONLY -> Reservation.UNAVAILABLE;
        };
    }
}
