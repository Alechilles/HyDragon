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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Increases damage received by entities exposed to a Miniwyvern Void projectile or Bond aura. */
public final class MiniwyvernVoidExposureDamageSystem extends DamageEventSystem {
    private static final String BOND_EFFECT_ID = "HyDragon_Miniwyvern_Void_Exposure";
    private static final String PROJECTILE_EFFECT_ID = "HyDragon_Miniwyvern_Void_Projectile_Exposure";

    @Nullable @Override public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull @Override public Query<EntityStore> getQuery() {
        return UUIDComponent.getComponentType();
    }

    @Override public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        if (!(damage.getSource() instanceof Damage.EntitySource source)) return;
        Ref<EntityStore> sourceRef = source.getRef();
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        boolean blocked = Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
        boolean healing = damage.getCause() != null && "healing".equalsIgnoreCase(damage.getCause().getId());
        if (!shouldModify(damage.isCancelled(), damage.getAmount(), MiniwyvernOwnerAuraDamageSystem.isLiveRef(sourceRef),
                sourceRef != null && sourceRef.equals(targetRef), blocked, healing)) return;
        EffectControllerComponent controller = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        if (controller == null) return;
        boolean bondActive = hasEffect(controller, BOND_EFFECT_ID);
        boolean projectileActive = hasEffect(controller, PROJECTILE_EFFECT_ID);
        if (!bondActive && !projectileActive) return;
        damage.setAmount(increasedAmount(damage.getAmount(), bondActive, projectileActive));
    }

    static boolean shouldModify(boolean cancelled, float amount, boolean entityCaused, boolean self,
            boolean blocked, boolean healing) {
        return !cancelled && Float.isFinite(amount) && amount > 0.0F && entityCaused && !self && !blocked && !healing;
    }

    static float increasedAmount(float amount, boolean bondActive, boolean projectileActive) {
        double fraction = bondActive ? 0.12D : projectileActive ? 0.10D : 0.0D;
        return (float) (amount * (1.0D + fraction));
    }

    private static boolean hasEffect(EffectControllerComponent controller, String effectId) {
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
        return effectIndex >= 0 && controller.getActiveEffects().containsKey(effectIndex);
    }
}
