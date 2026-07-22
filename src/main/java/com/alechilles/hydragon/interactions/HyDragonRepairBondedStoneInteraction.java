package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.HyDragonMessages;
import com.alechilles.hydragon.config.StoneMaintenanceConfig;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import javax.annotation.Nonnull;
import com.hypixel.hytale.server.core.Message;
import java.util.Optional;

/** Configured repair-material interaction for one damaged bonded stone. */
public final class HyDragonRepairBondedStoneInteraction extends HyDragonServerInteraction {
    public static final String TYPE_ID = "HyDragonRepairBondedStone";
    public static final BuilderCodec<HyDragonRepairBondedStoneInteraction> CODEC = BuilderCodec.builder(
            HyDragonRepairBondedStoneInteraction.class,
            HyDragonRepairBondedStoneInteraction::new,
            SimpleInteraction.CODEC
    ).documentation("Repairs and revives the exact damaged profile linked to a bonded stone.").build();

    protected HyDragonRepairBondedStoneInteraction() {
        super();
    }

    public HyDragonRepairBondedStoneInteraction(String id) {
        super(id);
    }

    @Nonnull
    @Override
    protected HyDragonFeature requiredFeature() {
        return HyDragonFeature.BONDED_STONE_REPAIR;
    }

    @Nonnull
    @Override
    protected String actionLabel() {
        return "bonded stone repair";
    }

    @Nonnull
    @Override
    protected HyDragonInteractionRuntime.Action action() {
        return HyDragonInteractionRuntime.Action.REPAIR;
    }

    @Nonnull
    @Override
    protected Optional<StoneMaintenanceConfig.RepairRequirement> consumableRequirement() {
        return HyDragonInteractionRuntime.repairRequirement();
    }

    @Override
    protected Message successMessage() {
        return HyDragonMessages.repairSuccess();
    }

    @Override
    protected Message invalidMessage() {
        return HyDragonMessages.repairInvalid();
    }

    @Override
    protected Message unavailableMessage() {
        return HyDragonMessages.vesselUnavailable();
    }
}
