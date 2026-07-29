package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alechilles.alecstamework.api.BondedCompanionChangedEvent;
import com.alechilles.alecstamework.api.BondedCompanionStateView;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SoulBondAbandonmentHandlerTest {
    @Test
    void releasesOnlyTheAbandonedMiniwyvernInTheDragonHornRoster() {
        RecordingLedger ledger = new RecordingLedger();
        SoulBondAbandonmentHandler handler = new SoulBondAbandonmentHandler(ledger);
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();

        handler.onBondedChanged(event(owner, profile, TameworkGameplayAdapter.DRAGON_HORN_ROSTER, "abandoned"));
        handler.onBondedChanged(event(owner, UUID.randomUUID(), "other:roster", "abandoned"));
        handler.onBondedChanged(event(owner, UUID.randomUUID(), TameworkGameplayAdapter.DRAGON_HORN_ROSTER, "dismissed"));

        assertEquals(new Release(owner, profile), ledger.release);
    }

    private static BondedCompanionChangedEvent event(
            UUID owner, UUID profile, String roster, String reason) {
        return new BondedCompanionChangedEvent(
                profile.toString(), owner, roster, null, BondedCompanionStateView.STORED, 1L, reason);
    }

    private static final class RecordingLedger implements SoulBondLedger {
        private Release release;

        @Override
        public Reservation reserve(UUID playerUuid, String operationId) {
            return Reservation.UNAVAILABLE;
        }

        @Override
        public Reservation complete(UUID playerUuid, String operationId, UUID profileId, long claimedAtEpochMillis) {
            return Reservation.UNAVAILABLE;
        }

        @Override
        public Reservation releaseAfterAbandonment(UUID playerUuid, UUID profileId) {
            release = new Release(playerUuid, profileId);
            return Reservation.APPLIED;
        }

        @Override
        public Reservation reconcile(UUID playerUuid, String operationId, Optional<UUID> profileId) {
            return Reservation.UNAVAILABLE;
        }

        @Override
        public Optional<Claim> find(UUID playerUuid) {
            return Optional.empty();
        }
    }

    private record Release(UUID owner, UUID profile) { }
}
