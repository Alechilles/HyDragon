package com.alechilles.hydragon.persistence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Crash-safe local persistence for HyDragon-owned state.
 *
 * <p>The store retains unknown properties, quarantines records it cannot safely understand, and publishes
 * immutable snapshots only after a same-directory atomic replacement succeeds. All mutations are serialized
 * under one private lock; snapshot reads are lock-free.</p>
 */
public final class HyDragonStateStore implements PendingProfileProjectionStore {
    public static final int STORE_SCHEMA_VERSION = 1;

    private static final String STORE_SCHEMA_KEY = "store.schema";
    private static final String PLAYER_PREFIX = "player.";
    private static final String PROFILE_PREFIX = "profile.";
    private static final String ENCOUNTER_PREFIX = "encounter.";
    private static final String PROFILE_PROJECTION_PREFIX = "profileProjection.";
    private static final String TRANSACTION_PREFIX = "transaction.";
    private static final Set<String> PLAYER_FIELDS = Set.of(
            "schema", "state", "operationId", "profileId", "claimedAtEpochMillis");
    private static final Set<String> PROFILE_FIELDS = Set.of(
            "schema", "kind", "speciesId", "archetypeId", "lastOperationId");
    private static final Set<String> ENCOUNTER_FIELDS = Set.of(
            "schema",
            "id",
            "definitionId",
            "worldName",
            "regionKey",
            "phase",
            "definitionBuildupSourceIds",
            "definitionLureSourceId",
            "definitionStaggerSourceIds",
            "definitionGroundingThreshold",
            "definitionGroundedState",
            "definitionGroundedEffectId",
            "definitionCaptureWindowMs",
            "definitionEncounterTimeoutMs",
            "definitionRetryCooldownMs",
            "targetNpcUuid",
            "eligiblePlayerUuids",
            "createdAtEpochMillis",
            "phaseStartedAtEpochMillis",
            "updatedAtEpochMillis",
            "cooldownUntilEpochMillis");
    private static final Set<String> PROFILE_PROJECTION_FIELDS = Set.of(
            "schema", "operationId", "profileId", "roleId", "recordedAtEpochMillis");
    private static final Set<String> TRANSACTION_FIELDS = Set.of(
            "schema",
            "operationId",
            "correlationId",
            "kind",
            "status",
            "callerNamespace",
            "idempotencyKey",
            "ownerUuid",
            "intentId",
            "sourceItemId",
            "sourceHolderEvidenceId",
            "sourceContainerPath",
            "sourceInventorySlot",
            "sourceInventoryRevision",
            "sourceItemFingerprint",
            "sourceStackQuantityAtPrepare",
            "materialQuantity",
            "authoritySourceItemId",
            "authoritySourceHolderEvidenceId",
            "authoritySourceContainerPath",
            "authoritySourceInventorySlot",
            "authoritySourceInventoryRevision",
            "authoritySourceItemFingerprint",
            "authoritySourceStackQuantityAtPrepare",
            "authorityOperationId",
            "profileId",
            "bindingId",
            "bindingGeneration",
            "profileRevision",
            "revision",
            "createdAtEpochMillis",
            "updatedAtEpochMillis",
            "quarantineReason");

    private final Object mutationLock = new Object();
    private final Path file;
    private final AtomicFileWriter writer;
    private final ReconciliationInventory startupInventory;
    private Properties committedProperties;
    private volatile HyDragonStateSnapshot snapshot;

    public HyDragonStateStore(Path file) throws IOException {
        this(file, AtomicFileWriter.systemWriter());
    }

