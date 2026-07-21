package com.alechilles.hydragon.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Immutable durable journal row for one HyDragon-owned item-consumption saga.
 *
 * <p>The source evidence and operation origin are frozen at preparation. Authority identifiers may only be
 * filled in once as an operation progresses, which allows Soul Bond provisioning to publish its canonical
 * profile after preparation without permitting that profile to be replaced on a retry.</p>
 */
public record ConsumableTransactionRecord(
        int schemaVersion,
        String operationId,
        String correlationId,
        ConsumableTransactionKind kind,
        ConsumableTransactionStatus status,
        OperationOrigin origin,
        UUID ownerUuid,
        String intentId,
        SourceItemEvidence sourceItem,
        int materialQuantity,
        Optional<SourceItemEvidence> authoritySourceItem,
        Optional<String> authorityOperationId,
        Optional<String> profileId,
        Optional<UUID> bindingId,
        OptionalLong bindingGeneration,
        OptionalLong profileRevision,
        long revision,
        long createdAtEpochMillis,
        long updatedAtEpochMillis,
        Optional<String> quarantineReason) {
    public static final int SCHEMA_VERSION = 1;

    public ConsumableTransactionRecord {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported consumable transaction schema " + schemaVersion);
        }
        operationId = requiredText(operationId, "operationId");
        correlationId = requiredText(correlationId, "correlationId");
        kind = Objects.requireNonNull(kind, "kind");
        status = Objects.requireNonNull(status, "status");
        origin = Objects.requireNonNull(origin, "origin");
        ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        intentId = requiredText(intentId, "intentId");
        sourceItem = Objects.requireNonNull(sourceItem, "sourceItem");
        authoritySourceItem = Objects.requireNonNull(authoritySourceItem, "authoritySourceItem");
        authorityOperationId = normalized(authorityOperationId, "authorityOperationId");
        profileId = normalized(profileId, "profileId");
        bindingId = Objects.requireNonNull(bindingId, "bindingId");
        bindingGeneration = requireNonNegative(bindingGeneration, "bindingGeneration");
        profileRevision = requireNonNegative(profileRevision, "profileRevision");
        quarantineReason = normalized(quarantineReason, "quarantineReason");
        if (materialQuantity <= 0 || materialQuantity > sourceItem.stackQuantityAtPrepare()) {
            throw new IllegalArgumentException("materialQuantity must fit within the prepared source stack");
        }
        if (revision < 0 || createdAtEpochMillis < 0 || updatedAtEpochMillis < createdAtEpochMillis) {
            throw new IllegalArgumentException("revision and timestamps are invalid");
        }
        if (status == ConsumableTransactionStatus.QUARANTINED && quarantineReason.isEmpty()) {
            throw new IllegalArgumentException("QUARANTINED requires quarantineReason");
        }
        if (status != ConsumableTransactionStatus.QUARANTINED && quarantineReason.isPresent()) {
            throw new IllegalArgumentException("quarantineReason is valid only for QUARANTINED");
        }
        if (kind == ConsumableTransactionKind.BONDED_STONE_REPAIR
                && (authoritySourceItem.isEmpty() || bindingId.isEmpty() || bindingGeneration.isEmpty() || profileId.isEmpty()
                || profileRevision.isEmpty() || authorityOperationId.isEmpty())) {
            throw new IllegalArgumentException(
                    "BONDED_STONE_REPAIR requires damaged-source evidence, authority operation, binding, generation, profile, and profile revision");
        }
        if (kind != ConsumableTransactionKind.BONDED_STONE_REPAIR && authoritySourceItem.isPresent()) {
            throw new IllegalArgumentException("authoritySourceItem is reserved for BONDED_STONE_REPAIR");
        }
        if (kind == ConsumableTransactionKind.MINIWYVERN_ATTUNEMENT
                && (profileId.isEmpty() || profileRevision.isEmpty())) {
            throw new IllegalArgumentException("MINIWYVERN_ATTUNEMENT requires profile identity and revision");
        }
    }

    public static ConsumableTransactionRecord prepared(
            String operationId,
            String correlationId,
            ConsumableTransactionKind kind,
            OperationOrigin origin,
            UUID ownerUuid,
            String intentId,
            SourceItemEvidence sourceItem,
            int materialQuantity,
            Optional<SourceItemEvidence> authoritySourceItem,
            Optional<String> authorityOperationId,
            Optional<String> profileId,
            Optional<UUID> bindingId,
            OptionalLong bindingGeneration,
            OptionalLong profileRevision,
            long createdAtEpochMillis) {
        return new ConsumableTransactionRecord(
                SCHEMA_VERSION,
                operationId,
                correlationId,
                kind,
                ConsumableTransactionStatus.PREPARED,
                origin,
                ownerUuid,
                intentId,
                sourceItem,
                materialQuantity,
                authoritySourceItem,
                authorityOperationId,
                profileId,
                bindingId,
                bindingGeneration,
                profileRevision,
                0,
                createdAtEpochMillis,
                createdAtEpochMillis,
                Optional.empty());
    }

    /**
     * Source-compatible factory for non-repair consumers. Bonded repair callers must use the overload that
     * supplies the separately fenced damaged-stone projection.
     */
    public static ConsumableTransactionRecord prepared(
            String operationId,
            String correlationId,
            ConsumableTransactionKind kind,
            OperationOrigin origin,
            UUID ownerUuid,
            String intentId,
            SourceItemEvidence sourceItem,
            int materialQuantity,
            Optional<String> authorityOperationId,
            Optional<String> profileId,
            Optional<UUID> bindingId,
            OptionalLong bindingGeneration,
            OptionalLong profileRevision,
            long createdAtEpochMillis) {
        return prepared(
                operationId,
                correlationId,
                kind,
                origin,
                ownerUuid,
                intentId,
                sourceItem,
                materialQuantity,
                Optional.empty(),
                authorityOperationId,
                profileId,
                bindingId,
                bindingGeneration,
                profileRevision,
                createdAtEpochMillis);
    }

    /** Creates the next CAS generation while preserving every frozen field. */
    public ConsumableTransactionRecord transitionTo(
            ConsumableTransactionStatus nextStatus,
            long updatedAtEpochMillis,
            Optional<String> resolvedAuthorityOperationId,
            Optional<String> resolvedProfileId,
            OptionalLong resolvedProfileRevision,
            Optional<String> quarantineReason) {
        if (!status.mayTransitionTo(Objects.requireNonNull(nextStatus, "nextStatus"))) {
            throw new IllegalArgumentException("Illegal consumable transaction transition " + status + " -> " + nextStatus);
        }
        if (updatedAtEpochMillis < this.updatedAtEpochMillis) {
            throw new IllegalArgumentException("updatedAtEpochMillis cannot move backwards");
        }
        return new ConsumableTransactionRecord(
                schemaVersion,
                operationId,
                correlationId,
                kind,
                nextStatus,
                origin,
                ownerUuid,
                intentId,
                sourceItem,
                materialQuantity,
                authoritySourceItem,
                merge(authorityOperationId, resolvedAuthorityOperationId, "authorityOperationId"),
                merge(profileId, resolvedProfileId, "profileId"),
                bindingId,
                bindingGeneration,
                merge(profileRevision, resolvedProfileRevision, "profileRevision"),
                Math.addExact(revision, 1),
                createdAtEpochMillis,
                updatedAtEpochMillis,
                quarantineReason);
    }

    private static Optional<String> merge(Optional<String> current, Optional<String> supplied, String field) {
        current = normalized(current, field);
        supplied = normalized(supplied, field);
        if (current.isPresent() && supplied.isPresent() && !current.equals(supplied)) {
            throw new IllegalArgumentException(field + " cannot change once recorded");
        }
        return current.isPresent() ? current : supplied;
    }

    private static OptionalLong merge(OptionalLong current, OptionalLong supplied, String field) {
        current = requireNonNegative(current, field);
        supplied = requireNonNegative(supplied, field);
        if (current.isPresent() && supplied.isPresent() && current.getAsLong() != supplied.getAsLong()) {
            throw new IllegalArgumentException(field + " cannot change once recorded");
        }
        return current.isPresent() ? current : supplied;
    }

    private static Optional<String> normalized(Optional<String> value, String field) {
        Objects.requireNonNull(value, field);
        return value.map(item -> requiredText(item, field));
    }

    private static OptionalLong requireNonNegative(OptionalLong value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isPresent() && value.getAsLong() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
