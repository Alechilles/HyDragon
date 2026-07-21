package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.integration.HyDragonFeature;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import javax.annotation.Nonnull;

/** Revitalizing Essence interaction for one damaged bonded stone. */
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
}
