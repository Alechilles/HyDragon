package com.alechilles.hydragon.abilities;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** Moves owner-hit effect mutation out of the nested damage-event dispatch. */
final class MiniwyvernOwnerAuraEffectScheduler {
    private MiniwyvernOwnerAuraEffectScheduler() { }

    static void schedule(
            Executor worldTasks,
            UUID targetUuid,
            MiniwyvernOwnerAuraRegistry.Aura aura,
            BiConsumer<UUID, MiniwyvernOwnerAuraRegistry.Aura> effectApplier) {
        Objects.requireNonNull(worldTasks, "worldTasks");
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(aura, "aura");
        Objects.requireNonNull(effectApplier, "effectApplier");
        worldTasks.execute(() -> effectApplier.accept(targetUuid, aura));
    }
}
