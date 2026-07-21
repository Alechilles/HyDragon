package com.alechilles.hydragon.interactions;

import com.alechilles.hydragon.runtime.ConsumableReservation;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bson.BsonDocument;

/** Receipt-backed exact-CAS reservation of the player's current hotbar input. */
final class HytaleHeldItemReservation implements ConsumableReservation {
    static final String RECEIPT_METADATA_KEY = "hydragon:consumable_receipt";
    private static final String RECEIPT_VERSION = "1";

    private final UUID playerUuid;
    private final UUID worldUuid;
    private final short hotbarSlot;
    private final String operationId;
    private final SourceEvidence sourceEvidence;
    private final int quantity;
    private final String receipt;
    private final BsonDocument originalMetadata;

    private HytaleHeldItemReservation(UUID playerUuid,
                                      UUID worldUuid,
                                      short hotbarSlot,
                                      String operationId,
                                      SourceEvidence sourceEvidence,
                                      int quantity,
                                      String receipt,
                                      BsonDocument originalMetadata) {
        this.playerUuid = playerUuid;
        this.worldUuid = worldUuid;
        this.hotbarSlot = hotbarSlot;
        this.operationId = operationId;
        this.sourceEvidence = sourceEvidence;
        this.quantity = quantity;
        this.receipt = receipt;
        this.originalMetadata = originalMetadata;
    }

    static Optional<HytaleHeldItemReservation> reserve(InteractionContext context,
                                                        PlayerRef player,
                                                        String expectedItemId,
                                                        String requestedOperationId,
                                                        int quantity) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(player, "player");
        expectedItemId = required(expectedItemId, "expectedItemId");
        requestedOperationId = required(requestedOperationId, "requestedOperationId");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        ItemContainer container = context.getHeldItemContainer();
        ItemStack current = context.getHeldItem();
        short slot = context.getHeldItemSlot();
        if (container == null || current == null || current.isEmpty()
                || !expectedItemId.equals(current.getItemId()) || current.getQuantity() < quantity
                || slot < 0 || player.getWorldUuid() == null) {
            return Optional.empty();
        }

