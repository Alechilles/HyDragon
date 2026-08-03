package com.alechilles.hydragon.abilities;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
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
    private final MiniwyvernOwnerAuraEffectQueue effectQueue;

    public MiniwyvernOwnerAuraDamageSystem(
            MiniwyvernOwnerAuraRegistry registry, MiniwyvernOwnerAuraEffectQueue effectQueue) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.effectQueue = Objects.requireNonNull(effectQueue, "effectQueue");
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
        if (aura == null || !shouldApplyDamage(damage, ownerRef, target)) return;
        UUIDComponent targetIdentity = store.getComponent(target, UUIDComponent.getComponentType());
        if (targetIdentity != null && hasTargetEffect(aura)) {
            effectQueue.submit(store.getExternalData().getWorld().getName(), targetIdentity.getUuid(), aura);
        }
        if ("lightning".equals(aura.formId()) && aura.speedBurstMultiplier() > 0.0D) {
            registry.recordSpeedBurst(ownerIdentity.getUuid());
        }
    }

    boolean shouldApply(java.util.UUID ownerUuid, boolean playerSource, boolean cancelled, float amount) {
        return ownerUuid != null && playerSource && !cancelled && Float.isFinite(amount)
                && amount > 0.0F
                && registry.activeFor(ownerUuid).map(MiniwyvernOwnerAuraDamageSystem::hasTargetEffect).orElse(false);
    }

    private boolean shouldApplyDamage(
            Damage damage, Ref<EntityStore> ownerRef, Ref<EntityStore> targetRef) {
        boolean blocked = Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
        boolean healing = damage.getCause() != null
                && "healing".equalsIgnoreCase(damage.getCause().getId());
        return !damage.isCancelled() && Float.isFinite(damage.getAmount()) && damage.getAmount() > 0.0F
                && isLiveRef(ownerRef) && !ownerRef.equals(targetRef) && !blocked && !healing;
    }

    static boolean hasTargetEffect(@Nullable MiniwyvernOwnerAuraRegistry.Aura aura) {
        return aura != null && aura.effectId() != null && !aura.effectId().isBlank();
    }

    static boolean isLiveRef(@Nullable Ref<EntityStore> ref) { return ref != null && ref.isValid(); }
}
