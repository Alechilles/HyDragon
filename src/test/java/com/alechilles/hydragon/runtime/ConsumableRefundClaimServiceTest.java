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
    void eggRecoveryClaimIsOwnerScopedAndClosesExactlyOnce() throws Exception {
        UUID owner = UUID.randomUUID();
        StateStoreOperationJournal journal = refundDue(owner, "egg-refund");
        ConsumableRefundClaimService service = new ConsumableRefundClaimService(journal);
        AtomicInteger deliveries = new AtomicInteger();

        assertEquals(GameplayResult.Status.DENIED, service.claim(UUID.randomUUID(), "egg-refund", claim ->
                CompletableFuture.completedFuture(ConsumableRefundClaimService.RefundDelivery.Decision.APPLIED))
                .toCompletableFuture().join().status());
        assertEquals(GameplayResult.Status.APPLIED, service.claim(owner, "egg-refund", claim -> {
            deliveries.incrementAndGet();
            assertEquals("Wyvern_Egg", claim.itemId());
            assertEquals(1, claim.quantity());
            return CompletableFuture.completedFuture(ConsumableRefundClaimService.RefundDelivery.Decision.APPLIED);
        }).toCompletableFuture().join().status());
        assertEquals(GameplayResult.Status.ALREADY_APPLIED, service.claim(owner, "egg-refund", claim -> {
            deliveries.incrementAndGet();
            return CompletableFuture.completedFuture(ConsumableRefundClaimService.RefundDelivery.Decision.APPLIED);
        }).toCompletableFuture().join().status());
        assertEquals(1, deliveries.get());
        assertEquals(OperationJournal.Phase.REFUNDED, journal.find("egg-refund").orElseThrow().phase());
    }

    private StateStoreOperationJournal refundDue(UUID owner, String operationId) throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve(operationId + ".properties"));
        StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 7L);
        ConsumableReservation.SourceEvidence egg = new ConsumableReservation.SourceEvidence(
                "Wyvern_Egg", "player:" + owner, "hotbar", 2, 1L, "egg-" + operationId, 1);
        assertEquals(OperationJournal.Decision.APPLIED, journal.begin(new OperationJournal.Descriptor(
                operationId, operationId, OperationJournal.Kind.SOUL_BOND, owner,
                "provision-and-link", egg, 1, Optional.empty(), Optional.empty(),
                OptionalLong.empty())));
        assertEquals(OperationJournal.Decision.APPLIED, journal.transition(
                operationId, OperationJournal.Phase.PREPARED,
                OperationJournal.Phase.MATERIAL_CONSUMED, OperationJournal.Update.EMPTY));
        assertEquals(OperationJournal.Decision.APPLIED, journal.transition(
                operationId, OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Phase.REFUND_DUE, OperationJournal.Update.EMPTY));
        return journal;
    }
}
