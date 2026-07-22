package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConsumableSagaRecoveryRuntimeTest {
    @Test
    void routesSoulBondAndClosesConsumedAttunement() {
        MemoryJournal journal = new MemoryJournal();
        journal.add(entry("soul", OperationJournal.Kind.SOUL_BOND, OperationJournal.Phase.MATERIAL_CONSUMED));
        journal.add(entry("attune", OperationJournal.Kind.MINIWYVERN_ATTUNEMENT,
                OperationJournal.Phase.MATERIAL_CONSUMED));
        AtomicInteger soulCalls = new AtomicInteger();
        ConsumableSagaRecoveryRuntime runtime = new ConsumableSagaRecoveryRuntime(journal, entry -> {
            soulCalls.incrementAndGet();
            return CompletableFuture.completedFuture(GameplayResult.applied("recovered"));
        });

        assertEquals(2, runtime.tickSome(8));
        assertEquals(1, soulCalls.get());
        assertEquals(OperationJournal.Phase.COMMITTED, journal.find("attune").orElseThrow().phase());
    }

    @Test
    void preparedAttunementIsNotRecoveredWithoutItsExactReceipt() {
        MemoryJournal journal = new MemoryJournal();
        journal.add(entry("attune", OperationJournal.Kind.MINIWYVERN_ATTUNEMENT,
                OperationJournal.Phase.PREPARED));
        ConsumableSagaRecoveryRuntime runtime = new ConsumableSagaRecoveryRuntime(
                journal, ignored -> CompletableFuture.completedFuture(GameplayResult.applied("unused")));
        assertEquals(0, runtime.tickSome(4));
        assertEquals(0, runtime.snapshot().recoverableOperations());
    }

    private static OperationJournal.Entry entry(
            String operationId, OperationJournal.Kind kind, OperationJournal.Phase phase) {
        UUID owner = UUID.nameUUIDFromBytes(operationId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        OperationJournal.Descriptor descriptor = new OperationJournal.Descriptor(
                operationId, operationId, kind, owner, "intent",
                new ConsumableReservation.SourceEvidence(
                        "item", "player:" + owner, "hotbar", 0, 1L, "fingerprint-" + operationId, 1),
                1, Optional.empty(), Optional.empty(),
                kind == OperationJournal.Kind.MINIWYVERN_ATTUNEMENT
                        ? Optional.of(UUID.randomUUID().toString()) : Optional.empty(),
                Optional.empty(), OptionalLong.empty(),
                kind == OperationJournal.Kind.MINIWYVERN_ATTUNEMENT
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
