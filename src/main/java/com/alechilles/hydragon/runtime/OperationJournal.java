package com.alechilles.hydragon.runtime;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Durable compare-and-transition journal required by cross-plugin item sagas.
 * Implementations must persist before returning APPLIED; an in-memory implementation is not valid in production.
 */
public interface OperationJournal {
    Optional<Entry> find(String operationId);

    /** Immutable recovery scan; production journals override this with durable entries. */
    default List<Entry> entries() {
        return List.of();
    }

    Decision begin(Descriptor descriptor);

    Decision transition(String operationId, Phase expected, Phase next, Update update);

    boolean available();

    enum Kind { SOUL_BOND, MINIWYVERN_ATTUNEMENT, BONDED_STONE_REPAIR }

    enum Phase {
        PREPARED,
        MATERIAL_CONSUMED,
        COMMITTED,
        REFUND_DUE,
        REFUNDED,
        QUARANTINED
    }

    enum Decision { APPLIED, ALREADY_APPLIED, CONFLICT, QUARANTINED, UNAVAILABLE }

    record Entry(String operationId, Kind kind, Phase phase, Descriptor descriptor, long revision) {
        public Entry {
            operationId = requireText(operationId, "operationId");
            kind = Objects.requireNonNull(kind, "kind");
            phase = Objects.requireNonNull(phase, "phase");
            descriptor = Objects.requireNonNull(descriptor, "descriptor");
            if (!operationId.equals(descriptor.operationId()) || kind != descriptor.kind() || revision < 0) {
                throw new IllegalArgumentException("journal entry identity is inconsistent");
            }
        }

        private static String requireText(String value, String field) {
            String normalized = Objects.requireNonNull(value, field).trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
            return normalized;
        }
    }

    record Descriptor(String operationId,
                      String correlationId,
                      Kind kind,
                      UUID ownerUuid,
                      String intentId,
                      ConsumableReservation.SourceEvidence source,
                      int materialQuantity,
                      Optional<ConsumableReservation.SourceEvidence> authoritySource,
                      Optional<String> authorityOperationId,
                      Optional<String> profileId,
                      Optional<UUID> bindingId,
                      OptionalLong bindingGeneration,
                      OptionalLong profileRevision) {
        public Descriptor {
            operationId = required(operationId, "operationId");
            correlationId = required(correlationId, "correlationId");
            kind = Objects.requireNonNull(kind, "kind");
            ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
            intentId = required(intentId, "intentId");
            source = Objects.requireNonNull(source, "source");
            authoritySource = Objects.requireNonNull(authoritySource, "authoritySource");
            authorityOperationId = Objects.requireNonNull(authorityOperationId, "authorityOperationId");
            profileId = Objects.requireNonNull(profileId, "profileId");
            bindingId = Objects.requireNonNull(bindingId, "bindingId");
            bindingGeneration = Objects.requireNonNull(bindingGeneration, "bindingGeneration");
            profileRevision = Objects.requireNonNull(profileRevision, "profileRevision");
            if (materialQuantity <= 0 || materialQuantity > source.stackQuantityAtPrepare()) {
                throw new IllegalArgumentException("materialQuantity exceeds source stack");
            }
        }
    }

    record Update(Optional<String> authorityOperationId,
                  Optional<String> profileId,
                  OptionalLong profileRevision,
                  Optional<String> quarantineReason) {
        public static final Update EMPTY = new Update(
                Optional.empty(), Optional.empty(), OptionalLong.empty(), Optional.empty());

        public Update {
            authorityOperationId = Objects.requireNonNull(authorityOperationId, "authorityOperationId");
            profileId = Objects.requireNonNull(profileId, "profileId");
            profileRevision = Objects.requireNonNull(profileRevision, "profileRevision");
            quarantineReason = Objects.requireNonNull(quarantineReason, "quarantineReason");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
