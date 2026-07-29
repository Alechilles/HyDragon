package com.alechilles.hydragon.abilities;

import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.EffectOp;
import com.hypixel.hytale.protocol.EntityEffectUpdate;
import com.hypixel.hytale.protocol.EntityEffectsUpdate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MiniwyvernVoidEffectReplicationProbe {
    private final ConcurrentHashMap<UUID, Observation> pending = new ConcurrentHashMap<>();

    public MiniwyvernVoidEffectReplicationProbe() {
    }

    void observe(UUID targetUuid, int effectIndex) {
        pending.put(Objects.requireNonNull(targetUuid, "targetUuid"), new Observation(effectIndex));
    }

    void observeApplication(UUID targetUuid, int effectIndex, String formId, boolean applied) {
        if (applied && "void".equals(formId)) observe(targetUuid, effectIndex);
    }

    Observation consume(UUID targetUuid) {
        return pending.remove(Objects.requireNonNull(targetUuid, "targetUuid"));
    }

    static PacketEvidence inspectQueuedUpdates(int effectIndex, ComponentUpdate[] updates) {
        int adds = 0;
        int removes = 0;
        float latestAddRemainingSeconds = Float.NaN;
        if (updates != null) {
            for (ComponentUpdate update : updates) {
                if (!(update instanceof EntityEffectsUpdate effects) || effects.entityEffectUpdates == null) continue;
                for (EntityEffectUpdate effect : effects.entityEffectUpdates) {
                    if (effect == null || effect.id != effectIndex) continue;
                    if (effect.type == EffectOp.Add) {
                        adds++;
                        latestAddRemainingSeconds = effect.remainingTime;
                    } else if (effect.type == EffectOp.Remove) {
                        removes++;
                    }
                }
            }
        }
        return new PacketEvidence(adds, removes, latestAddRemainingSeconds);
    }

    record PacketEvidence(int adds, int removes, float latestAddRemainingSeconds) {
    }

    record Observation(int effectIndex) {
    }
}
