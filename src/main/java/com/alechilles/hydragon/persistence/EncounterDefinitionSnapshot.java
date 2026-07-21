package com.alechilles.hydragon.persistence;

import com.alechilles.hydragon.config.DragonEncounterConfig;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable lifecycle rules captured when a dynamic encounter is admitted.
 *
 * <p>Active encounters use this persisted snapshot instead of the live asset map. That makes a
 * config reload atomic for new admissions while allowing an already-spawned target to finish or
 * reconcile safely even when its source definition is changed or removed before a restart.</p>
 */
public record EncounterDefinitionSnapshot(
        Set<String> buildupSourceIds,
        String lureSourceId,
        Set<String> staggerSourceIds,
        double groundingThreshold,
        String groundedState,
        String groundedEffectId,
        long captureWindowMs,
        long encounterTimeoutMs,
        long retryCooldownMs) {

    public EncounterDefinitionSnapshot {
        buildupSourceIds = normalizedSet(buildupSourceIds, "buildupSourceIds");
        lureSourceId = requiredText(lureSourceId, "lureSourceId");
        staggerSourceIds = normalizedSet(staggerSourceIds, "staggerSourceIds");
        if (!buildupSourceIds.contains(lureSourceId)) {
            throw new IllegalArgumentException("lureSourceId must be a buildup source");
        }
        if (staggerSourceIds.isEmpty() || !buildupSourceIds.containsAll(staggerSourceIds)) {
            throw new IllegalArgumentException("staggerSourceIds must be non-empty buildup sources");
        }
        if (!Double.isFinite(groundingThreshold) || groundingThreshold <= 0.0D) {
            throw new IllegalArgumentException("groundingThreshold must be positive and finite");
        }
        groundedState = requiredText(groundedState, "groundedState");
        groundedEffectId = requiredText(groundedEffectId, "groundedEffectId");
        requirePositive(captureWindowMs, "captureWindowMs");
        requirePositive(encounterTimeoutMs, "encounterTimeoutMs");
        if (retryCooldownMs < 0L) {
            throw new IllegalArgumentException("retryCooldownMs must not be negative");
        }
    }

    public static EncounterDefinitionSnapshot capture(DragonEncounterConfig definition) {
        Objects.requireNonNull(definition, "definition");
        DragonEncounterConfig.GroundingSettings grounding = definition.getGrounding();
        DragonEncounterConfig.CleanupSettings cleanup = definition.getCleanupAndCooldown();
        return new EncounterDefinitionSnapshot(
                new LinkedHashSet<>(grounding.getBuildupSourceIds()),
                grounding.getLureSourceId(),
                new LinkedHashSet<>(grounding.getStaggerSourceIds()),
                grounding.getThreshold(),
                grounding.getGroundedState(),
                grounding.getGroundedEffectId(),
                grounding.getCaptureWindowMs(),
                cleanup.getEncounterTimeoutMs(),
                cleanup.getRetryCooldownMs());
    }

    private static Set<String> normalizedSet(Set<String> values, String field) {
        Objects.requireNonNull(values, field);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requiredText(value, field));
        }
        return Set.copyOf(normalized);
    }

    private static String requiredText(String value, String field) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return text;
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0L) throw new IllegalArgumentException(field + " must be positive");
    }
}
