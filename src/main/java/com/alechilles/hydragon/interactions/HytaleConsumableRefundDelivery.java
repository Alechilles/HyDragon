package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.runtime.ConsumableRefundClaimService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Exact-on-retry inventory delivery for a durable consumable refund claim. */
public final class HytaleConsumableRefundDelivery implements ConsumableRefundClaimService.RefundDelivery {
    public static final String RECEIPT_METADATA_KEY = "hydragon:refund_receipt";
    private final Store<EntityStore> store;
    private final Ref<EntityStore> playerEntity;
    private final PlayerRef player;

    public HytaleConsumableRefundDelivery(
            Store<EntityStore> store, Ref<EntityStore> playerEntity, PlayerRef player) {
        this.store = Objects.requireNonNull(store, "store");
        this.playerEntity = Objects.requireNonNull(playerEntity, "playerEntity");
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public CompletionStage<Decision> deliver(ConsumableRefundClaimService.Claim claim) {
        Objects.requireNonNull(claim, "claim");
        try {
            store.assertThread();
            if (!playerEntity.isValid() || !claim.ownerUuid().equals(player.getUuid())) {
                return completed(Decision.UNAVAILABLE);
            }
            var inventory = InventoryComponent.getCombined(
                    store, playerEntity, InventoryComponent.EVERYTHING);
            if (inventory == null) return completed(Decision.UNAVAILABLE);
            int receipts = inventory.countItemStacks(stack -> claim.operationId().equals(
                    stack.getFromMetadataOrNull(RECEIPT_METADATA_KEY, Codec.STRING)));
            if (receipts == 1) return completed(Decision.ALREADY_APPLIED);
            if (receipts > 1) return completed(Decision.AMBIGUOUS);

            ItemStack refund = new ItemStack(claim.itemId(), claim.quantity())
                    .withMetadata(RECEIPT_METADATA_KEY, Codec.STRING, claim.operationId());
            if (!inventory.canAddItemStack(refund, false, true)) {
                return completed(Decision.NO_SPACE);
            }
            ItemStackTransaction transaction = inventory.addItemStack(refund, true, false, true);
            if (transaction == null || !transaction.succeeded()
                    || (transaction.getRemainder() != null && !transaction.getRemainder().isEmpty())) {
                return completed(Decision.UNAVAILABLE);
            }
            int appliedReceipts = inventory.countItemStacks(stack -> claim.operationId().equals(
                    stack.getFromMetadataOrNull(RECEIPT_METADATA_KEY, Codec.STRING)));
            return completed(appliedReceipts == 1 ? Decision.APPLIED : Decision.AMBIGUOUS);
        } catch (RuntimeException | LinkageError failure) {
            return completed(Decision.UNAVAILABLE);
        }
    }

    private static CompletionStage<Decision> completed(Decision decision) {
        return CompletableFuture.completedFuture(decision);
    }
}
