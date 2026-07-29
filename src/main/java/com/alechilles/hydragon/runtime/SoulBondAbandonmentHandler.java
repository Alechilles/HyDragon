package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.BondedCompanionChangedEvent;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Releases a Soul Bond only after Tamework confirms permanent abandonment of its Miniwyvern profile. */
public final class SoulBondAbandonmentHandler {
    private static final String ABANDONED_REASON = "abandoned";

    private final SoulBondLedger ledger;

    public SoulBondAbandonmentHandler(SoulBondLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    void onBondedChanged(BondedCompanionChangedEvent event) {
        Objects.requireNonNull(event, "event");
        if (!TameworkGameplayAdapter.DRAGON_HORN_ROSTER.equals(event.rosterId())
                || !ABANDONED_REASON.equals(event.reason().toLowerCase(Locale.ROOT))) {
            return;
        }
        try {
            ledger.releaseAfterAbandonment(event.ownerUuid(), UUID.fromString(event.profileId()));
        } catch (IllegalArgumentException ignored) {
            // A malformed external profile ID cannot match a UUID-backed HyDragon entitlement.
        }
    }
}
