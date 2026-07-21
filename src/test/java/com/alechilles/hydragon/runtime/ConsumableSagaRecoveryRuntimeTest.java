package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConsumableSagaRecoveryRuntimeTest {
    @Test
    void routesConsumedOperationsAndClosesAttunements() {
        MemoryJournal journal = new MemoryJournal();
        journal.add(entry("a", OperationJournal.Kind.SOUL_BOND, OperationJournal.Phase.MATERIAL_CONSUMED));
        journal.add(entry("b", OperationJournal.Kind.MINIWYVERN_ATTUNEMENT,
                OperationJournal.Phase.MATERIAL_CONSUMED));
        journal.add(entry("c", OperationJournal.Kind.BONDED_STONE_REPAIR,
                OperationJournal.Phase.MATERIAL_CONSUMED));
        journal.add(entry("d", OperationJournal.Kind.BONDED_STONE_REPAIR, OperationJournal.Phase.REFUND_DUE));
        AtomicInteger souls = new AtomicInteger();
        AtomicInteger repairs = new AtomicInteger();
        ConsumableSagaRecoveryRuntime runtime = new ConsumableSagaRecoveryRuntime(
                journal,
                entry -> completed(souls),
                entry -> completed(repairs));

        assertEquals(3, runtime.tickSome(8));

        assertEquals(1, souls.get());
        assertEquals(1, repairs.get());
        assertEquals(OperationJournal.Phase.COMMITTED, journal.find("b").orElseThrow().phase());
        assertEquals(1, runtime.snapshot().refundClaims());
    }

    @Test
    void boundsWorkAndDoesNotOverlapAnInFlightOperation() {
        MemoryJournal journal = new MemoryJournal();
        journal.add(entry("a", OperationJournal.Kind.SOUL_BOND, OperationJournal.Phase.MATERIAL_CONSUMED));
        journal.add(entry("b", OperationJournal.Kind.SOUL_BOND, OperationJournal.Phase.MATERIAL_CONSUMED));
        CompletableFuture<GameplayResult> pending = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        ConsumableSagaRecoveryRuntime runtime = new ConsumableSagaRecoveryRuntime(
                journal,
                entry -> {
                    calls.incrementAndGet();
                    return pending;
                },
                entry -> pending);

        assertEquals(1, runtime.tickSome(1));
        assertEquals(1, runtime.tickSome(1));
        assertEquals(2, calls.get());
        assertEquals(0, runtime.tickSome(1));

        pending.complete(GameplayResult.applied("done"));
        assertEquals(1, runtime.tickSome(1));
    }

    @Test
    void leavesPreparedAndRefundClaimsForExactReceiptOrOwnerClaimFlows() {
        MemoryJournal journal = new MemoryJournal();
        journal.add(entry("prepared", OperationJournal.Kind.MINIWYVERN_ATTUNEMENT,
                OperationJournal.Phase.PREPARED));
        journal.add(entry("refund", OperationJournal.Kind.BONDED_STONE_REPAIR,
                OperationJournal.Phase.REFUND_DUE));
        ConsumableSagaRecoveryRuntime runtime = new ConsumableSagaRecoveryRuntime(
                journal, ignored -> completed(new AtomicInteger()), ignored -> completed(new AtomicInteger()));

        assertEquals(0, runtime.tickSome(4));
        assertEquals(0, runtime.snapshot().recoverableOperations());
        assertEquals(1, runtime.snapshot().refundClaims());
    }

    private static CompletionStage<GameplayResult> completed(AtomicInteger calls) {
        calls.incrementAndGet();
        return CompletableFuture.completedFuture(GameplayResult.applied("recovered"));
    }

    private static OperationJournal.Entry entry(
            String operationId, OperationJournal.Kind kind, OperationJournal.Phase phase) {
        UUID owner = UUID.nameUUIDFromBytes(operationId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        OperationJournal.Descriptor descriptor = new OperationJournal.Descriptor(
                operationId, operationId, kind, owner, "intent",
                new ConsumableReservation.SourceEvidence(
                        "item", "player:" + owner, "hotbar", 0, 1L, "fingerprint-" + operationId, 1),
                1,
                kind == OperationJournal.Kind.BONDED_STONE_REPAIR
                        ? Optional.of(new ConsumableReservation.SourceEvidence(
                        "stone", "player:" + owner, "hotbar", 1, 1L, "stone-" + operationId, 1))
                        : Optional.empty(),
                kind == OperationJournal.Kind.BONDED_STONE_REPAIR ? Optional.of("authority") : Optional.empty(),
                kind == OperationJournal.Kind.MINIWYVERN_ATTUNEMENT
                        || kind == OperationJournal.Kind.BONDED_STONE_REPAIR
                        ? Optional.of(UUID.randomUUID().toString()) : Optional.empty(),
                kind == OperationJournal.Kind.BONDED_STONE_REPAIR
                        ? Optional.of(UUID.randomUUID()) : Optional.empty(),
                kind == OperationJournal.Kind.BONDED_STONE_REPAIR
                        ? OptionalLong.of(1L) : OptionalLong.empty(),
                kind == OperationJournal.Kind.MINIWYVERN_ATTUNEMENT
                        || kind == OperationJournal.Kind.BONDED_STONE_REPAIR
                        ? OptionalLong.of(1L) : OptionalLong.empty());
        return new OperationJournal.Entry(operationId, kind, phase, descriptor, 0L);
    }

    private static final class MemoryJournal implements OperationJournal {
        private final Map<String, Entry> entries = new LinkedHashMap<>();

        void add(Entry entry) { entries.put(entry.operationId(), entry); }
        public Optional<Entry> find(String operationId) { return Optional.ofNullable(entries.get(operationId)); }
        public List<Entry> entries() { return List.copyOf(entries.values()); }
        public Decision begin(Descriptor descriptor) { return Decision.UNAVAILABLE; }
        public Decision transition(String operationId, Phase expected, Phase next, Update update) {
            Entry current = entries.get(operationId);
            if (current == null || current.phase() != expected) return Decision.CONFLICT;
            entries.put(operationId, new Entry(operationId, current.kind(), next,
                    current.descriptor(), current.revision() + 1));
            return Decision.APPLIED;
        }
        public boolean available() { return true; }
    }
}
