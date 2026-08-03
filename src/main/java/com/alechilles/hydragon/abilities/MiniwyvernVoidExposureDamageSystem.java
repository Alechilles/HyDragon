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
    private static final String PROJECTILE_EFFECT_ID = "HyDragon_Miniwyvern_Void_Projectile_Exposure";
    private final MiniwyvernOwnerAuraRegistry registry;

    public MiniwyvernVoidExposureDamageSystem() {
        this(new MiniwyvernOwnerAuraRegistry());
    }

    public MiniwyvernVoidExposureDamageSystem(MiniwyvernOwnerAuraRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
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
        if (!(damage.getSource() instanceof Damage.EntitySource source)) return;
        Ref<EntityStore> sourceRef = source.getRef();
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        boolean blocked = Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
        boolean healing = damage.getCause() != null && "healing".equalsIgnoreCase(damage.getCause().getId());
        if (!shouldModify(damage.isCancelled(), damage.getAmount(), MiniwyvernOwnerAuraDamageSystem.isLiveRef(sourceRef),
                sourceRef != null && sourceRef.equals(targetRef), blocked, healing)) return;
        EffectControllerComponent controller = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        if (controller == null) return;
        UUIDComponent targetIdentity = chunk.getComponent(index, UUIDComponent.getComponentType());
        double bondFraction = 0.0D;
        if (targetIdentity != null) {
            for (MiniwyvernOwnerAuraRegistry.TargetAura targetAura
                    : registry.activeTargetAuras(targetIdentity.getUuid(), System.currentTimeMillis())) {
                if (targetAura.targetDamageTakenFraction() > bondFraction
                        && hasEffect(controller, targetAura.effectId())) {
                    bondFraction = targetAura.targetDamageTakenFraction();
                }
            }
        }
        boolean projectileActive = hasEffect(controller, PROJECTILE_EFFECT_ID);
        if (bondFraction <= 0.0D && !projectileActive) return;
        damage.setAmount(increasedAmount(damage.getAmount(), bondFraction, projectileActive));
    }

    static boolean shouldModify(boolean cancelled, float amount, boolean entityCaused, boolean self,
            boolean blocked, boolean healing) {
        return !cancelled && Float.isFinite(amount) && amount > 0.0F && entityCaused && !self && !blocked && !healing;
    }

    static float increasedAmount(float amount, boolean bondActive, boolean projectileActive) {
        return increasedAmount(amount, bondActive ? 0.12D : 0.0D, projectileActive);
    }

    static float increasedAmount(float amount, double bondFraction) {
        return increasedAmount(amount, bondFraction, false);
    }

    static float increasedAmount(float amount, double bondFraction, boolean projectileActive) {
        double fraction = validFraction(bondFraction)
                ? bondFraction : projectileActive ? 0.10D : 0.0D;
        return (float) (amount * (1.0D + fraction));
    }

    private static boolean validFraction(double fraction) {
        return Double.isFinite(fraction) && fraction > 0.0D && fraction < 1.0D;
    }

    private static boolean hasEffect(EffectControllerComponent controller, String effectId) {
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
        return effectIndex >= 0 && controller.getActiveEffects().containsKey(effectIndex);
    }
}
