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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies owner-hit auras in a dedicated ECS phase, matching projectile impact effects. */
public final class MiniwyvernOwnerAuraEffectSystem extends EntityTickingSystem<EntityStore> {
    private static final Logger LOGGER = Logger.getLogger(MiniwyvernOwnerAuraEffectSystem.class.getName());
    private static final long VOID_DIAGNOSTIC_INTERVAL_MS = 2_000L;
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.AFTER, MiniwyvernOwnerAuraDamageSystem.class),
            new SystemDependency<>(Order.BEFORE, EntityTrackerSystems.EffectControllerSystem.class));

    private final MiniwyvernOwnerAuraEffectQueue queue;
    private final MiniwyvernOwnerAuraRegistry registry;
    private final MiniwyvernVoidEffectLifetimeSystem voidLifetime;
    private final MiniwyvernVoidEffectReplicationProbe replicationProbe;
    private final ConcurrentHashMap<UUID, Long> lastVoidDiagnosticAt = new ConcurrentHashMap<>();

    public MiniwyvernOwnerAuraEffectSystem(
            MiniwyvernOwnerAuraEffectQueue queue,
            MiniwyvernOwnerAuraRegistry registry,
            MiniwyvernVoidEffectLifetimeSystem voidLifetime,
            MiniwyvernVoidEffectReplicationProbe replicationProbe) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.voidLifetime = Objects.requireNonNull(voidLifetime, "voidLifetime");
        this.replicationProbe = Objects.requireNonNull(replicationProbe, "replicationProbe");
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
            add(target, targetUuid, aura, controller, commandBuffer);
        }
        for (MiniwyvernOwnerAuraRegistry.Aura aura : cycle.applications()) {
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
            logVoidApplication(aura, targetUuid, false, false, false, "missing-effect", 0.0F);
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
            logVoidApplication(aura, targetUuid, true, false,
                    controller.hasEffect(effect), "restart-removed", effect.getDuration());
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
            logVoidApplication(aura, targetUuid, false, false, false, "missing-effect", 0.0F);
            return;
        }

        boolean activeBefore = controller.hasEffect(effect);
        // This is the same asset-authored EffectController call used by Tamework projectile
        // ImpactEffect and Hytale's ApplyEffect interaction.
        boolean applied = controller.addEffect(target, effect, commandBuffer);
        boolean activeAfter = controller.hasEffect(effect);
        replicationProbe.observeApplication(
                targetUuid,
                EntityEffect.getAssetMap().getIndex(aura.effectId()),
                aura.formId(),
                applied);
        logVoidApplication(aura, targetUuid, activeBefore, applied, activeAfter,
                "applied", effect.getDuration());
        if (applied && "void".equals(aura.formId())) {
            voidLifetime.observe(targetUuid);
        }
        if (applied && aura.damageReductionFraction() > 0.0D) {
            registry.recordToxicWeakness(targetUuid, aura.effectId(), aura.damageReductionFraction(),
                    effect.getDuration(), System.currentTimeMillis());
        }
    }

    static boolean requiresModelVfxRestart(boolean activeBefore, @Nullable String modelVfxId) {
        return activeBefore && modelVfxId != null && !modelVfxId.isBlank();
    }

    private void logVoidApplication(
            MiniwyvernOwnerAuraRegistry.Aura aura,
            UUID targetUuid,
            boolean activeBefore,
            boolean applied,
            boolean activeAfter,
            String outcome,
            float durationSeconds) {
        if (!"void".equals(aura.formId())) return;
        long nowMs = System.currentTimeMillis();
        Long last = lastVoidDiagnosticAt.put(targetUuid, nowMs);
        if (last != null && nowMs - last < VOID_DIAGNOSTIC_INTERVAL_MS) return;
        LOGGER.info(() -> "Void owner-hit aura " + outcome + " for " + targetUuid
                + ": activeBefore=" + activeBefore + ", addEffect=" + applied
                + ", activeAfter=" + activeAfter + ", authoredDuration=" + durationSeconds);
    }
}
