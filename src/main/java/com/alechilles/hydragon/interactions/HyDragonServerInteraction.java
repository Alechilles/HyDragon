package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.HyDragonPlugin;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Common server-authoritative fail-closed dispatch for HyDragon item interactions. */
abstract class HyDragonServerInteraction extends SimpleInteraction {
    protected HyDragonServerInteraction() {
        super();
    }

    protected HyDragonServerInteraction(String id) {
        super(id);
    }

    @Nonnull
    @Override
    public final WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected final void tick0(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        if (!firstRun) {
            super.tick0(false, time, type, context, cooldownHandler);
            return;
        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> playerEntity = context.getEntity();
        if (commandBuffer == null || playerEntity == null) {
            fail(context, firstRun, time, type, cooldownHandler);
            return;
        }
        PlayerRef player = commandBuffer.getComponent(playerEntity, PlayerRef.getComponentType());
        if (player == null || !isRequestValid()) {
            fail(context, firstRun, time, type, cooldownHandler);
            return;
        }

        HyDragonPlugin plugin = HyDragonPlugin.getInstance();
        TameworkBridge bridge = plugin == null ? null : plugin.getTameworkBridge();
        FeatureGate gate = bridge == null ? null : bridge.snapshot().feature(requiredFeature());
        String reason = gate == null
                ? "HyDragon runtime is not ready"
                : gate.available() ? "the public transaction adapter is not installed" : gate.reason();

        // No item or profile mutation is attempted until the public transaction adapter is installed.
        commandBuffer.run(store -> player.sendMessage(Message.raw(
                "HyDragon: " + actionLabel() + " unavailable — " + reason)));
        fail(context, firstRun, time, type, cooldownHandler);
    }

    @Override
    protected final void simulateTick0(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        if (context.getServerState() != null && context.getServerState().state == InteractionState.Failed) {
            context.getState().state = InteractionState.Failed;
        }
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }

    protected boolean isRequestValid() {
        return true;
    }

    @Nonnull
    protected abstract HyDragonFeature requiredFeature();

    @Nonnull
    protected abstract String actionLabel();

    private void fail(
            InteractionContext context,
            boolean firstRun,
            float time,
            InteractionType type,
            CooldownHandler cooldownHandler) {
        context.getState().state = InteractionState.Failed;
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }
}
