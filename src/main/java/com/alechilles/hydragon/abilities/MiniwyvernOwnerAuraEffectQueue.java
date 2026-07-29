package com.alechilles.hydragon.abilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MiniwyvernOwnerAuraEffectQueue {
    private final ConcurrentHashMap<RequestKey, MiniwyvernOwnerAuraRegistry.Aura> pending =
            new ConcurrentHashMap<>();

    public MiniwyvernOwnerAuraEffectQueue() {
    }

    void submit(String worldName, UUID targetUuid, MiniwyvernOwnerAuraRegistry.Aura aura) {
        Objects.requireNonNull(aura, "aura");
        pending.put(new RequestKey(worldName, targetUuid, aura.effectId()), aura);
    }

    List<MiniwyvernOwnerAuraRegistry.Aura> drain(String worldName, UUID targetUuid) {
        String requiredWorld = requireText(worldName, "worldName");
        Objects.requireNonNull(targetUuid, "targetUuid");
        List<QueuedAura> drained = new ArrayList<>();
        for (var entry : pending.entrySet()) {
            RequestKey key = entry.getKey();
            MiniwyvernOwnerAuraRegistry.Aura aura = entry.getValue();
            if (key.worldName().equals(requiredWorld) && key.targetUuid().equals(targetUuid)
                    && pending.remove(key, aura)) {
                drained.add(new QueuedAura(key.effectId(), aura));
            }
        }
        drained.sort(Comparator.comparing(QueuedAura::effectId));
        return drained.stream().map(QueuedAura::aura).toList();
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private record RequestKey(String worldName, UUID targetUuid, String effectId) {
        private RequestKey {
            worldName = requireText(worldName, "worldName");
            Objects.requireNonNull(targetUuid, "targetUuid");
            effectId = requireText(effectId, "effectId");
        }
    }

    private record QueuedAura(String effectId, MiniwyvernOwnerAuraRegistry.Aura aura) {
    }
}
