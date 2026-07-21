package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.HyDragonMessages;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import javax.annotation.Nonnull;
import com.hypixel.hytale.server.core.Message;
import java.util.UUID;

/** Soul Bond item interaction. Runtime provisioning remains capability/contract gated. */
public final class HyDragonSoulBondInteraction extends HyDragonServerInteraction {
    public static final String TYPE_ID = "HyDragonSoulBond";
    public static final BuilderCodec<HyDragonSoulBondInteraction> CODEC = BuilderCodec.builder(
            HyDragonSoulBondInteraction.class,
            HyDragonSoulBondInteraction::new,
            SimpleInteraction.CODEC
    ).documentation("Claims the player's once-only Soul Bond Miniwyvern entitlement.").build();

    protected HyDragonSoulBondInteraction() {
        super();
    }

    public HyDragonSoulBondInteraction(String id) {
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
        return "Soul Bond claim";
    }

    @Nonnull
    @Override
    protected HyDragonInteractionRuntime.Action action() {
        return HyDragonInteractionRuntime.Action.SOUL_BOND;
    }

    @Nonnull
    @Override
    protected String expectedItemId() {
        return "Draconic_Soul_Bond";
    }

    @Override
    protected String newOperationId(UUID playerUuid) {
        return "hydragon:soul-bond:" + playerUuid;
    }

    @Override
    protected Message successMessage() {
        return HyDragonMessages.soulBondClaimed();
    }

    @Override
    protected Message invalidMessage() {
        return HyDragonMessages.soulBondAlreadyClaimed();
    }

    @Override
    protected Message unavailableMessage() {
        return HyDragonMessages.soulBondUnavailable();
    }
}
