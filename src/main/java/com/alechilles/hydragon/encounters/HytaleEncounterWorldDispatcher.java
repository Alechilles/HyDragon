package com.alechilles.hydragon.encounters;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import com.hypixel.hytale.server.spawning.SpawningContext;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.joml.Vector3d;

/** Concrete Hytale 0.5.6 world-thread gateway for special encounter entities. */
public final class HytaleEncounterWorldDispatcher implements EncounterWorldDispatcher {
    private static final int[][] SAFE_OFFSETS = {
            {0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4},
            {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {6, 6}, {-6, 6}, {6, -6}, {-6, -6}
    };

    private final ComponentType<EntityStore, HyDragonEncounterComponent> markerType;

    public HytaleEncounterWorldDispatcher(
            ComponentType<EntityStore, HyDragonEncounterComponent> markerType) {
        this.markerType = Objects.requireNonNull(markerType, "markerType");
    }

    @Override
    public void dispatch(String worldName, UUID targetNpcUuid, Consumer<EncounterWorldGateway> callback) {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(callback, "callback");
        Universe universe = Universe.get();
        World world = universe == null ? null : universe.getWorlds().get(worldName);
        if (world == null) return;
        try {
            world.execute(() -> callback.accept(new Port(world, markerType)));
        } catch (RejectedExecutionException ignored) {
            // World shutdown is an expected fail-closed lifecycle boundary.
        }
    }

    private static final class Port implements EncounterWorldGateway {
        private final World world;
        private final Store<EntityStore> store;
        private final ComponentType<EntityStore, HyDragonEncounterComponent> markerType;

        private Port(World world, ComponentType<EntityStore, HyDragonEncounterComponent> markerType) {
            this.world = world;
            this.store = world.getEntityStore().getStore();
            this.markerType = markerType;
        }

        @Override public boolean isWorldThread() { return world.isInThread(); }

        @Override
        public SpawnResult spawn(SpawnRequest request) {
            TargetLookup replay = findTarget(request.encounterId(), world.getName(), null);
            if (replay.presence() == TargetPresence.PRESENT) {
                return SpawnResult.success(replay.targetNpcUuid().orElseThrow());
            }

            NPCPlugin npcPlugin = NPCPlugin.get();
            int roleIndex = npcPlugin.getIndex(request.targetRoleId());
            var roleBuilder = roleIndex < 0 ? null : npcPlugin.tryGetCachedValidRole(roleIndex);
            if (roleBuilder == null || !roleBuilder.isSpawnable()
                    || !(roleBuilder instanceof ISpawnableWithModel spawnable)) {
                return SpawnResult.failure("target-role-not-spawnable");
            }

            for (int[] offset : SAFE_OFFSETS) {
                SpawningContext context = new SpawningContext();
                try {
                    if (!context.setSpawnable(spawnable)) continue;
                    double probeX = request.candidate().x() + offset[0];
                    double probeZ = request.candidate().z() + offset[1];
                    if (!context.set(world, probeX, request.candidate().y(), probeZ)
                            || context.canSpawn() != SpawnTestResult.TEST_OK
                            || context.getModel() == null) {
                        continue;
                    }
                    Vector3d position = new Vector3d(context.xSpawn, context.ySpawn, context.zSpawn);
                    var pair = npcPlugin.spawnEntity(
                            store,
                            roleIndex,
                            position,
                            new Rotation3f(0.0F, 0.0F, 0.0F),
                            context.getModel(),
                            (npc, holder, ignoredStore) -> holder.addComponent(
                                    markerType,
                                    new HyDragonEncounterComponent(request.encounterId(), request.definitionId())),
                            null);
                    if (pair == null || pair.first() == null || !pair.first().isValid()) continue;
                    UUIDComponent identity = store.getComponent(pair.first(), UUIDComponent.getComponentType());
                    if (identity != null && identity.getUuid() != null) {
                        return SpawnResult.success(identity.getUuid());
                    }
                    store.removeEntity(pair.first(), RemoveReason.REMOVE);
                } catch (RuntimeException ignored) {
                    // Try the next deterministic placement candidate.
                } finally {
                    context.releaseFull();
                }
            }
            return SpawnResult.failure("no-player-safe-placement");
        }

        @Override
        public TargetLookup findTarget(String encounterId, String worldName, UUID expectedTargetUuid) {
            if (!world.getName().equals(worldName)) return TargetLookup.unknown();
            if (expectedTargetUuid != null) {
                Ref<EntityStore> expected = world.getEntityRef(expectedTargetUuid);
                HyDragonEncounterComponent marker = valid(expected)
                        ? store.getComponent(expected, markerType) : null;
                if (marker != null && marker.matches(encounterId)) return TargetLookup.present(expectedTargetUuid);
            }

            AtomicReference<UUID> found = new AtomicReference<>();
            store.forEachChunk(markerType, (chunk, commandBuffer) -> {
                if (found.get() != null) return;
                for (int index = 0; index < chunk.size(); index++) {
                    HyDragonEncounterComponent marker = chunk.getComponent(index, markerType);
                    if (marker == null || !marker.matches(encounterId)) continue;
                    UUIDComponent identity = chunk.getComponent(index, UUIDComponent.getComponentType());
                    if (identity != null && identity.getUuid() != null) {
                        found.compareAndSet(null, identity.getUuid());
                        return;
                    }
                }
            });
            UUID resolved = found.get();
            if (resolved != null) return TargetLookup.present(resolved);

            // The 0.5.6 runtime cannot prove that an unloaded persistent entity is gone. UNKNOWN
            // deliberately blocks replacement instead of risking a duplicate encounter target.
            return TargetLookup.unknown();
        }

        @Override
        public boolean applyGroundedState(UUID targetNpcUuid, String groundedState, String groundedEffectId) {
            Ref<EntityStore> target = world.getEntityRef(targetNpcUuid);
            if (!valid(target)) return false;
            NPCEntity npc = store.getComponent(target, NPCEntity.getComponentType());
            StateSupport states = npc == null || npc.getRole() == null
                    ? null : npc.getRole().getStateSupport();
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(groundedEffectId);
            EffectControllerComponent effects = store.getComponent(target, EffectControllerComponent.getComponentType());
            if (npc == null || states == null || effect == null || effects == null
                    || states.getStateHelper().getStateIndex(groundedState) < 0) {
                return false;
            }
            states.setState(target, groundedState, null, store);
            return effects.addEffect(target, effect, Math.max(0.05F, effect.getDuration()),
                    OverlapBehavior.OVERWRITE, store);
        }

        @Override
        public boolean retireTarget(UUID targetNpcUuid, String reason) {
            Ref<EntityStore> target = world.getEntityRef(targetNpcUuid);
            if (!valid(target)) return false;
            HyDragonEncounterComponent marker = store.getComponent(target, markerType);
            if (marker == null) return false;
            store.removeEntity(target, RemoveReason.REMOVE);
            return true;
        }

        private static boolean valid(Ref<EntityStore> ref) {
            return ref != null && ref.isValid();
        }
    }
}