        String existing = current.getFromMetadataOrNull(RECEIPT_METADATA_KEY, Codec.STRING);
        Receipt decoded = existing == null ? null : Receipt.decode(existing);
        if (decoded != null && !decoded.operationId().equals(requestedOperationId)) {
            return Optional.empty();
        }
        BsonDocument originalMetadata;
        String fingerprint;
        String receipt;
        if (decoded != null) {
            if (!decoded.itemId().equals(expectedItemId) || decoded.quantity() != quantity
                    || decoded.stackQuantity() != current.getQuantity()) {
                return Optional.empty();
            }
            originalMetadata = BsonDocument.parse(decoded.originalMetadataJson());
            fingerprint = decoded.fingerprint();
            receipt = existing;
        } else {
            originalMetadata = current.getMetadata() == null
                    ? new BsonDocument() : BsonDocument.parse(current.getMetadata().toJson());
            fingerprint = fingerprint(current.getItemId(), current.getQuantity(), current.getDurability(),
                    current.getMaxDurability(), originalMetadata);
            receipt = new Receipt(
                    requestedOperationId, expectedItemId, quantity, current.getQuantity(),
                    fingerprint, originalMetadata.toJson()).encode();
            ItemStack reserved = current.withMetadata(RECEIPT_METADATA_KEY, Codec.STRING, receipt);
            if (!container.replaceItemStackInSlot(slot, current, reserved).succeeded()) {
                return Optional.empty();
            }
        }
        SourceEvidence evidence = new SourceEvidence(
                expectedItemId,
                player.getUuid().toString(),
                "hotbar",
                slot,
                Math.max(0, context.getOperationCounter()),
                fingerprint,
                decoded == null ? current.getQuantity() : decoded.stackQuantity());
        return Optional.of(new HytaleHeldItemReservation(
                player.getUuid(), player.getWorldUuid(), slot, requestedOperationId,
                evidence, quantity, receipt, originalMetadata));
    }

    static Optional<String> existingOperationId(InteractionContext context) {
        ItemStack current = context == null ? null : context.getHeldItem();
        String raw = current == null ? null
                : current.getFromMetadataOrNull(RECEIPT_METADATA_KEY, Codec.STRING);
        Receipt receipt = raw == null ? null : Receipt.decode(raw);
        return receipt == null ? Optional.empty() : Optional.of(receipt.operationId());
    }

    @Override
    public String operationId() {
        return operationId;
    }

    @Override
    public SourceEvidence sourceEvidence() {
        return sourceEvidence;
    }

    @Override
    public int quantity() {
        return quantity;
    }

    @Override
    public CompletionStage<Disposition> consume() {
        return mutate(true);
    }

    @Override
    public CompletionStage<Disposition> release() {
        return mutate(false);
    }

    private CompletionStage<Disposition> mutate(boolean consume) {
        CompletableFuture<Disposition> completion = new CompletableFuture<>();
        Universe universe = Universe.get();
        World world = universe == null ? null : universe.getWorld(worldUuid);
        if (world == null) return CompletableFuture.completedFuture(Disposition.UNAVAILABLE);
        try {
            world.execute(() -> mutateOnWorldThread(world, consume, completion));
        } catch (RuntimeException failure) {
            completion.complete(Disposition.UNAVAILABLE);
        }
        return completion;
    }

    private void mutateOnWorldThread(World world, boolean consume, CompletableFuture<Disposition> completion) {
        try {
            Store<EntityStore> store = world.getEntityStore() == null ? null : world.getEntityStore().getStore();
            if (store == null) {
                completion.complete(Disposition.UNAVAILABLE);
                return;
            }
            store.assertThread();
            Ref<EntityStore> player = world.getEntityRef(playerUuid);
            InventoryComponent.Hotbar hotbar = player == null || !player.isValid() ? null
                    : store.getComponent(player, InventoryComponent.Hotbar.getComponentType());
            ItemContainer container = hotbar == null ? null : hotbar.getInventory();
            ItemStack current = container == null ? null : container.getItemStack(hotbarSlot);
            if (current == null || !sourceEvidence.itemId().equals(current.getItemId())) {
                completion.complete(Disposition.UNKNOWN);
                return;
            }
            String currentReceipt = current.getFromMetadataOrNull(RECEIPT_METADATA_KEY, Codec.STRING);
            if (!receipt.equals(currentReceipt)) {
                completion.complete(matchesOriginal(current) && !consume
                        ? Disposition.ALREADY_APPLIED : Disposition.UNKNOWN);
                return;
            }
            if (current.getQuantity() != sourceEvidence.stackQuantityAtPrepare()) {
                completion.complete(Disposition.CONFLICT);
                return;
            }
            int remaining = consume ? current.getQuantity() - quantity : current.getQuantity();
            if (remaining < 0) {
                completion.complete(Disposition.CONFLICT);
                return;
            }
            if (remaining == 0) {
                completion.complete(container.removeItemStackFromSlot(hotbarSlot, current.getQuantity()).succeeded()
                        ? Disposition.APPLIED : Disposition.CONFLICT);
                return;
            }
            ItemStack replacement = current.withQuantity(remaining).withMetadata(originalMetadata);
            completion.complete(container.replaceItemStackInSlot(hotbarSlot, current, replacement).succeeded()
                    ? Disposition.APPLIED : Disposition.CONFLICT);
        } catch (RuntimeException failure) {
            completion.complete(Disposition.UNKNOWN);
        }
    }

    private boolean matchesOriginal(ItemStack current) {
        return fingerprint(current.getItemId(), current.getQuantity(), current.getDurability(),
                current.getMaxDurability(), current.getMetadata())
                .equals(sourceEvidence.itemFingerprint());
    }

    private static String fingerprint(String itemId,
                                      int quantity,
                                      double durability,
                                      double maxDurability,
                                      BsonDocument metadata) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = itemId + '\u001f' + quantity + '\u001f' + durability + '\u001f'
                    + maxDurability + '\u001f' + (metadata == null ? "{}" : metadata.toJson());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private record Receipt(String operationId,
                           String itemId,
                           int quantity,
                           int stackQuantity,
                           String fingerprint,
                           String originalMetadataJson) {
        private String encode() {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return String.join(".", RECEIPT_VERSION,
                    encoded(encoder, operationId), encoded(encoder, itemId), Integer.toString(quantity),
                    Integer.toString(stackQuantity), encoded(encoder, fingerprint),
                    encoded(encoder, originalMetadataJson));
        }

        private static Receipt decode(String value) {
            try {
                String[] parts = value.split("\\.", -1);
                if (parts.length != 7 || !RECEIPT_VERSION.equals(parts[0])) return null;
                Base64.Decoder decoder = Base64.getUrlDecoder();
                return new Receipt(decoded(decoder, parts[1]), decoded(decoder, parts[2]),
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                        decoded(decoder, parts[5]), decoded(decoder, parts[6]));
            } catch (RuntimeException invalid) {
                return null;
            }
        }

        private static String encoded(Base64.Encoder encoder, String value) {
            return encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private static String decoded(Base64.Decoder decoder, String value) {
            return new String(decoder.decode(value), StandardCharsets.UTF_8);
        }
    }
}
