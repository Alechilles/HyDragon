package com.alechilles.hydragon.abilities;

import java.util.UUID;
import java.util.function.Consumer;

/** Resolves a loaded projection and invokes the callback on its owning Hytale world thread. */
@FunctionalInterface
public interface MiniwyvernAbilityWorldDispatcher {
    void dispatch(UUID ownerUuid, UUID npcUuid, Consumer<MiniwyvernAbilityWorld> callback);
}
