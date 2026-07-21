package com.alechilles.hydragon.persistence;

import java.util.Map;
import java.util.Objects;

/** Raw record retained without mutation because its schema or contents could not be read safely. */
public record QuarantinedRecord(
        PersistentRecordType type,
        String persistentId,
        String reason,
        Map<String, String> rawProperties) {
    public QuarantinedRecord {
        Objects.requireNonNull(type, "type");
        persistentId = Objects.requireNonNull(persistentId, "persistentId");
        reason = Objects.requireNonNull(reason, "reason");
        rawProperties = Map.copyOf(Objects.requireNonNull(rawProperties, "rawProperties"));
    }
}
