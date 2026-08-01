package com.alechilles.hydragon.combat;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChainingInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.BalancingInitialisationSystem;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

/** Supplies the interaction state required by the Nordic Drake's chained melee root. */
public final class NordicDrakeChainingDataSystem extends HolderSystem<EntityStore> {
    static final String TAMED_NORDIC_DRAKE_ROLE = "Tamed_NordicDrake";

    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, ChainingInteraction.Data> chainingDataType;
    private final Query<EntityStore> query;

    public NordicDrakeChainingDataSystem() {
        this.npcType = NPCEntity.getComponentType();
        this.chainingDataType = InteractionModule.get().getChainingDataComponent();
        this.query = Query.and(npcType, Query.not(chainingDataType));
    }

    @Override
    public void onEntityAdd(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store) {
        NPCEntity npc = holder.getComponent(npcType);
        if (npc != null) {
            provisionChainingData(
                    npc.getRoleName(), () -> holder.ensureComponent(chainingDataType));
        }
    }

    @Override
    public void onEntityRemoved(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store) {
        // No teardown is required; the component belongs to the entity lifecycle.
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, BalancingInitialisationSystem.class));
    }

    static void provisionChainingData(String roleName, Runnable provisioner) {
        Objects.requireNonNull(provisioner, "provisioner");
        if (TAMED_NORDIC_DRAKE_ROLE.equals(roleName)) {
            provisioner.run();
        }
    }
}
