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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Increases player-owner damage against targets carrying that owner's live aura effect. */
public final class MiniwyvernAuraMarkedTargetDamageSystem extends DamageEventSystem {
    private final MiniwyvernOwnerAuraRegistry registry;

    public MiniwyvernAuraMarkedTargetDamageSystem(MiniwyvernOwnerAuraRegistry registry) {
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
        PlayerRef player = sourceRef == null
                ? null : store.getComponent(sourceRef, PlayerRef.getComponentType());
        UUIDComponent ownerIdentity = sourceRef == null
                ? null : store.getComponent(sourceRef, UUIDComponent.getComponentType());
        EffectControllerComponent targetController =
                chunk.getComponent(index, EffectControllerComponent.getComponentType());
        MiniwyvernOwnerAuraRegistry.Aura aura = ownerIdentity == null ? null
                : registry.activeFor(ownerIdentity.getUuid()).orElse(null);
        boolean playerSource = player != null && player.isValid();
        boolean markedTarget = aura != null && aura.ownerDamageToAffectedFraction() > 0.0D
                && targetController != null && hasEffect(targetController,
                aura == null ? null : aura.effectId());
        boolean blocked = Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
        boolean healing = damage.getCause() != null && "healing".equalsIgnoreCase(damage.getCause().getId());
        if (!shouldModify(damage.isCancelled(), damage.getAmount(),
                MiniwyvernOwnerAuraDamageSystem.isLiveRef(sourceRef),
                sourceRef != null && sourceRef.equals(targetRef), blocked, healing,
                playerSource, markedTarget)) return;
        damage.setAmount(increasedOwnerDamage(
                damage.getAmount(), aura.ownerDamageToAffectedFraction()));
    }

    static boolean shouldModify(boolean cancelled, float amount, boolean entityCaused, boolean self,
            boolean blocked, boolean healing, boolean playerSource, boolean markedTarget) {
        return !cancelled && Float.isFinite(amount) && amount > 0.0F
                && entityCaused && !self && !blocked && !healing && playerSource && markedTarget;
    }

    static float increasedOwnerDamage(float amount, double fraction) {
        return (float) (amount * (1.0D + fraction));
    }

    private static boolean hasEffect(EffectControllerComponent controller, @Nullable String effectId) {
        if (effectId == null || effectId.isBlank()) return false;
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
        return effectIndex >= 0 && controller.getActiveEffects().containsKey(effectIndex);
    }
}
