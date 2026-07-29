package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.hydragon.interactions.HyDragonInteractionRuntime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Production composition of HyDragon-owned consumable interactions. */
public final class HyDragonGameplayRuntime implements HyDragonInteractionRuntime.Handler, AutoCloseable {
    private final SoulBondService soulBonds;
    private final SoulBondAbandonmentHandler abandonmentHandler;
    private AutoCloseable bondedChangeSubscription;

    public HyDragonGameplayRuntime(
            SoulBondService soulBonds,
            SoulBondAbandonmentHandler abandonmentHandler) {
        this.soulBonds = Objects.requireNonNull(soulBonds, "soulBonds");
        this.abandonmentHandler = Objects.requireNonNull(abandonmentHandler, "abandonmentHandler");
    }

    /** Starts the permanent-bond release listener after the Tamework integration is ready. */
    public synchronized void start(TameworkGameplayAdapter tamework) {
        if (bondedChangeSubscription != null) return;
        bondedChangeSubscription = Objects.requireNonNull(tamework, "tamework")
                .subscribeBondedChanges(abandonmentHandler::onBondedChanged);
    }

    @Override
    public CompletionStage<GameplayResult> soulBond(
            UUID playerUuid,
            String worldName,
            PopulationAdmissionLocation destination,
            ConsumableReservation reservation) {
        return soulBonds.claim(playerUuid, worldName, destination, reservation);
    }

    @Override
    public synchronized void close() {
        if (bondedChangeSubscription == null) return;
        try {
            bondedChangeSubscription.close();
        } catch (Exception ignored) {
            // Plugin shutdown must not leave the interaction runtime installed because an external listener failed.
        } finally {
            bondedChangeSubscription = null;
        }
    }
}
