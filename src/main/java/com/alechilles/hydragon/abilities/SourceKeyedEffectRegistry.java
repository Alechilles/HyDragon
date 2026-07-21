package com.alechilles.hydragon.abilities;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks logical effect sources above Hytale's effect-id-only controller.
 *
 * <p>The game stores one active effect per asset id, so removing that effect for one Miniwyvern
 * would otherwise remove the same effect supplied by another. This registry keeps the shared
 * effect alive until its last live source is released.</p>
 */
final class SourceKeyedEffectRegistry {
    private final Map<EffectKey, Map<String, Long>> expiriesBySource = new HashMap<>();

    synchronized RetainResult retain(EffectKey key, String sourceKey, long expiresAtMs, long nowMs) {
        Objects.requireNonNull(key, "key");
        String source = requiredText(sourceKey, "sourceKey");
        prune(nowMs);
        Map<String, Long> sources = expiriesBySource.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        Long previous = sources.put(source, expiresAtMs);
        long effective = sources.values().stream().mapToLong(Long::longValue).max().orElse(expiresAtMs);
        return new RetainResult(previous, effective);
    }

    synchronized void rollback(EffectKey key, String sourceKey, Long previousExpiryMs) {
        Map<String, Long> sources = expiriesBySource.get(key);
        if (sources == null) return;
        if (previousExpiryMs == null) sources.remove(sourceKey);
        else sources.put(sourceKey, previousExpiryMs);
        if (sources.isEmpty()) expiriesBySource.remove(key);
    }

    synchronized ReleaseResult release(EffectKey key, String sourceKey, long nowMs) {
        Objects.requireNonNull(key, "key");
        String source = requiredText(sourceKey, "sourceKey");
        prune(nowMs);
        Map<String, Long> sources = expiriesBySource.get(key);
        if (sources == null) return new ReleaseResult(true, 0L);
        sources.remove(source);
        if (sources.isEmpty()) {
            expiriesBySource.remove(key);
            return new ReleaseResult(true, 0L);
        }
        long remaining = sources.values().stream().mapToLong(Long::longValue).max().orElse(nowMs) - nowMs;
        return new ReleaseResult(false, Math.max(0L, remaining));
    }

    private void prune(long nowMs) {
        Iterator<Map.Entry<EffectKey, Map<String, Long>>> effects = expiriesBySource.entrySet().iterator();
        while (effects.hasNext()) {
            Map<String, Long> sources = effects.next().getValue();
            sources.entrySet().removeIf(entry -> entry.getValue() <= nowMs);
            if (sources.isEmpty()) effects.remove();
        }
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    record EffectKey(String worldName, UUID entityUuid, String effectId) {
        EffectKey {
            worldName = requiredText(worldName, "worldName");
            Objects.requireNonNull(entityUuid, "entityUuid");
            effectId = requiredText(effectId, "effectId");
        }
    }

    record RetainResult(Long previousExpiryMs, long effectiveExpiryMs) {
    }

    record ReleaseResult(boolean removeUnderlyingEffect, long remainingDurationMs) {
    }
}
