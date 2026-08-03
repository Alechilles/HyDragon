package com.alechilles.hydragon.abilities;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies the Void Bond's owner heal once per configured server-side cooldown. */
public final class MiniwyvernAuraSiphonDamageSystem extends DamageEventSystem implements AutoCloseable {
    private final MiniwyvernOwnerAuraRegistry registry;
    private final ConcurrentHashMap<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final AutoCloseable registryClearHook;
    private final AutoCloseable registryOwnerClearHook;

    public MiniwyvernAuraSiphonDamageSystem(MiniwyvernOwnerAuraRegistry registry) {
        this(registry, System::currentTimeMillis);
    }

    public MiniwyvernAuraSiphonDamageSystem(
            MiniwyvernOwnerAuraRegistry registry, LongSupplier clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.registryClearHook = registry.addClearHook(cooldownUntil::clear);
        this.registryOwnerClearHook = registry.addOwnerClearHook(cooldownUntil::remove);
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(UUIDComponent.getComponentType(), EffectControllerComponent.getComponentType());
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        if (!(damage.getSource() instanceof Damage.EntitySource source)) return;
        Ref<EntityStore> sourceRef = source.getRef();
        if (!MiniwyvernOwnerAuraDamageSystem.isLiveRef(sourceRef)) return;
        PlayerRef player = store.getComponent(sourceRef, PlayerRef.getComponentType());
        UUIDComponent ownerIdentity = store.getComponent(sourceRef, UUIDComponent.getComponentType());
        if (player == null || !player.isValid() || ownerIdentity == null) return;

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        UUIDComponent targetIdentity = chunk.getComponent(index, UUIDComponent.getComponentType());
        EffectControllerComponent targetEffects =
                chunk.getComponent(index, EffectControllerComponent.getComponentType());
        MiniwyvernOwnerAuraRegistry.Aura aura = registry.activeFor(ownerIdentity.getUuid()).orElse(null);
        boolean matchingTargetStatus = aura != null
                && hasActiveEffect(targetEffects, aura.effectId());
        boolean blocked = Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
        boolean healing = damage.getCause() != null
                && "healing".equalsIgnoreCase(damage.getCause().getId());
        if (targetIdentity == null || !shouldSiphon(
                damage.isCancelled(), damage.getAmount(),
                MiniwyvernOwnerAuraDamageSystem.isLiveRef(sourceRef),
                sourceRef != null && sourceRef.equals(targetRef), blocked, healing,
                player.isValid(), matchingTargetStatus, aura)) {
            return;
        }

        EntityStatMap stats = store.getComponent(sourceRef, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || !Float.isFinite(health.get()) || !Float.isFinite(health.getMax())
                || health.getMax() <= health.get()) return;
        double amount = healAmount(health.getMax(), aura.siphonMaximumHealthFraction());
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        if (!trySiphon(ownerIdentity.getUuid(), aura, clock.getAsLong())) return;
        stats.setStatValue(DefaultEntityStatTypes.getHealth(),
                (float) Math.min(health.getMax(), health.get() + amount));
    }

    /** Atomically accepts a siphon hit when the owner's Void cooldown is ready. */
    public boolean trySiphon(
            UUID ownerUuid, MiniwyvernOwnerAuraRegistry.Aura aura, long nowMs) {
        if (ownerUuid == null || aura == null || !ownerUuid.equals(aura.ownerUuid())
                || nowMs < 0L || !"void".equalsIgnoreCase(aura.formId())
                || !Double.isFinite(aura.siphonMaximumHealthFraction())
                || aura.siphonMaximumHealthFraction() <= 0.0D
                || aura.siphonMaximumHealthFraction() >= 1.0D
                || aura.siphonCooldownMs() <= 0L) {
            if (ownerUuid != null) cooldownUntil.remove(ownerUuid);
            return false;
        }
        long nextReady = saturatingAdd(nowMs, aura.siphonCooldownMs());
        final boolean[] accepted = {false};
        cooldownUntil.compute(ownerUuid, (ignored, current) -> {
            if (current != null && nowMs < current) return current;
            accepted[0] = true;
            return nextReady;
        });
        return accepted[0];
    }

    static boolean shouldSiphon(
            boolean cancelled,
            float amount,
            boolean entityCaused,
            boolean self,
            boolean blocked,
            boolean healing,
            boolean playerSource,
            boolean matchingTargetStatus,
            @Nullable MiniwyvernOwnerAuraRegistry.Aura aura) {
        return !cancelled && Float.isFinite(amount) && amount > 0.0F
                && entityCaused && !self && !blocked && !healing && playerSource
                && matchingTargetStatus && aura != null
                && "void".equalsIgnoreCase(aura.formId())
                && aura.siphonMaximumHealthFraction() > 0.0D;
    }

    static double healAmount(double maximumHealth, double fraction) {
        return Double.isFinite(maximumHealth) && maximumHealth > 0.0D
                && Double.isFinite(fraction) && fraction > 0.0D
                ? maximumHealth * fraction : 0.0D;
    }

    private static boolean hasActiveEffect(
            @Nullable EffectControllerComponent controller, @Nullable String effectId) {
        if (controller == null || effectId == null || effectId.isBlank()) return false;
        int effectIndex = com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect
                .getAssetMap().getIndex(effectId);
        return effectIndex >= 0 && controller.getActiveEffects().containsKey(effectIndex);
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    /** Clears cooldown state and unregisters the registry lifecycle hook. */
    public void clear() {
        cooldownUntil.clear();
    }

    @Override
    public void close() {
        clear();
        try {
            registryClearHook.close();
        } catch (Exception ignored) {
            // Hook cleanup is best-effort during plugin shutdown.
        }
        try {
            registryOwnerClearHook.close();
        } catch (Exception ignored) {
            // Hook cleanup is best-effort during plugin shutdown.
        }
    }
}
