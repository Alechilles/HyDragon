package com.alechilles.hydragon.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Bounded, restart-safe reconciliation loop for consumable sagas with durable authority work to resume.
 *
 * <p>The journal remains authoritative. Soul Bond preparation is safe to query/resume by its stable
 * Tamework idempotency key even without a live item receipt; it remains PREPARED until a later item
 * reservation can commit consumption. Other prepared operations still require their exact receipt.</p>
 */
public final class ConsumableSagaRecoveryRuntime {
    private final OperationJournal journal;
    private final Recovery soulBondRecovery;
    private final Recovery repairRecovery;
    private final Set<String> inFlight = new HashSet<>();
    private String cursor;

    public ConsumableSagaRecoveryRuntime(
            OperationJournal journal,
            SoulBondService soulBonds,
            BondedStoneRepairService repairs) {
        this(journal, soulBonds::recover, repairs::recover);
    }

    ConsumableSagaRecoveryRuntime(
            OperationJournal journal,
            Recovery soulBondRecovery,
            Recovery repairRecovery) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.soulBondRecovery = Objects.requireNonNull(soulBondRecovery, "soulBondRecovery");
        this.repairRecovery = Objects.requireNonNull(repairRecovery, "repairRecovery");
    }

    /** Starts at most {@code maximumOperations} recoveries and never overlaps the same operation. */
    public synchronized int tickSome(int maximumOperations) {
        if (maximumOperations <= 0 || !journal.available()) return 0;
        List<OperationJournal.Entry> candidates = journal.entries().stream()
                .filter(ConsumableSagaRecoveryRuntime::automaticallyRecoverable)
                .sorted(Comparator.comparing(OperationJournal.Entry::operationId))
                .toList();
        if (candidates.isEmpty()) {
            cursor = null;
            return 0;
        }

        List<OperationJournal.Entry> ordered = rotateAfter(candidates, cursor);
        int started = 0;
        for (OperationJournal.Entry entry : ordered) {
            if (started >= maximumOperations) break;
            if (!inFlight.add(entry.operationId())) continue;
            cursor = entry.operationId();
            started++;
            recover(entry).whenComplete((ignored, failure) -> complete(entry.operationId()));
        }
        return started;
    }

    public synchronized Snapshot snapshot() {
        long recoverable = journal.entries().stream()
                .filter(ConsumableSagaRecoveryRuntime::automaticallyRecoverable)
                .count();
        long refundClaims = journal.entries().stream()
                .filter(entry -> entry.phase() == OperationJournal.Phase.REFUND_DUE)
                .count();
        return new Snapshot(recoverable, refundClaims, inFlight.size());
    }

    private CompletionStage<GameplayResult> recover(OperationJournal.Entry entry) {
        try {
            return switch (entry.kind()) {
                case SOUL_BOND -> nonNull(soulBondRecovery.recover(entry));
                case BONDED_STONE_REPAIR -> nonNull(repairRecovery.recover(entry));
                case MINIWYVERN_ATTUNEMENT -> CompletableFuture.completedFuture(
                        closeCommittedAttunement(entry));
            };
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(
                    GameplayResult.reconciliation("consumable saga recovery remains pending"));
        }
    }

    private GameplayResult closeCommittedAttunement(OperationJournal.Entry entry) {
        OperationJournal.Decision decision = journal.transition(
                entry.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
        return decision == OperationJournal.Decision.APPLIED
                || decision == OperationJournal.Decision.ALREADY_APPLIED
                ? new GameplayResult(GameplayResult.Status.ALREADY_APPLIED, "Miniwyvern attuned")
                : GameplayResult.reconciliation("attunement journal closure remains pending");
    }

    private static CompletionStage<GameplayResult> nonNull(CompletionStage<GameplayResult> stage) {
        return stage == null
                ? CompletableFuture.completedFuture(
                GameplayResult.reconciliation("consumable saga recovery remains pending"))
                : stage;
    }

    private synchronized void complete(String operationId) {
        inFlight.remove(operationId);
    }

    private static boolean automaticallyRecoverable(OperationJournal.Entry entry) {
        return entry.phase() == OperationJournal.Phase.MATERIAL_CONSUMED
                || (entry.kind() == OperationJournal.Kind.SOUL_BOND
                && entry.phase() == OperationJournal.Phase.PREPARED);
    }

    private static List<OperationJournal.Entry> rotateAfter(
            List<OperationJournal.Entry> entries, String cursor) {
        if (cursor == null) return entries;
        int split = 0;
        while (split < entries.size()
                && entries.get(split).operationId().compareTo(cursor) <= 0) {
            split++;
        }
        if (split == 0 || split == entries.size()) return entries;
        List<OperationJournal.Entry> rotated = new ArrayList<>(entries.size());
        rotated.addAll(entries.subList(split, entries.size()));
        rotated.addAll(entries.subList(0, split));
        return List.copyOf(rotated);
    }

    @FunctionalInterface
    interface Recovery {
        CompletionStage<GameplayResult> recover(OperationJournal.Entry entry);
    }

    public record Snapshot(long recoverableOperations, long refundClaims, int inFlightOperations) {
        public Snapshot {
            if (recoverableOperations < 0 || refundClaims < 0 || inFlightOperations < 0) {
                throw new IllegalArgumentException("recovery counters cannot be negative");
            }
        }
    }
}
