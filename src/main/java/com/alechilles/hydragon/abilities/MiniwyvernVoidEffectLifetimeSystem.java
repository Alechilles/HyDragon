package com.alechilles.hydragon.abilities;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Temporary runtime trace that distinguishes server-side early removal from client-only VFX loss. */
public final class MiniwyvernVoidEffectLifetimeSystem extends EntityTickingSystem<EntityStore> {
    private static final String EFFECT_ID = "HyDragon_Miniwyvern_Void_Exposure";
    private static final long[] MILESTONES_MS = {250L, 1_000L, 5_000L};
    private static final Logger LOGGER = Logger.getLogger(MiniwyvernVoidEffectLifetimeSystem.class.getName());
    private final ConcurrentHashMap<UUID, Observation> observations = new ConcurrentHashMap<>();

    void observe(UUID targetUuid) {
        if (targetUuid != null) observations.put(targetUuid, new Observation(System.currentTimeMillis(), 0));
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(UUIDComponent.getComponentType(), EffectControllerComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUIDComponent identity = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (identity == null) return;
        UUID targetUuid = identity.getUuid();
        Observation observation = observations.get(targetUuid);
        if (observation == null) return;
        long nowMs = System.currentTimeMillis();
        int milestone = observation.nextMilestone();
        if (milestone >= MILESTONES_MS.length || nowMs - observation.appliedAtMs() < MILESTONES_MS[milestone]) return;

        EffectControllerComponent controller = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        boolean active = controller != null && effect != null && controller.hasEffect(effect);
        LOGGER.info(() -> "Void Exposure lifetime for " + targetUuid + " at "
                + MILESTONES_MS[milestone] + "ms: active=" + active);
        if (milestone == MILESTONES_MS.length - 1) observations.remove(targetUuid, observation);
        else observations.replace(targetUuid, observation, new Observation(observation.appliedAtMs(), milestone + 1));
    }

    private record Observation(long appliedAtMs, int nextMilestone) {
    }
}
