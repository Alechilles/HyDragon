package com.alechilles.hydragon.abilities;

import com.alechilles.alecstamework.api.NpcProfileView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.joml.Vector3d;

/**
 * Hytale 0.5.6 world-thread adapter for effects, health, hostile target slots, damage, and legacy projectiles.
 * Unsupported raw action/jump modifiers are reported as unavailable instead of being approximated.
 */
public final class HytaleMiniwyvernAbilityWorldDispatcher implements MiniwyvernAbilityWorldDispatcher {
    private static final String[] HOSTILE_TARGET_SLOTS = {"CAETargetSlot", "LockedTarget", "AttackTarget"};
    private final TameworkApi api;

    public HytaleMiniwyvernAbilityWorldDispatcher(TameworkApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public void dispatch(UUID ownerUuid, UUID npcUuid, Consumer<MiniwyvernAbilityWorld> callback) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(npcUuid, "npcUuid");
        Objects.requireNonNull(callback, "callback");
        Universe universe = Universe.get();
        if (universe == null) return;
        for (World world : universe.getWorlds().values()) {
            try {
                world.execute(() -> {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    Ref<EntityStore> ownerRef = world.getEntityRef(ownerUuid);
                    Ref<EntityStore> npcRef = world.getEntityRef(npcUuid);
                    // Cleanup after a death/unload still needs the loaded owner even when the
                    // companion projection has already disappeared. Normal ticks fail their
                    // projection check through companion().
                    if (!valid(ownerRef)) return;
                    callback.accept(new Port(api, world, store, ownerUuid, npcUuid, ownerRef, npcRef));
                });
            } catch (RejectedExecutionException ignored) {
                // World shutdown is a normal fail-closed lifecycle boundary.
            }
        }
    }

    private static boolean valid(Ref<EntityStore> ref) {
        return ref != null && ref.isValid();
    }

    private static final class Port implements MiniwyvernAbilityWorld {
        private final TameworkApi api;
        private final World world;
        private final Store<EntityStore> store;
        private final UUID ownerUuid;
        private final UUID npcUuid;
        private final Ref<EntityStore> ownerRef;
        private final Ref<EntityStore> npcRef;

        private Port(TameworkApi api, World world, Store<EntityStore> store,
                     UUID ownerUuid, UUID npcUuid, Ref<EntityStore> ownerRef, Ref<EntityStore> npcRef) {
            this.api = api;
            this.world = world;
            this.store = store;
            this.ownerUuid = ownerUuid;
            this.npcUuid = npcUuid;
            this.ownerRef = ownerRef;
            this.npcRef = npcRef;
        }

        @Override public boolean isWorldThread() { return world.isInThread(); }
        @Override public String worldName() { return world.getName(); }
        @Override public Optional<Target> owner() { return target(ownerRef, ownerUuid); }
        @Override public Optional<Target> companion() { return target(npcRef, ownerUuid); }

        @Override
        public Optional<Target> hostileTarget(double maximumRange) {
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc == null || npc.getRole() == null || npc.getRole().getMarkedEntitySupport() == null) {
                return Optional.empty();
            }
            for (String slot : HOSTILE_TARGET_SLOTS) {
                Ref<EntityStore> candidate = npc.getRole().getMarkedEntitySupport().getMarkedEntityRef(slot);
                if (!valid(candidate)) continue;
                Target resolved = target(candidate, resolveOwner(candidate)).orElse(null);
                if (resolved == null || resolved.distance() > maximumRange || !resolved.alive()) continue;
                try {
                    Attitude attitude = npc.getRole().getWorldSupport().getAttitude(npcRef, candidate, store);
                    if (attitude == Attitude.HOSTILE) return Optional.of(resolved);
                } catch (RuntimeException ignored) {
                    // Unknown attitude is not treated as hostile.
                }
            }
            return Optional.empty();
        }

        @Override
        public Health health(UUID entityUuid) {
            Ref<EntityStore> ref = resolve(entityUuid);
            if (!valid(ref)) return new Health(0.0D, 0.0D);
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
            if (value == null || !Float.isFinite(value.get()) || !Float.isFinite(value.getMax()) || value.getMax() < 0) {
                return new Health(0.0D, 0.0D);
            }
            double max = Math.max(0.0D, value.getMax());
            return new Health(Math.max(0.0D, Math.min(max, value.get())), max);
        }

        @Override
        public boolean applyEffect(UUID entityUuid, String sourceKey, String effectId, double durationSeconds) {
            Ref<EntityStore> ref = resolve(entityUuid);
            if (!valid(ref) || effectId == null || effectId.isBlank()) return false;
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
            EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (effect == null || controller == null) return false;
            float duration = durationSeconds > 0.0D && Double.isFinite(durationSeconds)
                    ? (float) durationSeconds : Math.max(0.05F, effect.getDuration());
            return controller.addEffect(ref, effect, duration, OverlapBehavior.OVERWRITE, store);
        }

        @Override
        public boolean removeEffect(UUID entityUuid, String sourceKey, String effectId) {
            Ref<EntityStore> ref = resolve(entityUuid);
            // An unloaded/deleted target has no live effect left to remove. Treat that as an
            // idempotent cleanup success so durable source tracking can converge.
            if (!valid(ref)) return true;
            if (effectId == null || effectId.isBlank()) return false;
            int index = EntityEffect.getAssetMap().getIndex(effectId);
            EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (index < 0 || controller == null) return false;
            if (!controller.getActiveEffects().containsKey(index)) return true;
            controller.removeEffect(ref, index, RemovalBehavior.COMPLETE, store);
            return true;
        }

