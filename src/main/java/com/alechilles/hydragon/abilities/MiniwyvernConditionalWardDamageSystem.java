package com.alechilles.hydragon.abilities;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies Fire, Nature, and Toxic conditional owner damage reduction. */
public final class MiniwyvernConditionalWardDamageSystem extends DamageEventSystem {
    private final MiniwyvernOwnerAuraRegistry registry;

    public MiniwyvernConditionalWardDamageSystem(MiniwyvernOwnerAuraRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Nullable @Override public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull @Override public Query<EntityStore> getQuery() {
        return Query.and(UUIDComponent.getComponentType(), EntityStatMap.getComponentType());
    }

    @Override public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        UUIDComponent identity = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (identity == null) return;
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        boolean belowHalf = health != null && Float.isFinite(health.get())
                && Float.isFinite(health.getMax()) && health.getMax() > 0.0F
                && health.get() / health.getMax() < 0.5F;
        long nowMs = System.currentTimeMillis();
        MiniwyvernOwnerAuraRegistry.Aura aura = registry.activeFor(identity.getUuid()).orElse(null);
        boolean active = aura != null && registry.conditionalWardActive(identity.getUuid(), belowHalf, nowMs);
        boolean blocked = Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
        boolean healing = damage.getCause() != null
                && "healing".equalsIgnoreCase(damage.getCause().getId());
        if (!active || !shouldModify(damage.isCancelled(), damage.getAmount(), blocked, healing)) return;
        damage.setAmount(reducedAmount(damage.getAmount(), aura.conditionalWardDamageReductionFraction()));
    }

    static boolean shouldModify(boolean cancelled, float amount, boolean blocked, boolean healing) {
        return !cancelled && Float.isFinite(amount) && amount > 0.0F && !blocked && !healing;
    }

    static float reducedAmount(float amount, double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0.0D || fraction >= 1.0D) return amount;
        return (float) (amount * (1.0D - fraction));
    }
}
