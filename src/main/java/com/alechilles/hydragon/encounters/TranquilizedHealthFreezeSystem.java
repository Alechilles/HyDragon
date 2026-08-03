package com.alechilles.hydragon.encounters;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Freezes positive health recovery while Tamework's tranquilizer effect is active. */
public final class TranquilizedHealthFreezeSystem extends EntityTickingSystem<EntityStore> {
    private static final String TRANQUILIZED_EFFECT_ID = "Tw_Status_Tranquilized";
    private static final float SLEEP_HEALTH_FRACTION = 0.20F;
    private final Map<UUID, Float> frozenHealth = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(EntityStatMap.getComponentType(), UUIDComponent.getComponentType(),
                EffectControllerComponent.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, NPCPlugin.NPCEntityRegenerateStatsSystem.class));
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUIDComponent identity = chunk.getComponent(index, UUIDComponent.getComponentType());
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        EffectControllerComponent effects = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        if (identity == null || stats == null || effects == null) return;
        UUID uuid = identity.getUuid();
        EntityEffect tranquilized = EntityEffect.getAssetMap().getAsset(TRANQUILIZED_EFFECT_ID);
        if (tranquilized == null || !effects.hasEffect(tranquilized)) {
            frozenHealth.remove(uuid);
            return;
        }
        var health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.getMax() <= 0.0F
                || health.get() > health.getMax() * SLEEP_HEALTH_FRACTION) {
            frozenHealth.remove(uuid);
            return;
        }
        float current = health.get();
        Float previous = frozenHealth.putIfAbsent(uuid, current);
        if (previous == null) return;
        if (current > previous) {
            stats.setStatValue(DefaultEntityStatTypes.getHealth(), previous);
            return;
        }
        frozenHealth.put(uuid, current);
    }
}