        @Override
        public boolean supportsOwnerModifiers(Map<String, Double> modifiers) {
            // 0.5.6 EntityEffect assets express horizontal speed, but expose no safe source-keyed
            // action-speed/jump/mobility mutation API. Lightning/Wind therefore stay disabled.
            return modifiers == null || modifiers.isEmpty();
        }

        @Override
        public boolean applyOwnerModifiers(UUID ownerUuid, String sourceKey,
                                           Map<String, Double> modifiers, double durationSeconds) {
            return modifiers == null || modifiers.isEmpty();
        }

        @Override
        public boolean removeOwnerModifiers(UUID ownerUuid, String sourceKey) {
            return true;
        }

        @Override
        public boolean launchProjectile(UUID sourceUuid, UUID targetUuid, String projectileId) {
            Ref<EntityStore> source = resolve(sourceUuid);
            Ref<EntityStore> target = resolve(targetUuid);
            if (!valid(source) || !valid(target) || projectileId == null || projectileId.isBlank()) return false;
            Projectile projectile = Projectile.getAssetMap().getAsset(projectileId);
            TransformComponent sourceTransform = store.getComponent(source, TransformComponent.getComponentType());
            TransformComponent targetTransform = store.getComponent(target, TransformComponent.getComponentType());
            UUIDComponent sourceIdentity = store.getComponent(source, UUIDComponent.getComponentType());
            if (projectile == null || sourceTransform == null || targetTransform == null || sourceIdentity == null) {
                return false;
            }
            Vector3d origin = new Vector3d(sourceTransform.getPosition()).add(0.0D, 0.5D, 0.0D);
            Vector3d delta = new Vector3d(targetTransform.getPosition()).sub(origin);
            double horizontal = Math.hypot(delta.x, delta.z);
            float yaw = PhysicsMath.headingFromDirection(delta.x, delta.z);
            float pitch = (float) Math.atan2(delta.y, Math.max(0.0001D, horizontal));
            TimeResource time = store.getResource(TimeResource.getResourceType());
            Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                    time, projectileId, origin, new Rotation3f(pitch, yaw, 0.0F));
            ProjectileComponent component = holder.getComponent(ProjectileComponent.getComponentType());
            if (component == null) return false;
            holder.ensureComponent(Intangible.getComponentType());
            component.shoot(holder, sourceIdentity.getUuid(), origin.x, origin.y, origin.z, yaw, pitch);
            return store.addEntity(holder, AddReason.SPAWN) != null;
        }

        @Override
        public boolean dealDamage(UUID sourceUuid, UUID targetUuid, double amount) {
            if (!Double.isFinite(amount) || amount <= 0.0D || amount > Float.MAX_VALUE) return false;
            Ref<EntityStore> source = resolve(sourceUuid);
            Ref<EntityStore> target = resolve(targetUuid);
            if (!valid(source) || !valid(target) || DamageCause.PHYSICAL == null) return false;
            DamageSystems.executeDamage(target, store,
                    new Damage(new Damage.EntitySource(source), DamageCause.PHYSICAL, (float) amount));
            return true;
        }

        @Override
        public boolean heal(UUID entityUuid, double amount) {
            if (!Double.isFinite(amount) || amount <= 0.0D || amount > Float.MAX_VALUE) return false;
            Ref<EntityStore> ref = resolve(entityUuid);
            EntityStatMap stats = valid(ref) ? store.getComponent(ref, EntityStatMap.getComponentType()) : null;
            EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
            if (value == null || value.get() >= value.getMax()) return false;
            stats.setStatValue(DefaultEntityStatTypes.getHealth(),
                    (float) Math.min(value.getMax(), value.get() + amount));
            return true;
        }

        @Override
        public boolean areAllies(UUID ownerUuid, UUID targetUuid) {
            if (ownerUuid.equals(targetUuid)) return true;
            Ref<EntityStore> target = resolve(targetUuid);
            UUID targetOwner = valid(target) ? resolveOwner(target) : null;
            if (ownerUuid.equals(targetOwner)) return true;
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc == null || npc.getRole() == null || !valid(target)) return true;
            try {
                return npc.getRole().getWorldSupport().getAttitude(npcRef, target, store) != Attitude.HOSTILE;
            } catch (RuntimeException failure) {
                return true;
            }
        }

        private Optional<Target> target(Ref<EntityStore> ref, UUID targetOwner) {
            if (!valid(ref)) return Optional.empty();
            UUIDComponent identity = store.getComponent(ref, UUIDComponent.getComponentType());
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            TransformComponent companionTransform = valid(npcRef)
                    ? store.getComponent(npcRef, TransformComponent.getComponentType()) : null;
            if (identity == null || transform == null || companionTransform == null) return Optional.empty();
            double distance = transform.getPosition().distance(companionTransform.getPosition());
            return Optional.of(new Target(identity.getUuid(), targetOwner, world.getName(), distance,
                    health(identity.getUuid()).current() > 0.0D));
        }

        private UUID resolveOwner(Ref<EntityStore> ref) {
            UUIDComponent identity = store.getComponent(ref, UUIDComponent.getComponentType());
            if (identity == null) return null;
            try {
                return api.profiles().getByNpcUuid(identity.getUuid()).map(NpcProfileView::ownerUuid).orElse(null);
            } catch (RuntimeException failure) {
                return null;
            }
        }

        private Ref<EntityStore> resolve(UUID uuid) {
            if (uuid == null) return null;
            if (uuid.equals(ownerUuid)) return ownerRef;
            if (uuid.equals(npcUuid)) return npcRef;
            return world.getEntityRef(uuid);
        }
    }
}
