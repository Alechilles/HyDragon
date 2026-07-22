package com.alechilles.hydragon.abilities;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable, engine-neutral scheduler state for one Soul Bond Miniwyvern profile. */
public record MiniwyvernAbilityState(
        int schemaVersion,
        String archetypeId,
        Map<String, Long> cooldownUntilByAbility,
        Map<UUID, Double> iceBuildupByTarget,
        Map<UUID, Long> controlImmunityUntilByTarget,
        Map<UUID, Long> iceTargetUpdatedAtByTarget,
        Set<String> appliedSourceKeys,
        Map<String, UUID> targetBySourceKey,
        Map<String, Long> sourceExpiresAtBySourceKey,
        long updatedAtEpochMillis) {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_TRACKED_ICE_TARGETS = 128;
    public static final int MAX_TRACKED_SOURCE_KEYS = 256;

    public MiniwyvernAbilityState {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported ability-state schema " + schemaVersion);
        }
        archetypeId = requiredText(archetypeId, "archetypeId").toLowerCase(java.util.Locale.ROOT);
        cooldownUntilByAbility = copyLongMap(cooldownUntilByAbility, "cooldownUntilByAbility");
        iceBuildupByTarget = copyDoubleMap(iceBuildupByTarget, "iceBuildupByTarget");
        controlImmunityUntilByTarget = copyLongMap(controlImmunityUntilByTarget, "controlImmunityUntilByTarget");
        iceTargetUpdatedAtByTarget = copyLongMap(iceTargetUpdatedAtByTarget, "iceTargetUpdatedAtByTarget");
        Set<UUID> trackedIceTargets = new java.util.LinkedHashSet<>(iceBuildupByTarget.keySet());
        trackedIceTargets.addAll(controlImmunityUntilByTarget.keySet());
        if (trackedIceTargets.size() > MAX_TRACKED_ICE_TARGETS
                || !iceTargetUpdatedAtByTarget.keySet().equals(trackedIceTargets)) {
            throw new IllegalArgumentException("Ice target tracking is incomplete or exceeds its bound");
        }
        Set<String> normalizedAppliedSourceKeys = Set.copyOf(
                Objects.requireNonNull(appliedSourceKeys, "appliedSourceKeys"));
        if (normalizedAppliedSourceKeys.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("appliedSourceKeys cannot contain blank values");
        }
        if (normalizedAppliedSourceKeys.size() > MAX_TRACKED_SOURCE_KEYS) {
            throw new IllegalArgumentException("appliedSourceKeys exceeds its bound");
        }
        appliedSourceKeys = normalizedAppliedSourceKeys;
        targetBySourceKey = Map.copyOf(Objects.requireNonNull(targetBySourceKey, "targetBySourceKey"));
        if (targetBySourceKey.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getKey().isBlank() || entry.getValue() == null
                || !normalizedAppliedSourceKeys.contains(entry.getKey()))) {
            throw new IllegalArgumentException("targetBySourceKey contains an invalid or untracked source");
        }
        sourceExpiresAtBySourceKey = copyLongMap(sourceExpiresAtBySourceKey, "sourceExpiresAtBySourceKey");
        if (!sourceExpiresAtBySourceKey.keySet().equals(normalizedAppliedSourceKeys)) {
            throw new IllegalArgumentException("sourceExpiresAtBySourceKey must cover every tracked source");
        }
        if (updatedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("updatedAtEpochMillis must not be negative");
        }
    }

    public static MiniwyvernAbilityState empty(String archetypeId, long nowMs) {
        return new MiniwyvernAbilityState(
                SCHEMA_VERSION, archetypeId, Map.of(), Map.of(), Map.of(), Map.of(),
                Set.of(), Map.of(), Map.of(), nowMs);
    }

    private static <K> Map<K, Long> copyLongMap(Map<K, Long> values, String field) {
        Objects.requireNonNull(values, field);
        for (Map.Entry<K, Long> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0L) {
                throw new IllegalArgumentException(field + " contains an invalid entry");
            }
        }
        return Map.copyOf(values);
    }

    private static Map<UUID, Double> copyDoubleMap(Map<UUID, Double> values, String field) {
        Objects.requireNonNull(values, field);
        for (Map.Entry<UUID, Double> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !Double.isFinite(entry.getValue()) || entry.getValue() < 0.0D) {
                throw new IllegalArgumentException(field + " contains an invalid entry");
            }
        }
        return Map.copyOf(values);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
