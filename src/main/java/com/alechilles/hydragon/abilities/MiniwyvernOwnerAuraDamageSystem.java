package com.alechilles.hydragon.abilities;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
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

/** Applies a currently summoned Miniwyvern's owner-hit aura after Hytale's damage filters. */
public final class MiniwyvernOwnerAuraDamageSystem extends DamageEventSystem {
    private final MiniwyvernOwnerAuraRegistry registry;

    public MiniwyvernOwnerAuraDamageSystem(MiniwyvernOwnerAuraRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Nullable @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getInspectDamageGroup(); }
    @Nonnull @Override public Query<EntityStore> getQuery() { return UUIDComponent.getComponentType(); }

    @Override public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        if (!(damage.getSource() instanceof Damage.EntitySource source)) return;
        Ref<EntityStore> ownerRef = source.getRef();
        if (!isLiveRef(ownerRef)) return;
        Ref<EntityStore> target = chunk.getReferenceTo(index);
        PlayerRef player = store.getComponent(ownerRef, PlayerRef.getComponentType());
        UUIDComponent ownerIdentity = store.getComponent(ownerRef, UUIDComponent.getComponentType());
        if (player == null || !player.isValid() || ownerIdentity == null) return;
        MiniwyvernOwnerAuraRegistry.Aura aura = registry.activeFor(ownerIdentity.getUuid()).orElse(null);
        if (aura == null || !shouldApply(ownerIdentity.getUuid(), true,
                damage.isCancelled(), damage.getAmount())) return;
        EffectControllerComponent controller = store.getComponent(target, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(aura.effectId());
        if (controller != null && effect != null && controller.addEffect(target, effect,
                (float) aura.durationSeconds(), OverlapBehavior.OVERWRITE, store)
                && aura.damageReductionFraction() > 0.0D) {
            UUIDComponent targetIdentity = store.getComponent(target, UUIDComponent.getComponentType());
            if (targetIdentity != null) registry.recordToxicWeakness(targetIdentity.getUuid(), aura.effectId(),
                    aura.damageReductionFraction(), aura.durationSeconds(), System.currentTimeMillis());
        }
    }

    boolean shouldApply(java.util.UUID ownerUuid, boolean playerSource, boolean cancelled, float amount) {
        return ownerUuid != null && playerSource && !cancelled && Float.isFinite(amount)
                && amount > 0.0F && registry.activeFor(ownerUuid).isPresent();
    }

    static boolean isLiveRef(@Nullable Ref<EntityStore> ref) { return ref != null && ref.isValid(); }

}
