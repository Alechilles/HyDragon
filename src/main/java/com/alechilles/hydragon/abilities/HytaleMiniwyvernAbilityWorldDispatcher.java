package com.alechilles.hydragon.abilities;

import com.alechilles.alecstamework.api.NpcProfileView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.protocol.SoundCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.joml.Vector3d;

/**
 * Hytale 0.5.6 world-thread adapter for effects, health, hostile target slots, damage, and legacy projectiles.
 * Unsupported raw action/jump modifiers are reported as unavailable instead of being approximated.
 */
public final class HytaleMiniwyvernAbilityWorldDispatcher implements MiniwyvernAbilityWorldDispatcher {
    private static final String[] HOSTILE_TARGET_SLOTS = {"CAETargetSlot", "LockedTarget", "AttackTarget"};
    private static final Logger LOGGER = Logger.getLogger(HytaleMiniwyvernAbilityWorldDispatcher.class.getName());
    private static final Set<String> PRESENTATION_WARNINGS = ConcurrentHashMap.newKeySet();
    private final TameworkApi api;
    private final SourceKeyedEffectRegistry effectSources = new SourceKeyedEffectRegistry();

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
                    callback.accept(new Port(api, effectSources, world, store, ownerUuid, npcUuid, ownerRef, npcRef));
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
        private final SourceKeyedEffectRegistry effectSources;
        private final World world;
        private final Store<EntityStore> store;
        private final UUID ownerUuid;
        private final UUID npcUuid;
        private final Ref<EntityStore> ownerRef;
        private final Ref<EntityStore> npcRef;

