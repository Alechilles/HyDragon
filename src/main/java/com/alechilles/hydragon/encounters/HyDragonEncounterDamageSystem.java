package com.alechilles.hydragon.encounters;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Routes real, post-filter Hytale damage events on marked encounter NPCs into grounding buildup. */
final class HyDragonEncounterDamageSystem extends DamageEventSystem {
    private final HyDragonEncounterServerRuntime runtime;
    private final ComponentType<EntityStore, HyDragonEncounterComponent> markerType;

    HyDragonEncounterDamageSystem(
            HyDragonEncounterServerRuntime runtime,
            ComponentType<EntityStore, HyDragonEncounterComponent> markerType) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.markerType = Objects.requireNonNull(markerType, "markerType");
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return markerType;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        HyDragonEncounterComponent marker = chunk.getComponent(index, markerType);
        if (marker == null || damage.isCancelled() || damage.getAmount() <= 0.0F) return;
        runtime.onEncounterDamage(
                marker.getEncounterId(),
                chunk.getReferenceTo(index),
                damage,
                store,
                commandBuffer);
    }
}
