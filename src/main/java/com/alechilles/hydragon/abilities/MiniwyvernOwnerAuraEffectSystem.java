package com.alechilles.hydragon.abilities;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies owner-hit auras in a dedicated ECS phase, matching projectile impact effects. */
public final class MiniwyvernOwnerAuraEffectSystem extends EntityTickingSystem<EntityStore> {
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.AFTER, MiniwyvernOwnerAuraDamageSystem.class),
            new SystemDependency<>(Order.BEFORE, EntityTrackerSystems.EffectControllerSystem.class));

    private final MiniwyvernOwnerAuraEffectQueue queue;
    private final MiniwyvernOwnerAuraRegistry registry;
    private final MiniwyvernVoidEffectLifetimeSystem voidLifetime;

    public MiniwyvernOwnerAuraEffectSystem(
            MiniwyvernOwnerAuraEffectQueue queue,
            MiniwyvernOwnerAuraRegistry registry,
            MiniwyvernVoidEffectLifetimeSystem voidLifetime) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.voidLifetime = Objects.requireNonNull(voidLifetime, "voidLifetime");
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
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
        EffectControllerComponent controller = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        if (identity == null || controller == null) return;

        UUID targetUuid = identity.getUuid();
        String worldName = store.getExternalData().getWorld().getName();
        Ref<EntityStore> target = chunk.getReferenceTo(index);
        MiniwyvernOwnerAuraEffectQueue.Cycle cycle = queue.drainCycle(worldName, targetUuid);
        for (MiniwyvernOwnerAuraRegistry.Aura aura : cycle.reapplications()) {
            if (!registry.isCurrentAura(aura)) continue;
            add(target, targetUuid, aura, controller, commandBuffer);
        }
        for (MiniwyvernOwnerAuraRegistry.Aura aura : cycle.applications()) {
            if (!registry.isCurrentAura(aura)) continue;
            applyOrRestart(worldName, target, targetUuid, aura, controller, commandBuffer);
        }
    }

    private void applyOrRestart(
            String worldName,
            Ref<EntityStore> target,
            UUID targetUuid,
            MiniwyvernOwnerAuraRegistry.Aura aura,
            EffectControllerComponent controller,
            CommandBuffer<EntityStore> commandBuffer) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(aura.effectId());
        if (effect == null) {
            return;
        }

        boolean activeBefore = controller.hasEffect(effect);
        com.hypixel.hytale.protocol.EntityEffect packet = effect.toPacket();
        String modelVfxId = packet.applicationEffects == null
                ? null : packet.applicationEffects.modelVFXId;
        if (requiresModelVfxRestart(activeBefore, modelVfxId)) {
            int effectIndex = EntityEffect.getAssetMap().getIndex(aura.effectId());
            controller.removeEffect(
                    target, effectIndex, RemovalBehavior.COMPLETE, commandBuffer);
            queue.submitAfterRemoval(worldName, targetUuid, aura);
            return;
        }

        add(target, targetUuid, aura, controller, commandBuffer);
    }

    private void add(
            Ref<EntityStore> target,
            UUID targetUuid,
            MiniwyvernOwnerAuraRegistry.Aura aura,
            EffectControllerComponent controller,
            CommandBuffer<EntityStore> commandBuffer) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(aura.effectId());
        if (effect == null) {
            return;
        }

        // This is the same asset-authored EffectController call used by Tamework projectile
        // ImpactEffect and Hytale's ApplyEffect interaction.
        boolean applied = controller.addEffect(target, effect, commandBuffer);
        if (applied && "void".equals(aura.formId())) {
            voidLifetime.observe(targetUuid);
        }
        if (applied) {
            registry.recordTargetAura(targetUuid, aura, effect.getDuration(), System.currentTimeMillis());
        }
    }

    static boolean requiresModelVfxRestart(boolean activeBefore, @Nullable String modelVfxId) {
        return activeBefore && modelVfxId != null && !modelVfxId.isBlank();
    }

}
