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
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.livingentity.LivingEntityEffectClearChangesSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies a currently summoned Miniwyvern's owner-hit aura after Hytale's damage filters. */
public final class MiniwyvernOwnerAuraDamageSystem extends DamageEventSystem {
    private static final Logger LOGGER = Logger.getLogger(MiniwyvernOwnerAuraDamageSystem.class.getName());
    private static final long VOID_DIAGNOSTIC_INTERVAL_MS = 2_000L;
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.BEFORE, EntityTrackerSystems.EffectControllerSystem.class));
    private final MiniwyvernOwnerAuraRegistry registry;
    private final MiniwyvernVoidEffectLifetimeSystem voidLifetime;
    private final ConcurrentHashMap<UUID, Long> lastVoidDiagnosticAt = new ConcurrentHashMap<>();
    private final AtomicBoolean schedulingLogged = new AtomicBoolean();

    public MiniwyvernOwnerAuraDamageSystem(MiniwyvernOwnerAuraRegistry registry) {
        this(registry, new MiniwyvernVoidEffectLifetimeSystem());
    }

    public MiniwyvernOwnerAuraDamageSystem(
            MiniwyvernOwnerAuraRegistry registry, MiniwyvernVoidEffectLifetimeSystem voidLifetime) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.voidLifetime = Objects.requireNonNull(voidLifetime, "voidLifetime");
    }

    @Nullable @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getInspectDamageGroup(); }
    @Nonnull @Override public Set<Dependency<EntityStore>> getDependencies() { return DEPENDENCIES; }
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
        logEffectScheduling(store);
        EffectControllerComponent controller = commandBuffer.getComponent(target, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(aura.effectId());
        if (controller == null || effect == null) {
            logVoidApplication(aura, store.getComponent(target, UUIDComponent.getComponentType()),
                    false, false, false, controller == null ? "missing-controller" : "missing-effect");
            return;
        }
        boolean activeBefore = controller.hasEffect(effect);
        boolean applied = controller.addEffect(target, effect,
                (float) aura.durationSeconds(), OverlapBehavior.OVERWRITE, commandBuffer);
        boolean activeAfter = controller.hasEffect(effect);
        logVoidApplication(aura, store.getComponent(target, UUIDComponent.getComponentType()),
                activeBefore, applied, activeAfter, "applied");
        if (applied && "void".equals(aura.formId())) {
            UUIDComponent targetIdentity = store.getComponent(target, UUIDComponent.getComponentType());
            if (targetIdentity != null) voidLifetime.observe(targetIdentity.getUuid());
        }
        if (applied && aura.damageReductionFraction() > 0.0D) {
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

    private void logEffectScheduling(Store<EntityStore> store) {
        if (!schedulingLogged.compareAndSet(false, true)) return;
        int auraSystem = -1;
        int effectTracker = -1;
        int clearChanges = -1;
        var data = store.getRegistry().getData();
        for (int index = 0; index < data.getSystemSize(); index++) {
            ISystem<EntityStore> system = data.getSystem(index);
            if (system == this) auraSystem = index;
            if (system instanceof EntityTrackerSystems.EffectControllerSystem) effectTracker = index;
            if (system instanceof LivingEntityEffectClearChangesSystem) clearChanges = index;
        }
        LOGGER.info("Miniwyvern owner-hit effect schedule: aura=" + auraSystem
                + ", effectTracker=" + effectTracker + ", clearChanges=" + clearChanges);
    }

    private void logVoidApplication(
            MiniwyvernOwnerAuraRegistry.Aura aura,
            @Nullable UUIDComponent targetIdentity,
            boolean activeBefore,
            boolean applied,
            boolean activeAfter,
            String outcome) {
        if (!"void".equals(aura.formId()) || targetIdentity == null) return;
        UUID targetUuid = targetIdentity.getUuid();
        long nowMs = System.currentTimeMillis();
        Long last = lastVoidDiagnosticAt.put(targetUuid, nowMs);
        if (last != null && nowMs - last < VOID_DIAGNOSTIC_INTERVAL_MS) return;
        LOGGER.info(() -> "Void owner-hit aura " + outcome + " for " + targetUuid
                + ": activeBefore=" + activeBefore + ", addEffect=" + applied
                + ", activeAfter=" + activeAfter + ", duration=" + aura.durationSeconds());
    }

}
