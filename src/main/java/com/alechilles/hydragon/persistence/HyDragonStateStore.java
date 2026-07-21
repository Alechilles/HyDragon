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
public final class HyDragonStateStore {
    public static final int STORE_SCHEMA_VERSION = 1;

    private static final String STORE_SCHEMA_KEY = "store.schema";
    private static final String PLAYER_PREFIX = "player.";
    private static final String PROFILE_PREFIX = "profile.";
    private static final String ENCOUNTER_PREFIX = "encounter.";
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
            "targetNpcUuid",
            "eligiblePlayerUuids",
            "createdAtEpochMillis",
            "phaseStartedAtEpochMillis",
            "updatedAtEpochMillis",
            "cooldownUntilEpochMillis");

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

        quarantined.sort(Comparator.comparing((QuarantinedRecord record) -> record.type().name())
                .thenComparing(QuarantinedRecord::persistentId));
        return new HyDragonStateSnapshot(
                storeSchema,
                writable,
                players,
                profiles,
                encounters,
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
                optionalUuid(properties, prefix + "targetNpcUuid"),
                uuidSet(properties, prefix + "eligiblePlayerUuids"),
                requiredLong(properties, prefix + "createdAtEpochMillis"),
                requiredLong(properties, prefix + "phaseStartedAtEpochMillis"),
                requiredLong(properties, prefix + "updatedAtEpochMillis"),
                requiredLong(properties, prefix + "cooldownUntilEpochMillis"));
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
        return ENCOUNTER_PREFIX + encodeEncounterId(encounterId) + ".";
    }

    private static String encodeEncounterId(String encounterId) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(encounterId.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeEncounterId(String encodedId) {
        byte[] decoded = Base64.getUrlDecoder().decode(encodedId);
        String value = new String(decoded, StandardCharsets.UTF_8);
        if (!encodeEncounterId(value).equals(encodedId)) {
            throw new IllegalArgumentException("Encounter key is not canonical base64url");
        }
        return requiredText(value, "encounterId");
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
