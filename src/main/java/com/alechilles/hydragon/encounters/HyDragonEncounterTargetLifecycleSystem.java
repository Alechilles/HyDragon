package com.alechilles.hydragon.encounters;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Distinguishes permanent target removal from ordinary chunk/world unload. */
public final class HyDragonEncounterTargetLifecycleSystem extends RefSystem<EntityStore> {
    private final HytaleEncounterWorldDispatcher dispatcher;
    private final ComponentType<EntityStore, HyDragonEncounterComponent> markerType;
    private final ComponentType<EntityStore, UUIDComponent> uuidType;
    private final Query<EntityStore> query;

    public HyDragonEncounterTargetLifecycleSystem(
            HytaleEncounterWorldDispatcher dispatcher,
            ComponentType<EntityStore, HyDragonEncounterComponent> markerType) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.markerType = Objects.requireNonNull(markerType, "markerType");
        this.uuidType = UUIDComponent.getComponentType();
        this.query = Query.and(markerType, uuidType);
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> reference,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Observation observation = observation(reference, commandBuffer);
        if (observation != null) {
            dispatcher.observeTargetAdded(worldName(store), observation.encounterId(), observation.targetUuid());
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> reference,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!provesAbsence(reason)) return;
        Observation observation = observation(reference, commandBuffer);
        if (observation != null) {
            dispatcher.observeTargetRemoved(worldName(store), observation.encounterId(), observation.targetUuid());
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    static boolean provesAbsence(RemoveReason reason) {
        return reason == RemoveReason.REMOVE || reason == RemoveReason.BUILDER_TOOLS_UNDO;
    }

    private Observation observation(
            Ref<EntityStore> reference,
            CommandBuffer<EntityStore> commandBuffer) {
        HyDragonEncounterComponent marker = commandBuffer.getComponent(reference, markerType);
        UUIDComponent identity = commandBuffer.getComponent(reference, uuidType);
        UUID uuid = identity == null ? null : identity.getUuid();
        if (marker == null || uuid == null || marker.getEncounterId() == null
                || marker.getEncounterId().isBlank()) return null;
        return new Observation(marker.getEncounterId(), uuid);
    }

    private static String worldName(Store<EntityStore> store) {
        return store.getExternalData().getWorld().getName();
    }

    private record Observation(String encounterId, UUID targetUuid) {
    }
}