    /** Constructor with an injectable atomic boundary for deterministic failure testing. */
    public HyDragonStateStore(Path file, AtomicFileWriter writer) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.writer = Objects.requireNonNull(writer, "writer");
        this.committedProperties = loadProperties(this.file);
        this.snapshot = parseSnapshot(committedProperties);
        this.startupInventory = snapshot.reconciliationInventory();
    }

    public Path file() {
        return file;
    }

    public HyDragonStateSnapshot snapshot() {
        return snapshot;
    }

    /** Work discovered during construction, unaffected by subsequent runtime mutations. */
    public ReconciliationInventory startupInventory() {
        return startupInventory;
    }

    /** Stores a validated entitlement record; exact replay performs no disk write. */
    public MutationOutcome putPlayerSoulBond(PlayerSoulBondRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        synchronized (mutationLock) {
            return putPlayerSoulBondLocked(record);
        }
    }

    /** Begins the one-time Soul Bond grant without regressing a replayed completed operation. */
    public MutationOutcome beginSoulBond(UUID playerUuid, String operationId) throws IOException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        operationId = requiredText(operationId, "operationId");
        synchronized (mutationLock) {
            MutationOutcome blocked = mutationBlock(PersistentRecordType.PLAYER_SOUL_BOND, playerUuid.toString());
            if (blocked != null) {
                return blocked;
            }
            PlayerSoulBondRecord current = snapshot.playerSoulBonds().get(playerUuid);
            if (current == null || current.state() == SoulBondState.UNCLAIMED) {
                return putPlayerSoulBondLocked(PlayerSoulBondRecord.pending(playerUuid, operationId));
            }
            if (hasOperation(current, operationId)) {
                return MutationOutcome.ALREADY_APPLIED;
            }
            return MutationOutcome.CONFLICT;
        }
    }

    /** Completes a matching pending/reconciliation operation and is safe to replay after completion. */
    public MutationOutcome completeSoulBond(
            UUID playerUuid,
            String operationId,
            UUID profileId,
            long claimedAtEpochMillis) throws IOException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        operationId = requiredText(operationId, "operationId");
        Objects.requireNonNull(profileId, "profileId");
        if (claimedAtEpochMillis < 0) {
            throw new IllegalArgumentException("claimedAtEpochMillis must not be negative");
        }
        synchronized (mutationLock) {
            MutationOutcome blocked = mutationBlock(PersistentRecordType.PLAYER_SOUL_BOND, playerUuid.toString());
            if (blocked != null) {
                return blocked;
            }
            PlayerSoulBondRecord current = snapshot.playerSoulBonds().get(playerUuid);
            if (current == null || current.state() == SoulBondState.UNCLAIMED || !hasOperation(current, operationId)) {
                return MutationOutcome.CONFLICT;
            }
            if (current.state() == SoulBondState.CLAIMED) {
                return current.profileId().filter(profileId::equals).isPresent()
                        ? MutationOutcome.ALREADY_APPLIED
                        : MutationOutcome.CONFLICT;
            }
            return putPlayerSoulBondLocked(
                    PlayerSoulBondRecord.claimed(playerUuid, operationId, profileId, claimedAtEpochMillis));
        }
    }

    /**
     * Atomically completes a Soul Bond and creates the HyDragon-owned neutral Miniwyvern extension.
     * Neither record is published unless the same file generation contains both.
     */
    public MutationOutcome completeSoulBondWithMiniwyvernProfile(
            UUID playerUuid,
            String operationId,
            UUID profileId,
            long claimedAtEpochMillis) throws IOException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        operationId = requiredText(operationId, "operationId");
        Objects.requireNonNull(profileId, "profileId");
        if (claimedAtEpochMillis < 0) {
            throw new IllegalArgumentException("claimedAtEpochMillis must not be negative");
        }
        synchronized (mutationLock) {
            MutationOutcome playerBlock = mutationBlock(
                    PersistentRecordType.PLAYER_SOUL_BOND, playerUuid.toString());
            if (playerBlock != null) return playerBlock;
            MutationOutcome profileBlock = mutationBlock(
                    PersistentRecordType.PROFILE_EXTENSION, profileId.toString());
            if (profileBlock != null) return profileBlock;

            PlayerSoulBondRecord currentPlayer = snapshot.playerSoulBonds().get(playerUuid);
            if (currentPlayer == null || currentPlayer.state() == SoulBondState.UNCLAIMED
                    || !hasOperation(currentPlayer, operationId)) {
                return MutationOutcome.CONFLICT;
            }
            if (currentPlayer.profileId().isPresent()
                    && !currentPlayer.profileId().orElseThrow().equals(profileId)) {
                return MutationOutcome.CONFLICT;
            }

            ProfileExtensionRecord desiredProfile = ProfileExtensionRecord.soulboundMiniwyvern(
                    profileId, "miniwyvern", "neutral", Optional.of(operationId));
            ProfileExtensionRecord currentProfile = snapshot.profileExtensions().get(profileId);
            if (currentProfile != null && !currentProfile.equals(desiredProfile)) {
                return MutationOutcome.CONFLICT;
            }

            PlayerSoulBondRecord desiredPlayer = PlayerSoulBondRecord.claimed(
                    playerUuid, operationId, profileId, claimedAtEpochMillis);
            if (desiredPlayer.equals(currentPlayer) && desiredProfile.equals(currentProfile)) {
                return MutationOutcome.ALREADY_APPLIED;
            }

            Properties next = copyProperties(committedProperties);
            writePlayer(next, desiredPlayer);
            writeProfile(next, desiredProfile);
            commit(next);
            return MutationOutcome.APPLIED;
        }
    }

    /**
     * Records an ambiguous external result for startup repair. This may create a record when an external
     * operation succeeded before the local pending write was observed.
     */
    public MutationOutcome markSoulBondNeedsReconciliation(
            UUID playerUuid,
            String operationId,
            Optional<UUID> profileId,
            OptionalLong claimedAtEpochMillis) throws IOException {
        Objects.requireNonNull(playerUuid, "playerUuid");
        operationId = requiredText(operationId, "operationId");
        profileId = Objects.requireNonNull(profileId, "profileId");
        claimedAtEpochMillis = Objects.requireNonNull(claimedAtEpochMillis, "claimedAtEpochMillis");
        if (claimedAtEpochMillis.isPresent() && claimedAtEpochMillis.getAsLong() < 0) {
            throw new IllegalArgumentException("claimedAtEpochMillis must not be negative");
        }
        synchronized (mutationLock) {
            MutationOutcome blocked = mutationBlock(PersistentRecordType.PLAYER_SOUL_BOND, playerUuid.toString());
            if (blocked != null) {
                return blocked;
            }
            PlayerSoulBondRecord current = snapshot.playerSoulBonds().get(playerUuid);
            if (current != null && current.state() != SoulBondState.UNCLAIMED && !hasOperation(current, operationId)) {
                return MutationOutcome.CONFLICT;
            }
            if (current != null && current.state() == SoulBondState.CLAIMED) {
                boolean compatibleProfile = profileId.isEmpty()
                        || current.profileId().filter(profileId.orElseThrow()::equals).isPresent();
                return compatibleProfile ? MutationOutcome.ALREADY_APPLIED : MutationOutcome.CONFLICT;
            }

            Optional<UUID> resolvedProfile = profileId;
            OptionalLong resolvedClaimedAt = claimedAtEpochMillis;
            if (current != null && current.state() == SoulBondState.NEEDS_RECONCILIATION) {
                if (resolvedProfile.isEmpty()) {
                    resolvedProfile = current.profileId();
                }
                if (resolvedClaimedAt.isEmpty()) {
                    resolvedClaimedAt = current.claimedAtEpochMillis();
                }
            }
            return putPlayerSoulBondLocked(PlayerSoulBondRecord.needsReconciliation(
                    playerUuid,
                    operationId,
                    resolvedProfile,
                    resolvedClaimedAt));
        }
    }

    /** Stores a profile extension; exact replay performs no disk write. */
    public MutationOutcome putProfileExtension(ProfileExtensionRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        synchronized (mutationLock) {
            MutationOutcome blocked = mutationBlock(PersistentRecordType.PROFILE_EXTENSION, record.profileId().toString());
            if (blocked != null) {
                return blocked;
            }
            ProfileExtensionRecord current = snapshot.profileExtensions().get(record.profileId());
            if (record.equals(current)) {
                return MutationOutcome.ALREADY_APPLIED;
            }
            if (current != null
                    && record.lastOperationId().isPresent()
                    && record.lastOperationId().equals(current.lastOperationId())) {
                return MutationOutcome.CONFLICT;
            }
            Properties next = copyProperties(committedProperties);
            writeProfile(next, record);
            commit(next);
            return MutationOutcome.APPLIED;
        }
    }

    /** Stores an encounter checkpoint; exact replay performs no disk write. */
    public MutationOutcome putEncounter(EncounterRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        synchronized (mutationLock) {
            MutationOutcome blocked = mutationBlock(PersistentRecordType.ENCOUNTER, record.encounterId());
            if (blocked != null) {
                return blocked;
            }
            if (record.equals(snapshot.encounters().get(record.encounterId()))) {
                return MutationOutcome.ALREADY_APPLIED;
            }
            Properties next = copyProperties(committedProperties);
            writeEncounter(next, record);
            commit(next);
            return MutationOutcome.APPLIED;
        }
    }

    /** Removes a completed encounter and all properties scoped to that logical record; replay is idempotent. */
    public MutationOutcome removeEncounter(String encounterId) throws IOException {
        encounterId = requiredText(encounterId, "encounterId");
        synchronized (mutationLock) {
            MutationOutcome blocked = mutationBlock(PersistentRecordType.ENCOUNTER, encounterId);
            if (blocked != null) {
                return blocked;
            }
            if (!snapshot.encounters().containsKey(encounterId)) {
                return MutationOutcome.ALREADY_APPLIED;
            }
            Properties next = copyProperties(committedProperties);
            removePrefix(next, encounterPropertyPrefix(encounterId));
            commit(next);
            return MutationOutcome.APPLIED;
        }
    }

    @Override
    public Map<UUID, PendingProfileProjectionRecord> pendingProfileProjections() {
        return snapshot.pendingProfileProjections();
    }

    /** Durably queues minimal capture evidence before profile projection is attempted. */
    @Override
    public MutationOutcome putPendingProfileProjection(PendingProfileProjectionRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        synchronized (mutationLock) {
            String persistentId = record.operationId().toString();
            MutationOutcome blocked = mutationBlock(PersistentRecordType.PENDING_PROFILE_PROJECTION, persistentId);
            if (blocked != null) return blocked;
            PendingProfileProjectionRecord current = snapshot.pendingProfileProjections().get(record.operationId());
            if (record.matchesEvidence(current)) return MutationOutcome.ALREADY_APPLIED;
            if (current != null) return MutationOutcome.CONFLICT;
            Properties next = copyProperties(committedProperties);
            writePendingProfileProjection(next, record);
            commit(next);
            return MutationOutcome.APPLIED;
        }
    }

    /** Removes a projection only after its projection result is known to be terminal. */
    @Override
    public MutationOutcome removePendingProfileProjection(UUID operationId) throws IOException {
        Objects.requireNonNull(operationId, "operationId");
        synchronized (mutationLock) {
            String persistentId = operationId.toString();
            MutationOutcome blocked = mutationBlock(PersistentRecordType.PENDING_PROFILE_PROJECTION, persistentId);
            if (blocked != null) return blocked;
            if (!snapshot.pendingProfileProjections().containsKey(operationId)) {
                return MutationOutcome.ALREADY_APPLIED;
            }
            Properties next = copyProperties(committedProperties);
            removePrefix(next, profileProjectionPropertyPrefix(operationId));
            commit(next);
            return MutationOutcome.APPLIED;
        }
    }

    /**
     * Durably opens a consumable saga before the source stack is changed.
     *
     * <p>Both the operation ID and the cross-plugin origin are unique. An exact retry is a no-op; an attempt
     * to reuse either identity for different evidence fails closed.</p>
     */
    public MutationOutcome beginConsumableTransaction(ConsumableTransactionRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        if (record.status() != ConsumableTransactionStatus.PREPARED || record.revision() != 0) {
            throw new IllegalArgumentException("A new consumable transaction must be PREPARED at revision 0");
        }
        synchronized (mutationLock) {
            MutationOutcome blocked = mutationBlock(
                    PersistentRecordType.CONSUMABLE_TRANSACTION,
                    record.operationId());
            if (blocked != null) {
                return blocked;
            }
            ConsumableTransactionRecord current = snapshot.consumableTransactions().get(record.operationId());
            if (current != null && current.matchesPreparation(record)) {
                return MutationOutcome.ALREADY_APPLIED;
            }
            if (current != null) {
                return MutationOutcome.CONFLICT;
            }
            boolean originAlreadyUsed = snapshot.consumableTransactions().values().stream()
                    .anyMatch(existing -> existing.origin().equals(record.origin()));
            if (originAlreadyUsed) {
                return MutationOutcome.CONFLICT;
            }
            Properties next = copyProperties(committedProperties);
            writeTransaction(next, record);
            commit(next);
            return MutationOutcome.APPLIED;
        }
    }

    /**
     * Compare-and-sets one saga phase and revision. Replaying the completed transition returns
     * {@link MutationOutcome#ALREADY_APPLIED} without another disk write.
     */
    public MutationOutcome advanceConsumableTransaction(
            String operationId,
            long expectedRevision,
            ConsumableTransactionStatus expectedStatus,
            ConsumableTransactionStatus nextStatus,
            long updatedAtEpochMillis,
            Optional<String> authorityOperationId,
            Optional<String> profileId,
            OptionalLong profileRevision,
            Optional<String> quarantineReason) throws IOException {
        operationId = requiredText(operationId, "operationId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        Objects.requireNonNull(expectedStatus, "expectedStatus");
        Objects.requireNonNull(nextStatus, "nextStatus");
        if (!expectedStatus.mayTransitionTo(nextStatus)) {
            throw new IllegalArgumentException(
                    "Illegal consumable transaction transition " + expectedStatus + " -> " + nextStatus);
        }
        Objects.requireNonNull(authorityOperationId, "authorityOperationId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(profileRevision, "profileRevision");
        Objects.requireNonNull(quarantineReason, "quarantineReason");
        synchronized (mutationLock) {
            MutationOutcome blocked = mutationBlock(PersistentRecordType.CONSUMABLE_TRANSACTION, operationId);
            if (blocked != null) {
                return blocked;
            }
            ConsumableTransactionRecord current = snapshot.consumableTransactions().get(operationId);
            if (current == null) {
                return MutationOutcome.CONFLICT;
            }
            if (current.status() == nextStatus && current.revision() == expectedRevision + 1
                    && compatible(current.authorityOperationId(), authorityOperationId)
                    && compatible(current.profileId(), profileId)
                    && compatible(current.profileRevision(), profileRevision)
                    && compatible(current.quarantineReason(), quarantineReason)) {
                return MutationOutcome.ALREADY_APPLIED;
            }
            if (current.revision() != expectedRevision || current.status() != expectedStatus) {
                return MutationOutcome.CONFLICT;
            }
            if (!canResolve(current.authorityOperationId(), authorityOperationId)
                    || !canResolve(current.profileId(), profileId)
                    || !canResolve(current.profileRevision(), profileRevision)) {
                return MutationOutcome.CONFLICT;
            }
            ConsumableTransactionRecord replacement = current.transitionTo(
                    nextStatus,
                    updatedAtEpochMillis,
                    authorityOperationId,
                    profileId,
                    profileRevision,
                    quarantineReason);
            Properties next = copyProperties(committedProperties);
            writeTransaction(next, replacement);
            commit(next);
            return MutationOutcome.APPLIED;
        }
    }

    private static boolean compatible(Optional<String> recorded, Optional<String> supplied) {
        return supplied.isEmpty() || recorded.equals(supplied);
    }

    private static boolean compatible(OptionalLong recorded, OptionalLong supplied) {
        return supplied.isEmpty() || (recorded.isPresent() && recorded.getAsLong() == supplied.getAsLong());
    }

    private static boolean canResolve(Optional<String> recorded, Optional<String> supplied) {
        return recorded.isEmpty() || supplied.isEmpty() || recorded.equals(supplied);
    }

    private static boolean canResolve(OptionalLong recorded, OptionalLong supplied) {
        return recorded.isEmpty() || supplied.isEmpty() || recorded.getAsLong() == supplied.getAsLong();
    }

    private MutationOutcome putPlayerSoulBondLocked(PlayerSoulBondRecord record) throws IOException {
        MutationOutcome blocked = mutationBlock(PersistentRecordType.PLAYER_SOUL_BOND, record.playerUuid().toString());
        if (blocked != null) {
            return blocked;
        }
        if (record.equals(snapshot.playerSoulBonds().get(record.playerUuid()))) {
            return MutationOutcome.ALREADY_APPLIED;
        }
        Properties next = copyProperties(committedProperties);
        writePlayer(next, record);
        commit(next);
        return MutationOutcome.APPLIED;
    }

    private MutationOutcome mutationBlock(PersistentRecordType type, String persistentId) {
        if (!snapshot.writable()) {
            return MutationOutcome.STORE_READ_ONLY;
        }
        boolean quarantined = snapshot.quarantinedRecords().stream()
                .anyMatch(record -> record.type() == type && record.persistentId().equals(persistentId));
        return quarantined ? MutationOutcome.QUARANTINED : null;
    }

    private void commit(Properties next) throws IOException {
        next.setProperty(STORE_SCHEMA_KEY, Integer.toString(STORE_SCHEMA_VERSION));
        HyDragonStateSnapshot candidate = parseSnapshot(next);
        if (!candidate.writable()) {
            throw new IOException("Refusing to commit a state generation with an unsupported store schema");
        }
        byte[] serialized = serialize(next);
        writer.writeAtomically(file, serialized);
        committedProperties = next;
        snapshot = candidate;
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties properties = new Properties();
        if (!Files.exists(file)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed Properties encoding in " + file, exception);
        }
        return properties;
    }

    private static byte[] serialize(Properties properties) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            properties.store(output, "HyDragon durable state - unknown properties are preserved");
            return output.toByteArray();
        }
    }

    private static HyDragonStateSnapshot parseSnapshot(Properties properties) {
        List<QuarantinedRecord> quarantined = new ArrayList<>();
        int storeSchema = STORE_SCHEMA_VERSION;
        boolean writable = true;
        String rawStoreSchema = properties.getProperty(STORE_SCHEMA_KEY);
        if (rawStoreSchema != null) {
            try {
                storeSchema = Integer.parseInt(rawStoreSchema.trim());
                if (storeSchema != STORE_SCHEMA_VERSION) {
                    writable = false;
                    quarantined.add(new QuarantinedRecord(
                            PersistentRecordType.STORE,
                            "store",
                            "Unsupported store schema " + storeSchema,
                            rawWithPrefix(properties, "store.")));
                }
            } catch (RuntimeException exception) {
                storeSchema = -1;
                writable = false;
                quarantined.add(new QuarantinedRecord(
                        PersistentRecordType.STORE,
                        "store",
                        "Malformed store schema: " + rawStoreSchema,
                        rawWithPrefix(properties, "store.")));
            }
        }

        Map<UUID, PlayerSoulBondRecord> players = new LinkedHashMap<>();
        for (String persistentId : collectRecordSegments(properties, PLAYER_PREFIX)) {
            String prefix = PLAYER_PREFIX + persistentId + ".";
            try {
                UUID playerUuid = UUID.fromString(persistentId);
                PlayerSoulBondRecord record = parsePlayer(properties, prefix, playerUuid);
                players.put(playerUuid, record);
            } catch (RuntimeException exception) {
                quarantined.add(quarantine(
                        PersistentRecordType.PLAYER_SOUL_BOND,
                        persistentId,
                        exception,
                        properties,
                        prefix));
            }
        }

        Map<UUID, ProfileExtensionRecord> profiles = new LinkedHashMap<>();
        for (String persistentId : collectRecordSegments(properties, PROFILE_PREFIX)) {
            String prefix = PROFILE_PREFIX + persistentId + ".";
            try {
                UUID profileId = UUID.fromString(persistentId);
                ProfileExtensionRecord record = parseProfile(properties, prefix, profileId);
                profiles.put(profileId, record);
            } catch (RuntimeException exception) {
                quarantined.add(quarantine(
                        PersistentRecordType.PROFILE_EXTENSION,
                        persistentId,
                        exception,
                        properties,
                        prefix));
            }
        }

        Map<String, EncounterRecord> encounters = new LinkedHashMap<>();
        for (String encodedId : collectRecordSegments(properties, ENCOUNTER_PREFIX)) {
            String prefix = ENCOUNTER_PREFIX + encodedId + ".";
            String persistentId = encodedId;
            try {
                String encounterId = decodeEncounterId(encodedId);
                persistentId = encounterId;
                EncounterRecord record = parseEncounter(properties, prefix, encounterId);
                EncounterRecord duplicate = encounters.putIfAbsent(encounterId, record);
                if (duplicate != null) {
                    throw new IllegalArgumentException("Duplicate encoded encounter id " + encounterId);
                }
            } catch (RuntimeException exception) {
                quarantined.add(quarantine(
                        PersistentRecordType.ENCOUNTER,
                        persistentId,
                        exception,
                        properties,
                        prefix));
            }
        }

        Map<UUID, PendingProfileProjectionRecord> pendingProfileProjections = new LinkedHashMap<>();
        for (String persistentId : collectRecordSegments(properties, PROFILE_PROJECTION_PREFIX)) {
            String prefix = PROFILE_PROJECTION_PREFIX + persistentId + ".";
            try {
                UUID operationId = UUID.fromString(persistentId);
                PendingProfileProjectionRecord record = parsePendingProfileProjection(
                        properties, prefix, operationId);
                pendingProfileProjections.put(operationId, record);
            } catch (RuntimeException exception) {
                quarantined.add(quarantine(
                        PersistentRecordType.PENDING_PROFILE_PROJECTION,
                        persistentId,
                        exception,
                        properties,
                        prefix));
            }
        }

        Map<String, ConsumableTransactionRecord> transactions = new LinkedHashMap<>();
        for (String encodedId : collectRecordSegments(properties, TRANSACTION_PREFIX)) {
            String prefix = TRANSACTION_PREFIX + encodedId + ".";
            String persistentId = encodedId;
            try {
                String operationId = decodeRecordId(encodedId, "operationId");
                persistentId = operationId;
                ConsumableTransactionRecord record = parseTransaction(properties, prefix, operationId);
                ConsumableTransactionRecord duplicate = transactions.putIfAbsent(operationId, record);
                if (duplicate != null) {
                    throw new IllegalArgumentException("Duplicate encoded transaction operation id " + operationId);
                }
            } catch (RuntimeException exception) {
                quarantined.add(quarantine(
                        PersistentRecordType.CONSUMABLE_TRANSACTION,
                        persistentId,
                        exception,
                        properties,
                        prefix));
            }
        }

        Map<OperationOrigin, String> origins = new LinkedHashMap<>();
        for (ConsumableTransactionRecord record : List.copyOf(transactions.values())) {
            String existingOperation = origins.putIfAbsent(record.origin(), record.operationId());
            if (existingOperation != null) {
                transactions.remove(record.operationId());
                transactions.remove(existingOperation);
                quarantined.add(new QuarantinedRecord(
                        PersistentRecordType.CONSUMABLE_TRANSACTION,
                        existingOperation,
                        "Duplicate durable operation origin also used by " + record.operationId(),
                        rawWithPrefix(properties, transactionPropertyPrefix(existingOperation))));
                quarantined.add(new QuarantinedRecord(
                        PersistentRecordType.CONSUMABLE_TRANSACTION,
                        record.operationId(),
                        "Duplicate durable operation origin also used by " + existingOperation,
                        rawWithPrefix(properties, transactionPropertyPrefix(record.operationId()))));
            }
        }

        quarantined.sort(Comparator.comparing((QuarantinedRecord record) -> record.type().name())
                .thenComparing(QuarantinedRecord::persistentId));
        return new HyDragonStateSnapshot(
                storeSchema,
                writable,
                players,
                profiles,
                encounters,
                pendingProfileProjections,
                transactions,
                quarantined);
    }

    private static PlayerSoulBondRecord parsePlayer(Properties properties, String prefix, UUID playerUuid) {
        int schema = requiredInt(properties, prefix + "schema");
        requireSchema(schema, PlayerSoulBondRecord.SCHEMA_VERSION, "player Soul Bond");
        SoulBondState state = requiredEnum(properties, prefix + "state", SoulBondState.class);
        return new PlayerSoulBondRecord(
                schema,
                playerUuid,
                state,
                optionalText(properties, prefix + "operationId"),
                optionalUuid(properties, prefix + "profileId"),
                optionalLong(properties, prefix + "claimedAtEpochMillis"));
    }

    private static ProfileExtensionRecord parseProfile(Properties properties, String prefix, UUID profileId) {
        int schema = requiredInt(properties, prefix + "schema");
        requireSchema(schema, ProfileExtensionRecord.SCHEMA_VERSION, "profile extension");
        ProfileKind kind = requiredEnum(properties, prefix + "kind", ProfileKind.class);
        return new ProfileExtensionRecord(
                schema,
                profileId,
                kind,
                requiredText(properties, prefix + "speciesId"),
                optionalText(properties, prefix + "archetypeId"),
                optionalText(properties, prefix + "lastOperationId"));
    }

    private static EncounterRecord parseEncounter(Properties properties, String prefix, String encounterId) {
        int schema = requiredInt(properties, prefix + "schema");
        requireSchema(schema, EncounterRecord.SCHEMA_VERSION, "encounter");
        String persistedId = requiredText(properties, prefix + "id");
        if (!encounterId.equals(persistedId)) {
            throw new IllegalArgumentException("Encounter id does not match its encoded property key");
        }
        return new EncounterRecord(
                schema,
                encounterId,
                requiredText(properties, prefix + "definitionId"),
                requiredText(properties, prefix + "worldName"),
                requiredText(properties, prefix + "regionKey"),
                requiredText(properties, prefix + "phase"),
                new EncounterDefinitionSnapshot(
                        textSet(properties, prefix + "definitionBuildupSourceIds"),
                        requiredText(properties, prefix + "definitionLureSourceId"),
                        textSet(properties, prefix + "definitionStaggerSourceIds"),
                        requiredDouble(properties, prefix + "definitionGroundingThreshold"),
                        requiredText(properties, prefix + "definitionGroundedState"),
                        requiredText(properties, prefix + "definitionGroundedEffectId"),
                        requiredLong(properties, prefix + "definitionCaptureWindowMs"),
                        requiredLong(properties, prefix + "definitionEncounterTimeoutMs"),
                        requiredLong(properties, prefix + "definitionRetryCooldownMs")),
                optionalUuid(properties, prefix + "targetNpcUuid"),
                uuidSet(properties, prefix + "eligiblePlayerUuids"),
                requiredLong(properties, prefix + "createdAtEpochMillis"),
                requiredLong(properties, prefix + "phaseStartedAtEpochMillis"),
                requiredLong(properties, prefix + "updatedAtEpochMillis"),
                requiredLong(properties, prefix + "cooldownUntilEpochMillis"));
    }

    private static PendingProfileProjectionRecord parsePendingProfileProjection(
            Properties properties,
            String prefix,
            UUID operationId) {
        int schema = requiredInt(properties, prefix + "schema");
        requireSchema(schema, PendingProfileProjectionRecord.SCHEMA_VERSION, "pending profile projection");
        UUID persistedOperationId = UUID.fromString(requiredText(properties, prefix + "operationId"));
        if (!operationId.equals(persistedOperationId)) {
            throw new IllegalArgumentException(
                    "Pending profile projection operation id does not match its property key");
        }
        return new PendingProfileProjectionRecord(
                schema,
                operationId,
                requiredText(properties, prefix + "profileId"),
                requiredText(properties, prefix + "roleId"),
                requiredLong(properties, prefix + "recordedAtEpochMillis"));
    }

    private static ConsumableTransactionRecord parseTransaction(
            Properties properties,
            String prefix,
            String operationId) {
        int schema = requiredInt(properties, prefix + "schema");
        requireSchema(schema, ConsumableTransactionRecord.SCHEMA_VERSION, "consumable transaction");
        String persistedOperationId = requiredText(properties, prefix + "operationId");
        if (!operationId.equals(persistedOperationId)) {
            throw new IllegalArgumentException("Transaction operation id does not match its encoded property key");
        }
        return new ConsumableTransactionRecord(
                schema,
                operationId,
                requiredText(properties, prefix + "correlationId"),
                requiredEnum(properties, prefix + "kind", ConsumableTransactionKind.class),
                requiredEnum(properties, prefix + "status", ConsumableTransactionStatus.class),
                new OperationOrigin(
                        requiredText(properties, prefix + "callerNamespace"),
                        requiredText(properties, prefix + "idempotencyKey")),
                UUID.fromString(requiredText(properties, prefix + "ownerUuid")),
                requiredText(properties, prefix + "intentId"),
                new SourceItemEvidence(
                        requiredText(properties, prefix + "sourceItemId"),
                        requiredText(properties, prefix + "sourceHolderEvidenceId"),
                        requiredText(properties, prefix + "sourceContainerPath"),
                        requiredInt(properties, prefix + "sourceInventorySlot"),
                        requiredLong(properties, prefix + "sourceInventoryRevision"),
                        requiredText(properties, prefix + "sourceItemFingerprint"),
                        requiredInt(properties, prefix + "sourceStackQuantityAtPrepare")),
                requiredInt(properties, prefix + "materialQuantity"),
                optionalSourceItem(properties, prefix, "authoritySource"),
                optionalText(properties, prefix + "authorityOperationId"),
                optionalText(properties, prefix + "profileId"),
                optionalUuid(properties, prefix + "bindingId"),
                optionalLong(properties, prefix + "bindingGeneration"),
                optionalLong(properties, prefix + "profileRevision"),
                requiredLong(properties, prefix + "revision"),
                requiredLong(properties, prefix + "createdAtEpochMillis"),
                requiredLong(properties, prefix + "updatedAtEpochMillis"),
                optionalText(properties, prefix + "quarantineReason"));
    }

    private static Optional<SourceItemEvidence> optionalSourceItem(
            Properties properties,
            String prefix,
            String fieldPrefix) {
        String itemIdKey = prefix + fieldPrefix + "ItemId";
        boolean present = properties.stringPropertyNames().stream()
                .anyMatch(key -> key.startsWith(prefix + fieldPrefix));
        if (!present) {
            return Optional.empty();
        }
        return Optional.of(new SourceItemEvidence(
                requiredText(properties, itemIdKey),
                requiredText(properties, prefix + fieldPrefix + "HolderEvidenceId"),
                requiredText(properties, prefix + fieldPrefix + "ContainerPath"),
                requiredInt(properties, prefix + fieldPrefix + "InventorySlot"),
                requiredLong(properties, prefix + fieldPrefix + "InventoryRevision"),
                requiredText(properties, prefix + fieldPrefix + "ItemFingerprint"),
                requiredInt(properties, prefix + fieldPrefix + "StackQuantityAtPrepare")));
    }

    private static void writePlayer(Properties properties, PlayerSoulBondRecord record) {
        String prefix = PLAYER_PREFIX + record.playerUuid() + ".";
        clearKnownFields(properties, prefix, PLAYER_FIELDS);
        properties.setProperty(prefix + "schema", Integer.toString(record.schemaVersion()));
        properties.setProperty(prefix + "state", record.state().name());
        setOptional(properties, prefix + "operationId", record.operationId());
        setOptional(properties, prefix + "profileId", record.profileId().map(UUID::toString));
        setOptionalLong(properties, prefix + "claimedAtEpochMillis", record.claimedAtEpochMillis());
    }

    private static void writeProfile(Properties properties, ProfileExtensionRecord record) {
        String prefix = PROFILE_PREFIX + record.profileId() + ".";
        clearKnownFields(properties, prefix, PROFILE_FIELDS);
        properties.setProperty(prefix + "schema", Integer.toString(record.schemaVersion()));
        properties.setProperty(prefix + "kind", record.kind().name());
        properties.setProperty(prefix + "speciesId", record.speciesId());
        setOptional(properties, prefix + "archetypeId", record.archetypeId());
        setOptional(properties, prefix + "lastOperationId", record.lastOperationId());
    }

    private static void writeEncounter(Properties properties, EncounterRecord record) {
        String prefix = encounterPropertyPrefix(record.encounterId());
        clearKnownFields(properties, prefix, ENCOUNTER_FIELDS);
        properties.setProperty(prefix + "schema", Integer.toString(record.schemaVersion()));
        properties.setProperty(prefix + "id", record.encounterId());
        properties.setProperty(prefix + "definitionId", record.definitionId());
        properties.setProperty(prefix + "worldName", record.worldName());
        properties.setProperty(prefix + "regionKey", record.regionKey());
        properties.setProperty(prefix + "phase", record.phase());
        EncounterDefinitionSnapshot definition = record.definitionSnapshot();
        properties.setProperty(prefix + "definitionBuildupSourceIds", joinTextSet(definition.buildupSourceIds()));
        properties.setProperty(prefix + "definitionLureSourceId", definition.lureSourceId());
        properties.setProperty(prefix + "definitionStaggerSourceIds", joinTextSet(definition.staggerSourceIds()));
        properties.setProperty(prefix + "definitionGroundingThreshold", Double.toString(definition.groundingThreshold()));
        properties.setProperty(prefix + "definitionGroundedState", definition.groundedState());
        properties.setProperty(prefix + "definitionGroundedEffectId", definition.groundedEffectId());
        properties.setProperty(prefix + "definitionCaptureWindowMs", Long.toString(definition.captureWindowMs()));
        properties.setProperty(prefix + "definitionEncounterTimeoutMs", Long.toString(definition.encounterTimeoutMs()));
        properties.setProperty(prefix + "definitionRetryCooldownMs", Long.toString(definition.retryCooldownMs()));
        setOptional(properties, prefix + "targetNpcUuid", record.targetNpcUuid().map(UUID::toString));
        String eligiblePlayers = record.eligiblePlayerUuids().stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
        properties.setProperty(prefix + "eligiblePlayerUuids", eligiblePlayers);
        properties.setProperty(prefix + "createdAtEpochMillis", Long.toString(record.createdAtEpochMillis()));
        properties.setProperty(prefix + "phaseStartedAtEpochMillis", Long.toString(record.phaseStartedAtEpochMillis()));
        properties.setProperty(prefix + "updatedAtEpochMillis", Long.toString(record.updatedAtEpochMillis()));
        properties.setProperty(prefix + "cooldownUntilEpochMillis", Long.toString(record.cooldownUntilEpochMillis()));
    }

    private static void writePendingProfileProjection(
            Properties properties,
            PendingProfileProjectionRecord record) {
        String prefix = profileProjectionPropertyPrefix(record.operationId());
        clearKnownFields(properties, prefix, PROFILE_PROJECTION_FIELDS);
        properties.setProperty(prefix + "schema", Integer.toString(record.schemaVersion()));
        properties.setProperty(prefix + "operationId", record.operationId().toString());
        properties.setProperty(prefix + "profileId", record.profileId());
        properties.setProperty(prefix + "roleId", record.roleId());
        properties.setProperty(
                prefix + "recordedAtEpochMillis", Long.toString(record.recordedAtEpochMillis()));
    }

    private static void writeTransaction(Properties properties, ConsumableTransactionRecord record) {
        String prefix = transactionPropertyPrefix(record.operationId());
        clearKnownFields(properties, prefix, TRANSACTION_FIELDS);
        properties.setProperty(prefix + "schema", Integer.toString(record.schemaVersion()));
        properties.setProperty(prefix + "operationId", record.operationId());
        properties.setProperty(prefix + "correlationId", record.correlationId());
        properties.setProperty(prefix + "kind", record.kind().name());
        properties.setProperty(prefix + "status", record.status().name());
        properties.setProperty(prefix + "callerNamespace", record.origin().callerNamespace());
        properties.setProperty(prefix + "idempotencyKey", record.origin().idempotencyKey());
        properties.setProperty(prefix + "ownerUuid", record.ownerUuid().toString());
        properties.setProperty(prefix + "intentId", record.intentId());
        properties.setProperty(prefix + "sourceItemId", record.sourceItem().itemId());
        properties.setProperty(prefix + "sourceHolderEvidenceId", record.sourceItem().holderEvidenceId());
        properties.setProperty(prefix + "sourceContainerPath", record.sourceItem().containerPath());
        properties.setProperty(prefix + "sourceInventorySlot", Integer.toString(record.sourceItem().inventorySlot()));
        properties.setProperty(
                prefix + "sourceInventoryRevision",
                Long.toString(record.sourceItem().inventoryRevision()));
        properties.setProperty(prefix + "sourceItemFingerprint", record.sourceItem().itemFingerprint());
        properties.setProperty(
                prefix + "sourceStackQuantityAtPrepare",
                Integer.toString(record.sourceItem().stackQuantityAtPrepare()));
        properties.setProperty(prefix + "materialQuantity", Integer.toString(record.materialQuantity()));
        writeOptionalSourceItem(properties, prefix, "authoritySource", record.authoritySourceItem());
        setOptional(properties, prefix + "authorityOperationId", record.authorityOperationId());
        setOptional(properties, prefix + "profileId", record.profileId());
        setOptional(properties, prefix + "bindingId", record.bindingId().map(UUID::toString));
        setOptionalLong(properties, prefix + "bindingGeneration", record.bindingGeneration());
        setOptionalLong(properties, prefix + "profileRevision", record.profileRevision());
        properties.setProperty(prefix + "revision", Long.toString(record.revision()));
        properties.setProperty(prefix + "createdAtEpochMillis", Long.toString(record.createdAtEpochMillis()));
        properties.setProperty(prefix + "updatedAtEpochMillis", Long.toString(record.updatedAtEpochMillis()));
        setOptional(properties, prefix + "quarantineReason", record.quarantineReason());
    }

    private static void writeOptionalSourceItem(
            Properties properties,
            String prefix,
            String fieldPrefix,
            Optional<SourceItemEvidence> evidence) {
        if (evidence.isEmpty()) {
            return;
        }
        SourceItemEvidence value = evidence.orElseThrow();
        properties.setProperty(prefix + fieldPrefix + "ItemId", value.itemId());
        properties.setProperty(prefix + fieldPrefix + "HolderEvidenceId", value.holderEvidenceId());
        properties.setProperty(prefix + fieldPrefix + "ContainerPath", value.containerPath());
        properties.setProperty(prefix + fieldPrefix + "InventorySlot", Integer.toString(value.inventorySlot()));
        properties.setProperty(prefix + fieldPrefix + "InventoryRevision", Long.toString(value.inventoryRevision()));
        properties.setProperty(prefix + fieldPrefix + "ItemFingerprint", value.itemFingerprint());
        properties.setProperty(
                prefix + fieldPrefix + "StackQuantityAtPrepare",
                Integer.toString(value.stackQuantityAtPrepare()));
    }

    private static void clearKnownFields(Properties properties, String prefix, Set<String> fields) {
        for (String field : fields) {
            properties.remove(prefix + field);
        }
    }

    private static void removePrefix(Properties properties, String prefix) {
        List<String> keys = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .toList();
        keys.forEach(properties::remove);
    }

    private static void setOptional(Properties properties, String key, Optional<String> value) {
        if (value.isPresent()) {
            properties.setProperty(key, value.orElseThrow());
        } else {
            properties.remove(key);
        }
    }

    private static void setOptionalLong(Properties properties, String key, OptionalLong value) {
        if (value.isPresent()) {
            properties.setProperty(key, Long.toString(value.getAsLong()));
        } else {
            properties.remove(key);
        }
    }

    private static boolean hasOperation(PlayerSoulBondRecord record, String operationId) {
        return record.operationId().filter(operationId::equals).isPresent();
    }

    private static String encounterPropertyPrefix(String encounterId) {
        return ENCOUNTER_PREFIX + encodeRecordId(encounterId) + ".";
    }

    private static String transactionPropertyPrefix(String operationId) {
        return TRANSACTION_PREFIX + encodeRecordId(operationId) + ".";
    }

    private static String profileProjectionPropertyPrefix(UUID operationId) {
        return PROFILE_PROJECTION_PREFIX + operationId + ".";
    }

    private static String encodeRecordId(String recordId) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(recordId.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeEncounterId(String encodedId) {
        return decodeRecordId(encodedId, "encounterId");
    }

    private static String decodeRecordId(String encodedId, String field) {
        byte[] decoded = Base64.getUrlDecoder().decode(encodedId);
        String value = new String(decoded, StandardCharsets.UTF_8);
        if (!encodeRecordId(value).equals(encodedId)) {
            throw new IllegalArgumentException(field + " key is not canonical base64url");
        }
        return requiredText(value, field);
    }

    private static Set<String> collectRecordSegments(Properties properties, String familyPrefix) {
        Set<String> segments = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(familyPrefix)) {
                continue;
            }
            int fieldSeparator = key.indexOf('.', familyPrefix.length());
            if (fieldSeparator > familyPrefix.length()) {
                segments.add(key.substring(familyPrefix.length(), fieldSeparator));
            }
        }
        return segments;
    }

    private static QuarantinedRecord quarantine(
            PersistentRecordType type,
            String persistentId,
            RuntimeException exception,
            Properties properties,
            String prefix) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return new QuarantinedRecord(type, persistentId, message, rawWithPrefix(properties, prefix));
    }

    private static Map<String, String> rawWithPrefix(Properties properties, String prefix) {
        Map<String, String> raw = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                raw.put(key, properties.getProperty(key));
            }
        }
        return raw;
    }

    private static void requireSchema(int actual, int expected, String family) {
        if (actual != expected) {
            throw new IllegalArgumentException("Unsupported " + family + " schema " + actual);
        }
    }

    private static int requiredInt(Properties properties, String key) {
        return Integer.parseInt(requiredText(properties, key));
    }

    private static long requiredLong(Properties properties, String key) {
        return Long.parseLong(requiredText(properties, key));
    }

    private static double requiredDouble(Properties properties, String key) {
        return Double.parseDouble(requiredText(properties, key));
    }

    private static OptionalLong optionalLong(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(requiredText(value, key)));
    }

    private static Optional<UUID> optionalUuid(Properties properties, String key) {
        return optionalText(properties, key).map(UUID::fromString);
    }

    private static Set<UUID> uuidSet(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required property " + key);
        }
        if (value.isBlank()) {
            return Set.of();
        }
        Set<UUID> result = new TreeSet<>(Comparator.comparing(UUID::toString));
        for (String item : value.split(",", -1)) {
            result.add(UUID.fromString(requiredText(item, key)));
        }
        return Set.copyOf(result);
    }

    private static Set<String> textSet(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing required property " + key);
        if (value.isBlank()) return Set.of();
        Set<String> result = new TreeSet<>();
        for (String item : value.split(",", -1)) result.add(requiredText(item, key));
        return Set.copyOf(result);
    }

    private static String joinTextSet(Set<String> values) {
        for (String value : values) {
            if (value.indexOf(',') >= 0) {
                throw new IllegalArgumentException("Persisted encounter source IDs cannot contain commas");
            }
        }
        return values.stream().sorted().collect(Collectors.joining(","));
    }

    private static <E extends Enum<E>> E requiredEnum(Properties properties, String key, Class<E> type) {
        return Enum.valueOf(type, requiredText(properties, key));
    }

    private static Optional<String> optionalText(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? Optional.empty() : Optional.of(requiredText(value, key));
    }

    private static String requiredText(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required property " + key);
        }
        return requiredText(value, key);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static Properties copyProperties(Properties source) {
        Properties copy = new Properties();
        for (String key : source.stringPropertyNames()) {
            copy.setProperty(key, source.getProperty(key));
        }
        return copy;
    }
}
