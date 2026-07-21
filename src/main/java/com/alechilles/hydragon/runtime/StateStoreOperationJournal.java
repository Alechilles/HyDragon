package com.alechilles.hydragon.runtime;

import com.alechilles.hydragon.persistence.ConsumableTransactionKind;
import com.alechilles.hydragon.persistence.ConsumableTransactionRecord;
import com.alechilles.hydragon.persistence.ConsumableTransactionStatus;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.MutationOutcome;
import com.alechilles.hydragon.persistence.OperationOrigin;
import com.alechilles.hydragon.persistence.SourceItemEvidence;
import java.io.IOException;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/** Production adapter from runtime saga ports to HyDragon's durable CAS journal. */
public final class StateStoreOperationJournal implements OperationJournal {
    private final HyDragonStateStore store;
    private final LongSupplier clock;

    public StateStoreOperationJournal(HyDragonStateStore store, LongSupplier clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<Entry> find(String operationId) {
        return store.snapshot().consumableTransaction(operationId).map(StateStoreOperationJournal::entry);
    }

    @Override
    public List<Entry> entries() {
        return store.snapshot().consumableTransactions().values().stream()
                .map(StateStoreOperationJournal::entry)
                .sorted(java.util.Comparator.comparing(Entry::operationId))
                .toList();
    }

    @Override
    public Decision begin(Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        ConsumableReservation.SourceEvidence source = descriptor.source();
        long now = Math.max(0L, clock.getAsLong());
        ConsumableTransactionRecord record = ConsumableTransactionRecord.prepared(
                descriptor.operationId(),
                descriptor.correlationId(),
                kind(descriptor.kind()),
                new OperationOrigin(TameworkGameplayAdapter.CALLER_NAMESPACE, descriptor.operationId()),
                descriptor.ownerUuid(),
                descriptor.intentId(),
                new SourceItemEvidence(
                        source.itemId(), source.holderEvidenceId(), source.containerPath(),
                        source.inventorySlot(), source.inventoryRevision(), source.itemFingerprint(),
                        source.stackQuantityAtPrepare()),
                descriptor.materialQuantity(),
                descriptor.authoritySource().map(StateStoreOperationJournal::toPersistentSource),
                descriptor.authorityOperationId(),
                descriptor.profileId(),
                descriptor.bindingId(),
                descriptor.bindingGeneration(),
                descriptor.profileRevision(),
                now);
        try {
            return decision(store.beginConsumableTransaction(record));
        } catch (IOException | RuntimeException failure) {
            return Decision.UNAVAILABLE;
        }
    }

    @Override
    public Decision transition(String operationId, Phase expected, Phase next, Update update) {
        Objects.requireNonNull(update, "update");
        Optional<ConsumableTransactionRecord> current = store.snapshot().consumableTransaction(operationId);
        if (current.isEmpty()) return Decision.CONFLICT;
        try {
            return decision(store.advanceConsumableTransaction(
                    operationId,
                    current.orElseThrow().revision(),
                    status(expected),
                    status(next),
                    Math.max(current.orElseThrow().updatedAtEpochMillis(), Math.max(0L, clock.getAsLong())),
                    update.authorityOperationId(),
                    update.profileId(),
                    update.profileRevision(),
                    update.quarantineReason()));
        } catch (IOException | RuntimeException failure) {
            return Decision.UNAVAILABLE;
        }
    }

    @Override
    public boolean available() {
        return store.snapshot().writable();
    }

    private static Entry entry(ConsumableTransactionRecord record) {
        SourceItemEvidence source = record.sourceItem();
        Descriptor descriptor = new Descriptor(
                record.operationId(), record.correlationId(), kind(record.kind()), record.ownerUuid(),
                record.intentId(), new ConsumableReservation.SourceEvidence(
                source.itemId(), source.holderEvidenceId(), source.containerPath(), source.inventorySlot(),
                source.inventoryRevision(), source.itemFingerprint(), source.stackQuantityAtPrepare()),
                record.materialQuantity(),
                record.authoritySourceItem().map(StateStoreOperationJournal::toRuntimeSource),
                record.authorityOperationId(), record.profileId(), record.bindingId(),
                record.bindingGeneration(), record.profileRevision());
        return new Entry(record.operationId(), descriptor.kind(), phase(record.status()), descriptor, record.revision());
    }

    private static ConsumableTransactionKind kind(Kind kind) {
        return ConsumableTransactionKind.valueOf(kind.name());
    }

    private static SourceItemEvidence toPersistentSource(ConsumableReservation.SourceEvidence source) {
        return new SourceItemEvidence(
                source.itemId(), source.holderEvidenceId(), source.containerPath(), source.inventorySlot(),
                source.inventoryRevision(), source.itemFingerprint(), source.stackQuantityAtPrepare());
    }

    private static ConsumableReservation.SourceEvidence toRuntimeSource(SourceItemEvidence source) {
        return new ConsumableReservation.SourceEvidence(
                source.itemId(), source.holderEvidenceId(), source.containerPath(), source.inventorySlot(),
                source.inventoryRevision(), source.itemFingerprint(), source.stackQuantityAtPrepare());
    }

    private static Kind kind(ConsumableTransactionKind kind) {
        return Kind.valueOf(kind.name());
    }

    private static ConsumableTransactionStatus status(Phase phase) {
        return ConsumableTransactionStatus.valueOf(phase.name());
    }

    private static Phase phase(ConsumableTransactionStatus status) {
        return Phase.valueOf(status.name());
    }

    private static Decision decision(MutationOutcome outcome) {
        return switch (outcome) {
            case APPLIED -> Decision.APPLIED;
            case ALREADY_APPLIED -> Decision.ALREADY_APPLIED;
            case CONFLICT -> Decision.CONFLICT;
            case QUARANTINED -> Decision.QUARANTINED;
            case STORE_READ_ONLY -> Decision.UNAVAILABLE;
        };
    }
}
