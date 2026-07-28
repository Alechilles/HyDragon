package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Recovery remains scoped to durable Soul Bond operations after attunement removal. */
final class ConsumableSagaRecoveryRuntimeTest {
    @Test
    void resumesPreparedAndConsumedSoulBondOperationsOnly() {
        MemoryJournal journal = new MemoryJournal(List.of(
                entry("prepared", OperationJournal.Phase.PREPARED),
                entry("consumed", OperationJournal.Phase.MATERIAL_CONSUMED),
                entry("committed", OperationJournal.Phase.COMMITTED)));
        List<String> recovered = new ArrayList<>();
        ConsumableSagaRecoveryRuntime runtime = new ConsumableSagaRecoveryRuntime(
                journal, entry -> {
                    recovered.add(entry.operationId());
                    return CompletableFuture.completedFuture(GameplayResult.applied("recovered"));
                });

        assertEquals(2, runtime.tickSome(8));
        assertEquals(List.of("consumed", "prepared"), recovered);
        assertEquals(2L, runtime.snapshot().recoverableOperations());
    }

    private static OperationJournal.Entry entry(String operationId, OperationJournal.Phase phase) {
        ConsumableReservation.SourceEvidence source = new ConsumableReservation.SourceEvidence(
                "Wyvern_Egg", "owner", "hotbar", 0, 0L, "egg", 1);
        OperationJournal.Descriptor descriptor = new OperationJournal.Descriptor(
                operationId, "correlation-" + operationId, OperationJournal.Kind.SOUL_BOND,
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "soul-bond", source,
                1, Optional.empty(), Optional.empty(), OptionalLong.empty());
        return new OperationJournal.Entry(operationId, OperationJournal.Kind.SOUL_BOND, phase, descriptor, 0L);
    }

    private static final class MemoryJournal implements OperationJournal {
        private final List<Entry> entries;

        private MemoryJournal(List<Entry> entries) {
            this.entries = List.copyOf(entries);
        }

        @Override public Optional<Entry> find(String operationId) { return Optional.empty(); }
        @Override public List<Entry> entries() { return entries; }
        @Override public Decision begin(Descriptor descriptor) { return Decision.UNAVAILABLE; }
        @Override public Decision transition(String id, Phase expected, Phase next, Update update) {
            return Decision.UNAVAILABLE;
        }
        @Override public boolean available() { return true; }
    }
}
