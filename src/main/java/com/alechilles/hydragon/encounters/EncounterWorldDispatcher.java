package com.alechilles.hydragon.encounters;

import java.util.UUID;
import java.util.function.Consumer;

/** Dispatches target-specific work to the owning Hytale world thread. */
@FunctionalInterface
public interface EncounterWorldDispatcher {
    void dispatch(String worldName, UUID targetNpcUuid, Consumer<EncounterWorldGateway> callback);
}
