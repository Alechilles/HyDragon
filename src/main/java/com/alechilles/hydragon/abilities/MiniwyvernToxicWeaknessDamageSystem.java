package com.alechilles.hydragon.abilities;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Reduces Toxic-weakened entities' outgoing damage during Hytale's pre-application filter phase. */
public final class MiniwyvernToxicWeaknessDamageSystem extends DamageEventSystem {
    private final MiniwyvernOwnerAuraRegistry registry;

    public MiniwyvernToxicWeaknessDamageSystem(MiniwyvernOwnerAuraRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Nullable @Override public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull @Override public Query<EntityStore> getQuery() {
        return UUIDComponent.getComponentType();
    }

    @Override public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        if (damage.isCancelled() || !Float.isFinite(damage.getAmount()) || damage.getAmount() <= 0.0F
                || !(damage.getSource() instanceof Damage.EntitySource source)) return;
        Ref<EntityStore> sourceRef = source.getRef();
        if (!MiniwyvernOwnerAuraDamageSystem.isLiveRef(sourceRef)) return;
        UUIDComponent identity = store.getComponent(sourceRef, UUIDComponent.getComponentType());
        if (identity == null) return;
        MiniwyvernOwnerAuraRegistry.ToxicWeakness weakness = registry.activeToxicWeakness(
                identity.getUuid(), System.currentTimeMillis()).orElse(null);
        if (weakness == null) return;
        EffectControllerComponent controller = store.getComponent(sourceRef, EffectControllerComponent.getComponentType());
        int effectIndex = EntityEffect.getAssetMap().getIndex(weakness.effectId());
        if (controller == null || effectIndex < 0 || !controller.getActiveEffects().containsKey(effectIndex)) return;
        damage.setAmount(reducedAmount(damage.getAmount(), weakness.damageReductionFraction()));
    }

    static float reducedAmount(float amount, double fraction) {
        return (float) (amount * (1.0D - fraction));
    }
}