        private Port(TameworkApi api, SourceKeyedEffectRegistry effectSources, World world, Store<EntityStore> store,
                     UUID ownerUuid, UUID npcUuid, Ref<EntityStore> ownerRef, Ref<EntityStore> npcRef) {
            this.api = api;
            this.effectSources = effectSources;
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
        public List<Target> hostileTargets(double maximumRange, int maximumTargets) {
            if (!valid(npcRef) || maximumTargets <= 0 || !Double.isFinite(maximumRange) || maximumRange < 0.0D) {
                return List.of();
            }
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
            SpatialResource<Ref<EntityStore>, EntityStore> spatial =
                    store.getResource(EntityModule.get().getEntitySpatialResourceType());
            if (npc == null || npc.getRole() == null || transform == null || spatial == null) return List.of();

            List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
            spatial.getSpatialStructure().collect(transform.getPosition(), maximumRange, nearby);
            List<Target> targets = new ArrayList<>();
            for (Ref<EntityStore> candidate : nearby) {
                if (!valid(candidate) || candidate.equals(ownerRef) || candidate.equals(npcRef)) continue;
                Target resolved = target(candidate, resolveOwner(candidate)).orElse(null);
                if (resolved == null || !resolved.alive() || resolved.distance() > maximumRange) continue;
                try {
                    if (npc.getRole().getWorldSupport().getAttitude(npcRef, candidate, store) == Attitude.HOSTILE) {
                        targets.add(resolved);
                    }
                } catch (RuntimeException ignored) {
                    // Unknown attitude is not hostile.
                }
            }
            return targets.stream()
                    .sorted(Comparator.comparingDouble(Target::distance).thenComparing(Target::entityUuid))
                    .limit(maximumTargets)
                    .toList();
        }

        @Override
        public boolean synchronizeAppearance(UUID entityUuid, String appearanceId) {
            Ref<EntityStore> ref = resolve(entityUuid);
            if (!valid(ref) || appearanceId == null || appearanceId.isBlank()) return false;
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(appearanceId);
            ModelComponent component = store.getComponent(ref, ModelComponent.getComponentType());
            Model current = component == null ? null : component.getModel();
            if (asset == null || current == null) return false;
            if (appearanceId.equals(current.getModelAssetId())) return true;
            float scale = Math.max(asset.getMinScale(), Math.min(asset.getMaxScale(), current.getScale()));
            store.putComponent(ref, ModelComponent.getComponentType(),
                    new ModelComponent(Model.createScaledModel(asset, scale)));
            return true;
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
            long nowMs = System.currentTimeMillis();
            long requestedExpiry = saturatingAdd(nowMs, Math.max(1L, Math.round(duration * 1_000.0D)));
            SourceKeyedEffectRegistry.EffectKey key =
                    new SourceKeyedEffectRegistry.EffectKey(world.getName(), entityUuid, effectId);
            SourceKeyedEffectRegistry.RetainResult retained =
                    effectSources.retain(key, sourceKey, requestedExpiry, nowMs);
            float effectiveDuration = Math.max(0.05F, (retained.effectiveExpiryMs() - nowMs) / 1_000.0F);
            boolean applied = controller.addEffect(ref, effect, effectiveDuration, OverlapBehavior.OVERWRITE, store);
            if (!applied) effectSources.rollback(key, sourceKey, retained.previousExpiryMs());
            return applied;
        }

        @Override
        public boolean removeEffect(UUID entityUuid, String sourceKey, String effectId) {
            Ref<EntityStore> ref = resolve(entityUuid);
            // An unloaded/deleted target has no live effect left to remove. Treat that as an
            // idempotent cleanup success so durable source tracking can converge.
            if (!valid(ref)) return true;
            if (effectId == null || effectId.isBlank()) return false;
            SourceKeyedEffectRegistry.ReleaseResult release = effectSources.release(
                    new SourceKeyedEffectRegistry.EffectKey(world.getName(), entityUuid, effectId),
                    sourceKey,
                    System.currentTimeMillis());
            if (!release.removeUnderlyingEffect()) return true;
            int index = EntityEffect.getAssetMap().getIndex(effectId);
            EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (index < 0 || controller == null) return false;
            if (!controller.getActiveEffects().containsKey(index)) return true;
            controller.removeEffect(ref, index, RemovalBehavior.COMPLETE, store);
            return true;
        }

        @Override
        public boolean supportsPassiveModifierEffect(
                String modifierId,
                double requestedValue,
                double configuredMaximum,
                String effectId) {
            if (!"MovementSpeedMultiplier".equals(modifierId)
                    || !Double.isFinite(requestedValue) || requestedValue <= 0.0D
                    || !Double.isFinite(configuredMaximum) || configuredMaximum < requestedValue
                    || effectId == null || effectId.isBlank()) {
                return false;
            }
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
            if (effect == null || effect.getApplicationEffects() == null) return false;
            float actual = effect.getApplicationEffects().getHorizontalSpeedMultiplier();
            return Float.isFinite(actual) && approximatelyEqual(actual, requestedValue);
        }

        @Override
        public boolean supportsEffectStacking(String effectId, String stackingPolicy, int maximumStacks) {
            if (effectId == null || effectId.isBlank() || stackingPolicy == null
                    || maximumStacks != 1) {
                return false;
            }
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
            if (effect == null) return false;
            String policy = stackingPolicy.trim().toUpperCase(java.util.Locale.ROOT);
            if (!Set.of("SOURCE_REFRESH", "NON_STACKING", "CLAMPED", "THRESHOLD_STUN_100")
                    .contains(policy)) {
                return false;
            }
            // Hytale 0.5.6 exposes one controller slot per effect asset. A stable logical source
            // with overwrite/ignore behavior truthfully implements refresh or one capped stack;
            // it cannot express a bounded stack count greater than one.
            return effect.getOverlapBehavior() == OverlapBehavior.OVERWRITE
                    || effect.getOverlapBehavior() == OverlapBehavior.IGNORE;
        }

        @Override
        public boolean supportsBoundedDefenseReduction(
                String effectId,
                double requestedReduction,
                double minimumDefenseMultiplier,
                double maximumReduction) {
            if (effectId == null || effectId.isBlank()
                    || !Double.isFinite(requestedReduction) || requestedReduction <= 0.0D
                    || !Double.isFinite(minimumDefenseMultiplier)
                    || minimumDefenseMultiplier <= 0.0D || minimumDefenseMultiplier > 1.0D
                    || !Double.isFinite(maximumReduction)
                    || maximumReduction <= 0.0D || maximumReduction >= 1.0D) {
                return false;
            }
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
            if (effect == null || effect.getDamageResistanceValues() == null
                    || effect.getDamageResistanceValues().isEmpty()) {
                return false;
            }
            boolean matchedRequestedReduction = false;
            for (ResistanceModifier[] modifiers : effect.getDamageResistanceValues().values()) {
                if (modifiers == null || modifiers.length == 0) return false;
                for (ResistanceModifier modifier : modifiers) {
                    if (modifier == null
                            || modifier.getCalculationType()
                            != ResistanceModifier.ResistanceCalculationType.PERCENT
                            || !Float.isFinite(modifier.getAmount())
                            || modifier.getAmount() >= 0.0F) {
                        return false;
                    }
                    double reduction = -modifier.getAmount();
                    if (reduction > maximumReduction + 0.000_001D
                            || 1.0D - reduction < minimumDefenseMultiplier - 0.000_001D) {
                        return false;
                    }
                    matchedRequestedReduction |= approximatelyEqual(reduction, requestedReduction);
                }
            }
            return matchedRequestedReduction
                    && requestedReduction <= maximumReduction + 0.000_001D
                    && 1.0D - requestedReduction >= minimumDefenseMultiplier - 0.000_001D;
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
        public int emitPresentation(UUID entityUuid, List<String> particleAndSoundIds) {
            Ref<EntityStore> ref = resolve(entityUuid);
            TransformComponent transform = valid(ref)
                    ? store.getComponent(ref, TransformComponent.getComponentType()) : null;
            if (transform == null || particleAndSoundIds == null || particleAndSoundIds.isEmpty()) return 0;
            int emitted = 0;
            Vector3d position = transform.getPosition();
            for (String rawId : particleAndSoundIds) {
                String id = rawId == null ? "" : rawId.trim();
                if (id.isEmpty()) continue;
                try {
                    if (ParticleSystem.getAssetMap().getAsset(id) != null) {
                        ParticleUtil.spawnParticleEffect(id, position, store);
                        emitted++;
                        continue;
                    }
                    int soundIndex = SoundEvent.getAssetMap().getIndex(id);
                    if (soundIndex >= 0) {
                        SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, position, store);
                        emitted++;
                        continue;
                    }
                    warnPresentationOnce(id, "asset is neither a loaded ParticleSystem nor SoundEvent");
                } catch (RuntimeException failure) {
                    warnPresentationOnce(id, failure.getClass().getSimpleName());
                }
            }
            return emitted;
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

        private static long saturatingAdd(long left, long right) {
            return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
        }

        private static boolean approximatelyEqual(double left, double right) {
            return Math.abs(left - right) <= 0.000_01D;
        }

        private static void warnPresentationOnce(String id, String reason) {
            String key = id + ':' + reason;
            if (PRESENTATION_WARNINGS.add(key)) {
                LOGGER.warning("Miniwyvern presentation asset '" + id + "' was skipped: " + reason);
            }
        }
    }
}
