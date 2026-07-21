package com.alechilles.hydragon.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsumableTransactionJournalTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BINDING = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PROFILE = "33333333-3333-3333-3333-333333333333";

    @TempDir
    Path temporaryDirectory;

    @Test
    void soulBondRestartResumesOneOriginAndOneSourceConsumption() throws Exception {
        Path file = temporaryDirectory.resolve("soul-bond.properties");
        HyDragonStateStore first = new HyDragonStateStore(file);
        ConsumableTransactionRecord prepared = soulBond("soul:owner:1", "corr:soul:1");

        assertEquals(MutationOutcome.APPLIED, first.beginConsumableTransaction(prepared));
        assertEquals(MutationOutcome.ALREADY_APPLIED, first.beginConsumableTransaction(prepared));
        assertEquals(
                MutationOutcome.CONFLICT,
                first.beginConsumableTransaction(soulBond("soul:other-operation", "corr:soul:2")));

        HyDragonStateStore restartedBeforeConsumption = new HyDragonStateStore(file);
        assertEquals(
                List.of("soul:owner:1"),
                restartedBeforeConsumption.startupInventory().consumableTransactionsToReconcile().stream()
                        .map(ConsumableTransactionRecord::operationId)
                        .toList());
        assertEquals(
                MutationOutcome.APPLIED,
                restartedBeforeConsumption.advanceConsumableTransaction(
                        "soul:owner:1",
                        0,
                        ConsumableTransactionStatus.PREPARED,
                        ConsumableTransactionStatus.MATERIAL_CONSUMED,
                        110,
                        Optional.of("tamework-provision-operation"),
                        Optional.of(PROFILE),
                        OptionalLong.of(7),
                        Optional.empty()));
        assertEquals(
                MutationOutcome.ALREADY_APPLIED,
                restartedBeforeConsumption.advanceConsumableTransaction(
                        "soul:owner:1",
                        0,
                        ConsumableTransactionStatus.PREPARED,
                        ConsumableTransactionStatus.MATERIAL_CONSUMED,
                        110,
                        Optional.of("tamework-provision-operation"),
                        Optional.of(PROFILE),
                        OptionalLong.of(7),
                        Optional.empty()));
        ConsumableTransactionRecord replayedBegin = ConsumableTransactionRecord.prepared(
                prepared.operationId(),
                prepared.correlationId(),
                prepared.kind(),
                prepared.origin(),
                prepared.ownerUuid(),
                prepared.intentId(),
                prepared.sourceItem(),
                prepared.materialQuantity(),
                prepared.authoritySourceItem(),
                Optional.empty(),
                Optional.empty(),
                prepared.bindingId(),
                prepared.bindingGeneration(),
                OptionalLong.empty(),
                9_999);
        assertEquals(
                MutationOutcome.ALREADY_APPLIED,
                restartedBeforeConsumption.beginConsumableTransaction(replayedBegin));

        HyDragonStateStore restartedAfterConsumption = new HyDragonStateStore(file);
        ConsumableTransactionRecord consumed = restartedAfterConsumption.snapshot()
                .consumableTransaction("soul:owner:1")
                .orElseThrow();
        assertEquals(ConsumableTransactionStatus.MATERIAL_CONSUMED, consumed.status());
        assertEquals(1, consumed.revision());
        assertEquals(prepared.sourceItem(), consumed.sourceItem());
        assertEquals(
                MutationOutcome.APPLIED,
                restartedAfterConsumption.advanceConsumableTransaction(
                        "soul:owner:1",
                        1,
                        ConsumableTransactionStatus.MATERIAL_CONSUMED,
                        ConsumableTransactionStatus.COMMITTED,
                        120,
                        Optional.empty(),
                        Optional.empty(),
                        OptionalLong.empty(),
                        Optional.empty()));

        HyDragonStateStore complete = new HyDragonStateStore(file);
        assertEquals(ConsumableTransactionStatus.COMMITTED,
                complete.snapshot().consumableTransaction("soul:owner:1").orElseThrow().status());
        assertTrue(complete.startupInventory().consumableTransactionsToReconcile().isEmpty());
    }

    @Test
    void attunementCannotChangeCanonicalProfileOrCommitTwiceAfterRestart() throws Exception {
        Path file = temporaryDirectory.resolve("attunement.properties");
        HyDragonStateStore store = new HyDragonStateStore(file);
        ConsumableTransactionRecord attunement = ConsumableTransactionRecord.prepared(
                "attune:owner:fire:1",
                "corr:attune:1",
                ConsumableTransactionKind.MINIWYVERN_ATTUNEMENT,
                new OperationOrigin("hydragon", "attune:owner:fire:1"),
                OWNER,
                "fire",
                source("Draconic_Essence_Fire", "sha256:fire-source", 8),
                1,
                Optional.empty(),
                Optional.of("profile-data-cas:fire:1"),
                Optional.of(PROFILE),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalLong.of(12),
                200);
        assertEquals(MutationOutcome.APPLIED, store.beginConsumableTransaction(attunement));
        assertEquals(MutationOutcome.APPLIED, store.advanceConsumableTransaction(
                attunement.operationId(),
                0,
                ConsumableTransactionStatus.PREPARED,
                ConsumableTransactionStatus.MATERIAL_CONSUMED,
                210,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                Optional.empty()));

        HyDragonStateStore restarted = new HyDragonStateStore(file);
        assertEquals(ConsumableTransactionStatus.MATERIAL_CONSUMED,
                restarted.startupInventory().consumableTransactionsToReconcile().getFirst().status());
        assertEquals(MutationOutcome.CONFLICT, restarted.advanceConsumableTransaction(
                attunement.operationId(),
                1,
                ConsumableTransactionStatus.MATERIAL_CONSUMED,
                ConsumableTransactionStatus.COMMITTED,
                220,
                Optional.empty(),
                Optional.of("different-profile"),
                OptionalLong.empty(),
                Optional.empty()));
        assertEquals(MutationOutcome.APPLIED, restarted.advanceConsumableTransaction(
                attunement.operationId(),
                1,
                ConsumableTransactionStatus.MATERIAL_CONSUMED,
                ConsumableTransactionStatus.COMMITTED,
                220,
                Optional.empty(),
                Optional.of(PROFILE),
                OptionalLong.of(12),
                Optional.empty()));
        assertEquals(MutationOutcome.ALREADY_APPLIED, restarted.advanceConsumableTransaction(
                attunement.operationId(),
                1,
                ConsumableTransactionStatus.MATERIAL_CONSUMED,
                ConsumableTransactionStatus.COMMITTED,
                220,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                Optional.empty()));
    }

    @Test
    void repairConvergesToRefundAndCanNeverAlsoCommit() throws Exception {
        Path file = temporaryDirectory.resolve("repair.properties");
        HyDragonStateStore store = new HyDragonStateStore(file);
        ConsumableTransactionRecord repair = repair("repair:binding:9");
        assertEquals(MutationOutcome.APPLIED, store.beginConsumableTransaction(repair));
        assertEquals(MutationOutcome.APPLIED, store.advanceConsumableTransaction(
                repair.operationId(), 0, ConsumableTransactionStatus.PREPARED,
                ConsumableTransactionStatus.MATERIAL_CONSUMED, 310,
                Optional.empty(), Optional.empty(), OptionalLong.empty(), Optional.empty()));

        HyDragonStateStore afterConsumeCrash = new HyDragonStateStore(file);
        assertEquals(MutationOutcome.APPLIED, afterConsumeCrash.advanceConsumableTransaction(
                repair.operationId(), 1, ConsumableTransactionStatus.MATERIAL_CONSUMED,
                ConsumableTransactionStatus.REFUND_DUE, 320,
                Optional.empty(), Optional.empty(), OptionalLong.empty(), Optional.empty()));

        HyDragonStateStore afterDenialCrash = new HyDragonStateStore(file);
        assertEquals(ConsumableTransactionStatus.REFUND_DUE,
                afterDenialCrash.startupInventory().consumableTransactionsToReconcile().getFirst().status());
        assertEquals(MutationOutcome.APPLIED, afterDenialCrash.advanceConsumableTransaction(
                repair.operationId(), 2, ConsumableTransactionStatus.REFUND_DUE,
                ConsumableTransactionStatus.REFUNDED, 330,
                Optional.empty(), Optional.empty(), OptionalLong.empty(), Optional.empty()));
        assertEquals(MutationOutcome.ALREADY_APPLIED, afterDenialCrash.advanceConsumableTransaction(
                repair.operationId(), 2, ConsumableTransactionStatus.REFUND_DUE,
                ConsumableTransactionStatus.REFUNDED, 330,
                Optional.empty(), Optional.empty(), OptionalLong.empty(), Optional.empty()));
        assertEquals(MutationOutcome.CONFLICT, afterDenialCrash.advanceConsumableTransaction(
                repair.operationId(), 1, ConsumableTransactionStatus.MATERIAL_CONSUMED,
                ConsumableTransactionStatus.COMMITTED, 340,
                Optional.empty(), Optional.empty(), OptionalLong.empty(), Optional.empty()));
        assertTrue(new HyDragonStateStore(file).startupInventory()
                .consumableTransactionsToReconcile().isEmpty());
    }

    @Test
    void ambiguousRepairIsDurablyQuarantinedWithBothSourceFingerprints() throws Exception {
        Path file = temporaryDirectory.resolve("ambiguous-repair.properties");
        ConsumableTransactionRecord repair = repair("repair:ambiguous");
        HyDragonStateStore store = new HyDragonStateStore(file);
        assertEquals(MutationOutcome.APPLIED, store.beginConsumableTransaction(repair));
        assertEquals(MutationOutcome.APPLIED, store.advanceConsumableTransaction(
                repair.operationId(),
                0,
                ConsumableTransactionStatus.PREPARED,
                ConsumableTransactionStatus.QUARANTINED,
                305,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                Optional.of("authority status and exact damaged slot disagree")));

        HyDragonStateStore restarted = new HyDragonStateStore(file);
        ConsumableTransactionRecord quarantined = restarted.startupInventory()
                .consumableTransactionsToReconcile().getFirst();
        assertEquals(ConsumableTransactionStatus.QUARANTINED, quarantined.status());
        assertEquals("sha256:repair-source", quarantined.sourceItem().itemFingerprint());
        assertEquals("sha256:damaged-stone-binding-generation-9",
                quarantined.authoritySourceItem().orElseThrow().itemFingerprint());
        assertThrows(IllegalArgumentException.class, () -> restarted.advanceConsumableTransaction(
                repair.operationId(),
                1,
                ConsumableTransactionStatus.QUARANTINED,
                ConsumableTransactionStatus.COMMITTED,
                306,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                Optional.empty()));
    }

    @Test
    void failedAtomicCasDoesNotPublishTheNextPhase() throws Exception {
        Path file = temporaryDirectory.resolve("atomic-cas.properties");
        HyDragonStateStore initial = new HyDragonStateStore(file);
        ConsumableTransactionRecord prepared = soulBond("soul:atomic", "corr:atomic");
        assertEquals(MutationOutcome.APPLIED, initial.beginConsumableTransaction(prepared));
        byte[] committed = Files.readAllBytes(file);

        AtomicInteger attempts = new AtomicInteger();
        HyDragonStateStore failing = new HyDragonStateStore(file, (destination, content) -> {
            attempts.incrementAndGet();
            throw new IOException("simulated crash before atomic replacement");
        });
        assertThrows(IOException.class, () -> failing.advanceConsumableTransaction(
                prepared.operationId(), 0, ConsumableTransactionStatus.PREPARED,
                ConsumableTransactionStatus.MATERIAL_CONSUMED, 101,
                Optional.empty(), Optional.empty(), OptionalLong.empty(), Optional.empty()));
        assertEquals(1, attempts.get());
        assertEquals(ConsumableTransactionStatus.PREPARED,
                failing.snapshot().consumableTransaction(prepared.operationId()).orElseThrow().status());
        assertTrue(java.util.Arrays.equals(committed, Files.readAllBytes(file)));
        assertEquals(ConsumableTransactionStatus.PREPARED,
                new HyDragonStateStore(file).snapshot().consumableTransaction(prepared.operationId()).orElseThrow().status());
    }

    @Test
    void unknownOrMalformedJournalRowsAreQuarantinedAndImmutable() throws Exception {
        Path file = temporaryDirectory.resolve("quarantined-transaction.properties");
        String operationId = "repair:unsupported";
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(operationId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String prefix = "transaction." + encoded + ".";
        Properties properties = new Properties();
        properties.setProperty("store.schema", "1");
        properties.setProperty(prefix + "schema", "99");
        properties.setProperty(prefix + "operationId", operationId);
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "unsupported transaction");
        }

        HyDragonStateStore store = new HyDragonStateStore(file);
        assertFalse(store.snapshot().consumableTransactions().containsKey(operationId));
        assertTrue(store.startupInventory().quarantinedRecords().stream()
                .anyMatch(record -> record.type() == PersistentRecordType.CONSUMABLE_TRANSACTION
                        && record.persistentId().equals(operationId)
                        && record.reason().contains("Unsupported")));
        assertEquals(MutationOutcome.QUARANTINED,
                store.beginConsumableTransaction(repair(operationId)));
    }

    @Test
    void recordsRejectIncompleteRepairEvidenceAndIllegalPhaseEdges() {
        assertThrows(IllegalArgumentException.class, () -> ConsumableTransactionRecord.prepared(
                "repair:incomplete",
                "corr:repair:incomplete",
                ConsumableTransactionKind.BONDED_STONE_REPAIR,
                new OperationOrigin("hydragon", "repair:incomplete"),
                OWNER,
                "revive",
                source("Revitalizing_Essence", "sha256:repair", 1),
                1,
                Optional.of(damagedStoneSource()),
                Optional.empty(),
                Optional.of(PROFILE),
                Optional.of(BINDING),
                OptionalLong.of(5),
                OptionalLong.of(8),
                1));
        assertThrows(IllegalArgumentException.class, () -> soulBond("soul:illegal", "corr:illegal")
                .transitionTo(
                        ConsumableTransactionStatus.REFUND_DUE,
                        101,
                        Optional.empty(),
                        Optional.empty(),
                        OptionalLong.empty(),
                        Optional.empty()));
    }

    private static ConsumableTransactionRecord soulBond(String operationId, String correlationId) {
        return ConsumableTransactionRecord.prepared(
                operationId,
                correlationId,
                ConsumableTransactionKind.SOUL_BOND,
                new OperationOrigin("hydragon", "soul-bond:owner:once"),
                OWNER,
                "Wyvern_Mini",
                source("Draconic_Soul_Bond", "sha256:soul-source", 1),
                1,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                100);
    }

    private static ConsumableTransactionRecord repair(String operationId) {
        return ConsumableTransactionRecord.prepared(
                operationId,
                "corr:" + operationId,
                ConsumableTransactionKind.BONDED_STONE_REPAIR,
                new OperationOrigin("hydragon", operationId),
                OWNER,
                "Draconic_Stone:dead-to-stored",
                source("Revitalizing_Essence", "sha256:repair-source", 3),
                1,
                Optional.of(damagedStoneSource()),
                Optional.of("tamework-vessel-operation"),
                Optional.of(PROFILE),
                Optional.of(BINDING),
                OptionalLong.of(9),
                OptionalLong.of(14),
                300);
    }

    private static SourceItemEvidence source(String itemId, String fingerprint, int quantity) {
        return new SourceItemEvidence(
                itemId,
                OWNER.toString(),
                "player-inventory/main",
                4,
                23,
                fingerprint,
                quantity);
    }

    private static SourceItemEvidence damagedStoneSource() {
        return new SourceItemEvidence(
                "Draconic_Stone_State_Damaged",
                OWNER.toString(),
                "player-inventory/main",
                7,
                44,
                "sha256:damaged-stone-binding-generation-9",
                1);
    }
}
