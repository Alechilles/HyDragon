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

/** Reduces weakened/slow-marked entities' outgoing damage during Hytale's pre-application filter phase. */
public final class MiniwyvernToxicWeaknessDamageSystem extends DamageEventSystem {
    private static final String PROJECTILE_EFFECT_ID = "HyDragon_Miniwyvern_Toxic_Projectile_Weakness";
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
        if (!(damage.getSource() instanceof Damage.EntitySource source)) return;
        Ref<EntityStore> sourceRef = source.getRef();
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        boolean blocked = Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
        boolean healing = damage.getCause() != null && "healing".equalsIgnoreCase(damage.getCause().getId());
        if (!shouldModify(damage.isCancelled(), damage.getAmount(), MiniwyvernOwnerAuraDamageSystem.isLiveRef(sourceRef),
                sourceRef != null && sourceRef.equals(targetRef), blocked, healing)) return;
        UUIDComponent identity = store.getComponent(sourceRef, UUIDComponent.getComponentType());
        if (identity == null) return;
        long nowMs = System.currentTimeMillis();
        var weaknesses = registry.activeToxicWeaknesses(identity.getUuid(), nowMs);
        EffectControllerComponent controller = store.getComponent(sourceRef, EffectControllerComponent.getComponentType());
        if (controller == null) return;
        double bondFraction = weaknesses.stream()
                .filter(weakness -> hasEffect(controller, weakness.effectId()))
                .mapToDouble(MiniwyvernOwnerAuraRegistry.ToxicWeakness::damageReductionFraction)
                .max()
                .orElse(0.0D);
        boolean projectileActive = hasEffect(controller, PROJECTILE_EFFECT_ID);
        if (bondFraction <= 0.0D && !projectileActive) return;
        damage.setAmount(reducedAmount(damage.getAmount(), bondFraction, projectileActive));
    }

    static boolean shouldModify(boolean cancelled, float amount, boolean entityCaused, boolean self,
            boolean blocked, boolean healing) {
        return !cancelled && Float.isFinite(amount) && amount > 0.0F && entityCaused && !self && !blocked && !healing;
    }

    static float reducedAmount(float amount, boolean bondActive, boolean projectileActive) {
        return reducedAmount(amount, bondActive ? 0.12D : 0.0D, projectileActive);
    }

    static float reducedAmount(float amount, double bondFraction) {
        return reducedAmount(amount, bondFraction, false);
    }

    static float reducedAmount(float amount, double bondFraction, boolean projectileActive) {
        double fraction = validFraction(bondFraction)
                ? bondFraction : projectileActive ? 0.10D : 0.0D;
        return (float) (amount * (1.0D - fraction));
    }

    private static boolean validFraction(double fraction) {
        return Double.isFinite(fraction) && fraction > 0.0D && fraction < 1.0D;
    }

    private static boolean hasEffect(EffectControllerComponent controller, String effectId) {
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
        return effectIndex >= 0 && controller.getActiveEffects().containsKey(effectIndex);
    }
}
