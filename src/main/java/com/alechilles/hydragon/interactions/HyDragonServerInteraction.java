package com.alechilles.hydragon.interactions;

import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.hydragon.integration.HyDragonMessages;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.HyDragonPlugin;
import com.alechilles.hydragon.runtime.GameplayResult;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

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

        Optional<ConsumableRequirement> requirement = consumableRequirement();
        if (requirement.isEmpty()) {
            commandBuffer.run(store -> player.sendMessage(unavailableMessage()));
            fail(context, firstRun, time, type, cooldownHandler);
            return;
        }
        ConsumableRequirement consumable = requirement.orElseThrow();
        Optional<String> accessItemId = requiredAccessItemId();
        if (accessItemId.isPresent()
                && !hasInventoryItem(commandBuffer, playerEntity, accessItemId.orElseThrow())) {
            commandBuffer.run(store -> player.sendMessage(accessItemMissingMessage()));
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
                context, player, consumable.itemId(), operationId, consumable.quantity());
        if (reserved.isEmpty()) {
            commandBuffer.run(store -> player.sendMessage(invalidMessage()));
            fail(context, firstRun, time, type, cooldownHandler);
            return;
        }
        TransformComponent transform = commandBuffer.getComponent(
                playerEntity, TransformComponent.getComponentType());
        if (transform == null) {
            reserved.orElseThrow().release();
            commandBuffer.run(store -> player.sendMessage(unavailableMessage()));
            fail(context, firstRun, time, type, cooldownHandler);
            return;
        }
        PopulationAdmissionLocation destination = new PopulationAdmissionLocation(
                world.getName(),
                ChunkUtil.chunkCoordinate(transform.getPosition().x()),
                ChunkUtil.chunkCoordinate(transform.getPosition().z()));

                HyDragonInteractionRuntime.dispatch(
                        action(), requiredFeature(), player.getUuid(), world.getName(), destination, archetypeId(),
                        reserved.orElseThrow())
                .whenComplete((result, failure) -> completeInteraction(
                        worldUuid, player.getUuid(), operationId, destination, result, failure));
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
    protected String expectedItemId() {
        return "";
    }

    protected String archetypeId() {
        return "";
    }

    protected int consumedQuantity() {
        return 1;
    }

    /** Optional non-consumed inventory access item required before the consumable can be reserved. */
    protected Optional<String> requiredAccessItemId() {
        return Optional.empty();
    }

    protected Message accessItemMissingMessage() {
        return unavailableMessage();
    }

    /** Captures one immutable material policy for the entire request. */
    protected Optional<ConsumableRequirement> consumableRequirement() {
        try {
            return Optional.of(new ConsumableRequirement(
                    expectedItemId(), consumedQuantity()));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Optional.empty();
        }
    }

    protected record ConsumableRequirement(String itemId, int quantity) {
        protected ConsumableRequirement {
            itemId = java.util.Objects.requireNonNull(itemId, "itemId").trim();
            if (itemId.isEmpty() || quantity <= 0) {
                throw new IllegalArgumentException("consumable requirement is invalid");
            }
        }
    }

    protected String newOperationId(UUID playerUuid) {
        return "hydragon:" + action().name().toLowerCase(java.util.Locale.ROOT) + ":" + UUID.randomUUID();
    }

    protected Message successMessage() {
        return HyDragonMessages.gameplayUnavailable();
    }

    protected Message invalidMessage() {
        return unavailableMessage();
    }

    protected Message unavailableMessage() {
        return HyDragonMessages.gameplayUnavailable();
    }

    protected Message deniedMessage(GameplayResult result) {
        return invalidMessage();
    }

    private static boolean hasInventoryItem(
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> playerEntity,
            String itemId) {
        try {
            var inventory = InventoryComponent.getCombined(
                    commandBuffer, playerEntity, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            return inventory != null && inventory.countItemStacks(
                    stack -> stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId())) > 0;
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private void completeInteraction(
            UUID worldUuid,
            UUID playerUuid,
            String operationId,
            PopulationAdmissionLocation destination,
            GameplayResult result,
            Throwable failure) {
        GameplayResult effective = failure == null && result != null
                ? result : GameplayResult.retryable("interaction callback failed");
        HyDragonPlugin plugin = HyDragonPlugin.getInstance();
        if (plugin != null) {
            String message = "HyDragon interaction outcome: action=" + action()
                    + ", operation=" + operationId
                    + ", player=" + playerUuid
                    + ", world=" + destination.worldName()
                    + ", chunk=" + destination.chunkX() + ',' + destination.chunkZ()
                    + ", status=" + effective.status()
                    + ", reason=" + effective.reason();
            if (failure == null) {
                plugin.getLogger().at(effective.succeeded() ? Level.INFO : Level.WARNING).log(message);
            } else {
                plugin.getLogger().at(Level.WARNING).withCause(failure).log(message);
            }
        }
        sendResult(worldUuid, playerUuid, effective);
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
                            ? deniedMessage(result) : unavailableMessage());
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
