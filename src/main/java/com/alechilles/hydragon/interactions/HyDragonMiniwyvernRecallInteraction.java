package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.HyDragonMessages;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import javax.annotation.Nonnull;

/** Reusable Soul Bound Wyvern focus interaction. */
public final class HyDragonMiniwyvernRecallInteraction extends HyDragonServerInteraction {
    public static final String TYPE_ID = "HyDragonMiniwyvernRecall";
    public static final BuilderCodec<HyDragonMiniwyvernRecallInteraction> CODEC =
            BuilderCodec.builder(
                    HyDragonMiniwyvernRecallInteraction.class,
                    HyDragonMiniwyvernRecallInteraction::new,
                    SimpleInteraction.CODEC)
                    .documentation("Recalls the player's existing Soul Bound Miniwyvern.")
                    .build();

    protected HyDragonMiniwyvernRecallInteraction() {
        super();
    }

    public HyDragonMiniwyvernRecallInteraction(String id) {
        super(id);
    }

    @Nonnull
    @Override
    protected HyDragonFeature requiredFeature() {
        return HyDragonFeature.SOUL_BOND_CLAIM;
    }

    @Nonnull
    @Override
    protected String actionLabel() {
        return "Miniwyvern recall";
    }

    @Nonnull
    @Override
    protected HyDragonInteractionRuntime.Action action() {
        return HyDragonInteractionRuntime.Action.RECALL_MINIWYVERN;
    }

    @Nonnull
    @Override
    protected String expectedItemId() {
        return "Soul_Bound_Wyvern";
    }

    @Override
    protected Message successMessage() {
        return HyDragonMessages.soulBoundWyvernRecalled();
    }

    @Override
    protected Message invalidMessage() {
        return HyDragonMessages.soulBoundWyvernUnavailable();
    }

    @Override
    protected Message unavailableMessage() {
        return HyDragonMessages.soulBoundWyvernUnavailable();
    }
}
