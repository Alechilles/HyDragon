package com.alechilles.hydragon.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HyDragonStateStoreTest {
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROFILE_ONE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROFILE_TWO = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TARGET_NPC = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @TempDir
    Path temporaryDirectory;

    @Test
    void completesSoulBondAndNeutralMiniwyvernExtensionInOneGeneration() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        String operationId = "hydragon:soul-bond:" + owner;
        HyDragonStateStore store = new HyDragonStateStore(
                temporaryDirectory.resolve("atomic-soul-bond.properties"));

        assertEquals(MutationOutcome.APPLIED, store.beginSoulBond(owner, operationId));
        assertEquals(MutationOutcome.APPLIED,
                store.completeSoulBondWithMiniwyvernProfile(owner, operationId, profile, 42L));
        assertEquals(MutationOutcome.ALREADY_APPLIED,
                store.completeSoulBondWithMiniwyvernProfile(owner, operationId, profile, 42L));

        assertEquals(profile, store.snapshot().playerSoulBond(owner).orElseThrow().profileId().orElseThrow());
        ProfileExtensionRecord extension = store.snapshot().profileExtension(profile).orElseThrow();
        assertEquals(ProfileKind.SOULBOUND_MINIWYVERN, extension.kind());
        assertEquals(Optional.of("neutral"), extension.archetypeId());
        assertEquals(Optional.of(operationId), extension.lastOperationId());
    }

    @Test
    void deniedSoulBondCompensationIsAtomicIdempotentAndRestartSafe() throws Exception {
        Path file = temporaryDirectory.resolve("soul-bond-denial.properties");
        String operationId = "hydragon:soul-bond:" + UUID.randomUUID();
        String authorityOperationId = UUID.randomUUID().toString();
        HyDragonStateStore store = new HyDragonStateStore(file);
        assertEquals(MutationOutcome.APPLIED, store.beginSoulBond(PLAYER_ONE, operationId));
        assertEquals(MutationOutcome.APPLIED,
                store.beginConsumableTransaction(soulBondTransaction(operationId, PLAYER_ONE, 10L)));

        assertEquals(MutationOutcome.APPLIED, store.compensateDeniedSoulBond(
                PLAYER_ONE, operationId, Optional.of(authorityOperationId), 20L));
        assertEquals(MutationOutcome.ALREADY_APPLIED, store.compensateDeniedSoulBond(
                PLAYER_ONE, operationId, Optional.of(authorityOperationId), 30L));
        assertEquals(SoulBondState.UNCLAIMED,
                store.snapshot().playerSoulBond(PLAYER_ONE).orElseThrow().state());
        ConsumableTransactionRecord canceled = store.snapshot()
                .consumableTransaction(operationId).orElseThrow();
        assertEquals(ConsumableTransactionStatus.CANCELED, canceled.status());
        assertEquals(Optional.of(authorityOperationId), canceled.authorityOperationId());
        assertEquals(1L, canceled.revision());

        HyDragonStateStore restarted = new HyDragonStateStore(file);
        assertEquals(SoulBondState.UNCLAIMED,
                restarted.snapshot().playerSoulBond(PLAYER_ONE).orElseThrow().state());
        assertEquals(ConsumableTransactionStatus.CANCELED,
                restarted.snapshot().consumableTransaction(operationId).orElseThrow().status());
        assertEquals(MutationOutcome.ALREADY_APPLIED, restarted.compensateDeniedSoulBond(
                PLAYER_ONE, operationId, Optional.of(authorityOperationId), 40L));
    }

    @Test
    void failedDeniedSoulBondCompensationPublishesNeitherHalf() throws Exception {
        Path file = temporaryDirectory.resolve("soul-bond-denial-failure.properties");
        String operationId = "hydragon:soul-bond:" + UUID.randomUUID();
        HyDragonStateStore initial = new HyDragonStateStore(file);
        assertEquals(MutationOutcome.APPLIED, initial.beginSoulBond(PLAYER_ONE, operationId));
        assertEquals(MutationOutcome.APPLIED,
                initial.beginConsumableTransaction(soulBondTransaction(operationId, PLAYER_ONE, 10L)));
        byte[] before = Files.readAllBytes(file);

        HyDragonStateStore failing = new HyDragonStateStore(file, (destination, content) -> {
            throw new IOException("simulated compensation replacement failure");
        });
        assertThrows(IOException.class, () -> failing.compensateDeniedSoulBond(
                PLAYER_ONE, operationId, Optional.empty(), 20L));

        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(SoulBondState.PENDING,
                failing.snapshot().playerSoulBond(PLAYER_ONE).orElseThrow().state());
        assertEquals(ConsumableTransactionStatus.PREPARED,
                failing.snapshot().consumableTransaction(operationId).orElseThrow().status());
        HyDragonStateStore restarted = new HyDragonStateStore(file);
        assertEquals(SoulBondState.PENDING,
                restarted.snapshot().playerSoulBond(PLAYER_ONE).orElseThrow().state());
        assertEquals(ConsumableTransactionStatus.PREPARED,
                restarted.snapshot().consumableTransaction(operationId).orElseThrow().status());
    }

    @Test
    void restartRoundTripBuildsReconciliationInventory() throws Exception {
        Path file = temporaryDirectory.resolve("hydragon-state.properties");
        HyDragonStateStore first = new HyDragonStateStore(file);

        assertEquals(MutationOutcome.APPLIED, first.beginSoulBond(PLAYER_ONE, "soul:one"));
        assertEquals(MutationOutcome.APPLIED, first.completeSoulBond(PLAYER_ONE, "soul:one", PROFILE_ONE, 1_000));
        assertEquals(MutationOutcome.APPLIED, first.beginSoulBond(PLAYER_TWO, "soul:two"));
        ProfileExtensionRecord profile = ProfileExtensionRecord.soulboundMiniwyvern(
                PROFILE_ONE,
                "hydragon:miniwyvern",
                "neutral",
                Optional.of("soul:one"));
        EncounterRecord encounter = encounterRecord();
        assertEquals(MutationOutcome.APPLIED, first.putProfileExtension(profile));
        assertEquals(MutationOutcome.APPLIED, first.putEncounter(encounter));

        HyDragonStateStore restarted = new HyDragonStateStore(file);

        assertEquals(first.snapshot().playerSoulBonds(), restarted.snapshot().playerSoulBonds());
        assertEquals(first.snapshot().profileExtensions(), restarted.snapshot().profileExtensions());
        assertEquals(first.snapshot().encounters(), restarted.snapshot().encounters());
        assertEquals(SoulBondState.CLAIMED, restarted.snapshot().playerSoulBond(PLAYER_ONE).orElseThrow().state());
        assertEquals(PROFILE_ONE, restarted.snapshot().playerSoulBond(PLAYER_ONE)
                .orElseThrow()
                .profileId()
                .orElseThrow());
        assertEquals(Set.of(PLAYER_ONE, PLAYER_TWO), restarted.snapshot().encounter("encounter:nordic:1")
                .orElseThrow()
                .eligiblePlayerUuids());

        ReconciliationInventory startup = restarted.startupInventory();
        assertEquals(List.of(PLAYER_TWO), startup.soulBondsNeedingReconciliation().stream()
                .map(PlayerSoulBondRecord::playerUuid)
                .toList());
        assertEquals(List.of(PLAYER_ONE), startup.claimedSoulBondsToVerify().stream()
                .map(PlayerSoulBondRecord::playerUuid)
                .toList());
        assertEquals(1, startup.profileExtensionsToVerify().size());
        assertEquals(1, startup.encountersToResumeOrCleanUp().size());
        assertTrue(startup.quarantinedRecords().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> restarted.snapshot().encounters().clear());
    }

    @Test
    void pendingProfileProjectionRoundTripsAndIsRemovedIdempotently() throws Exception {
        Path file = temporaryDirectory.resolve("pending-projection.properties");
        UUID operationId = UUID.randomUUID();
        PendingProfileProjectionRecord record = PendingProfileProjectionRecord.captured(
                operationId, PROFILE_TWO.toString(), "NordicDrake", 123L);
        HyDragonStateStore first = new HyDragonStateStore(file);

        assertEquals(MutationOutcome.APPLIED, first.putPendingProfileProjection(record));
        assertEquals(MutationOutcome.ALREADY_APPLIED, first.putPendingProfileProjection(record));
        assertEquals(MutationOutcome.ALREADY_APPLIED, first.putPendingProfileProjection(
                PendingProfileProjectionRecord.captured(
                        operationId, PROFILE_TWO.toString(), "NordicDrake", 999L)));
        assertEquals(MutationOutcome.CONFLICT, first.putPendingProfileProjection(
                PendingProfileProjectionRecord.captured(
                        operationId, PROFILE_ONE.toString(), "NordicDrake", 123L)));

        HyDragonStateStore restarted = new HyDragonStateStore(file);
        assertEquals(record, restarted.snapshot().pendingProfileProjection(operationId).orElseThrow());
        assertEquals(List.of(record), restarted.startupInventory().profileProjectionsToRetry());

        assertEquals(MutationOutcome.APPLIED, restarted.removePendingProfileProjection(operationId));
        assertEquals(MutationOutcome.ALREADY_APPLIED, restarted.removePendingProfileProjection(operationId));
        assertTrue(new HyDragonStateStore(file).snapshot().pendingProfileProjections().isEmpty());
    }

    @Test
    void preservesUnknownGlobalAndRecordPropertiesDuringMutation() throws Exception {
        Path file = temporaryDirectory.resolve("unknown.properties");
        Properties initial = new Properties();
        initial.setProperty("store.schema", "1");
        initial.setProperty("future.global.mode", "keep-me");
        String playerPrefix = "player." + PLAYER_ONE + ".";
        initial.setProperty(playerPrefix + "schema", "1");
        initial.setProperty(playerPrefix + "state", "UNCLAIMED");
        initial.setProperty(playerPrefix + "futureExtension", "also-keep-me");
        storeProperties(file, initial);

        HyDragonStateStore store = new HyDragonStateStore(file);
        assertEquals(MutationOutcome.APPLIED, store.beginSoulBond(PLAYER_ONE, "soul:unknown-preservation"));

        Properties after = loadProperties(file);
        assertEquals("keep-me", after.getProperty("future.global.mode"));
        assertEquals("also-keep-me", after.getProperty(playerPrefix + "futureExtension"));
        assertEquals("PENDING", after.getProperty(playerPrefix + "state"));
    }

    @Test
    void operationReplayIsIdempotentAndDoesNotRewrite() throws Exception {
        Path file = temporaryDirectory.resolve("idempotent.properties");
        AtomicInteger writes = new AtomicInteger();
        AtomicFileWriter systemWriter = AtomicFileWriter.systemWriter();
        HyDragonStateStore store = new HyDragonStateStore(file, (destination, content) -> {
            writes.incrementAndGet();
            systemWriter.writeAtomically(destination, content);
        });

        assertEquals(MutationOutcome.APPLIED, store.beginSoulBond(PLAYER_ONE, "soul:replay"));
        assertEquals(MutationOutcome.ALREADY_APPLIED, store.beginSoulBond(PLAYER_ONE, "soul:replay"));
        assertEquals(MutationOutcome.CONFLICT, store.beginSoulBond(PLAYER_ONE, "soul:different"));
        assertEquals(MutationOutcome.APPLIED, store.completeSoulBond(PLAYER_ONE, "soul:replay", PROFILE_ONE, 500));
        assertEquals(
                MutationOutcome.ALREADY_APPLIED,
                store.completeSoulBond(PLAYER_ONE, "soul:replay", PROFILE_ONE, 999));
        assertEquals(
                MutationOutcome.CONFLICT,
                store.completeSoulBond(PLAYER_ONE, "soul:replay", PROFILE_TWO, 500));
        assertEquals(2, writes.get());

        ProfileExtensionRecord fire = ProfileExtensionRecord.soulboundMiniwyvern(
                PROFILE_ONE,
                "hydragon:miniwyvern",
                "fire",
                Optional.of("attune:fire"));
        assertEquals(MutationOutcome.APPLIED, store.putProfileExtension(fire));
        assertEquals(MutationOutcome.ALREADY_APPLIED, store.putProfileExtension(fire));
        assertEquals(
                MutationOutcome.CONFLICT,
                store.putProfileExtension(ProfileExtensionRecord.soulboundMiniwyvern(
                        PROFILE_ONE,
                        "hydragon:miniwyvern",
                        "ice",
                        Optional.of("attune:fire"))));
        assertEquals(3, writes.get());
    }

    @Test
    void unsupportedAndMalformedRecordsAreQuarantinedAndNotOverwritten() throws Exception {
        Path file = temporaryDirectory.resolve("quarantine.properties");
        Properties initial = new Properties();
        initial.setProperty("store.schema", "1");
        String profilePrefix = "profile." + PROFILE_ONE + ".";
        initial.setProperty(profilePrefix + "schema", "99");
        initial.setProperty(profilePrefix + "kind", "SOULBOUND_MINIWYVERN");
        initial.setProperty(profilePrefix + "speciesId", "hydragon:miniwyvern");
        initial.setProperty(profilePrefix + "archetypeId", "neutral");
        String malformedPlayerPrefix = "player." + PLAYER_TWO + ".";
        initial.setProperty(malformedPlayerPrefix + "schema", "1");
        initial.setProperty(malformedPlayerPrefix + "state", "CLAIMED");
        initial.setProperty(malformedPlayerPrefix + "operationId", "soul:malformed");
        storeProperties(file, initial);
        byte[] before = Files.readAllBytes(file);

        AtomicInteger writes = new AtomicInteger();
        HyDragonStateStore store = new HyDragonStateStore(file, (destination, content) -> writes.incrementAndGet());

        assertFalse(store.snapshot().profileExtensions().containsKey(PROFILE_ONE));
        assertFalse(store.snapshot().playerSoulBonds().containsKey(PLAYER_TWO));
        assertEquals(2, store.startupInventory().quarantinedRecords().size());
        assertTrue(store.startupInventory().quarantinedRecords().stream()
                .anyMatch(record -> record.type() == PersistentRecordType.PROFILE_EXTENSION
                        && record.reason().contains("Unsupported")));
        assertTrue(store.startupInventory().quarantinedRecords().stream()
                .anyMatch(record -> record.type() == PersistentRecordType.PLAYER_SOUL_BOND
                        && record.reason().contains("profileId")));

        ProfileExtensionRecord replacement = ProfileExtensionRecord.soulboundMiniwyvern(
                PROFILE_ONE,
                "hydragon:miniwyvern",
                "fire",
                Optional.of("attune:one"));
        assertEquals(MutationOutcome.QUARANTINED, store.putProfileExtension(replacement));
        assertEquals(MutationOutcome.QUARANTINED, store.beginSoulBond(PLAYER_TWO, "soul:new"));
        assertEquals(0, writes.get());
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void failedAtomicWriteKeepsCommittedFileAndPublishedSnapshot() throws Exception {
        Path file = temporaryDirectory.resolve("failure.properties");
        HyDragonStateStore initial = new HyDragonStateStore(file);
        assertEquals(MutationOutcome.APPLIED, initial.beginSoulBond(PLAYER_ONE, "soul:committed"));
        byte[] committedBytes = Files.readAllBytes(file);

        HyDragonStateStore failing = new HyDragonStateStore(file, (destination, content) -> {
            throw new IOException("simulated atomic replacement failure");
        });
        HyDragonStateSnapshot before = failing.snapshot();

        IOException failure = assertThrows(
                IOException.class,
                () -> failing.putProfileExtension(ProfileExtensionRecord.fullDragon(
                        PROFILE_ONE,
                        "hydragon:nordic_drake",
                        Optional.of("capture:one"))));

        assertTrue(failure.getMessage().contains("simulated"));
        assertArrayEquals(committedBytes, Files.readAllBytes(file));
        assertEquals(before, failing.snapshot());
        assertTrue(failing.snapshot().profileExtensions().isEmpty());
    }

    @Test
    void unsupportedStoreSchemaIsReadOnlyWithoutLosingRawData() throws Exception {
        Path file = temporaryDirectory.resolve("future-store.properties");
        Properties initial = new Properties();
        initial.setProperty("store.schema", "42");
        initial.setProperty("future.payload", "untouched");
        storeProperties(file, initial);
        byte[] before = Files.readAllBytes(file);

        HyDragonStateStore store = new HyDragonStateStore(file);

        assertFalse(store.snapshot().writable());
        assertEquals(MutationOutcome.STORE_READ_ONLY, store.beginSoulBond(PLAYER_ONE, "soul:blocked"));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(PersistentRecordType.STORE, store.snapshot().quarantinedRecords().getFirst().type());
    }

    private static EncounterRecord encounterRecord() {
        return new EncounterRecord(
                EncounterRecord.SCHEMA_VERSION,
                "encounter:nordic:1",
                "hydragon:nordic_drake_high_altitude",
                "Orbis",
                "glacial-ridge:14:9",
                "GROUNDING",
                encounterDefinitionSnapshot(),
                Optional.of(TARGET_NPC),
                Set.of(PLAYER_ONE, PLAYER_TWO),
                100,
                200,
                250,
                1_000);
    }

    private static ConsumableTransactionRecord soulBondTransaction(
            String operationId, UUID ownerUuid, long createdAtEpochMillis) {
        return ConsumableTransactionRecord.prepared(
                operationId,
                operationId,
                ConsumableTransactionKind.SOUL_BOND,
                new OperationOrigin("Alechilles:HyDragon", operationId),
                ownerUuid,
                "soul_bond:ZGVmYXVsdA:0:0",
                new SourceItemEvidence(
                        "Draconic_Soul_Bond", ownerUuid.toString(), "hotbar", 0,
                        1L, "fingerprint", 1),
                1,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                createdAtEpochMillis);
    }

    private static EncounterDefinitionSnapshot encounterDefinitionSnapshot() {
        return new EncounterDefinitionSnapshot(
                Set.of("projectile:Lure", "projectile:Stagger"),
                "projectile:Lure",
                Set.of("projectile:Stagger"),
                100.0D,
                "Combat.AirLand",
                "HyDragon_Grounded",
                45_000L,
                120_000L,
                60_000L,
                30_000L);
    }

    private static void storeProperties(Path file, Properties properties) throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "test fixture");
        }
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }
}
