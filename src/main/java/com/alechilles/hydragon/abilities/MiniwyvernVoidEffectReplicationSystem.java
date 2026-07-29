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
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.EntityEffectsUpdate;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.livingentity.LivingEntityEffectClearChangesSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** One-shot trace of the exact effect update queued to visible clients. */
public final class MiniwyvernVoidEffectReplicationSystem extends EntityTickingSystem<EntityStore> {
    private static final Logger LOGGER = Logger.getLogger(MiniwyvernVoidEffectReplicationSystem.class.getName());
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.AFTER, EntityTrackerSystems.EffectControllerSystem.class),
            new SystemDependency<>(Order.BEFORE, LivingEntityEffectClearChangesSystem.class));

    private final MiniwyvernVoidEffectReplicationProbe probe;

    public MiniwyvernVoidEffectReplicationSystem(MiniwyvernVoidEffectReplicationProbe probe) {
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
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
        if (identity == null) return;
        UUID targetUuid = identity.getUuid();
        MiniwyvernVoidEffectReplicationProbe.Observation observation = probe.consume(targetUuid);
        if (observation == null) return;

        Ref<EntityStore> target = chunk.getReferenceTo(index);
        EffectControllerComponent controller = chunk.getComponent(
                index, EffectControllerComponent.getComponentType());
        MiniwyvernVoidEffectReplicationProbe.PacketEvidence controllerEvidence =
                MiniwyvernVoidEffectReplicationProbe.inspectQueuedUpdates(
                        observation.effectIndex(),
                        new ComponentUpdate[] {new EntityEffectsUpdate(controller.consumeChanges())});

        int visibleViewers = 0;
        int queuedViewers = 0;
        int queuedAdds = 0;
        int queuedRemoves = 0;
        float queuedRemaining = Float.NaN;
        EntityTrackerSystems.Visible visible = store.getComponent(
                target, EntityTrackerSystems.Visible.getComponentType());
        if (visible != null) {
            for (EntityTrackerSystems.EntityViewer viewer : visible.visibleTo.values()) {
                visibleViewers++;
                EntityTrackerSystems.EntityUpdate queued = viewer.updates.get(target);
                MiniwyvernVoidEffectReplicationProbe.PacketEvidence evidence =
                        MiniwyvernVoidEffectReplicationProbe.inspectQueuedUpdates(
                                observation.effectIndex(), queued == null ? null : queued.toUpdatesArray());
                if (evidence.adds() > 0 || evidence.removes() > 0) queuedViewers++;
                queuedAdds += evidence.adds();
                queuedRemoves += evidence.removes();
                if (!Float.isNaN(evidence.latestAddRemainingSeconds())) {
                    queuedRemaining = evidence.latestAddRemainingSeconds();
                }
            }
        }

        SystemOrder systemOrder = locateSystemOrder(store);
        int finalVisibleViewers = visibleViewers;
        int finalQueuedViewers = queuedViewers;
        int finalQueuedAdds = queuedAdds;
        int finalQueuedRemoves = queuedRemoves;
        float finalQueuedRemaining = queuedRemaining;
        LOGGER.info(() -> "Void replication probe for " + targetUuid
                + ": effectIndex=" + observation.effectIndex()
                + ", controllerAdds=" + controllerEvidence.adds()
                + ", controllerRemoves=" + controllerEvidence.removes()
                + ", controllerRemaining=" + controllerEvidence.latestAddRemainingSeconds()
                + ", visibleViewers=" + finalVisibleViewers
                + ", queuedViewers=" + finalQueuedViewers
                + ", queuedAdds=" + finalQueuedAdds
                + ", queuedRemoves=" + finalQueuedRemoves
                + ", queuedRemaining=" + finalQueuedRemaining
                + ", order=" + systemOrder);
    }

    private SystemOrder locateSystemOrder(Store<EntityStore> store) {
        int probeIndex = -1;
        int trackerIndex = -1;
        int clearIndex = -1;
        int sendIndex = -1;
        var data = store.getRegistry().getData();
        for (int index = 0; index < data.getSystemSize(); index++) {
            ISystem<EntityStore> system = data.getSystem(index);
            if (system == this) probeIndex = index;
            if (system instanceof EntityTrackerSystems.EffectControllerSystem) trackerIndex = index;
            if (system instanceof LivingEntityEffectClearChangesSystem) clearIndex = index;
            if (system instanceof EntityTrackerSystems.SendPackets) sendIndex = index;
        }
        return new SystemOrder(trackerIndex, probeIndex, clearIndex, sendIndex);
    }

    private record SystemOrder(int tracker, int probe, int clear, int send) {
    }
}
