package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.integration.HyDragonMessages;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.runtime.GameplayResult;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

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

        UUID worldUuid = player.getWorldUuid();
        Universe universe = Universe.get();
        World world = universe == null || worldUuid == null ? null : universe.getWorld(worldUuid);
        if (world == null || !HyDragonInteractionRuntime.installed()) {
            commandBuffer.run(store -> player.sendMessage(unavailableMessage()));
            fail(context, firstRun, time, type, cooldownHandler);
            return;
        }
        String operationId = HytaleHeldItemReservation.existingOperationId(context)
                .orElseGet(() -> newOperationId(player.getUuid()));
        Optional<HytaleHeldItemReservation> reserved = HytaleHeldItemReservation.reserve(
                context, player, expectedItemId(), operationId, consumedQuantity());
        if (reserved.isEmpty()) {
            commandBuffer.run(store -> player.sendMessage(invalidMessage()));
            fail(context, firstRun, time, type, cooldownHandler);
            return;
        }

        HyDragonInteractionRuntime.dispatch(
                        action(), player.getUuid(), world.getName(), archetypeId(), reserved.orElseThrow())
                .whenComplete((result, failure) -> sendResult(
                        worldUuid,
                        player.getUuid(),
                        failure == null ? result : GameplayResult.retryable("interaction callback failed")));
        super.tick0(true, time, type, context, cooldownHandler);
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

    @Nonnull
    protected abstract HyDragonInteractionRuntime.Action action();

    @Nonnull
    protected abstract String expectedItemId();

    protected String archetypeId() {
        return "";
    }

    protected int consumedQuantity() {
        return 1;
    }

    protected String newOperationId(UUID playerUuid) {
        return "hydragon:" + action().name().toLowerCase(java.util.Locale.ROOT) + ":" + UUID.randomUUID();
    }

    protected Message successMessage() {
        return HyDragonMessages.vesselUnavailable();
    }

    protected Message invalidMessage() {
        return unavailableMessage();
    }

    protected Message unavailableMessage() {
        return HyDragonMessages.vesselUnavailable();
    }

    private void sendResult(UUID worldUuid, UUID playerUuid, GameplayResult result) {
        Universe universe = Universe.get();
        World world = universe == null ? null : universe.getWorld(worldUuid);
        if (world == null) return;
        try {
            world.execute(() -> {
                Ref<EntityStore> ref = world.getEntityRef(playerUuid);
                if (ref == null || !ref.isValid() || world.getEntityStore() == null) return;
                PlayerRef player = world.getEntityStore().getStore().getComponent(ref, PlayerRef.getComponentType());
                if (player != null) {
                    player.sendMessage(result != null && result.succeeded() ? successMessage()
                            : result != null && result.status() == GameplayResult.Status.DENIED
                            ? invalidMessage() : unavailableMessage());
                }
            });
        } catch (RuntimeException ignored) {
            // Feedback is best-effort; transaction state remains journal-authoritative.
        }
    }

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
