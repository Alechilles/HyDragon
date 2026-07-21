package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alechilles.hydragon.persistence.HyDragonStateStore;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsumableRefundClaimServiceTest {
    @TempDir Path temp;

    @Test
    void onlyOwnerCanClaimAndDeliveryIsClosedDurably() throws Exception {
        UUID owner = UUID.randomUUID();
        StateStoreOperationJournal journal = refundDue(owner, "refund-one");
        ConsumableRefundClaimService service = new ConsumableRefundClaimService(journal);
        AtomicInteger deliveries = new AtomicInteger();

        GameplayResult denied = service.claim(UUID.randomUUID(), "refund-one", claim -> {
            deliveries.incrementAndGet();
            return CompletableFuture.completedFuture(
                    ConsumableRefundClaimService.RefundDelivery.Decision.APPLIED);
        }).toCompletableFuture().join();
        GameplayResult applied = service.claim(owner, "refund-one", claim -> {
            deliveries.incrementAndGet();
            assertEquals("Draconic_Essence_Revitalizing", claim.itemId());
            assertEquals(1, claim.quantity());
            return CompletableFuture.completedFuture(
                    ConsumableRefundClaimService.RefundDelivery.Decision.APPLIED);
        }).toCompletableFuture().join();
        GameplayResult replay = service.claim(owner, "refund-one", claim -> {
            deliveries.incrementAndGet();
            return CompletableFuture.completedFuture(
                    ConsumableRefundClaimService.RefundDelivery.Decision.APPLIED);
        }).toCompletableFuture().join();

        assertEquals(GameplayResult.Status.DENIED, denied.status());
        assertEquals(GameplayResult.Status.APPLIED, applied.status());
        assertEquals(GameplayResult.Status.ALREADY_APPLIED, replay.status());
        assertEquals(1, deliveries.get());
        assertEquals(OperationJournal.Phase.REFUNDED, journal.find("refund-one").orElseThrow().phase());
    }

    @Test
    void noSpaceLeavesClaimPending() throws Exception {
        UUID owner = UUID.randomUUID();
        StateStoreOperationJournal journal = refundDue(owner, "refund-space");
        ConsumableRefundClaimService service = new ConsumableRefundClaimService(journal);

        GameplayResult result = service.claim(owner, "refund-space", claim ->
                CompletableFuture.completedFuture(
                        ConsumableRefundClaimService.RefundDelivery.Decision.NO_SPACE))
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.DENIED, result.status());
        assertEquals(OperationJournal.Phase.REFUND_DUE,
                journal.find("refund-space").orElseThrow().phase());
    }

    @Test
    void deliveredReceiptCanCloseAfterPriorJournalFailure() throws Exception {
        UUID owner = UUID.randomUUID();
        StateStoreOperationJournal journal = refundDue(owner, "refund-retry");
        ConsumableRefundClaimService service = new ConsumableRefundClaimService(journal);

        GameplayResult result = service.claim(owner, "refund-retry", claim ->
                CompletableFuture.completedFuture(
                        ConsumableRefundClaimService.RefundDelivery.Decision.ALREADY_APPLIED))
                .toCompletableFuture().join();

        assertEquals(GameplayResult.Status.APPLIED, result.status());
        assertEquals(OperationJournal.Phase.REFUNDED,
                journal.find("refund-retry").orElseThrow().phase());
    }

    private StateStoreOperationJournal refundDue(UUID owner, String operationId) throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve(operationId + ".properties"));
        StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 7L);
        ConsumableReservation.SourceEvidence essence = new ConsumableReservation.SourceEvidence(
                "Draconic_Essence_Revitalizing", "player:" + owner, "hotbar", 2, 1L,
                "essence-" + operationId, 1);
        ConsumableReservation.SourceEvidence stone = new ConsumableReservation.SourceEvidence(
                "Draconic_Stone_State_Damaged", "player:" + owner, "hotbar", 1, 1L,
                "stone-" + operationId, 1);
        assertEquals(OperationJournal.Decision.APPLIED, journal.begin(new OperationJournal.Descriptor(
                operationId, operationId, OperationJournal.Kind.BONDED_STONE_REPAIR, owner,
                "repair_dead_to_stored", essence, 1, Optional.of(stone), Optional.of("authority"),
                Optional.of(UUID.randomUUID().toString()), Optional.of(UUID.randomUUID()),
                OptionalLong.of(1L), OptionalLong.of(1L))));
        assertEquals(OperationJournal.Decision.APPLIED, journal.transition(
                operationId, OperationJournal.Phase.PREPARED,
                OperationJournal.Phase.MATERIAL_CONSUMED, OperationJournal.Update.EMPTY));
        assertEquals(OperationJournal.Decision.APPLIED, journal.transition(
                operationId, OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Phase.REFUND_DUE, OperationJournal.Update.EMPTY));
        return journal;
    }
}
