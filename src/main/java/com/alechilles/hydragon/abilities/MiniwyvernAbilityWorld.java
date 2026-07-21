package com.alechilles.hydragon.abilities;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** World-thread-only Hytale mutation/query boundary used by the deterministic ability scheduler. */
public interface MiniwyvernAbilityWorld {
    boolean isWorldThread();

    String worldName();

    Optional<Target> owner();

    Optional<Target> companion();

    Optional<Target> hostileTarget(double maximumRange);

    /** Returns a deterministic, bounded set of hostile targets around the companion. */
    default List<Target> hostileTargets(double maximumRange, int maximumTargets) {
        if (maximumTargets <= 0) return List.of();
        return hostileTarget(maximumRange).stream().limit(maximumTargets).toList();
    }

    /** Applies the archetype's canonical model while preserving the live entity and its scale. */
    boolean synchronizeAppearance(UUID entityUuid, String appearanceId);

    Health health(UUID entityUuid);

    boolean applyEffect(UUID entityUuid, String sourceKey, String effectId, double durationSeconds);

    boolean removeEffect(UUID entityUuid, String sourceKey, String effectId);

    /**
     * Verifies that a data-selected effect implements one exact passive modifier semantic.
     * This keeps engine capabilities granular: an unavailable jump or action-speed channel does
     * not suppress a separately supported horizontal-speed effect.
     */
    default boolean supportsPassiveModifierEffect(
            String modifierId,
            double requestedValue,
            double configuredMaximum,
            String effectId) {
        return false;
    }

    /** Verifies the configured effect/source stacking contract before an effect is applied. */
    default boolean supportsEffectStacking(String effectId, String stackingPolicy, int maximumStacks) {
        return maximumStacks == 1;
    }

    /** Verifies that the selected effect asset honors the configured Void defense bounds. */
    default boolean supportsBoundedDefenseReduction(
            String effectId,
            double requestedReduction,
            double minimumDefenseMultiplier,
            double maximumReduction) {
        return false;
    }

    boolean supportsOwnerModifiers(Map<String, Double> modifiers);

    boolean applyOwnerModifiers(UUID ownerUuid, String sourceKey, Map<String, Double> modifiers, double durationSeconds);

    boolean removeOwnerModifiers(UUID ownerUuid, String sourceKey);

    /** Emits every valid particle/sound asset at the requested entity and returns the emitted count. */
    default int emitPresentation(UUID entityUuid, List<String> particleAndSoundIds) {
        return 0;
    }

    boolean launchProjectile(UUID sourceUuid, UUID targetUuid, String projectileId);

    boolean dealDamage(UUID sourceUuid, UUID targetUuid, double amount);

    boolean heal(UUID entityUuid, double amount);

    boolean areAllies(UUID ownerUuid, UUID targetUuid);

    record Target(UUID entityUuid, UUID ownerUuid, String worldName, double distance, boolean alive) {
        public Target {
            java.util.Objects.requireNonNull(entityUuid, "entityUuid");
            worldName = java.util.Objects.requireNonNull(worldName, "worldName").trim();
            if (worldName.isEmpty()) throw new IllegalArgumentException("worldName is required");
            if (!Double.isFinite(distance) || distance < 0.0D) {
                throw new IllegalArgumentException("distance must be finite and non-negative");
            }
        }
    }

    record Health(double current, double maximum) {
        public Health {
            if (!Double.isFinite(current) || !Double.isFinite(maximum)
                    || maximum < 0.0D || current < 0.0D || current > maximum) {
                throw new IllegalArgumentException("invalid health snapshot");
            }
        }

        public double fraction() {
            return maximum <= 0.0D ? 0.0D : current / maximum;
        }
    }
}
