package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.hydragon.interactions.HyDragonInteractionRuntime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Production composition of HyDragon-owned consumable interactions. */
public final class HyDragonGameplayRuntime implements HyDragonInteractionRuntime.Handler {
    private final SoulBondService soulBonds;
    public HyDragonGameplayRuntime(SoulBondService soulBonds) {
        this.soulBonds = Objects.requireNonNull(soulBonds, "soulBonds");
    }

    @Override
    public CompletionStage<GameplayResult> soulBond(
            UUID playerUuid,
            String worldName,
            PopulationAdmissionLocation destination,
            ConsumableReservation reservation) {
        return soulBonds.claim(playerUuid, worldName, destination, reservation);
    }

}
