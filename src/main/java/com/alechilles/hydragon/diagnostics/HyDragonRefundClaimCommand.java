package com.alechilles.hydragon.diagnostics;

import com.alechilles.hydragon.interactions.HytaleConsumableRefundDelivery;
import com.alechilles.hydragon.integration.HyDragonMessages;
import com.alechilles.hydragon.runtime.ConsumableRefundClaimService;
import com.alechilles.hydragon.runtime.GameplayResult;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/** Player-owned recovery command for terminally denied repair consumables. */
public final class HyDragonRefundClaimCommand extends AbstractPlayerCommand {
    private final Supplier<ConsumableRefundClaimService> serviceSupplier;

    public HyDragonRefundClaimCommand(Supplier<ConsumableRefundClaimService> serviceSupplier) {
        super("hydragonclaim", "server.messages.refund.description");
        this.serviceSupplier = Objects.requireNonNull(serviceSupplier, "serviceSupplier");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
        ConsumableRefundClaimService service = serviceSupplier.get();
        if (service == null) {
            context.sendMessage(HyDragonMessages.refundUnavailable());
            return;
        }
        List<ConsumableRefundClaimService.Claim> claims = service.claims(playerRef.getUuid());
        if (claims.isEmpty()) {
            context.sendMessage(HyDragonMessages.refundNone());
            return;
        }
        ConsumableRefundClaimService.Claim claim = claims.getFirst();
        service.claim(playerRef.getUuid(), claim.operationId(),
                        new HytaleConsumableRefundDelivery(store, ref, playerRef))
                .whenComplete((result, failure) -> sendResult(context, result, failure));
    }

    private static void sendResult(CommandContext context, GameplayResult result, Throwable failure) {
        if (failure != null || result == null) {
            context.sendMessage(HyDragonMessages.refundPending());
            return;
        }
        context.sendMessage(result.succeeded()
                ? HyDragonMessages.refundRecovered()
                : result.status() == GameplayResult.Status.DENIED
                ? HyDragonMessages.refundNoSpace()
                : HyDragonMessages.refundPending());
    }
}
